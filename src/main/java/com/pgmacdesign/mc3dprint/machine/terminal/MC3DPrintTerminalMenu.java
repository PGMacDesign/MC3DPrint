package com.pgmacdesign.mc3dprint.machine.terminal;

import com.pgmacdesign.mc3dprint.fu.SpoolItem;
import com.pgmacdesign.mc3dprint.registry.ModMenuTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The MC3DPrint Terminal's menu: a catalog to order from, the orders in flight, and the filament
 * the network can actually pay with.
 *
 * <p><b>No slots.</b> Ordering moves nothing by hand, so there is nothing to click items into. The
 * player's inventory is not shown either: the output goes to ME storage or to them directly, and a
 * grid of slots would only invite the assumption that this is a crafting bench.
 *
 * <p><b>The client view is advisory, always.</b> Everything here is a mirror the server pushed, and
 * the ordering packet carries an item id and a count and nothing else. Cost, tier, eligibility and
 * affordability are all recomputed server-side at dispatch, so a client that lies about what it can
 * see gains nothing. That is the reason this class holds no authority of its own.
 */
public class MC3DPrintTerminalMenu extends AbstractContainerMenu {

    /** Rows visible at once in the catalog grid. */
    public static final int VISIBLE_ROWS = 6;
    /** Entries per row. */
    public static final int COLUMNS = 9;
    public static final int PAGE_SIZE = VISIBLE_ROWS * COLUMNS;

    /** Highest tier the network can pay at all, used to size the tier rail. */
    public static final int MAX_TIER = SpoolItem.CAPACITY_BY_TIER.length;

    // --- client mirror, pushed by the server ---
    private List<CatalogEntry> catalog = new ArrayList<>();
    private List<OrderView> orders = new ArrayList<>();
    private final int[] fuByTier = new int[MAX_TIER];
    private int bestMachineTier;
    private int machineCount;

    // --- client-only view state ---
    private String search = "";
    private int scrollRow;
    /** Memoized {@link #visibleCatalog()}; null means "recompute". */
    @Nullable
    private List<CatalogEntry> filtered;

    /**
     * Server side only: whatever opened this menu and owns the order book. Null on the client,
     * where there is nothing to own and nothing to authorize.
     */
    @Nullable
    private TerminalHost host;

    /**
     * A queued order as the client sees it. Deliberately not {@link PrintRequest}: that class is
     * server state with mutators, and shipping it to the client would invite someone to write to a
     * copy and expect it to mean something.
     */
    public record OrderView(UUID id, net.minecraft.world.item.Item item, int delivered,
                            int quantity, PrintRequest.Status status, String reason) {
        public int remaining() {
            return Math.max(0, quantity - delivered);
        }
    }

    public MC3DPrintTerminalMenu(int windowId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(windowId, playerInventory);
    }

    public MC3DPrintTerminalMenu(int windowId, Inventory playerInventory) {
        // Cast is required, not stylistic: a bare null is ambiguous between the FriendlyByteBuf
        // and TerminalHost overloads.
        this(windowId, playerInventory, (TerminalHost) null);
    }

    public MC3DPrintTerminalMenu(int windowId, Inventory playerInventory, @Nullable TerminalHost host) {
        super(ModMenuTypes.MC3DPRINT_TERMINAL.get(), windowId);
        this.host = host;
    }

    @Nullable
    public TerminalHost host() {
        return host;
    }

    // --- server -> client sync ---

    public void acceptSync(List<CatalogEntry> catalog, List<OrderView> orders,
                           int[] fuByTier, int bestMachineTier, int machineCount) {
        this.catalog = catalog;
        this.filtered = null; // a fresh catalog invalidates the filtered view built from the old one
        this.orders = byProgress(orders);
        System.arraycopy(fuByTier, 0, this.fuByTier, 0, Math.min(fuByTier.length, MAX_TIER));
        this.bestMachineTier = bestMachineTier;
        this.machineCount = machineCount;
        clampScroll();
    }

    public List<CatalogEntry> catalog() {
        return catalog;
    }

    /**
     * The catalog after the current search, which is what the grid actually draws.
     *
     * <p>Cached, because the screen asks for this several times per frame and the filter walks
     * every row lowercasing two strings. At a modded pack's catalog size that is real work to be
     * doing sixty times a second for a list that only changes when the player types.
     */
    public List<CatalogEntry> visibleCatalog() {
        if (filtered == null) {
            filtered = TerminalCatalog.search(catalog, search);
        }
        return filtered;
    }

    public List<OrderView> orders() {
        return orders;
    }

    /** Exposed for tests: the display order the sync applies. */
    public static List<OrderView> orderedForDisplay(List<OrderView> incoming) {
        return byProgress(incoming);
    }

    private static List<OrderView> byProgress(List<OrderView> incoming) {
        List<OrderView> live = new ArrayList<>();
        List<OrderView> waiting = new ArrayList<>();
        List<OrderView> finished = new ArrayList<>();
        for (OrderView o : incoming) {
            switch (o.status()) {
                case RUNNING, HELD -> live.add(o);
                case QUEUED -> waiting.add(o);
                case COMPLETE, CANCELLED -> finished.add(o);
            }
        }
        java.util.Collections.reverse(finished);
        live.addAll(waiting);
        live.addAll(finished);
        return live;
    }

    /** Tier-unit FU reachable at exactly {@code tier} (1-based), for the tier rail. */
    public int fuAtTier(int tier) {
        return tier >= 1 && tier <= MAX_TIER ? fuByTier[tier - 1] : 0;
    }

    public int bestMachineTier() {
        return bestMachineTier;
    }

    public int machineCount() {
        return machineCount;
    }

    // --- client view state ---

    public String search() {
        return search;
    }

    public void setSearch(String search) {
        this.search = search == null ? "" : search;
        this.filtered = null;
        this.scrollRow = 0; // a new query starts at the top, or the view jumps somewhere arbitrary
    }

    public int scrollRow() {
        return scrollRow;
    }

    public void setScrollRow(int row) {
        this.scrollRow = row;
        clampScroll();
    }

    public int maxScrollRow() {
        int rows = (visibleCatalog().size() + COLUMNS - 1) / COLUMNS;
        return Math.max(0, rows - VISIBLE_ROWS);
    }

    private void clampScroll() {
        scrollRow = Math.max(0, Math.min(scrollRow, maxScrollRow()));
    }

    /** The entry at a grid cell, or null past the end of the filtered list. */
    @Nullable
    public CatalogEntry entryAt(int cell) {
        List<CatalogEntry> shown = visibleCatalog();
        int index = scrollRow * COLUMNS + cell;
        return index >= 0 && index < shown.size() ? shown.get(index) : null;
    }

    // --- container contract ---

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // No slots, so nothing can be shift-clicked. Returning EMPTY is the contract for
        // "nothing moved"; the default implementation would index a slot list that is empty.
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        // The host knows where it is and whether it still exists; the menu has no position of its
        // own. Returning a flat true left the screen usable after the part was broken and from any
        // distance, which matters because cancelling does not pass through the eligibility checks
        // that happen to refuse a stale order.
        //
        // Client-side there is no host, and vanilla calls this on both sides, so a null host means
        // "not our business to judge" rather than "invalid".
        return host == null || host.stillValidFor(player);
    }
}
