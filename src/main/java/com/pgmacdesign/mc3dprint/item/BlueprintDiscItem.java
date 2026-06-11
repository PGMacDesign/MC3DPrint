package com.pgmacdesign.mc3dprint.item;

import com.pgmacdesign.mc3dprint.blueprint.Blueprint;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A written Blueprint Disc. Carries a UUID reference into the world's
 * {@link com.pgmacdesign.mc3dprint.blueprint.BlueprintFileStore} plus cached
 * display metadata (name, size, block count) so tooltips never need disk I/O.
 *
 * Shift+Right Click toggles the write lock — locked discs refuse overwrite
 * and deletion (enforced by the scanner/printer when they write).
 */
public class BlueprintDiscItem extends Item {
    public static final String TAG_BLUEPRINT_ID = "BlueprintId";
    public static final String TAG_NAME = "BlueprintName";
    public static final String TAG_SIZE = "Size";
    public static final String TAG_BLOCK_COUNT = "BlockCount";
    public static final String TAG_LOCKED = "Locked";

    public BlueprintDiscItem(Properties properties) {
        super(properties);
    }

    // --- NBT accessors ---

    public static Optional<UUID> getBlueprintId(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.hasUUID(TAG_BLUEPRINT_ID)) {
            return Optional.empty();
        }
        return Optional.of(tag.getUUID(TAG_BLUEPRINT_ID));
    }

    public static boolean hasBlueprint(ItemStack stack) {
        return getBlueprintId(stack).isPresent();
    }

    public static boolean isLocked(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(TAG_LOCKED);
    }

    /** Writes the reference + cached metadata. Refuses if the disc is locked. */
    public static boolean writeBlueprint(ItemStack stack, UUID id, Blueprint blueprint) {
        if (isLocked(stack)) {
            return false;
        }
        CompoundTag tag = stack.getOrCreateTag();
        tag.putUUID(TAG_BLUEPRINT_ID, id);
        tag.putString(TAG_NAME, blueprint.name());
        tag.putIntArray(TAG_SIZE, new int[]{blueprint.sizeX(), blueprint.sizeY(), blueprint.sizeZ()});
        tag.putInt(TAG_BLOCK_COUNT, blueprint.blockCount());
        return true;
    }

    public static boolean clearBlueprint(ItemStack stack) {
        if (isLocked(stack)) {
            return false;
        }
        stack.setTag(null);
        return true;
    }

    // --- Interaction ---

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isSecondaryUseActive() || !hasBlueprint(stack)) {
            return InteractionResultHolder.pass(stack);
        }
        if (!level.isClientSide) {
            boolean nowLocked = !isLocked(stack);
            stack.getOrCreateTag().putBoolean(TAG_LOCKED, nowLocked);
            player.displayClientMessage(Component.translatable(
                    nowLocked ? "message.mc3dprint.disc_locked" : "message.mc3dprint.disc_unlocked"), true);
            level.playSound(null, player.blockPosition(),
                    nowLocked ? SoundEvents.IRON_DOOR_CLOSE : SoundEvents.IRON_DOOR_OPEN,
                    SoundSource.PLAYERS, 0.4F, 1.6F);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return isLocked(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.hasUUID(TAG_BLUEPRINT_ID)) {
            tooltip.add(Component.translatable("tooltip.mc3dprint.disc_empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        String name = tag.getString(TAG_NAME);
        tooltip.add(Component.literal(name.isEmpty() ? "?" : name).withStyle(ChatFormatting.AQUA));
        int[] size = tag.getIntArray(TAG_SIZE);
        if (size.length == 3) {
            tooltip.add(Component.translatable("tooltip.mc3dprint.disc_size", size[0], size[1], size[2], tag.getInt(TAG_BLOCK_COUNT))
                    .withStyle(ChatFormatting.GRAY));
        }
        if (tag.getBoolean(TAG_LOCKED)) {
            tooltip.add(Component.translatable("tooltip.mc3dprint.disc_locked").withStyle(ChatFormatting.GOLD));
        }
    }
}
