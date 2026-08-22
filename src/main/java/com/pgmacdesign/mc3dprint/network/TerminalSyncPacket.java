package com.pgmacdesign.mc3dprint.network;

import com.mojang.logging.LogUtils;
import com.pgmacdesign.mc3dprint.machine.terminal.CatalogEntry;
import com.pgmacdesign.mc3dprint.machine.terminal.MC3DPrintTerminalMenu;
import com.pgmacdesign.mc3dprint.machine.terminal.PrintRequest;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server to client: what an open terminal should show. Catalog rows, orders in flight, filament per
 * tier, and the best machine on the network.
 *
 * <p>Everything here is advisory. The client never decides what may be ordered; it only draws what
 * it was told, and {@link TerminalOrderPacket} sends back an item and a count the server
 * re-validates from scratch.
 */
public record TerminalSyncPacket(List<CatalogEntry> catalog,
                                 List<MC3DPrintTerminalMenu.OrderView> orders,
                                 int[] fuByTier, int bestMachineTier, int machineCount) {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Both sides bound to the SAME limit. A hostile or corrupt varint length would otherwise let a
     * reader pre-allocate an enormous list, and a genuinely oversized catalog would write a count
     * the reader then rejects, which breaks the whole GUI rather than truncating it. Modded packs
     * push the item registry well past this, so truncation is a real path, not a theoretical one.
     */
    private static final int MAX_CATALOG = 8192;
    private static final int MAX_ORDERS = 256;

    public static void encode(TerminalSyncPacket msg, FriendlyByteBuf buf) {
        int rows = Math.min(msg.catalog().size(), MAX_CATALOG);
        if (msg.catalog().size() > MAX_CATALOG) {
            LOGGER.warn("Terminal catalog has {} rows; syncing only the first {}",
                    msg.catalog().size(), MAX_CATALOG);
        }
        buf.writeVarInt(rows);
        for (int i = 0; i < rows; i++) {
            msg.catalog().get(i).write(buf);
        }
        int orderCount = Math.min(msg.orders().size(), MAX_ORDERS);
        buf.writeVarInt(orderCount);
        for (int i = 0; i < orderCount; i++) {
            MC3DPrintTerminalMenu.OrderView o = msg.orders().get(i);
            buf.writeUUID(o.id());
            buf.writeVarInt(net.minecraft.core.registries.BuiltInRegistries.ITEM.getId(o.item()));
            buf.writeVarInt(o.delivered());
            buf.writeVarInt(o.quantity());
            buf.writeByte(o.status().ordinal());
            buf.writeUtf(o.reason() == null ? "" : o.reason(), 128);
        }
        buf.writeByte(msg.fuByTier().length);
        for (int fu : msg.fuByTier()) {
            buf.writeVarInt(Math.max(0, fu));
        }
        buf.writeByte(msg.bestMachineTier());
        buf.writeVarInt(msg.machineCount());
    }

    public static TerminalSyncPacket decode(FriendlyByteBuf buf) {
        int rows = Math.min(buf.readVarInt(), MAX_CATALOG);
        List<CatalogEntry> catalog = new ArrayList<>(Math.min(rows, 1024));
        for (int i = 0; i < rows; i++) {
            catalog.add(CatalogEntry.read(buf));
        }
        int orderCount = Math.min(buf.readVarInt(), MAX_ORDERS);
        List<MC3DPrintTerminalMenu.OrderView> orders = new ArrayList<>(Math.min(orderCount, 256));
        PrintRequest.Status[] statuses = PrintRequest.Status.values();
        for (int i = 0; i < orderCount; i++) {
            UUID id = buf.readUUID();
            var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.byId(buf.readVarInt());
            int delivered = buf.readVarInt();
            int quantity = buf.readVarInt();
            int ordinal = buf.readByte();
            // Clamp rather than index: the ordinal is wire data, and a mismatched build would
            // otherwise throw inside the decoder and take the GUI down with it.
            PrintRequest.Status status = ordinal >= 0 && ordinal < statuses.length
                    ? statuses[ordinal]
                    : PrintRequest.Status.QUEUED;
            String reason = buf.readUtf(128);
            orders.add(new MC3DPrintTerminalMenu.OrderView(id, item, delivered, quantity, status,
                    reason.isEmpty() ? null : reason));
        }
        int tiers = Math.min(buf.readByte(), MC3DPrintTerminalMenu.MAX_TIER);
        int[] fu = new int[MC3DPrintTerminalMenu.MAX_TIER];
        for (int i = 0; i < tiers; i++) {
            fu[i] = buf.readVarInt();
        }
        return new TerminalSyncPacket(catalog, orders, fu, buf.readByte(), buf.readVarInt());
    }

    /** Client side only, so the client handler never reaches a dedicated server's classpath. */
    public static void handle(TerminalSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                com.pgmacdesign.mc3dprint.client.ClientTerminalHandler.apply(msg));
        ctx.get().setPacketHandled(true);
    }
}
