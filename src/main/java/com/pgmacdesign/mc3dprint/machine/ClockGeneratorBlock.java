package com.pgmacdesign.mc3dprint.machine;

import com.pgmacdesign.mc3dprint.registry.ModBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

import javax.annotation.Nullable;

public class ClockGeneratorBlock extends BaseEntityBlock {

    public ClockGeneratorBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter getter,
                                java.util.List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.mc3dprint.clock_generator",
                ClockGeneratorBlockEntity.ratePerTick(),
                ClockGeneratorBlockEntity.burnMultiplier()).withStyle(ChatFormatting.GRAY));
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof ClockGeneratorBlockEntity generator)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        ItemStack held = player.getItemInHand(hand);
        if (!held.isEmpty()) {
            if (!ClockGeneratorBlockEntity.isFuel(held)) {
                player.displayClientMessage(Component.translatable("message.mc3dprint.generator_not_fuel"), true);
                return InteractionResult.CONSUME;
            }
            int burnTicks = generator.addFuel(held);
            if (burnTicks > 0) {
                player.displayClientMessage(Component.translatable("message.mc3dprint.generator_fueled",
                        burnTicks / 1200), true);
                level.playSound(null, pos, SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 0.6F, 1.0F);
            } else {
                player.displayClientMessage(Component.translatable("message.mc3dprint.generator_fuel_full"), true);
            }
            return InteractionResult.CONSUME;
        }
        player.displayClientMessage(Component.translatable("message.mc3dprint.generator_status",
                generator.burnTicksRemaining() / 1200, generator.storedEnergy()), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof ClockGeneratorBlockEntity generator) {
            generator.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(handler -> {
                for (int i = 0; i < handler.getSlots(); i++) {
                    ItemStack stack = handler.getStackInSlot(i);
                    if (!stack.isEmpty()) {
                        Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
                    }
                }
            });
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ClockGeneratorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.CLOCK_GENERATOR.get(),
                ClockGeneratorBlockEntity::serverTick);
    }
}
