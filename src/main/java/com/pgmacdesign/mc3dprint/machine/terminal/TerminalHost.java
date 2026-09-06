package com.pgmacdesign.mc3dprint.machine.terminal;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.List;

/**
 * Whatever is acting as a terminal: it owns an order book, knows which machines it can reach, and
 * knows where finished items go.
 *
 * <p>This is the seam that keeps AE2 out of the shared tree. The AE2 part implements it by asking
 * its grid; a gametest implements it with a fixed list and a counting sink. Nothing on this side of
 * the seam knows which it is talking to, which is why the dispatch path can be tested with AE2
 * absent and why an AE2 API break cannot reach the order book.
 */
public interface TerminalHost {

    /** The order book. The single writer for every request this terminal has taken. */
    PrintRequestQueue queue();

    /** Machines this terminal can dispatch to, best-first. Positions, not block entities. */
    List<BlockPos> machines(ServerLevel level);

    /** Where finished items go. */
    OrderSink sink();

    /**
     * The items the network actually holds, or {@code null} when this host has no notion of stock.
     *
     * <p>The terminal offers what the network already has rather than the whole item registry: it
     * reprints what you own, so the catalog is a mirror of your storage instead of a wish list of
     * everything the game can make. Null means "no restriction", which is what a test host with no
     * ME network behind it wants.
     *
     * <p>This does NOT relax any print rule. Being in stock gets an item listed; it still has to
     * pass every eligibility check to be orderable, so a blacklisted item sitting in a drive stays
     * exactly as unprintable as it was.
     */
    @javax.annotation.Nullable
    default java.util.Set<net.minecraft.world.item.Item> stockedItems() {
        return null;
    }

    /**
     * A cheap fingerprint of {@link #stockedItems()}, so a per-tick sync can notice stock moving
     * without walking the network's contents every time.
     */
    default int stockedStamp() {
        return 0;
    }

    /**
     * Whether the network holds {@code item} RIGHT NOW, asked without the cache.
     *
     * <p>{@link #stockedItems()} is cached for a second so a per-tick sync does not walk the
     * network's contents, which is fine for deciding what to DRAW. It is not fine for deciding
     * what may be ORDERED: a snapshot up to a second old would let the last copy of something be
     * taken out and the terminal still accept an order for it, and nothing downstream re-checks.
     */
    default boolean stocksNow(net.minecraft.world.item.Item item) {
        java.util.Set<net.minecraft.world.item.Item> stocked = stockedItems();
        return stocked == null || stocked.contains(item);
    }

    /**
     * Whether this terminal is still a thing {@code player} may act through: the part still exists,
     * its node is live, and they are close enough to reach it.
     *
     * <p>Asked by both the menu's stillValid and every state-changing packet. The menu alone is not
     * enough authorization, because a screen can stay open after the part is broken or after the
     * player has walked into another dimension.
     */
    boolean stillValidFor(net.minecraft.world.entity.player.Player player);

    // Note on the filament figures inside MachineSnapshot: spending is DOWN-only, so each tier's
    // number counts that tier and every tier above it. A Tier 3 spool pays a Tier 1 cost (lossily);
    // a Tier 1 spool never pays a Tier 3 one. That asymmetry is why the rail is per-tier at all,
    // since one grand total would be the number that lies.

    /**
     * Everything a sync needs, resolved in a single pass over the machines.
     *
     * <p>One call rather than one per question: the per-question shape cost ten grid walks per
     * sync, and a client packet could trigger those without limit.
     */
    MachineSnapshot snapshot(ServerLevel level);
}
