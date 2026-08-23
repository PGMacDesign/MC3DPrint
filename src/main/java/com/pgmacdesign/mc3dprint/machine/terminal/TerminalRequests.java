package com.pgmacdesign.mc3dprint.machine.terminal;

import com.pgmacdesign.mc3dprint.compat.RegistryCompat;
import com.pgmacdesign.mc3dprint.network.MC3DPrintNetwork;
import com.pgmacdesign.mc3dprint.network.TerminalSyncPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-side handling of what a terminal's client asked for, and the sync that goes back.
 *
 * <p><b>Nothing from the client is trusted past "which item, how many".</b> The item is re-resolved
 * from the registry, eligibility is recomputed against the network's own best machine, and the
 * quantity is clamped. A modified client asking for a restricted trophy, a wind-only item, or
 * something above every machine it can reach gets refused here rather than discovering it later.
 */
public final class TerminalRequests {

    /** Ceiling on a single order. Large enough for a stack run, small enough to stay watchable. */
    public static final int MAX_ORDER_QUANTITY = 1024;

    private TerminalRequests() {}

    /** Handles a place-or-cancel from {@code player}'s open terminal. */
    public static void handle(ServerPlayer player, ResourceLocation itemId, int quantity,
                              Optional<UUID> cancelId) {
        if (!(player.containerMenu instanceof MC3DPrintTerminalMenu menu)) {
            return;
        }
        TerminalHost host = menu.host();
        if (host == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        // Re-check here as well as in stillValid. A menu can outlive its part by a tick, and the
        // packet is the thing that actually changes state, so this is the check that matters.
        if (!host.stillValidFor(player)) {
            player.closeContainer();
            return;
        }
        if (cancelId.isPresent()) {
            // Only resync when something actually changed. A cancel for an id the book has never
            // heard of is free to send and used to cost a full catalog rebuild plus ten grid
            // sweeps, which made this packet an amplification vector for anyone with the terminal
            // open.
            // Owner-scoped: sync hands every order id to every viewer, so an unscoped cancel lets
            // any player kill any other player's work with an ordinary packet.
            if (host.queue().cancelFor(cancelId.get(), player.getUUID(),
                    "cancelled by " + player.getName().getString())) {
                sync(player, menu, host, level);
            }
            return;
        }

        Item item = itemId == null ? null : RegistryCompat.item(itemId);
        if (item == null || item == Items.AIR) {
            return;
        }
        int qty = Math.max(1, Math.min(quantity, MAX_ORDER_QUANTITY));

        // Re-decide eligibility here rather than believing the row the client drew. The catalog it
        // is looking at may be a tick stale, or may not have come from us at all.
        int bestTier = host.snapshot(level).bestTier();
        PrintEligibility.Result eligibility = PrintEligibility.of(new ItemStack(item), bestTier);
        if (!eligibility.printable()) {
            com.pgmacdesign.mc3dprint.compat.MsgCompat.actionBar(player,
                    Component.literal("Cannot print: " + eligibility.reason()));
            return;
        }
        if (host.queue().enqueue(UUID.randomUUID(), item, qty, player.getUUID()).isEmpty()) {
            com.pgmacdesign.mc3dprint.compat.MsgCompat.actionBar(player, Component.literal(
                    "The terminal's order book is full (" + PrintRequestQueue.MAX_OPEN_REQUESTS
                            + " open orders)"));
        }
        sync(player, menu, host, level);
    }

    /**
     * A cheap fingerprint of everything a sync would show. Compared against the last one sent so a
     * per-tick push costs a grid walk and nothing else when nothing has moved, instead of rebuilding
     * and resending the whole item catalog sixty times a second to every viewer.
     */
    public static int stampOf(MachineSnapshot snapshot, PrintRequestQueue queue) {
        int stamp = snapshot.machineCount() * 31 + snapshot.bestTier();
        for (int fu : snapshot.fuByTier()) {
            stamp = stamp * 31 + fu;
        }
        for (PrintRequest r : queue.all()) {
            stamp = stamp * 31 + r.id().hashCode();
            stamp = stamp * 31 + r.delivered();
            stamp = stamp * 31 + r.status().ordinal();
            // The reason is drawn next to a held order, so it is part of what a viewer sees. Left
            // out, a hold that changes from "waiting for filament" to "waiting for power" would
            // keep showing the old text until some other field happened to move.
            stamp = stamp * 31 + (r.reason() == null ? 0 : r.reason().hashCode());
        }
        return stamp;
    }

    /**
     * Rebuilds this player's view and pushes it.
     *
     * <p>The machine list is resolved ONCE and reused. Asking the host for it per question meant
     * ten full grid sweeps per sync, each walking every node in six directions with a block-entity
     * lookup, plus a sort whose comparator did two more lookups per comparison. That is a lot of
     * work to repeat for a list that cannot change inside a single call.
     */
    public static void sync(ServerPlayer player, MC3DPrintTerminalMenu menu,
                            TerminalHost host, ServerLevel level) {
        MachineSnapshot snapshot = host.snapshot(level);
        int bestTier = snapshot.bestTier();
        int[] fu = snapshot.fuByTier();
        List<CatalogEntry> catalog = TerminalCatalog.build(bestTier,
                tier -> tier >= 1 && tier <= fu.length ? fu[tier - 1] : 0);

        List<MC3DPrintTerminalMenu.OrderView> orders = new ArrayList<>();
        for (PrintRequest r : host.queue().all()) {
            orders.add(new MC3DPrintTerminalMenu.OrderView(
                    r.id(), r.item(), r.delivered(), r.quantity(), r.status(), r.reason()));
        }
        MC3DPrintNetwork.sendTo(player, new TerminalSyncPacket(
                catalog, orders, fu, bestTier, snapshot.machineCount()));
    }
}
