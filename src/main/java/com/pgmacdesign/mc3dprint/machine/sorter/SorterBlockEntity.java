package com.pgmacdesign.mc3dprint.machine.sorter;

import com.pgmacdesign.mc3dprint.compat.BeData;
import com.pgmacdesign.mc3dprint.compat.TransferCompat;
import com.pgmacdesign.mc3dprint.config.MC3DPrintConfig;
import com.pgmacdesign.mc3dprint.fu.FuValue;
import com.pgmacdesign.mc3dprint.fu.FuValueRegistry;
import com.pgmacdesign.mc3dprint.machine.WinderBlockEntity;
import com.pgmacdesign.mc3dprint.registry.ModBlockEntities;
import com.pgmacdesign.mc3dprint.registry.ModCapabilities;
import com.pgmacdesign.mc3dprint.registry.ModItemTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Filament Tier Item Sorter: a passive, unpowered logistics block. Items arrive by hopper,
 * pipe, or hand into a nine-slot general pool; each tick it routes up to N of them (config,
 * clamped 1-64) to the Filament Winder holding a spool of that item's material tier.
 *
 * <p>Discovery rides the MC3D Cable graph as <b>topology only</b> (the cable never carries
 * items) via {@link IWinderRouting}: a winder adds itself, a cable forwards its whole network,
 * and this block unions its six faces. Direct-touch works with no cable at all. Selection is
 * round-robin across same-tier winders, skipping any that would stall (spool full, or input
 * slot busy) — the cursor advances only past the winder that actually took the item.
 *
 * <p>Two permanent failure modes are refused at the door ({@link #pool}'s {@code isItemValid}):
 * an item with no FU value, or one on the winder blacklist. Everything else is held in the
 * pool and retried; nothing is ever voided. The pool is <b>insert-only</b> to the outside
 * world — {@link #getItemHandler} hands back a wrapper whose {@code extractItem} is a no-op —
 * so a hopper/pipe funnels in but can never pull back out.
 *
 * <p><b>Reject routing</b> softens that door rather than replacing it. When a non-MC3DPrint
 * inventory sits on any face, un-windable items are accepted and pushed into it by
 * {@link #drainRejects()} on the next tick, so a mixed stream can be pointed straight at the
 * sorter. With no such inventory, or with the feature configured off, they are refused exactly
 * as before, which is why no existing build changes behaviour.
 *
 * <p>The decision and the action are deliberately split. The door ({@code isItemValid}) only
 * answers the read-only question "would a target take this", because it runs inside
 * {@code insertItem} — where a write would corrupt a caller's {@code simulate} pass, and where
 * pushing to a neighbouring sorter could recurse without bound. The write happens on the tick,
 * where this block already mutates neighbours to feed winders.
 */
public class SorterBlockEntity extends BlockEntity implements MenuProvider {
    public static final int POOL_SLOTS = 9;
    public static final int MAX_TIER = 8;

    /**
     * The general routing pool. {@code isItemValid} is the door: a routable item always enters,
     * an un-windable one only when a reject target would take it. Applies to BOTH capability
     * insertion (hoppers/pipes) and menu shift-click (menu slots honour {@code isItemValid}).
     */
    private final ItemStackHandler pool = new ItemStackHandler(POOL_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return acceptable(stack);
        }
    };

    /** External capability view: insert-only. Nothing funnelled in is ever externally extractable. */
    private final InsertOnlyHandler externalHandler = new InsertOnlyHandler(pool);

    /**
     * Monotonic per-tier round-robin counters (index = tier, 1..8; 0 unused). Stored as a
     * plain counter and taken {@code floorMod (candidate count)} at use, so adding/removing a
     * winder can never point it out of range or systematically skip one.
     */
    private int[] cursors = new int[MAX_TIER + 1];

    /** Per-tier reachable-winder counts, refreshed each tick for the GUI readout only. */
    private int[] winderCountByTier = new int[MAX_TIER + 1];

    /**
     * Faces currently holding a usable reject target, refreshed each tick. Cached because the
     * door consults it on every insertion attempt, and a hopper retries against all nine pool
     * slots each time; re-scanning six faces per slot per attempt is not worth the precision.
     * Handlers are resolved live from these faces rather than cached themselves, so a target
     * removed mid-tick is simply not found.
     */
    private List<Direction> rejectFaces = List.of();

    public SorterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FILAMENT_ITEM_SORTER.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SorterBlockEntity sorter) {
        sorter.tick();
    }

    /** True if the stack can be wound, i.e. has an FU value AND is not winder-blacklisted. */
    private static boolean routable(ItemStack stack) {
        return FuValueRegistry.valueOf(stack).isPresent() && !ModItemTags.isWinderBlacklisted(stack);
    }

    /**
     * The door. A routable item always enters; an un-windable one only when a reject target
     * would take it, so that it can be pushed straight back out on the next tick. Read-only by
     * construction — this runs inside {@code insertItem}, including a caller's {@code simulate}
     * pass, and must never write.
     */
    private boolean acceptable(ItemStack stack) {
        if (stack.isEmpty()) {
            return true; // allow clearing a slot
        }
        return routable(stack) || rejectTargetWouldAccept(stack);
    }

    private void tick() {
        if (level == null || level.isClientSide()) {
            return;
        }
        refreshRejectFaces();
        drainRejects();
        List<WinderBlockEntity> winders = reachableWinders();
        recountWinders(winders); // keep the GUI readout fresh even while idle
        if (isPoolEmpty()) {
            return;
        }
        int budget = MC3DPrintConfig.sorterMaxPerTick(); // hard per-tick placement cap [1,64]
        int placed = 0;
        while (placed < budget && routeOneItem(winders)) {
            placed++;
        }
    }

    /** Route exactly one item into a round-robin-selected winder, or return false if nothing routable. */
    private boolean routeOneItem(List<WinderBlockEntity> winders) {
        for (int slot = 0; slot < pool.getSlots(); slot++) {
            ItemStack stack = pool.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            // Live door re-validation: an item that lost its value or got blacklisted since it
            // entered the pool is never routed (and never destroyed — it just sits).
            Optional<FuValue> value = FuValueRegistry.valueOf(stack);
            if (value.isEmpty() || ModItemTags.isWinderBlacklisted(stack)) {
                continue;
            }
            int tier = value.get().tier();
            List<WinderBlockEntity> candidates = candidatesForTier(winders, tier);
            if (candidates.isEmpty()) {
                continue;
            }
            if (placeIntoRoundRobin(slot, stack, tier, candidates)) {
                return true;
            }
        }
        return false;
    }

    /** Winders whose docked spool is exactly {@code tier}, BlockPos-sorted for a stable ring. */
    private List<WinderBlockEntity> candidatesForTier(List<WinderBlockEntity> winders, int tier) {
        List<WinderBlockEntity> out = new ArrayList<>();
        for (WinderBlockEntity w : winders) {
            if (!w.isRemoved() && w.dockedSpoolTier() == tier) {
                out.add(w);
            }
        }
        out.sort(Comparator.comparingInt((WinderBlockEntity w) -> w.getBlockPos().getX())
                .thenComparingInt(w -> w.getBlockPos().getY())
                .thenComparingInt(w -> w.getBlockPos().getZ()));
        return out;
    }

    /**
     * Insert ONE item into the first non-stalled candidate at or after the cursor. Inserts into
     * the winder first, then decrements the pool by exactly what was accepted (never extract-then-
     * insert), and advances the cursor only past the winder that actually took it.
     */
    private boolean placeIntoRoundRobin(int slot, ItemStack stack, int tier, List<WinderBlockEntity> candidates) {
        int n = candidates.size();
        int base = cursors[tier];
        for (int i = 0; i < n; i++) {
            WinderBlockEntity w = candidates.get(Math.floorMod(base + i, n));
            // Re-validate the (possibly cable-cached) winder live at insert time.
            if (w.isRemoved() || w.dockedSpoolTier() != tier || !w.acceptsForRouting(stack)) {
                continue; // stall-skip: this winder does not burn a turn
            }
            ItemStack one = stack.copyWithCount(1);
            ItemStack remainder = w.getItemHandler(Direction.UP).insertItem(0, one, false);
            int accepted = one.getCount() - remainder.getCount();
            if (accepted <= 0) {
                continue; // lost a race for the slot this tick; try the next candidate
            }
            stack.shrink(accepted);
            pool.setStackInSlot(slot, stack.isEmpty() ? ItemStack.EMPTY : stack);
            cursors[tier] = base + i + 1; // advance PAST only the winder that took the item
            setChanged();
            return true;
        }
        return false;
    }

    // --- Reject routing: un-windable items out to an adjacent inventory ---

    /**
     * The mod's own machines are never reject targets. Only four of them expose an item
     * capability at all, and the winder is the one that makes this mandatory rather than
     * tidy: its input slot accepts <i>any</i> item (only the spool slot is filtered), so a
     * sorter beside a winder — the normal layout — would otherwise push bedrock straight into
     * it and jam it. The MC3D Cable needs no mention: it exposes no item capability, so it can
     * never be found here and never competes for a face.
     */
    private static boolean isOwnMachine(@Nullable BlockEntity be) {
        return be instanceof SorterBlockEntity
                || be instanceof WinderBlockEntity
                || be instanceof com.pgmacdesign.mc3dprint.machine.PrinterBlockEntity
                || be instanceof com.pgmacdesign.mc3dprint.machine.ClockGeneratorBlockEntity;
    }

    /** The item handler on {@code dir}, or null if absent or one of the mod's own machines. */
    @Nullable
    private IItemHandler rejectHandler(Direction dir) {
        if (level == null) {
            return null;
        }
        BlockPos neighbour = worldPosition.relative(dir);
        if (isOwnMachine(level.getBlockEntity(neighbour))) {
            return null;
        }
        return TransferCompat.findItems(level, neighbour, dir.getOpposite());
    }

    private void refreshRejectFaces() {
        if (level == null || !MC3DPrintConfig.sorterRejectRouting()) {
            rejectFaces = List.of();
            return;
        }
        List<Direction> faces = new ArrayList<>();
        for (Direction dir : Direction.values()) {
            if (rejectHandler(dir) != null) {
                faces.add(dir);
            }
        }
        rejectFaces = faces;
    }

    /** Simulate-only: would any reject target take at least one of these? */
    private boolean rejectTargetWouldAccept(ItemStack stack) {
        if (!MC3DPrintConfig.sorterRejectRouting() || rejectFaces.isEmpty()) {
            return false;
        }
        ItemStack one = stack.copyWithCount(1);
        for (Direction dir : rejectFaces) {
            IItemHandler target = rejectHandler(dir);
            if (target == null) {
                continue;
            }
            for (int slot = 0; slot < target.getSlots(); slot++) {
                if (target.insertItem(slot, one, true).isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Push un-routable pool stacks out to a reject target, at most {@code sorterMaxPerTick}
     * stacks per tick.
     *
     * <p><b>The budget is not decorative.</b> One push is not one insertion call: it walks
     * every reject face and every slot of each target, because {@link IItemHandler} offers no
     * cheaper way to ask where a stack fits. Six double chests is ~324 {@code insertItem} calls
     * for a single stack, so draining all nine pool slots unbounded would be thousands of calls
     * per tick per sorter — the exact server-thread stall {@code sorterMaxPerTick} exists to
     * prevent for routing. The counter is per attempt rather than per success, so a full target
     * cannot be rescanned nine times a tick for nothing.
     *
     * <p>The budget is the only thing bounding this. Do <b>not</b> also hoist the handler
     * lookups into a once-per-tick list here: that resolves capabilities on every tick of every
     * sorter regardless of whether anything needs rejecting, and it stalled the gametest oracle
     * hard enough to hang the run at 47 of 192 tests. {@link #pushToRejectTargets} resolves them
     * lazily so a sorter with nothing to reject does no capability work at all.
     *
     * <p>The budget is deliberately <i>separate</i> from routing's rather than shared: a flood
     * of junk must not be able to consume the routing budget and stall the sorter's actual job.
     *
     * <p>Also the recovery path for items stranded by a live rule change. {@code routeOneItem}
     * skips an item that entered valid and later lost its FU value or was blacklisted, and the
     * pool is not externally extractable, so before this existed only a player opening the GUI
     * could clear one. Recovery needs reject routing enabled and a target with room, like any
     * other push.
     */
    private void drainRejects() {
        if (!MC3DPrintConfig.sorterRejectRouting() || rejectFaces.isEmpty()) {
            return;
        }
        int budget = MC3DPrintConfig.sorterMaxPerTick();
        int attempts = 0;
        for (int slot = 0; slot < pool.getSlots() && attempts < budget; slot++) {
            ItemStack stack = pool.getStackInSlot(slot);
            if (stack.isEmpty() || routable(stack)) {
                continue;
            }
            attempts++;
            // Insert first, then write back the remainder, so a partial push can never void
            // the part that was not accepted.
            ItemStack remainder = pushToRejectTargets(stack.copy());
            if (remainder.getCount() != stack.getCount()) {
                pool.setStackInSlot(slot, remainder.isEmpty() ? ItemStack.EMPTY : remainder);
                setChanged();
            }
        }
    }

    /**
     * Inserts as much of {@code stack} as the reject targets accept; returns what is left.
     *
     * <p>Handlers are resolved here, inside the per-stack path, rather than hoisted into a
     * once-per-tick list in {@link #drainRejects()}. Hoisting looks cheaper and is not: it
     * resolves capabilities on <b>every</b> tick of <b>every</b> sorter, including the
     * overwhelmingly common case of a sorter with nothing to reject, where this way does no
     * capability work at all. Resolving lazily keeps an idle sorter genuinely idle.
     */
    private ItemStack pushToRejectTargets(ItemStack stack) {
        for (Direction dir : rejectFaces) {
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            IItemHandler target = rejectHandler(dir);
            if (target == null) {
                continue;
            }
            for (int slot = 0; slot < target.getSlots() && !stack.isEmpty(); slot++) {
                stack = target.insertItem(slot, stack, false);
            }
        }
        return stack;
    }

    private boolean isPoolEmpty() {
        for (int i = 0; i < pool.getSlots(); i++) {
            if (!pool.getStackInSlot(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** Union of every winder reachable across the sorter's six faces (identity-deduped). */
    private List<WinderBlockEntity> reachableWinders() {
        if (level == null) {
            return List.of();
        }
        Set<WinderBlockEntity> set = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Direction dir : Direction.values()) {
            IWinderRouting routing = level.getCapability(
                    ModCapabilities.WINDER_ROUTING, worldPosition.relative(dir), dir.getOpposite());
            if (routing != null) {
                routing.collectWinders(set);
            }
        }
        return new ArrayList<>(set);
    }

    private void recountWinders(List<WinderBlockEntity> winders) {
        int[] counts = new int[MAX_TIER + 1];
        for (WinderBlockEntity w : winders) {
            if (w.isRemoved()) {
                continue;
            }
            int t = w.dockedSpoolTier();
            if (t >= 1 && t <= MAX_TIER) {
                counts[t]++;
            }
        }
        winderCountByTier = counts;
    }

    /** The raw pool — used by the menu (full player access) and gametests, never for the capability. */
    public ItemStackHandler pool() {
        return pool;
    }

    /** GUI readout of reachable winders per tier (index 0 = T1 .. 7 = T8). */
    public ContainerData containerData() {
        return new ContainerData() {
            @Override
            public int get(int index) {
                int[] c = winderCountByTier;
                return index >= 0 && index + 1 < c.length ? c[index + 1] : 0;
            }

            @Override
            public void set(int index, int value) {
                // read-only view
            }

            @Override
            public int getCount() {
                return MAX_TIER;
            }
        };
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int windowId, Inventory playerInventory, Player player) {
        return new SorterMenu(windowId, playerInventory, this);
    }

    // --- Capability (exposed raw; registered centrally in ModCapabilities) ---

    /** Insert-only on every face — the pool is a one-way funnel, never externally extractable. */
    public IItemHandler getItemHandler(@Nullable Direction side) {
        return externalHandler;
    }

    //? if >=1.21.5 {
    /*@Override
    protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput out) {
        super.saveAdditional(out);
        writeData(BeData.writer(out));
    }

    @Override
    protected void loadAdditional(net.minecraft.world.level.storage.ValueInput in) {
        super.loadAdditional(in);
        readData(BeData.reader(in));
    }
    *///?} else {
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        writeData(BeData.writer(tag, registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        readData(BeData.reader(tag, registries));
    }
    //?}

    private void writeData(BeData.Writer w) {
        w.putHandler("Pool", pool);
        w.putIntArray("Cursors", cursors);
    }

    private void readData(BeData.Reader r) {
        r.readHandler("Pool", pool);
        int[] stored = r.getIntArray("Cursors");
        if (stored.length == cursors.length) {
            cursors = stored;
        }
    }

    /**
     * IItemHandlerModifiable so the 1.21.9+ capability bridge accepts it, but {@code extractItem}
     * is a hard no-op: the external world can only ever insert. The menu bypasses this and touches
     * the raw pool directly.
     *
     * <p><b>Do not narrow this to plain {@code IItemHandler}.</b> On 1.21.9+ {@code ModCapabilities}
     * registers the sorter's item capability behind an {@code instanceof IItemHandlerModifiable}
     * guard and hands the handler to {@code TransferCompat.ItemHandlerBridge}, whose
     * {@code revertToSnapshot} restores each slot through {@link #setStackInSlot}. Drop the
     * interface and that guard fails, the capability resolves to {@code null}, and hoppers/pipes
     * can no longer feed the sorter at all on five of the eight supported targets.
     *
     * <p><b>{@code setStackInSlot} is filtered</b>, unlike an earlier version of this class which
     * left it open and justified that as "a privileged internal API". It is not internal: below
     * 1.21.9 this handler is registered with the item capability directly, so any mod holding the
     * capability can call it, and an unfiltered write was a side door straight past
     * {@code isItemValid}. The rollback path that genuinely needs unfiltered writes now goes
     * through {@link com.pgmacdesign.mc3dprint.compat.SnapshotRestorable#restoreSlot} instead.
     */
    private static final class InsertOnlyHandler
            implements IItemHandlerModifiable, com.pgmacdesign.mc3dprint.compat.SnapshotRestorable {
        private final ItemStackHandler backing;

        InsertOnlyHandler(ItemStackHandler backing) {
            this.backing = backing;
        }

        @Override
        public int getSlots() {
            return backing.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return backing.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return backing.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY; // one-way funnel
        }

        @Override
        public int getSlotLimit(int slot) {
            return backing.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return backing.isItemValid(slot, stack);
        }

        /**
         * Filtered. This is a PUBLIC capability surface: below 1.21.9 the sorter registers this
         * handler with the item capability directly, so any mod holding it can call this. Left
         * unfiltered it was a side door around {@code isItemValid} that could park unroutable
         * items in the pool, where routing can never move them and only a player opening the GUI
         * can clear them.
         *
         * <p>An invalid stack is REFUSED (no write), never swallowed: the caller's stack object
         * is untouched, so nothing is voided. Rollback restores go through
         * {@link #restoreSlot} instead, which is unfiltered by design.
         */
        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            if (!stack.isEmpty() && !backing.isItemValid(slot, stack)) {
                return;
            }
            backing.setStackInSlot(slot, stack);
        }

        /**
         * Unfiltered restore path for our own snapshot journal. A revert has to put back exactly
         * what it snapshotted even if the pool filter would refuse those stacks today (FU values
         * and blacklist membership can move between reloads), and refusing there would void a
         * player's items rather than protect them.
         */
        @Override
        public void restoreSlot(int slot, ItemStack stack) {
            backing.setStackInSlot(slot, stack);
        }
    }
}
