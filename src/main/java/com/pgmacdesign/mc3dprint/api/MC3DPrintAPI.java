package com.pgmacdesign.mc3dprint.api;

import com.pgmacdesign.mc3dprint.fu.FuValueRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * Stable, minimal public API for other mods to contribute Filament Unit values
 * and tiers to MC3DPrint <em>without</em> this mod hardcoding them.
 *
 * <p>There are two ways in, both documented in
 * {@code docs/FU-VALUES-AND-COMPAT.md}:
 *
 * <ol>
 *   <li><b>Direct call</b> (hard dependency on MC3DPrint): invoke the
 *       {@code registerFuValue}/{@code registerTagFuValue} methods below from
 *       your {@code FMLCommonSetupEvent} or {@code InterModEnqueueEvent}
 *       listener.</li>
 *   <li><b>IMC</b> (no hard dependency): send an {@link FuRegistration} payload
 *       under the method key {@link #IMC_REGISTER_FU_VALUE} via
 *       {@code InterModComms.sendTo(...)} — MC3DPrint consumes it during its own
 *       {@code InterModProcessEvent}.</li>
 * </ol>
 *
 * <p><b>Precedence.</b> When MC3DPrint values an item it consults, in order:
 * explicit config item entry, explicit config tag entry, API registrations made
 * here, then recipe-derived values. So a pack maker's config always wins over an
 * API registration, which in turn wins over derivation. Registering the same id
 * twice replaces the earlier value.
 *
 * <p>All registrations made here survive config reloads. They do <em>not</em>
 * persist across game restarts — re-register every launch from your setup event.
 */
public final class MC3DPrintAPI {

    /** Forge IMC method key carrying an {@link FuRegistration} supplier. */
    public static final String IMC_REGISTER_FU_VALUE = "register_fu_value";

    private MC3DPrintAPI() {}

    /**
     * Registers an FU value + tier for a single item by id. Safe to call before
     * the item exists in the registry; resolution happens lazily at lookup.
     *
     * @param itemId registry id of the item (e.g. {@code yourmod:ruby})
     * @param fu     Filament Unit value; clamped to &gt;= 1
     * @param tier   minimum machine/spool tier; clamped to 1..8
     */
    public static void registerFuValue(ResourceLocation itemId, int fu, int tier) {
        FuValueRegistry.registerApiItemValue(itemId, fu, tier);
    }

    /**
     * Registers an FU value + tier for a single {@link Item} instance.
     *
     * @param item the item
     * @param fu   Filament Unit value; clamped to &gt;= 1
     * @param tier minimum machine/spool tier; clamped to 1..8
     */
    public static void registerFuValue(Item item, int fu, int tier) {
        FuValueRegistry.registerApiItemValue(item, fu, tier);
    }

    /**
     * Registers an FU value + tier for every item in a tag. Direct item
     * registrations and config entries still take precedence over tag matches.
     *
     * @param tag  the item tag
     * @param fu   Filament Unit value; clamped to &gt;= 1
     * @param tier minimum machine/spool tier; clamped to 1..8
     */
    public static void registerTagFuValue(TagKey<Item> tag, int fu, int tier) {
        FuValueRegistry.registerApiTagValue(tag, fu, tier);
    }
}
