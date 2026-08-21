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
        if (cancelId.isPresent()) {
            host.queue().cancel(cancelId.get(), "cancelled by " + player.getGameProfile().getName());
            sync(player, menu, host, level);
            return;
        }

        Item item = itemId == null ? null : RegistryCompat.item(itemId);
        if (item == null || item == Items.AIR) {
            return;
        }
        int qty = Math.max(1, Math.min(quantity, MAX_ORDER_QUANTITY));

        // Re-decide eligibility here rather than believing the row the client drew. The catalog it
        // is looking at may be a tick stale, or may not have come from us at all.
        int bestTier = host.bestMachineTier(level);
        PrintEligibility.Result eligibility = PrintEligibility.of(new ItemStack(item), bestTier);
        if (!eligibility.printable()) {
            player.displayClientMessage(
                    Component.literal("Cannot print: " + eligibility.reason()), true);
            return;
        }
        if (host.queue().enqueue(UUID.randomUUID(), item, qty).isEmpty()) {
            player.displayClientMessage(Component.literal(
                    "The terminal's order book is full (" + PrintRequestQueue.MAX_OPEN_REQUESTS
                            + " open orders)"), true);
        }
        sync(player, menu, host, level);
    }

    /** Rebuilds this player's view and pushes it. Called on change, not every tick. */
    public static void sync(ServerPlayer player, MC3DPrintTerminalMenu menu,
                            TerminalHost host, ServerLevel level) {
        int bestTier = host.bestMachineTier(level);
        int[] fu = new int[MC3DPrintTerminalMenu.MAX_TIER];
        for (int t = 1; t <= MC3DPrintTerminalMenu.MAX_TIER; t++) {
            fu[t - 1] = host.fuAtTier(level, t);
        }
        List<CatalogEntry> catalog = TerminalCatalog.build(bestTier,
                tier -> tier >= 1 && tier <= fu.length ? fu[tier - 1] : 0);

        List<MC3DPrintTerminalMenu.OrderView> orders = new ArrayList<>();
        for (PrintRequest r : host.queue().all()) {
            orders.add(new MC3DPrintTerminalMenu.OrderView(
                    r.id(), r.item(), r.delivered(), r.quantity(), r.status(), r.reason()));
        }
        MC3DPrintNetwork.sendTo(player, new TerminalSyncPacket(
                catalog, orders, fu, bestTier, host.machines(level).size()));
    }
}
