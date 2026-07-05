package com.pgmacdesign.mc3dprint.machine;

import com.pgmacdesign.mc3dprint.compat.NbtCompat;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * A persistent deconstruct job over a fixed world region. Progress is an index
 * into a deterministic TOP-DOWN order (Y descending, then Z, then X ascending) —
 * supported blocks come off before their supports, the reverse of print order.
 * Resume after restart/power loss is "skip the first N", same as {@link PrintJob};
 * a processed index is never revisited, so no position is removed or credited twice.
 */
public final class DeconstructJob {
    private final BlockPos min;   // world min corner of the region
    private final BlockPos size;
    private final int totalPositions;
    private int progress;         // positions processed (removed OR skipped)
    private int removed;          // blocks actually removed
    private long creditedFu;      // tier-unit FU credited across all tiers (display/history)

    public DeconstructJob(BlockPos min, BlockPos size) {
        this.min = min;
        this.size = size;
        this.totalPositions = size.getX() * size.getY() * size.getZ();
    }

    public BlockPos min() {
        return min;
    }

    public BlockPos size() {
        return size;
    }

    public int totalPositions() {
        return totalPositions;
    }

    public int progress() {
        return progress;
    }

    /** Progress only ever advances (monotonic — the resume/no-reprocess invariant). */
    public void advance() {
        progress++;
    }

    public int removed() {
        return removed;
    }

    public void recordRemoval(int fuCredited) {
        removed++;
        creditedFu += fuCredited;
    }

    public long creditedFu() {
        return creditedFu;
    }

    public boolean isComplete() {
        return progress >= totalPositions;
    }

    /** World position for a progress index: top layer first, Z then X within a layer. */
    public BlockPos posFor(int index) {
        int layerArea = size.getX() * size.getZ();
        int yFromTop = index / layerArea;
        int inLayer = index % layerArea;
        return min.offset(inLayer % size.getX(),
                size.getY() - 1 - yFromTop,
                inLayer / size.getX());
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        NbtCompat.putBlockPos(tag, "Min", min);
        NbtCompat.putBlockPos(tag, "RegionSize", size);
        tag.putInt("Progress", progress);
        tag.putInt("Removed", removed);
        tag.putLong("CreditedFu", creditedFu);
        return tag;
    }

    public static DeconstructJob load(CompoundTag tag) {
        DeconstructJob job = new DeconstructJob(
                NbtCompat.getBlockPos(tag, "Min").orElse(BlockPos.ZERO),
                NbtCompat.getBlockPos(tag, "RegionSize").orElse(new BlockPos(1, 1, 1)));
        job.progress = Math.max(0, NbtCompat.getInt(tag, "Progress"));
        job.removed = NbtCompat.getInt(tag, "Removed");
        job.creditedFu = NbtCompat.getLong(tag, "CreditedFu");
        return job;
    }
}
