package com.pgmacdesign.mc3dprint.blueprint;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Diagnostic audit for two classes of bug surfaced in playtesting:
 * <ul>
 *   <li><b>Buried entrance</b> — a build sits on a raised solid foundation so its door
 *       sill ends up 2+ blocks above the base with no exterior step, making it
 *       impossible to walk into (e.g. taiga_spruce_longhouse on a cobble layer,
 *       snowy_alpine_chalet on two tiled-stone layers).</li>
 *   <li><b>Floating</b> — the whole structure is shifted one block up, leaving the
 *       y=0 layer nearly empty so it hovers above the ground (e.g. jungle_hut).</li>
 * </ul>
 *
 * <p>Not a guardrail (elevated stilt huts / towers with ladders are legitimately
 * high-doored), so this only REPORTS — gated on {@code -DauditFoundations=true} and
 * writes {@code build/blueprint-foundation-audit.txt}. Reads palette strings directly
 * (door {@code half=lower}, air), so no Forge registry / running server is needed.
 *
 * <pre>
 *   ./gradlew test --tests *BlueprintFoundationAuditTest* -DauditFoundations=true --rerun-tasks
 * </pre>
 */
class BlueprintFoundationAuditTest {

    private static final Path SOURCE_DIR =
            Path.of("src", "main", "resources", "data", "mc3dprint", "blueprints");
    private static final Path OUTPUT = Path.of("build", "blueprint-foundation-audit.txt");

    @Test
    @EnabledIfSystemProperty(named = "auditFoundations", matches = "true")
    void auditFoundations() throws IOException {
        List<Path> files;
        try (var stream = Files.list(SOURCE_DIR)) {
            files = stream.filter(p -> p.toString().endsWith(".blueprint")).sorted().toList();
        }

        List<String> flagged = new ArrayList<>();
        List<String> all = new ArrayList<>();

        for (Path file : files) {
            String name = file.getFileName().toString().replace(".blueprint", "");
            Blueprint bp = readBlueprint(file);
            int sx = bp.sizeX(), sy = bp.sizeY(), sz = bp.sizeZ();
            int footprint = Math.max(1, sx * sz);

            int doorSillY = Integer.MAX_VALUE;
            int[] layerFill = new int[sy];
            for (int y = 0; y < sy; y++) {
                for (int z = 0; z < sz; z++) {
                    for (int x = 0; x < sx; x++) {
                        BlueprintBlockState cell = bp.get(x, y, z);
                        if (cell == null || cell.isAir()) continue;
                        layerFill[y]++;
                        if (cell.blockId().endsWith("_door")
                                && "lower".equals(cell.properties().get("half"))) {
                            doorSillY = Math.min(doorSillY, y);
                        }
                    }
                }
            }

            double y0 = layerFill[0] / (double) footprint;
            double y1 = sy > 1 ? layerFill[1] / (double) footprint : 0;
            boolean hasDoor = doorSillY != Integer.MAX_VALUE;

            // Floating: base layer nearly empty while the layer above is substantially filled
            // (the structure was shifted up off the ground).
            boolean floating = y0 < 0.10 && y1 > 0.25;
            // Buried entrance: door sill 2+ above the base with a solid-ish slab beneath it.
            double fillBelowDoor = 0;
            if (hasDoor && doorSillY >= 2) {
                int below = 0;
                for (int y = 0; y < doorSillY; y++) below += layerFill[y];
                fillBelowDoor = below / (double) (footprint * doorSillY);
            }
            boolean buriedDoor = hasDoor && doorSillY >= 2 && fillBelowDoor > 0.6;

            String line = String.format(
                    "%-34s dims=%dx%dx%d  y0fill=%.2f y1fill=%.2f  doorSillY=%s%s",
                    name, sx, sy, sz, y0, y1,
                    hasDoor ? String.valueOf(doorSillY) : "-",
                    buriedDoor ? String.format("  fillBelowDoor=%.2f", fillBelowDoor) : "");
            all.add(line);
            if (floating) flagged.add("[FLOATING]   " + line);
            if (buriedDoor) flagged.add("[BURIED_DOOR] " + line);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== FLAGGED (").append(flagged.size()).append(") ===\n");
        flagged.forEach(l -> sb.append(l).append('\n'));
        sb.append("\n=== ALL (").append(all.size()).append(") ===\n");
        all.forEach(l -> sb.append(l).append('\n'));
        Files.createDirectories(OUTPUT.getParent());
        Files.writeString(OUTPUT, sb.toString());
        System.out.println("[FoundationAudit] " + flagged.size() + " flagged / " + all.size()
                + " builds -> " + OUTPUT.toAbsolutePath());
    }

    private static Blueprint readBlueprint(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            CompoundTag tag = NbtIo.read(new DataInputStream(new GZIPInputStream(in)));
            return BlueprintSerializer.read(tag);
        }
    }
}
