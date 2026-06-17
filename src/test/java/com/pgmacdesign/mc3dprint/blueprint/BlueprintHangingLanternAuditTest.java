package com.pgmacdesign.mc3dprint.blueprint;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Diagnostic + always-on gate for the <b>unsupported hanging lantern</b> bug class
 * surfaced in playtesting (japanese_pagoda — all 16 eave lanterns popped off the
 * instant the build finished printing). A {@code lantern[hanging=true]} (or
 * {@code soul_lantern[hanging=true]}) needs the block DIRECTLY ABOVE it to present a
 * sturdy down-face it can hang from — a full solid block, or a chain (chains float
 * AND support a lantern's centre). A top-half stair or a top slab does NOT present a
 * sturdy down-face, so the printer's {@code UPDATE_SUPPRESS_DROPS} flag places the
 * lantern anyway, but it pops off as a dropped item on the next block update — a
 * SILENT failure the GameTests don't catch (they place without a canSurvive recheck).
 *
 * <p>This is the same attachment-support family as the unbacked-ladder audit
 * ({@link BlueprintLadderSupportAuditTest}); the recurring root cause is hanging a
 * fixture off a stair/slab whose visible underside is NOT a sturdy face. The safe
 * pattern (used by the pagoda fix) is a CHAIN between the eave/ceiling and the lantern.
 *
 * <p>Reads palette strings directly (no Forge registry / server). A hanging lantern
 * attaches at the block above via CENTER support — the block's downward face only has
 * to cover the centre column. So this flags exactly the cases that genuinely leave the
 * centre uncovered and DO break: <b>air/OOB</b> above, a <b>top-half stair</b>, or a
 * <b>top slab</b>. Everything else passes — full blocks, chains, AND fences/walls (their
 * centre post covers the support column, so vanilla lets a lantern hang off them too).
 *
 * <p>Runs as an ALWAYS-ON hard gate on every {@code ./gradlew build}: zero tolerance,
 * no allowlist. Any unsupported hanging lantern fails the build. The report is still
 * written to {@code build/blueprint-hanging-lantern-audit.txt} for inspection.
 */
class BlueprintHangingLanternAuditTest {

    private static final Path SOURCE_DIR =
            Path.of("src", "main", "resources", "data", "mc3dprint", "blueprints");
    private static final Path OUTPUT = Path.of("build", "blueprint-hanging-lantern-audit.txt");

    @Test
    void auditHangingLanterns() throws IOException {
        List<Path> files;
        try (var stream = Files.list(SOURCE_DIR)) {
            files = stream.filter(p -> p.toString().endsWith(".blueprint")).sorted().toList();
        }

        List<String> flagged = new ArrayList<>();
        List<String> all = new ArrayList<>();
        int lanternCount = 0;

        for (Path file : files) {
            String name = file.getFileName().toString().replace(".blueprint", "");
            Blueprint bp = readBlueprint(file);
            int sx = bp.sizeX(), sy = bp.sizeY(), sz = bp.sizeZ();

            for (int y = 0; y < sy; y++) {
                for (int z = 0; z < sz; z++) {
                    for (int x = 0; x < sx; x++) {
                        BlueprintBlockState cell = bp.get(x, y, z);
                        if (cell == null) continue;
                        String id = cell.blockId();
                        boolean isLantern = "minecraft:lantern".equals(id) || "minecraft:soul_lantern".equals(id);
                        if (!isLantern || !"true".equals(cell.properties().getOrDefault("hanging", "false"))) {
                            continue;
                        }
                        lanternCount++;
                        // support cell = directly ABOVE (a hanging lantern hangs from it)
                        String backing;
                        boolean supported;
                        if (y + 1 >= sy) {
                            backing = "OOB(sky)";
                            supported = false;
                        } else {
                            BlueprintBlockState above = bp.get(x, y + 1, z);
                            if (above == null || above.isAir()) {
                                backing = "AIR";
                                supported = false;
                            } else {
                                backing = above.blockId();
                                supported = hasSturdyDownFace(above);
                            }
                        }
                        String line = String.format("%-30s %s (%d,%d,%d) -> above %s",
                                name, id.replace("minecraft:", ""), x, y, z, backing);
                        all.add(line);
                        if (!supported) flagged.add("[UNSUPPORTED] " + line);
                    }
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Hanging-lantern support audit — ").append(lanternCount)
                .append(" hanging lanterns across ").append(files.size()).append(" builds\n\n");
        sb.append("=== FLAGGED — air/non-sturdy above (").append(flagged.size()).append(") ===\n");
        flagged.forEach(l -> sb.append(l).append('\n'));
        sb.append("\n=== ALL HANGING LANTERNS (").append(all.size()).append(") ===\n");
        all.forEach(l -> sb.append(l).append('\n'));
        Files.createDirectories(OUTPUT.getParent());
        Files.writeString(OUTPUT, sb.toString());
        System.out.println("[HangingLanternAudit] " + flagged.size() + " flagged / "
                + lanternCount + " hanging lanterns -> " + OUTPUT.toAbsolutePath());

        // Hard gate: zero tolerance — any air/non-sturdy-backed hanging lantern pops off
        // after printing (a silent failure). No allowlist; hang it off a chain or a full
        // block instead.
        Assertions.assertTrue(flagged.isEmpty(),
                "Unsupported hanging lantern(s) found — hang off a chain or a full block:\n"
                        + String.join("\n", flagged));
    }

    /**
     * Does the block above a hanging lantern cover the centre support column (so the
     * lantern stays)? A hanging lantern uses CENTER support, so only the cases that
     * genuinely leave the centre uncovered fail: a TOP-half stair (its lower half is
     * the offset step, not the centre) and a TOP slab (nothing in the lower half).
     * Everything else — full blocks, chains, fences/walls (centre post), bottom slabs,
     * bottom-half stairs — covers the centre and holds the lantern.
     */
    private static boolean hasSturdyDownFace(BlueprintBlockState above) {
        String id = above.blockId();
        Map<String, String> p = above.properties();
        if (id.contains("_stairs")) {
            return !"top".equals(p.getOrDefault("half", "bottom")); // top-half = no centre below
        }
        if (id.contains("_slab")) {
            return !"top".equals(p.getOrDefault("type", "bottom")); // top slab = empty lower half
        }
        return true;                                                // full block / chain / fence / wall / …
    }

    private static Blueprint readBlueprint(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            CompoundTag tag = NbtIo.read(new DataInputStream(new GZIPInputStream(in)));
            return BlueprintSerializer.read(tag);
        }
    }
}
