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
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * Diagnostic audit for the <b>unsupported plant</b> bug class surfaced in playtesting
 * (bee_apiary placed its flowers on oak_planks, so they pop into floating dropped items
 * the moment they receive a block update after printing). A soil-dependent plant needs a
 * specific block directly beneath it ({@code mayPlaceOn} in vanilla); if the cell at
 * y-1 isn't a valid support, the printer's {@code UPDATE_SUPPRESS_DROPS} flag places the
 * plant anyway, but it breaks off as a dropped item — a SILENT failure the GameTests
 * don't catch.
 *
 * <p>For every soil-dependent plant cell in every curated blueprint, this looks at the
 * block directly below (y-1) and FLAGS any whose support isn't valid per the hardcoded
 * vanilla-1.20.1 {@code mayPlaceOn} rule table below. Reads palette strings directly (no
 * Forge registry / running server is needed), so this is a conservative id-based
 * heuristic — review flags, don't treat as gospel. Anything not soil-dependent (potted
 * plants, small mushrooms, lily pads, kelp, vines, cocoa, chorus, dripleaf, …) is
 * deliberately EXCLUDED to avoid false positives.
 *
 * <pre>
 *   ./gradlew test --tests *BlueprintPlantSupportAuditTest* -DauditPlantSupport=true --rerun-tasks
 * </pre>
 */
class BlueprintPlantSupportAuditTest {

    private static final Path SOURCE_DIR =
            Path.of("src", "main", "resources", "data", "mc3dprint", "blueprints");
    private static final Path OUTPUT = Path.of("build", "blueprint-plant-support-audit.txt");

    // ---- Support-block sets (vanilla 1.20.1 mayPlaceOn) --------------------------------

    /** The "dirt family" — blocks that satisfy {@code DIRT} block-tag-style placement. */
    private static final Set<String> DIRT_FAMILY = Set.of(
            "minecraft:dirt", "minecraft:grass_block", "minecraft:podzol",
            "minecraft:coarse_dirt", "minecraft:mycelium", "minecraft:rooted_dirt",
            "minecraft:moss_block", "minecraft:mud", "minecraft:muddy_mangrove_roots");

    private static final String FARMLAND = "minecraft:farmland";

    /** Generic BushBlock plants: valid on DIRT_FAMILY or farmland. */
    private static final Set<String> DIRT_OR_FARMLAND = union(DIRT_FAMILY, Set.of(FARMLAND));

    /** Sand variants. */
    private static final Set<String> SAND = Set.of("minecraft:sand", "minecraft:red_sand");

    /** cactus: sand only. */
    private static final Set<String> CACTUS_SUPPORT = SAND;

    /** sugar_cane: dirt-family + sand (water-adjacency intentionally ignored here). */
    private static final Set<String> SUGAR_CANE_SUPPORT = union(DIRT_FAMILY, SAND);

    /** bamboo: dirt-family + sand + gravel. */
    private static final Set<String> BAMBOO_SUPPORT =
            union(DIRT_FAMILY, union(SAND, Set.of("minecraft:gravel")));

    /** nether_wart: soul_sand only. */
    private static final Set<String> NETHER_WART_SUPPORT = Set.of("minecraft:soul_sand");

    /** Nether foliage (fungi/roots/sprouts): nylium + soul_soil + dirt-family + farmland. */
    private static final Set<String> NETHER_FOLIAGE_SUPPORT = union(
            DIRT_OR_FARMLAND,
            Set.of("minecraft:crimson_nylium", "minecraft:warped_nylium", "minecraft:soul_soil"));

    /** dead_bush: dirt-family + sand + terracotta-family. */
    private static final Set<String> DEAD_BUSH_SUPPORT = union(
            DIRT_FAMILY,
            union(SAND, Set.of(
                    "minecraft:terracotta",
                    "minecraft:white_terracotta", "minecraft:orange_terracotta",
                    "minecraft:magenta_terracotta", "minecraft:light_blue_terracotta",
                    "minecraft:yellow_terracotta", "minecraft:lime_terracotta",
                    "minecraft:pink_terracotta", "minecraft:gray_terracotta",
                    "minecraft:light_gray_terracotta", "minecraft:cyan_terracotta",
                    "minecraft:purple_terracotta", "minecraft:blue_terracotta",
                    "minecraft:brown_terracotta", "minecraft:green_terracotta",
                    "minecraft:red_terracotta", "minecraft:black_terracotta")));

