package com.pgmacdesign.mc3dprint.network;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.blueprint.repository.RepoEntry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server -> client: the catalogue the viewing player should see in an open
 * repository GUI, plus the subset of (official) builds they've printed — kept
 * separate from the entries since a build can be printed without being catalogued.
 */
public record RepositoryListingPacket(List<RepoEntry> entries, List<UUID> printed) implements CustomPacketPayload {

    public static final Type<RepositoryListingPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MC3DPrint.MOD_ID, "repository_listing"));

    // Hand-written over RegistryFriendlyByteBuf: RepoEntry has 9 fields and already
    // owns its toBuf/fromBuf, so we just length-prefix the two lists.
    public static final StreamCodec<RegistryFriendlyByteBuf, RepositoryListingPacket> STREAM_CODEC =
            StreamCodec.ofMember(RepositoryListingPacket::write, RepositoryListingPacket::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(entries.size());
        for (RepoEntry entry : entries) {
            entry.toBuf(buf);
        }
        buf.writeVarInt(printed.size());
        for (UUID id : printed) {
            buf.writeUUID(id);
        }
    }

    private static RepositoryListingPacket read(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<RepoEntry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entries.add(RepoEntry.fromBuf(buf));
        }
        int printedCount = buf.readVarInt();
        List<UUID> printed = new ArrayList<>(printedCount);
        for (int i = 0; i < printedCount; i++) {
            printed.add(buf.readUUID());
        }
        return new RepositoryListingPacket(entries, printed);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
