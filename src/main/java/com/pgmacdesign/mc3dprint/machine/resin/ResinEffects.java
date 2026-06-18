package com.pgmacdesign.mc3dprint.machine.resin;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Pure(ish) effect logic for the six Resin effects, called from
 * {@code PrinterBlockEntity}'s blueprint print loop. Kept here so the block entity
 * stays lean and the effects are unit-testable in isolation. The numeric knobs all
 * live in {@code MC3DPrintConfig} (the caller passes them in).
 */
public final class ResinEffects {
    private ResinEffects() {}

    // ============================== VERDANT ==============================

    /**
     * If the placed block is a growable plant this resin tier handles, return its
     * fully-grown state; otherwise the input state unchanged. T1 = field crops +
     * nether wart; T2 also matures sweet berries + cocoa. (Cane/cactus/bamboo are
     * already placed at the captured height, so they need no per-block change.)
     */
    public static BlockState matureState(BlockState state, int tier) {
        Block block = state.getBlock();
        if (block instanceof CropBlock crop) { // wheat / carrots / potatoes / beetroot
            return crop.getStateForAge(crop.getMaxAge());
        }
        if (block instanceof NetherWartBlock) {
            return state.setValue(NetherWartBlock.AGE, 3);
        }
        if (tier >= 2) {
            if (block instanceof SweetBerryBushBlock) {
                return state.setValue(SweetBerryBushBlock.AGE, 3);
            }
            if (block instanceof CocoaBlock) {
                return state.setValue(CocoaBlock.AGE, 2);
            }
        }
        return state;
    }

    // ============================ ORE SALTING ============================

    /** Natural stone-types that can grow ore veins (NOT cobble/bricks/polished). */
    public static boolean isSaltableHost(BlockState state) {
        Block b = state.getBlock();
        return b == Blocks.STONE || b == Blocks.DEEPSLATE || b == Blocks.NETHERRACK;
    }

    /** Pick the ore variant matching the host (stone/deepslate/netherrack). gemShare is
     *  the chance the (overworld) result is diamond/emerald rather than a common ore. */
    public static BlockState pickOre(BlockState host, RandomSource rng, double gemShare) {
        Block b = host.getBlock();
        if (b == Blocks.NETHERRACK) {
            return (rng.nextBoolean() ? Blocks.NETHER_GOLD_ORE : Blocks.NETHER_QUARTZ_ORE).defaultBlockState();
        }
        boolean deep = b == Blocks.DEEPSLATE;
        if (rng.nextDouble() < gemShare) {
            boolean diamond = rng.nextBoolean();
            if (deep) {
                return (diamond ? Blocks.DEEPSLATE_DIAMOND_ORE : Blocks.DEEPSLATE_EMERALD_ORE).defaultBlockState();
            }
            return (diamond ? Blocks.DIAMOND_ORE : Blocks.EMERALD_ORE).defaultBlockState();
        }
        Block[] common = deep
                ? new Block[]{Blocks.DEEPSLATE_COAL_ORE, Blocks.DEEPSLATE_COPPER_ORE, Blocks.DEEPSLATE_IRON_ORE,
                        Blocks.DEEPSLATE_GOLD_ORE, Blocks.DEEPSLATE_REDSTONE_ORE, Blocks.DEEPSLATE_LAPIS_ORE}
                : new Block[]{Blocks.COAL_ORE, Blocks.COPPER_ORE, Blocks.IRON_ORE,
                        Blocks.GOLD_ORE, Blocks.REDSTONE_ORE, Blocks.LAPIS_ORE};
        return common[rng.nextInt(common.length)].defaultBlockState();
    }

    // ============================== OVERDRIVE ==============================

    /**
     * Overdrive cost floor for a block's BASE FU (pre-markup): T2 = break-even (base),
     * T3 = base*(1 - t3BelowPct) (net gain). The caller takes min(normalCost, floor),
     * so it never raises a cost and never stacks below the floor.
     */
    public static int overdriveFloor(int baseFu, int tier, double t3BelowPct) {
        if (baseFu <= 0) {
            return 0;
        }
        if (tier >= 3) {
            return Math.max(0, (int) Math.ceil(baseFu * (1.0 - t3BelowPct) - 1.0e-7));
        }
        return baseFu; // T2: exactly break-even
    }

