package com.pgmacdesign.mc3dprint.machine;

import com.pgmacdesign.mc3dprint.compat.NbtCompat;

import com.pgmacdesign.mc3dprint.blueprint.PrintOrientation;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

import java.util.UUID;

/**
 * A persistent structure print job. Progress is the count of blocks already
 * placed (placement order is deterministic: Y, then Z, then X), so resume
 * after restart/power loss is just "skip the first N".
 */
public final class PrintJob {
    private final UUID blueprintId;
    private final String blueprintName;
    private final BlockPos origin; // world position of the oriented min corner
    private final PrintOrientation orientation;
    private final BlockPos size;   // oriented volume size (renderer + zone math without the blueprint)
    private final int totalBlocks;
    private int placed;

    public PrintJob(UUID blueprintId, String blueprintName, BlockPos origin,
                    PrintOrientation orientation, BlockPos size, int totalBlocks) {
        this.blueprintId = blueprintId;
        this.blueprintName = blueprintName;
        this.origin = origin;
        this.orientation = orientation;
        this.size = size;
        this.totalBlocks = totalBlocks;
    }

    public BlockPos size() {
        return size;
    }

    public UUID blueprintId() {
        return blueprintId;
    }

    public String blueprintName() {
        return blueprintName;
    }

    public BlockPos origin() {
        return origin;
    }

    public PrintOrientation orientation() {
        return orientation;
    }

    public int totalBlocks() {
        return totalBlocks;
    }

    public int placed() {
        return placed;
    }

    public void setPlaced(int placed) {
        this.placed = placed;
    }

    public boolean isComplete() {
        return placed >= totalBlocks;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        NbtCompat.putUUID(tag, "Blueprint", blueprintId);
        tag.putString("Name", blueprintName);
        NbtCompat.putBlockPos(tag, "Origin", origin);
        tag.putByte("Rotation", (byte) orientation.rotation().ordinal());
        tag.putByte("Mirror", (byte) orientation.mirror().ordinal());
        NbtCompat.putBlockPos(tag, "JobSize", size);
        tag.putInt("Total", totalBlocks);
        tag.putInt("Placed", placed);
        return tag;
    }

    public static PrintJob load(CompoundTag tag) {
        PrintJob job = new PrintJob(
                NbtCompat.getUUID(tag, "Blueprint").orElseThrow(),
                NbtCompat.getString(tag, "Name"),
                NbtCompat.getBlockPos(tag, "Origin").orElse(BlockPos.ZERO),
                new PrintOrientation(
                        Rotation.values()[Math.floorMod(NbtCompat.getByte(tag, "Rotation"), Rotation.values().length)],
                        Mirror.values()[Math.floorMod(NbtCompat.getByte(tag, "Mirror"), Mirror.values().length)]),
                NbtCompat.getBlockPos(tag, "JobSize").orElse(BlockPos.ZERO),
                NbtCompat.getInt(tag, "Total"));
        job.placed = NbtCompat.getInt(tag, "Placed");
        return job;
    }
}
