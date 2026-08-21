package com.pgmacdesign.mc3dprint.integration.ae2;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import com.pgmacdesign.mc3dprint.machine.PrinterBlockEntity;
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
        List<BlockPos> found = new ArrayList<>();
        for (IGridNode node : grid.getNodes()) {
            BlockPos host = positionOf(node);
            if (host == null) {
                continue;
            }
            for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
                BlockPos at = host.relative(dir);
                if (found.contains(at) || !level.isLoaded(at)) {
                    continue;
                }
                if (level.getBlockEntity(at) instanceof PrinterBlockEntity) {
                    found.add(at);
                }
            }
        }
        found.sort((a, b) -> Integer.compare(tierAt(level, b), tierAt(level, a)));
        return found;
    }

    @Override
    public OrderSink sink() {
        return part.sink();
    }

    @Override
    public int bestMachineTier(ServerLevel level) {
        int best = 0;
        for (BlockPos pos : machines(level)) {
            best = Math.max(best, tierAt(level, pos));
        }
        return best;
    }

    /**
     * Filament across the network that could pay a cost at {@code tier}. Down-only spending means
     * this counts that tier and everything above it, which is exactly what
     * {@code affordableFu(costTier)} already computes for a single machine.
     *
     * <p>Summed across machines, which slightly overstates a network whose machines share racks
     * over cable: the same rack is counted once per machine that can reach it. It is a display
     * figure only, and the authoritative check happens per machine at dispatch, so the worst case
     * is an order that binds and then holds rather than one that prints unpaid.
     */
    @Override
    public int fuAtTier(ServerLevel level, int tier) {
        long total = 0;
        for (BlockPos pos : machines(level)) {
            PrinterBlockEntity printer = printerAt(level, pos);
            if (printer != null) {
                total += printer.affordableFu(tier);
            }
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
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
        if (owner instanceof appeng.api.parts.IPart part) {
            appeng.api.parts.IPartItem<?> ignored = part.getPartItem();
            net.minecraft.world.level.block.entity.BlockEntity host = hostOf(part);
            return host == null ? null : host.getBlockPos();
        }
        return null;
    }

    @Nullable
    private static net.minecraft.world.level.block.entity.BlockEntity hostOf(
            appeng.api.parts.IPart part) {
        // AEBasePart tracks its host; anything else implementing IPart is not ours to introspect.
        return part instanceof appeng.parts.AEBasePart base ? base.getBlockEntity() : null;
    }

    private static int tierAt(ServerLevel level, BlockPos pos) {
        PrinterBlockEntity printer = printerAt(level, pos);
        return printer == null ? 0 : printer.tier().number();
    }

    @Nullable
    private static PrinterBlockEntity printerAt(ServerLevel level, BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockEntity(pos) instanceof PrinterBlockEntity p
                ? p : null;
    }
}
