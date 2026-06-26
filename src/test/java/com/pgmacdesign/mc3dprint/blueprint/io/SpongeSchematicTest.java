package com.pgmacdesign.mc3dprint.blueprint.io;

import com.pgmacdesign.mc3dprint.blueprint.Blueprint;
import com.pgmacdesign.mc3dprint.blueprint.BlueprintBlockState;
import com.pgmacdesign.mc3dprint.blueprint.BlueprintFormatException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SpongeSchematicTest {

    /** 2x1x2 volume: stone, stairs / air, stone — YZX order. */
    private static CompoundTag sampleV2() {
        CompoundTag root = new CompoundTag();
        root.putInt("Version", 2);
        root.putInt("DataVersion", 3465);
        root.putShort("Width", (short) 2);
        root.putShort("Height", (short) 1);
        root.putShort("Length", (short) 2);

        CompoundTag palette = new CompoundTag();
        palette.putInt("minecraft:air", 0);
        palette.putInt("minecraft:stone", 1);
        palette.putInt("minecraft:oak_stairs[facing=east]", 2);
        root.put("Palette", palette);
        root.putInt("PaletteMax", 3);

        // index = x + z*Width + y*Width*Length
        // (0,0,0)=stone, (1,0,0)=stairs, (0,0,1)=air, (1,0,1)=stone
        root.putByteArray("BlockData", VarInt.encodeAll(new int[]{1, 2, 0, 1}));

        ListTag blockEntities = new ListTag();
        CompoundTag be = new CompoundTag();
        be.putIntArray("Pos", new int[]{0, 0, 0});
        be.putString("Id", "minecraft:chest");
        be.putInt("CustomField", 7);
        blockEntities.add(be);
        root.put("BlockEntities", blockEntities);
        return root;
    }

    private static CompoundTag sampleV3() {
        CompoundTag schematic = new CompoundTag();
        schematic.putInt("Version", 3);
        schematic.putInt("DataVersion", 3465);
        schematic.putShort("Width", (short) 2);
        schematic.putShort("Height", (short) 1);
        schematic.putShort("Length", (short) 2);

        CompoundTag blocks = new CompoundTag();
        CompoundTag palette = new CompoundTag();
        palette.putInt("minecraft:air", 0);
        palette.putInt("minecraft:stone", 1);
        blocks.put("Palette", palette);
        blocks.putByteArray("Data", VarInt.encodeAll(new int[]{1, 0, 0, 1}));

        ListTag blockEntities = new ListTag();
        CompoundTag be = new CompoundTag();
        be.putIntArray("Pos", new int[]{1, 0, 1});
        be.putString("Id", "minecraft:barrel");
        CompoundTag data = new CompoundTag();
        data.putInt("CustomField", 9);
        be.put("Data", data);
        blockEntities.add(be);
        blocks.put("BlockEntities", blockEntities);

        schematic.put("Blocks", blocks);
        CompoundTag root = new CompoundTag();
        root.put("Schematic", schematic);
        return root;
    }

    @Test
    void importsV2VolumeInYzxOrder() {
        Blueprint bp = SpongeSchematicImporter.importSchematic("test", sampleV2());
        assertEquals(2, bp.sizeX());
        assertEquals(1, bp.sizeY());
        assertEquals(2, bp.sizeZ());
        assertEquals("minecraft:stone", bp.get(0, 0, 0).blockId());
        assertEquals("minecraft:oak_stairs", bp.get(1, 0, 0).blockId());
        assertNull(bp.get(0, 0, 1));
        assertEquals("minecraft:stone", bp.get(1, 0, 1).blockId());
    }

    @Test
    void importsV2BlockEntityWithNormalizedId() {
        Blueprint bp = SpongeSchematicImporter.importSchematic("test", sampleV2());
        CompoundTag be = bp.blockEntities().values().iterator().next();
        assertEquals("minecraft:chest", com.pgmacdesign.mc3dprint.compat.NbtCompat.getString(be, "id"));
        assertEquals(7, com.pgmacdesign.mc3dprint.compat.NbtCompat.getInt(be, "CustomField"));
    }

    @Test
    void importsV3NestedFormat() {
        Blueprint bp = SpongeSchematicImporter.importSchematic("test", sampleV3());
        assertEquals("minecraft:stone", bp.get(0, 0, 0).blockId());
        assertNull(bp.get(1, 0, 0));
        CompoundTag be = bp.blockEntities().values().iterator().next();
        assertEquals("minecraft:barrel", com.pgmacdesign.mc3dprint.compat.NbtCompat.getString(be, "id"));
        assertEquals(9, com.pgmacdesign.mc3dprint.compat.NbtCompat.getInt(be, "CustomField"));
    }

    @Test
    void rejectsUnsupportedVersion() {
        CompoundTag root = sampleV2();
        root.putInt("Version", 1);
        assertThrows(BlueprintFormatException.class,
                () -> SpongeSchematicImporter.importSchematic("test", root));
    }

    @Test
    void exportImportRoundTrip() {
        Blueprint original = Blueprint.builder("Round Trip", 3, 2, 2)
                .set(0, 0, 0, BlueprintBlockState.parse("minecraft:stone"))
                .set(2, 1, 1, BlueprintBlockState.parse("minecraft:oak_stairs[facing=west,half=top]"))
                .set(1, 0, 1, BlueprintBlockState.parse("minecraft:glass"))
                .build();

        CompoundTag exported = SpongeSchematicExporter.exportV2(original, 3465);
        Blueprint reimported = SpongeSchematicImporter.importSchematic("Round Trip", exported);

        assertEquals(original.blockCount(), reimported.blockCount());
        assertEquals(original.get(0, 0, 0), reimported.get(0, 0, 0));
        assertEquals(original.get(2, 1, 1), reimported.get(2, 1, 1));
        assertEquals(original.get(1, 0, 1), reimported.get(1, 0, 1));
        assertNull(reimported.get(1, 1, 1));
    }

    @Test
    void exportRoundTripsBlockEntities() {
        CompoundTag chest = new CompoundTag();
        chest.putString("id", "minecraft:chest");
        Blueprint original = Blueprint.builder("BE Trip", 1, 1, 1)
                .set(0, 0, 0, BlueprintBlockState.parse("minecraft:chest[facing=south]"))
                .blockEntity(0, 0, 0, chest)
                .build();

        Blueprint reimported = SpongeSchematicImporter.importSchematic("BE Trip",
                SpongeSchematicExporter.exportV2(original, 3465));
        assertEquals(1, reimported.blockEntities().size());
        assertEquals("minecraft:chest", com.pgmacdesign.mc3dprint.compat.NbtCompat.getString(
                reimported.blockEntities().values().iterator().next(), "id"));
    }
}
