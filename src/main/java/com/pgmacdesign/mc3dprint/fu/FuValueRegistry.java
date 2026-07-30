package com.pgmacdesign.mc3dprint.fu;

import com.pgmacdesign.mc3dprint.compat.RegistryCompat;

import com.mojang.logging.LogUtils;
import com.pgmacdesign.mc3dprint.config.MC3DPrintConfig;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Filament Unit values. Sources, in strict precedence order:
 * <ol>
 *   <li>explicit config <b>item</b> entry ({@code minecraft:foo=FU@TIER})</li>
 *   <li>explicit config <b>tag</b> entry ({@code #namespace:tag=FU@TIER})</li>
 *   <li><b>API</b> registration (other mods, via {@code MC3DPrintAPI} / IMC)</li>
 *   <li><b>recipe-derived</b> value (crafting/smelting/stonecutting graph)</li>
 *   <li>otherwise: unknown (empty)</li>
 * </ol>
 *
 * Entry syntax: {@code minecraft:cobblestone=1@1} (item) or
 * {@code #minecraft:logs=3@1} (tag) — FU value @ minimum winder/printer tier.
 *
 * <p>Recipe derivation is wired in lazily: a server/client binds a live
 * recipe snapshot via {@link #bind} (from datapack-sync / recipes-updated
 * events), and the first {@link #valueOf} that misses the explicit + API maps
 * relaxes the recipe graph (see {@link RelaxationFuValuator}). Until a bind happens
 * derivation returns empty — fail safe, never a crash.
 */
public final class FuValueRegistry {
    private static final Logger LOGGER = LogUtils.getLogger();

    // --- Explicit config maps (parsed from fuValues) ---
    private static Map<Item, FuValue> itemValues;
    private static List<TagEntry> tagValues;

    // --- API maps (other mods; survive config reload, not restart) ---
    private static final Map<ResourceLocation, FuValue> apiItemIds = new HashMap<>();
    private static final Map<Item, FuValue> apiItems = new HashMap<>();
    private static final List<TagEntry> apiTags = new ArrayList<>();

    // --- Recipe derivation (bound from datapack/recipe events) ---
    private static Collection<RecipeHolder<?>> boundRecipes;
    private static RegistryAccess boundRegistryAccess;
    private static RelaxationFuValuator<Item> valuator;

    public record TagEntry(TagKey<Item> tag, FuValue value) {}

    private FuValueRegistry() {}

    /**
     * The FU value of a stack, following the documented precedence. Recipe
     * derivation only runs if a recipe snapshot is bound; otherwise the
     * explicit + API maps are the whole answer.
     */
    public static Optional<FuValue> valueOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        return valueOf(stack.getItem(), stack);
    }

    private static Optional<FuValue> valueOf(Item item, ItemStack stackOrNull) {
        Optional<FuValue> base = baseValue(item, stackOrNull);
        if (base.isPresent()) {
            return base;
        }
        // Cosmetic colour/patina is free: a dyed/oxidized variant takes its canonical
        // sibling's value (red_wool == white_wool, light_blue_shulker_box == shulker_box).
        // This runs BEFORE recipe derivation — not just as a fallback — for two reasons:
        // it enforces "recolour is free" (the variant never picks up a dye surcharge),
        // and it sidesteps a pathological derivation. Every dyeable family (wool, carpet,
        // beds, glass, terracotta, candles, concrete, shulker boxes…) is a COMPLETE
        // re-dye graph: any colour crafts from any other colour. Deriving a variant
        // therefore walks factorially-many colour permutations (16P12) — each cycle-
        // pruned so never memoized — which hangs the game (e.g. on creative-tab build).
        // Canonicalizing first collapses each family to its base, which derives once
        // from its primitive recipe. Idempotent: canonical(canonical) == canonical.
        Item canonical = canonicalCosmeticVariant(item);
        if (canonical != item) {
            return valueOf(canonical, null);
        }
        return derive(item);
    }

    /**
     * Explicit-config + API value only (no recipe derivation). This is also the
     * "base value" the recipe valuator short-circuits on so derived items never
     * recurse past a configured/registered material.
     */
    public static Optional<FuValue> baseValue(Item item, ItemStack stackOrNull) {
        ensureLoaded();
        FuValue direct = itemValues.get(item);
        if (direct != null) {
            return Optional.of(direct);
        }
        for (TagEntry entry : tagValues) {
            if (matchesTag(item, stackOrNull, entry.tag())) {
                return Optional.of(entry.value());
            }
        }
        FuValue apiDirect = apiItems.get(item);
        if (apiDirect == null) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (id != null) {
                apiDirect = apiItemIds.get(id);
            }
        }
        if (apiDirect != null) {
            return Optional.of(apiDirect);
        }
        for (TagEntry entry : apiTags) {
            if (matchesTag(item, stackOrNull, entry.tag())) {
                return Optional.of(entry.value());
            }
        }
        return Optional.empty();
    }

    private static boolean matchesTag(Item item, ItemStack stackOrNull, TagKey<Item> tag) {
        if (stackOrNull != null) {
            return stackOrNull.is(tag);
        }
        return item.builtInRegistryHolder().is(tag);
    }

    /**
     * Recipe-derived value (or empty if unbound / underivable). The valuator is built
     * lazily on first use after a bind: it relaxes the whole recipe graph to a fixed point
     * once (~0.5s), then every {@link #valueOf} is an O(1) lookup — versus the former DFS
     * that re-walked the graph per item and stalled a modded startup for ~90s. Bounded to
     * {@link RelaxationFuValuator#MAX_DEPTH}, keeping the historical depth cap.
     */
    private static synchronized Optional<FuValue> derive(Item item) {
        if (boundRecipes == null || boundRegistryAccess == null) {
            return Optional.empty(); // not bound yet — fail safe
        }
        if (valuator == null) {
            MinecraftRecipeIndex index = new MinecraftRecipeIndex(boundRecipes, boundRegistryAccess);
            valuator = new RelaxationFuValuator<>(index.recipesByOutput(),
                    derivedItem -> baseValue(derivedItem, null), RelaxationFuValuator.MAX_DEPTH);
        }
        return valuator.valueOf(item);
    }

    // --- Cosmetic-variant canonicalization (colour / patina is free) ---

    /**
     * Lazily-built variant→canonical map. A coloured or oxidized block is purely
     * cosmetic, so it must cost the same to print/wind as its canonical base. We
     * use this only as a value-resolution FALLBACK (see {@link #valueOf(Item, ItemStack)}),
     * never to override a value an item already has. Built from id strings via
     * {@link ForgeRegistries} so ids that don't exist on this MC version (e.g. the
     * 1.21 copper_bulb / copper_door family on 1.20.1) are simply skipped — no crash.
     */
    private static volatile Map<Item, Item> cosmeticCanonical;

    /**
     * The canonical (cosmetically-neutral) sibling of {@code item}, or {@code item}
     * itself if it isn't a known colour/patina variant. {@code canonical(canonical)}
     * is always the canonical (idempotent), so the fallback can't recurse forever.
     */
    public static Item canonicalCosmeticVariant(Item item) {
        Map<Item, Item> map = cosmeticCanonical;
        if (map == null) {
            map = buildCosmeticCanonical();
            cosmeticCanonical = map;
        }
        return map.getOrDefault(item, item);
    }

    private static synchronized Map<Item, Item> buildCosmeticCanonical() {
        if (cosmeticCanonical != null) {
            return cosmeticCanonical;
        }
        Map<Item, Item> map = new HashMap<>();

        // The 16 vanilla dye colours (item-id prefixes).
        String[] colors = {
                "white", "orange", "magenta", "light_blue", "yellow", "lime",
                "pink", "gray", "light_gray", "cyan", "purple", "blue",
                "brown", "green", "red", "black"
        };

        // Colour families: every <color>_<suffix> -> the canonical base block.
        // White is the canonical for wool/carpet/bed/banner/concrete(_powder);
        // glass/glass_pane/terracotta/candle/shulker_box are themselves the base
        // (the "<color>_" form is the variant, the bare form is canonical).
        for (String color : colors) {
            mapVariant(map, color + "_wool", "white_wool");
            mapVariant(map, color + "_carpet", "white_carpet");
            mapVariant(map, color + "_bed", "white_bed");
            mapVariant(map, color + "_banner", "white_banner");
            mapVariant(map, color + "_concrete", "white_concrete");
            mapVariant(map, color + "_concrete_powder", "white_concrete_powder");
            mapVariant(map, color + "_stained_glass", "glass");
            mapVariant(map, color + "_stained_glass_pane", "glass_pane");
            mapVariant(map, color + "_terracotta", "terracotta");
            mapVariant(map, color + "_glazed_terracotta", "terracotta");
            mapVariant(map, color + "_candle", "candle");
            mapVariant(map, color + "_shulker_box", "shulker_box");
        }

        // Copper oxidation + wax: every exposed/weathered/oxidized and waxed form
        // collapses to the plain, unwaxed, unoxidized base block.
        String[] copperBases = {
                "copper_block", "cut_copper", "cut_copper_stairs", "cut_copper_slab",
                "chiseled_copper", "copper_grate", "copper_bulb",
                "copper_door", "copper_trapdoor"
        };
        String[] oxidationPrefixes = {
                "exposed_", "weathered_", "oxidized_",
                "waxed_", "waxed_exposed_", "waxed_weathered_", "waxed_oxidized_"
        };
        for (String base : copperBases) {
            for (String prefix : oxidationPrefixes) {
                mapVariant(map, prefix + base, base);
            }
        }

        return map;
    }

    /** Adds variantId→canonicalId to the map iff both ids resolve to real items on this MC version. */
    private static void mapVariant(Map<Item, Item> map, String variantId, String canonicalId) {
        Item variant = lookupItem(variantId);
        Item canonical = lookupItem(canonicalId);
        if (variant != null && canonical != null && variant != canonical) {
            map.put(variant, canonical);
        }
    }

    private static Item lookupItem(String path) {
        ResourceLocation id = ResourceLocation.tryParse("minecraft:" + path);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return null;
        }
        return RegistryCompat.item(id);
    }

    /**
     * Binds the live recipe snapshot used for derivation. Called from
     * {@code OnDatapackSyncEvent} (server) and the client recipes-received event.
     * A flat {@code Collection<RecipeHolder<?>>} (rather than a {@code RecipeManager})
     * is the version-neutral carrier — 1.21.5 split per-type lookup behind
     * {@code RecipeMap}, and the client event hands back a {@code RecipeMap}, not a
     * manager. Re-binding drops the derived cache so the next derive rebuilds the
     * index against the new recipes.
     *
     * <p>An <b>empty</b> recipe set never replaces a good binding. 1.21.5+ stopped
     * syncing recipe data to clients by default, so the client recipes-received event
     * hands back an empty/partial {@code RecipeMap}. In single-player the client and
     * server share this static registry, so that empty client bind would otherwise
     * clobber the integrated server's full binding — wiping every recipe-DERIVED FU
     * value (purpur, end-stone bricks, …) and making those blocks unprintable mid-build.
     * Vanilla always loads recipes, so a truly-empty set is always the spurious one.
     */
    public static synchronized void bind(Collection<RecipeHolder<?>> recipes, RegistryAccess registryAccess) {
        if (recipes.isEmpty() && boundRecipes != null && !boundRecipes.isEmpty()) {
            LOGGER.debug("Ignoring empty recipe bind; keeping {} recipes for FU derivation", boundRecipes.size());
            return;
        }
        boundRecipes = recipes;
        boundRegistryAccess = registryAccess;
        valuator = null; // lazy rebuild on next derive
        LOGGER.debug("Bound {} recipes for FU derivation", recipes.size());
    }

    /**
     * The registry/holder lookup bound at server start (a {@link RegistryAccess}
     * IS-A {@link net.minecraft.core.HolderLookup.Provider}), or {@code null} before
     * bind. Used by static cost-estimation paths that must reconstruct an ItemStack
     * from NBT (1.20.5+ requires a provider) without a Level in scope.
     */
    public static RegistryAccess boundRegistries() {
        return boundRegistryAccess;
    }

    /** Drops the parsed explicit cache AND the derived cache (config reload). */
    public static synchronized void invalidate() {
        itemValues = null;
        tagValues = null;
        valuator = null; // explicit values feed derivation -> rebuild
    }

    // --- API ingress (called by MC3DPrintAPI) ---

    public static synchronized void registerApiItemValue(ResourceLocation itemId, int fu, int tier) {
        apiItemIds.put(itemId, clamp(fu, tier));
        valuator = null; // derived values may depend on this base
    }

    public static synchronized void registerApiItemValue(Item item, int fu, int tier) {
        apiItems.put(item, clamp(fu, tier));
        valuator = null;
    }

    public static synchronized void registerApiTagValue(TagKey<Item> tag, int fu, int tier) {
        apiTags.add(new TagEntry(tag, clamp(fu, tier)));
        valuator = null;
    }

    private static FuValue clamp(int fu, int tier) {
        return new FuValue(Math.max(1, fu), Math.max(1, Math.min(8, tier)));
    }

    private static synchronized void ensureLoaded() {
        if (itemValues != null) {
            return;
        }
        Map<Item, FuValue> items = new HashMap<>();
        List<TagEntry> tags = new ArrayList<>();
        int[] counts = loadMerged(defaultEntries(), MC3DPrintConfig.FU_VALUES.get(), items, tags);
        LOGGER.info("[MC3DP] fuValues: {} defaults, {} config entries applied, {} removed via '=off'",
                counts[0], counts[1], counts[2]);
        itemValues = items;
        tagValues = tags;
    }

    /**
     * Config-over-defaults merge: {@code defaults} load first, then {@code overrides}
     * replace per key ({@code <id>=off} removes). New defaults shipped by a mod update
     * therefore apply without rewriting the user's toml — the config-no-wipe contract.
     * Returns {defaults applied, overrides applied, removals} for the startup log.
     */
    public static int[] loadMerged(List<String> defaults, List<? extends String> overrides,
                                   Map<Item, FuValue> items, List<TagEntry> tags) {
        for (String entry : defaults) {
            try {
                parseEntry(entry.trim(), items, tags);
            } catch (RuntimeException e) {
                LOGGER.warn("Skipping invalid default FU entry '{}': {}", entry, e.getMessage());
            }
        }
        int defaultCount = items.size() + tags.size();
        int applied = 0;
        int removed = 0;
        for (String entry : overrides) {
            try {
                if (parseEntry(entry.trim(), items, tags)) {
                    applied++;
                } else {
                    removed++;
                }
            } catch (RuntimeException e) {
                LOGGER.warn("Skipping invalid FU value entry '{}': {}", entry, e.getMessage());
            }
        }
        return new int[]{defaultCount, applied, removed};
    }

    /** Applies one entry; returns false when it was an {@code =off} removal. */
    private static boolean parseEntry(String entry, Map<Item, FuValue> items, List<TagEntry> tags) {
        if (entry.isEmpty() || entry.startsWith("//")) {
            return true;
        }
        int eq = entry.indexOf('=');
        if (eq < 0) {
            throw new IllegalArgumentException("expected <id>=<fu>@<tier> or <id>=off");
        }
        String id = entry.substring(0, eq).trim();
        String rhs = entry.substring(eq + 1).trim();

        if ("off".equalsIgnoreCase(rhs)) {
            if (id.startsWith("#")) {
                TagKey<Item> key = TagKey.create(Registries.ITEM, parseTagId(id));
                tags.removeIf(t -> t.tag().equals(key));
            } else {
                items.remove(RegistryCompat.item(parseItemId(id)));
            }
            return false;
        }

        int at = rhs.indexOf('@');
        if (at < 0) {
            throw new IllegalArgumentException("expected <id>=<fu>@<tier> or <id>=off");
        }
        int fu = Integer.parseInt(rhs.substring(0, at).trim());
        int tier = Integer.parseInt(rhs.substring(at + 1).trim());
        // Non-positive FU or a sub-1 tier corrupts cost math (negative filament, tier-0 lookups);
        // reject like any other malformed entry (loadMerged logs + skips). The =off path is above.
        if (fu <= 0 || tier < 1) {
            throw new IllegalArgumentException("fu must be > 0 and tier >= 1, got " + fu + "@" + tier);
        }
        FuValue value = new FuValue(fu, tier);

        if (id.startsWith("#")) {
            TagKey<Item> key = TagKey.create(Registries.ITEM, parseTagId(id));
            // an override of a tag REPLACES the default entry for that tag, never stacks
            tags.removeIf(t -> t.tag().equals(key));
            tags.add(new TagEntry(key, value));
        } else {
            items.put(RegistryCompat.item(parseItemId(id)), value);
        }
        return true;
    }

    private static ResourceLocation parseTagId(String id) {
        ResourceLocation tagId = ResourceLocation.tryParse(id.substring(1));
        if (tagId == null) {
            throw new IllegalArgumentException("bad tag id " + id);
        }
        return tagId;
    }

    private static ResourceLocation parseItemId(String id) {
        ResourceLocation itemId = ResourceLocation.tryParse(id);
        if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)) {
            throw new IllegalArgumentException("unknown item " + id);
        }
        return itemId;
    }

    public static List<String> defaultEntries() {
        return List.of(
                // ===== T1 — bulk / infinite (trivially gathered) =====
                "minecraft:cobblestone=1@1", "minecraft:dirt=1@1", "minecraft:gravel=1@1",
                // grass_block: the most abundant surface block in the game — a grassy dirt
                // variant (no recipe → can't derive), valued = dirt so it actually prints
                // (was unprintable/allowlisted, which silently dropped grass footings, e.g.
                // floated jungle_hut's stilts). Cheapest tier; abundance-safe (T1 floor).
                "minecraft:grass_block=1@1",
                "minecraft:sand=1@1", "minecraft:red_sand=1@1", "minecraft:soul_sand=1@1",
                // cactus: renewable/farmable → lowest tier (abundance rule). VALUED (was
                // intentionally unvalued like bamboo/kelp) so the printer can AUTO-PLANT it
                // into the cactus farm; winder-blacklisted so it can't launder back to FU.
                "minecraft:cactus=2@1",
                // kelp: same treatment as cactus — VALUED at the lowest tier so the printer
                // auto-plants the first kelp layer into the kelp farm (it was unvalued/skipped);
                // winder-blacklisted so it can't launder back to FU.
                "minecraft:kelp=2@1",
                // bamboo: same treatment again — VALUED so the printer auto-plants the first
                // bamboo shoot into the bamboo farm (was unvalued/skipped); winder-blacklisted.
                "minecraft:bamboo=2@1",
                // twisting_vines: nether vine, renewable via bonemeal, has an item but no recipe →
                // unvalued leaf. Valued at the floor (like kelp/cactus) so scanned builds using it
                // print; winder-blacklisted so the renewable supply can't launder back to FU.
                "minecraft:twisting_vines=2@1",
                "minecraft:soul_soil=1@1", "minecraft:clay_ball=1@1", "minecraft:netherrack=1@1",
                "minecraft:deepslate=1@1", "minecraft:cobbled_deepslate=1@1", "minecraft:tuff=1@1",
                "minecraft:dripstone_block=1@1", "minecraft:pointed_dripstone=1@1", "minecraft:mud=1@1",
                "minecraft:snow_block=1@1", "minecraft:ice=1@1",
                // powder snow: its block-item IS the powder_snow_bucket (a SolidBucketItem), and the
                // bucket has no recipe — so without this entry the block has no derivable FU value and
                // strict mode refuses it (the "powder snow needs support" case). Priced at T2
                // (iron-bucket-gated) and winder-blacklisted, so it prints with a cost yet can't be
                // laundered into FU (powder snow is renewable in cold biomes).
                "minecraft:powder_snow_bucket=16@2",
                // white_concrete_powder anchored as a canonical for cosmetic-variant
                // normalization (the 16 dyed *_concrete_powder fall back to it). Can't
                // derive (white dye is unvalued); valued by composition = 4 sand + 4
                // gravel (both 1 FU) over 8 output ≈ 1 FU @ T1. white_concrete already
                // sits at 5@1, so hardened concrete stays pricier than its powder.
                "minecraft:white_concrete_powder=1@1",
                "minecraft:coal=2@1", "minecraft:moss_block=2@1",
                // mycelium: a dirt-variant ground block (no recipe; silk-touch + biome-gated).
                // Valued as cheap T1 ground alongside dirt/moss_block so it's usable as terrain
                // in any build without forcing a higher disc tier (mushroom blocks below are T2).
                "minecraft:mycelium=2@1",
                // farm crops / basic gatherables (2 FU): lets wheat→hay_block, sugar_cane→paper→book→
                // enchanting_table/cartography_table, and flint→fletching_table all derive correctly
                "minecraft:wheat=2@1", "minecraft:sugar_cane=2@1", "minecraft:flint=2@1",
                // stone family & wood (3 FU)
                "minecraft:stone=3@1", "minecraft:smooth_stone=3@1", "minecraft:stone_bricks=3@1",
                "minecraft:andesite=3@1", "minecraft:diorite=3@1", "minecraft:granite=3@1",
                "minecraft:calcite=3@1", "minecraft:sandstone=3@1", "minecraft:red_sandstone=3@1",
                "minecraft:basalt=3@1", "minecraft:smooth_basalt=3@1", "minecraft:blackstone=3@1",
                "#minecraft:logs=3@1", "#minecraft:planks=3@1",
                // processed building (5 FU)
                "minecraft:glass=5@1", "minecraft:terracotta=5@1", "minecraft:nether_bricks=5@1",
                "minecraft:white_concrete=5@1", "minecraft:orange_concrete=5@1",
                "minecraft:magenta_concrete=5@1", "minecraft:light_blue_concrete=5@1",
                "minecraft:yellow_concrete=5@1", "minecraft:lime_concrete=5@1",
                "minecraft:pink_concrete=5@1", "minecraft:gray_concrete=5@1",
                "minecraft:light_gray_concrete=5@1", "minecraft:cyan_concrete=5@1",
                "minecraft:purple_concrete=5@1", "minecraft:blue_concrete=5@1",
                "minecraft:brown_concrete=5@1", "minecraft:green_concrete=5@1",
                "minecraft:red_concrete=5@1", "minecraft:black_concrete=5@1",

                // ===== T2 — early ores / dimension entry (nuggets = ingot/9, lossy by design) =====
                "minecraft:end_stone=5@2", "minecraft:packed_ice=5@2", "minecraft:magma_block=5@2",
                "minecraft:copper_ingot=10@2", "minecraft:amethyst_shard=10@2", "minecraft:lapis_lazuli=10@2",
                "minecraft:gold_ingot=15@2", "minecraft:iron_ingot=20@2",
                // Nuggets priced so 9 nuggets cost MORE than the ingot (2*9=18 > gold 15; 3*9=27 >
                // iron 20). Item Mode can print a cheap nugget and craft it up, so an exactly-1/9
                // nugget would make print-9-nuggets -> craft ingot -> wind ingot a net-positive FU
                // pump; the ceil keeps that round-trip lossy.
                "minecraft:gold_nugget=2@2", "minecraft:iron_nugget=3@2",
                // leather: farmable animal drop (needed so book→enchanting_table chain derives)
                "minecraft:leather=8@2",
                // bone: common skeleton drop, AFK-farmable → PRINTABLE (fossils/bone-block decor)
                // but winder-blacklisted like the other renewable drops, so a skeleton farm can't
                // launder it to FU. Valuing it also lets bone_meal (1→3) and bone_block derive.
                "minecraft:bone=6@2",
                // huge-mushroom blocks (no recipe → direct value): giant-mushroom material, a step
                // above basic T1 building blocks. Bonemeal-renewable but involved to farm; T2's
                // ceiling (end_stone/packed_ice/magma_block ~5 FU) is all low-value, so no laundering.
                "minecraft:red_mushroom_block=5@2", "minecraft:brown_mushroom_block=5@2",
                "minecraft:mushroom_stem=5@2",
                // honeycomb (the leaf): valuing the item lets beehive (6 planks + 3 honeycomb) and
                // honeycomb_block (4 honeycomb) DERIVE through the recipe valuator — same pattern as
                // dyes→stained glass. bee_nest stays unvalued (worldgen-only, no recipe; apiaries use
                // crafted beehives). honeycomb is shear-farmed from a bee setup → T2.
                "minecraft:honeycomb=6@2",

                // ===== T3 — processing + early-game friction (wool/string are a real early gate) =====
                "minecraft:redstone=4@3", "minecraft:quartz=5@3", "minecraft:string=8@3",
                // gunpowder: creeper/ghast/witch drop, a real early-game friction item (TNT,
                // rockets, fire charges) that is tedious to farm. Sits just above string (8@3),
                // the same band and the same kind of hostile-mob gate, nudged up because creeper
                // farms are fussier to build than picking spider drops out of a generic mob farm.
                // PRINT-ONLY (winder-blacklisted) on the bone precedent: a creeper farm is
                // AFK-automatable, so letting it wind would open a laundering channel into T3.
                // Being unwindable also makes the abundance rule moot here: the tier only decides
                // which machine/spool can print it, not what a farm can launder into.
                // Valuing it lets tnt (5 gunpowder + 4 sand) and firework_rocket derive.
                "minecraft:gunpowder=10@3",
                "minecraft:obsidian=10@3", "minecraft:crying_obsidian=15@3", "minecraft:shroomlight=10@3",
                "minecraft:glowstone=20@3", "minecraft:slime_ball=30@3", "#minecraft:wool=30@3",
                // treasure, uncraftable loot — WIND-ONLY (on #no_print): recyclable into FU but
                // never printed. ~3x leather (8@2); at T3 a spool tops out at glowstone-tier prints,
                // so abundance-safe even though saddles/name tags are slowly villager-renewable.
                "minecraft:saddle=25@3", "minecraft:name_tag=25@3",
                // utility overrides (cheap mats, high automation power — pinned above derivation)
                "minecraft:hopper=100@3", "minecraft:bookshelf=40@3",
                // basic food (print-to-eat unlock; all food is winder-blacklisted)
                "minecraft:bread=15@3", "minecraft:apple=10@3", "minecraft:baked_potato=12@3",
                "minecraft:cookie=8@3", "minecraft:dried_kelp=8@3",
                "minecraft:mushroom_stew=15@3", "minecraft:beetroot_soup=15@3",

                // ===== T4 — renewable-valuable =====
                "minecraft:emerald=50@4", "minecraft:magma_cream=30@4",
                "minecraft:blaze_rod=40@4", "minecraft:ghast_tear=50@4", "minecraft:totem_of_undying=200@4",
                // Mob heads (no recipe → direct value). The four charged-creeper heads share one
                // acquisition path (a lightning-charged creeper must kill the mob), which is slow and
                // NOT AFK-automatable, so they are windable for a recycle payout AND stay trophy-
                // printable from official discs (#print_restricted) — the latter also lets scanned
                // builds using them as decor print. Capped at T4: a 40-FU spool tops out at blaze_rod,
                // so no head can wind into anything rarer than itself.
                "minecraft:creeper_head=40@4", "minecraft:zombie_head=40@4",
                "minecraft:skeleton_skull=40@4", "minecraft:piglin_head=40@4",
                // wither_skeleton_skull: WIND-ONLY (on #no_print). A wither-skeleton farm is AFK-
                // automatable, so it is deliberately capped at T4 (not its T7 boss-rarity) and barred
                // from printing. Winding is a lossy sink (its spool can't out-print the skull), and
                // keeping it unprintable preserves the "no printed wither-spawn ingredient" guard its
                // old unvalued status gave.
                "minecraft:wither_skeleton_skull=40@4",
                "minecraft:prismarine_shard=8@4", "minecraft:prismarine_crystals=12@4",
                // chorus is abundance-capped at T4 (a T6 chorus spool could print netherite)
                "minecraft:chorus_fruit=8@4", "minecraft:popped_chorus_fruit=10@4",
                "minecraft:sticky_piston=80@4",
                // hearty food
                "minecraft:cooked_beef=20@4", "minecraft:cooked_porkchop=20@4", "minecraft:cooked_chicken=18@4",
                "minecraft:cooked_mutton=18@4", "minecraft:cooked_rabbit=18@4", "minecraft:cooked_cod=15@4",
                "minecraft:cooked_salmon=18@4", "minecraft:golden_carrot=40@4", "minecraft:pumpkin_pie=20@4",
                "minecraft:rabbit_stew=30@4", "minecraft:suspicious_stew=20@4",

                // ===== T5 — deep mining / monument =====
                "minecraft:diamond=50@5", "minecraft:ender_pearl=40@5", "minecraft:sea_lantern=50@5",
                "minecraft:sponge=60@5", "minecraft:sculk=15@5", "minecraft:sculk_vein=15@5",
                "minecraft:sculk_catalyst=40@5", "minecraft:sculk_sensor=40@5", "minecraft:sculk_shrieker=40@5",
                "minecraft:shulker_shell=80@5", "minecraft:golden_apple=300@5",
                // trident + nautilus_shell are drowned-farmable (renewable via a mob farm), so the
                // abundance rule caps them BELOW netherite: at T6 their spool could print netherite
                // (unfarmable), letting an AFK drowned farm launder into it. T5 keeps them alongside
                // the already-capped shulker_shell; a T5 spool tops out at diamond-tier prints.
                "minecraft:trident=150@5", "minecraft:nautilus_shell=80@5",

                // ===== T6 — netherite + high-value finite =====
                "minecraft:netherite_ingot=500@6", "minecraft:netherite_scrap=125@6",
                "minecraft:ancient_debris=125@6",
                // T6-ACCESS ROUTES: high-value End/loot rares that WIND into a T6 spool, giving a
                // netherite path that isn't ancient-debris mining. All are manual grinds, never AFK
                // farms, so none can pump: elytra + dragon_head are End-city/ship exploration, notch
                // apples are loot-only (uncraftable, so pulled out of the blanket food blacklist for
                // this), heavy_core is Trial Chamber ominous-vault loot (1.21+ item; simply absent on
                // the 1.20.1 legacy line). This is what makes "convert equal-value rares into
                // netherite" work through the winder without touching the exact-tier backbone.
                "minecraft:elytra=2000@6", "minecraft:enchanted_golden_apple=1500@6",
                "minecraft:heavy_core=250@6",
                // dragon_head: post-dragon End-ship trophy, End-city-grindable. Now windable (off the
                // winder blacklist) as a slow netherite route, consistent with elytra from the same
                // biome; kept at T6 (off T7) so it also still trophy-prints on a T6 setup.
                "minecraft:dragon_head=250@6",

                // ===== T7 — boss / heavy-grind (base draconium added @T7 via DE compat when loaded) =====
                "minecraft:nether_star=1500@7",
                // dragon_egg: the ultimate 1-per-world trophy (unfarmable — drops once, never again),
                // WIND-ONLY (on #no_print). A big 10k recycle payout for your unique egg, kept
                // unprintable so it stays 1-per-world. Unfarmable, so any value is a one-time windfall
                // with no pump/abundance concern despite sitting a tier above its spool's rarest print.
                "minecraft:dragon_egg=10000@7",

                // ===== T8 — finite trophies (awakened draconium added @T8 wind-only via DE compat) =====
                "minecraft:echo_shard=500@8", "minecraft:heart_of_the_sea=800@8");
        // Derivation fills the rest: storage blocks (9x/4x base), stairs/slabs/walls,
        // tools, beacon (via nether_star), conduit (via heart_of_the_sea), quartz_block
        // (4x quartz), purpur/end_rod (via popped_chorus_fruit), etc.
        // UNPRINTABLE (deliberately unvalued — strict mode refuses them): survival-unobtainables
        // (bedrock, spawner, reinforced_deepslate, budding_amethyst, command blocks, barrier, ...).
        // wither_skeleton_skull and dragon_egg are now valued but WIND-ONLY via #no_print (recyclable,
        // never printed), not unvalued. See docs/rebalance/rebalance-plan.md.
    }
}
