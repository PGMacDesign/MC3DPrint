package com.pgmacdesign.mc3dprint.registry;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Item {@link TagKey}s owned by MC3DPrint.
 *
 * <h2>Winder blacklist</h2>
 * {@link #WINDER_BLACKLIST} is the "can print, can't wind" list: items that may
 * still cost FU to <em>print</em>, but must never be <em>converted back</em>
 * into filament at the Filament Winder.
 *
 * <p><b>Why this exists (the stick-laundering exploit).</b> Many cheap items
 * have a recipe-derived FU value that is <em>greater in aggregate</em> than the
 * source they were crafted from, because the derivation divides the source FU
 * across the craft outputs and rounds each output up to a floor of 1 FU. The
 * canonical case is sticks: one oak log is worth 3 FU, but it crafts into
 * planks and then into 8 sticks, each of which derives to 1 FU. Feeding those
 * 8 sticks back through the winder yields 8 FU from a 3 FU log — free filament,
 * minted out of a rounding seam. Wooden buttons, pressure plates, and similar
 * 2-input / many-output micro-crafts have the same shape. Blacklisting the
 * output item closes the loop at the winder without disturbing its (correct)
 * printable FU cost or its recipe-derivation value.
 *
 * <p><b>Scope right now.</b> The backing data tag
 * ({@code data/mc3dprint/tags/items/winder_blacklist.json}) ships with only
 * {@code minecraft:stick}. That is intentional — this change lands the
 * framework and documentation; the full set of launder-prone items is added
 * during the tier/economy rebalance.
 *
 * <p><b>How to extend.</b> For exact items add ids (or {@code #tag} entries) to
 * that JSON file. For a modded <em>family</em> a vanilla tag can't wildcard (e.g.
 * RFTools Dimensions' {@code rftoolsdim:dimensional_*} blocks — seven-plus
 * trivially-farmable variants, laundering vectors like the dimensional shard),
 * add an id prefix to {@link #WINDER_BLACKLIST_ID_PREFIXES}. Planting items need
 * neither: they are matched by block type, so a mod's seeds are covered before
 * anyone has heard of the mod. The winding /
 * conversion / deconstruct gates all test membership through
 * {@link #isWinderBlacklisted(ItemStack)} — call that, never a bare
 * {@code stack.is(WINDER_BLACKLIST)}, so both the tag and the prefixes apply
 * uniformly. A blacklisted input still reports the existing
 * {@code STATUS_NOT_CONVERTIBLE} GUI status, so no new lang or screen work is
 * required when the list grows.
 */
public final class ModItemTags {

    /**
     * Items that can still be printed (and still carry an FU value) but must
     * never be wound into filament — see the class javadoc for the rationale.
     * Backed by {@code data/mc3dprint/tags/items/winder_blacklist.json}.
     */
    public static final TagKey<Item> WINDER_BLACKLIST =
            TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(MC3DPrint.MOD_ID, "winder_blacklist"));

    /**
     * Item-id prefixes treated as winder-blacklisted, for modded families a vanilla
     * tag can't wildcard. {@code rftoolsdim:dimensional_} covers RFTools Dimensions'
     * seven-plus {@code dimensional_*} blocks — trivially farmable, so windable-proxy
     * laundering vectors like {@code rftoolsbase:dimensionalshard} (which is an exact
     * entry in the tag). {@code mysticalagradditions:insanium_} covers all eleven insanium
     * forms in one entry: insanium carries a derived value only because it sits four
     * essences above supremium in a plain crafting recipe, and winding it would let a farm
     * reach tier 5. Prefix-matched so new variants stay covered without a code change, and
     * inert when the mod is absent, since nothing can match the namespace.
     */
    public static final List<String> WINDER_BLACKLIST_ID_PREFIXES = List.of(
            "rftoolsdim:dimensional_",
            "mysticalagradditions:insanium_");

    /**
     * Ids barred from winding at runtime rather than by data, because whether they should be
     * barred depends on which OTHER mods are installed and a datapack tag cannot express that.
     * Written by compat hooks during common setup, read on the server thread thereafter.
     *
     * <p>The case this exists for: Mystical Agradditions turns nether star and dragon egg into
     * crops. Both are priced here as unfarmable trophies (the dragon egg's 10,000 FU is
     * justified in {@code FuValueRegistry} by it dropping exactly once per world), so a crop
     * that yields them turns a tier-7 spool into a faucet. They cannot go in the data tag,
     * because that would punish every pack that does not run Agradditions.
     */
    private static final Set<String> RUNTIME_WINDER_BLOCKED = ConcurrentHashMap.newKeySet();

    /**
     * Bars {@code itemId} from ever being wound, converted or deconstructed into filament.
     * Idempotent, and intended to be called from a mod-gated compat hook during common setup.
     */
    public static void blockWinding(ResourceLocation itemId) {
        RUNTIME_WINDER_BLOCKED.add(itemId.toString());
    }

    /** Drops every runtime bar. Test support; nothing in the mod un-bars an item at runtime. */
    public static void clearRuntimeWinderBlocks() {
        RUNTIME_WINDER_BLOCKED.clear();
    }

    /**
     * The id-only half of the winder gate: the runtime bars plus the prefix list. Split out
     * from {@link #isWinderBlacklisted(ItemStack)} so it can be driven from JUnit without
     * bootstrapping the item registry.
     */
    public static boolean isIdWinderBlocked(ResourceLocation itemId) {
        String id = itemId.toString();
        if (RUNTIME_WINDER_BLOCKED.contains(id)) {
            return true;
        }
        for (String prefix : WINDER_BLACKLIST_ID_PREFIXES) {
            if (id.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether {@code stack} must never be wound / converted / deconstructed back into
     * filament: true if it is in the {@link #WINDER_BLACKLIST} tag, or its registry id is
     * barred by {@link #isIdWinderBlocked(ResourceLocation)} (a prefix match or a runtime
     * bar). Every anti-laundering gate calls this, so all three sources apply uniformly.
     */
    public static boolean isWinderBlacklisted(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.is(WINDER_BLACKLIST)) {
            return true;
        }
        if (isPlantingItem(stack)) {
            return true;
        }
        return isIdWinderBlocked(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    /**
     * Whether {@code stack} is the item you plant to grow something: a block item whose block is
     * a {@link net.minecraft.world.level.block.BushBlock}. Seeds, saplings, flowers, nether wart
     * and every modded equivalent land here, because a crop block registers its planting item
     * against itself ({@code wheat -> wheat_seeds}) and every crop in the game descends from
     * {@code BushBlock}.
     *
     * <p>This is a rule rather than a list on purpose. The tag names vanilla's planting items, but
     * FU values reach modded items too, through the config's {@code fuValues} overrides, through
     * recipe derivation, and through the compat API. A valued modded seed with no tag entry would
     * otherwise be windable, and a seed is the one thing a farm produces without limit, so that is
     * a renewable filament faucet: the exact laundering this class exists to stop. Enumerating
     * every mod's seeds cannot work, since the next mod is not installed yet.
     *
     * <p>Printing is unaffected. A planted block still costs its planting item, and this only bars
     * the return trip.
     */
    private static boolean isPlantingItem(ItemStack stack) {
        return stack.getItem() instanceof net.minecraft.world.item.BlockItem blockItem
                && blockItem.getBlock() instanceof net.minecraft.world.level.block.BushBlock;
    }

    /**
     * Trophy-class items a printer must NOT reproduce freely even when they carry
     * an FU value (needed to price official builds that contain them). Printable
     * ONLY when the loaded disc is an OFFICIAL curated blueprint whose
     * {@code CuratedBlueprints.restrictedAllowance} lists the item — the same
     * anti-exploit shape as the resin official-only gate. Item mode refuses them
     * outright (that path would be straight duplication).
     * Backed by {@code data/mc3dprint/tags/item/print_restricted.json}.
     */
    public static final TagKey<Item> PRINT_RESTRICTED =
            TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(MC3DPrint.MOD_ID, "print_restricted"));

    /**
     * "Can wind, can't print" — the mirror of {@link #WINDER_BLACKLIST}. These items carry
     * an FU value (so they wind into filament for a recycle payout) but the printer must never
     * reproduce them in any mode. Distinct from {@link #PRINT_RESTRICTED}: a restricted trophy
     * still prints from an official curated disc, whereas a NO_PRINT item never prints at all,
     * so this gate takes precedence when an item is on both (e.g. wither_skeleton_skull).
     *
     * <p>Two uses: treasure/uncraftable items we want recyclable but not duplicable (saddle,
     * name_tag), and launder-risk items kept unprintable even though they must be valued to wind
     * (wither_skeleton_skull — a wither-skeleton farm is AFK-automatable, so it is valued LOW,
     * capped at T4 not its T7 rarity, and barred from printing here). Backed by
     * {@code data/mc3dprint/tags/item/no_print.json}.
     */
    public static final TagKey<Item> NO_PRINT =
            TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(MC3DPrint.MOD_ID, "no_print"));

    /**
     * "Scaffolding rule" — captured by a scan so the build reads correctly in the blueprint, but
     * invisible to the print in every way: it never places, never costs FU, and never counts
     * toward the blueprint's tier.
     *
     * <p>Scaffolding is the case this exists for. It is how you reach the far corners of a build
     * to scan it, so it lands in almost every hand scan by accident, and it is not part of the
     * build. Charging for it inflates the quote, and letting it set the tier can push an
     * otherwise Tier 1 build up to the tier of the scaffolding itself, demanding a machine the
     * build never needed.
     *
     * <p>Distinct from {@link #NO_PRINT}, which is about items that must never be reproduced
     * (they still cost and still count). Backed by
     * {@code data/mc3dprint/tags/item/print_ignored.json}.
     */
    public static final TagKey<Item> PRINT_IGNORED =
            TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(MC3DPrint.MOD_ID, "print_ignored"));

    private ModItemTags() {}
}
