package com.pgmacdesign.mc3dprint.machine;

import com.pgmacdesign.mc3dprint.compat.TransferCompat;
import com.pgmacdesign.mc3dprint.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * Creative-only infinite RF source: every tick it offers unlimited energy to
 * all six neighbors, and exposes an always-full extract-only energy capability
 * for cables that pull. No recipe — creative menu only.
 */
public class CreativeEnergyBlockEntity extends BlockEntity {

    private final IEnergyStorage energy = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return 0;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return maxExtract;
        }

        @Override
        public int getEnergyStored() {
            return Integer.MAX_VALUE;
        }

        @Override
        public int getMaxEnergyStored() {
            return Integer.MAX_VALUE;
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

    public CreativeEnergyBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.CREATIVE_ENERGY_SOURCE.get(), pos, blockState);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CreativeEnergyBlockEntity source) {
        for (Direction direction : Direction.values()) {
            IEnergyStorage handler = TransferCompat.findEnergy(level,
                    pos.relative(direction), direction.getOpposite());
            if (handler != null) {
                handler.receiveEnergy(Integer.MAX_VALUE, false);
            }
        }
    }

    // --- Capabilities (exposed raw; registered centrally in ModCapabilities) ---

    public IEnergyStorage getEnergyStorage() {
        return energy;
    }
}
