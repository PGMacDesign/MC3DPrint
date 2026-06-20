package com.pgmacdesign.mc3dprint.machine.cable;

import com.pgmacdesign.mc3dprint.config.MC3DPrintConfig;
import com.pgmacdesign.mc3dprint.fu.IFilamentSource;
import com.pgmacdesign.mc3dprint.registry.ModBlockEntities;
import com.pgmacdesign.mc3dprint.registry.ModCapabilities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The MC3D Cable: one block that carries BOTH currencies.
 *
 * <ul>
 *   <li><b>RF</b> — standard Forge Energy, deliberately capped at
 *       {@code config.cable.transferRate} (default 2000 FE/t) so it powers this
 *       mod's machines (and any other mod's FE machines) without ever rivaling a
 *       real power-management mod's backbone. Each tick a cable pulls from its
 *       adjacent extractable sources into a one-tick buffer and distributes that
 *       buffer to every FE acceptor reachable across the connected cable network
 *       — end-to-end in a single tick, no per-block crawl.</li>
 *   <li><b>Filament Units</b> — demand-driven and uncapped. The cable exposes
 *       {@link IFilamentSource}; when a printer drains it, the cable floods its
 *       network and drains the connected Filament Racks in place (down-only tier
 *       rules preserved by the racks themselves).</li>
 * </ul>
 *
 * Each cable independently floods the network each tick, which is O(network) per
 * cable; fine for the small networks a 2000 FE/t cable invites. The flood is
 * deduped by neighbor position so a machine touching two cables is served once.
 */
public class MC3DCableBlockEntity extends BlockEntity implements IFilamentSource {
    private static final int MAX_NETWORK = 4096; // runaway-flood backstop

    private final CableEnergyStorage energy;
    private final LazyOptional<IEnergyStorage> energyCap;
    private final LazyOptional<IFilamentSource> filamentCap = LazyOptional.of(() -> this);

    public MC3DCableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MC3DCABLE.get(), pos, state);
        int rate = MC3DPrintConfig.cableTransferRate();
        this.energy = new CableEnergyStorage(rate);
        this.energyCap = LazyOptional.of(() -> energy);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, MC3DCableBlockEntity cable) {
        cable.transferEnergy(level, pos);
    }

    private void transferEnergy(Level level, BlockPos pos) {
        int rate = MC3DPrintConfig.cableTransferRate();
        // 1) Pull from adjacent, non-cable, extractable sources into our buffer.
        for (Direction dir : Direction.values()) {
            BlockEntity be = level.getBlockEntity(pos.relative(dir));
            if (be == null || be instanceof MC3DCableBlockEntity) {
                continue;
            }
            IEnergyStorage src = be.getCapability(ForgeCapabilities.ENERGY, dir.getOpposite()).orElse(null);
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
        // 2) Distribute our buffer to every reachable acceptor across the network.
        if (energy.getEnergyStored() <= 0) {
            return;
        }
        for (NeighborRef ref : collectNeighbors(level, pos)) {
            if (energy.getEnergyStored() <= 0) {
                break;
            }
            IEnergyStorage acc = ref.be().getCapability(ForgeCapabilities.ENERGY, ref.side()).orElse(null);
            if (acc == null || !acc.canReceive()) {
                continue;
            }
            int accepted = acc.receiveEnergy(energy.getEnergyStored(), false);
            if (accepted > 0) {
                energy.extractEnergy(accepted, false); // maxExtract caps per-tick throughput
            }
        }
    }

    // --- IFilamentSource: drain the racks reachable across the network ---

    @Override
    public long drainFilament(long maxBase, int costTier) {
        if (level == null || maxBase <= 0) {
            return 0;
        }
        long remaining = maxBase;
        long drained = 0;
        for (NeighborRef ref : collectNeighbors(level, worldPosition)) {
            if (remaining <= 0) {
                break;
            }
            IFilamentSource src = ref.be().getCapability(ModCapabilities.FILAMENT_SOURCE, ref.side()).orElse(null);
            if (src == null) {
                continue;
            }
            long got = src.drainFilament(remaining, costTier);
            drained += got;
            remaining -= got;
        }
        return drained;
    }

    @Override
    public long availableFilament(int costTier) {
        if (level == null) {
            return 0;
        }
        long total = 0;
        for (NeighborRef ref : collectNeighbors(level, worldPosition)) {
            IFilamentSource src = ref.be().getCapability(ModCapabilities.FILAMENT_SOURCE, ref.side()).orElse(null);
            if (src != null) {
                total += src.availableFilament(costTier);
            }
        }
        return total;
    }

    /** A non-cable block adjacent to the cable network, and the face it touches. */
    private record NeighborRef(BlockEntity be, Direction side) {}

    /**
     * Flood the connected cable network and collect every adjacent non-cable
     * block entity, deduped by position (the side recorded is whichever cable
     * reached it first — irrelevant for our capability queries since both faces
     * expose the same handler on these machines).
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

    // --- Capabilities ---

    @Override
    @Nonnull
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ENERGY) {
            return energyCap.cast();
        }
        if (cap == ModCapabilities.FILAMENT_SOURCE) {
            return filamentCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        energyCap.invalidate();
        filamentCap.invalidate();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Energy", energy.getEnergyStored());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        energy.setEnergy(tag.getInt("Energy"));
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
