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
            items.add(com.pgmacdesign.mc3dprint.compat.RegistryCompat.registerItem(ITEMS, block.getId().getPath(),
                    props -> new BlockItem(block.get(), props), blockItemProps()));
        }
        return List.copyOf(items);
    }

    /**
     * Block-prefixed {@code Item.Properties} for a BlockItem so its name resolves via the
     * {@code block.<ns>.<id>} lang key on 1.21.2+ (a pass-through on 1.21.1). Use for every
     * BlockItem; without it 1.21.2+ shows the raw {@code item.mc3dprint.<id>} key in-game.
     */
    private static Item.Properties blockItemProps() {
        return com.pgmacdesign.mc3dprint.compat.RegistryCompat.blockItem(new Item.Properties());
    }

    public static final DeferredHolder<Item, Item> BLANK_BLUEPRINT_DISC = com.pgmacdesign.mc3dprint.compat.RegistryCompat.registerItem(ITEMS, "blank_blueprint_disc",
            Item::new, new Item.Properties().stacksTo(16));

    public static final DeferredHolder<Item, Item> BLUEPRINT_DISC = com.pgmacdesign.mc3dprint.compat.RegistryCompat.registerItem(ITEMS, "blueprint_disc",
            BlueprintDiscItem::new, new Item.Properties().stacksTo(1));

    public static final DeferredHolder<Item, Item> SCANNER = com.pgmacdesign.mc3dprint.compat.RegistryCompat.registerItem(ITEMS, "scanner",
            ScannerItem::new, new Item.Properties().stacksTo(1));

    public static final DeferredHolder<Item, Item> FILAMENT_WINDER = WINDERS.get(0);

    public static final DeferredHolder<Item, Item> PRINTER_CASING = com.pgmacdesign.mc3dprint.compat.RegistryCompat.registerItem(ITEMS, "printer_casing",
            props -> new BlockItem(ModBlocks.PRINTER_CASING.get(), props), blockItemProps());

    public static final DeferredHolder<Item, Item> FILAMENT_CONVERTER = com.pgmacdesign.mc3dprint.compat.RegistryCompat.registerItem(ITEMS, "filament_converter",
            props -> new BlockItem(ModBlocks.FILAMENT_CONVERTER.get(), props), blockItemProps());

    public static final DeferredHolder<Item, Item> REMOTE_TERMINAL = com.pgmacdesign.mc3dprint.compat.RegistryCompat.registerItem(ITEMS, "remote_terminal",
            props -> new com.pgmacdesign.mc3dprint.machine.RemoteTerminalBlock.TerminalBlockItem(
                    ModBlocks.REMOTE_TERMINAL.get(), props), blockItemProps());

    public static final DeferredHolder<Item, Item> FILAMENT_RACK = com.pgmacdesign.mc3dprint.compat.RegistryCompat.registerItem(ITEMS, "filament_rack",
            props -> new BlockItem(ModBlocks.FILAMENT_RACK.get(), props), blockItemProps());

    public static final DeferredHolder<Item, Item> MC3DCABLE = com.pgmacdesign.mc3dprint.compat.RegistryCompat.registerItem(ITEMS, "mc3dcable",
            props -> new BlockItem(ModBlocks.MC3DCABLE.get(), props), blockItemProps());

    public static final DeferredHolder<Item, Item> BLUEPRINT_REPOSITORY = com.pgmacdesign.mc3dprint.compat.RegistryCompat.registerItem(ITEMS, "blueprint_repository",
            props -> new BlockItem(ModBlocks.BLUEPRINT_REPOSITORY.get(), props), blockItemProps());

    public static final DeferredHolder<Item, Item> FILAMENT_ITEM_SORTER = com.pgmacdesign.mc3dprint.compat.RegistryCompat.registerItem(ITEMS, "filament_item_sorter",
            props -> new BlockItem(ModBlocks.FILAMENT_ITEM_SORTER.get(), props), blockItemProps());

    // These three carry a hover line via TooltipBlockItem (1.21.5 removed Block.appendHoverText —
    // tooltips live on the item now; same mechanism on both nodes for visual parity).
    public static final DeferredHolder<Item, Item> CLOCK_GENERATOR = com.pgmacdesign.mc3dprint.compat.RegistryCompat.registerItem(ITEMS, "clock_generator",
            props -> new com.pgmacdesign.mc3dprint.compat.TooltipBlockItem(ModBlocks.CLOCK_GENERATOR.get(), props,
                    () -> net.minecraft.network.chat.Component.translatable("tooltip.mc3dprint.clock_generator",
                            com.pgmacdesign.mc3dprint.machine.ClockGeneratorBlockEntity.ratePerTick(),
                            com.pgmacdesign.mc3dprint.machine.ClockGeneratorBlockEntity.burnMultiplier())
                            .withStyle(net.minecraft.ChatFormatting.GRAY)), blockItemProps());

    public static final DeferredHolder<Item, Item> REDSTONE_CLOCK = com.pgmacdesign.mc3dprint.compat.RegistryCompat.registerItem(ITEMS, "redstone_clock",
            props -> new com.pgmacdesign.mc3dprint.compat.TooltipBlockItem(ModBlocks.REDSTONE_CLOCK.get(), props,
                    () -> net.minecraft.network.chat.Component.translatable("tooltip.mc3dprint.redstone_clock")
                            .withStyle(net.minecraft.ChatFormatting.GRAY)), blockItemProps());

    public static final DeferredHolder<Item, Item> CREATIVE_ENERGY_SOURCE = com.pgmacdesign.mc3dprint.compat.RegistryCompat.registerItem(ITEMS, "creative_energy_source",
            props -> new com.pgmacdesign.mc3dprint.compat.TooltipBlockItem(ModBlocks.CREATIVE_ENERGY_SOURCE.get(), props,
                    () -> net.minecraft.network.chat.Component.translatable("tooltip.mc3dprint.creative_energy")
                            .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE)), blockItemProps());

    public static final DeferredHolder<Item, Item> CREATIVE_SPOOL = com.pgmacdesign.mc3dprint.compat.RegistryCompat.registerItem(ITEMS, "creative_filament_spool",
            com.pgmacdesign.mc3dprint.fu.CreativeSpoolItem::new, new Item.Properties());

    public static final DeferredHolder<Item, Item> EXTRUDIUM_ORE = com.pgmacdesign.mc3dprint.compat.RegistryCompat.registerItem(ITEMS, "extrudium_ore",
            props -> new BlockItem(ModBlocks.EXTRUDIUM_ORE.get(), props), blockItemProps());

    public static final DeferredHolder<Item, Item> EXTRUDIUM_CRYSTAL = com.pgmacdesign.mc3dprint.compat.RegistryCompat.registerItem(ITEMS, "extrudium_crystal",
            Item::new, new Item.Properties());

    public static final DeferredHolder<Item, Item> SPEED_UPGRADE = com.pgmacdesign.mc3dprint.compat.RegistryCompat.registerItem(ITEMS, "speed_upgrade",
            props -> new UpgradeItem(UpgradeItem.Type.SPEED, props), new Item.Properties().stacksTo(16));
    public static final DeferredHolder<Item, Item> EFFICIENCY_UPGRADE = com.pgmacdesign.mc3dprint.compat.RegistryCompat.registerItem(ITEMS, "efficiency_upgrade",
            props -> new UpgradeItem(UpgradeItem.Type.EFFICIENCY, props), new Item.Properties().stacksTo(16));
    public static final DeferredHolder<Item, Item> RF_EFFICIENCY_UPGRADE = com.pgmacdesign.mc3dprint.compat.RegistryCompat.registerItem(ITEMS, "rf_efficiency_upgrade",
            props -> new UpgradeItem(UpgradeItem.Type.RF_EFFICIENCY, props), new Item.Properties().stacksTo(16));
    public static final DeferredHolder<Item, Item> BUFFER_UPGRADE = com.pgmacdesign.mc3dprint.compat.RegistryCompat.registerItem(ITEMS, "buffer_upgrade",
            props -> new UpgradeItem(UpgradeItem.Type.BUFFER, props), new Item.Properties().stacksTo(16));

    /** Crafting intermediate for all craftable resins (extrudium crystal + slime). */
    public static final DeferredHolder<Item, Item> RESIN_BASE = com.pgmacdesign.mc3dprint.compat.RegistryCompat.registerItem(ITEMS, "resin_base",
            Item::new, new Item.Properties());

    /** All resin variants (effect × its valid tiers — the gated matrix). 11 items. */
    public static final List<DeferredHolder<Item, Item>> RESINS = buildResins();

    private static List<DeferredHolder<Item, Item>> buildResins() {
        List<DeferredHolder<Item, Item>> resins = new ArrayList<>();
        for (ResinItem.Effect effect : ResinItem.Effect.values()) {
            for (int tier : effect.tiers()) {
                final ResinItem.Effect e = effect;
                final int t = tier;
                resins.add(com.pgmacdesign.mc3dprint.compat.RegistryCompat.registerItem(ITEMS, ResinItem.registryId(e, t),
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
            fabricators.add(com.pgmacdesign.mc3dprint.compat.RegistryCompat.registerItem(ITEMS, controller.getId().getPath(),
                    props -> new FabricatorBlockItem(controller.get(), props),
                    com.pgmacdesign.mc3dprint.compat.RegistryCompat.blockItem(new Item.Properties().stacksTo(1))));
        }
        return List.copyOf(fabricators);
    }

    /** Filament spools, index 0 = Tier 1. */
    public static final List<DeferredHolder<Item, Item>> SPOOLS = buildSpools();

    private static List<DeferredHolder<Item, Item>> buildSpools() {
        List<DeferredHolder<Item, Item>> spools = new ArrayList<>(SpoolItem.CAPACITY_BY_TIER.length);
        for (int tier = 1; tier <= SpoolItem.CAPACITY_BY_TIER.length; tier++) {
            final int t = tier;
            spools.add(com.pgmacdesign.mc3dprint.compat.RegistryCompat.registerItem(ITEMS, "filament_spool_t" + tier,
                    props -> new SpoolItem(t, props), new Item.Properties()));
        }
        return List.copyOf(spools);
    }

    private ModItems() {}
}
