package com.pgmacdesign.mc3dprint.machine.rack;

import com.pgmacdesign.mc3dprint.fu.FilamentDrain;
import com.pgmacdesign.mc3dprint.fu.FuConversion;
import com.pgmacdesign.mc3dprint.fu.IFilamentSource;
import com.pgmacdesign.mc3dprint.fu.SpoolItem;
import com.pgmacdesign.mc3dprint.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A bookshelf-style rack that shelves up to {@link #SLOTS} Filament Spools
 * (right-click to add, empty-hand to pop the last). Solves the "non-stacking
 * spools clutter your inventory" problem and doubles as a Filament-Unit
 * reservoir: it exposes {@link IFilamentSource}, so a directly-touching printer
 * (or one wired in via {@code MC3DCable}) drains the shelved spools when its
 * own docked spools run dry mid-print. Down-only tier rules are honored exactly
 * like the printer via {@link FilamentDrain}.
 */
public class FilamentRackBlockEntity extends BlockEntity implements IFilamentSource {
    public static final int SLOTS = 8;

    private final ItemStackHandler spools = new ItemStackHandler(SLOTS) {
        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return stack.getItem() instanceof SpoolItem;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1; // spools don't stack
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            sync();
        }
    };

    public FilamentRackBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FILAMENT_RACK.get(), pos, state);
    }

    public ItemStackHandler spools() {
        return spools;
    }

    /** Shelved spool count — drives the comparator output and the renderer. */
    public int spoolCount() {
        int n = 0;
        for (int i = 0; i < spools.getSlots(); i++) {
            if (!spools.getStackInSlot(i).isEmpty()) {
                n++;
            }
        }
        return n;
    }

    /** Shelve a spool into the lowest empty slot (stack push). True if accepted. */
    public boolean insertSpool(ItemStack held) {
        for (int i = 0; i < spools.getSlots(); i++) {
            if (spools.getStackInSlot(i).isEmpty()) {
                spools.setStackInSlot(i, held.split(1));
                return true;
            }
        }
        return false;
    }

    /** Pop and return the highest filled spool (LIFO), or empty if the rack is bare. */
    public ItemStack removeSpool() {
        for (int i = spools.getSlots() - 1; i >= 0; i--) {
            ItemStack spool = spools.getStackInSlot(i);
            if (!spool.isEmpty()) {
                spools.setStackInSlot(i, ItemStack.EMPTY);
                return spool;
            }
        }
        return ItemStack.EMPTY;
    }

    // --- IFilamentSource ---

    @Override
    public long drainExactTier(int tier, long maxBase) {
        if (maxBase <= 0) {
            return 0;
        }
        long leftover = FilamentDrain.drainTier(spools, maxBase, tier, FuConversion.ratio());
        return maxBase - leftover; // may exceed maxBase by one ceil unit (the source contract)
    }

    @Override
    public long availableExactTier(int tier) {
        return FilamentDrain.availableTier(spools, tier, FuConversion.ratio());
    }

    // --- Capability (exposed raw; registered centrally in ModCapabilities) ---

    public IFilamentSource getFilamentSource() {
        return this;
    }

    // --- Persistence + client sync (the renderer needs the live spool stacks) ---

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Spools", spools.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Spools")) {
            spools.deserializeNBT(registries, tag.getCompound("Spools"));
        }
    }

    private void sync() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.put("Spools", spools.serializeNBT(registries));
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains("Spools")) {
            spools.deserializeNBT(registries, tag.getCompound("Spools"));
        }
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        if (pkt.getTag() != null) {
            handleUpdateTag(pkt.getTag(), registries);
        }
    }
}
