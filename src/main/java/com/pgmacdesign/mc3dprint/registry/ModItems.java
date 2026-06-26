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
import net.neoforged.neoforge.registries.DeferredHolder;

public final class ModItems {
    // DeferredRegister.Items so registerItem(name, factory, props) stamps each item's registry id
    // onto its Properties — MANDATORY in 1.21.2+ (an unset id throws at construction), a no-op on
    // 1.21.1. registerItem (generic) is used rather than registerSimpleBlockItem so the returned
    // holder stays DeferredHolder<Item, Item> (the latter is fixed to DeferredItem<BlockItem>).
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(MC3DPrint.MOD_ID);

    /** Printer block items, index 0 = Tier 1. */
    public static final List<DeferredHolder<Item, Item>> PRINTERS = buildBlockItems(ModBlocks.PRINTERS);

    /** Winder block items, index 0 = Tier 1. */
    public static final List<DeferredHolder<Item, Item>> WINDERS = buildBlockItems(ModBlocks.WINDERS);

    public static final DeferredHolder<Item, Item> TIER1_PRINTER = PRINTERS.get(0);

    private static List<DeferredHolder<Item, Item>> buildBlockItems(
            List<net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.level.block.Block, net.minecraft.world.level.block.Block>> blocks) {
        List<DeferredHolder<Item, Item>> items = new ArrayList<>(blocks.size());
        for (var block : blocks) {
            items.add(ITEMS.registerItem(block.getId().getPath(),
                    props -> new BlockItem(block.get(), props), new Item.Properties()));
        }
        return List.copyOf(items);
    }

    public static final DeferredHolder<Item, Item> BLANK_BLUEPRINT_DISC = ITEMS.registerItem("blank_blueprint_disc",
            Item::new, new Item.Properties().stacksTo(16));

    public static final DeferredHolder<Item, Item> BLUEPRINT_DISC = ITEMS.registerItem("blueprint_disc",
            BlueprintDiscItem::new, new Item.Properties().stacksTo(1));

    public static final DeferredHolder<Item, Item> SCANNER = ITEMS.registerItem("scanner",
            ScannerItem::new, new Item.Properties().stacksTo(1));

    public static final DeferredHolder<Item, Item> FILAMENT_WINDER = WINDERS.get(0);

    public static final DeferredHolder<Item, Item> PRINTER_CASING = ITEMS.registerItem("printer_casing",
            props -> new BlockItem(ModBlocks.PRINTER_CASING.get(), props), new Item.Properties());

    public static final DeferredHolder<Item, Item> FILAMENT_CONVERTER = ITEMS.registerItem("filament_converter",
            props -> new BlockItem(ModBlocks.FILAMENT_CONVERTER.get(), props), new Item.Properties());

    public static final DeferredHolder<Item, Item> REMOTE_TERMINAL = ITEMS.registerItem("remote_terminal",
            props -> new com.pgmacdesign.mc3dprint.machine.RemoteTerminalBlock.TerminalBlockItem(
                    ModBlocks.REMOTE_TERMINAL.get(), props), new Item.Properties());

    public static final DeferredHolder<Item, Item> FILAMENT_RACK = ITEMS.registerItem("filament_rack",
            props -> new BlockItem(ModBlocks.FILAMENT_RACK.get(), props), new Item.Properties());

    public static final DeferredHolder<Item, Item> MC3DCABLE = ITEMS.registerItem("mc3dcable",
            props -> new BlockItem(ModBlocks.MC3DCABLE.get(), props), new Item.Properties());

    public static final DeferredHolder<Item, Item> BLUEPRINT_REPOSITORY = ITEMS.registerItem("blueprint_repository",
            props -> new BlockItem(ModBlocks.BLUEPRINT_REPOSITORY.get(), props), new Item.Properties());

    public static final DeferredHolder<Item, Item> CLOCK_GENERATOR = ITEMS.registerItem("clock_generator",
            props -> new BlockItem(ModBlocks.CLOCK_GENERATOR.get(), props), new Item.Properties());

    public static final DeferredHolder<Item, Item> REDSTONE_CLOCK = ITEMS.registerItem("redstone_clock",
            props -> new BlockItem(ModBlocks.REDSTONE_CLOCK.get(), props), new Item.Properties());

