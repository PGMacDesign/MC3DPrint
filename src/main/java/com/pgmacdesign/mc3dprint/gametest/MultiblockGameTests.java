package com.pgmacdesign.mc3dprint.gametest;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.fu.SpoolItem;
import com.pgmacdesign.mc3dprint.machine.MachineTier;
import com.pgmacdesign.mc3dprint.machine.PrinterBlockEntity;
import com.pgmacdesign.mc3dprint.machine.multiblock.CasingBlock;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(MC3DPrint.MOD_ID)
@PrefixGameTestTemplate(false)
public class MultiblockGameTests {

    private static final BlockPos CONTROLLER_POS = new BlockPos(2, 1, 2);

    /**
     * Lays the 8 base cells of a T5 3x3: the four corners take {@code cornerBlock}
     * and the four edges take Printer Casing. With diamond corners this is a valid
     * T5 pattern; with casing corners it is intentionally invalid.
     */
    private static void placeT5Base(GameTestHelper helper, Block cornerBlock) {
        for (BlockPos offset : MultiblockPattern.componentOffsets(MachineTier.T5)) {
            Block block = MultiblockPattern.isCorner(offset, MachineTier.T5)
                    ? cornerBlock
                    : ModBlocks.PRINTER_CASING.get();
            helper.setBlock(CONTROLLER_POS.offset(offset), block);
        }
    }

    /** Builds a valid T5 base (diamond corners + casing edges, controller center) and returns the controller BE. */
    private static PrinterBlockEntity buildT5(GameTestHelper helper) {
        placeT5Base(helper, Blocks.DIAMOND_BLOCK);
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
        printer.setAutoStart(true); // item-mode now gates on Auto; formed-print test wants a print
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

        // break an EDGE casing (corners are diamond and don't drive unform)
        helper.runAfterDelay(10, () ->
                helper.getLevel().removeBlock(
                        helper.absolutePos(CONTROLLER_POS.offset(0, 0, -1)), false));

        helper.runAfterDelay(30, () -> {
            BlockState state = helper.getLevel().getBlockState(helper.absolutePos(CONTROLLER_POS));
            if (state.getValue(ControllerBlock.FORMED)) {
                helper.fail("Controller still formed after casing break");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty5", timeoutTicks = 150)
    public static void formingActivatesCasingsBreakingDeactivates(GameTestHelper helper) {
        buildT5(helper);

        // Form via the real right-click path so component activation is exercised.
        helper.useBlock(CONTROLLER_POS, helper.makeMockSurvivalPlayer());

        helper.runAfterDelay(5, () -> {
            BlockState controller = helper.getLevel().getBlockState(helper.absolutePos(CONTROLLER_POS));
            if (!controller.getValue(ControllerBlock.FORMED)) {
                helper.fail("Controller did not form on right-click");
                return;
            }
            for (BlockPos offset : MultiblockPattern.componentOffsets(MachineTier.T5)) {
                if (MultiblockPattern.isCorner(offset, MachineTier.T5)) {
                    continue; // corners are diamond blocks, not ACTIVE casings
                }
                BlockState casing = helper.getLevel().getBlockState(
                        helper.absolutePos(CONTROLLER_POS.offset(offset)));
                if (!(casing.getBlock() instanceof CasingBlock) || !casing.getValue(CasingBlock.ACTIVE)) {
                    helper.fail("Casing at " + offset + " not ACTIVE after forming");
                    return;
                }
            }

            // Break one EDGE casing — the controller unforms and the survivors go dark.
            helper.destroyBlock(CONTROLLER_POS.offset(0, 0, -1));
        });

        helper.runAfterDelay(25, () -> {
            BlockState controller = helper.getLevel().getBlockState(helper.absolutePos(CONTROLLER_POS));
            if (controller.getValue(ControllerBlock.FORMED)) {
                helper.fail("Controller still FORMED after casing break");
                return;
            }
            for (BlockPos offset : MultiblockPattern.componentOffsets(MachineTier.T5)) {
                BlockPos rel = CONTROLLER_POS.offset(offset);
                BlockState casing = helper.getLevel().getBlockState(helper.absolutePos(rel));
                // the broken casing is gone; any survivor must be inactive
                if (casing.getBlock() instanceof CasingBlock && casing.getValue(CasingBlock.ACTIVE)) {
                    helper.fail("Surviving casing at " + offset + " still ACTIVE after unform");
                    return;
                }
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
    public static void t5FormsWithDiamondCornersOnRightClick(GameTestHelper helper) {
        // 4 casing edges + 4 diamond_block corners + right-click → forms
        placeT5Base(helper, Blocks.DIAMOND_BLOCK);
        helper.setBlock(CONTROLLER_POS, ModBlocks.CONTROLLERS.get(0).get());
        helper.useBlock(CONTROLLER_POS, helper.makeMockSurvivalPlayer());

        helper.runAfterDelay(5, () -> {
            BlockState controller = helper.getLevel().getBlockState(helper.absolutePos(CONTROLLER_POS));
            if (!controller.getValue(ControllerBlock.FORMED)) {
                helper.fail("T5 did not form with diamond corners on right-click");
                return;
            }
            // corners must still be diamond blocks (rendered as themselves, not casing)
            for (BlockPos offset : MultiblockPattern.componentOffsets(MachineTier.T5)) {
                if (!MultiblockPattern.isCorner(offset, MachineTier.T5)) {
                    continue;
                }
                Block corner = helper.getLevel().getBlockState(
                        helper.absolutePos(CONTROLLER_POS.offset(offset))).getBlock();
                if (corner != Blocks.DIAMOND_BLOCK) {
                    helper.fail("T5 corner at " + offset + " is not a diamond block: " + corner);
                    return;
                }
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty5", timeoutTicks = 100)
    public static void t5RefusesToFormWithCasingCorners(GameTestHelper helper) {
        // corners are Printer Casing instead of diamond → invalid T5 pattern
        placeT5Base(helper, ModBlocks.PRINTER_CASING.get());
        helper.setBlock(CONTROLLER_POS, ModBlocks.CONTROLLERS.get(0).get());

        if (MultiblockPattern.validate(
                helper.getLevel(), helper.absolutePos(CONTROLLER_POS), MachineTier.T5) == null) {
            helper.fail("T5 validated with casing corners (diamond required)");
            return;
        }

        // and the real right-click path must NOT form it either
        helper.useBlock(CONTROLLER_POS, helper.makeMockSurvivalPlayer());
        helper.runAfterDelay(5, () -> {
            BlockState controller = helper.getLevel().getBlockState(helper.absolutePos(CONTROLLER_POS));
            if (controller.getValue(ControllerBlock.FORMED)) {
                helper.fail("T5 formed with casing corners");
                return;
            }
            helper.succeed();
        });
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
