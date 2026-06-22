package com.pgmacdesign.mc3dprint.blueprint.repository;

import com.pgmacdesign.mc3dprint.config.MC3DPrintConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * One accessor for the repository library that hides the shared-vs-personal split.
 * When {@code blueprintRepositoryIsShared} is true (default), reads/writes go to
 * the world-level {@link RepositoryData}; when false, to the viewing player's
 * persistent NBT (so each player keeps a personal library that survives death and
 * block break). A malformed config value falls back to shared (the BooleanValue
 * default), so the "bad data -> true" guard is automatic.
 */
public final class RepositoryIndex {
    private static final String PERSONAL_TAG = "mc3dprint:RepoEntries";
    private static final String PERSONAL_PRINTED_TAG = "mc3dprint:RepoPrinted";

    private RepositoryIndex() {}

    public static boolean shared() {
        return MC3DPrintConfig.BLUEPRINT_REPOSITORY_SHARED.get();
    }

    /** The viewer's catalogue, sorted by display name then id for a stable, index-addressable order. */
    public static List<RepoEntry> entries(ServerPlayer player) {
        List<RepoEntry> list = new ArrayList<>(shared()
                ? RepositoryData.get(player.server).entries()
                : personalEntries(player));
        list.sort(Comparator.comparing((RepoEntry e) -> e.name().toLowerCase()).thenComparing(e -> e.id().toString()));
        return list;
    }

    public static boolean contains(ServerPlayer player, UUID id) {
        if (shared()) {
            return RepositoryData.get(player.server).contains(id);
        }
        for (RepoEntry e : personalEntries(player)) {
            if (e.id().equals(id)) {
                return true;
            }
        }
        return false;
    }

    /** Catalogues an entry; returns true if newly added. */
    public static boolean add(ServerPlayer player, RepoEntry entry) {
        if (shared()) {
            return RepositoryData.get(player.server).add(entry);
        }
        CompoundTag persisted = persisted(player);
        ListTag list = persisted.getList(PERSONAL_TAG, Tag.TAG_COMPOUND);
        for (Tag element : list) {
            if (((CompoundTag) element).getUUID("Id").equals(entry.id())) {
                return false; // already catalogued
            }
        }
        list.add(entry.toNbt());
        persisted.put(PERSONAL_TAG, list);
        return true;
    }

    /**
     * Records that an (official) blueprint has been printed. Shared mode writes the
     * world store and never needs a player; personal mode needs the owner online —
     * an offline owner there simply doesn't get the mark (a deliberately niche gap).
     */
    public static void markPrinted(MinecraftServer server, @Nullable ServerPlayer owner, UUID id) {
        if (shared()) {
            RepositoryData.get(server).markPrinted(id);
            return;
        }
        if (owner == null) {
            return;
        }
        CompoundTag persisted = persisted(owner);
        ListTag list = persisted.getList(PERSONAL_PRINTED_TAG, Tag.TAG_STRING);
        String idString = id.toString();
        for (Tag element : list) {
            if (element.getAsString().equals(idString)) {
                return;
            }
        }
        list.add(StringTag.valueOf(idString));
        persisted.put(PERSONAL_PRINTED_TAG, list);
    }

    /** The set of blueprint UUIDs the viewer has printed (world set, or their own). */
    public static Set<UUID> printedIds(ServerPlayer viewer) {
        if (shared()) {
            return RepositoryData.get(viewer.server).printed();
        }
        Set<UUID> out = new HashSet<>();
        for (Tag element : persisted(viewer).getList(PERSONAL_PRINTED_TAG, Tag.TAG_STRING)) {
            out.add(UUID.fromString(element.getAsString()));
        }
        return out;
    }

    private static List<RepoEntry> personalEntries(ServerPlayer player) {
        List<RepoEntry> out = new ArrayList<>();
        for (Tag element : persisted(player).getList(PERSONAL_TAG, Tag.TAG_COMPOUND)) {
            out.add(RepoEntry.fromNbt((CompoundTag) element));
        }
        return out;
    }

    /** The player's persisted-through-death sub-tag (shared with other mod features). */
    private static CompoundTag persisted(ServerPlayer player) {
        CompoundTag root = player.getPersistentData();
        CompoundTag persisted = root.getCompound(Player.PERSISTED_NBT_TAG);
        root.put(Player.PERSISTED_NBT_TAG, persisted); // ensure it's attached for writes
        return persisted;
    }
}
