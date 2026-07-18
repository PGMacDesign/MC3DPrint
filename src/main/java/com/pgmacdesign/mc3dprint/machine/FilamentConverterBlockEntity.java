package com.pgmacdesign.mc3dprint.machine;

import com.pgmacdesign.mc3dprint.compat.BeData;

import com.pgmacdesign.mc3dprint.compat.TransferCompat;
import com.pgmacdesign.mc3dprint.config.MC3DPrintConfig;
import com.pgmacdesign.mc3dprint.fu.FuConversion;
import com.pgmacdesign.mc3dprint.fu.FuValue;
import com.pgmacdesign.mc3dprint.fu.FuValueRegistry;
import com.pgmacdesign.mc3dprint.fu.SpoolItem;
import com.pgmacdesign.mc3dprint.registry.ModBlockEntities;
import com.pgmacdesign.mc3dprint.registry.ModItemTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;

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
 *
 * <p>Honors the same {@link ModItemTags#WINDER_BLACKLIST} gate as the Filament
 * Winder: a blacklisted filter item (e.g. a stick) is never pulled or
 * converted, so the converter can't be used to automate the FU-laundering
 * exploit the winder blocks. See that tag's javadoc.
 */
public class FilamentConverterBlockEntity extends BlockEntity {
    private final MachineEnergyStorage energy = new MachineEnergyStorage(
            MC3DPrintConfig.WINDER_ENERGY_BUFFER.get(),
            MC3DPrintConfig.WINDER_MAX_ENERGY_RECEIVE.get(),
            this::setChanged);

    private ItemStack filter = ItemStack.EMPTY;
    private int cooldown;

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
        // Same gate as the winder: a blacklisted filter still has an FU value
        // but must never be converted (it would automate the stick-laundering
        // exploit). Bail before pulling so blacklisted items are left in place.
        if (value.isEmpty() || ModItemTags.isWinderBlacklisted(filter)
                || !energy.hasAtLeast(MC3DPrintConfig.WINDER_RF_PER_ITEM.get())) {
            return;
        }

        SpoolTarget target = findSpoolWithRoom(level, pos, value.get());
        if (target == null) {
            return;
        }
        if (!pullOneFilterItem(level, pos)) {
            return;
        }
        SpoolItem spoolItem = (SpoolItem) target.spool().getItem();
        long yield = FuConversion.windYield(value.get().fu(), value.get().tier(),
                spoolItem.tier(), FuConversion.ratio());
        SpoolItem.fill(target.spool(), FuConversion.clampToInt(yield));
        target.printer().notifySpoolsChanged(); // exterior spool render tracks fill
        energy.consume(MC3DPrintConfig.WINDER_RF_PER_ITEM.get());
        setChanged();
    }

    private record SpoolTarget(PrinterBlockEntity printer, ItemStack spool) {
    }

    /**
     * A docked, non-creative spool on an adjacent printer that can take the
     * material's full yield. Exact-tier rule (hard rule): the spool's tier must
     * equal the material's tier — same gate as the Filament Winder.
     */
    @Nullable
    private SpoolTarget findSpoolWithRoom(Level level, BlockPos pos, FuValue value) {
        int ratio = FuConversion.ratio();
        for (Direction direction : Direction.values()) {
            if (level.getBlockEntity(pos.relative(direction)) instanceof PrinterBlockEntity printer) {
                var spools = printer.spoolInventory();
                for (int i = 0; i < spools.getSlots(); i++) {
                    ItemStack spool = spools.getStackInSlot(i);
                    if (spool.getItem() instanceof SpoolItem spoolItem && !spoolItem.creative()
                            && FuConversion.canWindInto(value.tier(), spoolItem.tier())
                            && SpoolItem.getFu(spool) + FuConversion.windYield(value.fu(),
                                    value.tier(), spoolItem.tier(), ratio) <= spoolItem.capacity()) {
                        return new SpoolTarget(printer, spool);
                    }
                }
            }
        }
        return null;
    }

    private boolean pullOneFilterItem(Level level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockEntity neighbor = level.getBlockEntity(pos.relative(direction));
            if (neighbor == null || neighbor instanceof PrinterBlockEntity
                    || neighbor instanceof FilamentConverterBlockEntity) {
                continue; // never raid printers (their faces expose outputs)
            }
            IItemHandler handler = TransferCompat.findItems(level,
                    pos.relative(direction), direction.getOpposite());
            if (handler == null) {
                continue;
            }
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack inSlot = handler.getStackInSlot(slot);
                if (!inSlot.isEmpty() && ItemStack.isSameItemSameComponents(inSlot, filter)) {
                    if (!handler.extractItem(slot, 1, false).isEmpty()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // --- Capabilities (exposed raw; registered centrally in ModCapabilities) ---

    public IEnergyStorage getEnergyStorage() {
        return energy;
    }

    //? if >=1.21.5 {
    /*@Override
    protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput out) {
        super.saveAdditional(out);
        writeData(BeData.writer(out));
    }

    @Override
    protected void loadAdditional(net.minecraft.world.level.storage.ValueInput in) {
        super.loadAdditional(in);
        readData(BeData.reader(in));
    }
    *///?} else {
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        writeData(BeData.writer(tag, registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        readData(BeData.reader(tag, registries));
    }
    //?}

    private void writeData(BeData.Writer w) {
        w.putInt("Energy", energy.getEnergyStored());
        if (!filter.isEmpty()) {
            w.store("Filter", ItemStack.CODEC, filter);
        }
    }

    private void readData(BeData.Reader r) {
        energy.setStored(r.getIntOr("Energy", 0));
        filter = r.read("Filter", ItemStack.CODEC).orElse(ItemStack.EMPTY);
    }
}
