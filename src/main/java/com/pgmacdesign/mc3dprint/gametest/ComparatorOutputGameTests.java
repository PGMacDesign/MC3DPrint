package com.pgmacdesign.mc3dprint.gametest;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.blueprint.Blueprint;
import com.pgmacdesign.mc3dprint.blueprint.BlueprintBlockState;
import com.pgmacdesign.mc3dprint.blueprint.BlueprintFileStore;
import com.pgmacdesign.mc3dprint.fu.SpoolItem;
import com.pgmacdesign.mc3dprint.item.BlueprintDiscItem;
import com.pgmacdesign.mc3dprint.machine.MachineTier;
import com.pgmacdesign.mc3dprint.machine.PrinterBlockEntity;
import com.pgmacdesign.mc3dprint.machine.multiblock.ControllerBlock;
import com.pgmacdesign.mc3dprint.machine.multiblock.MultiblockPattern;
import com.pgmacdesign.mc3dprint.machine.upgrade.UpgradeItem;
import com.pgmacdesign.mc3dprint.registry.ModBlocks;
import com.pgmacdesign.mc3dprint.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * Comparator output on printers and fabricators: 0 when nothing is running,
 * otherwise 1 to 15 scaled by job progress.
 *
 * <p>This is deliberately UNGATED, so none of these tests install a Redstone
 * Module. The 0-versus-1 boundary is the property worth protecting: a comparator
 * has to be able to tell "idle" from "running but barely started".
 */
@GameTestHolder(MC3DPrint.MOD_ID)
@PrefixGameTestTemplate(false)
public class ComparatorOutputGameTests {

    private static final BlockPos PRINTER_POS = new BlockPos(2, 1, 2);

    /** Exactly what a comparator asks the block for. */
    private static int comparator(GameTestHelper helper, BlockPos local) {
        BlockPos abs = helper.absolutePos(local);
        return helper.getLevel().getBlockState(abs).getAnalogOutputSignal(helper.getLevel(), abs);
    }

    private static PrinterBlockEntity poweredT3(GameTestHelper helper) {
        helper.setBlock(PRINTER_POS, ModBlocks.PRINTERS.get(2).get()); // T3: first structure tier
        if (!(helper.getBlockEntity(PRINTER_POS) instanceof PrinterBlockEntity printer)) {
            throw new GameTestAssertException("Printer block entity missing");
        }
        java.util.Optional.ofNullable(printer.getEnergyStorage()).ifPresent(energy -> {
            for (int i = 0; i < 60; i++) {
                energy.receiveEnergy(4_000, false);
            }
        });
        PrinterGameTests.attachLoadedSpool(printer);
        return printer;
    }

