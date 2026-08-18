package com.pgmacdesign.mc3dprint.gametest;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.machine.PrinterBlockEntity;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * A planted crop is never free.
 *
 * <p>The free-print pass used to key on {@code BushBlock}, which every crop in the game descends
 * from, so scanning a field of a mod's valuable crops and printing it handed them over for nothing.
 * Mystical Agriculture essence crops were the report; the hole belonged to any mod whose seeds are
 * worth something. These tests pin the general rule rather than that mod, since no blocklist is
 * involved and none should be.
 */
@GameTestHolder(MC3DPrint.MOD_ID)
@PrefixGameTestTemplate(false)
public class CropPricingGameTests {

    /**
     * The premise the old free pass denied: a crop block's item IS its seed, so ordinary pricing
     * has something correct to charge. If this ever stops holding, the fix behind it stops working
     * and the free pass would look justified again.
     */
    @GameTest(template = "empty5", timeoutTicks = 40)
    public static void aCropBlockResolvesToItsSeedItem(GameTestHelper helper) {
        if (Blocks.WHEAT.asItem() != Items.WHEAT_SEEDS) {
            throw new GameTestAssertException("wheat should resolve to wheat_seeds, got "
                    + Blocks.WHEAT.asItem());
        }
        if (Blocks.CARROTS.asItem() != Items.CARROT) {
            throw new GameTestAssertException("carrots should resolve to the carrot item");
        }
        if (Blocks.SWEET_BERRY_BUSH.asItem() != Items.SWEET_BERRIES) {
            throw new GameTestAssertException("sweet berry bush should resolve to sweet_berries");
        }
        helper.succeed();
    }

    /** No member of the crop family gets the free pass, whatever its age or its mod. */
    @GameTest(template = "empty5", timeoutTicks = 40)
    public static void plantedGrowthIsNeverFreeStructuralMatter(GameTestHelper helper) {
        var families = new net.minecraft.world.level.block.Block[] {
                Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES, Blocks.BEETROOTS,
                Blocks.NETHER_WART, Blocks.OAK_SAPLING, Blocks.SWEET_BERRY_BUSH, Blocks.POPPY};
        for (var block : families) {
            if (PrinterBlockEntity.isStructuralMatterForTest(block.defaultBlockState())) {
                throw new GameTestAssertException(block
                        + " must not print free; it prices off its seed item");
            }
        }
        // A mature crop is the same block, so age cannot buy a free pass either.
        var mature = Blocks.WHEAT.defaultBlockState()
                .setValue(net.minecraft.world.level.block.CropBlock.AGE, 7);
        if (PrinterBlockEntity.isStructuralMatterForTest(mature)) {
            throw new GameTestAssertException("a grown crop must not print free");
        }
        helper.succeed();
    }

    /**
     * A planting item cannot wind, whether or not anyone listed it. The tag names vanilla's
     * seeds, but FU values reach modded items through config overrides, recipe derivation and the
     * compat API, and a seed is the one thing a farm makes without limit. Sweet berries stand in
     * for the modded case here: a valued planting item that is NOT in the tag and is barred anyway.
     */
    @GameTest(template = "empty5", timeoutTicks = 40)
    public static void aPlantingItemCannotWindWithoutBeingListed(GameTestHelper helper) {
        var berries = new net.minecraft.world.item.ItemStack(Items.SWEET_BERRIES);
        if (berries.is(com.pgmacdesign.mc3dprint.registry.ModItemTags.WINDER_BLACKLIST)) {
            throw new GameTestAssertException("sweet_berries is in the tag, so it no longer stands"
                    + " in for an unlisted modded seed; pick another unlisted planting item");
        }
        if (!com.pgmacdesign.mc3dprint.registry.ModItemTags.isWinderBlacklisted(berries)) {
            throw new GameTestAssertException("an unlisted planting item must still be barred from"
                    + " winding, or a modded farm launders itself into filament");
        }
        // Not everything a farm touches is a planting item: an iron block must still wind.
        if (com.pgmacdesign.mc3dprint.registry.ModItemTags.isWinderBlacklisted(
                new net.minecraft.world.item.ItemStack(Items.IRON_BLOCK))) {
            throw new GameTestAssertException("the planting rule is over-matching: iron blocks"
                    + " must still wind");
        }
        helper.succeed();
    }

    /**
     * The families that legitimately stay free. Tilled ground has an item that never drops and is
     * a faucet for nothing, and genuinely itemless blocks have nothing to charge at all.
     */
    @GameTest(template = "empty5", timeoutTicks = 40)
    public static void groundAndItemlessBlocksStayFree(GameTestHelper helper) {
        if (!PrinterBlockEntity.isStructuralMatterForTest(Blocks.FARMLAND.defaultBlockState())) {
            throw new GameTestAssertException("farmland must stay free or builds stop printing");
        }
        if (!PrinterBlockEntity.isStructuralMatterForTest(Blocks.DIRT_PATH.defaultBlockState())) {
            throw new GameTestAssertException("dirt path must stay free");
        }
        if (!PrinterBlockEntity.isStructuralMatterForTest(Blocks.WATER.defaultBlockState())) {
            throw new GameTestAssertException("water must stay free structural matter");
        }
        helper.succeed();
    }
}
