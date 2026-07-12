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
    void rejectsMissingFormatVersion() {
        // A missing/invalid version (0) is rejected; the reader is otherwise
        // version-tolerant (any version >= 1 loads), so there's no v1/v2 variant.
        CompoundTag tag = BlueprintSerializer.write(sample());
        tag.putInt("Version", 0);
        assertThrows(BlueprintFormatException.class, () -> BlueprintSerializer.read(tag));
    }

    @Test
    void rejectsOutOfRangePaletteIndex() {
        CompoundTag tag = BlueprintSerializer.write(sample());
        int[] blocks = com.pgmacdesign.mc3dprint.compat.NbtCompat.getIntArray(tag, "Blocks");
        blocks[0] = 42;
        tag.putIntArray("Blocks", blocks);
        assertThrows(BlueprintFormatException.class, () -> BlueprintSerializer.read(tag));
    }

    @Test
    void roundTripsEntities() {
        CompoundTag standNbt = new CompoundTag();
        standNbt.putString("id", "minecraft:armor_stand");
        Blueprint original = Blueprint.builder("Entity Test", 2, 3, 2)
                .set(0, 0, 0, BlueprintBlockState.parse("minecraft:stone"))
                .entity(0.5, 1.0, 0.5, standNbt)
                .build();
        Blueprint restored = BlueprintSerializer.read(BlueprintSerializer.write(original));
        assertEquals(original, restored);
        assertEquals(1, restored.entities().size());
        assertEquals("minecraft:armor_stand", restored.entities().get(0).typeId());
        assertEquals(1.0, restored.entities().get(0).y());
    }

    @Test
    void readsV1FilesWithoutEntities() {
        // A v1 file (pre-entity format) must still load — entities default to empty.
        CompoundTag tag = BlueprintSerializer.write(sample());
        tag.putInt("Version", 1);
        tag.remove("Entities");
        Blueprint restored = BlueprintSerializer.read(tag);
        assertEquals(0, restored.entities().size());
    }

    @Test
    void rejectsOverflowingSize() {
        // A crafted Size whose int product overflows must fail as a format error before the
        // Blocks-length check or any allocation (prevents OOM / AIOOBE on the printer serverTick).
        CompoundTag tag = BlueprintSerializer.write(sample());
        tag.putIntArray("Size", new int[]{65536, 65536, 1});
        assertThrows(BlueprintFormatException.class, () -> BlueprintSerializer.read(tag));
    }

    @Test
    void rejectsNegativeSize() {
        CompoundTag tag = BlueprintSerializer.write(sample());
        tag.putIntArray("Size", new int[]{-1, 3, 2});
        assertThrows(BlueprintFormatException.class, () -> BlueprintSerializer.read(tag));
    }

    @Test
    void rejectsBlocksLengthMismatch() {
        // Volume says one thing, Blocks array says another: reject rather than index out of bounds.
        CompoundTag tag = BlueprintSerializer.write(sample());
        tag.putIntArray("Blocks", new int[]{Blueprint.NO_BLOCK});
        assertThrows(BlueprintFormatException.class, () -> BlueprintSerializer.read(tag));
    }
}
