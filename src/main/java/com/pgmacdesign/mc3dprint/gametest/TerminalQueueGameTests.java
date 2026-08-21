package com.pgmacdesign.mc3dprint.gametest;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.machine.terminal.PrintEligibility;
import com.pgmacdesign.mc3dprint.machine.terminal.PrintRequest;
import com.pgmacdesign.mc3dprint.machine.terminal.PrintRequestQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Optional;
import java.util.UUID;

/**
 * The order book's laws, which have to hold whatever order events arrive in.
 *
 * <p>These are the failures that do not show up in a happy-path test: a completion landing after
 * its order was cancelled, two orders reaching for one machine, a save taken mid-order and reloaded
 * into a world where the machine has been mined. Each test drives the violating interleaving
 * directly rather than hoping to observe it.
 */
@GameTestHolder(MC3DPrint.MOD_ID)
@PrefixGameTestTemplate(false)
public class TerminalQueueGameTests {

    private static final BlockPos MACHINE_A = new BlockPos(1, 1, 1);
    private static final BlockPos MACHINE_B = new BlockPos(3, 1, 3);

    private static PrintRequest enqueue(PrintRequestQueue queue, int qty) {
        return queue.enqueue(UUID.randomUUID(), Items.IRON_INGOT, qty)
                .orElseThrow(() -> new GameTestAssertException("queue refused a valid order"));
    }

    /**
     * An order delivers exactly what was asked, once, even when a duplicate dispatch tries to
     * credit it again after it completed.
     */
    @GameTest(template = "empty5", timeoutTicks = 40)
    public static void anOrderDeliversItsQuantityExactlyOnce(GameTestHelper helper) {
        PrintRequestQueue queue = new PrintRequestQueue();
        PrintRequest req = enqueue(queue, 4);
        if (!queue.bind(req, MACHINE_A)) {
            throw new GameTestAssertException("a fresh order should bind to a free machine");
        }

        int first = queue.credit(req.id(), MACHINE_A, 4);
        if (first != 4 || req.delivered() != 4) {
            throw new GameTestAssertException("expected 4 delivered, got " + first
                    + " credited and " + req.delivered() + " recorded");
        }
        if (req.status() != PrintRequest.Status.COMPLETE) {
            throw new GameTestAssertException("an order that delivered its quantity must be"
                    + " COMPLETE, was " + req.status());
        }
        // The duplicate: a second dispatch for the same id lands after completion.
        int second = queue.credit(req.id(), MACHINE_A, 4);
        if (second != 0 || req.delivered() != 4) {
            throw new GameTestAssertException("a duplicate dispatch credited " + second
                    + " more; the order must deliver 4 total, not 8");
        }
        helper.succeed();
    }

    /** Over-delivery is clamped: a machine reporting more than remains cannot overfill the order. */
    @GameTest(template = "empty5", timeoutTicks = 40)
    public static void deliveryCannotExceedTheOrder(GameTestHelper helper) {
        PrintRequestQueue queue = new PrintRequestQueue();
        PrintRequest req = enqueue(queue, 3);
        queue.bind(req, MACHINE_A);

        int credited = queue.credit(req.id(), MACHINE_A, 10);
        if (credited != 3 || req.delivered() != 3) {
            throw new GameTestAssertException("a 3-item order credited " + credited
                    + " and recorded " + req.delivered());
        }
        helper.succeed();
    }

    /**
     * Cancelling frees the machine and permanently closes the order, so an item that was already
     * in flight cannot be delivered against it when it lands.
     */
    @GameTest(template = "empty5", timeoutTicks = 40)
    public static void aCancelledOrderCannotBeDeliveredLate(GameTestHelper helper) {
        PrintRequestQueue queue = new PrintRequestQueue();
        PrintRequest req = enqueue(queue, 8);
        queue.bind(req, MACHINE_A);
        queue.credit(req.id(), MACHINE_A, 2); // partially done

        queue.cancel(req, "player cancelled");
        if (queue.isLeased(MACHINE_A)) {
            throw new GameTestAssertException("cancelling must release the machine lease");
        }
        int late = queue.credit(req.id(), MACHINE_A, 1);
        if (late != 0 || req.delivered() != 2) {
            throw new GameTestAssertException("a late completion credited " + late
                    + " to a cancelled order; delivered is now " + req.delivered());
        }
        if (req.status() != PrintRequest.Status.CANCELLED) {
            throw new GameTestAssertException("a cancelled order must stay cancelled, was "
                    + req.status());
        }
        helper.succeed();
    }

