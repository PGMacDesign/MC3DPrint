package com.pgmacdesign.mc3dprint.integration.patchouli;

import com.pgmacdesign.mc3dprint.compat.NbtCompat;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.machine.PrinterBlock;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.fml.ModList;

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
        CompoundTag persisted = NbtCompat.getCompound(root, Player.PERSISTED_NBT_TAG);
        if (NbtCompat.getBoolean(persisted, TAG_BOOK_GIVEN)) {
            return;
        }
        ItemStack book = PatchouliCompat.guideBookStack();
        if (book.isEmpty()) {
            return; // Patchouli book item / component not available
        }
        if (!player.getInventory().add(book)) {
            player.drop(book, false);
        }
        persisted.putBoolean(TAG_BOOK_GIVEN, true);
        root.put(Player.PERSISTED_NBT_TAG, persisted);
    }
}
