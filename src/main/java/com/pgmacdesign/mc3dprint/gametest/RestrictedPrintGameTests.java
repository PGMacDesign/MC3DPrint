package com.pgmacdesign.mc3dprint.gametest;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.blueprint.Blueprint;
import com.pgmacdesign.mc3dprint.blueprint.BlueprintBlockState;
import com.pgmacdesign.mc3dprint.blueprint.BlueprintFileStore;
import com.pgmacdesign.mc3dprint.blueprint.CuratedBlueprints;
import com.pgmacdesign.mc3dprint.fu.SpoolItem;
import com.pgmacdesign.mc3dprint.item.BlueprintDiscItem;
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
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * The #print_restricted trophy gate: restricted blocks print ONLY from an
 * official disc whose curated blueprint carries an allowance for them; item
 * mode refuses them outright (that path would be straight duplication).
 */
@GameTestHolder(MC3DPrint.MOD_ID)
@PrefixGameTestTemplate(false)
public class RestrictedPrintGameTests {

    /** 1x2x1: stone base with a creeper head on top (T4 — within a T4 printer's
     *  material tier, so the RESTRICTION is the only gate under test). */
    private static Blueprint headBlueprint() {
        return Blueprint.builder("gametest-trophy", 1, 2, 1)
                .set(0, 0, 0, BlueprintBlockState.parse("minecraft:stone"))
                .set(0, 1, 0, BlueprintBlockState.parse("minecraft:creeper_head[rotation=0]"))
                .build();
    }

