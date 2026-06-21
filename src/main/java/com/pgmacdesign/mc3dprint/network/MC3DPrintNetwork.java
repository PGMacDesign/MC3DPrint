package com.pgmacdesign.mc3dprint.network;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * The mod's single SimpleChannel. Currently carries one S2C message: the
 * Blueprint Repository listing (which can't ride a {@code ContainerData} since
 * it's variable-length text, and is per-player in personal mode).
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
    }

    public static void sendTo(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}
