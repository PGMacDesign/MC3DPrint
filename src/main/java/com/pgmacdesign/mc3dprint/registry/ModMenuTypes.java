package com.pgmacdesign.mc3dprint.registry;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.machine.PrinterMenu;
import com.pgmacdesign.mc3dprint.machine.RedstoneClockMenu;
import com.pgmacdesign.mc3dprint.machine.SimpleGeneratorMenu;
import com.pgmacdesign.mc3dprint.machine.WinderMenu;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, MC3DPrint.MOD_ID);

    public static final RegistryObject<MenuType<PrinterMenu>> TIER1_PRINTER =
            MENU_TYPES.register("tier1_printer",
                    () -> IForgeMenuType.create(PrinterMenu::new));

    public static final RegistryObject<MenuType<WinderMenu>> FILAMENT_WINDER =
            MENU_TYPES.register("filament_winder",
                    () -> IForgeMenuType.create(WinderMenu::new));

    public static final RegistryObject<MenuType<SimpleGeneratorMenu>> SIMPLE_GENERATOR =
            MENU_TYPES.register("simple_generator",
                    () -> IForgeMenuType.create(SimpleGeneratorMenu::new));

    public static final RegistryObject<MenuType<RedstoneClockMenu>> REDSTONE_CLOCK =
            MENU_TYPES.register("redstone_clock",
                    () -> IForgeMenuType.create(RedstoneClockMenu::new));

    public static final RegistryObject<MenuType<com.pgmacdesign.mc3dprint.machine.repository.BlueprintRepositoryMenu>> BLUEPRINT_REPOSITORY =
            MENU_TYPES.register("blueprint_repository",
                    () -> IForgeMenuType.create(com.pgmacdesign.mc3dprint.machine.repository.BlueprintRepositoryMenu::new));

    private ModMenuTypes() {}
}