    public static final DeferredHolder<Item, Item> CREATIVE_ENERGY_SOURCE = ITEMS.registerItem("creative_energy_source",
            props -> new BlockItem(ModBlocks.CREATIVE_ENERGY_SOURCE.get(), props), new Item.Properties());

    public static final DeferredHolder<Item, Item> CREATIVE_SPOOL = ITEMS.registerItem("creative_filament_spool",
            com.pgmacdesign.mc3dprint.fu.CreativeSpoolItem::new, new Item.Properties());

    public static final DeferredHolder<Item, Item> EXTRUDIUM_ORE = ITEMS.registerItem("extrudium_ore",
            props -> new BlockItem(ModBlocks.EXTRUDIUM_ORE.get(), props), new Item.Properties());

    public static final DeferredHolder<Item, Item> EXTRUDIUM_CRYSTAL = ITEMS.registerItem("extrudium_crystal",
            Item::new, new Item.Properties());

    public static final DeferredHolder<Item, Item> SPEED_UPGRADE = ITEMS.registerItem("speed_upgrade",
            props -> new UpgradeItem(UpgradeItem.Type.SPEED, props), new Item.Properties().stacksTo(16));
    public static final DeferredHolder<Item, Item> EFFICIENCY_UPGRADE = ITEMS.registerItem("efficiency_upgrade",
            props -> new UpgradeItem(UpgradeItem.Type.EFFICIENCY, props), new Item.Properties().stacksTo(16));
    public static final DeferredHolder<Item, Item> RF_EFFICIENCY_UPGRADE = ITEMS.registerItem("rf_efficiency_upgrade",
            props -> new UpgradeItem(UpgradeItem.Type.RF_EFFICIENCY, props), new Item.Properties().stacksTo(16));
    public static final DeferredHolder<Item, Item> BUFFER_UPGRADE = ITEMS.registerItem("buffer_upgrade",
            props -> new UpgradeItem(UpgradeItem.Type.BUFFER, props), new Item.Properties().stacksTo(16));

    /** Crafting intermediate for all craftable resins (extrudium crystal + slime). */
    public static final DeferredHolder<Item, Item> RESIN_BASE = ITEMS.registerItem("resin_base",
            Item::new, new Item.Properties());

    /** All resin variants (effect × its valid tiers — the gated matrix). 11 items. */
    public static final List<DeferredHolder<Item, Item>> RESINS = buildResins();

    private static List<DeferredHolder<Item, Item>> buildResins() {
        List<DeferredHolder<Item, Item>> resins = new ArrayList<>();
        for (ResinItem.Effect effect : ResinItem.Effect.values()) {
            for (int tier : effect.tiers()) {
                final ResinItem.Effect e = effect;
                final int t = tier;
                resins.add(ITEMS.registerItem(ResinItem.registryId(e, t),
                        props -> new ResinItem(e, t, props), new Item.Properties().stacksTo(64)));
            }
        }
        return List.copyOf(resins);
    }

    /** Fabricator (controller) items, index 0 = Tier 5. Collapsed stacks re-form the multiblock. */
    public static final List<DeferredHolder<Item, Item>> FABRICATORS = buildFabricators();

    private static List<DeferredHolder<Item, Item>> buildFabricators() {
        List<DeferredHolder<Item, Item>> fabricators = new ArrayList<>(ModBlocks.CONTROLLERS.size());
        for (var controller : ModBlocks.CONTROLLERS) {
            fabricators.add(ITEMS.registerItem(controller.getId().getPath(),
                    props -> new FabricatorBlockItem(controller.get(), props), new Item.Properties().stacksTo(1)));
        }
        return List.copyOf(fabricators);
    }

    /** Filament spools, index 0 = Tier 1. */
    public static final List<DeferredHolder<Item, Item>> SPOOLS = buildSpools();

    private static List<DeferredHolder<Item, Item>> buildSpools() {
        List<DeferredHolder<Item, Item>> spools = new ArrayList<>(SpoolItem.CAPACITY_BY_TIER.length);
        for (int tier = 1; tier <= SpoolItem.CAPACITY_BY_TIER.length; tier++) {
            final int t = tier;
            spools.add(ITEMS.registerItem("filament_spool_t" + tier,
                    props -> new SpoolItem(t, props), new Item.Properties()));
        }
        return List.copyOf(spools);
    }

    private ModItems() {}
}
