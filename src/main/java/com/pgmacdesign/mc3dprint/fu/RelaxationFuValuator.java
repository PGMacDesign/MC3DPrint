package com.pgmacdesign.mc3dprint.fu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Recipe-graph FU valuation via bottom-up relaxation, deliberately free of any Minecraft
 * type so it unit-tests without bootstrapping the game. Items are addressed by an opaque
 * key {@code K} (in game: {@code Item}); recipes are supplied as an output -> recipes map
 * (built by {@link MinecraftRecipeIndex}).
 *
 * <p>This replaced an earlier top-down memoized DFS. The DFS resolved one item at a time
 * and, to stay correct, declined to memoize depth-/cycle-pruned misses — so it re-walked
 * underivable subtrees once per top-level item and stalled a heavily-modded startup for
 * ~90s (15k items). This computes every item at once with synchronous Bellman-Ford rounds:
 * round {@code k} values every item whose cheapest recipe tree has height {@code <= k}.
 * Costs are positive (FU {@code >= 1}) and floor-divided, so values only ever decrease and
 * the fixed point converges in at most (item-count) rounds — ~0.5s on the same pack, and
 * order-independent (the DFS's one order-dependent value is resolved to its true minimum).
 *
 * <p>Bounded to {@link #MAX_DEPTH} it reproduces the historical depth cap (a chain deeper
 * than the cap stays unvalued); unbounded it finds the true global minimum. Production runs
 * bounded, keeping the cap as a deliberate safety bound against a pathological deep chain.
 *
 * <p>Valuation rule per recipe: floor of (sum of the cheapest valued candidate per slot)
 * over the output count, clamped to a minimum of 1 FU; tier is the max ingredient tier; a
 * slot with no valued candidate makes the whole recipe unusable. An item with a base
 * (explicit) value short-circuits — recipes are never consulted for it.
 *
 * @param <K> item key type (e.g. {@code net.minecraft.world.item.Item})
 */
public final class RelaxationFuValuator<K> {

    /** Hard height cap mirroring the historical DFS depth cap; bounds the "keep the cap" mode. */
    public static final int MAX_DEPTH = 12;

    /** One craft: its ingredient slots and how many items it yields. */
    public interface RecipeView<K> {
        /**
         * One list of candidate keys per ingredient slot (a tag-or'd ingredient lists every
         * member). The valuator picks the cheapest valued candidate in each slot. An empty
         * candidate list means "free" (e.g. fuel) and contributes nothing.
         */
        List<List<K>> ingredientSlots();

        /** How many items the craft produces (>= 1). */
        int outputCount();
    }

    private final Function<K, Optional<FuValue>> baseValue;
    /** Converged derived values (non-base outputs only). */
    private final Map<K, FuValue> derived = new HashMap<>();

    /**
     * @param recipesByOutput output key -> recipes producing it (canonicalized by the
     *                        caller, exactly as {@link MinecraftRecipeIndex} builds it)
     * @param baseValue       explicit/base value of a key, if any (short-circuits derivation)
     * @param maxRounds       bounded height cap; pass {@link #MAX_DEPTH} to keep the historical
     *                        depth cap, or a large value for the true fixed point
     */
    public RelaxationFuValuator(Map<K, List<RecipeView<K>>> recipesByOutput,
                                Function<K, Optional<FuValue>> baseValue,
                                int maxRounds) {
        this.baseValue = baseValue;
        compute(recipesByOutput, maxRounds);
    }

    /** The FU value of {@code item}: base value if one exists, else the relaxed value, else empty. */
    public Optional<FuValue> valueOf(K item) {
        Optional<FuValue> base = baseValue.apply(item);
        if (base.isPresent()) {
            return base;
        }
        return Optional.ofNullable(derived.get(item));
    }

    private void compute(Map<K, List<RecipeView<K>>> recipesByOutput, int maxRounds) {
        // Convergence is guaranteed within (output-count) rounds; cap the unbounded case
        // there so a hypothetical non-decreasing bug can't spin forever.
        int cap = Math.min(maxRounds, recipesByOutput.size() + 2);
        Map<K, FuValue> prev = new HashMap<>();
        for (int round = 0; round < cap; round++) {
            Map<K, FuValue> next = new HashMap<>(prev);
            boolean changed = false;
            for (Map.Entry<K, List<RecipeView<K>>> e : recipesByOutput.entrySet()) {
                K output = e.getKey();
                if (baseValue.apply(output).isPresent()) {
                    continue; // base wins outright; recipes never consulted for it
                }
                FuValue prior = prev.get(output);
                FuValue best = prior;
                for (RecipeView<K> recipe : e.getValue()) {
                    FuValue candidate = evaluate(recipe, prev);
                    // strict <, first-min-fu wins on ties — a stable, deterministic choice
                    if (candidate != null && (best == null || candidate.fu() < best.fu())) {
                        best = candidate;
                    }
                }
                if (best != null && !best.equals(prior)) {
                    next.put(output, best);
                    changed = true;
                }
            }
            prev = next;
            if (!changed) {
                break; // fixed point reached
            }
        }
        derived.putAll(prev);
    }

    /** Floor of (cheapest per slot) / outputCount, min 1 FU; null if any slot is unsatisfiable. */
    private FuValue evaluate(RecipeView<K> recipe, Map<K, FuValue> prev) {
        long sum = 0;
        int maxTier = 1;
        for (List<K> slot : recipe.ingredientSlots()) {
            if (slot == null || slot.isEmpty()) {
                continue; // free / empty slot (e.g. fuel)
            }
            FuValue cheapest = null;
            for (K candidate : slot) {
                FuValue v = valueFrom(candidate, prev);
                if (v != null && (cheapest == null || v.fu() < cheapest.fu())) {
                    cheapest = v;
                }
            }
            if (cheapest == null) {
                return null; // slot unsatisfiable -> recipe unusable
            }
            sum += cheapest.fu();
            maxTier = Math.max(maxTier, cheapest.tier());
        }
        if (sum == 0) {
            return null; // nothing valued contributed -> can't derive
        }
        int count = Math.max(1, recipe.outputCount());
        int fu = (int) Math.max(1, sum / count); // FLOOR, min 1
        return new FuValue(fu, maxTier);
    }

    /** A candidate's value this round: its base value, else last round's relaxed value. */
    private FuValue valueFrom(K item, Map<K, FuValue> prev) {
        Optional<FuValue> base = baseValue.apply(item);
        if (base.isPresent()) {
            return base.get();
        }
        return prev.get(item);
    }

    /** Convenience for building a slot's candidate list (used by tests). */
    @SafeVarargs
    public static <K> List<K> slot(K... keys) {
        return new ArrayList<>(List.of(keys));
    }
}
