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
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * Diagnostic audit for the <b>embedded bed</b> bug class surfaced in playtesting
 * (elven_treehouse — bed reported "embedded in the wall"; this has happened more
 * than once). A bed occupies two cells: a {@code *_bed[part=head]} block and an
 * adjacent {@code part=foot}; the bed's {@code facing} points from foot toward
 * head (facing=north ⇒ head is north / z-1 of the foot, etc.). A usable bed needs
 * the player to be able to stand on/beside it with headroom above it. A bed
 * "embedded in a wall" has solid blocks where that clearance should be — solid
 * directly above the mattress (no headroom, buried under a wall/ceiling), or solid
 * full blocks pinching both long sides (wedged inside a wall).
 *
 * <p>Reads palette strings directly (no Forge registry / running server), so the
 * "solid" test is a conservative id-denylist heuristic — pass-throughs (torches,
 * signs, banners, carpets, ladders, trapdoors, plants, top slabs above) are NOT
 * solid; glass / leaves / planks / logs / wool / stairs / full blocks ARE. Review
 * flags, don't treat as gospel. Out-of-bounds neighbours (bed on a build edge,
 * open to world terrain) count as clearance-OK, not solid.
 *
 * <p>Calibrated against elven_treehouse: its light_blue_bed is foot(3,6,2) /
 * head(3,6,3) facing=south. blue_stained_glass sits directly above the foot
 * (no headroom) and the foot is pinched by birch_log + smooth_quartz on its two
 * long (±x) sides — it trips both signals.
 *
 * <p>This runs as an ALWAYS-ON hard gate on every {@code ./gradlew build}. One build is
 * allowlisted: {@code iron_farm}, whose three villager beds are intentionally tight under
 * the spawn-platform glass (by design). Any OTHER build with a flagged bed fails the build.
 * The full report is still written to {@code build/blueprint-bed-clearance-audit.txt}.
 */
class BlueprintBedClearanceAuditTest {

    private static final Path SOURCE_DIR =
            Path.of("src", "main", "resources", "data", "mc3dprint", "blueprints");
    private static final Path OUTPUT = Path.of("build", "blueprint-bed-clearance-audit.txt");

    /**
     * Builds whose flagged beds are tight <b>by design</b> and so are excluded from the
     * hard gate. {@code iron_farm}'s three villager beds sit intentionally pinched under
     * the spawn-platform glass — a functional iron-farm layout, not a navigability defect.
     */
    private static final Set<String> ALLOWLISTED_BUILDS = Set.of("iron_farm");

    // ── Heuristic constants ────────────────────────────────────────────────
    // A neighbour counts as SOLID (blocks clearance) unless its id contains one
    // of these substrings. These are the pass-throughs a player / sleeper can
    // coexist with: light & decoration that hangs or sits flush, plants, and
    // thin / partial blocks that don't pinch or smother a mattress. Everything
    // else — glass, panes(? see note), leaves, planks, logs, wool, stairs, full
    // blocks — is treated as SOLID and so as "embedding" the bed.
    private static final String[] PASS_THROUGH = {
            "torch", "_sign", "banner", "carpet", "ladder", "trapdoor",
            "lantern", "chain", "candle", "_button", "lever", "pressure_plate",
            "tripwire", "string", "redstone_wire", "rail", "vine", "lever",
            "_bed",            // the other half of a (this or adjacent) bed, never an obstruction
            "flower", "sapling", "mushroom", "fern", "grass", "roots",
            "sprouts", "lily", "dripleaf", "azalea", "_bush", "seagrass",
            "kelp", "potted_", "flower_pot", "sea_pickle", "amethyst_cluster",
            "_bud", "cobweb", "snow[", "minecraft:snow",  // snow layer, not snow_block
            "end_rod", "scaffolding", "bell", "campfire", "head", "skull",
            // top slabs / top trapdoors above a bed leave the head-space clear;
            // a bottom slab directly above is borderline — we err toward NOT
            // flagging slabs (type handled below for the long-side pinch).
            "_slab"
    };

