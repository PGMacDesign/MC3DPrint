package com.pgmacdesign.mc3dprint.client;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.registry.ModBlockEntities;
import com.pgmacdesign.mc3dprint.registry.ModMenuTypes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

//? if >=1.21.5 {
/*@EventBusSubscriber(modid = MC3DPrint.MOD_ID, value = Dist.CLIENT)
*///?} else {
@EventBusSubscriber(modid = MC3DPrint.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
//?}
public final class ClientSetup {
    private ClientSetup() {}

    @SubscribeEvent
    public static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.TIER1_PRINTER.get(), PrinterScreen::new);
        event.register(ModMenuTypes.FILAMENT_WINDER.get(), WinderScreen::new);
        event.register(ModMenuTypes.SIMPLE_GENERATOR.get(), SimpleGeneratorScreen::new);
        event.register(ModMenuTypes.REDSTONE_CLOCK.get(), RedstoneClockScreen::new);
        event.register(ModMenuTypes.BLUEPRINT_REPOSITORY.get(), BlueprintRepositoryScreen::new);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.PRINTER.get(), PrinterRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.FILAMENT_RACK.get(), FilamentRackRenderer::new);
    }
}
