package com.pgmacdesign.mc3dprint.machine.terminal;

/**
 * Everything a sync needs to know about the machines on a network, resolved in one pass.
 *
 * <p>This type exists to make the single pass the easy thing to write. Asking the host for the
 * best tier, then for filament at each of eight tiers, then for a machine count, read naturally
 * and cost ten separate walks of the grid, each doing a block-entity lookup per node per
 * direction. Bundling the answers means the expensive part happens once and cannot quietly
 * un-bundle itself later.
 *
 * @param machineCount how many MC3DPrint machines the network can reach
 * @param bestTier     the highest machine tier reachable, 0 when there are none
 * @param fuByTier     tier-unit FU able to pay a cost at each tier, indexed from tier 1 at [0]
 */
public record MachineSnapshot(int machineCount, int bestTier, int[] fuByTier) {

    public static MachineSnapshot empty(int tiers) {
        return new MachineSnapshot(0, 0, new int[tiers]);
    }
}
