package com.pgmacdesign.mc3dprint.machine;

import com.pgmacdesign.mc3dprint.fu.SpoolItem;
import com.pgmacdesign.mc3dprint.registry.ModMenuTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

import javax.annotation.Nullable;

public class WinderMenu extends AbstractContainerMenu {
    @Nullable
    private final WinderBlockEntity winder;
    private final ContainerData data;

    public WinderMenu(int windowId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(windowId, playerInventory, clientBlockEntity(playerInventory, buf),
                new SimpleContainerData(SplitContainerData.slotCount(WinderBlockEntity.DATA_COUNT)));
    }

    public WinderMenu(int windowId, Inventory playerInventory, @Nullable WinderBlockEntity winder) {
        this(windowId, playerInventory, winder, winder != null ? winder.containerData()
                : new SimpleContainerData(SplitContainerData.slotCount(WinderBlockEntity.DATA_COUNT)));
    }

    private WinderMenu(int windowId, Inventory playerInventory, @Nullable WinderBlockEntity winder,
                       ContainerData data) {
        super(ModMenuTypes.FILAMENT_WINDER.get(), windowId);
        this.winder = winder;
        this.data = data;

        IItemHandler inventory = winder != null ? winder.inventory() : new ItemStackHandler(WinderBlockEntity.SLOT_COUNT);
        addSlot(new SlotItemHandler(inventory, WinderBlockEntity.SLOT_INPUT, 58, 30));
        addSlot(new SlotItemHandler(inventory, WinderBlockEntity.SLOT_SPOOL, 126, 30) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof SpoolItem;
            }
        });

        // Player inventory is centred at x=19 under the 200px-wide Throughput panel.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 19 + col * 18, 108 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 19 + col * 18, 168));
        }

        addDataSlots(data);
    }

    @Nullable
    private static WinderBlockEntity clientBlockEntity(Inventory playerInventory, FriendlyByteBuf buf) {
        return playerInventory.player.level().getBlockEntity(buf.readBlockPos()) instanceof WinderBlockEntity winder
                ? winder : null;
    }

    public int progress() {
        return SplitContainerData.combine(data, WinderBlockEntity.DATA_PROGRESS);
    }

    public int maxProgress() {
        return Math.max(1, SplitContainerData.combine(data, WinderBlockEntity.DATA_MAX_PROGRESS));
    }

    public int energy() {
        return SplitContainerData.combine(data, WinderBlockEntity.DATA_ENERGY);
    }

    public int maxEnergy() {
        return Math.max(1, SplitContainerData.combine(data, WinderBlockEntity.DATA_MAX_ENERGY));
    }

    public int spoolFu() {
        return SplitContainerData.combine(data, WinderBlockEntity.DATA_SPOOL_FU);
    }

    public int spoolCapacity() {
        return SplitContainerData.combine(data, WinderBlockEntity.DATA_SPOOL_CAP);
    }

    /** Tier of spool the input material needs (0 when there's no valued input). */
    public int requiredTier() {
        return SplitContainerData.combine(data, WinderBlockEntity.DATA_REQUIRED_TIER);
    }

    /** {@link WinderBlockEntity#STATUS_OK} / WRONG_TIER / NOT_CONVERTIBLE. */
    public int status() {
        return SplitContainerData.combine(data, WinderBlockEntity.DATA_STATUS);
    }

    /** FU produced per item wound with the current input+spool (0 if no valid pair). */
    public int yieldPerItem() {
        return SplitContainerData.combine(data, WinderBlockEntity.DATA_YIELD);
    }

    /** The item currently in the input slot (for the Material readout / icon). */
    public ItemStack inputStack() {
        return slots.get(WinderBlockEntity.SLOT_INPUT).getItem();
    }

    /** The spool currently docked (for tier + fill readouts). */
    public ItemStack spoolStack() {
        return slots.get(WinderBlockEntity.SLOT_SPOOL).getItem();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = slots.get(slotIndex);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack moved = slot.getItem();
        ItemStack original = moved.copy();

        int machineSlots = WinderBlockEntity.SLOT_COUNT;
        if (slotIndex < machineSlots) {
            if (!moveItemStackTo(moved, machineSlots, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (moved.getItem() instanceof SpoolItem) {
            if (!moveItemStackTo(moved, WinderBlockEntity.SLOT_SPOOL, WinderBlockEntity.SLOT_SPOOL + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (!moveItemStackTo(moved, WinderBlockEntity.SLOT_INPUT, WinderBlockEntity.SLOT_INPUT + 1, false)) {
                return ItemStack.EMPTY;
            }
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
        if (winder == null || winder.getLevel() == null) {
            return false;
        }
        return winder.getLevel().getBlockEntity(winder.getBlockPos()) == winder
                && player.distanceToSqr(winder.getBlockPos().getCenter()) <= 64.0;
    }
}
