package com.pgmacdesign.mc3dprint.gametest;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.blueprint.Blueprint;
import com.pgmacdesign.mc3dprint.blueprint.BlueprintBlockState;
import com.pgmacdesign.mc3dprint.blueprint.BlueprintFileStore;
import com.pgmacdesign.mc3dprint.item.BlueprintDiscItem;
import com.pgmacdesign.mc3dprint.machine.MachineTier;
import com.pgmacdesign.mc3dprint.machine.PrinterBlock;
import com.pgmacdesign.mc3dprint.machine.PrinterBlockEntity;
import com.pgmacdesign.mc3dprint.machine.multiblock.ControllerBlock;
import com.pgmacdesign.mc3dprint.machine.multiblock.MultiblockPattern;
import com.pgmacdesign.mc3dprint.machine.upgrade.UpgradeItem;
import com.pgmacdesign.mc3dprint.registry.ModBlocks;
import com.pgmacdesign.mc3dprint.fu.SpoolItem;
import com.pgmacdesign.mc3dprint.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * Redstone Module: the machine emits weak power 15 on all six faces while it is
 * actively printing or deconstructing, and 0 otherwise.
 *
 * <p>These cover the invariants that are easy to break silently: the signal must
 * drop on a pause (not just on completion), a machine without the module must not
 * be a signal source at all, only the controller of a multiblock emits, and, the
 * big one, a machine that both consumes and emits redstone must never feed its own
 * output back into its rising-edge start trigger.
 */
@GameTestHolder(MC3DPrint.MOD_ID)
@PrefixGameTestTemplate(false)
public class RedstoneModuleGameTests {

    private static final BlockPos PRINTER_POS = new BlockPos(2, 1, 2);

    // --- helpers ---

    /** The block's own emitted signal, bypassing any unrelated direct power at the position. */
    private static int signalAt(GameTestHelper helper, BlockPos local, Direction side) {
        BlockPos abs = helper.absolutePos(local);
        BlockState state = helper.getLevel().getBlockState(abs);
        return state.getSignal(helper.getLevel(), abs, side);
    }

    /** Asserts the same signal on all six faces (the module is a weak all-sides emitter). */
    private static void assertSignal(GameTestHelper helper, BlockPos local, int expected, String when) {
        for (Direction side : Direction.values()) {
            int actual = signalAt(helper, local, side);
            if (actual != expected) {
                throw new GameTestAssertException("Expected signal " + expected + " " + when
                        + " but face " + side + " read " + actual);
            }
        }
    }

    private static PrinterBlockEntity poweredT3(GameTestHelper helper, BlockPos local) {
        helper.setBlock(local, ModBlocks.PRINTERS.get(2).get()); // T3: first tier with a print area
        if (!(helper.getBlockEntity(local) instanceof PrinterBlockEntity printer)) {
            throw new GameTestAssertException("Printer block entity missing");
        }
        printer.getCapability(ForgeCapabilities.ENERGY).ifPresent(energy -> {
            for (int i = 0; i < 60; i++) {
                energy.receiveEnergy(4_000, false);
            }
        });
        PrinterGameTests.attachLoadedSpool(printer);
        return printer;
    }

    private static void installRedstoneModule(GameTestHelper helper, PrinterBlockEntity printer) {
        if (!printer.installUpgrade(new ItemStack(ModItems.REDSTONE_UPGRADE.get()))) {
            throw new GameTestAssertException("Redstone Module should install");
        }
    }

    /** 2x1x2: stone at (0,0,0), glass at (1,0,1). Two placements, a short print. */
    private static Blueprint smallBlueprint() {
        return Blueprint.builder("gametest-redstone", 2, 1, 2)
                .set(0, 0, 0, BlueprintBlockState.parse("minecraft:stone"))
                .set(1, 0, 1, BlueprintBlockState.parse("minecraft:glass"))
                .build();
    }

