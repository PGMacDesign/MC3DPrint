package com.pgmacdesign.mc3dprint.gametest;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.config.MC3DPrintConfig;
import com.pgmacdesign.mc3dprint.machine.MachineTier;
import com.pgmacdesign.mc3dprint.machine.PrinterBlockEntity;
import com.pgmacdesign.mc3dprint.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * The per-tier {@code maxFootprint} config actually moves the size gate — it is
 * no longer hardcoded in {@link MachineTier}. Set-and-restore happens entirely
 * within one synchronous test body so no other test observes the altered cap.
 */
@GameTestHolder(MC3DPrint.MOD_ID)
@PrefixGameTestTemplate(false)
public class ConfigurableFootprintGameTests {

    @GameTest(template = "empty5", timeoutTicks = 40)
    public static void footprintCapFollowsConfig(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 1, 2), ModBlocks.PRINTERS.get(2).get()); // T3, default cap 3
        if (!(helper.getBlockEntity(new BlockPos(2, 1, 2)) instanceof PrinterBlockEntity printer)) {
            throw new GameTestAssertException("Printer block entity missing");
        }

        // a 5-wide region: over the T3 default (3), inside the raised cap below
        BlockPos a = helper.absolutePos(new BlockPos(0, 2, 0));
        BlockPos b = helper.absolutePos(new BlockPos(4, 2, 4));

        var cap = MC3DPrintConfig.maxFootprintValue(MachineTier.T3);
        int original = cap.get();
        try {
            if (printer.setDeconstructRegion(a, b) != PrinterBlockEntity.RegionResult.TOO_LARGE) {
                throw new GameTestAssertException("5-wide region accepted at default T3 cap " + original);
            }
            cap.set(9);
            if (printer.setDeconstructRegion(a, b) != PrinterBlockEntity.RegionResult.SET) {
                throw new GameTestAssertException("5-wide region rejected with T3 cap raised to 9");
            }
        } finally {
            cap.set(original);
        }
        helper.succeed();
    }
}
