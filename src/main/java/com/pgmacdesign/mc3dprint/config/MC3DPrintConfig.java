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
    public static final ForgeConfigSpec.DoubleValue UPGRADE_RF_FACTOR;
    public static final ForgeConfigSpec.DoubleValue UPGRADE_BUFFER_FACTOR;
    public static final ForgeConfigSpec.IntValue UPGRADE_MAX_PER_TYPE;
    public static final ForgeConfigSpec.IntValue CLOCK_GENERATOR_BURN_MULTIPLIER;
    public static final ForgeConfigSpec.IntValue PRINT_HISTORY_SIZE;
    public static final ForgeConfigSpec.IntValue UNKNOWN_BLOCK_FU;
    public static final ForgeConfigSpec.BooleanValue UNKNOWN_BLOCKS_PRINTABLE;
    public static final ForgeConfigSpec.BooleanValue DERIVE_FROM_SMELTING;
    public static final ForgeConfigSpec.BooleanValue DERIVE_FROM_STONECUTTING;
    public static final ForgeConfigSpec.IntValue FILAMENT_CONVERSION_RATIO;
    public static final ForgeConfigSpec.IntValue PREVIEW_MAX_BLOCKS;
    public static final ForgeConfigSpec.IntValue PREVIEW_RENDER_DISTANCE;
    public static final ForgeConfigSpec.DoubleValue RESIN_OVERDRIVE_T3_BELOW;
    public static final ForgeConfigSpec.DoubleValue RESIN_TREASURE_CHANCE_T2;
    public static final ForgeConfigSpec.DoubleValue RESIN_TREASURE_CHANCE_T3;
    public static final ForgeConfigSpec.IntValue RESIN_TREASURE_CAP_T2;
    public static final ForgeConfigSpec.IntValue RESIN_TREASURE_CAP_T3;
    public static final ForgeConfigSpec.DoubleValue RESIN_TREASURE_T2_UNCOMMON;
    public static final ForgeConfigSpec.DoubleValue RESIN_TREASURE_T3_RARE;
    public static final ForgeConfigSpec.DoubleValue RESIN_ORE_SALT_CHANCE;
    public static final ForgeConfigSpec.DoubleValue RESIN_ORE_SALT_GEM_SHARE;
    public static final ForgeConfigSpec.IntValue RESIN_ORE_SALT_MAX;
    public static final ForgeConfigSpec.IntValue RESIN_XP_CAP_T1;
    public static final ForgeConfigSpec.IntValue RESIN_XP_CAP_T2;
    public static final ForgeConfigSpec.IntValue RESIN_XP_CAP_T3;
    public static final ForgeConfigSpec.IntValue RESIN_XP_REF;
    public static final ForgeConfigSpec.IntValue RESIN_QM_COAL_BUDGET;
    public static final ForgeConfigSpec.IntValue RESIN_QM_FOOD_BUDGET;
    public static final ForgeConfigSpec.IntValue RESIN_QM_TORCH_BUDGET;
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
            EFFICIENCY[i] = builder.comment(
                    "Matter efficiency: the tier's innate print markup. At 0 Efficiency modules FU",
                    "cost = base / efficiency; each module removes an equal share, reaching exactly",
                    "1:1 at upgrades.maxPerType modules. Lower tiers are deliberately less efficient.")
                    .defineInRange("efficiency", tier.defaultEfficiency(), 0.01, 1.0);
            builder.pop();
        }

        builder.comment("Scanner").push("scanner");
        T1_SCANNER_MAX_EDGE = builder
                .comment("Scanner: maximum scan size per axis. The effective cap is the lesser of",
                        "this and the largest buildable printer footprint (T8=33 when Draconic",
                        "Evolution is installed, otherwise T7=23), so you can scan anything you",
                        "could print. Default matches the T8 ceiling; lower it for a smaller scanner.")
                .defineInRange("t1MaxEdge", 33, 1, 256);
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

        builder.comment("Upgrade modules. Speed/RF/Buffer stack multiplicatively per module;",
                "Efficiency is linear — it shaves the tier's innate print markup, reaching exactly",
                "1:1 (break-even) at maxPerType modules. Every type is capped at maxPerType per machine.")
                .push("upgrades");
        UPGRADE_SPEED_FACTOR = builder
                .comment("Print time multiplier per Speed Upgrade")
                .defineInRange("speedFactor", 0.8, 0.05, 1.0);
        UPGRADE_RF_FACTOR = builder
                .comment("RF cost multiplier per RF Efficiency Upgrade")
                .defineInRange("rfFactor", 0.85, 0.05, 1.0);
        UPGRADE_BUFFER_FACTOR = builder
                .comment("RF buffer multiplier per Buffer Upgrade")
                .defineInRange("bufferFactor", 1.5, 1.0, 8.0);
        UPGRADE_MAX_PER_TYPE = builder
                .comment("Max modules of any single type per machine. Also the number of Efficiency",
                        "modules that brings printing to exactly 1:1 (break-even); fewer leaves a markup.")
                .defineInRange("maxPerType", 4, 1, 64);
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

        builder.comment("Resin: consumed-per-print blueprint modifiers. All effects apply only",
                        "to official/found blueprints, never player-scanned ones.").push("resin");
        RESIN_OVERDRIVE_T3_BELOW = builder
                .comment("Overdrive Tier 3: fraction below break-even the print costs (net FU gain).",
                        "Tier 2 is always exactly break-even (0).")
                .defineInRange("overdriveT3BelowBreakEven", 0.20, 0.0, 0.9);
        RESIN_TREASURE_CHANCE_T2 = builder
                .comment("Treasure Tier 2: per-container chance a printed chest/barrel/shulker gets loot")
                .defineInRange("treasureChanceT2", 0.25, 0.0, 1.0);
        RESIN_TREASURE_CHANCE_T3 = builder
                .comment("Treasure Tier 3: per-container chance")
                .defineInRange("treasureChanceT3", 0.5, 0.0, 1.0);
        RESIN_TREASURE_CAP_T2 = builder
                .comment("Treasure Tier 2: max containers that can pop treasure per print")
                .defineInRange("treasureCapT2", 2, 0, 256);
        RESIN_TREASURE_CAP_T3 = builder
                .comment("Treasure Tier 3: max containers per print")
                .defineInRange("treasureCapT3", 4, 0, 256);
        RESIN_TREASURE_T2_UNCOMMON = builder
                .comment("Treasure Uncommon resin: chance a roll upgrades from the common to the uncommon table")
                .defineInRange("treasureT2UncommonChance", 0.2, 0.0, 1.0);
        RESIN_TREASURE_T3_RARE = builder
                .comment("Treasure Rare resin: chance a roll upgrades from the uncommon to the rare table")
                .defineInRange("treasureT3RareChance", 0.4, 0.0, 1.0);
        RESIN_ORE_SALT_CHANCE = builder
                .comment("Ore Salting: per-block chance a natural stone block prints as an ore vein")
                .defineInRange("oreSaltChance", 0.05, 0.0, 1.0);
        RESIN_ORE_SALT_GEM_SHARE = builder
                .comment("Ore Salting: share of salted ores that are diamond/emerald (rest common)")
                .defineInRange("oreSaltGemShare", 0.05, 0.0, 1.0);
        RESIN_ORE_SALT_MAX = builder
                .comment("Ore Salting: max ore veins salted per print")
                .defineInRange("oreSaltMaxPerPrint", 64, 0, 100_000);
        RESIN_XP_CAP_T1 = builder.comment("XP Yield Tier 1: max banked XP (~level 10 from 0)")
                .defineInRange("xpCapT1", 160, 0, Integer.MAX_VALUE);
        RESIN_XP_CAP_T2 = builder.comment("XP Yield Tier 2: max banked XP (~level 20 from 0)")
                .defineInRange("xpCapT2", 550, 0, Integer.MAX_VALUE);
        RESIN_XP_CAP_T3 = builder.comment("XP Yield Tier 3: max banked XP (~level 30 from 0)")
                .defineInRange("xpCapT3", 1500, 0, Integer.MAX_VALUE);
        RESIN_XP_REF = builder
                .comment("XP Yield reference: bankedXP = min(cap, round(cap * printCost / ref)).",
                        "Default 33000 ~ priciest curated build's print cost, so it reaches the cap.")
                .defineInRange("xpReference", 33000, 1, Integer.MAX_VALUE);
        RESIN_QM_COAL_BUDGET = builder
                .comment("Quartermaster: total COAL BLOCKS shared across all printed furnaces,",
                        "split evenly (2 furnaces => 32 each). A premium Rare-tier fuel stockpile.")
                .defineInRange("quartermasterCoalBlockBudget", 64, 0, 1024);
        RESIN_QM_FOOD_BUDGET = builder
                .comment("Quartermaster: total FOOD (bread) shared across all printed storage",
                        "containers (chests/barrels), split evenly.")
                .defineInRange("quartermasterFoodBudget", 64, 0, 1024);
        RESIN_QM_TORCH_BUDGET = builder
                .comment("Quartermaster: total TORCHES shared across all printed storage",
                        "containers (chests/barrels), split evenly.")
                .defineInRange("quartermasterTorchBudget", 64, 0, 1024);
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
