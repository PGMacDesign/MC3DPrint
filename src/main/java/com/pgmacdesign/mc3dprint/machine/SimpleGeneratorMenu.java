package com.pgmacdesign.mc3dprint.machine;

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

/**
 * Menu for the Simple Generator (clock generator): one fuel slot, plus synced
 * energy/burn/rate values for the GUI. Mirrors {@link WinderMenu}.
 */
public class SimpleGeneratorMenu extends AbstractContainerMenu {
    public static final int FUEL_SLOT = 0;
    public static final int SLOT_COUNT = 1;

    @Nullable
    private final ClockGeneratorBlockEntity generator;
    private final ContainerData data;

    public SimpleGeneratorMenu(int windowId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(windowId, playerInventory, clientBlockEntity(playerInventory, buf),
                new SimpleContainerData(SplitContainerData.slotCount(ClockGeneratorBlockEntity.DATA_COUNT)));
    }

    public SimpleGeneratorMenu(int windowId, Inventory playerInventory, @Nullable ClockGeneratorBlockEntity generator) {
        this(windowId, playerInventory, generator, generator != null ? generator.containerData()
                : new SimpleContainerData(SplitContainerData.slotCount(ClockGeneratorBlockEntity.DATA_COUNT)));
    }

    private SimpleGeneratorMenu(int windowId, Inventory playerInventory,
                                @Nullable ClockGeneratorBlockEntity generator, ContainerData data) {
        super(ModMenuTypes.SIMPLE_GENERATOR.get(), windowId);
        this.generator = generator;
        this.data = data;

        IItemHandler fuel = generator != null ? generator.fuel() : new ItemStackHandler(SLOT_COUNT);
        addSlot(new SlotItemHandler(fuel, FUEL_SLOT, 80, 53) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return ClockGeneratorBlockEntity.isFuel(stack);
            }
        });

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }

        addDataSlots(data);
    }

    @Nullable
    private static ClockGeneratorBlockEntity clientBlockEntity(Inventory playerInventory, FriendlyByteBuf buf) {
        return playerInventory.player.level().getBlockEntity(buf.readBlockPos()) instanceof ClockGeneratorBlockEntity gen
                ? gen : null;
    }

    public int energy() {
        return SplitContainerData.combine(data, ClockGeneratorBlockEntity.DATA_ENERGY);
    }

    public int maxEnergy() {
        return Math.max(1, SplitContainerData.combine(data, ClockGeneratorBlockEntity.DATA_MAX_ENERGY));
    }

    public int burnRemaining() {
        return SplitContainerData.combine(data, ClockGeneratorBlockEntity.DATA_BURN_REMAINING);
    }

    public int burnTotal() {
        return SplitContainerData.combine(data, ClockGeneratorBlockEntity.DATA_BURN_TOTAL);
    }

    /** RF/t being produced right now (0 when idle). */
    public int genRate() {
        return SplitContainerData.combine(data, ClockGeneratorBlockEntity.DATA_RATE);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = slots.get(slotIndex);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack moved = slot.getItem();
        ItemStack original = moved.copy();

        if (slotIndex < SLOT_COUNT) {
            // fuel slot -> player inventory
            if (!moveItemStackTo(moved, SLOT_COUNT, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (ClockGeneratorBlockEntity.isFuel(moved)) {
            // player inventory -> fuel slot (only fuel)
            if (!moveItemStackTo(moved, FUEL_SLOT, FUEL_SLOT + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else {
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
        if (generator == null || generator.getLevel() == null) {
            return false;
        }
        return generator.getLevel().getBlockEntity(generator.getBlockPos()) == generator
                && player.distanceToSqr(generator.getBlockPos().getCenter()) <= 64.0;
    }
}
