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
 * Imports Litematica schematics ({@code .litematic}).
 *
 * Root: Version/MinecraftDataVersion/Metadata/Regions. Each region under Regions holds
 * Position/Size (compound {x,y,z}; Size components may be NEGATIVE — the region extends
 * in the negative direction from Position), a vanilla-style BlockStatePalette list
 * ({Name, Properties}), BlockStates (a TIGHTLY bit-packed long array — entries span long
 * boundaries, unlike vanilla 1.16+ chunk packing), and TileEntities with inline x/y/z
 * relative to the region's minimum corner.
 *
 * Block order within a region: index = (y * sizeZ + z) * sizeX + x (YZX).
 *
 * Multi-region schematics are merged into one bounding box: every region is placed at
 * its schematic-relative offset and the result is shifted so the overall minimum corner
 * is (0,0,0). Entities and pending ticks are ignored, matching the other importers.
 */
public final class LitematicaImporter {
    /** Litematic format versions with the current region/bit-pack layout (1.13+ worlds). */
    private static final int MIN_SUPPORTED_VERSION = 4;

    private LitematicaImporter() {}

    public static Blueprint importLitematic(String fallbackName, CompoundTag root) {
        int version = root.getInt("Version");
        if (version < MIN_SUPPORTED_VERSION) {
            throw new BlueprintFormatException("Unsupported litematic version " + version
                    + " (supported: " + MIN_SUPPORTED_VERSION + "+)");
        }
        if (!root.contains("Regions")) {
            throw new BlueprintFormatException("Litematic has no Regions");
        }
        CompoundTag regionsTag = root.getCompound("Regions");
        List<Region> regions = new ArrayList<>();
        for (String regionName : regionsTag.getAllKeys()) {
            regions.add(Region.read(regionName, regionsTag.getCompound(regionName)));
        }
        if (regions.isEmpty()) {
            throw new BlueprintFormatException("Litematic has no Regions");
        }

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (Region region : regions) {
            minX = Math.min(minX, region.minX);
            minY = Math.min(minY, region.minY);
            minZ = Math.min(minZ, region.minZ);
            maxX = Math.max(maxX, region.minX + region.sizeX - 1);
            maxY = Math.max(maxY, region.minY + region.sizeY - 1);
            maxZ = Math.max(maxZ, region.minZ + region.sizeZ - 1);
        }

        String name = metadataName(root, fallbackName);
        Blueprint.Builder builder = Blueprint.builder(name,
                maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);
        for (Region region : regions) {
            region.copyInto(builder, region.minX - minX, region.minY - minY, region.minZ - minZ);
        }
        return builder.build();
    }

    private static String metadataName(CompoundTag root, String fallbackName) {
        String name = root.getCompound("Metadata").getString("Name");
        return name.isBlank() ? fallbackName : name;
    }

    private record Vec3i(int x, int y, int z) {
        static Vec3i read(CompoundTag tag, String key) {
            CompoundTag vec = tag.getCompound(key);
            return new Vec3i(vec.getInt("x"), vec.getInt("y"), vec.getInt("z"));
        }
    }

    private static final class Region {
        final int minX, minY, minZ;
        final int sizeX, sizeY, sizeZ;
        private final List<BlueprintBlockState> palette;
        private final long[] blockStates;
        private final int bitsPerEntry;
        private final ListTag tileEntities;

        private Region(String name, Vec3i position, Vec3i size,
                       List<BlueprintBlockState> palette, long[] blockStates, ListTag tileEntities) {
            if (size.x == 0 || size.y == 0 || size.z == 0) {
                throw new BlueprintFormatException("Litematic region '" + name + "' has zero-size axis");
            }
            // A negative Size component means the region extends downward from Position.
            this.minX = position.x + Math.min(size.x + 1, 0);
            this.minY = position.y + Math.min(size.y + 1, 0);
            this.minZ = position.z + Math.min(size.z + 1, 0);
            this.sizeX = Math.abs(size.x);
            this.sizeY = Math.abs(size.y);
            this.sizeZ = Math.abs(size.z);
            this.palette = palette;
            this.blockStates = blockStates;
            this.bitsPerEntry = Math.max(2, 32 - Integer.numberOfLeadingZeros(palette.size() - 1));
            this.tileEntities = tileEntities;

            long volume = (long) sizeX * sizeY * sizeZ;
            long expectedLongs = (volume * bitsPerEntry + 63) / 64;
            if (blockStates.length < expectedLongs) {
                throw new BlueprintFormatException("Litematic region '" + name + "' BlockStates has "
                        + blockStates.length + " longs, expected " + expectedLongs);
            }
        }

        static Region read(String name, CompoundTag tag) {
            ListTag paletteTag = tag.getList("BlockStatePalette", Tag.TAG_COMPOUND);
            if (paletteTag.isEmpty()) {
                throw new BlueprintFormatException("Litematic region '" + name + "' has empty palette");
            }
            List<BlueprintBlockState> palette = new ArrayList<>(paletteTag.size());
            for (Tag entry : paletteTag) {
                palette.add(readPaletteEntry((CompoundTag) entry));
            }
            return new Region(name,
                    Vec3i.read(tag, "Position"),
                    Vec3i.read(tag, "Size"),
                    palette,
                    tag.getLongArray("BlockStates"),
                    tag.getList("TileEntities", Tag.TAG_COMPOUND));
        }

        void copyInto(Blueprint.Builder builder, int offsetX, int offsetY, int offsetZ) {
            int volume = sizeX * sizeY * sizeZ;
            for (int i = 0; i < volume; i++) {
                int paletteId = unpack(i);
                if (paletteId >= palette.size()) {
                    throw new BlueprintFormatException("Litematic BlockStates references palette id "
                            + paletteId + " which is not in the palette");
                }
                int x = i % sizeX;
                int z = (i / sizeX) % sizeZ;
                int y = i / (sizeX * sizeZ);
                builder.set(offsetX + x, offsetY + y, offsetZ + z, palette.get(paletteId));
            }
            for (Tag tag : tileEntities) {
                CompoundTag be = ((CompoundTag) tag).copy();
                int x = be.getInt("x");
                int y = be.getInt("y");
                int z = be.getInt("z");
                // Position lives in the blueprint's block-entity map; stale region-relative
                // coords inside the NBT would fight the printer's placement.
                be.remove("x");
                be.remove("y");
                be.remove("z");
                builder.blockEntity(offsetX + x, offsetY + y, offsetZ + z, be);
            }
        }

        /** Tightly packed bit array: an entry may straddle two longs. */
        private int unpack(int index) {
            long mask = (1L << bitsPerEntry) - 1L;
            long startBit = (long) index * bitsPerEntry;
            int startLong = (int) (startBit >> 6);
            int endLong = (int) ((startBit + bitsPerEntry - 1) >> 6);
            int startOffset = (int) (startBit & 63);
            if (startLong == endLong) {
                return (int) ((blockStates[startLong] >>> startOffset) & mask);
            }
            return (int) (((blockStates[startLong] >>> startOffset)
                    | (blockStates[endLong] << (64 - startOffset))) & mask);
        }
    }

    private static BlueprintBlockState readPaletteEntry(CompoundTag entry) {
        String id = entry.getString("Name");
        if (id.isEmpty()) {
            throw new BlueprintFormatException("Litematic palette entry missing Name");
        }
        Map<String, String> properties = new TreeMap<>();
        if (entry.contains("Properties")) {
            CompoundTag props = entry.getCompound("Properties");
            for (String key : props.getAllKeys()) {
                properties.put(key, props.getString(key));
            }
        }
        return new BlueprintBlockState(id, properties);
    }
}
