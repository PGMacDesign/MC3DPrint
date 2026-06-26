package com.pgmacdesign.mc3dprint.blueprint.io;

import com.pgmacdesign.mc3dprint.blueprint.Blueprint;
import com.pgmacdesign.mc3dprint.blueprint.BlueprintFormatException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VanillaStructureImporterTest {

    private static ListTag intList(int... values) {
        ListTag list = new ListTag();
        for (int v : values) {
            list.add(IntTag.valueOf(v));
        }
        return list;
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

    private static CompoundTag block(int x, int y, int z, int state) {
        CompoundTag b = new CompoundTag();
        b.put("pos", intList(x, y, z));
        b.putInt("state", state);
        return b;
    }

    private static CompoundTag sampleStructure() {
        CompoundTag root = new CompoundTag();
        root.put("size", intList(2, 2, 1));

        ListTag palette = new ListTag();
        palette.add(paletteEntry("minecraft:stone"));
        palette.add(paletteEntry("minecraft:oak_stairs", "facing", "east", "half", "bottom"));
        palette.add(paletteEntry("minecraft:air"));
        root.put("palette", palette);

        ListTag blocks = new ListTag();
        blocks.add(block(0, 0, 0, 0));
        blocks.add(block(1, 0, 0, 1));
        blocks.add(block(0, 1, 0, 2)); // explicit air — should import as empty
        CompoundTag chest = block(1, 1, 0, 0);
        CompoundTag chestNbt = new CompoundTag();
        chestNbt.putString("id", "minecraft:chest");
        chest.put("nbt", chestNbt);
        blocks.add(chest);
        root.put("blocks", blocks);
        return root;
    }

    @Test
    void importsBlocksAndPalette() {
        Blueprint bp = VanillaStructureImporter.importStructure("test", sampleStructure());
        assertEquals(2, bp.sizeX());
        assertEquals(2, bp.sizeY());
        assertEquals(1, bp.sizeZ());
        assertEquals(3, bp.blockCount());
        assertEquals("minecraft:stone", bp.get(0, 0, 0).blockId());
        assertEquals("east", bp.get(1, 0, 0).properties().get("facing"));
    }

    @Test
    void explicitAirImportsAsEmpty() {
        Blueprint bp = VanillaStructureImporter.importStructure("test", sampleStructure());
        assertNull(bp.get(0, 1, 0));
    }

    @Test
    void importsBlockEntityNbt() {
        Blueprint bp = VanillaStructureImporter.importStructure("test", sampleStructure());
        assertEquals(1, bp.blockEntities().size());
        assertTrue(bp.blockEntities().values().stream()
                .anyMatch(t -> "minecraft:chest".equals(t.getString("id"))));
    }

    @Test
    void usesFirstPaletteFromPalettesList() {
        CompoundTag root = sampleStructure();
        ListTag palettes = new ListTag();
        palettes.add(com.pgmacdesign.mc3dprint.compat.NbtCompat.getList(root, "palette", 10));
        root.remove("palette");
        root.put("palettes", palettes);
        Blueprint bp = VanillaStructureImporter.importStructure("test", root);
        assertEquals("minecraft:stone", bp.get(0, 0, 0).blockId());
    }

    @Test
    void rejectsMissingPalette() {
        CompoundTag root = sampleStructure();
        root.remove("palette");
        assertThrows(BlueprintFormatException.class,
                () -> VanillaStructureImporter.importStructure("test", root));
    }

    @Test
    void rejectsOutOfRangeStateIndex() {
        CompoundTag root = sampleStructure();
        com.pgmacdesign.mc3dprint.compat.NbtCompat.getList(root, "blocks", 10).add(block(0, 0, 0, 99));
        assertThrows(BlueprintFormatException.class,
                () -> VanillaStructureImporter.importStructure("test", root));
    }
}
