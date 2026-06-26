package com.pgmacdesign.mc3dprint.fu;

import com.mojang.logging.LogUtils;
import com.pgmacdesign.mc3dprint.config.MC3DPrintConfig;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Binds a live {@link RecipeManager} (plus a {@link RegistryAccess} for reading
 * recipe outputs) to the pure {@link RecipeFuValuator.RecipeGraph}. Built lazily
 * the first time derivation runs after a {@link FuValueRegistry#bind bind}, and
 * thrown away whenever recipes reload.
 *
 * <p>Recipe types consulted are config-gated:
 * crafting (always), smelting ({@code deriveFromSmelting}), and stonecutting
 * ({@code deriveFromStonecutting}). Blasting/smoking/campfire are excluded by
 * design (they duplicate smelting and add nothing). {@link Recipe#isSpecial()
 * Special} recipes (map cloning, firework assembly, …) are skipped — they have
 * no fixed ingredient set to value.
 */
public final class MinecraftRecipeIndex implements RecipeFuValuator.RecipeGraph<Item> {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final RecipeManager recipeManager;
    private final RegistryAccess registryAccess;
    /** Supplies the explicit/base value for an item (config + API map). */
    private final Function<Item, Optional<FuValue>> baseLookup;

    /** Output item -> recipes producing it; built on first use. */
    private Map<Item, List<RecipeFuValuator.RecipeView<Item>>> index;

    public MinecraftRecipeIndex(RecipeManager recipeManager,
                                RegistryAccess registryAccess,
                                Function<Item, Optional<FuValue>> baseLookup) {
        this.recipeManager = recipeManager;
        this.registryAccess = registryAccess;
        this.baseLookup = baseLookup;
    }

    @Override
    public List<RecipeFuValuator.RecipeView<Item>> recipesFor(Item output) {
        if (index == null) {
            build();
        }
        return index.getOrDefault(output, List.of());
    }

    @Override
    public Optional<FuValue> baseValue(Item item) {
        return baseLookup.apply(item);
    }

    private void build() {
        Map<Item, List<RecipeFuValuator.RecipeView<Item>>> built = new HashMap<>();
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

    private <I extends RecipeInput, T extends Recipe<I>> void addType(
            Map<Item, List<RecipeFuValuator.RecipeView<Item>>> built, RecipeType<T> type) {
        for (RecipeHolder<T> holder : recipeManager.getAllRecipesFor(type)) {
            T recipe = holder.value();
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
            Item outputItem = result.getItem();
            RecipeFuValuator.RecipeView<Item> view = toView(recipe, result.getCount());
            if (view != null) {
                built.computeIfAbsent(outputItem, k -> new ArrayList<>()).add(view);
            }
        }
    }

    /** Snapshots a recipe's ingredient candidate keys; null if uninspectable. */
    private RecipeFuValuator.RecipeView<Item> toView(Recipe<?> recipe, int outputCount) {
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
            // getItems() already expands tags to concrete stacks; the valuator
            // picks the cheapest member.
            ItemStack[] matches = ingredient.getItems();
            List<Item> candidates = new ArrayList<>(matches.length);
            for (ItemStack match : matches) {
                if (!match.isEmpty()) {
                    candidates.add(match.getItem());
                }
            }
            if (!candidates.isEmpty()) {
                slots.add(candidates);
            }
        }
        final List<List<Item>> finalSlots = slots;
        final int finalCount = Math.max(1, outputCount);
        return new RecipeFuValuator.RecipeView<>() {
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
