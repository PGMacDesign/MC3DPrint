package com.pgmacdesign.mc3dprint.registry;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.fu.IFilamentSource;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

/**
 * The mod's custom {@link IFilamentSource} capability — the Filament-Unit
 * counterpart to {@code ForgeCapabilities.ENERGY}. Racks expose it as a source;
 * the cable relays it; printers query it. Forge already registers ENERGY, so
 * only this one needs declaring.
 */
@Mod.EventBusSubscriber(modid = MC3DPrint.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModCapabilities {
    public static final Capability<IFilamentSource> FILAMENT_SOURCE =
            CapabilityManager.get(new CapabilityToken<>() {});

    @SubscribeEvent
    public static void register(RegisterCapabilitiesEvent event) {
        event.register(IFilamentSource.class);
    }

    private ModCapabilities() {}
}
