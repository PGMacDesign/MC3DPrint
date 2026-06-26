package com.pgmacdesign.mc3dprint.machine.cable;

import com.pgmacdesign.mc3dprint.compat.NbtCompat;

import com.pgmacdesign.mc3dprint.config.MC3DPrintConfig;
import com.pgmacdesign.mc3dprint.fu.IFilamentSource;
import com.pgmacdesign.mc3dprint.registry.ModBlockEntities;
import com.pgmacdesign.mc3dprint.registry.ModCapabilities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The MC3D Cable: one block that carries BOTH currencies.
 *
 * <ul>
 *   <li><b>RF</b> — standard Forge Energy, capped at {@code config.cable.transferRate}
 *       (default 2000 FE/t) so it powers this mod's machines and any other mod's
 *       FE machines without rivaling a real power backbone. Each tick a cable
 *       pulls from its adjacent extractable sources and pushes to the FE
 *       acceptors across its network.</li>
 *   <li><b>Filament Units</b> — demand-driven. The cable exposes
 *       {@link IFilamentSource}; a printer flattens the reachable racks via
 *       {@link #collectSources} and sweeps tier bands across them.</li>
 * </ul>
 *
 * <p><b>Throttled topology cache.</b> Flooding the cable graph to discover which
 * racks/machines are reachable is the only expensive part, so membership is
 * recomputed at most once every {@link #RECOMPUTE_INTERVAL} ticks. The cache
 * stores only <em>positions</em> (topology); spool contents and energy levels are
 * always read live at use time, so a spool draining or a rack refilling needs no
 * invalidation. The trade-off is bounded staleness: a rack added or removed mid-
 * window is seen within ~5 seconds (a freshly broken one is skipped immediately
 * via an isRemoved check). Direct-touch (no cable) is never throttled — printers
 * scan their own neighbors live.
 */
public class MC3DCableBlockEntity extends BlockEntity implements IFilamentSource {
    private static final int MAX_NETWORK = 4096;   // runaway-flood backstop
    private static final int RECOMPUTE_INTERVAL = 100; // ticks between membership refloods (~5s)

    private final CableEnergyStorage energy;

    // Cached network membership (positions only) — recomputed on the throttle.
    private Map<BlockPos, Direction> sourceFaces;   // reachable Filament-Unit sources (racks)
    private Map<BlockPos, Direction> energyFaces;   // reachable FE acceptors
    private long lastRecompute = Long.MIN_VALUE;

    public MC3DCableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MC3DCABLE.get(), pos, state);
        int rate = MC3DPrintConfig.cableTransferRate();
        this.energy = new CableEnergyStorage(rate);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, MC3DCableBlockEntity cable) {
        cable.transferEnergy(level, pos);
    }

    /** Refloods membership at most once per {@link #RECOMPUTE_INTERVAL} ticks. */
    private void ensureFresh() {
        if (level == null) {
            return;
        }
        long now = level.getGameTime();
        if (sourceFaces != null && now - lastRecompute < RECOMPUTE_INTERVAL) {
            return;
        }
        Map<BlockPos, Direction> sources = new LinkedHashMap<>();
        Map<BlockPos, Direction> acceptors = new LinkedHashMap<>();
        for (NeighborRef ref : collectNeighbors(level, worldPosition)) {
            BlockPos pos = ref.be().getBlockPos();
            Direction face = ref.side();
            if (level.getCapability(ModCapabilities.FILAMENT_SOURCE, pos, face) != null) {
                sources.put(pos, face);
            }
            IEnergyStorage e = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, face);
            if (e != null && e.canReceive()) {
                acceptors.put(pos, face);
            }
        }
        this.sourceFaces = sources;
        this.energyFaces = acceptors;
        this.lastRecompute = now;
    }

    private void transferEnergy(Level level, BlockPos pos) {
        int rate = MC3DPrintConfig.cableTransferRate();
        // 1) Pull from adjacent, non-cable, extractable sources (always live — responsiveness).
        // TODO(PGM-13 / decision 5.2): optimize to BlockCapabilityCache
        for (Direction dir : Direction.values()) {
            BlockPos neighbourPos = pos.relative(dir);
            BlockEntity be = level.getBlockEntity(neighbourPos);
            if (be == null || be instanceof MC3DCableBlockEntity) {
                continue;
            }
            IEnergyStorage src = level.getCapability(Capabilities.EnergyStorage.BLOCK, neighbourPos, dir.getOpposite());
            if (src == null || !src.canExtract()) {
                continue;
            }
            int room = energy.receiveEnergy(rate, true);
            if (room <= 0) {
                break;
            }
            int pulled = src.extractEnergy(room, false);
            if (pulled > 0) {
                energy.receiveEnergy(pulled, false);
            }
        }
        // 2) Push to cached network acceptors (membership throttled; energy moves every tick).
        if (energy.getEnergyStored() <= 0) {
            return;
        }
        ensureFresh();
        for (Map.Entry<BlockPos, Direction> entry : energyFaces.entrySet()) {
            if (energy.getEnergyStored() <= 0) {
                break;
            }
            BlockEntity be = level.getBlockEntity(entry.getKey());
            if (be == null || be.isRemoved()) {
                continue;
            }
            IEnergyStorage acc = level.getCapability(Capabilities.EnergyStorage.BLOCK, entry.getKey(), entry.getValue());
            if (acc == null || !acc.canReceive()) {
                continue;
            }
            int accepted = acc.receiveEnergy(energy.getEnergyStored(), false);
            if (accepted > 0) {
                energy.extractEnergy(accepted, false); // maxExtract caps per-tick throughput
            }
        }
    }

    // --- IFilamentSource: the cable's racks are its leaf sources ---

    @Override
    public void collectSources(Set<IFilamentSource> out) {
        if (level == null) {
            return;
        }
        ensureFresh();
        for (Map.Entry<BlockPos, Direction> entry : sourceFaces.entrySet()) {
            BlockEntity be = level.getBlockEntity(entry.getKey());
            if (be == null || be.isRemoved()) {
                continue;
            }
            IFilamentSource src = level.getCapability(ModCapabilities.FILAMENT_SOURCE, entry.getKey(), entry.getValue());
            if (src != null) {
                out.add(src);
            }
        }
    }

    @Override
    public long drainExactTier(int tier, long maxBase) {
        if (level == null || maxBase <= 0) {
            return 0;
        }
        long drained = 0;
        long remaining = maxBase;
        for (IFilamentSource src : reachableSources()) {
            if (remaining <= 0) {
                break;
            }
            long got = src.drainExactTier(tier, remaining);
            drained += got;
            remaining -= got;
        }
        return drained;
    }

    @Override
    public long availableExactTier(int tier) {
        if (level == null) {
            return 0;
        }
        long total = 0;
        for (IFilamentSource src : reachableSources()) {
            total += src.availableExactTier(tier);
        }
        return total;
    }

    private Set<IFilamentSource> reachableSources() {
        Set<IFilamentSource> set = Collections.newSetFromMap(new IdentityHashMap<>());
        collectSources(set);
        return set;
    }

    /** A non-cable block adjacent to the cable network, and the face it touches. */
    private record NeighborRef(BlockEntity be, Direction side) {}

    /**
     * Flood the connected cable network and collect every adjacent non-cable
     * block entity, deduped by position. Only called on the throttle.
     */
    private static List<NeighborRef> collectNeighbors(Level level, BlockPos start) {
        Set<BlockPos> cables = new HashSet<>();
        Map<BlockPos, NeighborRef> neighbors = new LinkedHashMap<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        cables.add(start);
        queue.add(start);
        int guard = 0;
        while (!queue.isEmpty() && guard++ < MAX_NETWORK) {
            BlockPos p = queue.poll();
            for (Direction dir : Direction.values()) {
                BlockPos np = p.relative(dir);
                BlockEntity be = level.getBlockEntity(np);
                if (be instanceof MC3DCableBlockEntity) {
                    if (cables.add(np)) {
                        queue.add(np);
                    }
                } else if (be != null) {
                    neighbors.putIfAbsent(np, new NeighborRef(be, dir.getOpposite()));
                }
            }
        }
        return new ArrayList<>(neighbors.values());
    }

    // --- Capabilities (exposed raw; registered centrally in ModCapabilities) ---

    public IEnergyStorage getEnergyStorage() {
        return energy;
    }

    public IFilamentSource getFilamentSource() {
        return this;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", energy.getEnergyStored());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy.setEnergy(NbtCompat.getInt(tag, "Energy"));
    }

    /** Cable buffer: one tick's worth, freely receivable and extractable. */
    private static final class CableEnergyStorage extends EnergyStorage {
        CableEnergyStorage(int rate) {
            super(rate, rate, rate);
        }

        void setEnergy(int value) {
            this.energy = Math.max(0, Math.min(capacity, value));
        }
    }
}
