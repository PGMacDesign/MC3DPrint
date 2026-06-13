package com.pgmacdesign.mc3dprint.blueprint;

import com.mojang.logging.LogUtils;
import com.pgmacdesign.mc3dprint.MC3DPrint;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

/**
 * Installs bundled curated blueprints (data/&lt;ns&gt;/blueprints/*.blueprint)
 * into the world's file store on server start. UUIDs are derived from the
 * resource name so loot-table discs can reference them deterministically,
 * and pack makers can ship their own curated sets the same way.
 */
public final class CuratedBlueprints {
    private static final Logger LOGGER = LogUtils.getLogger();

    private CuratedBlueprints() {}

    public static UUID uuidFor(String namespace, String name) {
        return UUID.nameUUIDFromBytes((namespace + ":curated:" + name).getBytes(StandardCharsets.UTF_8));
    }

    /** Every curated blueprint shipped with the mod (bundled + auto-installed). */
    public static final List<String> CURATED_NAMES = List.of(
            "starter_hut", "storage_shed", "watchtower",
            "small_cottage", "plains_house", "log_cabin", "two_story_house", "cobble_house",
            "village_well", "lamp_post", "watchtower_tall", "wooden_bridge", "small_farm",
            "gazebo", "market_stall", "fishing_dock", "wall_gate_segment", "shrine",
            "windmill_base", "bakery", "blacksmith_hut", "animal_pen", "bell_tower");

    /**
     * Load a bundled curated blueprint straight from the mod's resources (no world
     * needed) so the creative tab can hand out fully-populated discs. The same
     * {@link #uuidFor} UUID resolves against the world store at print time.
     */
    public static Optional<Blueprint> loadBundled(String name) {
        String path = "/data/" + MC3DPrint.MOD_ID + "/blueprints/" + name + BlueprintFileStore.EXTENSION;
        try (InputStream in = CuratedBlueprints.class.getResourceAsStream(path)) {
            if (in == null) {
                return Optional.empty();
            }
            return Optional.of(BlueprintSerializer.read(
                    NbtIo.read(new DataInputStream(new GZIPInputStream(in)))));
        } catch (IOException | BlueprintFormatException e) {
            LOGGER.warn("Could not load bundled blueprint {}: {}", name, e.getMessage());
            return Optional.empty();
        }
    }

    public static void onServerStarted(ServerStartedEvent event) {
        install(event.getServer());
    }

    public static void install(MinecraftServer server) {
        BlueprintFileStore store = BlueprintFileStore.forServer(server);
        Map<ResourceLocation, net.minecraft.server.packs.resources.Resource> resources =
                server.getResourceManager().listResources("blueprints",
                        rl -> rl.getPath().endsWith(BlueprintFileStore.EXTENSION));

        int installed = 0;
        for (var entry : resources.entrySet()) {
            ResourceLocation rl = entry.getKey();
            String path = rl.getPath(); // blueprints/<name>.blueprint
            String name = path.substring("blueprints/".length(),
                    path.length() - BlueprintFileStore.EXTENSION.length());
            UUID id = uuidFor(rl.getNamespace(), name);
            if (store.exists(id)) {
                continue;
            }
            try (InputStream in = entry.getValue().open()) {
                CompoundTag tag = NbtIo.read(new DataInputStream(new GZIPInputStream(in)));
                store.save(id, BlueprintSerializer.read(tag));
                installed++;
            } catch (IOException | BlueprintFormatException e) {
                LOGGER.warn("Skipping curated blueprint {}: {}", rl, e.getMessage());
            }
        }
        if (installed > 0) {
            LOGGER.info("Installed {} curated blueprint(s) into the world store", installed);
        }
    }
}
