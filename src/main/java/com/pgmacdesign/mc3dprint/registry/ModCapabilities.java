package com.pgmacdesign.mc3dprint.registry;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.fu.IFilamentSource;
import com.pgmacdesign.mc3dprint.machine.sorter.IWinderRouting;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The mod's custom capabilities.
 *
 * <p>{@link #FILAMENT_SOURCE} is the Filament-Unit counterpart to
 * {@code ForgeCapabilities.ENERGY}. Racks expose it as a source; the cable relays it;
 * printers query it. {@link #WINDER_ROUTING} is the sorter's topology-only winder discovery
 * over the same cable graph. Forge already registers ENERGY, so only these need declaring.
 */
@Mod.EventBusSubscriber(modid = MC3DPrint.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModCapabilities {
    public static final Capability<IFilamentSource> FILAMENT_SOURCE =
            CapabilityManager.get(new CapabilityToken<>() {});

    /**
     * Topology-only discovery for the Filament Tier Item Sorter: a winder exposes itself, a cable
     * forwards its whole network. Kept separate from {@link #FILAMENT_SOURCE} because "which
     * winders can I reach" and "drain FU from spools" are different flows over the same cable graph.
     */
    public static final Capability<IWinderRouting> WINDER_ROUTING =
            CapabilityManager.get(new CapabilityToken<>() {});

    @SubscribeEvent
    public static void register(RegisterCapabilitiesEvent event) {
        event.register(IFilamentSource.class);
        event.register(IWinderRouting.class);
    }

    private ModCapabilities() {}
}
