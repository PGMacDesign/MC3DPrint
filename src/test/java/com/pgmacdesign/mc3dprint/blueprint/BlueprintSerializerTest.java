package com.pgmacdesign.mc3dprint.blueprint;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BlueprintSerializerTest {

    private static Blueprint sample() {
        CompoundTag chestData = new CompoundTag();
        chestData.putString("id", "minecraft:chest");
        return Blueprint.builder("Test Hut", 2, 3, 2)
                .set(0, 0, 0, BlueprintBlockState.parse("minecraft:stone"))
                .set(1, 0, 0, BlueprintBlockState.parse("minecraft:oak_stairs[facing=east]"))
                .set(0, 1, 0, BlueprintBlockState.parse("minecraft:chest[facing=north]"))
                .set(1, 2, 1, BlueprintBlockState.parse("minecraft:stone"))
                .blockEntity(0, 1, 0, chestData)
                .build();
    }

    @Test
    void roundTripsExactly() {
        Blueprint original = sample();
        Blueprint restored = BlueprintSerializer.read(BlueprintSerializer.write(original));
        assertEquals(original, restored);
    }

    @Test
    void preservesMetadata() {
        Blueprint restored = BlueprintSerializer.read(BlueprintSerializer.write(sample()));
        assertEquals("Test Hut", restored.name());
        assertEquals(2, restored.sizeX());
        assertEquals(3, restored.sizeY());
        assertEquals(2, restored.sizeZ());
        assertEquals(4, restored.blockCount());
    }

    @Test
    void emptyPositionsStayEmpty() {
        Blueprint restored = BlueprintSerializer.read(BlueprintSerializer.write(sample()));
        assertNull(restored.get(1, 1, 1));
        assertEquals("minecraft:stone", restored.get(0, 0, 0).blockId());
    }

    @Test
    void airIsNeverStored() {
        Blueprint blueprint = Blueprint.builder("Air Test", 1, 1, 1)
                .set(0, 0, 0, BlueprintBlockState.parse("minecraft:air"))
                .build();
        assertEquals(0, blueprint.blockCount());
        assertNull(blueprint.get(0, 0, 0));
    }

    @Test
    void rejectsUnknownFormatVersion() {
        CompoundTag tag = BlueprintSerializer.write(sample());
        tag.putInt("Version", 999);
        assertThrows(BlueprintFormatException.class, () -> BlueprintSerializer.read(tag));
    }

    @Test
    void rejectsOutOfRangePaletteIndex() {
        CompoundTag tag = BlueprintSerializer.write(sample());
        int[] blocks = tag.getIntArray("Blocks");
        blocks[0] = 42;
        tag.putIntArray("Blocks", blocks);
        assertThrows(BlueprintFormatException.class, () -> BlueprintSerializer.read(tag));
    }
}
