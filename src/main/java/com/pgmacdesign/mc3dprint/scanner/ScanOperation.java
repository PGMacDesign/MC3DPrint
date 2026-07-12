package com.pgmacdesign.mc3dprint.scanner;

import com.pgmacdesign.mc3dprint.compat.BeData;
import com.pgmacdesign.mc3dprint.compat.NbtCompat;

import com.pgmacdesign.mc3dprint.blueprint.Blueprint;
import com.pgmacdesign.mc3dprint.blueprint.BlueprintBlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

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
                // Anti-dupe parity with the entity path (below): strip stored container contents
                // so printing a player-scanned disc can't restore (dupe) the items for the price
                // of the container block alone.
                CompoundTag data = strippedOfContents(level, pos, state, saveBlockEntity(level, blockEntity));
                builder.blockEntity(localX, localY, localZ, data);
            }
        }

        // Decorative entities live in a separate system from blocks/block-entities,
        // so capture them explicitly. Position is stored blueprint-local (continuous);
        // Pos/UUID are stripped (pos carried separately, fresh UUID assigned at spawn).
        AABB region = new AABB(min.getX(), min.getY(), min.getZ(),
                max.getX() + 1, max.getY() + 1, max.getZ() + 1);
        for (Entity entity : level.getEntities((Entity) null, region, ScanOperation::isCapturable)) {
            //? if >=1.21.5 {
            /*net.minecraft.world.level.storage.TagValueOutput entityOut =
                    net.minecraft.world.level.storage.TagValueOutput.createWithContext(
                            net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess());
            boolean saved = entity.save(entityOut);
            CompoundTag nbt = entityOut.buildResult();
            if (!saved) {
                continue; // passenger / unsaveable
            }
            *///?} else {
            CompoundTag nbt = new CompoundTag();
            if (!entity.save(nbt)) {
                continue; // passenger / unsaveable
            }
            //?}
            nbt.remove("Pos");
            nbt.remove("UUID");
            nbt.remove("Motion");      // spawn at rest (carts/boats don't drift)
            nbt.remove("Passengers");  // strip dangling references to uncaptured riders
            nbt.remove("Leash");       // and to uncaptured leash holders
            // Hanging entities (frames/paintings) attach to an absolute block via TileX/Y/Z —
            // store it blueprint-local so the print can re-anchor it at any location.
            if (nbt.contains("TileX")) {
                nbt.putInt("TileX", NbtCompat.getInt(nbt, "TileX") - min.getX());
                nbt.putInt("TileY", NbtCompat.getInt(nbt, "TileY") - min.getY());
                nbt.putInt("TileZ", NbtCompat.getInt(nbt, "TileZ") - min.getZ());
            }
            builder.entity(
                    entity.getX() - min.getX(),
                    entity.getY() - min.getY(),
                    entity.getZ() - min.getZ(),
                    nbt);
        }
        return builder.build();
    }

    private static CompoundTag saveBlockEntity(Level level, BlockEntity blockEntity) {
        //? if >=1.21.5 {
        /*net.minecraft.world.level.storage.TagValueOutput beOut =
                net.minecraft.world.level.storage.TagValueOutput.createWithContext(
                        net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess());
        blockEntity.saveWithId(beOut);
        return beOut.buildResult();
        *///?} else {
        return blockEntity.saveWithId(level.registryAccess());
        //?}
    }

    /**
     * Returns {@code data} with any stored contents removed. Rebuilds a throwaway block entity
     * from the state, loads the captured tag, clears it via {@link net.minecraft.world.Clearable}
     * (vanilla containers + most modded storage implement it), and re-saves. Structural/cosmetic
     * NBT (sign text, banners, note blocks, skulls) survives the round-trip untouched.
     */
    private static CompoundTag strippedOfContents(Level level, BlockPos pos, BlockState state, CompoundTag data) {
        if (!(state.getBlock() instanceof net.minecraft.world.level.block.EntityBlock entityBlock)) {
            return data;
        }
        BlockEntity temp = entityBlock.newBlockEntity(pos, state);
        if (temp == null) {
            return data;
        }
        BeData.loadInto(temp, data, level.registryAccess());
        if (temp instanceof net.minecraft.world.Clearable clearable) {
            clearable.clearContent();
        }
        return saveBlockEntity(level, temp);
    }

    /**
     * Decorative entities we capture/reprint — explicitly NOT mobs/players/items.
     * Regular minecart/boat only; their container variants (chest_minecart,
     * chest_boat, …) are distinct EntityTypes and so excluded for free.
     */
    private static boolean isCapturable(Entity entity) {
        EntityType<?> type = entity.getType();
        return type == EntityType.ARMOR_STAND
                || type == EntityType.ITEM_FRAME
                || type == EntityType.GLOW_ITEM_FRAME
                || type == EntityType.PAINTING
                || type == EntityType.MINECART
                //? if >=1.21.5 {
                /*|| entity instanceof net.minecraft.world.entity.vehicle.Boat;
                *///?} else {
                || type == EntityType.BOAT;
                //?}
    }
}
