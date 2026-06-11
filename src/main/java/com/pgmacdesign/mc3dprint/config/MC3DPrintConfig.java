package com.pgmacdesign.mc3dprint.config;

import net.minecraftforge.common.ForgeConfigSpec;

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
        T1_MAX_ENERGY_RECEIVE = builder
                .comment("Max RF accepted per tick from cables")
                .defineInRange("maxEnergyReceive", 1_000, 1, Integer.MAX_VALUE);
        builder.pop();

        SPEC = builder.build();
    }

    private MC3DPrintConfig() {}
}
