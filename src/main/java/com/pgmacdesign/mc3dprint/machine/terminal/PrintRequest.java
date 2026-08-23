package com.pgmacdesign.mc3dprint.machine.terminal;

import com.pgmacdesign.mc3dprint.compat.NbtCompat;
import com.pgmacdesign.mc3dprint.compat.RegistryCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

/**
 * One order placed at a terminal: print {@code quantity} of {@code item}, bill it to whichever
 * machine ends up executing it.
 *
 * <p><b>Delivered count, not a done flag.</b> Completion is derived from {@link #delivered()}
 * reaching {@link #quantity()}, and the count only ever moves forward, by the machine holding the
 * lease, after an item has actually landed somewhere. A separate boolean would be a second source
 * of truth for the same fact, and the two drift the moment a completion arrives late: the flag says
 * done while the count says 40 of 64, and whichever one the next reader trusts is a coin flip. With
 * one counter there is nothing to disagree with.
 *
 * <p><b>The id outlives the object.</b> A request is matched by {@link #id()} everywhere it crosses
 * a boundary, so a completion for an order that was cancelled and replaced cannot be credited to
 * its successor. Identity is the id alone; two requests for the same item and quantity are still
 * two different orders.
 *
 * <p>Immutable except for {@code delivered} and {@code state}, both of which are only advanced
 * through {@link PrintRequestQueue} on the server thread.
 */
public final class PrintRequest {

    /** Where an order is in its life. Persisted by name, so reordering is safe. */
    public enum Status {
        /** Accepted, not yet bound to a machine. */
        QUEUED,
        /** Bound to a machine that is working on it. */
        RUNNING,
        /** Bound, but the machine cannot proceed right now (no filament, no power, no grid). */
        HELD,
        /** Delivered in full. Terminal state. */
        COMPLETE,
        /** Ended early. Terminal state, and nothing may be delivered against it afterwards. */
        CANCELLED;

        public boolean isTerminal() {
            return this == COMPLETE || this == CANCELLED;
        }

        static Status byName(String name) {
            for (Status s : values()) {
                if (s.name().equals(name)) {
                    return s;
                }
            }
            return QUEUED;
        }
    }

    private final UUID id;
    private final Item item;
    private final int quantity;
    /**
     * Who placed this. The queue is shared by everyone on the network and {@code sync} hands every
     * order id to every viewer, so without an owner any player can cancel any other player's work
     * with an ordinary packet.
     */
    @Nullable
    private final UUID owner;

    private int delivered;
    private Status status;
    @Nullable
    private BlockPos machine;
    /** Why the order stopped, for the GUI. Never load-bearing for control flow. */
    @Nullable
    private String reason;

    public PrintRequest(UUID id, Item item, int quantity) {
        this(id, item, quantity, null);
    }

