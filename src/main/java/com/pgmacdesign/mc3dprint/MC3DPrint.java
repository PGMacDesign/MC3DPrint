package com.pgmacdesign.mc3dprint;

import com.pgmacdesign.mc3dprint.config.MC3DPrintConfig;
import com.pgmacdesign.mc3dprint.fu.FuValueRegistry;
import com.pgmacdesign.mc3dprint.registry.ModBlockEntities;
import com.pgmacdesign.mc3dprint.registry.ModBlocks;
import com.pgmacdesign.mc3dprint.registry.ModCreativeTabs;
import com.pgmacdesign.mc3dprint.registry.ModItems;
import com.pgmacdesign.mc3dprint.registry.ModMenuTypes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.ModContainer;

@Mod(MC3DPrint.MOD_ID)
public class MC3DPrint {
    public static final String MOD_ID = "mc3dprint";

    // NeoForge injects the mod event bus + container into the @Mod constructor
    // (replacing Forge's FMLJavaModLoadingContext.get()).
    public MC3DPrint(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        com.pgmacdesign.mc3dprint.registry.ModDataComponents.COMPONENTS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModMenuTypes.MENU_TYPES.register(modEventBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);
        com.pgmacdesign.mc3dprint.registry.ModLootModifiers.LOOT_MODIFIERS.register(modEventBus);

        com.pgmacdesign.mc3dprint.advancement.ModCriteria.TRIGGERS.register(modEventBus);

        modContainer.registerConfig(ModConfig.Type.COMMON, MC3DPrintConfig.SPEC);

        modEventBus.addListener(com.pgmacdesign.mc3dprint.network.MC3DPrintNetwork::register);
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
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                com.pgmacdesign.mc3dprint.fu.FuEvents::onServerStarted);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                com.pgmacdesign.mc3dprint.fu.FuEvents::onDatapackSync);

        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                com.pgmacdesign.mc3dprint.command.ImportCommand::register);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                com.pgmacdesign.mc3dprint.command.DiscoveryCommand::register);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                com.pgmacdesign.mc3dprint.command.GuideCommand::register);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                com.pgmacdesign.mc3dprint.blueprint.CuratedBlueprints::onServerStarted);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                com.pgmacdesign.mc3dprint.integration.patchouli.GuidebookAutoGive::onItemCrafted);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                com.pgmacdesign.mc3dprint.integration.patchouli.GuidebookAutoGive::onContainerClose);
        // Breaking a premium multiblock corner (T5 diamond, T8 awakened draconium) must
        // unform the machine like breaking a casing does; those are foreign blocks with
        // no onRemove hook of ours, so catch their break here.
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                com.pgmacdesign.mc3dprint.machine.multiblock.ControllerBlock::onBlockBreak);
    }
}
