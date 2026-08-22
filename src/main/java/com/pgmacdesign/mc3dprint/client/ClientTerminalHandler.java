package com.pgmacdesign.mc3dprint.client;

import com.pgmacdesign.mc3dprint.machine.terminal.MC3DPrintTerminalMenu;
import com.pgmacdesign.mc3dprint.network.TerminalSyncPacket;
import net.minecraft.client.Minecraft;

/**
 * Applies a terminal sync to whatever terminal menu the player currently has open.
 *
 * <p>Client-only, and referenced only from a {@code playToClient} handler, so it is never loaded on
 * a dedicated server. Applying to the OPEN menu rather than a static cache means a sync that
 * arrives just after the player closed the screen is dropped instead of resurrecting stale rows
 * into the next terminal they open.
 */
public final class ClientTerminalHandler {

    private ClientTerminalHandler() {}

    public static void apply(TerminalSyncPacket packet) {
        if (Minecraft.getInstance().player == null) {
            return;
        }
        if (Minecraft.getInstance().player.containerMenu instanceof MC3DPrintTerminalMenu menu) {
            menu.acceptSync(packet.catalog(), packet.orders(), packet.fuByTier(),
                    packet.bestMachineTier(), packet.machineCount());
        }
    }
}
