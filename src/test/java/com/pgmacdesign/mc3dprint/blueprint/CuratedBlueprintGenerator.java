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
 * Generates the bundled "curated blueprint" set in vanilla-village style.
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
 * <p>Conventions used by every build below:
 * <ul>
 *   <li>Axes: {@code x} = width (east), {@code y} = up, {@code z} = depth (south).
 *       Stair {@code facing} points the way the full-height back of the stair
 *       faces; a roof slope rising toward +z uses {@code facing=south}.</li>
 *   <li>Footprints are kept &le; 9&times;9 and heights &le; 8 so a T5&ndash;T7
 *       printer can complete them.</li>
 *   <li>Palette is common vanilla blocks only (planks/logs, cobblestone, glass
 *       panes, stairs, slabs, doors, fences, torches, crops, water, lanterns).</li>
 * </ul>
 */
class CuratedBlueprintGenerator {

    /** Repo-relative output dir; Gradle runs tests with cwd = project root. */
    private static final Path OUTPUT_DIR =
            Path.of("src", "main", "resources", "data", "mc3dprint", "blueprints");

    // ---- common palette (parsed once; states are immutable & reusable) ----
    private static final BlueprintBlockState OAK_PLANKS = bs("minecraft:oak_planks");
    private static final BlueprintBlockState SPRUCE_PLANKS = bs("minecraft:spruce_planks");
    private static final BlueprintBlockState OAK_LOG_Y = bs("minecraft:oak_log[axis=y]");
    private static final BlueprintBlockState SPRUCE_LOG_Y = bs("minecraft:spruce_log[axis=y]");
    private static final BlueprintBlockState STRIPPED_OAK_Y = bs("minecraft:stripped_oak_log[axis=y]");
    private static final BlueprintBlockState COBBLE = bs("minecraft:cobblestone");
    private static final BlueprintBlockState MOSSY_COBBLE = bs("minecraft:mossy_cobblestone");
    private static final BlueprintBlockState STONE_BRICKS = bs("minecraft:stone_bricks");
    private static final BlueprintBlockState GLASS_PANE = bs("minecraft:glass_pane");
    private static final BlueprintBlockState GLASS = bs("minecraft:glass");
    private static final BlueprintBlockState OAK_FENCE = bs("minecraft:oak_fence");
    private static final BlueprintBlockState DIRT_PATH = bs("minecraft:dirt_path");
    private static final BlueprintBlockState GRASS_BLOCK = bs("minecraft:grass_block[snowy=false]");
    private static final BlueprintBlockState FARMLAND = bs("minecraft:farmland[moisture=7]");
    private static final BlueprintBlockState WATER = bs("minecraft:water[level=0]");
    private static final BlueprintBlockState WHEAT = bs("minecraft:wheat[age=7]");
    private static final BlueprintBlockState HAY = bs("minecraft:hay_block[axis=y]");
    private static final BlueprintBlockState TORCH = bs("minecraft:torch");
    private static final BlueprintBlockState LANTERN = bs("minecraft:lantern[hanging=false]");
    private static final BlueprintBlockState HANGING_LANTERN = bs("minecraft:lantern[hanging=true]");
    private static final BlueprintBlockState BELL_FLOOR = bs("minecraft:bell[attachment=floor,facing=north]");
    private static final BlueprintBlockState CAULDRON_WATER = bs("minecraft:water_cauldron[level=3]");
    private static final BlueprintBlockState COBBLE_WALL = bs("minecraft:cobblestone_wall");
    private static final BlueprintBlockState OAK_SLAB_BOTTOM = bs("minecraft:oak_slab[type=bottom]");
    private static final BlueprintBlockState OAK_SLAB_TOP = bs("minecraft:oak_slab[type=top]");
    private static final BlueprintBlockState SPRUCE_SLAB_BOTTOM = bs("minecraft:spruce_slab[type=bottom]");
    private static final BlueprintBlockState COBBLE_SLAB_TOP = bs("minecraft:cobblestone_slab[type=top]");
    private static final BlueprintBlockState SMOOTH_STONE_SLAB_TOP = bs("minecraft:smooth_stone_slab[type=top]");
    private static final BlueprintBlockState ANVIL = bs("minecraft:anvil[facing=north]");
    private static final BlueprintBlockState FURNACE = bs("minecraft:furnace[facing=south,lit=false]");
    private static final BlueprintBlockState SMOKER = bs("minecraft:smoker[facing=south,lit=false]");
    private static final BlueprintBlockState BARREL = bs("minecraft:barrel[facing=up,open=false]");
    private static final BlueprintBlockState COMPOSTER = bs("minecraft:composter[level=0]");
    private static final BlueprintBlockState CRAFTING_TABLE = bs("minecraft:crafting_table");
    private static final BlueprintBlockState BOOKSHELF = bs("minecraft:bookshelf");
    private static final BlueprintBlockState WHITE_WOOL = bs("minecraft:white_wool");

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

