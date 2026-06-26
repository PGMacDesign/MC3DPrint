package com.pgmacdesign.mc3dprint.gametest;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.machine.PrinterBlockEntity;
import com.pgmacdesign.mc3dprint.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * I/O design (hard spec): top = input, bottom = output, and the four sides
 * are reserved exclusively for filament spools — no general item I/O.
 */
@GameTestHolder(MC3DPrint.MOD_ID)
@PrefixGameTestTemplate(false)
public class IoFaceGameTests {

    @GameTest(template = "empty5", timeoutTicks = 100)
    public static void sidesExposeNoItemHandler(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, ModBlocks.TIER1_PRINTER.get());
        if (!(helper.getBlockEntity(pos) instanceof PrinterBlockEntity printer)) {
            throw new GameTestAssertException("Printer block entity missing");
        }

        for (Direction side : new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
            if (printer.getItemHandler(side) != null) {
                helper.fail("Side " + side + " exposes an item handler — sides are spool-only by spec");
                return;
            }
        }
        if (printer.getItemHandler(Direction.UP) == null) {
            helper.fail("Top face must accept input");
            return;
        }
        if (printer.getItemHandler(Direction.DOWN) == null) {
            helper.fail("Bottom face must expose output");
            return;
        }
        // energy stays available on all faces — the spool rule is item I/O only
        if (printer.getEnergyStorage() == null) {
            helper.fail("Energy must remain available on side faces");
            return;
        }
        helper.succeed();
    }
}
