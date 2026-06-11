package com.pgmacdesign.mc3dprint.blueprint.io;

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
        ListTag sizeTag = root.getList("size", Tag.TAG_INT);
        if (sizeTag.size() != 3) {
            throw new BlueprintFormatException("Structure size must be [x, y, z]");
        }
        int sizeX = sizeTag.getInt(0);
        int sizeY = sizeTag.getInt(1);
        int sizeZ = sizeTag.getInt(2);

        ListTag paletteTag;
        if (root.contains("palette", Tag.TAG_LIST)) {
            paletteTag = root.getList("palette", Tag.TAG_COMPOUND);
        } else if (root.contains("palettes", Tag.TAG_LIST)) {
            ListTag palettes = root.getList("palettes", Tag.TAG_LIST);
            if (palettes.isEmpty()) {
                throw new BlueprintFormatException("Structure has empty palettes list");
            }
            paletteTag = palettes.getList(0);
        } else {
            throw new BlueprintFormatException("Structure has no palette");
        }

        List<BlueprintBlockState> palette = new ArrayList<>(paletteTag.size());
        for (Tag tag : paletteTag) {
            palette.add(readPaletteEntry((CompoundTag) tag));
        }

        Blueprint.Builder builder = Blueprint.builder(name, sizeX, sizeY, sizeZ);
        for (Tag tag : root.getList("blocks", Tag.TAG_COMPOUND)) {
            CompoundTag blockTag = (CompoundTag) tag;
            ListTag posTag = blockTag.getList("pos", Tag.TAG_INT);
            if (posTag.size() != 3) {
                throw new BlueprintFormatException("Structure block pos must be [x, y, z]");
            }
            int x = posTag.getInt(0);
            int y = posTag.getInt(1);
            int z = posTag.getInt(2);
            int stateIndex = blockTag.getInt("state");
            if (stateIndex < 0 || stateIndex >= palette.size()) {
                throw new BlueprintFormatException("Structure block state index " + stateIndex
                        + " out of range for palette of size " + palette.size());
            }
            builder.set(x, y, z, palette.get(stateIndex));
            if (blockTag.contains("nbt", Tag.TAG_COMPOUND) && !palette.get(stateIndex).isAir()) {
                builder.blockEntity(x, y, z, blockTag.getCompound("nbt").copy());
            }
        }
        return builder.build();
    }

    private static BlueprintBlockState readPaletteEntry(CompoundTag entry) {
        String id = entry.getString("Name");
        if (id.isEmpty()) {
            throw new BlueprintFormatException("Structure palette entry missing Name");
        }
        Map<String, String> properties = new TreeMap<>();
        if (entry.contains("Properties", Tag.TAG_COMPOUND)) {
            CompoundTag props = entry.getCompound("Properties");
            for (String key : props.getAllKeys()) {
                properties.put(key, props.getString(key));
            }
        }
        return new BlueprintBlockState(id, properties);
    }
}
