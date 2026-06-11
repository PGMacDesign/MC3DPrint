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
                        output.accept(ModItems.TIER1_PRINTER.get());
                        output.accept(ModItems.BLANK_BLUEPRINT_DISC.get());
                    })
                    .build());

    private ModCreativeTabs() {}
}
