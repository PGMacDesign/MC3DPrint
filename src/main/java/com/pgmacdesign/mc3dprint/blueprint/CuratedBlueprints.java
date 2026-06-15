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

    /**
     * Every curated blueprint shipped with the mod (bundled + auto-installed).
     * Must match {@code CuratedBlueprintGenerator.generateCuratedBlueprints()} 1:1
     * (see docs/blueprint-specs.md §4 coverage matrix for names/footprints/order).
     */
    public static final List<String> CURATED_NAMES = List.of(
            // Small (T3–T4 footprint)
            "garden_shed", "campfire_site", "well", "market_stall", "small_cottage", "beacon_spire",
            // Medium (T5–T6 footprint)
            "plains_house", "small_farm", "bakery", "blacksmith", "windmill",
            "stone_bridge", "watchtower", "barn",
            // High-material-tier (disc T2–T5)
            "iron_foundry", "redstone_workshop", "diamond_vault",
            // Large (T6–T7 footprint) + remaining high-tier
            "church", "manor_house", "copper_observatory", "emerald_market_hall",
            "lighthouse", "castle_keep",
            // Phase 0 pilot builds (validate the parametric helper library)
            "cherry_grove_cottage", "enchanting_room", "japanese_pagoda",
            // Phase 1 pilot builds — group 2 (remaining bank archetypes)
            "savanna_acacia_villa", "tiered_fountain", "wall_battlement_segment",
            // Per-biome starter houses (§3.A)
            "desert_sandstone_house", "desert_pyramid_shrine", "taiga_log_cabin",
            "taiga_spruce_longhouse",
            // Phase 2 — Category A
            "snowy_igloo", "snowy_alpine_chalet", "jungle_hut", "jungle_temple_ruin",
            "mangrove_stilt_hut", "cherry_blossom_pavilion", "badlands_mesa_dwelling",
            "hobbit_hole", "treehouse",
            // Phase 2 — Category F (functional farms)
            "iron_farm", "mob_xp_tower", "sugarcane_farm_auto");

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
            try (InputStream in = entry.getValue().open()) {
                CompoundTag tag = NbtIo.read(new DataInputStream(new GZIPInputStream(in)));
                Blueprint bundled = BlueprintSerializer.read(tag);
                // Curated blueprints are mod-owned and keyed by a name-derived UUID, so a
                // content edit keeps the same id. Install when absent AND refresh when the
                // shipped content changed, so curated fixes reach existing worlds — but skip
                // the write when the store already holds an identical copy (idempotent boot).
                if (store.load(id).map(bundled::equals).orElse(false)) {
                    continue;
                }
                store.save(id, bundled);
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
