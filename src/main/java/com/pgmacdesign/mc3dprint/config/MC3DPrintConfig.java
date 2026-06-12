package com.pgmacdesign.mc3dprint.config;

import com.pgmacdesign.mc3dprint.fu.FuValueRegistry;
import com.pgmacdesign.mc3dprint.machine.MachineTier;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/**
 * Gameplay tuning, all pack-maker exposed per the design doc. Per-tier values
 * default to the balancing table — conservative on purpose ("easier to buff
 * than nerf post-release").
 */
public final class MC3DPrintConfig {
    public static final ForgeConfigSpec SPEC;

    // Per-tier (index = tier - 1)
    private static final ForgeConfigSpec.IntValue[] ENERGY_BUFFER = new ForgeConfigSpec.IntValue[8];
    private static final ForgeConfigSpec.IntValue[] MAX_ENERGY_RECEIVE = new ForgeConfigSpec.IntValue[8];
    private static final ForgeConfigSpec.IntValue[] ITEM_RF_PER_TICK = new ForgeConfigSpec.IntValue[8];
    private static final ForgeConfigSpec.IntValue[] ITEM_PRINT_TICKS = new ForgeConfigSpec.IntValue[8];
    private static final ForgeConfigSpec.IntValue[] RF_PER_BLOCK = new ForgeConfigSpec.IntValue[8];
    private static final ForgeConfigSpec.IntValue[] TICKS_PER_BLOCK = new ForgeConfigSpec.IntValue[8];
    private static final ForgeConfigSpec.DoubleValue[] EFFICIENCY = new ForgeConfigSpec.DoubleValue[8];

    public static final ForgeConfigSpec.IntValue T1_SCANNER_MAX_EDGE;
    public static final ForgeConfigSpec.IntValue WINDER_RF_PER_ITEM;
    public static final ForgeConfigSpec.IntValue WINDER_TICKS_PER_ITEM;
    public static final ForgeConfigSpec.IntValue WINDER_ENERGY_BUFFER;
    public static final ForgeConfigSpec.IntValue WINDER_MAX_ENERGY_RECEIVE;
    public static final ForgeConfigSpec.IntValue CLOCK_GENERATOR_RF_PER_TICK;
    public static final ForgeConfigSpec.DoubleValue UPGRADE_SPEED_FACTOR;
    public static final ForgeConfigSpec.DoubleValue UPGRADE_EFFICIENCY_FACTOR;
    public static final ForgeConfigSpec.DoubleValue UPGRADE_RF_FACTOR;
    public static final ForgeConfigSpec.DoubleValue UPGRADE_BUFFER_FACTOR;
    public static final ForgeConfigSpec.IntValue CLOCK_GENERATOR_BURN_MULTIPLIER;
    public static final ForgeConfigSpec.IntValue PRINT_HISTORY_SIZE;
    public static final ForgeConfigSpec.IntValue UNKNOWN_BLOCK_FU;
    public static final ForgeConfigSpec.BooleanValue UNKNOWN_BLOCKS_PRINTABLE;
    public static final ForgeConfigSpec.BooleanValue DERIVE_FROM_SMELTING;
    public static final ForgeConfigSpec.BooleanValue DERIVE_FROM_STONECUTTING;
    public static final ForgeConfigSpec.IntValue FILAMENT_CONVERSION_RATIO;
    public static final ForgeConfigSpec.IntValue PREVIEW_MAX_BLOCKS;
    public static final ForgeConfigSpec.IntValue PREVIEW_RENDER_DISTANCE;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> FU_VALUES;

