package com.pgmacdesign.mc3dprint.fu;

/**
 * A drainable Filament-Unit reservoir exposed to neighbors as a Forge
 * capability. A {@code FilamentRack} exposes its shelved spools directly; an
 * {@code MC3DCable} exposes its whole connected network of racks. A printer
 * that exhausts its own docked spools mid-print pulls the remainder from
 * adjacent sources through this capability (direct-touch rack, or a cable).
 *
 * <p>Draining is expressed in <b>base units</b> (tier-1 FU) so the down-only
 * tier rule ({@link FuConversion#canCover}) and the cross-tier exchange math
 * stay identical to the printer's internal drain. An implementation must only
 * drain spools whose tier {@link FuConversion#canCover(int, int) canCover}s the
 * cost tier, and must honor the same ceil-rounding contract as the printer:
 * at most one high-tier unit of overshoot per drain call (see
 * {@link FilamentDrain#drain}).
 */
public interface IFilamentSource {

    /**
     * Drains up to {@code maxBase} base-FU worth of filament able to cover a
     * cost denominated at {@code costTier}, mutating the backing spools in
     * place. Returns the base-FU actually drained — which may slightly exceed
     * {@code maxBase} on the final spool due to whole-unit (ceil) rounding, and
     * is 0 when nothing qualified.
     */
    long drainFilament(long maxBase, int costTier);

    /**
     * Non-draining peek: the base-FU this source could currently supply toward a
     * cost at {@code costTier} (down-only — spools below the cost tier count for
     * nothing). Used by a printer's affordability check so it doesn't stall when
     * its own docked spools are empty but a connected rack has stock.
     */
    long availableFilament(int costTier);
}
