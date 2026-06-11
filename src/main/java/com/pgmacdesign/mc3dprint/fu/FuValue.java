package com.pgmacdesign.mc3dprint.fu;

/**
 * A material's Filament Unit worth and the minimum machine tier required to
 * process it. Conversion is symmetric by design: winding a diamond yields
 * exactly what printing a diamond costs (before machine efficiency).
 */
public record FuValue(int fu, int tier) {
}
