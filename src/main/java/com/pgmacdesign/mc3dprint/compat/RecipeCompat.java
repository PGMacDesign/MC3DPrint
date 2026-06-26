package com.pgmacdesign.mc3dprint.compat;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;

import java.util.ArrayList;
import java.util.List;

/**
 * Version seam for the recipe-introspection APIs the FU valuator walks. Two
 * surfaces churned across 1.21.x and both lost their old shapes in 1.21.5:
 *
 * <ul>
 *   <li>{@code Recipe.getIngredients()} (a {@code NonNullList<Ingredient>}) was
 *       removed; the ingredient list now lives behind {@code placementInfo()}.</li>
 *   <li>{@code Ingredient.getItems()} (an {@code ItemStack[]} of expanded matches)
 *       became {@code items()}, a {@code Stream<Holder<Item>>}.</li>
 * </ul>
 *
 * <p>Both are read-only introspection used purely to value recipes, so flattening
 * each to a version-neutral {@code List} here keeps {@code MinecraftRecipeIndex}
 * free of guards. The recipe <i>result</i> is fetched uniformly via
 * {@code Recipe.assemble(EMPTY_INPUT, …)} (identical on both versions for the
 * non-special crafting/smelting/stonecutting recipes we consult), so it needs no
 * seam — only ingredient extraction does.
 */
public final class RecipeCompat {
    private RecipeCompat() {}

    /** The recipe's ingredient slots, empty slots excluded. */
    public static List<Ingredient> ingredients(Recipe<?> recipe) {
        //? if >=1.21.5 {
        /*return recipe.placementInfo().ingredients();
        *///?} else {
        return recipe.getIngredients();
        //?}
    }

    /**
     * The concrete item candidates an ingredient accepts (tags already expanded
     * to members). The valuator picks the cheapest-valued candidate per slot.
     */
    public static List<Item> ingredientItems(Ingredient ingredient) {
        List<Item> items = new ArrayList<>();
        //? if >=1.21.5 {
        /*ingredient.items().forEach(holder -> items.add(holder.value()));
        *///?} else {
        for (ItemStack match : ingredient.getItems()) {
            if (!match.isEmpty()) {
                items.add(match.getItem());
            }
        }
        //?}
        return items;
    }
}
