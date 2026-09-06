package com.pgmacdesign.mc3dprint.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Client to server: place an order, or cancel one.
 *
 * <p><b>This carries an item id and a count, and nothing else.</b> No cost, no tier, no
 * eligibility, no "the client says this is affordable". Every one of those is recomputed
 * server-side before anything is queued, so a modified client can ask for a restricted trophy or a
 * tier-8 item on a tier-1 network and simply be refused. Sending a price the server then trusts is
 * the classic version of this mistake, and the reason the payload is this thin.
 */
public record TerminalOrderPacket(ResourceLocation itemId, int quantity, Optional<UUID> cancelId) {

    /**
     * The queue's ceiling, referenced rather than repeated. These were two literals that had to
     * agree, and they stopped agreeing: the queue moved to 9999 while this stayed at 1024, so a
     * typed 9999 was clamped on the wire and the order silently became 1024.
     */
    public static final int MAX_QUANTITY =
            com.pgmacdesign.mc3dprint.machine.terminal.TerminalRequests.MAX_ORDER_QUANTITY;

    public static TerminalOrderPacket order(ResourceLocation itemId, int quantity) {
        return new TerminalOrderPacket(itemId, quantity, Optional.empty());
    }

    public static TerminalOrderPacket cancel(UUID id) {
        return new TerminalOrderPacket(new ResourceLocation("minecraft", "air"), 0, Optional.of(id));
    }

    public static void encode(TerminalOrderPacket msg, FriendlyByteBuf buf) {
        buf.writeResourceLocation(msg.itemId());
        buf.writeVarInt(msg.quantity());
        buf.writeBoolean(msg.cancelId().isPresent());
        msg.cancelId().ifPresent(buf::writeUUID);
    }

    public static TerminalOrderPacket decode(FriendlyByteBuf buf) {
        ResourceLocation id = buf.readResourceLocation();
        // Clamp on the way in as well as server-side, so the handler's validation is the only
        // place that has to think about absurd numbers.
        int qty = Math.max(0, Math.min(buf.readVarInt(), MAX_QUANTITY));
        Optional<UUID> cancel = buf.readBoolean() ? Optional.of(buf.readUUID()) : Optional.empty();
        return new TerminalOrderPacket(id, qty, cancel);
    }

    /**
     * Holding the terminal menu open is the authorization, the same rule the repository rename
     * uses: it proves the player is actually at a terminal rather than replaying a packet from
     * anywhere in the world.
     */
    public static void handle(TerminalOrderPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                return;
            }
            if (!(player.containerMenu
                    instanceof com.pgmacdesign.mc3dprint.machine.terminal.MC3DPrintTerminalMenu)) {
                return;
            }
            com.pgmacdesign.mc3dprint.machine.terminal.TerminalRequests
                    .handle(player, msg.itemId(), msg.quantity(), msg.cancelId());
        });
        ctx.get().setPacketHandled(true);
    }
}
