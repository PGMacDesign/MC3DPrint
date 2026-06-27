package com.pgmacdesign.mc3dprint.fu;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Pure recipe-graph FU valuation, deliberately free of any Minecraft type so it
 * unit-tests without bootstrapping the game. Items are addressed by an opaque
 * key {@code K} (in game: {@code Item}); recipes are supplied through
 * {@link RecipeGraph}.
 *
 * <p>Valuation rule (per item):
 * <pre>
 *   value(item) = min over all recipes producing it of:
 *                   floor( sum(cheapest-match ingredient FU) / outputCount )
 *   tier(item)  = max ingredient tier of that winning recipe
 * </pre>
 * Floored, clamped to a minimum of 1 FU. An item with a base (explicit) value
 * short-circuits — recipes are never consulted for it. Cycles (an item that
 * appears on the active resolution stack) prune that recipe path; depth beyond
 * {@link #MAX_DEPTH} prunes too. Results — including "underivable" (null) — are
 * memoized so the graph is walked at most once per item.
 *
 * @param <K> item key type (e.g. {@code net.minecraft.world.item.Item})
 */
public final class RecipeFuValuator<K> {

    /** Hard recursion ceiling; protects against pathological recipe chains. */
    public static final int MAX_DEPTH = 12;

    /**
     * The recipe data this valuator walks. An adapter binds it to a concrete
     * recipe system (Minecraft's {@code RecipeManager}) or a hand-built fake.
     */
    public interface RecipeGraph<K> {
        /** Recipes whose result is {@code output}; empty if none. */
        List<RecipeView<K>> recipesFor(K output);

        /**
         * The explicit (base) FU value of {@code item}, if one is configured
         * outside the recipe graph. Present values short-circuit derivation.
         */
        Optional<FuValue> baseValue(K item);
    }

    /** One craft: its ingredient slots and how many items it yields. */
    public interface RecipeView<K> {
        /**
         * One list of candidate keys per ingredient slot (a tag-or'd ingredient
         * lists every member). The valuator picks the cheapest valued candidate
         * in each slot. An empty candidate list means "free" (e.g. fuel) and
         * contributes nothing. Empty slots are skipped.
         */
        List<List<K>> ingredientSlots();

        /** How many items the craft produces (>= 1). */
        int outputCount();
    }

    private final RecipeGraph<K> graph;
    /** Memo: present-with-value, or present-with-null = proven underivable. */
    private final Map<K, FuValue> cache = new HashMap<>();
    private final Map<K, Boolean> resolved = new HashMap<>();
    /** Items currently on the resolution stack (cycle guard). */
    private final Deque<K> stack = new ArrayDeque<>();
    /**
     * Set by every {@link #resolve}/{@link #evaluate}/{@link #cheapestMatch} return:
     * {@code false} if the result was pruned by the depth cap or the cycle guard
     * anywhere in its subtree, so a {@code null} is provisional (a shallower path
     * might still value it) rather than a proven dead-end. Only a COMPLETE miss is
     * safe to memoize — caching incomplete misses would poison sibling paths, while
     * NOT caching complete misses re-walks underivable subtrees combinatorially
     * (the creative-tab / tooltip freeze). Single-threaded: read immediately after
     * each call, before the next one overwrites it.
     */
    private boolean lastComplete;

    public RecipeFuValuator(RecipeGraph<K> graph) {
        this.graph = graph;
    }

    /**
     * The FU value of {@code item}: its base value if one exists, otherwise the
     * cheapest recipe-derived value, otherwise empty. Memoized.
     */
    public Optional<FuValue> valueOf(K item) {
        return Optional.ofNullable(resolve(item, 0));
    }

    private FuValue resolve(K item, int depth) {
        if (resolved.containsKey(item)) {
            lastComplete = true; // only complete results are ever cached
            return cache.get(item);
        }
        // Base (explicit) values win outright and never trigger a graph walk.
        Optional<FuValue> base = graph.baseValue(item);
        if (base.isPresent()) {
            lastComplete = true;
            return memo(item, base.get());
        }
        if (depth >= MAX_DEPTH || stack.contains(item)) {
            // Pruned: a shallower path may still value it, so this null is provisional.
            lastComplete = false;
            return null;
        }

        stack.push(item);
        FuValue best = null;
        boolean complete = true; // did every recipe resolve without a depth/cycle cut?
        try {
            for (RecipeView<K> recipe : graph.recipesFor(item)) {
                FuValue candidate = evaluate(recipe, depth);
                if (!lastComplete) {
                    complete = false;
                }
                if (candidate != null && (best == null || candidate.fu() < best.fu())) {
                    best = candidate;
                }
            }
        } finally {
            stack.pop();
        }
        lastComplete = complete;
        // Memoize a hit always; memoize a MISS only when the walk was COMPLETE (a
        // proven dead-end). Caching complete misses is what stops the combinatorial
        // re-walk of underivable subtrees. depth==0 also memoizes (full budget, matches
        // prior behavior). An incomplete deeper miss stays uncached so a shallower path
        // can still value it.
        if (best != null || complete || depth == 0) {
            return memo(item, best);
        }
        return null;
    }

    private FuValue evaluate(RecipeView<K> recipe, int depth) {
        long sum = 0;
        int maxTier = 1;
        boolean complete = true;
        for (List<K> slot : recipe.ingredientSlots()) {
            if (slot == null || slot.isEmpty()) {
                continue; // free / empty slot (e.g. fuel)
            }
            FuValue cheapest = cheapestMatch(slot, depth);
            if (!lastComplete) {
                complete = false;
            }
            if (cheapest == null) {
                lastComplete = complete; // recipe unusable; complete iff the slot walk was
                return null;
            }
            sum += cheapest.fu();
            maxTier = Math.max(maxTier, cheapest.tier());
        }
        lastComplete = complete;
        if (sum == 0) {
            return null; // nothing valued contributed -> can't derive
        }
        int count = Math.max(1, recipe.outputCount());
        int fu = (int) Math.max(1, sum / count); // FLOOR, min 1
        return new FuValue(fu, maxTier);
    }

    /** Cheapest valued candidate in a slot, or null if none can be valued. */
    private FuValue cheapestMatch(List<K> candidates, int depth) {
        FuValue cheapest = null;
        boolean complete = true;
        for (K candidate : candidates) {
            FuValue v = resolve(candidate, depth + 1);
            if (!lastComplete) {
                complete = false;
            }
            if (v != null && (cheapest == null || v.fu() < cheapest.fu())) {
                cheapest = v;
            }
        }
        lastComplete = complete;
        return cheapest;
    }

    private FuValue memo(K item, FuValue value) {
        cache.put(item, value);
        resolved.put(item, Boolean.TRUE);
        return value;
    }

    /** Drops all memoized results (datapack reload / config change). */
    public void clear() {
        cache.clear();
        resolved.clear();
        stack.clear();
    }

    /** Test/diagnostic view of the memo (immutable copy). */
    public Map<K, FuValue> snapshot() {
        return new HashMap<>(cache);
    }

    /** Convenience for building a graph candidate list. */
    @SafeVarargs
    public static <K> List<K> slot(K... keys) {
        return new ArrayList<>(List.of(keys));
    }
}
