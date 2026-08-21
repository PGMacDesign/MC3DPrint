package com.pgmacdesign.mc3dprint.gametest;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.fu.SpoolItem;
import com.pgmacdesign.mc3dprint.machine.PrinterBlockEntity;
import com.pgmacdesign.mc3dprint.machine.terminal.OrderSink;
import com.pgmacdesign.mc3dprint.machine.terminal.PrintRequest;
import com.pgmacdesign.mc3dprint.machine.terminal.PrintRequestQueue;
import com.pgmacdesign.mc3dprint.machine.terminal.TerminalDispatcher;
import com.pgmacdesign.mc3dprint.registry.ModBlocks;
import com.pgmacdesign.mc3dprint.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A terminal order driven all the way through a real machine.
 *
 * <p>{@link TerminalQueueGameTests} pins the book's laws in isolation; these check the part that
 * only shows up once a machine is involved: that a dispatched order is billed exactly like a player
 * standing at the printer, that filament is spent if and only if an item is delivered, and that an
 * order never takes a machine somebody is already using.
 */
@GameTestHolder(MC3DPrint.MOD_ID)
@PrefixGameTestTemplate(false)
public class TerminalDispatchGameTests {

    private static final BlockPos PRINTER = new BlockPos(2, 1, 2);

    /** A sink that takes everything and remembers what it took. */
    private static final class CountingSink implements OrderSink {
        private final List<ItemStack> taken = new ArrayList<>();
        private int cap = Integer.MAX_VALUE;

        @Override
        public int accept(ItemStack stack) {
            if (cap <= 0) {
                return 0;
            }
            int n = Math.min(cap, stack.getCount());
            cap -= n;
            taken.add(new ItemStack(stack.getItem(), n));
            return n;
        }

        @Override
        public int simulate(ItemStack stack) {
            return Math.min(Math.max(cap, 0), stack.getCount());
        }

        int total() {
            return taken.stream().mapToInt(ItemStack::getCount).sum();
        }
    }

    /** The spool tier an item's cost is denominated in. Spending is down-only, so a Tier 1 spool
     * cannot pay a Tier 3 cost and the machine would sit on PAUSED_NO_FILAMENT forever. */
    private static int spoolTierFor(net.minecraft.world.item.Item item) {
        return com.pgmacdesign.mc3dprint.fu.FuValueRegistry.valueOf(new ItemStack(item))
                .map(com.pgmacdesign.mc3dprint.fu.FuValue::tier)
                .orElseThrow(() -> new GameTestAssertException("expected " + item
                        + " to carry an FU value"));
    }

    private static void giveSpool(PrinterBlockEntity printer, net.minecraft.world.item.Item forItem,
                                  int fu) {
        ItemStack spool = new ItemStack(ModItems.SPOOLS.get(spoolTierFor(forItem) - 1).get());
        SpoolItem.setFu(spool, fu);
        printer.spoolInventory().setStackInSlot(0, spool);
    }

    private static PrinterBlockEntity poweredPrinter(GameTestHelper helper, int tierIndex, int fu) {
        helper.setBlock(PRINTER, ModBlocks.PRINTERS.get(tierIndex).get());
        if (!(helper.getBlockEntity(PRINTER) instanceof PrinterBlockEntity printer)) {
            throw new GameTestAssertException("Printer block entity missing");
        }
        java.util.Optional.ofNullable(printer.getEnergyStorage()).ifPresent(energy -> {
            for (int i = 0; i < 200; i++) {
                energy.receiveEnergy(10_000, false);
            }
        });
        if (fu > 0) {
            giveSpool(printer, Items.IRON_INGOT, fu);
        }
        return printer;
    }

    /**
     * The machine list in WORLD coordinates. GameTestHelper takes structure-relative positions but
     * the dispatcher reads the level directly, so handing it the relative position would look up an
     * empty spot far from the test and every order would sit unbound forever.
     */
    private static List<BlockPos> machines(GameTestHelper helper) {
        return List.of(helper.absolutePos(PRINTER));
    }

