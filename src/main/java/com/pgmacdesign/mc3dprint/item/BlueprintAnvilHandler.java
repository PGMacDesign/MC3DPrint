package com.pgmacdesign.mc3dprint.item;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

/**
 * Lets players rename a written Blueprint Disc on an anvil for a flat 1 XP level
 * (no material cost, no scaling prior-work penalty). The blueprint reference and
 * cached metadata are preserved — only the display name changes. Typing a blank
 * name reverts the disc to its default name.
 */
@Mod.EventBusSubscriber(modid = MC3DPrint.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BlueprintAnvilHandler {
    private BlueprintAnvilHandler() {}

    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        // rename-only (right slot empty) of a written disc; otherwise let vanilla handle it
        if (!(left.getItem() instanceof BlueprintDiscItem) || !event.getRight().isEmpty()
                || !BlueprintDiscItem.hasBlueprint(left)) {
            return;
        }
        String typed = event.getName();
        String current = left.hasCustomHoverName() ? left.getHoverName().getString() : "";
        String desired = typed == null ? "" : typed;
        if (desired.equals(current)) {
            return; // nothing typed / no change — leave the anvil's default behavior
        }
        ItemStack output = left.copy();
        if (desired.isEmpty()) {
            output.resetHoverName();
        } else {
            output.setHoverName(Component.literal(desired));
        }
        event.setOutput(output);
        event.setCost(1);       // minimal: a single XP level
        event.setMaterialCost(0);
    }
}
