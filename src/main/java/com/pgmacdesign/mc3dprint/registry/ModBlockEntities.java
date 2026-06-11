package com.pgmacdesign.mc3dprint.registry;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.machine.PrinterBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MC3DPrint.MOD_ID);

    public static final RegistryObject<BlockEntityType<PrinterBlockEntity>> TIER1_PRINTER =
            BLOCK_ENTITIES.register("tier1_printer", () ->
                    BlockEntityType.Builder.of(PrinterBlockEntity::new, ModBlocks.TIER1_PRINTER.get()).build(null));

    private ModBlockEntities() {}
}