        // name -> builder. LinkedHashMap keeps a stable, readable order.
        Map<String, Blueprint> builds = new LinkedHashMap<>();
        builds.put("small_cottage", smallCottage());
        builds.put("plains_house", plainsHouse());
        builds.put("log_cabin", logCabin());
        builds.put("two_story_house", twoStoryHouse());
        builds.put("cobble_house", cobbleHouse());
        builds.put("village_well", villageWell());
        builds.put("lamp_post", lampPost());
        builds.put("watchtower_tall", watchtowerTall());
        builds.put("wooden_bridge", woodenBridge());
        builds.put("small_farm", smallFarm());
        builds.put("gazebo", gazebo());
        builds.put("market_stall", marketStall());
        builds.put("fishing_dock", fishingDock());
        builds.put("wall_gate_segment", wallGateSegment());
        builds.put("shrine", shrine());
        builds.put("windmill_base", windmillBase());
        builds.put("bakery", bakery());
        builds.put("blacksmith_hut", blacksmithHut());
        builds.put("animal_pen", animalPen());
        builds.put("bell_tower", bellTower());

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

    /** A door: lower + upper half. {@code facing} is the way the door opens toward (out of the building). */
    private static void door(Blueprint.Builder b, int x, int y, int z, String wood, String facing) {
        b.set(x, y, z, bs("minecraft:" + wood + "_door[facing=" + facing + ",half=lower,hinge=left,open=false]"));
        b.set(x, y + 1, z, bs("minecraft:" + wood + "_door[facing=" + facing + ",half=upper,hinge=left,open=false]"));
    }

    /** Wall torch on a face. {@code facing} points away from the wall it's mounted on. */
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

    // =====================================================================
    //  THE ~20 BUILDS
    // =====================================================================

    /** 5x5 footprint, gabled oak cottage with a door, two windows, interior torch. H=6. */
    private static Blueprint smallCottage() {
        Blueprint.Builder b = Blueprint.builder("Small Cottage", 5, 7, 5);
        floor(b, 0, 0, 0, 4, 4, OAK_PLANKS);
        walls(b, 0, 0, 4, 4, 1, 3, OAK_PLANKS);
        corners(b, 0, 0, 4, 4, 1, 3, OAK_LOG_Y);
        door(b, 2, 1, 0, "oak", "north");
        window(b, 0, 2, 2, GLASS_PANE);
        window(b, 4, 2, 2, GLASS_PANE);
        window(b, 2, 2, 4, GLASS_PANE);
        b.set(2, 1, 2, TORCH); // interior floor light
        gableRoofX(b, 0, 0, 4, 4, 4, "oak_stairs", OAK_SLAB_BOTTOM);
        return b.build();
    }

