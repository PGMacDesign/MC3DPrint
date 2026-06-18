package com.pgmacdesign.mc3dprint.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A "Resin" — a consumable, single-use modifier dropped into the printer's
 * Resin slot to make a blueprint print better. Works ONLY on official/found
 * blueprints (never player-scanned ones — see {@link BlueprintDiscItem#isOfficial});
 * one resin = one catalyzed print, consumed on the print's first placement.
 *
 * <p>Each resin pairs an {@link Effect} with a tier (1-3); the valid
 * (effect, tier) pairs are declared on the effect ({@link Effect#tiers()}).
 * The actual effect is applied by {@code PrinterBlockEntity} during the print —
 * this item is just the carrier + the tooltip. SLA-resin throwback to the
 * filament(FU)-based printer's "other" 3D-printing medium.
 */
public class ResinItem extends Item {

    /** The six v1 effects, each declaring which tiers it ships at (the gated matrix). */
    public enum Effect {
        VERDANT("verdant", 1, 2),
        XP("xp", 1, 2, 3),
        TREASURE("treasure", 2, 3),
        OVERDRIVE("overdrive", 2, 3),
        QUARTERMASTER("quartermaster", 3),
        ORE_SALTING("ore_salting", 3);

        private final String id;
        private final int[] tiers;

        Effect(String id, int... tiers) {
            this.id = id;
            this.tiers = tiers;
        }

        public String id() {
            return id;
        }

        public int[] tiers() {
            return tiers.clone();
        }
    }

    private final Effect effect;
    private final int tier;

    public ResinItem(Effect effect, int tier, Properties properties) {
        super(properties);
        this.effect = effect;
        this.tier = tier;
    }

    public Effect effect() {
        return effect;
    }

    public int tier() {
        return tier;
    }

    /** Registry id, e.g. {@code resin_treasure_t3}. */
    public static String registryId(Effect effect, int tier) {
        return "resin_" + effect.id() + "_t" + tier;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        String descKey = "tooltip.mc3dprint.resin." + effect.id();
        if (effect == Effect.VERDANT) {
            // The two Verdant rarities genuinely differ (see ResinEffects.matureState): Common
            // ripens staple crops + nether wart; Uncommon ALSO ripens cocoa & sweet berries. So
            // each rarity gets its own line instead of one identical "fully grown" blurb.
            descKey += (tier >= 2) ? ".uncommon" : ".common";
        }
        tooltip.add(Component.translatable(descKey).withStyle(ChatFormatting.GRAY));
        // Resin strength reads as a RARITY (Common/Uncommon/Rare), not a "Tier" —
        // the word "tier" is already the printer/spool axis (T1–T8) and seeing it on
        // resins too was confusing. The internal tier int (1-3) still drives it.
        tooltip.add(Component.translatable(rarityKey(tier)).withStyle(rarityColor(tier)));
        tooltip.add(Component.translatable("tooltip.mc3dprint.resin.footer")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    /** Lang key for the resin's rarity label (tier 1→common, 2→uncommon, 3→rare). */
    private static String rarityKey(int tier) {
        return switch (tier) {
            case 1 -> "tooltip.mc3dprint.resin.rarity.common";
            case 2 -> "tooltip.mc3dprint.resin.rarity.uncommon";
            default -> "tooltip.mc3dprint.resin.rarity.rare";
        };
    }

    private static ChatFormatting rarityColor(int tier) {
        return switch (tier) {
            case 1 -> ChatFormatting.WHITE;
            case 2 -> ChatFormatting.AQUA;
            default -> ChatFormatting.LIGHT_PURPLE;
        };
    }
}
