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

public class PrinterMenu extends AbstractContainerMenu {
    public static final int TEMPLATE_SLOT_X = 53;
    public static final int TEMPLATE_SLOT_Y = 35;
    public static final int OUTPUT_SLOT_X = 116;
    public static final int OUTPUT_SLOT_Y = 35;

    @Nullable
    private final PrinterBlockEntity printer;
    private final ContainerData data;

    /** Client constructor (from network). */
    public PrinterMenu(int windowId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(windowId, playerInventory, clientBlockEntity(playerInventory, buf));
    }

    /** Server constructor. */
    public PrinterMenu(int windowId, Inventory playerInventory, @Nullable PrinterBlockEntity printer) {
        super(ModMenuTypes.TIER1_PRINTER.get(), windowId);
        this.printer = printer;
        this.data = printer != null ? printer.containerData() : new SimpleContainerData(PrinterBlockEntity.DATA_COUNT);

        IItemHandler inventory = printer != null ? printer.inventory() : new ItemStackHandler(PrinterBlockEntity.SLOT_COUNT);
        addSlot(new SlotItemHandler(inventory, PrinterBlockEntity.SLOT_TEMPLATE, TEMPLATE_SLOT_X, TEMPLATE_SLOT_Y));
        addSlot(new SlotItemHandler(inventory, PrinterBlockEntity.SLOT_OUTPUT, OUTPUT_SLOT_X, OUTPUT_SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
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
    private static PrinterBlockEntity clientBlockEntity(Inventory playerInventory, FriendlyByteBuf buf) {
        return playerInventory.player.level().getBlockEntity(buf.readBlockPos()) instanceof PrinterBlockEntity printer
                ? printer : null;
    }

    public int progress() {
        return data.get(PrinterBlockEntity.DATA_PROGRESS);
    }

    public int maxProgress() {
        return Math.max(1, data.get(PrinterBlockEntity.DATA_MAX_PROGRESS));
    }

    public int energy() {
        return data.get(PrinterBlockEntity.DATA_ENERGY);
    }

    public int maxEnergy() {
        return Math.max(1, data.get(PrinterBlockEntity.DATA_MAX_ENERGY));
    }

    public PrinterBlockEntity.State state() {
        return PrinterBlockEntity.State.byOrdinal(data.get(PrinterBlockEntity.DATA_STATE));
    }

    public int fu() {
        return data.get(PrinterBlockEntity.DATA_FU);
    }

    public int fuCapacity() {
        return data.get(PrinterBlockEntity.DATA_FU_CAP);
    }

    public int templateCost() {
        return data.get(PrinterBlockEntity.DATA_TEMPLATE_COST);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = slots.get(slotIndex);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack moved = slot.getItem();
        ItemStack original = moved.copy();

        int machineSlots = PrinterBlockEntity.SLOT_COUNT;
        if (slotIndex < machineSlots) {
            // machine -> player inventory
            if (!moveItemStackTo(moved, machineSlots, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // player -> template slot
            if (!moveItemStackTo(moved, PrinterBlockEntity.SLOT_TEMPLATE, PrinterBlockEntity.SLOT_TEMPLATE + 1, false)) {
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
        if (printer == null || printer.getLevel() == null) {
            return false;
        }
        return printer.getLevel().getBlockEntity(printer.getBlockPos()) == printer
                && player.distanceToSqr(printer.getBlockPos().getCenter()) <= 64.0;
    }
}
