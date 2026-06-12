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
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class PrinterMenu extends AbstractContainerMenu {
    public static final int TEMPLATE_SLOT_X = 53;
    public static final int TEMPLATE_SLOT_Y = 35;
    public static final int OUTPUT_SLOT_X = 116;
    public static final int OUTPUT_SLOT_Y = 35;

    @Nullable
    private final PrinterBlockEntity printer;
    private final ContainerData data;

    /** Client constructor (from network). Data arrives via vanilla sync into the SimpleContainerData. */
    public PrinterMenu(int windowId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(windowId, playerInventory, clientBlockEntity(playerInventory, buf),
                new SimpleContainerData(SplitContainerData.slotCount(PrinterBlockEntity.DATA_COUNT)));
    }

    /** Server constructor. */
    public PrinterMenu(int windowId, Inventory playerInventory, @Nullable PrinterBlockEntity printer) {
        this(windowId, playerInventory, printer, printer != null ? printer.containerData()
                : new SimpleContainerData(SplitContainerData.slotCount(PrinterBlockEntity.DATA_COUNT)));
    }

    private PrinterMenu(int windowId, Inventory playerInventory, @Nullable PrinterBlockEntity printer,
                        ContainerData data) {
        super(ModMenuTypes.TIER1_PRINTER.get(), windowId);
        this.printer = printer;
        this.data = data;

        IItemHandler inventory = printer != null ? printer.inventory() : new ItemStackHandler(PrinterBlockEntity.SLOT_COUNT);
        addSlot(new SlotItemHandler(inventory, PrinterBlockEntity.SLOT_TEMPLATE, TEMPLATE_SLOT_X, TEMPLATE_SLOT_Y) {
            @Override
            public boolean mayPlace(@Nonnull ItemStack stack) {
                // spools dock on the machine (Shift+Click or sneak-use the block), never print
                return !(stack.getItem() instanceof SpoolItem);
            }
        });
        addSlot(new SlotItemHandler(inventory, PrinterBlockEntity.SLOT_OUTPUT, OUTPUT_SLOT_X, OUTPUT_SLOT_Y) {
            @Override
            public boolean mayPlace(@Nonnull ItemStack stack) {
                return false;
            }
        });

        // player inventory sits 22px lower than vanilla: the printer GUI has a
        // control strip (Start/Auto/build offsets) between machine and inventory
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 106 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 164));
        }

        addDataSlots(data);
    }

    @Nullable
    private static PrinterBlockEntity clientBlockEntity(Inventory playerInventory, FriendlyByteBuf buf) {
        return playerInventory.player.level().getBlockEntity(buf.readBlockPos()) instanceof PrinterBlockEntity printer
                ? printer : null;
    }

    public int progress() {
        return SplitContainerData.combine(data, PrinterBlockEntity.DATA_PROGRESS);
    }

    public int maxProgress() {
        return Math.max(1, SplitContainerData.combine(data, PrinterBlockEntity.DATA_MAX_PROGRESS));
    }

    public int energy() {
        return SplitContainerData.combine(data, PrinterBlockEntity.DATA_ENERGY);
    }

    public int maxEnergy() {
        return Math.max(1, SplitContainerData.combine(data, PrinterBlockEntity.DATA_MAX_ENERGY));
    }

    public PrinterBlockEntity.State state() {
        return PrinterBlockEntity.State.byOrdinal(SplitContainerData.combine(data, PrinterBlockEntity.DATA_STATE));
    }

    public int fu() {
        return SplitContainerData.combine(data, PrinterBlockEntity.DATA_FU);
    }

    public int fuCapacity() {
        return SplitContainerData.combine(data, PrinterBlockEntity.DATA_FU_CAP);
    }

    public int templateCost() {
        return SplitContainerData.combine(data, PrinterBlockEntity.DATA_TEMPLATE_COST);
    }

    public int spoolsUsed() {
        return SplitContainerData.combine(data, PrinterBlockEntity.DATA_SPOOLS_USED);
    }

    public int spoolSlots() {
        return SplitContainerData.combine(data, PrinterBlockEntity.DATA_SPOOL_SLOTS);
    }

    public boolean autoStart() {
        return SplitContainerData.combine(data, PrinterBlockEntity.DATA_AUTO_START) != 0;
    }

    /** Build offset for axis 0=X, 1=Y, 2=Z. */
    public int offset(int axis) {
        return SplitContainerData.combine(data, PrinterBlockEntity.DATA_OFFSET_X + axis);
    }

    // GUI button ids: 0 start, 1 auto toggle, 2..7 = X-/X+/Y-/Y+/Z-/Z+, 8 preview
    public static final int BUTTON_START = 0;
    public static final int BUTTON_AUTO = 1;
    public static final int BUTTON_OFFSET_BASE = 2;
    public static final int BUTTON_PREVIEW = 8;

    public boolean preview() {
        return SplitContainerData.combine(data, PrinterBlockEntity.DATA_PREVIEW) != 0;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (printer == null || printer.getLevel() == null || printer.getLevel().isClientSide) {
            return false;
        }
        if (id == BUTTON_START) {
            printer.requestStart();
            return true;
        }
        if (id == BUTTON_AUTO) {
            printer.setAutoStart(!printer.autoStart());
            return true;
        }
        if (id == BUTTON_PREVIEW) {
            printer.togglePreview(player);
            return true;
        }
        int offsetId = id - BUTTON_OFFSET_BASE;
        if (offsetId >= 0 && offsetId < 6) {
            printer.adjustOffset(offsetId / 2, (offsetId % 2 == 0) ? -1 : 1);
            return true;
        }
        return false;
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
        } else if (moved.getItem() instanceof SpoolItem) {
            // spools dock onto the machine, never into the print slot (server authoritative)
            if (printer == null || printer.getLevel() == null || printer.getLevel().isClientSide
                    || !printer.attachSpool(moved)) {
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
        // no distance check: Remote Terminals open this menu from far away by design
        if (printer == null || printer.getLevel() == null) {
            return false;
        }
        return printer.getLevel().getBlockEntity(printer.getBlockPos()) == printer && !printer.isRemoved();
    }
}
