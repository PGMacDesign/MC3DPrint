package com.pgmacdesign.mc3dprint.network;

import com.mojang.logging.LogUtils;
import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.blueprint.repository.RepoEntry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.slf4j.Logger;
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

    private static final Logger LOGGER = LogUtils.getLogger();

    // A repository holds far fewer builds than this; the cap stops a garbage/hostile varint
    // length from pre-allocating a huge list on the client, and it bounds the write side to
    // the SAME limit so a genuinely huge repository truncates (GUI shows the first N) instead
    // of writing a count the reader then rejects with a DecoderException (breaking the GUI).
    private static final int MAX_ENTRIES = 4096;

    private void write(RegistryFriendlyByteBuf buf) {
        int entryCount = Math.min(entries.size(), MAX_ENTRIES);
        if (entries.size() > MAX_ENTRIES) {
            LOGGER.warn("Repository listing has {} entries; syncing only the first {} to the GUI",
                    entries.size(), MAX_ENTRIES);
        }
        buf.writeVarInt(entryCount);
        for (int i = 0; i < entryCount; i++) {
            entries.get(i).toBuf(buf);
        }
        int printedCount = Math.min(printed.size(), MAX_ENTRIES);
        buf.writeVarInt(printedCount);
        for (int i = 0; i < printedCount; i++) {
            buf.writeUUID(printed.get(i));
        }
    }

    private static RepositoryListingPacket read(RegistryFriendlyByteBuf buf) {
        int count = readBounded(buf, "entries");
        List<RepoEntry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entries.add(RepoEntry.fromBuf(buf));
        }
        int printedCount = readBounded(buf, "printed");
        List<UUID> printed = new ArrayList<>(printedCount);
        for (int i = 0; i < printedCount; i++) {
            printed.add(buf.readUUID());
        }
        return new RepositoryListingPacket(entries, printed);
    }

    private static int readBounded(RegistryFriendlyByteBuf buf, String field) {
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_ENTRIES) {
            throw new io.netty.handler.codec.DecoderException(
                    "RepositoryListing " + field + " count out of range: " + count);
        }
        return count;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
