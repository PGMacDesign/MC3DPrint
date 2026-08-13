package com.pgmacdesign.mc3dprint.integration.patchouli;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Soft-dep access to the Patchouli book item. Everything here goes through registry
 * lookups and the raw {@code patchouli:book} NBT key, so the mod needs no Patchouli
 * dependency and simply has no Handbook when Patchouli is absent.
 */
public final class PatchouliCompat {
    public static final String MOD_ID = "patchouli";
    /** The book id this mod's guide is registered under, as Patchouli stores it on a stack. */
    public static final String GUIDE_BOOK_ID = MC3DPrint.MOD_ID + ":guide";
    /** Patchouli's stack NBT key naming which book a copy opens. */
    public static final String BOOK_TAG = "patchouli:book";

    private static final String BOOK_ITEM_ID = MOD_ID + ":guide_book";

    private PatchouliCompat() {}

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    /** A Handbook bound to this mod's guide, or an empty stack when Patchouli isn't providing one. */
    public static ItemStack guideBookStack() {
        Item bookItem = bookItem();
        if (bookItem == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(bookItem);
        stack.getOrCreateTag().putString(BOOK_TAG, GUIDE_BOOK_ID);
        return stack;
    }

    /** Whether {@code stack} is a Patchouli book already bound to THIS mod's guide. */
    public static boolean isGuideBook(ItemStack stack) {
        Item bookItem = bookItem();
        if (bookItem == null || !stack.is(bookItem)) {
            return false;
        }
        CompoundTag tag = stack.getTag();
        return tag != null && GUIDE_BOOK_ID.equals(tag.getString(BOOK_TAG));
    }

    private static Item bookItem() {
        ResourceLocation id = ResourceLocation.tryParse(BOOK_ITEM_ID);
        return id == null ? null : ForgeRegistries.ITEMS.getValue(id);
    }
}
