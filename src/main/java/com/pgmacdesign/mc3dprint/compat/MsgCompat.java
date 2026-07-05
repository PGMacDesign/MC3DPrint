package com.pgmacdesign.mc3dprint.compat;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * Version seam for player-facing status messages. MC 26.1 removed the old
 * two-arg overlay message method on {@code Player}; the overlay (action-bar)
 * form now lives on {@code ServerPlayer.sendSystemMessage(Component, boolean)}.
 *
 * <p>All of the mod's messages are action-bar status lines sent server-side (the
 * vanilla packet handles the client display), so the {@code instanceof ServerPlayer}
 * routing is lossless there; a client-side caller degrades to a plain system message.
 */
public final class MsgCompat {
    private MsgCompat() {}

    /** Shows an action-bar (overlay) status message to the player. */
    public static void actionBar(Player player, Component message) {
        //? if >=26.1 {
        /*if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(message, true);
        } else {
            player.sendSystemMessage(message);
        }
        *///?} else {
        player.displayClientMessage(message, true);
        //?}
    }
}
