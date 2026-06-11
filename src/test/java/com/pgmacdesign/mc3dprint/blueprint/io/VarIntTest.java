package com.pgmacdesign.mc3dprint.blueprint.io;

import com.pgmacdesign.mc3dprint.blueprint.BlueprintFormatException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VarIntTest {

    @Test
    void roundTripsSmallValues() {
        int[] values = {0, 1, 2, 127};
        assertArrayEquals(values, VarInt.decodeAll(VarInt.encodeAll(values), values.length));
    }

    @Test
    void roundTripsMultiByteValues() {
        int[] values = {128, 255, 300, 16384, 1_000_000};
        assertArrayEquals(values, VarInt.decodeAll(VarInt.encodeAll(values), values.length));
    }

    @Test
    void singleByteValuesEncodeAsSingleBytes() {
        byte[] encoded = VarInt.encodeAll(new int[]{0, 5, 127});
        assertArrayEquals(new byte[]{0, 5, 127}, encoded);
    }

    @Test
    void throwsOnTruncatedStream() {
        byte[] truncated = {(byte) 0x80}; // continuation bit set, no next byte
        assertThrows(BlueprintFormatException.class, () -> VarInt.decodeAll(truncated, 1));
    }

    @Test
    void throwsOnCountMismatch() {
        byte[] two = VarInt.encodeAll(new int[]{1, 2});
        assertThrows(BlueprintFormatException.class, () -> VarInt.decodeAll(two, 3));
    }
}
