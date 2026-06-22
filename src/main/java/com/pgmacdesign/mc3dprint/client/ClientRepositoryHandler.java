package com.pgmacdesign.mc3dprint.client;

import com.pgmacdesign.mc3dprint.blueprint.repository.RepoEntry;
import com.pgmacdesign.mc3dprint.machine.repository.BlueprintRepositoryMenu;
import net.minecraft.client.Minecraft;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** Client-only sink for {@link com.pgmacdesign.mc3dprint.network.RepositoryListingPacket}. */
public final class ClientRepositoryHandler {
    private ClientRepositoryHandler() {}

    public static void apply(List<RepoEntry> entries, List<UUID> printed) {
        if (Minecraft.getInstance().player != null
                && Minecraft.getInstance().player.containerMenu instanceof BlueprintRepositoryMenu menu) {
            menu.setEntries(entries, new HashSet<>(printed));
        }
    }
}
