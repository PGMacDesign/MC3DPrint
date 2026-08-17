package com.pgmacdesign.mc3dprint.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Reference-counted chunk force-loading for print and deconstruct jobs.
 *
 * <p><b>Why this exists.</b> Vanilla's {@link ServerLevel#setChunkForced} is a single boolean per
 * chunk with no notion of who asked, and NeoForge dropped Forge's ticket-owner API. Machines used
 * to call it directly, so two machines whose job boxes touched the same chunk fought over it: the
 * first to finish unforced the chunk out from under the one still working, its block entity stopped
 * ticking, and the job hung with no error and no way to self-recover. A chunk is 16 blocks, and
 * build offsets reach 32 while a deconstruct region reaches 64, so any print farm with machines
 * side by side could hit it.
 *
 * <p>Holders are tracked as a {@link Set} of owner positions per chunk rather than a counter, so a
 * double-claim or a double-release is a harmless no-op instead of a leak or an underflow. A chunk
 * is forced when its holder set becomes non-empty and released only when the last holder leaves.
 *
 * <p>In-memory and server-thread only, matching {@link PrintZoneManager}: each machine re-claims in
 * {@code onLoad}, so nothing needs to survive a restart.
 */
public final class PrintChunkLoader {

    private static final Map<ServerLevel, Map<ChunkKey, Set<BlockPos>>> HOLDERS = new WeakHashMap<>();

    private PrintChunkLoader() {}

    /**
     * Forces every chunk the job at {@code owner} needs: those its {@code box} covers, plus the
     * machine's own chunk (an offset build or a distant deconstruct region can put the box in
     * different chunks than the machine, and a machine that unloads stops working its region).
     * Replaces any previous claim by the same owner.
     */
    public static void acquire(ServerLevel level, BlockPos owner, BoundingBox box) {
        Set<ChunkKey> wanted = chunksFor(owner, box);
        Map<ChunkKey, Set<BlockPos>> holders = HOLDERS.computeIfAbsent(level, l -> new HashMap<>());
        BlockPos key = owner.immutable();

        // Drop chunks this owner held but no longer needs (a re-claim with a different box).
        release(level, key, wanted);

        for (ChunkKey chunk : wanted) {
            Set<BlockPos> set = holders.computeIfAbsent(chunk, c -> new HashSet<>());
            if (set.add(key) && set.size() == 1) {
                level.setChunkForced(chunk.x(), chunk.z(), true);
            }
        }
    }

    /** Drops every chunk held by {@code owner}, unforcing only those no other job still needs. */
    public static void release(ServerLevel level, BlockPos owner) {
        release(level, owner, Set.of());
    }

    private static void release(ServerLevel level, BlockPos owner, Set<ChunkKey> keep) {
        Map<ChunkKey, Set<BlockPos>> holders = HOLDERS.get(level);
        if (holders == null) {
            return;
        }
        holders.entrySet().removeIf(entry -> {
            if (keep.contains(entry.getKey()) || !entry.getValue().remove(owner)) {
                return false;
            }
            if (entry.getValue().isEmpty()) {
                level.setChunkForced(entry.getKey().x(), entry.getKey().z(), false);
                return true;
            }
            return false;
        });
    }

    /**
     * Chunk coordinates as a plain value key. Neither vanilla spelling survives every node this
     * mod ships on: the packed-long factory is gone from the 26.x line and the coordinate fields
     * are private there, so a two-int record keeps this map off that moving surface entirely.
     */
    private record ChunkKey(int x, int z) {}
    private static Set<ChunkKey> chunksFor(BlockPos owner, BoundingBox box) {
        Set<ChunkKey> chunks = new HashSet<>();
        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
            for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                chunks.add(new ChunkKey(cx, cz));
            }
        }
        chunks.add(new ChunkKey(owner.getX() >> 4, owner.getZ() >> 4));
        return chunks;
    }
}
