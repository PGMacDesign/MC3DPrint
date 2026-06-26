package com.pgmacdesign.mc3dprint.machine;

import com.mojang.serialization.MapCodec;
import com.pgmacdesign.mc3dprint.compat.InteractionCompat;
import com.pgmacdesign.mc3dprint.registry.ModBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
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

import javax.annotation.Nullable;

/**
 * The Redstone Clock block — an autonomous, silent timer that pulses redstone
 * from all six faces every N seconds (configurable 1–60 in its GUI). See
 * {@link RedstoneClockBlockEntity} for the timer + persistence behaviour.
 *
 * <p>Redstone: it is a {@link #isSignalSource signal source} that emits weak
 * power 15 on every side ONLY during the brief pulse window (read live from the
 * block entity), and 0 otherwise. Weak power drives adjacent dust, repeaters and
 * directly-adjacent pistons — everything a farm harvester needs.
 */
public class RedstoneClockBlock extends BaseEntityBlock {

    public static final MapCodec<RedstoneClockBlock> CODEC = simpleCodec(RedstoneClockBlock::new);

    public RedstoneClockBlock(Properties properties) {
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
        tooltip.add(Component.translatable("tooltip.mc3dprint.redstone_clock").withStyle(ChatFormatting.GRAY));
    }
    //?}

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof RedstoneClockBlockEntity clock) {
            ((ServerPlayer) player).openMenu(clock, pos);
        }
        return InteractionCompat.sidedSuccess(level.isClientSide);
    }

    // --- Redstone signal: weak power 15 on all sides while pulsing, else 0 ---

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof RedstoneClockBlockEntity clock && clock.isPulsing() ? 15 : 0;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RedstoneClockBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.REDSTONE_CLOCK.get(),
                RedstoneClockBlockEntity::serverTick);
    }
}
