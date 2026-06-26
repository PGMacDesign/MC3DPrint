package com.pgmacdesign.mc3dprint.compat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/**
 * Version seam for the CompoundTag read API. In 1.21.5+ the primitive/compound/list
 * getters were rewritten to return {@code Optional<T>} (and {@code getList}/{@code contains}
 * dropped their tag-type argument). Pre-1.21.5 they returned the value directly. Call sites
 * use these helpers so they stay version-agnostic; the divergence is localized here behind
 * Stonecutter guards.
 */
public final class NbtCompat {
    private NbtCompat() {}

    public static int getInt(CompoundTag tag, String key) {
        //? if >=1.21.5 {
        /*return tag.getInt(key).orElse(0);
        *///?} else {
        return tag.getInt(key);
        //?}
    }

    public static long getLong(CompoundTag tag, String key) {
        //? if >=1.21.5 {
        /*return tag.getLong(key).orElse(0L);
        *///?} else {
        return tag.getLong(key);
        //?}
    }

    public static float getFloat(CompoundTag tag, String key) {
        //? if >=1.21.5 {
        /*return tag.getFloat(key).orElse(0f);
        *///?} else {
        return tag.getFloat(key);
        //?}
    }

    public static double getDouble(CompoundTag tag, String key) {
        //? if >=1.21.5 {
        /*return tag.getDouble(key).orElse(0d);
        *///?} else {
        return tag.getDouble(key);
        //?}
    }

    public static boolean getBoolean(CompoundTag tag, String key) {
        //? if >=1.21.5 {
        /*return tag.getBoolean(key).orElse(false);
        *///?} else {
        return tag.getBoolean(key);
        //?}
    }

    public static byte getByte(CompoundTag tag, String key) {
        //? if >=1.21.5 {
        /*return tag.getByte(key).orElse((byte) 0);
        *///?} else {
        return tag.getByte(key);
        //?}
    }

    public static short getShort(CompoundTag tag, String key) {
        //? if >=1.21.5 {
        /*return tag.getShort(key).orElse((short) 0);
        *///?} else {
        return tag.getShort(key);
        //?}
    }

    public static String getString(CompoundTag tag, String key) {
        //? if >=1.21.5 {
        /*return tag.getString(key).orElse("");
        *///?} else {
        return tag.getString(key);
        //?}
    }

    public static int[] getIntArray(CompoundTag tag, String key) {
        //? if >=1.21.5 {
        /*return tag.getIntArray(key).orElse(new int[0]);
        *///?} else {
        return tag.getIntArray(key);
        //?}
    }

    public static long[] getLongArray(CompoundTag tag, String key) {
        //? if >=1.21.5 {
        /*return tag.getLongArray(key).orElse(new long[0]);
        *///?} else {
        return tag.getLongArray(key);
        //?}
    }

    /** Returns the child compound, or an empty compound if absent (matches legacy getCompound). */
    public static CompoundTag getCompound(CompoundTag tag, String key) {
        //? if >=1.21.5 {
        /*return tag.getCompound(key).orElseGet(CompoundTag::new);
        *///?} else {
        return tag.getCompound(key);
        //?}
    }

    /** Returns the child list (1.21.5+ dropped the tag-type arg), or an empty list if absent. */
    public static ListTag getList(CompoundTag tag, String key, int legacyType) {
        //? if >=1.21.5 {
        /*return tag.getList(key).orElseGet(ListTag::new);
        *///?} else {
        return tag.getList(key, legacyType);
        //?}
    }

    /** Key presence. 1.21.5+ dropped the tag-type overload; the 1-arg form exists on both. */
    public static boolean contains(CompoundTag tag, String key) {
        return tag.contains(key);
    }
}