    /** Classic plains village house: cobble base, oak walls + log frame, gable roof. 7x7, H=7. */
    private static Blueprint plainsHouse() {
        Blueprint.Builder b = Blueprint.builder("Plains House", 7, 8, 7);
        floor(b, 0, 0, 0, 6, 6, COBBLE);
        walls(b, 0, 0, 6, 6, 1, 3, OAK_PLANKS);
        corners(b, 0, 0, 6, 6, 1, 4, OAK_LOG_Y);
        // window band
        for (int z = 2; z <= 4; z += 2) {
            window(b, 0, 2, z, GLASS_PANE);
            window(b, 6, 2, z, GLASS_PANE);
        }
        window(b, 3, 2, 6, GLASS_PANE);
        door(b, 3, 1, 0, "oak", "north");
        wallTorch(b, 2, 3, 0, "north");
        wallTorch(b, 4, 3, 0, "north");
        gableRoofX(b, 0, 0, 6, 6, 4, "oak_stairs", OAK_SLAB_BOTTOM);
        return b.build();
    }

    /** Spruce log cabin, full-log walls, simple slab-topped gable. 6x6, H=6. */
    private static Blueprint logCabin() {
        Blueprint.Builder b = Blueprint.builder("Log Cabin", 6, 7, 6);
        floor(b, 0, 0, 0, 5, 5, SPRUCE_PLANKS);
        walls(b, 0, 0, 5, 5, 1, 3, SPRUCE_LOG_Y);
        door(b, 2, 1, 0, "spruce", "north");
        window(b, 0, 2, 2, GLASS_PANE);
        window(b, 5, 2, 2, GLASS_PANE);
        window(b, 5, 2, 3, GLASS_PANE);
        b.set(2, 1, 2, FURNACE);
        gableRoofX(b, 0, 0, 5, 5, 4, "spruce_stairs", SPRUCE_SLAB_BOTTOM);
        return b.build();
    }

    /** Two-story townhouse with a flat second-floor + slab roof, balcony fence. 6x8 high, footprint 6x6. */
    private static Blueprint twoStoryHouse() {
        Blueprint.Builder b = Blueprint.builder("Two-Story House", 6, 8, 6);
        floor(b, 0, 0, 0, 5, 5, COBBLE);
        // ground floor
        walls(b, 0, 0, 5, 5, 1, 3, OAK_PLANKS);
        corners(b, 0, 0, 5, 5, 1, 6, OAK_LOG_Y);
        door(b, 2, 1, 0, "oak", "north");
        window(b, 0, 2, 2, GLASS_PANE);
        window(b, 5, 2, 2, GLASS_PANE);
        // mid floor
        floor(b, 4, 1, 1, 4, 4, OAK_PLANKS);
        // upper floor
        walls(b, 0, 0, 5, 5, 5, 6, OAK_PLANKS);
        window(b, 0, 5, 3, GLASS_PANE);
        window(b, 5, 5, 3, GLASS_PANE);
        window(b, 2, 5, 0, GLASS_PANE);
        window(b, 3, 5, 0, GLASS_PANE);
        // upstairs study: bookshelves + a crafting table
        b.set(1, 5, 4, BOOKSHELF);
        b.set(4, 5, 4, BOOKSHELF);
        b.set(1, 5, 1, CRAFTING_TABLE);
        // roof with a small glass skylight
        flatRoof(b, 7, 0, 0, 5, 5, OAK_SLAB_TOP);
        b.set(2, 7, 2, GLASS);
        b.set(3, 7, 3, GLASS);
        b.set(2, 1, 2, TORCH);
        return b.build();
    }

    /** Cobblestone + mossy cobble cottage, stone-brick trim, gable roof. 5x6, H=6. */
    private static Blueprint cobbleHouse() {
        Blueprint.Builder b = Blueprint.builder("Cobble House", 5, 7, 6);
        floor(b, 0, 0, 0, 4, 5, COBBLE);
        walls(b, 0, 0, 4, 5, 1, 3, COBBLE);
        // mossy accents on the corners
        corners(b, 0, 0, 4, 5, 1, 3, MOSSY_COBBLE);
        // stone-brick lintel band
        line(b, 3, 0, 0, 4, 0, STONE_BRICKS);
        door(b, 2, 1, 0, "oak", "north");
        window(b, 0, 2, 3, GLASS_PANE);
        window(b, 4, 2, 3, GLASS_PANE);
        window(b, 2, 2, 5, GLASS_PANE);
        gableRoofX(b, 0, 0, 4, 5, 4, "cobblestone_stairs", COBBLE_SLAB_TOP);
        return b.build();
    }

