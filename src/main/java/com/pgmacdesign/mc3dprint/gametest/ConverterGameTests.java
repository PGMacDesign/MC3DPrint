package com.pgmacdesign.mc3dprint.gametest;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.fu.SpoolItem;
import com.pgmacdesign.mc3dprint.machine.FilamentConverterBlockEntity;
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
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(MC3DPrint.MOD_ID)
@PrefixGameTestTemplate(false)
public class ConverterGameTests {

    @GameTest(template = "empty5", timeoutTicks = 200)
    public static void converterFeedsAdjacentPrinterSpool(GameTestHelper helper) {
        // chest (1,1,2) <- converter (2,1,2) -> printer (3,1,2)
        helper.setBlock(new BlockPos(1, 1, 2), Blocks.CHEST);
        helper.setBlock(new BlockPos(2, 1, 2), ModBlocks.FILAMENT_CONVERTER.get());
        helper.setBlock(new BlockPos(3, 1, 2), ModBlocks.TIER1_PRINTER.get());

        if (!(helper.getBlockEntity(new BlockPos(1, 1, 2)) instanceof ChestBlockEntity chest)
                || !(helper.getBlockEntity(new BlockPos(2, 1, 2)) instanceof FilamentConverterBlockEntity converter)
                || !(helper.getBlockEntity(new BlockPos(3, 1, 2)) instanceof PrinterBlockEntity printer)) {
            throw new GameTestAssertException("Test blocks missing");
        }

        chest.setItem(0, new ItemStack(Items.COBBLESTONE, 8));
        java.util.Optional.ofNullable(converter.getEnergyStorage()).ifPresent(energy -> {
            for (int i = 0; i < 20; i++) {
                energy.receiveEnergy(1_000, false);
            }
        });
        converter.setFilter(new ItemStack(Items.COBBLESTONE));
        printer.spoolInventory().setStackInSlot(0, new ItemStack(ModItems.SPOOLS.get(0).get()));

        // 8 cobblestone @ 1 FU, 20 ticks per conversion
        helper.succeedWhen(() -> {
            if (printer.totalFu() < 8) {
                throw new GameTestAssertException("Spool has " + printer.totalFu() + " FU, expected 8");
            }
            if (!chest.getItem(0).isEmpty()) {
                throw new GameTestAssertException("Chest not emptied");
            }
        });
    }

    @GameTest(template = "empty5", timeoutTicks = 150)
    public static void converterIdlesWithoutFilter(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 1, 2), Blocks.CHEST);
        helper.setBlock(new BlockPos(2, 1, 2), ModBlocks.FILAMENT_CONVERTER.get());

        if (!(helper.getBlockEntity(new BlockPos(1, 1, 2)) instanceof ChestBlockEntity chest)) {
            throw new GameTestAssertException("Chest missing");
        }
        chest.setItem(0, new ItemStack(Items.COBBLESTONE, 4));

        helper.runAfterDelay(80, () -> {
            if (chest.getItem(0).getCount() != 4) {
                helper.fail("Converter pulled items with no filter set");
                return;
            }
            helper.succeed();
        });
    }
}
