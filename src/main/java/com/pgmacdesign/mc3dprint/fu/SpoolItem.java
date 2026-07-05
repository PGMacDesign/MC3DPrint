package com.pgmacdesign.mc3dprint.fu;

import com.pgmacdesign.mc3dprint.machine.PrinterBlockEntity;
import com.pgmacdesign.mc3dprint.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * A Filament Spool: stores FU, wound by the Filament Winder, attached to
 * printer sides (Shift+Right Click). Every tier holds a uniform 100,000 FU —
 * the spool's tier gates which materials it accepts, not how much it stores.
 */
public class SpoolItem extends Item {
    public static final String TAG_FU = "FU";
    public static final int SPOOL_CAPACITY = 100_000;
    // One entry per tier (T1–T8); the array length is the canonical tier count.
    public static final int[] CAPACITY_BY_TIER =
            {SPOOL_CAPACITY, SPOOL_CAPACITY, SPOOL_CAPACITY, SPOOL_CAPACITY,
             SPOOL_CAPACITY, SPOOL_CAPACITY, SPOOL_CAPACITY, SPOOL_CAPACITY};

    private final int tier; // 1-based

    public SpoolItem(int tier, Properties properties) {
        super(properties.stacksTo(1));
        this.tier = tier;
    }

    public int tier() {
        return tier;
    }

    public int capacity() {
        return CAPACITY_BY_TIER[tier - 1];
    }

    /** Creative spools are always full and never deplete. */
    public boolean creative() {
        return false;
    }

    public static int getFu(ItemStack stack) {
        if (stack.getItem() instanceof SpoolItem spool && spool.creative()) {
            return spool.capacity();
        }
        return stack.getOrDefault(ModDataComponents.FU.get(), 0);
    }

    public static void setFu(ItemStack stack, int fu) {
        if (stack.getItem() instanceof SpoolItem spool && !spool.creative()) {
            stack.set(ModDataComponents.FU.get(), Mth.clamp(fu, 0, spool.capacity()));
        }
    }

    /** Adds up to {@code amount} FU; returns how much was actually stored. */
    public static int fill(ItemStack stack, int amount) {
        if (!(stack.getItem() instanceof SpoolItem spool) || spool.creative()) {
            return 0; // creative spools report no room, so winders never wind into them
        }
        int current = getFu(stack);
        int added = Math.min(amount, spool.capacity() - current);
        if (added > 0) {
            setFu(stack, current + added);
        }
        return added;
    }

    /** Drains up to {@code amount} FU; returns how much was actually drained. */
    public static int drain(ItemStack stack, int amount) {
        if (stack.getItem() instanceof SpoolItem spool && spool.creative()) {
            return amount;
        }
        int current = getFu(stack);
        int drained = Math.min(amount, current);
        if (drained > 0) {
            setFu(stack, current - drained);
        }
        return drained;
    }

    /** Shift+Right Click a printer to dock the spool (sides are spool faces by design). */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() == null || !context.getPlayer().isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }
        Level level = context.getLevel();
        if (!(level.getBlockEntity(context.getClickedPos()) instanceof PrinterBlockEntity printer)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (printer.attachSpool(context.getItemInHand())) {
            level.playSound(null, context.getClickedPos(), SoundEvents.ITEM_FRAME_ADD_ITEM,
                    SoundSource.BLOCKS, 0.7F, 1.2F);
            return InteractionResult.CONSUME;
        }
        com.pgmacdesign.mc3dprint.compat.MsgCompat.actionBar(context.getPlayer(), 
                Component.translatable("message.mc3dprint.spool_slots_full"));
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return getFu(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * getFu(stack) / capacity());
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x4FC3F7;
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
        tooltip.add(Component.translatable("tooltip.mc3dprint.spool_fu", getFu(stack), capacity())
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.mc3dprint.spool_tier", tier)
                .withStyle(ChatFormatting.GRAY));
        if (tier > 1 && FuConversion.ratio() > 1) {
            tooltip.add(Component.translatable("tooltip.mc3dprint.spool_worth",
                            FuConversion.unitWorth(tier, FuConversion.ratio()))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
