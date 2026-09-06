package com.pgmacdesign.mc3dprint.machine.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;

import javax.annotation.Nullable;

/**
 * Structural block for T5-T8 multiblocks. Breaking one unforms any formed
 * controller whose pattern could include this position. When the controller it
 * belongs to is formed, the casing switches to its glowing ACTIVE appearance
 * and emits light, so the whole base visibly "powers on" together.
 */
public class CasingBlock extends Block implements EntityBlock {
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

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CasingBlockEntity(pos, state);
    }

    // Re-flagging a casing's ACTIVE value is the SAME block class, so
    // state.is(newState.getBlock()) is true there and this branch is skipped —
    // it only fires when the casing is genuinely being removed/replaced.
    // 1.21.5 replaced onRemove(state,level,pos,newState,isMoving) with
    // affectNeighborsAfterRemoval(state,serverLevel,pos,movedByPiston), called
    // only on real removal (server-side), so both guards collapse away there.
    //? if >=1.21.5 {
    /*@Override
    protected void affectNeighborsAfterRemoval(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, boolean movedByPiston) {
        ControllerBlock.unformContaining(level, pos);
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }
    *///?} else {
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!level.isClientSide() && !state.is(newState.getBlock())) {
            ControllerBlock.unformContaining(level, pos);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
    //?}

    // --- Redstone Module output, relayed from the controller ---
    //
    // A formed pad buries its controller: on a T8 it is one block of 81, with only its top and
    // bottom faces exposed, and a real build usually has both of those occupied. Emitting only
    // from the controller therefore left players with nowhere to take the signal from. Casings
    // already stand in for the machine for power and for the scanner, so they stand in here too:
    // the signal can be read off any face of the structure.
    //
    // The state lives on the controller, not here. A casing holds no emission of its own, so the
    // controller pokes every casing's neighbours when its emission flips (see
    // PrinterBlockEntity.notifyCasingsOfEmission), which is what makes the signal turn back OFF.

    @Override
    @SuppressWarnings("deprecation")
    public boolean isSignalSource(BlockState state) {
        // State-free, because the answer depends on the controller rather than on this block, and
        // this overload gets no world access. Dust therefore connects to a casing even while the
        // machine is idle, which is what you want when wiring the thing up.
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getSignal(BlockState state, net.minecraft.world.level.BlockGetter level,
                         net.minecraft.core.BlockPos pos, net.minecraft.core.Direction direction) {
        return emittingController(level, pos) ? 15 : 0;
    }

    /** True when this casing belongs to a formed machine whose Redstone Module is emitting. */
    private static boolean emittingController(net.minecraft.world.level.BlockGetter level,
                                              net.minecraft.core.BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof CasingBlockEntity casing)) {
            return false;
        }
        com.pgmacdesign.mc3dprint.machine.PrinterBlockEntity controller = casing.printerController();
        if (controller == null) {
            return false;
        }
        BlockState controllerState = controller.getBlockState();
        return controllerState.hasProperty(
                        com.pgmacdesign.mc3dprint.machine.PrinterBlock.EMITTING)
                && controllerState.getValue(
                        com.pgmacdesign.mc3dprint.machine.PrinterBlock.EMITTING);
    }
}
