package com.pgmacdesign.mc3dprint.gametest;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.fu.FuValueRegistry;
import com.pgmacdesign.mc3dprint.fu.SpoolItem;
import com.pgmacdesign.mc3dprint.machine.PrinterBlockEntity;
import com.pgmacdesign.mc3dprint.registry.ModBlocks;
import com.pgmacdesign.mc3dprint.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The Deconstruct fresh-arm safety gate: Auto NEVER starts the first job after a
 * region is armed — one explicit Start is required; after it, Auto resumes its
 * standing-recycler behavior for that region.
 */
@GameTestHolder(MC3DPrint.MOD_ID)
@PrefixGameTestTemplate(false)
public class DeconstructSafetyGameTests {

    private static PrinterBlockEntity poweredPrinter(GameTestHelper helper, BlockPos localPos) {
        helper.setBlock(localPos, ModBlocks.PRINTERS.get(2).get()); // T3
        if (!(helper.getBlockEntity(localPos) instanceof PrinterBlockEntity printer)) {
            throw new GameTestAssertException("Printer block entity missing");
        }
        java.util.Optional.ofNullable(printer.getEnergyStorage()).ifPresent(energy -> {
            for (int i = 0; i < 120; i++) {
                energy.receiveEnergy(1_000, false);
            }
        });
        return printer;
    }

    @GameTest(template = "empty5", timeoutTicks = 400)
    public static void armedRegionNeverAutoStartsUntilFirstManualStart(GameTestHelper helper) {
        var iron = FuValueRegistry.valueOf(new ItemStack(Items.IRON_BLOCK))
                .orElseThrow(() -> new GameTestAssertException("iron_block must be valued"));
        BlockPos target = new BlockPos(1, 2, 1);
        helper.setBlock(target, Blocks.IRON_BLOCK);

        PrinterBlockEntity printer = poweredPrinter(helper, new BlockPos(2, 1, 2));
        printer.setAutoStart(true); // the dangerous combination under test
        ItemStack spool = new ItemStack(ModItems.SPOOLS.get(iron.tier() - 1).get());
        SpoolItem.setFu(spool, 0);
        printer.spoolInventory().setStackInSlot(0, spool);
        printer.setDeconstructRegion(helper.absolutePos(target), helper.absolutePos(target));

        int yield = (int) Math.floor(iron.fu()
                * com.pgmacdesign.mc3dprint.config.MC3DPrintConfig.DECONSTRUCT_YIELD_FACTOR.get());

        // Phase 1: with Auto ON and a freshly armed region, NOTHING may happen.
        helper.runAfterDelay(80, () -> {
            helper.assertBlockPresent(Blocks.IRON_BLOCK, target);
            if (printer.deconstructJob() != null) {
                helper.fail("Auto must not start the first job after arming");
                return;
            }
            if (printer.state() != PrinterBlockEntity.State.READY) {
                helper.fail("expected READY while awaiting the first manual Start, got " + printer.state());
                return;
            }
            // Phase 2: one explicit Start disarms the gate and runs the job.
            printer.requestStart();
        });
        // Phase 3: first job done -> place a NEW block; Auto must eat it WITHOUT
        // another Start (standing-recycler behavior survives the gate).
        helper.runAfterDelay(200, () -> {
            helper.assertBlockPresent(Blocks.AIR, target);
            int banked = SpoolItem.getFu(printer.spoolInventory().getStackInSlot(0));
            if (banked != yield) {
                throw new GameTestAssertException("first job should credit " + yield + ", got " + banked);
            }
            helper.setBlock(target, Blocks.IRON_BLOCK);
        });
        helper.succeedWhen(() -> {
            int banked = SpoolItem.getFu(printer.spoolInventory().getStackInSlot(0));
            if (banked != 2 * yield) {
                throw new GameTestAssertException("auto-resume should credit a second " + yield
                        + " (total " + 2 * yield + "), got " + banked);
            }
            helper.assertBlockPresent(Blocks.AIR, target);
        });
    }

    @GameTest(template = "empty5", timeoutTicks = 60)
    public static void reArmingRegionReEnablesTheGate(GameTestHelper helper) {
        PrinterBlockEntity printer = poweredPrinter(helper, new BlockPos(2, 1, 2));
        printer.setAutoStart(true);
        BlockPos a = new BlockPos(1, 2, 1);
        printer.setDeconstructRegion(helper.absolutePos(a), helper.absolutePos(a));
        printer.requestStart(); // disarm once

        // handing over a NEW region must re-arm the manual-start gate
        BlockPos b = new BlockPos(3, 2, 3);
        helper.setBlock(b, Blocks.IRON_BLOCK);
        printer.setDeconstructRegion(helper.absolutePos(b), helper.absolutePos(b));
        helper.runAfterDelay(40, () -> {
            helper.assertBlockPresent(Blocks.IRON_BLOCK, b);
            if (printer.deconstructJob() != null) {
                helper.fail("re-armed region must wait for a fresh manual Start");
                return;
            }
            helper.succeed();
        });
    }
}
