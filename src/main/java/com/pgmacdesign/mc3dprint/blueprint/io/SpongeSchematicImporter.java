package com.pgmacdesign.mc3dprint.blueprint.io;

import com.pgmacdesign.mc3dprint.blueprint.Blueprint;
import com.pgmacdesign.mc3dprint.blueprint.BlueprintBlockState;
import com.pgmacdesign.mc3dprint.blueprint.BlueprintFormatException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.HashMap;
import java.util.Map;

/**
 * Imports Sponge schematics ({@code .schem}) versions 2 and 3 — the format
 * WorldEdit reads and writes.
 *
 * v2: root holds Version/Width/Height/Length/Palette/BlockData/BlockEntities.
 * v3: root holds a "Schematic" child whose block volume lives under "Blocks"
 * with Palette/Data/BlockEntities.
 *
 * Block order in both: index = x + z*Width + y*Width*Length (YZX).
 */
public final class SpongeSchematicImporter {
    private SpongeSchematicImporter() {}

    public static Blueprint importSchematic(String name, CompoundTag root) {
        // v3 wraps everything in a "Schematic" container tag
        CompoundTag schematic = root.contains("Schematic", Tag.TAG_COMPOUND)
                ? root.getCompound("Schematic")
                : root;
        int version = schematic.getInt("Version");
        return switch (version) {
            case 2 -> importV2(name, schematic);
            case 3 -> importV3(name, schematic);
            default -> throw new BlueprintFormatException("Unsupported Sponge schematic version " + version
                    + " (supported: 2, 3)");
        };
    }

    private static Blueprint importV2(String name, CompoundTag tag) {
        int width = tag.getShort("Width") & 0xFFFF;
        int height = tag.getShort("Height") & 0xFFFF;
        int length = tag.getShort("Length") & 0xFFFF;
        Map<Integer, BlueprintBlockState> palette = readPalette(tag.getCompound("Palette"));
        return buildVolume(name, width, height, length, palette,
                tag.getByteArray("BlockData"),
                tag.getList("BlockEntities", Tag.TAG_COMPOUND).copy(), false);
    }

    private static Blueprint importV3(String name, CompoundTag tag) {
        int width = tag.getShort("Width") & 0xFFFF;
        int height = tag.getShort("Height") & 0xFFFF;
        int length = tag.getShort("Length") & 0xFFFF;
        CompoundTag blocks = tag.getCompound("Blocks");
        Map<Integer, BlueprintBlockState> palette = readPalette(blocks.getCompound("Palette"));
        return buildVolume(name, width, height, length, palette,
                blocks.getByteArray("Data"),
                blocks.getList("BlockEntities", Tag.TAG_COMPOUND).copy(), true);
    }

    private static Map<Integer, BlueprintBlockState> readPalette(CompoundTag paletteTag) {
        Map<Integer, BlueprintBlockState> palette = new HashMap<>();
        for (String stateString : paletteTag.getAllKeys()) {
            palette.put(paletteTag.getInt(stateString), BlueprintBlockState.parse(stateString));
        }
        if (palette.isEmpty()) {
            throw new BlueprintFormatException("Sponge schematic has empty palette");
        }
        return palette;
    }

    private static Blueprint buildVolume(String name, int width, int height, int length,
                                         Map<Integer, BlueprintBlockState> palette,
                                         byte[] blockData, net.minecraft.nbt.ListTag blockEntities,
                                         boolean v3BlockEntityData) {
        if (width <= 0 || height <= 0 || length <= 0) {
            throw new BlueprintFormatException("Sponge schematic has invalid dimensions "
                    + width + "x" + height + "x" + length);
        }
        // Each dim can be up to 65535; the int product overflows and/or allocates multi-GB.
        // Compute as long and reject anything past a sane build size BEFORE any allocation.
        long volumeL = (long) width * height * length;
        if (volumeL > Blueprint.MAX_VOLUME) {
            throw new BlueprintFormatException("Sponge schematic volume " + volumeL
                    + " exceeds the maximum of " + Blueprint.MAX_VOLUME + " blocks");
        }
        int volume = (int) volumeL;
        int[] indices = VarInt.decodeAll(blockData, volume);

        Blueprint.Builder builder = Blueprint.builder(name, width, height, length);
        for (int i = 0; i < volume; i++) {
            BlueprintBlockState state = palette.get(indices[i]);
            if (state == null) {
                throw new BlueprintFormatException("BlockData references palette id " + indices[i]
                        + " which is not in the palette");
            }
            int x = i % width;
            int z = (i / width) % length;
            int y = i / (width * length);
            builder.set(x, y, z, state);
        }

        for (Tag tag : blockEntities) {
            CompoundTag be = (CompoundTag) tag;
            int[] pos = be.getIntArray("Pos");
            if (pos.length != 3) {
                throw new BlueprintFormatException("Sponge BlockEntity Pos must be [x, y, z]");
            }
            // v3 nests the entity NBT under "Data"; v2 inlines it next to Pos/Id
            CompoundTag data;
            if (v3BlockEntityData) {
                data = be.getCompound("Data").copy();
                data.putString("id", be.getString("Id"));
            } else {
                data = be.copy();
                data.remove("Pos");
                if (data.contains("Id")) {
                    data.putString("id", data.getString("Id"));
                    data.remove("Id");
                }
            }
            builder.blockEntity(pos[0], pos[1], pos[2], data);
        }
        return builder.build();
    }
}
