package com.pgmacdesign.mc3dprint.machine.terminal;

import com.pgmacdesign.mc3dprint.fu.FuValue;
import com.pgmacdesign.mc3dprint.fu.FuValueRegistry;
import com.pgmacdesign.mc3dprint.fu.SpoolItem;
import com.pgmacdesign.mc3dprint.registry.ModItemTags;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * Whether a machine of a given tier may print an item, and when not, why.
 *
 * <p>These rules already existed, inline in the printer's Item Mode tick. They are here so the
 * terminal's catalog answers with the same rules rather than its own copy: a catalog that says
 * printable about something Item Mode refuses is worse than no catalog, because the player only
 * finds out after ordering. Extracting them also means a future gate is added once.
 *
 * <p><b>Order matters and is not arbitrary.</b> Spool, then wind-only, then restricted trophy, then
 * value and tier. An item can sit under more than one gate (a wither skeleton skull is both
 * {@code #no_print} and {@code #print_restricted}) and the first match is what the player is told,
 * so the order decides which explanation they get. Wind-only comes before restricted because it is
 * the more absolute of the two: restricted items can still print from an official blueprint,
 * wind-only items never print at all.
 *
 * <p>Pure and side-effect free, so it can be asked speculatively for a whole catalog page without
 * touching a machine.
 */
public final class PrintEligibility {

    public enum Verdict {
        /** Printable by a machine of the asked tier. */
        OK,
        /** A spool: printing one would duplicate the filament stored inside it. */
        SPOOL,
        /** On {@code #no_print}: winds for a payout, never prints. */
        WIND_ONLY,
        /** On {@code #print_restricted}: only ever placed by an official blueprint. */
        RESTRICTED,
        /** No FU value, so there is no honest price. Strict mode refuses these. */
        UNVALUED,
        /** Valued, but above this machine's tier. A bigger machine would print it. */
        NEEDS_HIGHER_TIER;

        public boolean printable() {
            return this == OK;
        }
    }

    /**
     * @param verdict       the outcome
     * @param reason        player-facing explanation, already naming the item
     * @param requiredTier  the tier that WOULD print it, or 0 when tier is not the problem
     */
    public record Result(Verdict verdict, String reason, int requiredTier) {
        public boolean printable() {
            return verdict.printable();
        }
    }

    private static final Result OK = new Result(Verdict.OK, "", 0);

    private PrintEligibility() {}

    /** Classifies {@code stack} for a machine of {@code machineTier}. */
    public static Result of(ItemStack stack, int machineTier) {
        if (stack.isEmpty()) {
            return new Result(Verdict.UNVALUED, "", 0);
        }
        String id = idOf(stack);
        if (stack.getItem() instanceof SpoolItem) {
            return new Result(Verdict.SPOOL,
                    id + " is a filament spool; printers never duplicate stored filament", 0);
        }
        if (stack.is(ModItemTags.NO_PRINT)) {
            return new Result(Verdict.WIND_ONLY,
                    id + " is recyclable but not printable (wind-only)", 0);
        }
        if (stack.is(ModItemTags.PRINT_RESTRICTED)) {
            return new Result(Verdict.RESTRICTED,
                    id + " is a restricted trophy item — printers never duplicate it", 0);
        }
        Optional<FuValue> value = FuValueRegistry.valueOf(stack);
        if (value.isEmpty()) {
            return new Result(Verdict.UNVALUED, String.format(
                    "%s has no FU value (unpriced/unknown item — register one via the API/config)",
                    id), 0);
        }
        int tier = value.get().tier();
        if (tier > machineTier) {
            return new Result(Verdict.NEEDS_HIGHER_TIER, String.format(
                    "%s is Tier %d, which exceeds this machine's Tier %d", id, tier, machineTier),
                    tier);
        }
        return OK;
    }

    /** Whether any machine at all could print this, ignoring tier. Drives catalog greying. */
    public static boolean everPrintable(ItemStack stack) {
        Verdict v = of(stack, Integer.MAX_VALUE).verdict();
        return v == Verdict.OK;
    }

    private static String idOf(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }
}
