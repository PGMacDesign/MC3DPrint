package com.pgmacdesign.mc3dprint.machine.multiblock;

import com.pgmacdesign.mc3dprint.machine.MachineTier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Dumb structural block for T5-T8 multiblocks. Breaking one unforms any
 * formed controller whose pattern could include this position.
 */
public class CasingBlock extends Block {

    public CasingBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            // largest pattern is 9x9 -> controller within 4 blocks on the same Y
            int maxHalf = MultiblockPattern.baseEdge(MachineTier.T8) / 2;
            for (int dx = -maxHalf; dx <= maxHalf; dx++) {
                for (int dz = -maxHalf; dz <= maxHalf; dz++) {
                    BlockPos candidate = pos.offset(dx, 0, dz);
                    BlockState candidateState = level.getBlockState(candidate);
                    if (candidateState.getBlock() instanceof ControllerBlock controller
                            && candidateState.getValue(ControllerBlock.FORMED)) {
                        int half = MultiblockPattern.baseEdge(controller.tier()) / 2;
                        if (Math.abs(dx) <= half && Math.abs(dz) <= half) {
                            ControllerBlock.unform(level, candidate);
                        }
                    }
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
