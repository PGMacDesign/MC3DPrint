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

    // No-arg constructor + FMLJavaModLoadingContext.get(): the universal Forge 1.20.1
    // entry point that works across the whole declared range ([47,)). Taking the context
    // as a constructor PARAMETER only works on newer Forge (47.4.x) — an older server
    // (e.g. 47.1.106) rejects it with "Could not find mod constructor" and crashes.
    public MC3DPrint() {
        FMLJavaModLoadingContext context = FMLJavaModLoadingContext.get();
        IEventBus modEventBus = context.getModEventBus();

        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModMenuTypes.MENU_TYPES.register(modEventBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);
        com.pgmacdesign.mc3dprint.registry.ModLootModifiers.LOOT_MODIFIERS.register(modEventBus);

        com.pgmacdesign.mc3dprint.advancement.ModCriteria.register();

        context.registerConfig(ModConfig.Type.COMMON, MC3DPrintConfig.SPEC);

        com.pgmacdesign.mc3dprint.network.MC3DPrintNetwork.register();
        modEventBus.addListener(com.pgmacdesign.mc3dprint.registry.ModResourcePacks::onAddPackFinders);

        modEventBus.addListener((ModConfigEvent.Reloading event) -> {
            if (event.getConfig().getSpec() == MC3DPrintConfig.SPEC) {
                FuValueRegistry.invalidate();
            }
        });

        // recipe-derived FU valuation: bind live recipes on datapack sync (server)
        // and consume cross-mod FU registrations sent over IMC.
        modEventBus.addListener(com.pgmacdesign.mc3dprint.fu.FuEvents::onInterModProcess);
        // Optional-mod FU values, each registered only when that mod is present
        // (no-op + zero footprint otherwise).
        modEventBus.addListener(com.pgmacdesign.mc3dprint.integration.draconic.DraconicCompat::onCommonSetup);
        modEventBus.addListener(com.pgmacdesign.mc3dprint.integration.ae2.Ae2Compat::onCommonSetup);
        modEventBus.addListener(com.pgmacdesign.mc3dprint.integration.thermal.ThermalCompat::onCommonSetup);
        modEventBus.addListener(com.pgmacdesign.mc3dprint.integration.tinkers.TinkersCompat::onCommonSetup);
        modEventBus.addListener(com.pgmacdesign.mc3dprint.integration.mekanism.MekanismCompat::onCommonSetup);
        modEventBus.addListener(com.pgmacdesign.mc3dprint.integration.botania.BotaniaCompat::onCommonSetup);
        modEventBus.addListener(com.pgmacdesign.mc3dprint.integration.create.CreateCompat::onCommonSetup);
        modEventBus.addListener(com.pgmacdesign.mc3dprint.integration.immersiveengineering.ImmersiveEngineeringCompat::onCommonSetup);
        modEventBus.addListener(com.pgmacdesign.mc3dprint.integration.enderio.EnderIOCompat::onCommonSetup);
        modEventBus.addListener(com.pgmacdesign.mc3dprint.integration.mysticalagriculture
                .MysticalAgricultureCompat::onCommonSetup);
        modEventBus.addListener(com.pgmacdesign.mc3dprint.integration.mysticalagriculture
                .MysticalAgradditionsCompat::onCommonSetup);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                com.pgmacdesign.mc3dprint.fu.FuEvents::onServerStarted);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                com.pgmacdesign.mc3dprint.fu.FuEvents::onDatapackSync);

        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                com.pgmacdesign.mc3dprint.command.ImportCommand::register);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                com.pgmacdesign.mc3dprint.command.DiscoveryCommand::register);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                com.pgmacdesign.mc3dprint.blueprint.CuratedBlueprints::onServerStarted);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                com.pgmacdesign.mc3dprint.integration.patchouli.GuidebookAutoGive::onItemCrafted);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                com.pgmacdesign.mc3dprint.integration.patchouli.GuidebookAutoGive::onContainerClose);
        // Breaking a premium multiblock corner (T5 diamond, T8 awakened draconium) must
        // unform the machine like breaking a casing does; those are foreign blocks with
        // no onRemove hook of ours, so catch their break here.
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                com.pgmacdesign.mc3dprint.machine.multiblock.ControllerBlock::onBlockBreak);
    }
}
