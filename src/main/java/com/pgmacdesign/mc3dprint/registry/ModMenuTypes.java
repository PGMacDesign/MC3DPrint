package com.pgmacdesign.mc3dprint.registry;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.machine.PrinterMenu;
import com.pgmacdesign.mc3dprint.machine.RedstoneClockMenu;
import com.pgmacdesign.mc3dprint.machine.SimpleGeneratorMenu;
import com.pgmacdesign.mc3dprint.machine.WinderMenu;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, MC3DPrint.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<PrinterMenu>> TIER1_PRINTER =
            MENU_TYPES.register("tier1_printer",
                    () -> IMenuTypeExtension.create(PrinterMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<WinderMenu>> FILAMENT_WINDER =
            MENU_TYPES.register("filament_winder",
                    () -> IMenuTypeExtension.create(WinderMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<SimpleGeneratorMenu>> SIMPLE_GENERATOR =
            MENU_TYPES.register("simple_generator",
                    () -> IMenuTypeExtension.create(SimpleGeneratorMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<RedstoneClockMenu>> REDSTONE_CLOCK =
            MENU_TYPES.register("redstone_clock",
                    () -> IMenuTypeExtension.create(RedstoneClockMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<com.pgmacdesign.mc3dprint.machine.repository.BlueprintRepositoryMenu>> BLUEPRINT_REPOSITORY =
            MENU_TYPES.register("blueprint_repository",
                    () -> IMenuTypeExtension.create(com.pgmacdesign.mc3dprint.machine.repository.BlueprintRepositoryMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<com.pgmacdesign.mc3dprint.machine.sorter.SorterMenu>> FILAMENT_ITEM_SORTER =
            MENU_TYPES.register("filament_item_sorter",
                    () -> IMenuTypeExtension.create(com.pgmacdesign.mc3dprint.machine.sorter.SorterMenu::new));

    /**
     * Registered unconditionally, even on nodes with no AE2. A menu type is inert without something
     * to open it, and gating it on AE2 would mean the AE2-free terminal code could not be tested
     * or reused, which is the whole reason it lives outside src/ae2.
     */
    public static final DeferredHolder<MenuType<?>, MenuType<com.pgmacdesign.mc3dprint.machine.terminal.MC3DPrintTerminalMenu>> MC3DPRINT_TERMINAL =
            MENU_TYPES.register("mc3dprint_terminal",
                    () -> IMenuTypeExtension.create(com.pgmacdesign.mc3dprint.machine.terminal.MC3DPrintTerminalMenu::new));

    private ModMenuTypes() {}
}
