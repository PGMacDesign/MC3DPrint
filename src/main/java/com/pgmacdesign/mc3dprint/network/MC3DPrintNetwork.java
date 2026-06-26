package com.pgmacdesign.mc3dprint.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The mod's single payload channel. Currently carries one S2C message: the
 * Blueprint Repository listing (which can't ride a {@code ContainerData} since
 * it's variable-length text, and is per-player in personal mode).
 */
public final class MC3DPrintNetwork {
    private static final String PROTOCOL = "1";

    private MC3DPrintNetwork() {}

    /** Mod-bus listener (wired in the {@code @Mod} constructor). */
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL).playToClient(
                RepositoryListingPacket.TYPE,
                RepositoryListingPacket.STREAM_CODEC,
                MC3DPrintNetwork::handleListing);
    }

    // Runs client-side only (playToClient), so ClientRepositoryHandler is loaded
    // lazily on the client and never reaches a dedicated server's classpath.
    private static void handleListing(RepositoryListingPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> com.pgmacdesign.mc3dprint.client.ClientRepositoryHandler.apply(
                payload.entries(), payload.printed()));
    }

    public static void sendTo(ServerPlayer player, CustomPacketPayload packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }
}
