package com.pgmacdesign.mc3dprint.registry;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MC3DPrint.MOD_ID);

    public static final RegistryObject<CreativeModeTab> MC3DPRINT_TAB = CREATIVE_MODE_TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.mc3dprint"))
                    .icon(() -> new ItemStack(ModItems.TIER1_PRINTER.get()))
                    .displayItems((parameters, output) -> {
                        ModItems.PRINTERS.forEach(printer -> output.accept(printer.get()));
                        ModItems.FABRICATORS.forEach(fabricator -> output.accept(fabricator.get()));
                        output.accept(ModItems.PRINTER_CASING.get());
                        ModItems.WINDERS.forEach(winder -> output.accept(winder.get()));
                        output.accept(ModItems.FILAMENT_CONVERTER.get());
                        output.accept(ModItems.REMOTE_TERMINAL.get());
                        output.accept(ModItems.BLANK_BLUEPRINT_DISC.get());
                        output.accept(ModItems.BLUEPRINT_DISC.get());
                        output.accept(ModItems.SCANNER.get());
                        ModItems.SPOOLS.forEach(spool -> output.accept(spool.get()));
                        output.accept(ModItems.PRINTITE_ORE.get());
                        output.accept(ModItems.PRINTITE_CRYSTAL.get());
                        output.accept(ModItems.SPEED_UPGRADE.get());
                        output.accept(ModItems.EFFICIENCY_UPGRADE.get());
                        output.accept(ModItems.RF_EFFICIENCY_UPGRADE.get());
                        output.accept(ModItems.BUFFER_UPGRADE.get());
                    })
                    .build());

    private ModCreativeTabs() {}
}
