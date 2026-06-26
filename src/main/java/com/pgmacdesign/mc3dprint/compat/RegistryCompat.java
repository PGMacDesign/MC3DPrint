package com.pgmacdesign.mc3dprint.compat;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Version seam for registry-construction APIs that churned across 1.21.x.
 *
 * <p>1.21.5 removed {@code BlockEntityType.Builder.of(factory, blocks…).build(dataFixerType)} in
 * favour of the direct varargs constructor {@code new BlockEntityType<>(factory, blocks…)}.
 */
public final class RegistryCompat {
    private RegistryCompat() {}

    /** Build a {@code BlockEntityType} bound to {@code blocks}, version-agnostically. */
    public static <T extends BlockEntity> BlockEntityType<T> blockEntityType(
            BlockEntityType.BlockEntitySupplier<T> factory, Block... blocks) {
        //? if >=1.21.5 {
        /*return new BlockEntityType<>(factory, blocks);
        *///?} else {
        return BlockEntityType.Builder.of(factory, blocks).build(null);
        //?}
    }
}
