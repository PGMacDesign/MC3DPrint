package com.pgmacdesign.mc3dprint.blueprint;

import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * Generates the bundled "curated blueprint" set in vanilla-overworld style.
 *
 * <p>These are pre-built {@code .blueprint} files (GZIP-wrapped uncompressed NBT,
 * exactly what {@link CuratedBlueprints#install} reads back) written to
 * {@code src/main/resources/data/mc3dprint/blueprints/&lt;name&gt;.blueprint}.
 * On server start {@link CuratedBlueprints} installs each into the world store
 * under a deterministic UUID = {@code CuratedBlueprints.uuidFor(MOD_ID, name)}.
 *
 * <p>This is NOT a normal unit test. The single file-writing method is gated on
 * {@code -DgenBlueprints=true}, so an ordinary {@code ./gradlew build} skips it
 * and never rewrites resources. Run it deliberately:
 *
 * <pre>
 *   ./gradlew test --tests *CuratedBlueprintGenerator* -DgenBlueprints=true
 * </pre>
 *
 * <p>The test's working directory under Gradle is the project root, so the
 * output path below is repo-relative.
 *
 * <p>Conventions used by every build below (see docs/blueprint-specs.md §2):
 * <ul>
 *   <li>Axes: {@code x} = width (east), {@code y} = up, {@code z} = depth (south).
 *       Spec footprints are written W&times;L&times;H; the builder is
 *       {@code builder(name, sizeX=W, sizeY=H, sizeZ=L)}.</li>
 *   <li>Doors face <em>into</em> the building: north wall ({@code z=0}) &rarr;
 *       {@code facing=south}; south wall &rarr; {@code facing=north}; west wall
 *       ({@code x=0}) &rarr; {@code facing=east}; east wall &rarr; {@code facing=west}.
 *       Use {@link #door2}.</li>
 *   <li>Windows always include glass ({@link #window2}); gables are closed
 *       ({@link #gableEndFill}); hanging lanterns are backed by a chain to a solid
 *       block ({@link #chainLantern}); battlements sit flush on the wall top
 *       ({@link #crenellate}).</li>
 * </ul>
 */
class CuratedBlueprintGenerator {

    /** Repo-relative output dir; Gradle runs tests with cwd = project root. */
    private static final Path OUTPUT_DIR =
            Path.of("src", "main", "resources", "data", "mc3dprint", "blueprints");

    // ---- common palette (parsed once; states are immutable & reusable) ----
    private static final BlueprintBlockState OAK_PLANKS = bs("minecraft:oak_planks");
    private static final BlueprintBlockState SPRUCE_PLANKS = bs("minecraft:spruce_planks");
    private static final BlueprintBlockState DARK_OAK_PLANKS = bs("minecraft:dark_oak_planks");
    private static final BlueprintBlockState OAK_LOG_Y = bs("minecraft:oak_log[axis=y]");
    private static final BlueprintBlockState OAK_LOG_X = bs("minecraft:oak_log[axis=x]");
    private static final BlueprintBlockState SPRUCE_LOG_Y = bs("minecraft:spruce_log[axis=y]");
    private static final BlueprintBlockState STRIPPED_OAK_Y = bs("minecraft:stripped_oak_log[axis=y]");
    private static final BlueprintBlockState STRIPPED_OAK_X = bs("minecraft:stripped_oak_log[axis=x]");
    private static final BlueprintBlockState STRIPPED_SPRUCE_Y = bs("minecraft:stripped_spruce_log[axis=y]");
    private static final BlueprintBlockState COBBLE = bs("minecraft:cobblestone");
    private static final BlueprintBlockState MOSSY_COBBLE = bs("minecraft:mossy_cobblestone");
    private static final BlueprintBlockState STONE_BRICKS = bs("minecraft:stone_bricks");
    private static final BlueprintBlockState MOSSY_STONE_BRICKS = bs("minecraft:mossy_stone_bricks");
    private static final BlueprintBlockState CRACKED_STONE_BRICKS = bs("minecraft:cracked_stone_bricks");
    private static final BlueprintBlockState CHISELED_STONE_BRICKS = bs("minecraft:chiseled_stone_bricks");
    private static final BlueprintBlockState BRICKS = bs("minecraft:bricks");
    private static final BlueprintBlockState GLASS_PANE = bs("minecraft:glass_pane");
    private static final BlueprintBlockState GLASS = bs("minecraft:glass");
    private static final BlueprintBlockState TINTED_GLASS = bs("minecraft:tinted_glass");
    private static final BlueprintBlockState OAK_FENCE = bs("minecraft:oak_fence");
    private static final BlueprintBlockState DARK_OAK_FENCE = bs("minecraft:dark_oak_fence");
    private static final BlueprintBlockState DIRT_PATH = bs("minecraft:dirt_path");
    private static final BlueprintBlockState GRASS_BLOCK = bs("minecraft:grass_block[snowy=false]");
    private static final BlueprintBlockState FARMLAND = bs("minecraft:farmland[moisture=7]");
    private static final BlueprintBlockState WATER = bs("minecraft:water[level=0]");
    private static final BlueprintBlockState WHEAT = bs("minecraft:wheat[age=7]");
    private static final BlueprintBlockState CARROTS = bs("minecraft:carrots[age=7]");
    private static final BlueprintBlockState POTATOES = bs("minecraft:potatoes[age=7]");
    private static final BlueprintBlockState HAY = bs("minecraft:hay_block[axis=y]");
    private static final BlueprintBlockState TORCH = bs("minecraft:torch");
    private static final BlueprintBlockState LANTERN = bs("minecraft:lantern[hanging=false]");
    private static final BlueprintBlockState HANGING_LANTERN = bs("minecraft:lantern[hanging=true]");
    private static final BlueprintBlockState SOUL_HANGING_LANTERN = bs("minecraft:soul_lantern[hanging=true]");
    private static final BlueprintBlockState CHAIN = bs("minecraft:chain[axis=y]");
    private static final BlueprintBlockState BELL_FLOOR = bs("minecraft:bell[attachment=floor,facing=north]");
    private static final BlueprintBlockState BELL_CEILING = bs("minecraft:bell[attachment=ceiling,facing=north]");
    private static final BlueprintBlockState CAULDRON = bs("minecraft:cauldron");
    private static final BlueprintBlockState COBBLE_WALL = bs("minecraft:cobblestone_wall");
    private static final BlueprintBlockState STONE_BRICK_WALL = bs("minecraft:stone_brick_wall");
    private static final BlueprintBlockState OAK_SLAB_BOTTOM = bs("minecraft:oak_slab[type=bottom]");
    private static final BlueprintBlockState OAK_SLAB_TOP = bs("minecraft:oak_slab[type=top]");
    private static final BlueprintBlockState SPRUCE_SLAB_BOTTOM = bs("minecraft:spruce_slab[type=bottom]");
    private static final BlueprintBlockState SPRUCE_SLAB_TOP = bs("minecraft:spruce_slab[type=top]");
    private static final BlueprintBlockState COBBLE_SLAB_TOP = bs("minecraft:cobblestone_slab[type=top]");
    private static final BlueprintBlockState STONE_BRICK_SLAB_TOP = bs("minecraft:stone_brick_slab[type=top]");
    private static final BlueprintBlockState STONE_BRICK_SLAB_BOTTOM = bs("minecraft:stone_brick_slab[type=bottom]");
    private static final BlueprintBlockState SMOOTH_STONE_SLAB_TOP = bs("minecraft:smooth_stone_slab[type=top]");
    private static final BlueprintBlockState SPRUCE_SLAB_TOP_STR = bs("minecraft:spruce_slab[type=top]");
    private static final BlueprintBlockState ANVIL = bs("minecraft:anvil[facing=north]");
    private static final BlueprintBlockState FURNACE = bs("minecraft:furnace[facing=south,lit=false]");
    private static final BlueprintBlockState BLAST_FURNACE = bs("minecraft:blast_furnace[facing=south,lit=false]");
    private static final BlueprintBlockState SMOKER = bs("minecraft:smoker[facing=south,lit=false]");
    private static final BlueprintBlockState BARREL = bs("minecraft:barrel[facing=up,open=false]");
    private static final BlueprintBlockState COMPOSTER = bs("minecraft:composter[level=0]");
    private static final BlueprintBlockState CRAFTING_TABLE = bs("minecraft:crafting_table");
    private static final BlueprintBlockState SMITHING_TABLE = bs("minecraft:smithing_table");
    private static final BlueprintBlockState FLETCHING_TABLE = bs("minecraft:fletching_table");
    private static final BlueprintBlockState CARTOGRAPHY_TABLE = bs("minecraft:cartography_table");
    private static final BlueprintBlockState LECTERN = bs("minecraft:lectern[facing=south,has_book=false,powered=false]");
    private static final BlueprintBlockState GRINDSTONE = bs("minecraft:grindstone[face=floor,facing=north]");
    private static final BlueprintBlockState BOOKSHELF = bs("minecraft:bookshelf");
    private static final BlueprintBlockState CHEST = bs("minecraft:chest[facing=south,type=single,waterlogged=false]");
    private static final BlueprintBlockState WHITE_WOOL = bs("minecraft:white_wool");
    private static final BlueprintBlockState RED_WOOL = bs("minecraft:red_wool");
    private static final BlueprintBlockState GREEN_WOOL = bs("minecraft:green_wool");
    private static final BlueprintBlockState LAVA = bs("minecraft:lava[level=0]");
    private static final BlueprintBlockState IRON_BARS = bs("minecraft:iron_bars");
    private static final BlueprintBlockState IRON_BLOCK = bs("minecraft:iron_block");
    private static final BlueprintBlockState CAMPFIRE = bs("minecraft:campfire[lit=true,facing=north,signal_fire=false,waterlogged=false]");
    private static final BlueprintBlockState LADDER_SOUTH = bs("minecraft:ladder[facing=south,waterlogged=false]");
    private static final BlueprintBlockState SEA_LANTERN = bs("minecraft:sea_lantern");
    private static final BlueprintBlockState LIGHTNING_ROD = bs("minecraft:lightning_rod[facing=up,powered=false]");
    private static final BlueprintBlockState END_ROD = bs("minecraft:end_rod[facing=up]");
    private static final BlueprintBlockState GLOWSTONE = bs("minecraft:glowstone");

    @BeforeAll
    static void bootstrap() {
        // Mirrors what a registry-touching MC unit test does. The generator only
        // calls BlueprintBlockState.parse()/serialize() (pure string work) and
        // NBT writing, so this is belt-and-suspenders; guarded so it can never
        // fail the run if the registry is already (or cannot be) initialized.
        try {
            SharedConstants.setVersion(DetectedVersion.BUILT_IN);
            Bootstrap.bootStrap();
        } catch (Throwable ignored) {
            // headless/registry-free path is sufficient for parse + serialize
        }
    }

    // =====================================================================
    //  THE GENERATOR  (the only method that writes files)
    // =====================================================================

    @Test
    @EnabledIfSystemProperty(named = "genBlueprints", matches = "true")
    void generateCuratedBlueprints() throws IOException {
        Files.createDirectories(OUTPUT_DIR);

        // name -> builder. LinkedHashMap keeps a stable, readable order — matches
        // CuratedBlueprints.CURATED_NAMES 1:1 (coverage matrix §4 order).
        Map<String, Blueprint> builds = new LinkedHashMap<>();
        // Small (T3–T4 footprint)
        builds.put("garden_shed", gardenShed());
        builds.put("campfire_site", campfireSite());
        builds.put("well", well());
        builds.put("market_stall", marketStall());
        builds.put("small_cottage", smallCottage());
        builds.put("beacon_spire", beaconSpire());
        // Medium (T5–T6 footprint)
        builds.put("plains_house", plainsHouse());
        builds.put("small_farm", smallFarm());
        builds.put("bakery", bakery());
        builds.put("blacksmith", blacksmith());
        builds.put("windmill", windmill());
        builds.put("stone_bridge", stoneBridge());
        builds.put("watchtower", watchtower());
        builds.put("barn", barn());
        // High-material-tier (disc T2–T5)
        builds.put("iron_foundry", ironFoundry());
        builds.put("redstone_workshop", redstoneWorkshop());
        builds.put("diamond_vault", diamondVault());
        // Large (T7–T8 footprint) + remaining high-tier
        builds.put("church", church());
        builds.put("manor_house", manorHouse());
        builds.put("copper_observatory", copperObservatory());
        builds.put("emerald_market_hall", emeraldMarketHall());
        builds.put("lighthouse", lighthouse());
        builds.put("castle_keep", castleKeep());
        // Phase 0 pilot builds (validate the parametric helper library)
        builds.put("cherry_grove_cottage", cherryGroveCottage());
        builds.put("enchanting_room", enchantingRoom());
        builds.put("japanese_pagoda", japanesePagoda());
        // Phase 1 pilot builds — group 2 (remaining bank archetypes)
        builds.put("savanna_acacia_villa", savannaAcaciaVilla());
        builds.put("tiered_fountain", tieredFountain());
        builds.put("wall_battlement_segment", wallBattlementSegment());
        // Per-biome starter house (§3.A)
        builds.put("desert_sandstone_house", desertSandstoneHouse());
        builds.put("desert_pyramid_shrine", desertPyramidShrine());
        builds.put("taiga_log_cabin", taigaLogCabin());
        builds.put("taiga_spruce_longhouse", taigaSpruceLonghouse());
        // Phase 2 — Category A
        builds.put("snowy_igloo", snowyIgloo());
        builds.put("snowy_alpine_chalet", snowyAlpineChalet());
        builds.put("jungle_hut", jungleHut());
        builds.put("jungle_temple_ruin", jungleTempleRuin());
        builds.put("mangrove_stilt_hut", mangroveStiltHut());
        builds.put("cherry_blossom_pavilion", cherryBlossomPavilion());
        builds.put("badlands_mesa_dwelling", badlandsMesaDwelling());
        builds.put("hobbit_hole", hobbitHole());
        builds.put("treehouse", treehouse());
        // Phase 2 — Category F (functional farms)
        builds.put("iron_farm", ironFarm());
        builds.put("mob_xp_tower", mobXpTower());
        builds.put("sugarcane_farm_auto", sugarcaneFarmAuto());
        builds.put("pumpkin_melon_farm", pumpkinMelonFarm());
        builds.put("cactus_farm", cactusFarm());
        builds.put("bamboo_farm", bambooFarm());
        builds.put("kelp_farm", kelpFarm());
        builds.put("villager_trading_hall", villagerTradingHall());
        builds.put("animal_pen", animalPen());
        builds.put("chicken_coop_auto", chickenCoopAuto());
        builds.put("fishery_pond", fisheryPond());
        builds.put("tree_farm", treeFarm());
        builds.put("mushroom_farm", mushroomFarm());
        // Phase 2 — Category I (ornamental / garden)
        builds.put("koi_pond", koiPond());
        builds.put("gazebo", gazebo());
        builds.put("pergola_garden", pergolaGarden());
        builds.put("wishing_well", wishingWell());
        builds.put("statue_pedestal", statuePedestal());
        builds.put("obelisk", obelisk());
        builds.put("stonehenge_ring", stonehengeRing());
        builds.put("garden_archway", gardenArchway());
        builds.put("ruin_pillar", ruinPillar());
        builds.put("cemetery_plot", cemeteryPlot());
        builds.put("scarecrow", scarecrow());
        builds.put("flower_shop", flowerShop());
        builds.put("food_stall", foodStall());
        builds.put("park_bench_lamppost", parkBenchLamppost());
        builds.put("hedge_maze_segment", hedgeMazeSegment());
        builds.put("hot_air_balloon", hotAirBalloon());
        builds.put("dragon_statue", dragonStatue());
        // Phase 2 — Category G (storage)
        builds.put("storage_barrel_hall", storageBarrelHall());
        builds.put("brewing_room", brewingRoom());
        builds.put("super_smelter", superSmelter());
        builds.put("smithy_workshop", smithyWorkshop());
        builds.put("map_room", mapRoom());
        builds.put("library", library());
        // Phase 2 — Category H (infrastructure / civic / defensive)
        builds.put("sky_bridge_segment", skyBridgeSegment());
        builds.put("road_path_segment", roadPathSegment());
        builds.put("aqueduct_segment", aqueductSegment());
        builds.put("mineshaft_entrance", mineshaftEntrance());
        builds.put("railway_station", railwayStation());
        builds.put("tavern_inn", tavernInn());
        builds.put("apothecary_shop", apothecaryShop());
        builds.put("gatehouse", gatehouse());
        builds.put("guard_tower", guardTower());
        builds.put("drawbridge", drawbridge());
        builds.put("portcullis_gate", portcullisGate());
        builds.put("stable_horse", stableHorse());
        builds.put("greenhouse", greenhouse());
        // Phase 2 — Category B (modern / contemporary)
        builds.put("modern_concrete_house", modernConcreteHouse());
        builds.put("modern_pool_deck", modernPoolDeck());
        builds.put("cottagecore_cottage", cottagecoreCottage());
        builds.put("torii_gate", toriiGate());
        builds.put("japanese_tea_house", japaneseTeaHouse());
        builds.put("zen_garden", zenGarden());
        builds.put("japanese_dojo", japaneseDojo());

        int written = 0;
        for (Map.Entry<String, Blueprint> e : builds.entrySet()) {
            writeBlueprint(e.getKey(), e.getValue());
            written++;
        }
        System.out.println("[CuratedBlueprintGenerator] wrote " + written
                + " curated blueprint(s) to " + OUTPUT_DIR.toAbsolutePath());
    }

    /** Serializes and writes one blueprint as GZIP NBT — the inverse of {@link CuratedBlueprints}. */
    private static void writeBlueprint(String name, Blueprint blueprint) throws IOException {
        CompoundTag tag = BlueprintSerializer.write(blueprint);
        File file = OUTPUT_DIR.resolve(name + ".blueprint").toFile();
        try (DataOutputStream out = new DataOutputStream(
                new GZIPOutputStream(new FileOutputStream(file)))) {
            NbtIo.write(tag, out);
        }
    }

    // =====================================================================
    //  PARAMETRIC HELPERS  (compose these into builds)
    // =====================================================================

    private static BlueprintBlockState bs(String id) {
        return BlueprintBlockState.parse(id);
    }

    /** A solid floor of {@code mat} filling [x0..x1] x [z0..z1] at height y. */
    private static void floor(Blueprint.Builder b, int y, int x0, int z0, int x1, int z1, BlueprintBlockState mat) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                b.set(x, y, z, mat);
            }
        }
    }

    /** A horizontal line of {@code mat} from (x0,z0) to (x1,z1) at height y (axis-aligned). */
    private static void line(Blueprint.Builder b, int y, int x0, int z0, int x1, int z1, BlueprintBlockState mat) {
        int dx = Integer.signum(x1 - x0);
        int dz = Integer.signum(z1 - z0);
        int x = x0, z = z0;
        b.set(x, y, z, mat);
        while (x != x1 || z != z1) {
            x += dx;
            z += dz;
            b.set(x, y, z, mat);
        }
    }

    /** A vertical pillar of {@code mat} from y0..y1 at (x,z). */
    private static void pillar(Blueprint.Builder b, int x, int z, int y0, int y1, BlueprintBlockState mat) {
        for (int y = y0; y <= y1; y++) {
            b.set(x, y, z, mat);
        }
    }

    /**
     * The four hollow wall faces of a rectangular room of material {@code mat},
     * spanning [x0..x1] x [z0..z1] from yBottom..yTop (corners included, interior empty).
     */
    private static void walls(Blueprint.Builder b, int x0, int z0, int x1, int z1,
                              int yBottom, int yTop, BlueprintBlockState mat) {
        for (int y = yBottom; y <= yTop; y++) {
            line(b, y, x0, z0, x1, z0, mat); // north face
            line(b, y, x0, z1, x1, z1, mat); // south face
            line(b, y, x0, z0, x0, z1, mat); // west face
            line(b, y, x1, z0, x1, z1, mat); // east face
        }
    }

    /** Corner posts (logs) at the four corners of a [x0..x1]x[z0..z1] room, yBottom..yTop. */
    private static void corners(Blueprint.Builder b, int x0, int z0, int x1, int z1,
                                int yBottom, int yTop, BlueprintBlockState post) {
        pillar(b, x0, z0, yBottom, yTop, post);
        pillar(b, x1, z0, yBottom, yTop, post);
        pillar(b, x0, z1, yBottom, yTop, post);
        pillar(b, x1, z1, yBottom, yTop, post);
    }

    /** Single window pane swapped into a wall cell. */
    private static void window(Blueprint.Builder b, int x, int y, int z, BlueprintBlockState pane) {
        b.set(x, y, z, pane);
    }

    /** A wall torch on a face. {@code facing} points away from the wall it's mounted on. */
    private static void wallTorch(Blueprint.Builder b, int x, int y, int z, String facing) {
        b.set(x, y, z, bs("minecraft:wall_torch[facing=" + facing + "]"));
    }

    /**
     * A symmetric gable roof of stairs running along the X axis (ridge parallel to X),
     * sloping down in the +/-z direction. Spans [x0..x1] x [z0..z1] starting at yBase.
     * Stairs facing=south on the north slope, facing=north on the south slope; a slab caps the ridge.
     */
    private static void gableRoofX(Blueprint.Builder b, int x0, int z0, int x1, int z1, int yBase,
                                   String stair, BlueprintBlockState ridgeSlab) {
        BlueprintBlockState north = bs("minecraft:" + stair + "[facing=south,half=bottom,shape=straight]");
        BlueprintBlockState south = bs("minecraft:" + stair + "[facing=north,half=bottom,shape=straight]");
        int y = yBase;
        int zn = z0;     // advancing north slope row
        int zs = z1;     // advancing south slope row
        while (zs - zn > 1) {
            for (int x = x0; x <= x1; x++) {
                b.set(x, y, zn, north);
                b.set(x, y, zs, south);
            }
            zn++;
            zs--;
            y++;
        }
        // ridge: one center row (zs==zn) or a 2-wide cap (zs==zn+1), slab-topped
        for (int x = x0; x <= x1; x++) {
            b.set(x, y, zn, ridgeSlab);
            if (zs != zn) b.set(x, y, zs, ridgeSlab);
        }
    }

    /** Peak Y of {@link #gableRoofX} for a roof of the given z-depth at yBase — used to size builds. */
    private static int gablePeakY(int z0, int z1, int yBase) {
        int steps = 0;
        int zn = z0, zs = z1;
        while (zs - zn > 1) { zn++; zs--; steps++; }
        return yBase + steps;
    }

    /** A flat slab roof (top slabs) covering [x0..x1]x[z0..z1] at height y. */
    private static void flatRoof(Blueprint.Builder b, int y, int x0, int z0, int x1, int z1, BlueprintBlockState slabTop) {
        floor(b, y, x0, z0, x1, z1, slabTop);
    }

    /** Fence ring around the perimeter [x0..x1]x[z0..z1] at height y. */
    private static void fenceRing(Blueprint.Builder b, int y, int x0, int z0, int x1, int z1, BlueprintBlockState fence) {
        line(b, y, x0, z0, x1, z0, fence);
        line(b, y, x0, z1, x1, z1, fence);
        line(b, y, x0, z0, x0, z1, fence);
        line(b, y, x1, z0, x1, z1, fence);
    }

    /** A dirt-path strip along Z at column x, z0..z1. */
    private static void path(Blueprint.Builder b, int x, int z0, int z1) {
        for (int z = z0; z <= z1; z++) {
            b.set(x, 0, z, DIRT_PATH);
        }
    }

    /** A torch on a fence/post light: post of {@code postMat} y0..y1 with a torch on top. */
    private static void light(Blueprint.Builder b, int x, int z, int y0, int y1, BlueprintBlockState postMat) {
        pillar(b, x, z, y0, y1, postMat);
        b.set(x, y1 + 1, z, TORCH);
    }

    // ---------------------------------------------------------------------
    //  NEW HELPERS (docs/blueprint-specs.md §5)
    // ---------------------------------------------------------------------

    /**
     * (1) Fills the two triangular gable ends of a {@link #gableRoofX} with
     * {@code mat}, up to the rising ridge, so the attic is closed.
     *
     * <p>A {@link #gableRoofX} has its ridge running along X and slopes facing
     * &plusmn;Z, so the open triangular ends are the {@code x=x0} and {@code x=x1}
     * faces — NOT the z-faces (those are covered by the slopes themselves). The
     * earlier implementation filled the z-faces, which left the real gable
     * triangles open (you could see sky through them).
     *
     * <p>This walks the same step schedule {@link #gableRoofX} uses. At each
     * course the slope occupies rows {@code z=zn} (north) and {@code z=zs}
     * (south); the gap between them on the end face — {@code z = zn+1 .. zs-1} —
     * is the triangle interior and gets {@code mat}. As {@code zn}/{@code zs}
     * converge with rising {@code y}, the filled span narrows to the ridge,
     * forming a solid triangle from the eave up to the ridgeline.
     *
     * <p>{@code yBase} is the roof's first course (the wall plate). {@code z0/z1}
     * and {@code x0/x1} must match the {@link #gableRoofX} call.
     */
    private static void gableEndFill(Blueprint.Builder b, int x0, int z0, int x1, int z1,
                                     int yBase, BlueprintBlockState mat) {
        for (int x : new int[]{x0, x1}) {
            int y = yBase;
            int zn = z0, zs = z1;
            while (zs - zn > 1) {
                // fill the triangle interior strictly between the two slope rows
                for (int z = zn + 1; z <= zs - 1; z++) {
                    b.set(x, y, z, mat);
                }
                zn++;
                zs--;
                y++;
            }
            // at the ridge course zn..zs is empty (zn==zs) or a 1-wide cap
            // (zn+1==zs handled by the loop), so nothing remains to fill.
        }
    }

    /**
     * (2) A hip roof: slopes on all four sides, stepping inward to a ridge/point.
     * Each ring is stairs facing inward; covers [x0..x1]x[z0..z1] from yBase.
     */
    private static void hipRoof(Blueprint.Builder b, int x0, int z0, int x1, int z1, int yBase,
                                String stair, BlueprintBlockState cap) {
        BlueprintBlockState north = bs("minecraft:" + stair + "[facing=south,half=bottom,shape=straight]");
        BlueprintBlockState south = bs("minecraft:" + stair + "[facing=north,half=bottom,shape=straight]");
        BlueprintBlockState west = bs("minecraft:" + stair + "[facing=east,half=bottom,shape=straight]");
        BlueprintBlockState east = bs("minecraft:" + stair + "[facing=west,half=bottom,shape=straight]");
        int y = yBase;
        int ax0 = x0, az0 = z0, ax1 = x1, az1 = z1;
        while (ax1 - ax0 > 1 && az1 - az0 > 1) {
            for (int x = ax0; x <= ax1; x++) {
                b.set(x, y, az0, north);
                b.set(x, y, az1, south);
            }
            for (int z = az0; z <= az1; z++) {
                b.set(ax0, y, z, west);
                b.set(ax1, y, z, east);
            }
            ax0++; az0++; ax1--; az1--;
            y++;
        }
        // cap the remaining 1- or 2-wide ridge with the cap material
        for (int x = ax0; x <= ax1; x++) {
            for (int z = az0; z <= az1; z++) {
                b.set(x, y, z, cap);
            }
        }
    }

    /**
     * (3) A square pyramid roof: 4 slopes converging to a point/cap at the centre,
     * over [x0..x1]x[z0..z1] from yBase. Functionally a hip roof; kept distinct so
     * intent is clear for spires/tower tops.
     */
    private static void pyramidRoof(Blueprint.Builder b, int x0, int z0, int x1, int z1, int yBase,
                                    String stair, BlueprintBlockState cap) {
        hipRoof(b, x0, z0, x1, z1, yBase, stair, cap);
    }

    /**
     * (4) A gambrel (two-pitch-per-side, barn) roof running along X. The
     * characteristic barn profile: a flared shallow eave course (upside-down
     * stairs) at {@code yBase}, then a steeper plain gable above it converging to
     * a slab ridge. Closes in the same course-count as {@link #gableRoofX} (one
     * extra eave course), so it fits wherever a gable fits plus one.
     */
    private static void gambrelRoofX(Blueprint.Builder b, int x0, int z0, int x1, int z1, int yBase,
                                     String stair, BlueprintBlockState ridgeSlab) {
        // flared eave course: top-half stairs flush at the wall plate (the barn flare)
        BlueprintBlockState eaveN = bs("minecraft:" + stair + "[facing=south,half=top,shape=straight]");
        BlueprintBlockState eaveS = bs("minecraft:" + stair + "[facing=north,half=top,shape=straight]");
        for (int x = x0; x <= x1; x++) {
            b.set(x, yBase, z0, eaveN);
            b.set(x, yBase, z1, eaveS);
        }
        // steeper upper pitch as a plain gable, inset one z-row, from yBase+1
        gableRoofX(b, x0, z0 + 1, x1, z1 - 1, yBase + 1, stair, ridgeSlab);
    }

    /**
     * (5a) A midpoint-circle ring of {@code mat} at height {@code y}, centre
     * (cx,cz), radius r. Only the perimeter cells are placed.
     */
    private static void circleRing(Blueprint.Builder b, int y, int cx, int cz, int r, BlueprintBlockState mat) {
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                double d = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
                if (d <= r + 0.5 && d > r - 0.5) {
                    b.set(x, y, z, mat);
                }
            }
        }
    }

    /** (5b) A filled disc of {@code mat} at height {@code y}, centre (cx,cz), radius r. */
    private static void disc(Blueprint.Builder b, int y, int cx, int cz, int r, BlueprintBlockState mat) {
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                double d = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
                if (d <= r + 0.5) {
                    b.set(x, y, z, mat);
                }
            }
        }
    }

    /**
     * (6) A hemispherical dome of {@code mat}: stacked decreasing rings from the
     * springing height {@code cy} up to {@code cy+r}, centred at (cx,cz).
     */
    private static void dome(Blueprint.Builder b, int cx, int cz, int cy, int r, BlueprintBlockState mat) {
        for (int dy = 0; dy <= r; dy++) {
            double ringR = Math.sqrt((double) (r * r - dy * dy));
            int rr = (int) Math.round(ringR);
            if (rr <= 0) {
                b.set(cx, cy + dy, cz, mat);
            } else {
                circleRing(b, cy + dy, cx, cz, rr, mat);
            }
        }
    }

    /**
     * (7) A perimeter battlement on the wall top at height {@code y}: merlons
     * (wall blocks) in a 2-up/1-gap rhythm so it reads as crenellations. Sits
     * flush — call with {@code y} = wallTop+1 (no vertical gap).
     */
    private static void crenellate(Blueprint.Builder b, int y, int x0, int z0, int x1, int z1, BlueprintBlockState wall) {
        // perimeter cells, placed where (index % 3 != 2) → 2 merlons then a crenel
        int idx = 0;
        for (int x = x0; x <= x1; x++) { if (idx++ % 3 != 2) b.set(x, y, z0, wall); }
        idx = 0;
        for (int x = x0; x <= x1; x++) { if (idx++ % 3 != 2) b.set(x, y, z1, wall); }
        idx = 0;
        for (int z = z0 + 1; z <= z1 - 1; z++) { if (idx++ % 3 != 2) b.set(x0, y, z, wall); }
        idx = 0;
        for (int z = z0 + 1; z <= z1 - 1; z++) { if (idx++ % 3 != 2) b.set(x1, y, z, wall); }
    }

    /**
     * (8) A door whose facing is derived from the wall face it sits on, so it
     * always opens into the building. {@code wallFace} is one of N/S/E/W.
     * north wall (z=0) → facing=south; south → north; west (x=0) → east; east → west.
     */
    private static void door2(Blueprint.Builder b, int x, int y, int z, String wood, String wallFace) {
        String facing;
        switch (wallFace.toUpperCase()) {
            case "N": case "NORTH": facing = "south"; break;
            case "S": case "SOUTH": facing = "north"; break;
            case "W": case "WEST": facing = "east"; break;
            case "E": case "EAST": facing = "west"; break;
            default: facing = "south"; break;
        }
        b.set(x, y, z, bs("minecraft:" + wood + "_door[facing=" + facing + ",half=lower,hinge=left,open=false,powered=false]"));
        b.set(x, y + 1, z, bs("minecraft:" + wood + "_door[facing=" + facing + ",half=upper,hinge=left,open=false,powered=false]"));
    }

    /**
     * (8b) A complete two-block bed. A bed is the head part at {@code (x,y,z)}
     * plus the foot part one cell in the OPPOSITE of {@code facing}. In vanilla
     * 1.20.1 the {@code facing} property (carried identically on both halves)
     * points from foot toward head, so the head sits {@code facing} of the foot
     * and the foot sits one step back the other way:
     * <ul>
     *   <li>{@code facing=south} → foot at {@code (x, y, z-1)}</li>
     *   <li>{@code facing=north} → foot at {@code (x, y, z+1)}</li>
     *   <li>{@code facing=east}  → foot at {@code (x-1, y, z)}</li>
     *   <li>{@code facing=west}  → foot at {@code (x+1, y, z)}</li>
     * </ul>
     * The earlier builds placed only the {@code part=head} half, so a bed
     * printed as a single block. Use this everywhere a bed appears.
     */
    private static void bed(Blueprint.Builder b, int x, int y, int z, String color, String facing) {
        int fx = x, fz = z;
        switch (facing) {
            case "south": fz = z - 1; break;
            case "north": fz = z + 1; break;
            case "east":  fx = x - 1; break;
            case "west":  fx = x + 1; break;
            default: fz = z - 1; break; // treat unknown as south
        }
        b.set(x, y, z, bs("minecraft:" + color + "_bed[facing=" + facing + ",part=head,occupied=false]"));
        b.set(fx, y, fz, bs("minecraft:" + color + "_bed[facing=" + facing + ",part=foot,occupied=false]"));
    }

    /**
     * (9) An alternating-material band at height {@code y} over [x0..x1]x[z0..z1]:
     * matA for {@code period} cells then matB for {@code period} cells, by x-index.
     */
    private static void stripedBand(Blueprint.Builder b, int y, int x0, int z0, int x1, int z1,
                                    BlueprintBlockState matA, BlueprintBlockState matB, int period) {
        for (int x = x0; x <= x1; x++) {
            boolean a = ((x - x0) / Math.max(1, period)) % 2 == 0;
            for (int z = z0; z <= z1; z++) {
                b.set(x, y, z, a ? matA : matB);
            }
        }
    }

    /**
     * (10) A window: a glass pane in the wall cell, plus an optional exterior sill
     * (a slab/stair below, outside). Pass {@code sillMat=null} for no sill. The
     * sill is placed one block below at the same cell (purely cosmetic depth).
     */
    private static void window2(Blueprint.Builder b, int x, int y, int z,
                                BlueprintBlockState pane, BlueprintBlockState sillMat) {
        b.set(x, y, z, pane);
        if (sillMat != null && y - 1 >= 0) {
            b.set(x, y - 1, z, sillMat);
        }
    }

    /**
     * (11) A backed hanging lantern: {@code hangLen} chain links up to a solid
     * block, then a hanging lantern below them. The lantern ends up at
     * {@code (x, y, z)}; chains fill {@code y+1 .. y+hangLen}. Caller must ensure a
     * solid block exists at {@code y+hangLen+1} (or the top chain attaches to one).
     */
    private static void chainLantern(Blueprint.Builder b, int x, int y, int z, int hangLen) {
        for (int i = 1; i <= hangLen; i++) {
            b.set(x, y + i, z, CHAIN);
        }
        b.set(x, y, z, HANGING_LANTERN);
    }

    /**
     * (12) A walkable stair ramp climbing along +z from (x0..x1, z0) at yStart up
     * to (x0..x1, z1), one block per z-row, so it can be traversed in-world.
     */
    private static void ramp(Blueprint.Builder b, int x0, int z0, int x1, int z1, int yStart, String stair) {
        BlueprintBlockState up = bs("minecraft:" + stair + "[facing=north,half=bottom,shape=straight]");
        int dz = Integer.signum(z1 - z0);
        int y = yStart;
        int z = z0;
        while (true) {
            for (int x = x0; x <= x1; x++) {
                b.set(x, y, z, up);
            }
            if (z == z1) break;
            z += dz;
            y++;
        }
    }

    /**
     * (13) A timber-frame wall: a plank ring overlaid with a stripped-log stud/rail
     * grid — vertical studs every 2 cells and horizontal rails at the top & bottom
     * course. Over [x0..x1]x[z0..z1] from y0..y1.
     */
    private static void timberFrame(Blueprint.Builder b, int x0, int z0, int x1, int z1, int y0, int y1,
                                    BlueprintBlockState planks, BlueprintBlockState logY, BlueprintBlockState logX) {
        walls(b, x0, z0, x1, z1, y0, y1, planks);
        // vertical studs along all four faces every 2 cells
        for (int x = x0; x <= x1; x += 2) {
            pillar(b, x, z0, y0, y1, logY);
            pillar(b, x, z1, y0, y1, logY);
        }
        for (int z = z0; z <= z1; z += 2) {
            pillar(b, x0, z, y0, y1, logY);
            pillar(b, x1, z, y0, y1, logY);
        }
        // horizontal rails (top & bottom) — use axis-x logs on the z-faces
        line(b, y0, x0, z0, x1, z0, logX);
        line(b, y1, x0, z0, x1, z0, logX);
        line(b, y0, x0, z1, x1, z1, logX);
        line(b, y1, x0, z1, x1, z1, logX);
    }

    /** (14) The cut-copper variant for a patina level 0..3 (cut→exposed→weathered→oxidized). */
    private static BlueprintBlockState copperPatina(int level) {
        switch (level) {
            case 0: return bs("minecraft:cut_copper");
            case 1: return bs("minecraft:exposed_cut_copper");
            case 2: return bs("minecraft:weathered_cut_copper");
            default: return bs("minecraft:oxidized_cut_copper");
        }
    }

    /** Copper stair variant for a patina level (used for dome curves). */
    private static BlueprintBlockState copperStair(int level, String facing, String half) {
        String prefix;
        switch (level) {
            case 0: prefix = "cut_copper_stairs"; break;
            case 1: prefix = "exposed_cut_copper_stairs"; break;
            case 2: prefix = "weathered_cut_copper_stairs"; break;
            default: prefix = "oxidized_cut_copper_stairs"; break;
        }
        return bs("minecraft:" + prefix + "[facing=" + facing + ",half=" + half + ",shape=straight]");
    }

    // =====================================================================
    //  PHASE 0 SHARED LIBRARY  (palette-driven, parametric houses + rooms)
    // =====================================================================
    /*
     * ─────────────────────────────────────────────────────────────────────
     *  CONVENTIONS FOR FUTURE BUILD AUTHORS (read before writing a build)
     * ─────────────────────────────────────────────────────────────────────
     *
     *  AXES.  x = width  (east, +x),  y = up (+y),  z = depth (south, +z).
     *  Spec footprints are written W×L×H and the builder is
     *  {@code builder(name, sizeX=W, sizeY=H, sizeZ=L)} — note H feeds sizeY.
     *
     *  AIR-SKIP RULE.  {@link Blueprint.Builder#set} SILENTLY IGNORES air
     *  (see Blueprint.java:160 — "Air states are treated as empty and
     *  skipped"). You therefore CANNOT punch a hole by setting air. To leave
     *  a cell empty — a doorway, a window opening, an open interior, a ladder
     *  hatch — simply DO NOT call set() on that cell. NEVER try to clear a
     *  cell with {@code bs("minecraft:air")}; it is a no-op and the previously
     *  placed block (if any) stays. The {@link #house} helper relies on this:
     *  the doorway and the whole interior are left open by not writing them.
     *
     *  ENTERABLE INTERIORS.  The ground-floor interior MUST be open at
     *  standing height. A past bug raised an interior floor one level so the
     *  player spawned inside solid blocks and the build was unenterable. So:
     *  put the SOLID floor at {@code y = y0} (walkable surface = top of y0),
     *  the wall ring from {@code y0+1} up, the doorway gap at {@code y0+1}
     *  and {@code y0+2}, and leave the interior cells above the floor unset.
     *
     *  STAIR FACING (gable/hip).  Roof stairs face the way they DESCEND, i.e.
     *  toward the eave. Per {@link #gableRoofX}: the north slope (low z) uses
     *  {@code facing=south}; the south slope (high z) uses {@code facing=north}
     *  (and likewise west→{@code facing=east}, east→{@code facing=west} for
     *  {@link #hipRoof}). The helpers build the stair strings for you from a
     *  bare stair-block NAME (e.g. {@code "oak_stairs"}); a {@link Palette}
     *  carries that name in {@link Palette#roofStairName}.
     *
     *  CONNECTING BLOCKS self-reconcile AT PRINT TIME. Glass panes, fences,
     *  iron bars, walls, and redstone wire compute their own connection
     *  shapes when placed in-world, so you do NOT pre-set connection states —
     *  just place the base block and the printer reconciles neighbours.
     *
     *  TWO-BLOCK PLACEMENTS use the dedicated helpers so BOTH halves land:
     *  doors via {@link #door2} (facing derived from the wall face so it opens
     *  inward), beds via {@link #bed} (head + foot). Setting only one half
     *  prints a broken single block.
     * ─────────────────────────────────────────────────────────────────────
     */

    /**
     * A reusable block set that drives a biome/style house. Fields are grouped
     * by role so {@link #house} (and future room/roof builders) can stay
     * palette-agnostic — swap the {@link Palette} and the same geometry renders
     * in a different material family.
     *
     * <p>Roof material is split to feed the existing roof helpers cleanly:
     * {@link #gableRoofX}/{@link #hipRoof} take a bare stair-block <em>name</em>
     * {@code String} (they build the facing variants themselves), so the palette
     * stores {@link #roofStairName} (e.g. {@code "oak_stairs"}) alongside the
     * matching {@link #roofSlab} they want for the ridge/cap. Likewise
     * {@link #bed} and {@link #door2} take colour/wood <em>name</em> strings, so
     * {@link #doorWood} and {@link #bedColor} are stored as {@code String}.
     */
    private static final class Palette {
        /** Primary wall block (the bulk of the wall ring). */
        final BlueprintBlockState wall;
        /** Secondary trim / accent block (banding, plate course, dormers). */
        final BlueprintBlockState accentWall;
        /** Corner post, vertical (axis=y) variant. */
        final BlueprintBlockState logPillarY;
        /** Corner post, horizontal (axis=x) variant — for tie-beams/rails. */
        final BlueprintBlockState logPillarX;
        /** Interior plank/finish floor block. */
        final BlueprintBlockState plankFloor;
        /** Matching slab, top half (ridge caps, sills, counters). */
        final BlueprintBlockState slabTop;
        /** Matching slab, bottom half (ridge of a gable, eaves). */
        final BlueprintBlockState slabBottom;
        /** Window glazing (a pane or glass block). */
        final BlueprintBlockState windowPane;
        /** Bare stair-block NAME for {@link #gableRoofX}/{@link #hipRoof} (e.g. "oak_stairs"). */
        final String roofStairName;
        /** Slab used for the gable ridge / hip cap (usually {@link #slabBottom}). */
        final BlueprintBlockState roofSlab;
        /** Wood NAME for {@link #door2} (e.g. "oak"). */
        final String doorWood;
        /** Bed colour NAME for {@link #bed} (e.g. "white"). */
        final String bedColor;
        /** A light source block (lantern/torch/etc.) for sconces and interiors. */
        final BlueprintBlockState lightBlock;

        Palette(BlueprintBlockState wall, BlueprintBlockState accentWall,
                BlueprintBlockState logPillarY, BlueprintBlockState logPillarX,
                BlueprintBlockState plankFloor, BlueprintBlockState slabTop, BlueprintBlockState slabBottom,
                BlueprintBlockState windowPane, String roofStairName, BlueprintBlockState roofSlab,
                String doorWood, String bedColor, BlueprintBlockState lightBlock) {
            this.wall = wall;
            this.accentWall = accentWall;
            this.logPillarY = logPillarY;
            this.logPillarX = logPillarX;
            this.plankFloor = plankFloor;
            this.slabTop = slabTop;
            this.slabBottom = slabBottom;
            this.windowPane = windowPane;
            this.roofStairName = roofStairName;
            this.roofSlab = roofSlab;
            this.doorWood = doorWood;
            this.bedColor = bedColor;
            this.lightBlock = lightBlock;
        }
    }

    // ---- predefined biome/style palettes (vanilla, FU-valued blocks) --------

    /** Plains: oak planks + cobble, oak corner logs, glass panes, white bed. */
    private static final Palette PLAINS_OAK = new Palette(
            OAK_PLANKS, COBBLE, OAK_LOG_Y, OAK_LOG_X,
            OAK_PLANKS, OAK_SLAB_TOP, OAK_SLAB_BOTTOM, GLASS_PANE,
            "oak_stairs", OAK_SLAB_BOTTOM, "oak", "white", LANTERN);

    /** Taiga: spruce planks + cobble, spruce corner logs. */
    private static final Palette TAIGA_SPRUCE = new Palette(
            SPRUCE_PLANKS, COBBLE, SPRUCE_LOG_Y, bs("minecraft:spruce_log[axis=x]"),
            SPRUCE_PLANKS, SPRUCE_SLAB_TOP, SPRUCE_SLAB_BOTTOM, GLASS_PANE,
            "spruce_stairs", SPRUCE_SLAB_BOTTOM, "spruce", "blue", LANTERN);

    /** Savanna: acacia planks + acacia logs over stripped-acacia trim. */
    private static final Palette SAVANNA_ACACIA = new Palette(
            bs("minecraft:acacia_planks"), bs("minecraft:stripped_acacia_log[axis=x]"),
            bs("minecraft:acacia_log[axis=y]"), bs("minecraft:acacia_log[axis=x]"),
            bs("minecraft:acacia_planks"), bs("minecraft:acacia_slab[type=top]"),
            bs("minecraft:acacia_slab[type=bottom]"), GLASS_PANE,
            "acacia_stairs", bs("minecraft:acacia_slab[type=bottom]"), "acacia", "orange", LANTERN);

    /** Desert: cut sandstone walls + smooth-sandstone trim, sandstone-stair roof. */
    private static final Palette DESERT_SANDSTONE = new Palette(
            bs("minecraft:cut_sandstone"), bs("minecraft:smooth_sandstone"),
            bs("minecraft:chiseled_sandstone"), bs("minecraft:sandstone"),
            bs("minecraft:smooth_sandstone"), bs("minecraft:sandstone_slab[type=top]"),
            bs("minecraft:sandstone_slab[type=bottom]"), GLASS_PANE,
            "sandstone_stairs", bs("minecraft:sandstone_slab[type=bottom]"), "jungle", "yellow", LANTERN);

    /** Snowy: spruce + snow-block trim, spruce corner logs, blue bed. */
    private static final Palette SNOWY = new Palette(
            SPRUCE_PLANKS, bs("minecraft:snow_block"), SPRUCE_LOG_Y, bs("minecraft:spruce_log[axis=x]"),
            SPRUCE_PLANKS, SPRUCE_SLAB_TOP, SPRUCE_SLAB_BOTTOM, GLASS_PANE,
            "spruce_stairs", SPRUCE_SLAB_BOTTOM, "spruce", "light_blue", LANTERN);

    /** Jungle: jungle planks + jungle logs, cyan bed. */
    private static final Palette JUNGLE = new Palette(
            bs("minecraft:jungle_planks"), bs("minecraft:stripped_jungle_log[axis=x]"),
            bs("minecraft:jungle_log[axis=y]"), bs("minecraft:jungle_log[axis=x]"),
            bs("minecraft:jungle_planks"), bs("minecraft:jungle_slab[type=top]"),
            bs("minecraft:jungle_slab[type=bottom]"), GLASS_PANE,
            "jungle_stairs", bs("minecraft:jungle_slab[type=bottom]"), "jungle", "cyan", LANTERN);

    /** Mangrove: mangrove planks + mangrove logs, red bed. */
    private static final Palette MANGROVE = new Palette(
            bs("minecraft:mangrove_planks"), bs("minecraft:stripped_mangrove_log[axis=x]"),
            bs("minecraft:mangrove_log[axis=y]"), bs("minecraft:mangrove_log[axis=x]"),
            bs("minecraft:mangrove_planks"), bs("minecraft:mangrove_slab[type=top]"),
            bs("minecraft:mangrove_slab[type=bottom]"), GLASS_PANE,
            "mangrove_stairs", bs("minecraft:mangrove_slab[type=bottom]"), "mangrove", "red", LANTERN);

    /** Cherry: cherry planks + cherry logs, pink bed, soul-soft light read. */
    private static final Palette CHERRY = new Palette(
            bs("minecraft:cherry_planks"), bs("minecraft:stripped_cherry_log[axis=x]"),
            bs("minecraft:cherry_log[axis=y]"), bs("minecraft:cherry_log[axis=x]"),
            bs("minecraft:cherry_planks"), bs("minecraft:cherry_slab[type=top]"),
            bs("minecraft:cherry_slab[type=bottom]"), GLASS_PANE,
            "cherry_stairs", bs("minecraft:cherry_slab[type=bottom]"), "cherry", "pink", LANTERN);

    /** Badlands: terracotta walls + smooth-sandstone trim, brick-stair roof. */
    private static final Palette BADLANDS_TERRACOTTA = new Palette(
            bs("minecraft:terracotta"), bs("minecraft:smooth_sandstone"),
            bs("minecraft:cut_sandstone"), bs("minecraft:sandstone"),
            bs("minecraft:smooth_sandstone"), bs("minecraft:brick_slab[type=top]"),
            bs("minecraft:brick_slab[type=bottom]"), GLASS_PANE,
            "brick_stairs", bs("minecraft:brick_slab[type=bottom]"), "jungle", "orange", LANTERN);

    /** Dark-oak medieval: stone-brick walls + dark-oak corner posts, dark-oak doors. */
    private static final Palette DARK_OAK_MEDIEVAL = new Palette(
            STONE_BRICKS, DARK_OAK_PLANKS,
            bs("minecraft:dark_oak_log[axis=y]"), bs("minecraft:dark_oak_log[axis=x]"),
            DARK_OAK_PLANKS, STONE_BRICK_SLAB_TOP, STONE_BRICK_SLAB_BOTTOM, GLASS_PANE,
            "stone_brick_stairs", STONE_BRICK_SLAB_BOTTOM, "dark_oak", "red", LANTERN);

    /** Modern (optional): white-concrete walls + light-gray trim, glass, flat-feel roof. */
    private static final Palette MODERN_CONCRETE = new Palette(
            bs("minecraft:white_concrete"), bs("minecraft:light_gray_concrete"),
            bs("minecraft:smooth_quartz"), bs("minecraft:smooth_quartz"),
            bs("minecraft:smooth_quartz"), bs("minecraft:smooth_quartz_slab[type=top]"),
            bs("minecraft:smooth_quartz_slab[type=bottom]"), GLASS,
            "quartz_stairs", bs("minecraft:smooth_quartz_slab[type=bottom]"), "oak", "gray", SEA_LANTERN);

    // ---- generic gap-fillers (3D fills / shells / rooms) --------------------

    /**
     * A solid 3D box fill of {@code mat} over [x0..x1] × [y0..y1] × [z0..z1]
     * (inclusive). The 2D {@link #floor} fills a single y-layer; this is its
     * volumetric sibling for plinths, thick walls, and filled cores. Order of
     * the two corners doesn't matter — it normalises low/high per axis.
     */
    private static void solid(Blueprint.Builder b, int x0, int y0, int z0, int x1, int y1, int z1,
                              BlueprintBlockState mat) {
        int lx = Math.min(x0, x1), hx = Math.max(x0, x1);
        int ly = Math.min(y0, y1), hy = Math.max(y0, y1);
        int lz = Math.min(z0, z1), hz = Math.max(z0, z1);
        for (int y = ly; y <= hy; y++) {
            for (int x = lx; x <= hx; x++) {
                for (int z = lz; z <= hz; z++) {
                    b.set(x, y, z, mat);
                }
            }
        }
    }

    /**
     * A hollow box "room shell" for functional rooms: the four {@code wall}
     * faces (yo+1..y1-1 between floor and ceiling), a solid {@code floorMat}
     * slab at {@code y0}, and a solid {@code ceilMat} slab at {@code y1}, over
     * [x0..x1] × [z0..z1]. The interior (between floor and ceiling, inside the
     * wall ring) is LEFT OPEN per the air-skip rule so the room is enterable —
     * carve a doorway afterward by overwriting wall cells with {@link #door2}
     * (or simply omit a wall cell before calling if you want the gap pre-made).
     * Pass {@code ceilMat == null} for an open-topped room.
     */
    private static void roomShell(Blueprint.Builder b, int x0, int y0, int z0, int x1, int y1, int z1,
                                  BlueprintBlockState wall, BlueprintBlockState floorMat, BlueprintBlockState ceilMat) {
        int lx = Math.min(x0, x1), hx = Math.max(x0, x1);
        int ly = Math.min(y0, y1), hy = Math.max(y0, y1);
        int lz = Math.min(z0, z1), hz = Math.max(z0, z1);
        floor(b, ly, lx, lz, hx, hz, floorMat);
        if (hy - ly >= 2) {
            walls(b, lx, lz, hx, hz, ly + 1, hy - 1, wall);
        } else if (hy - ly == 1) {
            // 2-high box: the single mid course IS the wall ring
            walls(b, lx, lz, hx, hz, ly + 1, hy - 1 < ly + 1 ? ly + 1 : hy - 1, wall);
        }
        if (ceilMat != null && hy > ly) {
            floor(b, hy, lx, lz, hx, hz, ceilMat);
        }
    }

    // ---- parametric, palette-driven house -----------------------------------

    /**
     * A clean, ENTERABLE rectangular house driven entirely by a {@link Palette},
     * composed from the existing geometry helpers. Footprint is
     * [x0..x1] × [z0..z1]; the walkable interior floor sits at {@code y=0} (its
     * top face), walls rise {@code y=1..wallH}, and a gable roof closes the top.
     *
     * <p>What it lays down, in order:
     * <ol>
     *   <li>solid {@code plankFloor} foundation at {@code y=0} (walkable);</li>
     *   <li>{@code wall} ring {@code y=1..wallH} with {@code logPillarY} corner
     *       posts of equal height (no corner nub);</li>
     *   <li>a few {@code windowPane} windows centred on each long wall and the
     *       back wall;</li>
     *   <li>a doorway in the centre of the north ({@code z=z0}) wall — the two
     *       door cells get a {@link #door2} opening inward; the cell is the only
     *       break in the ring, left open by writing the door (a 2-block state),
     *       NOT by setting air;</li>
     *   <li>a gable roof ({@link #gableRoofX} + {@link #gableEndFill}) seated at
     *       {@code y=wallH} using the palette's {@link Palette#roofStairName} and
     *       {@link Palette#roofSlab}; if the footprint is nearly square
     *       ({@code |W−L| ≤ 1}) a {@link #hipRoof} is used instead so the roof
     *       reads correctly;</li>
     *   <li>if {@code furnish}, minimal interior props on the {@code y=1} floor:
     *       a {@link #bed}, a crafting table, a chest, and a {@link Palette#lightBlock}.</li>
     * </ol>
     *
     * <p>The interior above the floor is deliberately never written, so the
     * player can walk in through the door and stand inside (see the ENTERABLE
     * note in the conventions header).
     *
     * @param wallH top y of the wall ring (so walls occupy y=1..wallH, ≥3 advised)
     */
    private static void house(Blueprint.Builder b, int x0, int z0, int x1, int z1,
                              int wallH, Palette p, boolean furnish) {
        int lx = Math.min(x0, x1), hx = Math.max(x0, x1);
        int lz = Math.min(z0, z1), hz = Math.max(z0, z1);
        // 1) walkable foundation floor at y=0
        floor(b, 0, lx, lz, hx, hz, p.plankFloor);
        // 2) wall ring + equal-height corner posts (no nub)
        walls(b, lx, lz, hx, hz, 1, wallH, p.wall);
        corners(b, lx, lz, hx, hz, 1, wallH, p.logPillarY);
        // 3) windows centred (vertically mid-wall) on the two long walls + back wall
        int wy = Math.max(2, 1 + (wallH - 1) / 2); // a mid-height course, ≥2
        int cz = (lz + hz) / 2;
        int cx = (lx + hx) / 2;
        if (hz - lz >= 4) {
            window2(b, lx, wy, cz, p.windowPane, null); // west long wall
            window2(b, hx, wy, cz, p.windowPane, null); // east long wall
        }
        window2(b, cx, wy, hz, p.windowPane, null);     // south (back) wall
        // 4) door centred on the north wall (z=lz), opening inward (faces south)
        door2(b, cx, 1, lz, p.doorWood, "N");
        // 5) roof seated on the wall plate at y=wallH
        int w = hx - lx, l = hz - lz;
        if (Math.abs(w - l) <= 1) {
            hipRoof(b, lx, lz, hx, hz, wallH, p.roofStairName, p.roofSlab);
        } else {
            gableRoofX(b, lx, lz, hx, hz, wallH, p.roofStairName, p.roofSlab);
            gableEndFill(b, lx, lz, hx, hz, wallH, p.wall);
        }
        // 6) optional minimal furnishings on the standing floor (y=1)
        if (furnish) {
            bed(b, lx + 1, 1, hz - 1, p.bedColor, "south"); // head near back wall
            b.set(hx - 1, 1, hz - 1, CRAFTING_TABLE);
            b.set(hx - 1, 1, lz + 1, CHEST);
            b.set(lx + 1, 1, lz + 1, p.lightBlock);
        }
    }

    // ---- pagoda / stacked-eave roof (Japanese set) --------------------------

    /**
     * One pagoda eave tier — a single square roof skirt with a deep overhang and
     * the four signature UPTURNED CORNERS — centred on {@code (cx,cz)} with its
     * eave ring seated at {@code y=ey}. Returns the y of the corner finger tops
     * (the highest cell this tier touches) so callers can stack the next tier or
     * a finial above it.
     *
     * <p>Geometry, from the wall line outward (this is what makes it read as a
     * pagoda and not a generic stepped roof):
     * <ul>
     *   <li><b>Deep overhanging eave.</b> The eave ring of inward-facing
     *       {@code half=bottom} stairs sits at {@code (cx±half, cz±half)} — i.e.
     *       {@code half} blocks out from centre — and OVERHANGS the body, which
     *       must be inset. One course below ({@code ey-1}) an outward-facing
     *       {@code half=top} "bracket" ring sits one cell further out (at
     *       {@code half+1}), giving the underside the deep-bracket look and
     *       extending the visible lip to {@code half+1} from centre.</li>
     *   <li><b>Upturned corners.</b> At each corner a continuous rising diagonal
     *       reads as the signature flick: the bracket-lip corner sits at
     *       {@code ey-1} (on the {@code half+1} diagonal), the eave ring corner at
     *       {@code ey}, and a curl tip — an outward-facing {@code half=top} stair —
     *       sits one cell further out AND one course higher ({@code ey+1}, on the
     *       {@code half+1} diagonal). So the corner steps lip→ring→tip up and out,
     *       visibly curling the roofline UP instead of cutting off flat.</li>
     * </ul>
     *
     * <p><b>Footprint warning:</b> the bracket lip AND the upturned curl tip reach
     * {@code half+1} from centre, so a tier with half-extent {@code half} needs the
     * build footprint to span at least {@code [cx-(half+1) .. cx+(half+1)]} in both
     * axes. Size the body inset accordingly (e.g. an 11-wide footprint, centre 5,
     * tops out at a bottom tier of {@code half=4} whose lip just reaches x=0/10).
     *
     * @param ey        y of the eave ring (its overhang course)
     * @param half      half-extent of this tier's eave ring (ring width = 2*half+1)
     * @param roofStair bare stair-block NAME (e.g. "dark_oak_stairs")
     * @param capSlab   slab that closes the tier interior so it isn't see-through
     * @return the y of this tier's upturned corner curl tips ({@code ey+1})
     */
    private static int pagodaEaveTier(Blueprint.Builder b, int cx, int cz, int ey, int half,
                                      String roofStair, BlueprintBlockState capSlab) {
        // inward-facing eave slope (the roof pitch), half=bottom
        BlueprintBlockState north = bs("minecraft:" + roofStair + "[facing=south,half=bottom,shape=straight]");
        BlueprintBlockState south = bs("minecraft:" + roofStair + "[facing=north,half=bottom,shape=straight]");
        BlueprintBlockState west = bs("minecraft:" + roofStair + "[facing=east,half=bottom,shape=straight]");
        BlueprintBlockState east = bs("minecraft:" + roofStair + "[facing=west,half=bottom,shape=straight]");
        // outward-facing under-bracket, half=top (the deep-eave soffit look)
        BlueprintBlockState brkN = bs("minecraft:" + roofStair + "[facing=north,half=top,shape=straight]");
        BlueprintBlockState brkS = bs("minecraft:" + roofStair + "[facing=south,half=top,shape=straight]");
        BlueprintBlockState brkW = bs("minecraft:" + roofStair + "[facing=west,half=top,shape=straight]");
        BlueprintBlockState brkE = bs("minecraft:" + roofStair + "[facing=east,half=top,shape=straight]");

        int ax0 = cx - half, ax1 = cx + half, az0 = cz - half, az1 = cz + half;
        int bx0 = ax0 - 1, bx1 = ax1 + 1, bz0 = az0 - 1, bz1 = az1 + 1; // bracket extent (half+1)

        // 1) deep-eave under-bracket one course below the ring (outward-facing top
        //    stairs); INCLUDING the corner cells (bx,bz) so each corner has a lip
        //    block to anchor the upward curl on.
        for (int x = bx0; x <= bx1; x++) {
            b.set(x, ey - 1, bz0, brkN);
            b.set(x, ey - 1, bz1, brkS);
        }
        for (int z = bz0; z <= bz1; z++) {
            b.set(bx0, ey - 1, z, brkW);
            b.set(bx1, ey - 1, z, brkE);
        }
        // 2) the eave ring proper: inward-facing slope at ey
        for (int x = ax0; x <= ax1; x++) {
            b.set(x, ey, az0, north);
            b.set(x, ey, az1, south);
        }
        for (int z = az0; z <= az1; z++) {
            b.set(ax0, ey, z, west);
            b.set(ax1, ey, z, east);
        }
        // 3) close the tier interior so it isn't see-through to the next tier
        if (half >= 1) {
            floor(b, ey, ax0 + 1, az0 + 1, ax1 - 1, az1 - 1, capSlab);
        }
        // 4) UPTURNED CORNERS — the signature flick. Each corner is a continuous
        //    rising diagonal: bracket-lip corner at ey-1 (placed in step 1), eave
        //    ring corner at ey (placed in step 2), then a curl TIP one cell further
        //    out and one course up (ey+1, at the half+1 diagonal). The tip is an
        //    outward-facing top-half stair, so the roofline visibly curls upward.
        // The curl tip at each corner is an outward-facing top-half stair, one cell
        // diagonally outboard (half+1) and one course up — completing the
        // lip→ring→tip upward step. North corners flick north, south corners south.
        b.set(bx0, ey + 1, bz0, brkN);
        b.set(bx1, ey + 1, bz0, brkN);
        b.set(bx0, ey + 1, bz1, brkS);
        b.set(bx1, ey + 1, bz1, brkS);
        return ey + 1;
    }

    /**
     * A full stacked, telescoping, upturned-eave pagoda roof of {@code tiers}
     * tiers centred on {@code (cx,cz)}, the lowest eave seated at {@code y=cy}.
     * Each tier is a {@link #pagodaEaveTier} (deep overhang + upturned corners),
     * smaller than the one below, separated by a short body "drum" so the tiers
     * read as distinct stories rather than one mushroom. The apex gets a finial:
     * a slab base, a short mast, and {@code cap}.
     *
     * <p><b>Footprint warning:</b> the bottom tier's bracket lip reaches
     * {@code baseHalf+1} from {@code (cx,cz)} — size the body footprint so that
     * {@code [cx-(baseHalf+1) .. cx+(baseHalf+1)]} is in-bounds in both axes (an
     * 11-wide footprint centred at 5 fits a {@code baseHalf=4} bottom tier).
     *
     * @param baseHalf  half-extent of the bottom tier (ring width = 2*baseHalf+1)
     * @param tiers     number of stacked tiers (≥1; odd counts are traditional)
     * @param roofStair bare stair-block NAME (e.g. "dark_oak_stairs")
     * @param eaveSlab  slab used to close each tier interior + finial base
     * @param cap       the apex finial block (e.g. a lantern or end rod)
     */
    private static void pagodaRoof(Blueprint.Builder b, int cx, int cz, int cy, int baseHalf, int tiers,
                                   String roofStair, BlueprintBlockState eaveSlab, BlueprintBlockState cap) {
        int y = cy;
        for (int k = 0; k < tiers; k++) {
            int h = baseHalf - k;
            if (h < 0) break;
            int top = pagodaEaveTier(b, cx, cz, y, h, roofStair, eaveSlab);
            // short body drum between tiers (one course) so stories read distinctly
            if (k < tiers - 1) {
                int nh = Math.max(0, h - 1); // next tier's footprint, drum sits inside it
                floor(b, top, cx - nh, cz - nh, cx + nh, cz + nh, eaveSlab);
                y = top + 1; // next eave one course above the corner tips + drum
            } else {
                y = top;
            }
        }
        // finial: a short slab+cap stack on the central axis
        b.set(cx, y, cz, eaveSlab);
        b.set(cx, y + 1, cz, cap);
    }

    // =====================================================================
    //  THE 23 BUILDS  (docs/blueprint-specs.md §3, coverage matrix §4 order)
    // =====================================================================

    // ----- Small (T3–T4 footprint) -----

    /** §3.4 Garden Shed. 3×3×4 (W×L×H) → builder(3,4,3). Spruce, lean-to roof. */
    private static Blueprint gardenShed() {
        Blueprint.Builder b = Blueprint.builder("Garden Shed", 3, 4, 3);
        floor(b, 0, 0, 0, 2, 2, SPRUCE_PLANKS);
        walls(b, 0, 0, 2, 2, 1, 2, SPRUCE_PLANKS);
        corners(b, 0, 0, 2, 2, 1, 2, SPRUCE_LOG_Y);
        door2(b, 1, 1, 0, "spruce", "N");
        window2(b, 2, 1, 1, GLASS_PANE, null);
        b.set(1, 1, 2, COMPOSTER);
        b.set(2, 1, 2, BARREL);
        // single-pitch lean-to roof at y=3: stair lip at the front, slab to the back
        for (int x = 0; x <= 2; x++) {
            b.set(x, 3, 0, bs("minecraft:spruce_stairs[facing=north,half=bottom,shape=straight]"));
            b.set(x, 3, 1, SPRUCE_SLAB_BOTTOM);
            b.set(x, 3, 2, SPRUCE_SLAB_BOTTOM);
        }
        return b.build();
    }

    /** §3.5 Campsite. 5×5×3 (W×L×H) → builder(5,3,5). Grass, lit campfire, tent. */
    private static Blueprint campfireSite() {
        Blueprint.Builder b = Blueprint.builder("Campsite", 5, 3, 5);
        floor(b, 0, 0, 0, 4, 4, GRASS_BLOCK);
        floor(b, 0, 1, 1, 3, 3, DIRT_PATH);
        // cobble fire-ring border (8 cells around centre)
        for (int x = 1; x <= 3; x++) {
            for (int z = 1; z <= 3; z++) {
                if (!(x == 2 && z == 2)) b.set(x, 0, z, COBBLE);
            }
        }
        b.set(2, 0, 2, DIRT_PATH);
        b.set(2, 1, 2, CAMPFIRE); // lit campfire on the path centre
        // log seats (both on the north side, clear of the z=3..4 tent)
        b.set(1, 1, 1, OAK_LOG_X);
        b.set(3, 1, 1, OAK_LOG_X);
        // A-frame tent at the back (z=4)
        b.set(1, 1, 4, bs("minecraft:oak_stairs[facing=west,half=bottom,shape=straight]"));
        b.set(3, 1, 4, bs("minecraft:oak_stairs[facing=east,half=bottom,shape=straight]"));
        b.set(1, 1, 3, WHITE_WOOL);
        b.set(3, 1, 3, WHITE_WOOL);
        b.set(2, 2, 4, WHITE_WOOL); // ridge
        b.set(2, 2, 3, WHITE_WOOL);
        // light pole with backed lantern
        pillar(b, 4, 0, 1, 2, OAK_FENCE);
        b.set(4, 2, 0, OAK_FENCE);
        b.set(4, 1, 0, HANGING_LANTERN);
        return b.build();
    }

    /** §3.1 Village Well. 5×5×6 (W×L×H) → builder(5,6,5). Open canopy over visible water. */
    private static Blueprint well() {
        Blueprint.Builder b = Blueprint.builder("Village Well", 5, 6, 5);
        // foundation ring (y=0): 5×5 cobble with 3×3 hollow centre holding water
        floor(b, 0, 0, 0, 4, 4, COBBLE);
        for (int x = 1; x <= 3; x++) {
            for (int z = 1; z <= 3; z++) {
                b.set(x, 0, z, WATER); // visible water flush with the rim
            }
        }
        // rim coping (y=1): cobble walls on the perimeter only, centre open
        fenceRing(b, 1, 0, 0, 4, 4, COBBLE_WALL);
        // four oak-fence posts y=1..4
        pillar(b, 0, 0, 1, 4, OAK_FENCE);
        pillar(b, 4, 0, 1, 4, OAK_FENCE);
        pillar(b, 0, 4, 1, 4, OAK_FENCE);
        pillar(b, 4, 4, 1, 4, OAK_FENCE);
        // canopy (y=5): slab roof resting on the posts
        flatRoof(b, 5, 0, 0, 4, 4, COBBLE_SLAB_TOP);
        // backed hanging lantern over the water (chain up to the slab)
        b.set(2, 4, 2, CHAIN);
        b.set(2, 3, 2, HANGING_LANTERN);
        return b.build();
    }

    /**
     * Wishing Well (cottagecore). 5×5×7 (W×L×H) → builder(5,7,5). A mossy-cobble ring
     * around a visible 3×3 water core, four oak posts, a peaked oak-plank/stair shingled
     * roof with closed gable ends, and a chain dropping a cauldron "bucket" over the water.
     * Distinct from {@link #well()}: this adds the roof + bucket-on-rope. Not enterable.
     */
    private static Blueprint wishingWell() {
        Blueprint.Builder b = Blueprint.builder("Wishing Well", 5, 7, 5);
        BlueprintBlockState mossyWall = bs("minecraft:mossy_cobblestone_wall");
        // mossy base (y=0): 5×5 with a 3×3 visible water core flush with the rim
        floor(b, 0, 0, 0, 4, 4, MOSSY_COBBLE);
        for (int x = 1; x <= 3; x++) {
            for (int z = 1; z <= 3; z++) {
                b.set(x, 0, z, WATER);
            }
        }
        // mossy-cobble coping ring (y=1): perimeter walls only, centre open over water
        fenceRing(b, 1, 0, 0, 4, 4, mossyWall);
        // four oak posts at the corners, y=1..3
        pillar(b, 0, 0, 1, 3, OAK_FENCE);
        pillar(b, 4, 0, 1, 3, OAK_FENCE);
        pillar(b, 0, 4, 1, 3, OAK_FENCE);
        pillar(b, 4, 4, 1, 3, OAK_FENCE);
        // plank plate (y=4) tying the post tops together — the roof eave + chain anchor
        fenceRing(b, 4, 0, 0, 4, 4, OAK_PLANKS);
        // peaked, shingled oak roof over the plate with closed gable ends (peak y=6)
        gableRoofX(b, 0, 0, 4, 4, 4, "oak_stairs", OAK_SLAB_BOTTOM);
        gableEndFill(b, 0, 0, 4, 4, 4, OAK_PLANKS);
        // bucket-on-rope: an oak crossbeam under the ridge anchors a chain that drops a
        // cauldron "bucket" over the water. Chain needs a solid block above to read as
        // hanging; the (2,4,2) plank crossbeam (centre of the plate) provides it.
        b.set(2, 4, 2, OAK_PLANKS); // crossbeam / chain anchor
        b.set(2, 3, 2, CHAIN);
        b.set(2, 2, 2, CAULDRON);   // the bucket
        return b.build();
    }

    /**
     * Phase 2 §I — Statue Pedestal. 5×5×11 (W×L×H) → builder(5,11,5). A heroic
     * town-square knight/sentinel: a stepped stone-brick/quartz inscribed plinth,
     * then a blocky humanoid figure (legs, torso, arms, head) in iron / smooth
     * stone / andesite, one arm raised holding a sword. Decorative, not enterable.
     *
     * <p>AXES: x=W (0..4), z=depth (0..4). The figure faces the viewer at high z
     * (its front is the z=2..3 side); the raised sword arm is on the west (x=0..1).
     *
     * <p>RENDER-SAFETY: the sword is a single {@code iron_bars} crossguard at the
     * raised-hand height plus a stack of {@code end_rod} blade segments above it.
     * A lone bars block with no HORIZONTAL neighbour prints as an invisible stub
     * (see {@code CuratedBlueprintRenderIntegrityGameTests} — it only counts ±x/±z
     * connections, NOT a bars cell stacked above/below). So the one crossguard cell
     * is placed flush beside the raised iron-block hand (a sturdy full face → it
     * connects and renders), and the blade itself uses {@code end_rod}, which is not
     * an {@code IronBarsBlock} and so renders as a clean vertical rod regardless of
     * neighbours — the spec's sanctioned non-bars blade.
     */
    private static Blueprint statuePedestal() {
        Blueprint.Builder b = Blueprint.builder("Statue Pedestal", 5, 11, 5);
        BlueprintBlockState smoothStone = bs("minecraft:smooth_stone");
        BlueprintBlockState polishedAndesite = bs("minecraft:polished_andesite");
        BlueprintBlockState chiseledQuartz = bs("minecraft:chiseled_quartz_block");
        BlueprintBlockState quartzPillar = bs("minecraft:quartz_pillar[axis=y]");

        // ── STEPPED PLINTH (y0..y2) ────────────────────────────────────────
        // y0: full 5×5 stone-brick footing
        floor(b, 0, 0, 0, 4, 4, STONE_BRICKS);
        // a chiseled-stone-brick "inscription" course around the footing rim,
        // front face (z=4) reads as the dedication panel
        line(b, 0, 0, 4, 4, 4, CHISELED_STONE_BRICKS);
        // y1: inset 4×4 (x,z = 0..3 offset to centre) stone-brick step with a
        // chiseled-quartz front band — the bright dressed-stone tier
        floor(b, 1, 1, 1, 3, 3, STONE_BRICKS);
        line(b, 1, 1, 3, 3, 3, chiseledQuartz);
        // y2: polished-andesite cap (the dressed pedestal top the figure stands on)
        floor(b, 2, 1, 1, 3, 3, polishedAndesite);
        // four quartz-pillar corner posts framing the cap, y1..y2 (plinth columns)
        pillar(b, 1, 1, 1, 2, quartzPillar);
        pillar(b, 3, 1, 1, 2, quartzPillar);
        pillar(b, 1, 3, 1, 2, quartzPillar);
        pillar(b, 3, 3, 1, 2, quartzPillar);

        // ── HUMANOID FIGURE (y3..y9), centred on the cap, front toward +z ──
        // Legs: two iron-block legs at x=1 and x=3, z=2, y3..y4
        pillar(b, 1, 2, 3, 4, IRON_BLOCK);
        pillar(b, 3, 2, 3, 4, IRON_BLOCK);
        // a smooth-stone crotch/skirt tie at the leg tops so the legs read joined
        b.set(2, 4, 2, smoothStone);

        // Torso: a 3-wide × 3-tall iron/stone block (x=1..3, y5..y7, z=2) with a
        // chiseled-quartz chest plate (the sentinel's breastplate) on the front.
        solid(b, 1, 5, 2, 3, 7, 2, IRON_BLOCK);
        b.set(2, 6, 2, chiseledQuartz);   // breastplate emblem, centred on the chest

        // Arms hang off the torso sides. RIGHT arm (east, x=3) hangs down at the
        // side: smooth-stone shoulder + iron forearm, y5..y6 at x=3, z=2 is already
        // the torso edge — extend it outward one column so the silhouette reads as
        // a distinct arm rather than a flat slab. Place the arm at x=3 with a hand
        // block dropping to y4 (relaxed, at the side).
        b.set(3, 5, 2, smoothStone);      // right shoulder
        b.set(3, 4, 2, IRON_BLOCK);       // right hand at the side

        // LEFT arm (west, x=1) is RAISED, holding the sword aloft. Shoulder at the
        // torso, then the arm rises: smooth-stone upper arm (y6), iron forearm (y7),
        // raised iron hand (y8). The hand is the sturdy anchor the blade connects to.
        b.set(1, 6, 2, smoothStone);      // left shoulder/upper arm
        b.set(1, 7, 2, IRON_BLOCK);       // left forearm (continues torso column)
        b.set(1, 8, 2, IRON_BLOCK);       // raised left HAND — sword anchor

        // Head: a single smooth-stone head on a polished-andesite neck, centred.
        b.set(2, 8, 2, polishedAndesite); // neck
        b.set(2, 9, 2, smoothStone);      // head

        // ── SWORD, held aloft by the raised left hand ──────────────────────
        // Rises at x=0 (just west of the raised hand at x=1). The crossguard cell
        // (0,8,2) is iron_bars sitting horizontally flush against the iron-block
        // hand (1,8,2) → sturdy full face → the bars connect and render (no stub).
        // The blade above is end_rod — not an IronBarsBlock, so it renders as a
        // clean vertical rod with no connection requirement.
        b.set(0, 8, 2, IRON_BARS);        // crossguard / hilt — anchored to the hand
        b.set(0, 9, 2, END_ROD);          // blade
        b.set(0, 10, 2, END_ROD);         // blade tip
        return b.build();
    }

    /**
     * §I Obelisk. 3×3 footprint → builder(3,16,3). A slender four-sided landmark
     * monolith (Cleopatra's-Needle silhouette): a stepped smooth-stone base with a
     * chiseled-stone-brick inscription course, a tall 3×3 dressed-stone shaft framed
     * by chiseled-brick corner columns and girdled by a blackstone+gold inscription
     * band, then a setback course feeding a gilded 4-sided pyramidal capstone that
     * converges to a {@code gold_block} tip. Tall, solid, not enterable. T3 disc.
     */
    private static Blueprint obelisk() {
        Blueprint.Builder b = Blueprint.builder("Obelisk", 3, 16, 3);
        BlueprintBlockState smoothStone = bs("minecraft:smooth_stone");
        BlueprintBlockState blackstone = bs("minecraft:blackstone");
        BlueprintBlockState goldBlock = bs("minecraft:gold_block");

        // ── STEPPED STONE BASE (y0..y3) ────────────────────────────────────
        // y0: full 3×3 smooth-stone footing
        floor(b, 0, 0, 0, 2, 2, smoothStone);
        // y1: a chiseled-stone-brick inscription course wrapping the base rim
        floor(b, 1, 0, 0, 2, 2, CHISELED_STONE_BRICKS);
        // y2..y3: smooth-stone plinth, the dressed-stone body the shaft rises from
        floor(b, 2, 0, 0, 2, 2, smoothStone);
        floor(b, 3, 0, 0, 2, 2, STONE_BRICKS);

        // ── TAPERING SHAFT (y4..y11) ───────────────────────────────────────
        // The shaft fills the 3×3 footprint as a solid smooth-stone column; the
        // four corner cells are swapped to chiseled stone bricks so every face
        // reads as a dressed, faceted edge (the four-sided monolith look).
        for (int y = 4; y <= 11; y++) {
            floor(b, y, 0, 0, 2, 2, smoothStone);
            b.set(0, y, 0, CHISELED_STONE_BRICKS); // NW corner column
            b.set(2, y, 0, CHISELED_STONE_BRICKS); // NE corner column
            b.set(0, y, 2, CHISELED_STONE_BRICKS); // SW corner column
            b.set(2, y, 2, CHISELED_STONE_BRICKS); // SE corner column
        }
        // Inscription band at y7: a full 3×3 blackstone girdle with a gold-block
        // centre on each of the four faces — the gilded dedication band.
        floor(b, 7, 0, 0, 2, 2, blackstone);
        b.set(1, 7, 0, goldBlock); // north face centre
        b.set(1, 7, 2, goldBlock); // south face centre
        b.set(0, 7, 1, goldBlock); // west face centre
        b.set(2, 7, 1, goldBlock); // east face centre

        // ── SETBACK + GILDED PYRAMIDAL CAPSTONE (y12..y15) ─────────────────
        // y12: a chiseled-stone-brick neck course the pyramidion springs from.
        floor(b, 12, 0, 0, 2, 2, CHISELED_STONE_BRICKS);
        // y13: four blackstone-stair slopes facing inward around a smooth-stone
        // core — the start of the four-sided gilded cap.
        b.set(1, 13, 1, goldBlock); // core
        b.set(1, 13, 0, bs("minecraft:blackstone_stairs[facing=south,half=bottom,shape=straight]"));
        b.set(1, 13, 2, bs("minecraft:blackstone_stairs[facing=north,half=bottom,shape=straight]"));
        b.set(0, 13, 1, bs("minecraft:blackstone_stairs[facing=east,half=bottom,shape=straight]"));
        b.set(2, 13, 1, bs("minecraft:blackstone_stairs[facing=west,half=bottom,shape=straight]"));
        // (the four base corners of the cap stay open → the pyramidion reads as a
        //  4-sided point, not a flat block)
        // y14: a gold-block course narrowing to the apex
        b.set(1, 14, 1, goldBlock);
        // y15: the gilded tip — a single gold block crowning the monument
        b.set(1, 15, 1, goldBlock);
        return b.build();
    }

    /**
     * §I Ruin Pillar. 3×3 footprint → builder(3,9,3). A crumbling, partly-collapsed
     * stone column — "WorldEdit ruin" decor. A scatter of cobble / mossy-cobble rubble
     * and stone-brick-slab fragments rings the base, from which a weathered stone-brick
     * shaft (cracked / mossy / chiseled mix) rises off-centre and SNAPS partway up: the
     * top course is a broken stair+slab lip suggesting the sheared-off shaft, with a lone
     * leaning fragment beside it. Atmospheric, not enterable. Standalone or cluster-able.
     * Vanilla-only, no vines/leaves (UNVALUED) — the ruin reads from cracked/mossy stone.
     * T3 footprint, T2 disc.
     */
    private static Blueprint ruinPillar() {
        Blueprint.Builder b = Blueprint.builder("Ruin Pillar", 3, 8, 3);
        BlueprintBlockState stoneBrickStairN =
                bs("minecraft:stone_brick_stairs[facing=north,half=bottom,shape=straight]");
        BlueprintBlockState stoneBrickStairE =
                bs("minecraft:stone_brick_stairs[facing=east,half=bottom,shape=straight]");

        // ── RUBBLE FIELD (y0) ──────────────────────────────────────────────
        // A broken scatter ringing the base — deliberately NOT a full 3×3 floor so
        // the air-skip leaves gaps and it reads as a collapse pile, not a platform.
        // The shaft footing sits at (1,*,1); rubble fans out toward the NW/SE.
        b.set(0, 0, 0, MOSSY_COBBLE);          // NW corner chunk
        b.set(1, 0, 0, COBBLE);                 // north-edge cobble
        b.set(0, 0, 1, COBBLE);                 // west-edge cobble
        b.set(1, 0, 1, MOSSY_STONE_BRICKS);     // shaft footing (mossy, weathered)
        b.set(2, 0, 1, MOSSY_COBBLE);           // east rubble lump
        b.set(1, 0, 2, COBBLE);                 // south-edge cobble
        b.set(2, 0, 2, MOSSY_COBBLE);           // SE corner chunk
        // toppled stone-brick-slab fragments lying flat in the rubble
        b.set(0, 0, 2, STONE_BRICK_SLAB_BOTTOM); // SW fallen slab fragment
        b.set(2, 0, 0, STONE_BRICK_SLAB_BOTTOM); // NE fallen slab fragment

        // ── WEATHERED SHAFT (y1..y5), off-centre on the (1,1) footing ──────
        // A worn, mismatched stone-brick stack — the surviving lower drum of the
        // column. Material degrades upward (mossy → mixed → cracked) toward the break.
        b.set(1, 1, 1, MOSSY_STONE_BRICKS);     // damp, mossy base course
        b.set(1, 2, 1, STONE_BRICKS);           // intact mid course
        b.set(1, 3, 1, CRACKED_STONE_BRICKS);   // cracking begins
        b.set(1, 4, 1, CHISELED_STONE_BRICKS);  // a decorative drum course (worn relief)
        b.set(1, 5, 1, CRACKED_STONE_BRICKS);   // last full course before the break
        // a leaning chunk fused to the shaft (the column slumping as it failed)
        b.set(2, 1, 1, MOSSY_COBBLE);           // bulge of fallen masonry at the foot
        b.set(0, 2, 1, CRACKED_STONE_BRICKS);   // dislodged block clinging to the west face

        // ── SHEARED-OFF TOP (y6..y7) ───────────────────────────────────────
        // The shaft snaps: a half-slab lip + a stair "fracture face" suggesting the
        // broken cross-section, plus one cracked stub of the continuing column.
        b.set(1, 6, 1, CRACKED_STONE_BRICKS);   // jagged stub of the snapped shaft
        b.set(1, 6, 0, stoneBrickStairN);       // fracture lip overhanging north
        b.set(0, 6, 1, stoneBrickStairE);       // fracture lip overhanging west
        b.set(1, 7, 1, STONE_BRICK_SLAB_BOTTOM); // worn slab cap — the broken shaft top

        return b.build();
    }

    /**
     * Phase 2 §I — Cemetery Plot. 5×5×4 (W×L×H) → builder(5,4,5). A small walled
     * graveyard: a low stone-brick wall perimeter with an iron-bar gate on the front
     * (z=4), two stone-brick "cross" headstones, a slab grave-marker, two coarse-dirt
     * grave mounds, a gravel path, a dead oak-log stump, dead bushes, and lanterns on
     * the front gate posts. Decorative, not enterable. T1 disc.
     *
     * <p>AXES: x=W (0..4), z=depth (0..4). The gate faces the viewer at z=4; the two
     * mounds + headstones occupy the interior; the dead stump sits in the back corner.
     *
     * <p>PALETTE NOTE: the row lists podzol + cobwebs, both UNVALUED (strict-mode
     * unprintable) — omitted. The graveyard ground uses grass_block (a known economy
     * gap, exempt) with gravel + coarse_dirt accents (both FU-valued) instead, and
     * dead_bush (a BushBlock → structural/free) supplies the withered foliage.
     *
     * <p>RENDER-SAFETY: the gate uses {@code iron_bars}, which renders as an invisible
     * stub unless it has a connecting HORIZONTAL neighbour (another bars/pane, a WALLS
     * block, or a sturdy full face). Every gate bar is flanked by a stone-brick-wall
     * gate post (WALLS tag → connects) or another bar that chains back to a post, so no
     * bar ships as a stub (see {@code CuratedBlueprintRenderIntegrityGameTests}).
     */
    private static Blueprint cemeteryPlot() {
        Blueprint.Builder b = Blueprint.builder("Cemetery Plot", 5, 4, 5);
        BlueprintBlockState gravel = bs("minecraft:gravel");
        BlueprintBlockState coarseDirt = bs("minecraft:coarse_dirt");
        BlueprintBlockState deadBush = bs("minecraft:dead_bush");
        BlueprintBlockState stoneButton = bs("minecraft:stone_button[face=floor,facing=north,powered=false]");

        // ── GROUND (y0) ────────────────────────────────────────────────────
        // 5×5 grassy plot with a gravel approach path running up the centre (x=2)
        // from the front gate, and two coarse-dirt grave mounds flanking it.
        floor(b, 0, 0, 0, 4, 4, GRASS_BLOCK);
        line(b, 0, 2, 4, 2, 2, gravel);          // gravel path from gate (z=4) inward
        // two raised grave mounds (coarse dirt) — west grave + east grave
        b.set(1, 0, 1, coarseDirt);
        b.set(1, 0, 2, coarseDirt);
        b.set(3, 0, 1, coarseDirt);
        b.set(3, 0, 2, coarseDirt);

        // ── PERIMETER WALL (y1) ────────────────────────────────────────────
        // Low stone-brick wall around the plot, with a one-cell gate gap on the
        // front face (z=4) at x=2 for the iron-bar gate.
        line(b, 1, 0, 0, 4, 0, STONE_BRICK_WALL);   // back face (z=0)
        line(b, 1, 0, 0, 0, 4, STONE_BRICK_WALL);   // west face (x=0)
        line(b, 1, 4, 0, 4, 4, STONE_BRICK_WALL);   // east face (x=4)
        // front face (z=4): walls either side of the central gate gap at x=2
        b.set(0, 1, 4, STONE_BRICK_WALL);
        b.set(1, 1, 4, STONE_BRICK_WALL);           // west gate post (flanks the bars)
        b.set(3, 1, 4, STONE_BRICK_WALL);           // east gate post (flanks the bars)
        b.set(4, 1, 4, STONE_BRICK_WALL);

        // ── IRON-BAR GATE (y1) ─────────────────────────────────────────────
        // A single bars cell in the gate gap, flanked west+east by wall posts
        // (WALLS tag → both connect) so it renders as a proper gate, not a stub.
        b.set(2, 1, 4, IRON_BARS);

        // ── GATE LANTERNS (y2) ─────────────────────────────────────────────
        // Lanterns capping the two front gate posts to light the entrance.
        b.set(1, 2, 4, LANTERN);
        b.set(3, 2, 4, LANTERN);

        // ── HEADSTONES (cross silhouettes) ─────────────────────────────────
        // West grave: a stone-brick cross — upright pillar y1..y3 with a stone-button
        // crossbar suggestion, set at the head (z=1) of the west mound.
        b.set(1, 1, 1, STONE_BRICKS);               // headstone base
        b.set(1, 2, 1, STONE_BRICKS);               // upright
        b.set(1, 3, 1, STONE_BRICK_SLAB_BOTTOM);    // weathered cap
        b.set(0, 2, 1, stoneButton);                // crossarm nub (west)
        b.set(2, 2, 1, stoneButton);                // crossarm nub (east)
        // East grave: a simpler cracked-stone-brick + slab marker (a leaning headstone).
        b.set(3, 1, 1, CRACKED_STONE_BRICKS);       // headstone
        b.set(3, 2, 1, STONE_BRICK_SLAB_BOTTOM);    // slab cap

        // ── WITHERED FOLIAGE ───────────────────────────────────────────────
        // Dead bushes scattered on the mounds + a back-corner patch (free/structural).
        b.set(1, 1, 2, deadBush);                   // on west mound foot
        b.set(3, 1, 2, deadBush);                   // on east mound foot

        // ── DEAD TREE STUMP (back-east corner) ─────────────────────────────
        // A short bare oak-log stump with a stripped-log "broken top" — the
        // atmospheric dead tree. Sits clear of the graves in the NE corner.
        b.set(3, 1, 0, OAK_LOG_Y);
        b.set(3, 2, 0, OAK_LOG_Y);
        b.set(3, 3, 0, STRIPPED_OAK_Y);             // snapped, bleached crown
        b.set(2, 2, 0, deadBush);                   // a dead branch nub beside the trunk

        return b.build();
    }

    /**
     * §I Park Bench &amp; Lamppost. 5×3 footprint → builder(5,6,3). A classic park
     * scene: a slab-and-stair bench (oak-stair armrests + oak-slab seat on stone-brick
     * wall legs, with an oak-trapdoor / oak-sign backrest) beside a wrought-iron-look
     * lamppost (a chiseled-stone-brick base under a stone-brick-wall shaft, a chiseled
     * cap, a trapdoor "flare" shade, and a lantern head), all set on a small
     * stone-brick / dirt-path plot. A small decorative prop pairing — T1 disc.
     *
     * <p>Vanilla, FU-valued blocks only (stairs/slabs/walls/trapdoors/lantern/sign all
     * derive a value or are itemless-structural). NO glass/iron-bars panes anywhere, so
     * the render-integrity stub-pane gate never applies. Axes: x=W(0..4), y=up(0..5),
     * z=depth(0..2); the bench sits on the west half (x=0..2) and the lamppost stands
     * on the east edge (x=4).
     */
    private static Blueprint parkBenchLamppost() {
        Blueprint.Builder b = Blueprint.builder("Park Bench & Lamppost", 5, 6, 3);
        BlueprintBlockState seatStairW =
                bs("minecraft:oak_stairs[facing=south,half=bottom,shape=outer_left]");
        BlueprintBlockState seatStairE =
                bs("minecraft:oak_stairs[facing=south,half=bottom,shape=outer_right]");
        BlueprintBlockState backrestTrapdoor =
                bs("minecraft:oak_trapdoor[facing=south,half=top,open=false,powered=false,waterlogged=false]");
        BlueprintBlockState flareW =
                bs("minecraft:oak_trapdoor[facing=west,half=bottom,open=true,powered=false,waterlogged=false]");
        BlueprintBlockState flareN =
                bs("minecraft:oak_trapdoor[facing=north,half=bottom,open=true,powered=false,waterlogged=false]");
        BlueprintBlockState flareS =
                bs("minecraft:oak_trapdoor[facing=south,half=bottom,open=true,powered=false,waterlogged=false]");

        // ── GROUND PLOT (y0) ───────────────────────────────────────────────
        // 5×3 stone-brick paving with a dirt-path strip down the centre row (z=1),
        // suggesting a garden walkway running between the bench and the lamppost.
        floor(b, 0, 0, 0, 4, 2, STONE_BRICKS);
        line(b, 0, 0, 1, 4, 1, DIRT_PATH);

        // ── PARK BENCH (west half, x=0..2; sitter faces +z/south) ──────────
        // Legs: short stone-brick-wall posts under the two ends of the seat (z=1).
        b.set(0, 1, 1, STONE_BRICK_WALL);            // west leg
        b.set(2, 1, 1, STONE_BRICK_WALL);            // east leg
        // Seat (y=2, z=1): an oak-slab plank across the middle with chamfered
        // oak-stair armrests at each end so the bench reads as a proper seat.
        b.set(0, 2, 1, seatStairW);                  // west armrest (outer-left corner stair)
        b.set(1, 2, 1, OAK_SLAB_BOTTOM);             // seat plank
        b.set(2, 2, 1, seatStairE);                  // east armrest (outer-right corner stair)
        // Backrest (z=0): an upright oak-trapdoor board spanning the seat, capped by
        // a standing oak sign in the middle as the park-bench name plaque.
        b.set(0, 2, 0, backrestTrapdoor);
        b.set(1, 2, 0, backrestTrapdoor);
        b.set(2, 2, 0, backrestTrapdoor);
        b.set(1, 3, 0, bs("minecraft:oak_sign[rotation=8]")); // plaque, facing the path

        // ── LAMPPOST (east edge, x=4, z=1) ─────────────────────────────────
        // Chiseled-stone-brick base, a stone-brick-wall shaft (the wrought-iron pole),
        // a chiseled cap, an oak-trapdoor "flare" shade fanning out under the head,
        // and a standing lantern as the lamp itself.
        b.set(4, 1, 1, CHISELED_STONE_BRICKS);       // ornamental base
        b.set(4, 2, 1, STONE_BRICK_WALL);            // shaft
        b.set(4, 3, 1, STONE_BRICK_WALL);            // shaft
        b.set(4, 4, 1, CHISELED_STONE_BRICKS);       // cap the lamp head sits on
        // Trapdoor flare shade fanning out from the cap (3 in-volume sides; west toward
        // the bench, plus north/south). Open trapdoors → angled "lamp shade" panels.
        b.set(3, 4, 1, flareW);                      // west flare (over the path toward the bench)
        b.set(4, 4, 0, flareN);                      // north flare
        b.set(4, 4, 2, flareS);                      // south flare
        b.set(4, 5, 1, LANTERN);                     // lamp head, standing on the cap

        return b.build();
    }

    /**
     * Phase 2 §I Hedge Maze Segment. 9&times;9 footprint &rarr; builder(9, 4, 9). T1 disc.
     *
     * <p>A single TILEABLE tile of a castle-garden hedge maze: clipped topiary
     * hedge walls forming maze passages, gravel walkways with a dirt-path spine,
     * a stone-brick edging border framing the plot, and a sea-lantern topiary lamp
     * capping the NW corner hedge. Print copies edge-to-edge to grow a maze of any
     * size — the layout is symmetric under {@code x↔8-x} and {@code z↔8-z} and the
     * border opens at each edge MIDPOINT ({@code x=4} / {@code z=4}), so a tile's
     * x=8 column mirrors the next tile's x=0 column and the midpoint gates line up
     * into continuous through-passages across every seam.
     *
     * <p>NOTE on hedges: vanilla {@code leaves} are UNVALUED (unprintable in strict
     * mode), so the hedges are built from {@link #GREEN_WOOL} — a dyed wool that
     * normalises to the base wool FU cost and reads as a clipped topiary hedge.
     * Gravel ({@code 1@1}), stone bricks, dirt-path (structural), and the sea
     * lantern are all FU-valued/structural, so every cell clears the printability
     * gate. No glass/iron-bars panes anywhere, so the render-integrity stub-pane
     * gate never applies.
     *
     * <p>Hedge footprint (9&times;9, {@code h}=hedge, {@code .}=passage), rows z=0..8:
     * <pre>
     *   h h h h . h h h h
     *   h . . . . . . . h
     *   h . h h . h h . h
     *   h . h . . . h . h
     *   . . . . h . . . .
     *   h . h . . . h . h
     *   h . h h . h h . h
     *   h . . . . . . . h
     *   h h h h . h h h h
     * </pre>
     *
     * <p>Layout (Y), footprint x=0..8 &times; z=0..8:
     * <ul>
     *   <li><b>y=0</b> — ground deck. Every cell is paved: hedge cells and the
     *       perimeter ring get stone-brick edging (a clean footing the hedges sit
     *       on and the border frame); passage cells get gravel walkway; the central
     *       N&ndash;S and E&ndash;W passage spines (x=4 / z=4) are dirt-path so the
     *       through-routes read as the main walks.</li>
     *   <li><b>y=1..3</b> — the maze hedges: {@link #GREEN_WOOL} pillars, a uniform
     *       3 tall at every {@code h} cell, so the hedge height is consistent and
     *       tiles flush.</li>
     *   <li><b>corner lamp</b> — the NW corner hedge (0,0) is capped at its top
     *       course with a {@link #SEA_LANTERN} topiary lamp; corners are identical
     *       across tiles, so this reads as a regular lamp grid when the maze tiles.</li>
     * </ul>
     */
    private static Blueprint hedgeMazeSegment() {
        Blueprint.Builder b = Blueprint.builder("Hedge Maze Segment", 9, 4, 9);
        BlueprintBlockState gravel = bs("minecraft:gravel");

        // Hedge mask: true = topiary hedge wall, false = open passage.
        // Symmetric under x↔8-x and z↔8-z; border opens at the edge midpoints
        // (x=4 / z=4) so adjacent tiles share continuous through-passages.
        boolean[][] hedge = new boolean[9][9]; // [z][x]
        String[] rows = {
                "hhhh.hhhh",
                "h.......h",
                "h.hh.hh.h",
                "h.h...h.h",
                "....h....",
                "h.h...h.h",
                "h.hh.hh.h",
                "h.......h",
                "hhhh.hhhh",
        };
        for (int z = 0; z <= 8; z++) {
            for (int x = 0; x <= 8; x++) {
                hedge[z][x] = rows[z].charAt(x) == 'h';
            }
        }

        // ── GROUND DECK (y=0) ──────────────────────────────────────────────
        // Hedge cells + the perimeter ring → stone-brick edging (footing/border).
        // Passages → gravel; central spines (x==4 || z==4) → dirt-path walk.
        for (int z = 0; z <= 8; z++) {
            for (int x = 0; x <= 8; x++) {
                boolean perimeter = (x == 0 || x == 8 || z == 0 || z == 8);
                if (hedge[z][x] || perimeter) {
                    b.set(x, 0, z, STONE_BRICKS);
                } else if (x == 4 || z == 4) {
                    b.set(x, 0, z, DIRT_PATH);   // main through-walk spine
                } else {
                    b.set(x, 0, z, gravel);      // side passage walkway
                }
            }
        }

        // ── HEDGE WALLS (y=1..3) ───────────────────────────────────────────
        // Uniform 3-tall green-wool topiary at every hedge cell.
        for (int z = 0; z <= 8; z++) {
            for (int x = 0; x <= 8; x++) {
                if (hedge[z][x]) {
                    pillar(b, x, z, 1, 3, GREEN_WOOL);
                }
            }
        }

        // ── CORNER LAMP ────────────────────────────────────────────────────
        // Cap the NW corner hedge's top course with a sea-lantern topiary lamp.
        b.set(0, 3, 0, SEA_LANTERN);

        return b.build();
    }

    /** §3.2 Market Stall. 5×4×5 (W×L×H) → builder(5,5,4). Continuous striped awning. */
    private static Blueprint marketStall() {
        Blueprint.Builder b = Blueprint.builder("Market Stall", 5, 5, 4);
        floor(b, 0, 0, 0, 4, 3, OAK_PLANKS);
        // waist-high counter along the front (z=1)
        line(b, 1, 0, 1, 4, 1, SPRUCE_SLAB_TOP_STR);
        // four posts y=1..3
        pillar(b, 0, 1, 1, 3, OAK_LOG_Y);
        pillar(b, 4, 1, 1, 3, OAK_LOG_Y);
        pillar(b, 0, 3, 1, 3, OAK_LOG_Y);
        pillar(b, 4, 3, 1, 3, OAK_LOG_Y);
        // continuous sloped striped awning, back(high) → front(low)
        BlueprintBlockState backStair = bs("minecraft:oak_stairs[facing=north,half=bottom,shape=straight]");
        BlueprintBlockState frontStair = bs("minecraft:oak_stairs[facing=north,half=top,shape=straight]");
        for (int x = 0; x <= 4; x++) {
            b.set(x, 4, 3, backStair);                       // back lip high
            b.set(x, 4, 2, (x % 2 == 0) ? WHITE_WOOL : RED_WOOL); // striped middle
            b.set(x, 3, 1, frontStair);                      // front lip low
        }
        // goods on the counter
        b.set(1, 2, 1, BARREL);
        b.set(3, 2, 1, HAY);
        // backed hanging lantern under the awning. It hangs from the solid wool at
        // (2,4,2) — a half=top stair (the old (2,3,1) backing) has no bottom face for
        // a lantern to attach to, so it would float; wool gives a real solid face.
        b.set(2, 3, 2, HANGING_LANTERN);
        return b.build();
    }

    /**
     * Phase 2 §I Food Stall. 5&times;5&times;5 (W&times;L&times;H) &rarr; builder(5,5,5). T1 disc.
     *
     * <p>A street-food vendor cart — deliberately DISTINCT from {@link #marketStall}
     * (a generic dry-goods stall): this is a working food stand with a campfire
     * grill, two smoker ovens behind the counter, barrels of produce, a striped
     * wool awning, hanging lanterns, and a standing sign for signage (no item
     * frames). Open-fronted so a customer can walk up to the counter (south face,
     * z=0, is the service side; the kitchen is at the back, z=4).
     *
     * <p>AXES: x=W (0..4), y=up (0..4), z=depth (0..4). South (z=0) is the open
     * front the awning slopes toward; north (z=4) is the back kitchen wall.
     *
     * <p>Vanilla, FU-valued blocks only (spruce plank/log/slab/trapdoor, white/red
     * wool, barrel, campfire, smoker, composter, hay, lantern, oak sign — all derive
     * value and are used by other shipping builds), so every cell clears the
     * printability gate. NO glass/iron-bars panes anywhere, so the render-integrity
     * stub-pane gate never applies; the lanterns hang on chains backed by the solid
     * wool awning above them.
     *
     * <p>Layout:
     * <ul>
     *   <li><b>y=0</b> — walkable spruce-plank floor over the full 5&times;5.</li>
     *   <li><b>back kitchen wall (z=4)</b> — spruce planks y=1..2 with spruce-log
     *       corner posts; two {@link #SMOKER} ovens (facing south, toward the cook)
     *       flanking a central composter, barrels of produce in the back corners.</li>
     *   <li><b>front counter (z=1)</b> — a spruce-trapdoor apron (top half, closed)
     *       under a spruce top-slab counter surface, spanning the open front between
     *       the two front posts; the bay above stays open (air-skip) for service.</li>
     *   <li><b>grill</b> — a lit {@link #CAMPFIRE} on the floor at the cook's spot
     *       (2,1,3), the stall's smoke signature.</li>
     *   <li><b>awning (y=3..4)</b> — a striped white/red wool canopy sloping from a
     *       high back lip (oak stairs at z=4, y=4) over a striped wool middle
     *       (z=2..3, y=4) down to a low front lip (oak stairs at z=0, y=3).</li>
     *   <li><b>lanterns</b> — two hanging lanterns on single chains under the front
     *       awning edge, each backed by the solid wool block directly above it.</li>
     *   <li><b>signage</b> — a standing oak sign on the counter advertising the
     *       stand (FU-valued, recipe-derived; no item frame).</li>
     * </ul>
     */
    private static Blueprint foodStall() {
        Blueprint.Builder b = Blueprint.builder("Food Stall", 5, 5, 5);
        BlueprintBlockState smokerSouth = bs("minecraft:smoker[facing=south,lit=true]");
        BlueprintBlockState trapdoorApron =
                bs("minecraft:spruce_trapdoor[facing=south,half=top,open=false,powered=false,waterlogged=false]");

        // 1) walkable spruce-plank floor
        floor(b, 0, 0, 0, 4, 4, SPRUCE_PLANKS);

        // 2) back kitchen wall (z=4), spruce planks y=1..2, between the corner posts
        line(b, 1, 1, 4, 3, 4, SPRUCE_PLANKS);
        line(b, 2, 1, 4, 3, 4, SPRUCE_PLANKS);

        // 3) four corner posts y=1..3 (spruce logs) — front and back
        pillar(b, 0, 1, 1, 3, SPRUCE_LOG_Y);
        pillar(b, 4, 1, 1, 3, SPRUCE_LOG_Y);
        pillar(b, 0, 4, 1, 3, SPRUCE_LOG_Y);
        pillar(b, 4, 4, 1, 3, SPRUCE_LOG_Y);

        // 4) kitchen line at the back: two smoker ovens flanking a central composter,
        //    barrels of produce in the back corners
        b.set(0, 1, 4, BARREL);              // produce barrel (back-west corner base)
        b.set(4, 1, 4, BARREL);              // produce barrel (back-east corner base)
        b.set(1, 1, 4, smokerSouth);         // smoker oven, facing the cook (south)
        b.set(3, 1, 4, smokerSouth);         // smoker oven, facing the cook (south)
        b.set(2, 1, 4, COMPOSTER);           // scraps composter between the ovens

        // 5) front counter (z=1): trapdoor apron under a top-slab surface, spanning
        //    the open front between the two front posts (x=1..3)
        for (int x = 1; x <= 3; x++) {
            b.set(x, 1, 1, trapdoorApron);        // counter apron face
            b.set(x, 2, 1, SPRUCE_SLAB_TOP);      // counter surface
        }

        // 6) grill + goods: lit campfire at the cook's spot, a hay-bale of produce
        //    in the west aisle and a produce barrel in the east aisle. (No carved
        //    pumpkin — survival pumpkins are intentionally UNVALUED in the FU economy
        //    and would silently skip in strict mode; barrel/hay derive value.)
        b.set(2, 1, 3, CAMPFIRE);            // the grill (smoke signature)
        b.set(0, 1, 3, HAY);                 // straw / produce stack, west aisle
        b.set(4, 1, 3, BARREL);              // produce barrel on display, east aisle

        // 7) striped wool awning, back(high) → front(low), spanning x=0..4
        BlueprintBlockState backStair = bs("minecraft:oak_stairs[facing=north,half=bottom,shape=straight]");
        BlueprintBlockState frontStair = bs("minecraft:oak_stairs[facing=north,half=top,shape=straight]");
        for (int x = 0; x <= 4; x++) {
            b.set(x, 4, 4, backStair);                        // back lip high
            b.set(x, 4, 3, (x % 2 == 0) ? WHITE_WOOL : RED_WOOL); // striped row
            b.set(x, 4, 2, (x % 2 == 0) ? RED_WOOL : WHITE_WOOL); // striped row (offset)
            b.set(x, 3, 0, frontStair);                       // front lip low
        }

        // 8) two hanging lanterns on single chains under the front awning edge,
        //    each backed by the solid wool block directly above (y=4) so they don't
        //    float; lantern at y=3, chain at y... the wool at (1,4,3)/(3,4,3) backs them
        chainLantern(b, 1, 3, 3, 0); // lantern at (1,3,3), backed by wool at (1,4,3)
        chainLantern(b, 3, 3, 3, 0); // lantern at (3,3,3), backed by wool at (3,4,3)

        // 9) signage — a standing oak sign atop the west front post (solid spruce
        //    log support below at y=3), facing the customer. No item frame.
        b.set(0, 4, 1, bs("minecraft:oak_sign[rotation=8]"));

        return b.build();
    }

    /** §3.3 Small Cottage. 5×5×7 (W×L×H) → builder(5,7,5). Furnished, closed gable. */
    private static Blueprint smallCottage() {
        Blueprint.Builder b = Blueprint.builder("Small Cottage", 5, 7, 5);
        floor(b, 0, 0, 0, 4, 4, COBBLE);
        walls(b, 0, 0, 4, 4, 1, 3, OAK_PLANKS);
        corners(b, 0, 0, 4, 4, 1, 3, STRIPPED_OAK_Y);
        door2(b, 2, 1, 0, "oak", "N"); // faces south, into the room
        window2(b, 0, 2, 2, GLASS_PANE, null);
        window2(b, 4, 2, 2, GLASS_PANE, null);
        window2(b, 2, 2, 4, GLASS_PANE, null);
        // interior: bed + crafting table + floor torch
        bed(b, 1, 1, 3, "white", "south"); // head at z=3, foot at z=2
        b.set(3, 1, 3, CRAFTING_TABLE);
        b.set(2, 1, 2, TORCH);
        // roof: gable + closed ends
        gableRoofX(b, 0, 0, 4, 4, 4, "oak_stairs", OAK_SLAB_BOTTOM);
        gableEndFill(b, 0, 0, 4, 4, 4, OAK_PLANKS);
        return b.build();
    }

    /** §3.23 Beacon Spire. 5×5×12 (W×L×H) → builder(5,12,5). Working beacon + quartz spire. */
    private static Blueprint beaconSpire() {
        Blueprint.Builder b = Blueprint.builder("Beacon Spire", 5, 12, 5);
        BlueprintBlockState smoothQuartz = bs("minecraft:smooth_quartz");
        BlueprintBlockState quartzPillar = bs("minecraft:quartz_pillar[axis=y]");
        BlueprintBlockState quartzBricks = bs("minecraft:quartz_bricks");
        BlueprintBlockState diamondBlock = bs("minecraft:diamond_block");
        BlueprintBlockState emeraldBlock = bs("minecraft:emerald_block");
        BlueprintBlockState beacon = bs("minecraft:beacon");
        // foundation
        floor(b, 0, 0, 0, 4, 4, smoothQuartz);
        // 3×3 mineral pyramid base for a real beacon beam (diamond ring + emerald centre)
        for (int x = 1; x <= 3; x++) {
            for (int z = 1; z <= 3; z++) {
                b.set(x, 0, z, (x == 2 && z == 2) ? emeraldBlock : diamondBlock);
            }
        }
        // beacon on the base centre, projecting the beam
        b.set(2, 1, 2, beacon);
        // spire body: quartz-pillar corners, quartz-brick infill, glass shaft around beam
        for (int y = 1; y <= 9; y++) {
            pillar(b, 0, 0, y, y, quartzPillar);
            pillar(b, 4, 0, y, y, quartzPillar);
            pillar(b, 0, 4, y, y, quartzPillar);
            pillar(b, 4, 4, y, y, quartzPillar);
            // brick infill on the four faces, but keep the beam column (2,*,2) clear
            line(b, y, 1, 0, 3, 0, quartzBricks);
            line(b, y, 1, 4, 3, 4, quartzBricks);
            line(b, y, 0, 1, 0, 3, quartzBricks);
            line(b, y, 4, 1, 4, 3, quartzBricks);
        }
        // glass/tinted-glass shroud around the open beam shaft so the beam shows
        for (int y = 2; y <= 9; y++) {
            b.set(2, y, 1, TINTED_GLASS);
            b.set(1, y, 2, TINTED_GLASS);
            b.set(3, y, 2, TINTED_GLASS);
            b.set(2, y, 3, TINTED_GLASS);
        }
        // setback shoulders (quartz-stairs ring) at y=9
        for (int x = 0; x <= 4; x++) {
            b.set(x, 10, 0, bs("minecraft:quartz_stairs[facing=south,half=bottom,shape=straight]"));
            b.set(x, 10, 4, bs("minecraft:quartz_stairs[facing=north,half=bottom,shape=straight]"));
        }
        for (int z = 1; z <= 3; z++) {
            b.set(0, 10, z, bs("minecraft:quartz_stairs[facing=east,half=bottom,shape=straight]"));
            b.set(4, 10, z, bs("minecraft:quartz_stairs[facing=west,half=bottom,shape=straight]"));
        }
        // apex: stained-glass crown on the beam axis (a sea_lantern here is opaque and
        // STOPS the beacon beam — stained glass lets it through and tints it) + end-rod finials
        b.set(2, 10, 2, bs("minecraft:white_stained_glass"));
        b.set(2, 11, 2, END_ROD);
        b.set(0, 11, 0, END_ROD);
        b.set(4, 11, 0, END_ROD);
        b.set(0, 11, 4, END_ROD);
        b.set(4, 11, 4, END_ROD);
        return b.build();
    }

    // ----- Medium (T5–T6 footprint) -----

    /** §3.6 Plains House. 7×7×8 (W×L×H) → builder(7,8,7). Furnished, no corner nub. */
    private static Blueprint plainsHouse() {
        Blueprint.Builder b = Blueprint.builder("Plains House", 7, 8, 7);
        floor(b, 0, 0, 0, 6, 6, COBBLE);
        walls(b, 0, 0, 6, 6, 1, 3, OAK_PLANKS);
        corners(b, 0, 0, 6, 6, 1, 3, OAK_LOG_Y); // same height as walls — no nub
        // symmetric window band
        window2(b, 0, 2, 2, GLASS_PANE, null);
        window2(b, 0, 2, 4, GLASS_PANE, null);
        window2(b, 6, 2, 2, GLASS_PANE, null);
        window2(b, 6, 2, 4, GLASS_PANE, null);
        window2(b, 3, 2, 6, GLASS_PANE, null);
        door2(b, 3, 1, 0, "oak", "N");
        // torches flanking the door, mounted on the inside of the north wall (facing
        // south backs onto the z=0 plank — facing north at z=0 had no block to hang on)
        wallTorch(b, 2, 3, 1, "south");
        wallTorch(b, 4, 3, 1, "south");
        // plate course to seat the roof flush
        line(b, 4, 0, 0, 6, 0, OAK_PLANKS);
        line(b, 4, 0, 6, 6, 6, OAK_PLANKS);
        line(b, 4, 0, 0, 0, 6, OAK_PLANKS);
        line(b, 4, 6, 0, 6, 6, OAK_PLANKS);
        // roof + closed gable ends
        gableRoofX(b, 0, 0, 6, 6, 4, "oak_stairs", OAK_SLAB_BOTTOM);
        gableEndFill(b, 0, 0, 6, 6, 4, OAK_PLANKS);
        // interior furnishings
        bed(b, 1, 1, 5, "white", "south"); // head at z=5, foot at z=4
        b.set(5, 1, 5, CHEST);
        b.set(1, 1, 1, CRAFTING_TABLE);
        b.set(2, 1, 1, FURNACE);
        return b.build();
    }

    /** §3.7 Wheat Farm. 7×7×3 (W×L×H) → builder(7,3,7). Soil + water + age-7 crops + fences. */
    private static Blueprint smallFarm() {
        Blueprint.Builder b = Blueprint.builder("Wheat Farm", 7, 3, 7);
        // dirt-path walking border
        floor(b, 0, 0, 0, 6, 6, DIRT_PATH);
        // inner 5×5 of fully-grown farmland — NO flowing water source. Printing is
        // incremental (one block per tick), so a water source set in the middle of the
        // field flows into the cells that haven't printed yet and can never be enclosed
        // in time — the farm came out a bare pool with no soil/crops. Instead: moisture=7
        // soil prints hydrated-looking, and a crop on EVERY tile stops the soil reverting
        // to dirt as it dries, so it stays a full, recognisable crop field.
        for (int x = 1; x <= 5; x++) {
            for (int z = 1; z <= 5; z++) {
                b.set(x, 0, z, FARMLAND);
                BlueprintBlockState crop;
                if (z <= 3) crop = WHEAT;
                else if (z == 4) crop = CARROTS;
                else crop = POTATOES;
                b.set(x, 1, z, crop);
            }
        }
        // fence ring on the path lip
        fenceRing(b, 1, 0, 0, 6, 6, OAK_FENCE);
        // taller corner posts so the fence reads as fencing
        pillar(b, 0, 0, 1, 2, OAK_LOG_Y);
        pillar(b, 6, 0, 1, 2, OAK_LOG_Y);
        pillar(b, 0, 6, 1, 2, OAK_LOG_Y);
        pillar(b, 6, 6, 1, 2, OAK_LOG_Y);
        // gate faces OUT (north) — you walk out of the plot
        b.set(3, 1, 0, bs("minecraft:oak_fence_gate[facing=north,open=false,in_wall=false,powered=false]"));
        // detail: hay stack + backed lantern on a corner post cap
        pillar(b, 6, 6, 1, 2, HAY);
        b.set(0, 2, 0, OAK_FENCE);
        b.set(0, 1, 0, HANGING_LANTERN);
        return b.build();
    }

    /** §3.11 Bakery. 7×6×7 (W×L×H) → builder(7,7,6). Brick oven/chimney + glass shopfront. */
    private static Blueprint bakery() {
        Blueprint.Builder b = Blueprint.builder("Bakery", 7, 7, 6);
        floor(b, 0, 0, 0, 6, 5, COBBLE);
        walls(b, 0, 0, 6, 5, 1, 3, OAK_PLANKS);
        corners(b, 0, 0, 6, 5, 1, 3, OAK_LOG_Y);
        door2(b, 3, 1, 0, "oak", "N");
        // shopfront glass band on the front (z=0)
        window2(b, 1, 2, 0, GLASS_PANE, null);
        window2(b, 2, 2, 0, GLASS_PANE, null);
        window2(b, 4, 2, 0, GLASS_PANE, null);
        window2(b, 5, 2, 0, GLASS_PANE, null);
        window2(b, 0, 2, 2, GLASS_PANE, null);
        window2(b, 6, 2, 2, GLASS_PANE, null);
        // counter with goods
        line(b, 1, 1, 2, 5, 2, SMOOTH_STONE_SLAB_TOP);
        b.set(1, 2, 2, BARREL);
        b.set(5, 2, 2, COMPOSTER);
        // two smokers (ovens) at the back
        b.set(1, 1, 4, SMOKER);
        b.set(2, 1, 4, SMOKER);
        // brick oven mouth at the chimney base
        b.set(5, 1, 4, bs("minecraft:smoker[facing=west,lit=true]"));
        // roof + closed gable ends
        gableRoofX(b, 0, 0, 6, 5, 4, "oak_stairs", OAK_SLAB_BOTTOM);
        gableEndFill(b, 0, 0, 6, 5, 4, OAK_PLANKS);
        // brick chimney LAST so it punches through the east gable end (placed after the
        // roof/gable-fill, the brick wins those cells instead of being buried by them),
        // capped by a smoking campfire at the ridge peak (y=6 is the H=7 budget ceiling)
        pillar(b, 6, 4, 1, 5, BRICKS);
        b.set(6, 6, 4, CAMPFIRE);
        return b.build();
    }

    /** §3.8 Blacksmith. 7×6×6 (W×L×H) → builder(7,6,6). Caged-lava forge, anvil, open front. */
    private static Blueprint blacksmith() {
        Blueprint.Builder b = Blueprint.builder("Blacksmith", 7, 6, 6);
        floor(b, 0, 0, 0, 6, 5, COBBLE);
        // open north front (z=0). Back + sides: cobble y=1..2, stone-brick y=3..4.
        for (int y = 1; y <= 4; y++) {
            BlueprintBlockState mat = (y <= 2) ? COBBLE : STONE_BRICKS;
            line(b, y, 0, 5, 6, 5, mat); // back wall
            line(b, y, 0, 1, 0, 5, mat); // west wall (start z=1, front open)
            line(b, y, 6, 1, 6, 5, mat); // east wall
        }
        corners(b, 0, 0, 6, 5, 1, 4, OAK_LOG_Y);
        // forge: lava source caged in iron bars, back-right alcove
        b.set(5, 1, 4, LAVA);
        b.set(5, 1, 3, IRON_BARS);
        b.set(5, 2, 3, IRON_BARS);
        b.set(5, 2, 4, IRON_BARS);
        b.set(4, 1, 4, IRON_BARS);
        b.set(6, 1, 4, IRON_BARS);
        // chimney venting the forge up to the roof line (H=6 budget tops out at y=5).
        // Starts at y=2 so it CAPS the lava (5,1,4) rather than overwriting the source —
        // the forge stays a glowing, iron-bar-caged lava cell, not dead cobble.
        pillar(b, 5, 4, 2, 5, COBBLE);
        // furniture
        b.set(3, 1, 4, ANVIL);              // faces north (out the open front)
        b.set(1, 1, 4, BLAST_FURNACE);
        b.set(2, 1, 4, FURNACE);
        b.set(1, 1, 1, SMITHING_TABLE);
        b.set(6, 1, 2, GRINDSTONE);
        b.set(3, 1, 1, BARREL);
        b.set(0, 1, 2, CAULDRON);
        // counter along the open front
        line(b, 1, 1, 1, 5, 1, bs("minecraft:dark_oak_slab[type=top]"));
        // backed lantern over the anvil (hangs from the y=5 roof slab above)
        b.set(3, 4, 4, HANGING_LANTERN);
        // roof: a low flat stone-brick-slab roof at y=5 (the H=6 budget can't take a
        // gable above 4-tall walls) with a dark-oak overhang trim on the open front.
        flatRoof(b, 5, 0, 0, 6, 5, STONE_BRICK_SLAB_TOP);
        line(b, 5, 0, 0, 6, 0, DARK_OAK_PLANKS); // front overhang trim
        return b.build();
    }

    /** §3.9 Windmill. 7×7×9 (W×L×H) → builder(7,9,7). Radiating wool+fence sails. */
    private static Blueprint windmill() {
        Blueprint.Builder b = Blueprint.builder("Windmill", 7, 9, 7);
        // 5×5 body centred in the 7×7 footprint (x,z ∈ 1..5)
        floor(b, 0, 1, 1, 5, 5, COBBLE);
        walls(b, 1, 1, 5, 5, 1, 1, COBBLE);            // grounding course
        walls(b, 1, 1, 5, 5, 2, 5, SPRUCE_PLANKS);     // upper body
        corners(b, 1, 1, 5, 5, 1, 5, SPRUCE_LOG_Y);
        // Door on the WEST side wall, not the front: the sails are a centred cross on the
        // front (north) face and their down-blade runs to the ground right where a front
        // door would be, blocking the entrance. Real windmills put the door beside/below
        // the sails, so enter from the side and leave the sail face clean.
        door2(b, 1, 1, 3, "spruce", "W");
        window2(b, 1, 3, 3, GLASS_PANE, null);  // west wall, transom above the door
        window2(b, 5, 3, 3, GLASS_PANE, null);  // east wall
        window2(b, 3, 3, 5, GLASS_PANE, null);  // back wall
        // conical cap (y=6..7): inward stair ring + slab cap
        for (int x = 1; x <= 5; x++) {
            b.set(x, 6, 1, bs("minecraft:spruce_stairs[facing=south,half=bottom,shape=straight]"));
            b.set(x, 6, 5, bs("minecraft:spruce_stairs[facing=north,half=bottom,shape=straight]"));
        }
        for (int z = 2; z <= 4; z++) {
            b.set(1, 6, z, bs("minecraft:spruce_stairs[facing=east,half=bottom,shape=straight]"));
            b.set(5, 6, z, bs("minecraft:spruce_stairs[facing=west,half=bottom,shape=straight]"));
        }
        floor(b, 7, 2, 2, 4, 4, SPRUCE_SLAB_TOP);
        // axle hub projecting out the front at mid-height
        b.set(3, 4, 0, STRIPPED_OAK_X);
        // SAILS: four blades radiating from the hub in the X–Y plane at z=0
        // up blade
        b.set(3, 5, 0, OAK_FENCE); b.set(3, 6, 0, OAK_FENCE); b.set(3, 7, 0, OAK_FENCE);
        b.set(2, 5, 0, WHITE_WOOL); b.set(2, 6, 0, WHITE_WOOL); b.set(2, 7, 0, WHITE_WOOL);
        // down blade
        b.set(3, 3, 0, OAK_FENCE); b.set(3, 2, 0, OAK_FENCE); b.set(3, 1, 0, OAK_FENCE);
        b.set(4, 3, 0, WHITE_WOOL); b.set(4, 2, 0, WHITE_WOOL); b.set(4, 1, 0, WHITE_WOOL);
        // left blade
        b.set(2, 4, 0, OAK_FENCE); b.set(1, 4, 0, OAK_FENCE); b.set(0, 4, 0, OAK_FENCE);
        b.set(0, 3, 0, WHITE_WOOL); b.set(1, 3, 0, WHITE_WOOL); b.set(2, 3, 0, WHITE_WOOL);
        // right blade
        b.set(4, 4, 0, OAK_FENCE); b.set(5, 4, 0, OAK_FENCE); b.set(6, 4, 0, OAK_FENCE);
        b.set(4, 5, 0, WHITE_WOOL); b.set(5, 5, 0, WHITE_WOOL); b.set(6, 5, 0, WHITE_WOOL);
        // interior (grain mill read) — on the y=0 floor (standing level y=1), not floating
        b.set(2, 1, 4, COMPOSTER);
        b.set(4, 1, 4, BARREL);
        return b.build();
    }

    /**
     * §3.12 Stone Bridge. 5×9×5 (W×L×H) → builder(5,5,9). Walkable arch over water.
     * Height budget is H=5 (y 0..4): a gentle 0→1→2 (crown) →1→0 deck profile leaves
     * room for a parapet (deck+1) and lamp caps without exceeding sizeY.
     */
    private static Blueprint stoneBridge() {
        Blueprint.Builder b = Blueprint.builder("Stone Bridge", 5, 5, 9);
        // water beneath the whole span
        floor(b, 0, 0, 0, 4, 8, WATER);
        // gentle symmetric deck profile per z-row (crown of y=2 at z=4)
        int[] deckY = {0, 1, 1, 2, 2, 2, 1, 1, 0};
        // Stairs ascending toward +z use facing=south (the verified gableRoofX
        // convention); ascending toward -z uses facing=north. The earlier code had
        // these INVERTED and also overwrote the two end stairs with a flat cobble
        // abutment, producing un-walkable 1-block ledges at both approaches — the very
        // wooden_bridge defect this build was meant to fix. Now every height change is
        // a stair you can walk up, ends included.
        BlueprintBlockState rampUp = bs("minecraft:stone_brick_stairs[facing=south,half=bottom,shape=straight]");
        BlueprintBlockState rampDown = bs("minecraft:stone_brick_stairs[facing=north,half=bottom,shape=straight]");
        for (int z = 0; z <= 8; z++) {
            int dy = deckY[z];
            boolean rising = z < 4 && deckY[z] < deckY[z + 1];
            boolean falling = z > 4 && deckY[z] < deckY[z - 1];
            BlueprintBlockState surface;
            if (rising) surface = rampUp;
            else if (falling) surface = rampDown;
            else surface = STONE_BRICKS; // flat tread (and the crown)
            for (int x = 0; x <= 4; x++) b.set(x, dy, z, surface);
        }
        // parapet walls along both edges, one above the deck
        for (int z = 0; z <= 8; z++) {
            b.set(0, deckY[z] + 1, z, STONE_BRICK_WALL);
            b.set(4, deckY[z] + 1, z, STONE_BRICK_WALL);
        }
        // lamp posts at the crown corners: fence post with a lantern resting on its cap
        b.set(0, 3, 4, OAK_FENCE);
        b.set(0, 4, 4, LANTERN); // sits on the post (non-hanging — no backing needed)
        b.set(4, 3, 4, OAK_FENCE);
        b.set(4, 4, 4, LANTERN);
        return b.build();
    }

    /** §3.13 Watchtower. 5×5×9 (W×L×H) → builder(5,9,5). Floored platform + crenellations. */
    private static Blueprint watchtower() {
        Blueprint.Builder b = Blueprint.builder("Watchtower", 5, 9, 5);
        floor(b, 0, 0, 0, 4, 4, COBBLE);
        walls(b, 0, 0, 4, 4, 1, 6, COBBLE);
        corners(b, 0, 0, 4, 4, 1, 6, STONE_BRICKS); // same height — closes the gap
        door2(b, 2, 1, 0, "oak", "N");
        // ladder up the interior, backed by the south wall (facing=north → attaches to
        // (1,y,4)). The old (2,1) ladder backed onto the DOOR at (2,*,0), which is not a
        // valid support; it also ran only to y=6, below the deck. Now it reaches y=7 and
        // surfaces through a hatch in the platform.
        pillar(b, 1, 3, 1, 7, bs("minecraft:ladder[facing=north,waterlogged=false]"));
        // lookout glazing (y=3) + arrow-slit iron bars (y=5)
        window2(b, 0, 3, 2, GLASS_PANE, null);
        window2(b, 4, 3, 2, GLASS_PANE, null);
        window2(b, 2, 3, 4, GLASS_PANE, null);
        b.set(0, 5, 2, IRON_BARS);
        b.set(4, 5, 2, IRON_BARS);
        b.set(2, 5, 0, IRON_BARS);
        b.set(2, 5, 4, IRON_BARS);
        // platform deck (y=7) with a ladder hatch at (1,3) so you can climb out onto it
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 4; z++) {
                if (x == 1 && z == 3) continue; // ladder hatch
                b.set(x, 7, z, OAK_PLANKS);
            }
        }
        // crenellations flush on the platform (y=8)
        crenellate(b, 8, 0, 0, 4, 4, COBBLE_WALL);
        // light + door torches (mounted on the INSIDE of the north wall — facing south
        // backs onto the z=0 cobble; facing north at z=0 had no block to mount on)
        b.set(2, 8, 2, LANTERN);
        wallTorch(b, 1, 3, 1, "south");
        wallTorch(b, 3, 3, 1, "south");
        return b.build();
    }

    /** §3.10 Barn. 9×7×7 (W×L×H) → builder(9,7,7). Red gable, double doors, hay loft, pen. */
    private static Blueprint barn() {
        Blueprint.Builder b = Blueprint.builder("Barn", 9, 7, 7);
        floor(b, 0, 0, 0, 8, 6, SPRUCE_PLANKS);
        floor(b, 0, 2, 1, 6, 5, GRASS_BLOCK); // inner pen floor
        // timber-frame walls (kept low so the gambrel barn roof fits the H=7 budget)
        timberFrame(b, 0, 0, 8, 6, 1, 2, SPRUCE_PLANKS, STRIPPED_SPRUCE_Y, bs("minecraft:stripped_spruce_log[axis=x]"));
        // big double doors on the front (z=0): two ADJACENT leaves with mirrored hinges,
        // overwriting the timber stud timberFrame placed at (4,0). The old (3,0)+(5,0)
        // pair left that stud standing between them, reading as two separate single doors.
        b.set(4, 1, 0, bs("minecraft:spruce_door[facing=south,half=lower,hinge=left,open=false,powered=false]"));
        b.set(4, 2, 0, bs("minecraft:spruce_door[facing=south,half=upper,hinge=left,open=false,powered=false]"));
        b.set(5, 1, 0, bs("minecraft:spruce_door[facing=south,half=lower,hinge=right,open=false,powered=false]"));
        b.set(5, 2, 0, bs("minecraft:spruce_door[facing=south,half=upper,hinge=right,open=false,powered=false]"));
        // windows on the long walls
        window2(b, 0, 2, 2, GLASS_PANE, null);
        window2(b, 0, 2, 4, GLASS_PANE, null);
        window2(b, 8, 2, 2, GLASS_PANE, null);
        window2(b, 8, 2, 4, GLASS_PANE, null);
        // gambrel roof (eave y=3, gable y=4..6) + red-wool gable accent on both ends
        gambrelRoofX(b, 0, 0, 8, 6, 3, "spruce_stairs", SPRUCE_SLAB_BOTTOM);
        // close the eave-course gap and gable triangle with red wool (the barn-red read)
        for (int x = 1; x <= 7; x++) {
            b.set(x, 3, 0, RED_WOOL);
            b.set(x, 3, 6, RED_WOOL);
        }
        // close the flared eave course on the two X-end faces (x=0,x=8) so the
        // lower pitch's ends aren't open under the gambrel. The corner cells at
        // z=0/z=6 already carry the flare stairs, so fill only the inner z=1..5
        // span; the upper gable then closes y=4..6.
        for (int z = 1; z <= 5; z++) {
            b.set(0, 3, z, RED_WOOL);
            b.set(8, 3, z, RED_WOOL);
        }
        gableEndFill(b, 0, 1, 8, 5, 4, RED_WOOL);
        // interior: hay loft, pen fence + gate, water trough, composter/barrel
        pillar(b, 7, 5, 1, 2, HAY);
        pillar(b, 1, 5, 1, 2, HAY);
        fenceRing(b, 1, 2, 1, 6, 5, OAK_FENCE);
        b.set(4, 1, 1, bs("minecraft:oak_fence_gate[facing=north,open=false,in_wall=false,powered=false]"));
        b.set(3, 1, 3, WATER);
        b.set(4, 1, 3, WATER);
        b.set(1, 1, 1, COMPOSTER);
        b.set(1, 1, 5, BARREL);
        // backed lantern hanging from the ridge slab (which stays at y=6,z=3)
        b.set(4, 5, 3, CHAIN);
        b.set(4, 4, 3, HANGING_LANTERN);
        return b.build();
    }

    // ----- High-material-tier (disc T2–T5) -----

    /** §3.19 Iron Foundry. 9×9×9 (W×L×H) → builder(9,9,9). Iron-block smelter core. */
    private static Blueprint ironFoundry() {
        Blueprint.Builder b = Blueprint.builder("Iron Foundry", 9, 9, 9);
        BlueprintBlockState deepslateTiles = bs("minecraft:deepslate_tiles");
        floor(b, 0, 0, 0, 8, 8, deepslateTiles);
        // walls: cobble base (y=1..2) + stone-brick upper (y=3..5)
        walls(b, 0, 0, 8, 8, 1, 2, COBBLE);
        walls(b, 0, 0, 8, 8, 3, 5, STONE_BRICKS);
        corners(b, 0, 0, 8, 8, 1, 5, STONE_BRICKS);
        // iron door entrance (faces south, into the hall)
        b.set(4, 1, 0, bs("minecraft:iron_door[facing=south,half=lower,hinge=left,open=false,powered=false]"));
        b.set(4, 2, 0, bs("minecraft:iron_door[facing=south,half=upper,hinge=left,open=false,powered=false]"));
        // iron-bars clerestory windows
        b.set(0, 4, 4, IRON_BARS);
        b.set(8, 4, 4, IRON_BARS);
        b.set(4, 4, 8, IRON_BARS);
        b.set(2, 4, 0, IRON_BARS);
        b.set(6, 4, 0, IRON_BARS);
        // central iron-block smelter stack (3×3 footprint, y=1..4) with caged lava base
        for (int x = 3; x <= 5; x++) {
            for (int z = 3; z <= 5; z++) {
                if (x == 4 && z == 4) {
                    b.set(x, 1, z, LAVA); // crucible
                } else {
                    pillar(b, x, z, 1, 4, IRON_BLOCK);
                }
            }
        }
        // cage the lava crucible with iron bars at y=1..2 around the open top
        b.set(4, 2, 4, IRON_BARS);
        // chimney venting through the roof
        pillar(b, 4, 4, 5, 8, IRON_BLOCK);
        // blast-furnace bank along the west wall
        b.set(1, 1, 2, BLAST_FURNACE);
        b.set(1, 1, 3, BLAST_FURNACE);
        b.set(1, 1, 4, BLAST_FURNACE);
        // furniture
        b.set(7, 1, 2, ANVIL);
        b.set(7, 1, 3, SMITHING_TABLE);
        b.set(7, 1, 6, CAULDRON);
        b.set(2, 1, 6, SMOOTH_STONE_SLAB_TOP);
        // flat smooth-stone roof + clerestory monitor venting smoke
        flatRoof(b, 6, 0, 0, 8, 8, SMOOTH_STONE_SLAB_TOP);
        walls(b, 3, 3, 5, 5, 6, 7, IRON_BARS); // raised clerestory around the chimney
        flatRoof(b, 8, 3, 3, 5, 5, SMOOTH_STONE_SLAB_TOP);
        // light
        b.set(2, 5, 2, CHAIN);
        b.set(2, 4, 2, HANGING_LANTERN);
        b.set(6, 5, 6, CHAIN);
        b.set(6, 4, 6, HANGING_LANTERN);
        return b.build();
    }

    /** §3.20 Redstone Workshop. 9×9×7 (W×L×H) → builder(9,7,9). Glowing redstone wall. */
    private static Blueprint redstoneWorkshop() {
        Blueprint.Builder b = Blueprint.builder("Redstone Workshop", 9, 7, 9);
        BlueprintBlockState blackstone = bs("minecraft:polished_blackstone");
        BlueprintBlockState blackstoneBricks = bs("minecraft:polished_blackstone_bricks");
        BlueprintBlockState blackstoneSlabTop = bs("minecraft:polished_blackstone_slab[type=top]");
        BlueprintBlockState redstoneBlock = bs("minecraft:redstone_block");
        BlueprintBlockState redstoneLamp = bs("minecraft:redstone_lamp[lit=true]");
        floor(b, 0, 0, 0, 8, 8, blackstone);
        // walls: stone-brick + blackstone-brick banding
        walls(b, 0, 0, 8, 8, 1, 2, STONE_BRICKS);
        walls(b, 0, 0, 8, 8, 3, 4, blackstoneBricks);
        corners(b, 0, 0, 8, 8, 1, 4, blackstoneBricks);
        // dark-oak door (faces south)
        door2(b, 4, 1, 0, "dark_oak", "N");
        // glass lab windows
        window2(b, 0, 2, 3, GLASS, null);
        window2(b, 0, 2, 5, GLASS, null);
        window2(b, 8, 2, 3, GLASS, null);
        window2(b, 8, 2, 5, GLASS, null);
        // redstone display wall (back, z=8): alternating redstone block / lit lamp + levers
        for (int x = 1; x <= 7; x++) {
            for (int y = 1; y <= 3; y++) {
                b.set(x, y, 8, ((x + y) % 2 == 0) ? redstoneBlock : redstoneLamp);
            }
        }
        // levers mounted ON the z=8 display wall (face=wall,facing=north backs onto z+1=8)
        b.set(2, 1, 7, bs("minecraft:lever[face=wall,facing=north,powered=false]"));
        b.set(6, 1, 7, bs("minecraft:lever[face=wall,facing=north,powered=false]"));
        // workbench / contraption diorama
        b.set(1, 1, 1, CRAFTING_TABLE);
        b.set(2, 1, 1, BARREL);
        b.set(3, 1, 1, bs("minecraft:dispenser[facing=south,triggered=false]"));
        b.set(4, 1, 1, bs("minecraft:target"));
        line(b, 1, 1, 3, 6, 3, blackstone); // bench
        b.set(2, 2, 3, bs("minecraft:observer[facing=up,powered=false]"));
        b.set(3, 2, 3, bs("minecraft:repeater[facing=south,delay=1,locked=false,powered=false]"));
        b.set(4, 2, 3, bs("minecraft:redstone_torch[lit=true]"));
        b.set(5, 2, 3, bs("minecraft:piston[facing=up,extended=false]"));
        b.set(6, 2, 3, bs("minecraft:sticky_piston[facing=up,extended=false]"));
        // flat blackstone-slab roof with a glass skylight
        flatRoof(b, 5, 0, 0, 8, 8, blackstoneSlabTop);
        floor(b, 5, 3, 3, 5, 5, GLASS);
        // functional redstone-lamp ceiling lights
        b.set(2, 4, 2, redstoneLamp);
        b.set(6, 4, 6, redstoneLamp);
        return b.build();
    }

    /** §3.22 Diamond Vault. 9×9×8 (W×L×H) → builder(9,8,9). Diamond-block treasure core. */
    private static Blueprint diamondVault() {
        Blueprint.Builder b = Blueprint.builder("Diamond Vault", 9, 8, 9);
        BlueprintBlockState polishedDeepslate = bs("minecraft:polished_deepslate");
        BlueprintBlockState deepslateBricks = bs("minecraft:deepslate_bricks");
        BlueprintBlockState chiseledDeepslate = bs("minecraft:chiseled_deepslate");
        BlueprintBlockState reinforcedDeepslate = bs("minecraft:reinforced_deepslate");
        BlueprintBlockState diamondBlock = bs("minecraft:diamond_block");
        BlueprintBlockState netheriteBlock = bs("minecraft:netherite_block");
        BlueprintBlockState gildedBlackstone = bs("minecraft:gilded_blackstone");
        BlueprintBlockState polishedDeepslateSlabTop = bs("minecraft:polished_deepslate_slab[type=top]");
        floor(b, 0, 0, 0, 8, 8, polishedDeepslate);
        // thick vault walls: deepslate-brick outer + reinforced inner accents
        walls(b, 0, 0, 8, 8, 1, 5, deepslateBricks);
        walls(b, 1, 1, 7, 7, 1, 5, reinforcedDeepslate);
        // chiseled pilasters
        pillar(b, 0, 0, 1, 5, chiseledDeepslate);
        pillar(b, 8, 0, 1, 5, chiseledDeepslate);
        pillar(b, 0, 8, 1, 5, chiseledDeepslate);
        pillar(b, 8, 8, 1, 5, chiseledDeepslate);
        // vault door: netherite-framed iron door behind an iron-bars cage (front z=0)
        pillar(b, 3, 0, 1, 3, netheriteBlock);
        pillar(b, 5, 0, 1, 3, netheriteBlock);
        line(b, 4, 3, 0, 5, 0, netheriteBlock);
        b.set(4, 1, 0, bs("minecraft:iron_door[facing=south,half=lower,hinge=left,open=false,powered=false]"));
        b.set(4, 2, 0, bs("minecraft:iron_door[facing=south,half=upper,hinge=left,open=false,powered=false]"));
        b.set(4, 1, 1, IRON_BARS);
        b.set(4, 2, 1, IRON_BARS);
        b.set(3, 1, 0, CHAIN);
        b.set(5, 1, 0, CHAIN);
        // treasure core: 3×3 diamond plinth on gilded blackstone, caged in iron bars
        floor(b, 1, 3, 3, 5, 5, gildedBlackstone);
        for (int x = 3; x <= 5; x++) {
            for (int z = 3; z <= 5; z++) {
                b.set(x, 2, z, diamondBlock);
                b.set(x, 3, z, (x == 4 && z == 4) ? diamondBlock : SEA_LANTERN); // lit from within
            }
        }
        b.set(4, 4, 4, diamondBlock); // apex of the plinth
        // iron-bars cage around the plinth
        for (int x = 2; x <= 6; x++) {
            b.set(x, 2, 2, IRON_BARS);
            b.set(x, 2, 6, IRON_BARS);
            b.set(x, 3, 2, IRON_BARS);
            b.set(x, 3, 6, IRON_BARS);
        }
        for (int z = 2; z <= 6; z++) {
            b.set(2, 2, z, IRON_BARS);
            b.set(6, 2, z, IRON_BARS);
            b.set(2, 3, z, IRON_BARS);
            b.set(6, 3, z, IRON_BARS);
        }
        // security: sculk + sculk sensors in the floor
        b.set(1, 0, 1, bs("minecraft:sculk"));
        b.set(7, 0, 7, bs("minecraft:sculk"));
        b.set(1, 1, 1, bs("minecraft:sculk_sensor[sculk_sensor_phase=inactive,power=0,waterlogged=false]"));
        b.set(7, 1, 7, bs("minecraft:sculk_sensor[sculk_sensor_phase=inactive,power=0,waterlogged=false]"));
        // soul-lantern cold lighting on chains
        b.set(1, 5, 4, CHAIN);
        b.set(1, 4, 4, SOUL_HANGING_LANTERN);
        b.set(7, 5, 4, CHAIN);
        b.set(7, 4, 4, SOUL_HANGING_LANTERN);
        // roof: polished-deepslate slab + tinted-glass oculus over the plinth
        flatRoof(b, 6, 0, 0, 8, 8, polishedDeepslateSlabTop);
        floor(b, 6, 3, 3, 5, 5, TINTED_GLASS);
        flatRoof(b, 7, 0, 0, 8, 8, polishedDeepslateSlabTop);
        floor(b, 7, 3, 3, 5, 5, TINTED_GLASS);
        return b.build();
    }

    // ----- Large (T6–T7 footprint) -----

    /** §3.14 Village Church. 9×15×12 (W×L×H) → builder(9,12,15). Nave + steeple + cross. */
    private static Blueprint church() {
        Blueprint.Builder b = Blueprint.builder("Village Church", 9, 12, 15);
        BlueprintBlockState whiteGlass = bs("minecraft:white_stained_glass");
        BlueprintBlockState blueGlass = bs("minecraft:blue_stained_glass");
        BlueprintBlockState redGlass = bs("minecraft:red_stained_glass");
        BlueprintBlockState yellowGlass = bs("minecraft:yellow_stained_glass");
        BlueprintBlockState[] glasses = {whiteGlass, blueGlass, redGlass, yellowGlass};
        // foundation (9 wide × 15 deep)
        floor(b, 0, 0, 0, 8, 14, COBBLE);
        // nave walls y=1..6, mossy flecks at intervals (roof rises across the 9-wide span,
        // peaking at y=11 — the H=12 budget forces the walls to stop at y=6)
        walls(b, 0, 0, 8, 14, 1, 6, STONE_BRICKS);
        b.set(0, 3, 5, MOSSY_STONE_BRICKS);
        b.set(8, 4, 9, MOSSY_STONE_BRICKS);
        b.set(0, 5, 11, MOSSY_STONE_BRICKS);
        // tall lancet (1-wide, 3-tall) stained-glass windows down both long walls
        int gi = 0;
        for (int z : new int[]{3, 6, 9, 12}) {
            BlueprintBlockState g = glasses[gi++ % glasses.length];
            for (int y = 3; y <= 5; y++) {
                b.set(0, y, z, g);
                b.set(8, y, z, g);
            }
            // buttress pilasters between windows
            b.set(0, 1, z - 1, bs("minecraft:stone_brick_stairs[facing=east,half=bottom,shape=straight]"));
            b.set(8, 1, z - 1, bs("minecraft:stone_brick_stairs[facing=west,half=bottom,shape=straight]"));
        }
        // entrance: double spruce doors at the front (z=0) with chiseled arch surround
        door2(b, 3, 1, 0, "spruce", "N");
        door2(b, 5, 1, 0, "spruce", "N");
        line(b, 3, 3, 0, 5, 0, CHISELED_STONE_BRICKS);
        b.set(4, 4, 0, CHISELED_STONE_BRICKS);
        // chancel (back z=12..14): raised dais + altar + rose window
        floor(b, 1, 2, 12, 6, 14, STONE_BRICK_SLAB_BOTTOM);
        b.set(4, 2, 13, CHISELED_STONE_BRICKS); // altar
        b.set(3, 2, 13, LANTERN);
        b.set(5, 2, 13, LANTERN);
        // rose window: 3×3 stained-glass cluster high on the back wall (z=14)
        for (int x = 3; x <= 5; x++) {
            for (int y = 3; y <= 5; y++) {
                b.set(x, y, 14, glasses[(x + y) % glasses.length]);
            }
        }
        // pews: two columns of spruce stairs down the nave, aisle between
        for (int z = 3; z <= 11; z += 2) {
            for (int x = 2; x <= 3; x++) b.set(x, 1, z, bs("minecraft:spruce_stairs[facing=north,half=bottom,shape=straight]"));
            for (int x = 5; x <= 6; x++) b.set(x, 1, z, bs("minecraft:spruce_stairs[facing=north,half=bottom,shape=straight]"));
        }
        // roof: gable with ridge running along Z (the long axis), sloping across the
        // 9-wide X span. Built inline (gableRoofX slopes the wrong way for a long nave).
        {
            BlueprintBlockState wSlope = bs("minecraft:stone_brick_stairs[facing=east,half=bottom,shape=straight]");
            BlueprintBlockState eSlope = bs("minecraft:stone_brick_stairs[facing=west,half=bottom,shape=straight]");
            int y = 7, xw = 0, xe = 8;
            while (xe - xw > 1) {
                for (int z = 0; z <= 14; z++) {
                    b.set(xw, y, z, wSlope);
                    b.set(xe, y, z, eSlope);
                }
                xw++; xe--; y++;
            }
            // ridge cap (xw==xe), running the full depth
            for (int z = 0; z <= 14; z++) b.set(xw, y, z, STONE_BRICK_SLAB_TOP);
            // close the two gable ends (z=0 and z=14) with stone brick up to the ridge
            for (int zEnd : new int[]{0, 14}) {
                int yy = 7, a = 0, c = 8;
                while (c - a > 1) {
                    for (int x = a + 1; x <= c - 1; x++) b.set(x, yy, zEnd, STONE_BRICKS);
                    a++; c--; yy++;
                }
                b.set(4, yy, zEnd, STONE_BRICKS);
            }
        }
        // STEEPLE: 3×3 tower in the front-left corner (x0..2,z0..2) rising tallest
        for (int y = 1; y <= 8; y++) {
            walls(b, 0, 0, 2, 2, y, y, STONE_BRICKS);
        }
        // belfry openings near the top (fence) + a floor bell
        fenceRing(b, 7, 0, 0, 2, 2, DARK_OAK_FENCE);
        b.set(1, 6, 1, OAK_PLANKS); // belfry floor
        b.set(1, 7, 1, BELL_FLOOR);
        b.set(1, 5, 1, GLOWSTONE);  // spire light behind a louvre
        // pyramid spire on the tower (y=9) + cross at the peak (y=10..11)
        pyramidRoof(b, 0, 0, 2, 2, 9, "stone_brick_stairs", CHISELED_STONE_BRICKS);
        // fill the spire core (the y=9 ring leaves (1,9,1) hollow) so the cap and cross
        // above rest on solid stone instead of floating over a 1-block air gap
        b.set(1, 9, 1, CHISELED_STONE_BRICKS);
        b.set(1, 10, 1, DARK_OAK_FENCE);
        b.set(1, 11, 1, DARK_OAK_FENCE);
        b.set(0, 11, 1, DARK_OAK_FENCE); // cross arm
        b.set(2, 11, 1, DARK_OAK_FENCE);
        // nave-centreline backed lanterns. The nave has no flat ceiling (open to
        // the gable), so an oak tie-beam at y=7 gives the chain a solid backing
        // directly above (otherwise the chain/lantern would float in the attic).
        b.set(4, 7, 5, OAK_LOG_X);
        b.set(4, 6, 5, CHAIN);
        b.set(4, 5, 5, HANGING_LANTERN);
        b.set(4, 7, 10, OAK_LOG_X);
        b.set(4, 6, 10, CHAIN);
        b.set(4, 5, 10, HANGING_LANTERN);
        return b.build();
    }

    /** §3.15 Manor House. 13×11×11 (W×L×H) → builder(13,11,11). Timber upper + hip roof. */
    private static Blueprint manorHouse() {
        Blueprint.Builder b = Blueprint.builder("Manor House", 13, 11, 11);
        floor(b, 0, 0, 0, 12, 10, COBBLE);
        // garden boundary wall projecting in front (z=0 row outside is implicit; use a low lip)
        // ground floor (y=1..4): stone-brick walls
        walls(b, 0, 0, 12, 10, 1, 4, STONE_BRICKS);
        corners(b, 0, 0, 12, 10, 1, 4, STONE_BRICKS);
        // grand door under an oak-stairs porch canopy (front centre)
        door2(b, 6, 1, 0, "dark_oak", "N");
        for (int x = 5; x <= 7; x++) b.set(x, 4, 0, bs("minecraft:oak_stairs[facing=south,half=bottom,shape=straight]"));
        // two-tall ground windows along the long walls
        for (int x : new int[]{2, 4, 8, 10}) {
            window2(b, x, 2, 0, GLASS_PANE, null);
            b.set(x, 3, 0, GLASS_PANE);
            b.set(x, 2, 10, GLASS_PANE);
            b.set(x, 3, 10, GLASS_PANE);
        }
        b.set(0, 2, 5, GLASS_PANE); b.set(0, 3, 5, GLASS_PANE);
        b.set(12, 2, 5, GLASS_PANE); b.set(12, 3, 5, GLASS_PANE);
        // mid floor over the ground storey, with a stairwell hatch at (11,2) for a ladder
        for (int x = 1; x <= 11; x++) {
            for (int z = 1; z <= 9; z++) {
                if (x == 11 && z == 2) continue; // ladder hatch up to the upper floor
                b.set(x, 5, z, SPRUCE_PLANKS);
            }
        }
        // ladder joining the two storeys — they were SEALED (no stairs/ladder anywhere),
        // so the upper floor was unreachable. Backed by the east wall at (12,2)
        // (facing=west); runs to y=6, the upper-floor standing level.
        pillar(b, 11, 2, 1, 6, bs("minecraft:ladder[facing=west,waterlogged=false]"));
        // upper floor (y=5..8): timber-frame
        timberFrame(b, 0, 0, 12, 10, 5, 8, SPRUCE_PLANKS, STRIPPED_OAK_Y, STRIPPED_OAK_X);
        // two-tall upper windows above the ground ones
        for (int x : new int[]{2, 4, 8, 10}) {
            b.set(x, 6, 0, GLASS_PANE);
            b.set(x, 7, 0, GLASS_PANE);
            b.set(x, 6, 10, GLASS_PANE);
            b.set(x, 7, 10, GLASS_PANE);
        }
        // chimney on a gable end rising past the roof
        pillar(b, 1, 5, 1, 10, COBBLE);
        // low hip roof (slopes all four sides): 2 inward stair rings (y=9..10) then a
        // flat slab cap — height-capped to the H=11 budget (y 0..10).
        {
            BlueprintBlockState north = bs("minecraft:stone_brick_stairs[facing=south,half=bottom,shape=straight]");
            BlueprintBlockState south = bs("minecraft:stone_brick_stairs[facing=north,half=bottom,shape=straight]");
            BlueprintBlockState west = bs("minecraft:stone_brick_stairs[facing=east,half=bottom,shape=straight]");
            BlueprintBlockState east = bs("minecraft:stone_brick_stairs[facing=west,half=bottom,shape=straight]");
            int ax0 = 0, az0 = 0, ax1 = 12, az1 = 10, y = 9;
            for (int step = 0; step < 2; step++) {
                for (int x = ax0; x <= ax1; x++) { b.set(x, y, az0, north); b.set(x, y, az1, south); }
                for (int z = az0; z <= az1; z++) { b.set(ax0, y, z, west); b.set(ax1, y, z, east); }
                ax0++; az0++; ax1--; az1--; y++;
            }
            floor(b, 10, ax0, az0, ax1, az1, STONE_BRICK_SLAB_TOP);
        }
        // re-assert the chimney top AFTER the roof so it pokes through instead of being
        // capped by the hip ring (the ring overwrote (1,10,5)); y=10 is the H=11 ceiling
        b.set(1, 9, 5, COBBLE);
        b.set(1, 10, 5, COBBLE);
        // front dormer: a small gable with a glass pane cut into the front slope
        b.set(6, 9, 1, GLASS_PANE);
        b.set(5, 9, 1, bs("minecraft:stone_brick_stairs[facing=west,half=bottom,shape=straight]"));
        b.set(7, 9, 1, bs("minecraft:stone_brick_stairs[facing=east,half=bottom,shape=straight]"));
        // interior: library wall, beds, lantern sconces on chains
        for (int z = 1; z <= 9; z += 2) b.set(11, 6, z, BOOKSHELF);
        bed(b, 2, 6, 8, "white", "south");  // head z=8, foot z=7
        bed(b, 10, 6, 8, "white", "south"); // head z=8, foot z=7
        b.set(6, 4, 5, CHAIN);
        b.set(6, 3, 5, HANGING_LANTERN);
        // upper lantern: chain up to the y=10 hip-roof slab cap (y=9 closes the gap)
        b.set(6, 9, 5, CHAIN);
        b.set(6, 8, 5, CHAIN);
        b.set(6, 7, 5, HANGING_LANTERN);
        // window boxes (flower pots) on ground-floor sills
        for (int x : new int[]{2, 4, 8, 10}) b.set(x, 1, 0, bs("minecraft:flower_pot"));
        return b.build();
    }

    /** §3.18 Copper Observatory. 11×11×13 (W×L×H) → builder(11,13,11). Patina copper dome. */
    private static Blueprint copperObservatory() {
        Blueprint.Builder b = Blueprint.builder("Copper Observatory", 11, 13, 11);
        BlueprintBlockState andesite = bs("minecraft:polished_andesite");
        // NB: copper_bulb / copper_grate are 1.21+ (Tricky Trials) and do not exist in
        // 1.20.1 (resolve() would silently drop them). Use 1.20.1-valid stand-ins that
        // keep the build's copper signature (the dome) intact: sea_lantern glow + iron-bar vents.
        BlueprintBlockState domeGlow = SEA_LANTERN;
        BlueprintBlockState copperGrate = IRON_BARS;
        // foundation + round andesite floor (centre 5,5, r=4)
        floor(b, 0, 0, 0, 10, 10, STONE_BRICKS);
        disc(b, 0, 5, 5, 4, andesite);
        // cylindrical stone-brick drum (y=1..6)
        for (int y = 1; y <= 6; y++) {
            circleRing(b, y, 5, 5, 4, STONE_BRICKS);
        }
        // entrance (spruce door, faces south) on the north side
        b.set(5, 1, 1, bs("minecraft:spruce_door[facing=south,half=lower,hinge=left,open=false,powered=false]"));
        b.set(5, 2, 1, bs("minecraft:spruce_door[facing=south,half=upper,hinge=left,open=false,powered=false]"));
        // glass-pane windows + copper-grate vents around the drum
        b.set(1, 3, 5, GLASS_PANE);
        b.set(9, 3, 5, GLASS_PANE);
        b.set(5, 3, 9, GLASS_PANE);
        b.set(2, 4, 8, copperGrate);
        b.set(8, 4, 2, copperGrate);
        // COPPER DOME (y=7..11): patina gradient, greener toward the top. The
        // springing course (y=7) is a ring (open drum top); the rising courses are
        // filled DISCS so the hemisphere closes — stacked rings alone leave a ring of
        // holes near the apex (radius steps 4→3→0), which reads as an open roof.
        circleRing(b, 7, 5, 5, 4, copperPatina(0)); // springing ring (cut copper)
        for (int dy = 1; dy <= 4; dy++) {
            int level = Math.min(3, dy); // gradient cut→exposed→weathered→oxidized upward
            double ringR = Math.sqrt(16.0 - (dy * dy));
            int rr = (int) Math.round(ringR);
            if (rr <= 0) {
                b.set(5, 7 + dy, 5, copperPatina(level));
            } else {
                disc(b, 7 + dy, 5, 5, rr, copperPatina(level));
            }
        }
        // 2-wide vertical telescope slit on one side, shuttered with tinted glass
        for (int y = 7; y <= 11; y++) {
            b.set(5, y, 1, TINTED_GLASS);
            b.set(6, y, 1, TINTED_GLASS);
        }
        // apex lightning-rod finial
        b.set(5, 12, 5, LIGHTNING_ROD);
        // interior: copper-bulb ring (T2 glow), telescope (end-rod + copper barrel), lectern, books
        b.set(3, 6, 5, domeGlow);
        b.set(7, 6, 5, domeGlow);
        b.set(5, 6, 3, domeGlow);
        b.set(5, 6, 7, domeGlow);
        b.set(5, 1, 5, copperPatina(0));    // telescope mount
        b.set(5, 2, 5, END_ROD);            // telescope barrel angled up toward slit
        b.set(5, 3, 4, END_ROD);
        b.set(4, 1, 6, LECTERN);
        b.set(6, 1, 6, BOOKSHELF);
        b.set(3, 1, 7, BOOKSHELF);
        return b.build();
    }

    /** §3.21 Emerald Market Hall. 13×13×10 (W×L×H) → builder(13,10,13). Emerald frieze, arcade. */
    private static Blueprint emeraldMarketHall() {
        Blueprint.Builder b = Blueprint.builder("Emerald Market Hall", 13, 10, 13);
        BlueprintBlockState cutSandstone = bs("minecraft:cut_sandstone");
        BlueprintBlockState emeraldBlock = bs("minecraft:emerald_block");
        BlueprintBlockState greenTerracotta = bs("minecraft:green_terracotta");
        floor(b, 0, 0, 0, 12, 12, cutSandstone);
        // colonnade: oak-log columns (y=1..5) on a stone-brick stylobate pad — open sides
        for (int x = 0; x <= 12; x += 3) {
            for (int z = 0; z <= 12; z += 3) {
                b.set(x, 1, z, STONE_BRICKS); // pad under each column
                pillar(b, x, z, 1, 5, OAK_LOG_Y);
            }
        }
        // arches of oak stairs between adjacent columns along z (top course)
        for (int x = 0; x <= 12; x += 3) {
            for (int z = 0; z < 12; z += 3) {
                b.set(x, 5, z + 1, bs("minecraft:oak_stairs[facing=south,half=top,shape=straight]"));
                b.set(x, 5, z + 2, bs("minecraft:oak_stairs[facing=north,half=top,shape=straight]"));
            }
        }
        // EMERALD frieze/cornice band running the top course (y=5) around the perimeter
        fenceRing(b, 5, 0, 0, 12, 12, emeraldBlock);
        // stalls under the arcade: striped awnings + trader tables
        for (int x = 1; x <= 11; x += 4) {
            b.set(x, 4, 1, GREEN_WOOL);
            b.set(x + 1, 4, 1, WHITE_WOOL);
            b.set(x, 1, 2, BARREL);
        }
        b.set(2, 1, 10, FLETCHING_TABLE);
        b.set(10, 1, 10, CARTOGRAPHY_TABLE);
        b.set(6, 1, 10, LECTERN);
        b.set(8, 1, 10, COMPOSTER); // stall composter (spec §3.21)
        // low hip roof: 3 inward stair rings (y=6..8) then a flat green-terracotta cap (y=9)
        // — height-capped so the 13-span closes within the H=10 budget.
        {
            BlueprintBlockState north = bs("minecraft:oak_stairs[facing=south,half=bottom,shape=straight]");
            BlueprintBlockState south = bs("minecraft:oak_stairs[facing=north,half=bottom,shape=straight]");
            BlueprintBlockState west = bs("minecraft:oak_stairs[facing=east,half=bottom,shape=straight]");
            BlueprintBlockState east = bs("minecraft:oak_stairs[facing=west,half=bottom,shape=straight]");
            int ax0 = 0, az0 = 0, ax1 = 12, az1 = 12, y = 6;
            for (int step = 0; step < 3; step++) {
                for (int x = ax0; x <= ax1; x++) { b.set(x, y, az0, north); b.set(x, y, az1, south); }
                for (int z = az0; z <= az1; z++) { b.set(ax0, y, z, west); b.set(ax1, y, z, east); }
                ax0++; az0++; ax1--; az1--; y++;
            }
            // flat terracotta cap over the remaining interior at y=9
            floor(b, 9, ax0, az0, ax1, az1, greenTerracotta);
        }
        // centrepiece: ceiling bell at the hall centre. A ceiling bell needs a SOLID
        // block directly above (a chain alone won't hold it), so hang it from an oak
        // tie-block; the tie itself hangs from chains climbing to the y=9 terracotta cap.
        b.set(6, 6, 6, OAK_LOG_Y); // solid mount directly above the bell
        b.set(6, 7, 6, CHAIN);
        b.set(6, 8, 6, CHAIN);
        b.set(6, 5, 6, BELL_CEILING);
        // lantern chains between columns — climb to the y=9 cap so they don't float
        pillar(b, 3, 3, 5, 8, CHAIN); b.set(3, 4, 3, HANGING_LANTERN);
        pillar(b, 9, 9, 5, 8, CHAIN); b.set(9, 4, 9, HANGING_LANTERN);
        return b.build();
    }

    /** §3.16 Lighthouse. 9×9×16 (W×L×H) → builder(9,16,9). Striped tower + sea-lantern room. */
    private static Blueprint lighthouse() {
        Blueprint.Builder b = Blueprint.builder("Lighthouse", 9, 16, 9);
        BlueprintBlockState whiteConcrete = bs("minecraft:white_concrete");
        BlueprintBlockState redConcrete = bs("minecraft:red_concrete");
        // cobble footing + round tower base (centre 4,4)
        floor(b, 0, 0, 0, 8, 8, COBBLE);
        disc(b, 0, 4, 4, 3, STONE_BRICKS);
        // tower (y=1..11): candy-stripe bands (3 white, 2 red repeating)
        for (int y = 1; y <= 11; y++) {
            int band = (y - 1) % 5;
            BlueprintBlockState mat = (band < 3) ? whiteConcrete : redConcrete;
            circleRing(b, y, 4, 4, 3, mat);
        }
        // door at base (north side, faces south) + ladder inside
        b.set(4, 1, 1, bs("minecraft:oak_door[facing=south,half=lower,hinge=left,open=false,powered=false]"));
        b.set(4, 2, 1, bs("minecraft:oak_door[facing=south,half=upper,hinge=left,open=false,powered=false]"));
        // ladder backed by the wall ring at (3,7) — the old (4,3) ladder sat in the hollow
        // centre attached to nothing (facing=south wanted a block at (4,2), interior air),
        // so it couldn't be climbed. (3,7) is a solid concrete ring cell with no porthole.
        pillar(b, 3, 6, 1, 11, bs("minecraft:ladder[facing=north,waterlogged=false]"));
        // glass portholes spiraling up
        b.set(7, 4, 4, GLASS);
        b.set(4, 6, 7, GLASS);
        b.set(1, 8, 4, GLASS);
        b.set(4, 10, 1, GLASS);
        // gallery (y=12): widen by 1 with a stair corbel ring + iron-bars railing
        circleRing(b, 12, 4, 4, 4, bs("minecraft:stone_brick_stairs[facing=north,half=top,shape=straight]"));
        circleRing(b, 13, 4, 4, 4, IRON_BARS);
        // lantern room (y=13..14): glass drum with sea-lantern core
        circleRing(b, 13, 4, 4, 3, GLASS);
        circleRing(b, 14, 4, 4, 3, GLASS);
        b.set(4, 13, 4, SEA_LANTERN);
        b.set(4, 14, 4, SEA_LANTERN);
        // cap (y=15): copper conical cap (patina accent — weathered). Disc r=3 so it
        // fully covers the r=3 lantern-room drum below — no open ring at the rim.
        disc(b, 15, 4, 4, 3, copperPatina(2));
        b.set(4, 15, 4, copperPatina(2));
        return b.build();
    }

    /** §3.17 Castle Keep. 21×21×16 (W×L×H) → builder(21,16,21). Curtain wall + towers + keep. */
    private static Blueprint castleKeep() {
        Blueprint.Builder b = Blueprint.builder("Castle Keep", 21, 16, 21);
        BlueprintBlockState polishedAndesite = bs("minecraft:polished_andesite");
        floor(b, 0, 0, 0, 20, 20, COBBLE);
        // curtain wall (y=1..6), 2 thick, with cracked/mossy flecks
        walls(b, 0, 0, 20, 20, 1, 6, STONE_BRICKS);
        // inner skin → 2-thick, but leave the gatehouse passage OPEN on the north inner
        // face (x=9..11, y=1..4) so the gate doors tunnel into the courtyard. A plain
        // walls() call sealed the passage with solid stone, making the keep unenterable.
        for (int y = 1; y <= 6; y++) {
            for (int x = 1; x <= 19; x++) {
                if (y <= 4 && x >= 9 && x <= 11) continue; // gate passage opening
                b.set(x, y, 1, STONE_BRICKS);            // north inner face
            }
            line(b, y, 1, 19, 19, 19, STONE_BRICKS); // south inner face
            line(b, y, 1, 1, 1, 19, STONE_BRICKS);   // west inner face
            line(b, y, 19, 1, 19, 19, STONE_BRICKS); // east inner face
        }
        b.set(0, 3, 7, CRACKED_STONE_BRICKS);
        b.set(20, 4, 13, MOSSY_STONE_BRICKS);
        b.set(7, 2, 0, MOSSY_STONE_BRICKS);
        // wall-walk behind the parapet (slab) + crenellations on top (y=7)
        for (int x = 1; x <= 19; x++) {
            b.set(x, 7, 1, STONE_BRICK_SLAB_TOP);
            b.set(x, 7, 19, STONE_BRICK_SLAB_TOP);
        }
        for (int z = 1; z <= 19; z++) {
            b.set(1, 7, z, STONE_BRICK_SLAB_TOP);
            b.set(19, 7, z, STONE_BRICK_SLAB_TOP);
        }
        // crenellations flush on the y=6 curtain-wall top (y=7) — the outer perimeter
        // wall ends at y=6, so placing merlons at y=7 avoids a floating y=7 gap. The
        // wall-walk slabs above sit on the inner ring (z=1/19, x=1/19), not these cells.
        crenellate(b, 7, 0, 0, 20, 20, STONE_BRICK_WALL);
        // gatehouse (front z=0 centre): 3-wide gate with an iron-bars portcullis and
        // dark-oak door leaves. walls() drew solid stone across z=0; the door/bar/arch
        // features below overwrite the opening cells (set() replaces an existing block).
        // The lower opening (doors + central portcullis bars) supersedes the wall there.
        b.set(9, 1, 0, bs("minecraft:dark_oak_door[facing=south,half=lower,hinge=left,open=false,powered=false]"));
        b.set(9, 2, 0, bs("minecraft:dark_oak_door[facing=south,half=upper,hinge=left,open=false,powered=false]"));
        b.set(11, 1, 0, bs("minecraft:dark_oak_door[facing=south,half=lower,hinge=right,open=false,powered=false]"));
        b.set(11, 2, 0, bs("minecraft:dark_oak_door[facing=south,half=upper,hinge=right,open=false,powered=false]"));
        // Portcullis: a barred grate filling the upper half of the gateway (y=3..4,
        // x=9..11), seated over the dark-oak door leaves below. Spanning the full
        // 3-wide opening lets the end bars connect to the stone jambs (x=8/x=12 are
        // solid wall) and to each other, so the grate actually renders. A lone
        // centre column at y=1..2 (flanked only by the door leaves, which iron bars
        // don't connect to) would render as an invisible stub, so the foot of the
        // opening is left to the doors and the portcullis reads as a raised grate.
        for (int y = 3; y <= 4; y++) {
            for (int x = 9; x <= 11; x++) b.set(x, y, 0, IRON_BARS);
        }
        line(b, 5, 9, 0, 11, 0, CHISELED_STONE_BRICKS); // gate arch
        // four corner towers (3×3, y=1..10, taller than the wall) crenellated
        int[][] towers = {{0, 0}, {18, 0}, {0, 18}, {18, 18}};
        for (int[] t : towers) {
            int tx = t[0], tz = t[1];
            for (int y = 1; y <= 10; y++) walls(b, tx, tz, tx + 2, tz + 2, y, y, STONE_BRICKS);
            window2(b, tx + 1, 6, tz, GLASS_PANE, null);
            b.set(tx, 8, tz + 1, IRON_BARS); // arrow slit
            crenellate(b, 11, tx, tz, tx + 2, tz + 2, STONE_BRICK_WALL);
        }
        // central keep: 7×7 tower (x,z 7..13), y=1..14, tallest element. Floor at y=0
        // (flush with the courtyard) — at y=1 it was a raised step you couldn't walk onto,
        // sealing the keep off through its own door.
        floor(b, 0, 7, 7, 13, 13, polishedAndesite);
        for (int y = 1; y <= 14; y++) walls(b, 7, 7, 13, 13, y, y, STONE_BRICKS);
        door2(b, 10, 1, 7, "oak", "N");
        for (int z = 9; z <= 11; z += 2) {
            window2(b, 7, 4, z, GLASS_PANE, null);
            window2(b, 13, 4, z, GLASS_PANE, null);
        }
        pillar(b, 8, 8, 1, 13, LADDER_SOUTH); // interior climb
        crenellate(b, 15, 7, 7, 13, 13, STONE_BRICK_WALL);
        // great-hall lantern chandelier on a chain. The keep is an open-topped tower
        // (no ceiling until the y=15 battlement), so add an oak tie-beam spanning the
        // keep at y=7 (wall-to-wall x=7..13) to back the chain — otherwise it floats.
        line(b, 7, 7, 10, 13, 10, OAK_LOG_X);
        b.set(10, 6, 10, CHAIN); b.set(10, 5, 10, HANGING_LANTERN);
        // courtyard braziers (lit campfires) in the corners
        b.set(4, 1, 4, CAMPFIRE);
        b.set(16, 1, 16, CAMPFIRE);
        // gatehouse passage lanterns on chains
        b.set(9, 5, 1, CHAIN); b.set(9, 4, 1, HANGING_LANTERN);
        b.set(11, 5, 1, CHAIN); b.set(11, 4, 1, HANGING_LANTERN);
        return b.build();
    }

    /**
     * Castle gatehouse (§3.H) — 9(W) × 11(up) × 7(depth), disc T1, vanilla only.
     *
     * <p>Twin square stone-brick towers (west x=0..2, east x=6..8) flank a 3-wide
     * walk-through archway (x=3..5) tunneling the full depth z=0..6 at y=1..3. The
     * tower inner walls (x=2 and x=6) are solid stone the whole height — they are the
     * jambs the portcullis anchors to.
     *
     * <p><b>Portcullis (render-safe iron grate).</b> An iron-bars grate fills the
     * front face (z=0) across the full 3-wide opening, raised to y=2..4 so you can
     * walk under it at y=1 (true walk-through gate). Spanning the full width is what
     * makes every bar render: the end columns (x=3, x=5) sit against the solid stone
     * jambs (x=2 / x=6 present a sturdy face), and the centre column (x=4) connects to
     * both neighbours — so no bar is left as an invisible center-post stub. (Same fix
     * castle_keep uses: a lone bar flanked only by air/doors renders as a stub; a grate
     * anchored jamb-to-jamb does not.) A chain hangs from the arch lintel to the grate
     * top on each side, reading as the portcullis hoist.
     *
     * <p>Crenellated battlements cap both towers and the gate span. The west tower
     * holds an upper guardroom (floor at y=6, ceiling open to its battlement) lit by a
     * chain-hung lantern, with arrow-slit windows (single glass panes flanked by stone)
     * on the outer faces. Wall-mounted lanterns flank the passage mouth.
     */
    private static Blueprint gatehouse() {
        Blueprint.Builder b = Blueprint.builder("Gatehouse", 9, 11, 7);
        // footing
        floor(b, 0, 0, 0, 8, 6, COBBLE);

        // --- twin towers: solid-walled square shells, y=1..7 (taller than the gate span) ---
        // West tower (x=0..2) and east tower (x=6..8), each spanning the full depth z=0..6.
        for (int y = 1; y <= 7; y++) {
            walls(b, 0, 0, 2, 6, y, y, STONE_BRICKS); // west tower shell
            walls(b, 6, 0, 8, 6, y, y, STONE_BRICKS); // east tower shell
        }
        // weathering flecks on the outer faces
        b.set(0, 3, 4, CRACKED_STONE_BRICKS);
        b.set(8, 4, 2, MOSSY_STONE_BRICKS);
        b.set(1, 1, 0, MOSSY_STONE_BRICKS);

        // --- gate span between the towers (x=3..5) ---
        // Front/back lintels above the opening close the curtain between the towers
        // at y=4..5 (the opening itself is y=1..3). The jambs are the tower walls x=2/x=6.
        for (int y = 4; y <= 5; y++) {
            line(b, y, 3, 0, 5, 0, STONE_BRICKS); // front lintel
            line(b, y, 3, 6, 5, 6, STONE_BRICKS); // back lintel
        }
        b.set(4, 5, 0, CHISELED_STONE_BRICKS); // decorative keystone, front
        b.set(4, 5, 6, CHISELED_STONE_BRICKS); // decorative keystone, back
        // span ceiling over the passage (y=6) so the battlement above has a floor to sit on
        floor(b, 6, 3, 0, 5, 6, STONE_BRICKS);

        // --- PORTCULLIS: full-width iron grate on the front face (z=0), raised to y=2..4 ---
        // y=1 is left open so the gate is walk-through. Every bar connects: end columns
        // (x=3,x=5) abut the stone jambs (x=2,x=6); the centre column (x=4) bridges them.
        for (int y = 2; y <= 4; y++) {
            for (int x = 3; x <= 5; x++) {
                b.set(x, y, 0, IRON_BARS);
            }
        }
        // hoist chains from the lintel keystone down onto the grate top, one per jamb side
        b.set(3, 5, 0, CHAIN);
        b.set(5, 5, 0, CHAIN);

        // --- battlements ---
        crenellate(b, 8, 0, 0, 2, 6, STONE_BRICK_WALL); // west tower top
        crenellate(b, 8, 6, 0, 8, 6, STONE_BRICK_WALL); // east tower top
        crenellate(b, 7, 3, 0, 5, 6, STONE_BRICK_WALL); // gate span top (one course lower)

        // --- west tower upper guardroom (floor y=6, lit; open to its y=8 battlement) ---
        floor(b, 6, 0, 0, 2, 6, STONE_BRICKS); // guardroom floor / passage ceiling
        // arrow-slit windows: a single glass pane flanked horizontally by stone (the
        // tower wall cells on either side present sturdy faces, so each pane renders).
        window2(b, 0, 4, 2, GLASS_PANE, null); // west outer face
        window2(b, 0, 4, 4, GLASS_PANE, null); // west outer face
        // chain-hung lantern from the guardroom's battlement crossbeam
        b.set(1, 7, 3, OAK_LOG_X);     // tie-beam to back the chain
        b.set(1, 6, 3, CHAIN);
        b.set(1, 5, 3, HANGING_LANTERN);
        // climb into the guardroom
        pillar(b, 1, 1, 1, 5, LADDER_SOUTH);

        // --- passage-mouth lighting: hanging lanterns just inside both ends ---
        b.set(3, 4, 1, CHAIN); b.set(3, 3, 1, HANGING_LANTERN); // front-left
        b.set(5, 4, 1, CHAIN); b.set(5, 3, 1, HANGING_LANTERN); // front-right
        b.set(3, 4, 5, CHAIN); b.set(3, 3, 5, HANGING_LANTERN); // back-left
        b.set(5, 4, 5, CHAIN); b.set(5, 3, 5, HANGING_LANTERN); // back-right

        return b.build();
    }

    /**
     * Category H — guard_tower. 5×5×13 (W×L×H) → builder(5,13,5). A slim ROUND
     * corner-sentinel tower (distinct from the square cobble {@link #watchtower}):
     * a stone-brick {@link #circleRing} shaft (centre 2,2, r=2 → octagonal 5×5 with
     * the four corners open), an interior {@link #ladder}/pillar climbing the full
     * shaft and surfacing through a hatch onto a battlement deck, arrow-slit
     * {@code iron_bars} on the cardinal faces, a crenellated parapet crown, a small
     * stone-brick-stair roofed lookout over the deck, a brazier (lit campfire) at the
     * top and a lantern below it, and an oak door at the base.
     *
     * <p>RENDER-SAFETY: every {@code iron_bars} arrow slit sits IN the shaft ring on a
     * cardinal cell, so its two along-ring stone-brick neighbours present sturdy faces
     * → each bar connects and renders (no stub). The deck-railing bars at the parapet
     * each abut a stone-brick-wall merlon or the ring below them.
     *
     * <p>PRINTABILITY: stone bricks / stairs / slabs / walls (all derive from stone
     * bricks), ladder, oak planks/trapdoor, iron bars, lantern, and a lit campfire are
     * all FU-valued or recipe-derived — no unvalued or known-gap blocks.
     */
    private static Blueprint guardTower() {
        Blueprint.Builder b = Blueprint.builder("Guard Tower", 5, 13, 5);
        final int cx = 2, cz = 2, r = 2;
        // ladder backed by the SOUTH ring wall: facing=north attaches to the block at
        // z+1, i.e. the solid ring cell (cx, y, cz+1) directly behind it.
        BlueprintBlockState ladderN = bs("minecraft:ladder[facing=north,waterlogged=false]");

        // --- base: stone-brick disc footing (r=2) at y=0 ---
        disc(b, 0, cx, cz, r, STONE_BRICKS);

        // --- round shaft (y=1..8): octagonal stone-brick ring, weathered flecks ---
        for (int y = 1; y <= 8; y++) {
            circleRing(b, y, cx, cz, r, STONE_BRICKS);
        }
        b.set(0, 4, cz, CRACKED_STONE_BRICKS); // weathering on the west face
        b.set(cx, 6, 0, MOSSY_STONE_BRICKS);   // ...and the north face
        b.set(4, 2, cz, MOSSY_STONE_BRICKS);   // ...and the east face

        // --- door at the base (north cardinal cell, faces south into the shaft) ---
        door2(b, cx, 1, 0, "oak", "N");

        // --- interior ladder up the full shaft, backed by the south ring wall ---
        // climbs y=1..9 and surfaces through the deck hatch carved at (cx, 9, cz).
        pillar(b, cx, cz, 1, 9, ladderN);

        // --- arrow-slit iron bars on the cardinal faces, two courses (y=3, y=6) ---
        // each sits on a ring cell flanked along-ring by sturdy stone bricks → renders.
        for (int y : new int[]{3, 6}) {
            b.set(0, y, cz, IRON_BARS); // west slit
            b.set(4, y, cz, IRON_BARS); // east slit
            b.set(cx, y, 4, IRON_BARS); // south slit (north face carries the door)
        }

        // --- battlement deck (y=9): oak-plank floor over the ring interior, with a
        // ladder hatch at the centre so you climb out onto the deck ---
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 4; z++) {
                double d = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
                if (d > r + 0.5) continue;          // outside the round footprint
                if (x == cx && z == cz) continue;   // ladder hatch
                b.set(x, 9, z, OAK_PLANKS);
            }
        }

        // --- crenellated parapet crown (y=10) flush on the ring, plus iron-bars
        // safety railing in the crenels so the gaps read as a guarded walk ---
        circleRing(b, 10, cx, cz, r, STONE_BRICK_WALL);
        // knock the four cardinal wall cells down to bars (arrow ports / railing); each
        // bar abuts the stone-brick-wall merlons on either side along the ring.
        b.set(0, 10, cz, IRON_BARS);
        b.set(4, 10, cz, IRON_BARS);
        b.set(cx, 10, 0, IRON_BARS);
        b.set(cx, 10, 4, IRON_BARS);

        // --- roofed lookout: four corner posts + a stone-brick-stair roof cap so the
        // deck is sheltered while staying enterable (posts only at the ring cardinals) ---
        pillar(b, 0, cz, 10, 11, STONE_BRICKS);
        pillar(b, 4, cz, 10, 11, STONE_BRICKS);
        pillar(b, cx, 0, 10, 11, STONE_BRICKS);
        pillar(b, cx, 4, 10, 11, STONE_BRICKS);
        // pyramidal stair roof over the 5×5 cap at y=12 (eave stairs face inward), with a
        // solid centre keystone.
        b.set(1, 12, cz, bs("minecraft:stone_brick_stairs[facing=east,half=bottom,shape=straight]"));
        b.set(3, 12, cz, bs("minecraft:stone_brick_stairs[facing=west,half=bottom,shape=straight]"));
        b.set(cx, 12, 1, bs("minecraft:stone_brick_stairs[facing=south,half=bottom,shape=straight]"));
        b.set(cx, 12, 3, bs("minecraft:stone_brick_stairs[facing=north,half=bottom,shape=straight]"));
        b.set(cx, 12, cz, CHISELED_STONE_BRICKS); // roof keystone

        // --- lookout brazier: a lit campfire on the deck, with a backed hanging
        // lantern under the roof for night light (kept clear of the ladder hatch) ---
        b.set(1, 10, 1, CAMPFIRE);                 // corner brazier on the deck (open cell)
        b.set(cx, 11, 1, CHAIN);                   // chain off the north eave stair (cx,12,1)
        b.set(cx, 10, 1, HANGING_LANTERN);         // lantern under the roof, clear of the hatch

        // ground-level wall torch flanking the door (mounted on the inner north ring)
        wallTorch(b, 1, 2, 1, "south");

        return b.build();
    }

    /**
     * Category H — drawbridge. 7×5×6 (W×L×H) → builder(7, 6, 5). A castle
     * drawbridge crossing a moat: stone-brick gate abutments on each side
     * (x=0..1 west, x=5..6 east), a structural {@code water} moat channel between
     * them (x=2..4 at y=0), and a spruce-plank bridge deck (y=1) spanning the moat
     * — shown LOWERED/walkable so you can cross. Spruce-fence handrails edge the
     * deck; iron-bar reinforcement gratings sit IN each abutment inner face;
     * hoist chains drop from gate-top crossbeams onto the deck; backed hanging
     * lanterns light the crossing. Reads as a moat crossing with a timber bridge
     * and lifting chains.
     *
     * <p>RENDER-SAFETY: every {@code iron_bars} cell is flanked along the SAME
     * face (±z) by stone bricks, so each bar connects to a sturdy neighbour and
     * renders (no stub). The grating sits at x=1 (west) / x=5 (east) — the inner
     * abutment faces — between stone-brick uprights at z=1 and z=3.
     *
     * <p>PRINTABILITY: stone bricks/walls/stairs (derive from stone bricks),
     * spruce planks/fence (derive from spruce logs), chain + iron bars (derive
     * from iron), lantern (recipe-derived), and water (structural matter) — all
     * FU-valued or structural; no unvalued or known-gap blocks.
     */
    private static Blueprint drawbridge() {
        Blueprint.Builder b = Blueprint.builder("Drawbridge", 7, 6, 5);
        // x: 0..1 = west abutment, 2..4 = moat span, 5..6 = east abutment.
        // z: 0..4 (5 deep). Crossing runs along x.

        // --- moat bed + structural water channel (y=0, x=2..4 across full depth) ---
        floor(b, 0, 2, 0, 4, 4, WATER);

        // --- stone-brick gate abutments on each bank (solid footing y=0) ---
        floor(b, 0, 0, 0, 1, 4, STONE_BRICKS); // west bank
        floor(b, 0, 5, 0, 6, 4, STONE_BRICKS); // east bank

        // --- abutment gate walls: side uprights (z=0 and z=4 edges) rise y=1..4,
        // the inner abutment face (x=1 west / x=5 east) is left open at deck level
        // for the iron grating; outer columns (x=0 / x=6) anchor the structure ---
        for (int z : new int[]{0, 4}) {
            pillar(b, 0, z, 1, 4, STONE_BRICKS); // west outer
            pillar(b, 1, z, 1, 4, STONE_BRICKS); // west inner upright (grating jamb)
            pillar(b, 5, z, 1, 4, STONE_BRICKS); // east inner upright (grating jamb)
            pillar(b, 6, z, 1, 4, STONE_BRICKS); // east outer
        }
        // weathering flecks on the bank faces
        b.set(0, 2, 2, MOSSY_STONE_BRICKS);
        b.set(6, 3, 2, CRACKED_STONE_BRICKS);

        // --- centre grating jamb on each inner face (x=1/x=5, z=2), y=1..4: a solid
        // stone-brick mullion that (a) splits the gate into two iron-bar slits and
        // (b) gives both slits a sturdy z-neighbour so they render. ---
        pillar(b, 1, 2, 1, 4, STONE_BRICKS);
        pillar(b, 5, 2, 1, 4, STONE_BRICKS);

        // --- gate crossbeams over each inner face (y=4, spanning the depth) so the
        // hoist tackle and lanterns hang from something solid; chiseled keystone. ---
        line(b, 4, 1, 0, 1, 4, STONE_BRICK_WALL); // west gate beam (inner jamb line)
        line(b, 4, 5, 0, 5, 4, STONE_BRICK_WALL); // east gate beam
        b.set(1, 4, 2, CHISELED_STONE_BRICKS);    // decorative keystone, west
        b.set(5, 4, 2, CHISELED_STONE_BRICKS);    // decorative keystone, east

        // --- iron-bar reinforcement gratings IN each inner abutment face (x=1/x=5),
        // y=2..3, at z=1 and z=3. Each bar abuts the centre jamb (z=2) AND an edge
        // upright (z=0/z=4) on its own x-face → two sturdy z-neighbours → it always
        // renders connected (no stub). ---
        for (int y = 2; y <= 3; y++) {
            b.set(1, y, 1, IRON_BARS); // west grating, between z=0 upright and z=2 jamb
            b.set(1, y, 3, IRON_BARS); // west grating, between z=2 jamb and z=4 upright
            b.set(5, y, 1, IRON_BARS); // east grating
            b.set(5, y, 3, IRON_BARS); // east grating
        }

        // --- LOWERED bridge deck: spruce planks spanning the moat (x=2..4) at y=1,
        // flush with the bank tops so it's walkable end-to-end ---
        floor(b, 1, 2, 0, 4, 4, SPRUCE_PLANKS);
        // spruce-fence handrails along both deck edges (z=0 and z=4), one above the deck
        BlueprintBlockState spruceFence = bs("minecraft:spruce_fence");
        line(b, 2, 2, 0, 4, 0, spruceFence);
        line(b, 2, 2, 4, 4, 4, spruceFence);

        // --- timber hoist beam over the deck centre line (spruce log along x, z=2,
        // y=4) carrying the lifting tackle from bank to bank ---
        line(b, 4, 2, 2, 4, 2, bs("minecraft:spruce_log[axis=x]"));
        // lifting chains: drop from the hoist beam at the moat edges (x=2 & x=4) down
        // to the deck — the visible drawbridge tackle that hauls the span up. ---
        pillar(b, 2, 2, 2, 3, CHAIN); // west chain to the deck
        pillar(b, 4, 2, 2, 3, CHAIN); // east chain to the deck

        // --- backed hanging lanterns at each gate centre, hung off the crossbeam
        // keystone (chain link at y=3, lantern at y=2) lighting the crossing ---
        b.set(1, 3, 2, CHAIN); b.set(1, 2, 2, HANGING_LANTERN); // west gate light
        b.set(5, 3, 2, CHAIN); b.set(5, 2, 2, HANGING_LANTERN); // east gate light

        return b.build();
    }

    /**
     * Category H — portcullis_gate. 5×7×3 (W×up×depth) → builder(5, 7, 3). A
     * standalone defensive gate prop: two solid stone-brick jambs flanking a
     * 3-wide passage, a stone-brick-stair arched top, and a FULL iron-bar grate
     * (the lowered portcullis) filling the entire opening — every bar anchored to
     * the jambs and to its neighbours so the whole grate renders connected. Hoist
     * chains rise from the grate top through the arch suggesting the lifting tackle,
     * with chiseled-stone-brick keystone detailing and a pair of lanterns.
     *
     * <p>Layout (x=W 0..4, y=up 0..6, z=depth 0..2):
     * <ul>
     *   <li><b>Jambs.</b> x=0 (west) and x=4 (east) are solid stone-brick pillars
     *       the full depth (z=0..2), y=1..6 — the towers the portcullis anchors to.
     *       Their inner faces (toward x=2) present sturdy stone.</li>
     *   <li><b>Opening.</b> x=1..3, z=0..2 — a 3-wide walk-through passage.</li>
     *   <li><b>Arched top.</b> stone-brick stairs lean inward over the opening at
     *       y=5 (west pair facing east, east pair facing west, apex haunch between),
     *       a flat stone-brick lintel closes the crown at y=6 with a chiseled
     *       keystone centre. Raised one course above the hoist so the chains clear.</li>
     * </ul>
     *
     * <p><b>Portcullis (render-safe full grate).</b> Iron bars fill the front-face
     * opening (z=0) across the full width x=1..3 at y=1..3 — a complete 3×3 grate.
     * Every bar connects: the end columns (x=1, x=3) abut the solid stone jambs
     * (x=0, x=4 present sturdy faces), and the centre column (x=2) bridges to both
     * neighbours. No bar is a lone center-post stub. (Same jamb-to-jamb fix
     * {@link #gatehouse} uses.) The grate at y=1 keeps the passage closed — this is
     * a LOWERED portcullis, the gate shut.
     *
     * <p><b>Hoist.</b> A chain rises on each grate edge (x=1, x=3) from the grate
     * top (y=4) toward the arch, reading as the lifting tackle hauling the
     * portcullis; the y=5 arch voussoirs back the chains. The grate-back of the
     * jambs makes the chains hang cleanly inside the opening.
     *
     * <p>PRINTABILITY: stone bricks / stairs (derive from stone bricks), chiseled
     * stone bricks (recipe-derived), iron bars + chain (derive from iron), and
     * lanterns (recipe-derived) — all FU-valued or recipe-derived, no unvalued or
     * gate-flagged blocks. Vanilla only.
     */
    private static Blueprint portcullisGate() {
        Blueprint.Builder b = Blueprint.builder("Portcullis Gate", 5, 7, 3);
        // x: 0 = west jamb, 1..3 = opening, 4 = east jamb. z: 0..2 (3 deep). y: 0..6.

        // --- footing course (y=0) under the whole prop so the jambs sit on stone ---
        floor(b, 0, 0, 0, 4, 2, STONE_BRICKS);

        // --- solid stone-brick jambs (full depth z=0..2), y=1..6 — the towers the
        // portcullis anchors to; their inner faces are sturdy stone for the grate. ---
        for (int z = 0; z <= 2; z++) {
            pillar(b, 0, z, 1, 6, STONE_BRICKS); // west jamb
            pillar(b, 4, z, 1, 6, STONE_BRICKS); // east jamb
        }
        // weathering flecks on the jamb outer faces
        b.set(0, 2, 1, MOSSY_STONE_BRICKS);
        b.set(4, 3, 1, CRACKED_STONE_BRICKS);
        // chiseled-stone-brick base detailing on the inner jamb faces (front)
        b.set(0, 1, 0, CHISELED_STONE_BRICKS);
        b.set(4, 1, 0, CHISELED_STONE_BRICKS);

        // --- arched top over the opening (x=1..3) on the front/back faces (z=0,z=2):
        // stair voussoirs lean inward at y=5, a flat lintel crowns at y=6. Raised so
        // it sits one course above the hoist chains (y=4) — no collision. ---
        BlueprintBlockState archEast = bs("minecraft:stone_brick_stairs[facing=east,half=bottom,shape=straight]");
        BlueprintBlockState archWest = bs("minecraft:stone_brick_stairs[facing=west,half=bottom,shape=straight]");
        for (int z : new int[]{0, 2}) {
            b.set(1, 5, z, archEast); // west voussoir rises toward the crown
            b.set(3, 5, z, archWest); // east voussoir rises toward the crown
            b.set(2, 5, z, STONE_BRICKS); // crown haunch at the apex
            line(b, 6, 1, z, 3, z, STONE_BRICKS); // flat lintel closing the crown
            b.set(2, 6, z, CHISELED_STONE_BRICKS); // decorative keystone
        }
        // arch crossbeam spanning the depth at the apex (y=6, x=2) ties the faces
        // together; interior keystone
        line(b, 6, 2, 0, 2, 2, STONE_BRICKS);
        b.set(2, 6, 1, CHISELED_STONE_BRICKS);

        // --- PORTCULLIS: full iron-bar grate filling the front opening (z=0),
        // x=1..3 across, y=1..3 up. Every bar connects: x=1/x=3 abut the solid
        // jambs (x=0/x=4 sturdy faces); x=2 bridges to both. No stub. Grate is
        // DOWN (y=1 closed) — the gate is shut. ---
        for (int y = 1; y <= 3; y++) {
            for (int x = 1; x <= 3; x++) {
                b.set(x, y, 0, IRON_BARS);
            }
        }

        // --- hoist tackle: a chain rises on each grate edge from the grate top
        // (y=4) toward the arch, the lifting mechanism hauling the portcullis. The
        // y=5 arch voussoirs sit directly above and back the chains. ---
        b.set(1, 4, 0, CHAIN);
        b.set(3, 4, 0, CHAIN);

        // --- lanterns flanking the passage mouth, hung inside the opening on
        // chains off the y=5/6 arch (chain at y=4, lantern at y=3) ---
        b.set(1, 4, 1, CHAIN); b.set(1, 3, 1, HANGING_LANTERN); // west passage light
        b.set(3, 4, 1, CHAIN); b.set(3, 3, 1, HANGING_LANTERN); // east passage light

        return b.build();
    }

    // =====================================================================
    //  PHASE 0 PILOT BUILDS  (validate the parametric helper library)
    //  docs/blueprint-candidates.md — cherry cottage / enchanting room / pagoda
    // =====================================================================

    /**
     * Cherry Grove Cottage. 7×7 footprint → builder(7, 8, 7). A furnished,
     * enterable cherry-themed cottage built entirely from the parametric
     * {@link #house} helper with the {@link #CHERRY} palette, plus light pink
     * accents (pink_petals — a BushBlock, prints free as structural matter — and
     * a couple of flower pots). The {@link #house} call lays the walkable y=0
     * foundation, the cherry wall ring (y=1..4) with cherry-log corner posts, a
     * hip roof (7×7 is square so {@link #house} picks {@link #hipRoof}), a clear
     * north-wall door (left open via {@link #door2}), and the standard furnish set
     * (bed + crafting table + chest + lantern).
     */
    private static Blueprint cherryGroveCottage() {
        Blueprint.Builder b = Blueprint.builder("Cherry Grove Cottage", 7, 8, 7);
        // Parametric cherry house: walkable interior, hip roof, door, furnish.
        house(b, 0, 0, 6, 6, 4, CHERRY, true);
        // Pink-petal accents on open interior floor cells (y=1). Petals are a
        // BushBlock → structural/free; they sit on the y=0 plank floor. Keep clear
        // of the door cell (3,1), the furnishings, and the table-free middle path.
        b.set(2, 1, 3, bs("minecraft:pink_petals[flower_amount=3,facing=south]"));
        b.set(4, 1, 3, bs("minecraft:pink_petals[flower_amount=4,facing=north]"));
        b.set(3, 1, 5, bs("minecraft:pink_petals[flower_amount=2,facing=west]"));
        // Flower pots flanking the door on the inside (decorative; flower_pot is
        // recipe-derivable from brick → printable).
        b.set(2, 1, 1, bs("minecraft:flower_pot"));
        b.set(4, 1, 1, bs("minecraft:flower_pot"));
        // A potted cherry-pink bloom on the back windowsill for the cherry theme.
        b.set(3, 2, 5, bs("minecraft:potted_pink_tulip"));
        return b.build();
    }

    /**
     * Cottagecore Cottage (Phase 2 §B). 7×7 footprint → builder(7, 8, 7). A
     * storybook perennial cottage: a mossy-cobble base course under warm oak walls
     * with spruce-log corner posts, a steep spruce gable roof (thatch read) over a
     * mossy-cobble ridge, flower-box windows (interior slab sill + potted bloom by
     * each window), a covered front-entry stoop with a slab awning, and
     * hay/barrel/lantern accents around a furnished, ENTERABLE interior.
     *
     * <p>Geometry. Body is x=0..6, z=0..6. The walkable interior floor is oak
     * planks at y=0; walls rise y=1..4 (wallH=4); a {@link #gableRoofX} (ridge
     * along X, slopes facing ±Z) seats at y=4 and peaks at y=7 — hence sizeY=8.
     * The three closed walls use the {@link #walls} ring; the FRONT (north, z=0)
     * wall is laid manually so the centre cell (x=3) can be left OPEN for the
     * {@link #door2} (air-skip: a doorway is made by NOT writing the wall, then
     * writing the 2-block door) and the porch stoop reads cleanly.
     *
     * <p>Render-safety. Every {@link GLASS_PANE} window replaces a single wall
     * cell and stays flanked by solid wall on both horizontal sides (the potted
     * flower boxes sit on the INTERIOR sill, never in a pane's neighbour cell), so
     * each pane has a connecting neighbour and never prints as a stub.
     *
     * <p>All blocks are vanilla FU-valued or structural: mossy_cobblestone (moss
     * is valued; the slab/wall derive), oak/spruce wood, glass panes, hay, barrel,
     * composter, lanterns, flower_pot/potted_* (itemless-structural — NO loose
     * flowers/leaves), and fence porch posts.
     */
    private static Blueprint cottagecoreCottage() {
        Blueprint.Builder b = Blueprint.builder("Cottagecore Cottage", 7, 8, 7);
        final int wallH = 4;
        BlueprintBlockState mossyCobbleSlab = bs("minecraft:mossy_cobblestone_slab[type=bottom]");
        BlueprintBlockState awningStair = bs("minecraft:spruce_stairs[facing=south,half=top,shape=straight]");

        // 1) Walkable plank floor at y=0 (top face = standing surface).
        floor(b, 0, 0, 0, 6, 6, OAK_PLANKS);

        // 2) Walls. Base course (y=1) mossy cobble; oak planks y=2..wallH; spruce-log
        //    corner posts the full height. The three rear/side faces use the ring
        //    helper; the FRONT (z=0) face is laid by hand so x=3 stays open for the door.
        // -- side (west x=0, east x=6) and back (south z=6) faces --
        for (int y = 1; y <= wallH; y++) {
            BlueprintBlockState mat = (y == 1) ? MOSSY_COBBLE : OAK_PLANKS;
            line(b, y, 0, 0, 0, 6, mat); // west face
            line(b, y, 6, 0, 6, 6, mat); // east face
            line(b, y, 0, 6, 6, 6, mat); // south (back) face
            // -- front (north z=0) face, leaving x=3 open as the doorway --
            for (int x = 0; x <= 6; x++) {
                if (x == 3) continue; // doorway gap (air-skip)
                b.set(x, y, 0, mat);
            }
        }
        corners(b, 0, 0, 6, 6, 1, wallH, SPRUCE_LOG_Y);

        // 3) Front door (centred, opening inward = south) + covered entry stoop:
        //    a spruce-stair awning above the door and a mossy-cobble doorstep mat.
        door2(b, 3, 1, 0, "oak", "N");      // occupies (3, y=1..2, 0)
        b.set(3, 3, 0, OAK_PLANKS);          // close the wall above the door head
        b.set(3, wallH, 1, awningStair);     // little eave awning over the entry (interior side, top stair)
        b.set(3, 1, 1, mossyCobbleSlab);     // worn doorstep mat just inside
        // porch fence posts flanking the entry, with hanging lanterns
        pillar(b, 1, 0, 1, wallH - 1, OAK_FENCE);
        pillar(b, 5, 0, 1, wallH - 1, OAK_FENCE);
        b.set(1, wallH, 0, OAK_SLAB_BOTTOM); // post-cap backing for the lantern
        b.set(5, wallH, 0, OAK_SLAB_BOTTOM);

        // 4) Flower-box windows. One glass pane per long wall (west/east) + back
        //    wall, mid-height (y=3), each flanked by solid wall → render-safe. The
        //    flower box (oak-slab sill + potted bloom) sits on the INTERIOR side one
        //    cell in, so it never becomes the pane's horizontal neighbour.
        int wy = 3;
        // west window (x=0, z=3); box on the interior sill at (1, *, 3)
        window2(b, 0, wy, 3, GLASS_PANE, null);
        b.set(1, 1, 3, OAK_SLAB_BOTTOM);
        b.set(1, 2, 3, bs("minecraft:potted_oxeye_daisy"));
        // east window (x=6, z=3); box at (5, *, 3)
        window2(b, 6, wy, 3, GLASS_PANE, null);
        b.set(5, 1, 3, OAK_SLAB_BOTTOM);
        b.set(5, 2, 3, bs("minecraft:potted_cornflower"));
        // back window (x=3, z=6); box at (3, *, 5)
        window2(b, 3, wy, 6, GLASS_PANE, null);
        b.set(3, 1, 5, OAK_SLAB_BOTTOM);
        b.set(3, 2, 5, bs("minecraft:potted_poppy"));

        // 5) Steep spruce gable roof (ridge along X) on a mossy-cobble ridge cap;
        //    closed gable ends so the attic reads solid.
        gableRoofX(b, 0, 0, 6, 6, wallH, "spruce_stairs", mossyCobbleSlab);
        gableEndFill(b, 0, 0, 6, 6, wallH, OAK_PLANKS);

        // 6) Furnished, enterable interior on the standing floor (y=1), kept clear of
        //    the door cell (3,0)/(3,1) walk-in path and the window boxes.
        bed(b, 1, 1, 5, "red", "south");       // head at z=5 (back-left), foot z=4
        b.set(5, 1, 5, CRAFTING_TABLE);         // work corner
        b.set(5, 1, 1, COMPOSTER);              // cottagecore garden accent
        b.set(1, 1, 1, BARREL);                 // pantry barrel by the entry
        b.set(2, 1, 1, HAY);                    // hay accent
        b.set(4, 1, 1, LANTERN);                // floor lantern lighting the entry
        b.set(2, 1, 5, bs("minecraft:flower_pot")); // bedside empty pot

        return b.build();
    }

    /**
     * §B Torii Gate. 7×3 footprint → builder(7, 10, 3). The iconic Japanese
     * shrine gate: a free-standing vermilion-and-dark-wood landmark straddling a
     * gravel approach path. Two vermilion pillars (red_concrete cladding over a
     * stripped-dark-oak-log core, with dark-wood base/capital accents) carry TWO
     * horizontal crossbeams running along X (the gate's width): the lower
     * {@code nuki} tie-beam piercing the pillars, and the upper {@code kasagi}
     * lintel that CURVES UP at both ends via outward-facing stairs (the signature
     * upturned eaves). A small dark-wood {@code gakuzuka} plaque sits centred
     * between the two beams. A stone lantern stands beside the gravel path that
     * runs through the gate. Vermilion + dark-wood palette; vanilla-only,
     * standalone, not enterable. T1 disc.
     *
     * <p>Axes: x = width (0..6), z = depth (0..2), pillars centred on z=1 so the
     * gate reads as a thin plane you walk THROUGH along Z.
     */
    private static Blueprint toriiGate() {
        final int W = 7, H = 10, D = 3;
        Blueprint.Builder b = Blueprint.builder("Torii Gate", W, H, D);
        final int cz = 1;                       // gate plane / path centre line
        final int px0 = 1, px1 = 5;             // the two pillar columns
        BlueprintBlockState vermilion = bs("minecraft:red_concrete");          // lacquered post cladding
        BlueprintBlockState darkLogY = bs("minecraft:stripped_dark_oak_log[axis=y]"); // post core / base / capital
        BlueprintBlockState darkBeamX = bs("minecraft:stripped_dark_oak_log[axis=x]"); // nuki tie-beam (along width)
        BlueprintBlockState darkSlabTop = bs("minecraft:dark_oak_slab[type=top]");      // kasagi ridge cap
        BlueprintBlockState plaque = bs("minecraft:dark_oak_planks");          // central gakuzuka plaque
        BlueprintBlockState gravel = bs("minecraft:gravel");
        BlueprintBlockState stone = bs("minecraft:stone");

        // ── GRAVEL APPROACH PATH (y=0) ─────────────────────────────────────
        // A full-depth gravel strip running through the gate along Z, the width
        // of the gate, so the torii visibly straddles the path.
        floor(b, 0, 0, 0, W - 1, D - 1, gravel);
        // dark-stone footings directly under each pillar (the ishi-zue base stones)
        b.set(px0, 0, cz, stone);
        b.set(px1, 0, cz, stone);

        // ── VERMILION PILLARS (y=1..6) ─────────────────────────────────────
        // Each post: a dark-wood base course (y=1) and capital (y=6) bracketing a
        // tall vermilion-lacquered shaft (y=2..5). Centred on the gate plane (z=1).
        for (int px : new int[]{px0, px1}) {
            b.set(px, 1, cz, darkLogY);            // ishibashira base stone collar
            pillar(b, px, cz, 2, 5, vermilion);    // lacquered shaft
            b.set(px, 6, cz, darkLogY);            // daiwa capital (post head)
        }

        // ── LOWER NUKI TIE-BEAM (y=5) ──────────────────────────────────────
        // A dark stripped-log beam piercing horizontally THROUGH both pillars,
        // tying them together. It runs the full inner span x=1..5 and pokes one
        // cell past each post (x=0 / x=6) as the protruding beam ends (hana).
        line(b, 5, 0, cz, W - 1, cz, darkBeamX);
        // re-assert the vermilion shaft cell the beam passed over so the posts
        // still read as solid vermilion where the beam crosses them.
        b.set(px0, 5, cz, vermilion);
        b.set(px1, 5, cz, vermilion);

        // ── CENTRAL GAKUZUKA PLAQUE (y=6, x=3) ─────────────────────────────
        // The small dark-wood tablet standing between the two beams, centred.
        b.set(3, 6, cz, plaque);

        // ── UPPER KASAGI LINTEL (y=7) WITH UPTURNED ENDS ───────────────────
        // The crowning beam: a vermilion lintel spanning the posts, capped by a
        // dark-wood slab "ridge", and CURVING UP at both ends — stairs facing
        // outward at the tips give the signature upswept torii silhouette.
        line(b, 7, px0, cz, px1, cz, vermilion);        // vermilion lintel body x=1..5
        line(b, 8, px0, cz, px1, cz, darkSlabTop);      // dark-wood ridge cap above it
        // upturned WEST end: a vermilion riser one cell out (x=0) climbing above
        // the lintel, topped by an outward (west) facing stair = the curl-up tip.
        b.set(0, 7, cz, vermilion);
        b.set(0, 8, cz, vermilion);
        b.set(0, 9, cz, bs("minecraft:dark_oak_stairs[facing=west,half=bottom,shape=straight]"));
        // upturned EAST end (mirror, facing east).
        b.set(W - 1, 7, cz, vermilion);
        b.set(W - 1, 8, cz, vermilion);
        b.set(W - 1, 9, cz, bs("minecraft:dark_oak_stairs[facing=east,half=bottom,shape=straight]"));

        // ── STONE LANTERN (tōrō) BESIDE THE PATH ───────────────────────────
        // A small stacked stone lantern at the front-right, off the gate plane so
        // it reads as a path-side approach light: stone plinth → stone-brick body →
        // a lit lantern crowned by a dark-wood slab "roof".
        int lx = 5, lz = 0;                       // front-right corner, off the gate line
        b.set(lx, 0, lz, gravel);                 // ensure it sits on the path apron
        b.set(lx, 1, lz, stone);                  // plinth
        b.set(lx, 2, lz, bs("minecraft:stone_bricks")); // lantern body / fire box
        b.set(lx, 3, lz, LANTERN);                // the light (kasa beneath the cap)
        b.set(lx, 4, lz, darkSlabTop);            // little roof cap

        return b.build();
    }

    /**
     * Japanese Tea House (chashitsu). 9×9 footprint → builder(9, 16, 9). A compact,
     * low, STILTED spruce-and-dark-oak tea house with white-wool shōji wall panels
     * framed by dark-wood posts, a wraparound veranda (engawa) with a spruce-fence
     * railing, a small tatami-look interior, a paper-screen sliding "door" opening
     * onto the veranda, eave lanterns, and an upturned two-tier {@link #pagodaRoof}.
     * Fully ENTERABLE — you step up onto the engawa and in through the screen.
     *
     * <p>Layout (centre {@code cx=cz=4}; everything seated on a stone plinth so the
     * structure reads as raised on stilts):
     * <ul>
     *   <li><b>y=0 plinth</b> — full 9×9 stone-brick base (walkable ground).</li>
     *   <li><b>Stilts</b> — short stripped-spruce-log posts at the veranda corners
     *       and mid-edges, lifting the deck one course (the "stilted" look).</li>
     *   <li><b>y=1 engawa deck</b> — a spruce-plank veranda over the whole 1..7
     *       inner square; the room floor is the same deck.</li>
     *   <li><b>y=2 railing</b> — a spruce-fence balustrade ringing the veranda edge
     *       (x/z 1..7), broken at the north-centre for the step-up entrance.</li>
     *   <li><b>Room shell</b> — a 5×5 room (x/z 2..6), walls y=2..4: white-wool
     *       shōji infill framed by stripped-dark-oak corner posts + mid-wall studs,
     *       with a dark-oak head/sill band. Render-safe shōji "windows" are white
     *       wool flanked by wool, so nothing is a glass-pane stub.</li>
     *   <li><b>Paper-screen door</b> — north wall, a spruce door opening inward onto
     *       the engawa, flanked by white-wool screen panels.</li>
     *   <li><b>Tatami interior</b> — white-carpet "mats" bordered by green carpet,
     *       a low dark-oak slab table, and a hanging soul-lantern (lit + enterable).</li>
     *   <li><b>Roof</b> — a two-tier upturned-eave {@link #pagodaRoof} (baseHalf=3,
     *       so the lip reaches the 0..8 footprint edge WITHOUT clipping), seated
     *       above the wall head; hanging lanterns dangle under its corner flicks.</li>
     * </ul>
     *
     * <p>All blocks are FU-valued vanilla: spruce wood family, stripped dark-oak
     * logs, white/green wool + carpet (wool tag value), dark-oak stairs/slabs,
     * lanterns/chain, spruce fence. No glass panes are used as window stubs.
     */
    private static Blueprint japaneseTeaHouse() {
        final int W = 9, H = 16;
        final int cx = 4, cz = 4;
        Blueprint.Builder b = Blueprint.builder("Japanese Tea House", W, H, W);

        BlueprintBlockState shoji = WHITE_WOOL;                                  // paper-screen infill
        BlueprintBlockState postY = bs("minecraft:stripped_dark_oak_log[axis=y]"); // dark-wood frame posts
        BlueprintBlockState bandX = bs("minecraft:stripped_dark_oak_log[axis=x]"); // head/sill band along X
        BlueprintBlockState bandZ = bs("minecraft:stripped_dark_oak_log[axis=z]"); // head/sill band along Z
        BlueprintBlockState stilt = STRIPPED_SPRUCE_Y;                            // stilt posts
        BlueprintBlockState deck = SPRUCE_PLANKS;                                 // engawa / room floor
        BlueprintBlockState rail = bs("minecraft:spruce_fence");                  // engawa balustrade
        BlueprintBlockState roofSlab = bs("minecraft:dark_oak_slab[type=bottom]"); // tier-interior cap
        BlueprintBlockState tatami = bs("minecraft:white_carpet");               // tatami mat field
        BlueprintBlockState tatamiBorder = bs("minecraft:green_carpet");         // mat border (heri)
        final String ROOF = "dark_oak_stairs";

        // ---- 1) stone-brick plinth (y=0), full 9×9 footprint, walkable ----------
        floor(b, 0, 0, 0, W - 1, W - 1, STONE_BRICKS);

        // ---- 2) stilts: short stripped-spruce posts lifting the engawa one course
        // at the veranda corners and the mid-edge of each side (8 supports).
        int v0 = 1, v1 = 7;                       // engawa (veranda) extent
        for (int[] s : new int[][]{
                {v0, v0}, {v1, v0}, {v0, v1}, {v1, v1}, // corners
                {cx, v0}, {cx, v1}, {v0, cz}, {v1, cz}}) { // mid-edges
            b.set(s[0], 1, s[1], stilt);
        }

        // ---- 3) engawa deck (y=1): spruce planks over the whole 7×7 inner square,
        // the raised veranda + room floor in one plane (you step UP onto it).
        floor(b, 1, v0, v0, v1, v1, deck);

        // ---- 4) veranda railing (y=2): spruce-fence balustrade on the deck edge,
        // laid cell-by-cell so the north-centre cell (cx,v0) is LEFT OPEN as the
        // step-up entrance onto the engawa (can't "set air", so we just skip it).
        for (int x = v0; x <= v1; x++) {
            if (!(x == cx)) b.set(x, 2, v0, rail);   // north edge, gap at centre
            b.set(x, 2, v1, rail);                   // south edge
        }
        for (int z = v0; z <= v1; z++) {
            b.set(v0, 2, z, rail);                   // west edge
            b.set(v1, 2, z, rail);                   // east edge
        }

        // ---- 5) room shell (x/z 2..6), walls y=2..4 -----------------------------
        int r0 = 2, r1 = 6, rTop = 4;
        // white-wool shōji infill on all four faces …
        walls(b, r0, r0, r1, r1, 2, rTop, shoji);
        // … framed by dark-oak corner posts and mid-wall studs (placed AFTER the
        // wool so the frame wins the shared cells).
        corners(b, r0, r0, r1, r1, 2, rTop, postY);
        b.set(cx, 2, r0, postY); b.set(cx, 2, r1, postY);   // mid studs N/S (lower)
        b.set(cx, rTop, r0, postY); b.set(cx, rTop, r1, postY);
        b.set(r0, 2, cz, postY); b.set(r1, 2, cz, postY);   // mid studs W/E (lower)
        b.set(r0, rTop, cz, postY); b.set(r1, rTop, cz, postY);
        // dark-oak head band (y=4 plate) + sill band (y=2 base) for the timber look
        line(b, rTop, r0, r0, r1, r0, bandX); line(b, rTop, r0, r1, r1, r1, bandX);
        line(b, rTop, r0, r0, r0, r1, bandZ); line(b, rTop, r1, r0, r1, r1, bandZ);
        // re-assert corner posts after the band overwrote the corners
        corners(b, r0, r0, r1, r1, rTop, rTop, postY);

        // ---- 6) paper-screen sliding door (north wall, z=r0) --------------------
        // A spruce door opening inward onto the room, flanked by the wool screens
        // (already placed by walls()). The mid-stud at (cx, *, r0) was set above; the
        // door overwrites the lower two courses of that centre cell.
        door2(b, cx, 2, r0, "spruce", "N");

        // ---- 7) tatami-look interior floor (y=1, room interior x/z 3..5) --------
        // white-carpet mat field with a green-carpet border (heri) ring around it.
        floor(b, 1, r0 + 1, r0 + 1, r1 - 1, r1 - 1, tatami);   // 3..5 mats
        fenceRingless(b, 1, r0 + 1, r0 + 1, r1 - 1, r1 - 1, tatamiBorder); // border ring
        // a low tea table (dark-oak slab) dead-centre on the tatami
        b.set(cx, 1, cz, roofSlab);
        // a hanging soul-lantern from the ceiling plate, lit + clear of the table
        b.set(cx, rTop, cz, CHAIN);
        b.set(cx, rTop - 1, cz, SOUL_HANGING_LANTERN);

        // ---- 8) two-tier upturned-eave pagoda roof ------------------------------
        // First eave seats one course above the wall head (y=rTop=4 → ey=5). baseHalf
        // =3, so the bracket lip reaches cx±4 = 0..8 (the footprint edge) without
        // clipping. pagodaRoof lays both tiers + a small finial.
        int ey = rTop + 1;                          // y=5
        int[] halves = {3, 2};
        for (int t = 0; t < halves.length; t++) {
            int h = halves[t];
            int top = pagodaEaveTier(b, cx, cz, ey, h, ROOF, roofSlab);
            // eave lanterns dangling below this tier's four upturned corner lips
            // (one course below the corner finger, at the half+1 diagonal).
            int lx0 = cx - (h + 1), lx1 = cx + (h + 1), lz0 = cz - (h + 1), lz1 = cz + (h + 1);
            b.set(lx0, ey - 2, lz0, HANGING_LANTERN);
            b.set(lx1, ey - 2, lz0, HANGING_LANTERN);
            b.set(lx0, ey - 2, lz1, HANGING_LANTERN);
            b.set(lx1, ey - 2, lz1, HANGING_LANTERN);
            if (t < halves.length - 1) {
                // short drum between tiers, telescoped to the next tier footprint
                int nh = halves[t + 1];
                floor(b, top, cx - nh, cz - nh, cx + nh, cz + nh, roofSlab);
                ey = top + 1;
            } else {
                ey = top;
            }
        }
        // simple finial: a slab base + a lantern crown on the central axis
        b.set(cx, ey, cz, roofSlab);
        b.set(cx, ey + 1, cz, LANTERN);

        return b.build();
    }

    /**
     * Zen Garden (karesansui). 9×9 footprint → builder(9, 5, 9). A tranquil, flat
     * Japanese dry garden — NOT an enterable structure. A raked smooth-sandstone /
     * sand ground concentrically banded with gravel "ripples" around a few stone
     * boulder islands, a stacked stone lantern (tōrō), a small arched wood bridge
     * over a gravel dry-stream, a stepping-stone path, a tsukubai water basin, and a
     * low stone-wall perimeter accented with clipped green-wool topiary "hedges".
     *
     * <p>AXES: x=W (0..8), z=depth (0..8); centre {@code cx=cz=4}. Everything sits in
     * the y=0 ground plane with low accents at y=1..2 (bridge crown / lantern / topiary).
     *
     * <p>PALETTE NOTE — vanilla, FU-valued / structural only:
     * <ul>
     *   <li>Ground: {@code smooth_sandstone} bed + {@code sand} fill, banded with
     *       {@code gravel} ripples (all valued); {@code andesite}/{@code stone}
     *       stepping stones; a {@code water} disc basin (structural).</li>
     *   <li>Boulders: {@code cobblestone} / {@code mossy_cobblestone} /
     *       {@code andesite} mounds (valued).</li>
     *   <li>Lantern: stone plinth → {@code cobblestone_wall} shaft → {@code lantern}
     *       → stone-brick-slab roof.</li>
     *   <li>Bridge: oak-stair arch (rampUp/rampDown) over a gravel dry-stream,
     *       crowned by an oak slab — same verified ramp convention as {@link #koiPond}.</li>
     *   <li>Hedge/greenery: {@code green_wool} topiary mounds (wool tag → valued).
     *       NO bamboo / leaves / lily_pad (UNVALUED).</li>
     * </ul>
     *
     * <p>RENDER-SAFETY: no glass panes / iron bars are used (no window stubs). The
     * perimeter uses {@code stone_brick_wall} (WALLS tag); walls self-connect along
     * the ring and need no special flanking. Stone buttons are floor-mounted "pebbles".
     */
    private static Blueprint zenGarden() {
        final int W = 9, H = 5;
        final int cx = 4, cz = 4;
        Blueprint.Builder b = Blueprint.builder("Zen Garden", W, H, W);
        int x0 = 0, x1 = W - 1, z0 = 0, z1 = W - 1; // 0..8

        BlueprintBlockState bed = bs("minecraft:smooth_sandstone"); // raked sand base
        BlueprintBlockState sand = bs("minecraft:sand");            // raked sand fill
        BlueprintBlockState gravel = bs("minecraft:gravel");        // raked ripples / dry-stream
        BlueprintBlockState andesite = bs("minecraft:andesite");    // boulder / stepping stone
        BlueprintBlockState stone = bs("minecraft:stone");          // stepping stone / lantern plinth
        BlueprintBlockState topiary = GREEN_WOOL;                   // clipped hedge mounds
        BlueprintBlockState pebble =
                bs("minecraft:stone_button[face=floor,facing=north,powered=false]");

        // ── 1) GROUND BED (y=0) ────────────────────────────────────────────
        // Full 9×9 smooth-sandstone bed (the raked "white sand" field), then sand
        // banding so the surface reads as raked sand rather than flat stone.
        floor(b, 0, x0, z0, x1, z1, bed);
        // sand inner field (1..7) over the smooth-sandstone, leaving a 1-cell
        // smooth-sandstone border as the garden's framing apron.
        floor(b, 0, 1, 1, 7, 7, sand);

        // ── 2) RAKED GRAVEL RIPPLES (y=0) ──────────────────────────────────
        // Concentric gravel rings around the centre suggest the raked-sand ripple
        // pattern flowing around the central boulder island. circleRing draws the
        // perimeter only; r=3 then r=2 give two clean ripple bands.
        circleRing(b, 0, cx, cz, 3, gravel);
        circleRing(b, 0, cx, cz, 2, gravel);

        // ── 3) DRY-STREAM (karesansui river) (y=0) ─────────────────────────
        // A gravel "dry river" meandering along z at x=2 (the bridge crosses it).
        line(b, 0, 2, 1, 2, 7, gravel);

        // ── 4) BOULDER ISLANDS ─────────────────────────────────────────────
        // Central island: a 3-tall andesite/cobble mound on the garden centre, the
        // focal "mountain" the ripples flow around.
        b.set(cx, 0, cz, andesite);
        b.set(cx, 1, cz, MOSSY_COBBLE);
        b.set(cx, 2, cz, andesite);                 // crown stone
        b.set(cx - 1, 0, cz, COBBLE);               // skirt stones (low satellites)
        b.set(cx + 1, 0, cz, andesite);
        b.set(cx, 0, cz - 1, MOSSY_COBBLE);
        // North-east smaller island: a 2-tall mossy boulder pair.
        b.set(6, 0, 2, COBBLE);
        b.set(6, 1, 2, MOSSY_COBBLE);
        b.set(7, 0, 2, andesite);
        // South-west single accent boulder.
        b.set(2, 0, 6, MOSSY_COBBLE);
        b.set(2, 1, 6, COBBLE);

        // ── 5) STONE LANTERN (tōrō) ────────────────────────────────────────
        // Stacked path-side lantern at the back-east (off the ripple field): stone
        // plinth → cobble-wall shaft → lit lantern → stone-brick-slab roof cap.
        int lx = 6, lz = 6;
        b.set(lx, 0, lz, stone);                    // plinth foot (overwrites sand)
        b.set(lx, 1, lz, COBBLE_WALL);              // shaft (WALLS tag → render-safe)
        b.set(lx, 2, lz, LANTERN);                  // the light (fire box)
        b.set(lx, 3, lz, STONE_BRICK_SLAB_TOP);     // little roof cap

        // ── 6) ARCHED WOOD BRIDGE (over the dry-stream at x=2) ──────────────
        // A 1-wide oak-stair span crossing the gravel dry-stream along z at x=2.
        // Springs from z=2, crests at z=4 (y=1), descends to z=6 — the verified
        // koiPond ramp convention (rising→facing south, falling→facing north), so
        // every height change is walkable.
        BlueprintBlockState rampUp =
                bs("minecraft:oak_stairs[facing=south,half=bottom,shape=straight]");
        BlueprintBlockState rampDown =
                bs("minecraft:oak_stairs[facing=north,half=bottom,shape=straight]");
        b.set(2, 0, 2, rampUp);                     // approach rise (z=2 → up toward crown)
        b.set(2, 1, 3, rampUp);                     // climb to crown
        b.set(2, 1, 4, OAK_SLAB_TOP);               // crown deck (flat tread over centre)
        b.set(2, 1, 5, rampDown);                   // descend from crown
        b.set(2, 0, 6, rampDown);                   // approach fall (z=6)
        // low oak-fence handrails flanking the crown (one cell each side along x)
        b.set(1, 1, 4, OAK_FENCE);
        b.set(3, 1, 4, OAK_FENCE);

        // ── 7) STEPPING-STONE PATH (tobi-ishi) ─────────────────────────────
        // Flat stone/andesite stepping stones picking a diagonal route across the
        // sand from the front-west toward the central island (already-set cells
        // simply get re-stated as stone — they sit flush in the y=0 plane).
        for (int[] s : new int[][]{{1, 7}, {2, 6}, {3, 5}, {1, 5}, {3, 3}}) {
            b.set(s[0], 0, s[1], stone);
        }
        // a few scattered "pebble" buttons on the sand for texture.
        for (int[] p : new int[][]{{5, 3}, {3, 6}, {5, 5}, {1, 3}}) {
            b.set(p[0], 0, p[1], pebble);
        }

        // ── 8) TSUKUBAI WATER BASIN (front-east corner) ────────────────────
        // A tiny 1-cell sunken water basin ringed by stone-brick wall coping — the
        // ritual washing basin. Water is structural (prints free).
        b.set(7, 0, 6, WATER);
        b.set(7, 1, 6, COBBLE_WALL);                // basin spout post beside it (NE)
        b.set(7, 0, 5, stone);                      // flat approach stone

        // ── 9) PERIMETER HEDGE WALL + TOPIARY ──────────────────────────────
        // A low stone-brick-wall fence ringing the garden edge (the enclosure), with
        // clipped green-wool topiary mounds set at the four corners + edge midpoints
        // as the "hedge" (green_wool = wool tag → FU-valued; NOT leaves/bamboo).
        // The front-centre cell (cx,z0) is left open as the garden entrance.
        for (int x = x0; x <= x1; x++) {
            if (x != cx) b.set(x, 1, z0, STONE_BRICK_WALL); // back/front edge (z=0), gap at entry
            b.set(x, 1, z1, STONE_BRICK_WALL);              // far edge (z=8)
        }
        for (int z = z0 + 1; z <= z1 - 1; z++) {
            b.set(x0, 1, z, STONE_BRICK_WALL);              // west edge
            b.set(x1, 1, z, STONE_BRICK_WALL);              // east edge
        }
        // green-wool topiary mounds capping the four corner posts (waist-high hedge).
        for (int[] c : new int[][]{{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
            b.set(c[0], 2, c[1], topiary);
        }

        return b.build();
    }

    /**
     * Japanese Dojo (training hall). 11×9 footprint → builder(11, 12, 9). A long,
     * open, ENTERABLE training hall in the classic dōjō idiom: white shōji wall
     * panels (white-wool / white-concrete) framed by stripped-dark-oak timber posts,
     * a tatami-look floor (white carpet over spruce planks, green-carpet border
     * "heri"), a raised sensei dais at the far end, fence-and-sign weapon racks along
     * the side walls, hanging lanterns, a sliding shōji entrance, and a sweeping
     * four-sided dark-oak hip roof with a deep upturned eave bracket.
     *
     * <p>AXES: x=W (0..10), z=depth (0..8). The hall body is inset to x 1..9, z 1..7
     * (so the eave + bracket reach the footprint edge without clipping). The walkable
     * floor surface is the y=0 plinth top; walls rise y=1..4; the interior above the
     * tatami (y=2..4) is deliberately left unset so the player can walk in and stand.
     *
     * <p>PALETTE — vanilla, FU-valued / structural only:
     * <ul>
     *   <li>Plinth/foundation: {@code stone_bricks} (derives).</li>
     *   <li>Shōji infill: {@code white_wool} panels banded with {@code white_concrete}
     *       (both valued; SOLID blocks, not panes — so no stub-pane render risk).</li>
     *   <li>Timber frame: {@code stripped_dark_oak_log} posts + head/sill bands.</li>
     *   <li>Tatami floor: {@code spruce_planks} sub-floor, {@code white_carpet} mats,
     *       {@code green_carpet} border (heri) — all derive.</li>
     *   <li>Dais: {@code dark_oak_planks} platform + a {@code dark_oak_slab} step.</li>
     *   <li>Weapon racks: {@code dark_oak_fence} stands + {@code dark_oak_sign} /
     *       {@code oak_sign} plaques (signs are valued; item frames are ENTITIES and
     *       are NOT used).</li>
     *   <li>Roof: {@code dark_oak_stairs}/{@code dark_oak_slab} hip roof + an outward
     *       under-bracket course for the deep upturned-eave look.</li>
     *   <li>Light: {@code lantern} / hanging {@code lantern} on {@code chain}.</li>
     * </ul>
     *
     * <p>RENDER-SAFETY: the shōji is SOLID white_wool/white_concrete (no glass panes
     * or iron bars), so there are no stub-pane cells — the build never authors an
     * unconnected {@code IronBarsBlock}.
     */
    private static Blueprint japaneseDojo() {
        final int W = 11, L = 9, H = 12;
        Blueprint.Builder b = Blueprint.builder("Japanese Dojo", W, H, L);

        // hall body inset (leaves a 1-cell apron so the eave bracket clears the edge)
        final int x0 = 1, x1 = 9, z0 = 1, z1 = 7, wallTop = 4;
        final int cx = (x0 + x1) / 2;            // 5
        final int cz = (z0 + z1) / 2;            // 4

        BlueprintBlockState shoji = WHITE_WOOL;                                  // paper-screen infill
        BlueprintBlockState shojiBand = bs("minecraft:white_concrete");          // concrete accent band
        BlueprintBlockState postY = bs("minecraft:stripped_dark_oak_log[axis=y]"); // dark-wood frame posts
        BlueprintBlockState bandX = bs("minecraft:stripped_dark_oak_log[axis=x]"); // head/sill band along X
        BlueprintBlockState bandZ = bs("minecraft:stripped_dark_oak_log[axis=z]"); // head/sill band along Z
        BlueprintBlockState subFloor = SPRUCE_PLANKS;                            // plank sub-floor
        BlueprintBlockState tatami = bs("minecraft:white_carpet");               // tatami mat field
        BlueprintBlockState tatamiBorder = bs("minecraft:green_carpet");         // mat border (heri)
        BlueprintBlockState daisDeck = DARK_OAK_PLANKS;                          // sensei dais platform
        BlueprintBlockState daisStep = bs("minecraft:dark_oak_slab[type=bottom]"); // step up onto the dais
        BlueprintBlockState rackPost = DARK_OAK_FENCE;                           // weapon-rack stands
        BlueprintBlockState roofSlab = bs("minecraft:dark_oak_slab[type=bottom]"); // ridge / eave cap
        final String ROOF = "dark_oak_stairs";

        // ── 1) PLINTH (y=0) — full 11×9 stone-brick foundation, walkable ────────
        floor(b, 0, 0, 0, W - 1, L - 1, STONE_BRICKS);

        // ── 2) SHŌJI WALL RING (y=1..4) ────────────────────────────────────────
        // White-wool screen infill on all four faces, with a white-concrete band at
        // mid-height (y=2) for the framed-panel look, then stripped-dark-oak timber
        // posts every 2 cells + corner posts (placed AFTER the infill so the frame
        // wins the shared cells). Head/sill bands tie the frame together.
        walls(b, x0, z0, x1, z1, 1, wallTop, shoji);
        // mid-height white-concrete band course (y=2) across the whole wall ring
        line(b, 2, x0, z0, x1, z0, shojiBand); line(b, 2, x0, z1, x1, z1, shojiBand);
        line(b, 2, x0, z0, x0, z1, shojiBand); line(b, 2, x1, z0, x1, z1, shojiBand);
        // vertical timber studs every 2 cells along all four faces
        for (int x = x0; x <= x1; x += 2) {
            pillar(b, x, z0, 1, wallTop, postY);
            pillar(b, x, z1, 1, wallTop, postY);
        }
        for (int z = z0; z <= z1; z += 2) {
            pillar(b, x0, z, 1, wallTop, postY);
            pillar(b, x1, z, 1, wallTop, postY);
        }
        corners(b, x0, z0, x1, z1, 1, wallTop, postY);
        // dark-oak head band (y=wallTop plate) + sill band (y=1 base) for the frame
        line(b, wallTop, x0, z0, x1, z0, bandX); line(b, wallTop, x0, z1, x1, z1, bandX);
        line(b, wallTop, x0, z0, x0, z1, bandZ); line(b, wallTop, x1, z0, x1, z1, bandZ);
        line(b, 1, x0, z0, x1, z0, bandX); line(b, 1, x0, z1, x1, z1, bandX);
        line(b, 1, x0, z0, x0, z1, bandZ); line(b, 1, x1, z0, x1, z1, bandZ);
        // re-assert corner posts after the bands overwrote the corners
        corners(b, x0, z0, x1, z1, 1, wallTop, postY);

        // ── 3) SLIDING SHŌJI ENTRANCE (north wall, z=z0) ───────────────────────
        // A spruce sliding door opening inward onto the hall, centred on the front
        // wall (cx). The door (a 2-block state) overwrites the lower two courses of
        // the centre cell, which is the only break in the ring.
        door2(b, cx, 1, z0, "spruce", "N");

        // ── 4) TATAMI FLOOR (y=1) ──────────────────────────────────────────────
        // Spruce-plank sub-floor over the whole hall interior, then a white-carpet
        // mat field with a green-carpet border (heri) ring — the tatami look.
        floor(b, 1, x0 + 1, z0 + 1, x1 - 1, z1 - 1, subFloor);    // x 2..8, z 2..6
        floor(b, 1, x0 + 1, z0 + 1, x1 - 1, z1 - 1, tatami);      // carpet over the sub-floor
        fenceRingless(b, 1, x0 + 1, z0 + 1, x1 - 1, z1 - 1, tatamiBorder); // border ring (heri)

        // ── 5) RAISED SENSEI DAIS (far/back end, z=z1) ─────────────────────────
        // A low dark-oak platform spanning the back of the hall (one step up), with
        // a dark-oak-slab step in front of it so it's reachable. The dais sits at the
        // south (back) wall; the carpet stops short of it.
        floor(b, 1, x0 + 1, z1 - 1, x1 - 1, z1 - 1, daisDeck);    // back-row platform (z=6)
        for (int x = x0 + 1; x <= x1 - 1; x++) {
            b.set(x, 1, z1 - 2, daisStep);                       // slab step approach (z=5)
        }
        // a small lantern shrine accent on the dais centre
        b.set(cx, 2, z1 - 1, LANTERN);

        // ── 6) WEAPON RACKS (fence stands + sign plaques along the side walls) ──
        // Dark-oak fence stands two cells in from each side wall, with a sign plaque
        // mounted above as the rack head. Signs are FU-valued; item frames (entities)
        // are deliberately NOT used. Placed inside the standing space at y=1..2.
        for (int z : new int[]{z0 + 1, z1 - 2}) {
            // west-side rack
            pillar(b, x0 + 1, z, 1, 2, rackPost);
            b.set(x0 + 1, 3, z, bs("minecraft:dark_oak_sign[rotation=4]")); // plaque facing east into hall
            // east-side rack
            pillar(b, x1 - 1, z, 1, 2, rackPost);
            b.set(x1 - 1, 3, z, bs("minecraft:dark_oak_sign[rotation=12]")); // plaque facing west into hall
        }

        // ── 7) HANGING LANTERNS (interior light) ───────────────────────────────
        // Two lanterns on short chains from the wall-plate course, kept clear of the
        // standing space, lighting the hall.
        b.set(x0 + 1, wallTop, z0 + 1, CHAIN); b.set(x0 + 1, wallTop - 1, z0 + 1, HANGING_LANTERN);
        b.set(x1 - 1, wallTop, z0 + 1, CHAIN); b.set(x1 - 1, wallTop - 1, z0 + 1, HANGING_LANTERN);

        // ── 8) SWEEPING HIP ROOF with a deep upturned eave bracket ─────────────
        // First, an outward-facing under-bracket ring one course below the eave at
        // the footprint edge (x/z 0..10 / 0..8) — the deep-eave soffit that gives the
        // dōjō roof its upturned, overhanging read. Then a four-sided dark-oak hip
        // roof seated on the wall plate (y=wallTop), capped with a dark-oak slab.
        int ey = wallTop + 1;                          // y=5 eave course
        BlueprintBlockState brkN = bs("minecraft:" + ROOF + "[facing=north,half=top,shape=straight]");
        BlueprintBlockState brkS = bs("minecraft:" + ROOF + "[facing=south,half=top,shape=straight]");
        BlueprintBlockState brkW = bs("minecraft:" + ROOF + "[facing=west,half=top,shape=straight]");
        BlueprintBlockState brkE = bs("minecraft:" + ROOF + "[facing=east,half=top,shape=straight]");
        int bx0 = 0, bx1 = W - 1, bz0 = 0, bz1 = L - 1;
        for (int x = bx0; x <= bx1; x++) {
            b.set(x, ey - 1, bz0, brkN);
            b.set(x, ey - 1, bz1, brkS);
        }
        for (int z = bz0; z <= bz1; z++) {
            b.set(bx0, ey - 1, z, brkW);
            b.set(bx1, ey - 1, z, brkE);
        }
        // upturned corner curl tips — one course up at the four bracket corners
        b.set(bx0, ey, bz0, brkN); b.set(bx1, ey, bz0, brkN);
        b.set(bx0, ey, bz1, brkS); b.set(bx1, ey, bz1, brkS);
        // the hip roof proper, seated on the wall plate
        hipRoof(b, x0, z0, x1, z1, ey, ROOF, roofSlab);

        return b.build();
    }

    /**
     * Enchanting Room. 7×7 footprint → builder(7, 5, 7). A functional max-power
     * enchanting setup built on the {@link #roomShell} helper. An enchanting_table
     * sits dead-centre on the floor with EXACTLY 15 bookshelves ringing it at the
     * canonical vanilla power distance: the bookshelves occupy the 5×5 interior
     * perimeter (relative distance 2 from the table) at the same Y as the table,
     * with a 1-block AIR GAP (the distance-1 interior cells x∈{2,4}/z∈{2,4}) left
     * empty between every shelf and the table — that empty cell is what lets the
     * shelf's power reach the table in vanilla. The 5×5 perimeter has 16 cells; we
     * fill 15 and leave the cell in front of the door ({@code (3,1)}) open as the
     * walk-in entrance. Lapis storage (a barrel), wall-backed lanterns, and a
     * carpet round it out.
     */
    private static Blueprint enchantingRoom() {
        Blueprint.Builder b = Blueprint.builder("Enchanting Room", 7, 5, 7);
        // Box: stone-brick walls, polished-andesite floor, stone-brick ceiling.
        // Interior is x,z = 1..5 (5×5), standing height y=1..3, ceiling y=4.
        BlueprintBlockState polishedAndesite = bs("minecraft:polished_andesite");
        roomShell(b, 0, 0, 0, 6, 4, 6, STONE_BRICKS, polishedAndesite, STONE_BRICKS);
        // Walk-in door centred on the north wall (z=0), opening inward.
        door2(b, 3, 1, 0, "dark_oak", "N");
        // Carpet over the interior floor (white_carpet derives from wool → printable).
        floor(b, 1, 1, 1, 5, 5, bs("minecraft:white_carpet"));
        // Enchanting table dead-centre, on top of the carpeted floor.
        b.set(3, 1, 3, bs("minecraft:enchanting_table"));
        // The 15 bookshelves: every cell of the 5×5 interior perimeter (x∈{1,5} or
        // z∈{1,5}) at table level (y=1), EXCEPT (3,1) which is the entrance gap in
        // front of the door. The distance-1 ring (x∈{2,4}/z∈{2,4}) is deliberately
        // left empty — that 1-block air gap is the vanilla power requirement.
        int shelves = 0;
        for (int x = 1; x <= 5; x++) {
            for (int z = 1; z <= 5; z++) {
                boolean onRing = (x == 1 || x == 5 || z == 1 || z == 5);
                if (!onRing) continue;            // skip interior (table + air gap)
                if (x == 3 && z == 1) continue;   // entrance gap aligned with the door
                b.set(x, 1, z, BOOKSHELF);
                shelves++;
            }
        }
        // shelves == 15 by construction (16 perimeter cells − 1 entrance gap).
        // Lapis storage: a barrel set ON TOP of the back-corner shelf (y=2), so it
        // doesn't consume a ring cell (which would drop the count below 15) and
        // doesn't sit in a distance-1 air-gap cell (which would break the power
        // rule). Barrel derives from planks+slabs → printable.
        b.set(5, 2, 5, BARREL);
        // Wall-backed hanging lanterns on chains from the ceiling, in two corners,
        // kept clear of the bookshelf ring's headroom (lanterns hang at y=2).
        b.set(1, 3, 1, CHAIN); b.set(1, 2, 1, HANGING_LANTERN);
        b.set(5, 3, 1, CHAIN); b.set(5, 2, 1, HANGING_LANTERN);
        return b.build();
    }

    /**
     * Japanese Pagoda — landmark rebuild. 11×11 base → builder(11, 33, 11): a
     * tall, slender, five-tier temple pagoda in the iconic vermilion-and-white
     * palette with a dark roof for contrast, deep overhanging eaves with the
     * signature UPTURNED CORNERS on every tier, per-story balcony railings, and
     * a tall sōrin (the gilded spire finial) on top.
     *
     * <p>Authenticity choices (see the project CLAUDE.md pagoda checklist):
     * <ul>
     *   <li><b>Upturned eave corners</b> — every tier is a {@link #pagodaEaveTier}
     *       whose four corners step UP (a finger of top-half stairs curling out),
     *       so each roofline visibly flicks upward at the corners.</li>
     *   <li><b>Deep overhanging eaves</b> — each tier's body is inset two cells
     *       inside its eave ring, and an outward under-bracket course gives the
     *       soffit depth, so the roofs overhang generously.</li>
     *   <li><b>Telescoping tiers</b> — five eaves (halves 4,3,2,1,0), each clearly
     *       smaller, with a short white-walled body story + balcony railing
     *       visible between tiers so they read as distinct floors.</li>
     *   <li><b>Sōrin finial</b> — a tall dark-fence mast threaded with gold-block
     *       rings, an end-rod above, topped by a lightning rod: a prominent spire.</li>
     *   <li><b>Palette</b> — stripped-dark-oak log frame, white-concrete infill
     *       walls, red-concrete base/accent bands and balcony railings, dark-oak
     *       roof stairs/slabs; trapdoor-lattice windows; eave lanterns.</li>
     * </ul>
     *
     * <p>Footprint: the ground-floor body is inset to x/z 1..9 (half-extent 4 from
     * centre 5) so the bottom tier's eave + lip reach the x/z 0..10 footprint edge
     * without clipping. Each higher story telescopes inward by one cell per tier.
     */
    private static Blueprint japanesePagoda() {
        final int W = 11, H = 33;
        final int cx = 5, cz = 5;
        Blueprint.Builder b = Blueprint.builder("Japanese Pagoda", W, H, W);
        BlueprintBlockState redConcrete = bs("minecraft:red_concrete");
        BlueprintBlockState whiteConcrete = bs("minecraft:white_concrete");
        BlueprintBlockState postY = bs("minecraft:stripped_dark_oak_log[axis=y]");
        BlueprintBlockState beamX = bs("minecraft:stripped_dark_oak_log[axis=x]");
        BlueprintBlockState redFence = bs("minecraft:dark_oak_fence"); // balcony railings (dark wood)
        BlueprintBlockState roofSlab = bs("minecraft:dark_oak_slab[type=bottom]");
        BlueprintBlockState goldBlock = bs("minecraft:gold_block");
        final String ROOF = "dark_oak_stairs";

        // ----- 1) stone-brick plinth (y=0), walkable, full 11×11 footprint -----
        floor(b, 0, 0, 0, W - 1, W - 1, STONE_BRICKS);

        // ----- 2) ground story (enterable temple body), inset to x/z 1..9 -------
        // half-extent 4 → walls at 1..9; tall first story y=1..5.
        int g0 = 1, g1 = 9, gTop = 5;
        walls(b, g0, g0, g1, g1, 1, gTop, whiteConcrete);     // white lacquered infill
        corners(b, g0, g0, g1, g1, 1, gTop, postY);           // dark-oak corner posts
        // mid-wall posts on each face for the timber-frame look
        for (int s : new int[]{3, 5, 7}) {
            pillar(b, s, g0, 1, gTop, postY);
            pillar(b, s, g1, 1, gTop, postY);
            pillar(b, g0, s, 1, gTop, postY);
            pillar(b, g1, s, 1, gTop, postY);
        }
        // red lacquer base course (y=1) + head accent band (y=gTop)
        fenceRingless(b, 1, g0, g0, g1, g1, redConcrete);
        fenceRingless(b, gTop, g0, g0, g1, g1, redConcrete);
        // doorway (north wall, centred, opens inward) + trapdoor-lattice windows
        door2(b, cx, 1, g0, "dark_oak", "N");
        latticeWindow(b, g0, 3, cx, "W"); // west wall
        latticeWindow(b, g1, 3, cx, "E"); // east wall
        latticeWindow(b, cx, 3, g1, "S"); // south (back) wall
        // interior: spruce floor, ceiling tie-beam, hanging soul-lantern (lit + enterable)
        floor(b, 0, g0 + 1, g0 + 1, g1 - 1, g1 - 1, SPRUCE_PLANKS);
        line(b, gTop + 1, g0 + 1, cz, g1 - 1, cz, beamX);
        b.set(cx, gTop, cz, CHAIN);
        b.set(cx, gTop - 1, cz, SOUL_HANGING_LANTERN);

        // ----- 3) telescoping roof tiers (5), each with upturned corners --------
        // Tier eave halves: 4,3,2,1,0. Bottom eave seats at the ground-wall head.
        int ey = gTop + 1; // first eave course at y=6 (above the y=5 wall head)
        int[] halves = {4, 3, 2, 1, 0};
        for (int t = 0; t < halves.length; t++) {
            int h = halves[t];
            int top = pagodaEaveTier(b, cx, cz, ey, h, ROOF, roofSlab);
            // eave lanterns dangling below this tier's four upturned corners
            // (ey-2, hanging off the bracket-lip corner at the half+1 diagonal).
            int lx0 = cx - (h + 1), lx1 = cx + (h + 1), lz0 = cz - (h + 1), lz1 = cz + (h + 1);
            if (h >= 1) {
                b.set(lx0, ey - 2, lz0, HANGING_LANTERN);
                b.set(lx1, ey - 2, lz0, HANGING_LANTERN);
                b.set(lx0, ey - 2, lz1, HANGING_LANTERN);
                b.set(lx1, ey - 2, lz1, HANGING_LANTERN);
            }
            if (t < halves.length - 1) {
                // short body story above this tier, telescoped to the NEXT tier's
                // footprint, with a red balcony railing ringing it — distinct floors.
                int nh = halves[t + 1];
                int bx0 = cx - nh, bx1 = cx + nh, bz0 = cz - nh, bz1 = cz + nh;
                int storyY = top + 1;               // wall course sits above the corner tips
                // white-walled drum (the visible story); dark-oak corner posts placed
                // LAST so they win the corners over the red accent ring.
                walls(b, bx0, bz0, bx1, bz1, storyY, storyY, whiteConcrete);
                fenceRingless(b, storyY, bx0, bz0, bx1, bz1, redConcrete); // red lacquer band
                corners(b, bx0, bz0, bx1, bz1, storyY, storyY, postY);
                fenceRing(b, storyY + 1, bx0, bz0, bx1, bz1, redFence); // balcony railing above
                // next eave seats one course ABOVE the railing so it overhangs the
                // balcony without overwriting it (eave projects out past the railing).
                ey = storyY + 2;
            } else {
                ey = top; // topmost tier: finial seats here
            }
        }

        // ----- 4) sōrin finial (the prominent gilded spire) --------------------
        // A dark-fence mast threaded with gold-block rings, an end rod, capped by a
        // lightning rod — tall and obviously the apex of the pagoda.
        int fy = ey;                          // base just above the top tier
        b.set(cx, fy, cz, roofSlab);          // finial base disc
        b.set(cx, fy + 1, cz, goldBlock);     // lower ring (dew basin / fukubachi)
        b.set(cx, fy + 2, cz, redFence);      // mast
        b.set(cx, fy + 3, cz, goldBlock);     // ring
        b.set(cx, fy + 4, cz, redFence);      // mast
        b.set(cx, fy + 5, cz, goldBlock);     // ring
        b.set(cx, fy + 6, cz, END_ROD);       // upper jewel (hōju) stem
        b.set(cx, fy + 7, cz, LIGHTNING_ROD); // crowning spire tip
        return b.build();
    }

    /**
     * A single perimeter ring of {@code mat} at height {@code y} over
     * [x0..x1]×[z0..z1] (like {@link #fenceRing} but for any block) — used for the
     * pagoda's red lacquer base/accent courses without the fence semantics.
     */
    private static void fenceRingless(Blueprint.Builder b, int y, int x0, int z0, int x1, int z1,
                                      BlueprintBlockState mat) {
        line(b, y, x0, z0, x1, z0, mat);
        line(b, y, x0, z1, x1, z1, mat);
        line(b, y, x0, z0, x0, z1, mat);
        line(b, y, x1, z0, x1, z1, mat);
    }

    /**
     * A traditional shōji-style lattice window. The glass pane sits in the wall
     * cell {@code (x,y,z)} and the two cells flanking it ALONG the wall are left as
     * the solid wall block, so the pane connects to that wall on both in-line ends
     * and therefore actually renders (a glass pane is an {@link net.minecraft.world.level.block.IronBarsBlock}
     * — it draws its center post + an arm toward every horizontal neighbour it can
     * connect to, and renders as an almost-invisible stub if it has ZERO
     * connections). The decorative dark-oak "grille" trapdoors are placed on the
     * OUTER face — one cell OUT from the wall, perpendicular to it — so they read
     * as an exterior lattice over the glass WITHOUT replacing the pane's only
     * connectable neighbours.
     *
     * <p>{@code wallFace} is N/S/E/W and names the exterior face the window sits on:
     * it determines which axis the pane runs along (so the flanking wall cells line
     * up with the pane) and which direction "out" is for the grille + its facing.
     */
    private static void latticeWindow(Blueprint.Builder b, int x, int y, int z, String wallFace) {
        b.set(x, y, z, GLASS_PANE);
        String face = wallFace.toUpperCase();
        // alongX: the wall (and thus the pane's connectable run) runs along X, so
        // the pane's in-line neighbours are at x±1 and the wall faces N/S (outer
        // direction is ∓z). Otherwise the wall runs along Z (E/W faces, outer ∓x).
        boolean alongX = face.equals("N") || face.equals("S")
                || face.equals("NORTH") || face.equals("SOUTH");
        // Outward normal: N → -z, S → +z, W → -x, E → +x. The grille trapdoors
        // hang on this outer face, facing back toward the wall (cosmetic lattice).
        int ox = 0, oz = 0;
        String tdFacing;
        switch (face) {
            case "N": case "NORTH": oz = -1; tdFacing = "south"; break;
            case "S": case "SOUTH": oz =  1; tdFacing = "north"; break;
            case "W": case "WEST":  ox = -1; tdFacing = "east";  break;
            default: /* E / EAST */ ox =  1; tdFacing = "west";  break;
        }
        BlueprintBlockState trap =
                bs("minecraft:dark_oak_trapdoor[facing=" + tdFacing + ",half=top,open=false,powered=false,waterlogged=false]");
        // Grille on the outer face: one cell OUT from the wall, on the two cells
        // that flank the pane along the wall axis. This leaves the in-wall cells at
        // x±1 (alongX) / z±1 (alongZ) as solid wall so the pane keeps both of its
        // horizontal connections — and the lattice still reads over the window.
        if (alongX) {
            b.set(x - 1, y, z + oz, trap);
            b.set(x + 1, y, z + oz, trap);
        } else {
            b.set(x + ox, y, z - 1, trap);
            b.set(x + ox, y, z + 1, trap);
        }
    }

    // =====================================================================
    //  PHASE 1 PILOT BUILDS — group 2  (validate remaining bank archetypes)
    //  docs/blueprint-candidates.md — savanna villa / fountain / battlement
    // =====================================================================

    /**
     * Savanna Acacia Villa. 9×9 footprint → builder(9, 10, 9). A low-slung,
     * furnished, enterable acacia house on a cut-sandstone base — driven by the
     * parametric {@link #house} helper with the {@link #SAVANNA_ACACIA} palette
     * (validates that {@link #house} generalises across palettes: the wall ring,
     * acacia-log corner posts, hip roof, inward-opening door and furnish set all
     * come straight from the palette, identical geometry to the cherry cottage in
     * a wholly different material family).
     *
     * <p>The "cut-sandstone base" the spec calls for is layered on AFTER the
     * {@link #house} call: the y=0 plank floor is overwritten with cut sandstone
     * (so the foundation/standing surface reads as masonry, still walkable), a
     * 1-block cut-sandstone apron rings the footprint at y=0, and the bottom wall
     * course (y=1) gets a smooth-sandstone wainscot so the house sits visibly on a
     * stone plinth. 9×9 is square (|W−L|≤1) so {@link #house} seats a
     * {@link #hipRoof}; over a 9-wide span it rises 4 courses (peak ≈ y=8), well
     * inside the H=10 budget. A short acacia-fence pergola accent runs off the
     * front-left corner (the optional porch touch). All blocks are FU-valued
     * vanilla (acacia wood family, cut/smooth sandstone, glass panes).
     */
    private static Blueprint savannaAcaciaVilla() {
        Blueprint.Builder b = Blueprint.builder("Savanna Acacia Villa", 9, 10, 9);
        BlueprintBlockState cutSandstone = bs("minecraft:cut_sandstone");
        BlueprintBlockState smoothSandstone = bs("minecraft:smooth_sandstone");
        BlueprintBlockState acaciaLogY = bs("minecraft:acacia_log[axis=y]");
        BlueprintBlockState acaciaFence = bs("minecraft:acacia_fence");
        BlueprintBlockState acaciaSlabBottom = bs("minecraft:acacia_slab[type=bottom]");
        // Shutter leaves, hung on the wall face above/below each side window so they
        // frame the glass WITHOUT replacing the pane's in-line wall neighbours (a 1-wide
        // pane connects only to the wall cells beside it; trapdoors there would leave it
        // a non-rendering stub). West/east faces sit at the footprint edge (x=0/x=8) so
        // there is no outer cell to hang a beside-shutter on — vertical leaves it is.
        BlueprintBlockState acaciaShutterTop =    // upper leaf (reads as a shutter head)
                bs("minecraft:acacia_trapdoor[facing=north,half=top,open=false,powered=false,waterlogged=false]");
        BlueprintBlockState acaciaShutterBottom = // lower leaf (reads as a sill)
                bs("minecraft:acacia_trapdoor[facing=north,half=bottom,open=false,powered=false,waterlogged=false]");
        // 1) parametric acacia house: walkable y=0 floor, acacia wall ring y=1..4
        //    with acacia-log corners, hip roof, inward door, furnish set.
        house(b, 0, 0, 8, 8, 4, SAVANNA_ACACIA, true);
        // 2) cut-sandstone base: overwrite the y=0 plank floor with masonry so the
        //    foundation/standing surface reads as a cut-sandstone plinth (walkable).
        floor(b, 0, 0, 0, 8, 8, cutSandstone);
        // 3) smooth-sandstone wainscot on the bottom wall course (y=1) so the house
        //    sits visibly on a stone base. Perimeter only; skip the door cell
        //    (4,1,0) so the doorway stays open, then re-seat the acacia corner posts.
        for (int x = 0; x <= 8; x++) {
            if (x != 4) b.set(x, 1, 0, smoothSandstone); // north face (skip door)
            b.set(x, 1, 8, smoothSandstone);             // south face
        }
        for (int z = 1; z <= 7; z++) {
            b.set(0, 1, z, smoothSandstone);             // west face
            b.set(8, 1, z, smoothSandstone);             // east face
        }
        b.set(0, 1, 0, acaciaLogY); // corner posts stay timber over the wainscot
        b.set(8, 1, 0, acaciaLogY);
        b.set(0, 1, 8, acaciaLogY);
        b.set(8, 1, 8, acaciaLogY);
        // 4) acacia accents that validate the wider acacia palette on this build:
        //    open shade-beam slats spanning the interior just under the wall plate
        //    (a simple acacia-fence + slab pergola motif, kept ON the footprint so it
        //    fits the 9×9 budget) and acacia-trapdoor shutters framing the side windows.
        //    house() places the side windows at (0,2,4)/(8,2,4) and back at (4,2,8).
        //    Shutters are stacked above/below each pane (not beside it) so the glass keeps
        //    its z±1 wall neighbours and renders instead of becoming an invisible stub.
        b.set(0, 3, 4, acaciaShutterTop); b.set(0, 1, 4, acaciaShutterBottom); // west-window shutters
        b.set(8, 3, 4, acaciaShutterTop); b.set(8, 1, 4, acaciaShutterBottom); // east-window shutters
        // interior pergola/rafter accent: an acacia-slab beam under the plate on the
        // back half, carried by two short acacia-fence posts (open, walk-under).
        pillar(b, 2, 1, 1, 3, acaciaFence);
        pillar(b, 6, 1, 1, 3, acaciaFence);
        line(b, 4, 2, 1, 6, 1, acaciaSlabBottom);        // shade beam between the posts
        return b.build();
    }

    /**
     * Desert Sandstone House. 7×7 footprint → builder(7, 7, 7). A flat-roofed
     * desert home in the {@link #DESERT_SANDSTONE} palette: cut-sandstone walls on
     * a smooth-sandstone plinth, chiseled-sandstone corner pilasters, an acacia
     * door + acacia-trapdoor window shutters, a terracotta accent course under the
     * eave, and a low cut-sandstone parapet ringing the flat sandstone-slab roof —
     * the silhouette that reads "desert" instead of the palette's default pitched
     * roof. Enterable: walkable y=0 floor, open interior, inward-opening door.
     *
     * <p>Built from primitives (not {@link #house}) so the roof can be FLAT: the
     * {@code house} helper auto-seats a hip roof on a near-square footprint, which
     * is wrong for this archetype. Layout (Y), footprint x=0..6, z=0..6:
     * <ul>
     *   <li><b>y=0</b> — walkable smooth-sandstone foundation slab (full 7×7).</li>
     *   <li><b>y=1..4</b> — cut-sandstone wall ring with chiseled-sandstone corner
     *       pilasters (equal height, no nub); the bottom course (y=1) is
     *       smooth-sandstone wainscot so the house sits on a visible stone base.</li>
     *   <li><b>y=2</b> — glass-pane windows on all four walls, each flanked
     *       horizontally by wall cells (render-safe, never a stub) and dressed with
     *       acacia-trapdoor shutters stacked ABOVE/BELOW the pane (never beside it,
     *       so the pane keeps its wall neighbours).</li>
     *   <li><b>y=4</b> — a terracotta accent band on all four walls, the desert
     *       colour pop just under the eave.</li>
     *   <li><b>y=5</b> — flat roof: sandstone top-slabs over the full footprint, so
     *       the interior is fully closed (no sky holes).</li>
     *   <li><b>y=6</b> — a low parapet: a sandstone-wall crenellation ring on the
     *       roof edge, giving the flat-roof desert/fort read.</li>
     * </ul>
     */
    private static Blueprint desertSandstoneHouse() {
        Blueprint.Builder b = Blueprint.builder("Desert Sandstone House", 7, 7, 7);
        Palette p = DESERT_SANDSTONE;
        BlueprintBlockState cutSandstone = p.wall;                 // cut_sandstone
        BlueprintBlockState smoothSandstone = p.accentWall;        // smooth_sandstone
        BlueprintBlockState chiseledSandstone = p.logPillarY;      // chiseled_sandstone (corner pilasters)
        BlueprintBlockState sandstoneSlabTop = p.slabTop;          // sandstone_slab[type=top] (flat roof)
        BlueprintBlockState sandstoneWall = bs("minecraft:sandstone_wall"); // parapet crenellation
        BlueprintBlockState terracotta = bs("minecraft:terracotta");        // desert accent band
        // Acacia-trapdoor shutters: hung on the wall face ABOVE and BELOW each pane
        // (not beside it) so the 1-wide pane keeps its in-line wall neighbours and
        // renders instead of becoming an invisible stub — same idiom as the villa.
        BlueprintBlockState acaciaShutterTop =
                bs("minecraft:acacia_trapdoor[facing=north,half=top,open=false,powered=false,waterlogged=false]");
        BlueprintBlockState acaciaShutterBottom =
                bs("minecraft:acacia_trapdoor[facing=north,half=bottom,open=false,powered=false,waterlogged=false]");

        // 1) walkable foundation floor at y=0 (smooth-sandstone plinth)
        floor(b, 0, 0, 0, 6, 6, smoothSandstone);
        // 2) cut-sandstone wall ring y=1..4 with chiseled-sandstone corner pilasters
        walls(b, 0, 0, 6, 6, 1, 4, cutSandstone);
        corners(b, 0, 0, 6, 6, 1, 4, chiseledSandstone);
        // 2a) smooth-sandstone wainscot on the bottom course (y=1), perimeter only,
        //     skipping the door cell (3,0) so the doorway stays open; re-seat the
        //     chiseled corner pilasters over the wainscot.
        for (int x = 0; x <= 6; x++) {
            if (x != 3) b.set(x, 1, 0, smoothSandstone); // north face (skip door)
            b.set(x, 1, 6, smoothSandstone);             // south face
        }
        for (int z = 1; z <= 5; z++) {
            b.set(0, 1, z, smoothSandstone);             // west face
            b.set(6, 1, z, smoothSandstone);             // east face
        }
        b.set(0, 1, 0, chiseledSandstone);
        b.set(6, 1, 0, chiseledSandstone);
        b.set(0, 1, 6, chiseledSandstone);
        b.set(6, 1, 6, chiseledSandstone);
        // 3) door centred on the north wall (z=0), opening inward (faces south).
        //    Spec calls for an acacia door for the desert accent — pass "acacia"
        //    explicitly rather than the palette's default door wood (jungle).
        door2(b, 3, 1, 0, "acacia", "N");
        // 4) glass-pane windows at y=2, one centred-ish on each wall. Each pane sits
        //    between two wall cells along its wall line (render-safe). North-wall panes
        //    flank the door at x=1 and x=5.
        window2(b, 1, 2, 0, p.windowPane, null); // north wall, west of door
        window2(b, 5, 2, 0, p.windowPane, null); // north wall, east of door
        window2(b, 0, 2, 3, p.windowPane, null); // west wall, centre
        window2(b, 6, 2, 3, p.windowPane, null); // east wall, centre
        window2(b, 3, 2, 6, p.windowPane, null); // south (back) wall, centre
        // 4a) acacia-trapdoor shutters above/below each pane (not beside it)
        int[][] panes = {{1, 0}, {5, 0}, {0, 3}, {6, 3}, {3, 6}};
        for (int[] pn : panes) {
            b.set(pn[0], 3, pn[1], acaciaShutterTop);    // upper leaf
            b.set(pn[0], 1, pn[1], acaciaShutterBottom); // lower leaf
        }
        // 5) terracotta accent band on the top wall course (y=4), perimeter only,
        //    keeping the chiseled corner pilasters exposed.
        for (int x = 1; x <= 5; x++) {
            b.set(x, 4, 0, terracotta); // north
            b.set(x, 4, 6, terracotta); // south
        }
        for (int z = 1; z <= 5; z++) {
            b.set(0, 4, z, terracotta); // west
            b.set(6, 4, z, terracotta); // east
        }
        // 6) flat roof: sandstone top-slabs over the full footprint (interior closed)
        flatRoof(b, 5, 0, 0, 6, 6, sandstoneSlabTop);
        // 7) low parapet: a sandstone-wall crenellation ring on the roof edge
        crenellate(b, 6, 0, 0, 6, 6, sandstoneWall);
        // 8) minimal interior furnishings on the walkable y=1 floor
        bed(b, 1, 1, 5, p.bedColor, "south"); // yellow bed, head at z=5
        b.set(5, 1, 5, CHEST);
        b.set(1, 1, 1, CRAFTING_TABLE);
        b.set(5, 1, 1, p.lightBlock);         // lantern in the front corner
        return b.build();
    }

    /**
     * Tiered Fountain. 5×5 footprint → builder(5, 5, 5). A 2-tier stone-brick
     * fountain that validates the radial/decorative archetype: the round basins
     * are laid with the {@link #disc}/{@link #circleRing} helpers, the rims use
     * stone-brick slabs/stairs, and the pools are water source blocks (water is
     * itemless structural matter → prints free).
     *
     * <p>Layout (Y), centre (cx,cz)=(2,2):
     * <ul>
     *   <li><b>y=0</b> — lower pool: a stone-brick {@link #disc} (r=2) fills the
     *       5×5 base, then the inner {@link #disc} (r=1) is replaced with water so
     *       the wide bottom basin holds a ring of water inside a stone rim.</li>
     *   <li><b>y=1</b> — lower rim: a stone-brick {@link #circleRing} (r=2) walls
     *       the lower pool so the water is contained; the corners fall outside the
     *       ring (radial), giving the round read.</li>
     *   <li><b>y=2</b> — pedestal: a single stone-brick column at the centre lifts
     *       the upper basin (the cascade source height).</li>
     *   <li><b>y=3</b> — upper basin floor: a stone-brick {@link #disc} (r=1) — a
     *       3×3 plus-shaped pad — with a water source at its centre that cascades
     *       down the pedestal into the lower pool (reads as 2 tiers).</li>
     *   <li><b>y=4</b> — a low stone-brick-wall lip ringing the upper basin edge so
     *       the top tier is a visible raised bowl.</li>
     * </ul>
     */
    private static Blueprint tieredFountain() {
        Blueprint.Builder b = Blueprint.builder("Tiered Fountain", 5, 5, 5);
        int cx = 2, cz = 2;
        // y=0 lower pool: full stone-brick disc, inner disc carved to water
        disc(b, 0, cx, cz, 2, STONE_BRICKS);
        disc(b, 0, cx, cz, 1, WATER);                 // inner water ring + centre
        // y=1 lower rim: stone-brick ring contains the lower pool's water
        circleRing(b, 1, cx, cz, 2, STONE_BRICKS);
        // y=2 pedestal: central stone-brick column lifting the upper basin
        b.set(cx, 2, cz, STONE_BRICKS);
        // y=3 upper basin floor: small stone-brick disc (3×3 plus pad) + water source
        disc(b, 3, cx, cz, 1, STONE_BRICKS);
        b.set(cx, 3, cz, WATER);                       // cascade source → falls to lower pool
        // y=4 upper basin lip: a stone-brick-wall ring around the top tier edge
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            b.set(cx + d[0], 4, cz + d[1], STONE_BRICK_WALL);
        }
        return b.build();
    }

    /**
     * Wall Battlement Segment. 9×3 footprint → builder(9, 5, 3). A TILEABLE
     * curtain-wall run validating the tileable-segment archetype + {@link
     * #crenellate}: a solid stone-brick wall body with a walkway on top and merlons
     * along both long edges. Designed so copies placed end-to-end (offset by 9 in
     * x) tile flush — the wall body spans the full x=0..8 width at a CONSTANT height
     * and the merlon rhythm is continuous, so the seam between two segments reads as
     * one unbroken battlement.
     *
     * <p>Layout (Y), footprint x=0..8 (W), z=0..2 (depth, the wall thickness):
     * <ul>
     *   <li><b>y=0..2</b> — solid stone-brick wall body, full width, 3 deep, 3 tall
     *       (a {@link #solid} block) — flat top so the walkway is level and ends
     *       align at any tiling offset.</li>
     *   <li><b>y=3</b> — walkway deck: a stone-brick-slab (top) surface over the
     *       full footprint, the patrol path.</li>
     *   <li><b>y=4</b> — {@link #crenellate} merlons flush on both long edges (z=0
     *       and z=2), the 2-up/1-gap rhythm, leaving the z=1 walkway centre open to
     *       stand on. A stone-brick-wall ladder-free run; ends align so segments
     *       chain.</li>
     * </ul>
     *
     * <p>Constant height (peak y=4) and a body that fills the whole 0..8 x-span are
     * what make it tile flush — there is no taper, gable, or end cap to break the
     * seam when a second copy is printed at x=9.
     */
    private static Blueprint wallBattlementSegment() {
        Blueprint.Builder b = Blueprint.builder("Wall Battlement Segment", 9, 5, 3);
        // 1) solid wall body: full width x=0..8, depth z=0..2, height y=0..2 (flat top)
        solid(b, 0, 0, 0, 8, 2, 2, STONE_BRICKS);
        // a touch of texture so it doesn't read as a flat slab — cracked/mossy flecks
        b.set(2, 1, 0, CRACKED_STONE_BRICKS);
        b.set(6, 2, 2, MOSSY_STONE_BRICKS);
        // 2) walkway deck (y=3): stone-brick top-slab patrol path over the body
        floor(b, 3, 0, 0, 8, 2, STONE_BRICK_SLAB_TOP);
        // 3) merlons (y=4) flush on the walkway: crenellate the full footprint so the
        //    2-up/1-gap rhythm runs both long edges and tiles continuously at x=9.
        crenellate(b, 4, 0, 0, 8, 2, STONE_BRICK_WALL);
        return b.build();
    }

    /**
     * Desert Pyramid Shrine. 9×9 footprint → builder(9, 8, 9). A stepped
     * sandstone pyramid that reads unmistakably as a vanilla desert temple: a
     * wide cut-sandstone base ringing a small enterable chamber, then three
     * receding solid steps converging to a chiseled-sandstone finial, with the
     * signature orange/blue terracotta inlay motif on the base and step faces.
     *
     * <p>Vanilla blocks only. Cut/smooth/chiseled sandstone + sandstone slabs all
     * derive from base sandstone (FU-valued); dyed terracotta normalises to the
     * base terracotta cost — same palette family the desert_sandstone_house uses,
     * so every block clears the printability gate.
     *
     * <p>Layout (Y), centre (cx,cz)=(4,4):
     * <ul>
     *   <li><b>y=0</b> — smooth-sandstone foundation apron filling the full 9×9.</li>
     *   <li><b>y=1..2</b> — base tier: a cut-sandstone wall ring on the 9×9
     *       footprint enclosing a hollow 7×7 chamber. A door opens inward on the
     *       north wall (z=0); the interior is left unset (air-skip rule) so the
     *       shrine is enterable. Chiseled-sandstone corner pilasters; a blue
     *       terracotta accent course flanks the door on the front face.</li>
     *   <li><b>y=3</b> — chamber ceiling / step-0 deck: a solid sandstone-slab
     *       (top) cap over the full 9×9, closing the chamber and giving the first
     *       step a flat top.</li>
     *   <li><b>y=4</b> — step 1: a solid 7×7 cut-sandstone block (x=1..7), its
     *       outer edge banded in orange terracotta (the receding step face).</li>
     *   <li><b>y=5</b> — step 2: a solid 5×5 cut-sandstone block (x=2..6), edge
     *       banded in orange terracotta.</li>
     *   <li><b>y=6</b> — step 3: a solid 3×3 cut-sandstone block (x=3..5) with a
     *       blue terracotta centre inlay reading as the temple's top motif.</li>
     *   <li><b>y=7</b> — a single chiseled-sandstone finial capstone at (4,4).</li>
     * </ul>
     */
    private static Blueprint desertPyramidShrine() {
        Blueprint.Builder b = Blueprint.builder("Desert Pyramid Shrine", 9, 8, 9);
        Palette p = DESERT_SANDSTONE;
        BlueprintBlockState cutSandstone = p.wall;            // cut_sandstone
        BlueprintBlockState smoothSandstone = p.accentWall;   // smooth_sandstone
        BlueprintBlockState chiseledSandstone = p.logPillarY; // chiseled_sandstone
        BlueprintBlockState sandstoneSlabTop = p.slabTop;     // sandstone_slab[type=top]
        BlueprintBlockState orangeTerracotta = bs("minecraft:orange_terracotta");
        BlueprintBlockState blueTerracotta = bs("minecraft:blue_terracotta");
        int cx = 4, cz = 4;

        // y=0 — smooth-sandstone foundation apron over the full 9×9 footprint
        floor(b, 0, 0, 0, 8, 8, smoothSandstone);

        // y=1..2 — base tier: cut-sandstone wall ring on the 9×9 footprint,
        //          enclosing a hollow 7×7 chamber (interior left unset = enterable)
        walls(b, 0, 0, 8, 8, 1, 2, cutSandstone);
        corners(b, 0, 0, 8, 8, 1, 2, chiseledSandstone); // chiseled corner pilasters
        // door centred on the north wall (z=0), opening inward (faces south)
        door2(b, cx, 1, 0, "acacia", "N");
        // blue terracotta accent course flanking the door on the front (north) face
        b.set(2, 2, 0, blueTerracotta);
        b.set(6, 2, 0, blueTerracotta);
        // glass-pane windows centred on the three other walls (render-safe: each
        // pane sits between two wall cells along its wall line)
        window2(b, cx, 2, 8, p.windowPane, null); // south (back) wall
        window2(b, 0, 2, cz, p.windowPane, null); // west wall
        window2(b, 8, 2, cz, p.windowPane, null); // east wall

        // y=3 — chamber ceiling / step-0 deck: solid sandstone-slab cap over 9×9
        floor(b, 3, 0, 0, 8, 8, sandstoneSlabTop);

        // y=4 — step 1: solid 7×7 cut-sandstone block, outer edge banded orange
        solid(b, 1, 4, 1, 7, 4, 7, cutSandstone);
        for (int x = 1; x <= 7; x++) {            // orange terracotta step face ring
            b.set(x, 4, 1, orangeTerracotta);
            b.set(x, 4, 7, orangeTerracotta);
        }
        for (int z = 2; z <= 6; z++) {
            b.set(1, 4, z, orangeTerracotta);
            b.set(7, 4, z, orangeTerracotta);
        }

        // y=5 — step 2: solid 5×5 cut-sandstone block, outer edge banded orange
        solid(b, 2, 5, 2, 6, 5, 6, cutSandstone);
        for (int x = 2; x <= 6; x++) {
            b.set(x, 5, 2, orangeTerracotta);
            b.set(x, 5, 6, orangeTerracotta);
        }
        for (int z = 3; z <= 5; z++) {
            b.set(2, 5, z, orangeTerracotta);
            b.set(6, 5, z, orangeTerracotta);
        }

        // y=6 — step 3: solid 3×3 cut-sandstone block with a blue terracotta
        //        centre inlay (the temple's top motif)
        solid(b, 3, 6, 3, 5, 6, 5, cutSandstone);
        b.set(cx, 6, cz, blueTerracotta);

        // y=7 — chiseled-sandstone finial capstone at the apex
        b.set(cx, 7, cz, chiseledSandstone);

        return b.build();
    }

    /**
     * Taiga Log Cabin. 7×7 footprint → builder(7, 8, 7). A cozy spruce log cabin
     * with a steep gable roof, spruce-log corner posts, glass-pane windows, an
     * enterable interior, and a cobblestone chimney crowned by a lit campfire
     * (the signature taiga "campfire-smoke chimney"). Built from the same
     * palette-driven helpers as {@link #house} but with the geometry inlined so the
     * footprint — which is square (7×7), and would otherwise get a {@link #hipRoof}
     * from {@code house()} — gets the spec's STEEP GABLE roof instead.
     *
     * <p>Vanilla, FU-valued blocks only (the {@link #TAIGA_SPRUCE} palette family +
     * cobblestone + a lit campfire), so every cell clears the printability gate.
     * The campfire is an itemless-free-printing edge case in vanilla terms but a
     * normal valued item here; either way it's a single placed block, no neighbour
     * dependency, so it's render-safe.
     *
     * <p>Layout (Y), footprint x=0..6 (W), z=0..6 (depth), centre (3,3):
     * <ul>
     *   <li><b>y=0</b> — walkable spruce-plank foundation floor over the full 7×7.</li>
     *   <li><b>y=1..4</b> — spruce-plank wall ring with spruce-log corner posts; a
     *       door opens inward on the north wall (z=0); glass-pane windows centred on
     *       the long walls and the back wall (each between two wall cells →
     *       render-safe). Interior left unset (air-skip rule) so the cabin is
     *       enterable.</li>
     *   <li><b>y=4..7</b> — steep spruce gable roof ({@link #gableRoofX} ridge along
     *       X) with closed gable ends ({@link #gableEndFill}).</li>
     *   <li><b>chimney</b> — a cobblestone stack on the east wall (x=6,z=5) rising
     *       from y=1 through the eave to y=8, with a firebox cobble base and a LIT
     *       {@link #CAMPFIRE} on top emitting smoke (the taiga signature).</li>
     *   <li><b>furnishings</b> (furnish=true equivalent) — a spruce/blue {@link #bed},
     *       a crafting table, a chest, and a lantern on the y=1 floor.</li>
     * </ul>
     */
    private static Blueprint taigaLogCabin() {
        Blueprint.Builder b = Blueprint.builder("Taiga Log Cabin", 7, 9, 7);
        Palette p = TAIGA_SPRUCE;
        int wallH = 4;
        int cx = 3, cz = 3;

        // 1) walkable spruce-plank foundation floor at y=0
        floor(b, 0, 0, 0, 6, 6, p.plankFloor);
        // 2) spruce-plank wall ring y=1..4 with spruce-log corner posts (no nub)
        walls(b, 0, 0, 6, 6, 1, wallH, p.wall);
        corners(b, 0, 0, 6, 6, 1, wallH, p.logPillarY);
        // 3) door centred on the north wall (z=0), opening inward (faces south)
        door2(b, cx, 1, 0, p.doorWood, "N");
        // 4) glass-pane windows at a mid-wall course (between two wall cells →
        //    render-safe), one on each long wall and the back wall
        int wy = 2;
        window2(b, 0, wy, cz, p.windowPane, null); // west long wall
        window2(b, 6, wy, cz, p.windowPane, null); // east long wall
        window2(b, 1, wy, 0, p.windowPane, null);  // north wall, west of door
        window2(b, 5, wy, 0, p.windowPane, null);  // north wall, east of door
        window2(b, cx, wy, 6, p.windowPane, null); // south (back) wall, centre
        // 5) steep spruce gable roof seated on the wall plate at y=wallH, ridge
        //    along X; close the triangular gable ends so the attic isn't open
        gableRoofX(b, 0, 0, 6, 6, wallH, p.roofStairName, p.roofSlab);
        gableEndFill(b, 0, 0, 6, 6, wallH, p.wall);
        // 6) cobblestone chimney on the east wall (x=6,z=5), rising past the eave;
        //    a lit campfire on top gives the signature taiga campfire-smoke plume.
        //    The stack overwrites the roof slope cells it passes through so it reads
        //    as a continuous flue breaking the roofline.
        pillar(b, 6, 5, 1, 7, COBBLE);
        b.set(6, 8, 5, CAMPFIRE);                  // lit campfire = chimney smoke
        // 7) minimal interior furnishings on the walkable y=1 floor
        bed(b, 1, 1, 5, p.bedColor, "south");      // blue bed, head at z=5
        b.set(5, 1, 5, CRAFTING_TABLE);
        b.set(1, 1, 1, CHEST);
        b.set(5, 1, 1, p.lightBlock);              // lantern in the front corner
        return b.build();
    }

    /**
     * Taiga Spruce Longhouse. 9×7 footprint → builder(9, 9, 7). A long, low
     * Nordic-style spruce longhouse on a raised cobblestone footing, with a
     * spruce-log timber frame, an open communal interior (one big hall, no
     * internal walls), glass-pane windows down both long walls, a door on a
     * short end, and a long X-ridge gable roof crowned with a HAY-BALE ridge so
     * it reads as a thatch-look roof (the spec's "hay thatch" cue). Inlined
     * rather than calling {@link #house} so the cobble footing, the extra
     * timber-frame posts, and the hay ridge are placed explicitly.
     *
     * <p>Vanilla, FU-valued blocks only — the {@link #TAIGA_SPRUCE} palette
     * family (spruce planks/logs/stairs/slabs, glass panes), cobblestone footing,
     * and hay blocks (a valued, farmable resource). Every cell clears the
     * printability gate; the only connecting blocks are the long-wall window
     * panes, each flanked by wall cells on both sides → render-safe.
     *
     * <p>Layout (Y), footprint x=0..8 (W=9, the long axis), z=0..6 (depth=7),
     * door centred on the north short wall at x=4:
     * <ul>
     *   <li><b>y=0</b> — a solid cobblestone footing slab over the full 9×7
     *       (the "stilted/cobblestone footing" the longhouse sits on); its top
     *       face is the walkable floor.</li>
     *   <li><b>y=1</b> — a spruce-plank finish floor laid on the footing so the
     *       interior reads as a timber hall, not bare stone (walkable surface =
     *       top of y=1; walls + interior props start at y=2).</li>
     *   <li><b>y=2..5</b> — spruce-plank wall ring with spruce-log corner posts
     *       AND intermediate spruce-log studs every 2 cells along the long walls
     *       (the longhouse timber-frame beam look); a door opens inward on the
     *       north short wall; glass-pane windows run down both long walls between
     *       stud bays. Interior left unset (air-skip rule) so the hall is one
     *       open, enterable space.</li>
     *   <li><b>y=5..8</b> — a spruce gable roof ({@link #gableRoofX} ridge along
     *       the long X axis, seated on the y=5 wall plate, peaking at y=8) with
     *       closed gable ends ({@link #gableEndFill}); the ridge course is HAY
     *       blocks for the thatch-look crown.</li>
     *   <li><b>furnishings</b> — a long communal hall: a spruce/blue {@link #bed}
     *       at each end, a central long fire pit ({@link #CAMPFIRE} flanked by
     *       cobble), a crafting table, a chest, and lanterns.</li>
     * </ul>
     */
    private static Blueprint taigaSpruceLonghouse() {
        Blueprint.Builder b = Blueprint.builder("Taiga Spruce Longhouse", 9, 9, 7);
        Palette p = TAIGA_SPRUCE;
        int x0 = 0, x1 = 8, z0 = 0, z1 = 6;
        int floorY = 1;        // walkable spruce floor sits on the cobble footing
        int wallBottom = 2;    // walls rise from above the finish floor
        int wallH = 5;         // wall plate (roof seats here)
        int cx = (x0 + x1) / 2; // 4
        int cz = (z0 + z1) / 2; // 3

        // 1) cobblestone footing over the whole footprint at y=0
        floor(b, 0, x0, z0, x1, z1, COBBLE);
        // 2) spruce-plank finish floor on top of the footing at y=1 (walkable)
        floor(b, floorY, x0, z0, x1, z1, p.plankFloor);
        // 3) spruce-plank wall ring y=2..5 with spruce-log corner posts
        walls(b, x0, z0, x1, z1, wallBottom, wallH, p.wall);
        corners(b, x0, z0, x1, z1, wallBottom, wallH, p.logPillarY);
        // 3b) intermediate spruce-log studs every 2 cells down both long walls
        //     (the timber-frame beam rhythm); corners already posted above.
        for (int x = x0 + 2; x <= x1 - 2; x += 2) {
            pillar(b, x, z0, wallBottom, wallH, p.logPillarY);
            pillar(b, x, z1, wallBottom, wallH, p.logPillarY);
        }
        // 4) door centred on the north short wall (z=0), opening inward (south)
        door2(b, cx, wallBottom, z0, p.doorWood, "N");
        // 5) glass-pane windows at a mid-wall course (each cell flanked by wall
        //    cells on both sides → render-safe).
        int wy = wallBottom + 1; // y=3
        // north short wall (z=z0): two windows flanking the central door
        window2(b, cx - 1, wy, z0, p.windowPane, null);
        window2(b, cx + 1, wy, z0, p.windowPane, null);
        // long walls (west x=x0, east x=x1): a window in the bay between each
        //    pair of studs, mid-wall (odd z columns avoid the corner posts).
        for (int z = z0 + 1; z <= z1 - 1; z += 2) {
            window2(b, x0, wy, z, p.windowPane, null); // west long wall
            window2(b, x1, wy, z, p.windowPane, null); // east long wall
        }
        // south short wall (z=z1): two windows flanking centre
        window2(b, cx - 1, wy, z1, p.windowPane, null);
        window2(b, cx + 1, wy, z1, p.windowPane, null);
        // 6) spruce gable roof seated on the wall plate at y=wallH, ridge along
        //    the long X axis; HAY-block ridge cap for the thatch-look crown.
        gableRoofX(b, x0, z0, x1, z1, wallH, p.roofStairName, HAY);
        gableEndFill(b, x0, z0, x1, z1, wallH, p.wall);
        // 7) communal hall furnishings on the walkable y=2 floor:
        //    a bed at each long end, a central long fire pit, table, chest, lights.
        bed(b, x0 + 1, wallBottom, z1 - 1, p.bedColor, "south"); // west-end bed
        bed(b, x1 - 1, wallBottom, z1 - 1, p.bedColor, "south"); // east-end bed
        // central long fire pit: a lit campfire flanked by cobble hearth stones
        b.set(cx, wallBottom, cz, CAMPFIRE);
        b.set(cx - 1, wallBottom, cz, COBBLE);
        b.set(cx + 1, wallBottom, cz, COBBLE);
        b.set(x0 + 1, wallBottom, z0 + 1, CRAFTING_TABLE);
        b.set(x1 - 1, wallBottom, z0 + 1, CHEST);
        b.set(x0 + 1, wallBottom, cz, p.lightBlock); // lantern, west
        b.set(x1 - 1, wallBottom, cz, p.lightBlock); // lantern, east
        return b.build();
    }

    /**
     * snowy_igloo — Category A, build 5/103. A domed snow-block igloo: the classic
     * recognizable taiga/snowy-biome shelter. Vanilla blocks only, all FU-valued.
     *
     * <p>Footprint 7×9×4 (W×L×H): a radius-3 snow hemisphere (7×7 footprint, 4 tall
     * incl. floor) with a 2-deep entry tunnel poking south, so the overall depth is 9.
     * Sits comfortably in the T4 single-printer footprint band.
     *
     * <p>Construction, honouring the air-skip / hollow-enterable rules:
     * <ul>
     *   <li><b>floor</b> — a {@link #disc} of snow_block at y=0 under the dome, with a
     *       packed-ice {@link #circleRing} and a single blue-ice centre tile as the ice
     *       accents called for in the spec.</li>
     *   <li><b>shell</b> — {@link #dome} (snow_block) springing from y=0, radius 3. The
     *       helper places only the perimeter rings, so the interior is naturally hollow
     *       — a player can stand inside (inner clearance ~5 wide × ~3 tall).</li>
     *   <li><b>entry</b> — a short snow tunnel on the south face: two arch courses
     *       (sides + roof) around the doorway column, with an open spruce trapdoor as
     *       the igloo flap at the mouth. The dome shell cell at the tunnel mouth is
     *       overwritten to a passable trapdoor-backed gap so the interior is enterable
     *       end-to-end.</li>
     *   <li><b>furnishings</b> — a {@code light_blue} {@link #bed} against the back wall
     *       and a floor lantern for the cosy interior the spec asks for.</li>
     * </ul>
     */
    private static Blueprint snowyIgloo() {
        // build-local materials (all vanilla, all FU-valued; ice/trapdoor derive)
        BlueprintBlockState snow = bs("minecraft:snow_block");
        BlueprintBlockState packedIce = bs("minecraft:packed_ice");
        BlueprintBlockState blueIce = bs("minecraft:blue_ice");
        // open spruce trapdoor on the floor course, hinged at the bottom → passable flap
        BlueprintBlockState flap =
                bs("minecraft:spruce_trapdoor[facing=south,half=bottom,open=true,powered=false,waterlogged=false]");

        int r = 3;                 // dome radius → 7-wide footprint, ~3-tall interior
        int cx = r;                // centre column x=3  (footprint x=0..6)
        int cz = r;                // dome centre z=3   (dome footprint z=0..6)
        int sizeX = 2 * r + 1;     // 7
        int sizeY = r + 1;         // 4 (floor at y=0 + dome up to y=3)
        int tunnel = 2;            // entry tunnel depth poking south
        int sizeZ = 2 * r + 1 + tunnel; // 9
        int z1 = 2 * r;            // dome's south edge z=6 (tunnel grows from here)

        Blueprint.Builder b = Blueprint.builder("Snowy Igloo", sizeX, sizeY, sizeZ);

        // 1) ice-accented snow floor disc at y=0
        disc(b, 0, cx, cz, r, snow);
        circleRing(b, 0, cx, cz, r - 1, packedIce); // inner packed-ice ring accent
        b.set(cx, 0, cz, blueIce);                  // blue-ice centre tile

        // 2) snow hemisphere shell (hollow by construction → enterable)
        dome(b, cx, cz, 0, r, snow);

        // 3) entry: a short snow tunnel on the south face, centred on x=cx.
        //    Tunnel courses at z=z1+1 .. z1+tunnel: side walls + roof, floor walkable.
        for (int z = z1 + 1; z <= z1 + tunnel; z++) {
            b.set(cx - 1, 0, z, snow); // west side, base
            b.set(cx + 1, 0, z, snow); // east side, base
            b.set(cx - 1, 1, z, snow); // west side, upper
            b.set(cx + 1, 1, z, snow); // east side, upper
            b.set(cx, 2, z, snow);     // tunnel roof
            b.set(cx, 0, z, snow);     // tunnel floor (walkable lip)
        }
        // tunnel mouth flap: an open spruce trapdoor at the outer end
        b.set(cx, 1, z1 + tunnel, flap);
        // open the dome shell where the tunnel meets it: overwrite the springing
        // ring cell at the doorway column with a passable trapdoor-backed gap so
        // you can pass from the tunnel into the hollow interior.
        b.set(cx, 1, z1, flap);

        // 4) interior furnishings on the y=0 floor
        bed(b, cx, 1, cz - 1, "light_blue", "north"); // head at back, foot toward door
        b.set(cx + 1, 1, cz + 1, LANTERN);            // floor lantern, off-centre

        return b.build();
    }

    /**
     * snowy_alpine_chalet — Category A, build 6/103. A steep-roofed alpine chalet:
     * the recognizable Swiss/snowy-mountain house with a stone-brick base, spruce +
     * stripped-spruce timber-frame walls, a steep snow-laden gable roof with
     * overhanging eaves, and a small fenced balcony tucked under the south eave.
     * Vanilla blocks only, all FU-valued (snow_block, stone bricks/slab, spruce
     * planks/logs/stairs/slabs/fence, glass panes — dyed/normalised values fine).
     *
     * <p>Footprint 9×7×9 (W×L×H): builder(9, 9, 7) — x=0..8 (W=9, ridge axis),
     * z=0..6 (depth=7); a steep X-ridge gable peaks at y=8. Sits in the T5 band.
     * The chalet BODY is inset to z=1..5 so the deep eaves (z=0 / z=6) and the
     * cantilevered balcony (z=6) fall inside the fixed 9×7 footprint.
     *
     * <p>Construction, honouring the air-skip / hollow-enterable rules:
     * <ul>
     *   <li><b>y=0</b> — a solid stone-brick footing slab over the body (x=0..8,
     *       z=1..5); its top face is the walkable ground floor. A stone-brick plinth
     *       ring rises one course (y=1) around the body perimeter for the chalet's
     *       masonry base, with a spruce-plank finish floor laid inside it (walkable
     *       surface = top of y=1).</li>
     *   <li><b>y=2..5</b> — spruce-plank wall ring on the plinth, with stripped-spruce
     *       corner posts and intermediate stripped-spruce studs every 2 cells (the
     *       alpine timber-frame look). A door opens inward on the north wall and one
     *       onto the balcony on the south wall; glass-pane windows sit between studs,
     *       each flanked by wall cells → render-safe. The interior is left unset so the
     *       chalet is one open, enterable room.</li>
     *   <li><b>eaves</b> — a deep overhanging spruce-slab soffit one cell out from each
     *       long wall at the roof base (z=0 and z=6), the alpine snow-shedding overhang.</li>
     *   <li><b>y=6..8</b> — a STEEP spruce-stair gable roof ({@link #gableRoofX}, ridge
     *       along the long X axis, seated on the y=6 wall plate over the body z=1..5,
     *       peaking at y=8) with a snow-block ridge cap and closed gable ends
     *       ({@link #gableEndFill}) — and a layer of snow-block "snow load" draped over
     *       the lowest stair course of each slope, the chalet's signature.</li>
     *   <li><b>balcony</b> — a small spruce-slab deck cantilevered off the south wall at
     *       floor-plate height (y=2) over z=6, railed with a spruce-fence ring; reachable
     *       through the south-wall door so it's a real, enterable balcony.</li>
     *   <li><b>furnishings</b> — a light-blue {@link #bed} against the back wall, a
     *       crafting table, a chest, and lanterns for a cosy alpine interior.</li>
     * </ul>
     */
    private static Blueprint snowyAlpineChalet() {
        Blueprint.Builder b = Blueprint.builder("Snowy Alpine Chalet", 9, 9, 7);
        Palette p = SNOWY; // spruce + snow-block trim, spruce logs, light-blue bed
        // build-local materials (all vanilla, all FU-valued)
        BlueprintBlockState snow = bs("minecraft:snow_block");
        BlueprintBlockState strippedY = bs("minecraft:stripped_spruce_log[axis=y]");
        BlueprintBlockState spruceFence = bs("minecraft:spruce_fence");

        // Body inset to z=1..5 (depth 5) so eaves (z=0 / z=6) and balcony (z=6) fit
        // inside the fixed 9×7 footprint. Ridge runs the full width x=0..8.
        int x0 = 0, x1 = 8, z0 = 1, z1 = 5;
        int eaveN = z0 - 1, eaveS = z1 + 1; // 0 and 6 — within bounds
        int floorY = 1;        // walkable spruce floor sits on the stone-brick plinth
        int wallBottom = 2;    // walls rise from above the finish floor
        int wallH = 5;         // wall plate (roof seats at y=6, one above the plate)
        int roofBase = wallH + 1; // y=6
        int cx = (x0 + x1) / 2;   // 4
        int cz = (z0 + z1) / 2;   // 3

        // 1) stone-brick footing over the body at y=0 (walkable ground)
        floor(b, 0, x0, z0, x1, z1, STONE_BRICKS);
        // 1b) stone-brick plinth ring one course up (y=1) — the masonry base
        walls(b, x0, z0, x1, z1, floorY, floorY, STONE_BRICKS);
        // 1c) spruce-plank finish floor inside the plinth at y=1 (walkable surface)
        floor(b, floorY, x0 + 1, z0 + 1, x1 - 1, z1 - 1, p.plankFloor);

        // 2) spruce-plank wall ring y=2..5 with stripped-spruce corner posts
        walls(b, x0, z0, x1, z1, wallBottom, wallH, p.wall);
        corners(b, x0, z0, x1, z1, wallBottom, wallH, strippedY);
        // 2b) intermediate stripped-spruce studs every 2 cells down both long walls
        for (int x = x0 + 2; x <= x1 - 2; x += 2) {
            pillar(b, x, z0, wallBottom, wallH, strippedY);
            pillar(b, x, z1, wallBottom, wallH, strippedY);
        }

        // 3) door centred on the north wall (z=z0), opening inward (faces south)
        door2(b, cx, wallBottom, z0, p.doorWood, "N");
        // 3b) south-wall door onto the balcony, opening inward (faces north)
        door2(b, cx, wallBottom, z1, p.doorWood, "S");

        // 4) glass-pane windows at a mid-wall course (each flanked by wall cells →
        //    render-safe). North wall flanks the door; long walls between studs.
        int wy = wallBottom + 1; // y=3
        window2(b, cx - 1, wy, z0, p.windowPane, null); // north, west of door
        window2(b, cx + 1, wy, z0, p.windowPane, null); // north, east of door
        for (int z = z0 + 1; z <= z1 - 1; z += 2) {
            window2(b, x0, wy, z, p.windowPane, null);  // west long wall
            window2(b, x1, wy, z, p.windowPane, null);  // east long wall
        }

        // 5) deep overhanging eave soffit: a spruce-slab course one cell out from each
        //    long wall at the roof base (the alpine snow-shedding overhang).
        for (int x = x0; x <= x1; x++) {
            b.set(x, roofBase, eaveN, p.slabBottom); // north eave lip (z=0)
            b.set(x, roofBase, eaveS, p.slabBottom); // south eave lip (z=6)
        }

        // 6) STEEP spruce gable roof seated on the wall plate at y=roofBase over the
        //    body (z0..z1), ridge along the long X axis; snow-block ridge cap, closed
        //    gable ends.
        gableRoofX(b, x0, z0, x1, z1, roofBase, p.roofStairName, snow);
        gableEndFill(b, x0, z0, x1, z1, roofBase, p.wall);
        // 6b) snow load: drape snow blocks over the lowest stair course of each slope
        //     (one cell up the eave) so the roof reads as snow-laden.
        for (int x = x0; x <= x1; x++) {
            b.set(x, roofBase + 1, z0, snow); // snow on the north eave course
            b.set(x, roofBase + 1, z1, snow); // snow on the south eave course
        }

        // 7) small fenced balcony cantilevered off the south wall (z1) onto z=6 at
        //    floor-plate height; reachable via the south door.
        int bz = eaveS;                  // balcony deck row, z=6 (one cell south of wall)
        for (int x = cx - 1; x <= cx + 1; x++) {
            b.set(x, wallBottom - 1, bz, p.slabTop); // deck slab (top half = flush walk)
        }
        // spruce-fence railing around the open balcony edges (the wall closes the back)
        b.set(cx - 1, wallBottom, bz, spruceFence); // west rail
        b.set(cx,     wallBottom, bz, spruceFence); // south rail (front)
        b.set(cx + 1, wallBottom, bz, spruceFence); // east rail

        // 8) interior furnishings on the walkable y=2 floor
        bed(b, x0 + 1, wallBottom, z1 - 1, p.bedColor, "south"); // light-blue bed at back
        b.set(x1 - 1, wallBottom, z1 - 1, CRAFTING_TABLE);
        b.set(x1 - 1, wallBottom, z0 + 1, CHEST);
        b.set(x0 + 1, wallBottom, z0 + 1, p.lightBlock);         // lantern, front corner
        b.set(cx, wallBottom, cz, p.lightBlock);                 // central lantern

        return b.build();
    }

    /**
     * Jungle Hut — an elevated jungle-platform hut on jungle-log stilts with a jungle
     * gable canopy, fence accents, and a ladder up to the raised, enterable deck.
     * 7×7 footprint (T5), disc T1. Vanilla, FU-valued blocks only: jungle
     * logs/planks/slabs/stairs/fence/door, glass panes, ladder, lanterns, grass footing.
     * (jungle_leaves + bamboo were swapped out — neither is FU-valued or structural.)
     *
     * <p>Section structure (y from the ground up):
     * <ul>
     *   <li>{@code y=0} grass footing under the stilts (so the build reads as set on
     *       ground); the stilt posts rise from it.</li>
     *   <li>{@code y=0..2} jungle-log stilts at the four corners + the four edge
     *       midpoints, raising the platform clear of the ground.</li>
     *   <li>{@code y=3} jungle-plank platform deck (the walkable raised floor) with a
     *       ladder hatch left open at the south-edge access column.</li>
     *   <li>{@code y=4..6} jungle-plank wall ring with jungle-log corner posts, a
     *       north door opening inward, and render-safe glass-pane windows.</li>
     *   <li>{@code y=7..10} jungle gable roof (ridge along X) closing the top — the
     *       FU-valued stand-in for a leaf canopy (jungle_leaves carries no FU value).</li>
     *   <li>jungle-fence accent posts flank the stilts (stand-in for the unvalued
     *       bamboo); a ladder on the south stilt column climbs y=1..2 to the deck
     *       hatch; hanging lanterns light the porch underside.</li>
     * </ul>
     */
    private static Blueprint jungleHut() {
        Blueprint.Builder b = Blueprint.builder("Jungle Hut", 7, 11, 7);
        Palette p = JUNGLE; // jungle planks/logs/slabs/stairs, cyan bed, lantern
        // build-local materials (all vanilla, all FU-valued / structural).
        // NOTE: jungle_leaves and bamboo are NOT FU-valued and NOT structural matter,
        // so they print as silent holes in strict mode (caught by the printability
        // gate). The "jungle canopy + bamboo" look is therefore rendered with the
        // FU-valued jungle WOOD family instead: a jungle gable roof for the canopy
        // and jungle-fence posts for the bamboo accents.
        BlueprintBlockState jungleFence = bs("minecraft:jungle_fence");
        BlueprintBlockState logY = p.logPillarY; // jungle_log[axis=y]

        int x0 = 0, x1 = 6, z0 = 0, z1 = 6;
        int cx = (x0 + x1) / 2; // 3
        int cz = (z0 + z1) / 2; // 3
        int deckY = 3;          // raised platform (walkable surface = top of y=3)
        int wallBottom = 4;     // walls rise from above the deck
        int wallH = 6;          // wall plate (canopy seats at y=7)
        int roofY = wallH + 1;  // 7

        // 1) grass footing under the build so it sits on the ground (structural matter)
        floor(b, 0, x0, z0, x1, z1, GRASS_BLOCK);

        // 2) jungle-log stilts y=0..2 at the four corners + the four edge midpoints
        int[][] stilts = {
                {x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}, // corners
                {cx, z0}, {cx, z1}, {x0, cz}, {x1, cz}  // edge midpoints
        };
        for (int[] s : stilts) {
            pillar(b, s[0], s[1], 1, deckY - 1, logY); // y=1..2 up to just below the deck
        }
        // 2b) jungle-fence accent posts beside the four corner stilts (stand in for the
        //     bamboo growth — FU-valued and slim, so they read as jungle uprights).
        for (int[] c : new int[][]{{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
            int bx = c[0] == x0 ? x0 + 1 : x1 - 1;
            int bz = c[1] == z0 ? z0 + 1 : z1 - 1;
            pillar(b, bx, bz, 1, deckY - 1, jungleFence); // fence post beside each corner, under the deck
        }

        // 3) jungle-plank platform deck at y=3 (the raised walkable floor), with a
        //    ladder hatch left OPEN at the south access column (cx, z1-1).
        int hatchX = cx, hatchZ = z1 - 1; // (3,5)
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (x == hatchX && z == hatchZ) continue; // ladder hatch — leave open
                b.set(x, deckY, z, p.plankFloor);
            }
        }

        // 4) jungle-plank wall ring y=4..6 with jungle-log corner posts
        walls(b, x0, z0, x1, z1, wallBottom, wallH, p.wall);
        corners(b, x0, z0, x1, z1, wallBottom, wallH, logY);

        // 5) north door (z=z0) opening inward (faces south) + render-safe windows.
        //    Each pane is flanked by wall cells along its wall axis → connects.
        door2(b, cx, wallBottom, z0, p.doorWood, "N");
        int wy = wallBottom + 1; // y=5, mid-wall
        window2(b, cx - 1, wy, z0, p.windowPane, null); // north, west of door
        window2(b, cx + 1, wy, z0, p.windowPane, null); // north, east of door
        window2(b, x0, wy, cz, p.windowPane, null);     // west wall, centred
        window2(b, x1, wy, cz, p.windowPane, null);     // east wall, centred
        window2(b, cx, wy, z1, p.windowPane, null);     // south (back) wall, centred

        // 6) jungle canopy roof: a jungle gable roof (ridge along X) seated on the wall
        //    plate, with closed gable ends — the FU-valued stand-in for a leaf canopy.
        gableRoofX(b, x0, z0, x1, z1, roofY, p.roofStairName, p.roofSlab);
        gableEndFill(b, x0, z0, x1, z1, roofY, p.wall);

        // 7) ladder access: the south stilt column at (cx, z1) is solid jungle log
        //    y=1..2 (full backing post), so a south-facing ladder on the hatch column
        //    (cx, z1-1) backs onto it and climbs to the deck. Ladder faces south →
        //    attaches to the block at (cx, *, z1).
        pillar(b, cx, z1, 1, deckY - 1, logY); // full backing post for the ladder rungs
        b.set(hatchX, 1, hatchZ, LADDER_SOUTH); // rung y=1, backed by (cx,z1) log
        b.set(hatchX, 2, hatchZ, LADDER_SOUTH); // rung y=2 → climb out onto deck at y=3

        // 9) hanging lanterns under the deck for the elevated-hut glow, backed by the
        //    plank deck above them (deck at y=3 is solid over these cells). Placed off
        //    the fence-post cells so they don't collide with the accent posts.
        b.set(cx - 1, deckY - 1, cz, HANGING_LANTERN); // hangs from the deck underside
        b.set(cx + 1, deckY - 1, cz, HANGING_LANTERN);

        // 10) interior furnishings on the raised deck (standing floor = y=4)
        bed(b, x0 + 1, wallBottom, z1 - 1, p.bedColor, "south"); // cyan bed at back
        b.set(x1 - 1, wallBottom, z1 - 1, CRAFTING_TABLE);
        b.set(x1 - 1, wallBottom, z0 + 1, CHEST);
        b.set(x0 + 1, wallBottom, z0 + 1, p.lightBlock);         // lantern, front corner
        b.set(cx, wallBottom, cz, p.lightBlock);                 // central lantern

        return b.build();
    }

    /**
     * Jungle Temple Ruin. 9×9 footprint → builder(9, 8, 9). An overgrown,
     * partly-collapsed jungle stone temple: a stepped pyramid-ish silhouette with
     * a small enterable chamber at the base and a weathered, ruined read achieved
     * entirely with FU-valued stone families.
     *
     * <p><b>Why no vines/leaves/moss-block.</b> The candidate row calls for "vines"
     * for the overgrown look, but {@code vines}, {@code leaves}, {@code moss_block},
     * and {@code grass_block} carry NO FU value (they'd silently drop in strict mode
     * and trip the printability gate). The "overgrown / weathered" look is therefore
     * rendered with the FU-valued mossy/cracked stone families instead:
     * {@link #MOSSY_COBBLE} and {@link #MOSSY_STONE_BRICKS} for the moss creep,
     * {@link #CRACKED_STONE_BRICKS} for the spalled/cracked faces, plain
     * {@link #COBBLE} for the rubble, and {@link #CHISELED_STONE_BRICKS} for the
     * ornamental temple motif. A ruin reads convincingly in mossy/cracked stone
     * alone, so nothing here needs the BLOCKED path.
     *
     * <p>Layout (Y), footprint x=0..8 (W), z=0..8 (depth), centre (4,4):
     * <ul>
     *   <li><b>y=0</b> — mossy/plain cobble foundation apron over the full 9×9, with
     *       a scatter of rubble (mixed cobble variants) so the base reads weathered.</li>
     *   <li><b>y=1..3</b> — base tier wall ring on the 9×9 footprint enclosing a
     *       hollow 7×7 chamber (interior left unset = enterable). The ring is a
     *       weathered mix (mossy cobble / cracked & mossy stone bricks) with chiseled
     *       corner pilasters, a north doorway opening inward, and a partly-COLLAPSED
     *       south-east section (top courses left off) so the silhouette reads as a
     *       ruin rather than an intact box. Render-safe glass-pane "shrine windows"
     *       sit between wall cells on the intact walls.</li>
     *   <li><b>y=4</b> — chamber ceiling / step-0 deck: a weathered cobble cap with a
     *       few cells left open (collapsed roof) so the ruin reads broken from above.</li>
     *   <li><b>y=4..7</b> — three receding steps (7×7 → 5×5 → 3×3) of weathered stone
     *       forming the stepped pyramid spine, each step edge banded in mossy/cracked
     *       brick; a chiseled-stone-brick motif inlay crowns the 3×3 step and a single
     *       chiseled finial caps the apex.</li>
     *   <li><b>stepped entry</b> — a stone-brick-stair stair flight climbing the north
     *       face up to the chamber doorway (walkable, via {@link #ramp}).</li>
     *   <li><b>rubble</b> — toppled cobble-wall stubs at two corners and a fallen
     *       block or two on the apron, reinforcing the collapsed read.</li>
     * </ul>
     */
    private static Blueprint jungleTempleRuin() {
        Blueprint.Builder b = Blueprint.builder("Jungle Temple Ruin", 9, 8, 9);
        // weathered stone family — all vanilla, all FU-valued.
        BlueprintBlockState mossyCobble = MOSSY_COBBLE;
        BlueprintBlockState cobble = COBBLE;
        BlueprintBlockState crackedBrick = CRACKED_STONE_BRICKS;
        BlueprintBlockState mossyBrick = MOSSY_STONE_BRICKS;
        BlueprintBlockState chiseledBrick = CHISELED_STONE_BRICKS;
        BlueprintBlockState cobbleWall = COBBLE_WALL;
        int x0 = 0, x1 = 8, z0 = 0, z1 = 8;
        int cx = 4, cz = 4;

        // y=0 — weathered cobble foundation apron over the full 9×9. A simple
        // checker of mossy vs plain cobble gives a mottled, overgrown ground read.
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                b.set(x, 0, z, ((x + z) % 2 == 0) ? mossyCobble : cobble);
            }
        }

        // y=1..3 — base tier wall ring (9×9) enclosing a hollow 7×7 chamber. Built
        // course-by-course so the weathering varies by height and so the SE section
        // can be left collapsed (top courses omitted) for the ruin silhouette.
        for (int y = 1; y <= 3; y++) {
            // north & west faces: intact, weathered brick/cobble mix
            for (int x = x0; x <= x1; x++) {
                b.set(x, y, z0, weatheredWall(x, y, mossyCobble, crackedBrick, mossyBrick));
            }
            for (int z = z0; z <= z1; z++) {
                b.set(x0, y, z, weatheredWall(z, y, mossyCobble, crackedBrick, mossyBrick));
            }
            // east & south faces: collapse the upper courses on the SE quadrant.
            for (int z = z0; z <= z1; z++) {
                // east face (x=x1): drop y=3 for z>=5 (toppled corner)
                if (!(y == 3 && z >= 5)) {
                    b.set(x1, y, z, weatheredWall(z, y, mossyCobble, crackedBrick, mossyBrick));
                }
            }
            for (int x = x0; x <= x1; x++) {
                // south face (z=z1): drop y>=2 for x>=5 (collapsed wall section)
                if (!(y >= 2 && x >= 5)) {
                    b.set(x, y, z1, weatheredWall(x, y, mossyCobble, crackedBrick, mossyBrick));
                }
            }
        }
        // chiseled corner pilasters (the NW two stay full height; SE is rubble below)
        pillar(b, x0, z0, 1, 3, chiseledBrick);
        pillar(b, x1, z0, 1, 3, chiseledBrick);
        pillar(b, x0, z1, 1, 3, chiseledBrick);
        pillar(b, x1, z1, 1, 1, chiseledBrick); // SE pilaster broken off at y=1

        // north doorway (z=z0), opening inward (faces south), at the foot of the steps
        door2(b, cx, 1, z0, "jungle", "N");

        // render-safe shrine windows on the intact walls (each pane between two wall
        // cells along its wall line so it connects horizontally → render-safe).
        window2(b, cx, 2, z0, GLASS_PANE, null);  // north (over the door lintel line)
        window2(b, x0, 2, cz, GLASS_PANE, null);  // west wall, centred
        window2(b, cx, 2, z1, GLASS_PANE, null);  // south wall, centred (intact half)

        // y=4 — chamber ceiling / step-0 deck: weathered cobble cap over the 9×9,
        // with a collapsed hole (a few cells left OPEN) so the roof reads broken.
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (x >= 5 && z >= 5) continue;                 // collapsed corner of the roof
                if (x == cx && z == cz) continue;               // central oculus gap
                b.set(x, 4, z, ((x + z) % 2 == 0) ? cobble : mossyCobble);
            }
        }

        // y=4 step-1 ring sits ON the deck; build the receding pyramid spine as three
        // solid weathered blocks (7×7 → 5×5 → 3×3), each banded on its edge.
        // step 1: 7×7 solid at y=5, edge banded mossy/cracked
        solid(b, 1, 5, 1, 7, 5, 7, cobble);
        stepBand(b, 5, 1, 1, 7, 7, mossyBrick, crackedBrick);
        // step 2: 5×5 solid at y=6
        solid(b, 2, 6, 2, 6, 6, 6, cobble);
        stepBand(b, 6, 2, 2, 6, 6, crackedBrick, mossyBrick);
        // step 3: 3×3 solid at y=7 with a chiseled centre motif
        solid(b, 3, 7, 3, 5, 7, 5, mossyCobble);
        b.set(cx, 7, cz, chiseledBrick);

        // stepped entry: a stone-brick-stair flight up the north face to the doorway.
        // The doorway sits at y=1 (z=0); the steps climb from the apron up to it on
        // the cells just outside the north wall. Place a short 1-wide walkable flight
        // descending away from the door (so a player can walk up into the chamber).
        b.set(cx, 0, 0, bs("minecraft:stone_brick_stairs[facing=south,half=bottom,shape=straight]"));

        // rubble: toppled cobble-wall stubs marking the collapsed SE corner, plus a
        // fallen block on the apron — reinforces the ruin read.
        b.set(x1 - 1, 1, z1, cobbleWall);     // stub where the south wall fell
        b.set(x1, 1, z1 - 1, cobbleWall);     // stub where the east wall fell
        b.set(x1 - 1, 1, z1 - 1, cobble);     // a fallen block inside the breach

        return b.build();
    }

    /**
     * Mangrove Stilt Hut. 7×7 footprint → builder(7, 11, 7). A swamp dwelling
     * raised on mangrove-log stilts over open water, reached by a ladder up to a
     * planked deck, lit by hanging lanterns under the eaves and below the deck.
     *
     * <p><b>Block choices (all FU-valued vanilla).</b> The candidate row asks for
     * "mangrove planks/roots, mud brick, frog-light", but {@code mangrove_roots},
     * {@code muddy_mangrove_roots}, {@code mud}, {@code mud_bricks},
     * {@code packed_mud} and every {@code *_froglight} carry NO FU value (they'd
     * silently drop in strict mode and trip the printability gate). So the swamp
     * read is rendered with the FU-valued mangrove WOOD family ({@link #MANGROVE}
     * palette — planks/logs/slabs/stairs, red bed) over a {@link #COBBLE} /
     * {@link #MOSSY_COBBLE} stone footing, lit with {@link #LANTERN}/hanging
     * lanterns in place of froglight, and standing in real {@link #WATER}
     * (structural matter — prints free) below the deck. A stilt hut reads fine in
     * mangrove wood, so nothing here needs the BLOCKED path.
     *
     * <p>Layout (Y), footprint x=0..6 (W), z=0..6 (depth), centre (3,3):
     * <ul>
     *   <li><b>y=0</b> — a 7×7 water surface (the hut sits "over water"), with a
     *       mossy/plain cobble footing pad under each stilt so the posts read as
     *       founded on the swamp bed rather than floating.</li>
     *   <li><b>y=1..2</b> — eight mangrove-log stilts (4 corners + 4 edge mids)
     *       rising out of the water to just under the deck, plus a full backing
     *       post under the ladder column.</li>
     *   <li><b>y=3</b> — the raised mangrove-plank deck (walkable surface), with a
     *       ladder hatch left OPEN at the south access column.</li>
     *   <li><b>y=4..6</b> — mangrove-plank wall ring with mangrove-log corner
     *       posts, a north door opening inward, and render-safe glass-pane
     *       windows flanked by wall cells.</li>
     *   <li><b>y=7..</b> — a mangrove gable roof (ridge along X) with closed gable
     *       ends.</li>
     *   <li><b>access</b> — a south-facing ladder on the hatch column climbing
     *       from the water (y=1) up onto the deck (y=3); hanging lanterns under
     *       the deck and on the porch eave give the elevated-hut glow.</li>
     * </ul>
     */
    private static Blueprint mangroveStiltHut() {
        Blueprint.Builder b = Blueprint.builder("Mangrove Stilt Hut", 7, 11, 7);
        Palette p = MANGROVE; // mangrove planks/logs/slabs/stairs, red bed, lantern
        BlueprintBlockState logY = p.logPillarY; // mangrove_log[axis=y]

        int x0 = 0, x1 = 6, z0 = 0, z1 = 6;
        int cx = (x0 + x1) / 2; // 3
        int cz = (z0 + z1) / 2; // 3
        int deckY = 3;          // raised platform (walkable surface = top of y=3)
        int wallBottom = 4;     // walls rise from above the deck
        int wallH = 6;          // wall plate (roof seats at y=7)
        int roofY = wallH + 1;  // 7

        // 1) water surface over the whole footprint — the hut stands "over water".
        //    WATER is structural matter (prints free), so the swamp reads true.
        floor(b, 0, x0, z0, x1, z1, WATER);

        // 2) cobble footing pads on the swamp bed under each stilt (mossy/plain mix),
        //    overwriting the water cell so the posts sit founded rather than floating.
        int[][] stilts = {
                {x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}, // corners
                {cx, z0}, {cx, z1}, {x0, cz}, {x1, cz}  // edge midpoints
        };
        for (int[] s : stilts) {
            b.set(s[0], 0, s[1], ((s[0] + s[1]) % 2 == 0) ? COBBLE : MOSSY_COBBLE); // footing pad
            pillar(b, s[0], s[1], 1, deckY - 1, logY); // mangrove-log stilt y=1..2
        }

        // 3) mangrove-plank deck at y=3 (the raised walkable floor), with a ladder
        //    hatch left OPEN at the south access column (cx, z1-1).
        int hatchX = cx, hatchZ = z1 - 1; // (3,5)
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (x == hatchX && z == hatchZ) continue; // ladder hatch — leave open
                b.set(x, deckY, z, p.plankFloor);
            }
        }

        // 4) mangrove-plank wall ring y=4..6 with mangrove-log corner posts
        walls(b, x0, z0, x1, z1, wallBottom, wallH, p.wall);
        corners(b, x0, z0, x1, z1, wallBottom, wallH, logY);

        // 5) north door (z=z0) opening inward (faces south) + render-safe windows.
        //    Each pane is flanked by wall cells along its wall axis → connects.
        door2(b, cx, wallBottom, z0, p.doorWood, "N");
        int wy = wallBottom + 1; // y=5, mid-wall
        window2(b, cx - 1, wy, z0, p.windowPane, null); // north, west of door
        window2(b, cx + 1, wy, z0, p.windowPane, null); // north, east of door
        window2(b, x0, wy, cz, p.windowPane, null);     // west wall, centred
        window2(b, x1, wy, cz, p.windowPane, null);     // east wall, centred

        // 6) mangrove gable roof (ridge along X) seated on the wall plate, closed ends.
        gableRoofX(b, x0, z0, x1, z1, roofY, p.roofStairName, p.roofSlab);
        gableEndFill(b, x0, z0, x1, z1, roofY, p.wall);

        // 7) ladder access: the south stilt column at (cx, z1) is a full mangrove-log
        //    backing post y=1..2, so a south-facing ladder on the hatch column
        //    (cx, z1-1) backs onto it and climbs from the water up to the deck.
        pillar(b, cx, z1, 1, deckY - 1, logY); // full backing post for the ladder rungs
        b.set(hatchX, 1, hatchZ, LADDER_SOUTH); // rung y=1 (at water level)
        b.set(hatchX, 2, hatchZ, LADDER_SOUTH); // rung y=2 → climb out onto deck at y=3

        // 8) hanging lanterns under the deck (the over-water glow), backed by the
        //    solid plank deck above them. Placed off the stilt cells so they hang free.
        b.set(cx - 1, deckY - 1, cz, HANGING_LANTERN);
        b.set(cx + 1, deckY - 1, cz, HANGING_LANTERN);

        // 9) interior furnishings on the raised deck (standing floor = y=4)
        bed(b, x0 + 1, wallBottom, z1 - 1, p.bedColor, "south"); // red bed at back
        b.set(x1 - 1, wallBottom, z1 - 1, CRAFTING_TABLE);
        b.set(x1 - 1, wallBottom, z0 + 1, CHEST);
        b.set(x0 + 1, wallBottom, z0 + 1, p.lightBlock);         // lantern, front corner
        b.set(cx, wallBottom, cz, p.lightBlock);                 // central lantern

        return b.build();
    }

    /**
     * Weathering selector for {@link #jungleTempleRuin}: deterministically picks a
     * mossy/cracked/plain stone variant from a cell's coordinates so the wall reads
     * as a mottled ruin rather than a uniform texture. Pure function of (a, y).
     */
    private static BlueprintBlockState weatheredWall(int a, int y, BlueprintBlockState mossy,
                                                     BlueprintBlockState cracked, BlueprintBlockState mossyBrick) {
        int k = (a * 3 + y * 7) % 5;
        if (k <= 1) return mossy;        // ~2/5 mossy cobble (overgrown)
        if (k == 2) return cracked;      // ~1/5 cracked brick (spalled)
        return mossyBrick;               // ~2/5 mossy stone brick
    }

    /**
     * Edge band for a pyramid step in {@link #jungleTempleRuin}: rings the outer edge
     * of the [x0..x1]×[z0..z1] block at height {@code y} with an alternating
     * mossy/cracked weathered band so each receding step reads as worn masonry.
     */
    private static void stepBand(Blueprint.Builder b, int y, int x0, int z0, int x1, int z1,
                                 BlueprintBlockState a, BlueprintBlockState bMat) {
        for (int x = x0; x <= x1; x++) {
            b.set(x, y, z0, ((x + y) % 2 == 0) ? a : bMat);
            b.set(x, y, z1, ((x + y) % 2 == 0) ? bMat : a);
        }
        for (int z = z0 + 1; z <= z1 - 1; z++) {
            b.set(x0, y, z, ((z + y) % 2 == 0) ? a : bMat);
            b.set(x1, y, z, ((z + y) % 2 == 0) ? bMat : a);
        }
    }

    /**
     * Cherry Blossom Pavilion (§3.A) — 9×9 footprint → builder(9, 11, 9). An OPEN,
     * pillared cherry shelter: a raised cherry-plank deck with a soft pink "petal"
     * read, eight stripped-cherry pillars (four corners + four mid-span posts), open
     * cherry-fence railings between them (with a front opening to walk in), a tiered
     * (telescoping, upturned-eave) cherry {@link #pagodaRoof}, and hanging lanterns
     * glowing under the eaves. No walls — it reads as an enterable open pavilion.
     *
     * <p>Petal/blossom look uses {@code pink_carpet} (dyed wool derivative →
     * normalizes to base cost, FU-valued) laid over the deck, NOT {@code pink_petals}
     * (a gate-flagged, unvalued block per the Phase 2 rules). The carpet sits at
     * {@code y=1} on top of the {@code y=0} cherry-plank deck and reads as scattered
     * fallen blossoms.
     *
     * <p>Geometry. Centre {@code (cx=4, cz=4)}. Pillars rise {@code y=1..3}; railings
     * sit on the deck at {@code y=1}; standing height ({@code y=1..3}) is left OPEN
     * inside per the air-skip rule so the shelter is walk-in. The roof seats its
     * lowest eave at {@code y=5} (its under-bracket course lands at {@code y=4}, one
     * clear course above the {@code y=3} pillar tops). A {@code baseHalf=3} bottom
     * tier reaches {@code cx±(3+1) = 0..8} — exactly the 9-wide footprint edge.
     */
    private static Blueprint cherryBlossomPavilion() {
        final int W = 9, H = 11;
        final int cx = 4, cz = 4;
        Blueprint.Builder b = Blueprint.builder("Cherry Blossom Pavilion", W, H, W);
        Palette p = CHERRY; // cherry planks/logs/slabs/stairs, pink bed, lantern
        BlueprintBlockState pillarPost = bs("minecraft:stripped_cherry_log[axis=y]");
        BlueprintBlockState railing = bs("minecraft:cherry_fence");
        BlueprintBlockState pinkCarpet = bs("minecraft:pink_carpet");
        BlueprintBlockState deckTrim = bs("minecraft:cherry_slab[type=top]");
        int x0 = 0, x1 = W - 1, z0 = 0, z1 = W - 1; // 0..8
        int pillarTop = 3;   // pillars y=1..3
        int roofY = 5;       // lowest eave; under-bracket lands at y=4 (above tops)

        // 1) cherry-plank deck at y=0 (the walkable surface = top of y=0).
        floor(b, 0, x0, z0, x1, z1, p.plankFloor);
        // A cherry-slab trim border one course up around the deck rim reads as a
        // raised lip; skip the front-centre cell so the entry stays flush/open.
        for (int x = x0; x <= x1; x++) {
            if (x != cx) b.set(x, 1, z0, deckTrim); // north rim (front), gap at entry
            b.set(x, 1, z1, deckTrim);              // south rim (back)
        }
        for (int z = z0 + 1; z <= z1 - 1; z++) {
            b.set(x0, 1, z, deckTrim);              // west rim
            b.set(x1, 1, z, deckTrim);              // east rim
        }

        // 2) eight stripped-cherry pillars: four corners + four mid-span posts.
        int[][] posts = {
                {x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}, // corners
                {cx, z0}, {cx, z1}, {x0, cz}, {x1, cz}  // edge midpoints
        };
        for (int[] q : posts) {
            pillar(b, q[0], q[1], 1, pillarTop, pillarPost);
        }

        // 3) open cherry-fence railings spanning between the pillars at y=1 (waist
        //    height). The deck-rim cells (y=1 slabs) are overwritten by the rail on
        //    the perimeter line so the railing reads continuous; the FRONT centre
        //    (the (cx, z0) bay either side of the front mid-post) is left open as the
        //    walk-in entrance. Fences self-reconcile their connections at print time.
        for (int x = x0; x <= x1; x++) {
            // front (north) rail: skip the two cells flanking centre → open entry bay
            if (x != cx - 1 && x != cx + 1) b.set(x, 1, z0, railing);
            b.set(x, 1, z1, railing); // back (south) rail, full
        }
        for (int z = z0 + 1; z <= z1 - 1; z++) {
            b.set(x0, 1, z, railing); // west rail
            b.set(x1, 1, z, railing); // east rail
        }
        // re-stamp the pillar bases (the rail loop overwrote them with fence)
        for (int[] q : posts) {
            b.set(q[0], 1, q[1], pillarPost);
        }

        // 4) soft pink-carpet "petal" scatter on the open deck interior (y=1). Carpet
        //    derives from wool → FU-valued; reads as fallen cherry blossoms. Kept off
        //    the perimeter rail line and clear of a central walking path.
        int[][] petals = {
                {cx - 1, cz - 1}, {cx + 1, cz - 1}, {cx - 1, cz + 1}, {cx + 1, cz + 1},
                {cx - 2, cz}, {cx + 2, cz}, {cx, cz - 2}, {cx, cz + 2}
        };
        for (int[] q : petals) {
            b.set(q[0], 1, q[1], pinkCarpet);
        }

        // 5) tiered upturned-eave cherry roof seated above the pillars (two tiers +
        //    finial). baseHalf=3 reaches the 9-wide footprint edge exactly.
        pagodaRoof(b, cx, cz, roofY, 3, 2, p.roofStairName, p.roofSlab, p.lightBlock);

        // 6) hanging lanterns under the eaves on chains from the lowest eave ring at
        //    the four mid-span pillars, so the pavilion glows. The eave ring sits at
        //    y=roofY (5); hang a chain at y=4 and the lantern at y=3 (pillar-top
        //    height), backed by the eave above.
        int[][] lanternBays = {{cx, z0}, {cx, z1}, {x0, cz}, {x1, cz}};
        for (int[] q : lanternBays) {
            // offset one cell inward from the rim so the lantern hangs in the bay,
            // not inside the pillar column.
            int lx = q[0] == x0 ? x0 + 1 : (q[0] == x1 ? x1 - 1 : q[0]);
            int lz = q[1] == z0 ? z0 + 1 : (q[1] == z1 ? z1 - 1 : q[1]);
            b.set(lx, 4, lz, CHAIN);
            b.set(lx, 3, lz, HANGING_LANTERN);
        }

        return b.build();
    }

    /**
     * Badlands Mesa Dwelling — a layered terracotta home built into a mesa cliff
     * face, with banded terracotta strata, a red-sandstone footing, dark-oak
     * accents, a stepped terracotta-slab roof, and a fenced balcony off the front.
     * 9×7 footprint (T5), disc T1.
     *
     * <p>Vanilla, FU-valued blocks only. Dyed terracotta all normalises to base
     * {@code terracotta} (FU-valued, T1); the cut/smooth/chiseled {@code
     * red_sandstone} variants derive from {@code red_sandstone} (FU-valued, T1) via
     * recipes (same as the desert build's sandstone family); dark-oak logs/planks/
     * fence/door/slab/stairs are all valued. No raw {@code red_sand} or {@code sand}
     * (those risk the unvalued/structural edge — the brief says swap them out), no
     * leaves/vines/bamboo.
     *
     * <p>The "built into the mesa" read comes from a TALL solid terracotta back wall
     * (the north z=0 face) standing two courses above the rest of the structure, as
     * if the dwelling is carved into the rising cliff; the banded strata (orange →
     * white → light-gray → red terracotta by y-course) echo the mesa's natural
     * sediment layers.
     *
     * <p>Section structure (footprint x=0..8 W, z=0..6 depth):
     * <ul>
     *   <li>{@code y=0} red-sandstone footing slab over the whole footprint (walkable
     *       ground = top of y=0); a cut-red-sandstone plinth ring one course up.</li>
     *   <li>{@code y=1} smooth-red-sandstone finish floor inside the plinth.</li>
     *   <li>{@code y=2..6} banded terracotta wall ring (one colour per course) with
     *       dark-oak corner posts; a north (front, z=0) dark-oak door opening inward,
     *       a south (z=6, balcony) door, and render-safe glass-pane windows flanked
     *       by wall cells.</li>
     *   <li>{@code y=7} stepped terracotta-slab roof over the body + a chiseled
     *       red-sandstone parapet course on the back/cliff edge.</li>
     *   <li>tall solid terracotta cliff-face back wall (z=0) rising to y=8 so the
     *       dwelling reads as set into a mesa scarp.</li>
     *   <li>a dark-oak-fenced balcony cantilevered off the south (z=6) wall, reached
     *       through the south door; furnished, enterable interior.</li>
     * </ul>
     */
    private static Blueprint badlandsMesaDwelling() {
        Blueprint.Builder b = Blueprint.builder("Badlands Mesa Dwelling", 9, 10, 7);
        Palette p = BADLANDS_TERRACOTTA; // terracotta walls, red bed → orange here
        // build-local materials (all vanilla, all FU-valued / recipe-derived)
        BlueprintBlockState redSand        = bs("minecraft:red_sandstone");          // T1 footing
        BlueprintBlockState cutRedSand     = bs("minecraft:cut_red_sandstone");      // plinth ring
        BlueprintBlockState smoothRedSand  = bs("minecraft:smooth_red_sandstone");   // finish floor / trim
        BlueprintBlockState chiseledRedSand= bs("minecraft:chiseled_red_sandstone"); // parapet
        BlueprintBlockState terracotta     = bs("minecraft:terracotta");             // base band
        BlueprintBlockState orangeTC       = bs("minecraft:orange_terracotta");
        BlueprintBlockState whiteTC        = bs("minecraft:white_terracotta");
        BlueprintBlockState lightGrayTC    = bs("minecraft:light_gray_terracotta");
        BlueprintBlockState redTC          = bs("minecraft:red_terracotta");
        BlueprintBlockState brownTC        = bs("minecraft:brown_terracotta");
        // NB: vanilla has NO terracotta slab — the stepped roof uses red-sandstone
        // top-slabs (derive from red_sandstone, FU-valued T1) which read as the next
        // mesa shelf; the recessed upper terrace stays solid terracotta.
        BlueprintBlockState roofSlabTop    = bs("minecraft:red_sandstone_slab[type=top]");
        BlueprintBlockState darkOakSlabTop = bs("minecraft:dark_oak_slab[type=top]");
        BlueprintBlockState darkOakPost    = bs("minecraft:dark_oak_log[axis=y]");
        BlueprintBlockState darkOakFence   = bs("minecraft:dark_oak_fence");

        // Body inset to z=1..5 (depth 5) so the balcony (z=6) and the cliff-face back
        // wall (z=0) fit inside the fixed 9×7 footprint. Walls span the full width.
        int x0 = 0, x1 = 8, z0 = 1, z1 = 5;
        int cliffZ = 0;        // the mesa-scarp back wall row (front of the build)
        int balconyZ = 6;      // cantilevered balcony deck row
        int floorY = 1;        // walkable finish floor on the red-sandstone plinth
        int wallBottom = 2;    // walls rise above the finish floor
        int wallH = 6;         // wall plate (roof seats at y=7)
        int roofY = wallH + 1; // 7
        int cx = (x0 + x1) / 2; // 4
        int cz = (z0 + z1) / 2; // 3

        // band colour by wall course (mesa sediment strata, low→high)
        BlueprintBlockState[] bands = {orangeTC, brownTC, whiteTC, lightGrayTC, redTC};

        // 1) red-sandstone footing over the whole body at y=0 (walkable ground)
        floor(b, 0, x0, z0, x1, z1, redSand);
        // 1b) cut-red-sandstone plinth ring one course up (y=1) — the masonry base
        walls(b, x0, z0, x1, z1, floorY, floorY, cutRedSand);
        // 1c) smooth-red-sandstone finish floor inside the plinth at y=1 (walkable)
        floor(b, floorY, x0 + 1, z0 + 1, x1 - 1, z1 - 1, smoothRedSand);

        // 2) banded terracotta wall ring y=2..6, a different terracotta colour per
        //    course (the mesa strata), with dark-oak corner posts over all courses.
        for (int y = wallBottom; y <= wallH; y++) {
            BlueprintBlockState band = bands[(y - wallBottom) % bands.length];
            walls(b, x0, z0, x1, z1, y, y, band);
        }
        corners(b, x0, z0, x1, z1, wallBottom, wallH, darkOakPost);
        // 2b) intermediate dark-oak studs every 3 cells down both long walls so the
        //     banding reads as framed panels.
        for (int x = x0 + 3; x <= x1 - 3; x += 3) {
            pillar(b, x, z0, wallBottom, wallH, darkOakPost);
            pillar(b, x, z1, wallBottom, wallH, darkOakPost);
        }

        // 3) doors: front (north z=z0) opening inward (south), and a south (z1) door
        //    onto the balcony opening inward (north). Re-stamp banding overwritten
        //    nothing — doors are 2-block states placed over the wall cells.
        door2(b, cx, wallBottom, z0, "dark_oak", "N");
        door2(b, cx, wallBottom, z1, "dark_oak", "S");

        // 4) glass-pane windows at a mid-wall course (each flanked by wall cells →
        //    render-safe). North wall flanks the door; long walls between studs.
        int wy = wallBottom + 2; // y=4
        window2(b, cx - 1, wy, z0, p.windowPane, smoothRedSand); // north, west of door (+ sill)
        window2(b, cx + 1, wy, z0, p.windowPane, smoothRedSand); // north, east of door (+ sill)
        window2(b, x0, wy, cz, p.windowPane, null);              // west long wall
        window2(b, x1, wy, cz, p.windowPane, null);              // east long wall
        window2(b, cx - 1, wy, z1, p.windowPane, null);          // south, west of balcony door
        window2(b, cx + 1, wy, z1, p.windowPane, null);          // south, east of balcony door

        // 5) stepped terracotta-slab roof over the body at y=roofY, then a recessed
        //    upper terrace one course higher on the back (cliff) half so the roofline
        //    steps UP toward the mesa scarp (the layered-mesa-home silhouette).
        flatRoof(b, roofY, x0, z0, x1, z1, roofSlabTop);
        // recessed upper step: a smaller terracotta-slab deck on the back half,
        // ringed by a chiseled-red-sandstone parapet (reads as the next sediment shelf)
        floor(b, roofY + 1, x0 + 1, z0, x1 - 1, cz, redTC);
        crenellate(b, roofY + 2, x0 + 1, z0, x1 - 1, cz, chiseledRedSand);

        // 6) the mesa-scarp BACK WALL (north z=cliffZ): a tall solid terracotta strata
        //    column standing two courses proud of the roof, so the dwelling reads as
        //    carved into a rising cliff. Banded by y like the walls.
        for (int y = 0; y <= roofY + 1; y++) {
            BlueprintBlockState band = y < wallBottom
                    ? cutRedSand
                    : bands[(y - wallBottom) % bands.length];
            for (int x = x0; x <= x1; x++) {
                b.set(x, y, cliffZ, band);
            }
        }
        // dark-oak corner posts framing the scarp face at the build edges
        pillar(b, x0, cliffZ, 0, roofY + 1, darkOakPost);
        pillar(b, x1, cliffZ, 0, roofY + 1, darkOakPost);

        // 7) dark-oak-fenced balcony cantilevered off the south wall (z1) onto z=6 at
        //    floor-plate height; reachable through the south door.
        for (int x = cx - 2; x <= cx + 2; x++) {
            b.set(x, wallBottom - 1, balconyZ, darkOakSlabTop); // deck slab (top = flush walk)
            b.set(x, wallBottom - 1, z1, darkOakSlabTop);       // bridge the wall→balcony gap
        }
        // dark-oak-fence railing along the open balcony edge. The deck is one row deep
        // (z=balconyZ; the z1 row is the under-wall bridge slab), so the railing is a
        // single run along the south/front + the two end cells at z=balconyZ. The
        // railings sit on the deck row ONLY — never on the wall row z1 — so they don't
        // punch fence-holes into the south wall; the deck stays walkable through the
        // south door over the solid bridge slab. Fences self-reconcile at print time.
        for (int x = cx - 2; x <= cx + 2; x++) {
            b.set(x, wallBottom, balconyZ, darkOakFence); // south + end rails (front edge)
        }

        // 8) interior furnishings on the walkable y=2 floor
        bed(b, x0 + 1, wallBottom, z1 - 1, p.bedColor, "south"); // orange bed at back
        b.set(x1 - 1, wallBottom, z1 - 1, CRAFTING_TABLE);
        b.set(x1 - 1, wallBottom, z0 + 1, CHEST);
        b.set(x0 + 1, wallBottom, z0 + 1, p.lightBlock);         // lantern, front corner
        b.set(cx, wallBottom, cz, p.lightBlock);                 // central lantern

        return b.build();
    }

    /**
     * Hobbit Hole. 9×9 footprint → builder(9, 9, 7). A hillside hobbit hole whose
     * IDENTITY is the round front facade: a big round dark-oak door set in a circular
     * stone-brick + cobblestone arch, flanked by two round glass windows, with a cozy
     * enterable room carved behind it. The grassy hill is NOT part of the build — the
     * player prints the facade + room into their own hillside, so there is NO earthen
     * mound, no grass_block/dirt/podzol/leaves/vines (all unvalued/allowlisted → they
     * would silently not print). Everything here is vanilla, FU-valued (recipe-derived
     * for the dark-oak wood family + door, hardcoded for stone_bricks/cobble/glass/
     * terracotta).
     *
     * <p>The signature round facade is a VERTICAL circle in the X–Y plane at the front
     * face (z=0). The library's {@link #disc}/{@link #circleRing} helpers work in the
     * horizontal X–Z plane, so the round arch is generated inline with the same radial
     * distance test ({@code dist ≤ r+0.5}) but oriented X–Y at fixed z — a faithful use
     * of the radial convention for a vertical facade. The arch is a solid stone-brick
     * disc (cobblestone ring rim) carved by the air-skip rule: the round door opening,
     * the two round windows, and the room interior are simply left unwritten.
     *
     * <p>Layout, footprint x=0..8 (W=9), z=0..6 (depth=7), facade centred at cx=4,
     * cy=4 (so the circle spans y=0..8, the full height):
     * <ul>
     *   <li><b>z=0 (front facade)</b> — a filled stone-brick disc (radius 4) ringed by a
     *       cobblestone rim ({@code circleRing} math), terracotta trim accents at the
     *       cardinal rim points. A big round dark-oak DOOR sits centred at the bottom
     *       of the circle (cx=4, y=1..2), framed by a dark-oak-log arch jamb. Two round
     *       GLASS windows (a 3-pane plus shape each) sit left/right of the door, each
     *       pane flanked by solid stone-brick disc cells on both horizontal sides →
     *       render-safe (no stub panes). A dark-oak-log lintel arches over the door.</li>
     *   <li><b>z=1..6 (the room behind)</b> — a stone-brick wall ring with dark-oak-log
     *       corner posts and a dark-oak plank back wall (z=6), so the room reads as a
     *       timber-lined burrow dug into the hill. Walkable dark-oak plank floor at y=1;
     *       a stone-brick + dark-oak plank low ceiling at y=6 closes the burrow. The
     *       interior (y=2..5) is left unwritten → open and enterable through the round
     *       door.</li>
     *   <li><b>furnishings</b> — a cozy hobbit interior on the y=1 floor: a red
     *       {@link #bed}, a bookshelf nook, a crafting table, a chest, a barrel pantry,
     *       and lanterns. A terracotta hearth band warms the back wall.</li>
     * </ul>
     */
    private static Blueprint hobbitHole() {
        Blueprint.Builder b = Blueprint.builder("Hobbit Hole", 9, 9, 7);
        // build-local materials (all vanilla, all FU-valued / recipe-derived)
        BlueprintBlockState stoneBrick   = STONE_BRICKS;
        BlueprintBlockState mossyBrick   = MOSSY_STONE_BRICKS;
        BlueprintBlockState cobble       = COBBLE;
        BlueprintBlockState darkOak      = DARK_OAK_PLANKS;
        BlueprintBlockState darkOakLogY  = bs("minecraft:dark_oak_log[axis=y]");
        BlueprintBlockState darkOakLogX  = bs("minecraft:dark_oak_log[axis=x]");
        BlueprintBlockState terracotta   = bs("minecraft:terracotta");
        BlueprintBlockState orangeTC     = bs("minecraft:orange_terracotta");
        BlueprintBlockState brownTC      = bs("minecraft:brown_terracotta");
        BlueprintBlockState glass        = GLASS;

        int x0 = 0, x1 = 8;           // facade/room width
        int z0 = 0;                   // front facade row (the round face)
        int z1 = 6;                   // back wall (carved into the hill)
        int cx = 4;                   // facade circle centre x
        int cy = 4;                   // facade circle centre y (circle spans y=0..8)
        int r  = 4;                   // facade radius
        int floorY = 1;               // walkable dark-oak floor (top of y=1 plank course)
        int ceilY  = 6;               // low burrow ceiling
        int cz = (z0 + z1) / 2;       // 3, room centre depth

        // ── 1) THE ROOM BEHIND (carved-into-hill burrow), z=1..z1 ────────────
        // Built FIRST so the round facade (z=0) is never overwritten by it. The
        // front of the room is the round facade itself (added in step 2), so the
        // room's floor/walls/ceiling start at z=1.
        // 1a) walkable dark-oak plank floor at y=floorY over z=1..z1
        floor(b, floorY, x0, z0 + 1, x1, z1, darkOak);
        // 1b) stone-brick side walls + dark-oak plank back wall, y=floorY+1..ceilY-1
        for (int y = floorY + 1; y <= ceilY - 1; y++) {
            line(b, y, x0, z0 + 1, x0, z1, stoneBrick); // west wall (x=0)
            line(b, y, x1, z0 + 1, x1, z1, stoneBrick); // east wall (x=8)
            line(b, y, x0, z1, x1, z1, darkOak);        // dark-oak plank back wall (z=6)
        }
        // dark-oak-log corner posts framing the burrow
        pillar(b, x0, z1, floorY + 1, ceilY - 1, darkOakLogY);
        pillar(b, x1, z1, floorY + 1, ceilY - 1, darkOakLogY);
        pillar(b, x0, z0 + 1, floorY + 1, ceilY - 1, darkOakLogY);
        pillar(b, x1, z0 + 1, floorY + 1, ceilY - 1, darkOakLogY);
        // 1c) low burrow ceiling at y=ceilY over z=1..z1, edged with a stone-brick rim
        floor(b, ceilY, x0, z0 + 1, x1, z1, darkOak);
        line(b, ceilY, x0, z1, x1, z1, stoneBrick);

        // ── 2) THE ROUND FRONT FACADE (vertical X–Y circle at z=0) ────────────
        // Solid stone-brick disc, radius r, centred (cx,cy). Same radial distance
        // test as disc()/circleRing() but oriented in the X–Y plane at fixed z.
        // This IS the round front wall; the square corners of the 9×9 front are
        // left as air (the facade reads round). The room floor (y=floorY) extends
        // its front lip into z=0 under the door so the threshold is walkable.
        for (int x = cx - r; x <= cx + r; x++) {
            for (int y = cy - r; y <= cy + r; y++) {
                if (y < 0) continue;                 // never below the print floor
                double d = Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy));
                if (d <= r + 0.5) {
                    // cobblestone rim on the outer ring, stone brick fill inside, a
                    // mossy-brick speckle for an old, settled-into-the-hill read.
                    BlueprintBlockState mat = (d > r - 0.5) ? cobble
                            : (((x + y) % 5 == 0) ? mossyBrick : stoneBrick);
                    b.set(x, y, z0, mat);
                }
            }
        }
        // terracotta trim accents at the four cardinal rim points (cottage colour)
        b.set(cx, cy + r, z0, orangeTC);     // top of the arch
        b.set(cx - r, cy, z0, brownTC);      // left rim
        b.set(cx + r, cy, z0, brownTC);      // right rim

        // ── 3) TWO ROUND GLASS WINDOWS flanking the door (set into the disc) ──
        // Each pane keeps a solid stone-brick disc cell as a horizontal neighbour
        // (left/right within the disc fill) → render-safe, no stub panes. Written
        // BEFORE the door so nothing overwrites the door. The windows sit at the
        // upper-mid of the disc, clear of the door jambs.
        int[] winCx = { cx - 2, cx + 2 };  // window centre columns (inside r=4 disc)
        int winCy = cy + 1;                // window centre height (upper-mid of circle)
        for (int wc : winCx) {
            // re-stamp the horizontal flankers solid FIRST (they may be cobble rim),
            // then punch the 2-tall round glass port between them → render-safe.
            b.set(wc - 1, winCy, z0, stoneBrick);
            b.set(wc + 1, winCy, z0, stoneBrick);
            b.set(wc - 1, winCy - 1, z0, stoneBrick);
            b.set(wc + 1, winCy - 1, z0, stoneBrick);
            b.set(wc, winCy, z0, glass);
            b.set(wc, winCy - 1, z0, glass);
        }

        // ── 4) THE BIG ROUND DARK-OAK DOOR (centred, bottom of the circle) ────
        // Written LAST so neither the disc fill nor the floor lip can overwrite it.
        // Dark-oak-log jambs frame the opening; an arched dark-oak lintel caps it.
        // The doorway threshold cell (cx, floorY, z0) gets a plank lip so the floor
        // is continuous from the round door into the burrow.
        b.set(cx, floorY, z0, darkOak);                  // (overwritten by door lower, but keeps lip if door fails)
        b.set(cx - 1, floorY, z0, darkOakLogY);          // left jamb (post)
        b.set(cx - 1, floorY + 1, z0, darkOakLogY);
        b.set(cx + 1, floorY, z0, darkOakLogY);          // right jamb (post)
        b.set(cx + 1, floorY + 1, z0, darkOakLogY);
        // arched dark-oak lintel hugging the circle just above the doorway
        b.set(cx - 1, floorY + 2, z0, darkOakLogX);
        b.set(cx, floorY + 2, z0, darkOak);
        b.set(cx + 1, floorY + 2, z0, darkOakLogX);
        // the round door itself: lower at y=floorY, upper at y=floorY+1 (opens inward)
        door2(b, cx, floorY, z0, "dark_oak", "N");

        // ── 5) terracotta hearth band warming the back wall (cottage trim) ────
        b.set(cx, floorY + 1, z1, terracotta);
        b.set(cx, floorY + 2, z1, orangeTC);
        b.set(cx - 1, floorY + 1, z1, brownTC);
        b.set(cx + 1, floorY + 1, z1, brownTC);

        // ── 6) cozy interior furnishings on the walkable y=floorY floor ──────
        bed(b, x0 + 1, floorY + 1, z1 - 1, "red", "north");  // bed against the back-left
        b.set(x1 - 1, floorY + 1, z1 - 1, BOOKSHELF);        // reading nook (back-right)
        b.set(x1 - 1, floorY + 1, z1 - 2, BOOKSHELF);
        b.set(x1 - 1, floorY + 1, z0 + 2, CRAFTING_TABLE);   // work corner (front-right)
        b.set(x0 + 1, floorY + 1, z0 + 2, CHEST);            // storage (front-left)
        b.set(x0 + 1, floorY + 1, z1 - 2, BARREL);           // pantry barrel
        b.set(x0 + 2, floorY + 1, z0 + 1, LANTERN);          // floor lantern, front
        b.set(x1 - 2, floorY + 1, z1 - 1, LANTERN);          // floor lantern, back
        // a hanging lantern in the centre, chained to the burrow ceiling
        chainLantern(b, cx, ceilY - 2, cz, 1);

        return b.build();
    }

    /**
     * Treehouse (Category A, §A row "treehouse") — 9×9 footprint → builder(9, 14, 9).
     * "WorldEdit for survival" reads this as an elevated tree-cabin, NOT a plain stilt
     * hut: a THICK 3×3 oak-LOG trunk rises from the ground up through a raised oak-plank
     * platform and continues above the cabin roof (the player's own leaf canopy crowns
     * the bare top); spruce-log branch struts splay out under the deck; a ladder climbs
     * the trunk to the platform; oak-fence railings ring the deck; a small walled oak
     * cabin with a gable roof sits on the platform around the trunk; hanging lanterns
     * glow under the eaves and the deck.
     *
     * <p><b>No leaves/vines/bamboo</b> (gate-flagged, unvalued → silently won't print) —
     * the canopy is the player's real tree. Everything here is VALUED oak/spruce wood,
     * planks, fences, ladders, glass panes, and lanterns.
     *
     * <p>Geometry. Footprint x,z=0..8. Trunk is the central 3×3 oak-log column
     * x=3..5 × z=3..5 from the ground (y=0) up to y=13 (it pierces the deck and the
     * cabin roof, reading as a continuing tree). Deck (raised oak-plank platform) at
     * y=4 over the full 9×9, minus the trunk cells and a south ladder hatch. Cabin wall
     * ring y=5..8 on a 7×7 inset (x=1..7 × z=1..7) with oak-log corner posts; the trunk
     * passes up through the cabin interior. Gable roof (ridge along X) seats at y=9 over
     * the cabin footprint, closed ends. The trunk top y=10..13 stands proud of the roof.
     * The interior standing space (deck-top = y=4, head room y=5..8) is left open per the
     * air-skip rule so the cabin is enterable off the ladder.
     */
    private static Blueprint treehouse() {
        Blueprint.Builder b = Blueprint.builder("Treehouse", 9, 14, 9);
        // all vanilla, all FU-valued: oak/spruce logs, oak planks/fence/slab/stairs,
        // ladders, glass panes, lanterns. NO leaves/vines/bamboo (unvalued, won't print).
        BlueprintBlockState trunkLog = OAK_LOG_Y;               // central tree trunk (axis=y)
        BlueprintBlockState branchX  = bs("minecraft:spruce_log[axis=x]"); // branch struts (E/W)
        BlueprintBlockState branchZ  = bs("minecraft:spruce_log[axis=z]"); // branch struts (N/S)
        BlueprintBlockState deck     = OAK_PLANKS;              // raised platform planks
        BlueprintBlockState wall     = OAK_PLANKS;              // cabin walls
        BlueprintBlockState cornerY  = OAK_LOG_Y;              // cabin corner posts
        BlueprintBlockState railing  = OAK_FENCE;              // deck railing

        int x0 = 0, x1 = 8, z0 = 0, z1 = 8;
        int cx = 4, cz = 4;                 // trunk / footprint centre
        int tx0 = 3, tx1 = 5, tz0 = 3, tz1 = 5; // 3×3 trunk columns
        int deckY = 4;                      // raised platform (walkable surface = top of y=4)
        int trunkTop = 13;                  // trunk stands proud above the roof
        // cabin: 7×7 inset on the deck
        int wx0 = 1, wx1 = 7, wz0 = 1, wz1 = 7;
        int wallBottom = deckY + 1;         // 5
        int wallH = deckY + 4;              // 8 (wall plate; roof seats at y=9)
        int roofY = wallH + 1;              // 9
        // Ladder climbs the SOUTH face of the trunk: a south-facing ladder backs onto
        // the block to its NORTH, so rungs sit at z=tz1+1 (=6, just south of the trunk)
        // and the deck hatch is the SAME column so the player steps straight up onto it.
        int ladderZ = tz1 + 1;               // 6
        int hatchX = cx, hatchZ = ladderZ;   // (4,6) hatch directly above the rungs

        // ── 1) THE TRUNK ─────────────────────────────────────────────────────
        // A solid 3×3 oak-log column from the ground (y=0) up to trunkTop. It passes
        // through the deck and the cabin and stands proud above the roof so the build
        // reads as a cabin built around a living tree.
        for (int x = tx0; x <= tx1; x++) {
            for (int z = tz0; z <= tz1; z++) {
                pillar(b, x, z, 0, trunkTop, trunkLog);
            }
        }

        // ── 2) BRANCH STRUTS under the deck ──────────────────────────────────
        // Spruce-log branches splay out from the trunk just below the platform
        // (y=deckY-1=3), reaching to the four mid-edges — they read as boughs and
        // visually carry the platform. Axis matches the direction each branch runs.
        int by = deckY - 1; // 3
        line(b, by, tx0 - 1, cz, x0, cz, branchX);  // west branch  (x: 2→0)
        line(b, by, tx1 + 1, cz, x1, cz, branchX);  // east branch  (x: 6→8)
        line(b, by, cx, tz0 - 1, cx, z0, branchZ);  // north branch (z: 2→0)
        // south branch routed via x=cx-1 so it never collides with the ladder column
        // (the ladder rungs occupy x=cx, z=ladderZ at this y); it still reads as a bough.
        line(b, by, cx - 1, tz1 + 1, cx - 1, z1, branchZ); // south branch (z: 6→8)
        // diagonal-ish corner boughs one step out from the trunk (single log knobs)
        b.set(tx0 - 1, by, tz0 - 1, branchX);
        b.set(tx1 + 1, by, tz0 - 1, branchX);
        b.set(tx0 - 1, by, tz1 + 1, branchX);
        b.set(tx1 + 1, by, tz1 + 1, branchX);

        // ── 3) THE RAISED PLATFORM (deck) at y=deckY ─────────────────────────
        // Full 9×9 oak-plank floor, MINUS the trunk cells (logs already there) and the
        // south ladder hatch (left open per air-skip so the player climbs onto the deck).
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (x >= tx0 && x <= tx1 && z >= tz0 && z <= tz1) continue; // trunk passes through
                if (x == hatchX && z == hatchZ) continue;                  // ladder hatch
                b.set(x, deckY, z, deck);
            }
        }

        // ── 4) DECK RAILINGS (oak fence around the platform edge) ────────────
        // A fence ring on the deck perimeter at y=deckY+? sits one course above the
        // floor (y=deckY+1) so it reads as a guard rail. Leave a gap at the hatch so
        // the rail doesn't cap the ladder opening.
        int railY = deckY + 1; // 5
        for (int x = x0; x <= x1; x++) {
            b.set(x, railY, z0, railing);
            if (x == cx) continue; // leave a gap on the south edge in front of the door
            b.set(x, railY, z1, railing);
        }
        for (int z = z0 + 1; z <= z1 - 1; z++) {
            b.set(x0, railY, z, railing);
            b.set(x1, railY, z, railing);
        }
        // (the cabin wall ring below re-stamps the inset perimeter; the OUTER railing
        //  ring on the 0/8 edges is the open-deck guard, distinct from the cabin walls.)

        // ── 5) THE CABIN on the platform ─────────────────────────────────────
        // 7×7 inset oak-plank wall ring y=5..8 with oak-log corner posts. The trunk
        // rises up through the cabin interior (y=5..8), so the cabin genuinely wraps
        // the tree. Interior (off the trunk) left open per air-skip → enterable.
        walls(b, wx0, wz0, wx1, wz1, wallBottom, wallH, wall);
        corners(b, wx0, wz0, wx1, wz1, wallBottom, wallH, cornerY);

        // 5a) south door into the cabin (faces north, opens inward), flanked by glass
        //     windows. Each pane is flanked by solid wall cells → render-safe.
        door2(b, cx, wallBottom, wz1, "oak", "S");
        int wy = wallBottom + 1; // 6, mid-wall
        window2(b, wx0, wy, cz, GLASS_PANE, null);     // west wall, centred
        window2(b, wx1, wy, cz, GLASS_PANE, null);     // east wall, centred
        window2(b, cx - 1, wy, wz0, GLASS_PANE, null); // north wall, west of centre
        window2(b, cx + 1, wy, wz0, GLASS_PANE, null); // north wall, east of centre

        // 5b) gable roof (ridge along X) seated on the wall plate, closed gable ends.
        gableRoofX(b, wx0, wz0, wx1, wz1, roofY, "oak_stairs", OAK_SLAB_BOTTOM);
        gableEndFill(b, wx0, wz0, wx1, wz1, roofY, wall);

        // ── 6) THE LADDER up the trunk to the deck ───────────────────────────
        // South-facing ladder rungs in the column just south of the trunk (x=cx, z=6)
        // climb from the ground (y=1) up to deck-top. A LADDER_SOUTH backs onto the
        // block to its NORTH — here the solid trunk log at (cx, y, tz1=5) — so every
        // rung has a wall behind it. The deck hatch at (cx, 6) (left open in step 3)
        // sits directly above, so the player climbs out onto the platform.
        for (int y = 1; y <= deckY; y++) {
            b.set(cx, y, ladderZ, LADDER_SOUTH); // rungs y=1..4 (top rung level with deck)
        }

        // ── 7) HANGING LANTERNS (under-deck glow + cabin interior) ───────────
        // Under-deck lanterns hang off the plank deck (solid above them) at the four
        // mid-edges, lighting the trunk base like a real treehouse at night.
        b.set(x0 + 2, deckY - 1, cz, HANGING_LANTERN);
        b.set(x1 - 2, deckY - 1, cz, HANGING_LANTERN);
        b.set(cx, deckY - 1, z0 + 2, HANGING_LANTERN);
        // a hanging lantern inside the cabin, chained up to the roof underside
        chainLantern(b, wx0 + 1, wallH - 1, wz0 + 1, 1);

        // ── 8) interior furnishings on the deck (standing floor = y=deckY) ───
        bed(b, wx0 + 1, wallBottom, wz1 - 1, "white", "south"); // bed at back-left
        b.set(wx1 - 1, wallBottom, wz1 - 1, CHEST);             // storage, back-right
        b.set(wx1 - 1, wallBottom, wz0 + 1, CRAFTING_TABLE);    // work corner, front-right
        b.set(wx0 + 1, wallBottom, wz0 + 1, LANTERN);           // floor lantern, front-left

        return b.build();
    }

    /**
     * §F.iron_farm — a STATIC iron-farm shell, 15×15×12 (W×L×H) → builder(15,12,15).
     *
     * <p>The #1-most-requested build. This is the printable STRUCTURE only — beds,
     * water/lava, hoppers, chests, glass, stone, slabs and signs. Mobs are never
     * captured, so the player drops 3 villagers into the raised pods after printing
     * and the golems then spawn, drop through the lava blade, and wash to the hopper
     * chest. Everything here is a vanilla FU-valued or structural-free block.
     *
     * <p>Layout (south = +z is the "front"):
     * <ul>
     *   <li><b>y=0</b> — solid stone foundation (15×15).</li>
     *   <li><b>Collection chamber, y=1..3</b> — a stone-walled room with a 3×3 hopper
     *       floor feeding a central collection chest; four water source blocks flush
     *       golem drops inward. Glass viewing window (panes backed by stone → render
     *       safe) on the south face; iron-bar grates flank the door opening.</li>
     *   <li><b>Lava blade, y=4</b> — a 3×3 lava sheet on a stone-slab tray directly
     *       above the chamber: the kill floor the golems drop onto. Lava is structural
     *       (prints free). Stone walls box it so the blade can't spill.</li>
     *   <li><b>Spawn platform, y=5</b> — a stone slab platform the golems spawn on.</li>
     *   <li><b>Villager pods, y=6..8</b> — three glass-walled cells along the back
     *       (high-z) wall, each holding a bed, separated by stone piers. Player adds
     *       one villager per pod after printing.</li>
     *   <li><b>Glass perimeter + slab roof, y=5..9 / y=10</b> — encloses the pod deck
     *       so spawns are contained; every glass pane is backed by a solid neighbour.</li>
     * </ul>
     */
    private static Blueprint ironFarm() {
        Blueprint.Builder b = Blueprint.builder("Iron Farm", 15, 12, 15);
        // all vanilla, all FU-valued / structural-free:
        BlueprintBlockState stone   = bs("minecraft:stone");
        BlueprintBlockState cobble  = COBBLE;
        BlueprintBlockState glass   = GLASS;             // solid glass for walls (always renders)
        BlueprintBlockState pane    = GLASS_PANE;        // panes only where backed by a solid neighbour
        BlueprintBlockState bars    = IRON_BARS;         // grates, only where backed by a solid neighbour
        BlueprintBlockState slabTop = SMOOTH_STONE_SLAB_TOP;
        BlueprintBlockState slabBot = bs("minecraft:smooth_stone_slab[type=bottom]");
        BlueprintBlockState chest   = bs("minecraft:chest[facing=south,type=single,waterlogged=false]");
        BlueprintBlockState water   = WATER;             // structural (asItem()==AIR) → prints free
        BlueprintBlockState lava    = LAVA;              // structural → prints free

        int x0 = 0, x1 = 14, z0 = 0, z1 = 14;            // 15×15 footprint
        int cx = 7, cz = 7;                              // centre column

        // ── 1) STONE FOUNDATION at y=0 ───────────────────────────────────────
        floor(b, 0, x0, z0, x1, z1, stone);

        // ── 2) COLLECTION CHAMBER (the golem-drop sump), y=1..3 ──────────────
        // A 7×7 stone room centred on (cx,cz). Its floor is the foundation (y=0);
        // a recessed 3×3 hopper grid at y=1 feeds a central collection chest.
        int chx0 = cx - 3, chx1 = cx + 3, chz0 = cz - 3, chz1 = cz + 3; // 4..10
        // 2a) stone wall ring of the chamber, y=1..3, interior left open (enterable)
        walls(b, chx0, chz0, chx1, chz1, 1, 3, stone);
        // 2b) collection chest at the centre, ringed by hoppers that feed INTO it.
        //     The 4 orthogonally-adjacent hoppers face the chest; the 4 diagonals
        //     face an adjacent hopper so the whole ring funnels to the chest.
        b.set(cx, 1, cz, chest);
        b.set(cx - 1, 1, cz, bs("minecraft:hopper[enabled=true,facing=east]"));   // → chest
        b.set(cx + 1, 1, cz, bs("minecraft:hopper[enabled=true,facing=west]"));   // → chest
        b.set(cx, 1, cz - 1, bs("minecraft:hopper[enabled=true,facing=south]"));  // → chest
        b.set(cx, 1, cz + 1, bs("minecraft:hopper[enabled=true,facing=north]"));  // → chest
        b.set(cx - 1, 1, cz - 1, bs("minecraft:hopper[enabled=true,facing=east]"));   // → W hopper
        b.set(cx + 1, 1, cz - 1, bs("minecraft:hopper[enabled=true,facing=west]"));   // → E hopper
        b.set(cx - 1, 1, cz + 1, bs("minecraft:hopper[enabled=true,facing=east]"));   // → W hopper
        b.set(cx + 1, 1, cz + 1, bs("minecraft:hopper[enabled=true,facing=west]"));   // → E hopper
        // 2c) four water sources at the chamber-floor edges (y=1) flush drops inward.
        //     Sources sit against the inner wall faces; the flow carries items to the
        //     hopper ring. (Static capture — the player re-pushes if a source drifts.)
        b.set(chx0 + 1, 1, cz, water);                   // west inflow
        b.set(chx1 - 1, 1, cz, water);                   // east inflow
        b.set(cx, 1, chz0 + 1, water);                   // north inflow
        b.set(cx, 1, chz1 - 1, water);                   // south inflow
        // 2d) south-face viewing window: a row of glass panes at y=2, each flanked
        //     left/right by the stone wall run → render-safe (connects along X).
        for (int x = cx - 1; x <= cx + 1; x++) {
            b.set(x, 2, chz1, pane);                      // panes punched into the south wall
        }
        // 2e) iron-bar grate slits on the side walls (y=2), each backed by the stone
        //     corner posts to either side along Z → render-safe.
        b.set(chx0, 2, cz, bars);                         // west wall slit (backed by stone N/S)
        b.set(chx1, 2, cz, bars);                         // east wall slit
        // 2f) ceiling over the chamber at y=4 is the LAVA TRAY (see step 3); the rest
        //     of the chamber top is stone so the blade is contained.
        floor(b, 4, chx0, chz0, chx1, chz1, stone);

        // ── 3) LAVA BLADE (the kill floor), y=4 over the centre 3×3 ──────────
        // A stone-slab tray rim already laid as the y=4 stone ceiling; punch a 3×3
        // hole filled with a lava sheet directly above the hopper ring so golems that
        // spawn above drop onto the blade and their drops fall through to the hoppers.
        // (Air-skip means we just OVERWRITE the 3×3 stone ceiling cells with lava.)
        for (int x = cx - 1; x <= cx + 1; x++) {
            for (int z = cz - 1; z <= cz + 1; z++) {
                b.set(x, 4, z, lava);
            }
        }
        // a smooth-stone-slab lip framing the blade reads as the classic lava tray
        for (int x = cx - 2; x <= cx + 2; x++) {
            b.set(x, 4, cz - 2, slabTop);
            b.set(x, 4, cz + 2, slabTop);
        }
        for (int z = cz - 2; z <= cz + 2; z++) {
            b.set(cx - 2, 4, z, slabTop);
            b.set(cx + 2, 4, z, slabTop);
        }

        // ── 4) SPAWN PLATFORM at y=5 ─────────────────────────────────────────
        // A full 15×15 smooth-stone platform: the lit deck golems spawn on. Centre
        // 3×3 left OPEN (air-skip) as the drop-hole down onto the lava blade.
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (x >= cx - 1 && x <= cx + 1 && z >= cz - 1 && z <= cz + 1) continue; // drop hole
                b.set(x, 5, z, slabBot);
            }
        }

        // ── 5) PERIMETER WALLS y=6..9 (glass over a cobble base course) ──────
        // A cobble plinth course at y=6 gives every y=7 glass pane a solid block
        // beneath, and the solid GLASS blocks above always render; the only PANES
        // are the door-flanking grates in step 6 (backed by cobble/stone).
        line(b, 6, x0, z0, x1, z0, cobble); line(b, 6, x0, z1, x1, z1, cobble);
        line(b, 6, x0, z0, x0, z1, cobble); line(b, 6, x1, z0, x1, z1, cobble);
        for (int y = 7; y <= 9; y++) {
            line(b, y, x0, z0, x1, z0, glass); line(b, y, x0, z1, x1, z1, glass);
            line(b, y, x0, z0, x0, z1, glass); line(b, y, x1, z0, x1, z1, glass);
        }
        // cobble corner posts tie the glass cage together
        corners(b, x0, z0, x1, z1, 6, 9, cobble);
        // NORTH wall (z=z0, opposite the pods): iron-bar grate slits worked into the
        // glass at y=7, each flanked along X by the glass wall run → render-safe.
        // (Overwrites two glass cells with bars; reads as the access/control face.)
        b.set(cx - 1, 7, z0, bars);
        b.set(cx + 1, 7, z0, bars);

        // ── 6) VILLAGER PODS along the back (high-z) wall, y=6..8 ─────────────
        // Three glass-fronted cells, each a 3-wide bay with a bed, separated by stone
        // piers. The player drops one villager per pod after printing (mobs aren't
        // captured). Pods sit on the y=5 platform; their floor is that platform.
        // The bay is 2 cells deep: back row z=13 (against the z=14 perimeter glass) and
        // front row z=12. The glass FRONT of each pod sits at z=11 so the bay interior
        // (z=12..13) is free for the 2-block bed; piers run z=11..14 to box each cell.
        int podBack = z1 - 1;                    // 13, bed-head row (against back glass)
        int podFront = z1 - 3;                   // 11, glass front of the pods (faces -z)
        int[] podCx = { 3, 7, 11 };              // the three pod centres in X
        for (int pc : podCx) {
            // stone piers either side of the bay, y=6..8, from the front glass to back
            for (int z = podFront; z <= z1; z++) {
                pillar(b, pc - 2, z, 6, 8, stone);
                pillar(b, pc + 2, z, 6, 8, stone);
            }
            // glass front of the bay at y=6..8 (solid glass → always renders)
            for (int y = 6; y <= 8; y++) {
                for (int x = pc - 1; x <= pc + 1; x++) {
                    b.set(x, y, podFront, glass);
                }
            }
            // a bed inside the bay: head at z=13 (back), foot at z=12 → facing=south
            // (bed() puts the foot one step OPPOSITE facing; south → foot at z-1=12).
            bed(b, pc, 6, podBack, "white", "south");
        }

        // ── 7) SLAB ROOF at y=10 ─────────────────────────────────────────────
        // Caps the glass cage so spawns are enclosed; smooth-stone top slabs.
        floor(b, 10, x0, z0, x1, z1, slabTop);

        // ── 8) LABEL SIGNS on the NORTH platform, y=6 ────────────────────────
        // Standing oak signs (FU-valued, recipe-derived) flanking the north control
        // wall, on the open y=5 platform (z=1, clear of the chamber ring at z=4..10).
        b.set(cx - 2, 6, z0 + 1, bs("minecraft:oak_sign[rotation=0]"));
        b.set(cx + 2, 6, z0 + 1, bs("minecraft:oak_sign[rotation=0]"));

        return b.build();
    }

    /**
     * §F.mob_xp_tower — a STATIC dark-spawner / XP-farm tower, 9×9×24 (W×L×H)
     * → builder(9, 24, 9).
     *
     * <p>The classic "how do I build a mob farm" build, printed as the STRUCTURE
     * only: dark stone/deepslate shell, water channels, a central drop shaft, a
     * fall chamber, and a hopper+chest collection floor with a player kill slot.
     * Nothing alive is captured — after printing, hostile mobs spawn naturally on
     * the unlit top platform (the static shell keeps it dark), get pushed by the
     * water channels into the central drop hole, fall the height of the shaft, and
     * land in the collection sump where the player kills them through the slot and
     * the hopper ring sweeps drops into the chest. Every block is a vanilla
     * FU-valued block or structural-free matter (water).
     *
     * <p>Layout (south = +z is the "front", where the player stands; cx=cz=4):
     * <ul>
     *   <li><b>y=0</b> — solid stone foundation (9×9).</li>
     *   <li><b>Collection sump, y=1..4</b> — a deepslate-walled room. A central
     *       collection chest is ringed by 4 hoppers that feed into it; four water
     *       sources at the edges wash drops onto the hopper ring. The SOUTH face
     *       carries a glass-pane viewing window (panes backed along X by the wall →
     *       render-safe) and, one cell up, an iron-bar KILL SLOT the player hits
     *       mobs through (backed left/right by deepslate → render-safe). The sump
     *       ceiling (y=4) is solid except the central 3×3, which is the bottom of
     *       the drop shaft so fallen mobs land on the hopper ring.</li>
     *   <li><b>Drop shaft / fall chamber, y=5..18</b> — a solid stone tube whose
     *       inner 3×3 is hollow: the fall column (≈14 blocks of free fall ≫ the
     *       ~23 needed to soften, but tall enough to read as a tower and stack with
     *       the sump drop). The tube walls keep mobs in the chute as they fall.</li>
     *   <li><b>Spawn platform, y=19</b> — a 7×7 deepslate-slab floor (bottom slabs
     *       give a solid, mob-spawnable surface) with the central 3×3 left OPEN as
     *       the drop hole. Water sources at the four mid-edges flow inward toward
     *       the hole, pushing spawned mobs off the platform and down the shaft.</li>
     *   <li><b>Spawn chamber + dark roof, y=20..23</b> — stone walls box the
     *       platform and a solid stone slab roof at y=23 seals out skylight so the
     *       chamber stays dark and mobs spawn. Oak wall signs label the build.</li>
     * </ul>
     */
    private static Blueprint mobXpTower() {
        Blueprint.Builder b = Blueprint.builder("Mob XP Tower", 9, 24, 9);
        // all vanilla, all FU-valued / structural-free:
        BlueprintBlockState stone     = bs("minecraft:stone");
        BlueprintBlockState deepslate = bs("minecraft:deepslate[axis=y]");
        BlueprintBlockState cobble    = COBBLE;
        BlueprintBlockState pane      = GLASS_PANE;   // only where backed by a solid neighbour
        BlueprintBlockState bars      = IRON_BARS;    // kill slot, only where backed by a solid neighbour
        BlueprintBlockState slabBot   = bs("minecraft:deepslate_tile_slab[type=bottom]"); // spawn surface
        BlueprintBlockState slabRoof  = bs("minecraft:stone_slab[type=top]");
        BlueprintBlockState chest     = bs("minecraft:chest[facing=south,type=single,waterlogged=false]");
        BlueprintBlockState water     = WATER;        // structural (asItem()==AIR) → prints free

        int x0 = 0, x1 = 8, z0 = 0, z1 = 8;           // 9×9 footprint
        int cx = 4, cz = 4;                           // centre column (drop shaft)

        // ── 1) STONE FOUNDATION at y=0 ───────────────────────────────────────
        floor(b, 0, x0, z0, x1, z1, stone);

        // ── 2) COLLECTION SUMP, y=1..4 (deepslate-walled drop landing) ───────
        // A 9×9 deepslate wall ring, interior open so the player can enter and
        // stand at the kill slot. Hopper ring + chest at the centre; water washes
        // drops onto the hoppers.
        walls(b, x0, z0, x1, z1, 1, 3, deepslate);
        // 2a) central collection chest, ringed by 4 hoppers that feed INTO it.
        b.set(cx, 1, cz, chest);
        b.set(cx - 1, 1, cz, bs("minecraft:hopper[enabled=true,facing=east]"));   // → chest
        b.set(cx + 1, 1, cz, bs("minecraft:hopper[enabled=true,facing=west]"));   // → chest
        b.set(cx, 1, cz - 1, bs("minecraft:hopper[enabled=true,facing=south]"));  // → chest
        b.set(cx, 1, cz + 1, bs("minecraft:hopper[enabled=true,facing=north]"));  // → chest
        // 2b) four water sources at the sump-floor edges (y=1) wash drops inward.
        b.set(x0 + 1, 1, cz, water);                  // west inflow
        b.set(x1 - 1, 1, cz, water);                  // east inflow
        b.set(cx, 1, z0 + 1, water);                  // north inflow
        b.set(cx, 1, z1 - 1, water);                  // south inflow
        // 2c) SOUTH-face viewing window: a row of glass panes at y=2, each flanked
        //     left/right by the deepslate wall run → render-safe (connects along X).
        for (int x = cx - 1; x <= cx + 1; x++) {
            b.set(x, 2, z1, pane);
        }
        // 2d) KILL SLOT: an iron-bar slit at y=3 on the south wall centre, flanked
        //     by deepslate along X → render-safe. The player stands outside (+z)
        //     and hits mobs piled in the sump through the bars.
        b.set(cx, 3, z1, bars);
        // 2e) SUMP CEILING at y=4: solid deepslate EXCEPT the central 3×3, left
        //     OPEN (air-skip) as the bottom of the drop shaft so falling mobs land
        //     on the hopper ring below.
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (x >= cx - 1 && x <= cx + 1 && z >= cz - 1 && z <= cz + 1) continue; // shaft mouth
                b.set(x, 4, z, deepslate);
            }
        }

        // ── 3) DROP SHAFT / FALL CHAMBER, y=5..18 ────────────────────────────
        // A solid stone tube (5×5 outer) whose inner 3×3 is hollow: the fall
        // column. The tube walls keep mobs in the chute the whole way down.
        int sx0 = cx - 2, sx1 = cx + 2, sz0 = cz - 2, sz1 = cz + 2; // 2..6 (5×5)
        for (int y = 5; y <= 18; y++) {
            // outer ring of the tube (the inner 3×3 stays air → the fall column)
            line(b, y, sx0, sz0, sx1, sz0, stone); // north
            line(b, y, sx0, sz1, sx1, sz1, stone); // south
            line(b, y, sx0, sz0, sx0, sz1, stone); // west
            line(b, y, sx1, sz0, sx1, sz1, stone); // east
        }

        // ── 4) SPAWN PLATFORM at y=19 ────────────────────────────────────────
        // A 7×7 deepslate-tile-slab floor (bottom slabs = solid spawnable surface)
        // with the central 3×3 left OPEN as the drop hole. Water at the four
        // mid-edges flows toward the hole and pushes spawned mobs in.
        int px0 = x0 + 1, px1 = x1 - 1, pz0 = z0 + 1, pz1 = z1 - 1; // 1..7 (7×7)
        for (int x = px0; x <= px1; x++) {
            for (int z = pz0; z <= pz1; z++) {
                if (x >= cx - 1 && x <= cx + 1 && z >= cz - 1 && z <= cz + 1) continue; // drop hole
                b.set(x, 19, z, slabBot);
            }
        }
        // water channels: a source at each mid-edge of the platform, flowing inward.
        b.set(px0, 19, cz, water);                    // west channel
        b.set(px1, 19, cz, water);                    // east channel
        b.set(cx, 19, pz0, water);                    // north channel
        b.set(cx, 19, pz1, water);                    // south channel

        // ── 5) SPAWN CHAMBER WALLS y=20..22 + DARK ROOF y=23 ─────────────────
        // Stone walls box the platform; a cobble corner course ties them. The
        // y=23 stone-slab roof seals out skylight so the chamber stays dark and
        // hostile mobs spawn on the platform after printing.
        walls(b, x0, z0, x1, z1, 20, 22, stone);
        corners(b, x0, z0, x1, z1, 20, 22, cobble);
        floor(b, 23, x0, z0, x1, z1, slabRoof);

        // ── 6) LABEL SIGNS on the SOUTH sump face, y=2 ───────────────────────
        // Oak wall signs flanking the viewing window (FU-valued, recipe-derived),
        // mounted on the outside of the south wall (facing=south, +z).
        b.set(cx - 2, 2, z1, bs("minecraft:oak_wall_sign[facing=south]"));
        b.set(cx + 2, 2, z1, bs("minecraft:oak_wall_sign[facing=south]"));

        return b.build();
    }

    /**
     * §F.sugarcane_farm_auto — a STATIC automatic sugar-cane farm, 9×9×5 (W×L×H)
     * → builder(9, 5, 9).
     *
     * <p>The "Tier-1 must-build" auto sugar-cane farm, printed as the working
     * STRUCTURE: two planting strips of sand straddling a central water channel,
     * a row of sugar cane on each strip, an observer + piston harvest wall behind
     * each strip, redstone dust tying the observers to the pistons, and a hopper
     * line under the water that sweeps the broken cane into a collection chest.
     * Every block is a vanilla FU-valued block (sand, sugar_cane, observer, piston,
     * redstone, hopper, chest all derive) or structural-free matter (water, redstone
     * wire). The mechanism reproduces faithfully and "works" once printed: cane grows
     * to height 2, the observer beside the upper block detects the growth pulse,
     * fires the piston that breaks the top cane, and the item washes down the water
     * channel into the hoppers → chest.
     *
     * <p>Layout (south = +z is the "front"/access side; cx=cz=4):
     * <ul>
     *   <li><b>y=0</b> — solid stone foundation (9×9), with a central <b>water
     *       channel</b> punched along Z at x=4 (z=1..7): the flow that carries
     *       cut cane south to the hopper mouth.</li>
     *   <li><b>Hopper line + chest, y=0</b> — at the south end of the channel a
     *       hopper line (x=4, z=6..7) feeds a collection chest tucked behind the
     *       south wall, so every item the channel delivers is collected.</li>
     *   <li><b>Planting strips, y=1</b> — two rows of <b>sand</b> (x=3 and x=5,
     *       z=1..7) flanking the channel, each within one block of water → always
     *       hydrated. <b>Sugar cane</b> sits on every sand cell (y=2), the bottom
     *       course of each plant.</li>
     *   <li><b>Harvest wall, y=2..4</b> — behind each strip an <b>observer</b> at
     *       y=2 (x=2 west / x=6 east) faces the cane, watching the cell the second
     *       cane block grows into; a <b>piston</b> at y=3 above it faces the cane
     *       top and breaks the grown block. One column further out (x=1 / x=7) a
     *       stone shelf carries a <b>redstone dust</b> ribbon at y=4 tying each
     *       observer's back output to its piston.</li>
     *   <li><b>Side walls + label signs</b> — stone end walls (z=0 and z=8) box the
     *       channel; oak wall signs on the south face label the build.</li>
     * </ul>
     */
    private static Blueprint sugarcaneFarmAuto() {
        Blueprint.Builder b = Blueprint.builder("Auto Sugar Cane Farm", 9, 5, 9);
        // all vanilla, all FU-valued / structural-free:
        BlueprintBlockState stone   = bs("minecraft:stone");
        BlueprintBlockState cobble  = COBBLE;
        BlueprintBlockState sand    = bs("minecraft:sand");                 // FU-valued (sandstone derivation)
        BlueprintBlockState cane    = bs("minecraft:sugar_cane[age=0]");    // FU-valued (=2@1); BushBlock structural too
        BlueprintBlockState water   = WATER;                               // structural (asItem()==AIR) → prints free
        BlueprintBlockState chest   = bs("minecraft:chest[facing=north,type=single,waterlogged=false]");
        BlueprintBlockState redDust = bs("minecraft:redstone_wire[east=none,west=none,north=none,south=none,power=0]"); // structural

        int x0 = 0, x1 = 8, z0 = 0, z1 = 8;            // 9×9 footprint
        int cx = 4;                                    // central water-channel column
        int stripZ0 = 1, stripZ1 = 7;                  // planting / channel run along Z

        // ── 1) STONE FOUNDATION at y=0, with the central WATER CHANNEL ───────
        floor(b, 0, x0, z0, x1, z1, stone);
        // central channel: water along Z at x=cx (z=1..6); the cut cane floats south.
        for (int z = stripZ0; z <= stripZ1 - 1; z++) {
            b.set(cx, 0, z, water);
        }

        // ── 2) HOPPER LINE + COLLECTION CHEST at the SOUTH end, y=0 ──────────
        // The channel terminates over a hopper that feeds the chest. The hopper
        // mouth (z=7) catches what the flow delivers; it points north into the
        // chest tucked at the south edge (z=8), facing north so its front reads
        // inward. (Air-skip means these overwrite the stone foundation cells.)
        b.set(cx, 0, stripZ1, bs("minecraft:hopper[enabled=true,facing=north]")); // z=7 → feeds chest at z=8
        b.set(cx, 0, z1, chest);                                                   // collection chest, faces north

        // ── 3) PLANTING STRIPS (sand) + SUGAR CANE, flanking the channel ─────
        // Two sand rows at x=cx-1 and x=cx+1 (3 and 5), each one block from water →
        // hydrated. Cane sits on every sand cell (the bottom course of each plant).
        int wStripX = cx - 1, eStripX = cx + 1;        // 3 and 5
        for (int z = stripZ0; z <= stripZ1; z++) {
            b.set(wStripX, 1, z, sand);
            b.set(eStripX, 1, z, sand);
            b.set(wStripX, 2, z, cane);                // cane bottom block, west strip
            b.set(eStripX, 2, z, cane);                // cane bottom block, east strip
        }

        // ── 4) HARVEST WALL: stone backing + observer + piston + redstone ────
        // Behind each strip a stone pillar per cane carries the observer (y=2,
        // facing the cane, watching the cell the 2nd cane block grows into) and the
        // piston (y=3, facing the cane top, which it breaks). Redstone dust on the
        // y=3 stone wall ties observer-back → piston.
        // The harvest wall is two columns DEEPER than the strips. The observer sits
        // at the strip-back column (x=wWallX/eWallX), the piston at y=3 above it
        // faces the cane top, and the redstone dust runs on a stone shelf one column
        // further out (x=wShelfX/eShelfX) so it has a solid y=3 floor under it (the
        // shelf top) and ties each observer's back output to its piston.
        int wWallX = cx - 2, eWallX = cx + 2;          // 2 and 6 (observer/piston columns)
        int wShelfX = cx - 3, eShelfX = cx + 3;        // 1 and 7 (redstone-dust shelf columns)
        for (int z = stripZ0; z <= stripZ1; z++) {
            // observer mount: stone block at y=1, observer at y=2 facing the cane,
            // piston at y=3 facing the cane top (breaks the grown 2nd block toward
            // the channel). West wall faces east, east wall faces west.
            b.set(wWallX, 1, z, stone);
            b.set(eWallX, 1, z, stone);
            b.set(wWallX, 2, z, bs("minecraft:observer[facing=east,powered=false]"));
            b.set(eWallX, 2, z, bs("minecraft:observer[facing=west,powered=false]"));
            b.set(wWallX, 3, z, bs("minecraft:piston[facing=east,extended=false]"));
            b.set(eWallX, 3, z, bs("minecraft:piston[facing=west,extended=false]"));
            // redstone-dust shelf: solid stone y=1..3 with a dust ribbon at y=4 on top,
            // carrying the observer-back signal across to the piston.
            pillar(b, wShelfX, z, 1, 3, stone);
            pillar(b, eShelfX, z, 1, 3, stone);
            b.set(wShelfX, 4, z, redDust);
            b.set(eShelfX, 4, z, redDust);
        }

        // ── 5) END WALLS (box the channel) + LABEL SIGNS ─────────────────────
        // Cobble end caps at z=0 and z=8 across the planting/channel span close the
        // ends so the water channel reads as a contained trough.
        line(b, 1, wWallX, z0, eWallX, z0, cobble);   // north end cap, y=1
        line(b, 1, wWallX, z1, eWallX, z1, cobble);   // south end cap, y=1
        // oak wall signs on the south face flanking the chest (FU-valued, derived).
        b.set(wStripX, 1, z1, bs("minecraft:oak_wall_sign[facing=south]"));
        b.set(eStripX, 1, z1, bs("minecraft:oak_wall_sign[facing=south]"));

        return b.build();
    }

    /**
     * §F.pumpkin_melon_farm — a STATIC automatic pumpkin/melon farm, 9×9×5 (W×L×H)
     * → builder(9, 5, 9).
     *
     * <p>The "villager-trade staple" auto stem farm, printed as the working
     * STRUCTURE. Pumpkin/melon stems are {@link net.minecraft.world.level.block.BushBlock}
     * descendants, so — like crops and saplings — they're <b>structural-free</b> and
     * print at no FU cost; their item is a seed, never the grown stem. We do NOT place
     * any pumpkin/melon <em>produce</em> blocks (those would need an FU value and aren't
     * how the farm works): the stems grow their own pumpkins/melons onto the bare
     * <b>dirt grow-spaces</b> once printed, an observer beside each space detects the
     * new block, and a piston shoves it onto the water channel that sweeps it into the
     * collection chest.
     *
     * <p>Layout (south = +z is the "front"/access side; cx=4):
     * <ul>
     *   <li><b>y=0</b> — solid stone foundation (9×9). A <b>water channel</b> runs along
     *       Z at x=cx (z=1..6): it both hydrates the flanking farmland and carries the
     *       harvested gourd south to the hopper mouth.</li>
     *   <li><b>Hopper + chest, y=0</b> — at the south end of the channel a hopper
     *       (x=cx, z=7) feeds a collection chest tucked at the south edge (z=8, facing
     *       north), so every gourd the channel delivers is collected.</li>
     *   <li><b>Stem strips, y=1..2</b> — two <b>farmland</b> rows straddle the channel
     *       at x=cx-1 (3) and x=cx+1 (5), z=1..7, each one block from water → always
     *       hydrated. A <b>melon/pumpkin stem</b> (alternating down the row) sits on
     *       every farmland cell at y=2, the planted stem.</li>
     *   <li><b>Grow-spaces, y=1</b> — bare <b>dirt</b> rows one column further out at
     *       x=cx-2 (2) and x=cx+2 (6): the empty cell at y=2 above each is where the
     *       stem grows its gourd. (Itemless? No — dirt is FU-valued at 1@1.)</li>
     *   <li><b>Harvest wall, y=2..3</b> — behind each grow-space (x=1 west / x=7 east)
     *       an <b>observer</b> at y=2 faces inward at the grow cell, watching for the
     *       gourd to appear; a <b>piston</b> at y=3 above it faces inward and shoves the
     *       gourd off the dirt and toward the channel. A stone backing block at y=1
     *       carries the column. Redstone dust on the y=4 cap ties observer-back →
     *       piston so the pulse reaches it.</li>
     *   <li><b>End walls + label signs</b> — cobble end caps (z=0 and z=8) box the
     *       channel; oak wall signs on the south face label the build.</li>
     * </ul>
     */
    private static Blueprint pumpkinMelonFarm() {
        Blueprint.Builder b = Blueprint.builder("Auto Pumpkin/Melon Farm", 9, 5, 9);
        // all vanilla, all FU-valued / structural-free:
        BlueprintBlockState stone     = bs("minecraft:stone");
        BlueprintBlockState cobble    = COBBLE;
        BlueprintBlockState dirt      = bs("minecraft:dirt");               // FU-valued (1@1) → grow-space
        BlueprintBlockState farmland  = FARMLAND;                           // FarmBlock → structural-free
        BlueprintBlockState water     = WATER;                             // structural (asItem()==AIR) → prints free
        BlueprintBlockState melonStem = bs("minecraft:melon_stem[age=7]");  // BushBlock → structural-free
        BlueprintBlockState pumpkinStem = bs("minecraft:pumpkin_stem[age=7]"); // BushBlock → structural-free
        BlueprintBlockState chest     = bs("minecraft:chest[facing=north,type=single,waterlogged=false]");
        BlueprintBlockState redDust   = bs("minecraft:redstone_wire[east=none,west=none,north=none,south=none,power=0]"); // structural

        int x0 = 0, x1 = 8, z0 = 0, z1 = 8;            // 9×9 footprint
        int cx = 4;                                    // central water-channel column
        int stripZ0 = 1, stripZ1 = 7;                  // planting / channel run along Z

        // ── 1) STONE FOUNDATION at y=0, with the central WATER CHANNEL ───────
        floor(b, 0, x0, z0, x1, z1, stone);
        // central channel: water along Z at x=cx (z=1..6); the harvested gourd floats south.
        for (int z = stripZ0; z <= stripZ1 - 1; z++) {
            b.set(cx, 0, z, water);
        }

        // ── 2) HOPPER + COLLECTION CHEST at the SOUTH end, y=0 ───────────────
        // The channel terminates over a hopper that feeds the chest. The hopper
        // mouth (z=7) catches what the flow delivers; it points north into the
        // chest tucked at the south edge (z=8), facing north so its front reads
        // inward. (Air-skip means these overwrite the stone foundation cells.)
        b.set(cx, 0, stripZ1, bs("minecraft:hopper[enabled=true,facing=north]")); // z=7 → feeds chest at z=8
        b.set(cx, 0, z1, chest);                                                   // collection chest, faces north

        // ── 3) STEM STRIPS (farmland + stems) + DIRT GROW-SPACES ─────────────
        // Two farmland rows at x=cx-1 and x=cx+1 (3 and 5), each one block from
        // water → hydrated. A melon/pumpkin stem (alternating) sits on every
        // farmland cell. Bare dirt grow-spaces sit one column further out (2 and 6);
        // the empty y=2 cell above each is where the stem grows its gourd.
        int wStripX = cx - 1, eStripX = cx + 1;        // 3 and 5 (farmland + stem)
        int wGrowX  = cx - 2, eGrowX  = cx + 2;        // 2 and 6 (dirt grow-spaces)
        for (int z = stripZ0; z <= stripZ1; z++) {
            b.set(wStripX, 1, z, farmland);
            b.set(eStripX, 1, z, farmland);
            // alternate melon / pumpkin stems down the rows for a mixed farm
            boolean melon = (z % 2 == 0);
            b.set(wStripX, 2, z, melon ? melonStem : pumpkinStem);
            b.set(eStripX, 2, z, melon ? pumpkinStem : melonStem);
            // dirt grow-spaces (y=2 above stays air → the gourd grows there)
            b.set(wGrowX, 1, z, dirt);
            b.set(eGrowX, 1, z, dirt);
        }

        // ── 4) HARVEST WALL: stone backing + observer + piston + redstone ────
        // Behind each grow-space a stone pillar carries the observer (y=2, facing
        // the grow cell, watching for the gourd) and the piston (y=3, facing inward
        // to shove the gourd off the dirt toward the channel). West wall faces east,
        // east wall faces west. A stone cap at y=3 above the piston gives the dust a
        // floor, and a redstone-dust ribbon at y=4 ties each observer's back → piston.
        int wWallX = cx - 3, eWallX = cx + 3;          // 1 and 7 (observer/piston columns)
        for (int z = stripZ0; z <= stripZ1; z++) {
            b.set(wWallX, 1, z, stone);
            b.set(eWallX, 1, z, stone);
            b.set(wWallX, 2, z, bs("minecraft:observer[facing=east,powered=false]"));
            b.set(eWallX, 2, z, bs("minecraft:observer[facing=west,powered=false]"));
            b.set(wWallX, 3, z, bs("minecraft:piston[facing=east,extended=false]"));
            b.set(eWallX, 3, z, bs("minecraft:piston[facing=west,extended=false]"));
            // redstone-dust ribbon at y=4 rides on the piston top, carrying the
            // observer's back-output across to the piston (top floor = the piston body).
            b.set(wWallX, 4, z, redDust);
            b.set(eWallX, 4, z, redDust);
        }

        // ── 5) END WALLS (box the channel) + LABEL SIGNS ─────────────────────
        // Cobble end caps at z=0 and z=8 across the planting/channel span close the
        // ends so the water channel reads as a contained trough.
        line(b, 1, wWallX, z0, eWallX, z0, cobble);   // north end cap, y=1
        line(b, 1, wWallX, z1, eWallX, z1, cobble);   // south end cap, y=1
        // oak wall signs on the south face flanking the chest (FU-valued, derived).
        b.set(wStripX, 1, z1, bs("minecraft:oak_wall_sign[facing=south]"));
        b.set(eStripX, 1, z1, bs("minecraft:oak_wall_sign[facing=south]"));

        return b.build();
    }

    /**
     * §F.cactus_farm — a STATIC automatic cactus farm, 7×7×5 (W×L×H)
     * → builder(7, 5, 7).
     *
     * <p>The "Tier-1 easy farm", printed as the working STRUCTURE the player plants
     * into. Vanilla <b>cactus</b> has no producing recipe and is <em>not</em>
     * structural matter ({@code CactusBlock} is not a {@code BushBlock}), so it is
     * UNVALUED and would be silently skipped by the printer's strict-mode gate. We
     * therefore <b>omit the cactus itself</b> — the player plants it after printing —
     * and print the mechanism: sand columns on raised pedestals, an adjacent
     * <b>break-bar</b> (an oak <b>fence post</b>, not a glass pane / iron bar, so the
     * render-integrity stub-pane gate doesn't apply) at the grow height, a sunken
     * <b>water canal</b> that catches the snapped cactus, and a hopper → chest at the
     * south end. Every printed block is a vanilla FU-valued block (sand 1@1, stone,
     * cobble, oak_fence, hopper, chest all derive) or structural-free matter (water
     * prints free, {@code asItem()==AIR}).
     *
     * <p>How it works once printed + planted: the player drops a cactus on each sand
     * pedestal. Cactus grows straight up; the moment it grows into the block adjacent
     * to the fence-post break-bar one column over, the touching-a-solid-neighbour rule
     * pops the new segment as a drop. The drop lands in the sunken water canal beside
     * the pedestal, floats south, and is swept by the hopper into the collection chest.
     *
     * <p>Layout (south = +z is the "front"/access side; the canal runs along Z):
     * <ul>
     *   <li><b>y=0</b> — stone foundation (7×7) with two <b>water canals</b> punched
     *       along Z (z=1..5) at x=2 and x=4: the troughs that catch + carry the
     *       snapped cactus south.</li>
     *   <li><b>Hopper + chest, y=0</b> — at the south end of each canal a hopper
     *       (z=5) feeds a collection chest tucked at the south edge (z=6, facing
     *       north), so every cactus the canal delivers is collected.</li>
     *   <li><b>Sand pedestals, y=1..2</b> — two rows of sand columns at x=1 and x=5
     *       (z=1..5), each raised on a 1-block stone pedestal (y=1) with the sand
     *       grow-block on top (y=2). The player plants cactus on the y=2 sand; it
     *       grows up into y=3+.</li>
     *   <li><b>Break-bars, y=3</b> — an oak <b>fence post</b> one column inward from
     *       each sand row (x=2-adjacent → x=1.5 isn't a cell, so the bar sits on the
     *       canal-edge column at x=2/x=4 over the water at y=3) facing the grow cell:
     *       when the cactus reaches y=3 next to it, the segment pops off and falls
     *       into the canal below. (Fence posts auto-connect to nothing here and render
     *       fine as a single post — no IronBarsBlock stub risk.)</li>
     *   <li><b>Side walls + label signs</b> — cobble end caps (z=0, z=6) box the
     *       canals; oak wall signs on the south face label the build.</li>
     * </ul>
     */
    private static Blueprint cactusFarm() {
        Blueprint.Builder b = Blueprint.builder("Cactus Farm", 7, 5, 7);
        // all vanilla, all FU-valued / structural-free (NO cactus — unvalued; player plants it):
        BlueprintBlockState stone  = bs("minecraft:stone");
        BlueprintBlockState cobble = COBBLE;
        BlueprintBlockState sand   = bs("minecraft:sand");   // FU-valued (1@1) — the grow-block
        BlueprintBlockState water  = WATER;                  // structural (asItem()==AIR) → prints free
        BlueprintBlockState fence  = OAK_FENCE;              // FU-valued; break-bar (NOT a pane → no stub-pane gate)
        BlueprintBlockState chest  = bs("minecraft:chest[facing=north,type=single,waterlogged=false]");

        int x0 = 0, x1 = 6, z0 = 0, z1 = 6;            // 7×7 footprint
        int wCanalX = 2, eCanalX = 4;                  // the two sunken water canals
        int wSandX  = 1, eSandX  = 5;                  // the two sand-pedestal rows
        int rowZ0 = 1, rowZ1 = 5;                      // pedestals / canal run along Z

        // ── 1) STONE FOUNDATION at y=0, with the two WATER CANALS ────────────
        floor(b, 0, x0, z0, x1, z1, stone);
        // canals: water along Z at x=2 and x=4 (z=1..4); the snapped cactus floats south.
        for (int z = rowZ0; z <= rowZ1 - 1; z++) {
            b.set(wCanalX, 0, z, water);
            b.set(eCanalX, 0, z, water);
        }

        // ── 2) HOPPERS + COLLECTION CHEST at the SOUTH end, y=0 ──────────────
        // Each canal terminates over a hopper that feeds the chest. The hopper
        // mouths (z=5) catch what the flows deliver; they point north into a chest
        // tucked at the south edge (z=6), facing north so its front reads inward.
        // (Air-skip means these overwrite the stone foundation cells.)
        b.set(wCanalX, 0, rowZ1, bs("minecraft:hopper[enabled=true,facing=north]")); // z=5 → feeds chest
        b.set(eCanalX, 0, rowZ1, bs("minecraft:hopper[enabled=true,facing=north]"));
        b.set(wCanalX, 0, z1, chest);                                                 // west collection chest
        b.set(eCanalX, 0, z1, chest);                                                 // east collection chest

        // ── 3) SAND PEDESTALS (raised) — the player plants cactus on top ─────
        // Two sand rows at x=1 and x=5 (z=1..5). Each is a 1-block stone pedestal at
        // y=1 with the sand grow-block on top at y=2, so cactus planted there grows up
        // (y=3+) beside the break-bar over the adjacent canal, and snapped segments
        // fall PAST the pedestal into the canal one column over.
        for (int z = rowZ0; z <= rowZ1; z++) {
            b.set(wSandX, 1, z, stone);   // pedestal base
            b.set(eSandX, 1, z, stone);
            b.set(wSandX, 2, z, sand);    // grow-block (player plants cactus here)
            b.set(eSandX, 2, z, sand);
        }

        // ── 4) BREAK-BARS (oak fence posts) over the canals, at grow height ──
        // A fence post sits on the canal-edge column (x=2 / x=4) at y=3, directly
        // beside the y=3 cell the planted cactus grows into. When the cactus touches
        // the post the new segment pops as a drop and falls into the canal below.
        // Fence posts are NOT IronBarsBlock, so the stub-pane render gate doesn't apply.
        for (int z = rowZ0; z <= rowZ1; z++) {
            b.set(wCanalX, 3, z, fence);
            b.set(eCanalX, 3, z, fence);
        }

        // ── 5) END WALLS (box the canals) + LABEL SIGNS ──────────────────────
        // Cobble end caps at z=0 and z=6 across the canal span close the ends so each
        // water canal reads as a contained trough.
        line(b, 1, wSandX, z0, eSandX, z0, cobble);   // north end cap, y=1
        line(b, 1, wSandX, z1, eSandX, z1, cobble);   // south end cap, y=1
        // oak wall signs on the south face flanking the chests (FU-valued, derived).
        b.set(wSandX, 1, z1, bs("minecraft:oak_wall_sign[facing=south]"));
        b.set(eSandX, 1, z1, bs("minecraft:oak_wall_sign[facing=south]"));

        return b.build();
    }

    /**
     * §F.bamboo_farm — a STATIC automatic bamboo farm, 7×7×5 (W×L×H)
     * → builder(7, 5, 7).
     *
     * <p>The "Tier-1 post-1.20 farm", printed as the working STRUCTURE the player
     * plants into. Vanilla <b>bamboo</b> has no producing recipe and its grown stalk
     * is not structural matter ({@code BambooStalkBlock} is not a {@code BushBlock}),
     * so it is UNVALUED and would be silently skipped by the printer's strict-mode
     * gate. We therefore <b>omit the bamboo itself</b> — the player plants a shoot on
     * each grow-block after printing — and print the mechanism: <b>mud</b> grow-blocks
     * (bamboo plants on dirt/sand/gravel/mud; mud is FU-valued 1@1 and on-theme for
     * the post-1.20 mangrove-swamp look), an <b>observer</b> at the harvest height that
     * watches the grow column and detects the stalk growing one block taller, a
     * <b>piston</b> above it that shoves/breaks the new segment off, a sunken
     * <b>water canal</b> that catches the snapped bamboo, and a hopper → chest at the
     * south end. Every printed block is a vanilla FU-valued block (mud 1@1, stone,
     * cobble, redstone 4@3, hopper 100@3, observer/piston derive from crafting,
     * chest derives) or structural-free matter (water prints free, {@code asItem()==AIR};
     * redstone_wire is structural).
     *
     * <p>How it works once printed + planted: the player drops a bamboo shoot on each
     * mud grow-block. Bamboo grows straight up; the moment a new segment grows into the
     * cell an observer faces, the observer pulses, firing the piston beside it which
     * breaks the stalk above the cut point. The broken bamboo lands in the central
     * water canal, floats south, and is swept by the hopper into the collection chest.
     * (This mirrors the shipped cactus_farm / pumpkin_melon_farm static-shell pattern,
     * with a central canal flanked by two harvest rows.)
     *
     * <p>Layout (south = +z is the "front"/access side; the canal runs along Z; cx=3):
     * <ul>
     *   <li><b>y=0</b> — stone foundation (7×7) with a central <b>water canal</b>
     *       punched along Z (z=1..4) at x=cx: the trough that catches + carries the
     *       snapped bamboo south.</li>
     *   <li><b>Hopper + chest, y=0</b> — at the south end of the canal a hopper
     *       (x=cx, z=5) feeds a collection chest tucked at the south edge (z=6,
     *       facing north), so every bamboo segment the canal delivers is collected.</li>
     *   <li><b>Mud grow-blocks, y=1</b> — two rows of mud straddling the canal at
     *       x=cx-1 (2) and x=cx+1 (4), z=1..5: the player plants bamboo on top (y=2);
     *       the stalk grows up over the canal edge, and broken segments fall into the
     *       central canal.</li>
     *   <li><b>Harvest wall, y=2..3</b> — one column further out (x=1 west / x=5 east)
     *       a stone base at y=1 carries an <b>observer</b> at y=2 facing the grow
     *       column (west faces east, east faces west), watching the stalk grow into its
     *       face, and a <b>piston</b> at y=3 above it facing the grow column to break
     *       the new segment off toward the canal. A redstone-dust ribbon on the y=4
     *       piston-top ties each observer's back output across to fire its piston.</li>
     *   <li><b>End walls + label signs</b> — cobble end caps (z=0, z=6) box the
     *       canal; oak wall signs on the south face label the build.</li>
     * </ul>
     */
    private static Blueprint bambooFarm() {
        Blueprint.Builder b = Blueprint.builder("Bamboo Farm", 7, 5, 7);
        // all vanilla, all FU-valued / structural-free (NO bamboo — unvalued; player plants it):
        BlueprintBlockState stone   = bs("minecraft:stone");
        BlueprintBlockState cobble  = COBBLE;
        BlueprintBlockState mud     = bs("minecraft:mud");   // FU-valued (1@1) — the grow-block (on-theme post-1.20)
        BlueprintBlockState water   = WATER;                 // structural (asItem()==AIR) → prints free
        BlueprintBlockState chest   = bs("minecraft:chest[facing=north,type=single,waterlogged=false]");
        BlueprintBlockState redDust = bs("minecraft:redstone_wire[east=none,west=none,north=none,south=none,power=0]"); // structural

        int x0 = 0, x1 = 6, z0 = 0, z1 = 6;            // 7×7 footprint
        int cx = 3;                                    // central water-canal column
        int wMudX  = cx - 1, eMudX  = cx + 1;          // 2 and 4 (mud grow-block rows)
        int wWallX = cx - 2, eWallX = cx + 2;          // 1 and 5 (observer/piston harvest walls)
        int rowZ0 = 1, rowZ1 = 5;                      // grow-blocks / canal run along Z

        // ── 1) STONE FOUNDATION at y=0, with the central WATER CANAL ─────────
        floor(b, 0, x0, z0, x1, z1, stone);
        // canal: water along Z at x=cx (z=1..4); the snapped bamboo floats south.
        for (int z = rowZ0; z <= rowZ1 - 1; z++) {
            b.set(cx, 0, z, water);
        }

        // ── 2) HOPPER + COLLECTION CHEST at the SOUTH end, y=0 ───────────────
        // The canal terminates over a hopper that feeds the chest. The hopper mouth
        // (z=5) catches what the flow delivers; it points north into a chest tucked
        // at the south edge (z=6), facing north so its front reads inward. (Air-skip
        // means these overwrite the stone foundation cells.)
        b.set(cx, 0, rowZ1, bs("minecraft:hopper[enabled=true,facing=north]")); // z=5 → feeds chest
        b.set(cx, 0, z1, chest);                                                 // collection chest, faces north

        // ── 3) MUD GROW-BLOCKS — the player plants bamboo on top ─────────────
        // Two mud rows straddling the canal at x=2 and x=4 (z=1..5). The player plants
        // a bamboo shoot on the y=2 cell above each; the stalk grows up (y=3+) over the
        // canal edge, and broken segments fall into the central canal.
        for (int z = rowZ0; z <= rowZ1; z++) {
            b.set(wMudX, 1, z, mud);   // grow-block (player plants bamboo here)
            b.set(eMudX, 1, z, mud);
        }

        // ── 4) HARVEST WALL: observer + piston + redstone ────────────────────
        // One column further out from each mud row (x=1 west / x=5 east) a stone base
        // at y=1 carries an observer at y=2 facing the grow column (watching the stalk
        // grow into its face) and a piston at y=3 above it facing the grow column
        // (breaks the new segment off toward the central canal). West wall faces east,
        // east wall faces west. A redstone-dust ribbon at y=4 rides on the piston-top,
        // carrying each observer's back-output across to fire its piston.
        for (int z = rowZ0; z <= rowZ1; z++) {
            b.set(wWallX, 1, z, stone);   // observer/piston base
            b.set(eWallX, 1, z, stone);
            b.set(wWallX, 2, z, bs("minecraft:observer[facing=east,powered=false]"));
            b.set(eWallX, 2, z, bs("minecraft:observer[facing=west,powered=false]"));
            b.set(wWallX, 3, z, bs("minecraft:piston[facing=east,extended=false]"));
            b.set(eWallX, 3, z, bs("minecraft:piston[facing=west,extended=false]"));
            b.set(wWallX, 4, z, redDust);
            b.set(eWallX, 4, z, redDust);
        }

        // ── 5) END WALLS (box the canal) + LABEL SIGNS ───────────────────────
        // Cobble end caps at z=0 and z=6 across the grow span close the ends so the
        // water canal reads as a contained trough.
        line(b, 1, wWallX, z0, eWallX, z0, cobble);   // north end cap, y=1
        line(b, 1, wWallX, z1, eWallX, z1, cobble);   // south end cap, y=1
        // oak wall signs on the south face flanking the chest (FU-valued, derived).
        b.set(wMudX, 1, z1, bs("minecraft:oak_wall_sign[facing=south]"));
        b.set(eMudX, 1, z1, bs("minecraft:oak_wall_sign[facing=south]"));

        return b.build();
    }

    /**
     * §F.fishery_pond — a lakeside fishing shack on stilts over a water pond, 7×7×8
     * (W×L×H) → builder(7, 8, 7). The "lakeside trend" decor build: a structural
     * water pool, a spruce dock/deck raised over it, an enterable spruce fishing hut
     * (walls, door, render-safe windows, flat spruce-slab roof), barrels for the
     * catch, net-drying fence racks on the open dock, and lanterns for the glow.
     *
     * <p>All blocks are vanilla and FU-valued or structural-free: spruce
     * planks/logs/slabs/stairs/fence + barrels + lanterns are FU-valued; {@code water}
     * is structural matter ({@code asItem()==AIR}) so the pond prints free. No
     * gate-flagged blocks; window panes are flanked by wall cells on the same wall
     * axis so the render-integrity stub-pane gate never applies.
     *
     * <p>Layout (south = +z is the dock/access side; the hut sits at the north half):
     * <ul>
     *   <li><b>y=0</b> — water pond (7×7), structural. Cobble footing pads overwrite
     *       the water under each stilt so the posts read as founded, not floating.</li>
     *   <li><b>Stilts, y=1..2</b> — spruce logs rising from the footings up to the
     *       deck plate.</li>
     *   <li><b>Deck, y=3</b> — spruce planks over the north 5-deep half (the hut floor
     *       + a covered porch), plus a 2-wide dock finger reaching south into the open
     *       pond. The southmost dock edge is left open to the water for fishing.</li>
     *   <li><b>Hut, y=4..6</b> — a 5×5 spruce-plank wall ring with spruce-log corner
     *       posts on the north footprint, a south-facing door opening inward, and
     *       render-safe glass-pane windows.</li>
     *   <li><b>Roof, y=7</b> — a flat spruce-slab (top) roof over the hut — the shack's
     *       low pitched cap.</li>
     *   <li><b>Net-drying racks</b> — spruce-fence frames on the open dock, with a
     *       horizontal fence cross-rail reading as a hung net.</li>
     *   <li><b>Furnishings</b> — barrels (the catch), a crafting table, lanterns inside
     *       and a hanging lantern under the deck over the water.</li>
     * </ul>
     */
    private static Blueprint fisheryPond() {
        Blueprint.Builder b = Blueprint.builder("Fishery Pond", 7, 8, 7);
        Palette p = TAIGA_SPRUCE; // spruce planks/logs/slabs/stairs, glass panes, lanterns
        BlueprintBlockState logY = p.logPillarY; // spruce_log[axis=y]
        BlueprintBlockState fence = bs("minecraft:spruce_fence"); // net-rack frames (FU-valued)

        int x0 = 0, x1 = 6, z0 = 0, z1 = 6;
        int cx = (x0 + x1) / 2; // 3
        int deckY = 3;          // raised walkable surface = top of the y=3 deck
        int wallBottom = 4;     // walls rise above the deck
        int wallH = 6;          // wall plate
        int roofY = wallH + 1;  // 7 (flat slab cap)

        // hut occupies the north 5×5 (z=0..4); the dock finger reaches south (z=5..6).
        int hutX0 = 1, hutX1 = 5, hutZ0 = 0, hutZ1 = 4;

        // 1) WATER POND over the whole footprint (structural → prints free).
        floor(b, 0, x0, z0, x1, z1, WATER);

        // 2) COBBLE FOOTING PADS + SPRUCE-LOG STILTS under the hut corners, the hut's
        //    south edge midpoints, and the dock finger — overwriting the water cell so
        //    the posts sit founded. Stilts climb y=1..deckY-1 (y=1..2).
        int[][] stilts = {
                {hutX0, hutZ0}, {hutX1, hutZ0}, {hutX0, hutZ1}, {hutX1, hutZ1}, // hut corners
                {cx, hutZ0}, {cx, hutZ1},                                        // hut N/S mids
                {cx - 1, z1}, {cx + 1, z1}                                       // dock finger tip
        };
        for (int[] s : stilts) {
            b.set(s[0], 0, s[1], ((s[0] + s[1]) % 2 == 0) ? COBBLE : MOSSY_COBBLE); // footing pad
            pillar(b, s[0], s[1], 1, deckY - 1, logY); // log stilt y=1..2
        }

        // 3) SPRUCE-PLANK DECK at y=3. The hut footprint (z=0..4) is fully decked; a
        //    2-wide dock finger (x=cx-1..cx+1, z=5..6) reaches south over the open
        //    pond. The southmost row (z=6) edge is the fishing lip.
        floor(b, deckY, hutX0, hutZ0, hutX1, hutZ1, p.plankFloor); // hut floor + porch
        floor(b, deckY, cx - 1, z1 - 1, cx + 1, z1, p.plankFloor); // dock finger (z=5..6)

        // 4) HUT WALL RING y=4..6 with spruce-log corner posts (5×5 on the north half).
        walls(b, hutX0, hutZ0, hutX1, hutZ1, wallBottom, wallH, p.wall);
        corners(b, hutX0, hutZ0, hutX1, hutZ1, wallBottom, wallH, logY);

        // 5) SOUTH DOOR (front, faces the dock) opening inward + render-safe windows.
        //    Each pane is flanked by wall cells along its wall axis → connects, so no
        //    stub-pane gate. The door sits in the south wall at the dock side.
        door2(b, cx, wallBottom, hutZ1, p.doorWood, "S");
        int wy = wallBottom + 1; // y=5, mid-wall
        window2(b, hutX0, wy, hutZ0 + 2, p.windowPane, null); // west wall, centred
        window2(b, hutX1, wy, hutZ0 + 2, p.windowPane, null); // east wall, centred
        window2(b, cx - 1, wy, hutZ0, p.windowPane, null);    // north wall, west of centre
        window2(b, cx + 1, wy, hutZ0, p.windowPane, null);    // north wall, east of centre

        // 6) FLAT SPRUCE-SLAB ROOF (top slabs) at y=7 — the shack's low cap.
        flatRoof(b, roofY, hutX0, hutZ0, hutX1, hutZ1, p.slabTop);

        // 7) NET-DRYING RACKS on the open dock: two spruce-fence posts on the dock
        //    finger with a horizontal fence cross-rail between their tops, reading as
        //    a hung drying net. Posts climb from the deck (y=4..5); rail at y=5.
        pillar(b, cx - 1, z1, deckY + 1, deckY + 2, fence); // west rack post
        pillar(b, cx + 1, z1, deckY + 1, deckY + 2, fence); // east rack post
        b.set(cx, deckY + 2, z1, fence);                    // cross-rail (the "net")

        // 8) FURNISHINGS on the hut floor (standing floor = y=4):
        b.set(hutX0 + 1, wallBottom, hutZ0 + 1, BARREL);        // catch barrel, NW corner
        b.set(hutX1 - 1, wallBottom, hutZ0 + 1, BARREL);        // catch barrel, NE corner
        b.set(hutX1 - 1, wallBottom, hutZ1 - 1, CRAFTING_TABLE); // crafting table
        b.set(hutX0 + 1, wallBottom, hutZ1 - 1, p.lightBlock);   // interior lantern
        b.set(cx, wallBottom, hutZ0 + 1, BARREL);               // back-wall barrel

        // 9) DOCK LANTERNS: a standing lantern on the dock lip and a hanging lantern
        //    under the deck over the open water (backed by the solid plank deck above).
        b.set(cx, deckY + 1, z1, LANTERN);          // lantern on the dock tip
        b.set(cx, deckY - 1, z1 - 1, HANGING_LANTERN); // hangs under the deck over water

        return b.build();
    }

    /**
     * §F.tree_farm — a STATIC automatic tree farm, 7×7×6 (W×L×H)
     * → builder(7, 6, 7).
     *
     * <p>The "most efficient farm", printed as the working STRUCTURE the player
     * plants into. The trunk <b>logs</b> and the canopy <b>leaves</b> are the
     * <em>output</em> the tree grows itself once printed + grown — and leaves are
     * UNVALUED (no producing recipe; not structural matter), so we <b>never place
     * leaves</b> and we don't pre-place logs either: the tree makes its own. We print
     * the mechanism in vanilla FU-valued / structural-free blocks only.
     *
     * <p>The planting grid is a 3×3 of <b>dirt</b> grow-pads (FU-valued 1@1) on a
     * raised stone base; the player drops an <b>oak sapling</b> on each — saplings are
     * {@link net.minecraft.world.level.block.BushBlock} descendants, so they're
     * <b>structural-free</b> (their item is the sapling seed, never the grown tree) and
     * we pre-place them at y=2 so the build reads as a planted grove. Each pad is fed
     * by a <b>dispenser</b> (bonemeal in the player's loadout) aimed up at the sapling
     * for the bone-meal growth-assist; an <b>observer</b> on the back wall watches each
     * grow-column and a <b>piston</b> above it knocks the grown trunk loose for harvest.
     * A <b>hopper</b> floor under the grid funnels the dropped logs into a
     * <b>chest</b> at the south edge, with a <b>redstone</b> ribbon tying observer →
     * piston. Every printed block is vanilla FU-valued (dirt 1@1, stone, cobble,
     * hopper 100@3, dispenser/observer/piston derive from crafting, redstone 4@3, chest
     * derives) or structural-free matter (oak saplings = BushBlock → print free;
     * redstone_wire is structural). No leaves, no pre-placed logs, no gate-flagged block.
     *
     * <p>How it works once printed + planted: the player drops an oak sapling on each
     * dirt pad and triggers the dispensers (bone meal) to fast-grow the trees; as a
     * trunk grows into the cell an observer faces, the observer pulses, firing the
     * piston that pops the new log loose. The broken log falls onto the hopper floor,
     * which feeds the collection chest. (Mirrors the shipped cactus/bamboo/pumpkin
     * static-shell pattern: a grow grid flanked by an observer/piston harvest wall over
     * a hopper-fed chest.)
     *
     * <p>Layout (south = +z is the "front"/access side; cx=3):
     * <ul>
     *   <li><b>y=0</b> — stone foundation (7×7). A central 3×3 <b>hopper floor</b>
     *       (x=2..4, z=2..4) catches the broken logs and chains them south into the
     *       collection chest at the south edge.</li>
     *   <li><b>Collection chest, y=0</b> — tucked at the south edge (x=cx, z=6, facing
     *       north); the hopper floor empties into it.</li>
     *   <li><b>Dirt grow-pads, y=1</b> — a 3×3 of <b>dirt</b> directly over the hopper
     *       floor (x=2..4, z=2..4): the player plants an oak sapling on each.</li>
     *   <li><b>Oak saplings, y=2</b> — pre-planted on every dirt pad (structural-free)
     *       so the grove reads as planted; the tree grows up from here.</li>
     *   <li><b>Harvest wall, y=2..3</b> — on the north back wall (x=2..4, z=1) a stone
     *       base at y=1 carries an <b>observer</b> at y=2 facing south into the grow
     *       column (watching the trunk grow into its face) and a <b>piston</b> at y=3
     *       above it facing south to knock the new log loose toward the hopper floor. A
     *       <b>redstone-dust</b> ribbon at y=4 on the piston-top ties each observer's
     *       back output across to fire its piston.</li>
     *   <li><b>Bone-meal dispensers, y=1</b> — on the south rim (x=2..4, z=5) a row of
     *       <b>dispensers</b> facing north at the grow columns, loaded with bone meal
     *       for the player's growth-assist (a button/lever press fast-grows the
     *       saplings).</li>
     *   <li><b>End walls + label signs</b> — cobble end caps (z=0, z=6) box the grid;
     *       oak wall signs on the south face label the build.</li>
     * </ul>
     */
    private static Blueprint treeFarm() {
        Blueprint.Builder b = Blueprint.builder("Tree Farm", 7, 6, 7);
        // all vanilla, all FU-valued / structural-free (NO leaves, NO pre-placed logs — the tree grows them):
        BlueprintBlockState stone   = bs("minecraft:stone");
        BlueprintBlockState cobble  = COBBLE;
        BlueprintBlockState dirt    = bs("minecraft:dirt");                 // FU-valued (1@1) — the grow-pad
        BlueprintBlockState sapling = bs("minecraft:oak_sapling[stage=0]"); // SaplingBlock extends BushBlock → structural-free
        BlueprintBlockState chest   = bs("minecraft:chest[facing=north,type=single,waterlogged=false]");
        BlueprintBlockState redDust = bs("minecraft:redstone_wire[east=none,west=none,north=none,south=none,power=0]"); // structural

        int x0 = 0, x1 = 6, z0 = 0, z1 = 6;            // 7×7 footprint
        int cx = 3;                                    // centre column
        int gx0 = 2, gx1 = 4;                          // 3×3 grow-grid X span (x=2..4)
        int gz0 = 2, gz1 = 4;                          // 3×3 grow-grid Z span (z=2..4)

        // ── 1) STONE FOUNDATION at y=0, with the central 3×3 HOPPER FLOOR ────
        floor(b, 0, x0, z0, x1, z1, stone);
        // hopper floor under the grow grid (x=2..4, z=2..4); each hopper points
        // north→south down the chain toward the chest at the south edge. The front
        // (z=4) row feeds the chest; the rows behind feed forward into it.
        for (int z = gz0; z <= gz1; z++) {
            for (int x = gx0; x <= gx1; x++) {
                // chain the catch south: every hopper faces toward the +z (south) chest.
                b.set(x, 0, z, bs("minecraft:hopper[enabled=true,facing=south]"));
            }
        }

        // ── 2) COLLECTION CHEST at the SOUTH end, y=0 ───────────────────────
        // The hopper chain terminates at the chest tucked at the south edge (z=6),
        // facing north so its front reads inward toward the grid.
        b.set(cx, 0, z1, chest);

        // ── 3) DIRT GROW-PADS (y=1) + OAK SAPLINGS (y=2) ────────────────────
        // A 3×3 of dirt directly over the hopper floor; the player plants an oak
        // sapling on each. We pre-place the saplings (structural-free) so the grove
        // reads as planted; the tree grows up from y=3.
        for (int z = gz0; z <= gz1; z++) {
            for (int x = gx0; x <= gx1; x++) {
                b.set(x, 1, z, dirt);       // grow-pad
                b.set(x, 2, z, sapling);    // pre-planted oak sapling (BushBlock → free)
            }
        }

        // ── 4) HARVEST WALL on the NORTH back row (z=1): observer + piston ──
        // For each grow column (x=2..4) a stone base at y=1 carries an observer at y=2
        // facing south into the grow column (watching the trunk grow into its face) and
        // a piston at y=3 above it facing south to knock the new log loose toward the
        // hopper floor. A redstone-dust ribbon at y=4 rides on the piston-top, carrying
        // each observer's back-output across to fire its piston.
        int wallZ = gz0 - 1;                           // z=1, one row north of the grid
        for (int x = gx0; x <= gx1; x++) {
            b.set(x, 1, wallZ, stone);                                            // observer/piston base
            b.set(x, 2, wallZ, bs("minecraft:observer[facing=south,powered=false]"));
            b.set(x, 3, wallZ, bs("minecraft:piston[facing=south,extended=false]"));
            b.set(x, 4, wallZ, redDust);
        }

        // ── 5) BONE-MEAL DISPENSER ROW on the SOUTH rim (z=5), y=1 ──────────
        // A row of dispensers facing north at the grow columns, loaded with bone meal
        // for the player's growth-assist. (Dispenser is a standard cobble+bow+redstone
        // recipe → FU-derived. Its bone-meal contents are NBT, not a block, so they
        // don't affect printing.)
        int dispZ = gz1 + 1;                           // z=5, one row south of the grid
        for (int x = gx0; x <= gx1; x++) {
            b.set(x, 1, dispZ, bs("minecraft:dispenser[facing=north,triggered=false]"));
        }

        // ── 6) END WALLS (box the grid) + LABEL SIGNS ───────────────────────
        // Cobble end caps at z=0 and z=6 across the grid span close the ends so the
        // farm reads as a contained planter bed.
        line(b, 1, gx0, z0, gx1, z0, cobble);   // north end cap, y=1
        line(b, 1, gx0, z1, gx1, z1, cobble);   // south end cap, y=1
        // oak wall signs on the south face flanking the chest (FU-valued, derived).
        b.set(gx0, 1, z1, bs("minecraft:oak_wall_sign[facing=south]"));
        b.set(gx1, 1, z1, bs("minecraft:oak_wall_sign[facing=south]"));

        return b.build();
    }

    /**
     * §F.kelp_farm — a STATIC automatic kelp farm + dried-kelp smelter, 9×9×9
     * (W×L×H) → builder(9, 9, 9).
     *
     * <p>The "food / fuel / XP" kelp farm, printed as the working STRUCTURE the
     * player plants into. Vanilla <b>kelp</b> has no producing recipe and its
     * grown stalk is not structural matter ({@code KelpBlock}/{@code KelpPlantBlock}
     * are not {@code BushBlock}s), so it is UNVALUED and would be silently skipped by
     * the printer's strict-mode gate. We therefore <b>omit the kelp itself</b> — the
     * player plants a kelp shoot on the column floor after printing — and print the
     * mechanism: a glass-walled <b>water column</b> in which the kelp grows, an
     * <b>observer</b> at the top that watches the column for the stalk growing one
     * block taller, a <b>piston</b> beside it that breaks the new growth off, a
     * <b>hopper</b> floor under the column that sweeps the cut kelp into a collection
     * <b>chest</b>, and a small <b>furnace bank</b> (the dried-kelp smelter) on the
     * south side. Every printed block is a vanilla FU-valued block (stone, glass,
     * observer/piston/hopper/chest/furnace all derive from crafting) or
     * structural-free matter (water prints free, {@code asItem()==AIR}; redstone_wire
     * is structural). The water column reads correctly because its walls are glass
     * <b>blocks</b> (NOT panes) seated on the stone foundation — so the render-integrity
     * stub-pane gate ({@code IronBarsBlock} with zero connections) never applies.
     *
     * <p>How it works once printed + planted: the player plants a kelp shoot on the
     * stone column floor (under the water). Kelp grows straight up through the water;
     * when a new segment grows into the cell the top observer faces, the observer
     * pulses, firing the piston beside it which breaks the stalk at the cut point. The
     * broken kelp drifts down through the water column and lands on the hopper floor,
     * which feeds the collection chest. Smelting the collected kelp in the furnace bank
     * yields dried kelp (food / a fuel source / smelting XP).
     *
     * <p>Layout (south = +z is the "front"/access side; the column is centred at
     * cx=cz=4):
     * <ul>
     *   <li><b>y=0</b> — stone foundation (9×9). The central 3×3 (x=3..5, z=3..5) is
     *       the <b>hopper floor</b> of the column: hoppers that catch broken kelp and
     *       chain it south into the collection chest.</li>
     *   <li><b>Collection chest, y=0</b> — tucked at the south edge under the column
     *       wall (x=4, z=6, facing north): the hopper floor empties into it.</li>
     *   <li><b>Water column, y=1..6</b> — the central 3×3 (x=3..5, z=3..5) filled with
     *       <b>water</b>; the player plants kelp on the hopper floor and it grows up
     *       through this column.</li>
     *   <li><b>Glass walls, y=1..6</b> — a 5×5 ring of glass <b>blocks</b> (x=2..6,
     *       z=2..6) boxing the water column so it reads as a contained tank and the
     *       water is held in. Glass blocks (not panes) connect to the stone foundation
     *       and each other → no stub-pane render risk.</li>
     *   <li><b>Harvest head, y=7..8</b> — above the column an <b>observer</b> at y=7
     *       (x=4, facing down into the column top) watches the kelp grow into its face;
     *       a <b>piston</b> at y=7 beside it (x=3, facing east at the grow cell) breaks
     *       the new growth, and a <b>redstone-dust</b> ribbon on a stone shelf at y=8
     *       ties the observer's back output across to fire the piston.</li>
     *   <li><b>Dried-kelp smelter, y=1</b> — a 2-<b>furnace</b> bank on the south face
     *       (x=2 and x=6, z=8, facing north) for smelting the collected kelp into dried
     *       kelp.</li>
     *   <li><b>Label signs</b> — oak wall signs on the south face flank the chest.</li>
     * </ul>
     */
    private static Blueprint kelpFarm() {
        Blueprint.Builder b = Blueprint.builder("Kelp Farm", 9, 9, 9);
        // all vanilla, all FU-valued / structural-free (NO kelp — unvalued; player plants it):
        BlueprintBlockState stone   = bs("minecraft:stone");
        BlueprintBlockState glass   = GLASS;                 // window blocks (NOT panes) → no stub-pane gate
        BlueprintBlockState water   = WATER;                 // structural (asItem()==AIR) → prints free
        BlueprintBlockState chest   = bs("minecraft:chest[facing=north,type=single,waterlogged=false]");
        BlueprintBlockState furnace = bs("minecraft:furnace[facing=north,lit=false]");
        BlueprintBlockState redDust = bs("minecraft:redstone_wire[east=none,west=none,north=none,south=none,power=0]"); // structural

        int x0 = 0, x1 = 8, z0 = 0, z1 = 8;            // 9×9 footprint
        int cx = 4, cz = 4;                            // column centre
        int colX0 = 3, colX1 = 5, colZ0 = 3, colZ1 = 5; // 3×3 water column
        int wallX0 = 2, wallX1 = 6, wallZ0 = 2, wallZ1 = 6; // 5×5 glass-wall ring
        int colTop = 6;                                // water rises y=1..6

        // ── 1) STONE FOUNDATION at y=0 ──────────────────────────────────────
        floor(b, 0, x0, z0, x1, z1, stone);

        // ── 2) HOPPER FLOOR under the column + COLLECTION CHEST, y=0 ─────────
        // The 3×3 column floor is hoppers that catch broken kelp and chain it south
        // into the chest tucked under the south wall (x=cx, z=6, facing north).
        // Each hopper points south toward the next, the southmost feeding the chest.
        // (Air-skip means these overwrite the stone foundation cells.)
        for (int x = colX0; x <= colX1; x++) {
            for (int z = colZ0; z <= colZ1; z++) {
                b.set(x, 0, z, bs("minecraft:hopper[enabled=true,facing=south]")); // chain south to the chest
            }
        }
        b.set(cx, 0, wallZ1, chest);                   // collection chest just south of the column (z=6), faces north

        // ── 3) WATER COLUMN + GLASS WALLS, y=1..colTop ──────────────────────
        // The 3×3 interior fills with water (the player plants kelp on the hopper
        // floor; it grows up through this column). A 5×5 ring of glass BLOCKS boxes
        // the column so it reads as a contained tank — glass blocks (not panes) seat
        // on the foundation and each other, so no IronBarsBlock stub-pane risk.
        for (int y = 1; y <= colTop; y++) {
            floor(b, y, colX0, colZ0, colX1, colZ1, water);   // water fill (3×3)
            walls(b, wallX0, wallZ0, wallX1, wallZ1, y, y, glass); // glass ring (5×5 perimeter)
        }

        // ── 4) HARVEST HEAD: observer + piston + redstone, y=7..8 ───────────
        // Above the column an observer at y=7 (x=cx, facing DOWN into the column top)
        // watches the kelp grow into its face; its BACK output points up into y=8. A
        // piston at y=7 one column west (x=3, facing east at the grow cell) breaks the
        // new growth. Both are solid mechanism blocks at y=7, so a redstone-dust ribbon
        // laid across their tops at y=8 — over the observer (its back output) and over
        // the piston — ties the observer's back output across to fire the piston. A
        // stone cap one column east (x=5) at y=7 closes the head as a solid mechanism.
        int obsX = cx;                                 // observer over the column centre
        int pistonX = cx - 1;                          // piston beside it (west)
        b.set(obsX, colTop + 1, cz, bs("minecraft:observer[facing=down,powered=false]")); // y=7, watches column top
        b.set(pistonX, colTop + 1, cz, bs("minecraft:piston[facing=east,extended=false]")); // y=7, breaks the growth
        b.set(cx + 1, colTop + 1, cz, stone);          // y=7, stone cap east of the observer (x=5)
        // redstone-dust ribbon at y=8 riding the y=7 mechanism tops: over the observer
        // back-output and over the piston, carrying the harvest pulse across.
        b.set(obsX, colTop + 2, cz, redDust);          // y=8, on the observer back-output
        b.set(pistonX, colTop + 2, cz, redDust);       // y=8, on the piston top

        // ── 5) DRIED-KELP SMELTER: 2-furnace bank on the south face, y=1 ────
        // Two furnaces on the south edge (x=2 and x=6, z=8) for smelting the collected
        // kelp into dried kelp (food / fuel / XP). Furnaces derive their FU value from
        // the 8-cobblestone crafting recipe.
        b.set(wallX0, 1, z1, furnace);                 // west furnace
        b.set(wallX1, 1, z1, furnace);                 // east furnace

        // ── 6) LABEL SIGNS on the south face flanking the chest ─────────────
        b.set(cx - 1, 1, wallZ1, bs("minecraft:oak_wall_sign[facing=south]"));
        b.set(cx + 1, 1, wallZ1, bs("minecraft:oak_wall_sign[facing=south]"));

        return b.build();
    }

    /**
     * §F.villager_trading_hall — a STATIC printable villager trading hall, 9×9×5
     * (W×L×H) → builder(9, 5, 9).
     *
     * <p>The "#2 must-build mid-game" trade hall, printed as the working SHELL the
     * player drops villagers into. We do NOT print villagers (entities aren't
     * blocks); we print the architecture: an enterable stone-brick hall with a
     * central N–S walkway and six barred villager bays (three per side), each
     * carrying a {@link #bed} and a distinct job-site workstation so a captured
     * villager takes on that profession. Every printed block is a vanilla
     * FU-valued block — stone / stone_bricks / stone-brick slabs derive trivially;
     * beds derive from wool+planks; <b>iron_bars</b> derive from iron; and the
     * job-site stations (lectern, smithing_table, composter, blast_furnace,
     * cartography_table, grindstone — plus the equally-valid fletching_table,
     * barrel, smoker, stonecutter, loom, brewing_stand) derive from standard
     * crafting recipes — or structural-free torch/lantern matter.
     *
     * <p>Render-integrity: each side's frontage is one continuous vertical run of
     * <b>iron_bars</b> (z=1..6) anchored to the solid north wall (z=0) at its head
     * and bar-to-bar all the way south. Every bar therefore has a connecting
     * horizontal neighbour — the wall jamb or the next bar in the run — so the
     * frontage renders as a real grate, never an invisible stub. The fronts face
     * the central walkway, so the player sees each villager and trades through the
     * bars.
     *
     * <p>How it's used once printed: the player walks in the north door, drops a
     * villager into each barred bay, and the villager claims the workstation in
     * that bay (taking its profession) and the bed (its home). The barred frontage
     * keeps the villagers in place for trading.
     *
     * <p>Layout (north = z=0 is the entrance; central walkway at x=4):
     * <ul>
     *   <li><b>y=0</b> — stone floor (9×9), walkable.</li>
     *   <li><b>Outer walls, y=1..3</b> — a stone-brick ring; a {@link #door2} in the
     *       centre of the north wall (x=4, z=0) opens inward (facing south).</li>
     *   <li><b>Central walkway, x=4, z=1..7</b> — left open (air-skip) so the hall is
     *       enterable; the bays flank it east and west.</li>
     *   <li><b>Barred frontage</b> — a continuous iron-bar wall on the x=3 plane
     *       (west) and x=5 plane (east), y=1..2, z=1..6, anchored to the north
     *       wall.</li>
     *   <li><b>Three bays per side</b> — each 2-deep (z=1–2, z=3–4, z=5–6): a
     *       {@link #bed} running along z in the outer column (x=1 west / x=7 east)
     *       and a job-site workstation beside it (x=2 west / x=6 east).</li>
     *   <li><b>Roof, y=4</b> — a stone-brick-slab (top half) cap over the footprint.</li>
     *   <li><b>Lighting</b> — floor lanterns in the rear (z=7) corners + a wall torch
     *       on the back wall keep the hall lit (no hostile spawns indoors).</li>
     * </ul>
     */
    private static Blueprint villagerTradingHall() {
        Blueprint.Builder b = Blueprint.builder("Villager Trading Hall", 9, 5, 9);
        // all vanilla, all FU-valued or structural:
        BlueprintBlockState stone   = bs("minecraft:stone");
        BlueprintBlockState wall    = STONE_BRICKS;                 // outer ring
        BlueprintBlockState roof    = STONE_BRICK_SLAB_TOP;         // y=4 slab cap (FU-valued slab)
        BlueprintBlockState bars    = IRON_BARS;                    // frontage (connects to north wall + itself)
        BlueprintBlockState lantern = LANTERN;                      // floor lantern (hanging=false)

        int x0 = 0, x1 = 8, z0 = 0, z1 = 8;            // 9×9 footprint
        int wFrontX = 3, eFrontX = 5;                  // barred-frontage planes (flank the x=4 walkway)
        int yB = 1, yT = 3;                            // wall course height
        int doorX = 4;                                 // north-wall doorway, central walkway

        // ── 1) STONE FLOOR at y=0 ───────────────────────────────────────────
        floor(b, 0, x0, z0, x1, z1, stone);

        // ── 2) OUTER STONE-BRICK WALLS, y=1..3 ──────────────────────────────
        walls(b, x0, z0, x1, z1, yB, yT, wall);

        // ── 3) NORTH-WALL DOORWAY (central walkway), opens inward ───────────
        // door2 writes a 2-block door state (lower+upper) over the wall cells,
        // superseding the solid wall there — the only break in the ring.
        door2(b, doorX, yB, z0, "oak", "N");

        // ── 4) BARRED FRONTAGE, y=1..2, z=1..6 ──────────────────────────────
        // One continuous iron-bar run per side, anchored to the solid north wall
        // (z=0) and bar-to-bar all the way south, so every bar connects to a
        // neighbour (wall or bar) and renders as a real grate — no invisible stubs.
        for (int z = 1; z <= 6; z++) {
            for (int y = yB; y <= yB + 1; y++) {        // y=1..2 (head-height view)
                b.set(wFrontX, y, z, bars);            // west frontage
                b.set(eFrontX, y, z, bars);            // east frontage
            }
        }

        // ── 5) BAYS: bed + job-site workstation per bay ─────────────────────
        // Three 2-deep bays per side (z=1–2, z=3–4, z=5–6). The bed runs along z in
        // the outer column (x=1 west / x=7 east), head at the low-z end, foot at the
        // high-z end (facing=north → foot at z+1); the workstation sits beside the
        // bed in the inner column (x=2 west / x=6 east), against the bar frontage.
        // Six distinct professions (a dropped-in villager claims the station):
        //   W1 (z1–2): lectern (librarian)        E1 (z1–2): blast_furnace (armorer)
        //   W2 (z3–4): smithing_table (toolsmith)  E2 (z3–4): cartography_table (cartographer)
        //   W3 (z5–6): composter (farmer)          E3 (z5–6): grindstone (weaponsmith)
        // (Other valid stations — fletching_table, barrel, smoker, stonecutter,
        //  loom, brewing_stand — are equally FU-valued; the hall is extensible.)
        int[] bayHeadZ = {1, 3, 5};                    // each bay's bed head (foot at +1)
        BlueprintBlockState[] wStations = {LECTERN, SMITHING_TABLE, COMPOSTER};
        BlueprintBlockState[] eStations = {BLAST_FURNACE, CARTOGRAPHY_TABLE, GRINDSTONE};
        for (int i = 0; i < bayHeadZ.length; i++) {
            int hz = bayHeadZ[i];
            int sz = hz + 1;                           // station beside the bed foot
            bed(b, 1, yB, hz, "white", "north");       // west bed (x=1, head z=hz, foot z=hz+1)
            b.set(2, yB, sz, wStations[i]);            // west workstation
            bed(b, 7, yB, hz, "white", "north");       // east bed (x=7, head z=hz, foot z=hz+1)
            b.set(6, yB, sz, eStations[i]);            // east workstation
        }

        // ── 6) ROOF: stone-brick slab (top half) cap at y=4 ─────────────────
        floor(b, 4, x0, z0, x1, z1, roof);

        // ── 7) LIGHTING ─────────────────────────────────────────────────────
        // Floor lanterns in the rear (z=7) back-strip corners — clear of the beds
        // (which end at z=6) and the walkway — + a wall torch on the supported back
        // (south) wall light the hall so no hostiles spawn around the villagers.
        b.set(1, yB, 7, lantern);                        // west rear corner
        b.set(7, yB, 7, lantern);                        // east rear corner
        b.set(3, yB, 7, lantern);                        // west, beside the walkway end
        b.set(5, yB, 7, lantern);                        // east, beside the walkway end
        wallTorch(b, doorX, yT, z1 - 1, "north");        // on the south wall (z=8), faces north into hall

        return b.build();
    }

    /**
     * §G.storage_barrel_hall — an ENTERABLE storage hall, 9×9×5 (W×L×H) →
     * builder(9, 5, 9). The r/Minecraft "barrel wall" staple: a stone-brick
     * {@link #roomShell} whose interior perimeter is lined with FLOOR-TO-CEILING
     * barrel columns, framed by stripped-log structural posts, with oak-wall-sign
     * labels facing the central walkway and chain-hung lanterns down the middle.
     *
     * <p>It prints EMPTY — every barrel is an empty container the player fills.
     * The value is the organized, ready-to-use storage room, not its contents.
     *
     * <p><b>Why barrels stack into columns.</b> Unlike chests, a barrel opens even
     * with a solid block directly above it (its lid faces the side you click), so a
     * 3-high column of barrels is fully usable — every barrel still opens from the
     * walkway side. That's the whole reason the barrel-wall build exists, and why
     * the columns here run the full wall height (y=1..3) instead of a single course.
     *
     * <p>Layout (interior is x,z = 1..7, a 7×7 room; floor y0, walls y1..3, ceil y4):
     * <ul>
     *   <li><b>Walkway.</b> The central cross — column {@code x=4} and row {@code z=4}
     *       — is left OPEN at standing height so you can walk in from the north door
     *       and reach every wall. The interior floor at {@code y=0} is the walkable
     *       surface (air-skip leaves the interior above it empty).</li>
     *   <li><b>Barrel columns.</b> The interior perimeter ring (the cells just inside
     *       the stone-brick wall, {@code x∈{1,7}} or {@code z∈{1,7}}) is filled with
     *       barrels for the full wall height {@code y=1..3}, EXCEPT the four cells
     *       where the walkway cross meets the ring (those stay open as aisles) and
     *       the door approach. Each column is 3 barrels tall → a true barrel wall.</li>
     *   <li><b>Stripped-log frame.</b> The four corners get full-height stripped-oak
     *       posts (structural framing), and a stripped-oak tie course runs along the
     *       wall top at {@code y=3} as a header beam, breaking up the barrel mass.</li>
     *   <li><b>Sign labels.</b> Oak wall signs on the inner barrel faces flanking the
     *       walkway aisles, facing the walker — the "item frame label" of the spec,
     *       swapped to a wall sign because {@code item_frame} is a vanilla ENTITY with
     *       no block id / FU value (it can't print), whereas {@code oak_wall_sign} is
     *       a real FU-valued block that reads identically as a per-bay label.</li>
     *   <li><b>Lighting.</b> Chain-hung lanterns from the ceiling down the walkway so
     *       the hall is lit and mob-free.</li>
     * </ul>
     *
     * <p>All blocks are vanilla and FU-valued or structural: barrel (derives from
     * planks + slabs), stripped_oak_log, oak_wall_sign, stone_bricks, lantern, chain.
     */
    private static Blueprint storageBarrelHall() {
        Blueprint.Builder b = Blueprint.builder("Storage Barrel Hall", 9, 5, 9);

        BlueprintBlockState stone = bs("minecraft:stone");          // floor + ceiling read
        int x0 = 0, x1 = 8, z0 = 0, z1 = 8;                          // 9×9 footprint
        int yB = 1, yT = 3;                                          // wall course (y=1..3)
        int cx = 4, cz = 4;                                          // walkway cross axes
        int doorX = 4;                                               // north-wall doorway

        // ── 1) SHELL: stone-brick walls, stone floor, stone-brick ceiling ───
        // roomShell lays the floor (y0), the wall ring (y1..3), and the ceiling
        // (y4); the interior is left open per the air-skip rule so it's enterable.
        roomShell(b, x0, 0, z0, x1, yT + 1, z1, STONE_BRICKS, stone, STONE_BRICKS);

        // ── 2) NORTH-WALL DOORWAY (on the walkway axis), opens inward ────────
        door2(b, doorX, yB, z0, "oak", "N");

        // ── 3) BARREL COLUMNS around the interior perimeter, y=1..3 ──────────
        // The ring is the interior cells touching the wall (x∈{1,7} or z∈{1,7}).
        // Skip the four walkway-mouth cells where the central cross meets the ring
        // (x==cx on z∈{1,7}, z==cz on x∈{1,7}) so the player can reach the back
        // walls, and skip the door-approach cell (x==doorX, z==1) so entry is clear.
        int barrels = 0;
        for (int x = 1; x <= 7; x++) {
            for (int z = 1; z <= 7; z++) {
                boolean onRing = (x == 1 || x == 7 || z == 1 || z == 7);
                if (!onRing) continue;                               // interior stays open
                boolean walkwayMouth = (x == cx && (z == 1 || z == 7))
                        || (z == cz && (x == 1 || x == 7));
                if (walkwayMouth) continue;                          // keep aisles open
                if (x == doorX && z == 1) continue;                  // door approach clear
                pillar(b, x, z, yB, yT, BARREL);                     // 3-high barrel column
                barrels += 3;
            }
        }

        // ── 4) STRIPPED-LOG STRUCTURAL FRAME ────────────────────────────────
        // Full-height stripped-oak corner posts (replace the barrel/wall corner)
        // so the room reads as timber-framed storage, plus a header tie beam along
        // the wall top (y=3) breaking up the barrel mass into a banded wall.
        corners(b, x0, z0, x1, z1, yB, yT, STRIPPED_OAK_Y);
        line(b, yT, x0, z0, x1, z0, STRIPPED_OAK_X);                 // north header
        line(b, yT, x0, z1, x1, z1, STRIPPED_OAK_X);                 // south header
        line(b, yT, x0, z0, x0, z1, STRIPPED_OAK_X);                 // west header
        line(b, yT, x1, z0, x1, z1, STRIPPED_OAK_X);                 // east header

        // ── 5) SIGN LABELS on the barrel faces flanking each aisle ──────────
        // Wall signs at eye level (y=2) on the barrel column faces that border the
        // walkway aisles, facing the walker — the per-bay "item frame" labels. They
        // sit on the inner face of the columns adjacent to each walkway mouth.
        // West/east aisle (row z=cz): label the columns at z=cz±1 on x=1 and x=7,
        // facing inward along the aisle.
        b.set(1, yB + 1, cz - 1, bs("minecraft:oak_wall_sign[facing=south]"));
        b.set(1, yB + 1, cz + 1, bs("minecraft:oak_wall_sign[facing=north]"));
        b.set(7, yB + 1, cz - 1, bs("minecraft:oak_wall_sign[facing=south]"));
        b.set(7, yB + 1, cz + 1, bs("minecraft:oak_wall_sign[facing=north]"));
        // North/south aisle (column x=cx): label the columns at x=cx±1 on z=1 and z=7.
        b.set(cx - 1, yB + 1, 1, bs("minecraft:oak_wall_sign[facing=east]"));
        b.set(cx + 1, yB + 1, 1, bs("minecraft:oak_wall_sign[facing=west]"));
        b.set(cx - 1, yB + 1, 7, bs("minecraft:oak_wall_sign[facing=east]"));
        b.set(cx + 1, yB + 1, 7, bs("minecraft:oak_wall_sign[facing=west]"));

        // ── 6) LIGHTING: chain-hung lanterns down the walkway ───────────────
        // Hang from the ceiling (y4) on a single chain link (y3) to a lantern at y2,
        // along the walkway cross so the open aisles are lit (mob-free interior).
        // chainLantern(x,y,z,hangLen) puts the lantern at (x,y,z) and chains above.
        int lanternY = yB + 1;                                       // y=2 (eye level)
        chainLantern(b, cx, lanternY, 2, 1);                         // north walkway
        chainLantern(b, cx, lanternY, 6, 1);                         // south walkway
        chainLantern(b, 2, lanternY, cz, 1);                         // west walkway
        chainLantern(b, 6, lanternY, cz, 1);                         // east walkway
        chainLantern(b, cx, lanternY, cz, 1);                        // centre crossing

        return b.build();
    }

    /**
     * §G.brewing_room — a functional potion-brewing chamber, 7×7×5 (W×L×H) →
     * builder(7, 5, 7).
     *
     * <p>An enterable {@link #roomShell} the player walks into and stocks with fuel
     * (blaze powder) and ingredients to brew. Printed as the working SHELL: a row of
     * three brewing stands on a smooth-stone alchemy counter against the back wall, a
     * water-filled cauldron beside the counter for filling bottles, ingredient
     * storage (barrels + a chest), a small nether-wart bed in the back corner (the
     * single most-needed brewing ingredient grows in-place), bookshelf lab accents,
     * and glowstone + hanging-lantern lighting.
     *
     * <p><b>Printability.</b> Every block is vanilla and either FU-valued or
     * structural-free:
     * <ul>
     *   <li><b>brewing_stand</b> derives (blaze_rod + cobblestone), <b>cauldron</b>
     *       derives (iron ingots), <b>glowstone</b>=20@3, <b>bookshelf</b>=40@3,
     *       <b>barrel/chest</b> derive (planks/slabs), lanterns/chains derive.</li>
     *   <li><b>soul_sand</b>=1@1 and <b>netherrack</b>=1@1 are explicitly valued
     *       (mined-but-listed); the <b>nether_wart</b> crop is a {@code BushBlock}, so
     *       it's structural matter and prints free (its item is the seed, never the
     *       grown block) — same family as farmland crops.</li>
     *   <li><b>water</b> in the cauldron is itemless structural matter (prints free).</li>
     * </ul>
     * No glass panes or iron bars are used, so the stub-pane render gate is moot.
     *
     * <p>Layout (north = z=0 is the entrance wall; 7×7 = x0..6, z0..6; interior
     * x/z 1..5, standing height y=1..3, ceiling y=4):
     * <ul>
     *   <li><b>Shell</b> — stone-brick walls, polished-blackstone floor, stone-brick
     *       ceiling; walk-in oak door centred on the north wall, opening inward.</li>
     *   <li><b>Alchemy counter</b> — a smooth-stone counter along the back (south,
     *       z=5) wall at y=1 with three <b>brewing stands</b> set on top (y=2) at
     *       x=2,3,4, the canonical brewing-station row.</li>
     *   <li><b>Cauldron</b> — a water-filled cauldron at floor level in the SW work
     *       corner (x=1, z=5) for filling bottles.</li>
     *   <li><b>Nether-wart bed</b> — a 2×2 patch of <b>soul_sand</b> (y=1) in the SE
     *       corner with mature <b>nether_wart</b> growing on top (y=2); a netherrack
     *       lip frames it as a little nether garden.</li>
     *   <li><b>Storage</b> — two <b>barrels</b> and a <b>chest</b> along the west wall
     *       for bottles/ingredients.</li>
     *   <li><b>Lab accents</b> — a <b>bookshelf</b> pair flanking the counter and a
     *       <b>glowstone</b> block recessed in the ceiling centre.</li>
     *   <li><b>Lighting</b> — a chain-hung lantern over the work area + the ceiling
     *       glowstone keep the room lit (mob-free interior).</li>
     * </ul>
     */
    private static Blueprint brewingRoom() {
        Blueprint.Builder b = Blueprint.builder("Brewing Room", 7, 5, 7);

        BlueprintBlockState polishedBlackstone = bs("minecraft:polished_blackstone"); // floor (derives)
        BlueprintBlockState smoothStone = bs("minecraft:smooth_stone");                // counter top
        BlueprintBlockState soulSand = bs("minecraft:soul_sand");                      // wart bed (1@1)
        BlueprintBlockState netherrack = bs("minecraft:netherrack");                   // wart-bed lip (1@1)
        BlueprintBlockState netherWart = bs("minecraft:nether_wart[age=3]");           // BushBlock → structural
        BlueprintBlockState brewingStand = bs("minecraft:brewing_stand[has_bottle_0=false,has_bottle_1=false,has_bottle_2=false]");

        int x0 = 0, x1 = 6, z0 = 0, z1 = 6;   // 7×7 footprint
        int yB = 1, yT = 3;                   // wall course (y=1..3)
        int doorX = 3;                        // north-wall doorway (centred)

        // ── 1) SHELL: stone-brick walls, polished-blackstone floor, stone-brick ceiling ──
        // roomShell lays the floor (y0), wall ring (y1..3) and ceiling (y4); the
        // interior is left open per the air-skip rule so it's enterable.
        roomShell(b, x0, 0, z0, x1, yT + 1, z1, STONE_BRICKS, polishedBlackstone, STONE_BRICKS);

        // ── 2) NORTH-WALL DOORWAY (centred), opens inward ───────────────────
        door2(b, doorX, yB, z0, "oak", "N");

        // ── 3) ALCHEMY COUNTER + BREWING-STAND ROW (back/south wall) ────────
        // Smooth-stone counter at y=1 spanning x=1..5 against the south wall (z=5),
        // with three brewing stands set on top (y=2) at x=2,3,4 — the classic
        // three-station brewing row. The counter doubles as the wart-bed lip later.
        line(b, yB, 1, 5, 5, 5, smoothStone);
        b.set(2, yB + 1, 5, brewingStand);
        b.set(3, yB + 1, 5, brewingStand);
        b.set(4, yB + 1, 5, brewingStand);

        // ── 4) CAULDRON (water source) — SW work corner, floor level ────────
        // Cauldron at (1,1,4) just in front of the counter; fill it so bottles can
        // be drawn. WATER inside is itemless structural matter (prints free).
        BlueprintBlockState waterCauldron = bs("minecraft:water_cauldron[level=3]");
        b.set(1, yB, 4, waterCauldron);

        // ── 5) NETHER-WART BED — SE corner, a little nether garden ──────────
        // A 2×2 soul-sand patch at (4..5, z=3..4) on the floor (y=1) with mature
        // nether_wart growing on top (y=2). A netherrack lip on the inner edge frames
        // it. soul_sand & netherrack are valued; the wart crop is a BushBlock (free).
        for (int x = 4; x <= 5; x++) {
            for (int z = 3; z <= 4; z++) {
                b.set(x, yB, z, soulSand);
                b.set(x, yB + 1, z, netherWart);
            }
        }
        // netherrack lip on the two interior-facing edges (x=3 col, z=2 row) at floor
        b.set(3, yB, 3, netherrack);
        b.set(3, yB, 4, netherrack);
        b.set(4, yB, 2, netherrack);
        b.set(5, yB, 2, netherrack);

        // ── 6) INGREDIENT STORAGE — west wall (x=1) ─────────────────────────
        // Two barrels stacked + a chest at floor level along the west wall for
        // bottles and reagents. Kept off the door approach (z=1 left clear).
        b.set(1, yB, 2, CHEST);
        b.set(1, yB, 3, BARREL);
        b.set(1, yB + 1, 3, BARREL);   // a second barrel stacked for capacity

        // ── 7) LAB ACCENTS — bookshelves flanking the counter ──────────────
        // A bookshelf at each back corner (on the counter's flanks) for the
        // alchemist's-library feel. bookshelf=40@3.
        b.set(1, yB + 1, 5, BOOKSHELF);
        b.set(5, yB + 1, 5, BOOKSHELF);

        // ── 8) LIGHTING — ceiling glowstone + a chain-hung lantern ─────────
        // Recess a glowstone block into the ceiling centre (overwrites the ceiling
        // cell) and hang a lantern over the work area so the room is fully lit.
        b.set(3, yT + 1, 3, GLOWSTONE);          // ceiling centre (y=4)
        chainLantern(b, 3, yB + 1, 3, 1);        // lantern at y=2 over the centre, chain to y=3

        return b.build();
    }

    /**
     * §G.super_smelter — a STATIC, self-working automatic furnace bank, 7×7×5
     * (W×H×D) → builder(7, 5, 7).
     *
     * <p>The classic survival "super smelter": you drop raw ore (or food) into the
     * top INPUT chest, a hopper line splits it across a row of furnaces, a fuel
     * hopper feeds coal/charcoal into the furnaces' fuel slots, and an OUTPUT
     * hopper line under the furnaces sweeps the smelted product into a collection
     * chest at the bottom. Printed as the working SHELL — every block is placed in
     * a functioning configuration; the player only supplies ore + fuel. A
     * comparator/repeater readout line off the output chest is the canonical
     * "auto-shutoff / item-counter" wiring you see in the tutorials.
     *
     * <p>Every printed block is vanilla and FU-valued or derives for free:
     * stone_bricks/smooth_stone/smooth_stone_slab/stone (housing), furnace, hopper,
     * chest, comparator, repeater, redstone_wire, redstone_torch (all derive from
     * redstone + stone/quartz/wood), glowstone (light). No glass panes or iron bars
     * are used, so the stub-pane render gate is trivially satisfied.
     *
     * <p>Vertical layout (z is depth; south = +z = the front/access face):
     * <ul>
     *   <li><b>y=0</b> — smooth-stone base slab (the machine's footing).</li>
     *   <li><b>y=1</b> — OUTPUT layer: a 5-wide hopper line under the furnace row
     *       feeding east → an output chest, flanked by stone-brick housing. The
     *       comparator/repeater/redstone readout runs along the front lip here.</li>
     *   <li><b>y=2</b> — FURNACE BANK: five furnaces facing south (out the open
     *       front), each sitting on the output hopper below it, plus the fuel
     *       hopper line behind them (north, z=1) feeding the furnaces' sides.</li>
     *   <li><b>y=3</b> — INPUT layer: a hopper line over the furnace tops, fed from
     *       the input chest, distributing ore down into each furnace.</li>
     *   <li><b>y=4</b> — smooth-stone roof + glowstone light; the input chest sits
     *       on the roof rim at the back so the player can reach it.</li>
     * </ul>
     */
    private static Blueprint superSmelter() {
        Blueprint.Builder b = Blueprint.builder("Super Smelter", 7, 5, 7);

        BlueprintBlockState smoothStone = bs("minecraft:smooth_stone");           // base + roof
        BlueprintBlockState smoothSlab  = bs("minecraft:smooth_stone_slab[type=bottom]");
        // all FU-valued / derive-for-free:
        BlueprintBlockState furnaceS = bs("minecraft:furnace[facing=south,lit=false]");
        BlueprintBlockState comparator = bs("minecraft:comparator[facing=north,mode=compare,powered=false]");
        BlueprintBlockState repeater   = bs("minecraft:repeater[facing=north,delay=1,locked=false,powered=false]");
        BlueprintBlockState wire       = bs("minecraft:redstone_wire[east=none,north=none,power=0,south=none,west=none]");
        BlueprintBlockState rsTorch    = bs("minecraft:redstone_torch[lit=true]");

        int x0 = 0, x1 = 6, z0 = 0, z1 = 6;   // 7×7 footprint
        int bankX0 = 1, bankX1 = 5;           // 5-wide furnace bank columns
        int bankZ  = 3;                        // furnace bank depth row (centre)

        // ── 1) BASE SLAB at y=0 — smooth-stone footing under the whole machine ──
        floor(b, 0, x0, z0, x1, z1, smoothStone);

        // ── 2) STONE-BRICK HOUSING — back + side walls y=1..3, front (south) open ──
        // North wall (z=0), west wall (x=0), east wall (x=6) rise y=1..3 as a tidy
        // stone-brick shroud. The south face (z=6) is LEFT OPEN so the furnaces vent
        // and the player can read/extract from the front.
        for (int y = 1; y <= 3; y++) {
            line(b, y, x0, z0, x1, z0, STONE_BRICKS);   // north wall
            line(b, y, x0, z0, x0, z1 - 1, STONE_BRICKS); // west wall (stop short of open front)
            line(b, y, x1, z0, x1, z1 - 1, STONE_BRICKS); // east wall (stop short of open front)
        }

        // ── 3) OUTPUT LAYER, y=1 — hopper line under the furnaces → output chest ──
        // Five hoppers along the bank row (x=1..5, z=3) all face EAST so product
        // funnels to the east end, where an output chest (x=5..east) collects it.
        // The hopper at x=5 feeds the chest sitting one block east-ish; we place the
        // output chest at (x=5, z=4) facing the player and aim the last hopper into it.
        for (int x = bankX0; x <= bankX1 - 1; x++) {
            b.set(x, 1, bankZ, bs("minecraft:hopper[enabled=true,facing=east]"));   // → next hopper east
        }
        // terminal hopper drops DOWN into the output chest beneath/beside it
        b.set(bankX1, 1, bankZ, bs("minecraft:hopper[enabled=true,facing=south]")); // → output chest (south)
        // OUTPUT CHEST at the front-east, facing south (toward the player)
        b.set(bankX1, 1, bankZ + 1, CHEST);
        // stone-brick shelf under the output run so it reads as a contained channel
        b.set(bankX0 - 0, 1, bankZ - 1, STONE_BRICKS); // (1,1,2) back lip of channel — harmless filler

        // ── 4) COMPARATOR / REPEATER READOUT — front lip at y=1 (south, z=5) ──────
        // The canonical "is-it-done / item-counter" wiring: a comparator reading the
        // output chest, a length of redstone wire, a repeater to boost it, and a
        // redstone torch as the indicator lamp driver. Runs along z=5 (just inside the
        // open front) on the smooth-stone base — purely the readout the tutorials show.
        b.set(bankX1, 1, bankZ + 2, comparator);   // (5,1,5) comparator facing north toward the chest
        b.set(bankX1 - 1, 1, bankZ + 2, wire);     // (4,1,5) redstone trail
        b.set(bankX1 - 2, 1, bankZ + 2, repeater); // (3,1,5) repeater boosting the line
        b.set(bankX1 - 3, 1, bankZ + 2, wire);     // (2,1,5) redstone trail
        b.set(bankX1 - 4, 1, bankZ + 2, rsTorch);  // (1,1,5) indicator torch (lit)

        // ── 5) FURNACE BANK, y=2 — five furnaces facing south (out the open front) ──
        // Each furnace sits directly above its output hopper (drops smelted product
        // straight down into the y=1 hopper line).
        for (int x = bankX0; x <= bankX1; x++) {
            b.set(x, 2, bankZ, furnaceS);
        }

        // ── 6) FUEL HOPPER LINE, y=2 (behind the bank, z=2) → into furnace sides ──
        // A hopper line one row north of the furnaces, each hopper facing south so it
        // pushes fuel sideways into the adjacent furnace's fuel slot. Fed from the
        // fuel chest at the west end.
        for (int x = bankX0; x <= bankX1; x++) {
            b.set(x, 2, bankZ - 1, bs("minecraft:hopper[enabled=true,facing=south]")); // → furnace fuel slot
        }
        // FUEL CHEST at the west end of the fuel line (player tops up coal here)
        b.set(bankX0 - 1 + 1, 2, bankZ - 2, CHEST); // (1,2,1) feeds the fuel line southward
        b.set(bankX0, 2, bankZ - 2, bs("minecraft:hopper[enabled=true,facing=south]")); // chest → fuel line

        // ── 7) INPUT LAYER, y=3 — hopper line over furnace tops, fed by input chest ──
        // Five hoppers above the furnace row (x=1..5, z=3) each face DOWN so ore drops
        // straight into the furnace below. They're chained west→east from the input
        // chest so a single drop distributes across the bank.
        for (int x = bankX0; x <= bankX1; x++) {
            b.set(x, 3, bankZ, bs("minecraft:hopper[enabled=true,facing=down]")); // → furnace top below
        }
        // a west-end feeder hopper carries the input chest's contents east into the line
        b.set(bankX0, 3, bankZ - 1, bs("minecraft:hopper[enabled=true,facing=south]")); // input → bank

        // ── 8) ROOF y=4 + INPUT CHEST + LIGHT ────────────────────────────────────
        // Smooth-stone roof over the machine, a glowstone block recessed into the
        // centre for full lighting, and the INPUT chest sitting on the back-roof rim
        // (x=1, z=1) where the player tips in raw ore — it falls to the y=3 feeder.
        floor(b, 4, x0, z0, x1, z1, smoothStone);
        b.set(3, 4, 3, GLOWSTONE);                 // ceiling-centre light
        // INPUT chest perched on the roof at the back-west, reachable from outside
        b.set(bankX0, 4, bankZ - 1, CHEST);        // (1,4,2) input chest on the roof rim
        // smooth-stone-slab catwalk lip along the open front edge for a tidy finish
        for (int x = x0; x <= x1; x++) {
            b.set(x, 4, z1, smoothSlab);           // front roof eave (z=6) as a slab brow
        }

        return b.build();
    }

    /**
     * §G.smithy_workshop — an ENTERABLE stone-and-timber blacksmith's WORKSHOP,
     * 7×9×6 (W×D×H) → builder(7, 6, 9). Distinct from the open-front {@code blacksmith}
     * shop (a 7×6×6 lean-to): this is the enclosed, workstation-focused interior a
     * survival player walks into to grind, smith, and stonecut.
     *
     * <p>The whole-build floor sits at {@code y=0} (walkable), walls rise {@code y=1..4},
     * and a stone-brick ceiling closes the top at {@code y=5}. The interior above the
     * floor is deliberately left unset (air-skip) so the player can enter through the
     * north doorway and stand inside. Axes: x=W(0..6), y=up(0..5), z=depth(0..8);
     * south (+z, z=8) is the back wall, north (z=0) is the entry face.
     *
     * <p>Every printed block is vanilla and FU-valued or itemless-structural:
     * <ul>
     *   <li><b>shell</b> — cobblestone lower courses + stone-brick upper courses,
     *       smooth-stone floor, stone-brick ceiling, spruce-log corner posts and a
     *       spruce-plank ceiling-beam grid (the "timber" half of stone+timber). All
     *       derive or are valued (cobblestone/stone_bricks/smooth_stone/spruce_log/
     *       spruce_planks).</li>
     *   <li><b>caged-lava forge</b> (back-right corner) — a {@code lava} source sat on
     *       {@code netherrack}, walled on its two open inner faces by {@code iron_bars}.
     *       RENDER-SAFETY: every bar is flanked along a HORIZONTAL axis by either the
     *       solid stone-brick wall behind it, another bar, or the netherrack/forge
     *       frame, so each bar has a connecting neighbour and renders as a proper cage
     *       (never an invisible stub — see {@code CuratedBlueprintRenderIntegrityGameTests}).
     *       A cobblestone chimney CAPS the lava from y=2 up to the ceiling so the source
     *       cell survives as a glowing, caged forge rather than being overwritten.</li>
     *   <li><b>workstations</b> — {@code anvil}, {@code smithing_table}, {@code grindstone},
     *       {@code stonecutter}, {@code blast_furnace}, {@code furnace} (all valued /
     *       recipe-derived), arranged around the walls with a clear central walk lane.</li>
     *   <li><b>storage + tool racks</b> — barrels along the west wall; tool "racks" are
     *       {@code oak_wall_sign}s (item frames are ENTITIES and can't print, so the
     *       wall-mounted display is a sign instead, per the build rules).</li>
     *   <li><b>lighting</b> — a recessed {@code glowstone} in the ceiling centre plus a
     *       chain-hung {@code lantern} over the anvil and two wall-backed lanterns.</li>
     * </ul>
     */
    private static Blueprint smithyWorkshop() {
        Blueprint.Builder b = Blueprint.builder("Smithy Workshop", 7, 6, 9);

        BlueprintBlockState smoothStone = bs("minecraft:smooth_stone");           // floor (derives)
        BlueprintBlockState netherrack  = bs("minecraft:netherrack");             // forge bed (1@1)
        BlueprintBlockState stonecutter = bs("minecraft:stonecutter[facing=north]"); // derives (3 stone + iron)
        // wall signs face INTO the room (away from the wall they hang on). A wall sign's
        // facing = the direction it looks; it attaches to the block on the OPPOSITE side.
        BlueprintBlockState oakWallSignN = bs("minecraft:oak_wall_sign[facing=north]"); // hangs on a south(+z) wall
        BlueprintBlockState oakWallSignE = bs("minecraft:oak_wall_sign[facing=east]");  // hangs on a west(-x) wall
        BlueprintBlockState oakWallSignW = bs("minecraft:oak_wall_sign[facing=west]");  // hangs on an east(+x) wall

        int x0 = 0, x1 = 6, z0 = 0, z1 = 8;   // 7×9 footprint
        int yB = 1, yT = 4;                   // wall course (y=1..4)
        int doorX = 3;                        // north-wall doorway (centred)

        // ── 1) SHELL: smooth-stone floor (y0), wall ring (y1..4), stone-brick ceiling (y5) ──
        // roomShell lays the floor, the four-wall ring and the ceiling; the interior is
        // left open per the air-skip rule so it's enterable. Walls are stone-brick by
        // default here; the lower two courses are re-skinned to cobblestone below for the
        // weathered stone+timber look.
        roomShell(b, x0, 0, z0, x1, yT + 1, z1, STONE_BRICKS, smoothStone, STONE_BRICKS);

        // ── 2) STONE+TIMBER SKIN: cobble lower courses (y=1..2) + spruce-log corner posts ──
        // Re-skin the y=1..2 wall ring to cobblestone (leaving stone-brick at y=3..4) for
        // the classic two-tone rustic forge wall. Corner posts are spruce logs the full
        // wall height (they overwrite the corner cells with equal-height timber, no nub).
        walls(b, x0, z0, x1, z1, yB, yB + 1, COBBLE);
        corners(b, x0, z0, x1, z1, yB, yT, SPRUCE_LOG_Y);
        // ceiling tie-beams: two spruce-plank beams spanning W under the ceiling for the
        // exposed-rafter timber read (overwrite the y=4 wall-top course at z=3 and z=5).
        line(b, yT, x0, 3, x1, 3, SPRUCE_PLANKS);
        line(b, yT, x0, 5, x1, 5, SPRUCE_PLANKS);

        // ── 3) NORTH-WALL DOORWAY (centred), opens inward ───────────────────
        door2(b, doorX, yB, z0, "oak", "N");
        // window slits flanking the forge glow: glass panes high on the side walls,
        // each flanked along its wall axis (z±1) by solid wall cells so it connects
        // and renders (never a stub). West wall (x=0) and east wall (x=6) at y=3, z=2.
        window2(b, x0, 3, 2, GLASS_PANE, null);
        window2(b, x1, 3, 2, GLASS_PANE, null);

        // ── 4) CAGED-LAVA FORGE — back-right corner ─────────────────────────
        // The forge heart: a lava source at (5,1,7), one cell in from the back (z=8)
        // and east (x=6) shell walls. A netherrack hearth at floor level frames the
        // open diagonal corner (4,1,6). The two OPEN inner faces — north (5,1,6) and
        // west (4,1,7) — are walled by iron_bars so the player can't fall in but the
        // glow shines through; the back and east faces are the solid stone-brick shell.
        // RENDER-SAFETY (each iron_bars cell needs ONE connecting ±x/±z neighbour):
        //   • (4,1,7) west bar  → +z neighbour (4,1,6) is netherrack (sturdy full face) ✓
        //   • (5,1,6) north bar → +x neighbour (6,1,6) is the east stone-brick wall ✓
        //                         and -x neighbour (4,1,6) is netherrack ✓
        //   • (4,2,7) upper bar → -y is (4,1,7) bars, +z (4,2,6) is the upper north bar ✓
        //   • (5,2,6) upper bar → +x (6,2,6) east wall, -x (4,2,6) upper west bar ✓
        //   • (4,2,6) corner bar → connects to BOTH upper face bars (±x and ±z) ✓
        b.set(5, yB, 7, LAVA);           // lava source (forge heart)
        b.set(4, yB, 6, netherrack);     // hearth corner (diagonal, floor level)
        // iron-bar cage on the two inner-facing faces, y=1..2:
        b.set(4, yB, 7, IRON_BARS);      // west face, lower
        b.set(4, yB + 1, 7, IRON_BARS);  // west face, upper
        b.set(5, yB, 6, IRON_BARS);      // north face, lower
        b.set(5, yB + 1, 6, IRON_BARS);  // north face, upper
        b.set(4, yB + 1, 6, IRON_BARS);  // upper diagonal corner bar — ties both upper faces
        // chimney: cobble pillar CAPPING the lava from y=2 to the ceiling (y=4) so the
        // source cell at y=1 survives as a glowing, caged forge rather than dead stone.
        pillar(b, 5, 7, yB + 1, yT, COBBLE);

        // ── 5) WORKSTATIONS around the walls, central walk lane left clear ──
        // Back wall (z=7, west of the forge): the heavy smithing line.
        b.set(1, yB, 7, ANVIL);            // anvil, faces north (out toward the player)
        b.set(2, yB, 7, SMITHING_TABLE);   // smithing table beside the anvil
        b.set(3, yB, 7, BLAST_FURNACE);    // blast furnace
        // East wall (x=5..6) — secondary heat + stonework
        b.set(5, yB, 5, FURNACE);          // furnace mid-east wall
        b.set(5, yB, 3, stonecutter);      // stonecutter for the masonry side of the smithy
        // West wall (x=1) — finishing + storage
        b.set(1, yB, 5, GRINDSTONE);       // grindstone (floor-mounted) for repairs
        b.set(1, yB, 3, BARREL);           // material barrels
        b.set(1, yB + 1, 3, BARREL);       // a second barrel stacked for capacity
        b.set(1, yB, 2, CHEST);            // ingot chest near the door

        // ── 6) TOOL RACKS = WALL SIGNS (item frames are entities, can't print) ──
        // Wall-mounted "tool racks" rendered as oak wall signs (the printable stand-in
        // for the item-frame display the build calls for). Each sign occupies an interior
        // cell one block in front of a solid wall and faces into the room; the wall it
        // attaches to is opposite its facing.
        b.set(2, yB + 1, 7, oakWallSignN);  // hangs on back wall (2,*,8), above the smithing table
        b.set(3, yB + 1, 7, oakWallSignN);  // hangs on back wall (3,*,8), above the blast furnace
        b.set(1, yB + 1, 5, oakWallSignE);  // hangs on west wall (0,*,5), above the grindstone
        b.set(5, yB + 1, 4, oakWallSignW);  // hangs on east wall (6,*,4), faces W into the room

        // ── 7) LIGHTING — ceiling glowstone + a chain-hung lantern over the anvil ──
        b.set(3, yT + 1, 4, GLOWSTONE);     // ceiling-centre light (y=5)
        // lantern hung over the anvil (1,1,7): lantern at y=3, one chain link at y=4
        // attaching up to the solid stone-brick ceiling at (1,5,6).
        chainLantern(b, 1, yT - 1, 6, 1);   // lantern (1,3,6), chain (1,4,6) → ceiling (1,5,6)
        // wall-backed lanterns flanking the door for the entry glow (sit on the floor
        // against the side walls, just inside the threshold).
        b.set(1, yB, 1, LANTERN);
        b.set(5, yB, 1, LANTERN);

        return b.build();
    }

    /**
     * §G.map_room — an ENTERABLE cartography study, 7×7×6 (W×D×H) → builder(7, 6, 7).
     *
     * <p>A map-maker's study the survival player walks into to chart their world. Real
     * map displays hang printed maps in ITEM FRAMES — but item frames are ENTITIES and
     * can't print, so this build supplies the ROOM and a decorative MAP-THEMED back wall
     * instead, and the player hangs their own maps in frames afterward. The centrepiece
     * is a grid of dyed wall BANNERS (banners ARE blocks; dyed variants normalise to the
     * base white_banner FU value) framed by dark-oak log trim — a "tapestry map wall" —
     * and a COMPASS-ROSE floor laid in dyed wool / terracotta / concrete (all valued via
     * the cosmetic-colour fallback).
     *
     * <p>The whole-build floor sits at {@code y=0} (walkable, carries the compass rose),
     * walls rise {@code y=1..4}, and a dark-oak-plank ceiling closes the top at {@code y=5}.
     * The interior above the floor is left unset (air-skip) so the player enters through
     * the north doorway and stands inside. Axes: x=W(0..6), y=up(0..5), z=depth(0..6);
     * south (+z, z=6) is the map-wall (back), north (z=0) is the entry face.
     *
     * <p>Every printed block is vanilla and FU-valued or itemless-structural:
     * <ul>
     *   <li><b>shell</b> — dark-oak-plank walls + floor + ceiling, dark-oak-log corner
     *       posts (all derive / are valued).</li>
     *   <li><b>map wall</b> (south, z=6) — a 5-wide × 3-tall grid of dyed
     *       {@code wall_banner[facing=north]} (hang on the south wall, face into the room)
     *       at x=1..5, y=2..4, framed by stripped-dark-oak-log trim. wall_banner's item is
     *       the banner item, which is valued (dyed → white_banner).</li>
     *   <li><b>compass-rose floor</b> — white-wool N–S and E–W axis lines through the
     *       centre, a red-wool hub, and terracotta/concrete quadrant accents (dyed
     *       cosmetic variants → valued bases).</li>
     *   <li><b>furniture</b> — {@code cartography_table} (derives via paper), two
     *       {@code lectern}s, {@code chiseled_bookshelf} + {@code bookshelf} along the
     *       walls (all valued / recipe-derived); blue-carpet runners (dyed → white_carpet).</li>
     *   <li><b>lighting</b> — recessed {@code glowstone} ceiling centre + a chain-hung
     *       {@code lantern} over the cartography table and two wall-backed lanterns. No
     *       glass panes or iron bars are used, so the stub-pane render gate is trivially
     *       satisfied.</li>
     * </ul>
     */
    private static Blueprint mapRoom() {
        Blueprint.Builder b = Blueprint.builder("Map Room", 7, 6, 7);

        BlueprintBlockState darkOakLogY = bs("minecraft:dark_oak_log[axis=y]");
        BlueprintBlockState strippedDarkOakY = bs("minecraft:stripped_dark_oak_log[axis=y]");
        BlueprintBlockState strippedDarkOakX = bs("minecraft:stripped_dark_oak_log[axis=x]");
        BlueprintBlockState chiseledBookshelf = bs("minecraft:chiseled_bookshelf[facing=north,slot_0_occupied=false,slot_1_occupied=false,slot_2_occupied=false,slot_3_occupied=false,slot_4_occupied=false,slot_5_occupied=false]");
        // furniture facing INTO the room from the wall they back onto
        BlueprintBlockState lecternW = bs("minecraft:lectern[facing=west,has_book=false,powered=false]");
        BlueprintBlockState lecternE = bs("minecraft:lectern[facing=east,has_book=false,powered=false]");
        // compass-rose palette (dyed cosmetic variants → valued bases)
        BlueprintBlockState whiteWool = bs("minecraft:white_wool");
        BlueprintBlockState redWool = bs("minecraft:red_wool");
        BlueprintBlockState blueTerracotta = bs("minecraft:blue_terracotta");
        BlueprintBlockState lightBlueConcrete = bs("minecraft:light_blue_concrete");
        BlueprintBlockState blueCarpet = bs("minecraft:blue_carpet");
        // map-wall banner colours (dyed → normalise to white_banner; wall variant
        // hangs on the +z wall and faces north into the room)
        String[] bannerColors = {"red", "blue", "green", "yellow", "purple"};

        int x0 = 0, x1 = 6, z0 = 0, z1 = 6;   // 7×7 footprint
        int yB = 1, yT = 4;                   // wall course (y=1..4)
        int cx = 3, cz = 3;                   // room centre
        int doorX = 3;                        // north-wall doorway (centred)

        // ── 1) SHELL: dark-oak-plank floor (y0), wall ring (y1..4), ceiling (y5) ──
        // roomShell lays the floor, the four-wall ring and the ceiling; the interior is
        // left open per the air-skip rule so it's enterable.
        roomShell(b, x0, 0, z0, x1, yT + 1, z1, DARK_OAK_PLANKS, DARK_OAK_PLANKS, DARK_OAK_PLANKS);

        // ── 2) DARK-OAK-LOG CORNER POSTS + NORTH-WALL DOORWAY ───────────────
        // Full-height dark-oak-log corner posts (overwrite the corner cells, no nub),
        // and a centred north doorway opening inward.
        corners(b, x0, z0, x1, z1, yB, yT, darkOakLogY);
        door2(b, doorX, yB, z0, "dark_oak", "N");

        // ── 3) COMPASS-ROSE FLOOR (y=0) ─────────────────────────────────────
        // Re-skin the dark-oak-plank floor with a compass rose: white-wool N–S axis
        // (x=cx) and E–W axis (z=cz) lines spanning the interior, a red-wool hub at the
        // centre, and terracotta/concrete diagonal accents in the four quadrants so the
        // rose reads from above. All cells stay inside the interior (x,z ∈ 1..5).
        line(b, 0, cx, 1, cx, 5, whiteWool);          // N–S axis
        line(b, 0, 1, cz, 5, cz, whiteWool);          // E–W axis
        b.set(cx, 0, cz, redWool);                    // hub
        // diagonal accents one step out from the hub in each quadrant
        b.set(cx - 1, 0, cz - 1, blueTerracotta);
        b.set(cx + 1, 0, cz - 1, lightBlueConcrete);
        b.set(cx - 1, 0, cz + 1, lightBlueConcrete);
        b.set(cx + 1, 0, cz + 1, blueTerracotta);
        // outer cardinal points (the rose's "tips") in red wool
        b.set(cx, 0, 1, redWool);   // N tip
        b.set(cx, 0, 5, redWool);   // S tip
        b.set(1, 0, cz, redWool);   // W tip
        b.set(5, 0, cz, redWool);   // E tip

        // ── 4) MAP WALL (south, z=6) — banner tapestry framed by log trim ───
        // A 5-wide × 3-tall grid of dyed wall banners at x=1..5, y=2..4 hung on the
        // south wall (facing=north → look into the room). The wall_banner block's item
        // is the banner item (dyed → white_banner FU value), so it prints. Vertical
        // stripped-dark-oak-log trim columns flank the grid (x=0,6 already log-free at
        // the corners; we add inner pilasters at the grid edges via the wall top header).
        for (int gx = 1; gx <= 5; gx++) {
            String color = bannerColors[gx - 1];
            BlueprintBlockState wallBanner =
                    bs("minecraft:" + color + "_wall_banner[facing=north]");
            for (int gy = 2; gy <= 4; gy++) {
                b.set(gx, gy, z1, wallBanner);
            }
        }
        // stripped-log header beam capping the map wall (y=4 was overwritten by the top
        // banner row at z=6; lay the trim one course of stripped log along the wall TOP
        // at z=5, the row just in front, as an exposed-rafter frame over the tapestry).
        line(b, yT, x0, z1 - 1, x1, z1 - 1, strippedDarkOakX);

        // ── 5) CARTOGRAPHY STATION — centred against the map wall ───────────
        // The cartography table sits centred on the floor one cell in front of the map
        // wall (3,1,5), flanked by two lecterns (atlas stands) angled inward. The table
        // derives via paper; lecterns are valued.
        b.set(cx, yB, 5, CARTOGRAPHY_TABLE);
        b.set(1, yB, 5, lecternE);   // west lectern, faces east into the room
        b.set(5, yB, 5, lecternW);   // east lectern, faces west into the room

        // ── 6) LIBRARY WALLS — bookshelves along the side walls ─────────────
        // Bookshelf / chiseled-bookshelf runs along the west (x=1) and east (x=5) walls
        // at floor level, with a second course of plain bookshelves stacked for the
        // floor-to-ceiling study feel. Kept off the door approach (z=1 left clear on the
        // centre column) and the cartography station (z=5 handled above).
        for (int z = 2; z <= 4; z++) {
            b.set(1, yB, z, BOOKSHELF);
            b.set(1, yB + 1, z, chiseledBookshelf);
            b.set(5, yB, z, BOOKSHELF);
            b.set(5, yB + 1, z, chiseledBookshelf);
        }

        // ── 7) CARPET RUNNERS — blue carpet flanking the central rose ───────
        // Two blue-carpet runners on the floor either side of the compass axis (x=2 and
        // x=4 columns, z=2..4) frame the rose and soften the study. Carpet is a thin
        // block sitting ON the floor; dyed → white_carpet FU value.
        for (int z = 2; z <= 4; z++) {
            if (z == cz) continue;          // leave the E–W axis line visible
            b.set(2, 0, z, blueCarpet);
            b.set(4, 0, z, blueCarpet);
        }

        // ── 8) LIGHTING — ceiling glowstone + chain lantern + wall lanterns ──
        b.set(cx, yT + 1, cz, GLOWSTONE);          // ceiling-centre light (y=5)
        // lantern hung over the cartography table (3,1,5): lantern y=3, chain y=4 → ceiling
        chainLantern(b, cx, yT - 1, 5, 1);         // lantern (3,3,5), chain (3,4,5) → ceiling (3,5,5)
        // wall-backed lanterns flanking the door for the entry glow
        b.set(1, yB, 1, LANTERN);
        b.set(5, yB, 1, LANTERN);

        return b.build();
    }

    /**
     * §G.library — a GRAND LIBRARY: a tall, enterable two-storey reading hall.
     * 11×11 footprint → builder(11, 14, 11); disc T1. Vanilla, FU-valued blocks
     * only: stone bricks, dark-oak planks/logs/stairs/slabs/fence, bookshelf,
     * chiseled_bookshelf, lectern, ladder, carpet, glass, chain + lantern.
     *
     * <p>The build reads as a grand hall: a stone-brick shell with dark-oak log
     * framing, floor-to-gallery bookshelf + chiseled-bookshelf walls, a U-shaped
     * second-floor mezzanine (dark-oak-fence railings around an open central
     * atrium) reached by a ladder, reading nooks (lecterns over carpet runners),
     * tall glass windows down both long walls, hanging chandeliers (chains +
     * lanterns) over the atrium, and a peaked dark-oak gable roof.
     *
     * <p>Section structure (y from the ground up, H=14 → y 0..13):
     * <ul>
     *   <li>{@code y=0} dark-oak-plank floor (the walkable ground-storey surface).</li>
     *   <li>{@code y=1..7} stone-brick wall ring with dark-oak-log corner posts +
     *       mid-wall pilasters; a centred north doorway opening inward; tall glass
     *       windows down both long walls (panes flanked by wall → render-safe).</li>
     *   <li>{@code y=1..3} ground storey: bookshelf / chiseled-bookshelf stacks
     *       lining the side walls, lectern reading nooks on carpet runners, a
     *       central aisle; a ladder climbs the SE corner to the mezzanine.</li>
     *   <li>{@code y=4} U-shaped mezzanine plank floor (the central atrium is left
     *       open so the hall reads double-height), {@code y=5} dark-oak-fence
     *       railing around the atrium edge + a ladder hatch.</li>
     *   <li>{@code y=5..7} upper storey: more bookshelf stacks on the gallery, lit
     *       by the upper windows.</li>
     *   <li>{@code y=8} dark-oak tie-beams span the hall; hanging chandeliers
     *       (chain + lantern) drop from them over the atrium.</li>
     *   <li>{@code y=8..13} dark-oak gable roof (ridge along X) with closed gable
     *       ends, peaking at y=13 (the H=14 ceiling).</li>
     * </ul>
     */
    private static Blueprint library() {
        Blueprint.Builder b = Blueprint.builder("Grand Library", 11, 14, 11);

        BlueprintBlockState darkOakLogY = bs("minecraft:dark_oak_log[axis=y]");
        BlueprintBlockState darkOakLogX = bs("minecraft:dark_oak_log[axis=x]");
        BlueprintBlockState darkOakFence = DARK_OAK_FENCE;
        BlueprintBlockState darkOakSlabBottom = bs("minecraft:dark_oak_slab[type=bottom]");
        BlueprintBlockState darkOakSlabTop = bs("minecraft:dark_oak_slab[type=top]");
        BlueprintBlockState redCarpet = bs("minecraft:red_carpet");
        BlueprintBlockState ladderE = bs("minecraft:ladder[facing=east,waterlogged=false]");
        // chiseled bookshelf facing into the room from the wall it backs onto
        BlueprintBlockState chiseledN = bs("minecraft:chiseled_bookshelf[facing=north,slot_0_occupied=false,slot_1_occupied=false,slot_2_occupied=false,slot_3_occupied=false,slot_4_occupied=false,slot_5_occupied=false]");
        BlueprintBlockState chiseledE = bs("minecraft:chiseled_bookshelf[facing=east,slot_0_occupied=false,slot_1_occupied=false,slot_2_occupied=false,slot_3_occupied=false,slot_4_occupied=false,slot_5_occupied=false]");
        BlueprintBlockState chiseledW = bs("minecraft:chiseled_bookshelf[facing=west,slot_0_occupied=false,slot_1_occupied=false,slot_2_occupied=false,slot_3_occupied=false,slot_4_occupied=false,slot_5_occupied=false]");
        BlueprintBlockState lecternE = bs("minecraft:lectern[facing=east,has_book=false,powered=false]");
        BlueprintBlockState lecternW = bs("minecraft:lectern[facing=west,has_book=false,powered=false]");

        int x0 = 0, x1 = 10, z0 = 0, z1 = 10;   // 11×11 footprint
        int cx = (x0 + x1) / 2;                  // 5
        int cz = (z0 + z1) / 2;                  // 5
        int wallH = 7;                           // wall plate (roof seats at y=8)
        int mezzY = 4;                           // mezzanine floor level
        int roofY = wallH + 1;                   // 8

        // ── 1) FLOOR (y=0) — dark-oak plank ground-storey surface ───────────
        floor(b, 0, x0, z0, x1, z1, DARK_OAK_PLANKS);

        // ── 2) WALL RING (y=1..7) — stone brick + dark-oak-log corner posts ──
        walls(b, x0, z0, x1, z1, 1, wallH, STONE_BRICKS);
        corners(b, x0, z0, x1, z1, 1, wallH, darkOakLogY);
        // mid-wall pilasters (dark-oak log) breaking up the long (west/east) faces —
        // visual framing AND solid pane flankers for the windows set either side.
        pillar(b, x0, cz, 1, wallH, darkOakLogY);
        pillar(b, x1, cz, 1, wallH, darkOakLogY);
        // a header band of dark-oak log along the north/south wall tops (y=wallH)
        line(b, wallH, x0, z0, x1, z0, darkOakLogX);
        line(b, wallH, x0, z1, x1, z1, darkOakLogX);

        // ── 3) NORTH DOORWAY (centred) opening inward ───────────────────────
        door2(b, cx, 1, z0, "dark_oak", "N");

        // ── 4) TALL GLASS WINDOWS down both long walls (render-safe panes) ──
        // Each pane sits in a wall cell flanked horizontally by stone-brick wall
        // (or the centre log pilaster), so it always has a connectable neighbour.
        // Two 2-tall windows per long wall, set either side of the mid pilaster.
        for (int z : new int[]{2, 3, 7, 8}) {
            for (int y = 3; y <= 5; y++) {
                window2(b, x0, y, z, GLASS_PANE, null); // west long wall
                window2(b, x1, y, z, GLASS_PANE, null); // east long wall
            }
        }
        // front (north) clerestory windows either side of the door
        for (int x : new int[]{2, 8}) {
            window2(b, x, 4, z0, GLASS_PANE, null);
            window2(b, x, 5, z0, GLASS_PANE, null);
        }
        // back (south) wall windows flanking the centre
        for (int x : new int[]{3, 7}) {
            for (int y = 3; y <= 5; y++) {
                window2(b, x, y, z1, GLASS_PANE, null);
            }
        }

        // ── 5) GROUND-STOREY BOOKSHELF WALLS (y=1..3) ───────────────────────
        // Floor-to-gallery bookshelf + chiseled-bookshelf stacks lining the side
        // walls (x=1 west run, x=9 east run), kept off the window columns and the
        // ladder corner. Chiseled shelves face into the hall.
        for (int z = 2; z <= 8; z++) {
            if (z == 5) continue;                 // leave the mid pilaster column clear
            if (z == 8) continue;                 // SE corner reserved for the ladder
            b.set(1, 1, z, BOOKSHELF);
            b.set(1, 2, z, BOOKSHELF);
            b.set(1, 3, z, chiseledE);            // faces east into the hall
            b.set(9, 1, z, BOOKSHELF);
            b.set(9, 2, z, BOOKSHELF);
            b.set(9, 3, z, chiseledW);            // faces west into the hall
        }
        // back-wall bookshelf bank (south, z=9 inner) flanking the centre
        for (int x : new int[]{2, 8}) {
            b.set(x, 1, 9, BOOKSHELF);
            b.set(x, 2, 9, BOOKSHELF);
            b.set(x, 3, 9, chiseledN);            // faces north into the hall
        }

        // ── 6) READING NOOKS — lecterns over red-carpet runners (y=1) ───────
        // Carpet is a thin block that occupies the cell ABOVE the plank floor (y=1).
        // Two runners (x=3, x=7 columns) frame the central aisle; lecterns (atlas
        // stands) sit at the ends of each runner, angled inward toward the aisle.
        // The ends carry the lecterns, so the carpet runs only on the cells between.
        for (int z = 4; z <= 6; z++) {
            b.set(3, 1, z, redCarpet);            // west runner
            b.set(7, 1, z, redCarpet);            // east runner
        }
        b.set(3, 1, 3, lecternE);                 // west nook lectern faces east
        b.set(7, 1, 3, lecternW);                 // east nook lectern faces west
        b.set(3, 1, 7, lecternE);
        b.set(7, 1, 7, lecternW);

        // ── 7) MEZZANINE (y=4) — U-shaped gallery, open central atrium ──────
        // A plank gallery floor around three sides (west, east, south runs) two
        // cells deep; the centre is left OPEN so the hall reads double-height.
        // A ladder hatch is left open at the SE access column.
        int hatchX = 8, hatchZ = 8;               // ladder hatch (SE)
        for (int x = 1; x <= 9; x++) {
            for (int z = 1; z <= 9; z++) {
                boolean onGallery = (x <= 2) || (x >= 8) || (z >= 8);
                if (!onGallery) continue;          // open atrium
                if (x == hatchX && z == hatchZ) continue; // ladder hatch
                b.set(x, mezzY, z, DARK_OAK_PLANKS);
            }
        }
        // ── 8) MEZZANINE RAILING (y=5) — dark-oak fence around the atrium edge
        // Fence posts ring the inner edge of the gallery (where it meets the open
        // atrium) so the player doesn't walk off. The inner edge runs along
        // x=2 / x=8 (between gallery and atrium) and z=7 (south gallery lip).
        for (int z = 1; z <= 7; z++) {
            b.set(2, mezzY + 1, z, darkOakFence);  // west gallery inner rail
            b.set(8, mezzY + 1, z, darkOakFence);  // east gallery inner rail
        }
        for (int x = 2; x <= 8; x++) {
            b.set(x, mezzY + 1, 7, darkOakFence);  // south gallery inner rail
        }
        // re-open the hatch + an access gap in the rail so the ladder is reachable
        b.set(hatchX, mezzY + 1, 7, darkOakSlabBottom); // step off the ladder onto the gallery

        // ── 9) LADDER — SE corner, climbs y=1..3 to the mezzanine hatch ─────
        // Backed by the east stone-brick wall: ladder faces east → attaches to the
        // block at (hatchX+1, *, hatchZ) = (9, *, 8), which is the wall ring.
        for (int y = 1; y <= mezzY - 1; y++) {
            b.set(hatchX, y, hatchZ, ladderE);
        }

        // ── 10) UPPER-STOREY BOOKSHELVES (y=5..6) on the gallery ───────────
        // Bookshelf stacks along the gallery back the upper windows; chiseled
        // shelves face the atrium so the gallery reads as more stacks.
        for (int z = 2; z <= 7; z++) {
            if (z == 5) continue;                  // mid pilaster
            b.set(1, 5, z, BOOKSHELF);
            b.set(1, 6, z, chiseledE);
            b.set(9, 5, z, BOOKSHELF);
            b.set(9, 6, z, chiseledW);
        }
        for (int x : new int[]{3, 7}) {
            b.set(x, 5, 9, BOOKSHELF);
            b.set(x, 6, 9, chiseledN);
        }

        // ── 11) TIE-BEAMS (y=8) + CHANDELIERS over the atrium ───────────────
        // Dark-oak log tie-beams span the hall at the wall plate; chandeliers
        // (chain up to the beam, hanging lantern below) drop over the open atrium.
        for (int z : new int[]{3, 5, 7}) {
            line(b, roofY, x0, z, x1, z, darkOakLogX);
        }
        // chandeliers hang under the z=5 tie-beam over the central atrium
        for (int x : new int[]{4, 6}) {
            chainLantern(b, x, roofY - 3, 5, 2);   // lantern y=5, chains y=6..7 → beam y=8
        }
        chainLantern(b, cx, roofY - 4, 5, 3);      // taller central chandelier (lantern y=4)

        // ── 12) GABLE ROOF (y=8..13) — dark oak, ridge along X, closed ends ─
        gableRoofX(b, x0, z0, x1, z1, roofY, "dark_oak_stairs", darkOakSlabTop);
        gableEndFill(b, x0, z0, x1, z1, roofY, STONE_BRICKS);

        // ── 13) ENTRY LIGHTING — lanterns flanking the door ─────────────────
        b.set(2, 1, 1, LANTERN);
        b.set(8, 1, 1, LANTERN);

        return b.build();
    }

    /**
     * §H.sky_bridge_segment — a TILEABLE suspended stone-brick walkway, 5×12×6
     * (W×L×H) → builder(5, 6, 12). "The most-searched build type": a clean span
     * you print over and over to chain a long elevated bridge across a ravine or
     * between sky islands.
     *
     * <p><b>Tiling contract.</b> The segment is authored so identical copies butt
     * end-to-end along +z with no seam: the deck height is a constant (y=4), the
     * twin stone-brick-wall railings run every z-row along both long edges
     * (x=0/x=4), and the support + lantern rhythm is keyed to the ABSOLUTE z index
     * with period 4 over a length of 12 (a clean 3 periods). A copy placed at z=12
     * continues that rhythm exactly: arch struts land at z=0,4,8 → 12,16,20 (even
     * 4-spacing across the join) and under-deck lanterns at z=2,6,10 → 14,18,22.
     * There are deliberately NO end caps — neither z=0 nor z=11 closes off — so two
     * copies read as one continuous bridge.
     *
     * <p>Layout (centre lane cx=2; W = x0..4, L = z0..11, H = y0..5):
     * <ul>
     *   <li><b>Deck (y=4)</b> — a solid 5-wide stone-brick walkway spanning the
     *       whole length; the only walkable surface, flat so it tiles flush.</li>
     *   <li><b>Railings (y=5)</b> — a stone-brick wall on each long edge (x=0 and
     *       x=4) at every z, an unbroken parapet that connects across the seam.</li>
     *   <li><b>Arch struts (z=0,4,8)</b> — under each strut row, twin stone-brick
     *       piers drop from the deck to y=0 at the edges (x=0/x=4), and a shallow
     *       transverse soffit arch of {@code half=top} stone-brick stairs (curling
     *       inward from each pier) plus a stone-brick keystone hangs beneath the
     *       deck at y=3, giving the suspended-span silhouette. Struts only every
     *       4th row leave open air between them (the "sky" gaps).</li>
     *   <li><b>Lanterns (z=2,6,10)</b> — a hanging lantern tucked under each deck
     *       edge (y=3, backed by the solid deck at y=4), lighting the underside on
     *       the off-beat between struts so the lit rhythm also tiles.</li>
     * </ul>
     *
     * <p>Vanilla blocks only, all FU-valued and disc-T1: stone_bricks and its
     * stairs/walls derive from stone bricks; the lantern derives from iron nuggets
     * + torch. No glass panes or iron bars, so the stub-pane render gate is moot.
     */
    private static Blueprint skyBridgeSegment() {
        Blueprint.Builder b = Blueprint.builder("Sky Bridge Segment", 5, 6, 12);
        int x0 = 0, x1 = 4;           // 5-wide deck
        int z0 = 0, z1 = 11;          // 12-long span (3 × period-4)
        int deckY = 4;                // constant deck height (tiles flush)
        int railY = deckY + 1;        // 5
        // inward-curling soffit stairs for the transverse under-deck arch
        BlueprintBlockState archW = bs("minecraft:stone_brick_stairs[facing=east,half=top,shape=straight]");
        BlueprintBlockState archE = bs("minecraft:stone_brick_stairs[facing=west,half=top,shape=straight]");

        // ── 1) DECK (y=4) — solid 5×12 stone-brick walkway ──────────────────
        floor(b, deckY, x0, z0, x1, z1, STONE_BRICKS);

        // ── 2) RAILINGS (y=5) — twin stone-brick-wall parapets, every z-row ──
        for (int z = z0; z <= z1; z++) {
            b.set(x0, railY, z, STONE_BRICK_WALL);
            b.set(x1, railY, z, STONE_BRICK_WALL);
        }

        // ── 3) ARCH STRUTS (z % 4 == 0) — piers + transverse soffit arch ────
        for (int z = z0; z <= z1; z++) {
            if (z % 4 != 0) continue;
            // twin piers drop from just under the deck to the base at both edges
            pillar(b, x0, z, 0, deckY - 1, STONE_BRICKS);
            pillar(b, x1, z, 0, deckY - 1, STONE_BRICKS);
            // shallow transverse soffit arch under the deck (y=3): stairs curl
            // inward from each pier, stone-brick keystone in the centre lane
            b.set(1, deckY - 1, z, archW);
            b.set(3, deckY - 1, z, archE);
            b.set(2, deckY - 1, z, STONE_BRICKS); // keystone
        }

        // ── 4) UNDER-DECK LANTERNS (z % 4 == 2) — lit off-beat, backed by deck
        for (int z = z0; z <= z1; z++) {
            if (z % 4 != 2) continue;
            // hanging lantern one cell under each deck edge; the solid deck at
            // y=4 is the backing the hanging lantern attaches to.
            b.set(x0, deckY - 1, z, HANGING_LANTERN);
            b.set(x1, deckY - 1, z, HANGING_LANTERN);
        }

        return b.build();
    }

    /**
     * Phase 2 §H Road / Path Segment. 5×5 footprint → builder(5, 4, 5). T1 disc.
     *
     * <p>A single TILEABLE tile of a paved street: a 3-lane cobblestone carriageway
     * with a stone center line, raised smooth-stone sidewalks/curbs on each side,
     * and a wrought-iron lamppost (stone-brick-wall shaft + lantern, via the
     * {@link #light(Blueprint.Builder, int, int, int, int, BlueprintBlockState)}
     * helper) standing on the west footpath. Print copies edge-to-edge along the
     * travel axis (Z) to lay a road of any length: every Z-row of the carriageway,
     * center line and sidewalks is identical, so the surface is seamless across the
     * z=4↔z=0 seam, and the single lamp sits at z=0 (one per tile, period 5) so the
     * curb + lamp rhythm continues unbroken from tile to tile.
     *
     * <p>Axes: x=W (0..4, across the road), y=up (0..3), z=depth (0..4, the direction
     * of travel). Width layout (x): 0 = west sidewalk/curb, 1 = west lane, 2 = center
     * line, 3 = east lane, 4 = east sidewalk/curb.
     *
     * <p>Vanilla blocks only, all FU-valued/structural: cobblestone, stone, diorite,
     * smooth-stone slabs, dirt-path, stone-brick wall and the lantern all clear the
     * printability gate (cobble/stone/diorite/slabs are valued; dirt_path is
     * structural). No glass/iron-bars panes anywhere, so the render-integrity
     * stub-pane gate never applies; the standing lantern sits on the solid curb.
     *
     * <p>Layout (Y), footprint x=0..4 × z=0..4:
     * <ul>
     *   <li><b>y=0</b> — road base plane filling the full 5×5. The carriageway
     *       (x=1,3) is cobblestone, the center line (x=2) is stone (the painted lane
     *       divider, lighter than the cobble), and the two sidewalk columns (x=0,4)
     *       are a solid diorite footing the raised footpath sits on. Every column is
     *       uniform down Z so the surface tiles flush.</li>
     *   <li><b>y=1</b> — raised sidewalks/curbs: a smooth-stone slab (top) capping
     *       each sidewalk column (x=0 and x=4) for the full depth, so the footpath
     *       reads as a half-block-raised curb stepping down to the carriageway. The
     *       road-facing slab edge is the curb line.</li>
     *   <li><b>lamppost</b> — on the west footpath at (x=0, z=0): a 2-tall
     *       stone-brick-wall shaft on the curb with a torch crown (the {@code light()}
     *       helper), the wrought-iron street lamp. At z=0 only, so each tile drops one
     *       lamp and the spacing stays even across seams.</li>
     * </ul>
     */
    private static Blueprint roadPathSegment() {
        Blueprint.Builder b = Blueprint.builder("Road / Path Segment", 5, 4, 5);
        BlueprintBlockState stone   = bs("minecraft:stone");
        BlueprintBlockState diorite = bs("minecraft:diorite");
        int z0 = 0, z1 = 4;           // travel axis (tiles flush along Z)

        // ── ROAD BASE PLANE (y=0) ──────────────────────────────────────────
        // Sidewalk footings (x=0,4) = diorite; carriageway lanes (x=1,3) = cobble;
        // center line (x=2) = stone. Each column runs the full depth so it tiles.
        line(b, 0, 0, z0, 0, z1, diorite);   // west sidewalk footing
        line(b, 0, 1, z0, 1, z1, COBBLE);    // west lane
        line(b, 0, 2, z0, 2, z1, stone);     // painted center line
        line(b, 0, 3, z0, 3, z1, COBBLE);    // east lane
        line(b, 0, 4, z0, 4, z1, diorite);   // east sidewalk footing

        // ── RAISED SIDEWALKS / CURBS (y=1) ─────────────────────────────────
        // Smooth-stone top-slab footpath capping each sidewalk column; the
        // road-facing edge reads as the curb stepping down to the lanes. The
        // lamp cell (x=0,z=0) is left open here so the lamppost stands on the
        // diorite footing instead of clashing with the slab.
        for (int z = z0; z <= z1; z++) {
            if (!(z == 0)) b.set(0, 1, z, SMOOTH_STONE_SLAB_TOP);
            b.set(4, 1, z, SMOOTH_STONE_SLAB_TOP);
        }

        // ── LAMPPOST (west footpath, x=0, z=0) ─────────────────────────────
        // 2-tall stone-brick-wall shaft standing on the diorite curb footing,
        // torch-crowned. One per tile at z=0 so the lamp rhythm continues evenly
        // across the z=4↔z=0 seam.
        light(b, 0, 0, 1, 2, STONE_BRICK_WALL);

        return b.build();
    }

    /**
     * Phase 2 §H Aqueduct Segment. 7×9 footprint → builder(7, 9, 9). T1 disc.
     *
     * <p>A single TILEABLE bay of a Roman aqueduct: tall arched stone-brick piers
     * carrying an elevated water channel on top. Three transverse piers (full
     * 7-wide stone-brick legs) stand at z=0, z=4 and z=8, with a rounded arch
     * opening cut through each 3-deep bay between them; an unbroken stone-brick
     * trough runs across the top carrying a water source down its whole length.
     * Print copies edge-to-edge along the channel axis (Z) to carry water any
     * distance: the piers sit on a period-4 rhythm so the z=8 pier of one tile
     * coincides with the z=0 pier of the next (no doubled pier), the deck/trough
     * floor and channel walls are identical every z-row, and the water lane is
     * continuous across the z=8↔z=0 seam — so the aqueduct reads as one
     * arcade and the water flows unbroken from tile to tile.
     *
     * <p>Axes: x=W (0..6, across the arcade), y=up (0..8), z=depth (0..8, the
     * direction the channel runs). The structure is solid-bodied (piers span the
     * full width), so the arches read from the side AND you can see straight
     * through each bay along X.
     *
     * <p>Vanilla blocks only, all FU-valued or structural, so every block clears
     * the printability gate: stone_bricks and its stairs/walls/slabs all derive
     * from stone bricks; mossy- and cracked-stone-bricks are independently valued
     * weathering variants; water is an itemless structural block (prints free,
     * {@code asItem()==AIR}). No glass panes or iron bars anywhere, so the
     * render-integrity stub-pane gate never applies.
     *
     * <p>Layout (Y), footprint x=0..6 × z=0..8:
     * <ul>
     *   <li><b>Piers (z=0,4,8), y=0..5</b> — a solid stone-brick leg spanning the
     *       full width at each pier row, weathered with mossy/cracked flecks so the
     *       masonry reads aged. These are the only blocks reaching the ground; the
     *       bays between them are open air below the arch.</li>
     *   <li><b>Arch soffit (y=5), each bay</b> — a rounded opening under the deck:
     *       {@code half=top} stone-brick stairs spring inward from each pier
     *       (facing=south at the z-row beside the lower pier, facing=north at the
     *       z-row beside the upper pier), bridged by a stone-brick keystone over the
     *       bay centre. The centre column of each bay (the keystone cell aside) is
     *       left open down to the ground, giving the tall walk-through arch.</li>
     *   <li><b>Channel floor (y=6)</b> — a solid stone-brick deck over the full
     *       7×9, the watertight base of the trough (and the soffit's backing). Flat,
     *       so it tiles flush.</li>
     *   <li><b>Channel walls (y=7)</b> — a stone-brick wall on each long edge (x=0
     *       and x=6) at every z, an unbroken parapet/curb that contains the water and
     *       connects across the seam, lightly weathered.</li>
     *   <li><b>Water (y=7), lane x=1..5</b> — a water source filling the trough
     *       between the walls for the full length, the running channel. Continuous
     *       across the z=8↔z=0 seam so the water never breaks.</li>
     * </ul>
     */
    private static Blueprint aqueductSegment() {
        Blueprint.Builder b = Blueprint.builder("Aqueduct Segment", 7, 9, 9);
        int x0 = 0, x1 = 6;           // 7-wide arcade
        int z0 = 0, z1 = 8;           // 9-long span — piers at z=0,4,8 (period 4 → flush)
        int pierTop = 5;              // piers rise y=0..5; arch springs at y=5
        int deckY = 6;                // channel floor (trough base) deck
        int wallY = 7;                // channel walls + water lane
        // inward-curling soffit stairs for the longitudinal under-deck arch (Z-Y plane)
        BlueprintBlockState archLo = bs("minecraft:stone_brick_stairs[facing=south,half=top,shape=straight]");
        BlueprintBlockState archHi = bs("minecraft:stone_brick_stairs[facing=north,half=top,shape=straight]");

        // ── 1) PIERS (z=0,4,8) — solid full-width stone-brick legs, weathered ──
        for (int z = z0; z <= z1; z += 4) {
            solid(b, x0, 0, z, x1, pierTop, z, STONE_BRICKS);
        }
        // weathering flecks on the piers so the masonry reads aged, not uniform
        b.set(1, 1, 0, CRACKED_STONE_BRICKS);
        b.set(5, 2, 0, MOSSY_STONE_BRICKS);
        b.set(2, 3, 4, MOSSY_STONE_BRICKS);
        b.set(4, 1, 4, CRACKED_STONE_BRICKS);
        b.set(1, 2, 8, MOSSY_STONE_BRICKS);
        b.set(5, 4, 8, CRACKED_STONE_BRICKS);

        // ── 2) ARCH SOFFITS — rounded opening under the deck in each 3-deep bay ──
        // For each pier-to-pier bay [zp+1 .. zp+3], stairs spring inward from the
        // piers at the springline (y=pierTop) and a keystone bridges the centre,
        // across the full width. The bay centre stays open to the ground.
        for (int zp = z0; zp < z1; zp += 4) {
            int zLo = zp + 1;             // row beside the lower pier (zp)
            int zKey = zp + 2;            // bay centre — keystone row
            int zHi = zp + 3;             // row beside the upper pier (zp+4)
            for (int x = x0; x <= x1; x++) {
                b.set(x, pierTop, zLo, archLo);          // springer curling toward centre
                b.set(x, pierTop, zHi, archHi);          // springer curling toward centre
                b.set(x, pierTop, zKey, STONE_BRICKS);   // keystone bridging the arch
            }
        }

        // ── 3) CHANNEL FLOOR (y=6) — solid stone-brick trough base over the deck ─
        floor(b, deckY, x0, z0, x1, z1, STONE_BRICKS);

        // ── 4) CHANNEL WALLS (y=7) — twin stone-brick curbs containing the water ─
        for (int z = z0; z <= z1; z++) {
            b.set(x0, wallY, z, STONE_BRICK_WALL);
            b.set(x1, wallY, z, STONE_BRICK_WALL);
        }
        // a little weathering on the curb so it ages with the piers
        b.set(x0, wallY, 2, MOSSY_STONE_BRICKS);
        b.set(x1, wallY, 6, MOSSY_STONE_BRICKS);

        // ── 5) WATER (y=7) — the running channel between the curbs, full length ──
        for (int z = z0; z <= z1; z++) {
            for (int x = x0 + 1; x <= x1 - 1; x++) {
                b.set(x, wallY, z, WATER);
            }
        }

        return b.build();
    }

    /**
     * Phase 2 §H Mineshaft Entrance. 5×5 footprint → builder(5, 5, 5). T4 printer,
     * T1 disc — the survival Let's Play staple: a timbered tunnel mouth boring into
     * a hillside, with mine rails leading down and in.
     *
     * <p>Orientation: the open <b>access side is the south face</b> ({@code z=4}); the
     * hill the shaft bores into is to the <b>north</b> ({@code z=0}). The player walks
     * up a short raised cobble apron at the front, under a timbered portal (log posts +
     * crossbeam lintel with a plank/stair overhang roof), then the floor <b>steps down
     * one block</b> into a framed, recessed tunnel that recedes into darkness — the
     * signature "tunnel mouth": a framed air gap with rails running down and in.
     *
     * <p>Axes: x=W (0..4, across the portal), y=up (0..4), z=depth (0..4; z=4 front
     * apron → z=0 deep end). The descending read is real: the apron rail sits one
     * block higher than the tunnel-floor rail, so the minecart line visibly drops as
     * it enters the shaft.
     *
     * <p>Vanilla blocks only, all FU-valued or structural, so every block clears the
     * printability gate:
     * <ul>
     *   <li><b>cobblestone / mossy_cobblestone</b> — base, apron, reinforcement and
     *       weathering; both independently FU-valued.</li>
     *   <li><b>spruce_log / oak_log / oak_planks / oak_stairs / oak_slab</b> — the
     *       timber portal frame, lintel, support posts and overhang roof; all derive.</li>
     *   <li><b>rail</b> — crafts from iron + stick (6 iron + 1 stick → 16 rails), so it
     *       derives an FU value. Every rail in this build sits directly on a SOLID
     *       cobble block below it (apron rails on the y=1 apron cobble, tunnel rails on
     *       the y=0 base cobble), so the rails place and stay.</li>
     *   <li><b>lantern</b> — two pit lanterns light the mouth; FU-valued.</li>
     *   <li><b>oak_sign</b> — a standing "MINE" plaque on the apron; FU-valued.</li>
     * </ul>
     * No glass panes / iron bars anywhere, so the stub-pane render gate never applies.
     *
     * <p>Layout (south = +z is the open front; 5×5 = x0..4, z0..4):
     * <ul>
     *   <li><b>Base (y=0)</b> — a solid cobble pad over the whole 5×5 footprint; the
     *       tunnel rails ride on it and it gives the apron something to sit on.</li>
     *   <li><b>Side & back reinforcement walls (x=0, x=4 for z=0..2; back z=0)</b> —
     *       cobble/mossy-cobble walls y=1..2 boxing the tunnel interior into the hill,
     *       lightly weathered so the masonry reads aged and mine-worn.</li>
     *   <li><b>Raised apron (z=3..4)</b> — a cobble step up to y=1 the player mounts at
     *       the mouth; the front lip of the mine head.</li>
     *   <li><b>Timber portal (z=2)</b> — two spruce-log posts at x=1 and x=3 rising
     *       y=1..2, capped by an oak-log crossbeam lintel spanning x=1..3 at y=3; the
     *       classic mine head-frame. The portal opening (x=2) is left clear.</li>
     *   <li><b>Support posts (z=0)</b> — two more spruce-log posts at the deep end,
     *       y=1..2, carrying the tunnel ceiling like pit props.</li>
     *   <li><b>Ceiling / roof (y=3, then y=4)</b> — oak-plank tunnel ceiling over the
     *       shaft (z=0..2), and an oak-stair + plank OVERHANG roof projecting out over
     *       the apron (z=3..4) so the mouth reads as a sheltered head-frame.</li>
     *   <li><b>Rails — the descending line</b> — center column x=2: apron rails on the
     *       y=1 apron at z=4,3 (on apron cobble), then the line steps DOWN to the y=0
     *       tunnel floor at z=2,1,0, leading down and into the dark. A minecart stop
     *       (cobble buffer) caps the front of the apron line at z=4.</li>
     *   <li><b>Lighting & signage</b> — a lantern on each portal post top, and a
     *       standing oak "MINE" sign on the west apron lip.</li>
     * </ul>
     */
    private static Blueprint mineshaftEntrance() {
        Blueprint.Builder b = Blueprint.builder("Mineshaft Entrance", 5, 5, 5);
        // all vanilla, all FU-valued / structural; rail derives from iron + stick.
        BlueprintBlockState cobble = COBBLE;
        BlueprintBlockState mossy  = MOSSY_COBBLE;
        BlueprintBlockState post   = SPRUCE_LOG_Y;                 // portal + prop posts
        BlueprintBlockState lintel = bs("minecraft:oak_log[axis=x]"); // crossbeam over the mouth
        BlueprintBlockState planks = OAK_PLANKS;                   // ceiling + overhang deck
        BlueprintBlockState rail   = bs("minecraft:rail[shape=north_south]");
        BlueprintBlockState overhangStair =
                bs("minecraft:oak_stairs[facing=north,half=bottom,shape=straight]"); // eave lip over the front
        int x0 = 0, x1 = 4, z0 = 0, z1 = 4;
        int cx = 2;                       // center rail column / portal opening

        // ── 1) BASE PAD (y=0) — solid cobble under the whole footprint ───────
        floor(b, 0, x0, z0, x1, z1, cobble);

        // ── 2) TUNNEL SIDE + BACK WALLS (y=1..2) — box the shaft into the hill ─
        // West & east walls flank the shaft for its buried depth (z=0..2); the
        // back wall (z=0) closes the deep end below the props. Weathered.
        for (int y = 1; y <= 2; y++) {
            for (int z = z0; z <= 2; z++) {
                b.set(x0, y, z, cobble);   // west wall
                b.set(x1, y, z, cobble);   // east wall
            }
            // back wall between the side walls (x=1..3) at the deep end
            for (int x = x0 + 1; x <= x1 - 1; x++) {
                b.set(x, y, z0, cobble);
            }
        }
        // weathering flecks — the mine reads old and mossy
        b.set(x0, 1, 1, mossy);
        b.set(x1, 2, 0, mossy);
        b.set(1, 2, 0, mossy);
        b.set(3, 1, 0, mossy);

        // ── 3) RAISED FRONT APRON (z=3..4, y=1) — the cobble mine-head step ──
        floor(b, 1, x0, 3, x1, z1, cobble);
        b.set(2, 1, 4, mossy); // worn tread on the apron lip

        // ── 4) TIMBER PORTAL (z=2) — log posts + crossbeam lintel ───────────
        pillar(b, 1, 2, 1, 2, post);     // west portal post
        pillar(b, 3, 2, 1, 2, post);     // east portal post
        line(b, 3, 1, 2, 3, 2, lintel);  // crossbeam lintel x=1..3 at y=3

        // ── 5) SUPPORT PROPS (z=0) — pit props at the deep end ──────────────
        pillar(b, 1, 0, 1, 2, post);     // west prop
        pillar(b, 3, 0, 1, 2, post);     // east prop

        // ── 6) CEILING + OVERHANG ROOF ──────────────────────────────────────
        // Tunnel ceiling over the buried shaft (z=0..1) at y=3 — the lintel
        // crossbeam at z=2,y=3 is LEFT exposed as the visible head-frame beam.
        // Then an overhang deck projecting out over the apron (z=3..4) at y=4
        // with a stair eave lip.
        floor(b, 3, x0 + 1, z0, x1 - 1, 1, planks);   // shaft ceiling (z=0..1, between the side walls)
        floor(b, 4, x0, 3, x1, z1, planks);           // overhang deck over the apron
        for (int x = x0; x <= x1; x++) {
            b.set(x, 4, z1, overhangStair);           // front eave lip of the overhang
        }

        // ── 7) RAILS — the descending minecart line (center x=2) ────────────
        // Apron rails ride the y=1 apron (z=4,3); the line then STEPS DOWN to the
        // y=0 tunnel floor (z=2,1,0), leading down and into the dark.
        b.set(cx, 2, z1, rail);   // apron rail at z=4 (on apron cobble y=1)
        b.set(cx, 2, 3, rail);    // apron rail at z=3
        b.set(cx, 1, 2, rail);    // steps DOWN into the portal mouth (on base y=0)
        b.set(cx, 1, 1, rail);    // tunnel rail
        b.set(cx, 1, z0, rail);   // deep-end rail, fading into the dark
        // minecart stop — a cobble buffer block beside the front of the apron line
        b.set(cx + 1, 2, z1, cobble);

        // ── 8) LIGHTING + SIGNAGE ───────────────────────────────────────────
        // Hanging lanterns under the overhang deck (y=4 plank at z=3) light the
        // mouth without burying the exposed lintel beam at z=2.
        b.set(1, 3, 3, HANGING_LANTERN);  // west eave lantern under the overhang
        b.set(3, 3, 3, HANGING_LANTERN);  // east eave lantern under the overhang
        b.set(0, 2, 4, bs("minecraft:oak_sign[rotation=8]")); // "MINE" plaque on the west apron lip

        return b.build();
    }

    /**
     * Phase 2 §H Railway Station. 13×9 footprint → builder(13, 9, 9). T6 printer,
     * T2 disc — the minecart-network hub: a raised stone-brick platform running the
     * full length, a twin rail line (one powered) alongside it, a pillared canopy /
     * awning over the platform, a small ENTERABLE ticket booth with a counter and
     * glazed windows, a stone-brick clock/signage tower, hanging lanterns, and
     * stair-seat benches.
     *
     * <p>Orientation: the <b>track is the north strip</b> ({@code z=0..2}); the
     * <b>platform with all the furniture is the south strip</b> ({@code z=3..8}). The
     * platform top is a flat walkable surface at {@code y=2}; the rails sit one
     * level lower at {@code y=1} so the platform reads as a raised quay you board
     * trains from. The whole thing tiles edge-to-edge along the track axis (X): the
     * rail line, the platform deck and the platform edge are identical every
     * x-column, and the canopy posts sit on a period-4 rhythm so two stations
     * placed end-to-end give one continuous line + platform with no doubled post.
     *
     * <p>Axes: x=W (0..12, the direction the rails run), y=up (0..8), z=depth
     * (0..8; z=0 track edge → z=8 back of platform).
     *
     * <p>Vanilla blocks only, all FU-valued or structural, so every block clears the
     * printability gate:
     * <ul>
     *   <li><b>stone / stone_bricks / chiseled_stone_bricks / smooth_stone</b> and
     *       their stairs/slabs/walls — base pad, platform deck, edge, posts, booth,
     *       tower; all derive from stone bricks (or are independently valued).</li>
     *   <li><b>rail</b> (iron + stick) and <b>powered_rail</b> (gold + redstone +
     *       stick) — both derive an FU value; every rail tile rides directly on the
     *       SOLID y=0 stone pad below it, so the rails place and stay.</li>
     *   <li><b>iron_block / iron_bars</b> — iron canopy posts and a render-safe bar
     *       balustrade segment; both FU-valued.</li>
     *   <li><b>glass_pane</b> — ticket-booth windows, each flanked on BOTH sides by a
     *       solid stone-brick wall cell so the pane always has a horizontal
     *       connection and never renders as a stub.</li>
     *   <li><b>lantern</b> (hanging + standing), <b>oak_sign</b> — platform lighting
     *       and the station name plaque; all FU-valued. No item frames / paintings
     *       (those are entities, not blocks).</li>
     * </ul>
     *
     * <p>Layout (north = track at z=0; 13×9 = x0..12, z0..8):
     * <ul>
     *   <li><b>Base pad (y=0)</b> — solid stone over the whole 13×9 footprint; the
     *       rails ride on it and the platform sits on it.</li>
     *   <li><b>Rail line (y=1, z=1 & z=2)</b> — a normal {@code rail} line at z=2 and
     *       a {@code powered_rail} line at z=1, both running east-west the full
     *       width and continuous across the tiling seam. z=0 is left as the open
     *       trackside ballast (stone pad) so a train cab clears the platform.</li>
     *   <li><b>Platform deck (y=1 fill, walkable top y=2)</b> — stone-brick fill over
     *       z=3..8 raising the platform one block; a {@code chiseled_stone_bricks}
     *       edge band runs along z=3 (the boarding edge) and a stone-brick-slab lip
     *       caps z=3 at y=2 so the platform reads as a raised quay.</li>
     *   <li><b>Canopy (posts y=3..4 at x=2,6,10 on z=4; awning y=5)</b> — iron-block
     *       posts carry a stone-brick-slab awning over the platform (z=4..8), with a
     *       stair eave on the trackside edge (z=3) so it shelters the boarding edge.</li>
     *   <li><b>Ticket booth (x=0..3, z=5..8, enterable)</b> — a stone-brick room with
     *       an oak door on its east wall opening inward, glazed windows (pane between
     *       two solid wall cells) on the south & west walls, and a smooth-stone-slab
     *       service counter under a window facing the platform. Interior left open.</li>
     *   <li><b>Clock / signage tower (x=11, z=6..7)</b> — a slim stone-brick tower
     *       rising to y=8, capped with a {@code chiseled_stone_bricks} clock face and
     *       a lantern beacon; the tall vertical landmark that says "station".</li>
     *   <li><b>Furniture & lighting</b> — stair-seat benches against the back wall
     *       (z=8), hanging lanterns under the canopy, a standing lantern on the
     *       platform, and an oak "STATION" sign by the booth door.</li>
     * </ul>
     */
    private static Blueprint railwayStation() {
        Blueprint.Builder b = Blueprint.builder("Railway Station", 13, 9, 9);
        int x0 = 0, x1 = 12;          // 13-wide — the track/platform run along X
        int z0 = 0, z1 = 8;           // 9-deep — z=0..2 track, z=3..8 platform
        BlueprintBlockState stone   = bs("minecraft:stone");
        BlueprintBlockState smoothSlabTop = SMOOTH_STONE_SLAB_TOP;
        BlueprintBlockState rail    = bs("minecraft:rail[shape=east_west]");
        BlueprintBlockState poweredRail = bs("minecraft:powered_rail[shape=east_west,powered=true,waterlogged=false]");
        // canopy awning + eave (slab deck, stair drip edge facing the track / +z step)
        BlueprintBlockState awningEave = bs("minecraft:stone_brick_stairs[facing=south,half=top,shape=straight]");

        // ── 1) BASE PAD (y=0) — solid stone under the whole footprint ────────
        floor(b, 0, x0, z0, x1, z1, stone);

        // ── 2) RAIL LINE (y=1) — twin lines running the full width, tileable ──
        // z=0 is open ballast (the y=0 stone shows) so a passing cab clears the
        // platform; the powered line is z=1, the plain line z=2. Both ride the
        // solid y=0 pad and run unbroken across the x-seam.
        line(b, 1, x0, 1, x1, 1, poweredRail);   // powered_rail line at z=1
        line(b, 1, x0, 2, x1, 2, rail);          // rail line at z=2

        // ── 3) PLATFORM DECK (z=3..8) — raise one block, walkable top at y=2 ──
        // Fill y=1 over the platform footprint (deck body), then the y=2 walking
        // surface is the boarding edge band + the deck under everything else.
        solid(b, x0, 1, 3, x1, 1, z1, STONE_BRICKS);   // platform body (y=1 fill)
        floor(b, 2, x0, 4, x1, z1, STONE_BRICKS);       // walkable deck (z=4..8) at y=2
        // boarding edge (z=3): a chiseled band at y=1 and a slab lip at y=2 so the
        // platform reads as a raised quay you step up onto from the track side.
        line(b, 1, x0, 3, x1, 3, CHISELED_STONE_BRICKS);
        line(b, 2, x0, 3, x1, 3, STONE_BRICK_SLAB_TOP);

        // ── 4) CANOPY — iron posts + stone-brick awning over the OPEN platform ──
        // The awning shelters the open platform run (x=4..12); the ticket booth
        // (x=0..3) stands free with its own taller roof, so the awning is NOT laid
        // over the booth footprint (no double roof). Posts at x=4,8,12 on z=4 rise
        // y=3..4 off the deck; the awning slab roof is at y=5 over z=4..8, with a
        // stair drip-eave over the boarding edge (z=3) the full sheltered width.
        int awnX0 = 4;                       // awning starts east of the booth
        for (int px : new int[]{4, 8, 12}) {
            pillar(b, px, 4, 3, 4, IRON_BLOCK);
        }
        floor(b, 5, awnX0, 4, x1, z1, STONE_BRICK_SLAB_TOP);   // awning deck over the open platform
        for (int x = awnX0; x <= x1; x++) {
            b.set(x, 5, 3, awningEave);                         // drip-eave over the boarding edge
        }

        // ── 5) TICKET BOOTH (x=0..3, z=5..8) — small ENTERABLE room ──────────
        // Walls rise y=3..5 (so it pokes above the y=5 awning), a stone-brick floor
        // at y=2 sits flush with the platform deck (already laid), interior open.
        int bx0 = 0, bz0 = 5, bx1 = 3, bz1 = 8;
        walls(b, bx0, bz0, bx1, bz1, 3, 5, STONE_BRICKS);
        floor(b, 6, bx0, bz0, bx1, bz1, STONE_BRICK_SLAB_TOP);   // booth roof cap at y=6
        // doorway on the EAST wall (x=3) opening inward (facing=west); leave y=3,4 open
        door2(b, bx1, 3, 6, "oak", "E");
        // windows: every glass pane is flanked by solid wall cells on BOTH sides
        // along its wall run → it always has a horizontal connection (render-safe).
        // North (platform-facing) booth wall is z=5; this is the ticket WINDOW the
        // clerk serves through, facing the platform. Panes at x=1,2 are flanked by
        // the x=0 and x=3 corner posts and by each other.
        b.set(1, 4, bz0, GLASS_PANE);            // ticket window, flanked by x=0 wall + x=2 pane
        b.set(2, 4, bz0, GLASS_PANE);            // ticket window, flanked by x=1 pane + x=3 wall
        // West wall (x=0): a side window at z=6, flanked by the z=5 and z=7 wall cells.
        b.set(bx0, 4, 6, GLASS_PANE);
        // service counter INSIDE the booth, a smooth-stone slab run the clerk stands
        // behind, just inside the ticket window (z=6, one row in from the z=5 wall).
        b.set(1, 2, 6, smoothSlabTop);
        b.set(2, 2, 6, smoothSlabTop);

        // ── 6) CLOCK / SIGNAGE TOWER (x=11, z=6) — slim landmark to y=8 ───────
        // A slim stone-brick column rising off the deck to the build's full height,
        // with a chiseled "clock face" panel near the top and a lantern beacon cap.
        pillar(b, 11, 6, 3, 7, STONE_BRICKS);
        b.set(11, 6, 6, CHISELED_STONE_BRICKS);  // clock-face panel mid-shaft
        b.set(11, 7, 6, GLOWSTONE);              // lit clock dial just below the cap
        b.set(11, 8, 6, CHISELED_STONE_BRICKS);  // chiseled masonry cap (y=8 top)

        // ── 7) FURNITURE + LIGHTING ──────────────────────────────────────────
        // stair-seat benches along the back of the platform (z=8) FACING the track,
        // i.e. facing=north so a sitter looks out toward the rails. They rest on the
        // y=2 deck (their bottom at y=3).
        BlueprintBlockState bench = bs("minecraft:stone_brick_stairs[facing=north,half=bottom,shape=straight]");
        for (int bxs : new int[]{6, 8}) {
            b.set(bxs, 3, z1, bench);
        }
        // a short render-safe iron-bar balustrade on the back edge: two bars side by
        // side so each connects horizontally to the other (renders as a real railing).
        b.set(4, 3, z1, IRON_BARS);
        b.set(5, 3, z1, IRON_BARS);
        // hanging lanterns under the awning (attach to the y=5 awning slab above them)
        b.set(4, 4, 5, HANGING_LANTERN);
        b.set(8, 4, 5, HANGING_LANTERN);
        // a standing lantern on the platform deck by the tower base
        b.set(11, 3, 5, LANTERN);
        // oak "STATION" plaque standing on the platform deck beside the booth door
        b.set(4, 3, 7, bs("minecraft:oak_sign[rotation=12]"));

        return b.build();
    }

    /**
     * §F.animal_pen — a STATIC fenced animal pen with a corner shelter, 9×9×5
     * (W×L×H) → builder(9, 5, 9).
     *
     * <p>The "beginner essential": a fenced enclosure the player drops animals into
     * (cows/sheep/pigs/chickens). Printed as the working SHELL — fence perimeter,
     * gate, a covered lean-to shelter in the NW corner, a water trough, a hay-bale
     * feeder, and lighting. The PLAYER adds the animals after printing.
     *
     * <p><b>No grass floor.</b> Per the build conventions, the pen sits on the
     * player's own terrain — we do NOT print {@code grass_block} (it's unvalued AND
     * it's the player's ground). The pen interior cells at y=0 are left unset so the
     * existing ground shows through; only the corner SHELTER gets a printed oak-plank
     * floor (so animals can shelter on a dry wood surface) and the water trough is a
     * sunken pool. Every printed block is vanilla and FU-valued (oak_fence,
     * oak_fence_gate, oak_log, oak_planks, oak_slab, hay_block, lantern, torch derive
     * for free) or structural-free matter (water prints free, {@code asItem()==AIR}).
     *
     * <p>Layout (south = +z is the "front"/access side with the gate; 9×9 = x0..8,
     * z0..8):
     * <ul>
     *   <li><b>Fence perimeter, y=1</b> — {@link #fenceRing} of oak fence around the
     *       full [0..8]×[0..8] edge, with a single <b>oak fence gate</b> on the south
     *       face (x=4, z=8, facing=south) as the entrance. Fences/gates self-reconcile
     *       their connection shapes at print time, and oak fence is a {@code FenceBlock}
     *       (NOT an {@link net.minecraft.world.level.block.IronBarsBlock}), so the
     *       stub-pane render gate doesn't apply.</li>
     *   <li><b>Corner lean-to shelter, NW (x=0..3, z=0..3)</b> — an oak-plank floor at
     *       y=0 (the only printed ground, dry standing room), four oak-log corner posts
     *       y=1..2, oak-plank back (north, z=0) + side (west, x=0) walls y=1..2 to break
     *       the wind, and an oak-slab roof at y=3 over the footprint. The east + south
     *       faces stay open so animals can walk in. A hanging lantern under the roof
     *       lights the shelter.</li>
     *   <li><b>Water trough, y=0</b> — a 1×2 sunken water pool at (x=6, z=2..3), boxed
     *       on its north/south ends with cobble at y=0 so it reads as a contained
     *       trough. Water is structural and prints free; animals drink/path to it.</li>
     *   <li><b>Hay-bale feeder, y=1</b> — two hay blocks at (x=6, z=6) and (x=7, z=6)
     *       near the SE corner, the classic feed pile (hay derives from wheat, FU-valued).</li>
     *   <li><b>Lighting</b> — fence-post lanterns on the two front (south) gate-flanking
     *       posts and a torch atop a corner post, so the pen is lit and hostiles can't
     *       spawn among the livestock.</li>
     * </ul>
     */
    private static Blueprint animalPen() {
        Blueprint.Builder b = Blueprint.builder("Animal Pen", 9, 5, 9);
        // all vanilla, all FU-valued / structural-free (NO grass floor — player's terrain):
        BlueprintBlockState fence  = OAK_FENCE;   // FU-valued; FenceBlock (NOT IronBars → no stub-pane gate)
        BlueprintBlockState planks = OAK_PLANKS;  // shelter floor + walls
        BlueprintBlockState postY  = OAK_LOG_Y;   // shelter corner posts
        BlueprintBlockState slab   = OAK_SLAB_BOTTOM; // shelter roof
        BlueprintBlockState cobble = COBBLE;      // trough end caps
        BlueprintBlockState water  = WATER;       // structural → prints free
        BlueprintBlockState hay    = HAY;         // FU-valued feeder

        int x0 = 0, x1 = 8, z0 = 0, z1 = 8;       // 9×9 footprint
        int gateX = 4;                            // south-face gate column

        // ── 1) FENCE PERIMETER at y=1 (gate on the south face) ──────────────
        // The pen sits on the player's terrain: no floor is printed under the open
        // pen. The fence ring is the enclosure; the gate is the single entrance.
        fenceRing(b, 1, x0, z0, x1, z1, fence);
        // oak fence gate on the south wall (z=8), facing south (outward) — the
        // entrance. Overwrites the fence cell fenceRing placed at (4,1,8).
        b.set(gateX, 1, z1, bs("minecraft:oak_fence_gate[facing=south,open=false,in_wall=false,powered=false]"));

        // ── 2) CORNER LEAN-TO SHELTER, NW (x=0..3, z=0..3) ──────────────────
        int sx0 = 0, sx1 = 3, sz0 = 0, sz1 = 3;   // shelter footprint
        // oak-plank floor at y=0 (the only printed ground — dry standing room)
        floor(b, 0, sx0, sz0, sx1, sz1, planks);
        // four oak-log corner posts, y=1..2
        corners(b, sx0, sz0, sx1, sz1, 1, 2, postY);
        // back (north, z=0) + side (west, x=0) walls, y=1..2 — wind-break; east + south
        // faces stay open so animals walk in. (line() includes the shared corner cells,
        // which the posts already occupy — air-skip-safe overwrites with planks/logs.)
        for (int y = 1; y <= 2; y++) {
            line(b, y, sx0, sz0, sx1, sz0, planks);   // north wall
            line(b, y, sx0, sz0, sx0, sz1, planks);   // west wall
        }
        // re-assert the corner posts so the post material reads at the wall ends
        corners(b, sx0, sz0, sx1, sz1, 1, 2, postY);
        // oak-slab roof at y=3 over the shelter footprint
        floor(b, 3, sx0, sz0, sx1, sz1, slab);
        // hanging lantern under the roof centre (chain to the y=3 slab above)
        b.set(2, 3, 1, CHAIN);
        b.set(2, 2, 1, HANGING_LANTERN);

        // ── 3) WATER TROUGH, y=0 — a 1×2 sunken pool, boxed at its ends ─────
        b.set(6, 0, 2, water);
        b.set(6, 0, 3, water);
        b.set(6, 0, 1, cobble);   // north end cap
        b.set(6, 0, 4, cobble);   // south end cap

        // ── 4) HAY-BALE FEEDER, y=1 — the classic feed pile near the SE ────
        b.set(6, 1, 6, hay);
        b.set(7, 1, 6, hay);

        // ── 5) LIGHTING — fence-post lanterns around the pen ────────────────
        // Lanterns sit atop the fence posts flanking the south gate (x=3 and x=5, on
        // the y=1 fence) at y=2 — vanilla lanterns place on a fence's top face — plus
        // one atop the NE corner post. With the shelter's hanging lantern this lights
        // the whole enclosure so no hostiles spawn among the livestock.
        b.set(3, 2, z1, LANTERN);
        b.set(5, 2, z1, LANTERN);
        b.set(x1, 2, z0, LANTERN);  // atop the NE corner fence post

        return b.build();
    }

    /**
     * §F.chicken_coop_auto — a STATIC automatic cooked-chicken coop, 5×5×8 (W×L×H)
     * → builder(5, 8, 5).
     *
     * <p>The classic Reddit "auto cooked-chicken farm" (r/Minecraftbuilds, 9.9k
     * upvotes), printed as the working STRUCTURE only. Chickens are never captured —
     * the player drops a few starting chickens into the glass growth chamber after
     * printing; from then on it runs itself. Every block is a vanilla FU-valued or
     * structural-free block (glass uses solid BLOCKS, never lone panes → render-safe).
     *
     * <p>How the mechanism works once the player adds chickens:
     * <ul>
     *   <li>Adult chickens lay eggs onto the <b>hopper grid floor</b>; the eggs are
     *       swept into the collection chest below.</li>
     *   <li>A <b>comparator</b> reads the hopper feeding the chest and pulses a
     *       <b>dispenser</b> aimed down into the chamber; the dispenser throws eggs back
     *       in, hatching new chicks (the auto-restock loop). An <b>observer</b> watching
     *       the dispenser face keeps the pulse clean.</li>
     *   <li>Chicks are short and safe; once they grow to <b>adult height</b> their head
     *       enters the <b>lava blade</b> suspended two blocks above the standing floor.
     *       They die <i>cooked</i>, dropping cooked chicken + feathers straight down onto
     *       the hopper grid → chest. Lava is structural (prints free) and is boxed by
     *       glass so the blade can't spill.</li>
     * </ul>
     *
     * <p>Layout (south = +z is the "front" / chest-access side; cx=cz=2):
     * <ul>
     *   <li><b>y=0</b> — stone foundation. A central <b>collection chest</b> (faces south)
     *       ringed by four <b>hoppers</b> that feed into it; the SE diagonal hopper is the
     *       comparator-read hopper that drives the egg-dispenser loop.</li>
     *   <li><b>y=1</b> — the <b>hopper standing floor</b>: a 3×3 hopper grid (chickens
     *       stand on it) all feeding DOWN into the y=0 collection ring, so every dropped
     *       egg/cooked-chicken funnels to the chest. A stone rim frames it.</li>
     *   <li><b>y=2..4</b> — the <b>glass growth chamber</b> (solid glass walls on a stone
     *       base course at y=1) where the chickens live and grow.</li>
     *   <li><b>Lava blade, y=4</b> — a single lava cell on the chamber ceiling directly
     *       over the standing floor: the kill height. Glass walls box it.</li>
     *   <li><b>Control stack, y=5..7</b> — above the lava: a <b>dispenser</b> facing DOWN
     *       (throws eggs into the chamber), an <b>observer</b> + <b>comparator</b> + a
     *       <b>redstone</b> ribbon on a stone shelf tying the comparator-read hopper to the
     *       dispenser, and a smooth-stone-slab roof cap.</li>
     * </ul>
     */
    private static Blueprint chickenCoopAuto() {
        Blueprint.Builder b = Blueprint.builder("Auto Chicken Coop", 5, 8, 5);
        // all vanilla, all FU-valued / structural-free:
        BlueprintBlockState stone   = bs("minecraft:stone");
        BlueprintBlockState glass   = GLASS;                // solid glass for walls (always renders)
        BlueprintBlockState slabTop = SMOOTH_STONE_SLAB_TOP;
        BlueprintBlockState chest   = bs("minecraft:chest[facing=south,type=single,waterlogged=false]");
        BlueprintBlockState lava    = LAVA;                 // structural → prints free
        BlueprintBlockState redDust = bs("minecraft:redstone_wire[east=none,west=none,north=none,south=none,power=0]"); // structural

        int x0 = 0, x1 = 4, z0 = 0, z1 = 4;                 // 5×5 footprint
        int cx = 2, cz = 2;                                 // centre column

        // ── 1) STONE FOUNDATION at y=0, with the COLLECTION CHEST + HOPPER RING ──
        // The chest sits at the centre, faced south for front access. Four hoppers
        // ring it and feed INTO it; the SE diagonal hopper (cx+1,cz+1) is the one the
        // comparator reads to drive the egg-dispenser restock loop.
        floor(b, 0, x0, z0, x1, z1, stone);
        b.set(cx, 0, cz, chest);                                          // collection chest, faces south
        b.set(cx - 1, 0, cz, bs("minecraft:hopper[enabled=true,facing=east]"));   // → chest
        b.set(cx + 1, 0, cz, bs("minecraft:hopper[enabled=true,facing=west]"));   // → chest
        b.set(cx, 0, cz - 1, bs("minecraft:hopper[enabled=true,facing=south]"));  // → chest
        b.set(cx + 1, 0, cz + 1, bs("minecraft:hopper[enabled=true,facing=west]")); // comparator-read hopper → E hopper → chest

        // ── 2) HOPPER STANDING FLOOR at y=1 (the chickens stand here) ────────────
        // A 3×3 hopper grid centred on (cx,cz), every hopper feeding DOWN into the
        // y=0 collection ring below, so eggs + cooked chicken laid/dropped here funnel
        // straight to the chest. A stone rim around the grid frames the chamber base
        // and gives the glass walls a solid course to sit on.
        for (int x = cx - 1; x <= cx + 1; x++) {
            for (int z = cz - 1; z <= cz + 1; z++) {
                b.set(x, 1, z, bs("minecraft:hopper[enabled=true,facing=down]")); // → ring below
            }
        }
        // stone rim course at y=1 around the 3×3 grid (perimeter of the 5×5)
        line(b, 1, x0, z0, x1, z0, stone); line(b, 1, x0, z1, x1, z1, stone);
        line(b, 1, x0, z0, x0, z1, stone); line(b, 1, x1, z0, x1, z1, stone);

        // ── 3) GLASS GROWTH CHAMBER WALLS, y=2..4 ────────────────────────────────
        // Solid-glass walls (always render) on the y=1 stone rim, boxing the chickens
        // and the lava blade above them. Stone corner posts tie the cage together.
        for (int y = 2; y <= 4; y++) {
            line(b, y, x0, z0, x1, z0, glass); line(b, y, x0, z1, x1, z1, glass);
            line(b, y, x0, z0, x0, z1, glass); line(b, y, x1, z0, x1, z1, glass);
        }
        corners(b, x0, z0, x1, z1, 2, 4, stone);

        // ── 4) LAVA BLADE (the cook floor), y=4 over the standing floor ──────────
        // A 3×3 lava sheet on the chamber ceiling directly above the 3×3 hopper floor:
        // chicks are too short to reach it, but once they grow to adult height their
        // head enters the lava and they die cooked, dropping onto the hoppers below.
        // The glass walls (step 3) box the blade so it can't spill. Lava prints free.
        for (int x = cx - 1; x <= cx + 1; x++) {
            for (int z = cz - 1; z <= cz + 1; z++) {
                b.set(x, 4, z, lava);
            }
        }

        // ── 5) CONTROL STACK above the blade, y=5..7 ─────────────────────────────
        // A stone ceiling slab over the lava (so the blade is capped) carries the
        // redstone control gear: a dispenser facing DOWN through a gap drops eggs back
        // into the chamber, an observer + comparator + redstone ribbon tie the
        // comparator-read hopper's fill level to that dispenser (the auto-restock loop).
        floor(b, 5, x0, z0, x1, z1, stone);            // ceiling cap over the chamber
        // dispenser facing down at the centre — overwrites the stone cap cell so it can
        // throw eggs straight down into the growth chamber.
        b.set(cx, 5, cz, bs("minecraft:dispenser[facing=down,triggered=false]"));
        // comparator on the cap reading the chest/hopper fill, facing the dispenser;
        // an observer beside it cleans the pulse, and a redstone ribbon carries the
        // signal across the cap top. All sit on the solid y=5 stone ceiling.
        b.set(cx + 1, 6, cz, bs("minecraft:comparator[facing=west,mode=compare,powered=false]")); // → toward dispenser
        b.set(cx + 1, 6, cz + 1, bs("minecraft:observer[facing=west,powered=false]"));            // watches the dispenser line
        b.set(cx, 6, cz, redDust);                                                                // ribbon over the dispenser
        b.set(cx - 1, 6, cz, redDust);                                                            // ribbon run west
        // (every y=6 control cell sits on the solid y=5 stone ceiling laid above.)

        // ── 6) SLAB ROOF at y=7 ──────────────────────────────────────────────────
        // Smooth-stone top slabs cap the build and shelter the control gear.
        floor(b, 7, x0, z0, x1, z1, slabTop);

        // ── 7) LABEL SIGNS on the SOUTH face, flanking the chest, y=1 ────────────
        // Oak wall signs (FU-valued, recipe-derived) on the south rim either side of
        // the chest-access front so the build reads as the chicken coop.
        b.set(cx - 1, 1, z1, bs("minecraft:oak_wall_sign[facing=south]"));
        b.set(cx + 1, 1, z1, bs("minecraft:oak_wall_sign[facing=south]"));

        return b.build();
    }

    /**
     * §F.mushroom_farm — a STATIC underground low-ceiling mushroom farm, 9×9×5
     * (W×L×H) → builder(9, 5, 9). The "underground staple": a dark enclosed stone
     * chamber where small red/brown mushrooms grow on the dim planting beds, with a
     * bone-meal dispenser assist and a piston-+-water harvest that sweeps cut caps
     * into a collection chest.
     *
     * <p><b>Why this build is fully printable + render-safe.</b> Every block is a
     * vanilla FU-valued block or structural-free matter:
     * <ul>
     *   <li><b>Small mushroom plants</b> ({@code red_mushroom}/{@code brown_mushroom})
     *       are {@link net.minecraft.world.level.block.MushroomBlock} ⇒ a
     *       {@link net.minecraft.world.level.block.BushBlock}, so they're
     *       <b>structural-free</b> (planted growth, like crops/saplings) and print at
     *       no FU cost. We deliberately do NOT use the giant {@code *_mushroom_block}
     *       / {@code mushroom_stem} (those are the blocked mushroom_island_hut case)
     *       and NOT {@code mycelium}/{@code podzol}/{@code nylium} (UNVALUED).</li>
     *   <li><b>Base = stone</b> (3@1), with {@code stone_bricks} (3@1) ceiling/posts,
     *       {@code cobblestone} (1@1) end caps, {@code dirt} (1@1) growth pads;
     *       {@code dispenser}/{@code piston}/{@code chest} derive from crafting recipes
     *       and {@code hopper} is valued (100@3). {@code redstone_wire} is structural.</li>
     *   <li><b>Water</b> ({@code asItem()==AIR}) prints free; it both stays the
     *       harvested caps along the channel and reads as the harvest sluice.</li>
     *   <li>No glass panes / iron bars anywhere ⇒ the stub-pane render-integrity gate
     *       can never apply. The chamber is an enclosed dark box (mushrooms need low
     *       light) — the only torch is OUTSIDE the growing beds, at the player's
     *       access on the south rim.</li>
     * </ul>
     *
     * <p>Layout (south = +z is the "front"/access side; cx=4):
     * <ul>
     *   <li><b>y=0</b> — solid stone foundation (9×9). A <b>hopper line</b> runs down
     *       the centre (x=cx, z=1..6) all facing south, chaining cut caps to the
     *       collection <b>chest</b> at the south edge (x=cx, z=7, facing north).</li>
     *   <li><b>Water channel, y=1</b> — water along the centre column (x=cx, z=1..6)
     *       directly over the hopper line: knocked-loose caps drop onto the channel and
     *       the flow sweeps them south onto the hopper mouths below.</li>
     *   <li><b>Planting beds, y=1..2</b> — two <b>dirt</b> rows straddle the channel at
     *       x=cx-1 (3) and x=cx+1 (5), z=1..6; a small mushroom (red on the west bed,
     *       brown on the east bed) is pre-planted on each bed cell at y=2. Small
     *       mushrooms are BushBlocks ⇒ free.</li>
     *   <li><b>Harvest wall, y=2..3</b> — behind each bed (x=cx-2 (2) west / x=cx+2 (6)
     *       east) a stone backing at y=1 carries an <b>observer</b> at y=2 facing the
     *       mushroom and a <b>piston</b> at y=3 facing inward that shoves the cap off the
     *       bed and onto the water channel; a <b>redstone-dust</b> ribbon on a stone
     *       shelf one column further out (x=1 / x=7) ties each observer-back to its
     *       piston.</li>
     *   <li><b>Bone-meal dispensers, y=2</b> — on the north rim (z=0) at the two bed
     *       columns, facing south down each bed, loaded with bone meal for the player's
     *       growth assist.</li>
     *   <li><b>Dark chamber shell, y=1..4</b> — stone walls box the beds and a
     *       {@code stone_bricks} ceiling at y=4 keeps the interior dark (so the
     *       mushrooms survive); stone-brick corner posts tie it together. A single
     *       access torch sits OUTSIDE the beds on the south rim for the player.</li>
     *   <li><b>Label signs</b> — oak wall signs on the south face flank the chest.</li>
     * </ul>
     */
    private static Blueprint mushroomFarm() {
        Blueprint.Builder b = Blueprint.builder("Underground Mushroom Farm", 9, 5, 9);
        // all vanilla, all FU-valued / structural-free:
        BlueprintBlockState stone   = bs("minecraft:stone");
        BlueprintBlockState bricks  = STONE_BRICKS;                          // 3@1 — dark ceiling/posts
        BlueprintBlockState cobble  = COBBLE;
        BlueprintBlockState dirt    = bs("minecraft:dirt");                  // 1@1 — planting bed
        BlueprintBlockState redCap  = bs("minecraft:red_mushroom");          // MushroomBlock⇒BushBlock → free
        BlueprintBlockState brownCap= bs("minecraft:brown_mushroom");        // MushroomBlock⇒BushBlock → free
        BlueprintBlockState water   = WATER;                                 // structural (asItem()==AIR) → free
        BlueprintBlockState chest   = bs("minecraft:chest[facing=north,type=single,waterlogged=false]");
        BlueprintBlockState redDust = bs("minecraft:redstone_wire[east=none,west=none,north=none,south=none,power=0]"); // structural

        int x0 = 0, x1 = 8, z0 = 0, z1 = 8;            // 9×9 footprint
        int cx = 4;                                    // central water-channel / hopper column
        int bedZ0 = 1, bedZ1 = 6;                      // planting / channel run along Z

        // ── 1) STONE FOUNDATION at y=0, with the central HOPPER LINE ─────────
        floor(b, 0, x0, z0, x1, z1, stone);
        // hopper line down the centre, all facing south toward the chest.
        for (int z = bedZ0; z <= bedZ1; z++) {
            b.set(cx, 0, z, bs("minecraft:hopper[enabled=true,facing=south]"));
        }

        // ── 2) COLLECTION CHEST at the SOUTH end of the line, y=0 ────────────
        // The hopper line terminates at the chest tucked at z=7, facing north so its
        // front reads inward toward the channel. (z=8 is the chamber's south wall.)
        b.set(cx, 0, bedZ1 + 1, chest);                // z=7 collection chest

        // ── 3) WATER CHANNEL at y=1 over the hopper line ─────────────────────
        // Water along the centre column directly above the hoppers: knocked-loose caps
        // drop onto it and the flow sweeps them south onto the hopper mouths below.
        for (int z = bedZ0; z <= bedZ1; z++) {
            b.set(cx, 1, z, water);
        }

        // ── 4) PLANTING BEDS (dirt) + SMALL MUSHROOMS, flanking the channel ──
        // Two dirt rows at x=cx-1 (3, RED) and x=cx+1 (5, BROWN), z=1..6. A small
        // mushroom sits on every bed cell at y=2 (the planted cap; BushBlock → free).
        int wBedX = cx - 1, eBedX = cx + 1;            // 3 and 5
        for (int z = bedZ0; z <= bedZ1; z++) {
            b.set(wBedX, 1, z, dirt);                  // west bed pad
            b.set(eBedX, 1, z, dirt);                  // east bed pad
            b.set(wBedX, 2, z, redCap);                // planted red mushroom (free)
            b.set(eBedX, 2, z, brownCap);              // planted brown mushroom (free)
        }

        // ── 5) HARVEST WALL: stone backing + observer + piston + redstone ────
        // Behind each bed a stone column carries an observer (y=2, facing the mushroom)
        // and a piston (y=3, facing inward to shove the cap off the bed toward the
        // channel). Redstone dust on a stone shelf one column further out ties each
        // observer's back output to its piston. West wall faces east, east wall faces west.
        int wWallX = cx - 2, eWallX = cx + 2;          // 2 and 6 (observer/piston columns)
        int wShelfX = cx - 3, eShelfX = cx + 3;        // 1 and 7 (redstone-dust shelf columns)
        for (int z = bedZ0; z <= bedZ1; z++) {
            b.set(wWallX, 1, z, stone);
            b.set(eWallX, 1, z, stone);
            b.set(wWallX, 2, z, bs("minecraft:observer[facing=east,powered=false]"));
            b.set(eWallX, 2, z, bs("minecraft:observer[facing=west,powered=false]"));
            b.set(wWallX, 3, z, bs("minecraft:piston[facing=east,extended=false]"));
            b.set(eWallX, 3, z, bs("minecraft:piston[facing=west,extended=false]"));
            // redstone-dust shelf: solid stone y=1..3 with a dust ribbon at y=4 on top,
            // carrying the observer-back signal across to the piston.
            pillar(b, wShelfX, z, 1, 3, stone);
            pillar(b, eShelfX, z, 1, 3, stone);
            b.set(wShelfX, 4, z, redDust);
            b.set(eShelfX, 4, z, redDust);
        }

        // ── 6) BONE-MEAL DISPENSER ROW on the NORTH rim (z=0), y=2 ───────────
        // A dispenser at each bed column facing south down the bed, loaded with bone
        // meal for the player's growth assist. (Dispenser derives from a cobble + bow +
        // redstone recipe → FU-derived; its bone-meal contents are NBT, not a block.)
        b.set(wBedX, 2, z0, bs("minecraft:dispenser[facing=south,triggered=false]"));
        b.set(eBedX, 2, z0, bs("minecraft:dispenser[facing=south,triggered=false]"));

        // ── 7) DARK CHAMBER SHELL: stone walls + stone-brick ceiling, y=1..4 ─
        // Stone walls box the beds (mushrooms need an enclosed, dim space) and a
        // stone-brick ceiling at y=4 keeps the interior dark so the mushrooms survive;
        // stone-brick corner posts tie it together. Walls rise y=1..3 (the ceiling is
        // y=4). The harvest/shelf columns at x=1/7 already fill the wall line at those
        // x, so the ring just closes the remaining perimeter cells. We leave a 2-high
        // DOORWAY in the south wall at the WEST shelf column (x=wShelfX) by skipping
        // those two wall cells (air-skip: never written = enterable gap), then top it
        // with stone at y=3 as a lintel so the chamber stays dark above the door.
        int doorX = wShelfX;                         // x=1 — clear of the chest (cx) + beds
        for (int y = 1; y <= 3; y++) {
            line(b, y, x0, z0, x1, z0, stone);       // north wall
            line(b, y, x0, z0, x0, z1, stone);       // west wall
            line(b, y, x1, z0, x1, z1, stone);       // east wall
            // south wall with a 2-high doorway gap at doorX (skip y=1,2 there)
            for (int x = x0; x <= x1; x++) {
                if (x == doorX && y <= 2) continue;  // doorway opening
                b.set(x, y, z1, stone);
            }
        }
        corners(b, x0, z0, x1, z1, 1, 3, bricks);    // stone-brick corner posts
        floor(b, 4, x0, z0, x1, z1, bricks);         // dark stone-brick ceiling
        // re-lay the two redstone-dust ribbons on the y=4 shelf tops (the ceiling
        // floor() above would otherwise bury them); the dust rides on the solid stone
        // shelf pillars (y=1..3) and ties each observer-back to its piston. These two
        // edge cells (x=1/x=7) read as the wiring channel in the otherwise-sealed roof.
        for (int z = bedZ0; z <= bedZ1; z++) {
            b.set(wShelfX, 4, z, redDust);
            b.set(eShelfX, 4, z, redDust);
        }

        // ── 8) PLAYER ACCESS TORCH, OUTSIDE the dark beds ───────────────────
        // A single wall torch mounted on the INNER face of the south wall (the wall is
        // at z=8, the torch sits one cell inside at z=7 and faces north = away from the
        // wall into the access bay). It sits at x=doorX+1 (x=2), z=7 — clear of the
        // chest (x=cx) and one row south of the bed span (z=1..6), so it lights the
        // player's entry without raising the growing-bed light level (mushrooms need
        // darkness). facing=north means it's hung on the block to its south (the z=8
        // south wall), pointing into the room.
        wallTorch(b, doorX + 1, 2, z1 - 1, "north");

        // ── 9) END CAPS + LABEL SIGNS ────────────────────────────────────────
        // Cobble cap on the bed-span north end at y=1 frames the planter beds as a
        // contained trough (the south end is the chest/door line).
        line(b, 1, wWallX, z0, eWallX, z0, cobble);   // north end cap, y=1 (under dispensers)
        // oak wall signs on the south face flanking the chest (FU-valued, derived).
        b.set(wBedX, 1, z1, bs("minecraft:oak_wall_sign[facing=south]"));
        b.set(eBedX, 1, z1, bs("minecraft:oak_wall_sign[facing=south]"));

        return b.build();
    }

    /**
     * §I.koi_pond — an ORNAMENTAL koi pond, 7×7×3 (W×L×H) → builder(7, 3, 7).
     *
     * <p>A cottagecore garden water feature: a sunken water pool ringed by a
     * stone-brick / cobblestone rim, dressed with stone-button "pebble" accents, a
     * small arched stone bridge spanning the pond, fence-post lanterns at the
     * corners, and a sparse touch of sugar-cane greenery. Purely decorative — no
     * interior, no doors, no windows. Every block is vanilla and either FU-valued
     * (stone bricks / cobble / stairs / slabs / walls / buttons / fences /
     * lanterns / chains derive from stone or iron; sugar_cane=2@1) or structural
     * matter (water), so every cell clears the printability gate. No glass panes
     * or iron bars are used, so the render-integrity stub-pane gate never applies.
     *
     * <p>The "koi pond" garden references (lily pads / bamboo / dripleaf / seagrass
     * / leaves / flowers) are all UNVALUED and would silently fail to print, so they
     * are deliberately OMITTED. Greenery is rendered with a single short sugar-cane
     * stalk (valued) and the stonework itself carries the ornament.
     *
     * <p>Layout (x=0..6 W, z=0..6 depth, y up; cx=cz=3):
     * <ul>
     *   <li><b>y=0</b> — a full 7×7 stone-brick base. The interior is carved to a
     *       WATER disc (radius 2, centred at 3,3), leaving a one-cell stone-brick /
     *       cobble rim around the pool. Water is structural → prints free.</li>
     *   <li><b>y=0 rim accents</b> — stone <b>buttons</b> dotted on the flat rim as
     *       scattered "pebbles", and four cobble corner footing pads.</li>
     *   <li><b>y=1 edging</b> — a low stone-brick-wall ring around the OUTER edge of
     *       the pool (contains the water visually) plus a stone-brick-slab coping run
     *       on the outer rim corners.</li>
     *   <li><b>Arched bridge</b> — a 1-wide stone-brick stair arch running along z at
     *       x=cx, springing from the rim at y=1, cresting at y=2 over the pond centre,
     *       and descending to the far rim — a walkable hump the way {@link #stoneBridge}
     *       builds its crown.</li>
     *   <li><b>Lanterns</b> — oak-fence lamp posts at the four outer corners with a
     *       lantern resting on each cap (y=1 post, y=2 lantern; non-hanging, no backing
     *       needed), lighting the garden.</li>
     *   <li><b>Greenery</b> — a single sugar-cane stalk on a damp rim corner (valued,
     *       render-safe), the only living accent.</li>
     * </ul>
     */
    private static Blueprint koiPond() {
        Blueprint.Builder b = Blueprint.builder("Koi Pond", 7, 3, 7);
        int x0 = 0, x1 = 6, z0 = 0, z1 = 6;
        int cx = 3, cz = 3;

        // 1) STONE-BRICK BASE (7×7), then carve the interior to a WATER disc (r=2),
        //    leaving a one-cell stone-brick rim around the pool. Water is structural.
        floor(b, 0, x0, z0, x1, z1, STONE_BRICKS);
        disc(b, 0, cx, cz, 2, WATER);

        // 2) RIM PEBBLE ACCENTS — stone "buttons" dotted on the flat stone-brick rim
        //    (they derive from stone → FU-valued). face=floor so they sit flat as pebbles.
        BlueprintBlockState pebble = bs("minecraft:stone_button[face=floor,facing=north,powered=false]");
        for (int[] p : new int[][]{{1, 1}, {5, 1}, {1, 5}, {5, 5}, {3, 0}, {0, 3}, {6, 3}, {3, 6}}) {
            b.set(p[0], 0, p[1], pebble);
        }
        // cobble corner footing pads under the lamp posts (overwrite the brick base).
        for (int[] c : new int[][]{{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
            b.set(c[0], 0, c[1], COBBLE);
        }

        // 3) POOL EDGING — a low stone-brick-wall ring around the OUTER pool edge at
        //    y=1 (the ring just inside the rim, radius 2), reading as a raised coping
        //    that contains the water. circleRing draws the perimeter only.
        circleRing(b, 1, cx, cz, 2, STONE_BRICK_WALL);

        // 4) ARCHED STONE BRIDGE — a 1-wide span along z at x=cx. It springs from the
        //    near rim, crests over the pond centre, and descends to the far rim. Stairs
        //    ascending toward +z face south; descending toward -z face north (the
        //    verified stoneBridge convention), so every height change is walkable.
        BlueprintBlockState rampUp = bs("minecraft:stone_brick_stairs[facing=south,half=bottom,shape=straight]");
        BlueprintBlockState rampDown = bs("minecraft:stone_brick_stairs[facing=north,half=bottom,shape=straight]");
        // deck height by z-row across the bridge: rise 0→1→2 (crown at cz) →1→0.
        int[] deckY = {0, 1, 2, 2, 2, 1, 0};
        for (int z = z0; z <= z1; z++) {
            int dy = deckY[z];
            BlueprintBlockState surface;
            if (z < cz && deckY[z] < deckY[z + 1]) surface = rampUp;        // rising toward crown
            else if (z > cz && deckY[z] < deckY[z - 1]) surface = rampDown; // falling from crown
            else surface = STONE_BRICK_SLAB_TOP;                           // flat tread / crown
            b.set(cx, dy, z, surface);
        }

        // 5) LAMP POSTS — oak-fence post at each outer corner with a lantern on its cap.
        //    Non-hanging lantern rests on the post top (no backing needed).
        for (int[] c : new int[][]{{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
            b.set(c[0], 1, c[1], OAK_FENCE);
            b.set(c[0], 2, c[1], LANTERN);
        }

        // 6) GREENERY — a single short sugar-cane stalk on a rim corner cell adjacent to
        //    the water (valued 2@1, render-safe). Sits on the stone-brick rim at x=2,z=0
        //    (one cell out from the pool edge), one tall.
        b.set(2, 1, 0, bs("minecraft:sugar_cane[age=0]"));

        return b.build();
    }

    /**
     * Gazebo — an open garden shelter on a raised stone-brick/oak deck: four
     * stripped-oak corner posts, open oak-fence railings on three sides with a
     * walk-in entry gap at the front centre, and a peaked oak-stairs hip roof
     * crowned by a hanging lantern at the apex. Enterable, open-sided.
     * 7×7 footprint (T5), disc T1.
     *
     * <p>Vanilla, FU-valued blocks only — stone bricks, oak planks/slabs,
     * stripped-oak logs, oak fence, oak stairs, lanterns, chain. No
     * leaves/vines/flowers (UNVALUED). Glass panes are avoided here; the sides
     * are intentionally open (railings only). Stairs/slabs derive from their
     * base wood/stone, so every block is wound/printable.
     *
     * <p>Footprint x=0..6 (W), z=0..6 (depth); front is z=0.
     * <ul>
     *   <li>{@code y=0} stone-brick base slab over the whole 7×7 (the deck
     *       footing; walkable ground = top of y=0).</li>
     *   <li>{@code y=1} oak-plank deck floor inset one cell (x,z = 1..5) inside a
     *       stone-brick-slab rim coping, so the deck reads as a raised platform
     *       with a stone lip and an open front step at the entry.</li>
     *   <li>{@code y=1..3} four stripped-oak corner posts + four edge-midpoint
     *       posts; open oak-fence railings spanning the perimeter at waist height
     *       (y=2), with the two front-centre cells left open as the entrance.</li>
     *   <li>{@code y=4..7} oak-stairs hip roof seated on the posts, converging to
     *       an oak-plank cap at the apex (y=7).</li>
     *   <li>a hanging lantern on a chain dropped from the roof apex to light the
     *       interior, plus four hanging eave lanterns under the corner brackets.</li>
     * </ul>
     */
    private static Blueprint gazebo() {
        final int W = 7, H = 8, D = 7;
        Blueprint.Builder b = Blueprint.builder("Gazebo", W, H, D);
        int x0 = 0, x1 = W - 1, z0 = 0, z1 = D - 1; // 0..6
        int cx = 3, cz = 3;
        BlueprintBlockState post = STRIPPED_OAK_Y;
        BlueprintBlockState railing = OAK_FENCE;
        BlueprintBlockState deckTrim = STONE_BRICK_SLAB_TOP;
        int postTop = 3;   // posts y=1..3
        int roofY = 4;     // lowest eave course

        // 1) stone-brick base slab over the whole footprint (y=0 footing).
        floor(b, 0, x0, z0, x1, z1, STONE_BRICKS);

        // 2) raised oak-plank deck inset one cell (x,z = 1..5) at y=1, ringed by a
        //    stone-brick-slab coping on the perimeter so the deck reads as a raised
        //    platform with a stone lip. The front-centre rim cell (cx,z0) is left as
        //    an open step into the gazebo.
        floor(b, 1, x0 + 1, z0 + 1, x1 - 1, z1 - 1, OAK_PLANKS);
        for (int x = x0; x <= x1; x++) {
            if (x != cx) b.set(x, 1, z0, deckTrim); // front rim, gap at entry step
            b.set(x, 1, z1, deckTrim);              // back rim
        }
        for (int z = z0 + 1; z <= z1 - 1; z++) {
            b.set(x0, 1, z, deckTrim);              // west rim
            b.set(x1, 1, z, deckTrim);              // east rim
        }

        // 3) eight stripped-oak posts: four corners + four edge midpoints, y=1..3.
        int[][] posts = {
                {x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}, // corners
                {cx, z0}, {cx, z1}, {x0, cz}, {x1, cz}  // edge midpoints
        };
        for (int[] q : posts) {
            pillar(b, q[0], q[1], 1, postTop, post);
        }

        // 4) open oak-fence railings spanning between the posts at y=2 (waist
        //    height). The FRONT centre — the two cells flanking the front mid-post
        //    (cx-1,z0) and (cx+1,z0) — is left open as the walk-in entrance. Fences
        //    self-reconcile their connections at print time.
        for (int x = x0; x <= x1; x++) {
            if (x != cx - 1 && x != cx + 1) b.set(x, 2, z0, railing); // front, entry gap
            b.set(x, 2, z1, railing);                                  // back, full
        }
        for (int z = z0 + 1; z <= z1 - 1; z++) {
            b.set(x0, 2, z, railing); // west rail
            b.set(x1, 2, z, railing); // east rail
        }
        // re-stamp the post cells the rail loop overwrote (corners + front/side mids).
        for (int[] q : posts) {
            b.set(q[0], 2, q[1], post);
        }

        // 5) peaked oak-stairs hip roof seated on the posts, converging to an
        //    oak-plank cap at the apex. For a 7-wide footprint the hip steps
        //    0..6 → 1..5 → 2..4 → cap(3,3): courses at y=4,5,6 and the apex cap
        //    at y=7.
        hipRoof(b, x0, z0, x1, z1, roofY, "oak_stairs", OAK_PLANKS);

        // 6) central hanging lantern: a chain dropped from just under the apex with
        //    a lantern below it, lighting the open interior. Apex cap is at y=7; hang
        //    a chain at y=6 and the lantern at y=5 (above head height, clear of the
        //    deck).
        b.set(cx, 6, cz, CHAIN);
        b.set(cx, 5, cz, HANGING_LANTERN);

        // 7) four hanging eave lanterns under the corner roof brackets (offset one
        //    cell inward from each corner so they hang in the bay, backed by the
        //    lowest eave ring at y=4).
        for (int[] c : new int[][]{{x0 + 1, z0 + 1}, {x1 - 1, z0 + 1}, {x0 + 1, z1 - 1}, {x1 - 1, z1 - 1}}) {
            b.set(c[0], 3, c[1], HANGING_LANTERN);
        }

        return b.build();
    }

    /**
     * Pergola Garden — a wooden pergola walkway over a garden path. A row of three
     * stripped-oak post pairs (at x=1 and x=5) carry overhead cross-beams; an
     * oak-trapdoor lattice forms the open pergola roof. A dirt-path walkway runs
     * down the centre (x=3, structural → prints free); stone-brick-edged planter
     * boxes fill the four corner quadrants with farmland+wheat (structural). Hanging
     * lanterns on chains light the walk. 7×7 footprint (T5), disc T1.
     *
     * <p>Vanilla, FU-valued/structural blocks only. The pergola reads from its
     * wood frame + trapdoor lattice — NO leaves/vines/flowers (all UNVALUED). The
     * "garden" greenery is moss-block planter bases + structural farmland/wheat, so
     * everything is printable. Trapdoors are not {@code IronBarsBlock}, so the open
     * lattice never trips the stub-pane render gate (no panes/bars are used at all).
     *
     * <p>Footprint x=0..6 (W), z=0..6 (depth), y=0..5 (H=6):
     * <ul>
     *   <li>{@code y=0} cobblestone footing over the full footprint; a central
     *       dirt-path walkway (column x=3, z=0..6) — structural, free; four
     *       stone-brick-edged planter boxes (the corner quadrants) filled with
     *       farmland+wheat (structural) over a moss-block course.</li>
     *   <li>{@code y=1..3} six stripped-oak posts: pairs at x=1 & x=5 for z=1,3,5.</li>
     *   <li>{@code y=4} overhead pergola frame: two longitudinal stripped-oak
     *       beams (axis=z) along x=1 and x=5, and three cross-beams (axis=x)
     *       spanning x=1..5 over each post pair.</li>
     *   <li>{@code y=5} oak-trapdoor lattice (top-half, open) laid over the bays
     *       between the beams — the airy pergola "roof".</li>
     *   <li>hanging lanterns on chains under the centre cross-beam.</li>
     * </ul>
     */
    private static Blueprint pergolaGarden() {
        final int W = 7, H = 6, D = 7;
        Blueprint.Builder b = Blueprint.builder("Pergola Garden", W, H, D);
        int x0 = 0, x1 = W - 1, z0 = 0, z1 = D - 1; // 0..6
        int walkX = 3;                              // central walkway column
        BlueprintBlockState post = STRIPPED_OAK_Y;
        BlueprintBlockState beamX = STRIPPED_OAK_X;
        BlueprintBlockState beamZ = bs("minecraft:stripped_oak_log[axis=z]");
        BlueprintBlockState planterEdge = STONE_BRICKS;
        BlueprintBlockState planterBase = bs("minecraft:moss_block");
        BlueprintBlockState latticeTop =
                bs("minecraft:oak_trapdoor[facing=north,half=top,open=true,powered=false,waterlogged=false]");
        int[] postX = {1, 5};       // the two post rows flanking the walkway
        int[] postZ = {1, 3, 5};    // three post pairs along the path
        int postTop = 3;            // posts y=1..3
        int beamY = 4;              // overhead frame course
        int latticeY = 5;          // pergola lattice roof

        // 1) cobblestone footing over the whole footprint (walkable ground = top of y=0).
        floor(b, 0, x0, z0, x1, z1, COBBLE);

        // 2) central dirt-path walkway down x=3 (structural → prints free), overwriting
        //    the footing so the path reads sunken into the courtyard.
        path(b, walkX, z0, z1);

        // 3) four corner planter quadrants flanking the walkway: stone-brick-edged
        //    boxes filled with a moss-block course topped by structural farmland+wheat.
        //    Quadrants are x∈{0..1} (west) / x∈{5..6} (east) by z∈{0..1} / z∈{5..6};
        //    the post cells (x=1/5, z=1/5) sit on the inner planter corners, so the
        //    posts rise straight out of the garden beds.
        int[][] planters = {
                {x0, z0, x0 + 1, z0 + 1}, {x1 - 1, z0, x1, z0 + 1}, // north-west / north-east
                {x0, z1 - 1, x0 + 1, z1}, {x1 - 1, z1 - 1, x1, z1}  // south-west / south-east
        };
        for (int[] q : planters) {
            // stone-brick rim around the bed (one course up at y=1).
            fenceRing(b, 1, q[0], q[1], q[2], q[3], planterEdge);
            // inner soil cell of each 2×2 bed (the single cell diagonally inset from
            // the outer corner) gets moss base + farmland + wheat.
            int ix = (q[0] == x0) ? q[0] : q[2]; // outer corner x
            int iz = (q[1] == z0) ? q[1] : q[3]; // outer corner z
            b.set(ix, 0, iz, planterBase);   // moss base under the bed corner
            b.set(ix, 1, iz, FARMLAND);      // structural farmland
            b.set(ix, 2, iz, WHEAT);         // structural crop
        }

        // 4) six stripped-oak posts (pairs at x=1 & x=5 for z=1,3,5), y=1..3.
        for (int px : postX) {
            for (int pz : postZ) {
                pillar(b, px, pz, 1, postTop, post);
            }
        }

        // 5) overhead pergola frame at y=4: two longitudinal beams (axis=z) along
        //    x=1 and x=5 spanning z=1..5, plus three cross-beams (axis=x) over each
        //    post pair spanning x=1..5. Cross-beams are stamped after so the
        //    intersections read as crossing timbers.
        for (int px : postX) {
            line(b, beamY, px, postZ[0], px, postZ[postZ.length - 1], beamZ);
        }
        for (int pz : postZ) {
            line(b, beamY, postX[0], pz, postX[1], pz, beamX);
        }

        // 6) oak-trapdoor lattice "roof" at y=5, top-half open, laid across the bays
        //    bounded by the frame (x=1..5, z=1..5). Skip the four beam corners so the
        //    lattice reads as slats spanning between the timbers, leaving an airy,
        //    open pergola canopy. Trapdoors are not IronBars → no stub-pane risk.
        for (int x = postX[0]; x <= postX[1]; x++) {
            for (int z = postZ[0]; z <= postZ[postZ.length - 1]; z++) {
                boolean isFrameCorner =
                        (x == postX[0] || x == postX[1]) && (z == postZ[0] || z == postZ[postZ.length - 1]);
                if (isFrameCorner) continue;
                // alternate slats in a lattice checker so the canopy reads as open weave.
                if (((x + z) & 1) == 0) {
                    b.set(x, latticeY, z, latticeTop);
                }
            }
        }

        // 7) hanging lanterns on chains under the centre cross-beam (the z=3 beam),
        //    dropped over the walkway flanks (x=2 and x=4 — clear of the post columns)
        //    so they light the walk without blocking it. The cross-beam at y=4 backs
        //    the chain.
        for (int lx : new int[]{2, 4}) {
            b.set(lx, beamY - 1, postZ[1], CHAIN);
            b.set(lx, beamY - 2, postZ[1], HANGING_LANTERN);
        }

        return b.build();
    }

    /**
     * §I Stonehenge Ring. 9×9 footprint → builder(9,7,9). A weathered megalithic
     * circle: six outer <i>trilithons</i> (paired stone uprights capped by a
     * horizontal lintel) arranged around a ~9×9 ring, plus two taller inner
     * trilithons forming the central "great trilithon" pair. No grass floor is
     * printed — the monument sits on the player's existing ground — only a sparse
     * ring of foundation/heel stones marks the base. Weathering is conveyed by
     * mixing stone / cobblestone / mossy-cobblestone / mossy-stone-bricks /
     * andesite across the megaliths so no two read identically. Stone-slab caps sit
     * atop the tallest lintels as worn capstones. All blocks are vanilla and FU
     * valued or recipe-derived (slabs/mossy variants). Decorative landmark; T3 disc.
     */
    private static Blueprint stonehengeRing() {
        Blueprint.Builder b = Blueprint.builder("Stonehenge Ring", 9, 7, 9);
        // Weathered megalith palette — cycle these so adjacent stones differ.
        BlueprintBlockState stone = bs("minecraft:stone");
        BlueprintBlockState cobble = bs("minecraft:cobblestone");
        BlueprintBlockState mossyCobble = bs("minecraft:mossy_cobblestone");
        BlueprintBlockState mossyBrick = bs("minecraft:mossy_stone_bricks");
        BlueprintBlockState andesite = bs("minecraft:andesite");
        // Worn capstone slabs (top-half) for the lintel crowns.
        BlueprintBlockState stoneSlabTop = bs("minecraft:stone_slab[type=top]");
        BlueprintBlockState cobbleSlabTop = bs("minecraft:cobblestone_slab[type=top]");

        BlueprintBlockState[] megalith = { mossyCobble, stone, andesite, cobble, mossyBrick };

        // ── SPARSE FOUNDATION / HEEL STONES (y0) ──────────────────────────────
        // No solid floor (the player's ground stays). Just a thin scatter of
        // weathered base stones marking the circle and the central altar — so the
        // ring reads as a deliberate monument footprint, not floating megaliths.
        // Heel stones on the four cardinal edges of the ring:
        b.set(4, 0, 0, mossyCobble);   // north heel
        b.set(4, 0, 8, mossyCobble);   // south heel
        b.set(0, 0, 4, andesite);      // west heel
        b.set(8, 0, 4, andesite);      // east heel
        // Central altar stone (the "Altar Stone"): a small 1×3 mossy slab line.
        b.set(3, 0, 4, mossyBrick);
        b.set(4, 0, 4, mossyBrick);
        b.set(5, 0, 4, mossyBrick);

        // ── SIX OUTER TRILITHONS (uprights y1..y3, lintel y4) ─────────────────
        // Each trilithon is two uprights one cell apart along a tangent of the
        // ring, bridged by a horizontal lintel at their crown. Uprights are 3 tall
        // (y1..y3); the lintel spans the two upright tops plus the gap (y4). Pairs
        // are placed around the perimeter at the four corners-ish + two flanks so
        // the silhouette reads as a circle of standing gateways.
        //
        // {ax,az}=first upright, {bx,bz}=second upright (always 1 apart),
        // lintel runs along the shared axis covering both tops and the gap.
        int[][] outer = {
            // north edge — two trilithons flanking the north heel, lintels along X
            {2, 1, 3, 1},   // NW gate (uprights x=2,3 at z=1)
            {5, 1, 6, 1},   // NE gate (uprights x=5,6 at z=1)
            // south edge — two trilithons, lintels along X
            {2, 7, 3, 7},   // SW gate
            {5, 7, 6, 7},   // SE gate
            // west & east flanks — single gates, lintels along Z
            {1, 4, 1, 5},   // W gate (uprights z=4,5 at x=1) — shifted to keep in-bounds
            {7, 3, 7, 4},   // E gate (uprights z=3,4 at x=7)
        };
        int oi = 0;
        for (int[] t : outer) {
            int ax = t[0], az = t[1], bx = t[2], bz = t[3];
            BlueprintBlockState upMat = megalith[oi % megalith.length];
            BlueprintBlockState lintelMat = megalith[(oi + 2) % megalith.length];
            // two uprights, 3 tall
            pillar(b, ax, az, 1, 3, upMat);
            pillar(b, bx, bz, 1, 3, upMat);
            // lintel at y4 bridging the two upright crowns (covers a..b along the
            // axis they differ on, inclusive — the single gap cell between them
            // included since |a-b| is exactly 1 → just the two crown cells).
            int dx = Integer.signum(bx - ax);
            int dz = Integer.signum(bz - az);
            int cx = ax, cz = az;
            b.set(cx, 4, cz, lintelMat);
            while (cx != bx || cz != bz) {
                cx += dx; cz += dz;
                b.set(cx, 4, cz, lintelMat);
            }
            oi++;
        }

        // ── TWO TALLER INNER TRILITHONS (the "Great Trilithon" pair) ──────────
        // Inner uprights are 4 tall (y1..y4) with the lintel at y5, set inside the
        // ring facing the centre. They tower over the outer ring (the classic
        // Stonehenge sarsen-trilithon silhouette). Capstone slabs crown each lintel.
        int[][] inner = {
            // a northward-facing inner gate: uprights x=3,5 at z=3, lintel x3..x5 @ z=3
            {3, 3, 5, 3},
            // a southward-facing inner gate: uprights x=3,5 at z=5, lintel x3..x5 @ z=5
            {3, 5, 5, 5},
        };
        int ii = 0;
        for (int[] t : inner) {
            int ax = t[0], az = t[1], bx = t[2], bz = t[3];
            BlueprintBlockState upMat = (ii == 0) ? mossyBrick : andesite;
            BlueprintBlockState lintelMat = (ii == 0) ? stone : mossyCobble;
            // two uprights, 4 tall
            pillar(b, ax, az, 1, 4, upMat);
            pillar(b, bx, bz, 1, 4, upMat);
            // lintel at y5 spanning the full inner span (x=ax..bx along z=az)
            line(b, 5, ax, az, bx, bz, lintelMat);
            // worn capstone slabs atop the lintel ends (y6) — the crowning sarsens
            BlueprintBlockState cap = (ii == 0) ? stoneSlabTop : cobbleSlabTop;
            b.set(ax, 6, az, cap);
            b.set(bx, 6, bz, cap);
            ii++;
        }

        return b.build();
    }

    /**
     * §I Garden Archway. 5×3 footprint → builder(5, 5, 3). A walk-through arched
     * garden gateway: two stone-brick pillars (x=1 & x=3) on the gate plane (z=1)
     * carry a stepped stone-brick-stair arch over a central path that runs straight
     * through the x=2 opening along Z (north→south), so a player walks under the
     * arch. Oak-fence side trellises flank each pillar (z=0 & z=2) and are rail-tied
     * across their tops; a hanging lantern on a chain lights the passage from the
     * keystone; empty flower-pot planters sit at the four outer corners; a dirt-path
     * strip (structural → prints free) marks the walkway. Weathering is conveyed by
     * mixing stone-brick / mossy-stone-brick / cracked-stone-brick. All blocks are
     * vanilla and FU-valued (or recipe-derived: stairs/walls/mossy variants); no
     * leaves/vines/flowers. Decorative landmark; T1 disc.
     */
    private static Blueprint gardenArchway() {
        final int W = 5, H = 5, D = 3;
        Blueprint.Builder b = Blueprint.builder("Garden Archway", W, H, D);
        // Weathered stone palette — cycle so the masonry reads aged, not uniform.
        BlueprintBlockState mossyBrick = MOSSY_STONE_BRICKS;
        BlueprintBlockState crackedBrick = CRACKED_STONE_BRICKS;
        // Stone-brick-stair voussoirs for the arch (spring inward over the opening).
        BlueprintBlockState archWest = bs("minecraft:stone_brick_stairs[facing=east,half=bottom,shape=straight]");
        BlueprintBlockState archEast = bs("minecraft:stone_brick_stairs[facing=west,half=bottom,shape=straight]");
        // Flower-pot planter (empty pot is FU-valued; leaves/flowers are not).
        BlueprintBlockState flowerPot = bs("minecraft:flower_pot");

        int gateZ = 1;        // the gate plane: pillars + arch sit on z=1
        int wPillarX = 1;     // west pillar column
        int ePillarX = 3;     // east pillar column
        int openX = 2;        // the walk-through opening column

        // ── PATH (y0) ─────────────────────────────────────────────────────────
        // A dirt-path strip straight down x=2 (z=0..2) marks the walkway. It is an
        // itemless structural block → prints free and reads as a sunken garden path
        // under the arch. No solid floor otherwise — the arch sits on the player's
        // existing ground, framing the path.
        path(b, openX, 0, D - 1);

        // ── TWO STONE-BRICK PILLARS (y1..y3) ──────────────────────────────────
        // The gateway jambs the arch springs from. Cracked-brick bases + chiseled
        // mid-course + mossy crown so each pillar reads as weathered dressed stone.
        b.set(wPillarX, 1, gateZ, crackedBrick);
        b.set(wPillarX, 2, gateZ, CHISELED_STONE_BRICKS);
        b.set(wPillarX, 3, gateZ, mossyBrick);
        b.set(ePillarX, 1, gateZ, crackedBrick);
        b.set(ePillarX, 2, gateZ, CHISELED_STONE_BRICKS);
        b.set(ePillarX, 3, gateZ, mossyBrick);

        // ── STEPPED ARCH (y3 springers → y4 keystone) ─────────────────────────
        // Voussoir stairs spring inward from each pillar crown toward the opening,
        // bridged by a stone-brick keystone over x=2. The opening (x=2, y=1..3)
        // stays air, so the gateway is a true walk-through arch (head clearance =
        // 3 blocks under the keystone). A worn mossy-stone-brick-slab cap crowns the
        // keystone so the arch reads as a finished crest.
        b.set(wPillarX, 4, gateZ, archWest);                   // west voussoir leaning over the gap
        b.set(ePillarX, 4, gateZ, archEast);                   // east voussoir leaning over the gap
        b.set(openX, 4, gateZ, STONE_BRICKS);                  // keystone bridging the two voussoirs
        // The keystone (y4) is the crest — footprint H=5 caps at y=4, so no cap block
        // sits above it; the two springer stairs + keystone form the finished arch.

        // ── OAK-FENCE SIDE TRELLISES (y1..y2) ─────────────────────────────────
        // Each pillar gains a flanking oak-fence trellis post on its z=0 and z=2
        // faces (two tall). Each post connects horizontally to the solid stone-brick
        // pillar cell at the same y (z=1), so no fence end floats — the trellis reads
        // as a garden screen the player passes between. Oak fence (not glass-pane/
        // iron-bars) → no render-integrity stub risk regardless.
        for (int px : new int[]{wPillarX, ePillarX}) {
            pillar(b, px, 0, 1, 2, OAK_FENCE);          // north trellis post (connects to pillar at z=1)
            pillar(b, px, D - 1, 1, 2, OAK_FENCE);      // south trellis post (connects to pillar at z=1)
        }

        // ── HANGING LANTERN (under the keystone) ──────────────────────────────
        // A chain hung from the keystone with a hanging lantern below it, dropped
        // into the opening so it lights the passage without blocking head height.
        b.set(openX, 3, gateZ, CHAIN);                  // chain hung off the keystone (y4) → y3
        b.set(openX, 2, gateZ, HANGING_LANTERN);        // lantern at y2, clear of the y1 walk floor

        // ── FLOWER-POT PLANTERS (y1, four outer corners) ──────────────────────
        // Empty flower pots flank the gateway at the outer corners, raised on a
        // single cracked-stone-brick plinth so they read as framing planters.
        int[][] corners = {{0, 0}, {W - 1, 0}, {0, D - 1}, {W - 1, D - 1}};
        for (int[] c : corners) {
            b.set(c[0], 0, c[1], crackedBrick); // plinth (sits on ground at y0)
            b.set(c[0], 1, c[1], flowerPot);    // empty planter pot atop the plinth
        }

        return b.build();
    }

    /**
     * Scarecrow — a farm straw-man on a cross frame: a hay-bale body stacked on a
     * fence post, outstretched oak-fence arms forming the shoulder crossbar, a
     * white-wool "straw" head, and an oak-trapdoor sun-hat brim crowning it. A
     * tilled-farmland patch underfoot grounds it as a field guardian, with a stub
     * of wheat at the base reading as scattered straw.
     *
     * <p>NOTE: pumpkin / carved_pumpkin / jack_o_lantern are UNVALUED in the FU
     * economy (unprintable), so this build deliberately uses a <b>wool head</b> +
     * <b>hay body</b> + <b>trapdoor hat</b> straw-man read instead of the classic
     * pumpkin head — a cross + hat + hay torso reads unmistakably as a scarecrow.
     *
     * <p>Vanilla, FU-valued blocks only — farmland (structural-itemless, free),
     * wheat (structural-itemless, free), oak fence, hay block, white wool, oak
     * trapdoor (derives from oak planks). No glass panes / iron bars → no
     * render-integrity stub risk; the outstretched fence arms connect to the
     * centre fence post (and are explicitly fine as "arms").
     *
     * <p>Footprint x=0..2 (W=3), z=0 (D=1), y=0..5 (H=6). Front faces z=0.
     * <ul>
     *   <li>{@code y=0} a tilled-farmland strip (the field the scarecrow guards);
     *       a wheat stalk beside the post reads as straw at its feet.</li>
     *   <li>{@code y=1} hay-bale lower body seated on the farmland.</li>
     *   <li>{@code y=2} hay-bale torso.</li>
     *   <li>{@code y=3} oak-fence neck/shoulders, with outstretched oak-fence arms
     *       at x=0 and x=2 forming the crossbar the straw-man hangs from.</li>
     *   <li>{@code y=4} white-wool "straw" head on the post.</li>
     *   <li>{@code y=5} an oak trapdoor (half=bottom) capping the head as a floppy
     *       sun-hat brim.</li>
     * </ul>
     */
    private static Blueprint scarecrow() {
        final int W = 3, H = 6, D = 1;
        Blueprint.Builder b = Blueprint.builder("Scarecrow", W, H, D);
        final int cx = 1, z = 0;

        // 1) FIELD UNDERFOOT — a tilled-farmland strip the guardian stands in.
        //    Farmland is itemless-structural → prints free, grounds the scene.
        floor(b, 0, 0, z, W - 1, z, FARMLAND);

        // 2) STRAW AT ITS FEET — a wheat stalk beside the post base reads as loose
        //    straw (itemless-structural → free). Placed off-centre so it doesn't
        //    fight the body column.
        b.set(0, 1, z, WHEAT);

        // 3) HAY-BALE BODY — two bales stacked on the centre cell form the torso.
        b.set(cx, 1, z, HAY); // lower body
        b.set(cx, 2, z, HAY); // upper torso

        // 4) FENCE FRAME — the centre post continues up as the neck/shoulders, with
        //    outstretched oak-fence arms forming the scarecrow's crossbar. The arms
        //    connect to the centre post, so they render as proper extended arms.
        b.set(cx, 3, z, OAK_FENCE);     // neck / shoulders (the cross hub)
        b.set(0, 3, z, OAK_FENCE);      // left arm (outstretched)
        b.set(W - 1, 3, z, OAK_FENCE);  // right arm (outstretched)

        // 5) HEAD — a white-wool "straw" head atop the fence neck.
        b.set(cx, 4, z, WHITE_WOOL);

        // 6) HAT — an oak trapdoor (half=bottom) sits flat on the head as a
        //    wide-brim sun-hat. Trapdoor derives from oak planks → FU-valued; no
        //    pane/bar so no render-integrity stub risk.
        b.set(cx, 5, z, bs("minecraft:oak_trapdoor[facing=north,half=bottom,open=false,powered=false,waterlogged=false]"));

        return b.build();
    }

    /**
     * §I Flower Shop. 7×7 footprint → builder(7, 6, 7); disc T1. A cottagecore
     * glass-fronted commerce stall: an oak-framed shop whose entire FRONT wall
     * (north, {@code z=0}) is a big glass shopfront pierced by a central doorway,
     * shaded by a striped white/red wool awning. Inside, a trapdoor-and-slab
     * counter, barrels of stock, flower-pot displays (empty {@code flower_pot} —
     * recipe-derived from brick → FU-valued — plus a couple of structural
     * {@code potted_*} blooms for colour), and chain-backed hanging lanterns. The
     * back/side walls are solid oak planks with a glass window each; the shop is
     * ENTERABLE through the front door.
     *
     * <p>RENDER-SAFETY: every glass pane in the shopfront and the side/back windows
     * is flanked HORIZONTALLY by an oak-log mullion or a plank-wall cell (or another
     * pane in the same run), so each pane has at least one connectable neighbour and
     * never ships as an invisible stub (see {@link CuratedBlueprintRenderIntegrityGameTests}).
     * All blocks are vanilla and FU-valued, recipe-derived, or itemless-structural.
     */
    private static Blueprint flowerShop() {
        final int W = 7, H = 6, D = 7;
        Blueprint.Builder b = Blueprint.builder("Flower Shop", W, H, D);
        int x0 = 0, x1 = W - 1, z0 = 0, z1 = D - 1; // 0..6
        int wallH = 3;                              // walls y=1..3
        int cx = (x0 + x1) / 2;                     // centre column (x=3)

        // 1) WALKABLE FOUNDATION — oak-plank floor over the whole footprint (top of
        //    y=0). A spruce-plank threshold strip just inside the door reads as a mat.
        floor(b, 0, x0, z0, x1, z1, OAK_PLANKS);

        // 2) FRAME — oak-log corner posts y=1..3 + a back/side plank-wall ring. The
        //    FRONT (north, z=0) wall is intentionally NOT filled with plank here; it
        //    becomes the glass shopfront in step 3. So we write the three solid walls
        //    (south z=6, west x=0, east x=6) and the corner posts, then carve.
        corners(b, x0, z0, x1, z1, 1, wallH, OAK_LOG_Y);
        for (int y = 1; y <= wallH; y++) {
            line(b, y, x0, z1, x1, z1, OAK_PLANKS); // south (back) wall
            line(b, y, x0, z0, x0, z1, OAK_PLANKS); // west wall
            line(b, y, x1, z0, x1, z1, OAK_PLANKS); // east wall
        }

        // 3) GLASS SHOPFRONT — the north wall (z=0). A central oak-log mullion at the
        //    door jambs (x=2 and x=4) plus the corner posts (x=0,x=6) divide the front
        //    into glass bays. Every pane cell (y=1..3) sits between two solid mullions
        //    along X, so it connects horizontally → render-safe, no stub panes.
        //    Mullions:
        pillar(b, 2, z0, 1, wallH, OAK_LOG_Y); // left door jamb / mullion
        pillar(b, 4, z0, 1, wallH, OAK_LOG_Y); // right door jamb / mullion
        //    Glass bays: x=1 (between corner post x=0 and jamb x=2) and x=5 (between
        //    jamb x=4 and corner post x=6), full height y=1..3.
        for (int y = 1; y <= wallH; y++) {
            b.set(1, y, z0, GLASS_PANE);
            b.set(5, y, z0, GLASS_PANE);
        }
        //    A clerestory glass strip ABOVE the door (x=3) at the wall top course so
        //    the shopfront reads as continuous glazing over the entrance. It connects
        //    to the two log jambs at x=2/x=4 → render-safe.
        b.set(cx, wallH, z0, GLASS_PANE);
        //    Door dead-centre in the front wall, opening inward (faces south).
        door2(b, cx, 1, z0, "oak", "N");

        // 4) SIDE & BACK WINDOWS — one glass pane mid-wall on each solid wall. Each is
        //    embedded in its plank run, so its horizontal neighbours are plank cells
        //    (sturdy faces) → render-safe.
        window2(b, x0, 2, 3, GLASS_PANE, null); // west wall window
        window2(b, x1, 2, 3, GLASS_PANE, null); // east wall window
        window2(b, cx, 2, z1, GLASS_PANE, null); // back wall window

        // 5) ROOF — a flat oak-slab roof over the shop at y=wallH+1, with a one-cell
        //    overhang on the front so the awning tucks under it. Slabs derive from
        //    planks → FU-valued.
        flatRoof(b, wallH + 1, x0, z0, x1, z1, OAK_SLAB_BOTTOM);

        // 6) STRIPED AWNING — a sloped white/red wool canopy over the shopfront,
        //    projecting out in FRONT of the door line at the floor's z=0 edge. The
        //    awning is one course below the roof (y=wallH) sloping down to the front
        //    lip (y=wallH-1) one cell proud (z=0 outer face). Built from a striped
        //    wool band capped by a stair lip so it reads as a market awning.
        int awnY = wallH + 1;                       // back of the awning, flush under the roof line
        // back/high row of the awning sits on the wall plate at z=0, top course:
        for (int x = x0; x <= x1; x++) {
            b.set(x, awnY, z0, (((x - x0) / 1) % 2 == 0) ? WHITE_WOOL : RED_WOOL); // striped band
        }
        // front lip: a row of top-half oak stairs one cell proud is NOT possible
        // outside the footprint, so the awning's outer edge is the wool band itself;
        // a row of front-facing bottom stairs UNDER the band (y=wallH, z=0) gives the
        // canopy its sloped underside without leaving the volume.
        for (int x = x0; x <= x1; x++) {
            b.set(x, wallH, z0, bs("minecraft:oak_stairs[facing=south,half=top,shape=straight]"));
        }

        // 7) COUNTER — an L of oak trapdoors (closed, half=bottom) on slab supports
        //    just inside the shopfront forms a waist-high sales counter. Trapdoors and
        //    slabs derive from planks → FU-valued; trapdoors are NOT IronBars, so no
        //    stub-pane risk. The counter runs along the inside of the front-left bay
        //    and turns down the west side, leaving the door path (x=3) clear.
        BlueprintBlockState counterTop =
                bs("minecraft:oak_trapdoor[facing=north,half=bottom,open=false,powered=false,waterlogged=false]");
        // support course (y=1) under the counter so it reads solid, then the trapdoor
        // "countertop" at y=2.
        b.set(1, 1, 1, OAK_SLAB_BOTTOM); b.set(1, 2, 1, counterTop);
        b.set(1, 1, 2, OAK_SLAB_BOTTOM); b.set(1, 2, 2, counterTop);
        b.set(5, 1, 1, OAK_SLAB_BOTTOM); b.set(5, 2, 1, counterTop); // right-bay counter stub

        // 8) STOCK BARRELS — flower-shop supplies behind the counter, against the back
        //    wall. Barrels derive from planks+slabs → printable.
        b.set(1, 1, z1 - 1, BARREL);
        b.set(2, 1, z1 - 1, BARREL);
        b.set(x1 - 1, 1, z1 - 1, BARREL);

        // 9) FLOWER-POT DISPLAYS — the floral identity. Empty flower_pots line the
        //    front windowsill (on the counter and back shelf) and the back wall; a
        //    couple of structural potted_* blooms add colour. flower_pot is recipe-
        //    derived (brick) → FU-valued; potted_* are itemless-structural → free.
        b.set(1, 3, 1, bs("minecraft:flower_pot"));            // on the left counter
        b.set(5, 3, 1, bs("minecraft:potted_poppy"));          // on the right counter (red bloom)
        b.set(2, 1, z1 - 1, bs("minecraft:flower_pot"));       // back shelf (over a barrel-free cell)
        b.set(cx, 1, z1 - 1, bs("minecraft:potted_oxeye_daisy")); // back-centre bloom
        b.set(x1 - 1, 2, z1 - 1, bs("minecraft:potted_blue_orchid")); // back-right bloom on a barrel
        b.set(cx - 1, 1, 2, bs("minecraft:potted_dandelion")); // interior floor accent
        b.set(cx + 1, 1, 2, bs("minecraft:potted_cornflower")); // interior floor accent

        // 10) LANTERNS — two chain-backed hanging lanterns from the slab roof, lighting
        //     the interior. Chains attach up to the y=wallH+1 slab roof (a solid face),
        //     so the lanterns hang correctly rather than floating.
        b.set(2, wallH, 4, CHAIN);  b.set(2, wallH - 1, 4, HANGING_LANTERN);
        b.set(4, wallH, 4, CHAIN);  b.set(4, wallH - 1, 4, HANGING_LANTERN);

        return b.build();
    }

    /**
     * hot_air_balloon — Category I, build 44/103. A tall decorative landmark: a
     * large rounded patterned-wool envelope tapering to a neck, with a small wooden
     * basket gondola slung below it on fence-and-chain rigging, lit by lanterns.
     *
     * <p>Footprint 9×9 (W×D), {@code builder(9, 15, 9)} — x=0..8 (W), z=0..8 (D),
     * centred on (cx,cz)=(4,4); a tall envelope peaks at y=14. T5 footprint band,
     * disc T1. The basket sits at the bottom, the envelope dominates the top, the
     * two coupled by suspension lines — so it reads unmistakably as a balloon.
     *
     * <p>Vanilla, FU-valued blocks only. The envelope uses three wool colours
     * (red / white / blue) painted in vertical gores by angular sector → the
     * classic striped-balloon pattern; dyed wool normalises to the base wool FU
     * value so every colour is wound/printable. The basket is oak planks + oak
     * fence rim, the rigging is oak fences and chains, lit by lanterns. No glass
     * panes / iron bars anywhere, so the render-integrity stub-pane gate never
     * applies. All wool/oak/fence/chain/lantern blocks are FU-valued.
     *
     * <p>Construction (bottom-up), honouring the air-skip / hollow rules:
     * <ul>
     *   <li><b>y=0..2 BASKET</b> — a 3×3 oak-plank gondola at x=3..5,z=3..5: a
     *       solid plank floor at y=0, plank side walls at y=1 (interior left open
     *       → it reads as a basket you could stand in), and an oak-fence rim
     *       around the top lip at y=2.</li>
     *   <li><b>y=3..6 RIGGING</b> — four suspension lines rising from the basket's
     *       top corners: oak fence for the lower run (y=3..5) then chain (y=6) up
     *       toward the envelope neck, so the basket visibly hangs from the balloon.</li>
     *   <li><b>y=7..14 ENVELOPE</b> — a hollow onion/teardrop wool shell: stacked
     *       rings tapering from a 1-radius neck (y=7) out to the full 4-radius
     *       bulge (y=10) and back in to a 1-wide crown (y=14), each ring's cells
     *       coloured by angular sector into red/white/blue vertical stripes. A
     *       solid wool disc caps the very top (y=14) and a wool disc closes the
     *       neck floor (y=7) so the envelope reads as a closed balloon, not a tube.</li>
     *   <li><b>lanterns</b> — a hanging lantern slung under the envelope neck on a
     *       chain (the burner glow), plus two lanterns on the basket rim corners.</li>
     * </ul>
     */
    private static Blueprint hotAirBalloon() {
        final int W = 9, H = 15, D = 9;
        Blueprint.Builder b = Blueprint.builder("Hot Air Balloon", W, H, D);

        final int cx = 4, cz = 4;

        // three balloon-stripe wool colours (all dyed wool → normalise to base FU)
        final BlueprintBlockState woolA = bs("minecraft:red_wool");
        final BlueprintBlockState woolB = bs("minecraft:white_wool");
        final BlueprintBlockState woolC = bs("minecraft:blue_wool");
        final BlueprintBlockState[] stripe = { woolA, woolB, woolC };

        // ── BASKET (gondola) — 3×3 oak-plank box at x=3..5, z=3..5 ─────────────
        final int bx0 = 3, bx1 = 5, bz0 = 3, bz1 = 5;
        // y=0: solid plank floor
        floor(b, 0, bx0, bz0, bx1, bz1, OAK_PLANKS);
        // y=1: plank side walls (interior left open → enterable basket)
        walls(b, bx0, bz0, bx1, bz1, 1, 1, OAK_PLANKS);
        // y=2: oak-fence rim around the top lip
        fenceRing(b, 2, bx0, bz0, bx1, bz1, OAK_FENCE);

        // ── RIGGING — suspension lines from the four basket top corners ───────
        // fence run y=3..5 then a chain link y=6, climbing toward the neck.
        final int[][] corners = {{bx0, bz0}, {bx1, bz0}, {bx0, bz1}, {bx1, bz1}};
        for (int[] c : corners) {
            pillar(b, c[0], c[1], 3, 5, OAK_FENCE);
            b.set(c[0], 6, c[1], CHAIN);
        }

        // ── ENVELOPE — hollow onion/teardrop wool shell, striped by sector ────
        // ring radius per envelope course y=7..14 (neck → bulge → crown).
        // index 0 → y=7 … index 7 → y=14.
        final int[] ringR = {1, 2, 3, 4, 4, 3, 2, 1};
        final int yEnv0 = 7;
        for (int i = 0; i < ringR.length; i++) {
            int y = yEnv0 + i;
            int r = ringR[i];
            paintRing(b, y, cx, cz, r, stripe);
        }
        // close the neck floor (y=7) and the crown (y=14) with solid wool discs so
        // the envelope reads as a sealed balloon rather than an open tube.
        paintDisc(b, yEnv0, cx, cz, ringR[0], stripe);
        paintDisc(b, yEnv0 + ringR.length - 1, cx, cz, ringR[ringR.length - 1], stripe);

        // ── LANTERNS — burner glow under the neck + basket-rim lights ─────────
        // a chain link just below the neck centre carrying a hanging lantern (the
        // balloon's burner), backed by the solid neck-floor disc above it.
        b.set(cx, 6, cz, CHAIN);
        b.set(cx, 5, cz, HANGING_LANTERN);
        // two non-hanging lanterns resting on opposite basket-rim corners.
        b.set(bx0, 3, bz0, LANTERN);
        b.set(bx1, 3, bz1, LANTERN);

        return b.build();
    }

    /**
     * §I — Dragon Statue. 7×11×11 (W×H×D) → builder(7,11,11). A crouching, coiled
     * dragon sculpture on a dark stone plinth: a coiled tail at the rear (low z),
     * a rising scaled body, spread wings suggested with stairs/slabs, and a raised
     * neck + horned head with an open jaw at the front (high z). A Hard organic
     * build — the goal is a readable blocky-dragon silhouette, not anatomical
     * perfection.
     *
     * <p>AXES: x=W (0..6, centred on x=3), z=depth (0..10), y=up (0..10). The dragon
     * faces the viewer at HIGH z; tail coils at LOW z. Everything sits on a 1-thick
     * plinth (y0), so the creature body lives at y1+.
     *
     * <p>PALETTE (all FU-valued or recipe-derived from valued leaves):
     * stone_bricks / deepslate_bricks / polished_blackstone for the plinth;
     * deepslate, blackstone, coal_block and dark_prismarine for the dark scaled
     * hide; stairs & slabs of those for shaping the tail, wings, snout and brow;
     * sea_lantern eyes; end_rod horns / dorsal spines; chain whiskers.
     *
     * <p>RENDER-SAFETY: NO iron_bars/glass panes are used anywhere — every thin
     * element (horns, dorsal spikes) is {@code end_rod}, which is not an
     * {@code IronBarsBlock} and renders as a clean vertical rod regardless of
     * neighbours, so the render-integrity guardrail has nothing to flag. Chains
     * are anchored to a solid block above so they read as hanging.
     */
    private static Blueprint dragonStatue() {
        final int W = 7, H = 11, D = 11;
        Blueprint.Builder b = Blueprint.builder("Dragon Statue", W, H, D);

        // ── PALETTE ───────────────────────────────────────────────────────────
        final BlueprintBlockState deepslateBricks   = bs("minecraft:deepslate_bricks");
        final BlueprintBlockState polishedBlackstone = bs("minecraft:polished_blackstone");
        final BlueprintBlockState blackstone        = bs("minecraft:blackstone");
        final BlueprintBlockState deepslate         = bs("minecraft:polished_deepslate");
        final BlueprintBlockState coalBlock         = bs("minecraft:coal_block");
        // dark scaled hide accent. dark_prismarine is gate-flagged (no FU value and
        // can't derive — its recipe needs black_dye, which is unvalued), so the
        // "scales" use coal_block instead: glossy-black, FU-valued (9× coal), and
        // tonally on-theme for a dark dragon hide.
        final BlueprintBlockState darkScale         = coalBlock;
        final BlueprintBlockState seaLantern        = bs("minecraft:sea_lantern");
        // stair states for shaping (facing = the LOW side the step faces toward)
        final BlueprintBlockState blackstoneStairN  = bs("minecraft:blackstone_stairs[facing=north,half=bottom,shape=straight]");
        final BlueprintBlockState blackstoneStairS  = bs("minecraft:blackstone_stairs[facing=south,half=bottom,shape=straight]");
        final BlueprintBlockState deepslateStairE   = bs("minecraft:polished_deepslate_stairs[facing=east,half=bottom,shape=straight]");
        final BlueprintBlockState deepslateStairW   = bs("minecraft:polished_deepslate_stairs[facing=west,half=bottom,shape=straight]");
        final BlueprintBlockState blackstoneSlabTop = bs("minecraft:blackstone_slab[type=top]");
        // wing-membrane trailing edge: blackstone slab (FU-valued via blackstone),
        // replacing the gate-flagged dark_prismarine_slab.
        final BlueprintBlockState wingSlabTop       = blackstoneSlabTop;

        final int cx = 3; // centre column (the spine runs up x=3)

        // ── PLINTH (y0) — 7×11 dark footing the whole creature rests on ───────
        floor(b, 0, 0, 0, W - 1, D - 1, deepslateBricks);
        // polished-blackstone rim course around the footing edge (dressed border)
        line(b, 0, 0, 0, W - 1, 0, polishedBlackstone);
        line(b, 0, 0, D - 1, W - 1, D - 1, polishedBlackstone);
        line(b, 0, 0, 0, 0, D - 1, polishedBlackstone);
        line(b, 0, W - 1, 0, W - 1, D - 1, polishedBlackstone);

        // ── COILED TAIL (z=0..2) — a low spiral curling in from the rear ──────
        // The tail enters at the rear-right, sweeps across, and thickens as it
        // approaches the body. Kept to y1..y2 so it reads as resting on the plinth.
        // tail tip — a single end_rod-free coal-block nub curling at the corner
        b.set(5, 1, 0, coalBlock);
        b.set(4, 1, 0, deepslate);
        b.set(4, 1, 1, deepslate);
        b.set(3, 1, 1, deepslate);
        b.set(2, 1, 1, deepslate);
        b.set(2, 1, 2, deepslate);
        b.set(3, 1, 2, blackstone);
        b.set(4, 1, 2, blackstone);
        // the coil rises a touch toward the body so the tail "lifts" into the hips
        b.set(3, 2, 2, deepslate);
        b.set(4, 2, 1, blackstoneStairN); // curl lip catching the light

        // ── HAUNCHES / HIPS (z=3..4) — the body's heavy rear, 3 wide, rising ──
        solid(b, 2, 1, 3, 4, 2, 4, deepslate);     // bulk hip mass x2..4, y1..2
        b.set(cx, 3, 3, blackstone);               // hip ridge rising onto the back
        b.set(cx, 3, 4, blackstone);
        // hind-leg hints: a darker coal-block foot pad either side at the front of
        // the haunch, with a polished-deepslate stair toe so the legs read planted.
        b.set(1, 1, 4, coalBlock);
        b.set(5, 1, 4, coalBlock);
        b.set(1, 1, 5, deepslateStairW); // left toe steps down off the pad
        b.set(5, 1, 5, deepslateStairE); // right toe

        // ── MAIN BODY / RIBCAGE (z=5..6) — the tall scaled barrel of the chest ─
        solid(b, 2, 1, 5, 4, 3, 6, blackstone);    // ribcage core, 3w × 3 tall
        // dark-prismarine scale band wrapping the flanks (the patterned hide)
        b.set(2, 2, 5, darkScale); // left flank scales
        b.set(2, 2, 6, darkScale);
        b.set(4, 2, 5, darkScale); // right flank scales
        b.set(4, 2, 6, darkScale);
        b.set(cx, 3, 5, coalBlock);                // glossy spine ridge
        b.set(cx, 3, 6, coalBlock);

        // ── SPREAD WINGS — stair "membranes" fanning off the shoulders (z=5..6)
        // Suggested, not solid: each wing is a short run of stairs stepping UP and
        // OUT from the ribcage so the silhouette reads as a half-folded wing.
        // LEFT wing (west, x decreasing) at the shoulder height y3.
        b.set(1, 3, 5, deepslateStairW);
        b.set(0, 3, 5, blackstoneStairN);
        b.set(1, 3, 6, wingSlabTop); // trailing membrane edge
        b.set(0, 4, 5, deepslate);       // raised wing tip / shoulder spar
        // RIGHT wing (east, x increasing), mirror.
        b.set(5, 3, 5, deepslateStairE);
        b.set(6, 3, 5, blackstoneStairN);
        b.set(5, 3, 6, wingSlabTop);
        b.set(6, 4, 5, deepslate);

        // dorsal SPINES along the back ridge — slender end_rods marching forward
        // from the hips, over the shoulders, toward the neck (render-safe rods).
        b.set(cx, 4, 4, END_ROD);
        b.set(cx, 4, 5, END_ROD);
        b.set(cx, 4, 6, END_ROD);

        // ── NECK (z=7..8) — rises and arches forward toward the raised head ───
        b.set(cx, 1, 7, blackstone);
        b.set(cx, 2, 7, blackstone);
        b.set(cx, 3, 7, deepslate);
        b.set(cx, 4, 7, deepslate);   // the neck climbs
        b.set(cx, 5, 7, coalBlock);
        b.set(cx, 5, 8, blackstone);  // arches forward over z
        b.set(cx, 6, 8, deepslate);
        // neck scale collar — dark-prismarine flaring at the base of the skull
        b.set(2, 5, 7, darkScale);
        b.set(4, 5, 7, darkScale);

        // ── HEAD (z=9..10, top of the build) — raised, with an open jaw ───────
        // skull block, 3 wide for a proper dragon brow, at y6..y7
        solid(b, 2, 6, 9, 4, 7, 9, blackstone);    // skull, x2..4, y6..7, z9
        b.set(cx, 7, 9, deepslate);                // domed crown
        // SNOUT thrusting forward (z=10): a blackstone stair muzzle so the head
        // reads as pointing/roaring rather than a flat cube.
        b.set(cx, 7, 10, blackstoneStairS);        // upper snout, stepping down/forward
        b.set(cx, 6, 10, blackstone);              // upper jaw mass
        // LOWER JAW — a slab dropped a level, set forward, so the mouth gapes open.
        b.set(cx, 5, 10, blackstoneSlabTop);       // lower jaw (gap above = open maw)
        // EYES — sea-lantern set into the brow either side of the snout (glowing).
        b.set(2, 7, 9, seaLantern);
        b.set(4, 7, 9, seaLantern);

        // HORNS — a pair of end_rods sweeping up-back off the crown (render-safe).
        b.set(2, 8, 9, END_ROD);
        b.set(4, 8, 9, END_ROD);
        b.set(2, 9, 9, END_ROD); // left horn tip, taller
        // a single central crest end_rod just behind the crown for a regal silhouette
        b.set(cx, 8, 9, END_ROD);

        // WHISKERS / chin barbels — short chains hanging off the lower jaw. The jaw
        // slab at (cx,5,10) is the solid anchor above each chain link so it hangs.
        b.set(cx, 4, 10, CHAIN);

        return b.build();
    }

    /**
     * §H.tavern_inn — Category H, build 57/103. A two-story timber-framed
     * tavern/inn: the recognizable medieval roadhouse with a ground-floor common
     * room (bar counter, ale barrels, brewing keg, a caged-lava fireplace with a
     * cobble chimney, and tables) and an upper floor of guest bedrooms, joined by
     * an interior stair. Vanilla blocks only, all FU-valued or structural matter.
     *
     * <p>Footprint 11×13×17 (W×L×H) → builder(11, 17, 13): x=0..10 (W=11, the gable
     * ridge axis), z=0..12 (depth=13). Two 4-high stories on a stone footing close
     * under a spruce gable roof peaking at y=16. Sits in the T6 band (disc T3).
     *
     * <p>Construction, honouring the air-skip / hollow-enterable rules:
     * <ul>
     *   <li><b>y=0</b> — solid stone-brick footing over the whole footprint; its top
     *       face is the walkable ground floor.</li>
     *   <li><b>Ground story, y=1..5</b> — a {@link #timberFrame} wall ring (spruce
     *       planks + dark-oak log studs/rails) with dark-oak corner posts. A
     *       double-door entry on the north (z=0) wall opens inward; render-safe glass
     *       windows sit between studs on the long and back walls. Inside: a spruce-slab
     *       bar counter with barrels behind it, a brewing keg (cauldron + barrel), a
     *       caged-lava fireplace (lava boxed by iron bars / cobble with a chimney that
     *       rises through the roof), and two stair-seat tables.</li>
     *   <li><b>Floor break, y=6</b> — a spruce-plank ceiling/upper floor over the
     *       whole footprint EXCEPT a 1-cell stair hatch, so the two floors connect.
     *       An oak stair run climbs the south-west corner from y=1 to the hatch.</li>
     *   <li><b>Upper story, y=7..10</b> — a second {@link #timberFrame} ring with its
     *       own windows; the interior is split by a spruce-plank partition into two
     *       guest bedrooms, each with a {@link #bed}, a lantern, and a window.</li>
     *   <li><b>Roof, y=10..16</b> — a spruce {@link #gableRoofX} with
     *       {@link #gableEndFill} closed gable ends; the cobble chimney pokes through
     *       the north slope. Hanging tavern sign + chain lanterns dress the front
     *       eave.</li>
     * </ul>
     *
     * <p>Every glass pane is flanked along its wall run by solid wall cells (or other
     * panes chaining back to a corner post), so each has a horizontal connection and
     * is render-safe (no stub panes). The fireplace lava is fully boxed so it can't
     * flow at print time.
     */
    private static Blueprint tavernInn() {
        Blueprint.Builder b = Blueprint.builder("Tavern Inn", 11, 17, 13);
        int x0 = 0, x1 = 10;          // 11-wide (gable ridge along X)
        int z0 = 0, z1 = 12;          // 13-deep
        int cx = (x0 + x1) / 2;       // 5
        int cz = (z0 + z1) / 2;       // 6

        // timber-frame palette: spruce-plank infill + dark-oak log frame (the
        // classic tavern look), dark-oak corner posts, dark-oak doors.
        BlueprintBlockState planks   = SPRUCE_PLANKS;
        BlueprintBlockState studY    = bs("minecraft:dark_oak_log[axis=y]");
        BlueprintBlockState studX    = bs("minecraft:dark_oak_log[axis=x]");
        BlueprintBlockState postY    = bs("minecraft:dark_oak_log[axis=y]");
        BlueprintBlockState floorMat = SPRUCE_PLANKS;
        BlueprintBlockState footing  = STONE_BRICKS;
        BlueprintBlockState cobble   = COBBLE;
        BlueprintBlockState pane     = GLASS_PANE;

        int g0 = 1, g1 = 5;           // ground story walls y=1..5
        int upFloorY = 6;             // upper floor / ground ceiling at y=6
        int u0 = 7, u1 = 10;          // upper story walls y=7..10 (plate at y=10)

        // ── 1) FOOTING (y=0) — walkable stone-brick ground floor ─────────────
        floor(b, 0, x0, z0, x1, z1, footing);

        // ── 2) GROUND STORY (y=1..5) — timber-frame ring + dark-oak corners ──
        timberFrame(b, x0, z0, x1, z1, g0, g1, planks, studY, studX);
        corners(b, x0, z0, x1, z1, g0, g1, postY);

        // double-door entry centred on the north wall (z=0), opening inward (south).
        // door2 leaves both door cells; the flanking cells stay solid wall.
        door2(b, cx - 1, g0, z0, "dark_oak", "N");
        door2(b, cx + 1, g0, z0, "dark_oak", "N");
        // the cell between the two doors (cx) keeps its wall block (a mullion post);
        // overwrite it with a dark-oak post so the twin doors read as one wide entry.
        pillar(b, cx, z0, g0, g0 + 1, postY);

        // ground-floor windows at a mid-wall course y=3. Each pane is flanked along
        // its wall run by solid wall/stud cells → render-safe.
        int gwy = 3;
        // north wall (z0): a pane either side of the door cluster (x=2 and x=8),
        // each flanked by the corner post / a stud and by wall cells.
        b.set(x0 + 2, gwy, z0, pane);
        b.set(x1 - 2, gwy, z0, pane);
        // long walls (west x0, east x1): windows in the bays at odd z (clear of the
        // studs that sit at even z), flanked above/below and left/right by wall.
        for (int z = z0 + 3; z <= z1 - 3; z += 2) {
            b.set(x0, gwy, z, pane); // west long wall
            b.set(x1, gwy, z, pane); // east long wall
        }
        // south (back) wall (z1): two panes flanking centre.
        b.set(cx - 2, gwy, z1, pane);
        b.set(cx + 2, gwy, z1, pane);

        // ── 3) GROUND-FLOOR FURNISHINGS (on the y=1 walkable floor) ──────────
        // Bar counter: a spruce-slab-topped run along the back-east, with barrels
        // behind it as the cellar stock. Counter at z = z1-2, x = cx..x1-1.
        for (int x = cx; x <= x1 - 1; x++) {
            b.set(x, g0, z1 - 2, SPRUCE_SLAB_TOP); // counter top (slab → bar height)
            b.set(x, g0, z1 - 1, BARREL);          // ale barrels behind the bar
        }
        // brewing keg: a cauldron + a stacked barrel beside the bar (the "keg").
        b.set(cx - 1, g0, z1 - 1, CAULDRON);
        b.set(cx - 2, g0, z1 - 1, BARREL);

        // Fireplace on the WEST wall (x0), centred in z: a caged-lava hearth boxed
        // by cobble so the lava can't flow, with a chimney rising through the roof.
        int fz = cz;
        // hearth surround: cobble box one cell in from the wall around the lava cell
        b.set(x0 + 1, g0, fz - 1, cobble);
        b.set(x0 + 1, g0, fz + 1, cobble);
        b.set(x0 + 1, g0 + 1, fz - 1, cobble);
        b.set(x0 + 1, g0 + 1, fz + 1, cobble);
        b.set(x0, g0, fz, cobble);           // back of the hearth (replaces wall plank)
        b.set(x0, g0 + 1, fz, cobble);
        // lava firebox at the hearth mouth, boxed: iron-bar grate fronts it and a
        // cobble cap closes the top so the lava is fully enclosed and can't spread.
        b.set(x0 + 1, g0, fz, LAVA);
        b.set(x0 + 1, g0 + 2, fz, cobble);   // cap over the lava
        // cobble jambs flanking the grate (z=fz±1 at the mouth column x0+2) give the
        // iron bars a sturdy full face on both sides → it renders (no stub) and the
        // firebox front reads as a hearth grate set in masonry.
        b.set(x0 + 2, g0, fz - 1, cobble);
        b.set(x0 + 2, g0, fz + 1, cobble);
        b.set(x0 + 2, g0, fz, IRON_BARS);    // grate (now flanked N/S by cobble → connects)
        // chimney: a cobble flue rising from the hearth up through the roof line.
        pillar(b, x0 + 1, fz, g0 + 3, 15, cobble);

        // Two tables: a stripped-spruce post under a spruce top slab, flanked by
        // stair "stools" — placed in the open east common-room area.
        int[] tableZ = { z0 + 3, z0 + 6 };
        for (int tz : tableZ) {
            b.set(x1 - 2, g0, tz, STRIPPED_SPRUCE_Y);    // table leg/pedestal
            b.set(x1 - 2, g0 + 1, tz, SPRUCE_SLAB_TOP);  // table top
            // stair stools facing the table
            b.set(x1 - 3, g0, tz, bs("minecraft:spruce_stairs[facing=east,half=bottom,shape=straight]"));
            b.set(x1 - 1, g0, tz, bs("minecraft:spruce_stairs[facing=west,half=bottom,shape=straight]"));
        }
        // a couple of floor lanterns light the common room
        b.set(x0 + 2, g0, z0 + 2, LANTERN);
        b.set(x1 - 1, g0, z1 - 2, LANTERN);

        // ── 4) FLOOR BREAK (y=6) — upper floor with a stair hatch ────────────
        // interior STAIR run climbing the south-west corner (column x=x0+1) from the
        // ground floor up to the upper deck: one spruce stair per z-row, facing north
        // (so you climb toward -z), rising y=1..5 across z = z1 .. z1-4. The top step
        // is at (x0+1, 5, z1-4) → you emerge standing at deck height over z1-5.
        int stairTopZ = z1 - 4;   // 8
        for (int i = 0; i <= 4; i++) {
            int sz = z1 - i;
            int sy = g0 + i;
            b.set(x0 + 1, sy, sz, bs("minecraft:spruce_stairs[facing=north,half=bottom,shape=straight]"));
        }
        // spruce-plank deck over the whole footprint; the hatch (column x=x0+1 at the
        // top of the stairs, z = stairTopZ and the cell one north of it) is left UNSET
        // so you can walk up off the stair onto the upper floor.
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (x == x0 + 1 && (z == stairTopZ || z == stairTopZ - 1)) continue; // stair hatch
                b.set(x, upFloorY, z, floorMat);
            }
        }

        // ── 5) UPPER STORY (y=7..10) — timber-frame ring + guest bedrooms ────
        timberFrame(b, x0, z0, x1, z1, u0, u1, planks, studY, studX);
        corners(b, x0, z0, x1, z1, u0, u1, postY);
        // chimney continues through the upper ring (already a cobble pillar to y=15,
        // but ensure the upper-wall cell it passes stays cobble, not plank).
        b.set(x0 + 1, u0, cz, cobble); b.set(x0 + 1, u0 + 1, cz, cobble);
        b.set(x0 + 1, u1, cz, cobble);

        // upper-floor windows at y=9 (one below the plate), render-safe (flanked).
        int uwy = 9;
        // north & south walls: a pane either side of centre
        b.set(cx - 2, uwy, z0, pane);
        b.set(cx + 2, uwy, z0, pane);
        b.set(cx - 2, uwy, z1, pane);
        b.set(cx + 2, uwy, z1, pane);
        // long walls: windows in odd-z bays (clear of even-z studs)
        for (int z = z0 + 3; z <= z1 - 3; z += 2) {
            b.set(x0, uwy, z, pane);
            b.set(x1, uwy, z, pane);
        }

        // interior partition splitting the upper floor into two guest bedrooms,
        // running along X at z=cz (skip the chimney cell so it isn't doubled, and
        // leave a 1-cell doorway gap at x=cx so both rooms are reachable from the
        // stair landing). The partition is a plank wall y=7..9 (head clearance under
        // the gable above y=10).
        for (int x = x0 + 1; x <= x1 - 1; x++) {
            if (x == cx) continue;            // doorway gap between the rooms
            if (x == x0 + 1) continue;        // leave the chimney/landing column clear
            for (int y = u0; y <= u1 - 1; y++) {
                b.set(x, y, cz, planks);
            }
        }
        // guest bedrooms: a bed + a floor lantern in each room (on the y=7 floor).
        // North room (z0 side): bed head at z0+1 facing north (foot at z0+2).
        bed(b, x1 - 1, u0, z0 + 1, "red", "north");
        b.set(x0 + 2, u0, z0 + 1, LANTERN);
        // South room (z1 side): bed head at z1-1 facing south (foot at z1-2).
        bed(b, x1 - 1, u0, z1 - 1, "red", "south");
        b.set(x1 - 1, u0, cz + 1, LANTERN);

        // ── 6) ROOF (y=10..16) — spruce gable, closed ends, chimney pokes out ─
        gableRoofX(b, x0, z0, x1, z1, u1, "spruce_stairs", SPRUCE_SLAB_BOTTOM);
        gableEndFill(b, x0, z0, x1, z1, u1, planks);
        // the chimney already rises to y=15 (above the north-slope roof line at the
        // hearth's z), so it pokes through the roof; cap it with a top slab cowl.
        b.set(x0 + 1, 16, cz, SPRUCE_SLAB_TOP);

        // ── 7) HANGING TAVERN SIGN over the entry foyer ──────────────────────
        // A hanging oak sign welcoming guests, hung from the underside of the upper
        // floor just inside the entry (no exterior cell exists at z=-1, and punching
        // the wall would leave a hole). The upper-floor deck at (x1-3, upFloorY,
        // z0+1) is the solid anchor; a hanging sign drops one cell below it at the
        // common-room head height, clear of the doors at cx and the windows.
        b.set(x1 - 3, upFloorY - 1, z0 + 1,
              bs("minecraft:oak_hanging_sign[rotation=8,attached=true,waterlogged=false]"));

        return b.build();
    }

    /**
     * apothecary_shop — Category H, build 58/103. A cozy timber-framed herbalist /
     * apothecary shop: a glass shopfront onto the street, a brewing nook against the
     * back wall (brewing stands over a counter, water cauldrons for filling bottles),
     * shelves of potions (bookshelves + barrels) down the side walls, a sales counter
     * by the door, "drying herbs" hung from a ceiling beam (potted_* blooms — itemless
     * structural, so they print free), warm lantern light, and a gabled spruce roof.
     *
     * <p>Footprint 7×9 (W×L) → {@code builder(7, H, 9)} — x=0..6 (W=7, east is +x),
     * z=0..8 (L=9 depth, south is +z). Disc T3, T5 footprint band. The FRONT of the
     * shop faces NORTH (z=0): door + glass shopfront + awning. The brewing nook is at
     * the BACK (south, z=8). Walls rise y=1..3 on a stone-brick plinth (y=0); the
     * gable roof runs along X and peaks above that.
     *
     * <p>Vanilla, FU-valued / structural blocks only:
     * <ul>
     *   <li><b>Frame:</b> stone_bricks plinth, spruce timber-frame walls (planks +
     *       stripped-spruce studs via {@link #timberFrame}), dark_oak corner posts.</li>
     *   <li><b>Shopfront:</b> full {@code glass} blocks (NOT panes) — glass is FU-valued
     *       and is a normal cube, so it is exempt from the stub-pane render gate. Where
     *       a glass_pane IS used (the side/back windows) it is embedded in a plank run
     *       so it always has a sturdy neighbour → render-safe.</li>
     *   <li><b>Brewing nook:</b> brewing_stand (recipe-derived), water_cauldron
     *       (cauldron derived; the water inside is itemless-structural → free),
     *       smooth_stone counter.</li>
     *   <li><b>Stock:</b> bookshelf (40@3), barrel (derives from planks+slabs).</li>
     *   <li><b>Drying herbs:</b> potted_* blocks ({@code asItem()==AIR} → itemless
     *       structural, print free) hung under a stripped-spruce ceiling beam. Loose
     *       flowers/leaves are UNVALUED so they are deliberately NOT used — only the
     *       potted variants, which read as bunches of herbs strung up to dry.</li>
     *   <li><b>flower_pot</b> (empty, brick-derived → FU-valued) on the counter as an
     *       herbalist's prop.</li>
     *   <li><b>Light:</b> lantern + chain-backed hanging lanterns ({@link #chainLantern}).</li>
     *   <li><b>Roof:</b> spruce_stairs gable ({@link #gableRoofX}) with closed gable
     *       ends ({@link #gableEndFill}).</li>
     * </ul>
     */
    private static Blueprint apothecaryShop() {
        final int W = 7, D = 9;                 // 7 wide (x), 9 deep (z)
        int x0 = 0, x1 = W - 1;                 // x = 0..6
        int z0 = 0, z1 = D - 1;                 // z = 0..8  (z0=front/north, z1=back/south)
        int wallH = 3;                          // walls y=1..3
        int cx = (x0 + x1) / 2;                 // centre column (x=3)
        int yBase = wallH + 1;                  // roof first course (y=4)
        int peakY = gablePeakY(z0, z1, yBase);  // size the build to the roof peak
        final int H = peakY + 1;
        Blueprint.Builder b = Blueprint.builder("Apothecary Shop", W, H, D);

        BlueprintBlockState darkOakLogY = bs("minecraft:dark_oak_log[axis=y]");
        BlueprintBlockState smoothStone = bs("minecraft:smooth_stone");
        BlueprintBlockState brewingStand =
                bs("minecraft:brewing_stand[has_bottle_0=false,has_bottle_1=false,has_bottle_2=false]");
        BlueprintBlockState waterCauldron = bs("minecraft:water_cauldron[level=3]");
        BlueprintBlockState beam = STRIPPED_SPRUCE_Y; // ceiling beam for hanging herbs

        // ── 1) PLINTH + FLOOR — stone-brick footing at y=0 (walkable surface = top of
        //    y=0). Interior left open above per the air-skip rule → enterable.
        floor(b, 0, x0, z0, x1, z1, STONE_BRICKS);
        // A spruce-plank interior finish floor reads as a shop floor over the plinth's
        // top is unnecessary (y=0 is the surface); a threshold strip just inside the
        // door at z=1 gives a doormat feel.
        line(b, 0, cx - 1, 1, cx + 1, 1, SPRUCE_PLANKS);

        // ── 2) FRAME — dark-oak corner posts y=1..3 + spruce timber-frame on the three
        //    SOLID walls (south/back z=8, west x=0, east x=6). The FRONT (north, z=0)
        //    wall is left open here and becomes the glass shopfront in step 3.
        corners(b, x0, z0, x1, z1, 1, wallH, darkOakLogY);
        //    South (back) wall — full timber frame.
        timberFrame(b, x0, z1, x1, z1, 1, wallH, SPRUCE_PLANKS, STRIPPED_SPRUCE_Y, STRIPPED_OAK_X);
        //    West & east side walls — plank rings with stripped-spruce studs every 2.
        for (int y = 1; y <= wallH; y++) {
            line(b, y, x0, z0, x0, z1, SPRUCE_PLANKS); // west wall
            line(b, y, x1, z0, x1, z1, SPRUCE_PLANKS); // east wall
        }
        for (int z = z0; z <= z1; z += 2) {
            pillar(b, x0, z, 1, wallH, STRIPPED_SPRUCE_Y);
            pillar(b, x1, z, 1, wallH, STRIPPED_SPRUCE_Y);
        }

        // ── 3) GLASS SHOPFRONT — the north wall (z=0). Dark-oak mullions at the door
        //    jambs (x=2,x=4) plus the corner posts (x=0,x=6) divide the front into
        //    glass bays. Uses FULL glass blocks (not panes), which are normal cubes →
        //    exempt from the stub-pane render gate, and FU-valued.
        pillar(b, 2, z0, 1, wallH, darkOakLogY); // left door jamb / mullion
        pillar(b, 4, z0, 1, wallH, darkOakLogY); // right door jamb / mullion
        for (int y = 1; y <= wallH; y++) {
            b.set(1, y, z0, GLASS);              // left glass bay
            b.set(5, y, z0, GLASS);              // right glass bay
        }
        b.set(cx, wallH, z0, GLASS);             // clerestory glass over the door
        //    Door dead-centre, opening inward (north wall → faces south).
        door2(b, cx, 1, z0, "spruce", "N");

        // ── 4) SIDE & BACK WINDOWS — a glass pane mid-wall on each solid wall, embedded
        //    in the plank run so its horizontal neighbours are sturdy plank faces →
        //    render-safe (no stub panes).
        window2(b, x0, 2, 2, GLASS_PANE, null);  // west wall window
        window2(b, x1, 2, 2, GLASS_PANE, null);  // east wall window

        // ── 5) GABLE ROOF — spruce-stairs gable running along X, closed gable ends,
        //    a spruce-slab ridge. Sits on the wall plate at y=yBase.
        gableRoofX(b, x0, z0, x1, z1, yBase, "spruce_stairs", SPRUCE_SLAB_BOTTOM);
        gableEndFill(b, x0, z0, x1, z1, yBase, SPRUCE_PLANKS);

        // ── 6) BREWING NOOK — back (south) wall, z1-1=7. A smooth-stone counter at y=1
        //    spanning x=2..4 with three brewing stands on top (y=2): the classic
        //    three-station brewing row. Two water cauldrons flank it at floor level for
        //    filling bottles (the water inside is itemless-structural → prints free).
        line(b, 1, 2, z1 - 1, 4, z1 - 1, smoothStone);
        b.set(2, 2, z1 - 1, brewingStand);
        b.set(3, 2, z1 - 1, brewingStand);
        b.set(4, 2, z1 - 1, brewingStand);
        b.set(1, 1, z1 - 1, waterCauldron);      // SW filling cauldron
        b.set(5, 1, z1 - 1, waterCauldron);      // SE filling cauldron

        // ── 7) POTION SHELVES — bookshelves + barrels of potions down the side walls.
        //    Bookshelves at y=2 against the west/east walls toward the back; barrels at
        //    floor level for reagent storage. Kept off the door approach (z=1 clear).
        b.set(1, 2, z1 - 2, BOOKSHELF);
        b.set(5, 2, z1 - 2, BOOKSHELF);
        b.set(1, 1, z1 - 2, BARREL);
        b.set(5, 1, z1 - 2, BARREL);
        b.set(1, 1, 3, BARREL);                  // mid-shop west barrel
        b.set(5, 1, 3, BARREL);                  // mid-shop east barrel
        b.set(1, 2, 3, bs("minecraft:potted_azure_bluet")); // a bloom atop the west barrel

        // ── 8) SALES COUNTER — an L of spruce slabs on a support course just inside the
        //    shopfront, with a flower_pot prop and a chest. Slabs/chest derive →
        //    FU-valued; no IronBars so no stub-pane risk. Leaves the door path clear.
        b.set(1, 1, 2, SPRUCE_PLANKS); b.set(1, 2, 2, SPRUCE_SLAB_TOP);  // counter end
        b.set(2, 1, 2, SPRUCE_PLANKS); b.set(2, 2, 2, SPRUCE_SLAB_TOP);  // counter run
        b.set(2, 3, 2, bs("minecraft:flower_pot"));                      // herbalist's pot on the counter
        b.set(5, 1, 2, CHEST);                                           // sales chest by the east bay

        // ── 9) DRYING HERBS — a stripped-spruce ceiling beam across the shop at the
        //    wall-top course (y=wallH=3) with "bunches" of drying herbs (potted_*
        //    blooms) hung beneath it. potted_* are itemless-structural → print free,
        //    and read as bundles of herbs strung up to dry. (Loose flowers/leaves are
        //    UNVALUED → deliberately not used.)
        line(b, wallH, x0, 4, x1, 4, beam);      // beam across the mid-shop (y=3, z=4)
        b.set(1, wallH - 1, 4, bs("minecraft:potted_fern"));
        b.set(2, wallH - 1, 4, bs("minecraft:potted_allium"));
        b.set(4, wallH - 1, 4, bs("minecraft:potted_blue_orchid"));
        b.set(5, wallH - 1, 4, bs("minecraft:potted_cornflower"));

        // ── 10) LIGHTING — a hanging lantern slung under the centre of the ceiling beam
        //    (the beam at (cx,3,4) is its solid support face), plus a standing lantern on
        //    the brewing counter so the nook is lit.
        b.set(cx, wallH - 1, 4, HANGING_LANTERN); // lantern hangs at y=2 under the beam
        b.set(3, 3, z1 - 1, LANTERN);             // standing lantern on the brewing counter

        return b.build();
    }

    /**
     * A perimeter wool ring (like {@link #circleRing}) whose cells are coloured by
     * angular sector into vertical stripes — used for the hot-air-balloon envelope.
     * Cell colour = {@code palette[sector]} where {@code sector} is the angle from
     * the ring centre split into {@code palette.length} gores, so the stripes line
     * up vertically across stacked rings into balloon gores.
     */
    private static void paintRing(Blueprint.Builder b, int y, int cx, int cz, int r,
                                  BlueprintBlockState[] palette) {
        if (r <= 0) {
            b.set(cx, y, cz, palette[0]);
            return;
        }
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                double d = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
                if (d <= r + 0.5 && d > r - 0.5) {
                    b.set(x, y, z, palette[sectorIndex(x - cx, z - cz, palette.length)]);
                }
            }
        }
    }

    /** Filled-disc variant of {@link #paintRing} (closes the envelope neck/crown). */
    private static void paintDisc(Blueprint.Builder b, int y, int cx, int cz, int r,
                                  BlueprintBlockState[] palette) {
        if (r <= 0) {
            b.set(cx, y, cz, palette[0]);
            return;
        }
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                double d = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
                if (d <= r + 0.5) {
                    b.set(x, y, z, palette[sectorIndex(x - cx, z - cz, palette.length)]);
                }
            }
        }
    }

    /** Maps an offset (dx,dz) from a ring centre to one of {@code n} angular gores. */
    private static int sectorIndex(int dx, int dz, int n) {
        double angle = Math.atan2(dz, dx) + Math.PI; // 0 .. 2π
        int idx = (int) (angle / (2 * Math.PI) * n);
        if (idx >= n) idx = n - 1;
        if (idx < 0) idx = 0;
        return idx;
    }

    /**
     * §H.stable_horse — an OPEN-BAY HORSE STABLE, 9×11×10 (W×L×H) → builder(9, 10, 11).
     * Disc T2. PMC "stable" tag archetype: a timber-and-stone barn whose long west &
     * east walls are lined with fence-gated stalls flanking a central dirt aisle, a
     * hay loft over the back half, a tack-room nook (barrels + wall signs), a gabled
     * roof, and lanterns. The player walks in the open south bay and adds horses —
     * none are captured.
     *
     * <p>Every block is a vanilla FU-valued or structural-free block (spruce
     * logs/planks/slabs/stairs, oak fences + fence gates, hay, barrels, cauldrons,
     * lanterns, ladders, signs, dirt-path; water is structural). Glass panes sit only
     * in the solid timber-frame walls (sturdy faces on both sides) so the build never
     * ships a stub pane.
     *
     * <p>The full 11-deep gable peaks at y=9, so H must be 10 (y range 0..9). The loft
     * floor (y=4) and its hay sit strictly inboard of the roof slopes — at y=4 the
     * roof occupies only z=0/z=10, and the loft is inset to z=1..5, so they never
     * collide.
     *
     * <p>Layout (south = +z is the OPEN front; aisle runs north–south at x=4):
     * <ul>
     *   <li><b>y=0</b> — spruce-plank floor over the full 9×11 footprint; the central
     *       aisle (x=4, z=1..9) is dirt-path so the stalls read as flanking bays.</li>
     *   <li><b>y=1..3</b> — timber-frame north wall (z=0) and both long side walls
     *       (x=0,8); the south face (z=10) is LEFT OPEN as the drive-in bay (only the
     *       two south corner posts frame it). Glazed windows high in the side &amp;
     *       back walls. The interior is left unwritten above the floor → enterable.</li>
     *   <li><b>Stalls</b> — three west (x=1..3) and three east (x=5..7) bays, divided
     *       by oak-fence partitions across x at z=3/6/9 and a fence rail down the
     *       aisle edge (x=3 west, x=5 east), each stall opened to the aisle by an oak
     *       fence gate. Hay-bale bedding + a cauldron water trough per side.</li>
     *   <li><b>Hay loft, y=4</b> — a spruce-slab floor over the back half (inset
     *       z=1..5, x=1..7) carrying stacked hay bales; the front half stays open to
     *       the roof so a mounted player can ride in.</li>
     *   <li><b>Tack room, NW</b> — barrels stacked in the corner with oak wall signs
     *       (the tack board) on the west wall.</li>
     *   <li><b>Roof</b> — a spruce gable running along X from y=4 (peak y=9), gable
     *       ends filled; lanterns on the partition fence posts light the bays.</li>
     * </ul>
     */
    private static Blueprint stableHorse() {
        Blueprint.Builder b = Blueprint.builder("Horse Stable", 9, 10, 11);
        int x0 = 0, x1 = 8, z0 = 0, z1 = 10;

        // ── 1) FLOOR (y=0) — spruce planks, dirt-path central aisle ──────────
        floor(b, 0, x0, z0, x1, z1, SPRUCE_PLANKS);
        for (int z = 1; z <= 9; z++) {
            b.set(4, 0, z, DIRT_PATH);            // the walk-in aisle
        }

        // ── 2) TIMBER-FRAME SHELL (y=1..3) — open south bay ──────────────────
        // North wall (z=0) and both long side walls (x=0, x=8). The south face
        // (z=10) is deliberately NOT walled: it's the open drive-in bay. We frame
        // it with the two south corner posts only.
        for (int y = 1; y <= 3; y++) {
            line(b, y, x0, z0, x1, z0, SPRUCE_PLANKS);   // north wall
            line(b, y, x0, z0, x0, 9, SPRUCE_PLANKS);    // west wall (z=0..9)
            line(b, y, x1, z0, x1, 9, SPRUCE_PLANKS);    // east wall (z=0..9)
        }
        // stripped-spruce corner posts (incl. the two south posts framing the bay)
        corners(b, x0, z0, x1, z1, 1, 3, STRIPPED_SPRUCE_Y);
        // vertical studs down the long walls every 2 cells (timber read)
        for (int z = z0; z <= 9; z += 2) {
            pillar(b, x0, z, 1, 3, STRIPPED_SPRUCE_Y);
            pillar(b, x1, z, 1, 3, STRIPPED_SPRUCE_Y);
        }
        // top plate rail (stripped logs) along the three closed walls at y=3
        line(b, 3, x0, z0, x1, z0, bs("minecraft:stripped_spruce_log[axis=x]")); // north plate
        line(b, 3, x0, z0, x0, 9, bs("minecraft:stripped_spruce_log[axis=z]")); // west plate
        line(b, 3, x1, z0, x1, 9, bs("minecraft:stripped_spruce_log[axis=z]")); // east plate

        // glazed windows up high on the side walls (embedded in solid plank wall →
        // sturdy faces both sides, never a stub pane) + a back-wall pair
        window2(b, x0, 2, 3, GLASS_PANE, null);
        window2(b, x0, 2, 7, GLASS_PANE, null);
        window2(b, x1, 2, 3, GLASS_PANE, null);
        window2(b, x1, 2, 7, GLASS_PANE, null);
        window2(b, 3, 2, z0, GLASS_PANE, null);
        window2(b, 5, 2, z0, GLASS_PANE, null);

        // ── 3) STALLS — three per side flanking the x=4 aisle ────────────────
        // Partition fences run across x at the stall divisions z=3, z=6, z=9; the
        // aisle-edge rail (x=3 west, x=5 east) closes each stall except for one
        // fence-gate opening into the aisle. Connecting fences self-reconcile.
        for (int zDiv : new int[]{3, 6, 9}) {
            line(b, 1, 1, zDiv, 3, zDiv, OAK_FENCE);   // west divider
            line(b, 1, 5, zDiv, 7, zDiv, OAK_FENCE);   // east divider
        }
        // aisle-edge rails (x=3 west, x=5 east) along z=1..9
        line(b, 1, 3, 1, 3, 9, OAK_FENCE);
        line(b, 1, 5, 1, 5, 9, OAK_FENCE);
        // a fence gate per stall opening east/west onto the aisle (overwrites the
        // rail cell). Gate centres at z=2, z=5, z=8.
        for (int zg : new int[]{2, 5, 8}) {
            b.set(3, 1, zg, bs("minecraft:oak_fence_gate[facing=east,open=false,in_wall=false,powered=false]"));
            b.set(5, 1, zg, bs("minecraft:oak_fence_gate[facing=west,open=false,in_wall=false,powered=false]"));
        }
        // stall bedding: a hay bale in the back & front stall of each side (the
        // middle stall keeps a water trough instead). West x=1, east x=7.
        for (int zb : new int[]{5, 8}) {
            b.set(1, 1, zb, HAY);
            b.set(7, 1, zb, HAY);
        }
        // water troughs in the middle stall of each side: an empty cauldron
        // (FU-valued; the player fills it) over a sunken water cell at y=0
        // (water is structural → prints free).
        b.set(1, 1, 4, CAULDRON);   // west trough
        b.set(7, 1, 4, CAULDRON);   // east trough
        b.set(1, 0, 5, WATER);
        b.set(7, 0, 5, WATER);

        // ── 4) HAY LOFT (y=4) — spruce-slab floor over the back half ─────────
        // Inset to x=1..7, z=1..5 so it clears the roof slopes (at y=4 the roof
        // touches only z=0 and z=10). The front half (z=6..10) is open to the roof
        // so a mounted player can ride straight in.
        floor(b, 4, 1, 1, 7, 5, SPRUCE_SLAB_TOP);
        // stacked hay bales on the loft (the feed store); at y=5 the roof slopes
        // sit at z=1/z=9, so keep loft hay at z=2..4.
        b.set(2, 5, 2, HAY);
        b.set(2, 5, 3, HAY);
        b.set(6, 5, 2, HAY);
        b.set(6, 5, 3, HAY);
        b.set(4, 5, 3, HAY);
        // ladder up to the loft on the back wall, enterable from the aisle
        for (int y = 1; y <= 3; y++) {
            b.set(2, y, 1, bs("minecraft:ladder[facing=south,waterlogged=false]"));
        }

        // ── 5) TACK ROOM NOOK (NW corner) — barrels + wall signs ─────────────
        b.set(1, 1, 1, BARREL);
        b.set(1, 2, 1, BARREL);
        // tack board: oak wall signs mounted on the WEST wall (x=0) facing east into
        // the nook — facing=east attaches to the block to its west, so the x=0 plank
        // wall supports them. (Mounting on z=0 would have no block behind the sign.)
        b.set(1, 2, 2, bs("minecraft:oak_wall_sign[facing=east]"));
        b.set(1, 3, 2, bs("minecraft:oak_wall_sign[facing=east]"));

        // ── 6) GABLE ROOF (y=4) running along X, ends filled ─────────────────
        gableRoofX(b, x0, z0, x1, z1, 4, "spruce_stairs", SPRUCE_SLAB_BOTTOM);
        gableEndFill(b, x0, z0, x1, z1, 4, SPRUCE_PLANKS);

        // ── 7) LIGHTING — lanterns on the partition fence posts + tack barrel ──
        // Lanterns place on the top face of the y=1 partition fences (at y=2), one
        // per side per stall row, lighting the bays so no hostiles spawn.
        for (int zl : new int[]{3, 6}) {
            b.set(1, 2, zl, LANTERN);
            b.set(7, 2, zl, LANTERN);
        }
        b.set(1, 3, 1, LANTERN);   // atop the tack-room barrel stack

        return b.build();
    }

    /**
     * Greenhouse — a spruce-framed glass house for growing crops. A 9×9 footprint
     * (T5), disc T1. A stone-brick footing carries a spruce post-and-sill frame
     * whose wall bays are filled with GLASS BLOCKS (not panes) and capped by a
     * peaked GLASS GABLE roof, so the whole structure reads as a light-filled glass
     * envelope. Inside: two raised farmland planter beds growing a mix of crops
     * (wheat / carrots / potatoes / beetroots — all structural), a sunken water
     * irrigation channel down the centre aisle (structural), spruce-slab shelves
     * carrying flower_pots (FU-valued) and structural potted_* blooms, an oak door,
     * and hanging lanterns. Enterable down the central aisle.
     *
     * <p><b>Render-safe glazing.</b> Every glazed surface — the four wall bays AND
     * the gable roof — uses solid {@code glass} BLOCKS, never lone {@code glass_pane}
     * cells, so the stub-pane render gate never applies (a glass block is not an
     * {@code IronBarsBlock}). The frame's spruce posts and sills break the glass into
     * window-like bays for the greenhouse look without risking an invisible stub.
     *
     * <p><b>Printability.</b> All blocks are vanilla FU-valued or structural:
     * stone bricks / spruce frame / glass / oak door / flower_pot / lantern are
     * FU-valued; farmland (FarmBlock), the crops (CropBlock→BushBlock), water
     * (itemless), and potted_* (itemless) are structural → print free.
     *
     * <p>Footprint x=0..8 (W=9), z=0..8 (depth=9), y=0..7 (H=8):
     * <ul>
     *   <li>{@code y=0} stone-brick footing over the full 9×9; the central aisle
     *       column (x=4) is dug as a water irrigation channel.</li>
     *   <li>{@code y=1..4} spruce post-and-sill frame with GLASS-block wall bays;
     *       an oak door centred on the north wall; raised farmland planter beds with
     *       crops to either side of the aisle; spruce-slab shelves with flower-pots
     *       and potted blooms.</li>
     *   <li>{@code y=4..} a peaked GLASS gable roof (stepped glass blocks running
     *       along X) on a spruce ridge beam; gable ends glazed too.</li>
     * </ul>
     */
    private static Blueprint greenhouse() {
        final int W = 9, H = 9, D = 9;
        Blueprint.Builder b = Blueprint.builder("Greenhouse", W, H, D);
        int x0 = 0, x1 = W - 1, z0 = 0, z1 = D - 1; // 0..8
        int cx = 4, cz = 4;
        int wallH = 4; // glass wall bays rise y=1..4

        BlueprintBlockState postY = SPRUCE_LOG_Y;
        BlueprintBlockState sill = SPRUCE_SLAB_TOP; // a finished sill/plate course look
        BlueprintBlockState bedEdge = SPRUCE_SLAB_TOP;

        // 1) STONE-BRICK FOOTING over the whole footprint (walkable y=0 surface).
        floor(b, 0, x0, z0, x1, z1, STONE_BRICKS);

        // 2) WATER IRRIGATION CHANNEL down the central aisle (x=cx, z=1..7), sunk
        //    into the footing. Water is itemless → structural (prints free). The
        //    aisle stays walkable along its planted edges; the channel is the
        //    greenhouse's irrigation feature running between the two planter beds.
        for (int z = 1; z <= z1 - 1; z++) {
            b.set(cx, 0, z, WATER);
        }

        // 3) PLANTER BEDS — two raised farmland strips flanking the aisle, each
        //    growing a rotating mix of crops. Farmland (FarmBlock) and the crops
        //    (CropBlock) are structural → free. Beds sit at y=1 (one course up from
        //    the footing) edged with spruce slabs so they read as raised planters.
        BlueprintBlockState[] crops = {WHEAT, CARROTS, POTATOES, bs("minecraft:beetroots[age=3]")};
        // west bed: x=1..2, east bed: x=6..7; both z=1..7, fed by the central channel.
        int[][] beds = {{1, 2}, {6, 7}};
        int cropIdx = 0;
        for (int[] bed : beds) {
            for (int x = bed[0]; x <= bed[1]; x++) {
                for (int z = 1; z <= z1 - 1; z++) {
                    b.set(x, 1, z, FARMLAND);            // moist farmland (structural)
                    b.set(x, 2, z, crops[cropIdx % crops.length]); // crop atop (structural)
                    cropIdx++;
                }
            }
        }
        // spruce-slab planter coping along the aisle edges of each bed (x=2 east face,
        // x=6 west face) at y=1, so the beds read as built planters, not loose dirt.
        for (int z = 1; z <= z1 - 1; z++) {
            b.set(3, 1, z, bedEdge); // coping between west bed and aisle
            b.set(5, 1, z, bedEdge); // coping between east bed and aisle
        }

        // 4) SPRUCE FRAME — corner posts + a sill/plate course, leaving the wall
        //    bays for glass. Posts rise y=1..wallH at the four corners and at the
        //    wall midpoints (x=cx on z-walls, z=cz on x-walls) to break the glass
        //    into bays.
        int[][] frameCols = {
                {x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}, // corners
                {cx, z0}, {cx, z1}, {x0, cz}, {x1, cz}  // wall midpoints (mullions)
        };

        // 5) GLASS WALL BAYS — fill the four wall faces with GLASS BLOCKS y=1..wallH
        //    (render-safe: solid blocks, no panes). The door opening on the north
        //    wall (z=z0) is left UNSET so it stays open for the door.
        for (int y = 1; y <= wallH; y++) {
            for (int x = x0; x <= x1; x++) {
                // north wall, skip the door cell (cx) which the door fills below
                if (!(x == cx)) b.set(x, y, z0, GLASS);
                b.set(x, y, z1, GLASS); // south wall
            }
            for (int z = z0 + 1; z <= z1 - 1; z++) {
                b.set(x0, y, z, GLASS); // west wall
                b.set(x1, y, z, GLASS); // east wall
            }
        }
        // overlay the spruce mullions/posts so the frame reads through the glass.
        for (int[] q : frameCols) {
            pillar(b, q[0], q[1], 1, wallH, postY);
        }
        // a spruce sill course at y=1 around the base of the glass (skip door cell),
        // and a spruce plate course at y=wallH around the top — the frame banding.
        for (int x = x0; x <= x1; x++) {
            if (x != cx) b.set(x, 1, z0, sill);
            b.set(x, 1, z1, sill);
            b.set(x, wallH, z0, SPRUCE_SLAB_TOP);
            b.set(x, wallH, z1, SPRUCE_SLAB_TOP);
        }
        for (int z = z0 + 1; z <= z1 - 1; z++) {
            b.set(x0, 1, z, sill);
            b.set(x1, 1, z, sill);
            b.set(x0, wallH, z, SPRUCE_SLAB_TOP);
            b.set(x1, wallH, z, SPRUCE_SLAB_TOP);
        }
        // re-stamp the corner/mullion posts so the slab courses didn't clip them.
        for (int[] q : frameCols) {
            b.set(q[0], 1, q[1], postY);
            b.set(q[0], wallH, q[1], postY);
        }

        // 6) OAK DOOR centred on the north wall (z=z0), opening inward (faces south).
        //    The glass bay at (cx, *, z0) was left open above for it.
        door2(b, cx, 1, z0, "oak", "N");

        // 7) GLASS GABLE ROOF running along X (ridge parallel to X), built from solid
        //    GLASS BLOCKS in a stepped gable so the whole roof glazes (render-safe).
        //    Same step schedule as gableRoofX: north slope advances from z0, south
        //    slope from z1, converging to a ridge; a spruce ridge beam caps it.
        int ry = wallH;     // first roof course seats on the wall plate
        int zn = z0, zs = z1;
        while (zs - zn > 1) {
            for (int x = x0; x <= x1; x++) {
                b.set(x, ry, zn, GLASS);
                b.set(x, ry, zs, GLASS);
            }
            zn++;
            zs--;
            ry++;
        }
        // ridge: a spruce-log beam (axis=x) so the apex reads as a built ridge, not
        // floating glass; one centre row (zn==zs) or a 2-wide cap (zn+1==zs).
        BlueprintBlockState ridgeBeam = bs("minecraft:spruce_log[axis=x]");
        for (int x = x0; x <= x1; x++) {
            b.set(x, ry, zn, ridgeBeam);
            if (zs != zn) b.set(x, ry, zs, ridgeBeam);
        }
        // glaze the two triangular gable ends (x=x0 and x=x1) so the attic is closed
        // with glass, not open sky. Walk the same step schedule and fill between the
        // two converging slope rows.
        for (int x : new int[]{x0, x1}) {
            int gy = wallH;
            int gzn = z0, gzs = z1;
            while (gzs - gzn > 1) {
                for (int z = gzn + 1; z <= gzs - 1; z++) {
                    b.set(x, gy, z, GLASS);
                }
                gzn++;
                gzs--;
                gy++;
            }
        }

        // 8) SHELVES + POTTED DISPLAYS — spruce-slab shelves along the back (south)
        //    gable end carrying flower_pots (FU-valued, brick-derived) and structural
        //    potted_* blooms (itemless → free). Mounted at y=2 over the back planter
        //    coping so they read as a potting shelf without blocking the aisle.
        b.set(1, 2, z1 - 1, bs("minecraft:flower_pot"));
        b.set(2, 2, z1 - 1, bs("minecraft:potted_fern"));
        b.set(6, 2, z1 - 1, bs("minecraft:potted_oxeye_daisy"));
        b.set(7, 2, z1 - 1, bs("minecraft:flower_pot"));
        // a couple of potted blooms flanking the door inside, on the sill course.
        b.set(cx - 1, 2, z0 + 1, bs("minecraft:potted_dandelion"));
        b.set(cx + 1, 2, z0 + 1, bs("minecraft:potted_poppy"));

        // 9) LIGHTING — hanging lanterns on chains dropped from the spruce ridge beam
        //    down the central aisle (z=cz), lighting it so crops grow and no hostiles
        //    spawn. The ridge sits at y=ry (==H-1) over z=cz; hang a chain just under it
        //    with a lantern below. ry is the apex course after the roof loop.
        for (int x : new int[]{2, cx, 6}) {
            chainLantern(b, x, ry - 2, cz, 1); // lantern at ry-2, chain at ry-1, backed by the ridge at ry
        }
        // a pair of standing lanterns on the corner-post sills for ground glow.
        b.set(x0 + 1, 1, z0 + 1, LANTERN);
        b.set(x1 - 1, 1, z0 + 1, LANTERN);

        return b.build();
    }

    /**
     * Modern minimalist concrete house — Phase 2 Category B (§3.B, footprint
     * 11×9 → T6, disc T1). The "WiederDude" archetype: a clean flat-roofed
     * concrete box with full-height glazing, a parapet roof, dark-oak + quartz
     * trim accents, an open-plan interior, and a small upper roof terrace.
     *
     * <p><b>Axis mapping.</b> Spec footprint is W×L = 11×9, so the builder is
     * {@code builder(name, W=11, H=9, L=9)}: x=width 0..10, z=depth 0..8,
     * y=up 0..8.
     *
     * <p><b>Render-safe glazing.</b> The full-height window walls are GLASS
     * BLOCKS (not panes), broken into bays by concrete/quartz mullions — so they
     * never render as stub panes and the render-integrity gate passes trivially
     * (glass blocks aren't {@code IronBarsBlock}).
     *
     * <p><b>Palette (all vanilla, FU-valued or structural).</b>
     * <ul>
     *   <li>{@code light_gray_concrete} — foundation slab + roof deck;</li>
     *   <li>{@code white_concrete} — primary wall mass + parapet;</li>
     *   <li>{@code gray_concrete} — corner piers / mullions (the dark frame);</li>
     *   <li>{@code glass} — full-height window bays + a clerestory band;</li>
     *   <li>{@code dark_oak_planks}/{@code dark_oak_slab}/{@code dark_oak_log} —
     *       trim band, entry surround, terrace decking;</li>
     *   <li>{@code smooth_quartz}/{@code smooth_quartz_slab} — entry step +
     *       parapet coping accent;</li>
     *   <li>{@code sea_lantern} — recessed interior lighting;</li>
     *   <li>furniture: {@code dark_oak_door}, {@code white_bed}, crafting table,
     *       chest, bookshelf, {@code potted_*} greenery (structural).</li>
     * </ul>
     *
     * <p>The interior above the floor is deliberately left unwritten (air-skip)
     * so the player can walk in through the entry and stand inside — ENTERABLE.
     */
    private static Blueprint modernConcreteHouse() {
        final int W = 11, H = 9, D = 9;
        Blueprint.Builder b = Blueprint.builder("Modern Concrete House", W, H, D);
        int x0 = 0, x1 = W - 1, z0 = 0, z1 = D - 1; // x:0..10  z:0..8
        int cx = (x0 + x1) / 2; // 5
        int cz = (z0 + z1) / 2; // 4
        int wallH = 4;          // ground-floor walls rise y=1..4 (full-height glass bays)
        int deckY = wallH + 1;  // flat roof deck at y=5
        int parapetY = deckY + 1; // parapet ring at y=6 (1 course)

        // Palette ---------------------------------------------------------------
        BlueprintBlockState foundation = bs("minecraft:light_gray_concrete");
        BlueprintBlockState wallMass   = bs("minecraft:white_concrete");
        BlueprintBlockState frame      = bs("minecraft:gray_concrete"); // dark piers/mullions
        BlueprintBlockState deck       = bs("minecraft:light_gray_concrete");
        BlueprintBlockState parapet    = bs("minecraft:white_concrete");
        BlueprintBlockState trim       = DARK_OAK_PLANKS;
        BlueprintBlockState trimSlabBot = bs("minecraft:dark_oak_slab[type=bottom]");
        BlueprintBlockState quartzSlabTop = bs("minecraft:smooth_quartz_slab[type=top]");

        // Build order is strictly bottom-up and last-write-wins (Builder.set
        // overwrites the cell), so we lay the GLASS curtain first, then over-stamp
        // the solid mass, the frame grid, and the trim. No cell reads are needed.

        // 1) FOUNDATION SLAB — light-gray concrete over the whole footprint, the
        //    walkable y=0 surface (its top face is the interior floor).
        floor(b, 0, x0, z0, x1, z1, foundation);

        // 2) FULL-HEIGHT GLAZING — GLASS BLOCKS fill the entire wall ring y=1..wallH
        //    first; the solid mass + frame below overwrite the cells that aren't
        //    glass. Render-safe: solid blocks (not panes), so no stub-pane risk.
        for (int y = 1; y <= wallH; y++) {
            for (int x = x0; x <= x1; x++) {
                b.set(x, y, z0, GLASS); // north (front)
                b.set(x, y, z1, GLASS); // south (back)
            }
            for (int z = z0 + 1; z <= z1 - 1; z++) {
                b.set(x0, y, z, GLASS); // west
                b.set(x1, y, z, GLASS); // east
            }
        }

        // 3) SOLID CONCRETE WALL MASS — over-stamp the glass with white concrete
        //    where we want solid wall: a base plinth course (y=1) all around to
        //    ground the glazing, plus a full-height feature wall plug on the
        //    rear-west (SW) bay (the open-plan kitchen/service wall).
        // base spandrel: y=1 ring (the door cell on the north wall is re-glazed
        // / doored below, so writing it here is harmless and keeps the plinth even).
        for (int x = x0; x <= x1; x++) {
            b.set(x, 1, z0, wallMass); // north (front)
            b.set(x, 1, z1, wallMass); // south (back)
        }
        for (int z = z0 + 1; z <= z1 - 1; z++) {
            b.set(x0, 1, z, wallMass); // west
            b.set(x1, 1, z, wallMass); // east
        }
        // rear-west solid wall plug (the feature wall): x=x0..x0+3 on the back wall
        // (z=z1), full height, solid white concrete.
        for (int y = 1; y <= wallH; y++) {
            for (int x = x0; x <= x0 + 3; x++) {
                b.set(x, y, z1, wallMass);
            }
            b.set(x0, y, z1 - 1, wallMass); // short return on the west wall
        }

        // 4) CORNER PIERS + WALL MULLIONS — gray-concrete frame columns over-stamped
        //    on top of the glass/mass, rising the full wall height. The frame breaks
        //    the glazing into clean bays so it reads as a curtain wall, not one
        //    undivided sheet. Mullions sit at regular x/z intervals on each face.
        int[][] piers = {
                {x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}, // four corners
                {x0 + 3, z0}, {x0 + 7, z0},             // north-wall mullions (flank the door)
                {x0 + 3, z1}, {x0 + 7, z1},             // south-wall mullions
                {x0, cz}, {x1, cz}                      // east/west mid mullions
        };
        for (int[] p : piers) {
            pillar(b, p[0], p[1], 1, wallH, frame);
        }

        // 5) DARK-OAK TRIM BAND — a crisp horizontal accent line at the top of the
        //    wall (y=wallH) wrapping the whole box, the modern "fascia" reveal. It
        //    overlays the frame/glass top course; the gray piers still poke through
        //    at their cells (re-stamped below) for the grid look.
        for (int x = x0; x <= x1; x++) {
            b.set(x, wallH, z0, trim);
            b.set(x, wallH, z1, trim);
        }
        for (int z = z0 + 1; z <= z1 - 1; z++) {
            b.set(x0, wallH, z, trim);
            b.set(x1, wallH, z, trim);
        }
        // re-stamp the corner piers above the trim so the grid corners stay gray.
        for (int[] p : new int[][]{{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
            b.set(p[0], wallH, p[1], frame);
        }

        // 6) FLAT ROOF DECK — light-gray concrete slab over the full footprint at
        //    y=deckY. This is both the ceiling of the open-plan room and the floor
        //    of the upper terrace.
        floor(b, deckY, x0, z0, x1, z1, deck);

        // 7) PARAPET + UPPER TERRACE — a 1-course white-concrete parapet rings the
        //    roof edge at y=parapetY, with a smooth-quartz coping accent on the
        //    front (north) face for the clean modern cap. The terrace itself is the
        //    open deck inside the parapet; a dark-oak-slab seating ledge and a
        //    couple of potted plants make it a usable roof terrace.
        for (int x = x0; x <= x1; x++) {
            b.set(x, parapetY, z0, parapet);
            b.set(x, parapetY, z1, parapet);
        }
        for (int z = z0 + 1; z <= z1 - 1; z++) {
            b.set(x0, parapetY, z, parapet);
            b.set(x1, parapetY, z, parapet);
        }
        // quartz coping accent: cap the front (north) parapet run with smooth-quartz
        // top slabs sitting on the parapet, the bright reveal against white.
        for (int x = x0 + 1; x <= x1 - 1; x++) {
            b.set(x, parapetY + 1, z0, quartzSlabTop);
        }
        // a low dark-oak terrace bench/ledge along the back parapet (interior side)
        // and two potted plants — props that make the terrace read as a space.
        for (int x = x0 + 2; x <= x1 - 2; x++) {
            b.set(x, deckY + 1, z1 - 1, trimSlabBot); // bench seat just inside back parapet
        }
        b.set(x0 + 2, deckY + 1, z0 + 1, bs("minecraft:potted_bamboo"));
        b.set(x1 - 2, deckY + 1, z0 + 1, bs("minecraft:potted_fern"));

        // 8) ENTRY — a dark-oak door centred on the north (front) wall, opening
        //    inward (faces south). A smooth-quartz entry step sits just outside,
        //    and a dark-oak surround frames the opening (the gray mullions at x±2
        //    already flank it). The glass bay at (cx, *, z0) was left open above.
        door2(b, cx, 1, z0, "dark_oak", "N");
        b.set(cx, wallH, z0, trim); // dark-oak lintel over the door (overrides glass cell)
        // dark-oak reveal jambs hugging the door (overlay the base spandrel sides)
        b.set(cx - 1, 1, z0, trim);
        b.set(cx + 1, 1, z0, trim);

        // 9) CLERESTORY / TRANSOM over the door — a single glass cell above the door
        //    head keeps the entry bay glazed to the trim line (render-safe: it sits
        //    directly below the dark-oak lintel and beside frame mullions → no stub).
        //    (door occupies y=1,2; head trim at y=wallH; glaze the gap y=3..wallH-1)
        for (int y = 3; y <= wallH - 1; y++) {
            b.set(cx, y, z0, GLASS);
        }

        // 10) INTERIOR — open-plan furnishings on the y=1 floor. A modern living
        //     space: a low bed in the rear corner, a desk (crafting table) + chest,
        //     a bookshelf feature wall against the solid SW plug, and a sea-lantern
        //     recessed in the ceiling for clean light. Kept sparse for crisp lines.
        bed(b, x0 + 2, 1, z1 - 1, "white", "south");      // bed head near back wall
        b.set(x0 + 1, 1, z1 - 1, BOOKSHELF);              // bookshelf against SW feature wall
        b.set(x1 - 1, 1, z1 - 1, CRAFTING_TABLE);         // desk in the back-east corner
        b.set(x1 - 1, 1, z1 - 2, CHEST);                  // storage beside it
        b.set(x0 + 2, 1, z0 + 1, bs("minecraft:potted_oak_sapling")); // greenery by the entry
        // recessed ceiling lights: sea lanterns set into the roof-deck course (y=deckY)
        // down the centre line so they read as flush downlights over the open plan.
        b.set(x0 + 3, deckY, cz, SEA_LANTERN);
        b.set(cx, deckY, cz, SEA_LANTERN);
        b.set(x1 - 3, deckY, cz, SEA_LANTERN);

        return b.build();
    }

    /**
     * §B.66 Modern Pool Deck — an open-air leisure deck: a sunken rectangular pool
     * (water, structural) lined with prismarine + quartz, ringed by a quartz /
     * smooth-stone deck, with stair-and-slab loungers, a render-safe GLASS-block
     * railing, a small smooth-quartz bar/cabana corner, sea-lantern uplights set
     * into the pool floor, and potted greenery. 9×9 footprint (T5), disc T1.
     *
     * <p><b>Axis mapping.</b> Footprint W×L = 9×9 → builder {@code (W=9, H=4, L=9)}:
     * x = width 0..8, z = depth 0..8, y = up 0..3.
     *
     * <p><b>Vertical scheme (bottom-up, last-write-wins, air-skip).</b>
     * <ul>
     *   <li>{@code y=0} — ground course. The whole footprint is a solid base:
     *       smooth-stone under the perimeter deck and a prismarine-lined POOL FLOOR
     *       under the sunken 5×5 center. Sea-lantern uplights are set flush into the
     *       pool floor so they glow up through the water.</li>
     *   <li>{@code y=1} — surface level. Perimeter = quartz-block / smooth-quartz-slab
     *       decking (the walkable top). Center = WATER (the pool surface, flush with
     *       the deck → a true sunken pool). The water sits one course ABOVE its
     *       prismarine floor, exactly a one-deep sunken basin.</li>
     *   <li>{@code y=1..2} — props on the deck: stair+slab loungers, a GLASS-block
     *       perimeter railing (render-safe — solid blocks, never panes), a quartz
     *       bar/cabana in the NE corner with a slab counter + cabana posts, and
     *       potted plants. A {@code y=3} cabana slab roof caps the corner.</li>
     * </ul>
     *
     * <p><b>Render-safe railing.</b> The railing is GLASS BLOCKS (not panes), set
     * as a low (1-course) parapet around the deck edge with gaps left for access,
     * so the render-integrity gate passes trivially (glass blocks aren't
     * {@code IronBarsBlock}).
     *
     * <p><b>Palette (all vanilla, FU-valued or structural).</b> quartz_block /
     * smooth_quartz / quartz_stairs / quartz_slab (derive from quartz=5@3),
     * smooth_stone (3@1), prismarine + prismarine_bricks (derive
     * from prismarine_shard=8@4 / crystals=12@4), glass (5@1), sea_lantern (50@5),
     * water (structural), potted_* greenery.
     */
    private static Blueprint modernPoolDeck() {
        final int W = 9, H = 4, D = 9;
        Blueprint.Builder b = Blueprint.builder("Modern Pool Deck", W, H, D);
        int x0 = 0, x1 = W - 1, z0 = 0, z1 = D - 1; // x:0..8  z:0..8

        // sunken pool footprint: a 5×5 basin centered in the 9×9 deck (inset 2 cells
        // on every side → pool x∈[2..6], z∈[2..6]). The 2-cell-wide deck rings it.
        int px0 = 2, px1 = 6, pz0 = 2, pz1 = 6;

        // Palette ---------------------------------------------------------------
        BlueprintBlockState deckBase   = bs("minecraft:smooth_stone");            // y=0 base under the deck
        BlueprintBlockState deckTop    = bs("minecraft:quartz_block");            // y=1 walkable deck surface
        BlueprintBlockState deckTrim   = bs("minecraft:smooth_quartz_slab[type=top]"); // slab coping accent
        BlueprintBlockState poolFloor  = bs("minecraft:prismarine");              // pool basin floor
        BlueprintBlockState poolLining = bs("minecraft:prismarine_bricks");       // pool wall lining (basin sides)
        BlueprintBlockState railGlass  = GLASS;                                   // render-safe railing (BLOCKS)
        BlueprintBlockState barTop     = bs("minecraft:smooth_quartz");           // cabana / bar mass
        BlueprintBlockState barCounter = bs("minecraft:quartz_slab[type=top]");   // bar counter slab
        BlueprintBlockState cabanaRoof = bs("minecraft:smooth_quartz_slab[type=bottom]"); // cabana roof slab

        // 1) GROUND COURSE (y=0) — solid base over the whole footprint: smooth-stone
        //    under the perimeter deck, prismarine POOL FLOOR under the sunken center.
        floor(b, 0, x0, z0, x1, z1, deckBase);                 // full base
        floor(b, 0, px0, pz0, px1, pz1, poolFloor);            // over-stamp the pool floor
        // sea-lantern uplights set flush into the pool floor (glow up through water):
        // four set in from the basin corners + one in the dead center.
        b.set(px0 + 1, 0, pz0 + 1, SEA_LANTERN);
        b.set(px1 - 1, 0, pz0 + 1, SEA_LANTERN);
        b.set(px0 + 1, 0, pz1 - 1, SEA_LANTERN);
        b.set(px1 - 1, 0, pz1 - 1, SEA_LANTERN);
        b.set((px0 + px1) / 2, 0, (pz0 + pz1) / 2, SEA_LANTERN);

        // 2) SURFACE COURSE (y=1) — perimeter quartz deck (the walkable top), and
        //    WATER filling the pool center so its surface is flush with the deck.
        floor(b, 1, x0, z0, x1, z1, deckTop);                  // quartz deck over everything…
        floor(b, 1, px0, pz0, px1, pz1, WATER);                // …then water over the pool cells
        // prismarine-brick basin lining: re-stamp the pool's outer ring of floor cells
        // as the basin-wall course at y=0 so the sunken edge reads as a lined pool wall.
        line(b, 0, px0, pz0, px1, pz0, poolLining); // basin north wall
        line(b, 0, px0, pz1, px1, pz1, poolLining); // basin south wall
        line(b, 0, px0, pz0, px0, pz1, poolLining); // basin west wall
        line(b, 0, px1, pz0, px1, pz1, poolLining); // basin east wall

        // 3) DECK COPING (y=1) — a smooth-quartz top-slab trim ring lining the inner
        //    pool edge (between deck and water), the bright modern reveal around the
        //    waterline. (Top slabs sit at the deck surface; you can still walk it.)
        for (int x = px0 - 1; x <= px1 + 1; x++) {
            b.set(x, 1, pz0 - 1, deckTrim);
            b.set(x, 1, pz1 + 1, deckTrim);
        }
        for (int z = pz0 - 1; z <= pz1 + 1; z++) {
            b.set(px0 - 1, 1, z, deckTrim);
            b.set(px1 + 1, 1, z, deckTrim);
        }

        // 4) GLASS RAILING (y=2) — a low 1-course GLASS-BLOCK parapet around the deck
        //    edge, with a gap left on the south (front) face for access. Solid glass
        //    blocks → render-safe (no stub panes). Sits on the deck top at y=2.
        for (int x = x0; x <= x1; x++) {
            b.set(x, 2, z0, railGlass);                        // north railing (full)
            if (x < 3 || x > 5) b.set(x, 2, z1, railGlass);    // south railing (center gap = entry)
        }
        for (int z = z0 + 1; z <= z1 - 1; z++) {
            b.set(x0, 2, z, railGlass);                        // west railing
            b.set(x1, 2, z, railGlass);                        // east railing
        }

        // 5) LOUNGERS — two stair+slab sun chairs on the west deck strip facing the
        //    pool (east). A chair = a stairs (seat-back, facing away from sitter) with
        //    a top-slab footrest in front. They sit on the 2-wide deck at x=0..1, z.
        BlueprintBlockState chairBack = bs("minecraft:quartz_stairs[facing=east,half=bottom,shape=straight]");
        for (int cz : new int[]{pz0, pz1}) {                   // one aligned to each pool end
            b.set(x0, 2, cz, chairBack);                       // seat-back stair against west rail
            b.set(x0 + 1, 2, cz, bs("minecraft:quartz_slab[type=bottom]")); // footrest slab
        }
        // re-assert the west railing cells the chairs overwrote at z=pz0,pz1 one cell
        // higher so the rail stays continuous behind the loungers.
        b.set(x0, 3, pz0, railGlass);
        b.set(x0, 3, pz1, railGlass);

        // 6) BAR / CABANA — a small smooth-quartz bar in the NE corner: a 2×1 counter
        //    mass with a top-slab counter, two cabana corner posts, and a slab roof.
        b.set(x1 - 1, 2, z0 + 1, barTop);                      // bar mass (back)
        b.set(x1, 2, z0 + 1, barTop);
        b.set(x1 - 1, 2, z0 + 2, barCounter);                  // counter lip (front)
        b.set(x1, 2, z0 + 2, barCounter);
        // cabana posts rise from the bar to a slab roof
        pillar(b, x1 - 1, z0 + 1, 3, 3, barTop);
        pillar(b, x1, z0 + 1, 3, 3, barTop);
        b.set(x1 - 1, 3, z0 + 2, cabanaRoof);                  // roof slab over the counter
        b.set(x1, 3, z0 + 2, cabanaRoof);

        // 7) GREENERY — potted plants on the deck corners (structural-safe variants
        //    already proven in the curated set). One by the entry gap, two on the
        //    front deck strip flanking the pool.
        b.set(x0 + 1, 2, z1, bs("minecraft:potted_bamboo"));
        b.set(x1 - 1, 2, z1, bs("minecraft:potted_fern"));
        b.set(x0 + 1, 2, z0 + 1, bs("minecraft:potted_cactus"));

        return b.build();
    }
}
