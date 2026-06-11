package com.pgmacdesign.mc3dprint.machine;

import com.pgmacdesign.mc3dprint.config.MC3DPrintConfig;
import com.pgmacdesign.mc3dprint.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.RangedWrapper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Tier 1 printer, Item Mode only: the template slot holds the item to copy
 * (never consumed), the printer drains RF over time and emits copies into the
 * output slot.
 *
 * M2 placeholder economy: copies cost RF only. The FU/filament material cost
 * replaces this in M5 — until then this block intentionally duplicates items.
 *
 * Job state machine per the design doc: power loss or a full output PAUSES
 * the job (progress retained), never resets it.
 */
public class PrinterBlockEntity extends BlockEntity implements MenuProvider {
    public static final int SLOT_TEMPLATE = 0;
    public static final int SLOT_OUTPUT = 1;
    public static final int SLOT_COUNT = 2;

    // ContainerData indices for GUI sync
    public static final int DATA_PROGRESS = 0;
    public static final int DATA_MAX_PROGRESS = 1;
    public static final int DATA_ENERGY = 2;
    public static final int DATA_MAX_ENERGY = 3;
    public static final int DATA_STATE = 4;
    public static final int DATA_COUNT = 5;

    public enum State {
        IDLE, PRINTING, PAUSED_NO_POWER, PAUSED_OUTPUT_FULL;

        public static State byOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : IDLE;
        }
    }

    private final ItemStackHandler inventory = new ItemStackHandler(SLOT_COUNT) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    private final MachineEnergyStorage energy = new MachineEnergyStorage(
            MC3DPrintConfig.T1_ENERGY_BUFFER.get(),
            MC3DPrintConfig.T1_MAX_ENERGY_RECEIVE.get(),
            this::setChanged);

    private final LazyOptional<MachineEnergyStorage> energyCap = LazyOptional.of(() -> energy);
    private final LazyOptional<IItemHandler> inputCap =
            LazyOptional.of(() -> new RangedWrapper(inventory, SLOT_TEMPLATE, SLOT_TEMPLATE + 1));
    private final LazyOptional<IItemHandler> outputCap =
            LazyOptional.of(() -> new RangedWrapper(inventory, SLOT_OUTPUT, SLOT_OUTPUT + 1) {
                @Override
                @Nonnull
                public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
                    return stack; // extract-only face
                }
            });
    private final LazyOptional<IItemHandler> allCap = LazyOptional.of(() -> inventory);

    private int progress;
    private State state = State.IDLE;

    public PrinterBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.TIER1_PRINTER.get(), pos, blockState);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState blockState, PrinterBlockEntity printer) {
        printer.tick();
    }

    private void tick() {
        State previous = state;
        ItemStack template = inventory.getStackInSlot(SLOT_TEMPLATE);
        int costPerTick = MC3DPrintConfig.T1_ENERGY_PER_TICK.get();

        if (template.isEmpty()) {
            state = State.IDLE;
            progress = 0;
        } else if (!canEmitCopy(template)) {
            state = State.PAUSED_OUTPUT_FULL;
        } else if (!energy.hasAtLeast(costPerTick)) {
            state = State.PAUSED_NO_POWER;
        } else {
            state = State.PRINTING;
            energy.consume(costPerTick);
            progress++;
            if (progress >= maxProgress()) {
                emitCopy(template);
                progress = 0;
            }
        }

        if (state != previous) {
            setChanged();
        }
    }

    private boolean canEmitCopy(ItemStack template) {
        ItemStack output = inventory.getStackInSlot(SLOT_OUTPUT);
        if (output.isEmpty()) {
            return true;
        }
        return ItemStack.isSameItemSameTags(output, template) && output.getCount() < output.getMaxStackSize();
    }

    private void emitCopy(ItemStack template) {
        ItemStack output = inventory.getStackInSlot(SLOT_OUTPUT);
        if (output.isEmpty()) {
            inventory.setStackInSlot(SLOT_OUTPUT, template.copyWithCount(1));
        } else {
            output.grow(1);
            inventory.setStackInSlot(SLOT_OUTPUT, output);
        }
    }

    public int maxProgress() {
        return MC3DPrintConfig.T1_ITEM_PRINT_TICKS.get();
    }

    public ItemStackHandler inventory() {
        return inventory;
    }

    public ContainerData containerData() {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case DATA_PROGRESS -> progress;
                    case DATA_MAX_PROGRESS -> maxProgress();
                    case DATA_ENERGY -> energy.getEnergyStored();
                    case DATA_MAX_ENERGY -> energy.getMaxEnergyStored();
                    case DATA_STATE -> state.ordinal();
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                if (index == DATA_PROGRESS) {
                    progress = value;
                }
            }

            @Override
            public int getCount() {
                return DATA_COUNT;
            }
        };
    }

    // --- MenuProvider ---

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.mc3dprint.tier1_printer");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int windowId, Inventory playerInventory, Player player) {
        return new PrinterMenu(windowId, playerInventory, this);
    }

    // --- Capabilities ---

    @Override
    @Nonnull
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ENERGY) {
            return energyCap.cast();
        }
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            if (side == null) {
                return allCap.cast();
            }
            // Top inserts the template; every other face is extract-only output.
            // Sides will be reclaimed for filament spools in M5.
            return side == Direction.UP ? inputCap.cast() : outputCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        energyCap.invalidate();
        inputCap.invalidate();
        outputCap.invalidate();
        allCap.invalidate();
    }

    // --- Persistence ---

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Inventory", inventory.serializeNBT());
        tag.putInt("Energy", energy.getEnergyStored());
        tag.putInt("Progress", progress);
        tag.putInt("State", state.ordinal());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        inventory.deserializeNBT(tag.getCompound("Inventory"));
        energy.setStored(tag.getInt("Energy"));
        progress = tag.getInt("Progress");
        state = State.byOrdinal(tag.getInt("State"));
    }
}
