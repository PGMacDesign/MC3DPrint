package com.pgmacdesign.mc3dprint.fu;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-graph valuation tests with a hand-built fake recipe map — no Minecraft
 * bootstrap. Keys are plain Strings standing in for items.
 */
class RecipeFuValuatorTest {

    /** A tiny in-memory recipe graph builder. */
    private static final class FakeGraph implements RecipeFuValuator.RecipeGraph<String> {
        final Map<String, FuValue> bases = new HashMap<>();
        final Map<String, List<RecipeFuValuator.RecipeView<String>>> recipes = new HashMap<>();

        FakeGraph base(String item, int fu, int tier) {
            bases.put(item, new FuValue(fu, tier));
            return this;
        }

        /** Add a recipe: output produced in `count` from the given ingredient slots. */
        @SafeVarargs
        final FakeGraph recipe(String output, int count, List<String>... slots) {
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

        @Override
        public List<RecipeFuValuator.RecipeView<String>> recipesFor(String output) {
            return recipes.getOrDefault(output, List.of());
        }

        @Override
        public Optional<FuValue> baseValue(String item) {
            return Optional.ofNullable(bases.get(item));
        }
    }

    private static List<String> slot(String... keys) {
        return RecipeFuValuator.slot(keys);
    }

    @Test
    void baseValueShortCircuitsDerivation() {
        FakeGraph g = new FakeGraph().base("diamond", 50, 5);
        RecipeFuValuator<String> v = new RecipeFuValuator<>(g);
        assertEquals(new FuValue(50, 5), v.valueOf("diamond").orElseThrow());
    }

    @Test
    void derivesNinePackBlockWithMaxTier() {
        // diamond_block = 9 diamond / 1; tier = max ingredient tier (5)
        FakeGraph g = new FakeGraph()
                .base("diamond", 50, 5)
                .recipe("diamond_block", 1,
                        slot("diamond"), slot("diamond"), slot("diamond"),
                        slot("diamond"), slot("diamond"), slot("diamond"),
                        slot("diamond"), slot("diamond"), slot("diamond"));
        RecipeFuValuator<String> v = new RecipeFuValuator<>(g);
        assertEquals(new FuValue(450, 5), v.valueOf("diamond_block").orElseThrow());
    }

    @Test
    void floorsAndClampsToMinimumOne() {
        // 3 of a 1-FU input -> 3 output: floor(3/3) = 1
        FakeGraph g = new FakeGraph()
                .base("pip", 1, 1)
                .recipe("third", 3, slot("pip"), slot("pip"), slot("pip"));
        RecipeFuValuator<String> v = new RecipeFuValuator<>(g);
        assertEquals(new FuValue(1, 1), v.valueOf("third").orElseThrow());

        // 5 of a 1-FU input -> 4 output: floor(5/4) = 1 (rounds down)
        FakeGraph g2 = new FakeGraph()
                .base("pip", 1, 1)
                .recipe("quad", 4, slot("pip"), slot("pip"), slot("pip"), slot("pip"), slot("pip"));
        assertEquals(1, new RecipeFuValuator<>(g2).valueOf("quad").orElseThrow().fu());

        // 7 of a 2-FU input -> 4 output: floor(14/4) = 3
        FakeGraph g3 = new FakeGraph()
                .base("nib", 2, 1)
                .recipe("septa", 4, slot("nib"), slot("nib"), slot("nib"), slot("nib"),
                        slot("nib"), slot("nib"), slot("nib"));
        assertEquals(3, new RecipeFuValuator<>(g3).valueOf("septa").orElseThrow().fu());
    }

    @Test
    void picksMinAcrossRecipes() {
        // two recipes for "widget": cheap (10) and expensive (40) -> min wins
        FakeGraph g = new FakeGraph()
                .base("cheap_mat", 10, 1)
                .base("dear_mat", 40, 3)
                .recipe("widget", 1, slot("cheap_mat"))
                .recipe("widget", 1, slot("dear_mat"));
        RecipeFuValuator<String> v = new RecipeFuValuator<>(g);
        FuValue widget = v.valueOf("widget").orElseThrow();
        assertEquals(10, widget.fu());
        assertEquals(1, widget.tier()); // winning recipe's max ingredient tier
    }

    @Test
    void picksCheapestTagMemberWithinASlot() {
        // a slot listing two candidates (a tag) -> cheapest member is chosen
        FakeGraph g = new FakeGraph()
                .base("oak", 3, 1)
                .base("crimson", 8, 2)
                .recipe("stick", 1, slot("oak", "crimson"));
        assertEquals(3, new RecipeFuValuator<>(g).valueOf("stick").orElseThrow().fu());
    }

    @Test
    void cycleDetectionSkipsTheCyclicPath() {
        // a <-> b reversible recipes, plus a real base path for a. Derivation of
        // 'a' must not loop forever and must find the base-backed value.
        FakeGraph g = new FakeGraph()
                .base("raw", 5, 1)
                .recipe("a", 1, slot("b"))      // a from b (cyclic)
                .recipe("a", 1, slot("raw"))    // a from raw (valid)
                .recipe("b", 1, slot("a"));     // b from a (cyclic)
        RecipeFuValuator<String> v = new RecipeFuValuator<>(g);
        assertEquals(5, v.valueOf("a").orElseThrow().fu());
        // b can only be made from a -> a's value (5)
        assertEquals(5, v.valueOf("b").orElseThrow().fu());
    }

