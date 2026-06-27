package com.pgmacdesign.mc3dprint.compat;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.function.Supplier;

/**
 * A {@link BlockItem} that carries a one-line hover tooltip. 1.21.5 removed
 * {@code Block.appendHoverText} entirely (tooltips live on {@code Item} only), so
 * blocks that used to describe themselves must do it from their item instead.
 * Using this on BOTH versions keeps one mechanism — the line renders identically
 * to the old block tooltip (on 1.21.1 {@code BlockItem} delegated to the block,
 * which now no longer overrides it). The {@code line} is a {@link Supplier} so
 * config-derived text (e.g. the clock generator's RF rate) reads live.
 */
public class TooltipBlockItem extends BlockItem {
    private final Supplier<Component> line;

    public TooltipBlockItem(Block block, Properties properties, Supplier<Component> line) {
        super(block, properties);
        this.line = line;
    }

    @Override
    //? if >=1.21.5 {
    /*public void appendHoverText(ItemStack stack, Item.TooltipContext context,
            net.minecraft.world.item.component.TooltipDisplay display,
            java.util.function.Consumer<Component> consumer, TooltipFlag flag) {
        consumer.accept(line.get());
    *///?} else {
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
            java.util.List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(line.get());
    //?}
    }
}
