package com.pgmacdesign.mc3dprint.machine.cable;

import com.pgmacdesign.mc3dprint.registry.ModBlockEntities;
import com.pgmacdesign.mc3dprint.registry.ModCapabilities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.Map;

/**
 * The MC3D Cable block: a vanilla-fence-style connected block whose six side
 * booleans drive a multipart model. It connects to other cables and to any
 * block exposing Forge Energy or our {@link ModCapabilities#FILAMENT_SOURCE} on
 * the touched face — so it auto-attaches to printers, racks, generators, and any
 * other mod's FE machines. All transport lives in {@link MC3DCableBlockEntity}.
 */
public class MC3DCableBlock extends BaseEntityBlock {
    public static final BooleanProperty NORTH = BooleanProperty.create("north");
    public static final BooleanProperty SOUTH = BooleanProperty.create("south");
    public static final BooleanProperty EAST = BooleanProperty.create("east");
    public static final BooleanProperty WEST = BooleanProperty.create("west");
    public static final BooleanProperty UP = BooleanProperty.create("up");
    public static final BooleanProperty DOWN = BooleanProperty.create("down");

    private static final Map<Direction, BooleanProperty> BY_DIRECTION = new EnumMap<>(Direction.class);

    static {
        BY_DIRECTION.put(Direction.NORTH, NORTH);
        BY_DIRECTION.put(Direction.SOUTH, SOUTH);
        BY_DIRECTION.put(Direction.EAST, EAST);
        BY_DIRECTION.put(Direction.WEST, WEST);
        BY_DIRECTION.put(Direction.UP, UP);
        BY_DIRECTION.put(Direction.DOWN, DOWN);
    }

    private static final VoxelShape CORE = Block.box(5, 5, 5, 11, 11, 11);
    private static final Map<Direction, VoxelShape> ARM = new EnumMap<>(Direction.class);

    static {
        ARM.put(Direction.DOWN, Block.box(5, 0, 5, 11, 5, 11));
        ARM.put(Direction.UP, Block.box(5, 11, 5, 11, 16, 11));
        ARM.put(Direction.NORTH, Block.box(5, 5, 0, 11, 11, 5));
        ARM.put(Direction.SOUTH, Block.box(5, 5, 11, 11, 11, 16));
        ARM.put(Direction.WEST, Block.box(0, 5, 5, 5, 11, 11));
        ARM.put(Direction.EAST, Block.box(11, 5, 5, 16, 11, 11));
    }

    private final VoxelShape[] shapeByMask = new VoxelShape[64];

    public MC3DCableBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(NORTH, false).setValue(SOUTH, false)
                .setValue(EAST, false).setValue(WEST, false)
                .setValue(UP, false).setValue(DOWN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, SOUTH, EAST, WEST, UP, DOWN);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MC3DCableBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.MC3DCABLE.get(), MC3DCableBlockEntity::serverTick);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = defaultBlockState();
        for (Direction dir : Direction.values()) {
            state = state.setValue(BY_DIRECTION.get(dir), canConnectTo(level, pos.relative(dir), dir));
        }
        return state;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return state.setValue(BY_DIRECTION.get(direction), canConnectTo(level, neighborPos, direction));
    }

    /** True if the block at {@code neighborPos} (reached by going {@code dirToNeighbor}) is a cable
     *  or exposes energy / filament on its facing side. */
    private static boolean canConnectTo(BlockGetter getter, BlockPos neighborPos, Direction dirToNeighbor) {
        BlockState neighbor = getter.getBlockState(neighborPos);
        if (neighbor.getBlock() instanceof MC3DCableBlock) {
            return true;
        }
        // Cosmetic tag: blocks that ride the cable graph without exposing energy or filament
        // (e.g. the Filament Tier Item Sorter) still get an arm rendered toward them.
        if (neighbor.is(com.pgmacdesign.mc3dprint.registry.ModBlockTags.CABLE_CONNECTABLE)) {
            return true;
        }
        BlockEntity be = getter.getBlockEntity(neighborPos);
        if (be == null) {
            return false;
        }
        Direction face = dirToNeighbor.getOpposite();
        return be.getCapability(ForgeCapabilities.ENERGY, face).isPresent()
                || be.getCapability(ModCapabilities.FILAMENT_SOURCE, face).isPresent();
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state);
    }

    private VoxelShape shapeFor(BlockState state) {
        int mask = 0;
        for (Direction dir : Direction.values()) {
            if (state.getValue(BY_DIRECTION.get(dir))) {
                mask |= 1 << dir.ordinal();
            }
        }
        VoxelShape cached = shapeByMask[mask];
        if (cached != null) {
            return cached;
        }
        VoxelShape shape = CORE;
        for (Direction dir : Direction.values()) {
            if ((mask & (1 << dir.ordinal())) != 0) {
                shape = Shapes.or(shape, ARM.get(dir));
            }
        }
        shapeByMask[mask] = shape;
        return shape;
    }
}
