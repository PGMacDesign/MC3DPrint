package com.pgmacdesign.mc3dprint.machine;

import com.pgmacdesign.mc3dprint.fu.SpoolItem;
import com.pgmacdesign.mc3dprint.item.BlueprintDiscItem;
import com.pgmacdesign.mc3dprint.item.ResinItem;
import com.pgmacdesign.mc3dprint.machine.upgrade.UpgradeItem;
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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class PrinterMenu extends AbstractContainerMenu {
    public static final int TEMPLATE_SLOT_X = 53;
    public static final int TEMPLATE_SLOT_Y = 35;
    public static final int OUTPUT_SLOT_X = 116;
    public static final int OUTPUT_SLOT_Y = 35;

    // Upgrade slot wells: a 2-column grid on the right of the machine area. Slots
    // are added row-major (col 0 then col 1, top to bottom) up to the tier's
    // upgrade-slot count (T1=1 … T8=8). These x/y MUST match the wells painted by
    // tools/gen_printer_gui.py (it reads the coords from here).
    public static final int UPGRADE_SLOT_X = 178;
    public static final int UPGRADE_SLOT_Y = 18;
    public static final int UPGRADE_COL_STEP = 18;
    public static final int UPGRADE_ROW_STEP = 18;
    public static final int UPGRADE_COLS = 2;
    /** Max upgrade slots across all tiers (T8). Wells are painted up to this. */
    public static final int MAX_UPGRADE_SLOTS = 8;

    // Spool slots: a 2-column grid on the bottom-right so the docked filament
    // spools can be pulled out / put in from the GUI (previously only Shift+Right
    // Click on the block worked). Only the slots this tier has are added (T1=1 …
    // T4-T8=4); the two rows line up with the bottom two inventory rows (y=168 main,
    // y=190 hotbar → ROW_STEP 22). PrinterScreen draws a well under each LIVE slot
    // (so lower tiers show fewer wells, not empty ones).
    public static final int SPOOL_SLOT_X = 178;
    public static final int SPOOL_SLOT_Y = 168;
    public static final int SPOOL_COL_STEP = 18;
    public static final int SPOOL_ROW_STEP = 22;
    public static final int SPOOL_COLS = 2;
    /** Max spool slots across all tiers (T4+). */
    public static final int MAX_SPOOL_SLOTS = 4;

    // Resin slot: a single slot in the gap between the upgrade column (top) and the
    // spool column (bottom). PrinterScreen blits a well sprite here at runtime (like the
    // spool wells), so there's no baked coord in gen_printer_gui.py to keep in lockstep.
    public static final int RESIN_SLOT_X = 187;
    public static final int RESIN_SLOT_Y = 106;

    /** Index of the first upgrade slot in this menu (after template + output). */
    private final int upgradeSlotStart;
    /** Number of upgrade slots actually present (this printer's tier). */
    private final int upgradeSlotCount;
    /** Index of the first spool slot in this menu (after the upgrade slots). */
    private final int spoolSlotStart;
    /** Number of spool slots actually present (this printer's tier). */
    private final int spoolSlotCount;
    /** Index of the single resin slot (sits after the spool slots). */
    private final int resinSlotStart;

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

        // Upgrade slots: only the slots this tier actually has are added (T1=1 …
        // T8=8), laid out in a 2-column grid on the right. Empty wells beyond the
        // tier's count are painted by the texture but carry no Slot.
        IItemHandler upgradeHandler = printer != null ? printer.upgradeInventory()
                : new ItemStackHandler(0);
        this.upgradeSlotStart = slots.size();
        this.upgradeSlotCount = upgradeHandler.getSlots();
        for (int i = 0; i < upgradeSlotCount; i++) {
            int col = i % UPGRADE_COLS;
            int rowIdx = i / UPGRADE_COLS;
            int x = UPGRADE_SLOT_X + col * UPGRADE_COL_STEP;
            int y = UPGRADE_SLOT_Y + rowIdx * UPGRADE_ROW_STEP;
            addSlot(new SlotItemHandler(upgradeHandler, i, x, y) {
                // one module per slot, so the per-type cap counts cleanly
                @Override
                public int getMaxStackSize() {
                    return 1;
                }

                @Override
                public int getMaxStackSize(@Nonnull ItemStack stack) {
                    return 1;
                }

                @Override
                public boolean mayPlace(@Nonnull ItemStack stack) {
                    if (!(stack.getItem() instanceof UpgradeItem upgrade)) {
                        return false;
                    }
                    // a same-type swap into this very slot doesn't raise the count
                    if (getItem().getItem() instanceof UpgradeItem current && current.type() == upgrade.type()) {
                        return true;
                    }
                    return printer == null || !printer.upgradeTypeAtCap(upgrade.type());
                }
            });
        }

        // Spool slots: a 2-column grid on the bottom-right. Only the tier's slots are
        // added; the SlotItemHandler's mayPlace delegates to the handler's isItemValid,
        // which already restricts these to SpoolItem. One spool per slot.
        IItemHandler spoolHandler = printer != null ? printer.spoolInventory()
                : new ItemStackHandler(0);
        this.spoolSlotStart = slots.size();
        this.spoolSlotCount = spoolHandler.getSlots();
        for (int i = 0; i < spoolSlotCount; i++) {
            int col = i % SPOOL_COLS;
            int rowIdx = i / SPOOL_COLS;
            int x = SPOOL_SLOT_X + col * SPOOL_COL_STEP;
            int y = SPOOL_SLOT_Y + rowIdx * SPOOL_ROW_STEP;
            addSlot(new SlotItemHandler(spoolHandler, i, x, y) {
                @Override
                public int getMaxStackSize() {
                    return 1;
                }

                @Override
                public int getMaxStackSize(@Nonnull ItemStack stack) {
                    return 1;
                }
            });
        }

        // Resin slot: a single stack-holding slot for the consumed-per-print modifier,
        // in the gap between upgrades and spools. Locked (mayPickup=false) while a
        // catalyzed job hasn't yet spent it (Q6).
        IItemHandler resinHandler = printer != null ? printer.resinInventory() : new ItemStackHandler(1);
        this.resinSlotStart = slots.size();
        addSlot(new SlotItemHandler(resinHandler, 0, RESIN_SLOT_X, RESIN_SLOT_Y) {
            @Override
            public boolean mayPlace(@Nonnull ItemStack stack) {
                return stack.getItem() instanceof ResinItem;
            }

            @Override
            public boolean mayPickup(@Nonnull Player player) {
                return printer == null || !printer.isResinLocked();
            }
        });

        // player inventory sits below the control strip (Start/Auto/Ghost +
        // build-offset row + the rotate row) that lives between the machine and the inventory
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 132 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 190));
        }

        addDataSlots(data);

        // Re-check obstruction when the GUI opens so it shows up immediately,
        // not only after pressing Start (no-op client-side / without a disc).
        if (printer != null) {
            printer.recheckObstruction();
        }
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

    /** Number of upgrade slots this printer's tier exposes (0 for some tiers). */
    public int upgradeSlotCount() {
        return upgradeSlotCount;
    }

    /** True when a resin is slotted but the loaded blueprint is player-made, so the
     *  resin won't apply (Q9 gate). Client-safe — reads synced slot contents. */
    public boolean resinBlockedByPlayerBlueprint() {
        ItemStack resin = slots.get(resinSlotStart).getItem();
        if (!(resin.getItem() instanceof ResinItem)) {
            return false;
        }
        ItemStack disc = slots.get(0).getItem(); // template slot
        return BlueprintDiscItem.hasBlueprint(disc) && !BlueprintDiscItem.isOfficial(disc);
    }

    /** The tier (2 or 3) of an Overdrive resin currently in the resin slot, else 0. Client-safe
     *  (reads synced slot contents) — drives the disc's Overdrive cost-preview tooltip. */
    public int overdriveResinTierInSlot() {
        ItemStack resin = slots.get(resinSlotStart).getItem();
        if (resin.getItem() instanceof ResinItem r && r.effect() == ResinItem.Effect.OVERDRIVE) {
            return r.tier();
        }
        return 0;
    }

    /** The effect of the resin currently in the resin slot, or null if the slot is empty.
     *  Client-safe (reads synced slot contents) — drives the disc's "no effect" tooltip warning. */
    @javax.annotation.Nullable
    public ResinItem.Effect slottedResinEffect() {
        ItemStack resin = slots.get(resinSlotStart).getItem();
        return resin.getItem() instanceof ResinItem r ? r.effect() : null;
    }

    /** Build offset for axis 0=X, 1=Y, 2=Z. */
    public int offset(int axis) {
        return SplitContainerData.combine(data, PrinterBlockEntity.DATA_OFFSET_X + axis);
    }

    // GUI button ids: 0 start, 1 auto toggle, 2..7 = X-/X+/Y-/Y+/Z-/Z+, 8 preview, 9 rotate, 10 mode
    public static final int BUTTON_START = 0;
    public static final int BUTTON_AUTO = 1;
    public static final int BUTTON_OFFSET_BASE = 2;
    public static final int BUTTON_PREVIEW = 8;
    public static final int BUTTON_ROTATE = 9;
    public static final int BUTTON_MODE = 10;

    public boolean deconstructMode() {
        return SplitContainerData.combine(data, PrinterBlockEntity.DATA_DECON_MODE) != 0;
    }

    // --- Matter Calculator readout (0s when no disc is loaded) ---

    public int blueprintFuTotal() {
        return SplitContainerData.combine(data, PrinterBlockEntity.DATA_BP_FU_TOTAL);
    }

    public int blueprintRf() {
        return SplitContainerData.combine(data, PrinterBlockEntity.DATA_BP_RF);
    }

    public int blueprintEtaTicks() {
        return SplitContainerData.combine(data, PrinterBlockEntity.DATA_BP_ETA);
    }

    /** Lowest tier whose down-only filament coverage fails, or 0 when affordable. */
    public int shortfallTier() {
        return SplitContainerData.combine(data, PrinterBlockEntity.DATA_BP_SHORTFALL);
    }

    public int costForTier(int tier) {
        return SplitContainerData.combine(data, PrinterBlockEntity.DATA_COST_TIER_BASE + tier - 1);
    }

    public int availForTier(int tier) {
        return SplitContainerData.combine(data, PrinterBlockEntity.DATA_AVAIL_TIER_BASE + tier - 1);
    }

    public boolean preview() {
        return SplitContainerData.combine(data, PrinterBlockEntity.DATA_PREVIEW) != 0;
    }

    /** Current build rotation in degrees clockwise (0/90/180/270) — drives the button label. */
    public int rotationDegrees() {
        return SplitContainerData.combine(data, PrinterBlockEntity.DATA_ROTATION) * 90;
    }

    /** Smallest printer tier that fits the loaded blueprint (drives the NEEDS_HIGHER_TIER label). */
    public int requiredTier() {
        return SplitContainerData.combine(data, PrinterBlockEntity.DATA_REQUIRED_TIER);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (printer == null || printer.getLevel() == null || printer.getLevel().isClientSide()) {
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
        if (id == BUTTON_ROTATE) {
            printer.cycleRotation();
            return true;
        }
        if (id == BUTTON_MODE) {
            printer.setDeconstructMode(!printer.deconstructMode());
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

        // Machine-side slots = template + output + upgrade slots + spool slots + resin;
        // everything after that is the player inventory.
        int firstPlayerSlot = resinSlotStart + 1;
        boolean isMachineSlot = slotIndex < firstPlayerSlot;
        if (isMachineSlot) {
            // any machine slot (template / output / upgrade / spool) -> player inventory
            if (!moveItemStackTo(moved, firstPlayerSlot, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (moved.getItem() instanceof SpoolItem) {
            // spools dock into the spool slots (never into the print slot)
            if (spoolSlotCount == 0 || !moveItemStackTo(moved, spoolSlotStart,
                    spoolSlotStart + spoolSlotCount, false)) {
                return ItemStack.EMPTY;
            }
        } else if (moved.getItem() instanceof UpgradeItem) {
            // upgrade modules -> the upgrade slots (only ones this tier has)
            if (upgradeSlotCount == 0 || !moveItemStackTo(moved, upgradeSlotStart,
                    upgradeSlotStart + upgradeSlotCount, false)) {
                return ItemStack.EMPTY;
            }
        } else if (moved.getItem() instanceof ResinItem) {
            // resin -> the single resin slot
            if (!moveItemStackTo(moved, resinSlotStart, resinSlotStart + 1, false)) {
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
