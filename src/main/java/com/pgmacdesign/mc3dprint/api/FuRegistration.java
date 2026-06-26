package com.pgmacdesign.mc3dprint.api;

import net.minecraft.resources.ResourceLocation;

/**
 * Immutable payload for registering a Filament Unit value through Forge IMC.
 *
 * <p>Send it from your mod with:
 * <pre>{@code
 * InterModComms.sendTo("mc3dprint", MC3DPrintAPI.IMC_REGISTER_FU_VALUE,
 *         () -> new FuRegistration(ResourceLocation.fromNamespaceAndPath("yourmod", "ruby"), 60, 4));
 * }</pre>
 *
 * @param item the registry id of the item (e.g. {@code yourmod:ruby})
 * @param fu   the Filament Unit value (clamped to &gt;= 1 on receipt)
 * @param tier the minimum machine/spool tier required (clamped to 1..8)
 */
public record FuRegistration(ResourceLocation item, int fu, int tier) {
}
