package com.pgmacdesign.mc3dprint.registry;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.compat.RegistryCompat;
import com.pgmacdesign.mc3dprint.machine.PrinterBlockEntity;
import com.pgmacdesign.mc3dprint.machine.WinderBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MC3DPrint.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PrinterBlockEntity>> PRINTER =
            BLOCK_ENTITIES.register("printer", () ->
                    RegistryCompat.blockEntityType(PrinterBlockEntity::new,
                            java.util.stream.Stream.concat(
                                    ModBlocks.PRINTERS.stream(), ModBlocks.CONTROLLERS.stream())
                                    .map(DeferredHolder::get)
                                    .toArray(net.minecraft.world.level.block.Block[]::new)));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.pgmacdesign.mc3dprint.machine.multiblock.CasingBlockEntity>> CASING =
            BLOCK_ENTITIES.register("casing", () ->
                    RegistryCompat.blockEntityType(com.pgmacdesign.mc3dprint.machine.multiblock.CasingBlockEntity::new,
                            ModBlocks.PRINTER_CASING.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.pgmacdesign.mc3dprint.machine.FilamentConverterBlockEntity>> FILAMENT_CONVERTER =
            BLOCK_ENTITIES.register("filament_converter", () ->
                    RegistryCompat.blockEntityType(com.pgmacdesign.mc3dprint.machine.FilamentConverterBlockEntity::new,
                            ModBlocks.FILAMENT_CONVERTER.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.pgmacdesign.mc3dprint.machine.RemoteTerminalBlock.TerminalBlockEntity>> REMOTE_TERMINAL =
            BLOCK_ENTITIES.register("remote_terminal", () ->
                    RegistryCompat.blockEntityType(com.pgmacdesign.mc3dprint.machine.RemoteTerminalBlock.TerminalBlockEntity::new,
                            ModBlocks.REMOTE_TERMINAL.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.pgmacdesign.mc3dprint.machine.ClockGeneratorBlockEntity>> CLOCK_GENERATOR =
            BLOCK_ENTITIES.register("clock_generator", () ->
                    RegistryCompat.blockEntityType(com.pgmacdesign.mc3dprint.machine.ClockGeneratorBlockEntity::new,
                            ModBlocks.CLOCK_GENERATOR.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.pgmacdesign.mc3dprint.machine.CreativeEnergyBlockEntity>> CREATIVE_ENERGY_SOURCE =
            BLOCK_ENTITIES.register("creative_energy_source", () ->
                    RegistryCompat.blockEntityType(com.pgmacdesign.mc3dprint.machine.CreativeEnergyBlockEntity::new,
                            ModBlocks.CREATIVE_ENERGY_SOURCE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.pgmacdesign.mc3dprint.machine.RedstoneClockBlockEntity>> REDSTONE_CLOCK =
            BLOCK_ENTITIES.register("redstone_clock", () ->
                    RegistryCompat.blockEntityType(com.pgmacdesign.mc3dprint.machine.RedstoneClockBlockEntity::new,
                            ModBlocks.REDSTONE_CLOCK.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.pgmacdesign.mc3dprint.machine.rack.FilamentRackBlockEntity>> FILAMENT_RACK =
            BLOCK_ENTITIES.register("filament_rack", () ->
                    RegistryCompat.blockEntityType(com.pgmacdesign.mc3dprint.machine.rack.FilamentRackBlockEntity::new,
                            ModBlocks.FILAMENT_RACK.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.pgmacdesign.mc3dprint.machine.cable.MC3DCableBlockEntity>> MC3DCABLE =
            BLOCK_ENTITIES.register("mc3dcable", () ->
                    RegistryCompat.blockEntityType(com.pgmacdesign.mc3dprint.machine.cable.MC3DCableBlockEntity::new,
                            ModBlocks.MC3DCABLE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.pgmacdesign.mc3dprint.machine.repository.BlueprintRepositoryBlockEntity>> BLUEPRINT_REPOSITORY =
            BLOCK_ENTITIES.register("blueprint_repository", () ->
                    RegistryCompat.blockEntityType(com.pgmacdesign.mc3dprint.machine.repository.BlueprintRepositoryBlockEntity::new,
                            ModBlocks.BLUEPRINT_REPOSITORY.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.pgmacdesign.mc3dprint.machine.sorter.SorterBlockEntity>> FILAMENT_ITEM_SORTER =
            BLOCK_ENTITIES.register("filament_item_sorter", () ->
                    RegistryCompat.blockEntityType(com.pgmacdesign.mc3dprint.machine.sorter.SorterBlockEntity::new,
                            ModBlocks.FILAMENT_ITEM_SORTER.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WinderBlockEntity>> FILAMENT_WINDER =
            BLOCK_ENTITIES.register("filament_winder", () ->
                    RegistryCompat.blockEntityType(WinderBlockEntity::new,
                            ModBlocks.WINDERS.stream().map(DeferredHolder::get)
                                    .toArray(net.minecraft.world.level.block.Block[]::new)));

    private ModBlockEntities() {}
}
