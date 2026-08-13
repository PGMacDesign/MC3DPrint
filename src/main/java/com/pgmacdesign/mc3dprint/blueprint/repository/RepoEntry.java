package com.pgmacdesign.mc3dprint.blueprint.repository;

import com.pgmacdesign.mc3dprint.compat.NbtCompat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * One catalogued blueprint in a {@link RepositoryData} library. Stores the same
 * denormalized metadata a Blueprint Disc caches (name/size/count/tier/cost +
 * official flag) so the repository GUI never has to load full blueprints just to
 * list them — only burning re-loads the actual blueprint by {@link #id}.
 *
 * <p>{@link #depositor} is who put it in the library, and is what lets a player remove
 * their own mistake from a SHARED catalogue without being able to touch anyone else's
 * contribution. Null for a curated build, and for anything catalogued before the field
 * existed; those are operator-only to remove.
 */
public record RepoEntry(UUID id, String name, int sizeX, int sizeY, int sizeZ,
                        int blockCount, int tier, int cost, boolean official,
                        @Nullable UUID depositor) {

    /** A catalogue entry with no recorded depositor (curated builds, and pre-existing data). */
    public RepoEntry(UUID id, String name, int sizeX, int sizeY, int sizeZ,
                     int blockCount, int tier, int cost, boolean official) {
        this(id, name, sizeX, sizeY, sizeZ, blockCount, tier, cost, official, null);
    }

    /** The same entry under a different display name. */
    public RepoEntry withName(String newName) {
        return new RepoEntry(id, newName, sizeX, sizeY, sizeZ, blockCount, tier, cost,
                official, depositor);
    }

    /** True when {@code player} deposited this entry (never true for an unattributed one). */
    public boolean depositedBy(UUID player) {
        return depositor != null && depositor.equals(player);
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        NbtCompat.putUUID(tag, "Id", id);
        tag.putString("Name", name);
        tag.putIntArray("Size", new int[]{sizeX, sizeY, sizeZ});
        tag.putInt("Count", blockCount);
        tag.putInt("Tier", tier);
        tag.putInt("Cost", cost);
        tag.putBoolean("Official", official);
        if (depositor != null) {
            NbtCompat.putUUID(tag, "Depositor", depositor);
        }
        return tag;
    }

    public static RepoEntry fromNbt(CompoundTag tag) {
        int[] size = NbtCompat.getIntArray(tag, "Size");
        int sx = size.length == 3 ? size[0] : 0;
        int sy = size.length == 3 ? size[1] : 0;
        int sz = size.length == 3 ? size[2] : 0;
        return new RepoEntry(NbtCompat.getUUID(tag, "Id").orElseThrow(), NbtCompat.getString(tag, "Name"), sx, sy, sz,
                NbtCompat.getInt(tag, "Count"), NbtCompat.getInt(tag, "Tier"), NbtCompat.getInt(tag, "Cost"),
                NbtCompat.getBoolean(tag, "Official"),
                // Absent on every entry catalogued before depositors were tracked. Those load
                // unattributed rather than failing, and stay operator-only to remove.
                NbtCompat.getUUID(tag, "Depositor").orElse(null));
    }

    public void toBuf(FriendlyByteBuf buf) {
        buf.writeUUID(id);
        buf.writeUtf(name);
        buf.writeVarInt(sizeX);
        buf.writeVarInt(sizeY);
        buf.writeVarInt(sizeZ);
        buf.writeVarInt(blockCount);
        buf.writeVarInt(tier);
        buf.writeVarInt(cost);
        buf.writeBoolean(official);
        // Explicit presence flag rather than writeOptional: the generic form needs a
        // Writer<T> whose inference differs across the version nodes.
        buf.writeBoolean(depositor != null);
        if (depositor != null) {
            buf.writeUUID(depositor);
        }
    }

    public static RepoEntry fromBuf(FriendlyByteBuf buf) {
        return new RepoEntry(buf.readUUID(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(),
                buf.readBoolean() ? buf.readUUID() : null);
    }
}
