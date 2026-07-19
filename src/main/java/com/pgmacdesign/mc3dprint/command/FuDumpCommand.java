package com.pgmacdesign.mc3dprint.command;

import com.mojang.logging.LogUtils;
import com.pgmacdesign.mc3dprint.fu.FuValue;
import com.pgmacdesign.mc3dprint.fu.FuValueRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * {@code /mc3dprint dumpfu} — diagnostic dump of the whole FU valuation table.
 *
 * <p>Exists to answer two questions about a REAL modpack that a synthetic recipe
 * graph can't: how long a full-registry sweep actually costs (the JEI
 * {@code registerRecipes} stall), and whether today's recipe-derived values are
 * stable at all.
 *
 * <p>It sweeps every item twice — forward, then (after dropping the memo) in
 * reverse registry order — and records both results per item. The DFS valuator
 * memoizes some incomplete misses at depth 0, so a sweep can poison later items;
 * if the two passes disagree, an item's FU value (or whether it is printable)
 * depends on registry iteration order, which shifts whenever the modlist changes.
 * A clean run reports zero disagreements.
 *
 * <p>The output JSON is also the baseline any candidate valuator gets diffed
 * against before we accept an economy change.
 *
 * <p>Runs off-thread (the sweep can take minutes on a large pack) and writes to
 * {@code <world>/mc3dprint/fu-dump.json}.
 */
public final class FuDumpCommand {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** One item's value under each sweep direction; null = unvalued that pass. */
    private record Row(String id, FuValue forward, FuValue reverse) {
        boolean disagrees() {
            if (forward == null || reverse == null) {
                return forward != reverse;
            }
            return forward.fu() != reverse.fu() || forward.tier() != reverse.tier();
        }
    }

    private FuDumpCommand() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("mc3dprint")
                .then(Commands.literal("dumpfu")
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
        Path out = source.getServer().getWorldPath(LevelResource.ROOT)
                .resolve("mc3dprint").resolve("fu-dump.json");
        source.sendSuccess(() -> Component.literal(
                "MC3DPrint: sweeping FU values... this can take minutes on a large pack. "
                        + "You'll get a message when it finishes."), true);

        // Snapshot the item list on the server thread; the sweep itself only touches
        // FuValueRegistry (synchronized) and is safe off-thread, same as the existing
        // curated-blueprint cache warm.
        List<Item> items = new ArrayList<>();
        BuiltInRegistries.ITEM.forEach(items::add);

        Thread worker = new Thread(() -> {
            try {
                Result result = sweep(items);
                write(out, result);
                source.getServer().execute(() -> source.sendSuccess(() -> Component.literal(
                        "MC3DPrint: FU dump written to " + out.toAbsolutePath()
                                + " (" + result.rows.size() + " items, "
                                + result.forwardMs + " ms cold sweep, "
                                + result.disagreements + " order-dependent)"), true));
            } catch (Exception e) {
                LOGGER.error("FU dump failed", e);
                source.getServer().execute(() -> source.sendFailure(
                        Component.literal("MC3DPrint: FU dump failed — see the log.")));
            }
        }, "mc3dprint-fu-dump");
        worker.setDaemon(true);
        worker.start();
        return 1;
    }

    private record Result(List<Row> rows, long forwardMs, long reverseMs,
                          int forwardValued, int reverseValued, int disagreements) {}

    private static Result sweep(List<Item> items) {
        // Cold forward pass. invalidate() first so a warm cache from normal play
        // (tooltips, the creative tab, JEI) doesn't hide the real cost.
        FuValueRegistry.invalidate();
        long t0 = System.nanoTime();
        Map<Item, FuValue> forward = pass(items, false);
        long forwardMs = (System.nanoTime() - t0) / 1_000_000L;

        FuValueRegistry.invalidate();
        t0 = System.nanoTime();
        Map<Item, FuValue> reverse = pass(items, true);
        long reverseMs = (System.nanoTime() - t0) / 1_000_000L;

        List<Row> rows = new ArrayList<>(items.size());
        int disagreements = 0;
        for (Item item : items) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (id == null) {
                continue;
            }
            Row row = new Row(id.toString(), forward.get(item), reverse.get(item));
            rows.add(row);
            if (row.disagrees()) {
                disagreements++;
            }
        }
        rows.sort((a, b) -> a.id().compareTo(b.id()));
        return new Result(rows, forwardMs, reverseMs, forward.size(), reverse.size(), disagreements);
    }

    private static Map<Item, FuValue> pass(List<Item> items, boolean reversed) {
        Map<Item, FuValue> out = new LinkedHashMap<>();
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(reversed ? items.size() - 1 - i : i);
            Optional<FuValue> value;
            try {
                value = FuValueRegistry.valueOf(new ItemStack(item));
            } catch (RuntimeException e) {
                continue; // a hostile modded item shouldn't abort the whole dump
            }
            value.ifPresent(v -> out.put(item, v));
        }
        return out;
    }

    private static void write(Path out, Result r) throws IOException {
        TreeMap<String, Row> byId = new TreeMap<>();
        for (Row row : r.rows()) {
            byId.put(row.id(), row);
        }

        StringBuilder sb = new StringBuilder(byId.size() * 96);
        sb.append("{\n");
        sb.append("  \"itemCount\": ").append(r.rows().size()).append(",\n");
        sb.append("  \"forwardMs\": ").append(r.forwardMs()).append(",\n");
        sb.append("  \"reverseMs\": ").append(r.reverseMs()).append(",\n");
        sb.append("  \"forwardValued\": ").append(r.forwardValued()).append(",\n");
        sb.append("  \"reverseValued\": ").append(r.reverseValued()).append(",\n");
        sb.append("  \"orderDependentItems\": ").append(r.disagreements()).append(",\n");
        sb.append("  \"values\": {\n");
        int i = 0;
        int n = byId.size();
        for (Row row : byId.values()) {
            sb.append("    \"").append(row.id()).append("\": {");
            appendValue(sb, "fu", "tier", row.forward());
            sb.append(", ");
            appendValue(sb, "reverseFu", "reverseTier", row.reverse());
            sb.append('}').append(++i < n ? ",\n" : "\n");
        }
        sb.append("  }\n}\n");

        Files.createDirectories(out.getParent());
        Files.writeString(out, sb.toString());
        LOGGER.info("Wrote FU dump: {} items, cold sweep {} ms, {} order-dependent",
                r.rows().size(), r.forwardMs(), r.disagreements());
    }

    private static void appendValue(StringBuilder sb, String fuKey, String tierKey, FuValue v) {
        if (v == null) {
            sb.append('"').append(fuKey).append("\": null, \"").append(tierKey).append("\": null");
            return;
        }
        sb.append('"').append(fuKey).append("\": ").append(v.fu())
          .append(", \"").append(tierKey).append("\": ").append(v.tier());
    }
}
