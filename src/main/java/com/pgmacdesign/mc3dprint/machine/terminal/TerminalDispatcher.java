package com.pgmacdesign.mc3dprint.machine.terminal;

import com.pgmacdesign.mc3dprint.machine.PrinterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * Matches queued orders to machines and keeps the two in step, once per terminal tick.
 *
 * <p>AE2-free on purpose: it is handed a list of machine positions and a sink, and never asks where
 * either came from. That is what lets the whole dispatch path be gametested with AE2 absent, and it
 * means an AE2 API break cannot reach the code that decides who gets billed.
 *
 * <p><b>Dispatch is one order per machine, oldest order first.</b> Not because parallelism is hard,
 * but because the alternative silently changes the economy: two orders on one machine would both
 * spend from the same spools, and whichever ticked second would see filament that the first had
 * already committed. The lease in {@link PrintRequestQueue} is what enforces it; this class just
 * respects it.
 */
public final class TerminalDispatcher {

    private TerminalDispatcher() {}

    /**
     * Advances every open order by at most one binding per call.
     *
     * @param level     the server level the machines live in
     * @param queue     the order book, and the only thing that mutates a request
     * @param machines  candidate machine positions, best-first (the caller decides what best means)
     * @param sink      where finished items go
     */
    public static void tick(ServerLevel level, PrintRequestQueue queue,
                            List<BlockPos> machines, OrderSink sink) {
        // Orders bound to a machine that is no longer a machine are cancelled before anything is
        // dispatched, so a freed lease is available to the next order in the same tick rather than
        // the next one.
        //
        // An UNLOADED chunk is not evidence of anything. Treating it as "gone" would cancel every
        // order on a machine the player simply walked away from, so it counts as still valid and
        // the order holds until the chunk is back.
        queue.revalidate(pos -> !level.isLoaded(pos) || printerAt(level, pos).isPresent());

        for (PrintRequest request : queue.open()) {
            BlockPos bound = request.machine();
            if (bound != null) {
                syncBoundOrder(level, queue, request, bound, sink);
                continue;
            }
            bindToFreeMachine(level, queue, request, machines, sink);
        }

        // Take finished and cancelled orders off their machines. A completed order releases its
        // lease and leaves the open list, so nothing above would ever visit it again, and the
        // machine would sit holding a dead order: blocked from new work, and (without the
        // machine's own liveness check) printing and paying forever. The machine defends itself
        // too, but only this sweep frees it promptly for the next order.
        for (BlockPos pos : machines) {
            printerAt(level, pos).ifPresent(printer -> {
                PrinterBlockEntity.TerminalOrder held = printer.terminalOrder();
                if (held == null) {
                    return;
                }
                boolean stillOpen = queue.byId(held.id())
                        .map(r -> !r.status().isTerminal() && pos.equals(r.machine()))
                        .orElse(false);
                if (!stillOpen) {
                    printer.setTerminalOrder(null);
                }
            });
        }
    }

    /** Keeps a bound order and its machine agreeing about what is being worked on. */
    private static void syncBoundOrder(ServerLevel level, PrintRequestQueue queue,
                                       PrintRequest request, BlockPos bound, OrderSink sink) {
        if (!level.isLoaded(bound)) {
            queue.hold(request, "the machine is in unloaded chunks");
            return;
        }
        Optional<PrinterBlockEntity> machine = printerAt(level, bound);
        if (machine.isEmpty()) {
            queue.cancel(request, "the machine running this order is gone");
            return;
        }
        PrinterBlockEntity printer = machine.get();
        // The player has taken this machine back (a disc, or their own Item Mode template). Give
        // up the lease entirely rather than re-installing the order: the machine drops it every
        // tick while manual work is loaded, so re-handing it just ping-pongs, and the request
        // stays pinned to a machine that will never run it instead of moving to one that would.
        if (hasManualWork(printer)) {
            queue.hold(request, "the machine is busy with the player's own work");
            queue.release(request);
            printer.releaseTerminalOrder();
            return;
        }
        PrinterBlockEntity.TerminalOrder current = printer.terminalOrder();
        if (current == null || !current.id().equals(request.id())) {
            // The machine lost the order (chunk reload, block entity rebuilt) but the lease
            // survived. Re-hand it rather than cancelling: the order is still valid and the
            // delivered count is authoritative, so re-issuing cannot double-deliver.
            printer.setTerminalOrder(orderFor(request, queue, bound, sink));
        }
        reflectMachineState(queue, request, printer);
    }

