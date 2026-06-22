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

    public static RepositoryListingPacket decode(FriendlyByteBuf buf) {
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

    public static void handle(RepositoryListingPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.pgmacdesign.mc3dprint.client.ClientRepositoryHandler.apply(msg.entries, msg.printed)));
        context.setPacketHandled(true);
    }
}