    // ---- Soil-dependent plant sets -----------------------------------------------------

    /**
     * Generic BushBlock plants (need DIRT_FAMILY or farmland): the small flowers, tall
     * flowers (lower half), grass/fern, sweet_berry_bush, azalea, pink_petals, etc.
     * Saplings, plus the {@code *_sapling} suffix, are handled via {@link #isSapling}.
     */
    private static final Set<String> DIRT_OR_FARMLAND_PLANTS = Set.of(
            // small flowers
            "minecraft:dandelion", "minecraft:poppy", "minecraft:blue_orchid",
            "minecraft:allium", "minecraft:azure_bluet", "minecraft:red_tulip",
            "minecraft:orange_tulip", "minecraft:white_tulip", "minecraft:pink_tulip",
            "minecraft:oxeye_daisy", "minecraft:cornflower", "minecraft:lily_of_the_valley",
            "minecraft:wither_rose", "minecraft:torchflower",
            // tall flowers (lower half) — upper halves sit on the lower half, not soil
            "minecraft:sunflower", "minecraft:lilac", "minecraft:rose_bush", "minecraft:peony",
            // grasses / ferns
            "minecraft:grass", "minecraft:fern", "minecraft:tall_grass", "minecraft:large_fern",
            // misc bushes
            "minecraft:sweet_berry_bush", "minecraft:azalea", "minecraft:flowering_azalea",
            "minecraft:pink_petals");

    /** Crops: farmland only. */
    private static final Set<String> CROPS = Set.of(
            "minecraft:wheat", "minecraft:carrots", "minecraft:potatoes",
            "minecraft:beetroots", "minecraft:melon_stem", "minecraft:pumpkin_stem",
            "minecraft:attached_melon_stem", "minecraft:attached_pumpkin_stem",
            "minecraft:torchflower_crop", "minecraft:pitcher_crop");

    /** Nether foliage plants. */
    private static final Set<String> NETHER_FOLIAGE = Set.of(
            "minecraft:crimson_fungus", "minecraft:warped_fungus",
            "minecraft:crimson_roots", "minecraft:warped_roots", "minecraft:nether_sprouts");

