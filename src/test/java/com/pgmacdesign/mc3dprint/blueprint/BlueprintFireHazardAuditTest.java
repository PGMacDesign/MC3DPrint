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
 * Diagnostic audit for the <b>fire-hazard</b> bug class surfaced in playtesting
 * (tavern_inn's caged-lava hearth ignited the surrounding spruce building and burned it
 * down). A raw fire source — {@code minecraft:lava}, {@code minecraft:fire},
 * {@code minecraft:soul_fire} — sitting near flammable wood/wool/leaves will, after the
 * structure prints, ignite the flammable block and spread fire through the build. The
 * GameTests don't catch this because they only verify block placement, not fire ticking.
 *
 * <p><b>Ignition range:</b> lava ignites flammables a couple of cells away even across an
 * air gap, so this flags any fire source with a flammable block within Chebyshev distance
 * &le; {@value #IGNITION_RANGE} (max of |dx|,|dy|,|dz|) — which is exactly what burned the
 * tavern even though the lava was caged.
 *
 * <p><b>Fire sources</b> (the danger) are ONLY the three blocks that actively spread fire:
 * raw {@code lava}, {@code fire}, {@code soul_fire}. Blocks that look hot but do NOT spread
 * fire — {@code lava_cauldron}, {@code campfire}, {@code soul_campfire}, {@code magma_block},
 * any {@code *torch}, {@code lantern}, {@code glowstone}, {@code shroomlight} — are NOT
 * treated as sources (the safe substitutes a flammable build should use instead of raw lava).
 *
 * <p><b>Flammable</b> is a conservative, clearly-commented id rule set: any OVERWORLD wood
 * species prefix (catches planks/logs/stairs/slabs/fences/doors/signs/…) plus a fixed list
 * of specific flammables (leaves, wool, carpet, bed, banner, bookshelf, hay, tnt, coal_block,
 * scaffolding, bamboo, vines, composter/barrel/lectern, beehive/bee_nest, ladder, grass/ferns,
 * saplings, flowers, sweet_berry_bush, …). Nether wood ({@code crimson_*}/{@code warped_*}) and
 * nether plants are EXCLUDED — they don't burn. When unsure, a block is treated non-flammable
 * to avoid false positives.
 *
 * <p>Reads palette strings directly (no Forge registry / running server). This is now an
 * ALWAYS-ON GATE: all curated builds are clean, so the test FAILS if any build prints a raw
 * fire source within ignition range of a flammable block. It always writes the full report to
 * {@code build/blueprint-fire-hazard-audit.txt} regardless of pass/fail. The
 * {@code -DauditFireHazard=true} system property is no longer required (it remains harmless if
 * set); run it directly with:
 *
 * <pre>
 *   ./gradlew test --tests *BlueprintFireHazardAuditTest*
 * </pre>
 */
class BlueprintFireHazardAuditTest {

    private static final Path SOURCE_DIR =
            Path.of("src", "main", "resources", "data", "mc3dprint", "blueprints");
    private static final Path OUTPUT = Path.of("build", "blueprint-fire-hazard-audit.txt");

    /** Chebyshev distance (max of |dx|,|dy|,|dz|) within which lava/fire ignites a flammable. */
    private static final int IGNITION_RANGE = 2;

    /**
     * Builds whose raw lava is an INTENTIONAL, contained kill — not a hazard. iron_farm
     * runs a LAVA BLADE held up by oak dam signs (a sign is the only block that blocks
     * fluid yet passes mobs+items, so the design needs them). The signs sit 1 cell from
     * the lava (so the audit's Chebyshev<=2 flags them), but the nearest AIR to the lava
     * is 2 cells away — beyond fire's ~1-block ignition reach — so the signs don't actually
     * ignite. This is the same reason real lava-blade farms hold lava on signs. The lava is
     * otherwise caged in stone with no building material nearby.
     */
    private static final java.util.Set<String> INTENTIONAL_LAVA = java.util.Set.of("iron_farm");

    /**
     * The ONLY blocks that actively spread fire to neighbouring blocks. Matched by exact id.
     * Deliberately excludes lava_cauldron, campfire/soul_campfire, magma_block, torches,
     * lanterns, glowstone, shroomlight — none of those ignite flammable blocks, so a build
     * can use them freely near wood.
     */
    private static boolean isFireSource(String id) {
        return "minecraft:lava".equals(id)
                || "minecraft:fire".equals(id)
                || "minecraft:soul_fire".equals(id);
    }

    /**
     * OVERWORLD wood species prefixes. Any id starting with one of these is flammable —
     * this catches the whole family (planks, log, wood, stripped_*, stairs, slab, fence,
     * fence_gate, door, trapdoor, sign, hanging_sign, button, pressure_plate, sapling,
     * leaves, boat). Nether species (crimson_/warped_) are intentionally NOT here — nether
     * wood does not burn.
     */
    private static final String[] WOOD_PREFIXES = {
            "minecraft:oak_", "minecraft:spruce_", "minecraft:birch_", "minecraft:jungle_",
            "minecraft:acacia_", "minecraft:dark_oak_", "minecraft:mangrove_",
            "minecraft:cherry_", "minecraft:bamboo_"
    };

    /**
     * Suffix matches for specific flammables that aren't covered by a wood prefix
     * (leaves can be e.g. azalea_leaves; wools/carpets/beds/banners are dyed_*; signs use
     * wood prefixes already). Matched as id.endsWith(...).
     */
    private static final String[] FLAMMABLE_SUFFIXES = {
            "_leaves", "_wool", "_carpet", "_bed", "_banner", "_sapling"
    };

    /**
     * Specific flammable block ids (exact match) that are neither a wood-prefix family nor a
     * suffix family. Vanilla overworld-burnable decor/plants. Nether plants
     * (warped/crimson fungus, roots, nether sprouts, weeping/twisting vines) are NOT here.
     */
    private static final java.util.Set<String> FLAMMABLE_EXACT = java.util.Set.of(
            "minecraft:bookshelf", "minecraft:chiseled_bookshelf",
            "minecraft:hay_block", "minecraft:target", "minecraft:dried_kelp_block",
            "minecraft:tnt", "minecraft:coal_block", "minecraft:scaffolding",
            "minecraft:bamboo", "minecraft:bamboo_block",
            "minecraft:vine", "minecraft:composter", "minecraft:barrel",
            "minecraft:cartography_table", "minecraft:fletching_table",
            "minecraft:lectern", "minecraft:beehive", "minecraft:bee_nest",
            "minecraft:ladder", "minecraft:grass", "minecraft:tall_grass",
            "minecraft:fern", "minecraft:large_fern", "minecraft:dead_bush",
            "minecraft:sweet_berry_bush",
            // small flowers (overworld; all burn)
            "minecraft:dandelion", "minecraft:poppy", "minecraft:blue_orchid",
            "minecraft:allium", "minecraft:azure_bluet", "minecraft:red_tulip",
            "minecraft:orange_tulip", "minecraft:white_tulip", "minecraft:pink_tulip",
            "minecraft:oxeye_daisy", "minecraft:cornflower", "minecraft:lily_of_the_valley",
            "minecraft:wither_rose", "minecraft:sunflower", "minecraft:lilac",
            "minecraft:rose_bush", "minecraft:peony"
    );

    private static boolean isFlammable(String id) {
        for (String p : WOOD_PREFIXES) {
            if (id.startsWith(p)) {
                // Guard: a crimson_/warped_ id can never start with an overworld prefix, but
                // be explicit that nether wood is excluded.
                return true;
            }
        }
        for (String s : FLAMMABLE_SUFFIXES) {
            if (id.endsWith(s)) return true;
        }
        return FLAMMABLE_EXACT.contains(id);
    }

    @Test
    void auditFireHazard() throws IOException {
        List<Path> files;
        try (var stream = Files.list(SOURCE_DIR)) {
            files = stream.filter(p -> p.toString().endsWith(".blueprint")).sorted().toList();
        }

        List<String> flagged = new ArrayList<>();
        int fireSourceCells = 0;
        int flaggedSources = 0;

        for (Path file : files) {
            String name = file.getFileName().toString().replace(".blueprint", "");
            Blueprint bp = readBlueprint(file);
            int sx = bp.sizeX(), sy = bp.sizeY(), sz = bp.sizeZ();

            for (int y = 0; y < sy; y++) {
                for (int z = 0; z < sz; z++) {
                    for (int x = 0; x < sx; x++) {
                        BlueprintBlockState cell = bp.get(x, y, z);
                        if (cell == null || !isFireSource(cell.blockId())) continue;
                        fireSourceCells++;

                        // Scan the Chebyshev cube of radius IGNITION_RANGE for flammables.
                        int inRange = 0;
                        int nearestDist = Integer.MAX_VALUE;
                        String nearestId = null;
                        int nx = 0, ny = 0, nz = 0;
                        for (int dy = -IGNITION_RANGE; dy <= IGNITION_RANGE; dy++) {
                            for (int dz = -IGNITION_RANGE; dz <= IGNITION_RANGE; dz++) {
                                for (int dx = -IGNITION_RANGE; dx <= IGNITION_RANGE; dx++) {
                                    if (dx == 0 && dy == 0 && dz == 0) continue;
                                    int fx = x + dx, fy = y + dy, fz = z + dz;
                                    if (fx < 0 || fx >= sx || fy < 0 || fy >= sy
                                            || fz < 0 || fz >= sz) continue;
                                    BlueprintBlockState other = bp.get(fx, fy, fz);
                                    if (other == null || other.isAir()) continue;
                                    if (!isFlammable(other.blockId())) continue;
                                    inRange++;
                                    int cheb = Math.max(Math.abs(dx),
                                            Math.max(Math.abs(dy), Math.abs(dz)));
                                    if (cheb < nearestDist) {
                                        nearestDist = cheb;
                                        nearestId = other.blockId();
                                        nx = fx; ny = fy; nz = fz;
                                    }
                                }
                            }
                        }

                        if (inRange > 0 && !INTENTIONAL_LAVA.contains(name)) {
                            flaggedSources++;
                            flagged.add(String.format(
                                    "%-30s %s at (%d,%d,%d) — flammable %s at (%d,%d,%d) dist=%d  [%d flammable(s) in range]",
                                    name, cell.blockId(), x, y, z,
                                    nearestId, nx, ny, nz, nearestDist, inRange));
                        }
                    }
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Fire-hazard audit — ").append(fireSourceCells)
                .append(" raw fire-source cell(s) across ").append(files.size())
                .append(" builds; ignition range (Chebyshev) <= ").append(IGNITION_RANGE)
                .append("\n\n");
        sb.append("=== FLAGGED — fire source with flammable in ignition range (")
                .append(flaggedSources).append(") ===\n");
        flagged.forEach(l -> sb.append(l).append('\n'));
        if (flagged.isEmpty()) {
            sb.append("(none)\n");
        }
        Files.createDirectories(OUTPUT.getParent());
        Files.writeString(OUTPUT, sb.toString());
        System.out.println("[FireHazardAudit] " + flaggedSources + " flagged / "
                + fireSourceCells + " fire-source cells across " + files.size()
                + " builds -> " + OUTPUT.toAbsolutePath());

        // ALWAYS-ON GATE: every curated build must be fire-safe. A non-empty flagged list
        // means some build prints a raw lava/fire/soul_fire source within ignition range of
        // a flammable block — it would ignite and burn down after printing (the tavern_inn
        // bug). Fail the build; the full report (above) lists every offending cell. Fix the
        // forge/hearth (e.g. magma_block or lava_cauldron) and regenerate the blueprint.
        Assertions.assertTrue(flagged.isEmpty(),
                "Fire hazard(s) found: " + flaggedSources + " raw fire-source cell(s) within "
                        + "Chebyshev <= " + IGNITION_RANGE + " of a flammable block. See "
                        + OUTPUT.toAbsolutePath() + "\n"
                        + String.join("\n", flagged));
    }

    private static Blueprint readBlueprint(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            CompoundTag tag = NbtIo.read(new DataInputStream(new GZIPInputStream(in)));
            return BlueprintSerializer.read(tag);
        }
    }
}