    private static void bindToFreeMachine(ServerLevel level, PrintRequestQueue queue,
                                          PrintRequest request, List<BlockPos> machines,
                                          OrderSink sink) {
        for (BlockPos pos : machines) {
            if (queue.isLeased(pos)) {
                continue;
            }
            Optional<PrinterBlockEntity> found = printerAt(level, pos);
            if (found.isEmpty()) {
                continue;
            }
            PrinterBlockEntity printer = found.get();
            // A machine the player is already using is not available. A terminal order must never
            // preempt a print somebody started at the machine itself.
            if (printer.hasTerminalOrder() || hasManualWork(printer)) {
                continue;
            }
            // Refuse a machine that cannot do this job at all, so the order does not bind to a
            // tier-1 printer and sit there reporting NEEDS_HIGHER_TIER forever while a tier-8
            // fabricator behind it in the list stays idle.
            if (!PrintEligibility.of(new ItemStack(request.item()), printer.tier().number())
                    .printable()) {
                continue;
            }
            if (queue.bind(request, pos)) {
                printer.setTerminalOrder(orderFor(request, queue, pos, sink));
                return;
            }
        }
    }

    /** Mirrors the machine's own status onto the order, so the GUI explains itself. */
    private static void reflectMachineState(PrintRequestQueue queue, PrintRequest request,
                                            PrinterBlockEntity printer) {
        switch (printer.state()) {
            case PAUSED_NO_FILAMENT -> queue.hold(request, "waiting for filament");
            case PAUSED_NO_POWER -> queue.hold(request, "waiting for power");
            case PAUSED_OUTPUT_FULL -> queue.hold(request, "nowhere to put the output");
            case NEEDS_HIGHER_TIER -> queue.hold(request, "needs a higher-tier machine");
            // Eligibility was checked before binding, so reaching NOT_PRINTABLE means the rules
            // moved under a running order (a datapack reload retagging an item, say). Let go of
            // the machine and requeue rather than cancelling: the order may well be printable on
            // another machine, and cancelling would throw away the remainder of an order that has
            // already delivered and billed part of itself.
            case NOT_PRINTABLE -> {
                queue.hold(request, "this machine will not print that; looking for another");
                queue.release(request);
                printer.releaseTerminalOrder();
            }
            default -> queue.resume(request);
        }
    }

    private static PrinterBlockEntity.TerminalOrder orderFor(PrintRequest request,
                                                             PrintRequestQueue queue,
                                                             BlockPos machine, OrderSink sink) {
        return new PrinterBlockEntity.TerminalOrder(request.id(), request.item(), sink,
                // Credit through the queue, never the request: the queue re-checks that this
                // machine still holds the lease for this id, which is what makes a completion
                // arriving after a cancel a no-op instead of a delivery.
                (id, count) -> queue.credit(id, machine, count),
                // Asked before every item, so the machine stops the moment the order ends even if
                // nothing ever comes back to take the order off it.
                () -> queue.byId(request.id())
                        .filter(r -> !r.status().isTerminal())
                        .map(PrintRequest::remaining)
                        .orElse(0));
    }

    /** Releases a machine that is no longer wanted, so it does not sit holding a dead order. */
    public static void clear(ServerLevel level, BlockPos machine) {
        printerAt(level, machine).ifPresent(p -> p.setTerminalOrder(null));
    }

    /**
     * Whether the player has claimed this machine for work of their own: anything in the template
     * slot (an Item Mode item or a blueprint disc) or a running job. Asked in both directions, so
     * an order neither binds to an occupied machine nor stays bound to one that becomes occupied.
     */
    private static boolean hasManualWork(PrinterBlockEntity printer) {
        return printer.activeJob() != null
                || !printer.inventory().getStackInSlot(PrinterBlockEntity.SLOT_TEMPLATE).isEmpty();
    }

    private static Optional<PrinterBlockEntity> printerAt(ServerLevel level, BlockPos pos) {
        // getBlockEntity on an unloaded chunk would load it; a terminal must not keep chunks alive
        // just by looking at them, so an unloaded machine reads as absent and its order holds.
        if (!level.isLoaded(pos)) {
            return Optional.empty();
        }
        return level.getBlockEntity(pos) instanceof PrinterBlockEntity printer
                ? Optional.of(printer)
                : Optional.empty();
    }
}
