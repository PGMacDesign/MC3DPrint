package com.pgmacdesign.mc3dprint.blueprint;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * A print-time orientation: rotation about Y plus optional mirror. Blueprints
 * are stored unrotated; orientation is applied while iterating placement
 * positions, and the matching {@link Rotation}/{@link Mirror} is applied to
 * each resolved BlockState at placement so vanilla per-block logic
 * (stairs, rails, logs...) handles property rotation.
 *
 * Mirror is applied before rotation, matching vanilla structure placement.
 */
public record PrintOrientation(Rotation rotation, Mirror mirror) {
    public static final PrintOrientation NONE = new PrintOrientation(Rotation.NONE, Mirror.NONE);

    /** Footprint size after orientation: 90°/270° swap X and Z. */
    public BlockPos transformedSize(int sizeX, int sizeY, int sizeZ) {
        return switch (rotation) {
            case CLOCKWISE_90, COUNTERCLOCKWISE_90 -> new BlockPos(sizeZ, sizeY, sizeX);
            default -> new BlockPos(sizeX, sizeY, sizeZ);
        };
    }

    /**
     * Maps a blueprint-local position into oriented-local space. The result is
     * always within [0, transformedSize) — rotations are about the volume
     * center, re-anchored to the min corner.
     */
    public BlockPos transform(BlockPos local, int sizeX, int sizeY, int sizeZ) {
        int x = local.getX();
        int z = local.getZ();

        switch (mirror) {
            case LEFT_RIGHT -> z = sizeZ - 1 - z;   // flip across X axis
            case FRONT_BACK -> x = sizeX - 1 - x;   // flip across Z axis
            case NONE -> {}
        }

        return switch (rotation) {
            case NONE -> new BlockPos(x, local.getY(), z);
            case CLOCKWISE_90 -> new BlockPos(sizeZ - 1 - z, local.getY(), x);
            case CLOCKWISE_180 -> new BlockPos(sizeX - 1 - x, local.getY(), sizeZ - 1 - z);
            case COUNTERCLOCKWISE_90 -> new BlockPos(z, local.getY(), sizeX - 1 - x);
        };
    }

    public PrintOrientation rotated(Rotation by) {
        return new PrintOrientation(rotation.getRotated(by), mirror);
    }

    public PrintOrientation withMirror(Mirror newMirror) {
        return new PrintOrientation(rotation, newMirror);
    }
}