    public PrintRequest(UUID id, Item item, int quantity, @Nullable UUID owner) {
        this.id = id;
        this.item = item;
        this.quantity = Math.max(1, quantity);
        this.owner = owner;
        this.delivered = 0;
        this.status = Status.QUEUED;
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    /**
     * Whether {@code player} may cancel this. The placer always may; an order with no recorded
     * owner (placed before this existed, or by something that is not a player) is treated as
     * everyone's, since refusing it would leave it uncancellable forever.
     */
    public boolean mayCancel(UUID player) {
        return owner == null || owner.equals(player);
    }

    public UUID id() {
        return id;
    }

    public Item item() {
        return item;
    }

    public int quantity() {
        return quantity;
    }

    public int delivered() {
        return delivered;
    }

    public int remaining() {
        return Math.max(0, quantity - delivered);
    }

    public Status status() {
        return status;
    }

    @Nullable
    public BlockPos machine() {
        return machine;
    }

    @Nullable
    public String reason() {
        return reason;
    }

    /** A fresh stack of the ordered item, for quoting. Never the delivered stack. */
    public ItemStack template() {
        return new ItemStack(item);
    }

    // --- mutation, all funnelled through PrintRequestQueue on the server thread ---

    void bindTo(BlockPos pos) {
        this.machine = pos;
        this.status = Status.RUNNING;
        this.reason = null;
    }

    void hold(@Nullable String why) {
        if (!status.isTerminal()) {
            this.status = Status.HELD;
            this.reason = why;
        }
    }

    void resume() {
        if (status == Status.HELD) {
            this.status = Status.RUNNING;
            this.reason = null;
        }
    }

    /**
     * Credits {@code count} items as delivered and returns how many were actually credited, which
     * is zero once the order is terminal. Clamped to what remains so a double dispatch cannot push
     * the count past the order, and refuses to run at all after cancellation, which is what stops a
     * late completion from resurrecting a cancelled order.
     */
    int credit(int count) {
        if (status.isTerminal() || count <= 0) {
            return 0;
        }
        int credited = Math.min(count, remaining());
        delivered += credited;
        if (remaining() == 0) {
            status = Status.COMPLETE;
            reason = null;
        }
        return credited;
    }

    void cancel(@Nullable String why) {
        if (!status.isTerminal()) {
            status = Status.CANCELLED;
            reason = why;
            machine = null;
        }
    }

    void unbind() {
        machine = null;
        if (status == Status.RUNNING || status == Status.HELD) {
            status = Status.QUEUED;
        }
    }

    // --- persistence ---

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        NbtCompat.putUUID(tag, "Id", id);
        tag.putString("Item", BuiltInRegistries.ITEM.getKey(item).toString());
        tag.putInt("Qty", quantity);
        tag.putInt("Delivered", delivered);
        tag.putString("Status", status.name());
        if (owner != null) {
            NbtCompat.putUUID(tag, "Owner", owner);
        }
        if (machine != null) {
            NbtCompat.putBlockPos(tag, "Machine", machine);
        }
        if (reason != null) {
            tag.putString("Reason", reason);
        }
        return tag;
    }

    /**
     * Reads a request back, or empty when the item no longer exists (a mod was removed between
     * saves). A dropped order is the right outcome there: the alternative is an order that can
     * never be filled sitting in the queue forever.
     */
    public static Optional<PrintRequest> load(CompoundTag tag) {
        Optional<UUID> savedId = NbtCompat.getUUID(tag, "Id");
        if (savedId.isEmpty()) {
            return Optional.empty();
        }
        ResourceLocation itemId = ResourceLocation.tryParse(NbtCompat.getString(tag, "Item"));
        Item item = itemId == null ? null : RegistryCompat.item(itemId);
        // A missing item means the mod that owned it is gone. Dropping the order is the only
        // honest outcome: keeping it would leave something in the queue that can never be filled
        // and can never be cleared by finishing.
        if (item == null || item == Items.AIR) {
            return Optional.empty();
        }
        PrintRequest req = new PrintRequest(savedId.get(), item, NbtCompat.getInt(tag, "Qty"),
                NbtCompat.getUUID(tag, "Owner").orElse(null));
        // Clamped at BOTH ends. Math.min alone preserves a negative persisted value, and a
        // negative delivered count makes remaining() larger than the order, so the dispatcher
        // would print and charge for items nobody asked for after a reload.
        req.delivered = Math.max(0, Math.min(NbtCompat.getInt(tag, "Delivered"), req.quantity));
        req.status = Status.byName(NbtCompat.getString(tag, "Status"));
        req.machine = NbtCompat.getBlockPos(tag, "Machine").orElse(null);
        req.reason = NbtCompat.contains(tag, "Reason") ? NbtCompat.getString(tag, "Reason") : null;
        // A saved order whose delivered count already covers its quantity is finished, whatever
        // the saved status says. Left as QUEUED or RUNNING it would be unfinishable: credit()
        // clamps to remaining(), which is already zero, so it never reaches the COMPLETE branch,
        // and the order would sit in the open list holding a lease and a slot forever. A
        // truncated Qty tag reaches the same state, since the constructor floors quantity at 1.
        if (req.remaining() == 0 && !req.status.isTerminal()) {
            req.status = Status.COMPLETE;
            req.machine = null;
            req.reason = null;
        }
        return Optional.of(req);
    }

    @Override
    public String toString() {
        return "PrintRequest[" + id + " " + delivered + "/" + quantity + " "
                + BuiltInRegistries.ITEM.getKey(item) + " " + status + "]";
    }
}
