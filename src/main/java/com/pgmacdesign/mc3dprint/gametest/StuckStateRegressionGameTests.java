package com.pgmacdesign.mc3dprint.gametest;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.fu.SpoolItem;
import com.pgmacdesign.mc3dprint.machine.MachineTier;
import com.pgmacdesign.mc3dprint.machine.PrinterBlockEntity;
import com.pgmacdesign.mc3dprint.machine.multiblock.ControllerBlock;
import com.pgmacdesign.mc3dprint.machine.multiblock.MultiblockPattern;
import com.pgmacdesign.mc3dprint.registry.ModBlocks;
import com.pgmacdesign.mc3dprint.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Regressions for the "permanently Not Printable" fabricator wedge: Deconstruct
 * Mode with no armed region used to freeze NOT_PRINTABLE forever (the decon idle
 * branch never re-resolved error states and the raw status persisted through
 * relocation NBT). The fix is three-layered (a dedicated self-resolving
 * NO_REGION state, a load-time status sanitize, and a collapse-time reset to a
 * Print-mode baseline) and each layer gets its own test here.
 */
@GameTestHolder(MC3DPrint.MOD_ID)
@PrefixGameTestTemplate(false)
public class StuckStateRegressionGameTests {

    private static final BlockPos CONTROLLER_POS = new BlockPos(2, 1, 2);

    private static PrinterBlockEntity buildFormedT5(GameTestHelper helper) {
        for (BlockPos offset : MultiblockPattern.componentOffsets(MachineTier.T5)) {
            helper.setBlock(CONTROLLER_POS.offset(offset), ModBlocks.PRINTER_CASING.get());
        }
        helper.setBlock(CONTROLLER_POS, ModBlocks.CONTROLLERS.get(0).get());
        if (!(helper.getBlockEntity(CONTROLLER_POS) instanceof PrinterBlockEntity printer)) {
            throw new GameTestAssertException("Controller block entity missing");
        }
        for (int i = 0; i < 60; i++) {
            printer.getEnergyStorage().receiveEnergy(16_000, false);
        }
        BlockPos abs = helper.absolutePos(CONTROLLER_POS);
        if (MultiblockPattern.validate(helper.getLevel(), abs, MachineTier.T5) != null) {
            throw new GameTestAssertException("T5 pattern should validate");
        }
        BlockState state = helper.getLevel().getBlockState(abs);
        helper.getLevel().setBlock(abs, state.setValue(ControllerBlock.FORMED, true), Block.UPDATE_ALL);
        return printer;
    }

    /**
     * The reported repro path stays honest: printing a T3 item from a T4 spool
     * that runs dry pauses at NO_FILAMENT (never an error state), and emptying
     * the machine returns it to IDLE.
     */
    @GameTest(template = "empty5", timeoutTicks = 400)
    public static void itemRunsDryOnHigherTierSpoolPausesThenClears(GameTestHelper helper) {
        PrinterBlockEntity printer = buildFormedT5(helper);
        printer.setAutoStart(true);
        ItemStack spool = new ItemStack(ModItems.SPOOLS.get(3).get()); // T4
        SpoolItem.setFu(spool, 3); // covers one 10@3 print (12 T3 down-converted), then dry
        printer.spoolInventory().setStackInSlot(0, spool);
        printer.inventory().setStackInSlot(PrinterBlockEntity.SLOT_TEMPLATE, new ItemStack(Items.GUNPOWDER));

        helper.runAfterDelay(120, () -> {
            PrinterBlockEntity.State observed = printer.state();
            if (observed != PrinterBlockEntity.State.PAUSED_NO_FILAMENT) {
                helper.fail("Ran-dry printer should pause NO_FILAMENT, got " + observed);
                return;
            }
            printer.inventory().setStackInSlot(PrinterBlockEntity.SLOT_TEMPLATE, ItemStack.EMPTY);
            printer.inventory().setStackInSlot(PrinterBlockEntity.SLOT_OUTPUT, ItemStack.EMPTY);
            printer.spoolInventory().setStackInSlot(0, ItemStack.EMPTY);
        });
        helper.runAfterDelay(140, () -> {
            if (printer.state() != PrinterBlockEntity.State.IDLE) {
                helper.fail("Emptied machine stuck at " + printer.state());
                return;
            }
            helper.succeed();
        });
    }

