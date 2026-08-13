package com.pgmacdesign.mc3dprint.machine.repository;

import com.pgmacdesign.mc3dprint.compat.BeData;

import com.pgmacdesign.mc3dprint.blueprint.Blueprint;
import com.pgmacdesign.mc3dprint.blueprint.BlueprintFileStore;
import com.pgmacdesign.mc3dprint.blueprint.repository.RepoEntry;
import com.pgmacdesign.mc3dprint.blueprint.repository.RepositoryIndex;
import com.pgmacdesign.mc3dprint.item.BlueprintDiscItem;
import com.pgmacdesign.mc3dprint.network.MC3DPrintNetwork;
import com.pgmacdesign.mc3dprint.network.RepositoryListingPacket;
import com.pgmacdesign.mc3dprint.network.RepositoryRenamePacket;
import com.pgmacdesign.mc3dprint.registry.ModBlockEntities;
import com.pgmacdesign.mc3dprint.registry.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Server Blueprint Repository: a library you build by depositing the discs you
 * find/scan, then re-burn any catalogued build onto a blank disc on demand. The
 * library itself lives in {@link RepositoryIndex} (shared world store or per-player
 * NBT, per config) — never in this block — so breaking the block loses nothing.
 */
public class BlueprintRepositoryBlockEntity extends BlockEntity implements MenuProvider {
    public static final int SLOT_IN = 0;   // blank disc (to burn) OR written disc (to deposit)
    public static final int SLOT_OUT = 1;  // freshly burned written disc
    public static final int SLOT_COUNT = 2;

