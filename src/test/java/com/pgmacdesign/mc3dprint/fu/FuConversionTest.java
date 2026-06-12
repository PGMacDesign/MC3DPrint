package com.pgmacdesign.mc3dprint.fu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FuConversionTest {
    private static final int RATIO = 4;

    @Test
    void unitWorthGrowsByRatioPerTier() {
        assertEquals(1, FuConversion.unitWorth(1, RATIO));
        assertEquals(4, FuConversion.unitWorth(2, RATIO));
        assertEquals(64, FuConversion.unitWorth(4, RATIO));
        assertEquals(16_384, FuConversion.unitWorth(8, RATIO));
    }

    @Test
    void ratioOneIsUniversalFu() {
        assertEquals(1, FuConversion.unitWorth(8, 1));
        assertEquals(123, FuConversion.toBase(123, 7, 1));
        assertEquals(123, FuConversion.fromBase(123, 3, 1));
    }

    @Test
    void downConversionIsGenerous() {
        // 10 tier-3 FU toward a tier-1 cost: 10 * 16 = 160
        long base = FuConversion.toBase(10, 3, RATIO);
        assertEquals(160, FuConversion.fromBase(base, 1, RATIO));
    }

    @Test
    void upConversionIsHardBlocked() {
        // print side: down-only — a spool covers costs at or below its own tier
        assertFalse(FuConversion.canCover(1, 2));
        assertFalse(FuConversion.canCover(3, 4));
        assertTrue(FuConversion.canCover(2, 2));
        assertTrue(FuConversion.canCover(7, 1));

        // winder side: exact tier match only — no up, and no lossy down either
        assertFalse(FuConversion.canWindInto(1, 2)); // T1 material into T2 spool: never (up)
        assertFalse(FuConversion.canWindInto(2, 1)); // T2 material into T1 spool: never (exact-tier rule)
        assertTrue(FuConversion.canWindInto(1, 1));
        assertTrue(FuConversion.canWindInto(4, 4));
    }

    @Test
    void tierExchangeMathIsExact() {
        // winding is exact-tier in-game (windYield called with equal tiers → 1:1);
        // the general exchange below backs the print-side down-conversion math
        assertEquals(20, FuConversion.windYield(20, 2, 2, RATIO)); // iron into a T2 spool: 1:1
        assertEquals(50, FuConversion.windYield(50, 4, 4, RATIO)); // a T4 gem (emerald) into a T4 spool: 1:1
        assertEquals(80, FuConversion.windYield(20, 2, 1, RATIO)); // exchange primitive: 20 T2 = 80 T1
        assertEquals(800, FuConversion.windYield(50, 4, 2, RATIO)); // exchange primitive: 50 T4 = 800 T2
    }

    @Test
    void ceilCoversCostWithAtMostOneExtraUnit() {
        // covering 5 base units from a tier-2 spool (worth 4 each) takes 2 units
        assertEquals(2, FuConversion.fromBaseCeil(5, 2, RATIO));
        assertEquals(1, FuConversion.fromBaseCeil(4, 2, RATIO));
        assertEquals(1, FuConversion.fromBaseCeil(1, 2, RATIO));
        assertEquals(0, FuConversion.fromBaseCeil(0, 2, RATIO));
    }

    @Test
    void largeValuesDoNotOverflow() {
        // creative-scale: 5M tier-8 FU in base units needs a long
        long base = FuConversion.toBase(5_000_000, 8, RATIO);
        assertEquals(81_920_000_000L, base);
        assertEquals(Integer.MAX_VALUE, FuConversion.clampToInt(base));
    }
}
