package com.pgmacdesign.mc3dprint.integration.mekanism;

import com.pgmacdesign.mc3dprint.fu.FuValueRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * Mekanism ({@code mekanism}) FU values. Registered ONLY when Mekanism is loaded; entries
 * are stored by {@link ResourceLocation}, so a vanilla-only install never sees them — no
 * crash, no warning spam, zero config / creative-tab footprint.
 *
 * <p><b>Anti-launder is the whole story here.</b> Mekanism's identity is ore multiplication
 * (2x Enrichment → 3x Purification → 4x Injection → 5x Dissolution+Crystallizer). Every
 * {@code dust_*} / {@code clump_*} / {@code dirty_dust_*} / {@code crystal_*} / {@code shard_*}
 * is a multiplication intermediate, so they are ALL left UNVALUED on purpose — pricing one
 * would let a printed spool launder infinite multiplied material into the FU graph. Same for
 * the evaporation/brine mass-products ({@code salt}, {@code lithium}) and the bio
 * {@code hdpe_pellet}.
 *
 * <p><b>Why so few items?</b> Raw→ingot for the mined metals is plain {@code minecraft:smelting},
 * so {@code ingot_osmium/tin/lead/uranium} DERIVE from the raws we value (as do all
 * {@code block_*} ×9 and nuggets). We only pin: the mined leaves (raw + ore blocks) and the
 * custom-machine leaves — the Metallurgic Infuser alloys (bronze / steel / infused / reinforced
 * / atomic) and the Osmium Compressor refined metals — which our {@code RelaxationFuValuator}
 * cannot read.
 *
 * <p><b>Tiering</b> (anchored to vanilla: iron=20@T2, glowstone=20@T3, blaze=40@T4,
 * diamond=50@T5, netherite_scrap=125@T6):
 * <ul>
 *   <li><b>T2</b> — osmium/tin/lead are iron-abundance base metals; fluorite is a common
 *       deepslate gem. Osmium spawns at all Y levels, so it must stay low.</li>
 *   <li><b>T3</b> — uranium (deeper/nuclear); steel + bronze are cross-mod commons (reuse the
 *       steel=T3 anchor); infused alloy ≈ redstone+iron.</li>
 *   <li><b>T4</b> — refined glowstone (glowstone compressed with liquid osmium).</li>
 *   <li><b>T5</b> — refined obsidian (obsidian + liquid osmium) and reinforced alloy (carries
 *       diamond + refined obsidian).</li>
 *   <li><b>T6</b> — atomic alloy (top control circuitry, endgame).</li>
 *   <li><b>T8</b> — antimatter pellet, the SPS-only pinnacle (down-only printing means a T8
 *       spool can never print it up; it just gets a trophy tier).</li>
 * </ul>
 *
 * <p>Intentionally UNVALUED beyond the multiplication graph: nuclear reactor intermediates
 * ({@code pellet_plutonium}, {@code pellet_polonium}) — verify/defer; all machines, ducts,
 * tools, and the {@code mekanismgenerators/tools/additions} sub-mod items (they derive or are
 * out of scope).
 */
public final class MekanismCompat {
    private static final String MEK = "mekanism";

    private MekanismCompat() {}

    public static void onCommonSetup(FMLCommonSetupEvent event) {
        if (!ModList.get().isLoaded(MEK)) {
            return;
        }
        event.enqueueWork(() -> {
            // T2 — abundant mined base metals (ingots derive via vanilla smelting of the raw)
            for (String m : new String[] {"osmium", "tin", "lead"}) {
                register("raw_" + m, 18, 2);
                register(m + "_ore", 18, 2);
                register("deepslate_" + m + "_ore", 18, 2);
            }
            // T2 — fluorite (common deepslate gem; drops fluorite_gem)
            register("fluorite_gem", 10, 2);
            register("fluorite_ore", 10, 2);
            register("deepslate_fluorite_ore", 10, 2);

            // T3 — uranium (deeper, nuclear feedstock)
            register("raw_uranium", 25, 3);
            register("uranium_ore", 25, 3);
            register("deepslate_uranium_ore", 25, 3);
            // T3 — Metallurgic Infuser commons (cross-mod anchors: keep steel=T3 everywhere)
            register("ingot_steel", 25, 3);
            register("ingot_bronze", 22, 3);
            register("alloy_infused", 12, 3);     // iron + redstone

            // T4 — refined glowstone (Osmium Compressor)
            register("ingot_refined_glowstone", 35, 4);

            // T5 — refined obsidian + reinforced alloy (carries diamond + refined obsidian)
            register("ingot_refined_obsidian", 60, 5);
            register("alloy_reinforced", 70, 5);

            // T6 — atomic alloy (top control circuitry)
            register("alloy_atomic", 130, 6);

            // T8 — antimatter pellet, the SPS-only pinnacle (trophy tier)
            register("pellet_antimatter", 600, 8);
        });
    }

    private static void register(String path, int fu, int tier) {
        ResourceLocation id = ResourceLocation.tryParse(MEK + ":" + path);
        if (id != null) {
            FuValueRegistry.registerApiItemValue(id, fu, tier);
        }
    }
}
