package com.pgmacdesign.mc3dprint.integration.enderio;

import com.pgmacdesign.mc3dprint.fu.FuValueRegistry;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * EnderIO ({@code enderio}) FU values. Registered ONLY when EnderIO is loaded; stored by
 * {@link ResourceLocation}, so a vanilla-only install never sees them.
 *
 * <p><b>All nine alloys are Alloy-Smelter leaves</b> ({@code enderio:alloy_smelting} — a custom
 * recipe type the valuator can't read), so each ingot is pinned; their blocks (×9) and nuggets
 * (÷9) derive. The Alloy Smelter is 1:1 alloying (no multiplication), so they're safe to value —
 * tiered purely by input rarity.
 *
 * <p><b>1.20 rename trap:</b> EnderIO 1.20 renamed every alloy to {@code <name>_alloy_ingot}
 * (the legacy {@code conductive_iron} / {@code pulsating_iron} / {@code electrical_steel} ids are
 * GONE). The exact spellings below are load-bearing — a wrong path silently registers nothing.
 *
 * <p><b>Anti-launder:</b> {@code enderio:silicon} and all SAG-Mill ({@code sag_milling}) powders
 * are ore-doubling outputs → left UNVALUED. {@code grains_of_infinity} (a fire-ritual leaf) is
 * deferred — see {@code docs/rebalance/enderio.md}.
 */
public final class EnderIOCompat {
    private static final String EIO = "enderio";

    private EnderIOCompat() {}

    public static void onCommonSetup(FMLCommonSetupEvent event) {
        if (!ModList.get().isLoaded(EIO)) {
            return;
        }
        event.enqueueWork(() -> {
            // T3 — cheap alloys (redstone / base-metal / iron band)
            register("redstone_alloy_ingot", 8, 3);
            register("copper_alloy_ingot", 12, 3);
            register("conductive_alloy_ingot", 18, 3);   // was "conductive iron"
            // T4 — ender / Nether / obsidian-gated
            register("pulsating_alloy_ingot", 30, 4);    // iron + ender pearl
            register("energetic_alloy_ingot", 35, 4);    // gold + redstone + glowstone
            register("soularium_ingot", 30, 4);          // gold + soul sand
            register("dark_steel_ingot", 40, 4);         // iron + coal + obsidian
            // T5 — carries energetic alloy + ender
            register("vibrant_alloy_ingot", 55, 5);
            // T6 — End-gated capstone (dark steel + end stone + obsidian)
            register("end_steel_ingot", 90, 6);
        });
    }

    private static void register(String path, int fu, int tier) {
        ResourceLocation id = ResourceLocation.tryParse(EIO + ":" + path);
        if (id != null) {
            FuValueRegistry.registerApiItemValue(id, fu, tier);
        }
    }
}
