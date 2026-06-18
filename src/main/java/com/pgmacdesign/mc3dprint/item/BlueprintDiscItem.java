package com.pgmacdesign.mc3dprint.item;

import com.pgmacdesign.mc3dprint.blueprint.Blueprint;
import com.pgmacdesign.mc3dprint.blueprint.BlueprintBlockState;
import com.pgmacdesign.mc3dprint.fu.FuConversion;
import com.pgmacdesign.mc3dprint.fu.FuValue;
import com.pgmacdesign.mc3dprint.fu.FuValueRegistry;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

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
    public static final String TAG_TIER = "Tier";
    public static final String TAG_PRINT_COST = "PrintCost";
    public static final String TAG_LOCKED = "Locked";
    /**
     * True when a player authored this disc (Structure Scanner or {@code /import}).
     * Absent/false means the disc is OFFICIAL (curated or found-in-loot). The Resin
     * system only works on official blueprints — this is the anti-exploit gate that
     * stops a player scanning a cheap build and mass-printing treasure.
     */
    public static final String TAG_PLAYER_CREATED = "PlayerCreated";

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
        tag.putInt(TAG_TIER, blueprintTier(blueprint));
        tag.putInt(TAG_PRINT_COST, blueprintPrintCost(blueprint));
        return true;
    }

    /**
     * As {@link #writeBlueprint(ItemStack, UUID, Blueprint)}, but also records whether
     * a player authored this disc. Scanner/import pass {@code true}; curated + loot
     * paths use the 3-arg form (official, flag absent).
     */
    public static boolean writeBlueprint(ItemStack stack, UUID id, Blueprint blueprint, boolean playerCreated) {
        if (!writeBlueprint(stack, id, blueprint)) {
            return false;
        }
        stack.getOrCreateTag().putBoolean(TAG_PLAYER_CREATED, playerCreated);
        return true;
    }

    /**
     * Whether this disc is an OFFICIAL/found blueprint (curated or loot) rather than
     * one a player scanned/imported. Resin only applies to official blueprints. An
     * absent flag reads as official (so all pre-existing discs stay official).
     */
    public static boolean isOfficial(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null || !tag.getBoolean(TAG_PLAYER_CREATED);
    }

    /**
     * The blueprint's tier = the highest material tier among its blocks (one
     * diamond block in a pile of stone still reads as that block's tier), or 1 if
     * nothing is priced. This is the lowest machine tier that can print all of it.
     */
    public static int blueprintTier(Blueprint blueprint) {
        int max = 1;
        try {
            for (BlueprintBlockState paletteState : blueprint.palette()) {
                var resolved = paletteState.resolve();
                if (resolved.isEmpty()) {
                    continue;
                }
                Item item = resolved.get().getBlock().asItem();
                if (item == Items.AIR) {
                    continue;
                }
                int tier = FuValueRegistry.valueOf(new ItemStack(item)).map(FuValue::tier).orElse(1);
                if (tier > max) {
                    max = tier;
                }
            }
        } catch (RuntimeException ignored) {
            // FU registry may not be bound yet (e.g. early datagen) — fall back to 1
        }
        return max;
    }

    /**
     * The blueprint's total print cost, expressed as a single number of
     * {@link #blueprintTier top-tier} FU — i.e. how much of the highest-tier
     * filament a player needs to print the whole thing. Each block is summed
     * with multiplicity (500 stone counts 500×) in tier-1 base units, then the
     * total is converted up to the top tier with a ceiling divide (down-only
     * spending means any remainder rounds up a whole top-tier unit).
     *
     * <p>Free blocks contribute 0, mirroring the printer: itemless/structural
     * matter (water, crops, farmland) and anything the FU registry can't price
     * (unprintable in strict mode; curated blueprints have none). Returns 0 if
     * the FU registry isn't bound (e.g. early datagen).
     */
    public static int blueprintPrintCost(Blueprint blueprint) {
        try {
            int topTier = blueprintTier(blueprint);
            int ratio = FuConversion.ratio();

            // Tally each palette index with multiplicity over the dense block grid.
            int[] counts = new int[blueprint.palette().size()];
            blueprint.forEachBlock((pos, paletteIndex) -> counts[paletteIndex]++);

            long totalBase = 0;
            for (int i = 0; i < counts.length; i++) {
                int count = counts[i];
                if (count == 0) {
                    continue;
                }
                var resolved = blueprint.palette().get(i).resolve();
                if (resolved.isEmpty()) {
                    continue;
                }
                BlockState state = resolved.get();
                if (isStructuralMatter(state)) {
                    continue; // itemless / crops / farmland — prints free
                }
                Item item = state.getBlock().asItem();
                if (item == Items.AIR) {
                    continue;
                }
                Optional<FuValue> value = FuValueRegistry.valueOf(new ItemStack(item));
                if (value.isEmpty()) {
                    continue; // unpriced — strict mode can't print it; skip conservatively
                }
                FuValue fv = value.get();
                totalBase += FuConversion.toBase(fv.fu(), fv.tier(), ratio) * count;
            }

            long cost = FuConversion.fromBaseCeil(totalBase, topTier, ratio);
            return FuConversion.clampToInt(cost);
        } catch (RuntimeException ignored) {
            // FU registry may not be bound yet (e.g. early datagen) — fall back to 0
            return 0;
        }
    }

    /**
     * Mirrors {@code PrinterBlockEntity.isStructuralMatter}: blocks that print
     * free — itemless blocks (water/fire/wall-torches, {@code asItem()==AIR})
     * and tilled/planted growth (crops, stems, saplings, farmland, dirt path).
     */
    private static boolean isStructuralMatter(BlockState state) {
        if (state.isAir()) {
            return false;
        }
        if (state.getBlock().asItem() == Items.AIR) {
            return true;
        }
        Block block = state.getBlock();
        return block instanceof net.minecraft.world.level.block.BushBlock     // crops/stems/saplings/flowers/wart
                || block instanceof net.minecraft.world.level.block.FarmBlock      // farmland
                || block instanceof net.minecraft.world.level.block.DirtPathBlock; // grass/dirt path
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
        int tier = tag.getInt(TAG_TIER);
        if (tier > 0) {
            tooltip.add(Component.translatable("tooltip.mc3dprint.disc_tier", tier).withStyle(tierFormat(tier)));
            int printCost = tag.getInt(TAG_PRINT_COST);
            tooltip.add(Component.translatable("tooltip.mc3dprint.disc_print_cost", printCost, tier)
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        if (tag.getBoolean(TAG_LOCKED)) {
            tooltip.add(Component.translatable("tooltip.mc3dprint.disc_locked").withStyle(ChatFormatting.GOLD));
        }
    }

    /** Tier label color, roughly matching the tier accent ramp (T1 grey … T8 magenta). */
    private static ChatFormatting tierFormat(int tier) {
        return switch (tier) {
            case 2 -> ChatFormatting.BLUE;
            case 3 -> ChatFormatting.AQUA;
            case 4 -> ChatFormatting.GREEN;
            case 5 -> ChatFormatting.GOLD;
            case 6 -> ChatFormatting.RED;
            case 7 -> ChatFormatting.LIGHT_PURPLE;
            case 8 -> ChatFormatting.DARK_PURPLE;
            default -> ChatFormatting.GRAY;
        };
    }
}
