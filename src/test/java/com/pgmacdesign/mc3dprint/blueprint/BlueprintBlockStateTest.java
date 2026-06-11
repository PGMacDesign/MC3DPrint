package com.pgmacdesign.mc3dprint.blueprint;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlueprintBlockStateTest {

    @Test
    void parsesPlainBlockId() {
        BlueprintBlockState state = BlueprintBlockState.parse("minecraft:stone");
        assertEquals("minecraft:stone", state.blockId());
        assertTrue(state.properties().isEmpty());
        assertEquals("minecraft:stone", state.serialize());
    }

    @Test
    void parsesProperties() {
        BlueprintBlockState state = BlueprintBlockState.parse("minecraft:oak_stairs[facing=east,half=top]");
        assertEquals("minecraft:oak_stairs", state.blockId());
        assertEquals("east", state.properties().get("facing"));
        assertEquals("top", state.properties().get("half"));
    }

    @Test
    void serializationIsCanonicalRegardlessOfPropertyOrder() {
        BlueprintBlockState a = BlueprintBlockState.parse("minecraft:oak_stairs[half=top,facing=east]");
        BlueprintBlockState b = BlueprintBlockState.parse("minecraft:oak_stairs[facing=east,half=top]");
        assertEquals(a, b);
        assertEquals(a.serialize(), b.serialize());
        assertEquals("minecraft:oak_stairs[facing=east,half=top]", a.serialize());
    }

    @Test
    void normalizesMissingNamespace() {
        assertEquals("minecraft:stone", BlueprintBlockState.parse("stone").blockId());
    }

    @Test
    void roundTripsThroughSerialize() {
        BlueprintBlockState original = new BlueprintBlockState("create:cogwheel", Map.of("axis", "y"));
        assertEquals(original, BlueprintBlockState.parse(original.serialize()));
    }

    @Test
    void airDetection() {
        assertTrue(BlueprintBlockState.parse("minecraft:air").isAir());
        assertTrue(BlueprintBlockState.parse("minecraft:cave_air").isAir());
        assertFalse(BlueprintBlockState.parse("minecraft:stone").isAir());
    }

    @Test
    void handlesWhitespaceInPropertyList() {
        BlueprintBlockState state = BlueprintBlockState.parse("minecraft:oak_stairs[facing=east, half=top]");
        assertEquals("top", state.properties().get("half"));
    }
}
