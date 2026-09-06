package com.pgmacdesign.mc3dprint.registry;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.compat.TransferCompat;
import com.pgmacdesign.mc3dprint.fu.IFilamentSource;
import com.pgmacdesign.mc3dprint.machine.sorter.IWinderRouting;
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

    /**
     * Topology-only discovery for the Filament Tier Item Sorter: a winder exposes itself, a cable
     * forwards its whole network. Kept separate from {@link #FILAMENT_SOURCE} because "which
     * winders can I reach" and "drain FU from spools" are different flows over the same cable graph.
     */
    public static final BlockCapability<IWinderRouting, Direction> WINDER_ROUTING =
            BlockCapability.createSided(
                    ResourceLocation.fromNamespaceAndPath(MC3DPrint.MOD_ID, "winder_routing"),
                    IWinderRouting.class);

    @SubscribeEvent
    public static void register(RegisterCapabilitiesEvent event) {
        // --- Energy ---
        //? if >=1.21.9 {
        /*event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.MC3DCABLE.get(),
                (be, side) -> TransferCompat.energyHandler(be.getEnergyStorage()));
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.PRINTER.get(),
                (be, side) -> TransferCompat.energyHandler(be.getEnergyStorage()));
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.FILAMENT_WINDER.get(),
                (be, side) -> TransferCompat.energyHandler(be.getEnergyStorage()));
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.CLOCK_GENERATOR.get(),
                (be, side) -> TransferCompat.energyHandler(be.getEnergyStorage()));
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.CREATIVE_ENERGY_SOURCE.get(),
                (be, side) -> TransferCompat.energyHandler(be.getEnergyStorage()));
        // Casing re-exposes the formed controller's energy (the power-inlet forwarding).
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.CASING.get(),
                (be, side) -> {
                    var storage = be.getEnergyStorage(side);
                    return storage == null ? null : TransferCompat.energyHandler(storage);
                });
        *///?} else {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.MC3DCABLE.get(),
                (be, side) -> be.getEnergyStorage());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.PRINTER.get(),
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
        //?}

        // --- Filament source (custom) ---
        event.registerBlockEntity(FILAMENT_SOURCE, ModBlockEntities.MC3DCABLE.get(),
                (be, side) -> be.getFilamentSource());
        event.registerBlockEntity(FILAMENT_SOURCE, ModBlockEntities.FILAMENT_RACK.get(),
                (be, side) -> be.getFilamentSource());

        // --- Winder routing (custom) — a winder exposes itself; the cable forwards its network ---
        event.registerBlockEntity(WINDER_ROUTING, ModBlockEntities.FILAMENT_WINDER.get(),
                (be, side) -> be.getWinderRouting());
        event.registerBlockEntity(WINDER_ROUTING, ModBlockEntities.MC3DCABLE.get(),
                (be, side) -> be.getWinderRouting());

        // --- Item handler (per-face logic lives in each BE's getItemHandler) ---
        //? if >=1.21.9 {
        /*// The BEs declare IItemHandler but always hand back ItemStackHandler/RangedWrapper,
        // both IItemHandlerModifiable — required by the bridge's snapshot revert. The
        // instanceof keeps the cast safe if a future view ever isn't modifiable.
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.PRINTER.get(),
                (be, side) -> be.getItemHandler(side) instanceof net.neoforged.neoforge.items.IItemHandlerModifiable m
                        ? TransferCompat.itemHandler(m) : null);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.FILAMENT_WINDER.get(),
                (be, side) -> be.getItemHandler(side) instanceof net.neoforged.neoforge.items.IItemHandlerModifiable m
                        ? TransferCompat.itemHandler(m) : null);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.CLOCK_GENERATOR.get(),
                (be, side) -> be.getItemHandler(side) instanceof net.neoforged.neoforge.items.IItemHandlerModifiable m
                        ? TransferCompat.itemHandler(m) : null);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.FILAMENT_ITEM_SORTER.get(),
                (be, side) -> be.getItemHandler(side) instanceof net.neoforged.neoforge.items.IItemHandlerModifiable m
                        ? TransferCompat.itemHandler(m) : null);
        *///?} else {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.PRINTER.get(),
                (be, side) -> be.getItemHandler(side));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.FILAMENT_WINDER.get(),
                (be, side) -> be.getItemHandler(side));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.CLOCK_GENERATOR.get(),
                (be, side) -> be.getItemHandler(side));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.FILAMENT_ITEM_SORTER.get(),
                (be, side) -> be.getItemHandler(side));
        //?}
    }

    private ModCapabilities() {}
}
