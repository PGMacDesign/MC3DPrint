package com.pgmacdesign.mc3dprint.client;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.fu.FuValueRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

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
//? if >=1.21.5 {
/*@EventBusSubscriber(modid = MC3DPrint.MOD_ID, value = Dist.CLIENT)
*///?} else {
@EventBusSubscriber(modid = MC3DPrint.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
//?}
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
            // The lang string hyphenates the tier ("Tier-5") so JEI can filter on it. JEI indexes
            // tooltips as whitespace-split tokens and matches substrings, so an unhyphenated
            // "Tier 5" indexes as "tier" + "5" and searching it also returns every item whose FU
            // cost contains a 5. One token makes "tier-5" an exact filter. FuTooltipSearchTest
            // fails if the hyphen is ever "corrected" back to a space.
            event.getToolTip().add(Component.translatable(
                            "tooltip.mc3dprint.fu_value", value.tier(), String.format("%,d", value.fu()))
                    .withStyle(style -> style.withColor(TextColor.fromRgb(TIER_COLORS[tier]))));
            // A fuValues config override moves an item off its shipped default; flag that so a
            // surprising value reads as deliberate rather than a bug.
            String id = net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .getKey(event.getItemStack().getItem()).toString();
            int[] shipped = defaults().get(id);
            if (shipped != null && (shipped[0] != value.fu() || shipped[1] != value.tier())) {
                event.getToolTip().add(Component.translatable("tooltip.mc3dprint.fu_overridden")
                        .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
            }
            // Surface machine use so "can I print this?" reads at a glance anywhere you hover.
            // Most valued items print AND wind, so only the exceptions get a line. Mirrors the
            // printer/winder gates: #no_print = wind-only, #print_restricted = official-disc trophy,
            // winder-blacklisted = print-only.
            ItemStack stack = event.getItemStack();
            if (stack.is(com.pgmacdesign.mc3dprint.registry.ModItemTags.NO_PRINT)) {
                event.getToolTip().add(Component.translatable("tooltip.mc3dprint.wind_only")
                        .withStyle(net.minecraft.ChatFormatting.GOLD));
            } else if (stack.is(com.pgmacdesign.mc3dprint.registry.ModItemTags.PRINT_RESTRICTED)) {
                event.getToolTip().add(Component.translatable("tooltip.mc3dprint.trophy")
                        .withStyle(net.minecraft.ChatFormatting.GRAY));
            } else if (com.pgmacdesign.mc3dprint.registry.ModItemTags.isWinderBlacklisted(stack)) {
                event.getToolTip().add(Component.translatable("tooltip.mc3dprint.print_only")
                        .withStyle(net.minecraft.ChatFormatting.AQUA));
            }
        });
    }
}
