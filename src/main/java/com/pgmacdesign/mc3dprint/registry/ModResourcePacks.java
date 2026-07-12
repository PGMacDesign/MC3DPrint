package com.pgmacdesign.mc3dprint.registry;

import com.mojang.logging.LogUtils;
import com.pgmacdesign.mc3dprint.MC3DPrint;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.locating.IModFile;
import org.slf4j.Logger;

/**
 * Built-in alternate texture styles (docs/texture-packs.md): each pack ships
 * inside the jar under {@code resourcepacks/<style>/} and shows up as an
 * OPTIONAL entry in Options → Resource Packs. Default-off by design — the
 * default art is canonical; styles are opt-in skins, never auto-enabled.
 *
 * Forge 1.20.1 has no addPackFinders convenience helper (that's NeoForge),
 * so this builds the Pack by hand — the Create/Aether 1.20.1 pattern.
 */
public final class ModResourcePacks {

    private static final Logger LOGGER = LogUtils.getLogger();

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
        IModFile modFile = ModList.get().getModFileById(MC3DPrint.MOD_ID).getFile();
        event.addRepositorySource(consumer -> {
            Pack pack = Pack.readMetaAndCreate(
                    MC3DPrint.MOD_ID + ":" + folder,
                    Component.literal(title),
                    false,
                    id -> new PathPackResources(id, modFile.findResource("resourcepacks/" + folder), false),
                    PackType.CLIENT_RESOURCES,
                    Pack.Position.TOP,
                    PackSource.BUILT_IN);
            if (pack != null) {
                consumer.accept(pack);
            } else {
                // readMetaAndCreate returns null when pack.mcmeta is missing or
                // unreadable — surface it, or the pack silently never appears.
                LOGGER.warn("Built-in style pack '{}' failed to load (bad or missing pack.mcmeta)", folder);
            }
        });
    }
}
