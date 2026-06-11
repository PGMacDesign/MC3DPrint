package com.pgmacdesign.mc3dprint.machine;

import com.pgmacdesign.mc3dprint.config.MC3DPrintConfig;
import com.pgmacdesign.mc3dprint.fu.FuConversion;
import com.pgmacdesign.mc3dprint.fu.FuValue;
import com.pgmacdesign.mc3dprint.fu.FuValueRegistry;
import com.pgmacdesign.mc3dprint.fu.SpoolItem;
import com.pgmacdesign.mc3dprint.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Filament Converter: end-game spool automation. Pulls the configured item
 * from adjacent inventories (chests, pipes, AE2/RS interfaces — anything with
 * an item handler), converts it to FU, and tops off spools docked on adjacent
 * printers. "The spool never runs out as long as the network has stock."
 *
 * Right-click with an item to set the filter; sneak+empty hand clears it.
 * Only converts when a docked spool can hold the full yield — no FU is ever
 * stranded or voided. Consumes winder-rate RF per item.
 */
public class FilamentConverterBlockEntity extends BlockEntity {
    private final MachineEnergyStorage energy = new MachineEnergyStorage(
            MC3DPrintConfig.WINDER_ENERGY_BUFFER.get(),
            MC3DPrintConfig.WINDER_MAX_ENERGY_RECEIVE.get(),
            this::setChanged);
    private final LazyOptional<MachineEnergyStorage> energyCap = LazyOptional.of(() -> energy);

    private ItemStack filter = ItemStack.EMPTY;
    private int cooldown;
    /** Sub-spool-unit FU remainder from tier conversion, in base (tier-1) units. */
    private long carryBase;

    public FilamentConverterBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.FILAMENT_CONVERTER.get(), pos, blockState);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, FilamentConverterBlockEntity converter) {
        converter.tick(level, pos);
    }

    public ItemStack filter() {
        return filter;
    }

    public void setFilter(ItemStack stack) {
        this.filter = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        setChanged();
    }

    private void tick(Level level, BlockPos pos) {
        if (filter.isEmpty()) {
            return;
        }
        if (++cooldown < MC3DPrintConfig.WINDER_TICKS_PER_ITEM.get()) {
            return;
        }
        cooldown = 0;

        Optional<FuValue> value = FuValueRegistry.valueOf(filter);
        if (value.isEmpty() || !energy.hasAtLeast(MC3DPrintConfig.WINDER_RF_PER_ITEM.get())) {
            return;
        }

        ItemStack spool = findSpoolWithRoom(level, pos);
        if (spool.isEmpty()) {
            return;
        }
        if (!pullOneFilterItem(level, pos)) {
            return;
        }
        // convert the material's FU to the spool's tier; sub-unit remainder
        // accumulates in carryBase so nothing is ever voided
        int ratio = FuConversion.ratio();
        carryBase += FuConversion.toBase(value.get().fu(), value.get().tier(), ratio);
        SpoolItem spoolItem = (SpoolItem) spool.getItem();
        long depositable = FuConversion.fromBase(carryBase, spoolItem.tier(), ratio);
        if (depositable > 0) {
            int deposited = SpoolItem.fill(spool, FuConversion.clampToInt(depositable));
            carryBase -= FuConversion.toBase(deposited, spoolItem.tier(), ratio);
        }
        energy.consume(MC3DPrintConfig.WINDER_RF_PER_ITEM.get());
        setChanged();
    }

    /** A docked, non-creative spool on an adjacent printer with room left. */
    private ItemStack findSpoolWithRoom(Level level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (level.getBlockEntity(pos.relative(direction)) instanceof PrinterBlockEntity printer) {
                var spools = printer.spoolInventory();
                for (int i = 0; i < spools.getSlots(); i++) {
                    ItemStack spool = spools.getStackInSlot(i);
                    if (spool.getItem() instanceof SpoolItem spoolItem && !spoolItem.creative()
                            && SpoolItem.getFu(spool) < spoolItem.capacity()) {
                        return spool;
                    }
                }
            }
        }
        return ItemStack.EMPTY;
    }

    private boolean pullOneFilterItem(Level level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockEntity neighbor = level.getBlockEntity(pos.relative(direction));
            if (neighbor == null || neighbor instanceof PrinterBlockEntity
                    || neighbor instanceof FilamentConverterBlockEntity) {
                continue; // never raid printers (their faces expose outputs)
            }
            IItemHandler handler = neighbor.getCapability(ForgeCapabilities.ITEM_HANDLER,
                    direction.getOpposite()).orElse(null);
            if (handler == null) {
                continue;
            }
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack inSlot = handler.getStackInSlot(slot);
                if (!inSlot.isEmpty() && ItemStack.isSameItemSameTags(inSlot, filter)) {
                    if (!handler.extractItem(slot, 1, false).isEmpty()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    @Nonnull
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ENERGY) {
            return energyCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        energyCap.invalidate();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Energy", energy.getEnergyStored());
        tag.putLong("CarryBase", carryBase);
        if (!filter.isEmpty()) {
            tag.put("Filter", filter.save(new CompoundTag()));
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        energy.setStored(tag.getInt("Energy"));
        carryBase = Math.max(0, tag.getLong("CarryBase"));
        filter = tag.contains("Filter") ? ItemStack.of(tag.getCompound("Filter")) : ItemStack.EMPTY;
    }
}
