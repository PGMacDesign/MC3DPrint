package com.pgmacdesign.mc3dprint.fu;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.neoforged.api.distmarker.Dist;
//? if >=1.21.5 {
/*import net.neoforged.neoforge.client.event.RecipesReceivedEvent;
*///?} else {
import net.neoforged.neoforge.client.event.RecipesUpdatedEvent;
//?}
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * Client-side recipe bind. The recipes-received event fires after the server's
 * recipes finish syncing to a connected client, so derivation works in
 * single-player and on a client connected to a remote server. Dist-guarded so
 * this never loads on a dedicated server.
 *
 * <p>1.21.5 renamed the event {@code RecipesUpdatedEvent → RecipesReceivedEvent}
 * and it now carries a {@code RecipeMap} instead of a {@code RecipeManager}. It
 * also stopped syncing recipe data to clients by default — only types the server
 * explicitly requests via {@code OnDatapackSyncEvent#sendRecipes} arrive — so on
 * 1.21.5+ a remote client may bind an empty/partial set and client-side FU
 * tooltips degrade there. Single-player is unaffected (the integrated server's
 * own bind in {@code FuEvents} holds the full recipe set). [PORT] If remote-client
 * FU display matters, request the crafting/smelting/stonecutting types in
 * {@code FuEvents.onDatapackSync}.
 */
//? if >=1.21.5 {
/*@EventBusSubscriber(modid = MC3DPrint.MOD_ID, value = Dist.CLIENT)
*///?} else {
@EventBusSubscriber(modid = MC3DPrint.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
//?}
public final class FuClientBinding {
    private FuClientBinding() {}

    @SubscribeEvent
    //? if >=1.21.5 {
    /*public static void onRecipesUpdated(RecipesReceivedEvent event) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            FuValueRegistry.bind(event.getRecipeMap().values(), connection.registryAccess());
        }
    }
    *///?} else {
    public static void onRecipesUpdated(RecipesUpdatedEvent event) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            FuValueRegistry.bind(event.getRecipeManager().getRecipes(), connection.registryAccess());
        }
    }
    //?}
}
