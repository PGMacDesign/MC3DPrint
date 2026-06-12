package com.pgmacdesign.mc3dprint.fu;

import com.pgmacdesign.mc3dprint.config.MC3DPrintConfig;

/**
 * Tiered filament economics. FU is denominated by tier (a tier-S spool holds
 * tier-S FU); one tier-N unit is worth {@code ratio} tier-(N-1) units. All
 * math runs in "base units" (tier-1 FU) as longs:
 *
 * <pre>base = amount × ratio^(tier-1)</pre>
 *
 * <b>Hard rule (product requirement): FU never converts UP.</b> At print time
 * down-conversion is allowed — high-tier FU covers lower-tier costs at the
 * compounded ratio, but low-tier FU contributes nothing toward higher-tier
 * costs ({@link #canCover}). At the winder the rule is stricter: a material
 * only winds into a spool of its <em>exact</em> tier ({@link #canWindInto}) —
 * netherite (T5) needs a T5 spool, cobblestone (T1) needs a T1 spool. Together
 * these stop cobblestone farming from ever reaching a high-tier spool.
 *
 * Methods take the ratio as a parameter so the math is unit-testable without
 * a loaded config; {@link #ratio()} reads the live config value.
 */
public final class FuConversion {

    public static int ratio() {
        return MC3DPrintConfig.FILAMENT_CONVERSION_RATIO.get();
    }

    /** Down-only: a spool can pay costs at or below its own tier, never above. */
    public static boolean canCover(int spoolTier, int costTier) {
        return spoolTier >= costTier;
    }

    /** Exact-tier rule: a material only winds into a spool of its own tier. */
    public static boolean canWindInto(int materialTier, int spoolTier) {
        return spoolTier == materialTier;
    }

    /**
     * FU a material deposits into a spool. Winding callers are gated by
     * {@link #canWindInto} (equal tiers), so this returns the material's FU
     * 1:1; the general tier math is kept for the shared exchange primitives.
     */
    public static long windYield(int fu, int materialTier, int spoolTier, int ratio) {
        return fromBase(toBase(fu, materialTier, ratio), spoolTier, ratio);
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
