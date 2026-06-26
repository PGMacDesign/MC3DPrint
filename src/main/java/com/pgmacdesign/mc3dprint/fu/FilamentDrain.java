package com.pgmacdesign.mc3dprint.fu;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/**
 * Shared spool-draining primitive used by both the printer's internal spools
 * and any {@link IFilamentSource} (rack/cable). Extracted so every drain site
 * obeys the same hard rules: down-only tier coverage and at-most-one-high-tier-
 * unit ceil overshoot. See {@link FuConversion} for the tier math.
 */
public final class FilamentDrain {

    /**
     * Drains {@code remainingBase} (already in base units) from spools of
     * <b>exactly</b> {@code exactTier} in {@code spools}, in slot order. When a
     * spool's unit is worth more than the remaining cost, a whole unit is
     * consumed (ceil). Returns the leftover base still owed — {@code <= 0} means
     * covered (a negative value is the single allowed ceil overshoot on the
     * covering spool). Callers sweep tiers from the cost tier upward so the
     * cheapest qualifying filament is always spent first.
     */
    public static long drainTier(IItemHandlerModifiable spools, long remainingBase, int exactTier, int ratio) {
        for (int i = 0; i < spools.getSlots() && remainingBase > 0; i++) {
            ItemStack spool = spools.getStackInSlot(i);
            if (!(spool.getItem() instanceof SpoolItem spoolItem) || spoolItem.tier() != exactTier) {
                continue;
            }
            long stored = SpoolItem.getFu(spool);
            long storedBase = FuConversion.toBase(stored, exactTier, ratio);
            long drainUnits = storedBase <= remainingBase
                    ? stored
                    : FuConversion.fromBaseCeil(remainingBase, exactTier, ratio);
            int drained = SpoolItem.drain(spool, FuConversion.clampToInt(drainUnits));
            remainingBase -= FuConversion.toBase(drained, exactTier, ratio);
            spools.setStackInSlot(i, spool); // onContentsChanged syncs the shrinking reel
        }
        return remainingBase;
    }

    /** Base-FU stored in spools of exactly {@code exactTier} (non-draining). */
    public static long availableTier(IItemHandlerModifiable spools, int exactTier, int ratio) {
        long base = 0;
        for (int i = 0; i < spools.getSlots(); i++) {
            if (spools.getStackInSlot(i).getItem() instanceof SpoolItem spool && spool.tier() == exactTier) {
                base += FuConversion.toBase(SpoolItem.getFu(spools.getStackInSlot(i)), exactTier, ratio);
            }
        }
        return base;
    }

    private FilamentDrain() {}
}
