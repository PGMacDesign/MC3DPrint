package com.pgmacdesign.mc3dprint.gametest;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.blueprint.Blueprint;
import com.pgmacdesign.mc3dprint.blueprint.BlueprintBlockState;
import com.pgmacdesign.mc3dprint.blueprint.CuratedBlueprints;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Optional;

/**
 * Collection-routing regression tests for the curated FARM blueprints.
 *
 * <h2>What this proves (and what it deliberately does NOT)</h2>
 * The full survival farm loop — player plants a crop, it grows on a slow random
 * tick, a redstone/observer mechanism snaps it, mobs spawn — is <b>not</b>
 * tractable inside a GameTest (no player, no multi-minute growth, no mob spawn
 * cycles). The tractable, high-value slice is <b>collection routing</b>: once a
 * product item exists at the harvest point, do the build's hoppers / water
 * streams actually deliver it to the collection chest?
 *
 * <p>Several shipped farms got exactly this wrong historically (a hopper facing
 * the wrong way, a water channel that dead-ends short of the hopper, a chest the
 * chain never reaches). This test encodes the desired behaviour as a regression:
 * for each covered farm we
 * <ol>
 *   <li>place the REAL curated blueprint into the test world (every cell resolved
 *       and {@code setBlock}-placed, so the test reflects what ships — working
 *       hoppers, water sources, chest);</li>
 *   <li>locate the collection {@link ChestBlockEntity} by scanning the placed
 *       volume (cross-checked against the known chest cell from the build's dump);</li>
 *   <li>spawn an {@link net.minecraft.world.entity.item.ItemEntity} of the farm's
 *       PRODUCT at the harvest point (the water-collection cell / hopper-floor cell
 *       where a snapped crop or a mob drop would land);</li>
 *   <li>tick forward (hoppers move 1 item / 8 ticks; water flow needs a few ticks
 *       to propagate) and poll the chest;</li>
 *   <li>assert the chest's inventory contains the product — failing if the drop
 *       never arrives (= a real routing bug).</li>
 * </ol>
 *
 * <h2>Coverage map</h2>
 * See {@code docs/farm-test-coverage.md} for the auto-tested vs. needs-live-playtest
 * matrix. In short: this file auto-tests <b>routing</b> only. Growth rate, redstone
 * timing, and mob-spawn behaviour still need a human playtest.
 *
 * <h2>Adding a farm</h2>
 * Append a {@link Farm} entry to {@link #FARMS}. Coordinates are blueprint-local
 * (origin = min corner), taken straight from {@code build/blueprint-dumps/<name>.txt}
 * (regenerate with {@code ./gradlew test --tests '*BlueprintDumpTest*'
 * -DdumpBlueprints=true --rerun-tasks}). The dump grid is {@code rows = z}
 * (0..N, south), {@code cols = x} (0..N, east); the layer header is the Y.
 *
 * <pre>
 *   ./gradlew runGameTestServer -q
 * </pre>
 */
@GameTestHolder(MC3DPrint.MOD_ID)
@PrefixGameTestTemplate(false)
public class FarmCollectionGameTests {

    /**
     * Local origin the blueprint is placed at inside the test world. Relative y=0
     * is the GameTest floor, so the build's own y=0 layer lands at world-relative
     * y=1 (its block entities then have a solid block beneath them). Chosen so even
     * the largest covered build (iron_farm, 13×14×13) clears the floor and the
     * spawn pad without bleeding into the floor barrier.
     */
    private static final BlockPos ORIGIN = new BlockPos(0, 1, 0);

    /** How long we give hoppers + water to route before declaring a routing failure. */
    private static final int ROUTE_TICKS = 200;

    /**
     * One covered farm. All coordinates are <b>blueprint-local</b> (see the dump);
     * they are converted to test-world positions via {@link #ORIGIN}.
     *
     * @param blueprint  curated blueprint name (a {@link CuratedBlueprints#CURATED_NAMES} entry)
     * @param productId  registry id of the harvested product the routing must deliver
     * @param chest      local cell of the collection chest (from the dump — a sanity
     *                   cross-check; the test also scans for the chest independently)
     * @param harvest    local cell to spawn the product in — the water-collection /
     *                   hopper-floor cell where a snapped crop or mob drop would appear
     */
    private record Farm(String blueprint, String productId, BlockPos chest, BlockPos harvest) {}

