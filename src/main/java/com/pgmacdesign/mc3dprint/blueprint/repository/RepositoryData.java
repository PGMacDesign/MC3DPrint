package com.pgmacdesign.mc3dprint.blueprint.repository;

import com.pgmacdesign.mc3dprint.compat.NbtCompat;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
//? if >=1.21.5 {
/*import com.mojang.serialization.Codec;
import net.minecraft.world.level.saveddata.SavedDataType;
*///?}

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The SHARED (world-level) blueprint library — one per world, stored on the
 * overworld's data storage so it survives any repository block being broken.
 * Personal (per-player) libraries live on player NBT instead; see
 * {@link RepositoryIndex} for the config-driven choice between the two.
 */
public class RepositoryData extends SavedData {
    private static final String NAME = "mc3dprint_repository";

    // 1.21.5+ persists SavedData through a Codec instead of save()/load(). Keep the imperative
    // CompoundTag logic and adapt it: CompoundTag.CODEC round-trips the raw tag, xmap bridges to
    // load()/save(). No registries needed — nothing here serializes an ItemStack.
    //? if >=1.21.5 {
    /*private static final Codec<RepositoryData> CODEC = CompoundTag.CODEC.xmap(
            tag -> load(tag, null),
            data -> data.save(new CompoundTag(), null));
    *///?}

    // Keyed by blueprint UUID so a build is catalogued at most once (re-deposit is a no-op).
    private final Map<UUID, RepoEntry> entries = new LinkedHashMap<>();
    // Blueprint UUIDs that have been printed at least once (official builds only).
    private final Set<UUID> printed = new LinkedHashSet<>();
    // Curated blueprint UUIDs found as world loot this cycle; cleared wholesale when the
    // cycle completes. Kept apart from `entries` so depositing or burning a disc never
    // moves a build in or out of the loot pool.
    private final Set<UUID> discovered = new LinkedHashSet<>();
    // Whether the one-time copy of `entries` into `discovered` has run. Re-running it
    // after a cycle reset would immediately re-narrow the pool back down, so it is
    // recorded permanently rather than inferred from either set being non-empty.
    private boolean discoverySeeded;

    public static RepositoryData get(MinecraftServer server) {
        //? if >=26.1 {
        /*return server.overworld().getDataStorage().computeIfAbsent(
                new SavedDataType<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                        com.pgmacdesign.mc3dprint.MC3DPrint.MOD_ID, NAME),
                        RepositoryData::new, CODEC, DataFixTypes.LEVEL));
        *///?} elif >=1.21.5 {
        /*return server.overworld().getDataStorage().computeIfAbsent(
                new SavedDataType<>(NAME, RepositoryData::new, CODEC, DataFixTypes.LEVEL));
        *///?} else {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(RepositoryData::new, RepositoryData::load, DataFixTypes.LEVEL), NAME);
        //?}
    }

    static RepositoryData load(CompoundTag tag, HolderLookup.Provider registries) {
        RepositoryData data = new RepositoryData();
        for (Tag element : NbtCompat.getList(tag, "Entries", Tag.TAG_COMPOUND)) {
            RepoEntry entry = RepoEntry.fromNbt((CompoundTag) element);
            data.entries.put(entry.id(), entry);
        }
        for (Tag element : NbtCompat.getList(tag, "Printed", Tag.TAG_STRING)) {
            data.printed.add(UUID.fromString(NbtCompat.tagAsString(element)));
        }
        for (Tag element : NbtCompat.getList(tag, "Discovered", Tag.TAG_STRING)) {
            data.discovered.add(UUID.fromString(NbtCompat.tagAsString(element)));
        }
        data.discoverySeeded = NbtCompat.getBoolean(tag, "DiscoverySeeded");
        return data;
    }

    // @Override only below 1.21.5 — the supertype no longer declares save(CompoundTag, Provider);
    // on 1.21.5+ this is a plain helper invoked by CODEC's xmap.
    //? if <1.21.5 {
    @Override
    //?}
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (RepoEntry entry : entries.values()) {
            list.add(entry.toNbt());
        }
        tag.put("Entries", list);
        ListTag printedList = new ListTag();
        for (UUID id : printed) {
            printedList.add(StringTag.valueOf(id.toString()));
        }
        tag.put("Printed", printedList);
        ListTag discoveredList = new ListTag();
        for (UUID id : discovered) {
            discoveredList.add(StringTag.valueOf(id.toString()));
        }
        tag.put("Discovered", discoveredList);
        tag.putBoolean("DiscoverySeeded", discoverySeeded);
        return tag;
    }

    /** Flags a blueprint as printed; returns true if it wasn't already. */
    public boolean markPrinted(UUID id) {
        boolean isNew = printed.add(id);
        if (isNew) {
            setDirty();
        }
        return isNew;
    }

    public Set<UUID> printed() {
        return Set.copyOf(printed);
    }

    /** Flags a blueprint as found in world loot; returns true if it wasn't already. */
    public boolean markDiscovered(UUID id) {
        boolean isNew = discovered.add(id);
        if (isNew) {
            setDirty();
        }
        return isNew;
    }

    public Set<UUID> discovered() {
        return Set.copyOf(discovered);
    }

    /**
     * Ends the current discovery cycle: clears the ledger in one write, then re-seeds it
     * with {@code keep} (the build that completed the cycle) so the very next roll cannot
     * hand back the disc just granted.
     */
    public void resetDiscovered(UUID keep) {
        discovered.clear();
        if (keep != null) {
            discovered.add(keep);
        }
        setDirty();
    }

    public boolean isDiscoverySeeded() {
        return discoverySeeded;
    }

    public void markDiscoverySeeded() {
        if (!discoverySeeded) {
            discoverySeeded = true;
            setDirty();
        }
    }

    /**
     * Arms the one-time catalogue seed to run again on the next loot roll. Never called
     * by the loot path itself, which must seed at most once: this is the operator action
     * behind {@code /mc3dprint discovered reseed}, for re-syncing after depositing a
     * batch of discs the ledger has no record of.
     */
    public void clearDiscoverySeeded() {
        if (discoverySeeded) {
            discoverySeeded = false;
            setDirty();
        }
    }

    /** Adds (or refreshes) an entry; returns true if it was newly catalogued. */
    public boolean add(RepoEntry entry) {
        boolean isNew = !entries.containsKey(entry.id());
        entries.put(entry.id(), entry);
        setDirty();
        return isNew;
    }

    public boolean contains(UUID id) {
        return entries.containsKey(id);
    }

    /** Retitles a catalogued entry in place. False when it isn't catalogued here. */
    public boolean rename(UUID id, String name) {
        RepoEntry existing = entries.get(id);
        if (existing == null) {
            return false;
        }
        entries.put(id, new RepoEntry(existing.id(), name, existing.sizeX(), existing.sizeY(),
                existing.sizeZ(), existing.blockCount(), existing.tier(), existing.cost(),
                existing.official()));
        setDirty();
        return true;
    }

    public List<RepoEntry> entries() {
        return List.copyOf(entries.values());
    }
}
