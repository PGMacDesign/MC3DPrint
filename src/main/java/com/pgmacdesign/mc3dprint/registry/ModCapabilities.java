package com.pgmacdesign.mc3dprint.registry;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.fu.IFilamentSource;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * The mod's custom {@link IFilamentSource} capability — the Filament-Unit
 * counterpart to {@code Capabilities.EnergyStorage}. Racks expose it as a source;
 * the cable relays it; printers query it.
 *
 * <p>Under the NeoForge model every block-entity capability is registered
 * centrally in {@link #register} via {@code event.registerBlockEntity}, rather
 * than each BE overriding {@code getCapability}. The BEs expose plain accessor
 * methods returning the raw capability object, wired up here per block-entity type.
 */
//? if >=1.21.5 {
/*@EventBusSubscriber(modid = MC3DPrint.MOD_ID)
*///?} else {
@EventBusSubscriber(modid = MC3DPrint.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
//?}
public final class ModCapabilities {
    public static final BlockCapability<IFilamentSource, Direction> FILAMENT_SOURCE =
            BlockCapability.createSided(
                    ResourceLocation.fromNamespaceAndPath(MC3DPrint.MOD_ID, "filament_source"),
                    IFilamentSource.class);

    @SubscribeEvent
    public static void register(RegisterCapabilitiesEvent event) {
        // --- Energy ---
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.MC3DCABLE.get(),
                (be, side) -> be.getEnergyStorage());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.PRINTER.get(),
                (be, side) -> be.getEnergyStorage());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.FILAMENT_CONVERTER.get(),
                (be, side) -> be.getEnergyStorage());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.FILAMENT_WINDER.get(),
                (be, side) -> be.getEnergyStorage());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.CLOCK_GENERATOR.get(),
                (be, side) -> be.getEnergyStorage());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.CREATIVE_ENERGY_SOURCE.get(),
                (be, side) -> be.getEnergyStorage());
        // Casing re-exposes the formed controller's energy (the power-inlet forwarding).
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.CASING.get(),
                (be, side) -> be.getEnergyStorage(side));

        // --- Filament source (custom) ---
        event.registerBlockEntity(FILAMENT_SOURCE, ModBlockEntities.MC3DCABLE.get(),
                (be, side) -> be.getFilamentSource());
        event.registerBlockEntity(FILAMENT_SOURCE, ModBlockEntities.FILAMENT_RACK.get(),
                (be, side) -> be.getFilamentSource());

        // --- Item handler (per-face logic lives in each BE's getItemHandler) ---
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.PRINTER.get(),
                (be, side) -> be.getItemHandler(side));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.FILAMENT_WINDER.get(),
                (be, side) -> be.getItemHandler(side));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.CLOCK_GENERATOR.get(),
                (be, side) -> be.getItemHandler(side));
    }

    private ModCapabilities() {}
}
