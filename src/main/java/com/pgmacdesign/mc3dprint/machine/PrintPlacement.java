package com.pgmacdesign.mc3dprint.machine;

import com.pgmacdesign.mc3dprint.blueprint.PrintOrientation;
import com.pgmacdesign.mc3dprint.compat.NbtCompat;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.Optional;
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
                             BlockPos size, boolean oreSalted) {

    public static PrintPlacement of(PrintJob job, boolean oreSalted) {
        return new PrintPlacement(job.blueprintId(), job.origin(), job.orientation(), job.size(),
                oreSalted);
    }

    public BoundingBox box() {
        return BoundingBox.fromCorners(origin,
                origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        NbtCompat.putUUID(tag, "Blueprint", blueprintId);
        NbtCompat.putBlockPos(tag, "Origin", origin);
        tag.putByte("Rotation", (byte) orientation.rotation().ordinal());
        tag.putByte("Mirror", (byte) orientation.mirror().ordinal());
        NbtCompat.putBlockPos(tag, "PlacementSize", size);
        tag.putBoolean("OreSalted", oreSalted);
        return tag;
    }

    public static Optional<PrintPlacement> load(CompoundTag tag) {
        Optional<UUID> id = NbtCompat.getUUID(tag, "Blueprint");
        Optional<BlockPos> origin = NbtCompat.getBlockPos(tag, "Origin");
        Optional<BlockPos> size = NbtCompat.getBlockPos(tag, "PlacementSize");
        if (id.isEmpty() || origin.isEmpty() || size.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new PrintPlacement(id.get(), origin.get(),
                new PrintOrientation(
                        Rotation.values()[Math.floorMod(NbtCompat.getByte(tag, "Rotation"),
                                Rotation.values().length)],
                        Mirror.values()[Math.floorMod(NbtCompat.getByte(tag, "Mirror"),
                                Mirror.values().length)]),
                size.get(), NbtCompat.getBoolean(tag, "OreSalted")));
    }
}