    /** 3x1x3 solid stone: nine placements, long enough to stall and resume mid-job. */
    private static Blueprint slabBlueprint() {
        Blueprint.Builder builder = Blueprint.builder("gametest-redstone-slab", 3, 1, 3);
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

    // --- the signal follows the work ---

    @GameTest(template = "empty5", timeoutTicks = 300)
    public static void signalHighWhilePrintingAndLowOnBothTransitions(GameTestHelper helper) {
        PrinterBlockEntity printer = poweredT3(helper, PRINTER_POS);
        installRedstoneModule(helper, printer);
        assertSignal(helper, PRINTER_POS, 0, "before the print starts");

        printer.setAutoStart(true);
        printer.inventory().setStackInSlot(PrinterBlockEntity.SLOT_TEMPLATE,
                discFor(helper, smallBlueprint()));

        boolean[] sawHigh = {false};
        helper.succeedWhen(() -> {
            // Record the rising half BEFORE any assertion can bail out of this poll.
            if (signalAt(helper, PRINTER_POS, Direction.NORTH) == 15) {
                sawHigh[0] = true;
            }
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(1, 2, 1));
            helper.assertBlockPresent(Blocks.GLASS, new BlockPos(2, 2, 2));
            if (!sawHigh[0]) {
                throw new GameTestAssertException("Signal never went high during the print");
            }
            if (printer.state() != PrinterBlockEntity.State.IDLE) {
                throw new GameTestAssertException("Waiting for IDLE, got " + printer.state());
            }
            assertSignal(helper, PRINTER_POS, 0, "after the print finished");
        });
    }

    /**
     * Auto-repeat continuity. A machine on Auto chewing through a stack finishes one
     * item and starts the next without ever leaving PRINTING, so the signal must stay
     * solid rather than blinking once per item. A blink would be invisible to a human
     * but would retrigger anything edge-driven wired to it, so this samples EVERY tick
     * across three completed items instead of spot-checking.
     */
    @GameTest(template = "empty5", timeoutTicks = 600)
    public static void signalStaysHighAcrossRepeatedItemPrints(GameTestHelper helper) {
        PrinterBlockEntity printer = poweredT3(helper, PRINTER_POS);
        installRedstoneModule(helper, printer);
        ItemStack spool = new ItemStack(ModItems.SPOOLS.get(0).get());
        SpoolItem.setFu(spool, 8_000); // deep enough that FU never becomes the limit
        printer.spoolInventory().setStackInSlot(0, spool);
        printer.setAutoStart(true);
        printer.inventory().setStackInSlot(PrinterBlockEntity.SLOT_TEMPLATE,
                new ItemStack(Items.STONE, 64));

        int[] phase = {0};
        boolean[] sawHigh = {false};
        int[] dipAfterItems = {-1};

        helper.succeedWhen(() -> {
            int signal = signalAt(helper, PRINTER_POS, Direction.NORTH);
            int produced = printer.inventory().getStackInSlot(PrinterBlockEntity.SLOT_OUTPUT).getCount();

            if (phase[0] == 0) {
                if (signal == 15) {
                    sawHigh[0] = true;
                }
                // Once it is up, it must stay up for the whole run of items.
                if (sawHigh[0] && signal != 15 && dipAfterItems[0] < 0) {
                    dipAfterItems[0] = produced;
                }
                if (produced < 3) {
                    throw new GameTestAssertException("waiting for 3 items, have " + produced);
                }
                if (dipAfterItems[0] >= 0) {
                    throw new GameTestAssertException("Signal dipped off 15 between items (after item "
                            + dipAfterItems[0] + "); auto-repeat must not blink at the item boundary");
                }
                // Now prove it does still fall when the run genuinely stops.
                printer.inventory().setStackInSlot(PrinterBlockEntity.SLOT_TEMPLATE, ItemStack.EMPTY);
                phase[0] = 1;
                throw new GameTestAssertException("three items done with no dip; waiting for the signal to fall");
            }

            if (printer.state() != PrinterBlockEntity.State.IDLE) {
                throw new GameTestAssertException("Waiting for IDLE, got " + printer.state());
            }
            assertSignal(helper, PRINTER_POS, 0, "once the auto-repeat run stops");
        });
    }

