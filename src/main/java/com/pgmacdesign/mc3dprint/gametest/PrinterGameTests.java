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
public class PrinterGameTests {

    private static PrinterBlockEntity placePoweredPrinter(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.TIER1_PRINTER.get());
        if (!(helper.getBlockEntity(pos) instanceof PrinterBlockEntity printer)) {
            throw new GameTestAssertException("Printer block entity missing");
        }
        printer.getCapability(ForgeCapabilities.ENERGY).ifPresent(energy -> {
            for (int i = 0; i < 60; i++) {
                energy.receiveEnergy(1_000, false);
            }
        });
        attachLoadedSpool(printer);
        printer.setAutoStart(true); // item-mode now gates on Auto; tests want continuous print
        return printer;
    }

    static void attachLoadedSpool(PrinterBlockEntity printer) {
        ItemStack spool = new ItemStack(ModItems.SPOOLS.get(0).get());
        SpoolItem.setFu(spool, 400);
        printer.spoolInventory().setStackInSlot(0, spool);
    }

    @GameTest(template = "empty5", timeoutTicks = 150)
    public static void printerCopiesTemplateItem(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        PrinterBlockEntity printer = placePoweredPrinter(helper, pos);
        printer.inventory().setStackInSlot(PrinterBlockEntity.SLOT_TEMPLATE, new ItemStack(Items.STONE));

        helper.succeedWhen(() -> {
            ItemStack output = printer.inventory().getStackInSlot(PrinterBlockEntity.SLOT_OUTPUT);
            if (output.isEmpty() || !output.is(Items.STONE)) {
                throw new GameTestAssertException("No printed copy in output yet");
            }
            ItemStack template = printer.inventory().getStackInSlot(PrinterBlockEntity.SLOT_TEMPLATE);
            if (template.isEmpty()) {
                throw new GameTestAssertException("Template item must not be consumed");
            }
        });
    }

    @GameTest(template = "empty5", timeoutTicks = 150)
    public static void printerPausesWithoutPower(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, ModBlocks.TIER1_PRINTER.get());
        if (!(helper.getBlockEntity(pos) instanceof PrinterBlockEntity printer)) {
            throw new GameTestAssertException("Printer block entity missing");
        }
        attachLoadedSpool(printer); // filament present — isolate the power variable
        printer.inventory().setStackInSlot(PrinterBlockEntity.SLOT_TEMPLATE, new ItemStack(Items.STONE));

        // after plenty of ticks with zero energy, nothing must be printed
        helper.runAfterDelay(100, () -> {
            if (!printer.inventory().getStackInSlot(PrinterBlockEntity.SLOT_OUTPUT).isEmpty()) {
                helper.fail("Printer produced output without power");
            } else {
                helper.succeed();
            }
        });
    }

    @GameTest(template = "empty5", timeoutTicks = 150)
    public static void printerIdlesWithEmptyTemplate(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        PrinterBlockEntity printer = placePoweredPrinter(helper, pos);

        helper.runAfterDelay(60, () -> {
            if (!printer.inventory().getStackInSlot(PrinterBlockEntity.SLOT_OUTPUT).isEmpty()) {
                helper.fail("Printer produced output with no template");
            } else {
                helper.succeed();
            }
        });
    }
}
