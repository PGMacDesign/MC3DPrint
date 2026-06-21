package com.pgmacdesign.mc3dprint.client;

import com.pgmacdesign.mc3dprint.blueprint.repository.RepoEntry;
import com.pgmacdesign.mc3dprint.machine.repository.BlueprintRepositoryMenu;
import net.minecraft.client.Minecraft;

import java.util.List;

/** Client-only sink for {@link com.pgmacdesign.mc3dprint.network.RepositoryListingPacket}. */
public final class ClientRepositoryHandler {
    private ClientRepositoryHandler() {}

    public static void apply(List<RepoEntry> entries) {
        if (Minecraft.getInstance().player != null
                && Minecraft.getInstance().player.containerMenu instanceof BlueprintRepositoryMenu menu) {
            menu.setEntries(entries);
        }
    }
}
