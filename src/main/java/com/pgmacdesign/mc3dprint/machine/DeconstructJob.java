package com.pgmacdesign.mc3dprint.machine;

import com.pgmacdesign.mc3dprint.compat.NbtCompat;
import com.pgmacdesign.mc3dprint.machine.resin.ResinEffects;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * A persistent deconstruct job over a fixed world region. Progress is an index
 * into a deterministic TOP-DOWN order (Y descending, then Z, then X ascending) —
 * supported blocks come off before their supports, the reverse of print order.
 * Resume after restart/power loss is "skip the first N", same as {@link PrintJob};
 * a processed index is never revisited, so no position is removed or credited twice.
 *
 * <p>A job may carry an <b>un-print mask</b>: the placement of a past print plus the
 * world positions and block types that print put down. With a mask the job still walks
 * the same box, but only positions the print itself filled (and which still hold that
 * block) are eligible. Terrain the build sits on, and anything the player added inside
 * the footprint afterwards, are skipped in place rather than eaten.
 */
public final class DeconstructJob {
    private final BlockPos min;   // world min corner of the region
    private final BlockPos size;
    private final int totalPositions;
    private int progress;         // positions processed (removed OR skipped)
    private int removed;          // blocks actually removed
    private long creditedFu;      // tier-unit FU credited across all tiers (display/history)

    /** Set only on an un-print; persisted so the mask can be rebuilt after a restart. */
    @Nullable
    private final PrintPlacement placement;
    /**
     * World position -> the block that print put there. Transient: rebuilt from
     * {@link #placement} by the machine, which owns blueprint loading.
     */
    @Nullable
    private Map<BlockPos, Block> mask;

    public DeconstructJob(BlockPos min, BlockPos size) {
        this(min, size, null);
    }

    public DeconstructJob(BlockPos min, BlockPos size, @Nullable PrintPlacement placement) {
        this.min = min;
        this.size = size;
        this.totalPositions = size.getX() * size.getY() * size.getZ();
        this.placement = placement;
    }

    @Nullable
    public PrintPlacement placement() {
        return placement;
    }

    /** True for an un-print job whose mask hasn't been supplied yet (fresh start or reload). */
    public boolean needsMask() {
        return placement != null && mask == null;
    }

    public void setMask(Map<BlockPos, Block> mask) {
        this.mask = mask;
    }

    /**
     * Whether this position is in scope. Unmasked jobs take the whole box. A masked job
     * takes a position only if the print filled it AND it still holds that block, so a
     * replaced or player-added block is left alone.
     */
    public boolean allows(BlockPos pos, BlockState state) {
        if (mask == null) {
            return placement == null; // masked job with no mask yet: consume nothing
        }
        Block expected = mask.get(pos);
        if (expected == null) {
            return false;
        }
        if (state.getBlock() == expected) {
            return true;
        }
        // Ore Salting swapped some stone hosts for random ore at print time, and the roll
        // isn't reproducible from the blueprint. Only a print that actually ran the resin
        // widens the match, and only to the ores pickOre could have produced for THAT host,
        // so a player-placed ore in an unsalted build is still out of scope.
        return placement != null && placement.oreSalted()
                && ResinEffects.isSaltOutputFor(expected, state);
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
        if (placement != null) {
            tag.put("Unprint", placement.save());
        }
        return tag;
    }

    /**
     * Restores a job, or null when this tag describes an un-print we can't reconstruct.
     *
     * <p>Dropping an unreadable placement and carrying on would silently downgrade the job to a
     * whole-box deconstruct, which is the one outcome the mask exists to prevent. Refusing the
     * restore matches what the machine does when the blueprint file itself is gone.
     */
    @Nullable
    public static DeconstructJob load(CompoundTag tag) {
        PrintPlacement placement = null;
        if (NbtCompat.contains(tag, "Unprint")) {
            placement = PrintPlacement.load(NbtCompat.getCompound(tag, "Unprint")).orElse(null);
            if (placement == null) {
                return null;
            }
        }
        DeconstructJob job = new DeconstructJob(
                NbtCompat.getBlockPos(tag, "Min").orElse(BlockPos.ZERO),
                NbtCompat.getBlockPos(tag, "RegionSize").orElse(new BlockPos(1, 1, 1)),
                placement);
        job.progress = Math.max(0, NbtCompat.getInt(tag, "Progress"));
        job.removed = NbtCompat.getInt(tag, "Removed");
        job.creditedFu = NbtCompat.getLong(tag, "CreditedFu");
        return job;
    }
}
