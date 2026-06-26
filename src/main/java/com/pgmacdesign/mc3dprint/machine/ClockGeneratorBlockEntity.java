package com.pgmacdesign.mc3dprint.machine;

import com.pgmacdesign.mc3dprint.compat.NbtCompat;

import com.pgmacdesign.mc3dprint.config.MC3DPrintConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static com.pgmacdesign.mc3dprint.registry.ModBlockEntities.CLOCK_GENERATOR;

/**
 * Clock Generator: entry-level power so the mod is usable without any other RF
 * mod installed. Burns standard furnace fuel (coal, charcoal, coal blocks,
 * lava buckets, ...) at a configurable multiple of its furnace burn time —
 * super efficient, but never free. Generates a configurable RF per tick while
 * burning, pushes it to adjacent machines, and exposes an extract-only energy
 * capability for cables. Fuel goes in by hand (right-click) or hopper.
 */
public class ClockGeneratorBlockEntity extends BlockEntity implements MenuProvider {
    /** Buffer holds this many ticks of generation so brief disconnects don't void RF. */
    private static final int BUFFER_TICKS = 200;

    public static final int DATA_ENERGY = 0;
    public static final int DATA_MAX_ENERGY = 1;
    public static final int DATA_BURN_REMAINING = 2;
    public static final int DATA_BURN_TOTAL = 3;
    public static final int DATA_RATE = 4;
    public static final int DATA_COUNT = 5;

    private int stored;
    private int burnRemaining;
    /** Boosted burn time of the currently-igniting fuel item; 0 when not burning. Drives the GUI flame fill. */
    private int burnTotal;

    private final ItemStackHandler fuel = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return isFuel(stack);
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    private final IEnergyStorage energy = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return 0;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            int extracted = Math.min(maxExtract, stored);
            if (extracted > 0 && !simulate) {
                stored -= extracted;
                setChanged();
            }
            return extracted;
        }

        @Override
        public int getEnergyStored() {
            return stored;
        }

        @Override
        public int getMaxEnergyStored() {
            return capacity();
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return false;
        }
    };

    public ClockGeneratorBlockEntity(BlockPos pos, BlockState blockState) {
        super(CLOCK_GENERATOR.get(), pos, blockState);
    }

    public static int ratePerTick() {
        return MC3DPrintConfig.CLOCK_GENERATOR_RF_PER_TICK.get();
    }

    public static int burnMultiplier() {
        return MC3DPrintConfig.CLOCK_GENERATOR_BURN_MULTIPLIER.get();
    }

    public static boolean isFuel(ItemStack stack) {
        return stack.getBurnTime(RecipeType.SMELTING) > 0;
    }

    public static int capacity() {
        return ratePerTick() * BUFFER_TICKS;
    }

    public int storedEnergy() {
        return stored;
    }

    public int burnTicksRemaining() {
        return burnRemaining;
    }

    public int burnTicksTotal() {
        return burnTotal;
    }

    /** RF/t the generator is producing right now (0 when not burning). */
    public int currentRate() {
        return burnRemaining > 0 ? ratePerTick() : 0;
    }

    public ContainerData containerData() {
        return new SplitContainerData(DATA_COUNT, this::dataValue);
    }

    private int dataValue(int index) {
        return switch (index) {
            case DATA_ENERGY -> stored;
            case DATA_MAX_ENERGY -> capacity();
            case DATA_BURN_REMAINING -> burnRemaining;
            case DATA_BURN_TOTAL -> burnTotal;
            case DATA_RATE -> currentRate();
            default -> 0;
        };
    }

    public ItemStackHandler fuel() {
        return fuel;
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int windowId, Inventory playerInventory, Player player) {
        return new SimpleGeneratorMenu(windowId, playerInventory, this);
    }

    /** Inserts one fuel item from the held stack; returns its boosted burn time, or 0 if rejected. */
    public int addFuel(ItemStack held) {
        if (!isFuel(held)) {
            return 0;
        }
        ItemStack one = held.copyWithCount(1);
        if (!fuel.insertItem(0, one, false).isEmpty()) {
            return 0; // slot occupied by a different fuel or full
        }
        held.shrink(1);
        return one.getBurnTime(RecipeType.SMELTING) * burnMultiplier();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ClockGeneratorBlockEntity generator) {
        generator.tick(level, pos);
    }

    private void tick(Level level, BlockPos pos) {
        int before = stored;

        // ignite the next fuel item only when there's room to use the output
        if (burnRemaining <= 0 && stored < capacity()) {
            ItemStack next = fuel.extractItem(0, 1, false);
            if (!next.isEmpty()) {
                burnRemaining = next.getBurnTime(RecipeType.SMELTING) * burnMultiplier();
                burnTotal = burnRemaining; // remember the full burn for the GUI flame fill
                // burnable containers (lava bucket) leave their empty container behind
                ItemStack remainder = next.getCraftingRemainingItem();
                if (!remainder.isEmpty() && fuel.getStackInSlot(0).isEmpty()) {
                    fuel.setStackInSlot(0, remainder);
                }
            }
        }

        if (burnRemaining > 0) {
            burnRemaining--;
            stored = Math.min(stored + ratePerTick(), capacity());
            if (burnRemaining <= 0) {
                burnTotal = 0; // fuel spent — empty the flame indicator
            }
        }

        for (Direction direction : Direction.values()) {
            if (stored <= 0) {
                break;
            }
            IEnergyStorage handler = level.getCapability(Capabilities.EnergyStorage.BLOCK,
                    pos.relative(direction), direction.getOpposite());
            if (handler != null && handler.canReceive()) {
                stored -= handler.receiveEnergy(stored, false);
            }
        }

        if (stored != before || burnRemaining > 0) {
            setChanged();
        }
    }

    // --- Capabilities (exposed raw; registered centrally in ModCapabilities) ---

    public IEnergyStorage getEnergyStorage() {
        return energy;
    }

    /** Fuel handler on every face (right-click / hopper feed). */
    public IItemHandler getItemHandler(@Nullable Direction side) {
        return fuel;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", stored);
        tag.putInt("BurnRemaining", burnRemaining);
        tag.putInt("BurnTotal", burnTotal);
        tag.put("Fuel", fuel.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        stored = Math.max(0, NbtCompat.getInt(tag, "Energy"));
        burnRemaining = Math.max(0, NbtCompat.getInt(tag, "BurnRemaining"));
        burnTotal = Math.max(0, NbtCompat.getInt(tag, "BurnTotal"));
        if (tag.contains("Fuel")) {
            fuel.deserializeNBT(registries, NbtCompat.getCompound(tag, "Fuel"));
        }
    }
}