    private static final int[] DEFAULT_ITEM_RF_PER_TICK = {40, 60, 80, 100, 120, 150, 200, 250};

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        for (MachineTier tier : MachineTier.values()) {
            int i = tier.number() - 1;
            builder.comment("Tier " + tier.number() + " printer").push("tier" + tier.number());
            ENERGY_BUFFER[i] = builder.comment("Internal RF buffer capacity")
                    .defineInRange("energyBuffer", tier.defaultEnergyBuffer(), 1_000, Integer.MAX_VALUE);
            MAX_ENERGY_RECEIVE[i] = builder.comment("Max RF accepted per tick from cables")
                    .defineInRange("maxEnergyReceive", tier.defaultMaxReceive(), 1, Integer.MAX_VALUE);
            ITEM_RF_PER_TICK[i] = builder.comment("RF consumed per tick while printing an item")
                    .defineInRange("itemRfPerTick", DEFAULT_ITEM_RF_PER_TICK[i], 1, Integer.MAX_VALUE);
            ITEM_PRINT_TICKS[i] = builder.comment("Ticks to print one item in Item Mode")
                    .defineInRange("itemPrintTicks", tier.defaultItemPrintTicks(), 1, Integer.MAX_VALUE);
            RF_PER_BLOCK[i] = builder.comment("RF consumed per block placed in Blueprint Mode")
                    .defineInRange("rfPerBlock", tier.defaultRfPerBlock(), 1, Integer.MAX_VALUE);
            TICKS_PER_BLOCK[i] = builder.comment("Ticks between block placements in Blueprint Mode")
                    .defineInRange("ticksPerBlock", tier.defaultTicksPerBlock(), 1, Integer.MAX_VALUE);
            EFFICIENCY[i] = builder.comment("Matter efficiency: FU cost = base / efficiency")
                    .defineInRange("efficiency", tier.defaultEfficiency(), 0.01, 1.0);
            builder.pop();
        }

        builder.comment("Scanner").push("scanner");
        T1_SCANNER_MAX_EDGE = builder
                .comment("Tier 1 Scanner: maximum scan size per axis")
                .defineInRange("t1MaxEdge", 7, 1, 256);
        builder.pop();

        builder.comment("Filament Winder").push("winder");
        WINDER_RF_PER_ITEM = builder
                .comment("RF consumed per item wound onto a spool")
                .defineInRange("rfPerItem", 200, 0, Integer.MAX_VALUE);
        WINDER_TICKS_PER_ITEM = builder
                .comment("Ticks to wind one item")
                .defineInRange("ticksPerItem", 20, 1, Integer.MAX_VALUE);
        WINDER_ENERGY_BUFFER = builder
                .comment("Internal RF buffer capacity")
                .defineInRange("energyBuffer", 20_000, 1_000, Integer.MAX_VALUE);
        WINDER_MAX_ENERGY_RECEIVE = builder
                .comment("Max RF accepted per tick from cables")
                .defineInRange("maxEnergyReceive", 1_000, 1, Integer.MAX_VALUE);
        builder.pop();

        builder.comment("Upgrade modules (multiplicative per module installed)").push("upgrades");
        UPGRADE_SPEED_FACTOR = builder
                .comment("Print time multiplier per Speed Upgrade")
                .defineInRange("speedFactor", 0.8, 0.05, 1.0);
        UPGRADE_EFFICIENCY_FACTOR = builder
                .comment("FU cost multiplier per Efficiency Upgrade")
                .defineInRange("efficiencyFactor", 0.9, 0.05, 1.0);
        UPGRADE_RF_FACTOR = builder
                .comment("RF cost multiplier per RF Efficiency Upgrade")
                .defineInRange("rfFactor", 0.85, 0.05, 1.0);
        UPGRADE_BUFFER_FACTOR = builder
                .comment("RF buffer multiplier per Buffer Upgrade")
                .defineInRange("bufferFactor", 1.5, 1.0, 8.0);
        builder.pop();

