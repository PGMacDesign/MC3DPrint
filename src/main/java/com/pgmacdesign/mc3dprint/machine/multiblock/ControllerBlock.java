package com.pgmacdesign.mc3dprint.machine.multiblock;

import com.pgmacdesign.mc3dprint.machine.MachineTier;
import com.pgmacdesign.mc3dprint.machine.PrinterBlock;
import com.pgmacdesign.mc3dprint.machine.PrinterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * T5-T8 multiblock controller. Unformed: right-click validates the casing
 * pattern and forms the machine. Formed: behaves as a printer of its tier.
 *
 * Breaking a formed controller collapses the whole multiblock into a single
 * item carrying the full machine state ("build once, relocate freely").
 */
public class ControllerBlock extends PrinterBlock {
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    public ControllerBlock(MachineTier tier, Properties properties) {
        super(tier, properties);
        registerDefaultState(stateDefinition.any().setValue(FORMED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FORMED);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (state.getValue(FORMED)) {
            return super.use(state, level, pos, player, hand, hit);
        }
        if (!level.isClientSide) {
            Component error = MultiblockPattern.validate(level, pos, tier());
            if (error != null) {
                player.displayClientMessage(error, true);
            } else {
                level.setBlock(pos, state.setValue(FORMED, true), Block.UPDATE_ALL);
                player.displayClientMessage(Component.translatable("message.mc3dprint.multiblock_formed",
                        tier().number()), true);
                level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.6F, 1.2F);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && state.getValue(FORMED)
                && level.getBlockEntity(pos) instanceof PrinterBlockEntity printer) {
            printer.cancelActiveJob();
            printer.markCollapsing();

            ItemStack collapsed = new ItemStack(this);
            collapsed.getOrCreateTag().putBoolean(FabricatorBlockItem.TAG_COLLAPSED, true);
            collapsed.getOrCreateTag().put("BlockEntityTag", printer.saveWithoutMetadata());

            for (BlockPos offset : MultiblockPattern.componentOffsets(tier())) {
                BlockPos componentPos = pos.offset(offset);
                if (isOwnComponent(level, componentPos)) {
                    level.removeBlock(componentPos, false);
                }
            }
            if (!player.getAbilities().instabuild) {
                level.addFreshEntity(new ItemEntity(level,
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, collapsed));
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    private boolean isOwnComponent(Level level, BlockPos pos) {
        Block block = level.getBlockState(pos).getBlock();
        return block instanceof CasingBlock || (tier() == MachineTier.T8 && !(block instanceof ControllerBlock)
                && !level.getBlockState(pos).isAir() && isAwakenedCorner(level, pos));
    }

    private boolean isAwakenedCorner(Level level, BlockPos pos) {
        // T8 corners are DE blocks — only remove them if DE is actually present
        var key = net.minecraftforge.registries.ForgeRegistries.BLOCKS
                .getKey(level.getBlockState(pos).getBlock());
        return key != null && key.getNamespace().equals(MultiblockPattern.DRACONIC_MOD_ID);
    }

    /** Called by casings when one is broken — the machine unforms in place. */
    public static void unform(Level level, BlockPos controllerPos) {
        BlockState state = level.getBlockState(controllerPos);
        if (state.getBlock() instanceof ControllerBlock && state.getValue(FORMED)) {
            if (level.getBlockEntity(controllerPos) instanceof PrinterBlockEntity printer) {
                printer.cancelActiveJob();
            }
            level.setBlock(controllerPos, state.setValue(FORMED, false), Block.UPDATE_ALL);
            level.playSound(null, controllerPos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.6F, 1.0F);
        }
    }
}
