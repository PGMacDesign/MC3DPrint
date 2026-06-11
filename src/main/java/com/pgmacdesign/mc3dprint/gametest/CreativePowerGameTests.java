package com.pgmacdesign.mc3dprint.gametest;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.fu.SpoolItem;
import com.pgmacdesign.mc3dprint.machine.ClockGeneratorBlockEntity;
import com.pgmacdesign.mc3dprint.machine.PrinterBlockEntity;
import com.pgmacdesign.mc3dprint.registry.ModBlocks;
import com.pgmacdesign.mc3dprint.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(MC3DPrint.MOD_ID)
@PrefixGameTestTemplate(false)
public class CreativePowerGameTests {

    @GameTest(template = "empty5", timeoutTicks = 100)
    public static void creativeSourceFillsAdjacentPrinter(GameTestHelper helper) {
        BlockPos printerPos = new BlockPos(2, 1, 2);
        helper.setBlock(printerPos, ModBlocks.TIER1_PRINTER.get());
        helper.setBlock(printerPos.east(), ModBlocks.CREATIVE_ENERGY_SOURCE.get());
        if (!(helper.getBlockEntity(printerPos) instanceof PrinterBlockEntity printer)) {
            throw new GameTestAssertException("Printer block entity missing");
        }

        helper.succeedWhen(() -> printer.getCapability(ForgeCapabilities.ENERGY).ifPresent(energy -> {
            if (energy.getEnergyStored() < energy.getMaxEnergyStored()) {
                throw new GameTestAssertException("Printer buffer not filled by creative source: "
                        + energy.getEnergyStored() + "/" + energy.getMaxEnergyStored());
            }
        }));
    }

    @GameTest(template = "empty5", timeoutTicks = 100)
    public static void clockGeneratorBurnsFuelIntoPrinter(GameTestHelper helper) {
        BlockPos printerPos = new BlockPos(2, 1, 2);
        helper.setBlock(printerPos, ModBlocks.TIER1_PRINTER.get());
        helper.setBlock(printerPos.east(), ModBlocks.CLOCK_GENERATOR.get());
        if (!(helper.getBlockEntity(printerPos) instanceof PrinterBlockEntity printer)) {
            throw new GameTestAssertException("Printer block entity missing");
        }
        if (!(helper.getBlockEntity(printerPos.east()) instanceof ClockGeneratorBlockEntity generator)) {
            throw new GameTestAssertException("Clock generator block entity missing");
        }
        if (generator.addFuel(new ItemStack(Items.COAL)) <= 0) {
            throw new GameTestAssertException("Generator rejected coal as fuel");
        }

        // 40 ticks at the default 10 RF/t should land ~400 RF in the printer
        helper.runAfterDelay(40, () -> printer.getCapability(ForgeCapabilities.ENERGY).ifPresent(energy -> {
            if (energy.getEnergyStored() < 200) {
                helper.fail("Clock generator delivered too little RF: " + energy.getEnergyStored());
            } else {
                helper.succeed();
            }
        }));
    }

    @GameTest(template = "empty5", timeoutTicks = 100)
    public static void clockGeneratorIsDeadWithoutFuel(GameTestHelper helper) {
        BlockPos printerPos = new BlockPos(2, 1, 2);
        helper.setBlock(printerPos, ModBlocks.TIER1_PRINTER.get());
        helper.setBlock(printerPos.east(), ModBlocks.CLOCK_GENERATOR.get());
        if (!(helper.getBlockEntity(printerPos) instanceof PrinterBlockEntity printer)) {
            throw new GameTestAssertException("Printer block entity missing");
        }

        helper.runAfterDelay(60, () -> printer.getCapability(ForgeCapabilities.ENERGY).ifPresent(energy -> {
            if (energy.getEnergyStored() > 0) {
                helper.fail("Generator produced RF without fuel: " + energy.getEnergyStored());
            } else {
                helper.succeed();
            }
        }));
    }

    @GameTest(template = "empty5", timeoutTicks = 100)
    public static void creativeSpoolNeverDepletes(GameTestHelper helper) {
        ItemStack spool = new ItemStack(ModItems.CREATIVE_SPOOL.get());
        int capacity = SpoolItem.CAPACITY_BY_TIER[7];

        if (SpoolItem.getFu(spool) != capacity) {
            helper.fail("Creative spool should report full: " + SpoolItem.getFu(spool));
            return;
        }
        if (SpoolItem.drain(spool, 12_345) != 12_345) {
            helper.fail("Creative spool must satisfy any drain request");
            return;
        }
        if (SpoolItem.getFu(spool) != capacity) {
            helper.fail("Creative spool depleted after drain: " + SpoolItem.getFu(spool));
            return;
        }
        if (SpoolItem.fill(spool, 100) != 0) {
            helper.fail("Creative spool must report no room (winders should skip it)");
            return;
        }
        helper.succeed();
    }

