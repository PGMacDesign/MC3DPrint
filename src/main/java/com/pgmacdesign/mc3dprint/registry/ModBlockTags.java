package com.pgmacdesign.mc3dprint.registry;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * Block {@link TagKey}s owned by MC3DPrint.
 *
 * <h2>Cable-connectable</h2>
 * {@link #CABLE_CONNECTABLE} is a purely cosmetic tag: it adds an extra clause to
 * {@code MC3DCableBlock.canConnectTo} so the cable renders an arm toward the tagged block.
 * Some machines (the Filament Tier Item Sorter) discover winders over the cable graph but expose
 * neither Forge Energy nor {@code FILAMENT_SOURCE}, so without this the cable would visibly
 * refuse to attach to them even though the sorter reads the network fine. Data-driven, so a pack
 * author can make any future block attach without it carrying energy or filament.
 *
 * <p><b>Backed by {@code data/mc3dprint/tags/block/cable_connectable.json}</b> — SINGULAR
 * {@code block/}; the plural {@code blocks/} form silently fails to load on 1.21 (PGM-51).
 */
public final class ModBlockTags {

    public static final TagKey<Block> CABLE_CONNECTABLE =
            TagKey.create(Registries.BLOCK,
                    ResourceLocation.fromNamespaceAndPath(MC3DPrint.MOD_ID, "cable_connectable"));

    private ModBlockTags() {}
}