    /** Vanilla-style village well: cobble ring, water, two fence posts + slab roof. 5x5, H=5. */
    private static Blueprint villageWell() {
        Blueprint.Builder b = Blueprint.builder("Village Well", 5, 5, 5);
        // base ring of cobble with a 3x3 water core sunk one level
        floor(b, 0, 1, 1, 3, 3, COBBLE);
        floor(b, 1, 0, 0, 4, 4, COBBLE); // rim
        // hollow the rim center and fill with water
        for (int x = 1; x <= 3; x++) {
            for (int z = 1; z <= 3; z++) {
                b.set(x, 1, z, WATER);
            }
        }
        b.set(2, 0, 2, WATER); // deeper center
        // four corner fence posts up to a slab roof
        pillar(b, 0, 0, 2, 3, OAK_FENCE);
        pillar(b, 4, 0, 2, 3, OAK_FENCE);
        pillar(b, 0, 4, 2, 3, OAK_FENCE);
        pillar(b, 4, 4, 2, 3, OAK_FENCE);
        flatRoof(b, 4, 0, 0, 4, 4, COBBLE_SLAB_TOP);
        b.set(2, 3, 0, HANGING_LANTERN);
        return b.build();
    }

    /** A single decorative lamp post: cobble base, stripped-log post, fence cross-arms, top lantern. 3x3, H=6. */
    private static Blueprint lampPost() {
        Blueprint.Builder b = Blueprint.builder("Lamp Post", 3, 6, 3);
        b.set(1, 0, 1, COBBLE);
        pillar(b, 1, 1, 1, 4, STRIPPED_OAK_Y); // central post
        // horizontal fence cross-arms near the top, in all four directions
        b.set(0, 4, 1, OAK_FENCE);
        b.set(2, 4, 1, OAK_FENCE);
        b.set(1, 4, 0, OAK_FENCE);
        b.set(1, 4, 2, OAK_FENCE);
        b.set(1, 5, 1, LANTERN); // top lantern
        return b.build();
    }

    /** A taller cobble watchtower with crenellations and a ladder-less open top. 5x8, footprint 5x5. */
    private static Blueprint watchtowerTall() {
        Blueprint.Builder b = Blueprint.builder("Tall Watchtower", 5, 8, 5);
        floor(b, 0, 0, 0, 4, 4, COBBLE);
        walls(b, 0, 0, 4, 4, 1, 5, COBBLE);
        corners(b, 0, 0, 4, 4, 1, 6, STONE_BRICKS);
        door(b, 2, 1, 0, "oak", "north");
        window(b, 0, 3, 2, GLASS_PANE);
        window(b, 4, 3, 2, GLASS_PANE);
        window(b, 2, 3, 4, GLASS_PANE);
        // battlement: top ring with alternating gaps (crenellations)
        for (int x = 0; x <= 4; x++) {
            if (x % 2 == 0) {
                b.set(x, 7, 0, COBBLE_WALL);
                b.set(x, 7, 4, COBBLE_WALL);
            }
        }
        for (int z = 0; z <= 4; z++) {
            if (z % 2 == 0) {
                b.set(0, 7, z, COBBLE_WALL);
                b.set(4, 7, z, COBBLE_WALL);
            }
        }
        b.set(2, 6, 2, TORCH);
        return b.build();
    }

