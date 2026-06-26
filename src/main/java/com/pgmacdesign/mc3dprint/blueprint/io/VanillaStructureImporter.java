package com.pgmacdesign.mc3dprint.blueprint.io;

import com.pgmacdesign.mc3dprint.compat.NbtCompat;

import com.pgmacdesign.mc3dprint.blueprint.Blueprint;
import com.pgmacdesign.mc3dprint.blueprint.BlueprintBlockState;
import com.pgmacdesign.mc3dprint.blueprint.BlueprintFormatException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Imports vanilla structure template NBT (the {@code .nbt} files produced by
 * structure blocks — and by Create's schematic table, which uses the same format).
 *
 * Template format: {@code size: [x,y,z]}, {@code palette: [{Name, Properties}]}
 * (or {@code palettes} for randomized variants — first palette wins),
 * {@code blocks: [{pos: [x,y,z], state: int, nbt?: {}}]}.
 */
public final class VanillaStructureImporter {
    private VanillaStructureImporter() {}

    public static Blueprint importStructure(String name, CompoundTag root) {
        ListTag sizeTag = NbtCompat.getList(root, "size", Tag.TAG_INT);
        if (sizeTag.size() != 3) {
            throw new BlueprintFormatException("Structure size must be [x, y, z]");
        }
        int sizeX = NbtCompat.listGetInt(sizeTag, 0);
        int sizeY = NbtCompat.listGetInt(sizeTag, 1);
        int sizeZ = NbtCompat.listGetInt(sizeTag, 2);

        ListTag paletteTag;
        if (NbtCompat.contains(root, "palette")) {
            paletteTag = NbtCompat.getList(root, "palette", Tag.TAG_COMPOUND);
        } else if (NbtCompat.contains(root, "palettes")) {
            ListTag palettes = NbtCompat.getList(root, "palettes", Tag.TAG_LIST);
            if (palettes.isEmpty()) {
                throw new BlueprintFormatException("Structure has empty palettes list");
            }
            paletteTag = NbtCompat.listGetList(palettes, 0);
        } else {
            throw new BlueprintFormatException("Structure has no palette");
        }

        List<BlueprintBlockState> palette = new ArrayList<>(paletteTag.size());
        for (Tag tag : paletteTag) {
            palette.add(readPaletteEntry((CompoundTag) tag));
        }

        Blueprint.Builder builder = Blueprint.builder(name, sizeX, sizeY, sizeZ);
        for (Tag tag : NbtCompat.getList(root, "blocks", Tag.TAG_COMPOUND)) {
            CompoundTag blockTag = (CompoundTag) tag;
            ListTag posTag = NbtCompat.getList(blockTag, "pos", Tag.TAG_INT);
            if (posTag.size() != 3) {
                throw new BlueprintFormatException("Structure block pos must be [x, y, z]");
            }
            int x = NbtCompat.listGetInt(posTag, 0);
            int y = NbtCompat.listGetInt(posTag, 1);
            int z = NbtCompat.listGetInt(posTag, 2);
            int stateIndex = NbtCompat.getInt(blockTag, "state");
            if (stateIndex < 0 || stateIndex >= palette.size()) {
                throw new BlueprintFormatException("Structure block state index " + stateIndex
                        + " out of range for palette of size " + palette.size());
            }
            builder.set(x, y, z, palette.get(stateIndex));
            if (NbtCompat.contains(blockTag, "nbt") && !palette.get(stateIndex).isAir()) {
                builder.blockEntity(x, y, z, NbtCompat.getCompound(blockTag, "nbt").copy());
            }
        }
        return builder.build();
    }

    private static BlueprintBlockState readPaletteEntry(CompoundTag entry) {
        String id = NbtCompat.getString(entry, "Name");
        if (id.isEmpty()) {
            throw new BlueprintFormatException("Structure palette entry missing Name");
        }
        Map<String, String> properties = new TreeMap<>();
        if (NbtCompat.contains(entry, "Properties")) {
            CompoundTag props = NbtCompat.getCompound(entry, "Properties");
            for (String key : NbtCompat.keySet(props)) {
                properties.put(key, NbtCompat.getString(props, key));
            }
        }
        return new BlueprintBlockState(id, properties);
    }
}
