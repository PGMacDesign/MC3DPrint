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

    /** The highest machine tier reachable, which is what the catalog is sized against. */
    int bestMachineTier(ServerLevel level);

    /**
     * Tier-unit FU that could pay a cost denominated at {@code tier}.
     *
     * <p>Spending is DOWN-only, so this includes filament at {@code tier} and every tier above it:
     * a Tier 3 spool pays a Tier 1 cost (lossily), while a Tier 1 spool can never pay a Tier 3 one.
     * That asymmetry is why the rail is per-tier at all. A single grand total would be the number
     * that lies, since most of it may sit below the tier the player is looking at.
     */
    int fuAtTier(ServerLevel level, int tier);
}
