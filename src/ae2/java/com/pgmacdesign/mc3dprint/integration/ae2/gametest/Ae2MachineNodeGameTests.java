package com.pgmacdesign.mc3dprint.integration.ae2.gametest;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A printer has to look like an AE2 machine to AE2 itself, or an ME cable placed against one draws
 * no connection and the guide's "put it against a cable" instruction reads as a lie.
 *
 * <p>Asserted through {@link GridHelper}, which is the same entry point AE2's own cable bus uses to
 * decide whether to render a connection towards a neighbour. Testing it here rather than by placing
 * a cable part keeps the test about our side of the contract.
 */
@GameTestHolder(MC3DPrint.MOD_ID)
@PrefixGameTestTemplate(false)
public final class Ae2MachineNodeGameTests {

    private static final BlockPos PRINTER_POS = new BlockPos(1, 1, 1);

    private Ae2MachineNodeGameTests() {}

    @GameTest(template = "empty5", timeoutTicks = 60)
    public static void aPrinterExposesAGridNodeOnEverySide(GameTestHelper helper) {
        // T3: the first tier with a print area, and the tier the rest of the suite uses.
        helper.setBlock(PRINTER_POS, ModBlocks.PRINTERS.get(2).get());

        // The node is created on block-entity load, which is a tick away from setBlock.
        helper.runAfterDelay(5, () -> {
            BlockPos abs = helper.absolutePos(PRINTER_POS);
            IInWorldGridNodeHost host = GridHelper.getNodeHost(helper.getLevel(), abs);
            if (host == null) {
                throw new GameTestAssertException("a printer must answer AE2's node-host lookup,"
                        + " or no cable will ever connect to it");
            }
            for (Direction side : Direction.values()) {
                IGridNode node = GridHelper.getExposedNode(helper.getLevel(), abs, side);
                if (node == null) {
                    throw new GameTestAssertException(
                            "no grid node exposed on " + side + "; a cable on that face would"
                                    + " render as ending in mid-air");
                }
            }
            helper.succeed();
        });
    }

    /**
     * The node must NOT ask for a channel. A printer runs on RF and filament; the terminal part is
     * the piece that pays AE2. If this ever flips, every existing network silently loses one
     * channel per printer the moment the mod updates.
     */
    @GameTest(template = "empty5", timeoutTicks = 60)
    public static void aPrinterNodeCostsNoChannel(GameTestHelper helper) {
        helper.setBlock(PRINTER_POS, ModBlocks.PRINTERS.get(2).get());
        helper.runAfterDelay(5, () -> {
            IGridNode node = GridHelper.getExposedNode(
                    helper.getLevel(), helper.absolutePos(PRINTER_POS), Direction.NORTH);
            if (node == null) {
                throw new GameTestAssertException("printer exposed no node at all");
            }
            if (node.hasFlag(appeng.api.networking.GridFlags.REQUIRE_CHANNEL)) {
                throw new GameTestAssertException("a printer node must not require a channel");
            }
            helper.succeed();
        });
    }

    /**
     * Two touching machines have to land on the SAME grid. Terminal discovery is now one flat pass
     * over the grid's node list, so a machine that fails to join is a machine the terminal cannot
     * see, however correct its own node looks in isolation.
     */
    @GameTest(template = "empty5", timeoutTicks = 80)
    public static void touchingMachinesShareAGrid(GameTestHelper helper) {
        BlockPos second = PRINTER_POS.east();
        helper.setBlock(PRINTER_POS, ModBlocks.PRINTERS.get(2).get());
        helper.setBlock(second, ModBlocks.PRINTERS.get(2).get());

        helper.runAfterDelay(20, () -> {
            IGridNode a = GridHelper.getExposedNode(
                    helper.getLevel(), helper.absolutePos(PRINTER_POS), Direction.EAST);
            IGridNode b = GridHelper.getExposedNode(
                    helper.getLevel(), helper.absolutePos(second), Direction.WEST);
            if (a == null || b == null) {
                throw new GameTestAssertException("one of the two machines exposed no node");
            }
            if (a.getGrid() != b.getGrid()) {
                throw new GameTestAssertException("two touching machines landed on different"
                        + " grids, so the terminal would list only one of them");
            }
        });
        helper.runAfterDelay(40, helper::succeed);
    }

    /**
     * Breaking the machine has to take its node with it. A leaked node keeps the position on the
     * grid, so the terminal would keep listing a machine that is no longer there.
     */
    @GameTest(template = "empty5", timeoutTicks = 80)
    public static void breakingAPrinterRemovesItsNode(GameTestHelper helper) {
        helper.setBlock(PRINTER_POS, ModBlocks.PRINTERS.get(2).get());
        helper.runAfterDelay(5, () -> {
            BlockPos abs = helper.absolutePos(PRINTER_POS);
            if (GridHelper.getExposedNode(helper.getLevel(), abs, Direction.NORTH) == null) {
                throw new GameTestAssertException("the node was never created, so its removal"
                        + " proves nothing");
            }
            helper.setBlock(PRINTER_POS, net.minecraft.world.level.block.Blocks.AIR);
        });
        helper.runAfterDelay(20, () -> {
            BlockPos abs = helper.absolutePos(PRINTER_POS);
            if (GridHelper.getExposedNode(helper.getLevel(), abs, Direction.NORTH) != null) {
                throw new GameTestAssertException("a broken printer still exposes a grid node");
            }
            helper.succeed();
        });
    }
}
