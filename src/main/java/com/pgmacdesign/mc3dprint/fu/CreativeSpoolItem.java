package com.pgmacdesign.mc3dprint.fu;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Creative-only spool: behaves as a Tier 8 spool that is always full and never
 * depletes. No recipe — obtainable only from the creative menu.
 */
public class CreativeSpoolItem extends SpoolItem {

    public CreativeSpoolItem(Properties properties) {
        super(8, properties);
    }

    @Override
    public boolean creative() {
        return true;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return false;
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
        tooltip.add(Component.translatable("tooltip.mc3dprint.spool_creative")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("tooltip.mc3dprint.spool_tier", 8)
                .withStyle(ChatFormatting.GRAY));
    }
}
