package com.pgmacdesign.mc3dprint.integration.thermal;

import com.pgmacdesign.mc3dprint.fu.FuValueRegistry;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * Thermal Series (Foundation + Expansion + Dynamics) FU values. Modern Thermal is
 * unified under one mod id AND item namespace — {@code "thermal"} — so gating on that
 * single id covers all three. Registered only when loaded; zero footprint otherwise.
 *
 * <p>Machines, dynamos, ducts and the early alloys (bronze / electrum / invar /
 * constantan) all derive from standard crafting + smelting. Only the base metals and the
 * three signature alloys need explicit values — the alloys come from the Induction
 * Smelter custom recipe our valuator can't read (same situation as draconium).
 *
 * <p><b>Anti-launder:</b> Thermal's whole identity is resource multiplication (pulverizer
 * ore-doubling, tree extractor, crop insolator), so all {@code _dust} / {@code _plate} /
 * {@code slag} / {@code latex} / {@code rubber} / {@code phytogro} are left UNVALUED on
 * purpose — the doubling mechanics stay entirely out of the FU graph.
 */
public final class ThermalCompat {
    private static final String THERMAL = "thermal";

    private ThermalCompat() {}

    public static void onCommonSetup(FMLCommonSetupEvent event) {
        if (!ModList.get().isLoaded(THERMAL)) {
            return;
        }
        event.enqueueWork(() -> {
            // T2 — base metals (iron band): value ingot + raw + ore so they print & wind
            for (String metal : new String[] {"tin", "lead", "silver", "nickel"}) {
                register(metal + "_ingot", 20, 2);
                register("raw_" + metal, 18, 2);
                register(metal + "_ore", 18, 2);
                register("deepslate_" + metal + "_ore", 18, 2);
            }
            // T4–T5 — signature alloys (Induction Smelter custom recipe; won't derive)
            register("signalum_ingot", 35, 4);
            register("lumium_ingot", 40, 4);
            register("enderium_ingot", 90, 5);
        });
    }

    private static void register(String path, int fu, int tier) {
        ResourceLocation id = ResourceLocation.tryParse(THERMAL + ":" + path);
        if (id != null) {
            FuValueRegistry.registerApiItemValue(id, fu, tier);
        }
    }
}
