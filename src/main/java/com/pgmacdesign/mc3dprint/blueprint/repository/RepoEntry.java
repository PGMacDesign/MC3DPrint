package com.pgmacdesign.mc3dprint.blueprint.repository;

import com.pgmacdesign.mc3dprint.compat.NbtCompat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

/**
 * One catalogued blueprint in a {@link RepositoryData} library. Stores the same
 * denormalized metadata a Blueprint Disc caches (name/size/count/tier/cost +
 * official flag) so the repository GUI never has to load full blueprints just to
 * list them — only burning re-loads the actual blueprint by {@link #id}.
 */
public record RepoEntry(UUID id, String name, int sizeX, int sizeY, int sizeZ,
                        int blockCount, int tier, int cost, boolean official) {

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putString("Name", name);
        tag.putIntArray("Size", new int[]{sizeX, sizeY, sizeZ});
        tag.putInt("Count", blockCount);
        tag.putInt("Tier", tier);
        tag.putInt("Cost", cost);
        tag.putBoolean("Official", official);
        return tag;
    }

    public static RepoEntry fromNbt(CompoundTag tag) {
        int[] size = NbtCompat.getIntArray(tag, "Size");
        int sx = size.length == 3 ? size[0] : 0;
        int sy = size.length == 3 ? size[1] : 0;
        int sz = size.length == 3 ? size[2] : 0;
        return new RepoEntry(tag.getUUID("Id"), NbtCompat.getString(tag, "Name"), sx, sy, sz,
                NbtCompat.getInt(tag, "Count"), NbtCompat.getInt(tag, "Tier"), NbtCompat.getInt(tag, "Cost"), NbtCompat.getBoolean(tag, "Official"));
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
    }

    public static RepoEntry fromBuf(FriendlyByteBuf buf) {
        return new RepoEntry(buf.readUUID(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
    }
}
