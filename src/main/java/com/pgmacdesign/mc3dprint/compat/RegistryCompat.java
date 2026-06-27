package com.pgmacdesign.mc3dprint.compat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Version seam for registry-construction + lookup APIs that churned across 1.21.x.
 *
 * <p>1.21.5 removed {@code BlockEntityType.Builder.of(factory, blocks…).build(dataFixerType)} in
 * favour of the direct varargs constructor {@code new BlockEntityType<>(factory, blocks…)}, and
 * renamed {@code Registry.get(ResourceLocation)} (which returned {@code T}) to {@code getValue} —
 * the new {@code get} returns {@code Optional<Holder.Reference<T>>}.
 */
public final class RegistryCompat {
    private RegistryCompat() {}

    /** Look up an item by id (null if absent). 1.21.5 renamed {@code get}→{@code getValue}. */
    public static Item item(ResourceLocation id) {
        //? if >=1.21.5 {
        /*return BuiltInRegistries.ITEM.getValue(id);
        *///?} else {
        return BuiltInRegistries.ITEM.get(id);
        //?}
    }

    /** Look up a block by id (AIR if absent). 1.21.5 renamed {@code get}→{@code getValue}. */
    public static Block block(ResourceLocation id) {
        //? if >=1.21.5 {
        /*return BuiltInRegistries.BLOCK.getValue(id);
        *///?} else {
        return BuiltInRegistries.BLOCK.get(id);
        //?}
    }

    /**
     * Look up a registered {@code DataComponentType} by id (null if absent). Used to reach an
     * optional mod's data component reflection-free (no compile dependency) — e.g. Patchouli's
     * {@code patchouli:book}. 1.21.5 renamed {@code get}→{@code getValue}.
     */
    public static net.minecraft.core.component.DataComponentType<?> dataComponentType(ResourceLocation id) {
        //? if >=1.21.5 {
        /*return BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(id);
        *///?} else {
        return BuiltInRegistries.DATA_COMPONENT_TYPE.get(id);
        //?}
    }

    /**
     * Tag a {@code BlockItem}'s {@code Item.Properties} so its name resolves via the
     * {@code block.<ns>.<id>} lang key. 1.21.2 stopped having {@code BlockItem} auto-delegate
     * its description id to the block; without {@code useBlockDescriptionPrefix()} the item
     * falls back to {@code item.<ns>.<id>} and shows the raw key in-game. 1.21.1 still
     * auto-delegates, so it's a pass-through there. Apply to EVERY BlockItem registration.
     */
    public static Item.Properties blockItem(Item.Properties props) {
        //? if >=1.21.2 {
        /*return props.useBlockDescriptionPrefix();
        *///?} else {
        return props;
        //?}
    }

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