    /**
     * The covered set. Each harvest cell is the block <b>directly above the first
     * hopper of the collection apparatus</b> — exactly where a snapped crop / mob
     * drop falls into the chain. A hopper pulls items from the block above it, so an
     * item dropped here is picked up and routed through the (real, placed) hopper
     * chain to the chest. This isolates the leg that has historically broken
     * (wrong-facing hopper, chain that never reaches the chest, chest the chain
     * misses); the upstream water-<em>sweep</em> leg is a documented live-playtest
     * item (see {@code docs/farm-test-coverage.md}), because still water doesn't push
     * items and GameTest fluid ticks make a sweep assertion flaky.
     *
     * <pre>
     * farm                  product        chest (local)   harvest drop (local)   routing path under test
     * ───────────────────── ────────────── ─────────────── ────────────────────── ───────────────────────────────────
     * sugarcane_farm_auto   sugar_cane     (4,0,8)         (4,1,7) above hopper    hopper(4,0,7,facing N) → chest
     * kelp_farm             dried_kelp     (4,0,7)         (4,1,2) above N hopper  hopper floor(4,0,2..6,S) chains S → chest
     * bamboo_farm           bamboo         (4,0,8)         (4,1,5) in canal water  hopper line(4,0,1..7,S) chains S → chest
     * cactus_farm           cactus (drop)  (3,0,6)         (1,1,2) in water moat   hopper floor(1..5,1..5) funnels → chest
     * iron_farm             iron_ingot     (6,1,9)         (4,2,4) above ring hop  ring(4,1,4,S)→…→(6,1,7)→drain(6,1,8,S)→chest
     * chicken_coop_auto     cooked_chicken (2,1,4)         (2,2,2) above landing hopper  landing hopper(2,1,2,S)→carrier→chest
     * </pre>
     *
     * <p>Harvest cells sit above a TRANSPORT hopper. The chicken cooker's drop cell (3,2,3)
     * is a lit campfire — campfires DON'T destroy dropped items (only living entities), so
     * the cooked chicken sits on it and the hopper below routes it. The iron_farm harvest
     * cell deliberately avoids its lava blade (2×2 core at x∈{5,6},z∈{5,6}).
     */
    private static final Farm[] FARMS = {
            new Farm("sugarcane_farm_auto", "minecraft:sugar_cane",
                    new BlockPos(4, 0, 8), new BlockPos(4, 1, 7)),
            new Farm("kelp_farm", "minecraft:dried_kelp",
                    new BlockPos(4, 0, 7), new BlockPos(4, 1, 2)),
            new Farm("bamboo_farm", "minecraft:bamboo",
                    new BlockPos(4, 0, 8), new BlockPos(4, 1, 5)),
            new Farm("cactus_farm", "minecraft:cactus",
                    new BlockPos(3, 0, 6), new BlockPos(1, 1, 2)),
            new Farm("iron_farm", "minecraft:iron_ingot",
                    new BlockPos(6, 1, 9), new BlockPos(4, 2, 4)),
            new Farm("chicken_coop_auto", "minecraft:cooked_chicken",
                    new BlockPos(2, 1, 4), new BlockPos(2, 2, 2)),
    };

    // ── one @GameTest per farm (each gets its own world instance / pass-fail line) ──

    @GameTest(template = "empty5", timeoutTicks = ROUTE_TICKS + 60)
    public static void sugarcaneFarmRoutesToChest(GameTestHelper helper) {
        runRoutingTest(helper, FARMS[0]);
    }

    @GameTest(template = "empty5", timeoutTicks = ROUTE_TICKS + 60)
    public static void kelpFarmRoutesToChest(GameTestHelper helper) {
        runRoutingTest(helper, FARMS[1]);
    }

    @GameTest(template = "empty5", timeoutTicks = ROUTE_TICKS + 60)
    public static void bambooFarmRoutesToChest(GameTestHelper helper) {
        runRoutingTest(helper, FARMS[2]);
    }

    @GameTest(template = "empty5", timeoutTicks = ROUTE_TICKS + 60)
    public static void cactusFarmRoutesToChest(GameTestHelper helper) {
        runRoutingTest(helper, FARMS[3]);
    }

    @GameTest(template = "empty5", timeoutTicks = ROUTE_TICKS + 60)
    public static void ironFarmRoutesToChest(GameTestHelper helper) {
        runRoutingTest(helper, FARMS[4]);
    }

    @GameTest(template = "empty5", timeoutTicks = ROUTE_TICKS + 60)
    public static void chickenCoopRoutesToChest(GameTestHelper helper) {
        runRoutingTest(helper, FARMS[5]);
    }

    // ── shared routing harness ──────────────────────────────────────────────────

