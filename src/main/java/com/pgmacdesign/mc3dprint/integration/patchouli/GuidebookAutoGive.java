package com.pgmacdesign.mc3dprint.integration.patchouli;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.machine.PrinterBlock;
import com.pgmacdesign.mc3dprint.registry.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Objects;

/**
 * Hands the player the Fabricator's Handbook the first time they engage with the
 * mod's core loop, whichever happens first:
 * <ul>
 *   <li>they <b>craft a printer</b> (any tier — the entry printer is Tier 1), or</li>
 *   <li>they <b>obtain a Blueprint Disc</b> (found in loot, traded, scanned, picked up).</li>
 * </ul>
 * Soft dep: does nothing unless Patchouli is installed. One book per player, tracked in
 * persisted player NBT, so it survives death/dimension change and is never handed out
 * twice — a player who already has the flag (incl. from an earlier build) is skipped.
 */
public final class GuidebookAutoGive {
    public static final String PATCHOULI_MOD_ID = "patchouli";
    private static final String TAG_BOOK_GIVEN = MC3DPrint.MOD_ID + ":BookGiven";

    private GuidebookAutoGive() {}

    /** Trigger 1: crafting the first printer. */
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && eligible(player)
                && event.getCrafting().getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof PrinterBlock) {
            give(player);
        }
    }

    /**
     * Trigger 2: obtaining the first Blueprint Disc. Checked on container close (the reliable
     * catch-all for "a disc landed in the inventory" — a loot chest, a villager trade, the scan
     * command, or an item picked up then seen on the next inventory open), gated on the cheap
     * flag first so the inventory scan only runs until the book is handed out.
     */
    public static void onContainerClose(PlayerContainerEvent.Close event) {
        if (event.getEntity() instanceof ServerPlayer player
                && eligible(player)
                && hasBlueprintDisc(player)) {
            give(player);
        }
    }

    /** Patchouli present AND this player hasn't been given the book yet. */
    private static boolean eligible(ServerPlayer player) {
        if (!ModList.get().isLoaded(PATCHOULI_MOD_ID)) {
            return false;
        }
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        return !persisted.getBoolean(TAG_BOOK_GIVEN);
    }

    /** Give the book once and set the persisted flag. Server events are sequential, so the
     * flag set here makes any second trigger in the same tick a no-op via {@link #eligible}. */
    private static void give(ServerPlayer player) {
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
        CompoundTag root = player.getPersistentData();
        CompoundTag persisted = root.getCompound(Player.PERSISTED_NBT_TAG);
        persisted.putBoolean(TAG_BOOK_GIVEN, true);
        root.put(Player.PERSISTED_NBT_TAG, persisted);
    }

    private static boolean hasBlueprintDisc(Player player) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(ModItems.BLUEPRINT_DISC.get())) {
                return true;
            }
        }
        return false;
    }
}
