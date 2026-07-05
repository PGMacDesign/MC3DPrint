package com.pgmacdesign.mc3dprint.machine.multiblock;

import com.pgmacdesign.mc3dprint.compat.NbtCompat;

import com.pgmacdesign.mc3dprint.machine.MachineTier;
import com.pgmacdesign.mc3dprint.registry.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
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
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && NbtCompat.getBoolean(data.copyTag(), TAG_COLLAPSED);
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
        if (result.consumesAction() && !level.isClientSide()) {
            reformComponents(level, pos, controller.tier());
            BlockState placed = level.getBlockState(pos);
            if (placed.getBlock() instanceof ControllerBlock) {
                level.setBlock(pos, placed.setValue(ControllerBlock.FORMED, true), Block.UPDATE_ALL);
            }
        }
        return result;
    }

    /**
     * Re-place every base component of a collapsed multiblock around {@code controllerPos}:
     * the tier's premium corner (the T8 Awakened Draconium) at corner offsets, ACTIVE Printer
     * Casing everywhere else. Restoring the corners as themselves — not as casing — is what makes
     * relocate loss-free and refund-free (PGM-48): {@link ControllerBlock} no longer drops the
     * corners on collapse, so they must come back here. {@code cornerBlock} is null for tiers with
     * no premium corner, which re-form as all-casing exactly as before. Static + Level-only so the
     * relocate round-trip is gametest-able without a {@code BlockPlaceContext}.
     */
    public static void reformComponents(Level level, BlockPos controllerPos, MachineTier tier) {
        BlockState activeCasing = ModBlocks.PRINTER_CASING.get().defaultBlockState()
                .setValue(CasingBlock.ACTIVE, true);
        Block cornerBlock = MultiblockPattern.cornerBlock(tier);
        for (BlockPos offset : MultiblockPattern.componentOffsets(tier)) {
            boolean corner = cornerBlock != null && MultiblockPattern.isCorner(offset, tier);
            level.setBlock(controllerPos.offset(offset),
                    corner ? cornerBlock.defaultBlockState() : activeCasing, Block.UPDATE_ALL);
        }
    }

    @Override
    //? if >=1.21.5 {
    /*public void appendHoverText(ItemStack stack, Item.TooltipContext context,
            net.minecraft.world.item.component.TooltipDisplay display,
            java.util.function.Consumer<Component> consumer, TooltipFlag flag) {
        List<Component> tooltip = com.pgmacdesign.mc3dprint.compat.TooltipCompat.sink(consumer);
    *///?} else {
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
    //?}
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
