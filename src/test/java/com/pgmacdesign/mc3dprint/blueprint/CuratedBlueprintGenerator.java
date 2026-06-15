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
}