    /** A planked footbridge with fence railings spanning Z. 3 wide x 9 long, low. */
    private static Blueprint woodenBridge() {
        Blueprint.Builder b = Blueprint.builder("Wooden Bridge", 3, 4, 9);
        // gentle arch: deck rises one block in the middle
        for (int z = 0; z <= 8; z++) {
            int y = (z >= 3 && z <= 5) ? 1 : 0;
            line(b, y, 0, z, 2, z, OAK_PLANKS);
        }
        // approach ramps with stairs so the arch reads as a bridge
        for (int x = 0; x <= 2; x++) {
            b.set(x, 0, 2, bs("minecraft:oak_stairs[facing=south,half=bottom,shape=straight]"));
            b.set(x, 0, 6, bs("minecraft:oak_stairs[facing=north,half=bottom,shape=straight]"));
        }
        // fence railings both sides, riding the deck height
        for (int z = 0; z <= 8; z++) {
            int y = (z >= 3 && z <= 5) ? 2 : 1;
            b.set(0, y, z, OAK_FENCE);
            b.set(2, y, z, OAK_FENCE);
        }
        b.set(0, 3, 4, HANGING_LANTERN);
        b.set(2, 3, 4, HANGING_LANTERN);
        return b.build();
    }

    /** A small tilled farm plot: 5x5 farmland with a central water source and wheat, fence + gate. 7x7. */
    private static Blueprint smallFarm() {
        Blueprint.Builder b = Blueprint.builder("Small Farm", 7, 4, 7);
        // outer dirt-path border
        floor(b, 0, 0, 0, 6, 6, DIRT_PATH);
        // inner 5x5 farmland with a water core, wheat on top
        for (int x = 1; x <= 5; x++) {
            for (int z = 1; z <= 5; z++) {
                if (x == 3 && z == 3) {
                    b.set(x, 0, z, WATER); // central source, sunk into the path level
                } else {
                    b.set(x, 0, z, FARMLAND);
                    b.set(x, 1, z, WHEAT);
                }
            }
        }
        // fence ring + a gate on the north side
        fenceRing(b, 1, 0, 0, 6, 6, OAK_FENCE);
        b.set(3, 1, 0, bs("minecraft:oak_fence_gate[facing=north,open=false,in_wall=false]"));
        // corner post lights
        light(b, 0, 0, 1, 2, OAK_FENCE);
        light(b, 6, 6, 1, 2, OAK_FENCE);
        return b.build();
    }

    /** Open hexish gazebo: 5x5 plank floor, four corner posts, pyramid-ish slab roof. H=5. */
    private static Blueprint gazebo() {
        Blueprint.Builder b = Blueprint.builder("Gazebo", 5, 5, 5);
        floor(b, 0, 0, 0, 4, 4, OAK_PLANKS);
        corners(b, 0, 0, 4, 4, 1, 3, OAK_LOG_Y);
        // half-height fence railing between posts (leave a gap on north as entry)
        line(b, 1, 0, 4, 4, 4, OAK_FENCE);
        line(b, 1, 0, 0, 0, 4, OAK_FENCE);
        line(b, 1, 4, 0, 4, 4, OAK_FENCE);
        // roof: stair ring sloping inward + slab cap
        for (int x = 0; x <= 4; x++) {
            b.set(x, 4, 0, bs("minecraft:oak_stairs[facing=south,half=bottom,shape=straight]"));
            b.set(x, 4, 4, bs("minecraft:oak_stairs[facing=north,half=bottom,shape=straight]"));
        }
        for (int z = 1; z <= 3; z++) {
            b.set(0, 4, z, bs("minecraft:oak_stairs[facing=east,half=bottom,shape=straight]"));
            b.set(4, 4, z, bs("minecraft:oak_stairs[facing=west,half=bottom,shape=straight]"));
        }
        floor(b, 4, 1, 1, 3, 3, OAK_SLAB_TOP);
        b.set(2, 4, 2, HANGING_LANTERN);
        return b.build();
    }

