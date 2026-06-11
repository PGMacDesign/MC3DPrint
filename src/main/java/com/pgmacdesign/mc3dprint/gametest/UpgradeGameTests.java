package com.pgmacdesign.mc3dprint.gametest;

import com.pgmacdesign.mc3dprint.MC3DPrint;
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
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(MC3DPrint.MOD_ID)
@PrefixGameTestTemplate(false)
public class UpgradeGameTests {

    private static PrinterBlockEntity poweredT4(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, ModBlocks.PRINTERS.get(3).get());
        if (!(helper.getBlockEntity(pos) instanceof PrinterBlockEntity printer)) {
            throw new GameTestAssertException("Printer block entity missing");
        }
        printer.getCapability(ForgeCapabilities.ENERGY).ifPresent(energy -> {
            for (int i = 0; i < 60; i++) {
                energy.receiveEnergy(8_000, false);
            }
        });
        return printer;
    }

    @GameTest(template = "empty5", timeoutTicks = 200)
    public static void efficiencyUpgradeReducesFuCost(GameTestHelper helper) {
        PrinterBlockEntity printer = poweredT4(helper);
        printer.installUpgrade(new ItemStack(ModItems.EFFICIENCY_UPGRADE.get()));

        // T4 spool: diamond cost is T4-denominated, so the drain is exact 1:1
        ItemStack spool = new ItemStack(ModItems.SPOOLS.get(3).get());
        SpoolItem.setFu(spool, 100);
        printer.spoolInventory().setStackInSlot(0, spool);
        printer.inventory().setStackInSlot(PrinterBlockEntity.SLOT_TEMPLATE, new ItemStack(Items.DIAMOND));

        // diamond 50 FU base, T4 efficiency 75% -> 66.67, ×0.9 with one module -> ceil(60.0) = 60
        helper.succeedWhen(() -> {
            if (printer.inventory().getStackInSlot(PrinterBlockEntity.SLOT_OUTPUT).isEmpty()) {
                throw new GameTestAssertException("No output yet");
            }
            int remaining = printer.totalFu();
            if (remaining != 40) {
                throw new GameTestAssertException("Expected 40 FU remaining with efficiency module, got " + remaining);
            }
        });
    }

    @GameTest(template = "empty5", timeoutTicks = 100)
    public static void bufferUpgradeGrowsCapacity(GameTestHelper helper) {
        PrinterBlockEntity printer = poweredT4(helper);
        int baseCapacity = printer.getCapability(ForgeCapabilities.ENERGY)
                .map(e -> e.getMaxEnergyStored()).orElse(0);

        printer.installUpgrade(new ItemStack(ModItems.BUFFER_UPGRADE.get()));
        printer.installUpgrade(new ItemStack(ModItems.BUFFER_UPGRADE.get()));

        int upgraded = printer.getCapability(ForgeCapabilities.ENERGY)
                .map(e -> e.getMaxEnergyStored()).orElse(0);
        long expected = Math.round(baseCapacity * 1.5 * 1.5);
        if (upgraded != expected) {
            helper.fail("Expected capacity " + expected + ", got " + upgraded);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty5", timeoutTicks = 100)
    public static void upgradeSlotsLimitedByTier(GameTestHelper helper) {
        // T1 has exactly one upgrade slot
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, ModBlocks.PRINTERS.get(0).get());
        if (!(helper.getBlockEntity(pos) instanceof PrinterBlockEntity t1)) {
            throw new GameTestAssertException("Printer block entity missing");
        }
        if (!t1.installUpgrade(new ItemStack(ModItems.SPEED_UPGRADE.get()))) {
            helper.fail("First upgrade should install on T1");
            return;
        }
        if (t1.installUpgrade(new ItemStack(ModItems.SPEED_UPGRADE.get()))) {
            helper.fail("Second upgrade must not fit in T1's single slot");
            return;
        }
        helper.succeed();
    }
}
