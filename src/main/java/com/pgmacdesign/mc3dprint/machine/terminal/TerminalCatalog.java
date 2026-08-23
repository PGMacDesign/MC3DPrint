package com.pgmacdesign.mc3dprint.machine.terminal;

import com.pgmacdesign.mc3dprint.fu.FuValue;
import com.pgmacdesign.mc3dprint.fu.FuValueRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntUnaryOperator;

/**
 * Builds the list of things a terminal can offer: every priced item, with its cost, its tier, and
 * why it cannot be ordered when it cannot.
 *
 * <p><b>Unprintable rows stay in the list.</b> Filtering them out would hide the economy from the
 * player: a greyed row reading "Tier 6" or "wind-only" is the only place most of these rules are
 * ever visible in game. It also means the catalog does not change shape as the player's filament
 * rises and falls, so the grid does not reshuffle under the cursor mid-click.
 *
 * <p><b>Built rarely, not per tick.</b> Sweeping the item registry is cheap per item but there are
 * thousands of them, and the result only moves when the machine tier or the filament on hand
 * changes. Callers build once per GUI open and refresh on a stamp.
 */
public final class TerminalCatalog {

    private TerminalCatalog() {}

    /**
     * Every item that carries an FU value, ordered cheapest first within a tier, tiers ascending.
     *
     * @param bestTier  the highest machine tier on the network; rows above it are marked
     *                  NEEDS_HIGHER_TIER rather than dropped
     * @param available given a tier, the tier-unit FU reachable at exactly that tier
     */
    public static List<CatalogEntry> build(int bestTier, IntUnaryOperator available) {
        List<CatalogEntry> out = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack stack = new ItemStack(item);
            if (stack.isEmpty()) {
                continue;
            }
            FuValue value = FuValueRegistry.valueOf(stack).orElse(null);
            if (value == null) {
                // Unpriced items are not shown at all. There are thousands of them, most from
                // mods that never intended to be printable, and a catalog that lists every one
                // with "no FU value" is noise rather than documentation. The greyed rows are for
                // things that ARE priced but cannot be ordered, which is the interesting case.
                continue;
            }
            PrintEligibility.Result eligibility = PrintEligibility.of(stack, bestTier);
            boolean affordable = eligibility.printable()
                    && available.applyAsInt(value.tier()) >= value.fu();
            out.add(new CatalogEntry(item, value.fu(), value.tier(),
                    eligibility.verdict(), affordable));
        }
        out.sort(Comparator.comparingInt(CatalogEntry::tier)
                .thenComparingInt(CatalogEntry::fuCost)
                .thenComparing(e -> BuiltInRegistries.ITEM.getKey(e.item()).toString()));
        return out;
    }

    /**
     * Filters a built catalog by a search string, matching the item's display name and its registry
     * id. Case-insensitive, substring, no prefixes: the id match is what lets a player find
     * something by modid when two mods ship an item with the same name.
     */
    public static List<CatalogEntry> search(List<CatalogEntry> all, String query) {
        String q = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        if (q.isEmpty()) {
            return all;
        }
        List<CatalogEntry> out = new ArrayList<>();
        for (CatalogEntry e : all) {
            String id = BuiltInRegistries.ITEM.getKey(e.item()).toString().toLowerCase(java.util.Locale.ROOT);
            String name = e.stack().getHoverName().getString().toLowerCase(java.util.Locale.ROOT);
            if (id.contains(q) || name.contains(q)) {
                out.add(e);
            }
        }
        return out;
    }
}
