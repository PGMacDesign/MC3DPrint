package com.pgmacdesign.mc3dprint.config;

import com.pgmacdesign.mc3dprint.fu.FuValueRegistry;
import com.pgmacdesign.mc3dprint.machine.MachineTier;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * Gameplay tuning, all pack-maker exposed per the design doc. Per-tier values
 * default to the balancing table — conservative on purpose ("easier to buff
 * than nerf post-release").
 */
public final class MC3DPrintConfig {
    public static final ModConfigSpec SPEC;

    // Per-tier (index = tier - 1)
    private static final ModConfigSpec.IntValue[] ENERGY_BUFFER = new ModConfigSpec.IntValue[8];
    private static final ModConfigSpec.IntValue[] MAX_ENERGY_RECEIVE = new ModConfigSpec.IntValue[8];
    private static final ModConfigSpec.IntValue[] ITEM_RF_PER_TICK = new ModConfigSpec.IntValue[8];
    private static final ModConfigSpec.IntValue[] ITEM_PRINT_TICKS = new ModConfigSpec.IntValue[8];
    private static final ModConfigSpec.IntValue[] RF_PER_BLOCK = new ModConfigSpec.IntValue[8];
    private static final ModConfigSpec.IntValue[] TICKS_PER_BLOCK = new ModConfigSpec.IntValue[8];
    private static final ModConfigSpec.DoubleValue[] EFFICIENCY = new ModConfigSpec.DoubleValue[8];
    private static final ModConfigSpec.IntValue[] MAX_FOOTPRINT = new ModConfigSpec.IntValue[8];

    public static final ModConfigSpec.IntValue T1_SCANNER_MAX_EDGE;
    public static final ModConfigSpec.BooleanValue UNLOCK_SCANNER_SIZE;
    public static final ModConfigSpec.IntValue WINDER_RF_PER_ITEM;
    public static final ModConfigSpec.IntValue WINDER_TICKS_PER_ITEM;
    public static final ModConfigSpec.IntValue WINDER_ENERGY_BUFFER;
    public static final ModConfigSpec.IntValue WINDER_MAX_ENERGY_RECEIVE;
    public static final ModConfigSpec.DoubleValue DECONSTRUCT_YIELD_FACTOR;
    public static final ModConfigSpec.IntValue CABLE_TRANSFER_RATE;
    public static final ModConfigSpec.IntValue CLOCK_GENERATOR_RF_PER_TICK;
    public static final ModConfigSpec.DoubleValue UPGRADE_SPEED_FACTOR;
    public static final ModConfigSpec.DoubleValue UPGRADE_RF_FACTOR;
    public static final ModConfigSpec.DoubleValue UPGRADE_BUFFER_FACTOR;
    public static final ModConfigSpec.IntValue UPGRADE_MAX_PER_TYPE;
    public static final ModConfigSpec.IntValue CLOCK_GENERATOR_BURN_MULTIPLIER;
    public static final ModConfigSpec.IntValue PRINT_HISTORY_SIZE;
    public static final ModConfigSpec.IntValue UNKNOWN_BLOCK_FU;
    public static final ModConfigSpec.BooleanValue UNKNOWN_BLOCKS_PRINTABLE;
    public static final ModConfigSpec.BooleanValue DERIVE_FROM_SMELTING;
    public static final ModConfigSpec.BooleanValue DERIVE_FROM_STONECUTTING;
    public static final ModConfigSpec.IntValue FILAMENT_CONVERSION_RATIO;
    public static final ModConfigSpec.IntValue PREVIEW_MAX_BLOCKS;
    public static final ModConfigSpec.IntValue PREVIEW_RENDER_DISTANCE;
    public static final ModConfigSpec.BooleanValue ALLOW_ALL_DISCS_IN_CREATIVE;
    public static final ModConfigSpec.DoubleValue RESIN_OVERDRIVE_T3_BELOW;
    public static final ModConfigSpec.DoubleValue RESIN_TREASURE_CHANCE_T2;
    public static final ModConfigSpec.DoubleValue RESIN_TREASURE_CHANCE_T3;
    public static final ModConfigSpec.IntValue RESIN_TREASURE_CAP_T2;
    public static final ModConfigSpec.IntValue RESIN_TREASURE_CAP_T3;
    public static final ModConfigSpec.DoubleValue RESIN_TREASURE_T2_UNCOMMON;
    public static final ModConfigSpec.DoubleValue RESIN_TREASURE_T3_RARE;
    public static final ModConfigSpec.DoubleValue RESIN_ORE_SALT_CHANCE;
    public static final ModConfigSpec.DoubleValue RESIN_ORE_SALT_GEM_SHARE;
    public static final ModConfigSpec.IntValue RESIN_ORE_SALT_MAX;
    public static final ModConfigSpec.IntValue RESIN_XP_CAP_T1;
    public static final ModConfigSpec.IntValue RESIN_XP_CAP_T2;
    public static final ModConfigSpec.IntValue RESIN_XP_CAP_T3;
    public static final ModConfigSpec.IntValue RESIN_XP_REF;
    public static final ModConfigSpec.IntValue RESIN_QM_COAL_BUDGET;
    public static final ModConfigSpec.IntValue RESIN_QM_FOOD_BUDGET;
    public static final ModConfigSpec.IntValue RESIN_QM_TORCH_BUDGET;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> FU_VALUES;
    public static final ModConfigSpec.BooleanValue BLUEPRINT_REPOSITORY_SHARED;
    public static final ModConfigSpec.IntValue SORTER_MAX_PER_TICK;
    public static final ModConfigSpec.BooleanValue SORTER_REJECT_ROUTING;
    public static final ModConfigSpec.DoubleValue BLUEPRINT_CHANCE_MULTIPLIER;
    public static final ModConfigSpec.BooleanValue NO_DUPLICATE_BLUEPRINTS;

    private static final int[] DEFAULT_ITEM_RF_PER_TICK = {40, 60, 80, 100, 120, 150, 200, 250};

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

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
            MAX_FOOTPRINT[i] = builder.comment(
                    "Max horizontal print footprint (per axis, in blocks). 0 = items only (the",
                    "T1/T2 default). Also caps Deconstruct regions — a machine un-prints exactly",
                    "what it can print. Crank it up if you want ludicrously large prints; very",
                    "large builds keep their chunks force-loaded for the whole job, so expect a",
                    "server-performance cost to match your ambition.")
                    .defineInRange("maxFootprint", tier.maxFootprint(), 0, 1_000);
            builder.pop();
        }

        builder.comment("Scanner").push("scanner");
        T1_SCANNER_MAX_EDGE = builder
                .comment("Scanner: maximum scan size per axis (a flat cap, default 33). Independent",
                        "of machine tier and of Draconic Evolution — hand-scans stay a sane size while",
                        "official/curated discs can print larger builds on a high-tier fabricator.",
                        "Lower it for a smaller scanner; raising it does not change what a printer can build.")
                .defineInRange("t1MaxEdge", 33, 1, 256);
        UNLOCK_SCANNER_SIZE = builder
                .comment("Scanner size override. FALSE (default): the scanner is capped at the flat",
                        "t1MaxEdge above. TRUE: raise the scan cap to the largest footprint a buildable",
                        "fabricator can actually print — the configured tier8.maxFootprint (default 51)",
                        "with Draconic Evolution installed, otherwise tier7.maxFootprint (default 33) —",
                        "so you can scan a very large build and turn it into a printable blueprint.",
                        "Advanced/opt-in; off by default.")
                .define("unlockScannerSize", false);
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

        builder.comment("Deconstruct Mode: a printer/fabricator running in reverse — consumes a",
                        "selected region back into Filament Units at a LOSSY rate. The yield factor is",
                        "hard-capped below 1.0 so wind -> print -> deconstruct always strictly loses FU",
                        "(no laundering loop), independent of Efficiency modules or resins.").push("deconstruct");
        DECONSTRUCT_YIELD_FACTOR = builder
                .comment("Fraction of an item's wind value credited when its block is deconstructed",
                        "(floor, exact-tier). 0 makes Deconstruct a pure clearing tool.")
                .defineInRange("yieldFactor", 0.5, 0.0, 0.99);
        builder.pop();

        builder.comment("MC3D Cable: a single deliberately-modest cable that carries BOTH RF",
                        "(standard Forge Energy, so it powers any mod's machines) and Filament Units",
                        "(pulled on demand by printers from connected Filament Racks). Kept weak on",
                        "purpose so it never replaces a real power-management mod's cabling.").push("cable");
        CABLE_TRANSFER_RATE = builder
                .comment("Max RF moved per cable segment per tick (also its internal buffer size).",
                        "Default 2000 FE/t ~ Ender IO's basic tier: comfortably runs this mod's",
                        "machines (a printer draws <=250 FE/t under load) without being a base backbone.",
                        "Filament-Unit transfer is demand-driven and intentionally uncapped.")
                .defineInRange("transferRate", 2_000, 1, Integer.MAX_VALUE);
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
        ALLOW_ALL_DISCS_IN_CREATIVE = builder
                .comment("Creative tab: which curated Blueprint Discs are grabbable in the creative menu.",
                        "TRUE  - show EVERY curated build the mod ships (the full set). Handy for",
                        "        development, testing, and creative play.",
                        "FALSE - show only the small hand-picked launch set (ModCreativeTabs.CREATIVE_LAUNCH_DISCS),",
                        "        so players discover the rest as world loot.",
                        "This toggle is purely a creative-menu convenience and does NOT affect survival:",
                        "every curated build can still be found as world loot regardless of this setting.")
                .define("allowAllDiscsInCreative", true);
        FU_VALUES = builder
                .comment("Filament Unit value OVERRIDES, merged over the mod's built-in defaults at load —",
                        "leave empty to use the defaults, and new/rebalanced defaults apply on every update",
                        "without touching this file.",
                        "  '<item_or_#tag>=<fu>@<min_tier>'  adds a value or overrides the default",
                        "  '<item_or_#tag>=off'              removes a value (strict mode -> unprintable)",
                        "Symmetric: wind yield == print cost (before efficiency). Configs written by <=0.10",
                        "contained the full copied default list; those entries keep working as overrides.")
                .defineListAllowEmpty("fuValues", List.of(),
                        o -> o instanceof String s && s.contains("=") && (s.contains("@") || s.endsWith("=off")));
        builder.pop();

        builder.comment("Server Blueprint Repository: the library block that stores deposited",
                        "blueprints so they can be re-burned onto blank discs.").push("repository");
        BLUEPRINT_REPOSITORY_SHARED = builder
                .comment("Who shares a repository's catalogued blueprints.",
                        "TRUE  (default) - one SHARED world-level library: every player's deposits pool",
                        "        together and any repository block views the same catalogue; breaking or",
                        "        recrafting a block never loses it (stored as world data).",
                        "FALSE - PERSONAL per-player libraries: each player sees only their own deposits",
                        "        on any block (tied to the player, so nothing is lost on break/recraft).",
                        "Any value that isn't a valid boolean falls back to TRUE.")
                .define("blueprintRepositoryIsShared", true);
        builder.pop();

        builder.comment("Blueprint world loot: where curated Blueprint Discs turn up while looting.",
                        "Which tables carry them, and the base drop chance, live in the datapack file",
                        "data/mc3dprint/loot_modifiers/world_blueprints.json so packs can retarget them.")
                .push("loot");
        BLUEPRINT_CHANCE_MULTIPLIER = builder
                .comment("Scales the datapack's blueprint drop chance, then clamped to [0,1].",
                        "1.0 (default) uses the shipped rate as-is; 0.0 disables blueprint loot",
                        "entirely; 2.0 doubles it. Retune the drop rate without editing the datapack.")
                .defineInRange("blueprintChanceMultiplier", 1.0D, 0.0D, 10.0D);
        NO_DUPLICATE_BLUEPRINTS = builder
                .comment("TRUE (default): a build already found is excluded from future loot until",
                        "        every findable build has been found, at which point the cycle resets.",
                        "        Scope follows repository.blueprintRepositoryIsShared, so a shared",
                        "        library means one server-wide set and a personal library means one",
                        "        set per player.",
                        "FALSE - every roll draws from the full pool, so duplicates are possible.",
                        "Finds are recorded either way, so turning this back on resumes where it left off.")
                .define("noDuplicateBlueprints", true);
        builder.pop();

        builder.comment("Filament Tier Item Sorter: a passive, unpowered block that routes items to",
                        "the Filament Winder holding a spool of that item's material tier.").push("sorter");
        SORTER_MAX_PER_TICK = builder
                .comment("Items routed per sorter per tick. Clamped to [1,64] — one stack is the ceiling",
                        "because a winder's single input slot takes at most a stack, and an unbounded",
                        "value would let one sorter starve the server thread. Per-sorter, not a global",
                        "budget, so cost scales with how many sorters you place. No RF cost.")
                .defineInRange("maxRoutedPerTick", 4, 1, 64);
        SORTER_REJECT_ROUTING = builder
                .comment("TRUE - the sorter accepts un-windable items when a non-MC3DPrint inventory",
                        "sits on any face, and pushes them into it on the next tick, so a mixed item",
                        "stream can be pointed straight at the sorter.",
                        "FALSE - un-windable items are refused at insertion, as they were before this",
                        "existed. That is also the behaviour when no such inventory is adjacent, so",
                        "turning this off only matters to builds that already have one.")
                .define("rejectRouting", true);
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

    public static int maxFootprint(MachineTier tier) {
        return MAX_FOOTPRINT[tier.number() - 1].get();
    }

    /** The raw config value, for tests that need to set-and-restore the cap. */
    public static ModConfigSpec.IntValue maxFootprintValue(MachineTier tier) {
        return MAX_FOOTPRINT[tier.number() - 1];
    }

    public static int cableTransferRate() {
        return CABLE_TRANSFER_RATE.get();
    }

    /** Whether the sorter pushes un-windable items into an adjacent non-MC3DPrint inventory. */
    public static boolean sorterRejectRouting() {
        return SORTER_REJECT_ROUTING.get();
    }

    /** Items routed per sorter per tick, defensively clamped to [1,64] at read. */
    public static int sorterMaxPerTick() {
        return Math.max(1, Math.min(64, SORTER_MAX_PER_TICK.get()));
    }

    private MC3DPrintConfig() {}
}
