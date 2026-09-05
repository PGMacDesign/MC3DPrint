package com.pgmacdesign.mc3dprint.machine.terminal;

import java.util.Locale;

/**
 * Compact filament totals for places with no room for the digits.
 *
 * <p>Deliberately free of any Minecraft type so it can be unit tested: it used to live on the
 * client screen, where loading the class at all drags in the whole GUI stack.
 */
public final class FuFormat {

    private static final String[] UNITS = {"k", "M", "B", "T", "Q"};

    private FuFormat() {}

    /**
     * A filament total in at most four characters: {@code 812}, {@code 4.2k}, {@code 147M},
     * {@code 2.1B}.
     *
     * <p>The previous version stopped at millions, so a full set of high-tier spools rendered as
     * "2147M" and ran straight through its own tier label in the rail. This scales to
     * quadrillions, which no total can outgrow, and keeps one decimal only below ten where it is
     * the difference between "2B" and "2.1B".
     */
    public static String abbreviate(long fu) {
        if (fu < 0L) {
            return "0";
        }
        if (fu < 1_000L) {
            return Long.toString(fu);
        }
        double scaled = fu / 1_000.0D;
        int unit = 0;
        // 999.5 rather than 1000: 999_999 has to read as "1.0M" instead of rounding up to a
        // five-character "1000k", which is exactly the overflow this exists to prevent.
        while (scaled >= 999.5D && unit < UNITS.length - 1) {
            scaled /= 1_000.0D;
            unit++;
        }
        String number = scaled < 9.95D
                ? String.format(Locale.ROOT, "%.1f", scaled)
                : Long.toString(Math.round(scaled));
        return number + UNITS[unit];
    }
}
