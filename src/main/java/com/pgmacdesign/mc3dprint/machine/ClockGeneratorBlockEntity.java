package com.pgmacdesign.mc3dprint.machine;

import com.pgmacdesign.mc3dprint.config.MC3DPrintConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static com.pgmacdesign.mc3dprint.registry.ModBlockEntities.CLOCK_GENERATOR;

/**
 * Clock Generator: free trickle power so the mod is usable without any other
 * RF mod installed. Generates a configurable amount of RF per tick (default 10)
 * with no fuel, pushes it to adjacent machines, and exposes an extract-only
 * energy capability for cables.
 */
public class ClockGeneratorBlockEntity extends BlockEntity {
    /** Buffer holds this many ticks of generation so brief disconnects don't void RF. */
    private static final int BUFFER_TICKS = 200;

    private int stored;

    private final IEnergyStorage energy = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return 0;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            int extracted = Math.min(maxExtract, stored);
            if (extracted > 0 && !simulate) {
                stored -= extracted;
                setChanged();
            }
            return extracted;
        }

        @Override
        public int getEnergyStored() {
            return stored;
        }

        @Override
        public int getMaxEnergyStored() {
            return capacity();
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return false;
        }
    };
    private final LazyOptional<IEnergyStorage> energyCap = LazyOptional.of(() -> energy);

    public ClockGeneratorBlockEntity(BlockPos pos, BlockState blockState) {
        super(CLOCK_GENERATOR.get(), pos, blockState);
    }

    public static int ratePerTick() {
        return MC3DPrintConfig.CLOCK_GENERATOR_RF_PER_TICK.get();
    }

    private static int capacity() {
        return ratePerTick() * BUFFER_TICKS;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ClockGeneratorBlockEntity generator) {
        generator.tick(level, pos);
    }

    private void tick(Level level, BlockPos pos) {
        int before = stored;
        stored = Math.min(stored + ratePerTick(), capacity());

        for (Direction direction : Direction.values()) {
            if (stored <= 0) {
                break;
            }
            BlockEntity neighbor = level.getBlockEntity(pos.relative(direction));
            if (neighbor == null) {
                continue;
            }
            IEnergyStorage handler = neighbor.getCapability(ForgeCapabilities.ENERGY,
                    direction.getOpposite()).orElse(null);
            if (handler != null && handler.canReceive()) {
                stored -= handler.receiveEnergy(stored, false);
            }
        }

        if (stored != before) {
            setChanged();
        }
    }

    @Override
    @Nonnull
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ENERGY) {
            return energyCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        energyCap.invalidate();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Energy", stored);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        stored = Math.max(0, tag.getInt("Energy"));
    }
}
