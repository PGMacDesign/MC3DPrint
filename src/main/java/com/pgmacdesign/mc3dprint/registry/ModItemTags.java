package com.pgmacdesign.mc3dprint.registry;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

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
 * <p><b>How to extend.</b> Add item ids (or {@code #tag} entries) to that JSON
 * file. No Java change is needed — the winding/conversion gates test membership
 * via {@code stack.is(WINDER_BLACKLIST)} at runtime, so the data tag is the
 * single source of truth. A blacklisted input still reports the existing
 * {@code STATUS_NOT_CONVERTIBLE} GUI status (it reads as "not convertible",
 * which is precisely what it is from the player's perspective), so no new lang
 * or screen work is required when the list grows.
 */
public final class ModItemTags {

    /**
     * Items that can still be printed (and still carry an FU value) but must
     * never be wound into filament — see the class javadoc for the rationale.
     * Backed by {@code data/mc3dprint/tags/items/winder_blacklist.json}.
     */
    public static final TagKey<Item> WINDER_BLACKLIST =
            TagKey.create(Registries.ITEM, new ResourceLocation(MC3DPrint.MOD_ID, "winder_blacklist"));

    private ModItemTags() {}
}
