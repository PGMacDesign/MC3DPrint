package com.pgmacdesign.mc3dprint.gametest;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.blueprint.Blueprint;
import com.pgmacdesign.mc3dprint.blueprint.BlueprintBlockState;
import com.pgmacdesign.mc3dprint.blueprint.BlueprintFileStore;
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
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * A two-block piece costs what its item costs, once.
 *
 * <p>A door, a bed and a double plant are two placement entries holding the same block, and both
 * halves resolve through {@code asItem()} to the same item, so the print used to bill for the piece
 * twice. The pair is charged once now, but only when both halves are really in the print: an
 * unpaired secondary half keeps paying full price, because breaking a lone bed head or plant top
 * still yields the whole item, and a free one would be a free item.
 */
@GameTestHolder(MC3DPrint.MOD_ID)
@PrefixGameTestTemplate(false)
public class TwoBlockPieceCostGameTests {

    private static final BlockPos PRINTER = new BlockPos(2, 1, 2);

    private static PrinterBlockEntity loadedPrinter(GameTestHelper helper, Blueprint blueprint) {
        helper.setBlock(PRINTER, ModBlocks.PRINTERS.get(2).get()); // T3
        if (!(helper.getBlockEntity(PRINTER) instanceof PrinterBlockEntity printer)) {
            throw new GameTestAssertException("Printer block entity missing");
        }
        printer.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ENERGY).ifPresent(energy -> {
            for (int i = 0; i < 60; i++) {
                energy.receiveEnergy(1_000, false);
            }
        });
        BlueprintFileStore store = BlueprintFileStore.forServer(helper.getLevel().getServer());
        ItemStack disc = new ItemStack(ModItems.BLUEPRINT_DISC.get());
        BlueprintDiscItem.writeBlueprint(disc, store.save(blueprint), blueprint);
        printer.inventory().setStackInSlot(PrinterBlockEntity.SLOT_TEMPLATE, disc);
        return printer;
    }

    private static int predictedTotal(PrinterBlockEntity printer) {
        int[] perTier = printer.costReportPerTier();
        if (perTier == null) {
            throw new GameTestAssertException("no cost report for a loaded disc");
        }
        int total = 0;
        for (int c : perTier) {
            total += c;
        }
        return total;
    }

    /** Both halves of a door are one door, and the calculator and the print must agree on that. */
    @GameTest(template = "empty5", timeoutTicks = 300)
    public static void aPairedDoorIsChargedOnce(GameTestHelper helper) {
        Blueprint blueprint = Blueprint.builder("gametest-paired-door", 1, 2, 1)
                .set(0, 0, 0, BlueprintBlockState.parse("minecraft:oak_door[half=lower,facing=north]"))
                .set(0, 1, 0, BlueprintBlockState.parse("minecraft:oak_door[half=upper,facing=north]"))
                .build();
        PrinterBlockEntity printer = loadedPrinter(helper, blueprint);

        int oneDoor = printer.itemFuCost(new ItemStack(Items.OAK_DOOR));
        if (oneDoor <= 0) {
            throw new GameTestAssertException("oak_door must carry an FU value for this test to"
                    + " mean anything, got " + oneDoor);
        }
        final int predicted = predictedTotal(printer);
        if (predicted != oneDoor) {
            throw new GameTestAssertException("a door costs " + oneDoor
                    + " but the two halves were quoted at " + predicted);
        }

        ItemStack spool = new ItemStack(ModItems.SPOOLS.get(0).get());
        SpoolItem.setFu(spool, 10_000);
        printer.spoolInventory().setStackInSlot(0, spool);
        printer.setAutoStart(true);
        helper.succeedWhen(() -> {
            if (printer.activeJob() != null) {
                throw new GameTestAssertException("print still running");
            }
            int consumed = 10_000 - SpoolItem.getFu(printer.spoolInventory().getStackInSlot(0));
            if (consumed != predicted) {
                throw new GameTestAssertException("consumed " + consumed
                        + " FU for one door but the calculator predicted " + predicted);
            }
        });
    }

    /** A bed is the other shape of pair: the partner sits beside it, not below it. */
    @GameTest(template = "empty5", timeoutTicks = 60)
    public static void aPairedBedIsChargedOnce(GameTestHelper helper) {
        Blueprint blueprint = Blueprint.builder("gametest-paired-bed", 1, 1, 2)
                .set(0, 0, 0, BlueprintBlockState.parse("minecraft:red_bed[facing=south,part=foot]"))
                .set(0, 0, 1, BlueprintBlockState.parse("minecraft:red_bed[facing=south,part=head]"))
                .build();
        PrinterBlockEntity printer = loadedPrinter(helper, blueprint);

        int oneBed = printer.itemFuCost(new ItemStack(Items.RED_BED));
        if (oneBed <= 0) {
            throw new GameTestAssertException("red_bed must carry an FU value, got " + oneBed);
        }
        int predicted = predictedTotal(printer);
        if (predicted != oneBed) {
            throw new GameTestAssertException("a bed costs " + oneBed
                    + " but the two halves were quoted at " + predicted);
        }
        helper.succeed();
    }

    /**
     * The guard that keeps the discount from becoming a free-item hole. A scan can clip a piece in
     * half, and breaking a lone bed head still yields a whole bed, so the unpaired half pays.
     */
    @GameTest(template = "empty5", timeoutTicks = 60)
    public static void anUnpairedSecondaryHalfStillPaysFullPrice(GameTestHelper helper) {
        Blueprint blueprint = Blueprint.builder("gametest-lone-bed-head", 1, 1, 1)
                .set(0, 0, 0, BlueprintBlockState.parse("minecraft:red_bed[facing=south,part=head]"))
                .build();
        PrinterBlockEntity printer = loadedPrinter(helper, blueprint);

        int oneBed = printer.itemFuCost(new ItemStack(Items.RED_BED));
        int predicted = predictedTotal(printer);
        if (predicted != oneBed) {
            throw new GameTestAssertException("a bed head with no foot in the print must cost a"
                    + " full bed (" + oneBed + "), got " + predicted
                    + "; a free one is a free bed once the player breaks it");
        }
        helper.succeed();
    }

    /** Same guard for the vertical shape: an upper door half whose lower half was cropped out. */
    @GameTest(template = "empty5", timeoutTicks = 60)
    public static void anUnpairedUpperHalfStillPaysFullPrice(GameTestHelper helper) {
        Blueprint blueprint = Blueprint.builder("gametest-lone-door-top", 1, 1, 1)
                .set(0, 0, 0, BlueprintBlockState.parse("minecraft:oak_door[half=upper,facing=north]"))
                .build();
        PrinterBlockEntity printer = loadedPrinter(helper, blueprint);

        int oneDoor = printer.itemFuCost(new ItemStack(Items.OAK_DOOR));
        int predicted = predictedTotal(printer);
        if (predicted != oneDoor) {
            throw new GameTestAssertException("an upper door half with no lower half must cost a"
                    + " full door (" + oneDoor + "), got " + predicted);
        }
        helper.succeed();
    }

    /**
     * Two upper halves stacked on each other must not pair with each other. Same block, and the
     * one below is at the position a partner would occupy, so a blockId-only match would print
     * both free.
     */
    @GameTest(template = "empty5", timeoutTicks = 60)
    public static void twoSecondaryHalvesDoNotPairWithEachOther(GameTestHelper helper) {
        Blueprint blueprint = Blueprint.builder("gametest-double-top", 1, 2, 1)
                .set(0, 0, 0, BlueprintBlockState.parse("minecraft:oak_door[half=upper,facing=north]"))
                .set(0, 1, 0, BlueprintBlockState.parse("minecraft:oak_door[half=upper,facing=north]"))
                .build();
        PrinterBlockEntity printer = loadedPrinter(helper, blueprint);

        int oneDoor = printer.itemFuCost(new ItemStack(Items.OAK_DOOR));
        int predicted = predictedTotal(printer);
        if (predicted != oneDoor * 2) {
            throw new GameTestAssertException("two unpaired upper halves must each pay full price ("
                    + (oneDoor * 2) + "), got " + predicted);
        }
        helper.succeed();
    }

    /** A stack of doors is still one charge per door, not one charge for the whole column. */
    @GameTest(template = "empty5", timeoutTicks = 60)
    public static void stackedPairsAreChargedPerPair(GameTestHelper helper) {
        Blueprint blueprint = Blueprint.builder("gametest-stacked-doors", 1, 4, 1)
                .set(0, 0, 0, BlueprintBlockState.parse("minecraft:oak_door[half=lower,facing=north]"))
                .set(0, 1, 0, BlueprintBlockState.parse("minecraft:oak_door[half=upper,facing=north]"))
                .set(0, 2, 0, BlueprintBlockState.parse("minecraft:oak_door[half=lower,facing=north]"))
                .set(0, 3, 0, BlueprintBlockState.parse("minecraft:oak_door[half=upper,facing=north]"))
                .build();
        PrinterBlockEntity printer = loadedPrinter(helper, blueprint);

        int oneDoor = printer.itemFuCost(new ItemStack(Items.OAK_DOOR));
        int predicted = predictedTotal(printer);
        if (predicted != oneDoor * 2) {
            throw new GameTestAssertException("two stacked doors should cost " + (oneDoor * 2)
                    + ", got " + predicted);
        }
        helper.succeed();
    }
}