    /** Market stall: counter of slabs, two log posts, striped wool awning. 5x5, H=4. */
    private static Blueprint marketStall() {
        Blueprint.Builder b = Blueprint.builder("Market Stall", 5, 5, 4);
        floor(b, 0, 0, 0, 4, 3, OAK_PLANKS);
        // counter at the front
        line(b, 1, 0, 1, 4, 1, OAK_SLAB_TOP);
        // posts
        pillar(b, 0, 0, 1, 3, OAK_LOG_Y);
        pillar(b, 4, 0, 1, 3, OAK_LOG_Y);
        pillar(b, 0, 3, 1, 3, OAK_LOG_Y);
        pillar(b, 4, 3, 1, 3, OAK_LOG_Y);
        // wool awning sloped forward with stairs
        BlueprintBlockState awningStair = bs("minecraft:oak_stairs[facing=south,half=top,shape=straight]");
        for (int x = 0; x <= 4; x++) {
            b.set(x, 3, 0, awningStair);
            b.set(x, 3, 1, WHITE_WOOL);
            b.set(x, 4, 2, WHITE_WOOL);
            b.set(x, 4, 3, WHITE_WOOL);
        }
        // goods on the counter
        b.set(1, 2, 1, BARREL);
        b.set(3, 2, 1, bs("minecraft:hay_block[axis=y]"));
        b.set(2, 2, 0, HANGING_LANTERN);
        return b.build();
    }

    /** Fishing dock: a short plank pier over water with a post + hanging lantern and a barrel. 4x9. */
    private static Blueprint fishingDock() {
        Blueprint.Builder b = Blueprint.builder("Fishing Dock", 4, 4, 9);
        // water under the whole footprint (1 deep)
        floor(b, 0, 0, 0, 3, 8, WATER);
        // plank deck on the +x side, z 0..8
        for (int z = 0; z <= 8; z++) {
            b.set(1, 1, z, OAK_PLANKS);
            b.set(2, 1, z, OAK_PLANKS);
        }
        // support posts down into the water at the far end
        pillar(b, 1, 7, 0, 1, OAK_LOG_Y);
        pillar(b, 2, 7, 0, 1, OAK_LOG_Y);
        // lamp post at the end of the dock
        pillar(b, 2, 8, 2, 3, OAK_FENCE);
        b.set(2, 3, 8, HANGING_LANTERN);
        // a barrel and a railing
        b.set(1, 2, 1, BARREL);
        line(b, 2, 1, 0, 1, 8, OAK_FENCE);
        return b.build();
    }

    /** A defensive wall section with a gate arch: cobble wall + oak fence-gate + battlements. 7x6. */
    private static Blueprint wallGateSegment() {
        Blueprint.Builder b = Blueprint.builder("Wall Gate Segment", 7, 6, 3);
        floor(b, 0, 0, 0, 6, 2, COBBLE);
        // solid wall, but with a 3-wide x 3-high gateway opening in the middle (x 2..4)
        for (int x = 0; x <= 6; x++) {
            for (int y = 1; y <= 4; y++) {
                boolean gate = (x >= 2 && x <= 4 && y <= 3);
                if (!gate) {
                    b.set(x, y, 1, COBBLE);
                }
            }
        }
        // stone-brick gate arch lintel
        line(b, 4, 2, 1, 4, 1, STONE_BRICKS);
        // gate doors (oak) at the opening
        door(b, 2, 1, 1, "oak", "north");
        door(b, 4, 1, 1, "oak", "north");
        // battlement crenellations along the top
        for (int x = 0; x <= 6; x += 2) {
            b.set(x, 5, 1, COBBLE_WALL);
        }
        wallTorch(b, 1, 4, 0, "north");
        wallTorch(b, 5, 4, 0, "north");
        return b.build();
    }