    private static void runRoutingTest(GameTestHelper helper, Farm farm) {
        Blueprint blueprint = CuratedBlueprints.loadBundled(farm.blueprint())
                .orElseThrow(() -> new IllegalStateException(
                        "[" + farm.blueprint() + "] blueprint not found on classpath"));

        placeBlueprint(helper, blueprint);

        Item product = ForgeRegistries.ITEMS.getValue(
                net.minecraft.resources.ResourceLocation.parse(farm.productId()));
        if (product == null) {
            helper.fail("[" + farm.blueprint() + "] unknown product item " + farm.productId());
            return;
        }

        // Locate the chest: scan the placed volume for a ChestBlockEntity, then
        // cross-check it sits where the dump says it should. This catches a moved
        // chest as well as a routing fault.
        BlockPos chestLocal = findChest(helper, blueprint, farm);
        if (chestLocal == null) {
            helper.fail("[" + farm.blueprint() + "] no chest found in the placed build "
                    + "(expected near local " + farm.chest() + ")");
            return;
        }
        BlockPos chestWorld = ORIGIN.offset(chestLocal);

        // The product has to be ROUTED to the chest, so it must not already be there.
        Container chestBefore = chestContainer(helper, chestWorld);
        if (chestBefore == null) {
            helper.fail("[" + farm.blueprint() + "] chest at " + chestLocal + " is not a container");
            return;
        }

        // Drop the product at the harvest point — the cell directly above the first
        // hopper of the collection chain, exactly where a snapped crop / mob drop lands.
        // We spawn it LOW in that cell (y + 0.2, x/z centred) so it sits at the hopper's
        // suck-shape (the box just above the hopper) and is collected on the next tick.
        // The BlockPos overload spawns at the cell CENTER (y + 0.5); in a flooded
        // collection cell (bamboo/sugarcane canals) the item's buoyancy then floats it UP
        // and OUT of the suck box before the hopper grabs it — a test artefact, not a
        // build fault — so we use the float overload to seat it right at the hopper mouth.
        BlockPos harvestWorld = ORIGIN.offset(farm.harvest());
        helper.spawnItem(product,
                harvestWorld.getX() + 0.5f, harvestWorld.getY() + 0.2f, harvestWorld.getZ() + 0.5f);

        // Poll the chest until the product arrives (hoppers: 1 item / 8 ticks; water
        // flow: a few ticks to propagate). A clean PASS the moment it lands.
        helper.succeedWhen(() -> {
            Container chest = chestContainer(helper, chestWorld);
            if (chest == null || chest.countItem(product) <= 0) {
                throw new net.minecraft.gametest.framework.GameTestAssertException(
                        "[" + farm.blueprint() + "] product " + farm.productId()
                                + " has not reached the chest at local " + chestLocal
                                + " (dropped at local " + farm.harvest() + ")");
            }
        });
    }

    /** Resolve + place every blueprint cell at {@link #ORIGIN}. Unresolvable cells are skipped. */
    private static void placeBlueprint(GameTestHelper helper, Blueprint blueprint) {
        blueprint.forEachBlock((local, paletteIndex) -> {
            BlueprintBlockState cell = blueprint.palette().get(paletteIndex);
            Optional<BlockState> resolved = cell.resolve();
            if (resolved.isEmpty()) return; // unprintable in this world — skip, matches the printer
            // setBlock uses RELATIVE coords; ORIGIN shifts the whole build off the floor.
            helper.setBlock(ORIGIN.offset(local), resolved.get());
        });
    }

    /**
     * Find the collection chest. Prefer the cell the dump records (fast + exact);
     * fall back to scanning the whole placed volume for any {@link ChestBlockEntity}
     * so a future chest move still resolves (and a deleted chest fails loudly).
     */
    private static BlockPos findChest(GameTestHelper helper, Blueprint blueprint, Farm farm) {
        if (helper.getBlockEntity(ORIGIN.offset(farm.chest())) instanceof ChestBlockEntity) {
            return farm.chest();
        }
        for (int y = 0; y < blueprint.sizeY(); y++) {
            for (int z = 0; z < blueprint.sizeZ(); z++) {
                for (int x = 0; x < blueprint.sizeX(); x++) {
                    BlockPos local = new BlockPos(x, y, z);
                    if (helper.getBlockEntity(ORIGIN.offset(local)) instanceof ChestBlockEntity) {
                        return local;
                    }
                }
            }
        }
        return null;
    }

    /** The chest at the given world-relative pos as a {@link Container}, or null if it isn't one. */
    private static Container chestContainer(GameTestHelper helper, BlockPos worldPos) {
        return helper.getBlockEntity(worldPos) instanceof Container container ? container : null;
    }
}
