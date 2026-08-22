package com.pgmacdesign.mc3dprint.network;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * The mod's single SimpleChannel. Carries the Blueprint Repository listing S2C
 * (which can't ride a {@code ContainerData} since it's variable-length text, and is
 * per-player in personal mode) and the rename request C2S.
 */
public final class MC3DPrintNetwork {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MC3DPrint.MOD_ID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private MC3DPrintNetwork() {}

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, RepositoryListingPacket.class,
                RepositoryListingPacket::encode, RepositoryListingPacket::decode,
                RepositoryListingPacket::handle);
        CHANNEL.registerMessage(id++, RepositoryRenamePacket.class,
                RepositoryRenamePacket::encode, RepositoryRenamePacket::decode,
                RepositoryRenamePacket::handle);
        CHANNEL.registerMessage(id++, TerminalSyncPacket.class,
                TerminalSyncPacket::encode, TerminalSyncPacket::decode,
                TerminalSyncPacket::handle);
        CHANNEL.registerMessage(id++, TerminalOrderPacket.class,
                TerminalOrderPacket::encode, TerminalOrderPacket::decode,
                TerminalOrderPacket::handle);
    }

    /**
     * Sends to a player only when there's a live connection behind them. Fake players from
     * automation mods, and the mock players gametests act as, have no real client on the other
     * end; a GUI listing refresh is not worth taking the whole server-side action down over.
     */
    public static void sendTo(ServerPlayer player, Object packet) {
        if (player.connection == null || !player.connection.connection.isConnected()) {
            return;
        }
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}
