package com.pgmacdesign.mc3dprint.registry;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.fu.SpoolItem;
import com.pgmacdesign.mc3dprint.item.BlueprintDiscItem;
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

    public static final RegistryObject<Item> TIER1_PRINTER = ITEMS.register("tier1_printer",
            () -> new BlockItem(ModBlocks.TIER1_PRINTER.get(), new Item.Properties()));

    public static final RegistryObject<Item> BLANK_BLUEPRINT_DISC = ITEMS.register("blank_blueprint_disc",
            () -> new Item(new Item.Properties().stacksTo(16)));

    public static final RegistryObject<Item> BLUEPRINT_DISC = ITEMS.register("blueprint_disc",
            () -> new BlueprintDiscItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> SCANNER = ITEMS.register("scanner",
            () -> new ScannerItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> FILAMENT_WINDER = ITEMS.register("filament_winder",
            () -> new BlockItem(ModBlocks.FILAMENT_WINDER.get(), new Item.Properties()));

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
