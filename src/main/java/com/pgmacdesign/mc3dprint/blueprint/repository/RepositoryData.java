package com.pgmacdesign.mc3dprint.blueprint.repository;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
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

    public static RepositoryData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(RepositoryData::new, RepositoryData::load, DataFixTypes.LEVEL), NAME);
    }

    static RepositoryData load(CompoundTag tag, HolderLookup.Provider registries) {
        RepositoryData data = new RepositoryData();
        for (Tag element : tag.getList("Entries", Tag.TAG_COMPOUND)) {
            RepoEntry entry = RepoEntry.fromNbt((CompoundTag) element);
            data.entries.put(entry.id(), entry);
        }
        for (Tag element : tag.getList("Printed", Tag.TAG_STRING)) {
            data.printed.add(UUID.fromString(element.getAsString()));
        }
        return data;
    }

    @Override
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
