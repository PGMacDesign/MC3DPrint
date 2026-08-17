package com.pgmacdesign.mc3dprint.integration.ae2;

import appeng.api.networking.GridHelper;

/**
 * Compile-time proof that the AE2 API is on this node's classpath, and the seam the rest of the
 * AE2 integration will grow from.
 *
 * <p>Everything in {@code src/ae2/java} is compiled only on nodes that declare {@code ae2_version},
 * because AE2 does not exist for 1.21.2 through 1.21.11 or for 26.2. Code here may reference
 * {@code appeng.*} freely; code in the shared tree may not, and must not name these classes either,
 * since they are absent from most nodes.
 */
public final class Ae2Presence {

    private Ae2Presence() {}

    /** Touches an AE2 API type so a broken dependency fails the build rather than at runtime. */
    public static boolean apiOnClasspath() {
        return GridHelper.class != null;
    }
}
