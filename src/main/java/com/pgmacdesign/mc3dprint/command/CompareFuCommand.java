package com.pgmacdesign.mc3dprint.command;

import com.mojang.logging.LogUtils;
import com.pgmacdesign.mc3dprint.fu.FuValue;
import com.pgmacdesign.mc3dprint.fu.FuValueRegistry;
import com.pgmacdesign.mc3dprint.fu.MinecraftRecipeIndex;
import com.pgmacdesign.mc3dprint.fu.RecipeFuValuator;
import com.pgmacdesign.mc3dprint.fu.RelaxationFuValuator;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * {@code /mc3dprint comparefu} — the differential harness behind the FU-valuation
 * rewrite decision.
 *
 * <p>Runs THREE valuators over the identical bound recipe graph and diffs them per item:
 * <ol>
 *   <li><b>current</b> — the live {@link RecipeFuValuator} DFS (what the game ships today,
 *       and what {@code dumpfu} measured);</li>
 *   <li><b>bounded</b> — {@link RelaxationFuValuator} capped at {@link RecipeFuValuator#MAX_DEPTH},
 *       the theory that a fast bottom-up pass reproduces today's values;</li>
 *   <li><b>unbounded</b> — the same relaxation to a true fixed point, which values deep
 *       chains the depth cap misses (a real economy change).</li>
 * </ol>
 *
 * <p>All three go through the SAME base/cosmetic-canonical precedence
 * ({@link #resolve}); only the derivation engine differs, so any per-item difference is a
 * genuine valuation change, not a harness artifact. Writes
 * {@code <world>/mc3dprint/fu-compare.json} with the timings and the full diff list, so the
 * economy churn of each option is exact rather than estimated.
 */
public final class CompareFuCommand {
    private static final Logger LOGGER = LogUtils.getLogger();

    private record Cell(FuValue value) {
        Integer fu() { return value == null ? null : value.fu(); }
        Integer tier() { return value == null ? null : value.tier(); }
        boolean differs(Cell o) {
            if (value == null || o.value == null) {
                return value != o.value;
            }
            return value.fu() != o.value.fu() || value.tier() != o.value.tier();
        }
    }

    private record Row(String id, Cell current, Cell bounded, Cell unbounded) {}

    private CompareFuCommand() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("mc3dprint")
                .then(Commands.literal("comparefu")
                        .requires(source ->
                                //? if >=1.21.11 {
                                /*source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER)
                                *///?} else {
                                source.hasPermission(2)
                                //?}
                        )
                        .executes(context -> start(context.getSource()))));
    }

    private static int start(CommandSourceStack source) {
        Collection<RecipeHolder<?>> recipes = FuValueRegistry.boundRecipesForDiagnostics();
        RegistryAccess registryAccess = FuValueRegistry.boundRegistries();
        if (recipes.isEmpty() || registryAccess == null) {
            source.sendFailure(Component.literal(
                    "MC3DPrint: recipes not bound yet — join a world first, then rerun."));
            return 0;
        }
        Path out = source.getServer().getWorldPath(LevelResource.ROOT)
                .resolve("mc3dprint").resolve("fu-compare.json");
        source.sendSuccess(() -> Component.literal(
                "MC3DPrint: comparing current vs bounded vs unbounded valuation... "
                        + "this reruns the slow current sweep once, so give it a couple minutes."), true);

        List<Item> items = new ArrayList<>();
        BuiltInRegistries.ITEM.forEach(items::add);

        Thread worker = new Thread(() -> {
            try {
                Result result = compare(recipes, registryAccess, items);
                write(out, result);
                source.getServer().execute(() -> source.sendSuccess(() -> Component.literal(
                        "MC3DPrint: comparison written to " + out.toAbsolutePath()
                                + String.format(" | current %.1fs, bounded %.2fs, unbounded %.2fs | "
                                + "bounded changes %d, unbounded changes %d (of %d valued)",
                                result.currentMs / 1000.0, result.boundedMs / 1000.0,
                                result.unboundedMs / 1000.0, result.boundedDiffs, result.unboundedDiffs,
                                result.valuedCurrent)), true));
            } catch (Exception e) {
                LOGGER.error("FU comparison failed", e);
                source.getServer().execute(() -> source.sendFailure(
                        Component.literal("MC3DPrint: FU comparison failed — see the log.")));
            }
        }, "mc3dprint-fu-compare");
        worker.setDaemon(true);
        worker.start();
        return 1;
    }

    private record Result(List<Row> rows, long currentMs, long boundedMs, long unboundedMs,
                          int valuedCurrent, int boundedDiffs, int unboundedDiffs) {}

    private static Result compare(Collection<RecipeHolder<?>> recipes,
                                  RegistryAccess registryAccess, List<Item> items) {
        Function<Item, Optional<FuValue>> base = item -> FuValueRegistry.baseValue(item, null);

        // One shared index -> all three engines see the identical recipe set.
        MinecraftRecipeIndex index = new MinecraftRecipeIndex(recipes, registryAccess, base);
        var recipesByOutput = index.recipesByOutput();

        long t0 = System.nanoTime();
        RecipeFuValuator<Item> dfs = new RecipeFuValuator<>(index);
        Cell[] current = sweep(items, item -> resolve(item, base, dfs::valueOf));
        long currentMs = (System.nanoTime() - t0) / 1_000_000L;

        t0 = System.nanoTime();
        RelaxationFuValuator<Item> boundedEngine =
                new RelaxationFuValuator<>(recipesByOutput, base, RecipeFuValuator.MAX_DEPTH);
        Cell[] bounded = sweep(items, item -> resolve(item, base, boundedEngine::valueOf));
        long boundedMs = (System.nanoTime() - t0) / 1_000_000L;

        t0 = System.nanoTime();
        RelaxationFuValuator<Item> unboundedEngine =
                new RelaxationFuValuator<>(recipesByOutput, base, Integer.MAX_VALUE);
        Cell[] unbounded = sweep(items, item -> resolve(item, base, unboundedEngine::valueOf));
        long unboundedMs = (System.nanoTime() - t0) / 1_000_000L;

        List<Row> rows = new ArrayList<>();
        int valuedCurrent = 0, boundedDiffs = 0, unboundedDiffs = 0;
        for (int i = 0; i < items.size(); i++) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(items.get(i));
            if (id == null) {
                continue;
            }
            Cell c = current[i], b = bounded[i], u = unbounded[i];
            if (c.value() != null) {
                valuedCurrent++;
            }
            boolean bDiff = c.differs(b);
            boolean uDiff = c.differs(u);
            if (bDiff) {
                boundedDiffs++;
            }
            if (uDiff) {
                unboundedDiffs++;
            }
            if (bDiff || uDiff) {
                rows.add(new Row(id.toString(), c, b, u));
            }
        }
        rows.sort((a, b) -> a.id().compareTo(b.id()));
        return new Result(rows, currentMs, boundedMs, unboundedMs, valuedCurrent, boundedDiffs, unboundedDiffs);
    }

    private static Cell[] sweep(List<Item> items, Function<Item, FuValue> engine) {
        Cell[] out = new Cell[items.size()];
        for (int i = 0; i < items.size(); i++) {
            FuValue v;
            try {
                v = engine.apply(items.get(i));
            } catch (RuntimeException e) {
                v = null; // a hostile modded item shouldn't abort the whole compare
            }
            out[i] = new Cell(v);
        }
        return out;
    }

    /**
     * The live {@code valueOf} precedence with the derivation step pluggable: explicit/base
     * value wins, then a cosmetic colour/patina variant defers to its canonical sibling, then
     * the supplied engine derives. Shared by all three engines so only derivation differs.
     */
    private static FuValue resolve(Item item, Function<Item, Optional<FuValue>> base,
                                   Function<Item, Optional<FuValue>> derive) {
        Optional<FuValue> b = base.apply(item);
        if (b.isPresent()) {
            return b.get();
        }
        Item canonical = FuValueRegistry.canonicalCosmeticVariant(item);
        if (canonical != item) {
            return resolve(canonical, base, derive);
        }
        return derive.apply(item).orElse(null);
    }

    private static void write(Path out, Result r) throws IOException {
        TreeMap<String, Row> byId = new TreeMap<>();
        for (Row row : r.rows()) {
            byId.put(row.id(), row);
        }

        StringBuilder sb = new StringBuilder(byId.size() * 128 + 512);
        sb.append("{\n");
        sb.append("  \"itemCountValuedCurrent\": ").append(r.valuedCurrent()).append(",\n");
        sb.append("  \"currentMs\": ").append(r.currentMs()).append(",\n");
        sb.append("  \"boundedMs\": ").append(r.boundedMs()).append(",\n");
        sb.append("  \"unboundedMs\": ").append(r.unboundedMs()).append(",\n");
        sb.append("  \"boundedChangesVsCurrent\": ").append(r.boundedDiffs()).append(",\n");
        sb.append("  \"unboundedChangesVsCurrent\": ").append(r.unboundedDiffs()).append(",\n");
        sb.append("  \"diffs\": {\n");
        int i = 0;
        int n = byId.size();
        for (Row row : byId.values()) {
            sb.append("    \"").append(row.id()).append("\": {");
            cell(sb, "current", row.current());
            sb.append(", ");
            cell(sb, "bounded", row.bounded());
            sb.append(", ");
            cell(sb, "unbounded", row.unbounded());
            sb.append('}').append(++i < n ? ",\n" : "\n");
        }
        sb.append("  }\n}\n");

        Files.createDirectories(out.getParent());
        Files.writeString(out, sb.toString());
        LOGGER.info("Wrote FU comparison: {} valued, bounded changes {}, unbounded changes {}",
                r.valuedCurrent(), r.boundedDiffs(), r.unboundedDiffs());
    }

    private static void cell(StringBuilder sb, String key, Cell c) {
        sb.append('"').append(key).append("\": ");
        if (c.value() == null) {
            sb.append("null");
        } else {
            sb.append("{\"fu\": ").append(c.fu()).append(", \"tier\": ").append(c.tier()).append('}');
        }
    }
}
