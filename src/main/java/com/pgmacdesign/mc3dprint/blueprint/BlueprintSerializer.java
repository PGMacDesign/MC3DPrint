package com.pgmacdesign.mc3dprint.blueprint;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * NBT round-trip for the native blueprint format. Versioned so the on-disk
 * {@code .blueprint} format can evolve without breaking old files.
 */
public final class BlueprintSerializer {
    public static final int FORMAT_VERSION = 1;

    private static final String KEY_VERSION = "Version";
    private static final String KEY_NAME = "Name";
    private static final String KEY_SIZE = "Size";
    private static final String KEY_PALETTE = "Palette";
    private static final String KEY_BLOCKS = "Blocks";
    private static final String KEY_BLOCK_ENTITIES = "BlockEntities";
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

        ListTag blockEntities = new ListTag();
        for (Map.Entry<BlockPos, CompoundTag> entry : blueprint.blockEntities().entrySet()) {
            CompoundTag be = new CompoundTag();
            BlockPos pos = entry.getKey();
            be.putIntArray(KEY_BE_POS, new int[]{pos.getX(), pos.getY(), pos.getZ()});
            be.put(KEY_BE_DATA, entry.getValue().copy());
            blockEntities.add(be);
        }
        root.put(KEY_BLOCK_ENTITIES, blockEntities);
        return root;
    }

    public static Blueprint read(CompoundTag root) {
        int version = root.getInt(KEY_VERSION);
        if (version != FORMAT_VERSION) {
            throw new BlueprintFormatException("Unsupported blueprint format version " + version
                    + " (this build reads version " + FORMAT_VERSION + ")");
        }
        int[] size = root.getIntArray(KEY_SIZE);
        if (size.length != 3) {
            throw new BlueprintFormatException("Blueprint Size must be [x, y, z], got length " + size.length);
        }

        List<BlueprintBlockState> palette = new ArrayList<>();
        for (Tag tag : root.getList(KEY_PALETTE, Tag.TAG_STRING)) {
            palette.add(BlueprintBlockState.parse(tag.getAsString()));
        }

        int[] blocks = root.getIntArray(KEY_BLOCKS).clone();
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

        return new Blueprint(root.getString(KEY_NAME), size[0], size[1], size[2], palette, blocks, blockEntities);
    }
}
