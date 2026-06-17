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
import java.util.zip.GZIPInputStream;

/**
 * Diagnostic audit for the <b>floating gravity-block</b> bug class. Minecraft's
 * {@link net.minecraft.world.level.block.FallingBlock} family (sand, gravel,
 * concrete powder, anvils, the dragon egg, pointed dripstone, the suspicious
 * archaeology blocks) becomes a falling entity the instant a block update fires
 * with air directly beneath it. The printer places these statefully, so a curated
 * build with such a block over an AIR cell prints visibly broken: the block drops
 * the moment the player (or any neighbour update) touches it, leaving a hole in
 * the build. Unlike the ladder pop-off this is HIGH precision — air directly below
 * a falling block is unambiguous — so it runs as a zero-tolerance gate.
 *
 * <p>For every gravity-affected cell whose {@code y-1} neighbour is AIR/empty this
 * FLAGS the build. Out-of-bounds below ({@code y == 0}) is NOT flagged: at print
 * time that cell rests on world terrain under the build, so it is supported. Reads
 * palette strings directly (no Forge registry / running server) — the falling-block
 * id list is the vanilla {@code FallingBlock} set, matched by id, so the precision
 * is exact (no heuristic guessing like the ladder/reachability audits).
 *
 * <p>Runs as an ALWAYS-ON hard gate on every {@code ./gradlew build}: any floating
 * gravity block fails the build. Writes {@code build/blueprint-gravity-audit.txt}
 * for inspection.
 *
 * <p><b>Note:</b> {@code scaffolding} is deliberately excluded — it is a
 * {@code FallingBlock} subclass in code but does NOT fall when unsupported (it has
 * its own bottom/distance survival logic and stands on its own column), so flagging
 * it would be a false positive.
 */
class BlueprintGravitySupportAuditTest {

    private static final Path SOURCE_DIR =
            Path.of("src", "main", "resources", "data", "mc3dprint", "blueprints");
    private static final Path OUTPUT = Path.of("build", "blueprint-gravity-audit.txt");

    /**
     * Vanilla {@link net.minecraft.world.level.block.FallingBlock}-family block ids.
     * These fall as an entity the moment a block update fires with air below them.
     * Matched by exact id (after a couple of suffix/family patterns) so there is no
     * heuristic ambiguity. {@code scaffolding} is intentionally omitted (see class doc).
     */
    private static boolean isGravityBlock(String id) {
        switch (id) {
            case "minecraft:sand":
            case "minecraft:red_sand":
            case "minecraft:gravel":
            case "minecraft:anvil":
            case "minecraft:chipped_anvil":
            case "minecraft:damaged_anvil":
            case "minecraft:dragon_egg":
            case "minecraft:pointed_dripstone":
            case "minecraft:suspicious_sand":
            case "minecraft:suspicious_gravel":
                return true;
            default:
                // *_concrete_powder (all 16 dye colours)
                return id.endsWith("_concrete_powder");
        }
    }

    @Test
    void auditGravitySupport() throws IOException {
        List<Path> files;
        try (var stream = Files.list(SOURCE_DIR)) {
            files = stream.filter(p -> p.toString().endsWith(".blueprint")).sorted().toList();
        }

        List<String> flagged = new ArrayList<>();
        List<String> all = new ArrayList<>();
        int gravityCount = 0;

        for (Path file : files) {
            String name = file.getFileName().toString().replace(".blueprint", "");
            Blueprint bp = readBlueprint(file);
            int sx = bp.sizeX(), sy = bp.sizeY(), sz = bp.sizeZ();

            for (int y = 0; y < sy; y++) {
                for (int z = 0; z < sz; z++) {
                    for (int x = 0; x < sx; x++) {
                        BlueprintBlockState cell = bp.get(x, y, z);
                        if (cell == null || cell.isAir()) continue;
                        if (!isGravityBlock(cell.blockId())) continue;
                        gravityCount++;
                        // y == 0: below is world terrain (out-of-bounds), so it rests — OK.
                        if (y == 0) continue;
                        BlueprintBlockState below = bp.get(x, y - 1, z);
                        boolean airBelow = below == null || below.isAir();
                        String line = String.format("%-34s (%d,%d,%d) %s",
                                name, x, y, z, cell.blockId());
                        all.add(line);
                        if (airBelow) {
                            flagged.add(String.format("%-34s (%d,%d,%d) %s floats (air below)",
                                    name, x, y, z, cell.blockId()));
                        }
                    }
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Gravity-block support audit — ").append(gravityCount)
                .append(" gravity cells across ").append(files.size()).append(" builds\n\n");
        sb.append("=== FLAGGED — floating gravity block, air directly below (")
                .append(flagged.size()).append(") ===\n");
        if (flagged.isEmpty()) {
            sb.append("(none — every gravity block rests on a solid cell or world terrain)\n");
        }
        flagged.forEach(l -> sb.append(l).append('\n'));
        sb.append("\n=== ALL GRAVITY CELLS with a cell below them (").append(all.size())
                .append(") ===\n");
        all.forEach(l -> sb.append(l).append('\n'));
        Files.createDirectories(OUTPUT.getParent());
        Files.writeString(OUTPUT, sb.toString());
        System.out.println("[GravityAudit] " + flagged.size() + " flagged / " + gravityCount
                + " gravity cells -> " + OUTPUT.toAbsolutePath());

        // Hard gate: zero tolerance — any gravity block over air falls on print (a visible
        // hole in the build). Fix the support block (or ground the floating decor).
        Assertions.assertTrue(flagged.isEmpty(),
                "Floating gravity block(s) found — add a supporting block below, or ground "
                        + "the floating decor:\n" + String.join("\n", flagged));
    }

    private static Blueprint readBlueprint(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            CompoundTag tag = NbtIo.read(new DataInputStream(new GZIPInputStream(in)));
            return BlueprintSerializer.read(tag);
        }
    }
}