    /**
     * Deconstruct Mode with no region resolves to the self-explanatory NO_REGION
     * (not NOT_PRINTABLE), stays NO_REGION through a Start press, and toggling
     * back to Print mode recovers to IDLE. This was the permanent wedge.
     */
    @GameTest(template = "empty5", timeoutTicks = 400)
    public static void deconWithoutRegionShowsNoRegionAndRecovers(GameTestHelper helper) {
        PrinterBlockEntity printer = buildFormedT5(helper);
        printer.setAutoStart(true);
        printer.setDeconstructMode(true); // mode button; no region ever armed
        helper.runAfterDelay(20, () -> {
            if (printer.state() != PrinterBlockEntity.State.NO_REGION) {
                helper.fail("Decon with no region should show NO_REGION, got " + printer.state());
                return;
            }
            printer.requestStart(); // the press that used to freeze NOT_PRINTABLE
        });
        helper.runAfterDelay(80, () -> {
            if (printer.state() != PrinterBlockEntity.State.NO_REGION) {
                helper.fail("Start with no region should hold NO_REGION, got " + printer.state());
                return;
            }
            printer.setDeconstructMode(false); // the visible way out
        });
        helper.runAfterDelay(100, () -> {
            if (printer.state() != PrinterBlockEntity.State.IDLE) {
                helper.fail("Back in Print mode the empty machine should idle, got " + printer.state());
                return;
            }
            helper.succeed();
        });
    }

    /**
     * Reload sanitize: a status snapshot without restored work never survives
     * NBT. A machine saved mid-"NO_REGION" round-trips into a fresh BE as IDLE
     * and then re-resolves from live conditions (still decon mode, still no
     * region, so NO_REGION again: self-describing, never NOT_PRINTABLE).
     */
    @GameTest(template = "empty5", timeoutTicks = 400)
    public static void staleStatusNeverSurvivesNbtRoundTrip(GameTestHelper helper) {
        PrinterBlockEntity printer = buildFormedT5(helper);
        printer.setAutoStart(true);
        printer.setDeconstructMode(true);
        helper.runAfterDelay(10, printer::requestStart);
        helper.runAfterDelay(60, () -> {
            if (printer.state() != PrinterBlockEntity.State.NO_REGION) {
                helper.fail("Setup expected NO_REGION, got " + printer.state());
                return;
            }
            //? if >=1.21.5 {
            /*net.minecraft.world.level.storage.TagValueOutput out =
                    net.minecraft.world.level.storage.TagValueOutput.createWithContext(
                            net.minecraft.util.ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
            printer.saveWithoutMetadata(out);
            net.minecraft.nbt.CompoundTag saved = out.buildResult();
            *///?} else {
            net.minecraft.nbt.CompoundTag saved = printer.saveWithoutMetadata(helper.getLevel().registryAccess());
            //?}
            helper.setBlock(CONTROLLER_POS, net.minecraft.world.level.block.Blocks.AIR);
            helper.setBlock(CONTROLLER_POS, ModBlocks.CONTROLLERS.get(0).get());
            if (!(helper.getBlockEntity(CONTROLLER_POS) instanceof PrinterBlockEntity fresh)) {
                helper.fail("Fresh controller BE missing");
                return;
            }
            //? if >=1.21.5 {
            /*fresh.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(
                    net.minecraft.util.ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved));
            *///?} else {
            fresh.loadWithComponents(saved, helper.getLevel().registryAccess());
            //?}
            // Sanitized on load: no restored job means no restored status.
            if (fresh.state() != PrinterBlockEntity.State.IDLE) {
                helper.fail("Loaded status should sanitize to IDLE, got " + fresh.state());
                return;
            }
            BlockPos abs = helper.absolutePos(CONTROLLER_POS);
            BlockState st = helper.getLevel().getBlockState(abs);
            helper.getLevel().setBlock(abs, st.setValue(ControllerBlock.FORMED, true), Block.UPDATE_ALL);
        });
        helper.runAfterDelay(140, () -> {
            if (!(helper.getBlockEntity(CONTROLLER_POS) instanceof PrinterBlockEntity fresh)) {
                helper.fail("Reloaded BE missing");
                return;
            }
            // Ticking again: live conditions (decon, no region) resolve NO_REGION.
            if (fresh.state() != PrinterBlockEntity.State.NO_REGION) {
                helper.fail("Reloaded decon machine should resolve NO_REGION, got " + fresh.state());
                return;
            }
            helper.succeed();
        });
    }

