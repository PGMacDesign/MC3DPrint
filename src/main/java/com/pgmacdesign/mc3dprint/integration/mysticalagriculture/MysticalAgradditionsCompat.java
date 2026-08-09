package com.pgmacdesign.mc3dprint.integration.mysticalagriculture;

import com.pgmacdesign.mc3dprint.fu.FuValueRegistry;
import com.pgmacdesign.mc3dprint.registry.ModItemTags;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * Mystical Agradditions ({@code mysticalagradditions}) FU values, plus the two guards its
 * tier-6 crops make necessary. Registered only when the mod is loaded.
 *
 * <p><b>The guards are the point of this class; the ore values are incidental.</b> Agradditions
 * adds crops for nether star and dragon egg (27 essences into three shards or chunks, into one
 * item). Both are priced in {@link FuValueRegistry} as trophies that cannot be farmed, and the
 * dragon egg's 10,000 FU is justified there in as many words by it dropping exactly once per
 * world and never again. A crop that yields them makes a tier-7 spool renewable, and a tier-7
 * spool prints everything at tier 7 and below, which is the entire game.
 *
 * <p>The fix is to bar both from the winder here rather than in the data tag, because the tag
 * is unconditional and would punish every pack that does not run Agradditions. Nothing else
 * changes: both stay craftable and usable for their real purposes (beacons, a trophy block),
 * and the nether star stays printable, which is safe because printing carries a markup and the
 * only routes to tier-7 filament are the two items now barred plus draconium.
 *
 * <p><b>Insanium is deliberately unvalued and barred both ways.</b> Its recipe is four
 * supremium essences plus an infusion crystal, all of which are priced, so the valuator would
 * otherwise reach it and hand it roughly 1,100 FU at tier 5, a number nobody chose, on an item
 * that is farm output. Winding is barred by the {@code mysticalagradditions:insanium_} prefix
 * in {@link ModItemTags#WINDER_BLACKLIST_ID_PREFIXES}; printing is barred by the
 * {@code no_print} tag. Anchoring it would have been one line instead, but an unpriced
 * insanium keeps the mod's own tier-6 progression the only way to reach it.
 */
public final class MysticalAgradditionsCompat {
    private static final String MAA = "mysticalagradditions";

    private MysticalAgradditionsCompat() {}

    public static void onCommonSetup(FMLCommonSetupEvent event) {
        if (!ModList.get().isLoaded(MAA)) {
            return;
        }
        event.enqueueWork(() -> {
            // Dimension variants of the two Mystical Agriculture ores; they drop the same
            // essence and shard, so they carry the same values as the overworld pair.
            register("nether_inferium_ore", 20, 1);
            register("end_inferium_ore", 20, 1);
            register("nether_prosperity_ore", 4, 2);
            register("end_prosperity_ore", 4, 2);

            ModItemTags.blockWinding(ResourceLocation.withDefaultNamespace("nether_star"));
            ModItemTags.blockWinding(ResourceLocation.withDefaultNamespace("dragon_egg"));
        });
    }

    private static void register(String path, int fu, int tier) {
        ResourceLocation id = ResourceLocation.tryParse(MAA + ":" + path);
        if (id != null) {
            FuValueRegistry.registerApiItemValue(id, fu, tier);
        }
    }
}
