package com.pgmacdesign.mc3dprint.network;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;
import java.util.UUID;

/**
 * Client to server: place an order, or cancel one.
 *
 * <p><b>This carries an item id and a count, and nothing else.</b> No cost, no tier, no
 * eligibility, no "the client says this is affordable". Every one of those is recomputed
 * server-side before anything is queued, so a modified client can ask for a restricted trophy or a
 * tier-8 item on a tier-1 network and simply be refused. Sending a price the server then trusts is
 * the classic version of this mistake, and the reason the payload is this thin.
 *
 * @param itemId   what to print; a cancel carries the id anyway and it is ignored
 * @param quantity how many, clamped server-side
 * @param cancelId present when this is a cancellation rather than a new order
 */
public record TerminalOrderPacket(ResourceLocation itemId, int quantity, Optional<UUID> cancelId)
        implements CustomPacketPayload {

    public static final Type<TerminalOrderPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MC3DPrint.MOD_ID, "terminal_order"));

    /** Matches the queue's own ceiling on a single order, so the server never sees a silly number. */
    public static final int MAX_QUANTITY = 1024;

    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalOrderPacket> STREAM_CODEC =
            StreamCodec.ofMember(TerminalOrderPacket::write, TerminalOrderPacket::read);

    public static TerminalOrderPacket order(ResourceLocation itemId, int quantity) {
        return new TerminalOrderPacket(itemId, quantity, Optional.empty());
    }

    public static TerminalOrderPacket cancel(UUID id) {
        return new TerminalOrderPacket(
                ResourceLocation.withDefaultNamespace("air"), 0, Optional.of(id));
    }

    public boolean isCancel() {
        return cancelId.isPresent();
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeResourceLocation(itemId);
        buf.writeVarInt(quantity);
        buf.writeBoolean(cancelId.isPresent());
        cancelId.ifPresent(buf::writeUUID);
    }

    private static TerminalOrderPacket read(RegistryFriendlyByteBuf buf) {
        ResourceLocation id = buf.readResourceLocation();
        // Clamp on the way in as well as server-side. A hostile count cannot allocate anything
        // here, but keeping the wire value sane means the handler's own validation is the only
        // place that has to think about absurd numbers.
        int qty = Math.max(0, Math.min(buf.readVarInt(), MAX_QUANTITY));
        Optional<UUID> cancel = buf.readBoolean() ? Optional.of(buf.readUUID()) : Optional.empty();
        return new TerminalOrderPacket(id, qty, cancel);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
