package com.pgmacdesign.mc3dprint.scanner;

import com.pgmacdesign.mc3dprint.blueprint.Blueprint;
import com.pgmacdesign.mc3dprint.blueprint.BlueprintBlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Captures a world region into a blueprint. Air imports as empty (the printer
 * never places air; print areas must be pre-cleared per the design doc).
 * Block entity data is captured with {@code saveWithId} — position comes from
 * the blueprint-local coordinates.
 */
public final class ScanOperation {
    private ScanOperation() {}

    public static Blueprint capture(Level level, BlockPos cornerA, BlockPos cornerB, String name) {
        BlockPos min = new BlockPos(
                Math.min(cornerA.getX(), cornerB.getX()),
                Math.min(cornerA.getY(), cornerB.getY()),
                Math.min(cornerA.getZ(), cornerB.getZ()));
        BlockPos max = new BlockPos(
                Math.max(cornerA.getX(), cornerB.getX()),
                Math.max(cornerA.getY(), cornerB.getY()),
                Math.max(cornerA.getZ(), cornerB.getZ()));

        Blueprint.Builder builder = Blueprint.builder(name,
                max.getX() - min.getX() + 1,
                max.getY() - min.getY() + 1,
                max.getZ() - min.getZ() + 1);

        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            int localX = pos.getX() - min.getX();
            int localY = pos.getY() - min.getY();
            int localZ = pos.getZ() - min.getZ();
            builder.set(localX, localY, localZ, BlueprintBlockState.fromBlockState(state));

            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity != null) {
                CompoundTag data = blockEntity.saveWithId();
                builder.blockEntity(localX, localY, localZ, data);
            }
        }
        return builder.build();
    }
}
