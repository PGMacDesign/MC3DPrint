package com.pgmacdesign.mc3dprint.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Mod-internal print zone conflict detection (no external claim mods): two
 * printers may not run jobs whose volumes overlap.
 *
 * In-memory only — the source of truth is each printer's persisted active job,
 * and printers re-claim their zone in {@code onLoad}. Server thread only.
 */
public final class PrintZoneManager {
    private static final Map<ServerLevel, Map<BlockPos, BoundingBox>> ZONES = new WeakHashMap<>();

    private PrintZoneManager() {}

    /** Claims a zone for the printer at {@code owner}. False if it overlaps another printer's zone. */
    public static boolean claim(ServerLevel level, BlockPos owner, BoundingBox box) {
        Map<BlockPos, BoundingBox> zones = ZONES.computeIfAbsent(level, l -> new HashMap<>());
        for (Map.Entry<BlockPos, BoundingBox> entry : zones.entrySet()) {
            if (!entry.getKey().equals(owner) && entry.getValue().intersects(box)) {
                return false;
            }
        }
        zones.put(owner.immutable(), box);
        return true;
    }

    public static void release(ServerLevel level, BlockPos owner) {
        Map<BlockPos, BoundingBox> zones = ZONES.get(level);
        if (zones != null) {
            zones.remove(owner);
        }
    }
}
