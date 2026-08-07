package com.pgmacdesign.mc3dprint.blueprint.repository;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

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
        return server.overworld().getDataStorage().computeIfAbsent(
                RepositoryData::load, RepositoryData::new, NAME);
    }

    static RepositoryData load(CompoundTag tag) {
        RepositoryData data = new RepositoryData();
        for (Tag element : tag.getList("Entries", Tag.TAG_COMPOUND)) {
            RepoEntry entry = RepoEntry.fromNbt((CompoundTag) element);
            data.entries.put(entry.id(), entry);
        }
        for (Tag element : tag.getList("Printed", Tag.TAG_STRING)) {
            data.printed.add(UUID.fromString(element.getAsString()));
        }
        for (Tag element : tag.getList("Discovered", Tag.TAG_STRING)) {
            data.discovered.add(UUID.fromString(element.getAsString()));
        }
        data.discoverySeeded = tag.getBoolean("DiscoverySeeded");
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
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

    public List<RepoEntry> entries() {
        return List.copyOf(entries.values());
    }
}
