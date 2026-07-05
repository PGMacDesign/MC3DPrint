package com.pgmacdesign.mc3dprint.machine;

import com.pgmacdesign.mc3dprint.compat.TransferCompat;
import net.neoforged.neoforge.energy.EnergyStorage;

/**
 * RF buffer for machines: receive-only from the outside (cables push in,
 * nothing pulls out), drained internally by the machine itself.
 */
public class MachineEnergyStorage extends EnergyStorage implements TransferCompat.RawEnergy {
    private final Runnable onChanged;

    public MachineEnergyStorage(int capacity, int maxReceive, Runnable onChanged) {
        super(capacity, maxReceive, 0);
        this.onChanged = onChanged;
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        int received = super.receiveEnergy(maxReceive, simulate);
        if (received > 0 && !simulate) {
            onChanged.run();
        }
        return received;
    }

    /** Internal drain, bypassing the external maxExtract=0 restriction. */
    public int consume(int amount) {
        int drained = Math.min(amount, energy);
        if (drained > 0) {
            energy -= drained;
            onChanged.run();
        }
        return drained;
    }

    public boolean hasAtLeast(int amount) {
        return energy >= amount;
    }

    public void setStored(int amount) {
        energy = Math.max(0, Math.min(amount, capacity));
    }

    /** Buffer upgrades resize the capacity at runtime; stored energy is clamped. */
    public void setCapacity(int newCapacity) {
        capacity = Math.max(1, newCapacity);
        energy = Math.min(energy, capacity);
    }

    @Override
    public int rawEnergy() {
        return this.energy;
    }

    @Override
    public void rawEnergy(int value) {
        this.energy = value;
    }
}
