package com.pgmacdesign.mc3dprint.machine.terminal;

import com.pgmacdesign.mc3dprint.compat.NbtCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Item;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The terminal's order book: every {@link PrintRequest} it has taken, in the order taken, plus the
 * machine leases that keep two orders off the same machine.
 *
 * <p><b>One writer.</b> Every mutation of every request goes through this class, on the server
 * thread, and {@link PrintRequest}'s mutators are package-private so there is no second door. The
 * alternative, letting each machine advance the order it is running, means two machines and a
 * terminal all writing the same counter, and the interleavings that produces are exactly the ones
 * nobody tests: a completion landing between another machine's check and its write.
 *
 * <p><b>Leases are how a request gets exactly one executor.</b> {@link #bind} refuses a machine
 * that already holds a lease, so an order queues behind whatever is running rather than
 * interleaving with it, and {@link #release} is reached on every exit path, including cancellation
 * and a failed revalidation after load. A lease is keyed by machine position and stores the request
 * id, so a stale completion arriving for a machine that has since been re-leased is dropped on the
 * id check rather than credited to the new order.
 */
public final class PrintRequestQueue {

    /** Hard ceiling on queued orders, so a stuck terminal cannot grow the save file forever. */
    public static final int MAX_OPEN_REQUESTS = 64;

    private final List<PrintRequest> requests = new ArrayList<>();
    /** machine position -> the request id currently leased to it. */
    private final Map<BlockPos, UUID> leases = new HashMap<>();

    /** Every request, oldest first, including terminal ones until they are swept. */
    public List<PrintRequest> all() {
        return Collections.unmodifiableList(requests);
    }

    /** Requests that still have work to do, oldest first. */
    public List<PrintRequest> open() {
        List<PrintRequest> out = new ArrayList<>();
        for (PrintRequest r : requests) {
            if (!r.status().isTerminal()) {
                out.add(r);
            }
        }
        return out;
    }

    public Optional<PrintRequest> byId(UUID id) {
        for (PrintRequest r : requests) {
            if (r.id().equals(id)) {
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }

    public int openCount() {
        return open().size();
    }

    /**
     * Takes a new order, or empty when the book is full. The caller supplies the id so the packet
     * handler can generate it server-side; nothing client-sent is trusted here.
     */
    public Optional<PrintRequest> enqueue(UUID id, Item item, int quantity) {
        if (openCount() >= MAX_OPEN_REQUESTS || quantity <= 0) {
            return Optional.empty();
        }
        if (byId(id).isPresent()) {
            return Optional.empty(); // an id is used once, ever
        }
        PrintRequest req = new PrintRequest(id, item, quantity);
        requests.add(req);
        return Optional.of(req);
    }

    /**
     * Leases {@code machine} to {@code request}. Fails when either side is already committed: the
     * machine holds a live lease, or the request is terminal or already bound elsewhere.
     */
    public boolean bind(PrintRequest request, BlockPos machine) {
        if (request.status().isTerminal() || request.machine() != null) {
            return false;
        }
        UUID holder = leases.get(machine);
        if (holder != null && !holder.equals(request.id())) {
            return false;
        }
        leases.put(machine, request.id());
        request.bindTo(machine);
        return true;
    }

    /**
     * Drops {@code request}'s lease, if it still owns one. Safe to call twice and safe to call on a
     * request that never bound, which is what lets every exit path call it unconditionally rather
     * than each one deciding whether it should.
     */
    public void release(PrintRequest request) {
        BlockPos pos = request.machine();
        if (pos != null && request.id().equals(leases.get(pos))) {
            leases.remove(pos);
        }
        request.unbind();
    }

    /** The request currently leased to {@code machine}, if any. */
    public Optional<PrintRequest> leasedTo(BlockPos machine) {
        UUID id = leases.get(machine);
        return id == null ? Optional.empty() : byId(id);
    }

    public boolean isLeased(BlockPos machine) {
        return leases.containsKey(machine);
    }

    /**
     * Credits delivered items against {@code id} and returns how many were credited, which is zero
     * when the id is unknown, terminal, or no longer leased to {@code machine}.
     *
     * <p>The machine check is the guard against a late completion. A job that finishes an item
     * after its order was cancelled, or after the machine was re-leased to a different order,
     * arrives here with a machine that no longer matches, and is dropped rather than credited to
     * whatever now holds the lease.
     */
    public int credit(UUID id, BlockPos machine, int count) {
        PrintRequest req = byId(id).orElse(null);
        if (req == null || !id.equals(leases.get(machine))) {
            return 0;
        }
        int credited = req.credit(count);
        if (req.status() == PrintRequest.Status.COMPLETE) {
            release(req);
        }
        return credited;
    }

    public void hold(PrintRequest request, @Nullable String why) {
        request.hold(why);
    }

    public void resume(PrintRequest request) {
        request.resume();
    }

    /** Ends an order early and frees whatever it held. Nothing can be credited to it afterwards. */
    public void cancel(PrintRequest request, @Nullable String why) {
        BlockPos pos = request.machine();
        if (pos != null && request.id().equals(leases.get(pos))) {
            leases.remove(pos);
        }
        request.cancel(why);
    }

    public boolean cancel(UUID id, @Nullable String why) {
        return byId(id).map(r -> {
            cancel(r, why);
            return true;
        }).orElse(false);
    }

    /** Forgets terminal orders. Called after the GUI has had a chance to show them. */
    public void sweepTerminal() {
        requests.removeIf(r -> r.status().isTerminal());
    }

    /**
     * Re-checks every bound request against the world after a load, cancelling any whose machine is
     * gone. Restoring a lease to a position without confirming what is there now would point an
     * order at whatever the player has since built in that spot.
     *
     * @param stillValid answers whether a machine that can execute orders is still at that position
     */
    public void revalidate(java.util.function.Predicate<BlockPos> stillValid) {
        for (PrintRequest req : new ArrayList<>(requests)) {
            BlockPos pos = req.machine();
            if (pos == null || req.status().isTerminal()) {
                continue;
            }
            if (!stillValid.test(pos)) {
                cancel(req, "the machine running this order is gone");
            }
        }
        // Any lease not claimed by a live request is stale by construction.
        leases.entrySet().removeIf(e -> byId(e.getValue())
                .map(r -> r.status().isTerminal())
                .orElse(true));
    }

    // --- persistence ---

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (PrintRequest r : requests) {
            if (!r.status().isTerminal()) {
                list.add(r.save()); // terminal orders are history, not state worth reloading
            }
        }
        tag.put("Requests", list);
        return tag;
    }

    public void load(CompoundTag tag) {
        requests.clear();
        leases.clear();
        ListTag list = NbtCompat.getList(tag, "Requests", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            PrintRequest.load(NbtCompat.listGetCompound(list, i)).ifPresent(req -> {
                requests.add(req);
                BlockPos pos = req.machine();
                // Rebuild the lease from the request rather than persisting it separately: two
                // copies of the same fact can disagree after an interrupted save, and the request
                // is the one that has to be right.
                if (pos != null && !leases.containsKey(pos)) {
                    leases.put(pos, req.id());
                } else if (pos != null) {
                    req.unbind(); // two orders claiming one machine: the older one keeps it
                }
            });
        }
    }
}
