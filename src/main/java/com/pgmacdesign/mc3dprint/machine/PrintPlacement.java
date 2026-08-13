package com.pgmacdesign.mc3dprint.machine;

import com.pgmacdesign.mc3dprint.blueprint.PrintOrientation;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Where a blueprint was laid down: everything needed to recompute the exact world position
 * of every cell it printed, without keeping the blueprint itself in memory.
 *
 * <p>Recorded when a print starts and kept after it ends, so Deconstruct Mode can offer an
 * <em>un-print</em> of the last build (see {@code PrinterBlockEntity.armLastPrintRegion}).
 * A partial print counts: the placement is known the moment the job is created, so cancelling
 * halfway still leaves something to undo.
 */
public record PrintPlacement(UUID blueprintId, BlockPos origin, PrintOrientation orientation,
                             BlockPos size, boolean oreSalted, Set<BlockPos> preexisting) {

    public PrintPlacement {
        preexisting = Set.copyOf(preexisting);
    }

    public static PrintPlacement of(PrintJob job, boolean oreSalted) {
        return new PrintPlacement(job.blueprintId(), job.origin(), job.orientation(), job.size(),
                oreSalted, Set.of());
    }

    /**
     * The same placement, plus the cells the print FOUND already correct and therefore never
     * placed (repair mode fast-forwards those at zero cost). They're the blueprint's blocks by
     * coincidence, not this machine's work, so an un-print has to leave them: printing a stone
     * build into a stone hillside must not let the un-print eat the hill.
     *
     * <p>Empty for the normal case of printing into air, so the usual cost is nothing.
     */
    public PrintPlacement withPreexisting(Set<BlockPos> cells) {
        return new PrintPlacement(blueprintId, origin, orientation, size, oreSalted, cells);
    }

    public BoundingBox box() {
        return BoundingBox.fromCorners(origin,
                origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Blueprint", blueprintId);
        tag.put("Origin", NbtUtils.writeBlockPos(origin));
        tag.putByte("Rotation", (byte) orientation.rotation().ordinal());
        tag.putByte("Mirror", (byte) orientation.mirror().ordinal());
        tag.put("PlacementSize", NbtUtils.writeBlockPos(size));
        tag.putBoolean("OreSalted", oreSalted);
        if (!preexisting.isEmpty()) {
            long[] packed = new long[preexisting.size()];
            int i = 0;
            for (BlockPos pos : preexisting) {
                packed[i++] = pos.asLong();
            }
            tag.putLongArray("Preexisting", packed);
        }
        return tag;
    }

    public static Optional<PrintPlacement> load(CompoundTag tag) {
        if (!tag.hasUUID("Blueprint") || !tag.contains("Origin") || !tag.contains("PlacementSize")) {
            return Optional.empty();
        }
        return Optional.of(new PrintPlacement(tag.getUUID("Blueprint"),
                NbtUtils.readBlockPos(tag.getCompound("Origin")),
                new PrintOrientation(
                        Rotation.values()[Math.floorMod(tag.getByte("Rotation"),
                                Rotation.values().length)],
                        Mirror.values()[Math.floorMod(tag.getByte("Mirror"),
                                Mirror.values().length)]),
                NbtUtils.readBlockPos(tag.getCompound("PlacementSize")),
                tag.getBoolean("OreSalted"), unpack(tag)));
    }

    private static Set<BlockPos> unpack(CompoundTag tag) {
        long[] packed = tag.getLongArray("Preexisting");
        if (packed.length == 0) {
            return Set.of();
        }
        Set<BlockPos> out = new HashSet<>(packed.length);
        for (long value : packed) {
            out.add(BlockPos.of(value));
        }
        return out;
    }
}
