package com.pgmacdesign.mc3dprint.fu;

import com.pgmacdesign.mc3dprint.config.MC3DPrintConfig;

/**
 * Tiered filament economics. FU is denominated by tier (a tier-S spool holds
 * tier-S FU); one tier-N unit is worth {@code ratio} tier-(N-1) units. All
 * math runs in "base units" (tier-1 FU) as longs:
 *
 * <pre>base = amount × ratio^(tier-1)</pre>
 *
 * Down-converting (high-tier FU on a cheap job) is generous; up-converting
 * (cheap FU on a high-tier job) costs ratio^(tierGap) — printing a nether
 * star from cobblestone FU is possible, just brutally expensive. That, not a
 * hard wall, is what keeps the economy honest. Ratio 1 restores universal FU.
 *
 * Methods take the ratio as a parameter so the math is unit-testable without
 * a loaded config; {@link #ratio()} reads the live config value.
 */
public final class FuConversion {

    public static int ratio() {
        return MC3DPrintConfig.FILAMENT_CONVERSION_RATIO.get();
    }

    /** ratio^(tier-1) — the base-unit worth of one FU at {@code tier}. */
    public static long unitWorth(int tier, int ratio) {
        long worth = 1;
        for (int i = 1; i < tier; i++) {
            worth *= ratio;
        }
        return worth;
    }

    public static long toBase(long amount, int tier, int ratio) {
        return amount * unitWorth(tier, ratio);
    }

    /** Floor conversion: how many whole tier units the base amount affords. */
    public static long fromBase(long base, int tier, int ratio) {
        return base / unitWorth(tier, ratio);
    }

    /** Tier units needed to cover {@code base} base units, rounding up. */
    public static long fromBaseCeil(long base, int tier, int ratio) {
        long worth = unitWorth(tier, ratio);
        return (base + worth - 1) / worth;
    }

    public static int clampToInt(long value) {
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, value));
    }

    private FuConversion() {}
}
