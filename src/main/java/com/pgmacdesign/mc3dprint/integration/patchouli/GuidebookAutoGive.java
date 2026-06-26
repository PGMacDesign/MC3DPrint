package com.pgmacdesign.mc3dprint.integration.patchouli;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.machine.PrinterBlock;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.fml.ModList;

import java.util.Objects;

/**
 * Gives the Fabricator's Handbook on first printer craft (design doc:
 * "auto-given to the player on first printer component craft"). Soft dep:
 * does nothing unless Patchouli is installed. One book per player, tracked
 * in persisted player NBT.
 */
public final class GuidebookAutoGive {
    public static final String PATCHOULI_MOD_ID = "patchouli";
    private static final String TAG_BOOK_GIVEN = MC3DPrint.MOD_ID + ":BookGiven";

    private GuidebookAutoGive() {}

    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !ModList.get().isLoaded(PATCHOULI_MOD_ID)) {
            return;
        }
        if (!(event.getCrafting().getItem() instanceof BlockItem blockItem)
                || !(blockItem.getBlock() instanceof PrinterBlock)) {
            return;
        }
        CompoundTag root = player.getPersistentData();
        CompoundTag persisted = root.getCompound(Player.PERSISTED_NBT_TAG);
        if (persisted.getBoolean(TAG_BOOK_GIVEN)) {
            return;
        }
        Item bookItem = BuiltInRegistries.ITEM.getOptional(Objects.requireNonNull(
                ResourceLocation.tryParse(PATCHOULI_MOD_ID + ":guide_book"))).orElse(null);
        if (bookItem == null) {
            return;
        }
        ItemStack book = new ItemStack(bookItem);
        // TODO(PGM-21/C5): real Patchouli 1.21 component API
        CompoundTag bookTag = new CompoundTag();
        bookTag.putString("patchouli:book", MC3DPrint.MOD_ID + ":guide");
        book.set(DataComponents.CUSTOM_DATA, CustomData.of(bookTag));
        if (!player.getInventory().add(book)) {
            player.drop(book, false);
        }
        persisted.putBoolean(TAG_BOOK_GIVEN, true);
        root.put(Player.PERSISTED_NBT_TAG, persisted);
    }
}
