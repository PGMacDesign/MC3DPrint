package com.pgmacdesign.mc3dprint.fu;

import com.pgmacdesign.mc3dprint.machine.PrinterBlockEntity;
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

import javax.annotation.Nullable;
import java.util.List;

/**
 * A Filament Spool: stores FU, wound by the Filament Winder, attached to
 * printer sides (Shift+Right Click). Capacities follow the design table.
 */
public class SpoolItem extends Item {
    public static final String TAG_FU = "FU";
    public static final int[] CAPACITY_BY_TIER =
            {500, 2_000, 6_000, 20_000, 75_000, 250_000, 1_000_000, 5_000_000};

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

    public static int getFu(ItemStack stack) {
        return stack.getTag() != null ? stack.getTag().getInt(TAG_FU) : 0;
    }

    public static void setFu(ItemStack stack, int fu) {
        if (stack.getItem() instanceof SpoolItem spool) {
            stack.getOrCreateTag().putInt(TAG_FU, Mth.clamp(fu, 0, spool.capacity()));
        }
    }

    /** Adds up to {@code amount} FU; returns how much was actually stored. */
    public static int fill(ItemStack stack, int amount) {
        if (!(stack.getItem() instanceof SpoolItem spool)) {
            return 0;
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
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (printer.attachSpool(context.getItemInHand())) {
            level.playSound(null, context.getClickedPos(), SoundEvents.ITEM_FRAME_ADD_ITEM,
                    SoundSource.BLOCKS, 0.7F, 1.2F);
            return InteractionResult.CONSUME;
        }
        context.getPlayer().displayClientMessage(
                Component.translatable("message.mc3dprint.spool_slots_full"), true);
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
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.mc3dprint.spool_fu", getFu(stack), capacity())
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.mc3dprint.spool_tier", tier)
                .withStyle(ChatFormatting.GRAY));
    }
}
