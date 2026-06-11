package com.pgmacdesign.mc3dprint.config;

import com.pgmacdesign.mc3dprint.fu.FuValueRegistry;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/**
 * Gameplay tuning values, all pack-maker exposed per the design doc.
 * Values are conservative placeholders from the balancing table —
 * "easier to buff than nerf post-release."
 */
public final class MC3DPrintConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue T1_ENERGY_BUFFER;
    public static final ForgeConfigSpec.IntValue T1_ENERGY_PER_TICK;
    public static final ForgeConfigSpec.IntValue T1_ITEM_PRINT_TICKS;
    public static final ForgeConfigSpec.IntValue T1_MAX_ENERGY_RECEIVE;
    public static final ForgeConfigSpec.IntValue T1_SCANNER_MAX_EDGE;
    public static final ForgeConfigSpec.IntValue T1_RF_PER_BLOCK;
    public static final ForgeConfigSpec.IntValue T1_TICKS_PER_BLOCK;
    public static final ForgeConfigSpec.IntValue PRINT_HISTORY_SIZE;
    public static final ForgeConfigSpec.DoubleValue T1_EFFICIENCY;
    public static final ForgeConfigSpec.IntValue UNKNOWN_BLOCK_FU;
    public static final ForgeConfigSpec.IntValue WINDER_RF_PER_ITEM;
    public static final ForgeConfigSpec.IntValue WINDER_TICKS_PER_ITEM;
    public static final ForgeConfigSpec.IntValue WINDER_ENERGY_BUFFER;
    public static final ForgeConfigSpec.IntValue WINDER_MAX_ENERGY_RECEIVE;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> FU_VALUES;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("Tier 1 Printer").push("tier1");
        T1_ENERGY_BUFFER = builder
                .comment("Internal RF buffer capacity")
                .defineInRange("energyBuffer", 50_000, 1_000, Integer.MAX_VALUE);
        T1_ENERGY_PER_TICK = builder
                .comment("RF consumed per tick while printing")
                .defineInRange("energyPerTick", 40, 1, Integer.MAX_VALUE);
        T1_ITEM_PRINT_TICKS = builder
                .comment("Ticks to print one item in Item Mode (20 ticks = 1 second)")
                .defineInRange("itemPrintTicks", 80, 1, Integer.MAX_VALUE);
        T1_RF_PER_BLOCK = builder
                .comment("RF consumed per block placed in Blueprint Mode (balancing table: 100 RF at T1)")
                .defineInRange("rfPerBlock", 100, 1, Integer.MAX_VALUE);
        T1_TICKS_PER_BLOCK = builder
                .comment("Ticks between block placements in Blueprint Mode (balancing table: 0.5 blocks/sec at T1)")
                .defineInRange("ticksPerBlock", 40, 1, Integer.MAX_VALUE);
        T1_EFFICIENCY = builder
                .comment("Matter efficiency: FU cost = base / efficiency (balancing table: 50% at T1)")
                .defineInRange("efficiency", 0.5, 0.01, 1.0);
        T1_MAX_ENERGY_RECEIVE = builder
                .comment("Max RF accepted per tick from cables")
                .defineInRange("maxEnergyReceive", 1_000, 1, Integer.MAX_VALUE);
        builder.pop();

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

        builder.comment("General").push("general");
        PRINT_HISTORY_SIZE = builder
                .comment("Entries kept in each printer's job history log")
                .defineInRange("printHistorySize", 10, 0, 100);
        UNKNOWN_BLOCK_FU = builder
                .comment("FU cost per printed block whose item has no configured FU value")
                .defineInRange("unknownBlockFu", 3, 0, Integer.MAX_VALUE);
        FU_VALUES = builder
                .comment("Filament Unit values: '<item_or_#tag>=<fu>@<min_tier>'. Symmetric: wind yield == print cost (before efficiency).")
                .defineListAllowEmpty("fuValues", FuValueRegistry.defaultEntries(),
                        o -> o instanceof String s && s.contains("=") && s.contains("@"));
        builder.pop();

        SPEC = builder.build();
    }

    private MC3DPrintConfig() {}
}