    @GameTest(template = "empty5", timeoutTicks = 300)
    public static void noSignalWithoutTheModule(GameTestHelper helper) {
        PrinterBlockEntity printer = poweredT3(helper, PRINTER_POS);
        // deliberately no module installed
        printer.setAutoStart(true);
        printer.inventory().setStackInSlot(PrinterBlockEntity.SLOT_TEMPLATE,
                discFor(helper, smallBlueprint()));

        boolean[] sawPrinting = {false};
        helper.succeedWhen(() -> {
            if (printer.state() == PrinterBlockEntity.State.PRINTING) {
                sawPrinting[0] = true;
            }
            // A machine without the module must not even be a signal source, so adjacent
            // dust never connects to it and existing worlds behave exactly as before.
            BlockPos abs = helper.absolutePos(PRINTER_POS);
            if (helper.getLevel().getBlockState(abs).isSignalSource()) {
                throw new GameTestAssertException("A printer without a Redstone Module must not be a signal source");
            }
            assertSignal(helper, PRINTER_POS, 0, "on a printer with no Redstone Module");
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(1, 2, 1));
            helper.assertBlockPresent(Blocks.GLASS, new BlockPos(2, 2, 2));
            if (!sawPrinting[0]) {
                throw new GameTestAssertException("The print never ran, so this test proved nothing");
            }
        });
    }

    @GameTest(template = "empty5", timeoutTicks = 300)
    public static void signalDropsWhilePausedAndReturnsWhenTheStallClears(GameTestHelper helper) {
        PrinterBlockEntity printer = poweredT3(helper, PRINTER_POS);
        installRedstoneModule(helper, printer);
        printer.setAutoStart(true);
        printer.inventory().setStackInSlot(PrinterBlockEntity.SLOT_TEMPLATE,
                discFor(helper, slabBlueprint()));

        helper.runAfterDelay(20, () -> {
            if (printer.activeJob() == null) {
                helper.fail("The job should be running by tick 20");
                return;
            }
            assertSignal(helper, PRINTER_POS, 15, "mid-print");
            // No getEnergyStorage() on 1.20.1: reach the storage through the capability.
            printer.getCapability(ForgeCapabilities.ENERGY).ifPresent(energy -> {
                if (energy instanceof com.pgmacdesign.mc3dprint.machine.MachineEnergyStorage machine) {
                    machine.setStored(0);
                }
            }); // stall it
        });
        helper.runAfterDelay(50, () -> {
            if (printer.activeJob() == null) {
                helper.fail("The job must survive an RF stall, not be cancelled");
                return;
            }
            if (printer.state() != PrinterBlockEntity.State.PAUSED_NO_POWER) {
                helper.fail("Expected PAUSED_NO_POWER, got " + printer.state());
                return;
            }
            // The whole point: paused reads 0 even though a job is still loaded, so a
            // stall shows up as the signal dropping.
            assertSignal(helper, PRINTER_POS, 0, "while paused for power with a live job");
            printer.getCapability(ForgeCapabilities.ENERGY).ifPresent(energy -> {
                for (int i = 0; i < 60; i++) {
                    energy.receiveEnergy(4_000, false);
                }
            });
        });
        helper.runAfterDelay(80, () -> {
            assertSignal(helper, PRINTER_POS, 15, "after the stall cleared");
            helper.succeed();
        });
    }

    // --- the feedback loop ---

    /**
     * Places redstone dust horizontally adjacent to the printer, on its own stone
     * support. Once the machine emits, this dust powers up and feeds signal straight
     * back into the machine, which is the loop these tests exist to pin down.
     */
    private static final BlockPos DUST_POS = new BlockPos(2, 1, 1);

    private static void placeAdjacentDust(GameTestHelper helper) {
        helper.setBlock(DUST_POS.below(), Blocks.STONE);
        helper.setBlock(DUST_POS, Blocks.REDSTONE_WIRE);
    }

    private static int dustPower(GameTestHelper helper) {
        BlockState state = helper.getLevel().getBlockState(helper.absolutePos(DUST_POS));
        return state.is(Blocks.REDSTONE_WIRE) ? state.getValue(net.minecraft.world.level.block.RedStoneWireBlock.POWER) : -1;
    }

    @GameTest(template = "empty5", timeoutTicks = 300)
    public static void adjacentDustReadsFullPowerWhileRunning(GameTestHelper helper) {
        PrinterBlockEntity printer = poweredT3(helper, PRINTER_POS);
        installRedstoneModule(helper, printer);
        placeAdjacentDust(helper);
        printer.setAutoStart(true);
        printer.inventory().setStackInSlot(PrinterBlockEntity.SLOT_TEMPLATE,
                discFor(helper, smallBlueprint()));

        boolean[] sawPoweredDust = {false};
        helper.succeedWhen(() -> {
            if (dustPower(helper) == 15) {
                sawPoweredDust[0] = true;
            }
            if (printer.state() != PrinterBlockEntity.State.IDLE) {
                throw new GameTestAssertException("Waiting for IDLE, got " + printer.state());
            }
            if (!sawPoweredDust[0]) {
                throw new GameTestAssertException("Adjacent dust never reached power 15 during the print");
            }
            if (dustPower(helper) != 0) {
                throw new GameTestAssertException("Adjacent dust should fall back to 0 once the machine stops, got "
                        + dustPower(helper));
            }
        });
    }

    /**
     * The self-trigger guard. A machine start is a redstone RISING EDGE, so a machine
     * that emits redstone next to dust sees its own output as a fresh trigger. Left
     * alone, that sets the pending-start flag mid-job, the flag outlives the job, and
     * the next disc prints itself with no trigger, which silently turns a manual-mode
     * printer into an automatic one (and, in Deconstruct, re-runs the same region
     * forever, re-claiming zones and re-forcing chunks each cycle).
     */
    @GameTest(template = "empty5", timeoutTicks = 400)
    public static void emittingMachineNeverSelfTriggersAStart(GameTestHelper helper) {
        PrinterBlockEntity printer = poweredT3(helper, PRINTER_POS);
        installRedstoneModule(helper, printer);
        placeAdjacentDust(helper);
        printer.setAutoStart(false); // manual mode: nothing may start without an explicit trigger
        printer.inventory().setStackInSlot(PrinterBlockEntity.SLOT_TEMPLATE,
                discFor(helper, smallBlueprint()));
        printer.requestStart(); // the one and only trigger this test ever fires

        helper.runAfterDelay(15, () -> {
            // The feedback path has to be genuinely live, or the rest proves nothing:
            // the machine is running, so its own output is powering the dust beside it,
            // which is feeding signal straight back into its start trigger.
            if (printer.activeJob() == null) {
                helper.fail("The print should be running at tick 15");
                return;
            }
            if (dustPower(helper) != 15) {
                helper.fail("Adjacent dust should be at 15 while the machine runs, got " + dustPower(helper)
                        + "; without that the self-trigger path is never exercised");
            }
        });
        helper.runAfterDelay(60, () -> {
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(1, 2, 1));
            helper.assertBlockPresent(Blocks.GLASS, new BlockPos(2, 2, 2));
            if (printer.activeJob() != null) {
                helper.fail("The first print should be finished by tick 60");
                return;
            }
            // Clear the printed blocks and hand it a second disc. In manual mode nothing
            // may happen: if the machine banked a start from its own emission, this disc
            // prints itself.
            helper.setBlock(new BlockPos(1, 2, 1), Blocks.AIR);
            helper.setBlock(new BlockPos(2, 2, 2), Blocks.AIR);
            printer.inventory().setStackInSlot(PrinterBlockEntity.SLOT_OUTPUT, ItemStack.EMPTY);
            printer.inventory().setStackInSlot(PrinterBlockEntity.SLOT_TEMPLATE,
                    discFor(helper, smallBlueprint()));
        });
        helper.runAfterDelay(150, () -> {
            if (printer.activeJob() != null) {
                helper.fail("A second job started with no trigger: the machine self-triggered off its own signal");
                return;
            }
            helper.assertBlockNotPresent(Blocks.STONE, new BlockPos(1, 2, 1));
            if (printer.state() != PrinterBlockEntity.State.READY) {
                helper.fail("Expected READY while awaiting a trigger, got " + printer.state());
                return;
            }
            assertSignal(helper, PRINTER_POS, 0, "on an idle machine after the loop test");
            helper.succeed();
        });
    }

    // --- persistence ---

    @GameTest(template = "empty5", timeoutTicks = 300)
    public static void moduleAndEmissionSurviveASaveLoadRoundTrip(GameTestHelper helper) {
        PrinterBlockEntity printer = poweredT3(helper, PRINTER_POS);
        installRedstoneModule(helper, printer);
        printer.setAutoStart(true);
        printer.inventory().setStackInSlot(PrinterBlockEntity.SLOT_TEMPLATE,
                discFor(helper, slabBlueprint()));

        helper.runAfterDelay(20, () -> {
            if (!printer.shouldEmitRedstone()) {
                helper.fail("Expected to be emitting mid-print, state=" + printer.state());
                return;
            }
            assertSignal(helper, PRINTER_POS, 15, "mid-print, before the round trip");

            BlockPos abs = helper.absolutePos(PRINTER_POS);
            BlockState worldState = helper.getLevel().getBlockState(abs);
            // Full metadata, not saveWithoutMetadata: loadStatic needs the "id" field to
            // know which block entity type to rebuild.
            CompoundTag saved = printer.saveWithFullMetadata();
            BlockEntity reloaded = BlockEntity.loadStatic(abs, worldState, saved);
            if (!(reloaded instanceof PrinterBlockEntity restored)) {
                helper.fail("Printer block entity did not survive the NBT round trip");
                return;
            }
            if (restored.upgradeCount(UpgradeItem.Type.REDSTONE) != 1) {
                helper.fail("Redstone Module lost across save/load, count="
                        + restored.upgradeCount(UpgradeItem.Type.REDSTONE));
                return;
            }
            if (restored.state() != PrinterBlockEntity.State.PRINTING) {
                helper.fail("Machine state lost across save/load, got " + restored.state());
                return;
            }
            // The signal is derived, never separately persisted: the reloaded machine
            // re-derives the same answer from the same persisted inputs.
            if (!restored.shouldEmitRedstone()) {
                helper.fail("Reloaded machine should still be emitting");
                return;
            }
            if (!worldState.getValue(PrinterBlock.EMITTING)) {
                helper.fail("The emitting flag should live in the saved block state");
                return;
            }
            helper.succeed();
        });
    }

    // --- upgrade stacking ---

    @GameTest(template = "empty5", timeoutTicks = 100)
    public static void redstoneModuleCapsAtOnePerMachine(GameTestHelper helper) {
        // T5 has five upgrade slots, so a rejected second module cannot be a slot shortage.
        helper.setBlock(PRINTER_POS, ModBlocks.CONTROLLERS.get(0).get());
        if (!(helper.getBlockEntity(PRINTER_POS) instanceof PrinterBlockEntity t5)) {
            throw new GameTestAssertException("Controller block entity missing");
        }
        if (!t5.installUpgrade(new ItemStack(ModItems.REDSTONE_UPGRADE.get()))) {
            helper.fail("The first Redstone Module should install on T5");
            return;
        }
        if (t5.installUpgrade(new ItemStack(ModItems.REDSTONE_UPGRADE.get()))) {
            helper.fail("A second Redstone Module must be rejected even with four free slots");
            return;
        }
        if (!t5.upgradeTypeAtCap(UpgradeItem.Type.REDSTONE)) {
            helper.fail("One Redstone Module must report the type as at cap");
            return;
        }
        // The other four keep their own cap, so the free slots still take them.
        if (!t5.installUpgrade(new ItemStack(ModItems.SPEED_UPGRADE.get()))) {
            helper.fail("A different module type should still fit a free slot");
            return;
        }
        if (t5.upgradeTypeAtCap(UpgradeItem.Type.SPEED)) {
            helper.fail("One Speed module must not report the type as at cap");
            return;
        }
        helper.succeed();
    }

    // --- multiblock scope ---

    private static PrinterBlockEntity buildAndFormT5(GameTestHelper helper) {
        for (BlockPos offset : MultiblockPattern.componentOffsets(MachineTier.T5)) {
            helper.setBlock(PRINTER_POS.offset(offset), ModBlocks.PRINTER_CASING.get());
        }
        helper.setBlock(PRINTER_POS, ModBlocks.CONTROLLERS.get(0).get());
        if (!(helper.getBlockEntity(PRINTER_POS) instanceof PrinterBlockEntity printer)) {
            throw new GameTestAssertException("Controller block entity missing");
        }
        printer.getCapability(ForgeCapabilities.ENERGY).ifPresent(energy -> {
            for (int i = 0; i < 60; i++) {
                energy.receiveEnergy(16_000, false);
            }
        });
        PrinterGameTests.attachLoadedSpool(printer);
        BlockPos abs = helper.absolutePos(PRINTER_POS);
        if (MultiblockPattern.validate(helper.getLevel(), abs, MachineTier.T5) != null) {
            throw new GameTestAssertException("T5 pattern should validate");
        }
        BlockState state = helper.getLevel().getBlockState(abs);
        helper.getLevel().setBlock(abs, state.setValue(ControllerBlock.FORMED, true), Block.UPDATE_ALL);
        return printer;
    }

    @GameTest(template = "empty5", timeoutTicks = 300)
    public static void onlyTheControllerEmitsOnAFabricator(GameTestHelper helper) {
        PrinterBlockEntity fabricator = buildAndFormT5(helper);
        installRedstoneModule(helper, fabricator);
        fabricator.setAutoStart(true);
        // Item Mode keeps the machine PRINTING continuously, which gives a stable window.
        fabricator.inventory().setStackInSlot(PrinterBlockEntity.SLOT_TEMPLATE,
                new ItemStack(Items.STONE, 64));

        BlockPos casing = PRINTER_POS.offset(1, 0, 0);
        helper.succeedWhen(() -> {
            if (fabricator.state() != PrinterBlockEntity.State.PRINTING) {
                throw new GameTestAssertException("Waiting for PRINTING, got " + fabricator.state());
            }
            assertSignal(helper, PRINTER_POS, 15, "on a running fabricator controller");
            // A T8 footprint is 51x51: a casing-wide emitter would make every transition
            // a ~2600-block neighbor storm, so the machine speaks only through its controller.
            assertSignal(helper, casing, 0, "on a fabricator casing");
            if (helper.getLevel().getBlockState(helper.absolutePos(casing)).isSignalSource()) {
                throw new GameTestAssertException("A casing must never be a signal source");
            }
        });
    }

    @GameTest(template = "empty5", timeoutTicks = 300)
    public static void unformingClearsTheSignal(GameTestHelper helper) {
        PrinterBlockEntity fabricator = buildAndFormT5(helper);
        installRedstoneModule(helper, fabricator);
        fabricator.setAutoStart(true);
        fabricator.inventory().setStackInSlot(PrinterBlockEntity.SLOT_TEMPLATE,
                new ItemStack(Items.STONE, 64));

        helper.runAfterDelay(20, () -> {
            assertSignal(helper, PRINTER_POS, 15, "on a running fabricator");
            // Breaking a casing unforms the machine in place.
            helper.setBlock(PRINTER_POS.offset(1, 0, 0), Blocks.AIR);
            BlockState controller = helper.getLevel().getBlockState(helper.absolutePos(PRINTER_POS));
            if (controller.getValue(ControllerBlock.FORMED)) {
                helper.fail("Breaking a casing should have unformed the machine");
                return;
            }
            // Same tick as the unform: no window where an unformed machine still powers dust.
            assertSignal(helper, PRINTER_POS, 0, "the instant the machine unformed");
        });
        helper.runAfterDelay(40, () -> {
            assertSignal(helper, PRINTER_POS, 0, "on an unformed fabricator");
            helper.succeed();
        });
    }
}
