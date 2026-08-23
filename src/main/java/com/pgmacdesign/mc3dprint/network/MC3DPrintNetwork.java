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
        event.registrar(PROTOCOL)
                .playToClient(
                        RepositoryListingPacket.TYPE,
                        RepositoryListingPacket.STREAM_CODEC,
                        MC3DPrintNetwork::handleListing)
                .playToServer(
                        RepositoryRenamePacket.TYPE,
                        RepositoryRenamePacket.STREAM_CODEC,
                        MC3DPrintNetwork::handleRename)
                .playToClient(
                        TerminalSyncPacket.TYPE,
                        TerminalSyncPacket.STREAM_CODEC,
                        MC3DPrintNetwork::handleTerminalSync)
                .playToServer(
                        TerminalOrderPacket.TYPE,
                        TerminalOrderPacket.STREAM_CODEC,
                        MC3DPrintNetwork::handleTerminalOrder);
    }

    /**
     * Retitle a catalogued blueprint. Everything is re-checked here: the client is only
     * asking. The open-menu check is the authorization — it proves the player is standing at
     * a repository rather than replaying a packet from anywhere in the world.
     */
    private static void handleRename(RepositoryRenamePacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!(player.containerMenu
                    instanceof com.pgmacdesign.mc3dprint.machine.repository.BlueprintRepositoryMenu menu)) {
                return;
            }
            menu.renameFromClient(player, payload.id(), payload.name());
        });
    }

    /**
     * Place or cancel an order. Nothing the client sent is trusted beyond "which item, how many":
     * the item is re-resolved from the registry, eligibility and cost are recomputed against the
     * network's own best machine, and the quantity is clamped. Holding the terminal menu open is
     * the authorization, exactly as with the repository rename above: it proves the player is
     * actually at a terminal rather than replaying a packet from anywhere in the world.
     */
    private static void handleTerminalOrder(TerminalOrderPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!(player.containerMenu
                    instanceof com.pgmacdesign.mc3dprint.machine.terminal.MC3DPrintTerminalMenu)) {
                return;
            }
            com.pgmacdesign.mc3dprint.machine.terminal.TerminalRequests
                    .handle(player, payload.itemId(), payload.quantity(), payload.cancelId());
        });
    }

    // Runs client-side only (playToClient), so the client handler is loaded lazily on the
    // client and never reaches a dedicated server's classpath.
    private static void handleTerminalSync(TerminalSyncPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> com.pgmacdesign.mc3dprint.client.ClientTerminalHandler.apply(payload));
    }

    // Runs client-side only (playToClient), so ClientRepositoryHandler is loaded
    // lazily on the client and never reaches a dedicated server's classpath.
    private static void handleListing(RepositoryListingPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> com.pgmacdesign.mc3dprint.client.ClientRepositoryHandler.apply(
                payload.entries(), payload.printed()));
    }

    /**
     * Sends to a player only if their connection actually negotiated this payload. Without the
     * check, NeoForge throws "may not be sent to the client" and takes the whole server-side
     * action down with it — a GUI listing refresh is not worth that. It fires for any player
     * that isn't a real modded client on the other end: fake players from automation mods, and
     * the mock players gametests run actions as.
     */
    public static void sendTo(ServerPlayer player, CustomPacketPayload packet) {
        // isConnected() first: a fake player's listener has a Connection with a NULL channel,
        // and hasChannel dereferences it. Order matters, not just the pair of checks.
        if (!player.connection.getConnection().isConnected()
                || !player.connection.hasChannel(packet.type())) {
            return;
        }
        PacketDistributor.sendToPlayer(player, packet);
    }
}
