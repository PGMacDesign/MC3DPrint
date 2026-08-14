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
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

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

    /**
     * A block sitting where scaffolding was captured must not obstruct the job. The printer will
     * never place there, so requiring empty space would refuse the whole build over a cell it
     * was never going to touch.
     */
    @GameTest(template = "empty5", timeoutTicks = 400)
    public static void aBlockedScaffoldCellDoesNotObstructThePrint(GameTestHelper helper) {
        // A 2x2x2 capture: stone floor, scaffolding above it. A build of this size printed by a
        // printer at (2,1,2) lands at origin (1,2,1) — the same geometry the un-print tests use.
        for (int dx = 0; dx < 2; dx++) {
            for (int dz = 0; dz < 2; dz++) {
                helper.setBlock(new BlockPos(1 + dx, 2, 1 + dz), Blocks.STONE);
                helper.setBlock(new BlockPos(1 + dx, 3, 1 + dz), Blocks.SCAFFOLDING);
            }
        }
        Blueprint blueprint = ScanOperation.capture(helper.getLevel(),
                helper.absolutePos(new BlockPos(1, 2, 1)),
                helper.absolutePos(new BlockPos(2, 3, 2)), "scaffold-obstruction-test");

        for (int dx = 0; dx < 2; dx++) {
            for (int dz = 0; dz < 2; dz++) {
                helper.setBlock(new BlockPos(1 + dx, 2, 1 + dz), Blocks.AIR);
                helper.setBlock(new BlockPos(1 + dx, 3, 1 + dz), Blocks.AIR);
            }
        }

        java.util.UUID id = com.pgmacdesign.mc3dprint.blueprint.BlueprintFileStore
                .forServer(helper.getLevel().getServer()).save(blueprint);

        BlockPos printerPos = new BlockPos(2, 1, 2);
        helper.setBlock(printerPos, com.pgmacdesign.mc3dprint.registry.ModBlocks.PRINTERS.get(2).get());
        if (!(helper.getBlockEntity(printerPos) instanceof PrinterBlockEntity printer)) {
            throw new GameTestAssertException("printer block entity missing");
        }
        net.minecraft.world.item.ItemStack spool = new net.minecraft.world.item.ItemStack(
                com.pgmacdesign.mc3dprint.registry.ModItems.SPOOLS.get(0).get());
        com.pgmacdesign.mc3dprint.fu.SpoolItem.setFu(spool, 100_000);
        printer.spoolInventory().setStackInSlot(0, spool);
        printer.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ENERGY)
                .ifPresent(energy -> {
                    for (int i = 0; i < 60; i++) {
                        energy.receiveEnergy(1_000, false);
                    }
                });
        net.minecraft.world.item.ItemStack disc = new net.minecraft.world.item.ItemStack(
                com.pgmacdesign.mc3dprint.registry.ModItems.BLUEPRINT_DISC.get());
        BlueprintDiscItem.writeBlueprint(disc, id, blueprint);
        printer.inventory().setStackInSlot(PrinterBlockEntity.SLOT_TEMPLATE, disc);

        BlockPos origin = new BlockPos(1, 2, 1);
        BlockPos blockedScaffoldCell = origin.above(); // captured scaffolding, never printed
        helper.setBlock(blockedScaffoldCell, Blocks.COBBLESTONE);

        printer.setAutoStart(true);
        printer.requestStart();

        helper.succeedWhen(() -> {
            if (printer.state() == PrinterBlockEntity.State.PAUSED_OBSTRUCTED) {
                throw new GameTestAssertException(
                        "a captured scaffolding cell obstructed a print that never fills it");
            }
            helper.assertBlockPresent(Blocks.STONE, origin);
            // ...and the block occupying the scaffolding cell is left alone.
            helper.assertBlockPresent(Blocks.COBBLESTONE, blockedScaffoldCell);
        });
    }
}
