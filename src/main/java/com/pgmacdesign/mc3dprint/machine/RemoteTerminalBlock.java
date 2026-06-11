package com.pgmacdesign.mc3dprint.machine;

import com.pgmacdesign.mc3dprint.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Remote Terminal: pairs to one printer (sneak-click the printer with the
 * terminal item), then right-clicking the placed terminal opens that printer's
 * GUI from anywhere in the same dimension. Multiple terminals may pair to one
 * printer. Essential when the fabricator is buried inside a build.
 */
public class RemoteTerminalBlock extends BaseEntityBlock {

    public RemoteTerminalBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TerminalBlockEntity(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof TerminalBlockEntity terminal)) {
            return InteractionResult.PASS;
        }
        BlockPos target = terminal.target();
        if (target == null) {
            player.displayClientMessage(Component.translatable("message.mc3dprint.terminal_unpaired"), true);
            return InteractionResult.CONSUME;
        }
        if (!level.isLoaded(target) || !(level.getBlockEntity(target) instanceof PrinterBlockEntity printer)) {
            player.displayClientMessage(Component.translatable("message.mc3dprint.terminal_lost",
                    target.getX(), target.getY(), target.getZ()), true);
            return InteractionResult.CONSUME;
        }
        NetworkHooks.openScreen((ServerPlayer) player, printer, target);
        return InteractionResult.CONSUME;
    }

    /** Block item that pairs by sneak-clicking a printer; pairing rides BlockEntityTag. */
    public static class TerminalBlockItem extends BlockItem {
        public TerminalBlockItem(net.minecraft.world.level.block.Block block, Properties properties) {
            super(block, properties);
        }

        @Override
        public InteractionResult useOn(UseOnContext context) {
            Player player = context.getPlayer();
            Level level = context.getLevel();
            if (player != null && player.isSecondaryUseActive()
                    && level.getBlockEntity(context.getClickedPos()) instanceof PrinterBlockEntity) {
                if (!level.isClientSide) {
                    CompoundTag beTag = context.getItemInHand().getOrCreateTagElement("BlockEntityTag");
                    beTag.put("Target", NbtUtils.writeBlockPos(context.getClickedPos()));
                    BlockPos pos = context.getClickedPos();
                    player.displayClientMessage(Component.translatable("message.mc3dprint.terminal_paired",
                            pos.getX(), pos.getY(), pos.getZ()), true);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            return super.useOn(context);
        }

        @Override
        public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
            CompoundTag beTag = stack.getTagElement("BlockEntityTag");
            if (beTag != null && beTag.contains("Target")) {
                BlockPos target = NbtUtils.readBlockPos(beTag.getCompound("Target"));
                tooltip.add(Component.translatable("tooltip.mc3dprint.terminal_paired",
                                target.getX(), target.getY(), target.getZ())
                        .withStyle(net.minecraft.ChatFormatting.AQUA));
            } else {
                tooltip.add(Component.translatable("tooltip.mc3dprint.terminal_help")
                        .withStyle(net.minecraft.ChatFormatting.GRAY));
            }
        }
    }

    public static class TerminalBlockEntity extends BlockEntity {
        @Nullable
        private BlockPos target;

        public TerminalBlockEntity(BlockPos pos, BlockState state) {
            super(ModBlockEntities.REMOTE_TERMINAL.get(), pos, state);
        }

        @Nullable
        public BlockPos target() {
            return target;
        }

        @Override
        protected void saveAdditional(CompoundTag tag) {
            super.saveAdditional(tag);
            if (target != null) {
                tag.put("Target", NbtUtils.writeBlockPos(target));
            }
        }

        @Override
        public void load(CompoundTag tag) {
            super.load(tag);
            target = tag.contains("Target") ? NbtUtils.readBlockPos(tag.getCompound("Target")) : null;
        }
    }
}
