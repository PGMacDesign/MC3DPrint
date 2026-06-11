package com.pgmacdesign.mc3dprint.machine;

import com.pgmacdesign.mc3dprint.config.MC3DPrintConfig;
import com.pgmacdesign.mc3dprint.fu.FuValue;
import com.pgmacdesign.mc3dprint.fu.FuValueRegistry;
import com.pgmacdesign.mc3dprint.fu.SpoolItem;
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
import java.util.Optional;

/**
 * Filament Winder (Tier 1): converts materials into FU wound onto a spool.
 * RF is consumed at winding per the design doc. Winder tier gates inputs —
 * a T1 winder refuses diamonds, closing the print-tier bypass.
 */
public class WinderBlockEntity extends BlockEntity implements MenuProvider {
    public static final int SLOT_INPUT = 0;
    public static final int SLOT_SPOOL = 1;
    public static final int SLOT_COUNT = 2;

    public static final int DATA_PROGRESS = 0;
    public static final int DATA_MAX_PROGRESS = 1;
    public static final int DATA_ENERGY = 2;
    public static final int DATA_MAX_ENERGY = 3;
    public static final int DATA_SPOOL_FU = 4;
    public static final int DATA_SPOOL_CAP = 5;
    public static final int DATA_COUNT = 6;

    public static final int WINDER_TIER = 1;

    private final ItemStackHandler inventory = new ItemStackHandler(SLOT_COUNT) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            if (slot == SLOT_SPOOL) {
                return stack.getItem() instanceof SpoolItem;
            }
            return true;
        }
    };

    private final MachineEnergyStorage energy = new MachineEnergyStorage(
            MC3DPrintConfig.WINDER_ENERGY_BUFFER.get(),
            MC3DPrintConfig.WINDER_MAX_ENERGY_RECEIVE.get(),
            this::setChanged);

    private final LazyOptional<MachineEnergyStorage> energyCap = LazyOptional.of(() -> energy);
    private final LazyOptional<IItemHandler> inputCap =
            LazyOptional.of(() -> new RangedWrapper(inventory, SLOT_INPUT, SLOT_INPUT + 1));
    private final LazyOptional<IItemHandler> allCap = LazyOptional.of(() -> inventory);

    private int progress;

    public WinderBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.FILAMENT_WINDER.get(), pos, blockState);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState blockState, WinderBlockEntity winder) {
        winder.tick();
    }

    private void tick() {
        ItemStack input = inventory.getStackInSlot(SLOT_INPUT);
        ItemStack spool = inventory.getStackInSlot(SLOT_SPOOL);

        Optional<FuValue> value = FuValueRegistry.valueOf(input);
        if (value.isEmpty() || value.get().tier() > WINDER_TIER
                || !(spool.getItem() instanceof SpoolItem spoolItem)
                || SpoolItem.getFu(spool) + value.get().fu() > spoolItem.capacity()
                || !energy.hasAtLeast(MC3DPrintConfig.WINDER_RF_PER_ITEM.get())) {
            progress = 0;
            return;
        }

        progress++;
        if (progress >= MC3DPrintConfig.WINDER_TICKS_PER_ITEM.get()) {
            progress = 0;
            energy.consume(MC3DPrintConfig.WINDER_RF_PER_ITEM.get());
            SpoolItem.fill(spool, value.get().fu());
            input.shrink(1);
            inventory.setStackInSlot(SLOT_INPUT, input);
            inventory.setStackInSlot(SLOT_SPOOL, spool);
            setChanged();
        }
    }

    public ItemStackHandler inventory() {
        return inventory;
    }

    public ContainerData containerData() {
        return new ContainerData() {
            @Override
            public int get(int index) {
                ItemStack spool = inventory.getStackInSlot(SLOT_SPOOL);
                return switch (index) {
                    case DATA_PROGRESS -> progress;
                    case DATA_MAX_PROGRESS -> MC3DPrintConfig.WINDER_TICKS_PER_ITEM.get();
                    case DATA_ENERGY -> energy.getEnergyStored();
                    case DATA_MAX_ENERGY -> energy.getMaxEnergyStored();
                    case DATA_SPOOL_FU -> SpoolItem.getFu(spool);
                    case DATA_SPOOL_CAP -> spool.getItem() instanceof SpoolItem s ? s.capacity() : 0;
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

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.mc3dprint.filament_winder");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int windowId, Inventory playerInventory, Player player) {
        return new WinderMenu(windowId, playerInventory, this);
    }

    @Override
    @Nonnull
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ENERGY) {
            return energyCap.cast();
        }
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return side == null ? allCap.cast() : inputCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        energyCap.invalidate();
        inputCap.invalidate();
        allCap.invalidate();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Inventory", inventory.serializeNBT());
        tag.putInt("Energy", energy.getEnergyStored());
        tag.putInt("Progress", progress);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        inventory.deserializeNBT(tag.getCompound("Inventory"));
        energy.setStored(tag.getInt("Energy"));
        progress = tag.getInt("Progress");
    }
}
