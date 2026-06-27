package com.pgmacdesign.mc3dprint.integration.patchouli;

import com.pgmacdesign.mc3dprint.compat.RegistryCompat;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/**
 * Soft-dep bridge to Patchouli, kept reflection-free with ZERO compile dependency (no
 * Patchouli artifact on the classpath — same pattern as the modded-FU compat hooks).
 *
 * <p>Patchouli 1.21.x stores which book a {@code patchouli:guide_book} stack opens in a registered
 * data component — {@code PatchouliDataComponents.BOOK}, id {@code patchouli:book}, of type
 * {@code DataComponentType<ResourceLocation>} (the book's id); {@code ItemModBook.forBook(rl)} just
 * does {@code stack.set(BOOK, rl)}. Pre-1.21 Patchouli used the NBT key {@code patchouli:book}, but
 * this tree is 1.21.1+, so the component is always the right channel — vanilla {@code CUSTOM_DATA}
 * with that key is NOT read by 1.21 Patchouli and leaves the book unbound (the old interim bug).
 */
public final class PatchouliCompat {
    public static final String MOD_ID = "patchouli";
    /** Patchouli's book id this mod ships (resources at patchouli_books/guide/). */
    public static final String GUIDE_BOOK_ID = "mc3dprint:guide";
    private static final ResourceLocation BOOK_ITEM_ID = ResourceLocation.fromNamespaceAndPath(MOD_ID, "guide_book");
    private static final ResourceLocation BOOK_COMPONENT_ID = ResourceLocation.fromNamespaceAndPath(MOD_ID, "book");

    private PatchouliCompat() {}

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    /**
     * A {@code patchouli:guide_book} stack already bound (via the {@code patchouli:book} component)
     * to {@link #GUIDE_BOOK_ID}, or {@link ItemStack#EMPTY} if Patchouli isn't loaded / its book
     * item or component isn't registered.
     */
    public static ItemStack guideBookStack() {
        if (!isLoaded()) {
            return ItemStack.EMPTY;
        }
        var bookItem = RegistryCompat.item(BOOK_ITEM_ID);
        if (bookItem == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(bookItem);
        return stampBook(stack, GUIDE_BOOK_ID) ? stack : ItemStack.EMPTY;
    }

    /**
     * Bind {@code bookId} ("ns:bookname") onto a Patchouli book stack via the live {@code
     * patchouli:book} component, looked up from the registry so we need no Patchouli import. The
     * component is {@code DataComponentType<ResourceLocation>} — the unchecked cast matches
     * Patchouli's real type, so the value we store is exactly what {@code ItemModBook} reads back.
     * Returns false (no-op) if the component isn't present.
     */
    @SuppressWarnings("unchecked")
    public static boolean stampBook(ItemStack stack, String bookId) {
        DataComponentType<?> type = RegistryCompat.dataComponentType(BOOK_COMPONENT_ID);
        ResourceLocation book = ResourceLocation.tryParse(bookId);
        if (type == null || book == null) {
            return false;
        }
        stack.set((DataComponentType<ResourceLocation>) type, book);
        return true;
    }
}