    /** A small wayside shrine: stone-brick base, columns, a lantern altar under a slab canopy. 5x5, H=5. */
    private static Blueprint shrine() {
        Blueprint.Builder b = Blueprint.builder("Shrine", 5, 5, 5);
        floor(b, 0, 0, 0, 4, 4, STONE_BRICKS);
        // stepped altar
        floor(b, 1, 1, 1, 3, 3, bs("minecraft:chiseled_stone_bricks"));
        b.set(2, 2, 2, bs("minecraft:lantern[hanging=false]"));
        // four columns
        pillar(b, 0, 0, 1, 3, bs("minecraft:stone_brick_wall"));
        pillar(b, 4, 0, 1, 3, bs("minecraft:stone_brick_wall"));
        pillar(b, 0, 4, 1, 3, bs("minecraft:stone_brick_wall"));
        pillar(b, 4, 4, 1, 3, bs("minecraft:stone_brick_wall"));
        // slab canopy
        flatRoof(b, 4, 0, 0, 4, 4, SMOOTH_STONE_SLAB_TOP);
        return b.build();
    }

    /** The stone base + lower body of a windmill (no sails): round-ish cobble tower with a door. 7x8 footprint 7x7. */
    private static Blueprint windmillBase() {
        Blueprint.Builder b = Blueprint.builder("Windmill Base", 7, 8, 7);
        floor(b, 0, 0, 0, 6, 6, COBBLE);
        // octagon-ish wall: skip the four corner cells to round it off
        for (int y = 1; y <= 5; y++) {
            for (int x = 0; x <= 6; x++) {
                for (int z = 0; z <= 6; z++) {
                    boolean edge = (x == 0 || x == 6 || z == 0 || z == 6);
                    boolean corner = (x == 0 || x == 6) && (z == 0 || z == 6);
                    if (edge && !corner) {
                        b.set(x, y, z, COBBLE);
                    } else if (corner) {
                        // chamfer with stone-brick walls for a rounded look
                        b.set(x, y, z, bs("minecraft:stone_brick_wall"));
                    }
                }
            }
        }
        door(b, 3, 1, 0, "spruce", "north");
        window(b, 0, 3, 3, GLASS_PANE);
        window(b, 6, 3, 3, GLASS_PANE);
        window(b, 3, 3, 6, GLASS_PANE);
        // hub log where sails would attach
        pillar(b, 3, 0, 6, 7, SPRUCE_LOG_Y);
        // conical-ish slab cap
        flatRoof(b, 6, 1, 1, 5, 5, SPRUCE_SLAB_BOTTOM);
        flatRoof(b, 7, 2, 2, 4, 4, SPRUCE_SLAB_BOTTOM);
        return b.build();
    }

    /** Village bakery: oak/cobble shop with two smokers, a counter, chimney. 6x7, footprint 6x6. */
    private static Blueprint bakery() {
        Blueprint.Builder b = Blueprint.builder("Bakery", 6, 7, 6);
        floor(b, 0, 0, 0, 5, 5, COBBLE);
        walls(b, 0, 0, 5, 5, 1, 3, OAK_PLANKS);
        corners(b, 0, 0, 5, 5, 1, 4, OAK_LOG_Y);
        door(b, 2, 1, 0, "oak", "north");
        // big shop window
        window(b, 1, 2, 0, GLASS_PANE);
        window(b, 4, 2, 0, GLASS_PANE);
        window(b, 0, 2, 2, GLASS_PANE);
        window(b, 5, 2, 2, GLASS_PANE);
        // two smokers + a composter inside
        b.set(1, 1, 4, SMOKER);
        b.set(2, 1, 4, SMOKER);
        b.set(4, 1, 4, COMPOSTER);
        // counter of slabs
        line(b, 1, 1, 2, 4, 2, SMOOTH_STONE_SLAB_TOP);
        // chimney
        pillar(b, 1, 4, 4, 6, COBBLE);
        b.set(1, 6, 4, bs("minecraft:campfire[lit=true,facing=north]"));
        gableRoofX(b, 0, 0, 5, 5, 4, "oak_stairs", OAK_SLAB_BOTTOM);
        return b.build();
    }

