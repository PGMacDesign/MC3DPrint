package com.pgmacdesign.mc3dprint.registry;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.fu.SpoolItem;
import com.pgmacdesign.mc3dprint.item.BlueprintDiscItem;
import com.pgmacdesign.mc3dprint.item.ResinItem;
import com.pgmacdesign.mc3dprint.machine.multiblock.FabricatorBlockItem;
import com.pgmacdesign.mc3dprint.machine.upgrade.UpgradeItem;
import com.pgmacdesign.mc3dprint.scanner.ScannerItem;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, MC3DPrint.MOD_ID);

    /** Printer block items, index 0 = Tier 1. */
    public static final List<DeferredHolder<Item, Item>> PRINTERS = buildBlockItems(ModBlocks.PRINTERS);

    /** Winder block items, index 0 = Tier 1. */
    public static final List<DeferredHolder<Item, Item>> WINDERS = buildBlockItems(ModBlocks.WINDERS);

    public static final DeferredHolder<Item, Item> TIER1_PRINTER = PRINTERS.get(0);

    private static List<DeferredHolder<Item, Item>> buildBlockItems(
            List<net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.level.block.Block, net.minecraft.world.level.block.Block>> blocks) {
        List<DeferredHolder<Item, Item>> items = new ArrayList<>(blocks.size());
        for (var block : blocks) {
            items.add(ITEMS.register(block.getId().getPath(),
                    () -> new BlockItem(block.get(), new Item.Properties())));
        }
        return List.copyOf(items);
    }

    public static final DeferredHolder<Item, Item> BLANK_BLUEPRINT_DISC = ITEMS.register("blank_blueprint_disc",
            () -> new Item(new Item.Properties().stacksTo(16)));

    public static final DeferredHolder<Item, Item> BLUEPRINT_DISC = ITEMS.register("blueprint_disc",
            () -> new BlueprintDiscItem(new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<Item, Item> SCANNER = ITEMS.register("scanner",
            () -> new ScannerItem(new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<Item, Item> FILAMENT_WINDER = WINDERS.get(0);

    public static final DeferredHolder<Item, Item> PRINTER_CASING = ITEMS.register("printer_casing",
            () -> new BlockItem(ModBlocks.PRINTER_CASING.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> FILAMENT_CONVERTER = ITEMS.register("filament_converter",
            () -> new BlockItem(ModBlocks.FILAMENT_CONVERTER.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> REMOTE_TERMINAL = ITEMS.register("remote_terminal",
            () -> new com.pgmacdesign.mc3dprint.machine.RemoteTerminalBlock.TerminalBlockItem(
                    ModBlocks.REMOTE_TERMINAL.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> FILAMENT_RACK = ITEMS.register("filament_rack",
            () -> new BlockItem(ModBlocks.FILAMENT_RACK.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> MC3DCABLE = ITEMS.register("mc3dcable",
            () -> new BlockItem(ModBlocks.MC3DCABLE.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> BLUEPRINT_REPOSITORY = ITEMS.register("blueprint_repository",
            () -> new BlockItem(ModBlocks.BLUEPRINT_REPOSITORY.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> CLOCK_GENERATOR = ITEMS.register("clock_generator",
            () -> new BlockItem(ModBlocks.CLOCK_GENERATOR.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> REDSTONE_CLOCK = ITEMS.register("redstone_clock",
            () -> new BlockItem(ModBlocks.REDSTONE_CLOCK.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> CREATIVE_ENERGY_SOURCE = ITEMS.register("creative_energy_source",
            () -> new BlockItem(ModBlocks.CREATIVE_ENERGY_SOURCE.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> CREATIVE_SPOOL = ITEMS.register("creative_filament_spool",
            () -> new com.pgmacdesign.mc3dprint.fu.CreativeSpoolItem(new Item.Properties()));

    public static final DeferredHolder<Item, Item> EXTRUDIUM_ORE = ITEMS.register("extrudium_ore",
            () -> new BlockItem(ModBlocks.EXTRUDIUM_ORE.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> EXTRUDIUM_CRYSTAL = ITEMS.register("extrudium_crystal",
            () -> new Item(new Item.Properties()));

    public static final DeferredHolder<Item, Item> SPEED_UPGRADE = ITEMS.register("speed_upgrade",
            () -> new UpgradeItem(UpgradeItem.Type.SPEED, new Item.Properties().stacksTo(16)));
    public static final DeferredHolder<Item, Item> EFFICIENCY_UPGRADE = ITEMS.register("efficiency_upgrade",
            () -> new UpgradeItem(UpgradeItem.Type.EFFICIENCY, new Item.Properties().stacksTo(16)));
    public static final DeferredHolder<Item, Item> RF_EFFICIENCY_UPGRADE = ITEMS.register("rf_efficiency_upgrade",
            () -> new UpgradeItem(UpgradeItem.Type.RF_EFFICIENCY, new Item.Properties().stacksTo(16)));
    public static final DeferredHolder<Item, Item> BUFFER_UPGRADE = ITEMS.register("buffer_upgrade",
            () -> new UpgradeItem(UpgradeItem.Type.BUFFER, new Item.Properties().stacksTo(16)));

    /** Crafting intermediate for all craftable resins (extrudium crystal + slime). */
    public static final DeferredHolder<Item, Item> RESIN_BASE = ITEMS.register("resin_base",
            () -> new Item(new Item.Properties()));

    /** All resin variants (effect × its valid tiers — the gated matrix). 11 items. */
    public static final List<DeferredHolder<Item, Item>> RESINS = buildResins();

    private static List<DeferredHolder<Item, Item>> buildResins() {
        List<DeferredHolder<Item, Item>> resins = new ArrayList<>();
        for (ResinItem.Effect effect : ResinItem.Effect.values()) {
            for (int tier : effect.tiers()) {
                final ResinItem.Effect e = effect;
                final int t = tier;
                resins.add(ITEMS.register(ResinItem.registryId(e, t),
                        () -> new ResinItem(e, t, new Item.Properties().stacksTo(64))));
            }
        }
        return List.copyOf(resins);
    }

    /** Fabricator (controller) items, index 0 = Tier 5. Collapsed stacks re-form the multiblock. */
    public static final List<DeferredHolder<Item, Item>> FABRICATORS = buildFabricators();

    private static List<DeferredHolder<Item, Item>> buildFabricators() {
        List<DeferredHolder<Item, Item>> fabricators = new ArrayList<>(ModBlocks.CONTROLLERS.size());
        for (var controller : ModBlocks.CONTROLLERS) {
            fabricators.add(ITEMS.register(controller.getId().getPath(),
                    () -> new FabricatorBlockItem(controller.get(), new Item.Properties().stacksTo(1))));
        }
        return List.copyOf(fabricators);
    }

    /** Filament spools, index 0 = Tier 1. */
    public static final List<DeferredHolder<Item, Item>> SPOOLS = buildSpools();

    private static List<DeferredHolder<Item, Item>> buildSpools() {
        List<DeferredHolder<Item, Item>> spools = new ArrayList<>(SpoolItem.CAPACITY_BY_TIER.length);
        for (int tier = 1; tier <= SpoolItem.CAPACITY_BY_TIER.length; tier++) {
            final int t = tier;
            spools.add(ITEMS.register("filament_spool_t" + tier,
                    () -> new SpoolItem(t, new Item.Properties())));
        }
        return List.copyOf(spools);
    }

    private ModItems() {}
}
