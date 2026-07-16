package com.pgmacdesign.mc3dprint.integration.patchouli;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Objects;

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
        CompoundTag persisted = root.getCompound(Player.PERSISTED_NBT_TAG);
        if (persisted.getBoolean(TAG_BOOK_GIVEN)) {
            return;
        }
        Item bookItem = ForgeRegistries.ITEMS.getValue(Objects.requireNonNull(
                ResourceLocation.tryParse(PATCHOULI_MOD_ID + ":guide_book")));
        if (bookItem == null) {
            return;
        }
        ItemStack book = new ItemStack(bookItem);
        book.getOrCreateTag().putString("patchouli:book", MC3DPrint.MOD_ID + ":guide");
        if (!player.getInventory().add(book)) {
            player.drop(book, false);
        }
        player.sendSystemMessage(Component.translatable("message.mc3dprint.book_given"));
        persisted.putBoolean(TAG_BOOK_GIVEN, true);
        root.put(Player.PERSISTED_NBT_TAG, persisted);
    }
}
