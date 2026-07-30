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
 * <p><b>Base draconium chain (dust → ingot → block + the four ores) = Tier 7, 40 FU.</b>
 * Draconium is a post-netherite mined material, so it fills the otherwise-empty modded T7 band
 * below vanilla nether_star. draconium_dust is the true mined leaf (every ore drops it without
 * silk touch); the standard-crafted DE items below the fusion tier (draconium_core, wyvern_core)
 * derive from this base chain through {@code RelaxationFuValuator}. The 40 is deliberately far
 * below nether_star's 1500 — see the abundance note at the pin site.
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
            // draconium_block (9x ingot) DERIVE to the same 40@7 / 360@7 through the valuator
            // (tier = max ingredient tier), so pinning them would only risk drift.
            //
            // 40, not the old 250. The tier is right (T7 gates DE progression) but the magnitude
            // was not: at 250 a single mined dust down-converted to 250 x ratio(4) = 1000 T6 FU,
            // and netherite_ingot costs 500@6 — so ONE draconium minted TWO netherite ingots, and
            // six of them minted a nether star. Draconium is quarry-able where netherite and
            // nether stars are not, which is exactly the abundance inversion the tier ladder is
            // supposed to prevent. At 40 it takes ~38 dust per nether star (1500/40) and ~3.1 per
            // netherite ingot, so draconium stays a useful T7 feedstock without undercutting the
            // things above it.
            //
            // The mirror of this is deliberate and worth knowing: values are symmetric, so making
            // draconium a weaker INPUT also makes it a cheaper OUTPUT. Printing draconium from a
            // nether-star-wound T7 spool now yields ~37 dust per star instead of ~6. That is
            // accepted: a wither farm feeding a T7 fabricator is a large, late setup, and base
            // draconium is deliberately NOT on #no_print (unlike awakened) so DE builds stay
            // printable. Revisit here first if bulk-printed draconium ever trivialises DE's
            // mid-game.
            register("draconium_dust", 40, 7);
            register("overworld_draconium_ore", 40, 7);
            register("deepslate_draconium_ore", 40, 7);
            register("nether_draconium_ore", 40, 7);
            register("end_draconium_ore", 40, 7);
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
