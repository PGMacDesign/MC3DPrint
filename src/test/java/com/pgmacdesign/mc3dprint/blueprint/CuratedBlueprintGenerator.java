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
        for (int y = 1; y <= 4; y++) b.set(10, y, 0, IRON_BARS); // portcullis bar centre
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
}
