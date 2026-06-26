package com.pgmacdesign.mc3dprint.integration.immersiveengineering;

import com.pgmacdesign.mc3dprint.fu.FuValueRegistry;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * Immersive Engineering ({@code immersiveengineering}) FU values. Registered ONLY when IE is
 * loaded; stored by {@link ResourceLocation}, so a vanilla-only install never sees them.
 *
 * <p><b>Custom-recipe leaves:</b> steel (Blast Furnace), constantan + electrum (Arc Furnace /
 * Alloy Smelter), hop graphite (Arc Furnace coke dust). These don't derive — pin them.
 * {@code ingot_steel} reuses the cross-mod steel=T3 anchor (Tinkers' / Mekanism).
 *
 * <p><b>Mined metals</b> (aluminum / nickel / lead / silver / uranium): pin the raw + ore blocks;
 * {@code ingot_*} derives via {@code minecraft:smelting} of the raw. Note IE's verbatim spelling
 * is {@code raw_<metal>} / {@code ore_<metal>} / {@code deepslate_ore_<metal>} (NOT
 * {@code <metal>_ore}) — a wrong path silently registers nothing.
 *
 * <p><b>Anti-launder:</b> IE's Crusher ore-doubles and the Arc Furnace recycles, so every
 * {@code dust_*} / {@code slag*} / {@code grit_*} is left UNVALUED. {@code plate_*} (Metal Press,
 * 1:1) and {@code sheetmetal_*} / wires / components are skipped — intermediates, not materials.
 * IE aliases copper to vanilla ({@code minecraft:copper_ingot}), so IE copper needs nothing.
 */
public final class ImmersiveEngineeringCompat {
    private static final String IE = "immersiveengineering";

    private ImmersiveEngineeringCompat() {}

    public static void onCommonSetup(FMLCommonSetupEvent event) {
        if (!ModList.get().isLoaded(IE)) {
            return;
        }
        event.enqueueWork(() -> {
            // T2 — mined base metals (ingots derive via vanilla smelting of the raw)
            for (String m : new String[] {"aluminum", "nickel", "lead", "silver"}) {
                register("raw_" + m, 18, 2);
                register("ore_" + m, 18, 2);
                register("deepslate_ore_" + m, 18, 2);
            }
            // T3 — uranium (deeper, nuclear feedstock)
            register("raw_uranium", 25, 3);
            register("ore_uranium", 25, 3);
            register("deepslate_ore_uranium", 25, 3);
            // T3 — alloy/refined leaves (Blast Furnace / Arc Furnace; won't derive)
            register("ingot_steel", 25, 3);        // cross-mod anchor
            register("ingot_constantan", 20, 3);   // copper + nickel
            register("ingot_electrum", 20, 3);     // gold + silver
            register("ingot_hop_graphite", 25, 3); // Arc Furnace coke dust
        });
    }

    private static void register(String path, int fu, int tier) {
        ResourceLocation id = ResourceLocation.tryParse(IE + ":" + path);
        if (id != null) {
            FuValueRegistry.registerApiItemValue(id, fu, tier);
        }
    }
}