        builder.comment("General").push("general");
        CLOCK_GENERATOR_RF_PER_TICK = builder
                .comment("RF generated per tick by the Clock Generator while burning fuel")
                .defineInRange("clockGeneratorRfPerTick", 10, 1, Integer.MAX_VALUE);
        CLOCK_GENERATOR_BURN_MULTIPLIER = builder
                .comment("Clock Generator fuel efficiency: furnace burn time is multiplied by this",
                        "(default 10: one coal burns ~13 minutes = 160,000 RF at 10 RF/t)")
                .defineInRange("clockGeneratorBurnMultiplier", 10, 1, 1_000);
        PRINT_HISTORY_SIZE = builder
                .comment("Entries kept in each printer's job history log")
                .defineInRange("printHistorySize", 10, 0, 100);
        UNKNOWN_BLOCK_FU = builder
                .comment("FU cost per printed block whose item has no configured/derived FU value.",
                        "Only used when unknownBlocksPrintable=true. Raised from 3 -> 50 so an",
                        "un-priced modded block is never dirt-cheap.")
                .defineInRange("unknownBlockFu", 50, 0, Integer.MAX_VALUE);
        UNKNOWN_BLOCKS_PRINTABLE = builder
                .comment("Strict mode. FALSE (default): a structure containing any block with no",
                        "explicit, API, or recipe-derived FU value is NOT printable on any tier —",
                        "this closes the 'scan an un-priced expensive block, print it cheap' exploit.",
                        "TRUE: such blocks fall back to unknownBlockFu and are clamped to require",
                        "the printing machine's own tier (never cheap on a low-tier machine).")
                .define("unknownBlocksPrintable", false);
        DERIVE_FROM_SMELTING = builder
                .comment("Derive FU values from smelting recipes too (so smeltable ores resolve",
                        "from their smelted result). Blasting/smoking/campfire are never used.")
                .define("deriveFromSmelting", true);
        DERIVE_FROM_STONECUTTING = builder
                .comment("Derive FU values from stonecutting recipes too.")
                .define("deriveFromStonecutting", true);
        FILAMENT_CONVERSION_RATIO = builder
                .comment("Filament tier exchange rate: 1 FU of tier N is worth this many FU of tier N-1.",
                        "Down-converting is generous, up-converting costs ratio^tierGap (anti-exploit).",
                        "Set to 1 for a single universal FU with no tier economics.")
                .defineInRange("filamentConversionRatio", 4, 1, 64);
        PREVIEW_MAX_BLOCKS = builder
                .comment("Hologram preview: largest blueprint (in blocks) that can be previewed")
                .defineInRange("previewMaxBlocks", 10_000, 1, 1_000_000);
        PREVIEW_RENDER_DISTANCE = builder
                .comment("Hologram preview: ghost blocks render within this distance of the camera",
                        "(the frame outline always shows the full extent)")
                .defineInRange("previewRenderDistance", 16, 4, 64);
        FU_VALUES = builder
                .comment("Filament Unit values: '<item_or_#tag>=<fu>@<min_tier>'. Symmetric: wind yield == print cost (before efficiency).")
                .defineListAllowEmpty("fuValues", FuValueRegistry.defaultEntries(),
                        o -> o instanceof String s && s.contains("=") && s.contains("@"));
        builder.pop();

        SPEC = builder.build();
    }

    public static int energyBuffer(MachineTier tier) {
        return ENERGY_BUFFER[tier.number() - 1].get();
    }

    public static int maxEnergyReceive(MachineTier tier) {
        return MAX_ENERGY_RECEIVE[tier.number() - 1].get();
    }

    public static int itemRfPerTick(MachineTier tier) {
        return ITEM_RF_PER_TICK[tier.number() - 1].get();
    }

    public static int itemPrintTicks(MachineTier tier) {
        return ITEM_PRINT_TICKS[tier.number() - 1].get();
    }

    public static int rfPerBlock(MachineTier tier) {
        return RF_PER_BLOCK[tier.number() - 1].get();
    }

    public static int ticksPerBlock(MachineTier tier) {
        return TICKS_PER_BLOCK[tier.number() - 1].get();
    }

    public static double efficiency(MachineTier tier) {
        return EFFICIENCY[tier.number() - 1].get();
    }

    private MC3DPrintConfig() {}
}
