package com.pgmacdesign.mc3dprint.fu;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Bottom-up relaxation valuator: an order-independent alternative to the top-down
 * {@link RecipeFuValuator} DFS, deliberately free of Minecraft types so it unit-tests
 * without bootstrapping the game.
 *
 * <p>Where the DFS resolves one item at a time and re-walks underivable subtrees (the
 * cost that stalls a heavily-modded startup for minutes — see the {@code dumpfu}
 * diagnostic), this computes every item at once via synchronous Bellman-Ford rounds:
 * round {@code k} values every item whose cheapest recipe tree has height {@code <= k}.
 * Because costs are positive (FU {@code >= 1}) and floor-divided, values only ever
 * decrease and the fixed point converges in at most (item-count) rounds.
 *
 * <p>Two modes, selected by {@code maxRounds}:
 * <ul>
 *   <li><b>bounded</b> to {@link RecipeFuValuator#MAX_DEPTH} — reproduces the DFS's
 *       depth-capped minimum (same {@code <=12}-hop bound), but order-independently,
 *       so it also resolves the DFS's incomplete-miss memoization artifacts to their
 *       true bounded value.</li>
 *   <li><b>unbounded</b> — the true global minimum, valuing deep chains the depth cap
 *       misses. This is a real economy change, which is why the {@code comparefu}
 *       command diffs it against the current values rather than shipping it blind.</li>
 * </ul>
 *
 * <p>Valuation rule per recipe is identical to {@link RecipeFuValuator}: floor of
 * (sum of the cheapest valued candidate per slot) over the output count, clamped to a
 * minimum of 1 FU; tier is the max ingredient tier; a slot with no valued candidate
 * makes the whole recipe unusable. An item with a base (explicit) value short-circuits.
 *
 * @param <K> item key type (e.g. {@code net.minecraft.world.item.Item})
 */
public final class RelaxationFuValuator<K> {

    private final Function<K, Optional<FuValue>> baseValue;
    /** Converged derived values (non-base outputs only). */
    private final Map<K, FuValue> derived = new HashMap<>();

    /**
     * @param recipesByOutput output key -> recipes producing it (canonicalized by the
     *                        caller, exactly as {@link MinecraftRecipeIndex} feeds the DFS)
     * @param baseValue       explicit/base value of a key, if any (short-circuits derivation)
     * @param maxRounds       bounded height cap; pass {@link RecipeFuValuator#MAX_DEPTH} to
     *                        mirror the DFS, or a large value for the true fixed point
     */
    public RelaxationFuValuator(Map<K, List<RecipeFuValuator.RecipeView<K>>> recipesByOutput,
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

    private void compute(Map<K, List<RecipeFuValuator.RecipeView<K>>> recipesByOutput, int maxRounds) {
        // Convergence is guaranteed within (output-count) rounds; cap the unbounded case
        // there so a hypothetical non-decreasing bug can't spin forever.
        int cap = Math.min(maxRounds, recipesByOutput.size() + 2);
        Map<K, FuValue> prev = new HashMap<>();
        for (int round = 0; round < cap; round++) {
            Map<K, FuValue> next = new HashMap<>(prev);
            boolean changed = false;
            for (Map.Entry<K, List<RecipeFuValuator.RecipeView<K>>> e : recipesByOutput.entrySet()) {
                K output = e.getKey();
                if (baseValue.apply(output).isPresent()) {
                    continue; // base wins outright; recipes never consulted for it
                }
                FuValue prior = prev.get(output);
                FuValue best = prior;
                for (RecipeFuValuator.RecipeView<K> recipe : e.getValue()) {
                    FuValue candidate = evaluate(recipe, prev);
                    // strict <, first-min-fu wins on ties — matches RecipeFuValuator.resolve
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

    /** Mirror of {@link RecipeFuValuator}'s per-recipe evaluation, reading last round's values. */
    private FuValue evaluate(RecipeFuValuator.RecipeView<K> recipe, Map<K, FuValue> prev) {
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
}
