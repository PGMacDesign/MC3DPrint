package com.pgmacdesign.mc3dprint.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nullable;

//? if >=1.21.9 {
/*import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
*///?}

/**
 * Version seam for the NeoForge 21.9 transfer-API rewrite. 1.21.9 removed the
 * {@code Capabilities.EnergyStorage} / {@code Capabilities.ItemHandler} entries in favour of
 * {@code Capabilities.Energy} / {@code Capabilities.Item}, whose capability types are the new
 * transactional {@code EnergyHandler} / {@code ResourceHandler<ItemResource>}.
 *
 * <p>The legacy {@code IEnergyStorage} / {@code IItemHandler} interfaces still exist on 21.9
 * (deprecated), so they stay the mod's internal currency: every capability <em>query</em> goes
 * through {@link #findEnergy}/{@link #findItems}/{@link #hasEnergyCap} (on 1.21.9 the new handler
 * is wrapped back to legacy via {@code IEnergyStorage.of} / {@code IItemHandler.of}), and the
 * mod's own storages are <em>exposed</em> on 1.21.9 via the {@code energyHandler} /
 * {@code itemHandler} bridge factories — NeoForge ships no public legacy→new adapter, so those
 * bridges (with {@code SnapshotJournal}-backed transaction rollback) live here.
 */
public final class TransferCompat {
    private TransferCompat() {}

