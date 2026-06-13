package com.pgmacdesign.mc3dprint.machine.upgrade;

import com.pgmacdesign.mc3dprint.machine.PrinterBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Printer expansion modules. Modifiers stack multiplicatively per the design
 * doc ("prevents runaway values"). Install: Shift+Right Click a printer.
 * Slot count = machine tier.
 */
public class UpgradeItem extends Item {

    public enum Type {
        SPEED("speed"),
        EFFICIENCY("efficiency"),
        RF_EFFICIENCY("rf_efficiency"),
        BUFFER("buffer");

        private final String id;

        Type(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }

    private final Type type;

    public UpgradeItem(Type type, Properties properties) {
        super(properties);
        this.type = type;
    }

    public Type type() {
        return type;
    }

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
        if (printer.installUpgrade(context.getItemInHand())) {
            level.playSound(null, context.getClickedPos(), SoundEvents.ANVIL_USE,
                    SoundSource.BLOCKS, 0.4F, 1.8F);
        } else {
            String key = printer.upgradeTypeAtCap(this.type)
                    ? "message.mc3dprint.upgrade_type_at_cap"
                    : "message.mc3dprint.upgrade_slots_full";
            context.getPlayer().displayClientMessage(Component.translatable(key,
                    com.pgmacdesign.mc3dprint.config.MC3DPrintConfig.UPGRADE_MAX_PER_TYPE.get()), true);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        int maxPerType = com.pgmacdesign.mc3dprint.config.MC3DPrintConfig.UPGRADE_MAX_PER_TYPE.get();
        net.minecraft.network.chat.MutableComponent effect = switch (type) {
            case SPEED -> Component.translatable("tooltip.mc3dprint.upgrade.speed",
                    com.pgmacdesign.mc3dprint.config.MC3DPrintConfig.UPGRADE_SPEED_FACTOR.get());
            case RF_EFFICIENCY -> Component.translatable("tooltip.mc3dprint.upgrade.rf_efficiency",
                    com.pgmacdesign.mc3dprint.config.MC3DPrintConfig.UPGRADE_RF_FACTOR.get());
            case BUFFER -> Component.translatable("tooltip.mc3dprint.upgrade.buffer",
                    com.pgmacdesign.mc3dprint.config.MC3DPrintConfig.UPGRADE_BUFFER_FACTOR.get());
            // Efficiency is linear: maxPerType modules bring printing to exactly 1:1.
            case EFFICIENCY -> Component.translatable("tooltip.mc3dprint.upgrade.efficiency", maxPerType);
        };
        tooltip.add(effect.withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.mc3dprint.upgrade_help", maxPerType)
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
