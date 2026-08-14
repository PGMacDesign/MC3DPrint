package com.pgmacdesign.mc3dprint.gametest;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.blueprint.Blueprint;
import com.pgmacdesign.mc3dprint.item.BlueprintDiscItem;
import com.pgmacdesign.mc3dprint.machine.PrinterBlockEntity;
import com.pgmacdesign.mc3dprint.scanner.ScanOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The scaffolding rule: scaffolding is how you reach the corners of a build to scan it, so it
 * lands in hand scans by accident. It is captured, but it is not part of the build, so it must
 * not raise the blueprint's tier, must not add to the cost, and must never be placed.
 */
@GameTestHolder(MC3DPrint.MOD_ID)
@PrefixGameTestTemplate(false)
public class ScanOnlyBlockGameTests {

    /** Captures a 1x2x1 column: {@code lower} at the bottom, {@code upper} above it. */
    private static Blueprint captureColumn(GameTestHelper helper,
            net.minecraft.world.level.block.Block lower,
            net.minecraft.world.level.block.Block upper) {
        BlockPos a = new BlockPos(1, 2, 1);
        BlockPos b = a.above();
        helper.setBlock(a, lower);
        helper.setBlock(b, upper);
        return ScanOperation.capture(helper.getLevel(), helper.absolutePos(a), helper.absolutePos(b), "scan-only-test");
    }

    /**
     * A stone+scaffolding scan must quote exactly the same tier and cost as stone alone. The
     * scaffolding is still in the blueprint, so this pins the FILTER rather than the capture:
     * discs scanned before the rule existed are quoted correctly too.
     */
    @GameTest(template = "empty5", timeoutTicks = 100)
    public static void scaffoldingChangesNeitherTierNorCost(GameTestHelper helper) {
        Blueprint withScaffold = captureColumn(helper, Blocks.STONE, Blocks.SCAFFOLDING);
        helper.setBlock(new BlockPos(1, 3, 1), Blocks.AIR);
        Blueprint stoneOnly = captureColumn(helper, Blocks.STONE, Blocks.AIR);

        int tierWith = BlueprintDiscItem.blueprintTier(withScaffold);
        int tierWithout = BlueprintDiscItem.blueprintTier(stoneOnly);
        if (tierWith != tierWithout) {
            throw new GameTestAssertException("scaffolding moved the tier from "
                    + tierWithout + " to " + tierWith);
        }
        int costWith = BlueprintDiscItem.blueprintPrintCost(withScaffold);
        int costWithout = BlueprintDiscItem.blueprintPrintCost(stoneOnly);
        if (costWith != costWithout) {
            throw new GameTestAssertException("scaffolding moved the cost from "
                    + costWithout + " to " + costWith);
        }
        // ...and it really was captured, so this is the filter and not an empty scan.
        boolean captured = false;
        for (var paletteState : withScaffold.palette()) {
            var resolved = paletteState.resolve();
            if (resolved.isPresent() && resolved.get().is(Blocks.SCAFFOLDING)) {
                captured = true;
            }
        }
        if (!captured) {
            throw new GameTestAssertException("expected the scan to still capture the scaffolding");
        }
        helper.succeed();
    }

    /**
     * An itemless block that owns a block entity is not free "structural matter". Free-and-itemless
     * is checked ahead of the strict-mode gate, so anything landing there bypasses it entirely; a
     * block entity means the block can be holding something worth duplicating.
     */
    @GameTest(template = "empty5", timeoutTicks = 40)
    public static void itemlessBlocksWithABlockEntityArentFreeMatter(GameTestHelper helper) {
        if (!PrinterBlockEntity.isStructuralMatterForTest(Blocks.WATER.defaultBlockState())) {
            throw new GameTestAssertException("water must stay free structural matter");
        }
        if (!PrinterBlockEntity.isStructuralMatterForTest(Blocks.FIRE.defaultBlockState())) {
            throw new GameTestAssertException("fire must stay free structural matter");
        }
        // Not a counter-example despite the name: StandingAndWallBlockItem maps the wall variant
        // to the standing block's item, so a wall torch is priced as a torch and never reaches
        // the itemless branch at all.
        if (Blocks.WALL_TORCH.asItem() == net.minecraft.world.item.Items.AIR) {
            throw new GameTestAssertException("wall torch is expected to resolve to the torch item");
        }
        // Itemless, but block-entity backed: the shape the duplication exploit rode in on.
        if (PrinterBlockEntity.isStructuralMatterForTest(Blocks.END_PORTAL.defaultBlockState())) {
            throw new GameTestAssertException("an itemless block entity must not print free");
        }
        helper.succeed();
    }
}
