package com.pgmacdesign.mc3dprint.machine;

import com.mojang.serialization.MapCodec;
import com.pgmacdesign.mc3dprint.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import com.pgmacdesign.mc3dprint.compat.InteractionCompat;
import net.minecraft.world.InteractionResult;
//? if <1.21.5 {
import net.minecraft.world.ItemInteractionResult;
//?}
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

public class FilamentConverterBlock extends BaseEntityBlock {

    public static final MapCodec<FilamentConverterBlock> CODEC = simpleCodec(FilamentConverterBlock::new);

    public FilamentConverterBlock(Properties properties) {
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
        return new FilamentConverterBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.FILAMENT_CONVERTER.get(),
                FilamentConverterBlockEntity::serverTick);
    }

    @Override
    //? if >=1.21.5 {
    /*protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
    *///?} else {
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
    //?}
        if (!(level.getBlockEntity(pos) instanceof FilamentConverterBlockEntity converter)) {
            return InteractionCompat.ITEM_PASS;
        }
        if (level.isClientSide()) {
            return InteractionCompat.ITEM_SUCCESS;
        }
        ItemStack held = player.getItemInHand(hand);
        if (player.isSecondaryUseActive() && held.isEmpty()) {
            converter.setFilter(ItemStack.EMPTY);
            player.displayClientMessage(Component.translatable("message.mc3dprint.converter_filter_cleared"), true);
        } else if (!held.isEmpty()) {
            converter.setFilter(held);
            player.displayClientMessage(Component.translatable("message.mc3dprint.converter_filter_set",
                    held.getHoverName()), true);
        } else {
            Component filterName = converter.filter().isEmpty()
                    ? Component.translatable("message.mc3dprint.converter_no_filter")
                    : converter.filter().getHoverName();
            player.displayClientMessage(Component.translatable("message.mc3dprint.converter_status",
                    filterName), true);
        }
        return InteractionCompat.ITEM_CONSUME;
    }
}
