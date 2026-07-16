package com.pgmacdesign.mc3dprint.integration.patchouli;

import com.pgmacdesign.mc3dprint.compat.NbtCompat;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.fml.ModList;

/**
 * Gives the Fabricator's Handbook on a player's FIRST login (once Patchouli is
 * installed), so the guide is in the player's inventory from the very start
 * rather than only after they work out how to craft a printer. Soft dep: does
 * nothing unless Patchouli is present. One book per player, tracked in persisted
 * player NBT, so it survives death/dimension change and is never handed out twice
 * (a player who already received it via the older first-craft path keeps the flag).
 */
public final class GuidebookAutoGive {
    public static final String PATCHOULI_MOD_ID = "patchouli";
    private static final String TAG_BOOK_GIVEN = MC3DPrint.MOD_ID + ":BookGiven";

    private GuidebookAutoGive() {}

    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !ModList.get().isLoaded(PATCHOULI_MOD_ID)) {
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