    private static PrinterBlockEntity poweredPrinter(GameTestHelper helper, BlockPos localPos) {
        // T4: the highest single-block printer — clears creeper_head's material
        // tier, so only the trophy restriction can exclude it.
        helper.setBlock(localPos, ModBlocks.PRINTERS.get(3).get());
        if (!(helper.getBlockEntity(localPos) instanceof PrinterBlockEntity printer)) {
            throw new GameTestAssertException("Printer block entity missing");
        }
        printer.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ENERGY).ifPresent(energy -> {
            for (int i = 0; i < 60; i++) {
                energy.receiveEnergy(1_000, false);
            }
        });
        PrinterGameTests.attachLoadedSpool(printer);
        printer.setAutoStart(true);
        return printer;
    }

    @GameTest(template = "empty5", timeoutTicks = 300)
    public static void playerDiscSkipsRestrictedBlock(GameTestHelper helper) {
        BlockPos printerPos = new BlockPos(2, 1, 2);
        PrinterBlockEntity printer = poweredPrinter(helper, printerPos);

        Blueprint blueprint = headBlueprint();
        BlueprintFileStore store = BlueprintFileStore.forServer(helper.getLevel().getServer());
        UUID id = store.save(blueprint);
        ItemStack disc = new ItemStack(ModItems.BLUEPRINT_DISC.get());
        BlueprintDiscItem.writeBlueprint(disc, id, blueprint, true); // player-scanned
        printer.inventory().setStackInSlot(PrinterBlockEntity.SLOT_TEMPLATE, disc);

        // origin = printer + (0, 1, 0) for 1x2x1 -> stone at (2,2,2), head slot at (2,3,2)
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(2, 2, 2));
            helper.assertBlockPresent(Blocks.AIR, new BlockPos(2, 3, 2));
            if (printer.activeJob() != null) {
                throw new GameTestAssertException("job should complete, skipping the restricted head");
            }
        });
    }

    @GameTest(template = "empty5", timeoutTicks = 100)
    public static void officialAllowanceAdmitsRestrictedBlock(GameTestHelper helper) {
        // The gate decision lives in canPrintBlock, surfaced through the cost report:
        // the SAME head blueprint under the pig-house allowance uuid must include the
        // head's cost; under a player disc it must exclude it.
        BlockPos printerPos = new BlockPos(2, 1, 2);
        PrinterBlockEntity printer = poweredPrinter(helper, printerPos);
        Blueprint blueprint = headBlueprint();
        BlueprintFileStore store = BlueprintFileStore.forServer(helper.getLevel().getServer());

        UUID pigHouse = CuratedBlueprints.uuidFor(MC3DPrint.MOD_ID, "tristans_pig_house");
        store.save(pigHouse, blueprint);
        ItemStack official = new ItemStack(ModItems.BLUEPRINT_DISC.get());
        BlueprintDiscItem.writeBlueprint(official, pigHouse, blueprint, false); // official
        printer.inventory().setStackInSlot(PrinterBlockEntity.SLOT_TEMPLATE, official);
        int[] officialReport = printer.costReportPerTier();
        if (officialReport == null) {
            throw new GameTestAssertException("expected a cost report for the official disc");
        }
        int officialTotal = java.util.Arrays.stream(officialReport).sum();

        UUID playerId = store.save(blueprint);
        ItemStack playerDisc = new ItemStack(ModItems.BLUEPRINT_DISC.get());
        BlueprintDiscItem.writeBlueprint(playerDisc, playerId, blueprint, true);
        printer.inventory().setStackInSlot(PrinterBlockEntity.SLOT_TEMPLATE, playerDisc);
        int[] playerReport = printer.costReportPerTier();
        if (playerReport == null) {
            throw new GameTestAssertException("expected a cost report for the player disc");
        }
        int playerTotal = java.util.Arrays.stream(playerReport).sum();

        if (officialTotal <= playerTotal) {
            helper.fail("official pig-house disc must price the restricted head (official "
                    + officialTotal + " vs player " + playerTotal + ")");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty5", timeoutTicks = 100)
    public static void itemModeRefusesRestrictedTrophy(GameTestHelper helper) {
        BlockPos printerPos = new BlockPos(2, 1, 2);
        PrinterBlockEntity printer = poweredPrinter(helper, printerPos);
        printer.inventory().setStackInSlot(PrinterBlockEntity.SLOT_TEMPLATE,
                new ItemStack(Items.DRAGON_HEAD));

        helper.runAfterDelay(40, () -> {
            if (printer.state() != PrinterBlockEntity.State.NOT_PRINTABLE) {
                helper.fail("item mode must refuse a restricted trophy, got " + printer.state());
                return;
            }
            if (!printer.inventory().getStackInSlot(PrinterBlockEntity.SLOT_OUTPUT).isEmpty()) {
                helper.fail("no copy may be emitted");
                return;
            }
            helper.succeed();
        });
    }

    /**
     * NO_PRINT (wind-only) items are valued so they wind for a recycle payout, but the printer
     * must refuse them in item mode even on a tier-capable machine. wither_skeleton_skull is the
     * key case: it is ALSO on #print_restricted, so this proves NO_PRINT wins over the trophy gate.
     */
    @GameTest(template = "empty5", timeoutTicks = 120)
    public static void itemModeRefusesNoPrintTreasure(GameTestHelper helper) {
        PrinterBlockEntity printer = poweredPrinter(helper, new BlockPos(2, 1, 2)); // T4, clears all three tiers
        net.minecraft.world.item.Item[] windOnly = {
                Items.SADDLE, Items.NAME_TAG, Items.WITHER_SKELETON_SKULL, Items.DRAGON_EGG };
        stepRefusal(helper, printer, windOnly, 0);
    }

    // Load each wind-only item into the template slot in turn, asserting NOT_PRINTABLE + no output
    // after the printer has had a tick to recompute, before advancing to the next.
    private static void stepRefusal(GameTestHelper helper, PrinterBlockEntity printer,
                                    net.minecraft.world.item.Item[] items, int index) {
        printer.inventory().setStackInSlot(PrinterBlockEntity.SLOT_TEMPLATE, new ItemStack(items[index]));
        helper.runAfterDelay(30, () -> {
            if (printer.state() != PrinterBlockEntity.State.NOT_PRINTABLE) {
                helper.fail("item mode must refuse wind-only " + items[index] + ", got " + printer.state());
                return;
            }
            if (!printer.inventory().getStackInSlot(PrinterBlockEntity.SLOT_OUTPUT).isEmpty()) {
                helper.fail("no copy may be emitted for " + items[index]);
                return;
            }
            if (index + 1 < items.length) {
                stepRefusal(helper, printer, items, index + 1);
            } else {
                helper.succeed();
            }
        });
    }
}
