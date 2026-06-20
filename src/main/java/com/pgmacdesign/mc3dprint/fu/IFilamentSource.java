package com.pgmacdesign.mc3dprint.fu;

import java.util.Set;

/**
 * A drainable Filament-Unit reservoir exposed to neighbors as a Forge
 * capability. A {@code FilamentRack} exposes its shelved spools directly; an
 * {@code MC3DCable} exposes its whole connected network of racks.
 *
 * <p><b>Exact-tier contract.</b> All amounts are <b>base units</b> (tier-1 FU),
 * and every operation is scoped to spools of <b>exactly one tier</b>. This is
 * what lets a printer drain in a globally tier-smart order: it sweeps tier bands
 * from the cost tier upward, draining every reachable spool at the cheapest
 * qualifying tier first, so a high-tier spool is never wasted on a low-tier
 * block just because of where it was docked. Whole-unit (ceil) rounding still
 * applies on the spool that finally covers a cost — at most one unit of
 * overshoot per drain. See {@link FilamentDrain}.
 */
public interface IFilamentSource {

    /**
     * Drains up to {@code maxBase} base-FU from spools of <b>exactly</b>
     * {@code tier}, mutating them in place. Returns the base-FU actually drained
     * (may slightly exceed {@code maxBase} on the covering spool due to ceil
     * rounding), or 0 if this source holds none of that tier.
     */
    long drainExactTier(int tier, long maxBase);

    /** Non-draining peek: base-FU available from spools of exactly {@code tier}. */
    long availableExactTier(int tier);

    /**
     * Adds the concrete leaf sources reachable through this one to {@code out}
     * (identity-deduped by the caller). A rack adds itself; a cable adds the
     * racks across its network. The caller then sweeps tier bands across the
     * flattened set so draining is globally tier-smart and never double-counts a
     * rack reachable by more than one path.
     */
    default void collectSources(Set<IFilamentSource> out) {
        out.add(this);
    }
}
