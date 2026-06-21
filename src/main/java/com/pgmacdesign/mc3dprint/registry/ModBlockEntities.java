package com.pgmacdesign.mc3dprint.registry;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.machine.PrinterBlockEntity;
import com.pgmacdesign.mc3dprint.machine.WinderBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MC3DPrint.MOD_ID);

    public static final RegistryObject<BlockEntityType<PrinterBlockEntity>> PRINTER =
            BLOCK_ENTITIES.register("printer", () ->
                    BlockEntityType.Builder.of(PrinterBlockEntity::new,
                            java.util.stream.Stream.concat(
                                    ModBlocks.PRINTERS.stream(), ModBlocks.CONTROLLERS.stream())
                                    .map(RegistryObject::get)
                                    .toArray(net.minecraft.world.level.block.Block[]::new)).build(null));

    public static final RegistryObject<BlockEntityType<com.pgmacdesign.mc3dprint.machine.multiblock.CasingBlockEntity>> CASING =
            BLOCK_ENTITIES.register("casing", () ->
                    BlockEntityType.Builder.of(com.pgmacdesign.mc3dprint.machine.multiblock.CasingBlockEntity::new,
                            ModBlocks.PRINTER_CASING.get()).build(null));

    public static final RegistryObject<BlockEntityType<com.pgmacdesign.mc3dprint.machine.FilamentConverterBlockEntity>> FILAMENT_CONVERTER =
            BLOCK_ENTITIES.register("filament_converter", () ->
                    BlockEntityType.Builder.of(com.pgmacdesign.mc3dprint.machine.FilamentConverterBlockEntity::new,
                            ModBlocks.FILAMENT_CONVERTER.get()).build(null));

    public static final RegistryObject<BlockEntityType<com.pgmacdesign.mc3dprint.machine.RemoteTerminalBlock.TerminalBlockEntity>> REMOTE_TERMINAL =
            BLOCK_ENTITIES.register("remote_terminal", () ->
                    BlockEntityType.Builder.of(com.pgmacdesign.mc3dprint.machine.RemoteTerminalBlock.TerminalBlockEntity::new,
                            ModBlocks.REMOTE_TERMINAL.get()).build(null));

    public static final RegistryObject<BlockEntityType<com.pgmacdesign.mc3dprint.machine.ClockGeneratorBlockEntity>> CLOCK_GENERATOR =
            BLOCK_ENTITIES.register("clock_generator", () ->
                    BlockEntityType.Builder.of(com.pgmacdesign.mc3dprint.machine.ClockGeneratorBlockEntity::new,
                            ModBlocks.CLOCK_GENERATOR.get()).build(null));

    public static final RegistryObject<BlockEntityType<com.pgmacdesign.mc3dprint.machine.CreativeEnergyBlockEntity>> CREATIVE_ENERGY_SOURCE =
            BLOCK_ENTITIES.register("creative_energy_source", () ->
                    BlockEntityType.Builder.of(com.pgmacdesign.mc3dprint.machine.CreativeEnergyBlockEntity::new,
                            ModBlocks.CREATIVE_ENERGY_SOURCE.get()).build(null));

    public static final RegistryObject<BlockEntityType<com.pgmacdesign.mc3dprint.machine.RedstoneClockBlockEntity>> REDSTONE_CLOCK =
            BLOCK_ENTITIES.register("redstone_clock", () ->
                    BlockEntityType.Builder.of(com.pgmacdesign.mc3dprint.machine.RedstoneClockBlockEntity::new,
                            ModBlocks.REDSTONE_CLOCK.get()).build(null));

    public static final RegistryObject<BlockEntityType<com.pgmacdesign.mc3dprint.machine.rack.FilamentRackBlockEntity>> FILAMENT_RACK =
            BLOCK_ENTITIES.register("filament_rack", () ->
                    BlockEntityType.Builder.of(com.pgmacdesign.mc3dprint.machine.rack.FilamentRackBlockEntity::new,
                            ModBlocks.FILAMENT_RACK.get()).build(null));

    public static final RegistryObject<BlockEntityType<com.pgmacdesign.mc3dprint.machine.cable.MC3DCableBlockEntity>> MC3DCABLE =
            BLOCK_ENTITIES.register("mc3dcable", () ->
                    BlockEntityType.Builder.of(com.pgmacdesign.mc3dprint.machine.cable.MC3DCableBlockEntity::new,
                            ModBlocks.MC3DCABLE.get()).build(null));

    public static final RegistryObject<BlockEntityType<WinderBlockEntity>> FILAMENT_WINDER =
            BLOCK_ENTITIES.register("filament_winder", () ->
                    BlockEntityType.Builder.of(WinderBlockEntity::new,
                            ModBlocks.WINDERS.stream().map(RegistryObject::get)
                                    .toArray(net.minecraft.world.level.block.Block[]::new)).build(null));

    private ModBlockEntities() {}
}
