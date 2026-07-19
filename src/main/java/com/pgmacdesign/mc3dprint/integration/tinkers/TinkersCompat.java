package com.pgmacdesign.mc3dprint.integration.tinkers;

import com.pgmacdesign.mc3dprint.fu.FuValueRegistry;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * Tinkers' Construct ({@code tconstruct}) FU values. Registered ONLY when TC is loaded;
 * entries are stored by {@link ResourceLocation}, so a vanilla-only install never sees
 * them — no crash, no warning spam, zero config / creative-tab footprint.
 *
 * <p><b>Why only ingots + cobalt leaves?</b> Every TC metal block (9 ingots) and nugget
 * (1/9 ingot) is registered through a normal {@code minecraft:crafting} recipe, so our
 * {@code RelaxationFuValuator} derives them automatically from the ingot — we only pin the
 * ingot. Cobalt is the exception: it is <i>mined</i> in the Nether, so we value the raw
 * item + ore block (the leaves) and let the ingot derive from vanilla smelting (pinned
 * here too for clarity). The alloys below come from the <b>Smeltery</b> (a custom alloy
 * recipe our valuator can't read), which is exactly why they need explicit values.
 *
 * <p><b>Tiering follows TC's own material progression, anchored to the vanilla map:</b>
 * <ul>
 *   <li><b>T3</b> — refined/farmable alloys roughly an iron-step up (iron=20@T2):
 *       steel, slimesteel, amethyst bronze, rose gold, pig iron, knight metals.</li>
 *   <li><b>T4</b> — Nether / blaze / soul tier (blaze_rod=40@T4): cobalt,
 *       queen's slime, cinderslime, soulsteel.</li>
 *   <li><b>T5</b> — top non-debris TC metal (diamond=50@T5): hepatizon
 *       (cobalt + amethyst-bronze alloy).</li>
 *   <li><b>T6</b> — netherite-adjacent: manyullyn alloys cobalt with
 *       <i>ancient-debris-derived</i> netherite scrap (scrap=125@T6), so it must gate
 *       behind netherite-tier filament — down-only printing then can't launder
 *       diamond-tier filament into a debris-bearing metal.</li>
 * </ul>
 *
 * <p><b>Anti-launder:</b> steel/cobalt are the cross-mod commons — keep these values
 * when EnderIO / IE / Mekanism land so the same material can't arbitrage across mods.
 * pig_iron is edible but needs Smeltery + blood (not farm-spammable) and winds back 1:1,
 * so it is intentionally NOT winder-blacklisted.
 *
 * <p>Intentionally UNVALUED (strict mode refuses them): {@code cheese_ingot} /
 * {@code fake_ingot} (joke / render-only), and the crafting-derived bones
 * ({@code necrotic_bone}, {@code blazing_bone}, etc.) which derive on their own.
 * {@code knightmetal} is valued speculatively — verify its obtain path in-game.
 */
public final class TinkersCompat {
    private static final String TC = "tconstruct";

    private TinkersCompat() {}

    public static void onCommonSetup(FMLCommonSetupEvent event) {
        if (!ModList.get().isLoaded(TC)) {
            return;
        }
        event.enqueueWork(() -> {
            // T3 — refined / farmable alloys (one step above iron; blocks & nuggets derive)
            register("steel_ingot", 25, 3);
            register("slimesteel_ingot", 30, 3);
            register("amethyst_bronze_ingot", 22, 3);
            register("rose_gold_ingot", 22, 3);
            register("pig_iron_ingot", 24, 3);   // edible, but not farm-spammable — left windable
            register("knightslime_ingot", 30, 3);
            register("knightmetal_ingot", 30, 3); // verify obtain path in-game

            // T4 — Nether-mined cobalt + blaze/soul-tier alloys
            register("cobalt_ore", 30, 4);        // worldgen block — value so it prints
            register("raw_cobalt", 30, 4);        // mined leaf; cobalt_ingot derives from smelting
            register("cobalt_ingot", 30, 4);      // pinned for clarity
            register("queens_slime_ingot", 45, 4);
            register("cinderslime_ingot", 45, 4);
            register("soulsteel_ingot", 45, 4);

            // T5 — top non-debris TC metal (cobalt + amethyst-bronze alloy)
            register("hepatizon_ingot", 70, 5);

            // T6 — carries ancient-debris-derived netherite scrap; gate at netherite tier
            register("manyullyn_ingot", 130, 6);
        });
    }

    private static void register(String path, int fu, int tier) {
        ResourceLocation id = ResourceLocation.tryParse(TC + ":" + path);
        if (id != null) {
            FuValueRegistry.registerApiItemValue(id, fu, tier);
        }
    }
}
