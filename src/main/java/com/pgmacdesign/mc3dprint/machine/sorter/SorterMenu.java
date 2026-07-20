package com.pgmacdesign.mc3dprint.machine.sorter;

import com.pgmacdesign.mc3dprint.registry.ModMenuTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

import javax.annotation.Nullable;

public class SorterMenu extends AbstractContainerMenu {
    public static final int POOL_X = 8, POOL_Y = 20;
    public static final int INV_X = 8, HOTBAR_Y = 168;

    @Nullable
    private final SorterBlockEntity sorter;
    private final ContainerData data;

    public SorterMenu(int windowId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(windowId, playerInventory, clientBlockEntity(playerInventory, buf),
                new SimpleContainerData(SorterBlockEntity.MAX_TIER));
    }

    public SorterMenu(int windowId, Inventory playerInventory, @Nullable SorterBlockEntity sorter) {
        this(windowId, playerInventory, sorter,
                sorter != null ? sorter.containerData() : new SimpleContainerData(SorterBlockEntity.MAX_TIER));
    }

    private SorterMenu(int windowId, Inventory playerInventory, @Nullable SorterBlockEntity sorter, ContainerData data) {
        super(ModMenuTypes.FILAMENT_ITEM_SORTER.get(), windowId);
        this.sorter = sorter;
        this.data = data;

        IItemHandler pool = sorter != null ? sorter.pool()
                : new ItemStackHandler(SorterBlockEntity.POOL_SLOTS);
        for (int col = 0; col < SorterBlockEntity.POOL_SLOTS; col++) {
            addSlot(new SlotItemHandler(pool, col, POOL_X + col * 18, POOL_Y));
        }

        // The three main rows are added but inactive: the screen buys eight readout lines by not
        // drawing them, while shift-click still fills the whole inventory because moveItemStackTo
        // ignores isActive() (rendering, hit-testing and hotbar swaps all honour it). They share the
        // hotbar's coordinates since nothing ever draws or hit-tests them.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new HiddenSlot(playerInventory, col + row * 9 + 9, INV_X + col * 18, HOTBAR_Y));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, INV_X + col * 18, HOTBAR_Y));
        }

        addDataSlots(data);
    }

    /** A live-but-undrawn player-inventory slot. See the comment at its construction site. */
    private static class HiddenSlot extends Slot {
        HiddenSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean isActive() {
            return false;
        }
    }

    @Nullable
    private static SorterBlockEntity clientBlockEntity(Inventory playerInventory, FriendlyByteBuf buf) {
        return playerInventory.player.level().getBlockEntity(buf.readBlockPos()) instanceof SorterBlockEntity s
                ? s : null;
    }

    /** Reachable winders currently holding a spool of {@code tier} (synced for the readout). */
    public int winderCount(int tier) {
        return tier >= 1 && tier <= SorterBlockEntity.MAX_TIER ? data.get(tier - 1) : 0;
    }

    /** Live client view of a pool slot (for the client-computed "waiting per tier" readout). */
    public ItemStack poolStack(int slot) {
        return slot >= 0 && slot < SorterBlockEntity.POOL_SLOTS ? slots.get(slot).getItem() : ItemStack.EMPTY;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = slots.get(slotIndex);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack moved = slot.getItem();
        ItemStack original = moved.copy();
        int poolSlots = SorterBlockEntity.POOL_SLOTS;
        if (slotIndex < poolSlots) {
            if (!moveItemStackTo(moved, poolSlots, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(moved, 0, poolSlots, false)) {
            // player -> pool; SlotItemHandler.mayPlace enforces the pool door filter
            return ItemStack.EMPTY;
        }
        if (moved.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        if (sorter == null || sorter.getLevel() == null) {
            return false;
        }
        return sorter.getLevel().getBlockEntity(sorter.getBlockPos()) == sorter
                && player.distanceToSqr(sorter.getBlockPos().getCenter()) <= 64.0;
    }
}