    private final net.neoforged.neoforge.items.ItemStackHandler inventory =
            new net.neoforged.neoforge.items.ItemStackHandler(SLOT_COUNT) {
                @Override
                protected void onContentsChanged(int slot) {
                    setChanged();
                }

                @Override
                public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
                    if (slot == SLOT_OUT) {
                        return false; // output only
                    }
                    return stack.getItem() == ModItems.BLANK_BLUEPRINT_DISC.get()
                            || stack.getItem() instanceof BlueprintDiscItem;
                }
            };

    public BlueprintRepositoryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BLUEPRINT_REPOSITORY.get(), pos, state);
    }

    public net.neoforged.neoforge.items.ItemStackHandler inventory() {
        return inventory;
    }

    /** The viewer's catalogue, sorted — the order the GUI indexes into for selection. */
    public List<RepoEntry> listingFor(ServerPlayer player) {
        return RepositoryIndex.entries(player);
    }

    public void sendListing(ServerPlayer player) {
        MC3DPrintNetwork.sendTo(player, new RepositoryListingPacket(
                listingFor(player), new java.util.ArrayList<>(RepositoryIndex.printedIds(player))));
    }

    /** Catalogue the written disc in the input slot, consuming it. */
    public void deposit(ServerPlayer player) {
        if (level == null || level.getServer() == null) {
            return;
        }
        ItemStack in = inventory.getStackInSlot(SLOT_IN);
        if (!(in.getItem() instanceof BlueprintDiscItem)) {
            feedback(player, "deposit_need_written");
            return;
        }
        Optional<UUID> id = BlueprintDiscItem.getBlueprintId(in);
        if (id.isEmpty()) {
            feedback(player, "deposit_empty_disc");
            return;
        }
        if (!BlueprintFileStore.forServer(level.getServer()).exists(id.get())) {
            feedback(player, "deposit_missing"); // disc from another world; can't be re-burned here
            return;
        }
        if (RepositoryIndex.add(player, entryFromDisc(in, id.get()))) {
            in.shrink(1); // consumed into the library
            inventory.setStackInSlot(SLOT_IN, in);
            level.playSound(null, getBlockPos(), SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.7F, 1.3F);
            feedback(player, "deposit_ok");
            sendListing(player);
            return;
        }
        // Already catalogued: recycle the duplicate written disc back into a Blank
        // Blueprint Disc instead of wasting it — the library already has this build,
        // so erasing the disc and handing back a reusable blank is the friendly move.
        // A locked disc is protected from erasure, so leave it untouched.
        if (BlueprintDiscItem.isLocked(in)) {
            feedback(player, "deposit_dupe_locked");
            return;
        }
        inventory.setStackInSlot(SLOT_IN, new ItemStack(ModItems.BLANK_BLUEPRINT_DISC.get()));
        level.playSound(null, getBlockPos(), SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.5F, 1.4F);
        feedback(player, "deposit_dupe_blanked");
    }

    /** Burn the selected catalogued blueprint onto the blank disc in the input slot. */
    public void burn(ServerPlayer player, @Nullable UUID selectedId) {
        if (level == null || level.getServer() == null) {
            return;
        }
        if (selectedId == null || !RepositoryIndex.contains(player, selectedId)) {
            feedback(player, "burn_no_selection");
            return;
        }
        ItemStack in = inventory.getStackInSlot(SLOT_IN);
        if (in.getItem() != ModItems.BLANK_BLUEPRINT_DISC.get()) {
            feedback(player, "burn_need_blank");
            return;
        }
        if (!inventory.getStackInSlot(SLOT_OUT).isEmpty()) {
            feedback(player, "burn_output_blocked");
            return;
        }
        Optional<Blueprint> blueprint = BlueprintFileStore.forServer(level.getServer()).load(selectedId);
        if (blueprint.isEmpty()) {
            feedback(player, "deposit_missing");
            return;
        }
        boolean official = listingFor(player).stream()
                .filter(e -> e.id().equals(selectedId)).findFirst().map(RepoEntry::official).orElse(true);
        ItemStack disc = new ItemStack(ModItems.BLUEPRINT_DISC.get());
        // playerCreated is the inverse of official; preserves the resin anti-dupe gate.
        BlueprintDiscItem.writeBlueprint(disc, selectedId, blueprint.get(), !official);
        inventory.setStackInSlot(SLOT_OUT, disc);
        in.shrink(1);
        inventory.setStackInSlot(SLOT_IN, in);
        level.playSound(null, getBlockPos(), SoundEvents.UI_LOOM_TAKE_RESULT, SoundSource.BLOCKS, 0.7F, 1.1F);
        feedback(player, "burn_ok");
    }

    /**
     * Retitles a catalogued scan, in the index AND in the stored blueprint, so a disc re-burned
     * later carries the new name too.
     *
     * <p>Official builds are refused: curated names are shipped content, and in shared mode one
     * player's edit would retitle the build for the entire server. Player scans are the whole
     * point — "Scan @ 307,70,10" stops being useful the moment a library holds two of them.
     */
    public void rename(ServerPlayer player, UUID id, String rawName) {
        if (level == null || level.getServer() == null) {
            return;
        }
        String name = RepositoryRenamePacket.sanitize(rawName);
        if (name.isEmpty()) {
            feedback(player, "rename_blank");
            return;
        }
        RepoEntry entry = RepositoryIndex.find(player, id);
        if (entry == null) {
            feedback(player, "rename_missing");
            return;
        }
        if (entry.official()) {
            feedback(player, "rename_official");
            return;
        }
        if (!RepositoryIndex.rename(player, id, name)) {
            feedback(player, "rename_missing");
            return;
        }
        // Best effort on the file: the catalogue is what the GUI lists, so a rename still
        // "worked" for the player even if the blueprint itself can't be rewritten. Only the
        // name a future re-burn stamps on a disc would lag.
        BlueprintFileStore store = BlueprintFileStore.forServer(level.getServer());
        store.load(id).ifPresent(blueprint -> store.save(id, blueprint.withName(name)));

        level.playSound(null, getBlockPos(), SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.6F, 1.2F);
        feedback(player, "rename_ok");
        sendListing(player);
    }

    private static RepoEntry entryFromDisc(ItemStack disc, UUID id) {
        int[] size = BlueprintDiscItem.getSize(disc);
        int sx = size.length == 3 ? size[0] : 0;
        int sy = size.length == 3 ? size[1] : 0;
        int sz = size.length == 3 ? size[2] : 0;
        return new RepoEntry(id, BlueprintDiscItem.getBlueprintName(disc), sx, sy, sz,
                BlueprintDiscItem.getBlockCount(disc), BlueprintDiscItem.getTier(disc),
                BlueprintDiscItem.getPrintCost(disc), BlueprintDiscItem.isOfficial(disc));
    }

    private void feedback(ServerPlayer player, String key) {
        com.pgmacdesign.mc3dprint.compat.MsgCompat.actionBar(player, Component.translatable("message.mc3dprint.repository." + key));
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int windowId, Inventory playerInventory, Player player) {
        return new BlueprintRepositoryMenu(windowId, playerInventory, this);
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
        w.putHandler("Inventory", inventory);
    }

    private void readData(BeData.Reader r) {
        r.readHandler("Inventory", inventory);
    }
}