    /**
     * A machine runs one order at a time. The second order queues rather than interleaving, which
     * is what keeps two orders from both spending against the same machine's filament.
     */
    @GameTest(template = "empty5", timeoutTicks = 40)
    public static void oneMachineRunsOneOrder(GameTestHelper helper) {
        PrintRequestQueue queue = new PrintRequestQueue();
        PrintRequest first = enqueue(queue, 2);
        PrintRequest second = enqueue(queue, 2);

        if (!queue.bind(first, MACHINE_A)) {
            throw new GameTestAssertException("the first order should bind");
        }
        if (queue.bind(second, MACHINE_A)) {
            throw new GameTestAssertException("a second order bound to a machine already running"
                    + " one; it must queue instead");
        }
        if (second.status() != PrintRequest.Status.QUEUED || second.machine() != null) {
            throw new GameTestAssertException("the refused order must stay QUEUED and unbound");
        }
        // It may take a different machine, which is what makes this a lease and not a global lock.
        if (!queue.bind(second, MACHINE_B)) {
            throw new GameTestAssertException("the second order should bind to a free machine");
        }
        helper.succeed();
    }

    /**
     * The lease releases when the order finishes, so the machine is immediately available. A lease
     * that leaked here would strand the machine until a restart.
     */
    @GameTest(template = "empty5", timeoutTicks = 40)
    public static void finishingAnOrderFreesItsMachine(GameTestHelper helper) {
        PrintRequestQueue queue = new PrintRequestQueue();
        PrintRequest first = enqueue(queue, 1);
        queue.bind(first, MACHINE_A);
        queue.credit(first.id(), MACHINE_A, 1);

        if (queue.isLeased(MACHINE_A)) {
            throw new GameTestAssertException("a completed order left its machine leased");
        }
        PrintRequest next = enqueue(queue, 1);
        if (!queue.bind(next, MACHINE_A)) {
            throw new GameTestAssertException("the freed machine must accept the next order");
        }
        helper.succeed();
    }

    /** A half-finished order survives a save and reload with its progress intact. */
    @GameTest(template = "empty5", timeoutTicks = 40)
    public static void aPartlyFilledOrderSurvivesReload(GameTestHelper helper) {
        PrintRequestQueue queue = new PrintRequestQueue();
        PrintRequest req = enqueue(queue, 10);
        queue.bind(req, MACHINE_A);
        queue.credit(req.id(), MACHINE_A, 6);
        UUID id = req.id();

        CompoundTag saved = queue.save();
        PrintRequestQueue reloaded = new PrintRequestQueue();
        reloaded.load(saved);

        PrintRequest back = reloaded.byId(id)
                .orElseThrow(() -> new GameTestAssertException("the order did not survive reload"));
        if (back.delivered() != 6 || back.remaining() != 4) {
            throw new GameTestAssertException("reloaded order says " + back.delivered()
                    + " delivered with " + back.remaining() + " left; expected 6 and 4");
        }
        if (!MACHINE_A.equals(back.machine()) || !reloaded.isLeased(MACHINE_A)) {
            throw new GameTestAssertException("the machine lease was not rebuilt from the order");
        }
        helper.succeed();
    }

    /**
     * After a reload the order is re-checked against the world. A machine that is gone cancels the
     * order rather than leaving it pointed at whatever now occupies that position.
     */
    @GameTest(template = "empty5", timeoutTicks = 40)
    public static void reloadCancelsAnOrderWhoseMachineIsGone(GameTestHelper helper) {
        PrintRequestQueue queue = new PrintRequestQueue();
        PrintRequest req = enqueue(queue, 5);
        queue.bind(req, MACHINE_A);
        queue.credit(req.id(), MACHINE_A, 1);
        UUID id = req.id();

        PrintRequestQueue reloaded = new PrintRequestQueue();
        reloaded.load(queue.save());
        reloaded.revalidate(pos -> false); // the machine was mined while the world was closed

        PrintRequest back = reloaded.byId(id)
                .orElseThrow(() -> new GameTestAssertException("the order vanished entirely"));
        if (back.status() != PrintRequest.Status.CANCELLED) {
            throw new GameTestAssertException("an order whose machine is gone must be cancelled,"
                    + " was " + back.status());
        }
        if (reloaded.isLeased(MACHINE_A)) {
            throw new GameTestAssertException("revalidation left a lease on a dead machine");
        }
        helper.succeed();
    }

    /** A machine that is still there keeps its order, so a reload is not a mass cancellation. */
    @GameTest(template = "empty5", timeoutTicks = 40)
    public static void reloadKeepsAnOrderWhoseMachineRemains(GameTestHelper helper) {
        PrintRequestQueue queue = new PrintRequestQueue();
        PrintRequest req = enqueue(queue, 5);
        queue.bind(req, MACHINE_A);
        UUID id = req.id();

        PrintRequestQueue reloaded = new PrintRequestQueue();
        reloaded.load(queue.save());
        reloaded.revalidate(pos -> true);

        PrintRequest back = reloaded.byId(id).orElseThrow(
                () -> new GameTestAssertException("the order vanished"));
        if (back.status().isTerminal()) {
            throw new GameTestAssertException("a live machine's order must survive, was "
                    + back.status());
        }
        helper.succeed();
    }

