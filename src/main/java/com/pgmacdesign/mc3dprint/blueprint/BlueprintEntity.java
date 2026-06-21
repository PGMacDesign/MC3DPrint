package com.pgmacdesign.mc3dprint.blueprint;

import net.minecraft.nbt.CompoundTag;

/**
 * A decorative entity captured in a blueprint — an armor stand, item frame, or
 * painting. Position is blueprint-local (relative to the min corner), in the same
 * continuous coordinates entities use (not grid-snapped).
 *
 * <p>{@code nbt} is the entity's {@code save(...)} data with {@code Pos} and
 * {@code UUID} stripped: position is carried separately in {@code x/y/z} (so it
 * survives the print offset + orientation transform), and a fresh UUID is assigned
 * at spawn so reprints never collide. Everything else — armor-stand equipment,
 * the framed item, painting motive + facing — rides along verbatim.
 */
public record BlueprintEntity(double x, double y, double z, CompoundTag nbt) {

    /** The entity type id, e.g. {@code minecraft:armor_stand} (or empty if absent). */
    public String typeId() {
        return nbt.getString("id");
    }
}
