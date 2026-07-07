package com.pgmacdesign.mc3dprint.machine;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * The 8 machine tiers. Numbers are the balancing-table placeholders; runtime
 * values come from config (one block per tier), these are the defaults.
 *
 * T1-T4 are single craftable blocks; T5-T8 are multiblock structures.
 * {@code maxFootprint} 0 means items only (no structure printing).
 */
public enum MachineTier implements StringRepresentable {
    //  tier footprint spool  eff   rf/blk t/blk  itemTicks buffer       maxRecv
    T1(1,   0,        1,     0.50, 100,   40,    80,       50_000,      1_000),
    T2(2,   0,        2,     0.55, 90,    20,    60,       100_000,     2_000),
    T3(3,   3,        3,     0.65, 75,    10,    40,       250_000,     4_000),
    T4(4,   5,        4,     0.75, 60,    5,     20,       600_000,     8_000),
    T5(5,   9,        4,     0.85, 45,    3,     10,       1_500_000,   16_000),
    T6(6,   15,       4,     0.92, 30,    2,     8,        4_000_000,   32_000),
    T7(7,   33,       4,     0.98, 15,    1,     5,        10_000_000,  64_000),
    T8(8,   51,       4,     0.99, 10,    1,     4,        25_000_000,  128_000);

    private final int number;
    private final int maxFootprint;
    private final int spoolSlots;
    private final double defaultEfficiency;
    private final int defaultRfPerBlock;
    private final int defaultTicksPerBlock;
    private final int defaultItemPrintTicks;
    private final int defaultEnergyBuffer;
    private final int defaultMaxReceive;

    MachineTier(int number, int maxFootprint, int spoolSlots, double defaultEfficiency,
                int defaultRfPerBlock, int defaultTicksPerBlock, int defaultItemPrintTicks,
                int defaultEnergyBuffer, int defaultMaxReceive) {
        this.number = number;
        this.maxFootprint = maxFootprint;
        this.spoolSlots = spoolSlots;
        this.defaultEfficiency = defaultEfficiency;
        this.defaultRfPerBlock = defaultRfPerBlock;
        this.defaultTicksPerBlock = defaultTicksPerBlock;
        this.defaultItemPrintTicks = defaultItemPrintTicks;
        this.defaultEnergyBuffer = defaultEnergyBuffer;
        this.defaultMaxReceive = defaultMaxReceive;
    }

    public int number() {
        return number;
    }

    /** Config DEFAULT only — runtime checks must read {@code MC3DPrintConfig.maxFootprint(tier)}. */
    public int maxFootprint() {
        return maxFootprint;
    }

    public int spoolSlots() {
        return spoolSlots;
    }

    public int upgradeSlots() {
        // Slots scale with tier, but T7 jumps straight to the full 8 (so the top *visible*
        // tier gets the complete upgrade column without needing Draconic). T8 is the hidden
        // Draconic surprise and also gets 8. Lower tiers = their tier number.
        return number >= 7 ? 8 : number;
    }

    public boolean isMultiblock() {
        return number >= 5;
    }

    public double defaultEfficiency() {
        return defaultEfficiency;
    }

    public int defaultRfPerBlock() {
        return defaultRfPerBlock;
    }

    public int defaultTicksPerBlock() {
        return defaultTicksPerBlock;
    }

    public int defaultItemPrintTicks() {
        return defaultItemPrintTicks;
    }

    public int defaultEnergyBuffer() {
        return defaultEnergyBuffer;
    }

    public int defaultMaxReceive() {
        return defaultMaxReceive;
    }

    public static MachineTier byNumber(int number) {
        return values()[Math.max(1, Math.min(8, number)) - 1];
    }

    public static final Codec<MachineTier> CODEC = StringRepresentable.fromEnum(MachineTier::values);

    @Override
    public String getSerializedName() {
        return name();
    }
}
