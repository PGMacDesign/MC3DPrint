package com.pgmacdesign.mc3dprint.machine.repository;

import com.mojang.serialization.MapCodec;
import com.pgmacdesign.mc3dprint.compat.InteractionCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/** The Server Blueprint Repository block: a passive library terminal (no ticking). */
public class BlueprintRepositoryBlock extends BaseEntityBlock {
    public static final MapCodec<BlueprintRepositoryBlock> CODEC = simpleCodec(BlueprintRepositoryBlock::new);

    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;

    public BlueprintRepositoryBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BlueprintRepositoryBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!level.isClientSide
                && level.getBlockEntity(pos) instanceof BlueprintRepositoryBlockEntity repository
                && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(repository, pos);
            // The open-screen packet is sent first on the same connection, so this
            // listing arrives after the menu exists client-side.
            repository.sendListing(serverPlayer);
        }
        return InteractionCompat.sidedSuccess(level.isClientSide);
    }

    // 1.21.5 replaced onRemove(state,level,pos,newState,isMoving) with
    // affectNeighborsAfterRemoval(state,serverLevel,pos,movedByPiston), called only on real removal.
    //? if >=1.21.5 {
    /*@Override
    protected void affectNeighborsAfterRemoval(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, boolean movedByPiston) {
        if (level.getBlockEntity(pos) instanceof BlueprintRepositoryBlockEntity repository) {
            var inv = repository.inventory();
            for (int slot = 0; slot < inv.getSlots(); slot++) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), inv.getStackInSlot(slot));
            }
        }
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }
    *///?} else {
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof BlueprintRepositoryBlockEntity repository) {
            var inv = repository.inventory();
            for (int slot = 0; slot < inv.getSlots(); slot++) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), inv.getStackInSlot(slot));
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
    //?}

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
