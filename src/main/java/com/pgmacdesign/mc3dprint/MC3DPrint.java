package com.pgmacdesign.mc3dprint;

import com.pgmacdesign.mc3dprint.config.MC3DPrintConfig;
import com.pgmacdesign.mc3dprint.fu.FuValueRegistry;
import com.pgmacdesign.mc3dprint.registry.ModBlockEntities;
import com.pgmacdesign.mc3dprint.registry.ModBlocks;
import com.pgmacdesign.mc3dprint.registry.ModCreativeTabs;
import com.pgmacdesign.mc3dprint.registry.ModItems;
import com.pgmacdesign.mc3dprint.registry.ModMenuTypes;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(MC3DPrint.MOD_ID)
public class MC3DPrint {
    public static final String MOD_ID = "mc3dprint";

    public MC3DPrint(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();

        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModMenuTypes.MENU_TYPES.register(modEventBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);
        com.pgmacdesign.mc3dprint.registry.ModLootModifiers.LOOT_MODIFIERS.register(modEventBus);

        context.registerConfig(ModConfig.Type.COMMON, MC3DPrintConfig.SPEC);

        modEventBus.addListener((ModConfigEvent.Reloading event) -> {
            if (event.getConfig().getSpec() == MC3DPrintConfig.SPEC) {
                FuValueRegistry.invalidate();
            }
        });

        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                com.pgmacdesign.mc3dprint.command.ImportCommand::register);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                com.pgmacdesign.mc3dprint.blueprint.CuratedBlueprints::onServerStarted);
    }
}