    /** The happy path, and the one that proves a dispatched order is billed like any other print. */
    @GameTest(template = "empty5", timeoutTicks = 400)
    public static void adispatchedOrderPrintsAndIsBilledLikeItemMode(GameTestHelper helper) {
        PrinterBlockEntity printer = poweredPrinter(helper, 2, 100_000);
        PrintRequestQueue queue = new PrintRequestQueue();
        CountingSink sink = new CountingSink();
        PrintRequest req = queue.enqueue(UUID.randomUUID(), Items.IRON_INGOT, 3).orElseThrow();

        int unitCost = printer.itemFuCost(new ItemStack(Items.IRON_INGOT));
        if (unitCost <= 0) {
            throw new GameTestAssertException("iron ingot must be priced for this test to mean"
                    + " anything, got " + unitCost);
        }
        final int expectedSpend = unitCost * 3;
        int fuBefore = SpoolItem.getFu(printer.spoolInventory().getStackInSlot(0));

        helper.succeedWhen(() -> {
            TerminalDispatcher.tick(helper.getLevel(), queue, machines(helper), sink);
            if (req.status() != PrintRequest.Status.COMPLETE) {
                throw new GameTestAssertException("order still " + req.status()
                        + " at " + req.delivered() + "/3");
            }
            if (sink.total() != 3) {
                throw new GameTestAssertException("sink received " + sink.total() + ", expected 3");
            }
            int spent = fuBefore - SpoolItem.getFu(printer.spoolInventory().getStackInSlot(0));
            if (spent != expectedSpend) {
                throw new GameTestAssertException("spent " + spent + " FU for 3 ingots but Item"
                        + " Mode prices them at " + expectedSpend);
            }
        });
    }

    /**
     * Filament is spent if and only if an item is delivered. A sink that refuses everything must
     * leave the spool untouched, or the terminal is a filament shredder.
     */
    @GameTest(template = "empty5", timeoutTicks = 300)
    public static void arefusedDeliveryDoesNotBurnFilament(GameTestHelper helper) {
        PrinterBlockEntity printer = poweredPrinter(helper, 2, 100_000);
        PrintRequestQueue queue = new PrintRequestQueue();
        PrintRequest req = queue.enqueue(UUID.randomUUID(), Items.IRON_INGOT, 1).orElseThrow();
        int fuBefore = SpoolItem.getFu(printer.spoolInventory().getStackInSlot(0));

        // Fill the output slot so the fallback cannot bank it either, then refuse at the sink.
        printer.inventory().setStackInSlot(PrinterBlockEntity.SLOT_OUTPUT,
                new ItemStack(Items.DIAMOND, 64));

        helper.startSequence()
                .thenExecuteFor(200, () ->
                        TerminalDispatcher.tick(helper.getLevel(), queue, machines(helper), OrderSink.FULL))
                .thenExecute(() -> {
                    if (req.delivered() != 0) {
                        throw new GameTestAssertException("a refused delivery was credited: "
                                + req.delivered());
                    }
                    int spent = fuBefore - SpoolItem.getFu(printer.spoolInventory().getStackInSlot(0));
                    // Nothing was delivered and the output slot is full, so nothing may have been
                    // charged either. Spending here would make the terminal a filament shredder
                    // for anyone whose ME system filled up while they were away.
                    if (spent != 0) {
                        throw new GameTestAssertException("spent " + spent
                                + " FU with nowhere to put the item; it must not charge at all");
                    }
                })
                .thenSucceed();
    }

    /** An order never takes a machine the player is already using. */
    @GameTest(template = "empty5", timeoutTicks = 120)
    public static void anOrderWillNotPreemptAManualPrint(GameTestHelper helper) {
        PrinterBlockEntity printer = poweredPrinter(helper, 2, 100_000);
        // The player has loaded their own Item Mode template.
        printer.inventory().setStackInSlot(PrinterBlockEntity.SLOT_TEMPLATE,
                new ItemStack(Items.IRON_INGOT));

        PrintRequestQueue queue = new PrintRequestQueue();
        CountingSink sink = new CountingSink();
        PrintRequest req = queue.enqueue(UUID.randomUUID(), Items.IRON_INGOT, 1).orElseThrow();

        helper.startSequence()
                .thenExecuteFor(60, () ->
                        TerminalDispatcher.tick(helper.getLevel(), queue, machines(helper), sink))
                .thenExecute(() -> {
                    if (req.machine() != null || printer.hasTerminalOrder()) {
                        throw new GameTestAssertException("the order took a machine the player was"
                                + " already using");
                    }
                    if (req.status() != PrintRequest.Status.QUEUED) {
                        throw new GameTestAssertException("the order should still be QUEUED, was "
                                + req.status());
                    }
                })
                .thenSucceed();
    }

