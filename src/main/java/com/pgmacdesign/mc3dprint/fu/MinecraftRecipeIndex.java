package com.pgmacdesign.mc3dprint.fu;

import com.mojang.logging.LogUtils;
import com.pgmacdesign.mc3dprint.config.MC3DPrintConfig;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a live {@link RecipeManager} (plus a {@link RegistryAccess} for reading
 * recipe outputs) into the output -> recipes index that {@link RelaxationFuValuator}
 * relaxes. Built lazily the first time derivation runs after a
 * {@link FuValueRegistry#bind bind}, and thrown away whenever recipes reload.
 *
 * <p>Recipe types consulted are config-gated:
 * crafting (always), smelting ({@code deriveFromSmelting}), and stonecutting
 * ({@code deriveFromStonecutting}). Blasting/smoking/campfire are excluded by
 * design (they duplicate smelting and add nothing). {@link Recipe#isSpecial()
 * Special} recipes (map cloning, firework assembly, …) are skipped — they have
 * no fixed ingredient set to value.
 */
public final class MinecraftRecipeIndex {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final RecipeManager recipeManager;
    private final RegistryAccess registryAccess;

    /** Output item -> recipes producing it; built on first use. */
    private Map<Item, List<RelaxationFuValuator.RecipeView<Item>>> index;

    public MinecraftRecipeIndex(RecipeManager recipeManager,
                                RegistryAccess registryAccess) {
        this.recipeManager = recipeManager;
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
        Map<Item, List<RelaxationFuValuator.RecipeView<Item>>> built = new HashMap<>();
        addType(built, RecipeType.CRAFTING);
        if (MC3DPrintConfig.DERIVE_FROM_SMELTING.get()) {
            addType(built, RecipeType.SMELTING);
        }
        if (MC3DPrintConfig.DERIVE_FROM_STONECUTTING.get()) {
            addType(built, RecipeType.STONECUTTING);
        }
        index = built;
        LOGGER.debug("Built recipe FU index: {} output items have recipes", built.size());
    }

    private <C extends Container, T extends Recipe<C>> void addType(
            Map<Item, List<RelaxationFuValuator.RecipeView<Item>>> built, RecipeType<T> type) {
        for (T recipe : recipeManager.getAllRecipesFor(type)) {
            if (recipe.isSpecial()) {
                continue; // no fixed ingredient set to value
            }
            ItemStack result;
            try {
                result = recipe.getResultItem(registryAccess);
            } catch (RuntimeException e) {
                continue; // dynamic/odd recipe output — ignore
            }
            if (result.isEmpty()) {
                continue;
            }
            // Index under the canonical (colour-neutral) output so all of a dye family's
            // recipes — including the re-dye recipes — collapse onto one node.
            Item outputItem = FuValueRegistry.canonicalCosmeticVariant(result.getItem());
            RelaxationFuValuator.RecipeView<Item> view = toView(recipe, result.getCount());
            if (view != null) {
                built.computeIfAbsent(outputItem, k -> new ArrayList<>()).add(view);
            }
        }
    }

    /** Snapshots a recipe's ingredient candidate keys; null if uninspectable. */
    private RelaxationFuValuator.RecipeView<Item> toView(Recipe<?> recipe, int outputCount) {
        NonNullList<Ingredient> ingredients;
        try {
            ingredients = recipe.getIngredients();
        } catch (RuntimeException e) {
            return null;
        }
        List<List<Item>> slots = new ArrayList<>(ingredients.size());
        for (Ingredient ingredient : ingredients) {
            if (ingredient == null || ingredient.isEmpty()) {
                continue; // empty slot
            }
            // getItems() already expands tags to concrete stacks; the valuator picks
            // the cheapest member. Canonicalize cosmetic colour variants and dedup so a
            // "#wool" / "#shulker_boxes" slot collapses to a single canonical key —
            // otherwise the complete re-dye graph blows up the walk.
            ItemStack[] matches = ingredient.getItems();
            java.util.LinkedHashSet<Item> candidates = new java.util.LinkedHashSet<>();
            for (ItemStack match : matches) {
                if (!match.isEmpty()) {
                    candidates.add(FuValueRegistry.canonicalCosmeticVariant(match.getItem()));
                }
            }
            if (!candidates.isEmpty()) {
                slots.add(new ArrayList<>(candidates));
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