    @Test
    void auditBeds() throws IOException {
        List<Path> files;
        try (var stream = Files.list(SOURCE_DIR)) {
            files = stream.filter(p -> p.toString().endsWith(".blueprint")).sorted().toList();
        }

        List<String> flagged = new ArrayList<>();
        List<String> violations = new ArrayList<>(); // flagged beds NOT on the build allowlist
        List<String> all = new ArrayList<>();
        int bedCount = 0;

        for (Path file : files) {
            String name = file.getFileName().toString().replace(".blueprint", "");
            Blueprint bp = readBlueprint(file);
            int sx = bp.sizeX(), sy = bp.sizeY(), sz = bp.sizeZ();

            for (int y = 0; y < sy; y++) {
                for (int z = 0; z < sz; z++) {
                    for (int x = 0; x < sx; x++) {
                        BlueprintBlockState cell = bp.get(x, y, z);
                        if (cell == null) continue;
                        if (!cell.blockId().endsWith("_bed")) continue;
                        if (!"head".equals(cell.properties().get("part"))) continue;

                        bedCount++;
                        String facing = cell.properties().getOrDefault("facing", "north");

                        // facing points foot -> head. foot = cell OPPOSITE facing from head.
                        // long-side axis is perpendicular to the head/foot (facing) axis.
                        int fx = x, fz = z;             // foot coords (same y)
                        int[] sideA, sideB;             // (dx,dz) offsets for the two long sides
                        switch (facing) {
                            case "north" -> { fz = z + 1; sideA = new int[]{-1, 0}; sideB = new int[]{1, 0}; }
                            case "south" -> { fz = z - 1; sideA = new int[]{-1, 0}; sideB = new int[]{1, 0}; }
                            case "east"  -> { fx = x - 1; sideA = new int[]{0, -1}; sideB = new int[]{0, 1}; }
                            case "west"  -> { fx = x + 1; sideA = new int[]{0, -1}; sideB = new int[]{0, 1}; }
                            default      -> { fz = z + 1; sideA = new int[]{-1, 0}; sideB = new int[]{1, 0}; }
                        }

                        String reason = null;

                        // (1) No headroom: solid block directly above EITHER half.
                        boolean solidAboveHead = isSolid(bp, x, y + 1, z, sx, sy, sz);
                        boolean solidAboveFoot = isSolid(bp, fx, y + 1, fz, sx, sy, sz);
                        if (solidAboveHead && solidAboveFoot) {
                            reason = "solid above both halves (buried, no headroom)";
                        } else if (solidAboveHead) {
                            reason = "solid above head (no headroom)";
                        } else if (solidAboveFoot) {
                            reason = "solid above foot (no headroom)";
                        }

                        // (2) Wedged in a wall: BOTH halves pinched by solid full
                        //     blocks on BOTH long sides (the sides perpendicular to
                        //     the head->foot axis).
                        boolean headPinched =
                                isSolidPinch(bp, x, y, z, sideA, sx, sy, sz)
                                        && isSolidPinch(bp, x, y, z, sideB, sx, sy, sz);
                        boolean footPinched =
                                isSolidPinch(bp, fx, y, fz, sideA, sx, sy, sz)
                                        && isSolidPinch(bp, fx, y, fz, sideB, sx, sy, sz);
                        if (headPinched && footPinched) {
                            reason = append(reason, "wedged in wall (both long sides solid on both halves)");
                        }

                        String line = String.format(
                                "%-30s bed head(%d,%d,%d) foot(%d,%d,%d) facing=%s",
                                name, x, y, z, fx, y, fz, facing);
                        all.add(line);
                        if (reason != null) {
                            String flag = "[EMBEDDED] " + line + " — " + reason;
                            flagged.add(flag);
                            if (!ALLOWLISTED_BUILDS.contains(name)) {
                                violations.add(flag);
                            }
                        }
                    }
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Bed clearance audit — ").append(bedCount).append(" beds across ")
                .append(files.size()).append(" builds\n\n");
        sb.append("=== FLAGGED — embedded / no clearance (").append(flagged.size()).append(") ===\n");
        flagged.forEach(l -> sb.append(l).append('\n'));
        sb.append("\n=== ALL BEDS (").append(all.size()).append(") ===\n");
        all.forEach(l -> sb.append(l).append('\n'));
        Files.createDirectories(OUTPUT.getParent());
        Files.writeString(OUTPUT, sb.toString());
        System.out.println("[BedClearanceAudit] " + flagged.size() + " flagged ("
                + violations.size() + " non-allowlisted) / " + bedCount
                + " beds -> " + OUTPUT.toAbsolutePath());

        // Hard gate: an embedded/no-clearance bed is a navigability defect. iron_farm's
        // villager beds are intentionally tight (allowlisted); any OTHER flagged build fails.
        Assertions.assertTrue(violations.isEmpty(),
                "Embedded/no-clearance bed(s) outside the allowlist " + ALLOWLISTED_BUILDS
                        + " — give the bed headroom and clear long sides:\n"
                        + String.join("\n", violations));
    }

    /** True if (x,y,z) is in bounds and holds a SOLID (non-pass-through) block. OOB / air ⇒ false. */
    private static boolean isSolid(Blueprint bp, int x, int y, int z, int sx, int sy, int sz) {
        if (x < 0 || x >= sx || y < 0 || y >= sy || z < 0 || z >= sz) return false; // open to world
        BlueprintBlockState cell = bp.get(x, y, z);
        if (cell == null || cell.isAir()) return false;
        return !isPassThrough(cell.blockId());
    }

    /**
     * Pinch test for a long-side neighbour. Same SOLID rule as {@link #isSolid},
     * but a side slab only pinches when it's a full-width vertical face — a slab
     * is partial, so it does NOT count as a wall pinch here (it's already a
     * pass-through). The split exists so the headroom and pinch heuristics can
     * diverge later if needed; today they share the denylist.
     */
    private static boolean isSolidPinch(Blueprint bp, int x, int y, int z, int[] off,
                                        int sx, int sy, int sz) {
        return isSolid(bp, x + off[0], y, z + off[1], sx, sy, sz);
    }

    private static boolean isPassThrough(String blockId) {
        for (String s : PASS_THROUGH) {
            if (blockId.contains(s)) return true;
        }
        return false;
    }

    private static String append(String reason, String more) {
        return reason == null ? more : reason + "; " + more;
    }

    private static Blueprint readBlueprint(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            CompoundTag tag = NbtIo.read(new DataInputStream(new GZIPInputStream(in)));
            return BlueprintSerializer.read(tag);
        }
    }
}
