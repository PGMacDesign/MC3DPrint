package com.pgmacdesign.mc3dprint.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

import static com.pgmacdesign.mc3dprint.registry.ModBlockEntities.REDSTONE_CLOCK;

/**
 * Redstone Clock: a self-contained, autonomous timer. Once placed it runs on its
 * own (no external power) and emits a single short redstone pulse from ALL SIX
 * faces every {@code intervalSeconds} seconds, then goes quiet until the next
 * tick. It is completely SILENT — the pulse plays no sound.
 *
 * <p>The interval is configurable 1–60 seconds via the block's GUI and is NEVER
 * negative (clamped). It persists in the block-entity NBT, so it survives a
 * world reload AND round-trips through the scanner/printer: a curated blueprint
 * can bake a specific interval ({@code IntervalSeconds}) into a printed clock so
 * each build ticks at exactly the rate it needs. This replaces the
 * fire-too-often paired-observer "clock" the farms used.
 */
public class RedstoneClockBlockEntity extends BlockEntity implements MenuProvider {
    public static final int MIN_SECONDS = 1;
    public static final int MAX_SECONDS = 60;
    public static final int DEFAULT_SECONDS = 5;
    private static final int TICKS_PER_SECOND = 20;
    /** Pulse length: 2 game ticks = 1 redstone tick — a clean single pulse that
     *  still fires a piston (the same length a vanilla observer emits). */
    private static final int PULSE_GAME_TICKS = 2;

    public static final int DATA_INTERVAL = 0;
    public static final int DATA_COUNT = 1;

    private int intervalSeconds = DEFAULT_SECONDS;
    private int ticksRemaining = DEFAULT_SECONDS * TICKS_PER_SECOND;
    private int pulseTicksLeft;
    private boolean pulsing;

    public RedstoneClockBlockEntity(BlockPos pos, BlockState blockState) {
        super(REDSTONE_CLOCK.get(), pos, blockState);
    }

    private static int clampSeconds(int s) {
        return Math.max(MIN_SECONDS, Math.min(MAX_SECONDS, s));
    }

    public int intervalSeconds() {
        return intervalSeconds;
    }

    /** True only during the brief pulse window — the block reads this for its signal. */
    public boolean isPulsing() {
        return pulsing;
    }

    /** Sets the interval (clamped 1–60); shortening it takes effect on the current cycle. */
    public void setIntervalSeconds(int seconds) {
        int next = clampSeconds(seconds);
        if (next == intervalSeconds) {
            return;
        }
        intervalSeconds = next;
        ticksRemaining = Math.min(ticksRemaining, intervalSeconds * TICKS_PER_SECOND);
        setChanged();
    }

    public ContainerData containerData() {
        return new SplitContainerData(DATA_COUNT, this::dataValue);
    }

    private int dataValue(int index) {
        return index == DATA_INTERVAL ? intervalSeconds : 0;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RedstoneClockBlockEntity clock) {
        clock.tick(level);
    }

    private void tick(Level level) {
        if (pulsing) {
            if (--pulseTicksLeft <= 0) {
                pulsing = false;
                notifyNeighbors(level);   // pulse ends → drop the signal
                setChanged();
            }
            return;
        }
        if (--ticksRemaining <= 0) {
            pulsing = true;
            pulseTicksLeft = PULSE_GAME_TICKS;
            ticksRemaining = intervalSeconds * TICKS_PER_SECOND;
            notifyNeighbors(level);       // pulse starts → raise the signal (silent)
            setChanged();
        }
    }

    /** Recompute redstone on all 6 neighbours; deliberately plays NO sound. */
    private void notifyNeighbors(Level level) {
        level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int windowId, Inventory playerInventory, Player player) {
        return new RedstoneClockMenu(windowId, playerInventory, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("IntervalSeconds", intervalSeconds);
        tag.putInt("Ticks", ticksRemaining);
        tag.putInt("PulseLeft", pulseTicksLeft);
        tag.putBoolean("Pulsing", pulsing);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("IntervalSeconds")) {
            intervalSeconds = clampSeconds(tag.getInt("IntervalSeconds"));
        }
        // A blueprint-baked tag carries only IntervalSeconds → start a fresh cycle.
        ticksRemaining = tag.contains("Ticks")
                ? Math.max(0, tag.getInt("Ticks"))
                : intervalSeconds * TICKS_PER_SECOND;
        pulseTicksLeft = Math.max(0, tag.getInt("PulseLeft"));
        pulsing = tag.getBoolean("Pulsing");
    }
}