    /** Legacy view of the block energy capability at {@code pos} (null if absent). */
    @Nullable
    public static IEnergyStorage findEnergy(Level level, BlockPos pos, @Nullable Direction side) {
        //? if >=1.21.9 {
        /*EnergyHandler handler = level.getCapability(Capabilities.Energy.BLOCK, pos, side);
        return handler == null ? null : IEnergyStorage.of(handler);
        *///?} else {
        return level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, side);
        //?}
    }

    /** Legacy view of the block item capability at {@code pos} (null if absent). */
    @Nullable
    public static IItemHandler findItems(Level level, BlockPos pos, @Nullable Direction side) {
        //? if >=1.21.9 {
        /*ResourceHandler<ItemResource> handler = level.getCapability(Capabilities.Item.BLOCK, pos, side);
        return handler == null ? null : IItemHandler.of(handler);
        *///?} else {
        return level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side);
        //?}
    }

    /** True if an energy capability is exposed at {@code pos} (the cable connect check). */
    public static boolean hasEnergyCap(Level level, BlockPos pos, @Nullable Direction side) {
        //? if >=1.21.9 {
        /*return level.getCapability(Capabilities.Energy.BLOCK, pos, side) != null;
        *///?} else {
        return level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, side) != null;
        //?}
    }

    /**
     * Raw access to a legacy storage's stored-energy field, bypassing its receive/extract
     * restrictions. The mod's storages implement this so the 1.21.9 {@code energyHandler}
     * bridge can snapshot and revert them for transaction rollback; a storage without it
     * falls back to a stateless bridge that cannot roll back.
     */
    public interface RawEnergy {
        int rawEnergy();

        void rawEnergy(int value);
    }

    //? if >=1.21.9 {
    /*// Expose a legacy IEnergyStorage as a 1.21.9 EnergyHandler. Snapshot-correct when the
    // storage implements RawEnergy; otherwise falls back to a stateless bridge whose
    // mutations CANNOT be reverted if the enclosing transaction aborts — only correct for
    // infinite/creative storages with no meaningful state (e.g. the Creative Energy Source).
    public static EnergyHandler energyHandler(IEnergyStorage storage) {
        return storage instanceof RawEnergy raw
                ? new SnapshotEnergyBridge(storage, raw)
                : new StatelessEnergyBridge(storage);
    }

    // Expose a legacy modifiable item handler as a 1.21.9 ResourceHandler<ItemResource>,
    // with per-slot snapshot/revert for transaction rollback.
    public static ResourceHandler<ItemResource> itemHandler(
            net.neoforged.neoforge.items.IItemHandlerModifiable handler) {
        return new ItemHandlerBridge(handler);
    }

    // Transaction-correct energy bridge: snapshot = the raw stored int.
    private static final class SnapshotEnergyBridge extends SnapshotJournal<Integer>
            implements EnergyHandler {
        private final IEnergyStorage storage;
        private final RawEnergy raw;

        SnapshotEnergyBridge(IEnergyStorage storage, RawEnergy raw) {
            this.storage = storage;
            this.raw = raw;
        }

        @Override
        protected Integer createSnapshot() {
            return raw.rawEnergy();
        }

        @Override
        protected void revertToSnapshot(Integer snapshot) {
            raw.rawEnergy(snapshot);
        }

        @Override
        public long getAmountAsLong() {
            return storage.getEnergyStored();
        }

        @Override
        public long getCapacityAsLong() {
            return storage.getMaxEnergyStored();
        }

        @Override
        public int insert(int amount, TransactionContext tx) {
            if (amount <= 0 || !storage.canReceive()) {
                return 0;
            }
            if (storage.receiveEnergy(amount, true) <= 0) {
                return 0;
            }
            updateSnapshots(tx);
            return storage.receiveEnergy(amount, false);
        }

        @Override
        public int extract(int amount, TransactionContext tx) {
            if (amount <= 0 || !storage.canExtract()) {
                return 0;
            }
            if (storage.extractEnergy(amount, true) <= 0) {
                return 0;
            }
            updateSnapshots(tx);
            return storage.extractEnergy(amount, false);
        }
    }

    // No-rollback energy bridge for storages without RawEnergy. Mutations survive an
    // aborted transaction, so this is only correct for infinite/creative storages.
    private static final class StatelessEnergyBridge implements EnergyHandler {
        private final IEnergyStorage storage;

        StatelessEnergyBridge(IEnergyStorage storage) {
            this.storage = storage;
        }

        @Override
        public long getAmountAsLong() {
            return storage.getEnergyStored();
        }

        @Override
        public long getCapacityAsLong() {
            return storage.getMaxEnergyStored();
        }

        @Override
        public int insert(int amount, TransactionContext tx) {
            if (amount <= 0 || !storage.canReceive()) {
                return 0;
            }
            return storage.receiveEnergy(amount, false);
        }

        @Override
        public int extract(int amount, TransactionContext tx) {
            if (amount <= 0 || !storage.canExtract()) {
                return 0;
            }
            return storage.extractEnergy(amount, false);
        }
    }

    // Item bridge: snapshot = a defensive copy of every slot, reverted via setStackInSlot.
    // The default index-less insert/extract loops of ResourceHandler are deliberately not
    // overridden.
    private static final class ItemHandlerBridge
            extends SnapshotJournal<net.minecraft.core.NonNullList<net.minecraft.world.item.ItemStack>>
            implements ResourceHandler<ItemResource> {
        private final net.neoforged.neoforge.items.IItemHandlerModifiable handler;

        ItemHandlerBridge(net.neoforged.neoforge.items.IItemHandlerModifiable handler) {
            this.handler = handler;
        }

        @Override
        protected net.minecraft.core.NonNullList<net.minecraft.world.item.ItemStack> createSnapshot() {
            var copy = net.minecraft.core.NonNullList.withSize(
                    handler.getSlots(), net.minecraft.world.item.ItemStack.EMPTY);
            for (int i = 0; i < handler.getSlots(); i++) {
                copy.set(i, handler.getStackInSlot(i).copy());
            }
            return copy;
        }

        @Override
        protected void revertToSnapshot(net.minecraft.core.NonNullList<net.minecraft.world.item.ItemStack> snapshot) {
            for (int i = 0; i < snapshot.size(); i++) {
                handler.setStackInSlot(i, snapshot.get(i));
            }
        }

        @Override
        public int size() {
            return handler.getSlots();
        }

        @Override
        public ItemResource getResource(int index) {
            return ItemResource.of(handler.getStackInSlot(index));
        }

        @Override
        public long getAmountAsLong(int index) {
            return handler.getStackInSlot(index).getCount();
        }

        @Override
        public long getCapacityAsLong(int index, ItemResource resource) {
            return resource.isEmpty()
                    ? handler.getSlotLimit(index)
                    : Math.min(handler.getSlotLimit(index), resource.getMaxStackSize());
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
            return !resource.isEmpty() && handler.isItemValid(index, resource.toStack());
        }

        @Override
        public int insert(int index, ItemResource resource, int amount, TransactionContext tx) {
            if (resource.isEmpty() || amount <= 0) {
                return 0;
            }
            var remainder = handler.insertItem(index, resource.toStack(amount), true);
            if (amount - remainder.getCount() <= 0) {
                return 0;
            }
            updateSnapshots(tx);
            return amount - handler.insertItem(index, resource.toStack(amount), false).getCount();
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext tx) {
            if (resource.isEmpty() || amount <= 0
                    || !resource.matches(handler.getStackInSlot(index))) {
                return 0;
            }
            if (handler.extractItem(index, amount, true).isEmpty()) {
                return 0;
            }
            updateSnapshots(tx);
            return handler.extractItem(index, amount, false).getCount();
        }
    }
    *///?}
}
