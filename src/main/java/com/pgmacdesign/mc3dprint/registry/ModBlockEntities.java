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

    public static final RegistryObject<BlockEntityType<com.pgmacdesign.mc3dprint.machine.FilamentConverterBlockEntity>> FILAMENT_CONVERTER =
            BLOCK_ENTITIES.register("filament_converter", () ->
                    BlockEntityType.Builder.of(com.pgmacdesign.mc3dprint.machine.FilamentConverterBlockEntity::new,
                            ModBlocks.FILAMENT_CONVERTER.get()).build(null));

    public static final RegistryObject<BlockEntityType<com.pgmacdesign.mc3dprint.machine.RemoteTerminalBlock.TerminalBlockEntity>> REMOTE_TERMINAL =
            BLOCK_ENTITIES.register("remote_terminal", () ->
                    BlockEntityType.Builder.of(com.pgmacdesign.mc3dprint.machine.RemoteTerminalBlock.TerminalBlockEntity::new,
                            ModBlocks.REMOTE_TERMINAL.get()).build(null));

    public static final RegistryObject<BlockEntityType<WinderBlockEntity>> FILAMENT_WINDER =
            BLOCK_ENTITIES.register("filament_winder", () ->
                    BlockEntityType.Builder.of(WinderBlockEntity::new,
                            ModBlocks.WINDERS.stream().map(RegistryObject::get)
                                    .toArray(net.minecraft.world.level.block.Block[]::new)).build(null));

    private ModBlockEntities() {}
}
