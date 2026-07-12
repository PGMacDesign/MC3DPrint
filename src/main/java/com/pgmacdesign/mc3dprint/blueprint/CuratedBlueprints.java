package com.pgmacdesign.mc3dprint.blueprint;

import com.mojang.logging.LogUtils;
import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.fu.FuValueRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
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
            "iron_farm", "mob_xp_tower", "sugarcane_farm_auto", "pumpkin_melon_farm",
            "cactus_farm", "bamboo_farm", "kelp_farm", "villager_trading_hall",
            "animal_pen", "fishery_pond", "tree_farm",
            "mushroom_farm",
            // Phase 2 — Category I (ornamental / garden)
            "koi_pond", "gazebo", "pergola_garden", "wishing_well",
            "statue_pedestal", "obelisk", "stonehenge_ring", "garden_archway",
            "ruin_pillar", "cemetery_plot", "scarecrow", "flower_shop", "food_stall",
            "park_bench_lamppost", "hedge_maze_segment", "hot_air_balloon",
            "dragon_statue",
            // Phase 2 — Category G (storage)
            "storage_barrel_hall", "brewing_room", "super_smelter", "smithy_workshop",
            "map_room", "library",
            // Phase 2 — Category H (infrastructure / civic / defensive)
            "sky_bridge_segment", "road_path_segment", "aqueduct_segment",
            "mineshaft_entrance", "railway_station", "tavern_inn",
            "apothecary_shop",
            // Phase 2 — Category H (gatehouse)
            "gatehouse",
            // Phase 2 — Category H (guard_tower)
            "guard_tower",
            // Phase 2 — Category H (drawbridge)
            "drawbridge",
            // Phase 2 — Category H (portcullis_gate)
            "portcullis_gate",
            // Phase 2 — Category H (stable_horse)
            "stable_horse",
            // Phase 2 — Category H (greenhouse)
            "greenhouse",
            // Phase 2 — Category B (modern_concrete_house)
            "modern_concrete_house",
            // Phase 2 — Category B (modern_pool_deck)
            "modern_pool_deck",
            // Phase 2 — Category B (storybook_cottage)
            "storybook_cottage",
            // Phase 2 — Category B (torii_gate)
            "torii_gate",
            // Phase 2 — Category B (japanese_tea_house)
            "japanese_tea_house",
            // Phase 2 — Category B (zen_garden)
            "zen_garden",
            // Phase 2 — Category B (japanese_dojo)
            "japanese_dojo",
            // Phase 2 — Category B (mediterranean_terracotta_villa)
            "mediterranean_terracotta_villa",
            // Phase 2 — Category B (greek_quartz_temple)
            "greek_quartz_temple",
            // Phase 2 — Category B (roman_bath_house)
            "roman_bath_house",
            // Phase 2 — Category B (fantasy_wizard_tower)
            "fantasy_wizard_tower",
            // Phase 2 — Category B (victorian_townhouse)
            "victorian_townhouse",
            // Phase 2 — Category B (nordic_viking_longhouse)
            "nordic_viking_longhouse",
            // Phase 2 — Category B (copper_clocktower)
            "copper_clocktower",
            // Phase 2 — Category B (modern_glass_villa)
            "modern_glass_villa",
            // Phase 2 — Category B (elven_treehouse)
            "elven_treehouse",
            // Phase 2 — Category B (dwarven_hall)
            "dwarven_hall",
            // Phase 2 — Category E (dock_pier)
            "dock_pier",
            // Phase 2 — Category E (fishing_hut)
            "fishing_hut",
            // Phase 2 — Category E (underwater_conduit_shrine)
            "underwater_conduit_shrine",
            // Phase 2 — Category E (ocean_ruins)
            "ocean_ruins",
            // Phase 2 — Category E (coral_garden)
            "coral_garden",
            // Phase 2 — Category E (prismarine_monument_fragment)
            "prismarine_monument_fragment",
            // Phase 2 — Category E (underwater_dome_base)
            "underwater_dome_base",
            // Phase 2 — Category E (aquarium)
            "aquarium",
            // Phase 2 — Category E (sailing_ship)
            "sailing_ship",
            // Phase 2 — Category C (nether_portal_room)
            "nether_portal_room",
            // Phase 2 — Category C (crimson_warped_hut)
            "crimson_warped_hut",
            // Phase 2 — Category C (soul_outpost)
            "soul_outpost",
            // Phase 2 — Category C (nether_wart_farm)
            "nether_wart_farm",
            // Phase 2 — Category C (basalt_pillar_cluster)
            "basalt_pillar_cluster",
            // Phase 2 — Category C (nether_hub_room)
            "nether_hub_room",
            // Phase 2 — Category C (nether_fortress_bridge)
            "nether_fortress_bridge",
            // Phase 2 — Category C (blackstone_bastion_fragment)
            "blackstone_bastion_fragment",
            // Phase 2 — Category D (purpur_tower)
            "purpur_tower",
            // Phase 2 — Category D (end_stone_outpost)
            "end_stone_outpost",
            // Phase 2 — Category D (chorus_garden)
            "chorus_garden",
            // Phase 2 — Category D (shulker_box_vault)
            "shulker_box_vault",
            // Phase 2 — Category D (end_gateway_shrine)
            "end_gateway_shrine",
            // Phase 2 — Category A (mushroom_island_hut) — unblocked: mushroom blocks/mycelium now valued
            "mushroom_island_hut",
            // Phase 2 — Category F (bee_apiary) — unblocked: honeycomb valued -> beehive/honeycomb_block derive
            "bee_apiary",
            // Showpiece — Grand Cathedral (twin towers + vaulted nave; the Library's companion)
            "grand_cathedral",
            // Showpiece — Frozen Throne (ice palace; ice-wraith effigy guarding a chest vault)
            "frozen_throne",
            // Imported player scan — Tristan's Castle (gift build; powder snow priced via the bucket)
            "tristans_castle",
            // Imported player scan — Tristan's Pig House
            "tristans_pig_house",
            // Coppertide Park: MC Waterslides mod-gated water park set (only
            // surfaces when the mcwaterslides mod is installed; requiredMods gate)
            "water_park_lagoon", "water_park_coppertop_drop", "water_park_lazy_river",
            "water_park_glasswyrm", "water_park_pendulum_gorge", "water_park_rainbow_racer");

    /**
     * Blueprints explicitly kept OUT of world loot. <b>Opt-out by design:</b> every
     * curated blueprint is loot-available throughout the world, and every new one we
     * add is too — automatically — unless its name is listed here. Add a name only
     * when the decision is "this specific build should not be findable in the world."
     */
    public static final java.util.Set<String> LOOT_EXCLUDED = java.util.Set.of();

    /** The world-loot pool: every curated blueprint minus {@link #LOOT_EXCLUDED}. */
    public static List<String> lootBlueprints() {
        return CURATED_NAMES.stream().filter(name -> !LOOT_EXCLUDED.contains(name)).toList();
    }

    /**
     * Per-blueprint allowances for {@code #mc3dprint:print_restricted} items
     * (trophy blocks like mob heads). A restricted item prints ONLY from an
     * OFFICIAL disc whose curated blueprint is listed here with that item —
     * player-scanned discs never qualify (same anti-exploit shape as resins).
     */
    private static final java.util.Map<String, java.util.Set<String>> RESTRICTED_ALLOWANCES = java.util.Map.of(
            // the imported pig-house scan ships decorative dragon + creeper heads
            "tristans_pig_house", java.util.Set.of("minecraft:dragon_head", "minecraft:creeper_head"));

    private static volatile java.util.Map<UUID, java.util.Set<String>> allowancesByUuid;

    /** Restricted-item ids the OFFICIAL blueprint {@code id} may print; empty for all others. */
    public static java.util.Set<String> restrictedAllowance(UUID id) {
        java.util.Map<UUID, java.util.Set<String>> byUuid = allowancesByUuid;
        if (byUuid == null) {
            java.util.Map<UUID, java.util.Set<String>> built = new java.util.HashMap<>();
            RESTRICTED_ALLOWANCES.forEach((name, items) ->
                    built.put(uuidFor(MC3DPrint.MOD_ID, name), items));
            allowancesByUuid = byUuid = built;
        }
        return byUuid.getOrDefault(id, java.util.Set.of());
    }

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

    /** name -> required mod ids (palette-derived), computed once per curated blueprint. */
    private static final Map<String, Set<String>> REQUIRED_MODS = new ConcurrentHashMap<>();

    /**
     * The mod ids a curated blueprint needs, from its palette/entities ({@link Blueprint#requiredMods}).
     * Cached; empty for the all-vanilla builds (the current set). Empty (not present) if it fails to load.
     */
    public static Set<String> requiredMods(String name) {
        return REQUIRED_MODS.computeIfAbsent(name,
                n -> loadBundled(n).map(Blueprint::requiredMods).orElse(Set.of()));
    }

    /**
     * Visibility gate for the creative tab + world loot: true iff every mod this curated blueprint
     * needs is loaded. Vanilla-only builds are always available; a modded build (e.g. an AE2 setup)
     * only surfaces once its mod(s) are installed.
     */
    public static boolean modsAvailable(String name) {
        for (String mod : requiredMods(name)) {
            if (!ModList.get().isLoaded(mod)) {
                return false;
            }
        }
        return true;
    }

    private static final AtomicBoolean WARMING = new AtomicBoolean(false);

    /**
     * Warm the FU-value cache for every block in every curated blueprint, OFF the render thread.
     * The creative tab stamps a disc per curated blueprint on build (tier + print cost, via
     * {@code BlueprintDiscItem}), which derives an FU value for each block. Cold, against a large
     * modded recipe graph, that first build froze the client ~10s the first time the inventory
     * opened each session (PGM-55). Running it here — right after recipes bind at world-join — the
     * values are memoized before the player opens the menu, so the build is instant.
     *
     * <p>Safe off-thread: {@link #loadBundled} reads classpath resources and FU derivation is
     * synchronized. Idempotent (values are memoized); the {@link #WARMING} gate drops a duplicate
     * warm if one is already running.
     */
    public static void warmFuCacheAsync() {
        if (!WARMING.compareAndSet(false, true)) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                warmFuCache();
            } catch (RuntimeException e) {
                LOGGER.warn("FU cache warm failed: {}", e.getMessage());
            } finally {
                WARMING.set(false);
            }
        });
    }

    /** Synchronous body of {@link #warmFuCacheAsync}; returns the count of blueprints warmed. */
    public static int warmFuCache() {
        int warmed = 0;
        for (String name : CURATED_NAMES) {
            Optional<Blueprint> bp = loadBundled(name);
            if (bp.isEmpty()) {
                continue;
            }
            for (BlueprintBlockState paletteState : bp.get().palette()) {
                paletteState.resolve().ifPresent(state -> {
                    Item item = state.getBlock().asItem();
                    if (item != Items.AIR) {
                        FuValueRegistry.valueOf(new ItemStack(item));
                    }
                });
            }
            warmed++;
        }
        LOGGER.debug("Warmed FU cache across {} curated blueprints", warmed);
        return warmed;
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
            } catch (IOException | RuntimeException e) {
                // RuntimeException too (e.g. a corrupt existing world file surfacing as
                // UncheckedIOException): one bad blueprint must not abort server start.
                LOGGER.warn("Skipping curated blueprint {}: {}", rl, e.getMessage());
            }
        }
        if (installed > 0) {
            LOGGER.info("Installed {} curated blueprint(s) into the world store", installed);
        }
    }
}
