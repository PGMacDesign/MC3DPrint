package com.pgmacdesign.mc3dprint.blueprint.io;

import com.pgmacdesign.mc3dprint.blueprint.Blueprint;
import com.pgmacdesign.mc3dprint.blueprint.BlueprintFormatException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LitematicaImporterTest {

    // ---- fixture helpers ----

    /** Tightly packed encoder mirroring Litematica's bit array (entries straddle longs). */
    private static long[] pack(int[] values, int paletteSize) {
        int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(paletteSize - 1));
        long[] longs = new long[(int) (((long) values.length * bits + 63) / 64)];
        for (int i = 0; i < values.length; i++) {
            long startBit = (long) i * bits;
            int startLong = (int) (startBit >> 6);
            int endLong = (int) ((startBit + bits - 1) >> 6);
            int offset = (int) (startBit & 63);
            longs[startLong] |= (long) values[i] << offset;
            if (endLong != startLong) {
                longs[endLong] |= (long) values[i] >>> (64 - offset);
            }
        }
        return longs;
    }

    private static CompoundTag vec(int x, int y, int z) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", x);
        tag.putInt("y", y);
        tag.putInt("z", z);
        return tag;
    }

    private static CompoundTag paletteEntry(String name, String... props) {
        CompoundTag entry = new CompoundTag();
        entry.putString("Name", name);
        if (props.length > 0) {
            CompoundTag properties = new CompoundTag();
            for (int i = 0; i < props.length; i += 2) {
                properties.putString(props[i], props[i + 1]);
            }
            entry.put("Properties", properties);
        }
        return entry;
    }

    private static CompoundTag region(CompoundTag position, CompoundTag size,
                                      ListTag palette, long[] blockStates, ListTag tileEntities) {
        CompoundTag region = new CompoundTag();
        region.put("Position", position);
        region.put("Size", size);
        region.put("BlockStatePalette", palette);
        region.putLongArray("BlockStates", blockStates);
        if (tileEntities != null) {
            region.put("TileEntities", tileEntities);
        }
        return region;
    }

    private static CompoundTag root(String metadataName, CompoundTag... namedRegions) {
        CompoundTag root = new CompoundTag();
        root.putInt("Version", 6);
        root.putInt("MinecraftDataVersion", 3465);
        CompoundTag metadata = new CompoundTag();
        if (metadataName != null) {
            metadata.putString("Name", metadataName);
        }
        root.put("Metadata", metadata);
        CompoundTag regions = new CompoundTag();
        for (int i = 0; i < namedRegions.length; i++) {
            regions.put("region" + i, namedRegions[i]);
        }
        root.put("Regions", regions);
        return root;
    }

    /** 2x1x2: stone, stairs / air, stone — same layout as the Sponge fixture. */
    private static CompoundTag sample() {
        ListTag palette = new ListTag();
        palette.add(paletteEntry("minecraft:air"));
        palette.add(paletteEntry("minecraft:stone"));
        palette.add(paletteEntry("minecraft:oak_stairs", "facing", "east"));

        ListTag tileEntities = new ListTag();
        CompoundTag chest = new CompoundTag();
        chest.putString("id", "minecraft:chest");
        chest.putInt("x", 0);
        chest.putInt("y", 0);
        chest.putInt("z", 0);
        chest.putInt("CustomField", 7);
        tileEntities.add(chest);

        // index = (y*sizeZ + z)*sizeX + x: (0,0,0)=stone, (1,0,0)=stairs, (0,0,1)=air, (1,0,1)=stone
        return root("Sample", region(vec(0, 0, 0), vec(2, 1, 2), palette,
                pack(new int[]{1, 2, 0, 1}, 3), tileEntities));
    }

    // ---- tests ----

    @Test
    void importsVolumeInYzxOrder() {
        Blueprint bp = LitematicaImporter.importLitematic("fallback", sample());
        assertEquals(2, bp.sizeX());
        assertEquals(1, bp.sizeY());
        assertEquals(2, bp.sizeZ());
        assertEquals("minecraft:stone", bp.get(0, 0, 0).blockId());
        assertEquals("minecraft:oak_stairs", bp.get(1, 0, 0).blockId());
        assertEquals("east", bp.get(1, 0, 0).properties().get("facing"));
        assertNull(bp.get(0, 0, 1));
        assertEquals("minecraft:stone", bp.get(1, 0, 1).blockId());
    }

    @Test
    void prefersMetadataNameOverFallback() {
        assertEquals("Sample", LitematicaImporter.importLitematic("fallback", sample()).name());
    }

    @Test
    void importsTileEntityAndStripsRelativeCoords() {
        Blueprint bp = LitematicaImporter.importLitematic("fallback", sample());
        assertEquals(1, bp.blockEntities().size());
        CompoundTag be = bp.blockEntities().values().iterator().next();
        assertEquals("minecraft:chest", be.getString("id"));
        assertEquals(7, be.getInt("CustomField"));
        assertFalse(be.contains("x"));
        assertFalse(be.contains("y"));
        assertFalse(be.contains("z"));
    }

    @Test
    void normalizesNegativeRegionSize() {
        ListTag palette = new ListTag();
        palette.add(paletteEntry("minecraft:air"));
        palette.add(paletteEntry("minecraft:stone"));
        // Position (1,0,1) with Size (-2,1,-2) spans the same box as Position (0,0,0) Size (2,1,2)
        CompoundTag root = root(null, region(vec(1, 0, 1), vec(-2, 1, -2), palette,
                pack(new int[]{1, 0, 0, 1}, 2), null));

        Blueprint bp = LitematicaImporter.importLitematic("neg", root);
        assertEquals("neg", bp.name());
        assertEquals(2, bp.sizeX());
        assertEquals(1, bp.sizeY());
        assertEquals(2, bp.sizeZ());
        assertEquals("minecraft:stone", bp.get(0, 0, 0).blockId());
        assertNull(bp.get(1, 0, 0));
        assertEquals("minecraft:stone", bp.get(1, 0, 1).blockId());
    }

    @Test
    void mergesMultipleRegionsIntoOneBoundingBox() {
        ListTag stoneOnly = new ListTag();
        stoneOnly.add(paletteEntry("minecraft:air"));
        stoneOnly.add(paletteEntry("minecraft:stone"));
        ListTag glassOnly = new ListTag();
        glassOnly.add(paletteEntry("minecraft:air"));
        glassOnly.add(paletteEntry("minecraft:glass"));

        // 1x1x1 stone at (0,0,0) and 1x1x1 glass at (2,1,0) → 3x2x1 merged box
        CompoundTag root = root(null,
                region(vec(0, 0, 0), vec(1, 1, 1), stoneOnly, pack(new int[]{1}, 2), null),
                region(vec(2, 1, 0), vec(1, 1, 1), glassOnly, pack(new int[]{1}, 2), null));

        Blueprint bp = LitematicaImporter.importLitematic("merged", root);
        assertEquals(3, bp.sizeX());
        assertEquals(2, bp.sizeY());
        assertEquals(1, bp.sizeZ());
        assertEquals("minecraft:stone", bp.get(0, 0, 0).blockId());
        assertEquals("minecraft:glass", bp.get(2, 1, 0).blockId());
        assertNull(bp.get(1, 0, 0));
    }

    @Test
    void unpacksEntriesThatStraddleLongBoundaries() {
        // Palette of 5 → 3 bits/entry; 4x4x4 volume = 192 bits = 3 longs, so entries
        // straddle the long boundaries (e.g. index 21 spans bits 63..65).
        ListTag palette = new ListTag();
        palette.add(paletteEntry("minecraft:air"));
        palette.add(paletteEntry("minecraft:stone"));
        palette.add(paletteEntry("minecraft:glass"));
        palette.add(paletteEntry("minecraft:dirt"));
        palette.add(paletteEntry("minecraft:oak_planks"));

        int[] values = new int[64];
        for (int i = 0; i < values.length; i++) {
            values[i] = i % 5;
        }
        CompoundTag root = root(null, region(vec(0, 0, 0), vec(4, 4, 4), palette,
                pack(values, 5), null));

        Blueprint bp = LitematicaImporter.importLitematic("straddle", root);
        String[] ids = {null, "minecraft:stone", "minecraft:glass", "minecraft:dirt", "minecraft:oak_planks"};
        for (int i = 0; i < 64; i++) {
            int x = i % 4;
            int z = (i / 4) % 4;
            int y = i / 16;
            if (ids[i % 5] == null) {
                assertNull(bp.get(x, y, z), "index " + i);
            } else {
                assertEquals(ids[i % 5], bp.get(x, y, z).blockId(), "index " + i);
            }
        }
    }

    @Test
    void rejectsUnsupportedVersion() {
        CompoundTag root = sample();
        root.putInt("Version", 3);
        assertThrows(BlueprintFormatException.class,
                () -> LitematicaImporter.importLitematic("fallback", root));
    }

    @Test
    void rejectsMissingRegions() {
        CompoundTag root = sample();
        root.remove("Regions");
        assertThrows(BlueprintFormatException.class,
                () -> LitematicaImporter.importLitematic("fallback", root));
    }

    @Test
    void rejectsEmptyPalette() {
        CompoundTag root = root(null, region(vec(0, 0, 0), vec(1, 1, 1),
                new ListTag(), new long[]{0}, null));
        assertThrows(BlueprintFormatException.class,
                () -> LitematicaImporter.importLitematic("fallback", root));
    }

    @Test
    void rejectsTruncatedBlockStates() {
        ListTag palette = new ListTag();
        palette.add(paletteEntry("minecraft:air"));
        palette.add(paletteEntry("minecraft:stone"));
        CompoundTag root = root(null, region(vec(0, 0, 0), vec(8, 8, 8), palette,
                new long[]{0}, null));
        assertThrows(BlueprintFormatException.class,
                () -> LitematicaImporter.importLitematic("fallback", root));
    }
}
