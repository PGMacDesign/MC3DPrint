package com.pgmacdesign.mc3dprint.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.pgmacdesign.mc3dprint.blueprint.CuratedBlueprints;
import com.pgmacdesign.mc3dprint.blueprint.repository.RepositoryIndex;
import com.pgmacdesign.mc3dprint.loot.BlueprintLootPool;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * {@code /mc3dprint discovered}: inspect and edit the world-loot discovery ledger.
 *
 * <p>Mainly an operator escape hatch: the one-time seed only knows about blueprints
 * catalogued in a repository, so discs sitting loose in a chest on an existing world
 * need marking by hand to keep them out of the loot pool.
 */
public final class DiscoveryCommand {

    private static final SuggestionProvider<CommandSourceStack> BLUEPRINT_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggest(CuratedBlueprints.lootBlueprints(), builder);

    private DiscoveryCommand() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("mc3dprint")
                .then(Commands.literal("discovered")
                        .requires(source ->
                                //? if >=1.21.11 {
                                /*source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER)
                                *///?} else {
                                source.hasPermission(2)
                                //?}
                        )
                        .then(Commands.literal("list")
                                .executes(context -> list(context.getSource())))
                        .then(Commands.literal("reset")
                                .executes(context -> reset(context.getSource())))
                        .then(Commands.literal("reseed")
                                .executes(context -> reseed(context.getSource())))
                        .then(Commands.literal("add")
                                .then(Commands.argument("blueprint", StringArgumentType.string())
                                        .suggests(BLUEPRINT_SUGGESTIONS)
                                        .executes(context -> add(context.getSource(),
                                                StringArgumentType.getString(context, "blueprint")))))));
    }

    private static int list(CommandSourceStack source) {
        ServerPlayer player = scopePlayer(source);
        if (player == null && !RepositoryIndex.shared()) {
            source.sendFailure(Component.translatable("command.mc3dprint.discovered.needs_player"));
            return 0;
        }
        List<String> available = availableBuilds();
        Set<UUID> found = RepositoryIndex.discoveredIds(source.getServer(), player);
        int remaining = BlueprintLootPool.candidates(available, found).size();
        int discovered = available.size() - remaining;
        source.sendSuccess(() -> Component.translatable("command.mc3dprint.discovered.list",
                discovered, available.size(), remaining,
                Component.translatable(RepositoryIndex.shared()
                        ? "command.mc3dprint.discovered.scope_shared"
                        : "command.mc3dprint.discovered.scope_personal")), false);
        return discovered;
    }

    private static int reset(CommandSourceStack source) {
        ServerPlayer player = scopePlayer(source);
        if (player == null && !RepositoryIndex.shared()) {
            source.sendFailure(Component.translatable("command.mc3dprint.discovered.needs_player"));
            return 0;
        }
        RepositoryIndex.resetDiscovered(source.getServer(), player, null);
        source.sendSuccess(() -> Component.translatable("command.mc3dprint.discovered.reset"), true);
        return 1;
    }

    /**
     * Re-arms the one-time catalogue seed. The loot path never does this on its own,
     * because re-seeding after a cycle reset would narrow the pool straight back down;
     * as a deliberate operator action it is the way to re-sync after depositing a batch
     * of discs the ledger has no record of.
     */
    private static int reseed(CommandSourceStack source) {
        ServerPlayer player = scopePlayer(source);
        if (player == null && !RepositoryIndex.shared()) {
            source.sendFailure(Component.translatable("command.mc3dprint.discovered.needs_player"));
            return 0;
        }
        RepositoryIndex.clearDiscoverySeeded(source.getServer(), player);
        source.sendSuccess(() -> Component.translatable("command.mc3dprint.discovered.reseed"), true);
        return 1;
    }

    private static int add(CommandSourceStack source, String name) {
        ServerPlayer player = scopePlayer(source);
        if (player == null && !RepositoryIndex.shared()) {
            source.sendFailure(Component.translatable("command.mc3dprint.discovered.needs_player"));
            return 0;
        }
        if (!CuratedBlueprints.lootBlueprints().contains(name)) {
            source.sendFailure(Component.translatable("command.mc3dprint.discovered.unknown", name));
            return 0;
        }
        UUID id = BlueprintLootPool.idFor(name);
        if (RepositoryIndex.discoveredIds(source.getServer(), player).contains(id)) {
            source.sendFailure(Component.translatable("command.mc3dprint.discovered.already", name));
            return 0;
        }
        RepositoryIndex.markDiscovered(source.getServer(), player, id);
        source.sendSuccess(() -> Component.translatable("command.mc3dprint.discovered.added", name), true);
        return 1;
    }

    /** Builds the loot roll can actually draw, so counts here match what the pool sees. */
    private static List<String> availableBuilds() {
        return CuratedBlueprints.lootBlueprints().stream()
                .filter(CuratedBlueprints::modsAvailable).toList();
    }

    /** Null for console in shared mode (the world ledger needs no player) and an error otherwise. */
    @Nullable
    private static ServerPlayer scopePlayer(CommandSourceStack source) {
        return source.getEntity() instanceof ServerPlayer player ? player : null;
    }
}
