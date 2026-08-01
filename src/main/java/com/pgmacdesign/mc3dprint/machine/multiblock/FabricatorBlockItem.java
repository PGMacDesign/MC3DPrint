package com.pgmacdesign.mc3dprint.machine.multiblock;

import com.pgmacdesign.mc3dprint.machine.MachineTier;
import com.pgmacdesign.mc3dprint.registry.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Block item for T5-T8 controllers. A "collapsed" stack (from breaking a
 * formed controller) re-forms the entire multiblock on placement: casings are
 * placed automatically and the machine state restores via BlockEntityTag.
 */
public class FabricatorBlockItem extends BlockItem {
    public static final String TAG_COLLAPSED = "Collapsed";

    public FabricatorBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    private static boolean isCollapsed(ItemStack stack) {
        return stack.getTag() != null && stack.getTag().getBoolean(TAG_COLLAPSED);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        ItemStack stack = context.getItemInHand();
        if (!isCollapsed(stack)) {
            return super.place(context);
        }
        ControllerBlock controller = (ControllerBlock) getBlock();
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();

        // every casing position must be free before re-forming
        for (BlockPos offset : MultiblockPattern.componentOffsets(controller.tier())) {
            if (!level.getBlockState(pos.offset(offset)).canBeReplaced()) {
                if (context.getPlayer() != null) {
                    context.getPlayer().displayClientMessage(
                            Component.translatable("message.mc3dprint.reform_blocked"), true);
                }
                return InteractionResult.FAIL;
            }
        }

        InteractionResult result = super.place(context); // also restores BlockEntityTag
        if (result.consumesAction() && !level.isClientSide) {
            reformComponents(level, pos, controller.tier());
            BlockState placed = level.getBlockState(pos);
            if (placed.getBlock() instanceof ControllerBlock) {
                level.setBlock(pos, placed.setValue(ControllerBlock.FORMED, true), Block.UPDATE_ALL);
            }
        }
        return result;
    }

    /**
     * Re-places the base casings of a collapsed multiblock around {@code controllerPos}.
     * Each casing must come back with its formed-look PART (rail / corner post / bed),
     * not just ACTIVE: this path skips the right-click forming flow (which is where
     * setComponentsActive assigns parts), so leaving PART at NONE re-placed a
     * fabricator that glowed but looked unformed until a casing was broken and the
     * machine re-formed by hand. Static + Level-only so the relocate round-trip is
     * gametest-able without a {@code BlockPlaceContext}.
     */
    public static void reformComponents(Level level, BlockPos controllerPos, MachineTier tier) {
        BlockState activeCasing = ModBlocks.PRINTER_CASING.get().defaultBlockState()
                .setValue(CasingBlock.ACTIVE, true);
        for (BlockPos offset : MultiblockPattern.componentOffsets(tier)) {
            level.setBlock(controllerPos.offset(offset),
                    activeCasing.setValue(CasingBlock.PART,
                            ControllerBlock.partForOffset(offset.getX(), offset.getZ(), tier)),
                    Block.UPDATE_ALL);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        if (isCollapsed(stack)) {
            tooltip.add(Component.translatable("tooltip.mc3dprint.collapsed_fabricator")
                    .withStyle(ChatFormatting.AQUA));
        } else {
            int edge = MultiblockPattern.baseEdge(((ControllerBlock) getBlock()).tier());
            tooltip.add(Component.translatable("tooltip.mc3dprint.controller_help", edge, edge)
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
