package com.pgmacdesign.mc3dprint.machine.terminal;

import net.minecraft.world.item.ItemStack;

/**
 * Where a terminal order's output goes: ME storage, a player's inventory, or a machine's own output
 * slot when nothing better is reachable.
 *
 * <p>This exists so the executing machine never names AE2. The machine prints and hands the result
 * to a sink; whether that sink is an ME network is the terminal's business. It is also what makes
 * the whole dispatch path gametestable with AE2 absent, by handing it a sink that just counts.
 *
 * <p><b>Partial acceptance is the normal case, not an error.</b> A network fills up mid-order, so
 * the contract is "take what you can, tell me how much", and the caller credits only that. A sink
 * that accepted silently and dropped the remainder would turn a full ME system into an item
 * shredder, which is the failure this return value exists to prevent.
 */
public interface OrderSink {

    /**
     * Takes up to {@code stack.getCount()} items and returns how many were actually taken, from 0
     * to the full count. Must not mutate {@code stack}.
     *
     * <p>Anything not taken stays the caller's problem, and the caller must not treat it as
     * delivered.
     */
    int accept(ItemStack stack);

    /**
     * How many of {@code stack} would be accepted right now, taking nothing and changing nothing.
     *
     * <p>This exists so the machine can find out whether the item has somewhere to go BEFORE it
     * charges for it. Charging first and discovering afterwards that the network is full spends
     * filament and produces nothing, which is the one outcome the economy must never have. AE2's
     * own insert has a simulate mode, so this costs a real sink nothing to implement.
     *
     * <p>Default is optimistic, since a sink that cannot predict itself is better off trying.
     */
    default int simulate(ItemStack stack) {
        return stack.getCount();
    }

    /** A sink that never accepts anything, for a machine with nowhere to put its output. */
    OrderSink FULL = new OrderSink() {
        @Override
        public int accept(ItemStack stack) {
            return 0;
        }

        @Override
        public int simulate(ItemStack stack) {
            return 0;
        }
    };
}
