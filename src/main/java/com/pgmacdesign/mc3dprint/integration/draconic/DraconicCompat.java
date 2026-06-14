package com.pgmacdesign.mc3dprint.integration.draconic;

import com.pgmacdesign.mc3dprint.fu.FuValueRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * Draconic Evolution FU values (Tier 8). Registered only when DE is loaded; the FU
 * registry stores these by {@link ResourceLocation}, so on a vanilla-only install the
 * entries are simply never matched (no crash, no warning spam — unlike putting modded
 * ids in the config default list).
 *
 * <p>Only the BASE draconium chain (ore → dust → ingot → block) is valued. DE's
 * fusion-crafted gear (Draconic/Wyvern cores, energy components, draconic chest) uses
 * custom Fusion Crafting recipe types our {@code RecipeFuValuator} can't read, so it
 * stays unprintable by design. Standard-crafted DE items below the fusion tier derive
 * from this base chain.
 *
 * <p>The T8 multiblock already requires {@code draconicevolution:awakened_draconium_block}
 * at its corners, which confirms the namespace + id convention. Awakened Draconium is the
 * structural corner, not a print target, so it is intentionally not valued here. If a value
 * doesn't take, verify the path against the installed DE build.
 */
public final class DraconicCompat {
    private static final String DE = "draconicevolution";

    private DraconicCompat() {}

    public static void onCommonSetup(FMLCommonSetupEvent event) {
        if (!ModList.get().isLoaded(DE)) {
            return;
        }
        event.enqueueWork(() -> {
            register("draconium_dust", 200);
            register("draconium_ingot", 250);
            register("draconium_block", 2250); // 9x ingot
            register("draconium_ore", 250);
            register("nether_draconium_ore", 250);
            register("end_draconium_ore", 250);
        });
    }

    private static void register(String path, int fu) {
        ResourceLocation id = ResourceLocation.tryParse(DE + ":" + path);
        if (id != null) {
            FuValueRegistry.registerApiItemValue(id, fu, 8);
        }
    }
}
