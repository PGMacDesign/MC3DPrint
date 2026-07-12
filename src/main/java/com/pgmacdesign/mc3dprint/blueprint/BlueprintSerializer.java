package com.pgmacdesign.mc3dprint.blueprint;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * NBT round-trip for the native blueprint format. There is a single canonical
 * format version: optional features (decorative entities) are carried by key
 * presence ({@link #KEY_ENTITIES}), not a version bump, so the format stays one
 * consistent version with no v1/v2 variants. The reader is version-tolerant
 * (any version ≥ 1 loads, reading whatever keys are present) so a blueprint
 * scanned during the brief format-2 window still loads.
 */
public final class BlueprintSerializer {
    /** The single canonical on-disk format version. */
    public static final int FORMAT_VERSION = 1;

    private static final String KEY_VERSION = "Version";
    private static final String KEY_NAME = "Name";
    private static final String KEY_SIZE = "Size";
    private static final String KEY_PALETTE = "Palette";
    private static final String KEY_BLOCKS = "Blocks";
    private static final String KEY_BLOCK_ENTITIES = "BlockEntities";
    private static final String KEY_ENTITIES = "Entities";
    private static final String KEY_BE_POS = "Pos";
    private static final String KEY_BE_DATA = "Data";

    private BlueprintSerializer() {}

    public static CompoundTag write(Blueprint blueprint) {
        CompoundTag root = new CompoundTag();
        root.putInt(KEY_VERSION, FORMAT_VERSION);
        root.putString(KEY_NAME, blueprint.name());
        root.putIntArray(KEY_SIZE, new int[]{blueprint.sizeX(), blueprint.sizeY(), blueprint.sizeZ()});

        ListTag palette = new ListTag();
        for (BlueprintBlockState state : blueprint.palette()) {
            palette.add(StringTag.valueOf(state.serialize()));
        }
        root.put(KEY_PALETTE, palette);
        root.putIntArray(KEY_BLOCKS, blueprint.rawBlocks().clone());

        // Sort block entities by position so serialization is DETERMINISTIC — the
        // source map is a HashMap, whose iteration order varies per run and would
        // otherwise produce spurious byte diffs on every regen of a signed build.
        ListTag blockEntities = new ListTag();
        List<Map.Entry<BlockPos, CompoundTag>> sortedBe =
                new ArrayList<>(blueprint.blockEntities().entrySet());
        sortedBe.sort(Comparator
                .comparingInt((Map.Entry<BlockPos, CompoundTag> e) -> e.getKey().getX())
                .thenComparingInt(e -> e.getKey().getY())
                .thenComparingInt(e -> e.getKey().getZ()));
        for (Map.Entry<BlockPos, CompoundTag> entry : sortedBe) {
            CompoundTag be = new CompoundTag();
            BlockPos pos = entry.getKey();
            be.putIntArray(KEY_BE_POS, new int[]{pos.getX(), pos.getY(), pos.getZ()});
            be.put(KEY_BE_DATA, entry.getValue().copy());
            blockEntities.add(be);
        }
        root.put(KEY_BLOCK_ENTITIES, blockEntities);

        // Decorative entities, sorted (pos then type) so regen is byte-deterministic.
        ListTag entities = new ListTag();
        List<BlueprintEntity> sortedEntities = new ArrayList<>(blueprint.entities());
        sortedEntities.sort(Comparator
                .comparingDouble(BlueprintEntity::x)
                .thenComparingDouble(BlueprintEntity::y)
                .thenComparingDouble(BlueprintEntity::z)
                .thenComparing(BlueprintEntity::typeId));
        for (BlueprintEntity e : sortedEntities) {
            CompoundTag et = new CompoundTag();
            ListTag pos = new ListTag();
            pos.add(DoubleTag.valueOf(e.x()));
            pos.add(DoubleTag.valueOf(e.y()));
            pos.add(DoubleTag.valueOf(e.z()));
            et.put(KEY_BE_POS, pos);
            et.put(KEY_BE_DATA, e.nbt().copy());
            entities.add(et);
        }
        root.put(KEY_ENTITIES, entities);
        return root;
    }

    public static Blueprint read(CompoundTag root) {
        int version = root.getInt(KEY_VERSION);
        if (version < 1) {
            throw new BlueprintFormatException("Missing or invalid blueprint format version " + version);
        }
        // No per-version branching: optional keys (entities) are read by presence,
        // so any version ≥ 1 loads. New writes are always FORMAT_VERSION.
        int[] size = root.getIntArray(KEY_SIZE);
        if (size.length != 3) {
            throw new BlueprintFormatException("Blueprint Size must be [x, y, z], got length " + size.length);
        }
        // Reject a hostile/corrupt size (negative dims, long-overflow product, over-cap) here as a
        // format error, before it can reach the Blueprint constructor's allocation. Shared helper so
        // the overflow-exact check can't drift from the constructor's.
        long volume = Blueprint.checkedVolume(size[0], size[1], size[2]);
        if (volume < 0) {
            throw new BlueprintFormatException("Blueprint Size out of range: "
                    + size[0] + "x" + size[1] + "x" + size[2]);
        }

        List<BlueprintBlockState> palette = new ArrayList<>();
        for (Tag tag : root.getList(KEY_PALETTE, Tag.TAG_STRING)) {
            palette.add(BlueprintBlockState.parse(tag.getAsString()));
        }

        int[] blocks = root.getIntArray(KEY_BLOCKS).clone();
        if (blocks.length != volume) {
            throw new BlueprintFormatException("Blocks length " + blocks.length
                    + " does not match volume " + volume);
        }
        for (int paletteIndex : blocks) {
            if (paletteIndex != Blueprint.NO_BLOCK && (paletteIndex < 0 || paletteIndex >= palette.size())) {
                throw new BlueprintFormatException("Block palette index " + paletteIndex
                        + " out of range for palette of size " + palette.size());
            }
        }

        Map<BlockPos, CompoundTag> blockEntities = new HashMap<>();
        for (Tag tag : root.getList(KEY_BLOCK_ENTITIES, Tag.TAG_COMPOUND)) {
            CompoundTag be = (CompoundTag) tag;
            int[] pos = be.getIntArray(KEY_BE_POS);
            if (pos.length != 3) {
                throw new BlueprintFormatException("BlockEntity Pos must be [x, y, z]");
            }
            blockEntities.put(new BlockPos(pos[0], pos[1], pos[2]), be.getCompound(KEY_BE_DATA).copy());
        }

        // Entities — optional; absent in older files (getList returns empty), so a
        // blueprint with no decorative entities just loads with an empty list.
        List<BlueprintEntity> entities = new ArrayList<>();
        for (Tag tag : root.getList(KEY_ENTITIES, Tag.TAG_COMPOUND)) {
            CompoundTag et = (CompoundTag) tag;
            ListTag pos = et.getList(KEY_BE_POS, Tag.TAG_DOUBLE);
            if (pos.size() != 3) {
                throw new BlueprintFormatException("Entity Pos must be [x, y, z]");
            }
            entities.add(new BlueprintEntity(pos.getDouble(0), pos.getDouble(1), pos.getDouble(2),
                    et.getCompound(KEY_BE_DATA).copy()));
        }

        return new Blueprint(root.getString(KEY_NAME), size[0], size[1], size[2],
                palette, blocks, blockEntities, entities);
    }
}
