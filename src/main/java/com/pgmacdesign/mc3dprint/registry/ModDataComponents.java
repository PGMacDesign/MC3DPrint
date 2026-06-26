package com.pgmacdesign.mc3dprint.registry;

import com.mojang.serialization.Codec;
import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.item.BlueprintData;
import com.pgmacdesign.mc3dprint.scanner.ScanData;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Data components for the mod's item-stack state (1.20.5+ replacement for stack NBT).
 * The Blueprint Disc, Filament Spools, and the Structure Scanner store their state here;
 * Resin and Upgrade items carry no per-stack state and need no component.
 */
public final class ModDataComponents {
    private ModDataComponents() {}

    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MC3DPrint.MOD_ID);

    /** The Blueprint Disc's UUID reference + cached metadata. Absence == blank disc. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BlueprintData>> BLUEPRINT =
            COMPONENTS.registerComponentType("blueprint", builder -> builder
                    .persistent(BlueprintData.CODEC)
                    .networkSynchronized(BlueprintData.STREAM_CODEC));

    /** Disc write-lock flag. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> LOCKED =
            COMPONENTS.registerComponentType("locked", builder -> builder
                    .persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL));

    /** Filament Spool stored Filament Units. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> FU =
            COMPONENTS.registerComponentType("fu", builder -> builder
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    /** Structure Scanner two-corner selection. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ScanData>> SCAN =
            COMPONENTS.registerComponentType("scan", builder -> builder
                    .persistent(ScanData.CODEC)
                    .networkSynchronized(ScanData.STREAM_CODEC));
}
