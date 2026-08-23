package com.pgmacdesign.mc3dprint.integration.ae2;

import appeng.items.parts.PartItem;
import com.pgmacdesign.mc3dprint.MC3DPrint;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Every {@code appeng} reference in the integration's startup path, kept in one class so the JVM
 * resolves it only when AE2 is actually installed. Nothing here may be touched without first
 * passing the {@link Ae2Registry} gate; see that class for why.
 */
final class Ae2Parts {

    static final ResourceLocation TERMINAL_ID =
            ResourceLocation.fromNamespaceAndPath(MC3DPrint.MOD_ID, "me_print_terminal");

    private Ae2Parts() {}

    static void register(RegisterEvent event) {
        event.register(Registries.ITEM, helper -> helper.register(TERMINAL_ID,
                new PartItem<>(new Item.Properties(),
                        MC3DPrintTerminalPart.class, MC3DPrintTerminalPart::new)));
    }

    static void registerModels() {
        Ae2TerminalModels.register();
    }
}
