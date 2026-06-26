package com.pgmacdesign.mc3dprint.fu;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RecipesUpdatedEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

/**
 * Client-side recipe bind. {@code RecipesUpdatedEvent} fires after the server's
 * recipes finish syncing to a connected client, so derivation works in
 * single-player and on a client connected to a remote server. Dist-guarded so
 * this never loads on a dedicated server.
 */
@Mod.EventBusSubscriber(modid = MC3DPrint.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FuClientBinding {
    private FuClientBinding() {}

    @SubscribeEvent
    public static void onRecipesUpdated(RecipesUpdatedEvent event) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            FuValueRegistry.bind(event.getRecipeManager(), connection.registryAccess());
        }
    }
}
