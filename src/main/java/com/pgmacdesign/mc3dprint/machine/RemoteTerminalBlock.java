package com.pgmacdesign.mc3dprint.machine;

import com.mojang.serialization.MapCodec;
import com.pgmacdesign.mc3dprint.compat.BeData;
import com.pgmacdesign.mc3dprint.compat.InteractionCompat;
import com.pgmacdesign.mc3dprint.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/**
 * Remote Terminal: pairs to one printer (sneak-click the printer with the
 * terminal item), then right-clicking the placed terminal opens that printer's
 * GUI from anywhere in the same dimension. Multiple terminals may pair to one
 * printer. Essential when the fabricator is buried inside a build.
 */
public class RemoteTerminalBlock extends BaseEntityBlock {

    public static final MapCodec<RemoteTerminalBlock> CODEC = simpleCodec(RemoteTerminalBlock::new);

    public RemoteTerminalBlock(Properties properties) {
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
        return new TerminalBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
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
        ((ServerPlayer) player).openMenu(printer, target);
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
                    BlockPos pos = context.getClickedPos();
                    // Pairing rides BLOCK_ENTITY_DATA so it restores into the terminal BE on place.
                    CompoundTag beTag = new CompoundTag();
                    beTag.put("Target", NbtUtils.writeBlockPos(pos));
                    BlockItem.setBlockEntityData(context.getItemInHand(),
                            ModBlockEntities.REMOTE_TERMINAL.get(), beTag);
                    player.displayClientMessage(Component.translatable("message.mc3dprint.terminal_paired",
                            pos.getX(), pos.getY(), pos.getZ()), true);
                }
                return InteractionCompat.sidedSuccess(level.isClientSide);
            }
            return super.useOn(context);
        }

        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            CustomData beData = stack.get(DataComponents.BLOCK_ENTITY_DATA);
            Optional<BlockPos> target = beData == null
                    ? Optional.empty()
                    : NbtUtils.readBlockPos(beData.copyTag(), "Target");
            if (target.isPresent()) {
                BlockPos t = target.get();
                tooltip.add(Component.translatable("tooltip.mc3dprint.terminal_paired",
                                t.getX(), t.getY(), t.getZ())
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

        //? if >=1.21.5 {
        /*@Override
        protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput out) {
            super.saveAdditional(out);
            writeData(BeData.writer(out));
        }

        @Override
        protected void loadAdditional(net.minecraft.world.level.storage.ValueInput in) {
            super.loadAdditional(in);
            readData(BeData.reader(in));
        }
        *///?} else {
        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            writeData(BeData.writer(tag, registries));
        }

        @Override
        protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            readData(BeData.reader(tag, registries));
        }
        //?}

        private void writeData(BeData.Writer w) {
            if (target != null) {
                w.putBlockPos("Target", target);
            }
        }

        private void readData(BeData.Reader r) {
            target = r.getBlockPos("Target").orElse(null);
        }
    }
}
