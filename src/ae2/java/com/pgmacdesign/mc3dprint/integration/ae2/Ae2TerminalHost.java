package com.pgmacdesign.mc3dprint.integration.ae2;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import com.pgmacdesign.mc3dprint.machine.PrinterBlockEntity;
import com.pgmacdesign.mc3dprint.machine.terminal.MachineSnapshot;
import com.pgmacdesign.mc3dprint.machine.terminal.OrderSink;
import com.pgmacdesign.mc3dprint.machine.terminal.PrintRequestQueue;
import com.pgmacdesign.mc3dprint.machine.terminal.TerminalHost;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The AE2 side of a terminal: turns a grid into the machine list, filament view and output sink
 * that {@link TerminalHost} asks for.
 *
 * <p>This is the ONLY class that knows both AE2 and the order book, which is the point. Everything
 * on the other side of the seam is testable with AE2 absent, and an AE2 API break lands here rather
 * than in the code that decides who gets billed.
 */
final class Ae2TerminalHost implements TerminalHost {

    private final MC3DPrintTerminalPart part;
    private final PrintRequestQueue queue = new PrintRequestQueue();

    Ae2TerminalHost(MC3DPrintTerminalPart part) {
        this.part = part;
    }

    @Override
    public PrintRequestQueue queue() {
        return queue;
    }

    /**
     * MC3DPrint machines adjacent to any grid node on this network.
     *
     * <p>Adjacency rather than a grid capability, because a printer is not an AE2 machine and has
     * no grid node of its own: it is wired to the network the same way a furnace is, by sitting
     * next to something that is. Sorted best-tier-first so an order takes the largest machine that
     * is free rather than whichever happened to be found first.
     */
    @Override
    public List<BlockPos> machines(ServerLevel level) {
        IGrid grid = part.getMainNode().getGrid();
        if (grid == null) {
            return List.of();
        }
        // Set-backed rather than List.contains: the membership test runs once per grid node per
        // direction, so on a large network the linear scan was the dominant cost of a sync.
        java.util.Map<BlockPos, Integer> tiers = new java.util.LinkedHashMap<>();
        for (IGridNode node : grid.getNodes()) {
            BlockPos host = positionOf(node);
            if (host == null) {
                continue;
            }
            for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
                BlockPos at = host.relative(dir);
                if (tiers.containsKey(at) || !level.isLoaded(at)) {
                    continue;
                }
                if (level.getBlockEntity(at) instanceof PrinterBlockEntity printer) {
                    // Tier resolved once, here. The comparator used to look it up on both sides of
                    // every comparison, so sorting cost O(n log n) block-entity lookups on top.
                    tiers.put(at.immutable(), printer.tier().number());
                }
            }
        }
        List<BlockPos> found = new ArrayList<>(tiers.keySet());
        found.sort((a, b) -> Integer.compare(tiers.get(b), tiers.get(a)));
        return found;
    }

    /**
     * The part must still exist, be on a live grid, and be within reach. Vanilla's container reach
     * is 8 blocks; squared here to avoid the sqrt, and generous enough that standing at the cable
     * works while walking away does not.
     */
    @Override
    public boolean stillValidFor(net.minecraft.world.entity.player.Player player) {
        if (!part.getMainNode().isActive()) {
            return false;
        }
        net.minecraft.world.level.block.entity.BlockEntity be = part.getBlockEntity();
        if (be == null || be.isRemoved() || be.getLevel() != player.level()) {
            return false;
        }
        return be.getBlockPos().distToCenterSqr(player.position()) <= 64.0D;
    }

    @Override
    public OrderSink sink() {
        return part.sink();
    }

    /**
     * One walk of the grid answers everything a sync asks. Previously each question walked it
     * again: ten walks per sync, every node in six directions with a block-entity lookup, and a
     * sort doing two more per comparison. A client packet could trigger that without limit.
     */
    @Override
    public MachineSnapshot snapshot(ServerLevel level) {
        List<BlockPos> found = machines(level);
        if (found.isEmpty()) {
            return MachineSnapshot.empty(com.pgmacdesign.mc3dprint.machine.terminal
                    .MC3DPrintTerminalMenu.MAX_TIER);
        }
        int tiers = com.pgmacdesign.mc3dprint.machine.terminal.MC3DPrintTerminalMenu.MAX_TIER;
        int best = 0;
        long[] totals = new long[tiers];
        int[] perTier = new int[tiers];
        for (BlockPos pos : found) {
            PrinterBlockEntity printer = printerAt(level, pos);
            if (printer == null) {
                continue;
            }
            best = Math.max(best, printer.tier().number());
            // One pass for the whole rail rather than one call per tier, each of which re-walked
            // the cable network to gather its filament sources.
            printer.affordableFuByTier(perTier);
            for (int t = 0; t < tiers; t++) {
                totals[t] += perTier[t];
            }
        }
        int[] fu = new int[tiers];
        for (int i = 0; i < tiers; i++) {
            // Summed across machines, which overstates a network whose machines share racks over
            // cable: the same rack counts once per machine that can reach it. Display only; the
            // authoritative check is per machine at dispatch, so the worst case is an order that
            // binds and then holds, never one that prints unpaid.
            fu[i] = (int) Math.min(Integer.MAX_VALUE, totals[i]);
        }
        return new MachineSnapshot(found.size(), best, fu);
    }

    /**
     * Where a grid node physically is. {@code IGridNode} exposes no position of its own, only its
     * owner, so it comes from the owning block entity or, for a cable part, from the part host that
     * carries it.
     */
    @Nullable
    private static BlockPos positionOf(IGridNode node) {
        Object owner = node.getOwner();
        if (owner instanceof net.minecraft.world.level.block.entity.BlockEntity be) {
            return be.getBlockPos();
        }
        // AEBasePart tracks its own host; anything else implementing IPart is not ours to
        // introspect, so it simply does not contribute machines.
        if (owner instanceof appeng.parts.AEBasePart base) {
            net.minecraft.world.level.block.entity.BlockEntity host = base.getBlockEntity();
            return host == null ? null : host.getBlockPos();
        }
        return null;
    }


    @Nullable
    private static PrinterBlockEntity printerAt(ServerLevel level, BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockEntity(pos) instanceof PrinterBlockEntity p
                ? p : null;
    }
}