    /**
     * Breaking a formed controller (the collapse-to-item relocate path) resets
     * the snapshot to a Print-mode baseline while keeping the stored RF: the
     * collapsed item's NBT carries no deconstruct mode, no stale status, and the
     * full energy buffer. This is the player's escape hatch for any stuck state.
     */
    @GameTest(template = "empty5", timeoutTicks = 400)
    public static void collapseResetsModeAndStatusButKeepsRf(GameTestHelper helper) {
        PrinterBlockEntity printer = buildFormedT5(helper);
        printer.setAutoStart(true);
        printer.setDeconstructMode(true);
        helper.runAfterDelay(10, printer::requestStart);
        helper.runAfterDelay(60, () -> {
            if (printer.state() != PrinterBlockEntity.State.NO_REGION) {
                helper.fail("Setup expected NO_REGION, got " + printer.state());
                return;
            }
            int storedRf = printer.getEnergyStorage().getEnergyStored();
            if (storedRf <= 0) {
                helper.fail("Setup expected stored RF");
                return;
            }
            BlockPos abs = helper.absolutePos(CONTROLLER_POS);
            BlockState st = helper.getLevel().getBlockState(abs);
            // The real player-break collapse path (drops the machine item).
            st.getBlock().playerWillDestroy(helper.getLevel(), abs, st,
                    helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL));

            ItemStack collapsed = helper.getLevel()
                    .getEntitiesOfClass(ItemEntity.class, new AABB(abs).inflate(3)).stream()
                    .map(ItemEntity::getItem)
                    .filter(s -> s.getItem() instanceof com.pgmacdesign.mc3dprint.machine.multiblock.FabricatorBlockItem)
                    .findFirst().orElse(ItemStack.EMPTY);
            if (collapsed.isEmpty()) {
                helper.fail("Collapse did not drop the fabricator item");
                return;
            }
            net.minecraft.nbt.CompoundTag be = readBlockEntityData(collapsed);
            if (be == null) {
                helper.fail("Collapsed item carries no block entity data");
                return;
            }
            if (com.pgmacdesign.mc3dprint.compat.NbtCompat.getBoolean(be, "DeconMode")) {
                helper.fail("Collapsed item still in Deconstruct Mode");
                return;
            }
            PrinterBlockEntity.State saved = PrinterBlockEntity.State.byOrdinal(
                    com.pgmacdesign.mc3dprint.compat.NbtCompat.getInt(be, "State"));
            if (saved != PrinterBlockEntity.State.IDLE) {
                helper.fail("Collapsed item saved status " + saved + " (expected IDLE)");
                return;
            }
            int savedRf = com.pgmacdesign.mc3dprint.compat.NbtCompat.getInt(be, "Energy");
            if (savedRf != storedRf) {
                helper.fail("Collapse lost RF: had " + storedRf + ", item carries " + savedRf);
                return;
            }
            helper.succeed();
        });
    }

    /** Unwraps the collapsed item's block-entity NBT (stored under the BE data component). */
    private static net.minecraft.nbt.CompoundTag readBlockEntityData(ItemStack stack) {
        net.minecraft.world.item.component.CustomData data =
                stack.get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA);
        return data == null ? null : data.copyTag();
    }
}