    /** Blacksmith hut: open-front cobble forge with anvil, furnace, firebox, barrel. 6x6, H=5. */
    private static Blueprint blacksmithHut() {
        Blueprint.Builder b = Blueprint.builder("Blacksmith Hut", 6, 6, 6);
        floor(b, 0, 0, 0, 5, 5, COBBLE);
        // Three walls only — the north face (z=0) is left open as the working front.
        // (set() skips air and never clears an existing block, so we simply don't
        // draw the front wall rather than trying to carve it back out.)
        for (int y = 1; y <= 2; y++) {
            line(b, y, 0, 5, 5, 5, COBBLE); // south (back) wall
            line(b, y, 0, 0, 0, 5, COBBLE); // west wall
            line(b, y, 5, 0, 5, 5, COBBLE); // east wall
        }
        corners(b, 0, 0, 5, 5, 1, 3, OAK_LOG_Y);
        // forge furniture
        b.set(1, 1, 4, FURNACE);
        b.set(2, 1, 4, FURNACE);
        b.set(4, 1, 4, ANVIL);
        b.set(1, 1, 2, CAULDRON_WATER);
        b.set(3, 1, 2, BARREL);
        // counter + lantern
        line(b, 1, 1, 1, 4, 1, COBBLE_SLAB_TOP);
        b.set(2, 3, 2, HANGING_LANTERN);
        // slanted slab roof
        gableRoofX(b, 0, 0, 5, 5, 3, "cobblestone_stairs", COBBLE_SLAB_TOP);
        return b.build();
    }

    /** Fenced animal pen with a gate, hay bale, water trough, and a grass floor. 7x7, low. */
    private static Blueprint animalPen() {
        Blueprint.Builder b = Blueprint.builder("Animal Pen", 7, 3, 7);
        floor(b, 0, 0, 0, 6, 6, GRASS_BLOCK);
        fenceRing(b, 1, 0, 0, 6, 6, OAK_FENCE);
        // gate on north
        b.set(3, 1, 0, bs("minecraft:oak_fence_gate[facing=north,open=false,in_wall=false]"));
        // corner posts taller for visual interest
        pillar(b, 0, 0, 1, 2, OAK_LOG_Y);
        pillar(b, 6, 0, 1, 2, OAK_LOG_Y);
        pillar(b, 0, 6, 1, 2, OAK_LOG_Y);
        pillar(b, 6, 6, 1, 2, OAK_LOG_Y);
        // hay bale feed + a small water trough
        b.set(2, 1, 3, HAY);
        b.set(4, 0, 4, WATER);
        b.set(5, 0, 4, WATER);
        b.set(2, 1, 0, TORCH); // by the gate post
        return b.build();
    }

    /** Village bell tower: log frame, ladder-implied open shaft, a bell under a slab roof. 5x8, footprint 5x5. */
    private static Blueprint bellTower() {
        Blueprint.Builder b = Blueprint.builder("Bell Tower", 5, 8, 5);
        floor(b, 0, 0, 0, 4, 4, STONE_BRICKS);
        // four log legs
        corners(b, 0, 0, 4, 4, 1, 5, OAK_LOG_Y);
        // mid platform
        floor(b, 4, 1, 1, 3, 3, OAK_PLANKS);
        // upper open belfry: fence railing
        fenceRing(b, 5, 0, 0, 4, 4, OAK_FENCE);
        // top frame to hang the bell from
        corners(b, 0, 0, 4, 4, 6, 6, OAK_LOG_Y);
        line(b, 6, 0, 2, 4, 2, OAK_LOG_Y); // cross-beam (axis along X but log is y; acceptable as a beam stub)
        // the bell, hanging at center under the beam
        b.set(2, 5, 2, BELL_FLOOR);
        // slab pyramid cap
        flatRoof(b, 7, 1, 1, 3, 3, OAK_SLAB_TOP);
        for (int x = 0; x <= 4; x++) {
            b.set(x, 7, 0, bs("minecraft:oak_stairs[facing=south,half=bottom,shape=straight]"));
            b.set(x, 7, 4, bs("minecraft:oak_stairs[facing=north,half=bottom,shape=straight]"));
        }
        b.set(2, 6, 0, HANGING_LANTERN);
        return b.build();
    }
}
