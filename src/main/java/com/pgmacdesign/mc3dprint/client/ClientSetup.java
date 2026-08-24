package com.pgmacdesign.mc3dprint.client;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.registry.ModBlockEntities;
import com.pgmacdesign.mc3dprint.registry.ModMenuTypes;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = MC3DPrint.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ModMenuTypes.TIER1_PRINTER.get(), PrinterScreen::new);
            MenuScreens.register(ModMenuTypes.FILAMENT_WINDER.get(), WinderScreen::new);
            MenuScreens.register(ModMenuTypes.SIMPLE_GENERATOR.get(), SimpleGeneratorScreen::new);
            MenuScreens.register(ModMenuTypes.REDSTONE_CLOCK.get(), RedstoneClockScreen::new);
            MenuScreens.register(ModMenuTypes.BLUEPRINT_REPOSITORY.get(), BlueprintRepositoryScreen::new);
            MenuScreens.register(ModMenuTypes.FILAMENT_ITEM_SORTER.get(), SorterScreen::new);
            MenuScreens.register(ModMenuTypes.MC3DPRINT_TERMINAL.get(), MC3DPrintTerminalScreen::new);
        });
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.PRINTER.get(), PrinterRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.FILAMENT_RACK.get(), FilamentRackRenderer::new);
    }
}
