package com.pgmacdesign.mc3dprint.network;

import com.mojang.logging.LogUtils;
import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.machine.terminal.CatalogEntry;
import com.pgmacdesign.mc3dprint.machine.terminal.MC3DPrintTerminalMenu;
import com.pgmacdesign.mc3dprint.machine.terminal.PrintRequest;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server to client: what an open terminal should show. Catalog rows, orders in flight, filament per
 * tier, and the best machine on the network.
 *
 * <p>Everything here is advisory. The client never decides what may be ordered; it only draws what
 * it was told, and {@link TerminalOrderPacket} sends back an item and a count that the server
 * re-validates from scratch.
 */
public record TerminalSyncPacket(List<CatalogEntry> catalog,
                                 List<MC3DPrintTerminalMenu.OrderView> orders,
                                 int[] fuByTier, int bestMachineTier, int machineCount)
        implements CustomPacketPayload {

    public static final Type<TerminalSyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MC3DPrint.MOD_ID, "terminal_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalSyncPacket> STREAM_CODEC =
            StreamCodec.ofMember(TerminalSyncPacket::write, TerminalSyncPacket::read);

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Both sides bound to the SAME limit. A hostile or corrupt varint length would otherwise let a
     * reader pre-allocate an enormous list, and a genuinely oversized catalog would write a count
     * the reader then rejects with a DecoderException, which breaks the whole GUI rather than
     * truncating it. Modded packs push the item registry well past this, so truncation is a real
     * path and not a theoretical one.
     */
    private static final int MAX_CATALOG = 8192;
    private static final int MAX_ORDERS = 256;

    private void write(RegistryFriendlyByteBuf buf) {
        int rows = Math.min(catalog.size(), MAX_CATALOG);
        if (catalog.size() > MAX_CATALOG) {
            LOGGER.warn("Terminal catalog has {} rows; syncing only the first {}",
                    catalog.size(), MAX_CATALOG);
        }
        buf.writeVarInt(rows);
        for (int i = 0; i < rows; i++) {
            catalog.get(i).write(buf);
        }
        int orderCount = Math.min(orders.size(), MAX_ORDERS);
        buf.writeVarInt(orderCount);
        for (int i = 0; i < orderCount; i++) {
            MC3DPrintTerminalMenu.OrderView o = orders.get(i);
            buf.writeUUID(o.id());
            buf.writeVarInt(net.minecraft.core.registries.BuiltInRegistries.ITEM.getId(o.item()));
            buf.writeVarInt(o.delivered());
            buf.writeVarInt(o.quantity());
            buf.writeByte(o.status().ordinal());
            buf.writeUtf(o.reason() == null ? "" : o.reason(), 128);
        }
        buf.writeByte(fuByTier.length);
        for (int fu : fuByTier) {
            buf.writeVarInt(Math.max(0, fu));
        }
        buf.writeByte(bestMachineTier);
        buf.writeVarInt(machineCount);
    }

    private static TerminalSyncPacket read(RegistryFriendlyByteBuf buf) {
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
            // Clamp rather than index: the ordinal is wire data and a mismatched build would
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

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
