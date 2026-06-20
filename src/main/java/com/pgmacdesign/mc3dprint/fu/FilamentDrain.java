package com.pgmacdesign.mc3dprint.fu;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;

/**
 * Shared spool-draining primitive used by both the printer's internal spools
 * and any {@link IFilamentSource} (rack/cable). Extracted so every drain site
 * obeys the same hard rules: down-only tier coverage and at-most-one-high-tier-
 * unit ceil overshoot. See {@link FuConversion} for the tier math.
 */
public final class FilamentDrain {

    /**
     * Drains a cost (already converted to base units) across {@code spools} in
     * slot order, converting down at {@code ratio}. Spools below {@code costTier}
     * are skipped (FU never converts up); when a spool's unit is worth more than
     * the remaining cost, a whole unit is consumed (ceil). Returns the leftover
     * base still owed — {@code <= 0} means the cost was fully covered (a negative
     * value is the single allowed ceil overshoot on the last spool touched).
     */
    public static long drain(IItemHandlerModifiable spools, long remainingBase, int costTier, int ratio) {
        for (int i = 0; i < spools.getSlots() && remainingBase > 0; i++) {
            ItemStack spool = spools.getStackInSlot(i);
            if (!(spool.getItem() instanceof SpoolItem spoolItem)
                    || !FuConversion.canCover(spoolItem.tier(), costTier)) {
                continue;
            }
            long stored = SpoolItem.getFu(spool);
            long storedBase = FuConversion.toBase(stored, spoolItem.tier(), ratio);
            long drainUnits = storedBase <= remainingBase
                    ? stored
                    : FuConversion.fromBaseCeil(remainingBase, spoolItem.tier(), ratio);
            int drained = SpoolItem.drain(spool, FuConversion.clampToInt(drainUnits));
            remainingBase -= FuConversion.toBase(drained, spoolItem.tier(), ratio);
            spools.setStackInSlot(i, spool); // onContentsChanged syncs the shrinking reel
        }
        return remainingBase;
    }

    private FilamentDrain() {}
}
