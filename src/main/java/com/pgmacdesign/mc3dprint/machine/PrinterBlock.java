package com.pgmacdesign.mc3dprint.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;

import com.pgmacdesign.mc3dprint.registry.ModBlockEntities;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;

public class PrinterBlock extends BaseEntityBlock {
    private final MachineTier tier;

    public PrinterBlock(MachineTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
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
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.PRINTER.get(), PrinterBlockEntity::serverTick);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof PrinterBlockEntity printer)) {
            return InteractionResult.PASS;
        }
        // Sneak + empty hand: pop the last attached spool back off
        if (player.isSecondaryUseActive() && player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty()) {
            if (!level.isClientSide) {
                ItemStack spool = printer.detachSpool();
                if (!spool.isEmpty() && !player.getInventory().add(spool)) {
                    player.drop(spool, false);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide) {
            NetworkHooks.openScreen((ServerPlayer) player, printer, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

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
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), spools.getStackInSlot(slot));
            }
            ItemStackHandler upgrades = printer.upgradeInventory();
            for (int slot = 0; slot < upgrades.getSlots(); slot++) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), upgrades.getStackInSlot(slot));
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
