package com.pgmacdesign.mc3dprint.machine.terminal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The terminal's tier rail has ~34px for a label and a number, so the number has to stay short. */
class FuFormatTest {

    @Test
    void scalesPastMillions() {
        assertEquals("812", FuFormat.abbreviate(812));
        assertEquals("4.2k", FuFormat.abbreviate(4_200));
        assertEquals("147M", FuFormat.abbreviate(147_000_000));
        // The value that overflowed the rail: an int-saturated filament total.
        assertEquals("2.1B", FuFormat.abbreviate(2_147_483_647L));
        assertEquals("6.5Q", FuFormat.abbreviate(6_500_000_000_000_000L));
    }

    @Test
    void roundingNeverGrowsAnExtraDigit() {
        // 999_999 must not round to "1000k", which is the 5-character case that broke the layout.
        assertEquals("1.0M", FuFormat.abbreviate(999_999));
        assertEquals("999k", FuFormat.abbreviate(999_499));
    }

    @Test
    void neverExceedsFourCharacters() {
        for (long v = 0; v < 10_000L; v++) {
            assertTrue(FuFormat.abbreviate(v).length() <= 4, "at " + v);
        }
        for (long v = 1L; v <= 1_000_000_000_000_000_000L; v *= 3L) {
            String s = FuFormat.abbreviate(v);
            assertTrue(s.length() <= 4, v + " rendered as " + s);
        }
        assertTrue(FuFormat.abbreviate(Long.MAX_VALUE).length() <= 5,
                "even an absurd total stays short: " + FuFormat.abbreviate(Long.MAX_VALUE));
    }

    @Test
    void negativesDoNotRenderAsGarbage() {
        assertEquals("0", FuFormat.abbreviate(-1));
    }
}
