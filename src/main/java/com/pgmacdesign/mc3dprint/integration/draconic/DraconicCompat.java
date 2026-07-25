package com.pgmacdesign.mc3dprint.integration.draconic;

import com.pgmacdesign.mc3dprint.fu.FuValueRegistry;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * Draconic Evolution FU values. Registered only when DE is loaded; the FU registry stores
 * these by {@link ResourceLocation}, so on a vanilla-only install the entries are simply
 * never matched (no crash, no warning spam — unlike putting modded ids in the config
 * default list).
 *
 * <p><b>Base draconium chain (dust → ingot → block + the four ores) = Tier 7.</b> Draconium
 * is a post-netherite mined material, so it fills the otherwise-empty modded T7 band below
 * vanilla nether_star. draconium_dust is the true mined leaf (every ore drops it without silk
 * touch); the standard-crafted DE items below the fusion tier (draconium_core, wyvern_core)
 * derive from this base chain through {@code RelaxationFuValuator}.
 *
 * <p><b>Awakened draconium = Tier 8, WIND-ONLY.</b> It is DE's Fusion-Crafting endgame and
 * the T8 fabricator's structural corner. It must never be printed (that would bypass Fusion
 * Crafting), so all four forms (ingot/block/dust/nugget) are on the {@code #no_print} tag;
 * valuing the ingot only makes it windable, a recycle payout into a T8 spool. DE's deeper
 * fusion gear (cores, chaos, energy components) stays unvalued — unreadable custom recipes,
 * so unprintable by design. If a value doesn't take, verify the path against the installed
 * DE build (the ore ids are {@code overworld_/deepslate_/nether_/end_draconium_ore}).
 */
public final class DraconicCompat {
    private static final String DE = "draconicevolution";

    private DraconicCompat() {}

    public static void onCommonSetup(FMLCommonSetupEvent event) {
        if (!ModList.get().isLoaded(DE)) {
            return;
        }
        event.enqueueWork(() -> {
            // Base draconium chain — Tier 7. Draconium is a post-netherite MINED material (not
            // AFK-farmable), so it sits at the boss/heavy-grind band alongside vanilla nether_star,
            // below it in effort. Pin only the LEAVES: draconium_dust (every ore drops it without
            // silk touch) and the four worldgen ore blocks. draconium_ingot (smelt dust) and
            // draconium_block (9x ingot) DERIVE to the same 250@7 / 2250@7 through the valuator
            // (tier = max ingredient tier), so pinning them would only risk drift. Kept at ~250 on
            // purpose: higher would let a mined block launder into multiple nether stars.
            register("draconium_dust", 250, 7);
            register("overworld_draconium_ore", 250, 7);
            register("deepslate_draconium_ore", 250, 7);
            register("nether_draconium_ore", 250, 7);
            register("end_draconium_ore", 250, 7);
            // Awakened Draconium — Tier 8, WIND-ONLY. DE's Fusion-Crafting endgame and the T8
            // fabricator's structural corner: it must NEVER print (that would bypass Fusion
            // Crafting), so all four forms sit on #no_print (data/.../tags/item/no_print.json).
            // Valuing the ingot makes it windable for a recycle payout (500@8 ~= 1.3 nether stars
            // of down-print); block/dust/nugget derive from it and are print-barred too.
            register("awakened_draconium_ingot", 500, 8);
        });
    }

    private static void register(String path, int fu, int tier) {
        ResourceLocation id = ResourceLocation.tryParse(DE + ":" + path);
        if (id != null) {
            FuValueRegistry.registerApiItemValue(id, fu, tier);
        }
    }
}