    @Test
    void pureCycleIsUnderivable() {
        // a from b, b from a, no base anywhere -> neither resolves
        FakeGraph g = new FakeGraph()
                .recipe("a", 1, slot("b"))
                .recipe("b", 1, slot("a"));
        RecipeFuValuator<String> v = new RecipeFuValuator<>(g);
        assertTrue(v.valueOf("a").isEmpty());
        assertTrue(v.valueOf("b").isEmpty());
    }

    @Test
    void depthCapPrunesDeepChains() {
        // chain item0 <- item1 <- ... <- item(N), base only at the very bottom,
        // deeper than MAX_DEPTH. The top item can't reach the base -> empty.
        FakeGraph g = new FakeGraph();
        int n = RecipeFuValuator.MAX_DEPTH + 5;
        g.base("item" + n, 1, 1);
        for (int i = 0; i < n; i++) {
            g.recipe("item" + i, 1, slot("item" + (i + 1)));
        }
        RecipeFuValuator<String> v = new RecipeFuValuator<>(g);
        assertTrue(v.valueOf("item0").isEmpty(), "chain deeper than MAX_DEPTH must not resolve");

        // a shallow chain within the cap resolves fine
        FakeGraph shallow = new FakeGraph().base("bottom", 7, 2)
                .recipe("top", 1, slot("mid"))
                .recipe("mid", 1, slot("bottom"));
        assertEquals(7, new RecipeFuValuator<>(shallow).valueOf("top").orElseThrow().fu());
    }

    @Test
    void recipeWithAnUnvaluedRequiredIngredientIsUnusable() {
        // widget needs valued + unvalued; the recipe can't be priced -> empty
        FakeGraph g = new FakeGraph()
                .base("known", 10, 1)
                .recipe("widget", 1, slot("known"), slot("mystery"));
        assertTrue(new RecipeFuValuator<>(g).valueOf("widget").isEmpty());
    }

    @Test
    void emptySlotsAreTreatedAsFree() {
        // a fuel/empty slot contributes nothing; output values from the rest
        FakeGraph g = new FakeGraph()
                .base("ore", 12, 2)
                .recipe("ingot", 1, slot("ore"), slot()); // second slot = free
        assertEquals(12, new RecipeFuValuator<>(g).valueOf("ingot").orElseThrow().fu());
    }

    @Test
    void broadUnderivableGraphTerminatesFast() {
        // Regression for the creative-tab / tooltip FREEZE: a broad recipe graph
        // whose leaves are genuinely underivable. Each item has 3 slots, each a
        // 3-candidate tag -> 9 child lookups per item; across 10 levels that's ~9^10
        // re-walks of shared underivable subtrees UNLESS complete (proven dead-end)
        // misses are memoized. Leaves sit shallower than MAX_DEPTH so their null is a
        // real dead-end, not a depth cut. With the fix this resolves in microseconds;
        // without it, it never finishes.
        FakeGraph g = new FakeGraph();
        String[] kinds = {"a", "b", "c"};
        for (int i = 0; i < 10; i++) {
            List<String> children = slot(kinds[0] + (i + 1), kinds[1] + (i + 1), kinds[2] + (i + 1));
            for (String k : kinds) {
                g.recipe(k + i, 1, children, children, children);
            }
        }
        // a10/b10/c10 have no recipe and no base value -> complete dead-ends.
        RecipeFuValuator<String> v = new RecipeFuValuator<>(g);
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(5),
                () -> assertTrue(v.valueOf("a0").isEmpty(),
                        "a broad underivable graph must resolve to empty, not hang"));
    }

    @Test
    void broadGraphStillDerivesWhenLeavesValued() {
        // Same broad shape, but the leaves carry a base value: caching complete misses
        // must not corrupt real derivation. Each of the 10 recipe levels multiplies the
        // cheapest child by its 3 slots: leaf=2 -> level9=6 -> ... -> level0 = 2*3^10.
        FakeGraph g = new FakeGraph();
        String[] kinds = {"a", "b", "c"};
        for (int i = 0; i < 10; i++) {
            List<String> children = slot(kinds[0] + (i + 1), kinds[1] + (i + 1), kinds[2] + (i + 1));
            for (String k : kinds) {
                g.recipe(k + i, 1, children, children, children);
            }
        }
        for (String k : kinds) {
            g.base(k + 10, 2, 4);
        }
        RecipeFuValuator<String> v = new RecipeFuValuator<>(g);
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(5), () -> {
            FuValue value = v.valueOf("a0").orElseThrow();
            assertEquals(2 * 59049, value.fu()); // 2 * 3^10
            assertEquals(4, value.tier());
        });
    }

    @Test
    void clearDropsMemo() {
        FakeGraph g = new FakeGraph().base("x", 3, 1).recipe("y", 1, slot("x"));
        RecipeFuValuator<String> v = new RecipeFuValuator<>(g);
        assertEquals(3, v.valueOf("y").orElseThrow().fu());
        assertTrue(v.snapshot().containsKey("y"));
        v.clear();
        assertTrue(v.snapshot().isEmpty());
        assertEquals(3, v.valueOf("y").orElseThrow().fu()); // recomputes
    }
}
