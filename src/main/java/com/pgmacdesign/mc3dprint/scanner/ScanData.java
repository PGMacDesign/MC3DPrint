package com.pgmacdesign.mc3dprint.scanner;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Optional;

/**
 * The Structure Scanner's in-progress selection, carried as a data component on the
 * scanner stack: the two WorldEdit-wand corners (either may be unset) plus which corner
 * the next right-click assigns. Immutable + value-equal; absence means "no selection".
 */
public record ScanData(Optional<BlockPos> cornerA, Optional<BlockPos> cornerB, boolean nextIsB) {

    public static final ScanData EMPTY = new ScanData(Optional.empty(), Optional.empty(), false);

    public static final Codec<ScanData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockPos.CODEC.optionalFieldOf("cornerA").forGetter(ScanData::cornerA),
            BlockPos.CODEC.optionalFieldOf("cornerB").forGetter(ScanData::cornerB),
            Codec.BOOL.optionalFieldOf("nextIsB", false).forGetter(ScanData::nextIsB)
    ).apply(instance, ScanData::new));

    public static final StreamCodec<ByteBuf, ScanData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(BlockPos.STREAM_CODEC), ScanData::cornerA,
            ByteBufCodecs.optional(BlockPos.STREAM_CODEC), ScanData::cornerB,
            ByteBufCodecs.BOOL, ScanData::nextIsB,
            ScanData::new);
}
