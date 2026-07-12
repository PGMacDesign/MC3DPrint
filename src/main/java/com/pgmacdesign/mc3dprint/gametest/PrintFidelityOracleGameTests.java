package com.pgmacdesign.mc3dprint.gametest;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.blueprint.Blueprint;
import com.pgmacdesign.mc3dprint.blueprint.BlueprintBlockState;
import com.pgmacdesign.mc3dprint.blueprint.BlueprintFileStore;
import com.pgmacdesign.mc3dprint.fu.SpoolItem;
import com.pgmacdesign.mc3dprint.item.BlueprintDiscItem;
import com.pgmacdesign.mc3dprint.machine.PrinterBlockEntity;
import com.pgmacdesign.mc3dprint.machine.resin.ResinEffects;
import com.pgmacdesign.mc3dprint.registry.ModBlocks;
import com.pgmacdesign.mc3dprint.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * A print-fidelity ORACLE: instead of hand-asserting one block state at a time, each
 * test BUILDS a small structure, SCANS it into a blueprint, PRINTS the scanned disc,
 * and DIFFS the printed result against the original block-for-block. This covers whole
 * families of block states (every stair facing/half/shape, both door halves, bed parts,
 * tall-plant halves, connection bits, waterlog, crop ages) that the enumerated
 * {@link StructurePrintGameTests} cannot spell out one by one, and it catches classes of
 * bugs the compiler can't: facing flips, property loss across scan/serialize/print,
 * two-block self-break, suppress-drops pops.
 *
 * <p>Region A (build + scan) and region B (print) share the SAME coordinates: we build at
 * the print-target cells, scan them, CLEAR them, then print the disc back into the cleared
 * span. Clearing is essential; a reprint over intact blocks is a free no-op that would test
 * nothing (see {@code StructurePrintGameTests.reprintOverIntactStructureCostsNothing}).
 *
 * <p>Discs are written PLAYER-created (the scan path), so the intentional-divergence
 * allowlist in {@link #expectedPrinted(BlockState)} applies.
 */
@GameTestHolder(MC3DPrint.MOD_ID)
@PrefixGameTestTemplate(false)
public class PrintFidelityOracleGameTests {

    // The printer sits at y=1; a <=5x5 footprint centers over it and prints into y>=2,
    // so region A/B never overlaps the printer block itself.
    private static final BlockPos PRINTER = new BlockPos(2, 1, 2);

    // The printer's own placement flags: write the exact captured state, run no
    // updateShape (so two-block pieces and connections reproduce verbatim), drop nothing.
    // Building region A with the SAME flags makes scan->print a clean identity test.
    private static final int EXACT =
            Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    // How many mismatching cells to spell out before truncating. Kept small because
    // GameTest bakes the failure message into a lectern book with a ~1024-char page cap.
    private static final int MAX_REPORTED = 3;

    // ============================ shared harness ============================

    private record Cell(BlockPos local, BlockState state) {}

    /** A structure authored in blueprint-local coordinates, resolved to live states up front. */
    private static final class Structure {
        final int sizeX;
        final int sizeY;
        final int sizeZ;
        final List<Cell> cells = new ArrayList<>();

        Structure(int sizeX, int sizeY, int sizeZ) {
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
        }

        Structure add(int x, int y, int z, String state) {
            return add(x, y, z, resolve(state));
        }

        Structure add(int x, int y, int z, BlockState state) {
            cells.add(new Cell(new BlockPos(x, y, z), state));
            return this;
        }

        private static BlockState resolve(String state) {
            return BlueprintBlockState.parse(state).resolve()
                    .orElseThrow(() -> new IllegalStateException("unresolvable test state: " + state));
        }
    }

    /**
     * The blueprint origin, in helper-local coordinates, mirroring
     * {@link PrinterBlockEntity}: centered horizontally over the printer, one block up.
     * Uses the same integer {@code /2} the printer does so the two agree for even sizes.
     */
    private static BlockPos originLocal(int sizeX, int sizeZ) {
        return new BlockPos(PRINTER.getX() - sizeX / 2, PRINTER.getY() + 1, PRINTER.getZ() - sizeZ / 2);
    }

    private static PrinterBlockEntity poweredT4Printer(GameTestHelper helper) {
        // T4 (index 3): the largest single-block printer, 5x5 print area, fits every
        // footprint the oracle builds. Orientation stays NONE (no rotation), so facings
        // must reproduce exactly rather than being transformed.
        helper.setBlock(PRINTER, ModBlocks.PRINTERS.get(3).get());
        if (!(helper.getBlockEntity(PRINTER) instanceof PrinterBlockEntity printer)) {
            throw new GameTestAssertException("Printer block entity missing");
        }
        // Top energy well past any structure's need (T4 draws 60 RF/block; buffer caps at 600k).
        Optional.ofNullable(printer.getEnergyStorage()).ifPresent(energy -> {
            for (int i = 0; i < 200; i++) {
                energy.receiveEnergy(8_000, false);
            }
        });
        // A single T4 spool covers every tier<=4 block: affordableFu sums tiers >= the block's
        // cost tier and down-converts, so a full T4 reservoir pays for T1..T4 alike.
        ItemStack spool = new ItemStack(ModItems.SPOOLS.get(3).get());
        SpoolItem.setFu(spool, 1_000_000); // clamps to the 100k spool cap; still ample here
        printer.spoolInventory().setStackInSlot(0, spool);
        printer.setAutoStart(true);
        return printer;
    }

    private static ItemStack playerDiscFor(GameTestHelper helper, Blueprint blueprint) {
        BlueprintFileStore store = BlueprintFileStore.forServer(helper.getLevel().getServer());
        UUID id = store.save(blueprint);
        ItemStack disc = new ItemStack(ModItems.BLUEPRINT_DISC.get());
        // playerCreated=true: this is what a real scan produces, so crop-age normalization
        // (the anti-faucet allowlist item) is in force during the print.
        BlueprintDiscItem.writeBlueprint(disc, id, blueprint, true);
        return disc;
    }

    /**
     * The state the printer is EXPECTED to place for a given authored state, the
     * intentional-divergence allowlist, applied narrowly:
     * <ul>
     *   <li><b>(a)</b> crop / stem / nether-wart / berry / cocoa AGE is reset to 0 for a
     *       player-scanned disc ({@link ResinEffects#ungrownState}, the no-grow-faucet fix);</li>
     *   <li><b>(b)</b> nothing else is normalized on the print path here: no resin is armed,
     *       so {@code applyPlacementResin} is a no-op, and itemless-structural blocks place as-is;</li>
     *   <li><b>(c)</b> waterlog dewater fires ONLY in an ultrawarm dimension, this test world
     *       is the overworld, so waterlogged states are expected to survive untouched.</li>
     * </ul>
     * Orientation is NONE, so there is no rotate/mirror to model.
     */
    private static BlockState expectedPrinted(BlockState authored) {
        return ResinEffects.ungrownState(authored);
    }

    /**
     * Build region A, reconcile it exactly as the print does, scan + verify the scan,
     * clear, print, then diff the printed result against region A's reconciled truth.
     *
     * <p>The reconcile step is the crux. On completion the printer runs
     * {@code Block.updateFromNeighbourShapes} over every placed block (stair corners,
     * fence/pane/bar/wall connections, redstone), so the printed result is the
     * geometrically consistent form of the neighborhood, NOT the raw stored shape. A
     * REAL scan is always of a naturally built (already consistent) structure, so we
     * reproduce that here: reconcile region A with the identical pass, then treat region
     * A's post-reconcile world state as the oracle truth for both the scan check and the
     * print diff. This is order-independent for these blocks (a connection keys on the
     * neighbor's fixed identity/facing, not its reconciled sub-state), so the single
     * region-A pass and the printer's single end-of-job pass converge to the same states.
     */
    private static void runOracle(GameTestHelper helper, Structure structure) {
        PrinterBlockEntity printer = poweredT4Printer(helper);
        BlockPos origin = originLocal(structure.sizeX, structure.sizeZ);
        BlockPos maxLocal = origin.offset(structure.sizeX - 1, structure.sizeY - 1, structure.sizeZ - 1);

        // 1. build region A exactly as the printer places (no updateShape).
        for (Cell cell : structure.cells) {
            helper.getLevel().setBlock(helper.absolutePos(origin.offset(cell.local())), cell.state(), EXACT);
        }
        // 2. reconcile region A with the SAME pass the printer applies at completion.
        for (Cell cell : structure.cells) {
            reconcileCell(helper, origin.offset(cell.local()));
        }
        // 3. capture region A's reconciled truth, what a real scan sees, and what the
        //    print must reproduce.
        List<Cell> truth = new ArrayList<>();
        for (Cell cell : structure.cells) {
            truth.add(new Cell(cell.local(), helper.getBlockState(origin.offset(cell.local()))));
        }

        // 4. scan the full span; confirm the SCANNER reproduced region A verbatim.
        Blueprint blueprint = com.pgmacdesign.mc3dprint.scanner.ScanOperation.capture(
                helper.getLevel(), helper.absolutePos(origin), helper.absolutePos(maxLocal), "oracle");
        for (Cell cell : truth) {
            BlueprintBlockState scanned = blueprint.get(
                    cell.local().getX(), cell.local().getY(), cell.local().getZ());
            BlueprintBlockState wanted = BlueprintBlockState.fromBlockState(cell.state());
            if (scanned == null || !scanned.equals(wanted)) {
                helper.fail("scan lost " + cell.local() + ": " + wanted + " -> " + scanned);
                return;
            }
        }

        // 5. clear region A so the print must re-place every block (not free-skip matches).
        for (BlockPos p : BlockPos.betweenClosed(origin, maxLocal)) {
            helper.getLevel().setBlock(helper.absolutePos(p), AIR, EXACT);
        }

        // 6. load the scanned disc; the printer auto-starts on the next tick.
        printer.inventory().setStackInSlot(PrinterBlockEntity.SLOT_TEMPLATE, playerDiscFor(helper, blueprint));

        // 7. once the print finishes, diff every cell against region A's truth.
        helper.succeedWhen(() -> diffAfterPrint(helper, printer, truth, origin));
    }

    /** The printer's end-of-job reconcile, applied to a single region-A cell. */
    private static void reconcileCell(GameTestHelper helper, BlockPos localPos) {
        BlockPos abs = helper.absolutePos(localPos);
        BlockState current = helper.getLevel().getBlockState(abs);
        BlockState reconciled = Block.updateFromNeighbourShapes(current, helper.getLevel(), abs);
        // Mirror the printer's guard: never delete a block, so an unsupported fixture that
        // reconciles to air is kept as placed (same on both sides, so the diff still holds).
        if (reconciled != current && !reconciled.isAir()) {
            helper.getLevel().setBlock(abs, reconciled, EXACT);
        }
    }

    private static void diffAfterPrint(GameTestHelper helper, PrinterBlockEntity printer,
                                       List<Cell> truth, BlockPos origin) {
        // Disc ejects to the output slot only on completion; until then, keep waiting.
        if (!(printer.inventory().getStackInSlot(PrinterBlockEntity.SLOT_OUTPUT)
                .getItem() instanceof BlueprintDiscItem)) {
            throw new GameTestAssertException("print not finished (state " + printer.state() + ")");
        }
        StringBuilder problems = new StringBuilder();
        int mismatches = 0;
        for (Cell cell : truth) {
            BlockState expected = expectedPrinted(cell.state());
            BlockState actual = helper.getBlockState(origin.offset(cell.local()));
            if (actual != expected) {
                // Keep the message short, GameTest bakes it into a failure book capped at 1024 chars.
                if (mismatches < MAX_REPORTED) {
                    problems.append(" | ").append(cell.local())
                            .append(" want ").append(describe(expected))
                            .append(" got ").append(describe(actual));
                }
                mismatches++;
            }
        }
        if (mismatches > 0) {
            throw new GameTestAssertException("fidelity mismatch x" + mismatches + problems);
        }
    }

    private static String describe(BlockState state) {
        return BlueprintBlockState.fromBlockState(state).serialize();
    }

    // ============================ themed generators ============================

    // Each generator is deterministic: the enumerated families are fixed structures; the
    // mixed generator draws from a seeded PRNG (no wall-clock, no unseeded RNG).

    /** 1. Rotations: stairs at all four facings x top/bottom halves x shapes, plus slab types. */
    @GameTest(template = "empty5", timeoutTicks = 400)
    public static void oracleStairsAndSlabs(GameTestHelper helper) {
        Structure s = new Structure(5, 2, 2);
        // stairs: every facing, both halves, straight + inner/outer corner shapes
        s.add(0, 0, 0, "minecraft:oak_stairs[facing=north,half=bottom,shape=straight]");
        s.add(1, 0, 0, "minecraft:oak_stairs[facing=east,half=bottom,shape=straight]");
        s.add(2, 0, 0, "minecraft:oak_stairs[facing=south,half=top,shape=straight]");
        s.add(3, 0, 0, "minecraft:oak_stairs[facing=west,half=top,shape=straight]");
        s.add(4, 0, 0, "minecraft:stone_brick_stairs[facing=north,half=bottom,shape=inner_left]");
        s.add(0, 0, 1, "minecraft:stone_brick_stairs[facing=east,half=top,shape=inner_right]");
        s.add(1, 0, 1, "minecraft:stone_brick_stairs[facing=south,half=bottom,shape=outer_left]");
        s.add(2, 0, 1, "minecraft:stone_brick_stairs[facing=west,half=top,shape=outer_right]");
        // slabs: top / bottom / double
        s.add(3, 0, 1, "minecraft:stone_slab[type=top]");
        s.add(4, 0, 1, "minecraft:stone_slab[type=bottom]");
        s.add(0, 1, 0, "minecraft:oak_slab[type=double]");
        s.add(1, 1, 0, "minecraft:stone_slab[type=double]");
        runOracle(helper, s);
    }

    /** 2. Two-block pieces: doors (both halves), a bed (foot + head), tall plants + tall grass. */
    @GameTest(template = "empty5", timeoutTicks = 500)
    public static void oracleTwoBlockPieces(GameTestHelper helper) {
        Structure s = new Structure(5, 3, 3);
        // doors, the two halves must agree on facing/hinge/open, and each keeps its half
        s.add(0, 0, 0, "minecraft:oak_door[facing=north,hinge=left,half=lower,open=false]");
        s.add(0, 1, 0, "minecraft:oak_door[facing=north,hinge=left,half=upper,open=false]");
        s.add(1, 0, 0, "minecraft:iron_door[facing=east,hinge=right,half=lower,open=true]");
        s.add(1, 1, 0, "minecraft:iron_door[facing=east,hinge=right,half=upper,open=true]");
        // bed, foot + head, head one block east of the foot (facing=east)
        s.add(2, 0, 0, "minecraft:white_bed[facing=east,part=foot,occupied=false]");
        s.add(3, 0, 0, "minecraft:white_bed[facing=east,part=head,occupied=false]");
        // tall plants, dirt support (y=0), lower half (y=1), upper half (y=2)
        s.add(2, 0, 2, "minecraft:dirt");
        s.add(2, 1, 2, "minecraft:large_fern[half=lower]");
        s.add(2, 2, 2, "minecraft:large_fern[half=upper]");
        s.add(3, 0, 2, "minecraft:dirt");
        s.add(3, 1, 2, "minecraft:sunflower[half=lower]");
        s.add(3, 2, 2, "minecraft:sunflower[half=upper]");
        s.add(4, 0, 2, "minecraft:dirt");
        s.add(4, 1, 2, "minecraft:tall_grass[half=lower]");
        s.add(4, 2, 2, "minecraft:tall_grass[half=upper]");
        runOracle(helper, s);
    }

    /** 3. Attachables/directional: ladders, torches, levers/buttons, repeater/comparator/observer/piston. */
    @GameTest(template = "empty5", timeoutTicks = 500)
    public static void oracleAttachablesAndRedstone(GameTestHelper helper) {
        Structure s = new Structure(5, 2, 3);
        // full stone floor (support for everything floor-mounted) + a west wall at x=0,y=1
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 3; z++) {
                s.add(x, 0, z, "minecraft:stone");
            }
        }
        s.add(0, 1, 0, "minecraft:stone");
        s.add(0, 1, 1, "minecraft:stone");
        s.add(0, 1, 2, "minecraft:stone");
        // wall-mounted (backed by the west wall at x=0)
        s.add(1, 1, 0, "minecraft:ladder[facing=east]");
        s.add(1, 1, 1, "minecraft:wall_torch[facing=east]");
        s.add(1, 1, 2, "minecraft:lever[face=wall,facing=east,powered=false]");
        // floor-mounted
        s.add(2, 1, 0, "minecraft:torch");
        s.add(2, 1, 1, "minecraft:lever[face=floor,facing=north,powered=false]");
        s.add(2, 1, 2, "minecraft:stone_button[face=floor,facing=south,powered=false]");
        // redstone logic (facing + delay/mode preserved)
        s.add(3, 1, 0, "minecraft:repeater[facing=north,delay=3,locked=false,powered=false]");
        s.add(3, 1, 1, "minecraft:comparator[facing=east,mode=subtract,powered=false]");
        s.add(3, 1, 2, "minecraft:redstone_wire[east=side,west=none,north=none,south=none,power=0]");
        // full-cube mechanisms (facing preserved)
        s.add(4, 1, 0, "minecraft:observer[facing=up,powered=false]");
        s.add(4, 1, 1, "minecraft:piston[facing=west,extended=false]");
        s.add(4, 1, 2, "minecraft:sticky_piston[facing=up,extended=false]");
        runOracle(helper, s);
    }

    /**
     * 4. Connected/multipart. Connection state depends on neighbors, so it is authored
     * EXPLICITLY here (not left to natural formation): the pipeline must carry every
     * fence/pane/bar/wall/wire connection bit through scan -> serialize -> print verbatim,
     * which it does because the printer writes the exact captured state with no updateShape.
     */
    @GameTest(template = "empty5", timeoutTicks = 400)
    public static void oracleConnectedBlocks(GameTestHelper helper) {
        Structure s = new Structure(3, 2, 3);
        // fence line along +x
        s.add(0, 1, 0, "minecraft:oak_fence[north=false,south=false,east=true,west=false,waterlogged=false]");
        s.add(1, 1, 0, "minecraft:oak_fence[north=false,south=false,east=true,west=true,waterlogged=false]");
        s.add(2, 1, 0, "minecraft:oak_fence[north=false,south=false,east=false,west=true,waterlogged=false]");
        // glass-pane line along +x
        s.add(0, 1, 1, "minecraft:glass_pane[north=false,south=false,east=true,west=false,waterlogged=false]");
        s.add(1, 1, 1, "minecraft:glass_pane[north=false,south=false,east=true,west=true,waterlogged=false]");
        s.add(2, 1, 1, "minecraft:glass_pane[north=false,south=false,east=false,west=true,waterlogged=false]");
        // iron-bars line along +x
        s.add(0, 1, 2, "minecraft:iron_bars[north=false,south=false,east=true,west=false,waterlogged=false]");
        s.add(1, 1, 2, "minecraft:iron_bars[north=false,south=false,east=true,west=true,waterlogged=false]");
        s.add(2, 1, 2, "minecraft:iron_bars[north=false,south=false,east=false,west=true,waterlogged=false]");
        // cobblestone-wall line along +x (up + low connections)
        s.add(0, 0, 0, "minecraft:cobblestone_wall[north=none,south=none,east=low,west=none,up=true,waterlogged=false]");
        s.add(1, 0, 0, "minecraft:cobblestone_wall[north=none,south=none,east=low,west=low,up=false,waterlogged=false]");
        s.add(2, 0, 0, "minecraft:cobblestone_wall[north=none,south=none,east=none,west=low,up=true,waterlogged=false]");
        // redstone-wire run on a stone strip (support at y=0, wire at y=1)
        s.add(0, 0, 2, "minecraft:stone");
        s.add(1, 0, 2, "minecraft:stone");
        s.add(2, 0, 2, "minecraft:stone");
        s.add(0, 1, 2, "minecraft:redstone_wire[east=side,west=none,north=none,south=none,power=0]");
        s.add(1, 1, 2, "minecraft:redstone_wire[east=side,west=side,north=none,south=none,power=0]");
        s.add(2, 1, 2, "minecraft:redstone_wire[east=none,west=side,north=none,south=none,power=0]");
        runOracle(helper, s);
    }

    /** 5. Waterlogged (overworld: waterlog is preserved; ultrawarm dewater does not apply). */
    @GameTest(template = "empty5", timeoutTicks = 400)
    public static void oracleWaterlogged(GameTestHelper helper) {
        Structure s = new Structure(3, 1, 2);
        s.add(0, 0, 0, "minecraft:oak_slab[type=bottom,waterlogged=true]");
        s.add(1, 0, 0, "minecraft:stone_brick_stairs[facing=north,half=bottom,shape=straight,waterlogged=true]");
        s.add(2, 0, 0, "minecraft:oak_fence[north=false,south=false,east=false,west=false,waterlogged=true]");
        s.add(0, 0, 1, "minecraft:glass_pane[north=false,south=false,east=false,west=false,waterlogged=true]");
        s.add(1, 0, 1, "minecraft:cobblestone_wall[north=none,south=none,east=none,west=none,up=true,waterlogged=true]");
        s.add(2, 0, 1, "minecraft:iron_bars[north=false,south=false,east=false,west=false,waterlogged=true]");
        runOracle(helper, s);
    }

    /** 6a. Farmland + crops planted at age 0, a true identity round-trip (no normalization needed). */
    @GameTest(template = "empty5", timeoutTicks = 400)
    public static void oracleFarmlandAndCropsAgeZero(GameTestHelper helper) {
        Structure s = new Structure(4, 2, 1);
        s.add(0, 0, 0, "minecraft:farmland[moisture=7]");
        s.add(1, 0, 0, "minecraft:farmland[moisture=7]");
        s.add(2, 0, 0, "minecraft:farmland[moisture=7]");
        s.add(3, 0, 0, "minecraft:farmland[moisture=0]");
        s.add(0, 1, 0, "minecraft:wheat[age=0]");
        s.add(1, 1, 0, "minecraft:carrots[age=0]");
        s.add(2, 1, 0, "minecraft:potatoes[age=0]");
        s.add(3, 1, 0, "minecraft:beetroots[age=0]");
        runOracle(helper, s);
    }

    /**
     * 6b. Locks the anti-faucet fix: crops scanned at their MAX age must print back at age 0
     * from a player disc. Asserts age==0 explicitly (a literal, not via the ungrownState helper
     * the print path also uses) so a regression in that helper can't hide behind a circular check.
     */
    @GameTest(template = "empty5", timeoutTicks = 400)
    public static void oracleMatureCropsNormalizeToZero(GameTestHelper helper) {
        PrinterBlockEntity printer = poweredT4Printer(helper);
        Structure s = new Structure(4, 2, 1);
        s.add(0, 0, 0, "minecraft:farmland[moisture=7]");
        s.add(1, 0, 0, "minecraft:farmland[moisture=7]");
        s.add(2, 0, 0, "minecraft:farmland[moisture=7]");
        s.add(3, 0, 0, "minecraft:farmland[moisture=7]");
        s.add(0, 1, 0, "minecraft:wheat[age=7]");
        s.add(1, 1, 0, "minecraft:carrots[age=7]");
        s.add(2, 1, 0, "minecraft:potatoes[age=7]");
        s.add(3, 1, 0, "minecraft:beetroots[age=3]");

        BlockPos origin = originLocal(s.sizeX, s.sizeZ);
        BlockPos maxLocal = origin.offset(s.sizeX - 1, s.sizeY - 1, s.sizeZ - 1);
        for (Cell cell : s.cells) {
            helper.getLevel().setBlock(helper.absolutePos(origin.offset(cell.local())), cell.state(), EXACT);
        }
        Blueprint blueprint = com.pgmacdesign.mc3dprint.scanner.ScanOperation.capture(
                helper.getLevel(), helper.absolutePos(origin), helper.absolutePos(maxLocal), "oracle-mature");
        for (BlockPos p : BlockPos.betweenClosed(origin, maxLocal)) {
            helper.getLevel().setBlock(helper.absolutePos(p), AIR, EXACT);
        }
        printer.inventory().setStackInSlot(PrinterBlockEntity.SLOT_TEMPLATE, playerDiscFor(helper, blueprint));

        helper.succeedWhen(() -> {
            if (!(printer.inventory().getStackInSlot(PrinterBlockEntity.SLOT_OUTPUT)
                    .getItem() instanceof BlueprintDiscItem)) {
                throw new GameTestAssertException("print not finished (state " + printer.state() + ")");
            }
            assertCropAge(helper, origin.offset(0, 1, 0), BlockStateProperties.AGE_7, 0);
            assertCropAge(helper, origin.offset(1, 1, 0), BlockStateProperties.AGE_7, 0);
            assertCropAge(helper, origin.offset(2, 1, 0), BlockStateProperties.AGE_7, 0);
            assertCropAge(helper, origin.offset(3, 1, 0), BlockStateProperties.AGE_3, 0);
        });
    }

    private static void assertCropAge(GameTestHelper helper, BlockPos localPos,
                                      net.minecraft.world.level.block.state.properties.IntegerProperty age,
                                      int expected) {
        BlockState state = helper.getBlockState(localPos);
        if (!state.hasProperty(age)) {
            throw new GameTestAssertException("expected a crop at " + localPos + ", got " + describe(state));
        }
        int actual = state.getValue(age);
        if (actual != expected) {
            throw new GameTestAssertException("crop at " + localPos + " printed age " + actual
                    + ", expected " + expected + " (mature crops must normalize on a player disc)");
        }
    }

    // 7. Mixed/random: a seeded fill of a 5x5x3 region from a palette of round-trip-safe,
    // self-supporting states (no crops, no attachables) across several seeds.

    private static final String[] MIXED_PALETTE = {
            "minecraft:stone",
            "minecraft:cobblestone",
            "minecraft:oak_planks",
            "minecraft:bricks",
            "minecraft:white_wool",
            "minecraft:glass",
            "minecraft:smooth_stone",
            "minecraft:polished_andesite",
            "minecraft:chiseled_stone_bricks",
            "minecraft:oak_log[axis=x]",
            "minecraft:oak_log[axis=y]",
            "minecraft:oak_log[axis=z]",
            "minecraft:quartz_pillar[axis=x]",
            "minecraft:oak_stairs[facing=north,half=bottom,shape=straight]",
            "minecraft:oak_stairs[facing=east,half=top,shape=straight]",
            "minecraft:stone_brick_stairs[facing=south,half=bottom,shape=inner_left]",
            "minecraft:stone_slab[type=top]",
            "minecraft:stone_slab[type=bottom]",
            "minecraft:stone_slab[type=double]",
            "minecraft:oak_slab[type=bottom,waterlogged=true]",
    };

    private static Structure randomStructure(long seed) {
        Random random = new Random(seed);
        Structure s = new Structure(5, 3, 5);
        // ~45% fill keeps the block count near ~34 (well under the T4 energy budget) while
        // still exercising a dense, varied region.
        for (int y = 0; y < s.sizeY; y++) {
            for (int z = 0; z < s.sizeZ; z++) {
                for (int x = 0; x < s.sizeX; x++) {
                    if (random.nextDouble() < 0.45) {
                        s.add(x, y, z, MIXED_PALETTE[random.nextInt(MIXED_PALETTE.length)]);
                    }
                }
            }
        }
        return s;
    }

    @GameTest(template = "empty5", timeoutTicks = 600)
    public static void oracleMixedSeedA(GameTestHelper helper) {
        runOracle(helper, randomStructure(0x5EEDA11CEL));
    }

    @GameTest(template = "empty5", timeoutTicks = 600)
    public static void oracleMixedSeedB(GameTestHelper helper) {
        runOracle(helper, randomStructure(0xB1DECAFEL));
    }

    @GameTest(template = "empty5", timeoutTicks = 600)
    public static void oracleMixedSeedC(GameTestHelper helper) {
        runOracle(helper, randomStructure(0xFAB21CA7EDL));
    }
}
