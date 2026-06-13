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
        printer.setAutoStart(true); // item-mode now gates on Auto; tests want continuous print
        return printer;
    }

    @GameTest(template = "empty5", timeoutTicks = 200)
    public static void efficiencyUpgradeReducesFuCost(GameTestHelper helper) {
        PrinterBlockEntity printer = poweredT4(helper);
        printer.installUpgrade(new ItemStack(ModItems.EFFICIENCY_UPGRADE.get()));

        // T4 spool: emerald cost is T4-denominated, so the drain is exact 1:1
        ItemStack spool = new ItemStack(ModItems.SPOOLS.get(3).get());
        SpoolItem.setFu(spool, 100);
        printer.spoolInventory().setStackInSlot(0, spool);
        printer.inventory().setStackInSlot(PrinterBlockEntity.SLOT_TEMPLATE, new ItemStack(Items.EMERALD));

        // emerald 50 FU base; T4 innate markup = 1/0.75 − 1 = 0.333; one of four modules
        // removes a quarter -> markup 0.25 -> cost ceil(62.5) = 63; 100 − 63 = 37 left
        helper.succeedWhen(() -> {
            if (printer.inventory().getStackInSlot(PrinterBlockEntity.SLOT_OUTPUT).isEmpty()) {
                throw new GameTestAssertException("No output yet");
            }
            int remaining = printer.totalFu();
            if (remaining != 37) {
                throw new GameTestAssertException("Expected 37 FU remaining with one efficiency module, got " + remaining);
            }
        });
    }

    @GameTest(template = "empty5", timeoutTicks = 200)
    public static void fourEfficiencyModulesBreakEven(GameTestHelper helper) {
        PrinterBlockEntity printer = poweredT4(helper);
        // T4 has exactly four upgrade slots — fill them all with Efficiency
        for (int i = 0; i < 4; i++) {
            if (!printer.installUpgrade(new ItemStack(ModItems.EFFICIENCY_UPGRADE.get()))) {
                helper.fail("Efficiency module " + (i + 1) + " should install on T4");
                return;
            }
        }
        // T4 spool: emerald cost is T4-denominated, so the drain is exact 1:1
        ItemStack spool = new ItemStack(ModItems.SPOOLS.get(3).get());
        SpoolItem.setFu(spool, 100);
        printer.spoolInventory().setStackInSlot(0, spool);
        printer.inventory().setStackInSlot(PrinterBlockEntity.SLOT_TEMPLATE, new ItemStack(Items.EMERALD));

        // four (= maxPerType) Efficiency modules -> break-even: emerald costs its base 50 FU
        helper.succeedWhen(() -> {
            if (printer.inventory().getStackInSlot(PrinterBlockEntity.SLOT_OUTPUT).isEmpty()) {
                throw new GameTestAssertException("No output yet");
            }
            int remaining = printer.totalFu();
            if (remaining != 50) {
                throw new GameTestAssertException("Expected exactly 50 FU (1:1 break-even) remaining, got " + remaining);
            }
        });
    }

    @GameTest(template = "empty5", timeoutTicks = 100)
    public static void efficiencyCapBlocksFifthOfAType(GameTestHelper helper) {
        // T5 has five upgrade slots, but no more than four of any one type may go in
        // (CONTROLLERS holds the multiblock tiers T5-T8; index 0 = T5)
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, ModBlocks.CONTROLLERS.get(0).get());
        if (!(helper.getBlockEntity(pos) instanceof PrinterBlockEntity t5)) {
            throw new GameTestAssertException("Printer block entity missing");
        }
        for (int i = 0; i < 4; i++) {
            if (!t5.installUpgrade(new ItemStack(ModItems.EFFICIENCY_UPGRADE.get()))) {
                helper.fail("Efficiency module " + (i + 1) + " should install on T5");
                return;
            }
        }
        if (t5.installUpgrade(new ItemStack(ModItems.EFFICIENCY_UPGRADE.get()))) {
            helper.fail("A fifth Efficiency module must be rejected (per-type cap), even with a free slot");
            return;
        }
        // the free fifth slot still takes a DIFFERENT type
        if (!t5.installUpgrade(new ItemStack(ModItems.SPEED_UPGRADE.get()))) {
            helper.fail("A different module type should still fit the free slot");
            return;
        }
        helper.succeed();
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
