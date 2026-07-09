package com.pgmacdesign.mc3dprint.scanner;

import com.pgmacdesign.mc3dprint.blueprint.Blueprint;
import com.pgmacdesign.mc3dprint.blueprint.BlueprintFileStore;
import com.pgmacdesign.mc3dprint.config.MC3DPrintConfig;
import com.pgmacdesign.mc3dprint.item.BlueprintDiscItem;
import com.pgmacdesign.mc3dprint.machine.MachineTier;
import com.pgmacdesign.mc3dprint.machine.multiblock.MultiblockPattern;
import com.pgmacdesign.mc3dprint.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Handheld scanner: WorldEdit-wand-style two-corner selection, then scan.
 *
 * Controls:
 * - Right-click a block: set corner A, then corner B (alternating)
 * - Right-click air: scan (needs both corners + a Blank Blueprint Disc anywhere in the inventory)
 * - Sneak + right-click air: clear selection
 */
public class ScannerItem extends Item {
    public static final String TAG_CORNER_A = "CornerA";
    public static final String TAG_CORNER_B = "CornerB";
    public static final String TAG_NEXT_IS_B = "NextCornerB";

    public ScannerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = context.getItemInHand();
        Player player = context.getPlayer();
        CompoundTag tag = stack.getOrCreateTag();
        BlockPos clicked = context.getClickedPos();

        boolean settingB = tag.getBoolean(TAG_NEXT_IS_B);
        tag.put(settingB ? TAG_CORNER_B : TAG_CORNER_A, NbtUtils.writeBlockPos(clicked));
        tag.putBoolean(TAG_NEXT_IS_B, !settingB);

        if (player != null) {
            player.displayClientMessage(Component.translatable(
                    settingB ? "message.mc3dprint.corner_b_set" : "message.mc3dprint.corner_a_set",
                    clicked.getX(), clicked.getY(), clicked.getZ()), true);
            level.playSound(null, clicked, SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS,
                    0.5F, settingB ? 1.4F : 1.0F);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }

        if (player.isSecondaryUseActive()) {
            stack.removeTagKey(TAG_CORNER_A);
            stack.removeTagKey(TAG_CORNER_B);
            stack.removeTagKey(TAG_NEXT_IS_B);
            player.displayClientMessage(Component.translatable("message.mc3dprint.corners_cleared"), true);
            return InteractionResultHolder.consume(stack);
        }

        return scan(level, player, stack);
    }

    private InteractionResultHolder<ItemStack> scan(Level level, Player player, ItemStack stack) {
        Optional<BlockPos> cornerA = readCorner(stack, TAG_CORNER_A);
        Optional<BlockPos> cornerB = readCorner(stack, TAG_CORNER_B);
        if (cornerA.isEmpty() || cornerB.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.mc3dprint.scan_no_corners"), true);
            return InteractionResultHolder.fail(stack);
        }

        // Flat scan cap per axis (default 33), independent of machine tier and of whether
        // Draconic Evolution is installed. The scanner is deliberately decoupled from the print
        // footprint: hand-scans stay a sane size while official/curated discs can print larger
        // builds on a high-tier fabricator. Tune via the `t1MaxEdge` config.
        int maxEdge = MC3DPrintConfig.T1_SCANNER_MAX_EDGE.get();
        // Opt-in override (config `unlockScannerSize`): raise the cap to the largest footprint a
        // buildable fabricator can print — the configured T8 footprint with Draconic Evolution
        // (default 51), else T7's (default 33) — so a very large build can be captured and
        // printed. Off by default; never lowers the configured cap.
        if (MC3DPrintConfig.UNLOCK_SCANNER_SIZE.get()) {
            MachineTier top = ModList.get().isLoaded(MultiblockPattern.DRACONIC_MOD_ID)
                    ? MachineTier.T8 : MachineTier.T7;
            maxEdge = Math.max(maxEdge, MC3DPrintConfig.maxFootprint(top));
        }
        BlockPos a = cornerA.get();
        BlockPos b = cornerB.get();
        int dx = Math.abs(a.getX() - b.getX()) + 1;
        int dy = Math.abs(a.getY() - b.getY()) + 1;
        int dz = Math.abs(a.getZ() - b.getZ()) + 1;
        if (dx > maxEdge || dy > maxEdge || dz > maxEdge) {
            player.displayClientMessage(Component.translatable("message.mc3dprint.scan_too_large",
                    dx, dy, dz, maxEdge), true);
            return InteractionResultHolder.fail(stack);
        }

        // A blank blueprint disc ANYWHERE in the inventory works — no need to hold it in
        // the off-hand. Find the first one; fail fast if the player has none.
        net.minecraft.world.entity.player.Inventory inv = player.getInventory();
        int blankSlot = -1;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(ModItems.BLANK_BLUEPRINT_DISC.get())) {
                blankSlot = i;
                break;
            }
        }
        if (blankSlot < 0) {
            player.displayClientMessage(Component.translatable("message.mc3dprint.scan_need_blank_disc"), true);
            return InteractionResultHolder.fail(stack);
        }

        BlockPos min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
        String name = "Scan @ " + min.getX() + "," + min.getY() + "," + min.getZ();
        Blueprint blueprint = ScanOperation.capture(level, a, b, name);

        BlueprintFileStore store = BlueprintFileStore.forServer(((ServerLevel) level).getServer());
        UUID id = store.save(blueprint);

        ItemStack disc = new ItemStack(ModItems.BLUEPRINT_DISC.get());
        // playerCreated = true: a scanned disc is NOT official, so it can't take resin.
        BlueprintDiscItem.writeBlueprint(disc, id, blueprint, true);

        // consume one blank disc; hand back the written disc (into the inventory, else drop)
        inv.getItem(blankSlot).shrink(1);
        if (!player.getInventory().add(disc)) {
            player.drop(disc, false);
        }

        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            com.pgmacdesign.mc3dprint.advancement.ModCriteria.STRUCTURE_SCANNED.trigger(serverPlayer);
        }
        player.displayClientMessage(Component.translatable("message.mc3dprint.scan_complete",
                blueprint.blockCount()), true);
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5F, 1.5F);
        return InteractionResultHolder.consume(stack);
    }

    private static Optional<BlockPos> readCorner(ItemStack stack, String key) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(key, CompoundTag.TAG_COMPOUND)) {
            return Optional.empty();
        }
        return Optional.of(NbtUtils.readBlockPos(tag.getCompound(key)));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        readCorner(stack, TAG_CORNER_A).ifPresent(pos -> tooltip.add(Component
                .translatable("tooltip.mc3dprint.corner_a", pos.getX(), pos.getY(), pos.getZ())
                .withStyle(ChatFormatting.AQUA)));
        readCorner(stack, TAG_CORNER_B).ifPresent(pos -> tooltip.add(Component
                .translatable("tooltip.mc3dprint.corner_b", pos.getX(), pos.getY(), pos.getZ())
                .withStyle(ChatFormatting.AQUA)));
        tooltip.add(Component.translatable("tooltip.mc3dprint.scanner_help").withStyle(ChatFormatting.GRAY));
    }
}
