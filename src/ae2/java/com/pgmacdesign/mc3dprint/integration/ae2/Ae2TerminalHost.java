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
     * MC3DPrint machines on this network.
     *
     * <p>A machine now owns a grid node of its own (see {@link Ae2MachineNodes}), so it is simply
     * a member of the grid and this is one flat pass over the node list. It used to be a scan of
     * all six neighbours of every node on the network, because a printer had no node and could
     * only be found by sitting next to something that did.
     *
     * <p>Sorted best-tier-first so an order takes the largest free machine rather than whichever
     * was found first.
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
            BlockPos at = Ae2MachineNodes.machinePos(node);
            if (at == null || tiers.containsKey(at) || !level.isLoaded(at)) {
                continue;
            }
            if (level.getBlockEntity(at) instanceof PrinterBlockEntity printer) {
                // Tier resolved once, here. The comparator used to look it up on both sides of
                // every comparison, so sorting cost O(n log n) block-entity lookups on top.
                tiers.put(at, printer.tier().number());
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

    // --- network stock -------------------------------------------------------------------

    /**
     * Recompute interval for the stocked set, in ticks. Walking a large network's contents is not
     * free and the catalog is rebuilt from a fingerprint anyway, so a second of staleness buys a
     * bounded cost per tick instead of an unbounded one.
     */
    private static final long STOCK_TTL_TICKS = 20L;

    @Nullable
    private java.util.Set<net.minecraft.world.item.Item> stockedCache;
    private int stockedFingerprint;
    private long stockedAtTick = Long.MIN_VALUE;

    @Override
    @Nullable
    public java.util.Set<net.minecraft.world.item.Item> stockedItems() {
        refreshStock();
        return stockedCache;
    }

    @Override
    public int stockedStamp() {
        refreshStock();
        return stockedFingerprint;
    }

    private void refreshStock() {
        if (!(part.getLevel() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        if (stockedCache != null && now - stockedAtTick < STOCK_TTL_TICKS) {
            return;
        }
        stockedAtTick = now;
        IGrid grid = part.getMainNode().getGrid();
        if (grid == null || !part.getMainNode().isActive()) {
            // Offline: hold an EMPTY set rather than null. Null means "no restriction", and a
            // terminal with no channel must not answer that by offering the whole item registry.
            stockedCache = java.util.Set.of();
            stockedFingerprint = 0;
            return;
        }
        appeng.api.networking.storage.IStorageService service =
                grid.getService(appeng.api.networking.storage.IStorageService.class);
        if (service == null) {
            stockedCache = java.util.Set.of();
            stockedFingerprint = 0;
            return;
        }
        java.util.Set<net.minecraft.world.item.Item> items = new java.util.HashSet<>();
        int fingerprint = 0;
        for (appeng.api.stacks.AEKey key : service.getInventory().getAvailableStacks().keySet()) {
            if (key instanceof appeng.api.stacks.AEItemKey itemKey
                    && items.add(itemKey.getItem())) {
                // Order-independent on purpose: the set has no order and the walk need not be
                // stable between ticks.
                fingerprint += itemKey.getItem().hashCode();
            }
        }
        stockedCache = items;
        stockedFingerprint = fingerprint * 31 + items.size();
    }

    /**
     * Asks the network directly whether it still holds {@code item}, bypassing the stock cache.
     *
     * <p>A simulated extract of one is an exact, targeted question: it neither walks the whole
     * inventory nor trusts a snapshot that may be up to a second stale. The cached set decides what
     * the catalog DRAWS; this decides what may be ORDERED, and only this is authoritative.
     */
    @Override
    public boolean stocksNow(net.minecraft.world.item.Item item) {
        if (!(part.getLevel() instanceof ServerLevel)) {
            return false;
        }
        IGrid grid = part.getMainNode().getGrid();
        if (grid == null || !part.getMainNode().isActive()) {
            return false;
        }
        appeng.api.networking.storage.IStorageService service =
                grid.getService(appeng.api.networking.storage.IStorageService.class);
        if (service == null) {
            return false;
        }
        appeng.api.stacks.AEItemKey key =
                appeng.api.stacks.AEItemKey.of(new net.minecraft.world.item.ItemStack(item));
        if (key == null) {
            return false;
        }
        return service.getInventory().extract(key, 1L, appeng.api.config.Actionable.SIMULATE,
                appeng.api.networking.security.IActionSource.ofMachine(part)) > 0L;
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

    @Nullable
    private static PrinterBlockEntity printerAt(ServerLevel level, BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockEntity(pos) instanceof PrinterBlockEntity p
                ? p : null;
    }
}
