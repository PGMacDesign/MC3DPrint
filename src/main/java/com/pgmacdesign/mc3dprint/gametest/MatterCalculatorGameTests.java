package com.pgmacdesign.mc3dprint.gametest;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.blueprint.Blueprint;
import com.pgmacdesign.mc3dprint.blueprint.BlueprintBlockState;
import com.pgmacdesign.mc3dprint.blueprint.BlueprintFileStore;
import com.pgmacdesign.mc3dprint.fu.SpoolItem;
import com.pgmacdesign.mc3dprint.item.BlueprintDiscItem;
import com.pgmacdesign.mc3dprint.machine.PrinterBlockEntity;
import com.pgmacdesign.mc3dprint.registry.ModBlocks;
import com.pgmacdesign.mc3dprint.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * Matter Calculator acceptance: the pre-print report must equal what the job
 * actually consumes — same per-block primitives, so any drift is a real bug.
 */
@GameTestHolder(MC3DPrint.MOD_ID)
@PrefixGameTestTemplate(false)
public class MatterCalculatorGameTests {

    @GameTest(template = "empty5", timeoutTicks = 300)
    public static void predictedCostMatchesConsumed(GameTestHelper helper) {
        BlockPos printerPos = new BlockPos(2, 1, 2);
        helper.setBlock(printerPos, ModBlocks.PRINTERS.get(2).get()); // T3
        if (!(helper.getBlockEntity(printerPos) instanceof PrinterBlockEntity printer)) {
            throw new GameTestAssertException("Printer block entity missing");
        }
        java.util.Optional.ofNullable(printer.getEnergyStorage()).ifPresent(energy -> {
            for (int i = 0; i < 60; i++) {
                energy.receiveEnergy(1_000, false);
            }
        });

        Blueprint blueprint = Blueprint.builder("gametest-calc", 2, 1, 2)
                .set(0, 0, 0, BlueprintBlockState.parse("minecraft:stone"))
                .set(1, 0, 0, BlueprintBlockState.parse("minecraft:glass"))
                .set(1, 0, 1, BlueprintBlockState.parse("minecraft:stone"))
                .build();
        BlueprintFileStore store = BlueprintFileStore.forServer(helper.getLevel().getServer());
        UUID id = store.save(blueprint);
        ItemStack disc = new ItemStack(ModItems.BLUEPRINT_DISC.get());
        BlueprintDiscItem.writeBlueprint(disc, id, blueprint);
        printer.inventory().setStackInSlot(PrinterBlockEntity.SLOT_TEMPLATE, disc);

        // Read the prediction BEFORE starting (manual mode holds the job).
        int[] perTier = printer.costReportPerTier();
        if (perTier == null) {
            throw new GameTestAssertException("no cost report for a loaded disc");
        }
        int totalFu = 0;
        for (int c : perTier) {
            totalFu += c;
        }
        final int predictedFu = totalFu;
        if (predictedFu <= 0 || predictedFu != perTier[0]) {
            throw new GameTestAssertException("stone+glass build should be all-T1, got total "
                    + predictedFu + " with T1 " + perTier[0]);
        }
        final int predictedRf = printer.costReportRf();
        if (predictedRf <= 0 || printer.costReportEta() <= 0) {
            throw new GameTestAssertException("RF/ETA predictions must be positive");
        }

        ItemStack spool = new ItemStack(ModItems.SPOOLS.get(0).get());
        SpoolItem.setFu(spool, 10_000);
        printer.spoolInventory().setStackInSlot(0, spool);
        int energyBefore = printer.getEnergyStorage().getEnergyStored();

        printer.setAutoStart(true);
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(1, 2, 1));
            helper.assertBlockPresent(Blocks.GLASS, new BlockPos(2, 2, 1));
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(2, 2, 2));
            int consumedFu = 10_000 - SpoolItem.getFu(printer.spoolInventory().getStackInSlot(0));
            if (consumedFu != predictedFu) {
                throw new GameTestAssertException("consumed " + consumedFu
                        + " FU but calculator predicted " + predictedFu);
            }
            int consumedRf = energyBefore - printer.getEnergyStorage().getEnergyStored();
            if (consumedRf != predictedRf) {
                throw new GameTestAssertException("consumed " + consumedRf
                        + " RF but calculator predicted " + predictedRf);
            }
        });
    }

    @GameTest(template = "empty5", timeoutTicks = 60)
    public static void reportClearsWhenDiscRemoved(GameTestHelper helper) {
        BlockPos printerPos = new BlockPos(2, 1, 2);
        helper.setBlock(printerPos, ModBlocks.PRINTERS.get(2).get());
        if (!(helper.getBlockEntity(printerPos) instanceof PrinterBlockEntity printer)) {
            throw new GameTestAssertException("Printer block entity missing");
        }
        Blueprint blueprint = Blueprint.builder("gametest-calc-clear", 1, 1, 1)
                .set(0, 0, 0, BlueprintBlockState.parse("minecraft:stone"))
                .build();
        BlueprintFileStore store = BlueprintFileStore.forServer(helper.getLevel().getServer());
        ItemStack disc = new ItemStack(ModItems.BLUEPRINT_DISC.get());
        BlueprintDiscItem.writeBlueprint(disc, store.save(blueprint), blueprint);
        printer.inventory().setStackInSlot(PrinterBlockEntity.SLOT_TEMPLATE, disc);
        if (printer.costReportPerTier() == null) {
            throw new GameTestAssertException("expected a report with a disc loaded");
        }
        printer.inventory().setStackInSlot(PrinterBlockEntity.SLOT_TEMPLATE, ItemStack.EMPTY);
        if (printer.costReportPerTier() != null) {
            throw new GameTestAssertException("report must clear when the disc is removed");
        }
        helper.succeed();
    }
}
