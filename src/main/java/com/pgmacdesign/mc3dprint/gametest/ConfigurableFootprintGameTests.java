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
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The size gate reads the per-tier {@code maxFootprint} config rather than one hardcoded
 * constant: two tiers with different caps each refuse at their own boundary and accept just
 * under it.
 *
 * <p><b>This must not mutate the live config, and the reason is worth keeping.</b> It used to
 * raise the T3 cap with {@code cap.set(9)} and restore it in a {@code finally}, on the
 * reasoning (stated in the old javadoc) that a synchronous body means no other test can
 * observe the altered value. That reasoning covers concurrent tests but not the config
 * itself: {@code ForgeConfigSpec.ConfigValue.set} writes into the <em>file-backed</em> config,
 * autosave queues a disk write, and the config file watcher reloads from disk and clears every
 * cached value. A reload landing between the two writes republishes the raised cap, after
 * which unrelated tests read 9 instead of 3.
 *
 * <p>That is what made {@code footprintMirrorsPrintLimit} and
 * {@code oversizedFootprintNeedsHigherTier} fail intermittently: both assert T3 behaviour at a
 * 4-wide region, which is over the real cap of 3 but inside a leaked 9. The window is
 * filesystem- and scheduling-dependent, so it never reproduced locally and showed up on CI.
 * Batching would not have fixed it, since the watcher fires asynchronously.
 *
 * <p>The trade-off is explicit: deriving the expectations from the configured value proves the
 * gate honours whatever the config says at the exact boundary, but it cannot distinguish a
 * config read from {@link MachineTier#maxFootprint()}, because the config default is defined
 * from that enum. Proving live-editability would require mutating shared state, which is what
 * made this flaky.
 */
@GameTestHolder(MC3DPrint.MOD_ID)
@PrefixGameTestTemplate(false)
public class ConfigurableFootprintGameTests {

    /** Printer block index for a tier; the list is zero-based over tiers 1..8. */
    private static PrinterBlockEntity printerOfTier(GameTestHelper helper, MachineTier tier) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, ModBlocks.PRINTERS.get(tier.number() - 1).get());
        if (!(helper.getBlockEntity(pos) instanceof PrinterBlockEntity printer)) {
            throw new GameTestAssertException("Printer block entity missing for " + tier);
        }
        return printer;
    }

    /** Arms a region {@code width} blocks across on X and returns what the gate said. */
    private static PrinterBlockEntity.RegionResult armWidth(GameTestHelper helper,
                                                            PrinterBlockEntity printer, int width) {
        return printer.setDeconstructRegion(
                helper.absolutePos(new BlockPos(0, 2, 0)),
                helper.absolutePos(new BlockPos(width - 1, 2, 0)));
    }

    private static void assertBoundary(GameTestHelper helper, MachineTier tier) {
        int cap = MC3DPrintConfig.maxFootprint(tier);
        if (cap < 1) {
            throw new GameTestAssertException(tier + " has no printable footprint (cap " + cap + ")");
        }
        PrinterBlockEntity printer = printerOfTier(helper, tier);

        PrinterBlockEntity.RegionResult atCap = armWidth(helper, printer, cap);
        if (atCap != PrinterBlockEntity.RegionResult.SET) {
            throw new GameTestAssertException(
                    tier + " rejected a region exactly at its configured cap of " + cap + ": " + atCap);
        }
        PrinterBlockEntity.RegionResult overCap = armWidth(helper, printer, cap + 1);
        if (overCap != PrinterBlockEntity.RegionResult.TOO_LARGE) {
            throw new GameTestAssertException(
                    tier + " accepted a region one over its configured cap of " + cap + ": " + overCap);
        }
    }

    @GameTest(template = "empty5", timeoutTicks = 40)
    public static void footprintCapFollowsConfigAtT3(GameTestHelper helper) {
        assertBoundary(helper, MachineTier.T3);
        helper.succeed();
    }

    /**
     * The second tier is what makes this more than a boundary check: a gate hardcoded to any
     * single constant passes at one tier and fails at the other, since T3 and T4 ship
     * different caps.
     */
    @GameTest(template = "empty5", timeoutTicks = 40)
    public static void footprintCapFollowsConfigAtT4(GameTestHelper helper) {
        assertBoundary(helper, MachineTier.T4);
        helper.succeed();
    }
}
