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
            0x4FE0C8, // T4 emerald cyan
            0xC8803A, // T5 diamond (was netherite; netherite is now T6)
            0xF2E25C, // T6 netherite brown — netherite moved up from T5 to T6
            0xB96BE6, // T7 nether star / dragon purple
            0xFF5FA8, // T8 draconic magenta
    };

    private FilamentTooltip() {}

    /** Shipped defaults ("item=fu@tier"), parsed once — the reference for the override marker. */
    private static java.util.Map<String, int[]> defaultValues;

    private static java.util.Map<String, int[]> defaults() {
        if (defaultValues == null) {
            java.util.Map<String, int[]> map = new java.util.HashMap<>();
            for (String entry : FuValueRegistry.defaultEntries()) {
                int eq = entry.indexOf('=');
                int at = entry.indexOf('@');
                if (eq <= 0 || at <= eq || entry.startsWith("#")) {
                    continue; // tag defaults aren't attributable to a single item — skip
                }
                try {
                    map.put(entry.substring(0, eq).trim(), new int[]{
                            Integer.parseInt(entry.substring(eq + 1, at).trim()),
                            Integer.parseInt(entry.substring(at + 1).trim())});
                } catch (NumberFormatException ignored) {
                    // malformed default line — nothing to compare against
                }
            }
            defaultValues = map;
        }
        return defaultValues;
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        FuValueRegistry.valueOf(event.getItemStack()).ifPresent(value -> {
            int tier = Math.max(1, Math.min(8, value.tier()));
            event.getToolTip().add(Component.translatable(
                            "tooltip.mc3dprint.fu_value", value.tier(), String.format("%,d", value.fu()))
                    .withStyle(style -> style.withColor(TextColor.fromRgb(TIER_COLORS[tier]))));
            // JEI's searchable filament/tier_N tags mirror the DEFAULTS only, so when a
            // fuValues config override moves an item, say so — the live line above is
            // authoritative, the tag-driven search grouping may lag.
            String id = net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .getKey(event.getItemStack().getItem()).toString();
            int[] shipped = defaults().get(id);
            if (shipped != null && (shipped[0] != value.fu() || shipped[1] != value.tier())) {
                event.getToolTip().add(Component.translatable("tooltip.mc3dprint.fu_overridden")
                        .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
            }
        });
    }
}
