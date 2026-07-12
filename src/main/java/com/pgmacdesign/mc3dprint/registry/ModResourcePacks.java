package com.pgmacdesign.mc3dprint.registry;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/**
 * Built-in alternate texture styles (docs/texture-packs.md): each pack ships
 * inside the jar under {@code resourcepacks/<style>/} and shows up as an
 * OPTIONAL entry in Options → Resource Packs. Default-off by design — the
 * default art is canonical; styles are opt-in skins, never auto-enabled.
 */
public final class ModResourcePacks {

    private ModResourcePacks() {
    }

    public static void onAddPackFinders(AddPackFindersEvent event) {
        // Styles are client texture packs; without this gate the same folders
        // would also register into the server DATA pack repository.
        if (event.getPackType() != PackType.CLIENT_RESOURCES) {
            return;
        }
        register(event, "blueprint_mode", "MC3DPrint: Blueprint Mode");
        register(event, "dark_mode", "MC3DPrint: Dark Mode");
    }

    private static void register(AddPackFindersEvent event, String folder, String title) {
        event.addPackFinders(
                ResourceLocation.fromNamespaceAndPath(MC3DPrint.MOD_ID, "resourcepacks/" + folder),
                PackType.CLIENT_RESOURCES,
                Component.literal(title),
                PackSource.BUILT_IN,
                false,
                Pack.Position.TOP);
    }
}
