package com.pgmacdesign.mc3dprint.integration.patchouli;

import com.pgmacdesign.mc3dprint.compat.NbtCompat;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.machine.PrinterBlock;
import com.pgmacdesign.mc3dprint.registry.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.fml.ModList;

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
        if (!(event.getEntity() instanceof ServerPlayer player) || !eligible(player)) {
            return;
        }
        // Crafting the Handbook itself satisfies the hand-out: mark the player and give
        // nothing, or the auto-give would hand them a second copy on their next trigger.
        if (PatchouliCompat.isGuideBook(event.getCrafting())) {
            markGiven(player);
            return;
        }
        if (event.getCrafting().getItem() instanceof BlockItem blockItem
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
        CompoundTag persisted = NbtCompat.getCompound(player.getPersistentData(), Player.PERSISTED_NBT_TAG);
        return !NbtCompat.getBoolean(persisted, TAG_BOOK_GIVEN);
    }

    /** Give the book once and set the persisted flag. Server events are sequential, so the
     * flag set here makes any second trigger in the same tick a no-op via {@link #eligible}. */
    private static void give(ServerPlayer player) {
        if (grant(player)) {
            player.sendSystemMessage(Component.translatable("message.mc3dprint.book_given"));
        }
    }

    /**
     * Hands {@code player} a bound Handbook and marks them as having received it, whatever the
     * flag said before. Returns false only when Patchouli isn't providing a usable book stack.
     *
     * <p>Public because the auto-give is a ONE-shot per player: lose the book and no trigger
     * ever fires again, and the item belongs to Patchouli so it can't be given by a
     * {@code mc3dprint:} id. Recovery is either the crafting recipe (Extrudium Crystal + Book)
     * or {@code /mc3dprint guide}, which routes through here so a manual hand-out also disarms
     * the pending auto-give.
     */
    public static boolean grant(ServerPlayer player) {
        ItemStack book = PatchouliCompat.guideBookStack();
        if (book.isEmpty()) {
            return false; // Patchouli book item / component not available
        }
        if (!player.getInventory().add(book)) {
            player.drop(book, false);
        }
        markGiven(player);
        return true;
    }

    /** Records that this player has their Handbook, however they came by it. */
    private static void markGiven(ServerPlayer player) {
        CompoundTag root = player.getPersistentData();
        CompoundTag persisted = NbtCompat.getCompound(root, Player.PERSISTED_NBT_TAG);
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
