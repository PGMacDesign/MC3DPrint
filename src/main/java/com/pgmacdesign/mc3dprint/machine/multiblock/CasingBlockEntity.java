package com.pgmacdesign.mc3dprint.machine.multiblock;

import com.pgmacdesign.mc3dprint.machine.MachineTier;
import com.pgmacdesign.mc3dprint.machine.PrinterBlockEntity;
import com.pgmacdesign.mc3dprint.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;

import javax.annotation.Nullable;

/**
 * Makes a formed multiblock's casing a power inlet: it re-exposes the controller's
 * Forge Energy capability, so a cable (ours or any other mod's FE conduit) plugged
 * into <em>any</em> casing of the fabricator feeds the buried controller. Filament
 * is handled separately — the controller pulls from the whole pad's perimeter (see
 * {@code PrinterBlockEntity.reachableSources}).
 *
 * <p>Holds no state of its own; it just forwards to the controller it belongs to,
 * located lazily (cached, revalidated cheaply) by scanning for a formed controller
 * whose footprint contains this casing.
 */
public class CasingBlockEntity extends BlockEntity {
    @Nullable
    private BlockPos controllerPos;

    public CasingBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CASING.get(), pos, state);
    }

    /**
     * The formed controller this casing belongs to, or {@code null} when it isn't part of a
     * formed multiblock. Public because a casing has to stand in for the controller for any
     * interaction aimed at "the machine": on a T8 pad the controller is 1 block of 81, so
     * requiring a click on it exactly is not a usable interface (see the scanner's Deconstruct
     * region hand-off, which used to fall through to setting a scanner corner instead).
     */
    @Nullable
    public PrinterBlockEntity printerController() {
        return controller() instanceof PrinterBlockEntity printer ? printer : null;
    }

    @Nullable
    private BlockEntity controller() {
        if (level == null) {
            return null;
        }
        if (controllerPos != null) { // cached — revalidate cheaply
            if (isFormedController(controllerPos)) {
                return level.getBlockEntity(controllerPos);
            }
            controllerPos = null;
        }
        int maxHalf = MultiblockPattern.baseEdge(MachineTier.T8) / 2;
        for (int dx = -maxHalf; dx <= maxHalf; dx++) {
            for (int dz = -maxHalf; dz <= maxHalf; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                BlockPos candidate = worldPosition.offset(dx, 0, dz);
                BlockState state = level.getBlockState(candidate);
                if (state.getBlock() instanceof ControllerBlock controller
                        && state.getValue(ControllerBlock.FORMED)) {
                    int half = MultiblockPattern.baseEdge(controller.tier()) / 2;
                    if (Math.abs(dx) <= half && Math.abs(dz) <= half) {
                        controllerPos = candidate;
                        return level.getBlockEntity(candidate);
                    }
                }
            }
        }
        return null;
    }

    private boolean isFormedController(BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof ControllerBlock && state.getValue(ControllerBlock.FORMED);
    }

    // --- Capabilities (exposed raw; registered centrally in ModCapabilities) ---

    /**
     * Re-exposes the formed controller's energy storage so a cable plugged into any
     * casing feeds the buried controller. {@code null} when this casing isn't part
     * of a formed multiblock.
     */
    @Nullable
    public IEnergyStorage getEnergyStorage(@Nullable Direction side) {
        PrinterBlockEntity controller = printerController();
        return controller != null ? controller.getEnergyStorage() : null;
    }
}
