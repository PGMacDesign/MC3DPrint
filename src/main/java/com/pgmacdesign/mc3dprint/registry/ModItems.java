package com.pgmacdesign.mc3dprint.registry;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.fu.SpoolItem;
import com.pgmacdesign.mc3dprint.item.BlueprintDiscItem;
import com.pgmacdesign.mc3dprint.machine.multiblock.FabricatorBlockItem;
import com.pgmacdesign.mc3dprint.scanner.ScannerItem;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MC3DPrint.MOD_ID);

    /** Printer block items, index 0 = Tier 1. */
    public static final List<RegistryObject<Item>> PRINTERS = buildBlockItems(ModBlocks.PRINTERS);

    /** Winder block items, index 0 = Tier 1. */
    public static final List<RegistryObject<Item>> WINDERS = buildBlockItems(ModBlocks.WINDERS);

    public static final RegistryObject<Item> TIER1_PRINTER = PRINTERS.get(0);

    private static List<RegistryObject<Item>> buildBlockItems(
            List<net.minecraftforge.registries.RegistryObject<net.minecraft.world.level.block.Block>> blocks) {
        List<RegistryObject<Item>> items = new ArrayList<>(blocks.size());
        for (var block : blocks) {
            items.add(ITEMS.register(block.getId().getPath(),
                    () -> new BlockItem(block.get(), new Item.Properties())));
        }
        return List.copyOf(items);
    }

    public static final RegistryObject<Item> BLANK_BLUEPRINT_DISC = ITEMS.register("blank_blueprint_disc",
            () -> new Item(new Item.Properties().stacksTo(16)));

    public static final RegistryObject<Item> BLUEPRINT_DISC = ITEMS.register("blueprint_disc",
            () -> new BlueprintDiscItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> SCANNER = ITEMS.register("scanner",
            () -> new ScannerItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> FILAMENT_WINDER = WINDERS.get(0);

    public static final RegistryObject<Item> PRINTER_CASING = ITEMS.register("printer_casing",
            () -> new BlockItem(ModBlocks.PRINTER_CASING.get(), new Item.Properties()));

    /** Fabricator (controller) items, index 0 = Tier 5. Collapsed stacks re-form the multiblock. */
    public static final List<RegistryObject<Item>> FABRICATORS = buildFabricators();

    private static List<RegistryObject<Item>> buildFabricators() {
        List<RegistryObject<Item>> fabricators = new ArrayList<>(ModBlocks.CONTROLLERS.size());
        for (var controller : ModBlocks.CONTROLLERS) {
            fabricators.add(ITEMS.register(controller.getId().getPath(),
                    () -> new FabricatorBlockItem(controller.get(), new Item.Properties().stacksTo(1))));
        }
        return List.copyOf(fabricators);
    }

    /** Filament spools, index 0 = Tier 1. */
    public static final List<RegistryObject<Item>> SPOOLS = buildSpools();

    private static List<RegistryObject<Item>> buildSpools() {
        List<RegistryObject<Item>> spools = new ArrayList<>(SpoolItem.CAPACITY_BY_TIER.length);
        for (int tier = 1; tier <= SpoolItem.CAPACITY_BY_TIER.length; tier++) {
            final int t = tier;
            spools.add(ITEMS.register("filament_spool_t" + tier,
                    () -> new SpoolItem(t, new Item.Properties())));
        }
        return List.copyOf(spools);
    }

    private ModItems() {}
}
