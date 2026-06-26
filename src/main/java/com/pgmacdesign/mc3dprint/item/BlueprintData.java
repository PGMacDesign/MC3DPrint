package com.pgmacdesign.mc3dprint.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

/**
 * The Blueprint Disc's data component: a UUID reference into the world's
 * {@link com.pgmacdesign.mc3dprint.blueprint.BlueprintFileStore} plus the cached
 * display/economy metadata the disc carries so tooltips and GUIs never hit disk.
 * One atomic, immutable, value-equal component — absence of it on a stack is the
 * "blank disc" signal.
 *
 * <p>The actual block data is NOT here; it lives on disk keyed by {@link #id}.
 */
public record BlueprintData(
        UUID id,
        String name,
        int sizeX,
        int sizeY,
        int sizeZ,
        int blockCount,
        int tier,
        int printCost,
        int resinTargetMask,
        boolean playerCreated) {

    public static final Codec<BlueprintData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(BlueprintData::id),
            Codec.STRING.fieldOf("name").forGetter(BlueprintData::name),
            Codec.INT.fieldOf("sizeX").forGetter(BlueprintData::sizeX),
            Codec.INT.fieldOf("sizeY").forGetter(BlueprintData::sizeY),
            Codec.INT.fieldOf("sizeZ").forGetter(BlueprintData::sizeZ),
            Codec.INT.fieldOf("blockCount").forGetter(BlueprintData::blockCount),
            Codec.INT.fieldOf("tier").forGetter(BlueprintData::tier),
            Codec.INT.fieldOf("printCost").forGetter(BlueprintData::printCost),
            // Legacy default: an absent mask reads as -1 ("unknown") so older discs never
            // show a false "this resin would do nothing" warning.
            Codec.INT.optionalFieldOf("resinTargetMask", -1).forGetter(BlueprintData::resinTargetMask),
            Codec.BOOL.optionalFieldOf("playerCreated", false).forGetter(BlueprintData::playerCreated)
    ).apply(instance, BlueprintData::new));

    // Hand-written because the field set (10) exceeds StreamCodec.composite's 6-pair cap.
    public static final StreamCodec<RegistryFriendlyByteBuf, BlueprintData> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public BlueprintData decode(RegistryFriendlyByteBuf buf) {
                    UUID id = buf.readUUID();
                    String name = buf.readUtf();
                    int sizeX = buf.readVarInt();
                    int sizeY = buf.readVarInt();
                    int sizeZ = buf.readVarInt();
                    int blockCount = buf.readVarInt();
                    int tier = buf.readVarInt();
                    int printCost = buf.readVarInt();
                    int resinTargetMask = buf.readInt(); // fixed-width: mask is frequently -1
                    boolean playerCreated = buf.readBoolean();
                    return new BlueprintData(id, name, sizeX, sizeY, sizeZ, blockCount, tier,
                            printCost, resinTargetMask, playerCreated);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, BlueprintData value) {
                    buf.writeUUID(value.id());
                    buf.writeUtf(value.name());
                    buf.writeVarInt(value.sizeX());
                    buf.writeVarInt(value.sizeY());
                    buf.writeVarInt(value.sizeZ());
                    buf.writeVarInt(value.blockCount());
                    buf.writeVarInt(value.tier());
                    buf.writeVarInt(value.printCost());
                    buf.writeInt(value.resinTargetMask());
                    buf.writeBoolean(value.playerCreated());
                }
            };
}
