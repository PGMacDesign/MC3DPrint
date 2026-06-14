package com.pgmacdesign.mc3dprint.machine.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Structural block for T5-T8 multiblocks. Breaking one unforms any formed
 * controller whose pattern could include this position. When the controller it
 * belongs to is formed, the casing switches to its glowing ACTIVE appearance
 * and emits light, so the whole base visibly "powers on" together.
 */
public class CasingBlock extends Block {
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public static final EnumProperty<CasingPart> PART = EnumProperty.create("part", CasingPart.class);

    /**
     * Position-aware role each casing's TOP face plays in the formed printer:
     * a BED in the interior, a RAIL along the perimeter edges (oriented N-S or
     * E-W), and a CORNER post at the four corners. NONE is the inventory/inactive
     * default and renders as a plain casing.
     */
    public enum CasingPart implements StringRepresentable {
        NONE,
        BED,
        RAIL_NS,
        RAIL_EW,
        CORNER_NE,
        CORNER_NW,
        CORNER_SE,
        CORNER_SW;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public CasingBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(ACTIVE, false)
                .setValue(PART, CasingPart.NONE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE, PART);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        // Re-flagging a casing's ACTIVE value is the SAME block class, so
        // state.is(newState.getBlock()) is true there and this branch is skipped —
        // it only fires when the casing is genuinely being removed/replaced.
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            ControllerBlock.unformContaining(level, pos);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