    /**
     * Regression: vanilla syncs container data as 16-bit shorts. Large RF/FU
     * values must survive the split-and-recombine round trip (the GUI showed
     * 0 RF / 0 FU before SplitContainerData).
     */
    @GameTest(template = "empty5", timeoutTicks = 100)
    public static void containerDataSurvivesShortSync(GameTestHelper helper) {
        BlockPos printerPos = new BlockPos(2, 1, 2);
        helper.setBlock(printerPos, ModBlocks.PRINTERS.get(2).get()); // T3: 250k RF buffer
        if (!(helper.getBlockEntity(printerPos) instanceof PrinterBlockEntity printer)) {
            throw new GameTestAssertException("Printer block entity missing");
        }
        printer.getCapability(ForgeCapabilities.ENERGY).ifPresent(energy -> {
            while (energy.receiveEnergy(Integer.MAX_VALUE, true) > 0) {
                energy.receiveEnergy(Integer.MAX_VALUE, false);
            }
        });
        for (int i = 0; i < printer.spoolInventory().getSlots(); i++) {
            printer.spoolInventory().setStackInSlot(i, new ItemStack(ModItems.CREATIVE_SPOOL.get()));
        }

        var live = printer.containerData();
        var synced = new net.minecraft.world.inventory.SimpleContainerData(live.getCount());
        for (int i = 0; i < live.getCount(); i++) {
            synced.set(i, (short) live.get(i)); // emulate ClientboundContainerSetDataPacket truncation
        }

        // expected = what the live (untruncated) data reports, recombined
        int expectedEnergy = com.pgmacdesign.mc3dprint.machine.SplitContainerData.combine(
                live, PrinterBlockEntity.DATA_ENERGY);
        int expectedFuCap = com.pgmacdesign.mc3dprint.machine.SplitContainerData.combine(
                live, PrinterBlockEntity.DATA_FU_CAP);
        int syncedEnergy = com.pgmacdesign.mc3dprint.machine.SplitContainerData.combine(
                synced, PrinterBlockEntity.DATA_ENERGY);
        int syncedFuCap = com.pgmacdesign.mc3dprint.machine.SplitContainerData.combine(
                synced, PrinterBlockEntity.DATA_FU_CAP);

        if (expectedEnergy < 100_000) {
            helper.fail("Test premise broken: expected a large RF buffer, got " + expectedEnergy);
        } else if (syncedEnergy != expectedEnergy) {
            helper.fail("Energy corrupted by short sync: " + syncedEnergy + " != " + expectedEnergy);
        } else if (syncedFuCap != expectedFuCap || expectedFuCap < 100_000) {
            helper.fail("FU capacity corrupted by short sync: " + syncedFuCap + " != " + expectedFuCap);
        } else {
            helper.succeed();
        }
    }

    @GameTest(template = "empty5", timeoutTicks = 200)
    public static void creativeSpoolPrintsItems(GameTestHelper helper) {
        BlockPos printerPos = new BlockPos(2, 1, 2);
        helper.setBlock(printerPos, ModBlocks.TIER1_PRINTER.get());
        helper.setBlock(printerPos.east(), ModBlocks.CREATIVE_ENERGY_SOURCE.get());
        if (!(helper.getBlockEntity(printerPos) instanceof PrinterBlockEntity printer)) {
            throw new GameTestAssertException("Printer block entity missing");
        }
        printer.spoolInventory().setStackInSlot(0, new ItemStack(ModItems.CREATIVE_SPOOL.get()));
        printer.inventory().setStackInSlot(PrinterBlockEntity.SLOT_TEMPLATE, new ItemStack(Items.STONE));

        helper.succeedWhen(() -> {
            ItemStack output = printer.inventory().getStackInSlot(PrinterBlockEntity.SLOT_OUTPUT);
            if (output.isEmpty() || !output.is(Items.STONE)) {
                throw new GameTestAssertException("No printed copy from creative spool yet");
            }
        });
    }
}
