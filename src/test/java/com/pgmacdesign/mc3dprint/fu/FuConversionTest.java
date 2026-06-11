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
        // product rule: FU never converts up, in either machine
        assertFalse(FuConversion.canCover(1, 2));
        assertFalse(FuConversion.canCover(3, 4));
        assertTrue(FuConversion.canCover(2, 2));
        assertTrue(FuConversion.canCover(7, 1));

        assertFalse(FuConversion.canWindInto(1, 2)); // T1 material into T2 spool: never
        assertTrue(FuConversion.canWindInto(2, 1));  // T2 material down into T1 spool: fine
        assertTrue(FuConversion.canWindInto(4, 4));
    }

    @Test
    void downConversionIsExact() {
        // down-only means division is always exact — no remainders to strand
        assertEquals(80, FuConversion.windYield(20, 2, 1, RATIO)); // iron ingot into T1 spool
        assertEquals(20, FuConversion.windYield(20, 2, 2, RATIO)); // same tier 1:1
        assertEquals(800, FuConversion.windYield(50, 4, 2, RATIO)); // diamond into T2: 50*16
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
