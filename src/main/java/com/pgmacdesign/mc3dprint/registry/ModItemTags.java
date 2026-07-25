package com.pgmacdesign.mc3dprint.registry;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * Item {@link TagKey}s owned by MC3DPrint.
 *
 * <h2>Winder blacklist</h2>
 * {@link #WINDER_BLACKLIST} is the "can print, can't wind" list: items that may
 * still cost FU to <em>print</em>, but must never be <em>converted back</em>
 * into filament at the Filament Winder or the Filament Converter.
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
 * add an id prefix to {@link #WINDER_BLACKLIST_ID_PREFIXES}. The winding /
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
            TagKey.create(Registries.ITEM, new ResourceLocation(MC3DPrint.MOD_ID, "winder_blacklist"));

    /**
     * Item-id prefixes treated as winder-blacklisted, for modded families a vanilla
     * tag can't wildcard. {@code rftoolsdim:dimensional_} covers RFTools Dimensions'
     * seven-plus {@code dimensional_*} blocks — trivially farmable, so windable-proxy
     * laundering vectors like {@code rftoolsbase:dimensionalshard} (which is an exact
     * entry in the tag). Prefix-matched so new variants stay covered without a code change.
     */
    public static final List<String> WINDER_BLACKLIST_ID_PREFIXES = List.of(
            "rftoolsdim:dimensional_");

    /**
     * Whether {@code stack} must never be wound / converted / deconstructed back into
     * filament — true if it is in the {@link #WINDER_BLACKLIST} tag OR its registry id
     * starts with a {@link #WINDER_BLACKLIST_ID_PREFIXES} entry. Every anti-laundering
     * gate calls this, so the tag and the prefixes are honored uniformly.
     */
    public static boolean isWinderBlacklisted(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.is(WINDER_BLACKLIST)) {
            return true;
        }
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (key == null) {
            return false;
        }
        String id = key.toString();
        for (String prefix : WINDER_BLACKLIST_ID_PREFIXES) {
            if (id.startsWith(prefix)) {
                return true;
            }
        }
        return false;
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
            TagKey.create(Registries.ITEM, new ResourceLocation(MC3DPrint.MOD_ID, "print_restricted"));

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
     * {@code data/mc3dprint/tags/items/no_print.json}.
     */
    public static final TagKey<Item> NO_PRINT =
            TagKey.create(Registries.ITEM, new ResourceLocation(MC3DPrint.MOD_ID, "no_print"));

    private ModItemTags() {}
}
