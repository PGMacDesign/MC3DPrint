package com.pgmacdesign.mc3dprint.item;

import com.pgmacdesign.mc3dprint.compat.InteractionCompat;
import com.pgmacdesign.mc3dprint.compat.NbtCompat;

import com.pgmacdesign.mc3dprint.blueprint.Blueprint;
import com.pgmacdesign.mc3dprint.blueprint.BlueprintBlockState;
import com.pgmacdesign.mc3dprint.blueprint.BlueprintEntity;
import com.pgmacdesign.mc3dprint.fu.FuConversion;
import com.pgmacdesign.mc3dprint.fu.FuValue;
import com.pgmacdesign.mc3dprint.fu.FuValueRegistry;
import com.pgmacdesign.mc3dprint.machine.resin.ResinEffects;
import com.pgmacdesign.mc3dprint.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
//? if <1.21.5 {
import net.minecraft.world.InteractionResultHolder;
//?}
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
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
    /**
     * A small bitmask of which resin effects could possibly do something on this print,
     * pre-computed from the blueprint's palette at write time (see {@link #resinTargetMask}).
     * The client has only this cached metadata (never the full block data), so it's what the
     * printer GUI uses to warn that a slotted resin would be wasted. Absent reads as "unknown"
     * (all effects assumed useful) so older discs never show a false "no effect".
     */
    public static final String TAG_RESIN_TARGETS = "ResinTargets";
    public static final int RESIN_TARGET_VERDANT = 1;
    public static final int RESIN_TARGET_TREASURE = 1 << 1;
    public static final int RESIN_TARGET_QUARTERMASTER = 1 << 2;
    public static final int RESIN_TARGET_ORE_SALTING = 1 << 3;

    public BlueprintDiscItem(Properties properties) {
        super(properties);
    }

    // --- NBT accessors ---

    private static Optional<BlueprintData> data(ItemStack stack) {
        return Optional.ofNullable(stack.get(ModDataComponents.BLUEPRINT.get()));
    }

    public static Optional<UUID> getBlueprintId(ItemStack stack) {
        return data(stack).map(BlueprintData::id);
    }

    public static boolean hasBlueprint(ItemStack stack) {
        return stack.has(ModDataComponents.BLUEPRINT.get());
    }

    public static boolean isLocked(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.LOCKED.get(), Boolean.FALSE);
    }

    /** Writes the reference + cached metadata. Refuses if the disc is locked. */
    public static boolean writeBlueprint(ItemStack stack, UUID id, Blueprint blueprint) {
        return writeBlueprint(stack, id, blueprint, false);
    }

    /**
     * As {@link #writeBlueprint(ItemStack, UUID, Blueprint)}, but also records whether
     * a player authored this disc. Scanner/import pass {@code true}; curated + loot
     * paths use the 3-arg form (official, {@code playerCreated == false}).
     */
    public static boolean writeBlueprint(ItemStack stack, UUID id, Blueprint blueprint, boolean playerCreated) {
        if (isLocked(stack)) {
            return false;
        }
        stack.set(ModDataComponents.BLUEPRINT.get(), new BlueprintData(
                id, blueprint.name(),
                blueprint.sizeX(), blueprint.sizeY(), blueprint.sizeZ(),
                blueprint.blockCount(), blueprintTier(blueprint), blueprintPrintCost(blueprint),
                resinTargetMask(blueprint), playerCreated));
        return true;
    }

    /**
     * Whether this disc is an OFFICIAL/found blueprint (curated or loot) rather than
     * one a player scanned/imported. Resin only applies to official blueprints. An
     * absent component reads as official (so all blank/pre-existing discs stay official).
     */
    public static boolean isOfficial(ItemStack stack) {
        return data(stack).map(d -> !d.playerCreated()).orElse(true);
    }

    /** The stored print cost (top-tier FU units), or 0 if absent. */
    public static int getPrintCost(ItemStack stack) {
        return data(stack).map(BlueprintData::printCost).orElse(0);
    }

    /** The stored blueprint tier (the FU tier of the print cost), or 0 if absent. */
    public static int getTier(ItemStack stack) {
        return data(stack).map(BlueprintData::tier).orElse(0);
    }

    /** The cached display name, or "" if the disc is blank. */
    public static String getBlueprintName(ItemStack stack) {
        return data(stack).map(BlueprintData::name).orElse("");
    }

    /** The cached {sizeX, sizeY, sizeZ}, or an empty array if the disc is blank. */
    public static int[] getSize(ItemStack stack) {
        return data(stack).map(d -> new int[]{d.sizeX(), d.sizeY(), d.sizeZ()}).orElse(new int[0]);
    }

    /** The cached block count, or 0 if the disc is blank. */
    public static int getBlockCount(ItemStack stack) {
        return data(stack).map(BlueprintData::blockCount).orElse(0);
    }

    /** The stored resin-target bitmask, or -1 ("unknown") for a disc without this
     *  metadata — unknown is treated as "could benefit", so we never warn falsely. */
    public static int getResinTargets(ItemStack stack) {
        return data(stack).map(BlueprintData::resinTargetMask).orElse(-1);
    }

    /**
     * Scan a blueprint's palette and flag which resin effects have at least one valid target
     * in the build (a container for Treasure, natural stone for Ore Salting, etc.). This is the
     * single source for "would a resin do anything here", used both at write time (to stamp the
     * disc for the GUI warning) and — recomputed live with the full blueprint — by the printer's
     * consume guard. Scanning the palette (the set of DISTINCT states the build uses) is the
     * cheapest exact answer: a state is in the palette iff at least one block uses it.
     *
     * Verdant is flagged with the broadest tier (2) so the stamped mask never under-reports;
     * the printer's live guard re-checks Verdant against the actual resin tier for full precision.
     */
    public static int resinTargetMask(Blueprint blueprint) {
        int mask = 0;
        for (BlueprintBlockState paletteState : blueprint.palette()) {
            BlockState state = paletteState.resolve().orElse(null);
            if (state == null) {
                continue;
            }
            if (!ResinEffects.matureState(state, 2).equals(state)) {
                mask |= RESIN_TARGET_VERDANT;
            }
            if (ResinEffects.isStorageContainerBlock(state)) {
                mask |= RESIN_TARGET_TREASURE;
            }
            if (ResinEffects.isQuartermasterTargetBlock(state)) {
                mask |= RESIN_TARGET_QUARTERMASTER;
            }
            if (ResinEffects.isSaltableHost(state)) {
                mask |= RESIN_TARGET_ORE_SALTING;
            }
        }
        return mask;
    }

    /**
     * Whether an effect could benefit a build with the given target mask. XP Yield and Overdrive
     * benefit any non-trivial print (content-independent), so they always read as useful. An
     * unknown mask (-1, legacy disc) also reads as useful so we never warn falsely.
     */
    public static boolean maskBenefits(ResinItem.Effect effect, int mask) {
        if (mask < 0) {
            return true;
        }
        return switch (effect) {
            case VERDANT -> (mask & RESIN_TARGET_VERDANT) != 0;
            case TREASURE -> (mask & RESIN_TARGET_TREASURE) != 0;
            case QUARTERMASTER -> (mask & RESIN_TARGET_QUARTERMASTER) != 0;
            case ORE_SALTING -> (mask & RESIN_TARGET_ORE_SALTING) != 0;
            case XP, OVERDRIVE -> true;
        };
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

            // Decorative entities cost their contents (stand + armor; frame + framed item).
            // Reconstructing those item stacks from raw NBT needs a registry provider
            // (1.20.5+); use the one bound at server start. If unbound (early datagen),
            // skip the entity contribution rather than fail the whole quote.
            net.minecraft.core.HolderLookup.Provider registries = FuValueRegistry.boundRegistries();
            for (BlueprintEntity entity : registries == null
                    ? java.util.List.<BlueprintEntity>of() : blueprint.entities()) {
                for (ItemStack content : entityContentItems(registries, entity.nbt())) {
                    Optional<FuValue> v = FuValueRegistry.valueOf(content);
                    if (v.isPresent()) {
                        totalBase += FuConversion.toBase(v.get().fu(), v.get().tier(), ratio) * content.getCount();
                    }
                }
            }

            long cost = FuConversion.fromBaseCeil(totalBase, topTier, ratio);
            return FuConversion.clampToInt(cost);
        } catch (RuntimeException ignored) {
            // FU registry may not be bound yet (e.g. early datagen) — fall back to 0
            return 0;
        }
    }

    /**
     * The chargeable item contents of a captured decorative entity — the entity's
     * own item plus what it carries: an armor stand's armor/hand items, an item
     * frame's framed item. The painting/frame/stand base item derives its FU like
     * any craftable. Shared by the print-cost quote and the printer's per-entity
     * charge so the two never disagree.
     */
    /** The entity's own item (the stand/frame/painting/minecart/boat), or EMPTY if unknown. */
    public static ItemStack entityBaseItem(CompoundTag nbt) {
        Item base = switch (NbtCompat.getString(nbt, "id")) {
            case "minecraft:armor_stand" -> Items.ARMOR_STAND;
            case "minecraft:item_frame" -> Items.ITEM_FRAME;
            case "minecraft:glow_item_frame" -> Items.GLOW_ITEM_FRAME;
            case "minecraft:painting" -> Items.PAINTING;
            case "minecraft:minecart" -> Items.MINECART;
            case "minecraft:boat" -> boatItem(NbtCompat.getString(nbt, "Type"));
            default -> null;
        };
        return base == null ? ItemStack.EMPTY : new ItemStack(base);
    }

    public static List<ItemStack> entityContentItems(net.minecraft.core.HolderLookup.Provider registries,
                                                      CompoundTag nbt) {
        List<ItemStack> items = new ArrayList<>();
        ItemStack base = entityBaseItem(nbt);
        if (!base.isEmpty()) {
            items.add(base);
        }
        for (String slot : new String[]{"ArmorItems", "HandItems"}) {
            for (Tag tag : NbtCompat.getList(nbt, slot, Tag.TAG_COMPOUND)) {
                ItemStack stack = NbtCompat.parseItemStack(registries, (CompoundTag) tag);
                if (!stack.isEmpty()) {
                    items.add(stack);
                }
            }
        }
        if (nbt.contains("Item")) { // item frame's framed item
            ItemStack framed = NbtCompat.parseItemStack(registries, NbtCompat.getCompound(nbt, "Item"));
            if (!framed.isEmpty()) {
                items.add(framed);
            }
        }
        return items;
    }

    /** A regular boat's item, keyed by its wood {@code Type} ("oak", "bamboo" = raft, …). */
    private static Item boatItem(String type) {
        return switch (type) {
            case "spruce" -> Items.SPRUCE_BOAT;
            case "birch" -> Items.BIRCH_BOAT;
            case "jungle" -> Items.JUNGLE_BOAT;
            case "acacia" -> Items.ACACIA_BOAT;
            case "dark_oak" -> Items.DARK_OAK_BOAT;
            case "mangrove" -> Items.MANGROVE_BOAT;
            case "cherry" -> Items.CHERRY_BOAT;
            case "bamboo" -> Items.BAMBOO_RAFT;
            default -> Items.OAK_BOAT;
        };
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
        stack.remove(ModDataComponents.BLUEPRINT.get());
        return true;
    }

    // --- Interaction ---

    @Override
    //? if >=1.21.5 {
    /*public InteractionResult use(Level level, Player player, InteractionHand hand) {
    *///?} else {
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
    //?}
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isSecondaryUseActive() || !hasBlueprint(stack)) {
            return InteractionCompat.holderPass(stack);
        }
        if (!level.isClientSide()) {
            boolean nowLocked = !isLocked(stack);
            if (nowLocked) {
                stack.set(ModDataComponents.LOCKED.get(), Boolean.TRUE);
            } else {
                // Remove rather than store false so an unlocked disc is value-equal to a never-locked one.
                stack.remove(ModDataComponents.LOCKED.get());
            }
            player.displayClientMessage(Component.translatable(
                    nowLocked ? "message.mc3dprint.disc_locked" : "message.mc3dprint.disc_unlocked"), true);
            level.playSound(null, player.blockPosition(),
                    nowLocked ? SoundEvents.IRON_DOOR_CLOSE : SoundEvents.IRON_DOOR_OPEN,
                    SoundSource.PLAYERS, 0.4F, 1.6F);
        }
        return InteractionCompat.holderSuccess(stack, level.isClientSide());
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return isLocked(stack);
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
        BlueprintData blueprint = stack.get(ModDataComponents.BLUEPRINT.get());
        if (blueprint == null) {
            tooltip.add(Component.translatable("tooltip.mc3dprint.disc_empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        String name = blueprint.name();
        tooltip.add(Component.literal(name.isEmpty() ? "?" : name).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.mc3dprint.disc_size",
                        blueprint.sizeX(), blueprint.sizeY(), blueprint.sizeZ(), blueprint.blockCount())
                .withStyle(ChatFormatting.GRAY));
        int tier = blueprint.tier();
        if (tier > 0) {
            tooltip.add(Component.translatable("tooltip.mc3dprint.disc_tier", tier).withStyle(tierFormat(tier)));
            tooltip.add(Component.translatable("tooltip.mc3dprint.disc_print_cost", blueprint.printCost(), tier)
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        if (isLocked(stack)) {
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
