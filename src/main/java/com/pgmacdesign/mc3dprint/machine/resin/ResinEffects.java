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
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.StemBlock;
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

import java.util.ArrayList;
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
        if (block instanceof StemBlock) {
            // pumpkin/melon stems mature to age 7 (ready to fruit). A staple crop → Common+.
            // Without this, Verdant silently no-op'd on pumpkin/melon farms (the reported bug).
            return state.setValue(StemBlock.AGE, StemBlock.MAX_AGE);
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

    // ---- Block-level target tests (palette pre-scan) ----
    // These mirror the block-entity checks above/in PrinterBlockEntity, but operate on a
    // BlockState so a blueprint's palette can be scanned BEFORE printing to decide whether a
    // resin could do anything at all (see PrinterBlockEntity#resinWouldBenefit and
    // BlueprintDiscItem#resinTargetMask). Keeping the block classification here keeps the
    // "what does each effect target" knowledge in one place so the pre-scan can't drift from
    // the actual effect. (ChestBlock also covers trapped chests, matching ChestBlockEntity.)

    /** Block-level form of {@link #isStorageContainer} — what Treasure can fill. */
    public static boolean isStorageContainerBlock(BlockState state) {
        Block b = state.getBlock();
        return b instanceof ChestBlock || b instanceof BarrelBlock || b instanceof ShulkerBoxBlock;
    }

    /** Block-level form of the Quartermaster target set: furnace / brewing stand / chest / barrel
     *  (matches the block entities stocked in PrinterBlockEntity#applyContainerResin — note
     *  Quartermaster does NOT stock shulker boxes, so this is intentionally narrower than Treasure). */
    public static boolean isQuartermasterTargetBlock(BlockState state) {
        Block b = state.getBlock();
        return b instanceof AbstractFurnaceBlock || b instanceof BrewingStandBlock
                || b instanceof ChestBlock || b instanceof BarrelBlock;
    }

    /** Which rarity table this roll uses. Uncommon resin (tier 2): common, sometimes uncommon.
     *  Rare resin (tier 3): uncommon, sometimes rare. */
    public static ResourceLocation treasureTable(int tier, RandomSource rng, double t2Uncommon, double t3Rare) {
        if (tier >= 3) {
            return table(rng.nextDouble() < t3Rare ? "rare" : "uncommon");
        }
        return table(rng.nextDouble() < t2Uncommon ? "uncommon" : "common");
    }

    private static ResourceLocation table(String rarity) {
        return ResourceLocation.fromNamespaceAndPath(MC3DPrint.MOD_ID, "resin/treasure_" + rarity);
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

    // Quartermaster stocks each printed functional block. Coal (furnaces) and food/torches
    // (chests) come from SHARED per-print budgets split evenly across all such containers —
    // see PrinterBlockEntity, which pre-counts the containers and hands each its share. That's
    // why these take an amount instead of hard-coding one: a Rare-tier kit should feel generous
    // (a full stack of coal blocks across the furnaces, a stack of food across the chests),
    // not 3 coal per furnace regardless of how many there are.

    /** Stock a printed furnace's fuel slot with its share of coal blocks (clamped to one stack). */
    public static void quartermasterFurnace(AbstractFurnaceBlockEntity furnace, int coalBlocks) {
        if (coalBlocks > 0) {
            insertSlot(furnace, 1, new ItemStack(Blocks.COAL_BLOCK, Math.min(coalBlocks, 64)));
        }
    }

    /** Stock a printed brewing stand: blaze-powder fuel + three water bottles, ready to brew. */
    public static void quartermasterBrewing(BrewingStandBlockEntity stand) {
        insertSlot(stand, 4, new ItemStack(Items.BLAZE_POWDER, 8)); // fuel slot (a real stockpile)
        ItemStack water = PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.WATER);
        for (int s = 0; s < 3; s++) {
            insertSlot(stand, s, water.copy());
        }
    }

    /**
     * Stock a printed chest/barrel with its share of food + torches; the FIRST storage
     * container of the print also gets the one-time enchanted move-in tool kit (so 5 chests
     * don't hand out 5 sets of tools).
     */
    public static void quartermasterStorage(Container container, int bread, int torches, boolean includeTools) {
        List<ItemStack> kit = new ArrayList<>();
        if (bread > 0) {
            kit.add(new ItemStack(Items.BREAD, Math.min(bread, 64)));
        }
        if (torches > 0) {
            kit.add(new ItemStack(Blocks.TORCH, Math.min(torches, 64)));
        }
        if (includeTools) {
            kit.add(enchantedTool(Items.IRON_PICKAXE));
            kit.add(enchantedTool(Items.IRON_AXE));
            kit.add(enchantedTool(Items.IRON_SHOVEL));
        }
        insertIntoEmpty(container, kit);
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
