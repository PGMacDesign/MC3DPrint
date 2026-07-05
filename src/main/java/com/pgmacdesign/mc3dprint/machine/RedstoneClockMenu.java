package com.pgmacdesign.mc3dprint.machine;

import com.pgmacdesign.mc3dprint.registry.ModMenuTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Config-only menu for the {@link RedstoneClockBlockEntity}: no item slots, just
 * the synced interval value and four buttons (±1 / ±10 seconds) that adjust it
 * server-side, clamped 1–60.
 */
public class RedstoneClockMenu extends AbstractContainerMenu {
    public static final int BUTTON_MINUS_10 = 0;
    public static final int BUTTON_MINUS_1 = 1;
    public static final int BUTTON_PLUS_1 = 2;
    public static final int BUTTON_PLUS_10 = 3;

    @Nullable
    private final RedstoneClockBlockEntity clock;
    private final ContainerData data;

    public RedstoneClockMenu(int windowId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(windowId, playerInventory, clientBlockEntity(playerInventory, buf),
                new SimpleContainerData(SplitContainerData.slotCount(RedstoneClockBlockEntity.DATA_COUNT)));
    }

    public RedstoneClockMenu(int windowId, Inventory playerInventory, @Nullable RedstoneClockBlockEntity clock) {
        this(windowId, playerInventory, clock, clock != null ? clock.containerData()
                : new SimpleContainerData(SplitContainerData.slotCount(RedstoneClockBlockEntity.DATA_COUNT)));
    }

    private RedstoneClockMenu(int windowId, Inventory playerInventory,
                              @Nullable RedstoneClockBlockEntity clock, ContainerData data) {
        super(ModMenuTypes.REDSTONE_CLOCK.get(), windowId);
        this.clock = clock;
        this.data = data;
        addDataSlots(data);
    }

    @Nullable
    private static RedstoneClockBlockEntity clientBlockEntity(Inventory playerInventory, FriendlyByteBuf buf) {
        return playerInventory.player.level().getBlockEntity(buf.readBlockPos()) instanceof RedstoneClockBlockEntity c
                ? c : null;
    }

    public int intervalSeconds() {
        return SplitContainerData.combine(data, RedstoneClockBlockEntity.DATA_INTERVAL);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (clock == null || clock.getLevel() == null || clock.getLevel().isClientSide()) {
            return false;
        }
        int delta = switch (id) {
            case BUTTON_MINUS_10 -> -10;
            case BUTTON_MINUS_1 -> -1;
            case BUTTON_PLUS_1 -> 1;
            case BUTTON_PLUS_10 -> 10;
            default -> 0;
        };
        if (delta == 0) {
            return false;
        }
        clock.setIntervalSeconds(clock.intervalSeconds() + delta);
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        return ItemStack.EMPTY; // no slots
    }

    @Override
    public boolean stillValid(Player player) {
        if (clock == null || clock.getLevel() == null) {
            return false;
        }
        return clock.getLevel().getBlockEntity(clock.getBlockPos()) == clock
                && player.distanceToSqr(clock.getBlockPos().getCenter()) <= 64.0;
    }
}
