package com.pgmacdesign.mc3dprint.gametest;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.fu.SpoolItem;
import com.pgmacdesign.mc3dprint.machine.MachineTier;
import com.pgmacdesign.mc3dprint.machine.PrinterBlockEntity;
import com.pgmacdesign.mc3dprint.machine.multiblock.ControllerBlock;
import com.pgmacdesign.mc3dprint.machine.multiblock.MultiblockPattern;
import com.pgmacdesign.mc3dprint.registry.ModBlocks;
import com.pgmacdesign.mc3dprint.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(MC3DPrint.MOD_ID)
@PrefixGameTestTemplate(false)
public class MultiblockGameTests {

    private static final BlockPos CONTROLLER_POS = new BlockPos(2, 1, 2);

    /** Builds a valid T5 base (3x3 casings, controller center) and returns the controller BE. */
    private static PrinterBlockEntity buildT5(GameTestHelper helper) {
        for (BlockPos offset : MultiblockPattern.componentOffsets(MachineTier.T5)) {
            helper.setBlock(CONTROLLER_POS.offset(offset), ModBlocks.PRINTER_CASING.get());
        }
        helper.setBlock(CONTROLLER_POS, ModBlocks.CONTROLLERS.get(0).get());
        if (!(helper.getBlockEntity(CONTROLLER_POS) instanceof PrinterBlockEntity printer)) {
            throw new GameTestAssertException("Controller block entity missing");
        }
        printer.getCapability(ForgeCapabilities.ENERGY).ifPresent(energy -> {
            for (int i = 0; i < 60; i++) {
                energy.receiveEnergy(16_000, false);
            }
        });
        ItemStack spool = new ItemStack(ModItems.SPOOLS.get(0).get());
        SpoolItem.setFu(spool, 400);
        printer.spoolInventory().setStackInSlot(0, spool);
        return printer;
    }

    private static void form(GameTestHelper helper) {
        BlockPos abs = helper.absolutePos(CONTROLLER_POS);
        if (MultiblockPattern.validate(helper.getLevel(), abs, MachineTier.T5) != null) {
            throw new GameTestAssertException("T5 pattern should validate");
        }
        BlockState state = helper.getLevel().getBlockState(abs);
        helper.getLevel().setBlock(abs, state.setValue(ControllerBlock.FORMED, true), Block.UPDATE_ALL);
    }

    @GameTest(template = "empty5", timeoutTicks = 200)
    public static void t5FormsAndPrintsWhenFormed(GameTestHelper helper) {
        PrinterBlockEntity printer = buildT5(helper);
        form(helper);
        printer.inventory().setStackInSlot(PrinterBlockEntity.SLOT_TEMPLATE, new ItemStack(Items.STONE));

        helper.succeedWhen(() -> {
            ItemStack output = printer.inventory().getStackInSlot(PrinterBlockEntity.SLOT_OUTPUT);
            if (output.isEmpty() || !output.is(Items.STONE)) {
                throw new GameTestAssertException("Formed T5 has not printed yet");
            }
        });
    }

    @GameTest(template = "empty5", timeoutTicks = 150)
    public static void unformedControllerRefusesToOperate(GameTestHelper helper) {
        PrinterBlockEntity printer = buildT5(helper);
        // NOT formed
        printer.inventory().setStackInSlot(PrinterBlockEntity.SLOT_TEMPLATE, new ItemStack(Items.STONE));

        helper.runAfterDelay(60, () -> {
            if (!printer.inventory().getStackInSlot(PrinterBlockEntity.SLOT_OUTPUT).isEmpty()) {
                helper.fail("Unformed controller printed");
                return;
            }
            if (printer.state() != PrinterBlockEntity.State.IDLE) {
                helper.fail("Unformed controller should idle, got " + printer.state());
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty5", timeoutTicks = 150)
    public static void breakingCasingUnformsController(GameTestHelper helper) {
        buildT5(helper);
        form(helper);

        helper.runAfterDelay(10, () ->
                helper.getLevel().removeBlock(helper.absolutePos(new BlockPos(1, 1, 1)), false));

        helper.runAfterDelay(30, () -> {
            BlockState state = helper.getLevel().getBlockState(helper.absolutePos(CONTROLLER_POS));
            if (state.getValue(ControllerBlock.FORMED)) {
                helper.fail("Controller still formed after casing break");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty5", timeoutTicks = 100)
    public static void invalidPatternRefusesToForm(GameTestHelper helper) {
        helper.setBlock(CONTROLLER_POS, ModBlocks.CONTROLLERS.get(0).get());
        // no casings at all
        if (MultiblockPattern.validate(helper.getLevel(), helper.absolutePos(CONTROLLER_POS), MachineTier.T5) == null) {
            helper.fail("Empty pattern validated as T5");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty5", timeoutTicks = 100)
    public static void t8RefusesWithoutDraconicEvolution(GameTestHelper helper) {
        // dev environment has no Draconic Evolution — T8 must refuse outright
        if (MultiblockPattern.validate(helper.getLevel(), helper.absolutePos(CONTROLLER_POS), MachineTier.T8) == null) {
            helper.fail("T8 validated without Draconic Evolution");
            return;
        }
        helper.succeed();
    }
}
