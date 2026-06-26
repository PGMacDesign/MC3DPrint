package com.pgmacdesign.mc3dprint.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.pgmacdesign.mc3dprint.blueprint.Blueprint;
import com.pgmacdesign.mc3dprint.blueprint.BlueprintFileStore;
import com.pgmacdesign.mc3dprint.blueprint.BlueprintFormatException;
import com.pgmacdesign.mc3dprint.blueprint.io.SpongeSchematicImporter;
import com.pgmacdesign.mc3dprint.blueprint.io.VanillaStructureImporter;
import com.pgmacdesign.mc3dprint.item.BlueprintDiscItem;
import com.pgmacdesign.mc3dprint.registry.ModItems;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * {@code /mc3dprint import <file>} — converts a WorldEdit {@code .schem} or a
 * vanilla/Create structure {@code .nbt} from {@code world/mc3dprint/import/}
 * into a Blueprint Disc. The v1 interop path for both formats.
 */
public final class ImportCommand {

    private static final SuggestionProvider<CommandSourceStack> FILE_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(listImportable(importDir(context.getSource())), builder);

    private ImportCommand() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("mc3dprint")
                .then(Commands.literal("import")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("file", StringArgumentType.greedyString())
                                .suggests(FILE_SUGGESTIONS)
                                .executes(context -> importFile(context.getSource(),
                                        StringArgumentType.getString(context, "file")))))
                .then(Commands.literal("export")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> exportHeldDisc(context.getSource()))));
    }

    /** Exports the Blueprint Disc in the player's main hand as a Sponge .schem. */
    private static int exportHeldDisc(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.translatable("command.mc3dprint.export_not_player"));
            return 0;
        }
        ItemStack held = player.getMainHandItem();
        UUID id = BlueprintDiscItem.getBlueprintId(held).orElse(null);
        if (id == null) {
            source.sendFailure(Component.translatable("command.mc3dprint.export_no_disc"));
            return 0;
        }
        BlueprintFileStore store = BlueprintFileStore.forServer(source.getServer());
        Blueprint blueprint = store.load(id).orElse(null);
        if (blueprint == null) {
            source.sendFailure(Component.translatable("command.mc3dprint.export_missing", id));
            return 0;
        }

        int dataVersion = net.minecraft.SharedConstants.getCurrentVersion().getDataVersion().getVersion();
        CompoundTag schem = com.pgmacdesign.mc3dprint.blueprint.io.SpongeSchematicExporter
                .exportV2(blueprint, dataVersion);
        Path dir = source.getServer().getWorldPath(LevelResource.ROOT).resolve("mc3dprint").resolve("export");
        String safeName = blueprint.name().replaceAll("[^A-Za-z0-9._-]", "_");
        Path file = dir.resolve(safeName + ".schem");
        try {
            Files.createDirectories(dir);
            NbtIo.writeCompressed(schem, file);
        } catch (IOException e) {
            source.sendFailure(Component.translatable("command.mc3dprint.export_failed", e.getMessage()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.mc3dprint.export_success",
                file.getFileName().toString()), true);
        return 1;
    }

    private static Path importDir(CommandSourceStack source) {
        return source.getServer().getWorldPath(LevelResource.ROOT).resolve("mc3dprint").resolve("import");
    }

    private static List<String> listImportable(Path dir) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(p -> p.getFileName().toString())
                    .filter(name -> name.endsWith(".schem") || name.endsWith(".nbt"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private static int importFile(CommandSourceStack source, String fileName) {
        Path dir = importDir(source);
        Path file = dir.resolve(fileName).normalize();
        if (!file.startsWith(dir) || !Files.isRegularFile(file)) {
            source.sendFailure(Component.translatable("command.mc3dprint.import_not_found", fileName,
                    dir.toString()));
            return 0;
        }

        Blueprint blueprint;
        try {
            CompoundTag tag = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            String name = fileName.substring(0, fileName.lastIndexOf('.'));
            if (fileName.endsWith(".schem")) {
                blueprint = SpongeSchematicImporter.importSchematic(name, tag);
            } else {
                blueprint = VanillaStructureImporter.importStructure(name, tag);
            }
        } catch (IOException | BlueprintFormatException e) {
            source.sendFailure(Component.translatable("command.mc3dprint.import_failed", fileName,
                    e.getMessage()));
            return 0;
        }

        BlueprintFileStore store = BlueprintFileStore.forServer(source.getServer());
        UUID id = store.save(blueprint);
        ItemStack disc = new ItemStack(ModItems.BLUEPRINT_DISC.get());
        // playerCreated = true: an imported disc is treated as player-authored (not official).
        BlueprintDiscItem.writeBlueprint(disc, id, blueprint, true);

        if (source.getEntity() instanceof ServerPlayer player) {
            if (!player.getInventory().add(disc)) {
                player.drop(disc, false);
            }
        }
        final Blueprint imported = blueprint;
        source.sendSuccess(() -> Component.translatable("command.mc3dprint.import_success",
                fileName, imported.blockCount()), true);
        return 1;
    }
}
