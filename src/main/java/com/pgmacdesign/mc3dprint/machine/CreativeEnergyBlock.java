package com.pgmacdesign.mc3dprint.machine;

import com.mojang.serialization.MapCodec;
import com.pgmacdesign.mc3dprint.registry.ModBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

public class CreativeEnergyBlock extends BaseEntityBlock {

    public static final MapCodec<CreativeEnergyBlock> CODEC = simpleCodec(CreativeEnergyBlock::new);

    public CreativeEnergyBlock(Properties properties) {
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

    // 1.21.5 removed Block.appendHoverText (tooltips live on Item only). [PORT] Block-item
    // hover text is dropped on 1.21.8 here; restore via a TooltipBlockItem if parity is wanted.
    //? if <1.21.5 {
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                java.util.List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.mc3dprint.creative_energy")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
    }
    //?}

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CreativeEnergyBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.CREATIVE_ENERGY_SOURCE.get(),
                CreativeEnergyBlockEntity::serverTick);
    }
}
