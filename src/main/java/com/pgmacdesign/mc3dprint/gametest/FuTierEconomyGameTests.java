package com.pgmacdesign.mc3dprint.gametest;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.fu.SpoolItem;
import com.pgmacdesign.mc3dprint.machine.PrinterBlockEntity;
import com.pgmacdesign.mc3dprint.machine.WinderBlockEntity;
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

/**
 * Tiered FU economics (default ratio 4): 1 tier-N FU = 4 tier-(N-1) FU.
 * Down-conversion is generous, up-conversion is lossy — winding a mountain of
 * cobblestone must not cheaply print high-tier items.
 */
@GameTestHolder(MC3DPrint.MOD_ID)
@PrefixGameTestTemplate(false)
public class FuTierEconomyGameTests {

    private static PrinterBlockEntity placePrinter(GameTestHelper helper, BlockPos pos, int tierIndex) {
        helper.setBlock(pos, ModBlocks.PRINTERS.get(tierIndex).get());
        if (!(helper.getBlockEntity(pos) instanceof PrinterBlockEntity printer)) {
            throw new GameTestAssertException("Printer block entity missing");
        }
        return printer;
    }

    private static ItemStack spoolWithFu(int tier, int fu) {
        ItemStack spool = new ItemStack(ModItems.SPOOLS.get(tier - 1).get());
        SpoolItem.setFu(spool, fu);
        return spool;
    }

    @GameTest(template = "empty5", timeoutTicks = 100)
    public static void highTierFuStretchesOnCheapJobs(GameTestHelper helper) {
        PrinterBlockEntity printer = placePrinter(helper, new BlockPos(2, 1, 2), 3); // T4
        printer.spoolInventory().setStackInSlot(0, spoolWithFu(3, 10)); // 10 T3 FU

        // 10 T3 FU = 10 * 16 = 160 T1 FU, and exactly 10 toward T3 costs
        if (printer.effectiveFu(1) != 160) {
            helper.fail("Expected 160 effective T1 FU, got " + printer.effectiveFu(1));
        } else if (printer.effectiveFu(3) != 10) {
            helper.fail("Expected 10 effective T3 FU, got " + printer.effectiveFu(3));
        } else {
            helper.succeed();
        }
    }

    @GameTest(template = "empty5", timeoutTicks = 100)
    public static void lowTierFuIsWeakOnHighTierJobs(GameTestHelper helper) {
        PrinterBlockEntity printer = placePrinter(helper, new BlockPos(2, 1, 2), 3); // T4
        printer.spoolInventory().setStackInSlot(0, spoolWithFu(1, 100)); // 100 T1 FU

        // 100 T1 FU toward T4 costs: 100 / 64 = 1 (floor) — the anti-exploit
        if (printer.effectiveFu(4) != 1) {
            helper.fail("Expected 1 effective T4 FU from 100 T1 FU, got " + printer.effectiveFu(4));
        } else if (printer.effectiveFu(2) != 25) {
            helper.fail("Expected 25 effective T2 FU, got " + printer.effectiveFu(2));
        } else {
            helper.succeed();
        }
    }

    @GameTest(template = "empty5", timeoutTicks = 300)
    public static void printingDrainsAtTheExchangeRate(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        PrinterBlockEntity printer = placePrinter(helper, pos, 0); // T1
        printer.getCapability(ForgeCapabilities.ENERGY).ifPresent(energy -> {
            for (int i = 0; i < 60; i++) {
                energy.receiveEnergy(1_000, false);
            }
        });
        // T2 spool with 10 FU; cobblestone costs 1 FU @ T1 -> one print drains
        // a whole T2 unit only when the 4 T1-sub-units are exhausted (ceil rule:
        // print #1 consumes 1 T2 unit worth 4; prints overcharge at most 1 unit)
        printer.spoolInventory().setStackInSlot(0, spoolWithFu(2, 10));
        printer.inventory().setStackInSlot(PrinterBlockEntity.SLOT_TEMPLATE, new ItemStack(Items.COBBLESTONE));

        helper.succeedWhen(() -> {
            ItemStack output = printer.inventory().getStackInSlot(PrinterBlockEntity.SLOT_OUTPUT);
            if (output.isEmpty() || !output.is(Items.COBBLESTONE)) {
                throw new GameTestAssertException("No cobblestone printed yet");
            }
            int fu = SpoolItem.getFu(printer.spoolInventory().getStackInSlot(0));
            if (fu != 9) {
                helper.fail("Expected 9 T2 FU after one 1-FU@T1 print (ceil drains one unit), got " + fu);
            }
        });
    }

    @GameTest(template = "empty5", timeoutTicks = 300)
    public static void winderConvertsIntoHigherTierSpoolWithCarry(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, ModBlocks.FILAMENT_WINDER.get());
        if (!(helper.getBlockEntity(pos) instanceof WinderBlockEntity winder)) {
            throw new GameTestAssertException("Winder block entity missing");
        }
        winder.getCapability(ForgeCapabilities.ENERGY).ifPresent(energy -> {
            for (int i = 0; i < 60; i++) {
                energy.receiveEnergy(1_000, false);
            }
        });
        // 8 cobblestone (1 FU @ T1 each) into a T2 spool: 8 base units = 2 T2 FU
        winder.inventory().setStackInSlot(WinderBlockEntity.SLOT_SPOOL, spoolWithFu(2, 0));
        winder.inventory().setStackInSlot(WinderBlockEntity.SLOT_INPUT, new ItemStack(Items.COBBLESTONE, 8));

        helper.succeedWhen(() -> {
            if (!winder.inventory().getStackInSlot(WinderBlockEntity.SLOT_INPUT).isEmpty()) {
                throw new GameTestAssertException("Input not fully wound yet");
            }
            int fu = SpoolItem.getFu(winder.inventory().getStackInSlot(WinderBlockEntity.SLOT_SPOOL));
            if (fu != 2) {
                helper.fail("Expected 2 T2 FU from 8 cobblestone, got " + fu);
            }
        });
    }
}
