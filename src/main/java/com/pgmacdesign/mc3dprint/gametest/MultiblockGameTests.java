package com.pgmacdesign.mc3dprint.gametest;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.fu.SpoolItem;
import com.pgmacdesign.mc3dprint.machine.MachineTier;
import com.pgmacdesign.mc3dprint.machine.PrinterBlockEntity;
import com.pgmacdesign.mc3dprint.machine.multiblock.CasingBlock;
import com.pgmacdesign.mc3dprint.machine.multiblock.ControllerBlock;
import com.pgmacdesign.mc3dprint.machine.multiblock.FabricatorBlockItem;
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
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(MC3DPrint.MOD_ID)
@PrefixGameTestTemplate(false)
public class MultiblockGameTests {

    private static final BlockPos CONTROLLER_POS = new BlockPos(2, 1, 2);

    /**
     * Lays the 8 base cells of a T5 3x3 — corners take {@code cornerBlock}, edges
     * take Printer Casing. T5 now forms from all-casing (no premium corner required).
     */
    private static void placeT5Base(GameTestHelper helper, Block cornerBlock) {
        for (BlockPos offset : MultiblockPattern.componentOffsets(MachineTier.T5)) {
            Block block = MultiblockPattern.isCorner(offset, MachineTier.T5)
                    ? cornerBlock
                    : ModBlocks.PRINTER_CASING.get();
            helper.setBlock(CONTROLLER_POS.offset(offset), block);
        }
    }

