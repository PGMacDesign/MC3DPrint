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
            context.getPlayer().displayClientMessage(
                    Component.translatable("message.mc3dprint.upgrade_slots_full"), true);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.mc3dprint.upgrade." + type.id())
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.mc3dprint.upgrade_help")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
