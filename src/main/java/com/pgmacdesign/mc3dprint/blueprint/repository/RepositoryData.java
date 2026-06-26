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

    public static RepositoryData get(MinecraftServer server) {
        //? if >=1.21.5 {
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