    @Test
    @EnabledIfSystemProperty(named = "auditPlantSupport", matches = "true")
    void auditPlantSupport() throws IOException {
        List<Path> files;
        try (var stream = Files.list(SOURCE_DIR)) {
            files = stream.filter(p -> p.toString().endsWith(".blueprint")).sorted().toList();
        }

        List<String> flagged = new ArrayList<>();
        int plantCount = 0;

        for (Path file : files) {
            String name = file.getFileName().toString().replace(".blueprint", "");
            Blueprint bp = readBlueprint(file);
            int sx = bp.sizeX(), sy = bp.sizeY(), sz = bp.sizeZ();

            for (int y = 0; y < sy; y++) {
                for (int z = 0; z < sz; z++) {
                    for (int x = 0; x < sx; x++) {
                        BlueprintBlockState cell = bp.get(x, y, z);
                        if (cell == null || cell.isAir()) continue;
                        Set<String> support = requiredSupport(cell);
                        if (support == null) continue; // not a soil-dependent plant we audit
                        plantCount++;

                        // Below cell. If y==0 the soil is world terrain (OOB) — can't verify,
                        // so don't flag it (the printer prints onto whatever the player placed).
                        if (y == 0) continue;
                        BlueprintBlockState below = bp.get(x, y - 1, z);
                        String belowId = (below == null || below.isAir()) ? null : below.blockId();

                        boolean ok = belowId != null && support.contains(belowId);
                        if (!ok) {
                            String belowLabel = belowId == null ? "AIR" : belowId;
                            String floating = belowId == null ? " (below=AIR)" : "";
                            flagged.add(String.format("%-30s (%d,%d,%d) %s on %s%s",
                                    name, x, y, z, cell.blockId(), belowLabel, floating));
                        }
                    }
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Plant-support audit — ").append(plantCount)
                .append(" soil-dependent plant cells across ").append(files.size())
                .append(" builds\n\n");
        sb.append("=== FLAGGED — plant on invalid/absent support (").append(flagged.size())
                .append(") ===\n");
        flagged.forEach(l -> sb.append(l).append('\n'));
        Files.createDirectories(OUTPUT.getParent());
        Files.writeString(OUTPUT, sb.toString());
        System.out.println("[PlantSupportAudit] " + flagged.size() + " flagged / " + plantCount
                + " plants across " + files.size() + " builds -> " + OUTPUT.toAbsolutePath());
    }

    /** Tall (two-block) flowers whose UPPER half legitimately sits on its own lower half. */
    private static final Set<String> TALL_FLOWERS = Set.of(
            "minecraft:sunflower", "minecraft:lilac", "minecraft:rose_bush",
            "minecraft:peony", "minecraft:tall_grass", "minecraft:large_fern",
            "minecraft:pitcher_plant");

    /**
     * Returns the set of valid support block ids for {@code cell}'s block if it is a
     * soil-dependent plant we audit, or {@code null} if the block is not audited
     * (not soil-dependent, or deliberately excluded to avoid false positives).
     */
    private static Set<String> requiredSupport(BlueprintBlockState cell) {
        String id = cell.blockId();

        // Two-block plants: only the LOWER half rests on soil — the UPPER half sits on its
        // own lower half, so excluding it avoids "lilac on lilac" false positives.
        if (TALL_FLOWERS.contains(id) && "upper".equals(cell.properties().get("half"))) {
            return null;
        }

        // --- Exclusions: not soil-dependent / valid on many blocks / attach differently ---
        if (id.startsWith("minecraft:potted_")) return null;     // flower pots: decor on any solid block
        if (id.endsWith("_mushroom")) return null;               // small mushrooms: most opaque blocks, low light
        switch (id) {
            case "minecraft:lily_pad",                           // on water
                 "minecraft:kelp", "minecraft:kelp_plant",
                 "minecraft:seagrass", "minecraft:tall_seagrass",
                 "minecraft:sea_pickle",                         // underwater
                 "minecraft:vine", "minecraft:glow_lichen",
                 "minecraft:weeping_vines", "minecraft:weeping_vines_plant",
                 "minecraft:twisting_vines", "minecraft:twisting_vines_plant", // attach differently
                 "minecraft:cocoa",                              // attaches to jungle log
                 "minecraft:chorus_flower", "minecraft:chorus_plant", // end_stone / itself
                 "minecraft:big_dripleaf", "minecraft:big_dripleaf_stem",
                 "minecraft:small_dripleaf",                     // clay/moss/dirt — excluded, low value
                 "minecraft:spore_blossom" -> {                  // ceiling-mounted
                return null;
            }
            default -> { }
        }

        // --- Audited soil-dependent plants ---
        if (isSapling(id)) return DIRT_OR_FARMLAND;
        if (DIRT_OR_FARMLAND_PLANTS.contains(id)) return DIRT_OR_FARMLAND;
        if (CROPS.contains(id)) return Set.of(FARMLAND);
        if ("minecraft:cactus".equals(id)) return CACTUS_SUPPORT;
        if ("minecraft:sugar_cane".equals(id)) return SUGAR_CANE_SUPPORT;
        if ("minecraft:bamboo".equals(id) || "minecraft:bamboo_sapling".equals(id)) return BAMBOO_SUPPORT;
        if ("minecraft:nether_wart".equals(id)) return NETHER_WART_SUPPORT;
        if (NETHER_FOLIAGE.contains(id)) return NETHER_FOLIAGE_SUPPORT;
        if ("minecraft:dead_bush".equals(id)) return DEAD_BUSH_SUPPORT;

        return null;
    }

    /** All vanilla saplings end in {@code _sapling} (bamboo_sapling handled separately). */
    private static boolean isSapling(String id) {
        return id.endsWith("_sapling") && !id.equals("minecraft:bamboo_sapling");
    }

    private static Set<String> union(Set<String> a, Set<String> b) {
        var out = new java.util.HashSet<>(a);
        out.addAll(b);
        return Set.copyOf(out);
    }

    private static Blueprint readBlueprint(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            CompoundTag tag = NbtIo.read(new DataInputStream(new GZIPInputStream(in)));
            return BlueprintSerializer.read(tag);
        }
    }
}
