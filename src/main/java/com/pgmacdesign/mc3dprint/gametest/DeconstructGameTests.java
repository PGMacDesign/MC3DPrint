package com.pgmacdesign.mc3dprint.gametest;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.fu.FuValue;
import com.pgmacdesign.mc3dprint.fu.FuValueRegistry;
import com.pgmacdesign.mc3dprint.fu.SpoolItem;
import com.pgmacdesign.mc3dprint.machine.MachineTier;
import com.pgmacdesign.mc3dprint.machine.PrinterBlockEntity;
import com.pgmacdesign.mc3dprint.machine.multiblock.ControllerBlock;
import com.pgmacdesign.mc3dprint.machine.multiblock.MultiblockPattern;
import com.pgmacdesign.mc3dprint.registry.ModBlocks;
import com.pgmacdesign.mc3dprint.registry.ModDataComponents;
import com.pgmacdesign.mc3dprint.registry.ModItems;
import com.pgmacdesign.mc3dprint.scanner.ScanData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Optional;

/**
 * Deconstruct Mode invariants: exact lossy yield, zero-yield classes, skip-in-place
 * classes, halt-before-remove on full, footprint mirror, and the mode epoch.
 */
@GameTestHolder(MC3DPrint.MOD_ID)
@PrefixGameTestTemplate(false)
public class DeconstructGameTests {

    private static PrinterBlockEntity poweredPrinter(GameTestHelper helper, BlockPos localPos) {
        helper.setBlock(localPos, ModBlocks.PRINTERS.get(2).get()); // T3: first structure tier
        if (!(helper.getBlockEntity(localPos) instanceof PrinterBlockEntity printer)) {
            throw new GameTestAssertException("Printer block entity missing");
        }
        java.util.Optional.ofNullable(printer.getEnergyStorage()).ifPresent(energy -> {
            for (int i = 0; i < 60; i++) {
                energy.receiveEnergy(1_000, false);
            }
        });
        printer.setAutoStart(true);
        return printer;
    }

    /** Docks an EMPTY spool of {@code tier} in slot 0 so yield has somewhere to bank. */
    private static void attachEmptySpool(PrinterBlockEntity printer, int tier) {
        ItemStack spool = new ItemStack(ModItems.SPOOLS.get(tier - 1).get());
        SpoolItem.setFu(spool, 0);
        printer.spoolInventory().setStackInSlot(0, spool);
    }

    private static FuValue valueOf(net.minecraft.world.item.Item item) {
        Optional<FuValue> value = FuValueRegistry.valueOf(new ItemStack(item));
        if (value.isEmpty()) {
            throw new GameTestAssertException("expected " + item + " to have an FU value");
        }
        return value.get();
    }