    /**
     * Two orders that both claim one machine across a reload cannot both hold it. The older keeps
     * the lease; the younger goes back to QUEUED rather than silently sharing.
     */
    @GameTest(template = "empty5", timeoutTicks = 40)
    public static void reloadCannotGiveOneMachineToTwoOrders(GameTestHelper helper) {
        PrintRequestQueue queue = new PrintRequestQueue();
        PrintRequest first = enqueue(queue, 2);
        PrintRequest second = enqueue(queue, 2);
        queue.bind(first, MACHINE_A);
        // Force the corrupt shape a partial save could leave behind: two orders naming one machine.
        queue.bind(second, MACHINE_B);
        CompoundTag saved = queue.save();
        forceMachineOnEveryRequest(saved);

        PrintRequestQueue reloaded = new PrintRequestQueue();
        reloaded.load(saved);

        long bound = reloaded.open().stream()
                .filter(r -> MACHINE_A.equals(r.machine()))
                .count();
        if (bound != 1) {
            throw new GameTestAssertException("after reload " + bound
                    + " orders claim the same machine; exactly one may");
        }
        // The loser must be UNBOUND, not lost. Counting alone would also pass if reload simply
        // dropped the second order, which would silently delete something a player paid attention
        // to; it has to come back as ordinary queued work that can bind elsewhere.
        PrintRequest loser = reloaded.byId(second.id()).orElseThrow(
                () -> new GameTestAssertException("the second order was dropped by reload rather"
                        + " than unbound"));
        if (loser.status() != PrintRequest.Status.QUEUED || loser.machine() != null) {
            throw new GameTestAssertException("the loser must return to QUEUED with no machine,"
                    + " was " + loser.status() + " on " + loser.machine());
        }
        helper.succeed();
    }

    /** The book refuses more than it can hold, rather than growing the save file without bound. */
    @GameTest(template = "empty5", timeoutTicks = 60)
    public static void theBookHasACeiling(GameTestHelper helper) {
        PrintRequestQueue queue = new PrintRequestQueue();
        for (int i = 0; i < PrintRequestQueue.MAX_OPEN_REQUESTS; i++) {
            enqueue(queue, 1);
        }
        Optional<PrintRequest> overflow = queue.enqueue(UUID.randomUUID(), Items.IRON_INGOT, 1);
        if (overflow.isPresent()) {
            throw new GameTestAssertException("the queue accepted more than its ceiling of "
                    + PrintRequestQueue.MAX_OPEN_REQUESTS);
        }
        helper.succeed();
    }

    /**
     * The catalog and Item Mode answer with one rule set. This asserts the classifier itself, since
     * a drift between the two is exactly what it exists to prevent.
     */
    @GameTest(template = "empty5", timeoutTicks = 40)
    public static void eligibilityMatchesTheItemModeRules(GameTestHelper helper) {
        // A spool carries stored filament, so copying one would launder it.
        expect(PrintEligibility.of(new ItemStack(
                        com.pgmacdesign.mc3dprint.registry.ModItems.SPOOLS.get(0).get()), 8),
                PrintEligibility.Verdict.SPOOL);
        // Wind-only beats restricted for items on both lists, so the player is told the
        // absolute reason rather than the conditional one.
        expect(PrintEligibility.of(new ItemStack(Items.WITHER_SKELETON_SKULL), 8),
                PrintEligibility.Verdict.WIND_ONLY);
        // Valued and within tier.
        expect(PrintEligibility.of(new ItemStack(Items.IRON_INGOT), 8),
                PrintEligibility.Verdict.OK);
        // Valued but above a tier-1 machine.
        PrintEligibility.Result lowTier = PrintEligibility.of(new ItemStack(Items.DIAMOND), 1);
        if (lowTier.verdict() != PrintEligibility.Verdict.NEEDS_HIGHER_TIER
                || lowTier.requiredTier() <= 1) {
            throw new GameTestAssertException("a diamond on a T1 machine should report the tier"
                    + " it needs, got " + lowTier.verdict() + "/" + lowTier.requiredTier());
        }
        helper.succeed();
    }

    private static void expect(PrintEligibility.Result actual, PrintEligibility.Verdict want) {
        if (actual.verdict() != want) {
            throw new GameTestAssertException("expected " + want + " but got " + actual.verdict()
                    + " (" + actual.reason() + ")");
        }
    }

    /** Rewrites every saved request to name MACHINE_A, simulating a corrupt/partial save. */
    private static void forceMachineOnEveryRequest(CompoundTag saved) {
        net.minecraft.nbt.ListTag list = com.pgmacdesign.mc3dprint.compat.NbtCompat
                .getList(saved, "Requests", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            com.pgmacdesign.mc3dprint.compat.NbtCompat.putBlockPos(
                    com.pgmacdesign.mc3dprint.compat.NbtCompat.listGetCompound(list, i),
                    "Machine", MACHINE_A);
        }
    }
}
