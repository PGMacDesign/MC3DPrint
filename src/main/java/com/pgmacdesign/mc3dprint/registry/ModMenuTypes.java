package com.pgmacdesign.mc3dprint.registry;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.machine.PrinterMenu;
import com.pgmacdesign.mc3dprint.machine.RedstoneClockMenu;
import com.pgmacdesign.mc3dprint.machine.SimpleGeneratorMenu;
import com.pgmacdesign.mc3dprint.machine.WinderMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
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

    public static final RegistryObject<MenuType<com.pgmacdesign.mc3dprint.machine.sorter.SorterMenu>> FILAMENT_ITEM_SORTER =
            MENU_TYPES.register("filament_item_sorter",
                    () -> IForgeMenuType.create(com.pgmacdesign.mc3dprint.machine.sorter.SorterMenu::new));

    /**
     * Registered unconditionally, even without AE2. A menu type is inert with nothing to open it,
     * and gating it on AE2 would mean the AE2-free terminal code could not be tested or reused,
     * which is the whole reason it lives outside src/ae2.
     */
    public static final RegistryObject<MenuType<com.pgmacdesign.mc3dprint.machine.terminal.MC3DPrintTerminalMenu>> MC3DPRINT_TERMINAL =
            MENU_TYPES.register("mc3dprint_terminal",
                    () -> IForgeMenuType.create(com.pgmacdesign.mc3dprint.machine.terminal.MC3DPrintTerminalMenu::new));

    private ModMenuTypes() {}
}
