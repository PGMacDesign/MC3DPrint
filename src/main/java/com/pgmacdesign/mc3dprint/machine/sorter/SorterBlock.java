package com.pgmacdesign.mc3dprint.machine.sorter;

import com.mojang.serialization.MapCodec;
import com.pgmacdesign.mc3dprint.compat.InteractionCompat;
import com.pgmacdesign.mc3dprint.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/**
 * The Filament Tier Item Sorter block: a passive, ticking logistics block with no facing —
 * discovery is omnidirectional, so orientation carries no meaning. All routing lives in
 * {@link SorterBlockEntity}.
 */
public class SorterBlock extends BaseEntityBlock {
    public static final MapCodec<SorterBlock> CODEC = simpleCodec(SorterBlock::new);

    public SorterBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SorterBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.FILAMENT_ITEM_SORTER.get(), SorterBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof SorterBlockEntity sorter
                && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(sorter, pos);
        }
        return InteractionCompat.sidedSuccess(level.isClientSide());
    }

    // 1.21.5 replaced onRemove(state,level,pos,newState,isMoving) with
    // affectNeighborsAfterRemoval(state,serverLevel,pos,movedByPiston), called only on real removal.
    //? if >=1.21.5 {
    /*@Override
    protected void affectNeighborsAfterRemoval(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, boolean movedByPiston) {
        if (level.getBlockEntity(pos) instanceof SorterBlockEntity sorter) {
            var pool = sorter.pool();
            for (int slot = 0; slot < pool.getSlots(); slot++) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), pool.getStackInSlot(slot));
            }
        }
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }
    *///?} else {
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof SorterBlockEntity sorter) {
            var pool = sorter.pool();
            for (int slot = 0; slot < pool.getSlots(); slot++) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), pool.getStackInSlot(slot));
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
    //?}
}
