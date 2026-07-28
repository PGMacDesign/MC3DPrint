package com.pgmacdesign.mc3dprint.compat;

import net.minecraft.world.item.ItemStack;

/**
 * Opt-in escape hatch for handlers whose {@code setStackInSlot} enforces a filter.
 *
 * <p>{@link net.neoforged.neoforge.items.IItemHandlerModifiable#setStackInSlot} is part of a
 * public capability surface: on the nodes below 1.21.9 the block entity's handler is registered
 * with the item capability <i>directly</i>, so any mod holding that capability can call it. A
 * handler that filters {@code insertItem} but not {@code setStackInSlot} therefore has an open
 * side door.
 *
 * <p>Filtering {@code setStackInSlot} closes that door but breaks transaction rollback, which
 * must restore a snapshot <b>exactly</b>, including stacks the filter would refuse today (an
 * item's FU value or blacklist status can move between reloads, and refusing to restore it
 * would void a player's items). This interface separates the two: {@code setStackInSlot} stays
 * filtered for outside callers, and {@link #restoreSlot} is the unfiltered path our own
 * snapshot journal uses. It is deliberately not part of any standard API, so a foreign mod has
 * no reason to reach for it.
 *
 * @see com.pgmacdesign.mc3dprint.machine.sorter.SorterBlockEntity
 */
public interface SnapshotRestorable {

    /**
     * Writes {@code stack} into {@code slot} bypassing any validity filter. Callers must only
     * use this to restore a snapshot previously read from this same handler.
     */
    void restoreSlot(int slot, ItemStack stack);
}