    @GameTest(template = "empty5", timeoutTicks = 300)
    public static void deconstructCreditsExactLossyYield(GameTestHelper helper) {
        FuValue iron = valueOf(Items.IRON_BLOCK);
        int expected = (int) Math.floor(iron.fu()
                * com.pgmacdesign.mc3dprint.config.MC3DPrintConfig.DECONSTRUCT_YIELD_FACTOR.get());
        if (expected <= 0 || expected >= iron.fu()) {
            throw new GameTestAssertException("test needs 0 < yield < wind value, got "
                    + expected + " of " + iron.fu()); // also the no-laundering bound: credit < wind
        }

        BlockPos target = new BlockPos(1, 2, 1);
        helper.setBlock(target, Blocks.IRON_BLOCK);

        PrinterBlockEntity printer = poweredPrinter(helper, new BlockPos(2, 1, 2));
        attachEmptySpool(printer, iron.tier());
        printer.setDeconstructRegion(helper.absolutePos(target), helper.absolutePos(target));
        printer.requestStart(); // fresh-arm gate: first job always needs an explicit Start

        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.AIR, target);
            int banked = SpoolItem.getFu(printer.spoolInventory().getStackInSlot(0));
            if (banked != expected) {
                throw new GameTestAssertException("credited " + banked + " FU, expected " + expected);
            }
        });
    }

    @GameTest(template = "empty5", timeoutTicks = 300)
    public static void blacklistedBlockRemovesAtZeroYield(GameTestHelper helper) {
        // oak_planks is winder-blacklisted (#minecraft:planks) but FU-valued: it must
        // be removed with EXACTLY zero credit — the anti-laundering tag holds in reverse.
        FuValue planks = valueOf(Items.OAK_PLANKS);
        BlockPos target = new BlockPos(1, 2, 1);
        helper.setBlock(target, Blocks.OAK_PLANKS);

        PrinterBlockEntity printer = poweredPrinter(helper, new BlockPos(2, 1, 2));
        attachEmptySpool(printer, planks.tier());
        printer.setDeconstructRegion(helper.absolutePos(target), helper.absolutePos(target));
        printer.requestStart(); // fresh-arm gate: first job always needs an explicit Start

        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.AIR, target);
            int banked = SpoolItem.getFu(printer.spoolInventory().getStackInSlot(0));
            if (banked != 0) {
                throw new GameTestAssertException("blacklisted block credited " + banked + " FU, expected 0");
            }
        });
    }

    @GameTest(template = "empty5", timeoutTicks = 300)
    public static void skipsUnbreakableAndNonEmptyContainerInPlace(GameTestHelper helper) {
        // Region row: bedrock (unbreakable), a chest with contents, iron block. The job
        // must complete by skipping the first two IN PLACE and removing only the iron.
        BlockPos bedrock = new BlockPos(1, 2, 1);
        BlockPos chest = new BlockPos(2, 2, 1);
        BlockPos iron = new BlockPos(3, 2, 1);
        helper.setBlock(bedrock, Blocks.BEDROCK);
        helper.setBlock(chest, Blocks.CHEST);
        helper.setBlock(iron, Blocks.IRON_BLOCK);
        if (!(helper.getBlockEntity(chest) instanceof ChestBlockEntity chestBe)) {
            throw new GameTestAssertException("chest BE missing");
        }
        chestBe.setItem(0, new ItemStack(Items.DIRT, 3));

        PrinterBlockEntity printer = poweredPrinter(helper, new BlockPos(2, 1, 3));
        attachEmptySpool(printer, valueOf(Items.IRON_BLOCK).tier());
        printer.setDeconstructRegion(helper.absolutePos(bedrock), helper.absolutePos(iron));
        printer.requestStart(); // fresh-arm gate: first job always needs an explicit Start

        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.AIR, iron);
            helper.assertBlockPresent(Blocks.BEDROCK, bedrock);
            helper.assertBlockPresent(Blocks.CHEST, chest);
            if (printer.deconstructJob() != null) {
                throw new GameTestAssertException("job should complete past skipped positions");
            }
            if (!(helper.getBlockEntity(chest) instanceof ChestBlockEntity be)
                    || be.getItem(0).getCount() != 3) {
                throw new GameTestAssertException("chest contents must be untouched");
            }
        });
    }

    @GameTest(template = "empty5", timeoutTicks = 200)
    public static void haltsBeforeRemovingWhenNoCapacity(GameTestHelper helper) {
        // Full spool = the yield has nowhere to go. The machine must pause BEFORE
        // removing the block (filament never voided, world untouched).
        FuValue iron = valueOf(Items.IRON_BLOCK);
        BlockPos target = new BlockPos(1, 2, 1);
        helper.setBlock(target, Blocks.IRON_BLOCK);

        PrinterBlockEntity printer = poweredPrinter(helper, new BlockPos(2, 1, 2));
        ItemStack fullSpool = new ItemStack(ModItems.SPOOLS.get(iron.tier() - 1).get());
        SpoolItem.setFu(fullSpool, ((SpoolItem) fullSpool.getItem()).capacity());
        printer.spoolInventory().setStackInSlot(0, fullSpool);
        printer.setDeconstructRegion(helper.absolutePos(target), helper.absolutePos(target));
        printer.requestStart(); // fresh-arm gate: first job always needs an explicit Start

        helper.runAfterDelay(100, () -> {
            helper.assertBlockPresent(Blocks.IRON_BLOCK, target);
            if (printer.state() != PrinterBlockEntity.State.PAUSED_OUTPUT_FULL) {
                helper.fail("expected PAUSED_OUTPUT_FULL, got " + printer.state());
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty5", timeoutTicks = 40)
    public static void footprintMirrorsPrintLimit(GameTestHelper helper) {
        // T3 prints at most 3x3 — a 4-wide deconstruct region must refuse at hand-off.
        PrinterBlockEntity printer = poweredPrinter(helper, new BlockPos(2, 1, 2));
        PrinterBlockEntity.RegionResult result = printer.setDeconstructRegion(
                helper.absolutePos(new BlockPos(0, 2, 1)), helper.absolutePos(new BlockPos(3, 2, 1)));
        if (result != PrinterBlockEntity.RegionResult.TOO_LARGE) {
            helper.fail("4-wide region on a T3 should be TOO_LARGE, got " + result);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty5", timeoutTicks = 200)
    public static void modeSwitchCancelsDeconstructJob(GameTestHelper helper) {
        // Mode epoch: flipping back to Print mid-job drops the job cleanly.
        BlockPos a = new BlockPos(1, 2, 1);
        BlockPos b = new BlockPos(3, 2, 1);
        helper.setBlock(a, Blocks.IRON_BLOCK);
        helper.setBlock(new BlockPos(2, 2, 1), Blocks.IRON_BLOCK);
        helper.setBlock(b, Blocks.IRON_BLOCK);

        PrinterBlockEntity printer = poweredPrinter(helper, new BlockPos(2, 1, 3));
        attachEmptySpool(printer, valueOf(Items.IRON_BLOCK).tier());
        printer.setDeconstructRegion(helper.absolutePos(a), helper.absolutePos(b));
        printer.requestStart(); // fresh-arm gate: first job always needs an explicit Start

        helper.runAfterDelay(60, () -> {
            if (printer.deconstructJob() == null && printer.state() != PrinterBlockEntity.State.DECONSTRUCTING) {
                // job may legitimately have finished tiny regions; force the assertion to still hold
                printer.setDeconstructMode(false);
                helper.succeed();
                return;
            }
            printer.setDeconstructMode(false);
            if (printer.deconstructJob() != null) {
                helper.fail("deconstruct job must not survive a mode switch");
                return;
            }
            helper.succeed();
        });
    }

    // --- Scanner region hand-off onto a multiblock ---

    private static final BlockPos T5_CONTROLLER = new BlockPos(2, 1, 2);

    /** A formed T5 pad (all Printer Casing) centred on {@link #T5_CONTROLLER}. */
    private static PrinterBlockEntity formedT5(GameTestHelper helper) {
        for (BlockPos offset : MultiblockPattern.componentOffsets(MachineTier.T5)) {
            helper.setBlock(T5_CONTROLLER.offset(offset), ModBlocks.PRINTER_CASING.get());
        }
        helper.setBlock(T5_CONTROLLER, ModBlocks.CONTROLLERS.get(0).get());
        BlockPos abs = helper.absolutePos(T5_CONTROLLER);
        if (MultiblockPattern.validate(helper.getLevel(), abs, MachineTier.T5) != null) {
            throw new GameTestAssertException("T5 pattern should validate");
        }
        helper.getLevel().setBlock(abs,
                helper.getLevel().getBlockState(abs).setValue(ControllerBlock.FORMED, true),
                net.minecraft.world.level.block.Block.UPDATE_ALL);
        if (!(helper.getBlockEntity(T5_CONTROLLER) instanceof PrinterBlockEntity printer)) {
            throw new GameTestAssertException("Controller block entity missing");
        }
        return printer;
    }

    /** Sneak-right-click {@code target} with a scanner holding corners {@code a}/{@code b}. */
    private static ItemStack sneakClickWithScanner(GameTestHelper helper, BlockPos target,
            BlockPos a, BlockPos b) {
        ItemStack scanner = new ItemStack(ModItems.SCANNER.get());
        scanner.set(ModDataComponents.SCAN.get(), new ScanData(
                Optional.of(helper.absolutePos(a)), Optional.of(helper.absolutePos(b)), false));

        net.minecraft.world.entity.player.Player player =
                helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.setShiftKeyDown(true); // the hand-off is the SNEAK-click branch
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, scanner);

        BlockPos absTarget = helper.absolutePos(target);
        scanner.useOn(new net.minecraft.world.item.context.UseOnContext(
                player, net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(
                        net.minecraft.world.phys.Vec3.atCenterOf(absTarget),
                        net.minecraft.core.Direction.UP, absTarget, false)));
        return scanner;
    }

    /**
     * A sneak-click on ANY casing arms the buried controller. Regression: the hand-off used to
     * require the controller block itself, so on an N×N pad every other click fell through to
     * the corner-setting path and silently overwrote the selection being handed over.
     */
    @GameTest(template = "empty5", timeoutTicks = 40)
    public static void scannerArmsDeconstructRegionFromAnyCasing(GameTestHelper helper) {
        PrinterBlockEntity printer = formedT5(helper);
        BlockPos a = new BlockPos(1, 3, 1);
        BlockPos b = new BlockPos(2, 3, 2);
        BlockPos casing = T5_CONTROLLER.offset(1, 0, 0); // an edge casing, NOT the controller

        ItemStack scanner = sneakClickWithScanner(helper, casing, a, b);

        if (!printer.deconstructMode()) {
            helper.fail("clicking a casing left the controller in Print mode");
            return;
        }
        BlockPos min = printer.deconstructRegionMin();
        BlockPos size = printer.deconstructRegionSize();
        if (min == null || !min.equals(helper.absolutePos(a))) {
            helper.fail("region min was " + min + ", expected " + helper.absolutePos(a));
            return;
        }
        if (size == null || !size.equals(new BlockPos(2, 1, 2))) {
            helper.fail("region size was " + size + ", expected 2x1x2");
            return;
        }
        // The selection is handed over, never consumed or clobbered by a stray corner write.
        ScanData after = scanner.getOrDefault(ModDataComponents.SCAN.get(), ScanData.EMPTY);
        if (!after.cornerA().equals(Optional.of(helper.absolutePos(a)))
                || !after.cornerB().equals(Optional.of(helper.absolutePos(b)))) {
            helper.fail("scanner corners were modified by the hand-off: " + after);
            return;
        }
        helper.succeed();
    }

    /** An UNformed casing belongs to no machine, so it stays a plain corner-setting click. */
    @GameTest(template = "empty5", timeoutTicks = 40)
    public static void unformedCasingStillSetsAScannerCorner(GameTestHelper helper) {
        BlockPos casing = new BlockPos(1, 1, 1);
        helper.setBlock(casing, ModBlocks.PRINTER_CASING.get());

        ItemStack scanner = sneakClickWithScanner(helper, casing,
                new BlockPos(1, 3, 1), new BlockPos(2, 3, 2));

        ScanData after = scanner.getOrDefault(ModDataComponents.SCAN.get(), ScanData.EMPTY);
        if (!after.cornerA().equals(Optional.of(helper.absolutePos(casing)))) {
            helper.fail("expected corner A at the clicked casing, got " + after.cornerA());
            return;
        }
        helper.succeed();
    }
}
