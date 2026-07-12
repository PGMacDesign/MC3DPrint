package com.pgmacdesign.mc3dprint.machine;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.items.ItemStackHandler;

import javax.annotation.Nullable;

import com.pgmacdesign.mc3dprint.compat.InteractionCompat;
import com.pgmacdesign.mc3dprint.registry.ModBlockEntities;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;

public class PrinterBlock extends BaseEntityBlock {

    public static final MapCodec<PrinterBlock> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            MachineTier.CODEC.fieldOf("tier").forGetter(PrinterBlock::tier),
            propertiesCodec()
    ).apply(inst, PrinterBlock::new));

    private final MachineTier tier;

    public PrinterBlock(MachineTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    public MachineTier tier() {
        return tier;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL; // BaseEntityBlock defaults to INVISIBLE
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PrinterBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.PRINTER.get(), PrinterBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof PrinterBlockEntity printer)) {
            return InteractionResult.PASS;
        }
        // Sneak + empty hand: pop the last attached spool back off
        if (player.isSecondaryUseActive() && player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty()) {
            if (!level.isClientSide()) {
                if (printer.isActivelyPrinting()) {
                    com.pgmacdesign.mc3dprint.compat.MsgCompat.actionBar(player, net.minecraft.network.chat.Component.translatable(
                            "message.mc3dprint.spool_locked_printing"));
                } else {
                    ItemStack spool = printer.detachSpool();
                    if (!spool.isEmpty() && !player.getInventory().add(spool)) {
                        player.drop(spool, false);
                    }
                }
            }
            return InteractionCompat.sidedSuccess(level.isClientSide());
        }
        if (!level.isClientSide()) {
            ((ServerPlayer) player).openMenu(printer, pos);
        }
        return InteractionCompat.sidedSuccess(level.isClientSide());
    }

    // 1.21.5 replaced onRemove(state,level,pos,newState,isMoving) with
    // affectNeighborsAfterRemoval(state,serverLevel,pos,movedByPiston), called only on real removal.
    //? if >=1.21.5 {
    /*@Override
    protected void affectNeighborsAfterRemoval(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, boolean movedByPiston) {
        if (level.getBlockEntity(pos) instanceof PrinterBlockEntity printer && !printer.isCollapsing()) {
            printer.cancelActiveJob();
            ItemStackHandler inventory = printer.inventory();
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), inventory.getStackInSlot(slot));
            }
            ItemStackHandler spools = printer.spoolInventory();
            for (int slot = 0; slot < spools.getSlots(); slot++) {
                ItemStack spool = spools.getStackInSlot(slot);
                // creative spools never persist in the world — they vanish on break
                if (spool.getItem() instanceof com.pgmacdesign.mc3dprint.fu.SpoolItem spoolItem
                        && spoolItem.creative()) {
                    continue;
                }
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), spool);
            }
            ItemStackHandler upgrades = printer.upgradeInventory();
            for (int slot = 0; slot < upgrades.getSlots(); slot++) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), upgrades.getStackInSlot(slot));
            }
            // Resins are loot-only, so dropping them on break avoids silently voiding a stack (the
            // T5-T8 multiblock preserves them via full-NBT collapse; the single block must too).
            ItemStackHandler resins = printer.resinInventory();
            for (int slot = 0; slot < resins.getSlots(); slot++) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), resins.getStackInSlot(slot));
            }
        }
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }
    *///?} else {
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof PrinterBlockEntity printer
                && !printer.isCollapsing()) {
            printer.cancelActiveJob();
            ItemStackHandler inventory = printer.inventory();
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), inventory.getStackInSlot(slot));
            }
            ItemStackHandler spools = printer.spoolInventory();
            for (int slot = 0; slot < spools.getSlots(); slot++) {
                ItemStack spool = spools.getStackInSlot(slot);
                // creative spools never persist in the world — they vanish on break
                if (spool.getItem() instanceof com.pgmacdesign.mc3dprint.fu.SpoolItem spoolItem
                        && spoolItem.creative()) {
                    continue;
                }
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), spool);
            }
            ItemStackHandler upgrades = printer.upgradeInventory();
            for (int slot = 0; slot < upgrades.getSlots(); slot++) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), upgrades.getStackInSlot(slot));
            }
            // Resins are loot-only, so dropping them on break avoids silently voiding a stack (the
            // T5-T8 multiblock preserves them via full-NBT collapse; the single block must too).
            ItemStackHandler resins = printer.resinInventory();
            for (int slot = 0; slot < resins.getSlots(); slot++) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), resins.getStackInSlot(slot));
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
    //?}

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

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof Player player && level.getBlockEntity(pos) instanceof PrinterBlockEntity be) {
            be.setOwner(player.getUUID());
        }
    }
}
