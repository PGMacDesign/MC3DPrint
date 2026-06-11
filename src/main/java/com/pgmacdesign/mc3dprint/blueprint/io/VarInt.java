package com.pgmacdesign.mc3dprint.blueprint.io;

import com.pgmacdesign.mc3dprint.blueprint.BlueprintFormatException;

/** Varint codec for the Sponge schematic BlockData array. */
public final class VarInt {
    private VarInt() {}

    /** Decodes every varint in {@code data} into an int array of {@code expectedCount} values. */
    public static int[] decodeAll(byte[] data, int expectedCount) {
        int[] out = new int[expectedCount];
        int index = 0;
        int i = 0;
        while (i < data.length && index < expectedCount) {
            int value = 0;
            int shift = 0;
            byte b;
            do {
                if (i >= data.length) {
                    throw new BlueprintFormatException("Truncated varint in BlockData at value " + index);
                }
                if (shift > 28) {
                    throw new BlueprintFormatException("Varint too long in BlockData at value " + index);
                }
                b = data[i++];
                value |= (b & 0x7F) << shift;
                shift += 7;
            } while ((b & 0x80) != 0);
            out[index++] = value;
        }
        if (index != expectedCount) {
            throw new BlueprintFormatException("BlockData holds " + index + " values, expected " + expectedCount);
        }
        return out;
    }

    /** Encodes {@code values} as a varint byte stream. */
    public static byte[] encodeAll(int[] values) {
        // worst case 5 bytes per value
        byte[] buffer = new byte[values.length * 5];
        int len = 0;
        for (int value : values) {
            while ((value & ~0x7F) != 0) {
                buffer[len++] = (byte) ((value & 0x7F) | 0x80);
                value >>>= 7;
            }
            buffer[len++] = (byte) value;
        }
        byte[] out = new byte[len];
        System.arraycopy(buffer, 0, out, 0, len);
        return out;
    }
}
