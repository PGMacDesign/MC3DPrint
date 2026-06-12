package com.pgmacdesign.mc3dprint.machine.multiblock;

import com.pgmacdesign.mc3dprint.machine.MachineTier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Structural block for T5-T8 multiblocks. Breaking one unforms any formed
 * controller whose pattern could include this position. When the controller it
 * belongs to is formed, the casing switches to its glowing ACTIVE appearance
 * and emits light, so the whole base visibly "powers on" together.
 */
public class CasingBlock extends Block {
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public CasingBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        // Re-flagging a casing's ACTIVE value is the SAME block class, so
        // state.is(newState.getBlock()) is true there and this branch is skipped —
        // it only fires when the casing is genuinely being removed/replaced.
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
                            // exclude this very block: it is mid-removal, so re-setting
                            // its ACTIVE state would resurrect it.
                            ControllerBlock.unform(level, candidate, pos);
                        }
                    }
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
