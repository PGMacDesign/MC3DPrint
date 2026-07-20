package com.pgmacdesign.mc3dprint.machine.sorter;

import com.pgmacdesign.mc3dprint.config.MC3DPrintConfig;
import com.pgmacdesign.mc3dprint.fu.FuValue;
import com.pgmacdesign.mc3dprint.fu.FuValueRegistry;
import com.pgmacdesign.mc3dprint.machine.WinderBlockEntity;
import com.pgmacdesign.mc3dprint.registry.ModBlockEntities;
import com.pgmacdesign.mc3dprint.registry.ModCapabilities;
import com.pgmacdesign.mc3dprint.registry.ModItemTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;

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
 * world — the {@code ITEM_HANDLER} capability hands back a wrapper whose {@code extractItem} is
 * a no-op — so a hopper/pipe funnels in but can never pull back out.
 */
public class SorterBlockEntity extends BlockEntity implements MenuProvider {
    public static final int POOL_SLOTS = 9;
    public static final int MAX_TIER = 8;

    /**
     * The general routing pool. {@code isItemValid} is the permanent-reject door: an unvalued
     * or winder-blacklisted item never enters, for BOTH capability insertion (hoppers/pipes)
     * and menu shift-click (menu slots honour {@code isItemValid}).
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
    private final LazyOptional<IItemHandler> itemCap = LazyOptional.of(() -> externalHandler);

    /**
     * Monotonic per-tier round-robin counters (index = tier, 1..8; 0 unused). Stored as a
     * plain counter and taken {@code floorMod (candidate count)} at use, so adding/removing a
     * winder can never point it out of range or systematically skip one.
     */
    private int[] cursors = new int[MAX_TIER + 1];

    /** Per-tier reachable-winder counts, refreshed each tick for the GUI readout only. */
    private int[] winderCountByTier = new int[MAX_TIER + 1];

    public SorterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FILAMENT_ITEM_SORTER.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SorterBlockEntity sorter) {
        sorter.tick();
    }

    /** True if the stack is allowed into the pool (has an FU value AND is not winder-blacklisted). */
    private static boolean acceptable(ItemStack stack) {
        if (stack.isEmpty()) {
            return true; // allow clearing a slot
        }
        return FuValueRegistry.valueOf(stack).isPresent() && !ModItemTags.isWinderBlacklisted(stack);
    }

    private void tick() {
        if (level == null || level.isClientSide()) {
            return;
        }
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
            // The winder exposes ONLY its input slot on a non-null face (a RangedWrapper), so
            // index 0 here is SLOT_INPUT — the same slot a hopper would feed.
            IItemHandler target = w.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElse(null);
            if (target == null) {
                continue;
            }
            ItemStack one = stack.copyWithCount(1);
            ItemStack remainder = target.insertItem(0, one, false);
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
            BlockEntity be = level.getBlockEntity(worldPosition.relative(dir));
            if (be == null || be.isRemoved()) {
                continue;
            }
            be.getCapability(ModCapabilities.WINDER_ROUTING, dir.getOpposite())
                    .ifPresent(routing -> routing.collectWinders(set));
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

    // --- Capability ---

    /** Insert-only on every face — the pool is a one-way funnel, never externally extractable. */
    public IItemHandler getItemHandler(@Nullable Direction side) {
        return externalHandler;
    }

    @Override
    @Nonnull
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return itemCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemCap.invalidate();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Pool", pool.serializeNBT());
        tag.putIntArray("Cursors", cursors);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        pool.deserializeNBT(tag.getCompound("Pool"));
        int[] stored = tag.getIntArray("Cursors");
        if (stored.length == cursors.length) {
            cursors = stored;
        }
    }

    /**
     * {@code IItemHandlerModifiable} so anything expecting a modifiable handler (hopper/pipe
     * bridges that snapshot-and-revert) accepts it, but {@code extractItem} is a hard no-op: the
     * external world can only ever insert. The menu bypasses this and touches the raw pool directly.
     */
    private static final class InsertOnlyHandler implements IItemHandlerModifiable {
        private final ItemStackHandler backing;

        InsertOnlyHandler(ItemStackHandler backing) {
            this.backing = backing;
        }

        @Override
        public int getSlots() {
            return backing.getSlots();
        }

        @Override
        @Nonnull
        public ItemStack getStackInSlot(int slot) {
            return backing.getStackInSlot(slot);
        }

        @Override
        @Nonnull
        public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
            return backing.insertItem(slot, stack, simulate);
        }

        @Override
        @Nonnull
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY; // one-way funnel
        }

        @Override
        public int getSlotLimit(int slot) {
            return backing.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return backing.isItemValid(slot, stack);
        }

        @Override
        public void setStackInSlot(int slot, @Nonnull ItemStack stack) {
            backing.setStackInSlot(slot, stack);
        }
    }
}