    // ================================= XP =================================

    /** bankedXP = min(cap[tier], round(cap[tier] * printCost / ref)). */
    public static int bankedXpFor(int printCost, int tier, int capT1, int capT2, int capT3, int ref) {
        int cap = switch (tier) {
            case 1 -> capT1;
            case 2 -> capT2;
            default -> capT3;
        };
        if (ref <= 0 || printCost <= 0) {
            return 0;
        }
        long xp = Math.round((double) cap * printCost / ref);
        return (int) Math.min(cap, Math.max(0, xp));
    }

    // ============================== TREASURE ==============================

    /** True for the storage containers Treasure targets (chest/barrel/shulker). */
    public static boolean isStorageContainer(BlockEntity be) {
        return be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity
                || be instanceof ShulkerBoxBlockEntity;
    }

    /** Which rarity table this roll uses. T2: common, sometimes rare. T3: rare, sometimes epic. */
    public static ResourceLocation treasureTable(int tier, RandomSource rng, double t2Rare, double t3Epic) {
        if (tier >= 3) {
            return table(rng.nextDouble() < t3Epic ? "epic" : "rare");
        }
        return table(rng.nextDouble() < t2Rare ? "rare" : "common");
    }

    private static ResourceLocation table(String rarity) {
        return new ResourceLocation(MC3DPrint.MOD_ID, "resin/treasure_" + rarity);
    }

    /** Roll the loot table and drop its items into the container's empty slots. */
    public static void fillTreasure(ServerLevel level, BlockPos pos, Container container, ResourceLocation tableId) {
        LootTable lootTable = level.getServer().getLootData().getLootTable(tableId);
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .create(LootContextParamSets.CHEST);
        insertIntoEmpty(container, lootTable.getRandomItems(params));
    }

    // ============================ QUARTERMASTER ============================

    /** Deterministically stock a printed functional block with practical supplies. */
    public static void quartermaster(BlockEntity be) {
        if (be instanceof AbstractFurnaceBlockEntity furnace) {
            insertSlot(furnace, 1, new ItemStack(Blocks.COAL_BLOCK, 3)); // fuel slot
        } else if (be instanceof BrewingStandBlockEntity stand) {
            insertSlot(stand, 4, new ItemStack(Items.BLAZE_POWDER)); // fuel slot
            ItemStack water = PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.WATER);
            for (int s = 0; s < 3; s++) {
                insertSlot(stand, s, water.copy());
            }
        } else if (be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity) {
            insertIntoEmpty((Container) be, moveInKit());
        }
    }

    private static List<ItemStack> moveInKit() {
        return List.of(
                new ItemStack(Blocks.TORCH, 16),
                new ItemStack(Items.BREAD, 8),
                new ItemStack(Blocks.COAL_BLOCK, 2),
                enchantedTool(Items.IRON_PICKAXE),
                enchantedTool(Items.IRON_AXE),
                enchantedTool(Items.IRON_SHOVEL));
    }

    private static ItemStack enchantedTool(net.minecraft.world.item.Item tool) {
        ItemStack stack = new ItemStack(tool);
        stack.enchant(Enchantments.BLOCK_EFFICIENCY, 4);
        stack.enchant(Enchantments.UNBREAKING, 3);
        return stack;
    }

    // ============================== shared ==============================

    /** Drop each stack into the first empty slot; never overwrites captured contents. */
    private static void insertIntoEmpty(Container container, List<ItemStack> items) {
        for (ItemStack stack : items) {
            if (stack.isEmpty()) {
                continue;
            }
            for (int i = 0; i < container.getContainerSize(); i++) {
                if (container.getItem(i).isEmpty()) {
                    container.setItem(i, stack.copy());
                    break;
                }
            }
        }
        container.setChanged();
    }

    private static void insertSlot(Container container, int slot, ItemStack stack) {
        if (slot < container.getContainerSize() && container.getItem(slot).isEmpty()) {
            container.setItem(slot, stack);
        }
        container.setChanged();
    }
}
