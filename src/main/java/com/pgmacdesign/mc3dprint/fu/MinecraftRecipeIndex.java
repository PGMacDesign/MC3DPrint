package com.pgmacdesign.mc3dprint.fu;

import com.mojang.logging.LogUtils;
import com.pgmacdesign.mc3dprint.compat.RecipeCompat;
import com.pgmacdesign.mc3dprint.config.MC3DPrintConfig;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a live recipe snapshot (plus a {@link RegistryAccess} for assembling
 * recipe outputs) into the output -> recipes index that {@link RelaxationFuValuator}
 * relaxes. Built lazily the first time derivation runs after a
 * {@link FuValueRegistry#bind bind}, and thrown away whenever recipes reload.
 *
 * <p>The snapshot is a flat {@code Collection<RecipeHolder<?>>} (every loaded
 * recipe), filtered here by {@link Recipe#getType() type} — the per-type lookup
 * (formerly {@code RecipeManager.getAllRecipesFor}) moved behind {@code RecipeMap}
 * in 1.21.5, so iterating-and-filtering is the version-neutral path. Recipe types
 * consulted are config-gated: crafting (always), smelting
 * ({@code deriveFromSmelting}), and stonecutting ({@code deriveFromStonecutting}).
 * Blasting/smoking/campfire are excluded by design (they duplicate smelting and
 * add nothing). {@link Recipe#isSpecial() Special} recipes (map cloning, firework
 * assembly, …) are skipped — they have no fixed ingredient set to value.
 */
public final class MinecraftRecipeIndex {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Collection<RecipeHolder<?>> recipes;
    private final RegistryAccess registryAccess;

    /** Output item -> recipes producing it; built on first use. */
    private Map<Item, List<RelaxationFuValuator.RecipeView<Item>>> index;

    public MinecraftRecipeIndex(Collection<RecipeHolder<?>> recipes,
                                RegistryAccess registryAccess) {
        this.recipes = recipes;
        this.registryAccess = registryAccess;
    }

    /**
     * The output -> recipes index (triggers the one-time build). Outputs and ingredient
     * candidates are both canonicalized to their cosmetic-neutral sibling, so a dyeable
     * family (wool, glass, shulker boxes…) collapses onto one node rather than exploding
     * the walk across its complete re-dye graph. {@link RelaxationFuValuator} relaxes this.
     */
    public Map<Item, List<RelaxationFuValuator.RecipeView<Item>>> recipesByOutput() {
        if (index == null) {
            build();
        }
        return index;
    }

    private void build() {
        boolean smelting = MC3DPrintConfig.DERIVE_FROM_SMELTING.get();
        boolean stonecutting = MC3DPrintConfig.DERIVE_FROM_STONECUTTING.get();
        Map<Item, List<RelaxationFuValuator.RecipeView<Item>>> built = new HashMap<>();
        for (RecipeHolder<?> holder : recipes) {
            Recipe<?> recipe = holder.value();
            RecipeType<?> type = recipe.getType();
            boolean wanted = type == RecipeType.CRAFTING
                    || (smelting && type == RecipeType.SMELTING)
                    || (stonecutting && type == RecipeType.STONECUTTING);
            if (!wanted) {
                continue;
            }
            add(built, recipe);
        }
        index = built;
        LOGGER.debug("Built recipe FU index: {} output items have recipes", built.size());
    }

    private void add(Map<Item, List<RelaxationFuValuator.RecipeView<Item>>> built, Recipe<?> recipe) {
        if (recipe.isSpecial()) {
            return; // no fixed ingredient set to value
        }
        ItemStack result;
        try {
            result = result(recipe);
        } catch (RuntimeException e) {
            return; // dynamic/odd recipe output — ignore
        }
        if (result.isEmpty()) {
            return;
        }
        // Index under the canonical (colour-neutral) output so all of a dye family's
        // recipes — including the re-dye recipes — collapse onto one node.
        Item outputItem = FuValueRegistry.canonicalCosmeticVariant(result.getItem());
        RelaxationFuValuator.RecipeView<Item> view = toView(recipe, result.getCount());
        if (view != null) {
            built.computeIfAbsent(outputItem, k -> new ArrayList<>()).add(view);
        }
    }

    /**
     * The recipe's output stack. {@code getResultItem} on the base node; on 1.21.5+ (which removed
     * it) the result is resolved from the recipe's display. Deliberately NOT {@code assemble} — its
     * input is the concrete {@code CraftingInput}/{@code SingleRecipeInput}, so a generic
     * {@code RecipeInput} throws {@code ClassCastException} and silently kills all derivation.
     */
    private ItemStack result(Recipe<?> recipe) {
        //? if >=1.21.5 {
        /*net.minecraft.util.context.ContextMap ctx = new net.minecraft.util.context.ContextMap.Builder()
                .withParameter(net.minecraft.world.item.crafting.display.SlotDisplayContext.REGISTRIES, registryAccess)
                .create(net.minecraft.world.item.crafting.display.SlotDisplayContext.CONTEXT);
        for (net.minecraft.world.item.crafting.display.RecipeDisplay display : recipe.display()) {
            for (ItemStack stack : display.result().resolveForStacks(ctx)) {
                if (!stack.isEmpty()) {
                    return stack;
                }
            }
        }
        return ItemStack.EMPTY;
        *///?} else {
        return recipe.getResultItem(registryAccess);
        //?}
    }

    /** Snapshots a recipe's ingredient candidate keys; null if uninspectable. */
    private RelaxationFuValuator.RecipeView<Item> toView(Recipe<?> recipe, int outputCount) {
        List<Ingredient> ingredients;
        try {
            ingredients = RecipeCompat.ingredients(recipe);
        } catch (RuntimeException e) {
            return null;
        }
        List<List<Item>> slots = new ArrayList<>(ingredients.size());
        for (Ingredient ingredient : ingredients) {
            if (ingredient == null || ingredient.isEmpty()) {
                continue; // empty slot
            }
            // Tags are already expanded to concrete members; the valuator picks
            // the cheapest candidate per slot. Canonicalize cosmetic colour variants
            // and dedup so a "#wool" / "#shulker_boxes" slot collapses to a single
            // canonical key — otherwise the complete re-dye graph blows up the walk.
            List<Item> candidates = RecipeCompat.ingredientItems(ingredient);
            java.util.LinkedHashSet<Item> canonical = new java.util.LinkedHashSet<>();
            for (Item candidate : candidates) {
                canonical.add(FuValueRegistry.canonicalCosmeticVariant(candidate));
            }
            if (!canonical.isEmpty()) {
                slots.add(new ArrayList<>(canonical));
            }
        }
        final List<List<Item>> finalSlots = slots;
        final int finalCount = Math.max(1, outputCount);
        return new RelaxationFuValuator.RecipeView<>() {
            @Override
            public List<List<Item>> ingredientSlots() {
                return finalSlots;
            }

            @Override
            public int outputCount() {
                return finalCount;
            }
        };
    }
}