    /** 3x1x3 solid stone: nine placements, so progress is observable in steps. */
    private static Blueprint slabBlueprint() {
        Blueprint.Builder builder = Blueprint.builder("gametest-comparator", 3, 1, 3);
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                builder.set(x, 0, z, BlueprintBlockState.parse("minecraft:stone"));
            }
        }
        return builder.build();
    }

    private static ItemStack discFor(GameTestHelper helper, Blueprint blueprint) {
        BlueprintFileStore store = BlueprintFileStore.forServer(helper.getLevel().getServer());
        UUID id = store.save(blueprint);
        ItemStack disc = new ItemStack(ModItems.BLUEPRINT_DISC.get());
        BlueprintDiscItem.writeBlueprint(disc, id, blueprint);
        return disc;
    }

    // --- the 0 versus 1 boundary ---

    @GameTest(template = "empty5", timeoutTicks = 200)
    public static void comparatorReadsZeroWhenNothingIsRunning(GameTestHelper helper) {
        PrinterBlockEntity printer = poweredT3(helper);
        BlockPos abs = helper.absolutePos(PRINTER_POS);
        if (!helper.getLevel().getBlockState(abs).hasAnalogOutputSignal()) {
            helper.fail("A printer must advertise comparator output");
            return;
        }
        if (comparator(helper, PRINTER_POS) != 0) {
            helper.fail("An empty printer must read 0, got " + comparator(helper, PRINTER_POS));
            return;
        }
        // A disc sitting in a machine that has not started is still not running.
        printer.setAutoStart(false);
        printer.inventory().setStackInSlot(PrinterBlockEntity.SLOT_TEMPLATE,
                discFor(helper, slabBlueprint()));
        helper.runAfterDelay(20, () -> {
            if (printer.activeJob() != null) {
                helper.fail("Nothing should have started without a trigger");
                return;
            }
            if (comparator(helper, PRINTER_POS) != 0) {
                helper.fail("A loaded but unstarted printer must read 0, got "
                        + comparator(helper, PRINTER_POS));
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty5", timeoutTicks = 400)
    public static void comparatorTracksBlueprintProgressFromOneToFifteen(GameTestHelper helper) {
        PrinterBlockEntity printer = poweredT3(helper);
        // No Redstone Module: comparator reads are free, which is the whole point.
        if (printer.upgradeCount(UpgradeItem.Type.REDSTONE) != 0) {
            helper.fail("This test must run with no Redstone Module installed");
            return;
        }
        printer.setAutoStart(true);
        printer.inventory().setStackInSlot(PrinterBlockEntity.SLOT_TEMPLATE,
                discFor(helper, slabBlueprint()));

        int[] lastSeen = {0};
        boolean[] sawOne = {false};
        boolean[] sawFifteen = {false};
        boolean[] wentBackwards = {false};

        helper.succeedWhen(() -> {
            int now = comparator(helper, PRINTER_POS);
            if (printer.activeJob() != null) {
                if (now == 1) {
                    sawOne[0] = true;
                }
                if (now == 15) {
                    sawFifteen[0] = true;
                }
                if (now < lastSeen[0]) {
                    wentBackwards[0] = true;
                }
                if (now == 0) {
                    throw new GameTestAssertException("A running job must never read 0");
                }
                lastSeen[0] = now;
            }
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(1, 2, 1));
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(3, 2, 3));
            if (printer.state() != PrinterBlockEntity.State.IDLE) {
                throw new GameTestAssertException("Waiting for IDLE, got " + printer.state());
            }
            if (!sawOne[0]) {
                throw new GameTestAssertException("Comparator never read 1 at job start");
            }
            if (!sawFifteen[0]) {
                throw new GameTestAssertException("Comparator never reached 15 at completion");
            }
            if (wentBackwards[0]) {
                throw new GameTestAssertException("Comparator reading went backwards mid-job");
            }
            if (comparator(helper, PRINTER_POS) != 0) {
                throw new GameTestAssertException("Comparator must fall back to 0 once the job ends, got "
                        + comparator(helper, PRINTER_POS));
            }
        });
    }

    /**
     * Spans at least two completed items, sampling every tick. Stopping at the first
     * item cannot see the boundary, and the boundary is where this went wrong: the tick
     * an item completes resets the progress counter while the machine is still PRINTING
     * and writing the output slot, so a comparator really did observe a 0 between items
     * and an edge-driven contraption saw a phantom pulse once per item.
     */
    @GameTest(template = "empty5", timeoutTicks = 600)
    public static void comparatorNeverDropsToZeroBetweenItems(GameTestHelper helper) {
        PrinterBlockEntity printer = poweredT3(helper);
        printer.setAutoStart(true);
        printer.inventory().setStackInSlot(PrinterBlockEntity.SLOT_TEMPLATE,
                new ItemStack(Items.STONE, 64));

        boolean[] sawOne = {false};
        boolean[] sawFifteen = {false};
        int[] zeroAfterItems = {-1};

        helper.runAfterDelay(2, () -> {
            if (comparator(helper, PRINTER_POS) != 1) {
                helper.fail("An item print should read 1 just after it starts, got "
                        + comparator(helper, PRINTER_POS));
            }
        });
        helper.succeedWhen(() -> {
            int now = comparator(helper, PRINTER_POS);
            int produced = printer.inventory().getStackInSlot(PrinterBlockEntity.SLOT_OUTPUT).getCount();
            if (now == 1) {
                sawOne[0] = true;
            }
            if (now == 15) {
                sawFifteen[0] = true;
            }
            // Only police 0 once the run is under way: the setup tick before the machine
            // has ticked at all is legitimately idle, and that 0 is correct.
            if (sawOne[0] && now == 0 && zeroAfterItems[0] < 0) {
                zeroAfterItems[0] = produced;
            }
            if (produced < 2) {
                throw new GameTestAssertException("waiting for 2 items, have " + produced);
            }
            if (zeroAfterItems[0] >= 0) {
                throw new GameTestAssertException("Comparator dropped to 0 while still printing (after item "
                        + zeroAfterItems[0] + "); 0 must mean nothing loaded, not an item boundary");
            }
            if (!sawOne[0]) {
                throw new GameTestAssertException("Comparator never read 1 during the item print");
            }
            if (!sawFifteen[0]) {
                throw new GameTestAssertException("Item print should reach 15 on its last tick");
            }
        });
    }

    /**
     * A machine paused with a full output still has work loaded, so it must read at
     * least 1. It sits at progress 0 in that state, which is exactly the case a plain
     * "progress > 0" reading gets wrong.
     */
    @GameTest(template = "empty5", timeoutTicks = 400)
    public static void pausedOnFullOutputStillReadsAtLeastOne(GameTestHelper helper) {
        PrinterBlockEntity printer = poweredT3(helper);
        printer.setAutoStart(true);
        printer.inventory().setStackInSlot(PrinterBlockEntity.SLOT_TEMPLATE,
                new ItemStack(Items.STONE, 64));
        // Pre-fill the output with a full stack of something else so the very first
        // finished item has nowhere to go.
        printer.inventory().setStackInSlot(PrinterBlockEntity.SLOT_OUTPUT,
                new ItemStack(Items.DIRT, 64));

        helper.succeedWhen(() -> {
            if (printer.state() != PrinterBlockEntity.State.PAUSED_OUTPUT_FULL) {
                throw new GameTestAssertException("Waiting for PAUSED_OUTPUT_FULL, got " + printer.state());
            }
            int now = comparator(helper, PRINTER_POS);
            if (now < 1) {
                throw new GameTestAssertException(
                        "A machine paused with work loaded must read at least 1, got " + now);
            }
        });
    }

    @GameTest(template = "empty5", timeoutTicks = 400)
    public static void comparatorTracksDeconstructProgress(GameTestHelper helper) {
        PrinterBlockEntity printer = poweredT3(helper);
        // A 3x1x3 slab of stone directly above the machine, for it to eat.
        for (int x = 1; x <= 3; x++) {
            for (int z = 1; z <= 3; z++) {
                helper.setBlock(new BlockPos(x, 2, z), Blocks.STONE);
            }
        }
        // setDeconstructRegion takes WORLD positions, not gametest-local ones: passing
        // local coords lands the region near the world origin and it is refused TOO_FAR.
        if (printer.setDeconstructRegion(helper.absolutePos(new BlockPos(1, 2, 1)),
                helper.absolutePos(new BlockPos(3, 2, 3))) != PrinterBlockEntity.RegionResult.SET) {
            helper.fail("Deconstruct region should be accepted");
            return;
        }
        printer.setAutoStart(true);
        printer.requestStart(); // freshly armed regions need one explicit Start

        boolean[] sawOne = {false};
        boolean[] sawFifteen = {false};
        helper.succeedWhen(() -> {
            int now = comparator(helper, PRINTER_POS);
            if (printer.deconstructJob() != null) {
                if (now == 1) {
                    sawOne[0] = true;
                }
                if (now == 15) {
                    sawFifteen[0] = true;
                }
                if (now == 0) {
                    throw new GameTestAssertException("A running deconstruct must never read 0");
                }
            }
            helper.assertBlockNotPresent(Blocks.STONE, new BlockPos(1, 2, 1));
            helper.assertBlockNotPresent(Blocks.STONE, new BlockPos(3, 2, 3));
            if (printer.deconstructJob() != null) {
                throw new GameTestAssertException("waiting for the deconstruct to finish");
            }
            if (!sawOne[0]) {
                throw new GameTestAssertException("Comparator never read 1 at deconstruct start");
            }
            if (!sawFifteen[0]) {
                throw new GameTestAssertException("Comparator never reached 15 at deconstruct completion");
            }
            if (comparator(helper, PRINTER_POS) != 0) {
                throw new GameTestAssertException("Comparator must fall back to 0 once the region is done, got "
                        + comparator(helper, PRINTER_POS));
            }
        });
    }

    // --- multiblock scope ---

    @GameTest(template = "empty5", timeoutTicks = 300)
    public static void onlyTheControllerAnswersAComparator(GameTestHelper helper) {
        for (BlockPos offset : MultiblockPattern.componentOffsets(MachineTier.T5)) {
            helper.setBlock(PRINTER_POS.offset(offset), ModBlocks.PRINTER_CASING.get());
        }
        helper.setBlock(PRINTER_POS, ModBlocks.CONTROLLERS.get(0).get());
        if (!(helper.getBlockEntity(PRINTER_POS) instanceof PrinterBlockEntity fabricator)) {
            throw new GameTestAssertException("Controller block entity missing");
        }
        java.util.Optional.ofNullable(fabricator.getEnergyStorage()).ifPresent(energy -> {
            for (int i = 0; i < 60; i++) {
                energy.receiveEnergy(16_000, false);
            }
        });
        ItemStack spool = new ItemStack(ModItems.SPOOLS.get(0).get());
        SpoolItem.setFu(spool, 4_000);
        fabricator.spoolInventory().setStackInSlot(0, spool);

        BlockPos abs = helper.absolutePos(PRINTER_POS);
        BlockState state = helper.getLevel().getBlockState(abs);
        helper.getLevel().setBlock(abs, state.setValue(ControllerBlock.FORMED, true), Block.UPDATE_ALL);

        fabricator.setAutoStart(true);
        fabricator.inventory().setStackInSlot(PrinterBlockEntity.SLOT_TEMPLATE,
                new ItemStack(Items.STONE, 64));

        BlockPos casing = PRINTER_POS.offset(1, 0, 0);
        helper.succeedWhen(() -> {
            if (comparator(helper, PRINTER_POS) <= 0) {
                throw new GameTestAssertException("Waiting for the controller to report progress");
            }
            // Casings are not part of the comparator surface, same as the emitted signal.
            if (helper.getLevel().getBlockState(helper.absolutePos(casing)).hasAnalogOutputSignal()) {
                throw new GameTestAssertException("A casing must not advertise comparator output");
            }
            if (comparator(helper, casing) != 0) {
                throw new GameTestAssertException("A casing must read 0, got " + comparator(helper, casing));
            }
        });
    }

    @GameTest(template = "empty5", timeoutTicks = 200)
    public static void unformedControllerReadsZero(GameTestHelper helper) {
        helper.setBlock(PRINTER_POS, ModBlocks.CONTROLLERS.get(0).get());
        if (!(helper.getBlockEntity(PRINTER_POS) instanceof PrinterBlockEntity fabricator)) {
            throw new GameTestAssertException("Controller block entity missing");
        }
        java.util.Optional.ofNullable(fabricator.getEnergyStorage()).ifPresent(energy -> {
            for (int i = 0; i < 60; i++) {
                energy.receiveEnergy(16_000, false);
            }
        });
        PrinterGameTests.attachLoadedSpool(fabricator);
        fabricator.setAutoStart(true);
        fabricator.inventory().setStackInSlot(PrinterBlockEntity.SLOT_TEMPLATE,
                new ItemStack(Items.STONE, 64));

        helper.runAfterDelay(30, () -> {
            if (comparator(helper, PRINTER_POS) != 0) {
                helper.fail("An unformed controller must read 0, got " + comparator(helper, PRINTER_POS));
                return;
            }
            helper.succeed();
        });
    }
}
