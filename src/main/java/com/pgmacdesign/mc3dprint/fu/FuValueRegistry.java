package com.pgmacdesign.mc3dprint.fu;

import com.mojang.logging.LogUtils;
import com.pgmacdesign.mc3dprint.config.MC3DPrintConfig;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.ArrayList;
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
 * {@link RecipeManager} via {@link #bind} (from datapack-sync / recipes-updated
 * events), and the first {@link #valueOf} that misses the explicit + API maps
 * walks the recipe graph (see {@link RecipeFuValuator}). Until a bind happens
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
    private static RecipeManager boundRecipeManager;
    private static RegistryAccess boundRegistryAccess;
    private static RecipeFuValuator<Item> valuator;

    private record TagEntry(TagKey<Item> tag, FuValue value) {}

    private FuValueRegistry() {}

    /**
     * The FU value of a stack, following the documented precedence. Recipe
     * derivation only runs if a {@link RecipeManager} is bound; otherwise the
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
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
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
        return ForgeRegistries.ITEMS.tags() != null
                && ForgeRegistries.ITEMS.tags().getTag(tag).contains(item);
    }

    /** Recipe-derived value (or empty if unbound / underivable). */
    private static synchronized Optional<FuValue> derive(Item item) {
        if (boundRecipeManager == null || boundRegistryAccess == null) {
            return Optional.empty(); // not bound yet — fail safe
        }
        if (valuator == null) {
            MinecraftRecipeIndex graph = new MinecraftRecipeIndex(
                    boundRecipeManager, boundRegistryAccess,
                    derivedItem -> baseValue(derivedItem, null));
            valuator = new RecipeFuValuator<>(graph);
        }
        return valuator.valueOf(item);
    }

    /**
     * Binds the live recipe data used for derivation. Called from
     * {@code OnDatapackSyncEvent} (server) and {@code RecipesUpdatedEvent}
     * (client). Re-binding drops the derived cache so the next derive rebuilds
     * the index against the new recipes.
     */
    public static synchronized void bind(RecipeManager recipeManager, RegistryAccess registryAccess) {
        boundRecipeManager = recipeManager;
        boundRegistryAccess = registryAccess;
        valuator = null; // lazy rebuild on next derive
        LOGGER.debug("Bound recipe manager for FU derivation");
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
        for (String entry : MC3DPrintConfig.FU_VALUES.get()) {
            try {
                parseEntry(entry.trim(), items, tags);
            } catch (RuntimeException e) {
                LOGGER.warn("Skipping invalid FU value entry '{}': {}", entry, e.getMessage());
            }
        }
        itemValues = items;
        tagValues = tags;
    }

    private static void parseEntry(String entry, Map<Item, FuValue> items, List<TagEntry> tags) {
        if (entry.isEmpty() || entry.startsWith("//")) {
            return;
        }
        int eq = entry.indexOf('=');
        int at = entry.indexOf('@', eq);
        if (eq < 0 || at < 0) {
            throw new IllegalArgumentException("expected <id>=<fu>@<tier>");
        }
        String id = entry.substring(0, eq).trim();
        FuValue value = new FuValue(
                Integer.parseInt(entry.substring(eq + 1, at).trim()),
                Integer.parseInt(entry.substring(at + 1).trim()));

        if (id.startsWith("#")) {
            ResourceLocation tagId = ResourceLocation.tryParse(id.substring(1));
            if (tagId == null) {
                throw new IllegalArgumentException("bad tag id " + id);
            }
            tags.add(new TagEntry(TagKey.create(Registries.ITEM, tagId), value));
        } else {
            ResourceLocation itemId = ResourceLocation.tryParse(id);
            if (itemId == null || !ForgeRegistries.ITEMS.containsKey(itemId)) {
                throw new IllegalArgumentException("unknown item " + id);
            }
            items.put(ForgeRegistries.ITEMS.getValue(itemId), value);
        }
    }

    public static List<String> defaultEntries() {
        return List.of(
                // 1 FU @ T1 — bulk fill
                "minecraft:cobblestone=1@1", "minecraft:dirt=1@1", "minecraft:gravel=1@1",
                "minecraft:sand=1@1", "minecraft:soul_sand=1@1", "minecraft:soul_soil=1@1",
                "minecraft:clay_ball=1@1", "minecraft:netherrack=1@1",
                // 3 FU @ T1 — stone family & wood
                "minecraft:stone=3@1", "minecraft:sandstone=3@1", "minecraft:smooth_stone=3@1",
                "minecraft:stone_bricks=3@1", "minecraft:andesite=3@1", "minecraft:diorite=3@1",
                "minecraft:granite=3@1", "minecraft:calcite=3@1", "minecraft:tuff=3@1",
                "#minecraft:logs=3@1", "#minecraft:planks=3@1",
                // 5 FU @ T1 — processed building blocks
                "minecraft:glass=5@1", "minecraft:terracotta=5@1", "#minecraft:wool=5@1",
                "minecraft:nether_bricks=5@1", "minecraft:quartz_block=5@1",
                "minecraft:white_concrete=5@1", "minecraft:orange_concrete=5@1",
                "minecraft:magenta_concrete=5@1", "minecraft:light_blue_concrete=5@1",
                "minecraft:yellow_concrete=5@1", "minecraft:lime_concrete=5@1",
                "minecraft:pink_concrete=5@1", "minecraft:gray_concrete=5@1",
                "minecraft:light_gray_concrete=5@1", "minecraft:cyan_concrete=5@1",
                "minecraft:purple_concrete=5@1", "minecraft:blue_concrete=5@1",
                "minecraft:brown_concrete=5@1", "minecraft:green_concrete=5@1",
                "minecraft:red_concrete=5@1", "minecraft:black_concrete=5@1",
                // 10 FU @ T2 — base materials (gems/ingots are bases, not derived)
                "minecraft:copper_ingot=10@2", "minecraft:amethyst_shard=10@2", "minecraft:lapis_lazuli=10@2",
                // 15-20 FU @ T2 — metals (nuggets = ingot/9 rounded DOWN, lossy by design: 2026-06-11)
                "minecraft:gold_ingot=15@2", "minecraft:iron_ingot=20@2",
                "minecraft:gold_nugget=1@2", "minecraft:iron_nugget=2@2",
                // ~30 FU @ T3
                "minecraft:redstone=4@3", "minecraft:slime_ball=30@3", "minecraft:magma_cream=30@3",
                // coal: a base T1 value so coal_block DERIVES to 18@1 (9 coal) instead
                // of being unvalued. (coal_block's explicit entry was removed in favor
                // of derivation — see docs/FU-VALUES-AND-COMPAT.md)
                "minecraft:coal=2@1",
                // 50 FU @ T4 — emerald (villager-renewable, stays the T4 gem)
                "minecraft:emerald=50@4",
                // 50 FU @ T5 — diamond (mined; gated above emerald per 2026-06-11 decision)
                "minecraft:diamond=50@5",
                // T5+
                "minecraft:netherite_ingot=500@5", "minecraft:ancient_debris=125@5",
                "minecraft:nether_star=1500@6",
                "minecraft:dragon_egg=2500@7");
        // NOTE: storage/material blocks (diamond_block, iron_block, ...) are no
        // longer hardcoded — they DERIVE from their crafting recipe (9x or 4x the
        // base material) at the constituent's tier. See defaultEntries() history
        // and docs/FU-VALUES-AND-COMPAT.md. coal_block derives from coal above.
    }
}