    /** Builds a valid T5 base (all Printer Casing, controller center) and returns the controller BE. */
    private static PrinterBlockEntity buildT5(GameTestHelper helper) {
        placeT5Base(helper, ModBlocks.PRINTER_CASING.get());
        helper.setBlock(CONTROLLER_POS, ModBlocks.CONTROLLERS.get(0).get());
        if (!(helper.getBlockEntity(CONTROLLER_POS) instanceof PrinterBlockEntity printer)) {
            throw new GameTestAssertException("Controller block entity missing");
        }
        java.util.Optional.ofNullable(printer.getEnergyStorage()).ifPresent(energy -> {
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
        helper.useBlock(CONTROLLER_POS, helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL));

        helper.runAfterDelay(5, () -> {
            BlockState controller = helper.getLevel().getBlockState(helper.absolutePos(CONTROLLER_POS));
            if (!controller.getValue(ControllerBlock.FORMED)) {
                helper.fail("Controller did not form on right-click");
                return;
            }
            for (BlockPos offset : MultiblockPattern.componentOffsets(MachineTier.T5)) {
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
    public static void t5FormsWithCasingCornersOnRightClick(GameTestHelper helper) {
        // all Printer Casing (no premium corner) + right-click → forms
        placeT5Base(helper, ModBlocks.PRINTER_CASING.get());
        helper.setBlock(CONTROLLER_POS, ModBlocks.CONTROLLERS.get(0).get());
        helper.useBlock(CONTROLLER_POS, helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL));

        helper.runAfterDelay(5, () -> {
            BlockState controller = helper.getLevel().getBlockState(helper.absolutePos(CONTROLLER_POS));
            if (!controller.getValue(ControllerBlock.FORMED)) {
                helper.fail("T5 did not form with casing corners on right-click");
                return;
            }
            // every base cell — corners included — is now an ACTIVE casing
            for (BlockPos offset : MultiblockPattern.componentOffsets(MachineTier.T5)) {
                BlockState casing = helper.getLevel().getBlockState(
                        helper.absolutePos(CONTROLLER_POS.offset(offset)));
                if (!(casing.getBlock() instanceof CasingBlock) || !casing.getValue(CasingBlock.ACTIVE)) {
                    helper.fail("T5 base cell at " + offset + " not an ACTIVE casing: " + casing.getBlock());
                    return;
                }
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

    @GameTest(template = "empty5", timeoutTicks = 100)
    public static void relocateRestoresComponents(GameTestHelper helper) {
        // PGM-48: re-forming a collapsed multiblock must restore the SAME components it had.
        // T5 has no premium corner, so every base cell (corners included) comes back as ACTIVE
        // Printer Casing — this exercises FabricatorBlockItem.reformComponents end-to-end. (The
        // T8 path restores Awakened Draconium corners instead of casing; that needs Draconic
        // Evolution and is verified in-world, since the dev env can't register the block.)
        BlockPos absController = helper.absolutePos(CONTROLLER_POS);
        helper.setBlock(CONTROLLER_POS, ModBlocks.CONTROLLERS.get(0).get()); // T5 controller
        FabricatorBlockItem.reformComponents(helper.getLevel(), absController, MachineTier.T5);

        for (BlockPos offset : MultiblockPattern.componentOffsets(MachineTier.T5)) {
            BlockState s = helper.getLevel().getBlockState(absController.offset(offset));
            if (!(s.getBlock() instanceof CasingBlock) || !s.getValue(CasingBlock.ACTIVE)) {
                helper.fail("relocate must restore ACTIVE casing at " + offset + ", got " + s);
                return;
            }
            // The formed LOOK must come back too, not just ACTIVE: every base cell
            // carries a top-face part when formed (corner post / rail / bed). A
            // re-placed fabricator once glowed with PART=NONE everywhere and looked
            // unformed until manually broken and re-formed.
            if (s.getValue(CasingBlock.PART) == CasingBlock.CasingPart.NONE) {
                helper.fail("relocate must restore the formed-look PART at " + offset + ", got NONE");
                return;
            }
        }
        // Spot-check orientation: NW corner post, and a rail running along each edge.
        if (partAt(helper, absController, -1, -1) != CasingBlock.CasingPart.CORNER_NW
                || partAt(helper, absController, 0, -1) != CasingBlock.CasingPart.RAIL_EW
                || partAt(helper, absController, -1, 0) != CasingBlock.CasingPart.RAIL_NS) {
            helper.fail("relocate restored wrong PART orientation: NW="
                    + partAt(helper, absController, -1, -1)
                    + " N-edge=" + partAt(helper, absController, 0, -1)
                    + " W-edge=" + partAt(helper, absController, -1, 0));
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty5", timeoutTicks = 100)
    public static void relocateRestoresHeatedBedInterior(GameTestHelper helper) {
        // T5's 3x3 base is all corners and edges, so the test above never sees a BED
        // cell, yet BED is the bulk of a large base (48 of the 80 cells on a T8 9x9).
        // T6's 5x5 fills the empty5 template exactly and has an 8-cell interior ring.
        BlockPos absController = helper.absolutePos(CONTROLLER_POS);
        helper.setBlock(CONTROLLER_POS, ModBlocks.CONTROLLERS.get(1).get()); // T6
        FabricatorBlockItem.reformComponents(helper.getLevel(), absController, MachineTier.T6);

        int half = MultiblockPattern.baseEdge(MachineTier.T6) / 2;
        for (BlockPos offset : MultiblockPattern.componentOffsets(MachineTier.T6)) {
            BlockState s = helper.getLevel().getBlockState(absController.offset(offset));
            if (!(s.getBlock() instanceof CasingBlock)) {
                helper.fail("T6 base cell " + offset + " is not a casing: " + s.getBlock());
                return;
            }
            boolean interior = Math.abs(offset.getX()) < half && Math.abs(offset.getZ()) < half;
            CasingBlock.CasingPart part = s.getValue(CasingBlock.PART);
            if (interior != (part == CasingBlock.CasingPart.BED)) {
                helper.fail("T6 cell " + offset + (interior ? " (interior) should be BED" : " (perimeter) must not be BED")
                        + ", got " + part);
                return;
            }
        }
        helper.succeed();
    }

    private static CasingBlock.CasingPart partAt(GameTestHelper helper, BlockPos absController, int dx, int dz) {
        return helper.getLevel().getBlockState(absController.offset(dx, 0, dz)).getValue(CasingBlock.PART);
    }
}
