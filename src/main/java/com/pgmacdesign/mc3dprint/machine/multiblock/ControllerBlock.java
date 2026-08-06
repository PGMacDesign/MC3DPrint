package com.pgmacdesign.mc3dprint.machine.multiblock;

import com.pgmacdesign.mc3dprint.compat.InteractionCompat;
import com.pgmacdesign.mc3dprint.machine.MachineTier;
import com.pgmacdesign.mc3dprint.machine.PrinterBlock;
import com.pgmacdesign.mc3dprint.machine.PrinterBlockEntity;
import com.pgmacdesign.mc3dprint.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.event.level.BlockEvent;

import javax.annotation.Nullable;

/**
 * T5-T8 multiblock controller. Unformed: right-click validates the casing
 * pattern and forms the machine. Formed: behaves as a printer of its tier.
 *
 * Breaking a formed controller collapses the whole multiblock into a single
 * item carrying the full machine state ("build once, relocate freely").
 */
public class ControllerBlock extends PrinterBlock {
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    public ControllerBlock(MachineTier tier, Properties properties) {
        super(tier, properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FORMED, false)
                .setValue(PrinterBlock.EMITTING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder); // EMITTING (Redstone Module output)
        builder.add(FORMED);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (state.getValue(FORMED)) {
            return super.useWithoutItem(state, level, pos, player, hit);
        }
        if (!level.isClientSide()) {
            Component error = MultiblockPattern.validate(level, pos, tier());
            if (error != null) {
                com.pgmacdesign.mc3dprint.compat.MsgCompat.actionBar(player, error);
            } else {
                level.setBlock(pos, state.setValue(FORMED, true), Block.UPDATE_ALL);
                setComponentsActive(level, pos, tier(), true, null);
                com.pgmacdesign.mc3dprint.compat.MsgCompat.actionBar(player, Component.translatable("message.mc3dprint.multiblock_formed",
                        tier().number()));
                level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.6F, 1.2F);
            }
        }
        return InteractionCompat.sidedSuccess(level.isClientSide());
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && state.getValue(FORMED)
                && level.getBlockEntity(pos) instanceof PrinterBlockEntity printer) {
            // Collapse = relocate: snapshot a clean baseline (Print mode, idle, no
            // region/trigger) so a stuck mode or status can never ride along in the
            // item NBT. Inventory, upgrades and stored RF survive untouched.
            printer.resetForRelocation();
            printer.markCollapsing();

            ItemStack collapsed = new ItemStack(this);
            CustomData.update(DataComponents.CUSTOM_DATA, collapsed,
                    tag -> tag.putBoolean(FabricatorBlockItem.TAG_COLLAPSED, true));
            //? if >=1.21.5 {
            /*net.minecraft.world.level.storage.TagValueOutput printerOut =
                    net.minecraft.world.level.storage.TagValueOutput.createWithContext(
                            net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess());
            printer.saveWithoutMetadata(printerOut);
            BlockItem.setBlockEntityData(collapsed, ModBlockEntities.PRINTER.get(), printerOut);
            *///?} else {
            BlockItem.setBlockEntityData(collapsed, ModBlockEntities.PRINTER.get(),
                    printer.saveWithoutMetadata(level.registryAccess()));
            //?}

            for (BlockPos offset : MultiblockPattern.componentOffsets(tier())) {
                BlockPos componentPos = pos.offset(offset);
                if (!isOwnComponent(level, componentPos)) {
                    continue;
                }
                // The WHOLE machine — casings AND the premium corners (T8 Awakened Draconium) —
                // is consumed into the collapsed controller item and rebuilt on re-place by
                // FabricatorBlockItem.reformComponents. We deliberately do NOT drop the corners
                // here: dropping them while the re-form restored them for free was a refund
                // exploit (PGM-48). Relocate is now loss-free and refund-free.
                level.removeBlock(componentPos, false);
            }
            if (!player.getAbilities().instabuild) {
                level.addFreshEntity(new ItemEntity(level,
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, collapsed));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    private boolean isOwnComponent(Level level, BlockPos pos) {
        Block block = level.getBlockState(pos).getBlock();
        return block instanceof CasingBlock || isTierCorner(block);
    }

    /**
     * Whether {@code block} is this tier's premium corner block (T5 diamond,
     * T8 awakened draconium). Returns false when the tier has no corner block
     * (T6/T7 corners are plain casing) or the corner block is unavailable
     * (e.g. T8 with Draconic Evolution not loaded).
     */
    private boolean isTierCorner(Block block) {
        Block corner = MultiblockPattern.cornerBlock(tier());
        return corner != null && block == corner;
    }

    /**
     * Called by casings when one is broken — the machine unforms in place and
     * all of its still-present casings revert to the plain (inactive) look.
     *
     * @param excludePos a casing that is mid-removal (its own onRemove triggered
     *                   this) and must be skipped — re-setting its state would
     *                   resurrect the block we are in the middle of destroying.
     */
    public static void unform(Level level, BlockPos controllerPos, @Nullable BlockPos excludePos) {
        BlockState state = level.getBlockState(controllerPos);
        if (state.getBlock() instanceof ControllerBlock controller && state.getValue(FORMED)) {
            if (level.getBlockEntity(controllerPos) instanceof PrinterBlockEntity printer) {
                printer.cancelActiveJob();
            }
            // Clear the Redstone Module output in the same update: an unformed machine
            // does no work, so it must never be left holding a stale 15 for a tick.
            level.setBlock(controllerPos, state
                    .setValue(FORMED, false)
                    .setValue(PrinterBlock.EMITTING, false), Block.UPDATE_ALL);
            setComponentsActive(level, controllerPos, controller.tier(), false, excludePos);
            level.playSound(null, controllerPos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.6F, 1.0F);
        }
    }

    /**
     * Unforms any formed controller whose base footprint contains {@code brokenPos}
     * — i.e. {@code brokenPos} is one of its structural components. Shared by the
     * two removal paths: our own casings call this from {@link CasingBlock#onRemove},
     * and the premium CORNER blocks (the T8 Awakened Draconium) — foreign
     * blocks with no removal hook of ours — are caught by {@link #onBlockBreak}.
     * Scans the largest-tier footprint radius on the controller's Y plane, then
     * confirms {@code brokenPos} actually falls inside the found controller's own
     * (possibly smaller) footprint before unforming.
     */
    public static void unformContaining(Level level, BlockPos brokenPos) {
        if (level.isClientSide()) {
            return;
        }
        int maxHalf = MultiblockPattern.baseEdge(MachineTier.T8) / 2;
        for (int dx = -maxHalf; dx <= maxHalf; dx++) {
            for (int dz = -maxHalf; dz <= maxHalf; dz++) {
                if (dx == 0 && dz == 0) {
                    continue; // a component is never its own controller
                }
                BlockPos candidate = brokenPos.offset(dx, 0, dz);
                BlockState candidateState = level.getBlockState(candidate);
                if (candidateState.getBlock() instanceof ControllerBlock controller
                        && candidateState.getValue(FORMED)) {
                    int half = MultiblockPattern.baseEdge(controller.tier()) / 2;
                    if (Math.abs(dx) <= half && Math.abs(dz) <= half) {
                        // exclude brokenPos: it is mid-removal, so re-setting its state
                        // would resurrect the block being destroyed.
                        unform(level, candidate, brokenPos);
                    }
                }
            }
        }
    }

    /**
     * Forge block-break hook for the premium corner blocks. A {@link CasingBlock}
     * unforms its machine from its own {@code onRemove}, but the T8 corner is a
     * modded block we can't hook that way — so breaking an Awakened Draconium corner
     * would otherwise leave the machine formed. Catch that here. Our own
     * casings/controllers are skipped (they self-handle: casings
     * via onRemove, the controller via {@code playerWillDestroy}).
     */
    // 26.1 moved/renamed BlockEvent.BreakEvent -> level.block.BreakBlockEvent (getLevel/
    // getState/getPos are inherited from BlockEvent unchanged).
    //? if >=26.1 {
    /*public static void onBlockBreak(net.neoforged.neoforge.event.level.block.BreakBlockEvent event) {
    *///?} else {
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
    //?}
        if (!(event.getLevel() instanceof Level level) || level.isClientSide()) {
            return;
        }
        Block broken = event.getState().getBlock();
        if (broken instanceof CasingBlock || broken instanceof ControllerBlock) {
            return;
        }
        unformContaining(level, event.getPos());
    }

    /**
     * Flips the ACTIVE flag on every Printer Casing in the controller's base so
     * the structure glows when formed and goes dark when unformed, and assigns
     * each casing the part its TOP face plays in the unified printer (rail, corner
     * or bed) based on its offset. A tier's premium corner block (the T8 Awakened
     * Draconium) is not a casing and is left untouched, so it keeps
     * rendering as itself at the corners.
     */
    private static void setComponentsActive(Level level, BlockPos controllerPos, MachineTier tier,
                                            boolean active, @Nullable BlockPos excludePos) {
        for (BlockPos offset : MultiblockPattern.componentOffsets(tier)) {
            BlockPos componentPos = controllerPos.offset(offset);
            if (componentPos.equals(excludePos)) {
                continue;
            }
            BlockState componentState = level.getBlockState(componentPos);
            if (!(componentState.getBlock() instanceof CasingBlock)) {
                continue;
            }
            CasingBlock.CasingPart part = active
                    ? partForOffset(offset.getX(), offset.getZ(), tier)
                    : CasingBlock.CasingPart.NONE;
            if (componentState.getValue(CasingBlock.ACTIVE) != active
                    || componentState.getValue(CasingBlock.PART) != part) {
                level.setBlock(componentPos, componentState
                        .setValue(CasingBlock.ACTIVE, active)
                        .setValue(CasingBlock.PART, part), Block.UPDATE_ALL);
            }
        }
    }

    /**
     * Maps a casing's base-plane offset to its top-face part. +X=east, +Z=south.
     * Corners take the four CORNER_* posts, perimeter edges take a rail oriented
     * along the edge (E-W bar on the N/S edges, N-S bar on the E/W edges), and the
     * remaining interior tiles are the heated BED. Package-visible: the re-place
     * path ({@link FabricatorBlockItem#reformComponents}) restores the formed look
     * directly instead of routing through the right-click forming flow.
     */
    static CasingBlock.CasingPart partForOffset(int x, int z, MachineTier tier) {
        int half = MultiblockPattern.baseEdge(tier) / 2;
        if (Math.abs(x) == half && Math.abs(z) == half) {
            if (x < 0 && z < 0) return CasingBlock.CasingPart.CORNER_NW;
            if (x > 0 && z < 0) return CasingBlock.CasingPart.CORNER_NE;
            if (x > 0 && z > 0) return CasingBlock.CasingPart.CORNER_SE;
            return CasingBlock.CasingPart.CORNER_SW; // x < 0 && z > 0
        }
        if (Math.abs(x) == half || Math.abs(z) == half) {
            return Math.abs(z) == half
                    ? CasingBlock.CasingPart.RAIL_EW   // N/S edge: bar runs E-W
                    : CasingBlock.CasingPart.RAIL_NS;  // E/W edge: bar runs N-S
        }
        return CasingBlock.CasingPart.BED;
    }

    @Override
    //? if >=1.21.5 {
    /*protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   net.minecraft.world.level.redstone.Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
    *///?} else {
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
    //?}
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof PrinterBlockEntity printer) {
            printer.onNeighborSignal(level.hasNeighborSignal(pos));
        }
    }
}
