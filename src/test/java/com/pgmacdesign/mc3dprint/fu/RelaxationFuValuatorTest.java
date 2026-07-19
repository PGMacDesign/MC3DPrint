package com.pgmacdesign.mc3dprint.fu;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parity tests for {@link RelaxationFuValuator}: it must produce the same values as the
 * canonical {@link RecipeFuValuator} on the shared cases, and — bounded to
 * {@link RecipeFuValuator#MAX_DEPTH} — reproduce the DFS's depth cap, while unbounded it
 * values deep chains the cap misses. Keys are plain Strings standing in for items.
 */
class RelaxationFuValuatorTest {

    /** Mutable recipe-graph fixture shared by both valuators. */
    private static final class Fixture {
        final Map<String, FuValue> bases = new HashMap<>();
        final Map<String, List<RecipeFuValuator.RecipeView<String>>> recipes = new HashMap<>();

        Fixture base(String item, int fu, int tier) {
            bases.put(item, new FuValue(fu, tier));
            return this;
        }

        @SafeVarargs
        final Fixture recipe(String output, int count, List<String>... slots) {
            recipes.computeIfAbsent(output, k -> new ArrayList<>())
                    .add(new RecipeFuValuator.RecipeView<>() {
                        @Override
                        public List<List<String>> ingredientSlots() {
                            return List.of(slots);
                        }

                        @Override
                        public int outputCount() {
                            return count;
                        }
                    });
            return this;
        }

        Optional<FuValue> baseValue(String item) {
            return Optional.ofNullable(bases.get(item));
        }

        /** DFS view over the same data. */
        RecipeFuValuator<String> dfs() {
            return new RecipeFuValuator<>(new RecipeFuValuator.RecipeGraph<>() {
                @Override
                public List<RecipeFuValuator.RecipeView<String>> recipesFor(String output) {
                    return recipes.getOrDefault(output, List.of());
                }

                @Override
                public Optional<FuValue> baseValue(String item) {
                    return Fixture.this.baseValue(item);
                }
            });
        }

        RelaxationFuValuator<String> relax(int maxRounds) {
            return new RelaxationFuValuator<>(recipes, this::baseValue, maxRounds);
        }
    }

    private static List<String> slot(String... keys) {
        return RecipeFuValuator.slot(keys);
    }

    @Test
    void baseValueShortCircuits() {
        Fixture f = new Fixture().base("diamond", 50, 5);
        assertEquals(new FuValue(50, 5), f.relax(12).valueOf("diamond").orElseThrow());
    }

    @Test
    void derivesNinePackWithMaxTier() {
        Fixture f = new Fixture()
                .base("diamond", 50, 5)
                .recipe("diamond_block", 1,
                        slot("diamond"), slot("diamond"), slot("diamond"),
                        slot("diamond"), slot("diamond"), slot("diamond"),
                        slot("diamond"), slot("diamond"), slot("diamond"));
        assertEquals(new FuValue(450, 5), f.relax(12).valueOf("diamond_block").orElseThrow());
    }

    @Test
    void floorsAndClampsToMinimumOne() {
        Fixture f = new Fixture().base("pip", 1, 1)
                .recipe("third", 3, slot("pip"), slot("pip"), slot("pip"));
        assertEquals(new FuValue(1, 1), f.relax(12).valueOf("third").orElseThrow());

        Fixture g = new Fixture().base("nib", 2, 1)
                .recipe("septa", 4, slot("nib"), slot("nib"), slot("nib"), slot("nib"),
                        slot("nib"), slot("nib"), slot("nib"));
        assertEquals(3, g.relax(12).valueOf("septa").orElseThrow().fu()); // floor(14/4)=3
    }

    @Test
    void picksMinAcrossRecipesAndCheapestInSlot() {
        Fixture f = new Fixture()
                .base("cheap_mat", 10, 1)
                .base("dear_mat", 40, 3)
                .recipe("widget", 1, slot("cheap_mat"))
                .recipe("widget", 1, slot("dear_mat"));
        FuValue widget = f.relax(12).valueOf("widget").orElseThrow();
        assertEquals(10, widget.fu());
        assertEquals(1, widget.tier());

        Fixture g = new Fixture()
                .base("oak", 3, 1)
                .base("crimson", 8, 2)
                .recipe("stick", 1, slot("oak", "crimson"));
        assertEquals(3, g.relax(12).valueOf("stick").orElseThrow().fu());
    }

    @Test
    void cycleWithABaseResolvesToTheBasePath() {
        Fixture f = new Fixture()
                .base("raw", 5, 1)
                .recipe("a", 1, slot("b"))
                .recipe("a", 1, slot("raw"))
                .recipe("b", 1, slot("a"));
        RelaxationFuValuator<String> r = f.relax(12);
        assertEquals(5, r.valueOf("a").orElseThrow().fu());
        assertEquals(5, r.valueOf("b").orElseThrow().fu());
    }

    @Test
    void pureCycleIsUnvalued() {
        Fixture f = new Fixture()
                .recipe("a", 1, slot("b"))
                .recipe("b", 1, slot("a"));
        RelaxationFuValuator<String> r = f.relax(12);
        assertTrue(r.valueOf("a").isEmpty());
        assertTrue(r.valueOf("b").isEmpty());
    }

    @Test
    void unusableIngredientMakesRecipeUnusable() {
        Fixture f = new Fixture()
                .base("known", 10, 1)
                .recipe("widget", 1, slot("known"), slot("mystery"));
        assertTrue(f.relax(12).valueOf("widget").isEmpty());
    }

    @Test
    void emptySlotsAreFree() {
        Fixture f = new Fixture()
                .base("ore", 12, 2)
                .recipe("ingot", 1, slot("ore"), slot());
        assertEquals(12, f.relax(12).valueOf("ingot").orElseThrow().fu());
    }

    /**
     * The crux: bounded to MAX_DEPTH the relaxation reproduces the DFS's depth cap
     * (a chain deeper than the cap is unvalued), while unbounded it values the chain.
     */
    @Test
    void boundedMatchesDepthCapUnboundedDoesNot() {
        Fixture f = new Fixture();
        int n = RecipeFuValuator.MAX_DEPTH + 5;
        f.base("item" + n, 1, 1);
        for (int i = 0; i < n; i++) {
            f.recipe("item" + i, 1, slot("item" + (i + 1)));
        }

        // DFS and bounded relaxation agree: too deep -> unvalued.
        assertTrue(f.dfs().valueOf("item0").isEmpty(), "DFS: deeper than cap is unvalued");
        assertTrue(f.relax(RecipeFuValuator.MAX_DEPTH).valueOf("item0").isEmpty(),
                "bounded relaxation must reproduce the depth cap");

        // Unbounded relaxation reaches the base and values the whole chain.
        assertEquals(1, f.relax(Integer.MAX_VALUE).valueOf("item0").orElseThrow().fu(),
                "unbounded relaxation values a chain the cap misses");

        // A chain within the cap resolves identically under both.
        Fixture shallow = new Fixture().base("bottom", 7, 2)
                .recipe("top", 1, slot("mid"))
                .recipe("mid", 1, slot("bottom"));
        assertEquals(7, shallow.dfs().valueOf("top").orElseThrow().fu());
        assertEquals(7, shallow.relax(RecipeFuValuator.MAX_DEPTH).valueOf("top").orElseThrow().fu());
    }

    /** Direct parity sweep: on a well-behaved multi-path graph, bounded relaxation == DFS. */
    @Test
    void boundedRelaxationMatchesDfsOnLayeredGraph() {
        Fixture f = new Fixture()
                .base("wood", 2, 1)
                .base("iron", 8, 2)
                .base("gold", 20, 3);
        // a small layered craft tree with multiple recipes and tag-like slots
        f.recipe("plank", 4, slot("wood"));
        f.recipe("stick", 4, slot("plank"), slot("plank"));
        f.recipe("gear", 1, slot("iron"), slot("iron"), slot("iron"), slot("iron"), slot("stick"));
        f.recipe("gilded_gear", 1, slot("gear"), slot("gold"));
        f.recipe("gilded_gear", 1, slot("iron", "gold"), slot("gear")); // cheaper alt path

        RecipeFuValuator<String> dfs = f.dfs();
        RelaxationFuValuator<String> relax = f.relax(RecipeFuValuator.MAX_DEPTH);
        for (String item : List.of("wood", "iron", "gold", "plank", "stick", "gear", "gilded_gear")) {
            assertEquals(dfs.valueOf(item), relax.valueOf(item),
                    "bounded relaxation must match the DFS for " + item);
        }
    }
}
