package com.pgmacdesign.mc3dprint.network;

import com.pgmacdesign.mc3dprint.blueprint.repository.RepoEntry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

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

    public static void encode(RepositoryListingPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entries.size());
        for (RepoEntry entry : msg.entries) {
            entry.toBuf(buf);
        }
        buf.writeVarInt(msg.printed.size());
        for (UUID id : msg.printed) {
            buf.writeUUID(id);
        }
    }

    // A repository holds far fewer builds than this; the cap just stops a garbage/hostile
    // varint length from pre-allocating a huge list on the client.
    private static final int MAX_ENTRIES = 4096;

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
