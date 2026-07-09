package com.pgmacdesign.mc3dprint.machine;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeconstructJobTest {

    @Test
    void iterationIsTopDownAndCoversEveryPositionOnce() {
        DeconstructJob job = new DeconstructJob(new BlockPos(10, 60, -5), new BlockPos(3, 2, 2));
        assertEquals(12, job.totalPositions());

        // Top layer (y=61) fully before the bottom layer (y=60) — supported blocks
        // come off before their supports.
        for (int i = 0; i < 6; i++) {
            assertEquals(61, job.posFor(i).getY(), "index " + i + " should be top layer");
        }
        for (int i = 6; i < 12; i++) {
            assertEquals(60, job.posFor(i).getY(), "index " + i + " should be bottom layer");
        }

        Set<BlockPos> seen = new HashSet<>();
        for (int i = 0; i < job.totalPositions(); i++) {
            assertTrue(seen.add(job.posFor(i)), "duplicate position at index " + i);
        }
        assertEquals(12, seen.size());

        // deterministic within a layer: Z then X ascending
        assertEquals(new BlockPos(10, 61, -5), job.posFor(0));
        assertEquals(new BlockPos(11, 61, -5), job.posFor(1));
        assertEquals(new BlockPos(12, 61, -5), job.posFor(2));
        assertEquals(new BlockPos(10, 61, -4), job.posFor(3));
    }

    @Test
    void saveLoadRoundTripPreservesProgress() {
        DeconstructJob job = new DeconstructJob(new BlockPos(1, 2, 3), new BlockPos(2, 2, 2));
        job.advance();
        job.recordRemoval(7);
        job.advance();

        DeconstructJob loaded = DeconstructJob.load(job.save());
        assertEquals(job.min(), loaded.min());
        assertEquals(job.size(), loaded.size());
        assertEquals(2, loaded.progress());
        assertEquals(1, loaded.removed());
        assertEquals(7, loaded.creditedFu());
        // resumed iteration continues at the SAME position — nothing reprocessed
        assertEquals(job.posFor(2), loaded.posFor(loaded.progress()));
    }

    @Test
    void completesExactlyAtVolume() {
        DeconstructJob job = new DeconstructJob(BlockPos.ZERO, new BlockPos(2, 1, 2));
        for (int i = 0; i < 4; i++) {
            assertTrue(!job.isComplete());
            job.advance();
        }
        assertTrue(job.isComplete());
    }
}
