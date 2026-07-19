package com.pgmacdesign.mc3dprint.integration.ae2;

import com.pgmacdesign.mc3dprint.fu.FuValueRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * Applied Energistics 2 FU values. Registered ONLY when AE2 is loaded; entries are
 * stored by {@link ResourceLocation}, so a vanilla-only install never sees them — no
 * crash, no warning spam, and zero config / creative-tab footprint.
 *
 * <p>Only the custom-recipe LEAVES need explicit values: AE2's ME network, cables,
 * storage cells, autocrafting, spatial and quantum trees are plain vanilla crafting and
 * derive automatically. The leaves below come from the Inscriber / Charger / in-world
 * transforms our {@code RelaxationFuValuator} can't read.
 *
 * <p><b>Abundance cap:</b> certus quartz auto-farms in 1.20.1 (budding certus + growth
 * accelerator), so the certus → charged → fluix chain is pinned low (T2–T3, ~amethyst).
 * Letting it drift higher would let a farmer launder infinite certus into rarer goods.
 * The engineering processor carries a diamond input, so the controller / drive / spatial /
 * quantum bridge gate themselves to T5 by derivation.
 *
 * <p>Intentionally UNVALUED (strict mode refuses them): processor presses (meteorite
 * loot), budding-quartz blocks (infinite-certus generators), spatial storage cells
 * (dimension-bound NBT), singularities (dupe-prone), attuned P2P tunnels.
 */
public final class Ae2Compat {
    private static final String AE2 = "ae2";

    private Ae2Compat() {}

    public static void onCommonSetup(FMLCommonSetupEvent event) {
        if (!ModList.get().isLoaded(AE2)) {
            return;
        }
        event.enqueueWork(() -> {
            // T2 — abundance-capped to amethyst (certus is auto-farmable)
            register("certus_quartz_crystal", 10, 2);
            register("charged_certus_quartz_crystal", 12, 2);
            register("certus_quartz_dust", 8, 2);
            register("silicon", 10, 2);
            register("sky_stone_block", 8, 2);
            register("smooth_sky_stone_block", 8, 2);
            // T3 — network backbone + early processors (Inscriber)
            register("fluix_crystal", 15, 3);
            register("logic_processor", 35, 3);
            register("calculation_processor", 35, 3);
            // T5 — engineering processor carries diamond; derivation pulls controller /
            // drive / spatial / quantum up to T5 from here.
            register("engineering_processor", 70, 5);
        });
    }

    private static void register(String path, int fu, int tier) {
        ResourceLocation id = ResourceLocation.tryParse(AE2 + ":" + path);
        if (id != null) {
            FuValueRegistry.registerApiItemValue(id, fu, tier);
        }
    }
}