    /**
     * An order will not bind to a machine that cannot print it. Binding anyway would park the order
     * on a tier-1 printer reporting NEEDS_HIGHER_TIER forever while better machines sit idle.
     */
    @GameTest(template = "empty5", timeoutTicks = 120)
    public static void anOrderWillNotBindToATooSmallMachine(GameTestHelper helper) {
        poweredPrinter(helper, 0, 100_000); // Tier 1
        PrintRequestQueue queue = new PrintRequestQueue();
        CountingSink sink = new CountingSink();
        PrintRequest req = queue.enqueue(UUID.randomUUID(), Items.DIAMOND, 1).orElseThrow();

        helper.startSequence()
                .thenExecuteFor(40, () ->
                        TerminalDispatcher.tick(helper.getLevel(), queue, machines(helper), sink))
                .thenExecute(() -> {
                    if (req.machine() != null) {
                        throw new GameTestAssertException("a diamond order bound to a Tier 1"
                                + " machine that cannot print it");
                    }
                })
                .thenSucceed();
    }

    /** Breaking the machine mid-order cancels the order and does not strand the lease. */
    @GameTest(template = "empty5", timeoutTicks = 300)
    public static void breakingTheMachineCancelsItsOrder(GameTestHelper helper) {
        poweredPrinter(helper, 2, 100_000);
        PrintRequestQueue queue = new PrintRequestQueue();
        CountingSink sink = new CountingSink();
        PrintRequest req = queue.enqueue(UUID.randomUUID(), Items.IRON_INGOT, 64).orElseThrow();

        helper.startSequence()
                .thenExecuteFor(10, () ->
                        TerminalDispatcher.tick(helper.getLevel(), queue, machines(helper), sink))
                .thenExecute(() -> {
                    if (req.machine() == null) {
                        throw new GameTestAssertException("the order never bound");
                    }
                    helper.setBlock(PRINTER, net.minecraft.world.level.block.Blocks.AIR);
                })
                .thenExecuteFor(5, () ->
                        TerminalDispatcher.tick(helper.getLevel(), queue, machines(helper), sink))
                .thenExecute(() -> {
                    if (req.status() != PrintRequest.Status.CANCELLED) {
                        throw new GameTestAssertException("order should be CANCELLED after its"
                                + " machine was broken, was " + req.status());
                    }
                    if (queue.isLeased(helper.absolutePos(PRINTER))) {
                        throw new GameTestAssertException("the lease outlived the machine");
                    }
                })
                .thenSucceed();
    }

    /** Running out of filament holds the order rather than cancelling or delivering unpaid. */
    @GameTest(template = "empty5", timeoutTicks = 400)
    public static void runningOutOfFilamentHoldsTheOrder(GameTestHelper helper) {
        PrinterBlockEntity printer = poweredPrinter(helper, 2, 0);
        PrintRequestQueue queue = new PrintRequestQueue();
        CountingSink sink = new CountingSink();
        PrintRequest req = queue.enqueue(UUID.randomUUID(), Items.IRON_INGOT, 2).orElseThrow();

        helper.startSequence()
                .thenExecuteFor(80, () ->
                        TerminalDispatcher.tick(helper.getLevel(), queue, machines(helper), sink))
                .thenExecute(() -> {
                    if (sink.total() != 0) {
                        throw new GameTestAssertException("delivered " + sink.total()
                                + " items with no filament to pay for them");
                    }
                    if (req.status() == PrintRequest.Status.CANCELLED) {
                        throw new GameTestAssertException("no filament must HOLD the order, not"
                                + " cancel it");
                    }
                    // Now pay for it, and it should finish.
                    giveSpool(printer, Items.IRON_INGOT, 100_000);
                })
                .thenExecuteFor(300, () ->
                        TerminalDispatcher.tick(helper.getLevel(), queue, machines(helper), sink))
                .thenExecute(() -> {
                    if (req.status() != PrintRequest.Status.COMPLETE) {
                        throw new GameTestAssertException("order did not resume once filament"
                                + " returned; it is " + req.status() + " at " + req.delivered());
                    }
                })
                .thenSucceed();
    }
}
