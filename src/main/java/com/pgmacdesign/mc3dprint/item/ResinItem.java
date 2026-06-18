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
        tooltip.add(Component.translatable("tooltip.mc3dprint.resin." + effect.id())
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.mc3dprint.resin.tier", tier)
                .withStyle(tierColor(tier)));
        tooltip.add(Component.translatable("tooltip.mc3dprint.resin.footer")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    private static ChatFormatting tierColor(int tier) {
        return switch (tier) {
            case 1 -> ChatFormatting.WHITE;
            case 2 -> ChatFormatting.AQUA;
            default -> ChatFormatting.LIGHT_PURPLE;
        };
    }
}
