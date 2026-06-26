package com.pgmacdesign.mc3dprint.integration.create;

import com.pgmacdesign.mc3dprint.fu.FuValueRegistry;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * Create ({@code create}) FU values. Registered ONLY when Create is loaded; stored by
 * {@link ResourceLocation}, so a vanilla-only install never sees them.
 *
 * <p><b>Tiny material surface.</b> Create is a building/automation mod, not a tech-metals mod —
 * its entire economy-relevant material set is zinc (mined), andesite alloy, brass, and rose
 * quartz. Everything else is kinetic machinery, decorative stone, or derivable variants.
 *
 * <p><b>Anti-launder = the crushing graph.</b> Create's {@code create:crushing} recipes
 * ore-double (~1.75–2×) — and crush other mods' raws via {@code forge:raw_materials/*} tags — so
 * every {@code crushed_raw_*} is left UNVALUED. zinc gates brass + andesite alloy (both
 * mass-producible via automation but bottlenecked by the mined ore), so those stay modest.
 *
 * <p>{@code zinc_ingot} derives via {@code minecraft:smelting} of raw_zinc; brass/andesite
 * blocks/nuggets/sheets derive. Only the custom {@code create:mixing} alloys and the in-world
 * rose quartz (no datapack recipe — hardcoded redstone conversion) need pinning.
 */
public final class CreateCompat {
    private static final String CREATE = "create";

    private CreateCompat() {}

    public static void onCommonSetup(FMLCommonSetupEvent event) {
        if (!ModList.get().isLoaded(CREATE)) {
            return;
        }
        event.enqueueWork(() -> {
            // T2 — mined zinc (ingot derives from vanilla smelting of the raw)
            register("raw_zinc", 18, 2);
            register("zinc_ore", 18, 2);
            register("deepslate_zinc_ore", 18, 2);
            // T2 — andesite alloy: the gateway material (Mechanical Mixer, cheap inputs)
            register("andesite_alloy", 12, 2);
            // T3 — brass (Mechanical Mixer: copper + zinc); rose quartz (in-world redstone conversion)
            register("brass_ingot", 22, 3);
            register("rose_quartz", 10, 3);
        });
    }

    private static void register(String path, int fu, int tier) {
        ResourceLocation id = ResourceLocation.tryParse(CREATE + ":" + path);
        if (id != null) {
            FuValueRegistry.registerApiItemValue(id, fu, tier);
        }
    }
}
