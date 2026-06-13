package com.pgmacdesign.mc3dprint.client;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.fu.FuValueRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Ambient tier discovery: any item the Filament Winder can convert gets a
 * tier-colored "MC3DP: Tier N (F FU)" line on its tooltip — in the
 * inventory, the creative menu, and JEI alike. The tier shown is exactly the
 * spool tier the winder needs (exact-tier rule), so players can see "diamond is
 * Tier 4, nether star is Tier 6" at a glance instead of guessing.
 *
 * Values come from {@link FuValueRegistry} (config-driven), so this tracks any
 * pack overrides automatically. On a server the client shows its own common
 * config's values.
 */
@Mod.EventBusSubscriber(modid = MC3DPrint.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FilamentTooltip {

    // rarity ramp, indexed by tier (1..8); [0] is a safe fallback
    private static final int[] TIER_COLORS = {
            0x9E9E9E, // -
            0x9E9E9E, // T1 stone gray
            0x8FC7E8, // T2 copper/iron blue
            0xE06666, // T3 redstone red
            0x4FE0C8, // T4 diamond cyan
            0xC8803A, // T5 netherite brown
            0xF2E25C, // T6 gold
            0xB96BE6, // T7 nether star / dragon purple
            0xFF5FA8, // T8 draconic magenta
    };

    private FilamentTooltip() {}

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        FuValueRegistry.valueOf(event.getItemStack()).ifPresent(value -> {
            int tier = Math.max(1, Math.min(8, value.tier()));
            event.getToolTip().add(Component.translatable(
                            "tooltip.mc3dprint.fu_value", value.tier(), String.format("%,d", value.fu()))
                    .withStyle(style -> style.withColor(TextColor.fromRgb(TIER_COLORS[tier]))));
        });
    }
}
