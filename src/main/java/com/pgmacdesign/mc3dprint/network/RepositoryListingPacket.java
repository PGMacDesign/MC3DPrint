package com.pgmacdesign.mc3dprint.network;

import com.mojang.logging.LogUtils;
import com.pgmacdesign.mc3dprint.blueprint.repository.RepoEntry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server -> client: the catalogue the viewing player should see in an open
 * repository GUI, plus the subset of (official) builds they've printed — kept
 * separate from the entries since a build can be printed without being catalogued.
 */
public record RepositoryListingPacket(List<RepoEntry> entries, List<UUID> printed) {

    private static final Logger LOGGER = LogUtils.getLogger();

    // A repository holds far fewer builds than this; the cap stops a garbage/hostile varint
    // length from pre-allocating a huge list on the client, and it bounds the write side to
    // the SAME limit so a genuinely huge repository truncates (GUI shows the first N) instead
    // of writing a count the reader then rejects with a DecoderException (breaking the GUI).
    private static final int MAX_ENTRIES = 4096;

    public static void encode(RepositoryListingPacket msg, FriendlyByteBuf buf) {
        int entryCount = Math.min(msg.entries.size(), MAX_ENTRIES);
        if (msg.entries.size() > MAX_ENTRIES) {
            LOGGER.warn("Repository listing has {} entries; syncing only the first {} to the GUI",
                    msg.entries.size(), MAX_ENTRIES);
        }
        buf.writeVarInt(entryCount);
        for (int i = 0; i < entryCount; i++) {
            msg.entries.get(i).toBuf(buf);
        }
        int printedCount = Math.min(msg.printed.size(), MAX_ENTRIES);
        buf.writeVarInt(printedCount);
        for (int i = 0; i < printedCount; i++) {
            buf.writeUUID(msg.printed.get(i));
        }
    }

    public static RepositoryListingPacket decode(FriendlyByteBuf buf) {
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

    private static int readBounded(FriendlyByteBuf buf, String field) {
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_ENTRIES) {
            throw new io.netty.handler.codec.DecoderException(
                    "RepositoryListing " + field + " count out of range: " + count);
        }
        return count;
    }

    public static void handle(RepositoryListingPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.pgmacdesign.mc3dprint.client.ClientRepositoryHandler.apply(msg.entries, msg.printed)));
        context.setPacketHandled(true);
    }
}
