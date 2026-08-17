package com.pgmacdesign.mc3dprint.gametest;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.fu.SpoolItem;
import com.pgmacdesign.mc3dprint.machine.PrinterBlockEntity;
import com.pgmacdesign.mc3dprint.machine.WinderBlockEntity;
import com.pgmacdesign.mc3dprint.registry.ModBlocks;
import com.pgmacdesign.mc3dprint.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Tiered FU economics (default ratio 4): 1 tier-N FU = 4 tier-(N-1) FU. At
 * PRINT time conversion is down-only — high-tier FU covers low-tier costs at
 * the compounded ratio; low-tier FU contributes nothing toward higher-tier
 * costs. At the WINDER the rule is exact-tier: a material only winds into a
 * spool of its own tier (netherite → T6 spool, never a T1 spool).
 */
@GameTestHolder(MC3DPrint.MOD_ID)
@PrefixGameTestTemplate(false)
public class FuTierEconomyGameTests {

    private static PrinterBlockEntity placePrinter(GameTestHelper helper, BlockPos pos, int tierIndex) {
        helper.setBlock(pos, ModBlocks.PRINTERS.get(tierIndex).get());
        if (!(helper.getBlockEntity(pos) instanceof PrinterBlockEntity printer)) {
            throw new GameTestAssertException("Printer block entity missing");
        }
        return printer;
    }

    private static ItemStack spoolWithFu(int tier, int fu) {
        ItemStack spool = new ItemStack(ModItems.SPOOLS.get(tier - 1).get());
        SpoolItem.setFu(spool, fu);
        return spool;
    }

    @GameTest(template = "empty5", timeoutTicks = 100)
    public static void highTierFuStretchesOnCheapJobs(GameTestHelper helper) {
        PrinterBlockEntity printer = placePrinter(helper, new BlockPos(2, 1, 2), 3); // T4
        printer.spoolInventory().setStackInSlot(0, spoolWithFu(3, 10)); // 10 T3 FU

        // 10 T3 FU = 10 * 16 = 160 T1 FU, and exactly 10 toward T3 costs
        if (printer.effectiveFu(1) != 160) {
            helper.fail("Expected 160 effective T1 FU, got " + printer.effectiveFu(1));
        } else if (printer.effectiveFu(3) != 10) {
            helper.fail("Expected 10 effective T3 FU, got " + printer.effectiveFu(3));
        } else {
            helper.succeed();
        }
    }

    @GameTest(template = "empty5", timeoutTicks = 100)
    public static void lowTierFuNeverCoversHigherTierJobs(GameTestHelper helper) {
        PrinterBlockEntity printer = placePrinter(helper, new BlockPos(2, 1, 2), 3); // T4
        printer.spoolInventory().setStackInSlot(0, spoolWithFu(1, 400)); // full-ish T1 spool

        // hard rule: FU never converts up — zero toward any higher tier
        if (printer.effectiveFu(2) != 0) {
            helper.fail("T1 FU must not cover T2 costs, got " + printer.effectiveFu(2));
        } else if (printer.effectiveFu(4) != 0) {
            helper.fail("T1 FU must not cover T4 costs, got " + printer.effectiveFu(4));
        } else if (printer.effectiveFu(1) != 400) {
            helper.fail("T1 FU toward T1 costs should be 1:1, got " + printer.effectiveFu(1));
        } else {
            helper.succeed();
        }
    }

    @GameTest(template = "empty5", timeoutTicks = 300)
    public static void printingDrainsAtTheExchangeRate(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        PrinterBlockEntity printer = placePrinter(helper, pos, 0); // T1
        printer.getCapability(ForgeCapabilities.ENERGY).ifPresent(energy -> {
            for (int i = 0; i < 60; i++) {
                energy.receiveEnergy(1_000, false);
            }
        });
        // T2 spool with 10 FU; cobblestone costs 1 FU @ T1 -> one print drains
        // a whole T2 unit only when the 4 T1-sub-units are exhausted (ceil rule:
        // print #1 consumes 1 T2 unit worth 4; prints overcharge at most 1 unit)
        printer.spoolInventory().setStackInSlot(0, spoolWithFu(2, 10));
        printer.setAutoStart(true); // item-mode gates on Auto; this test wants a print
        printer.inventory().setStackInSlot(PrinterBlockEntity.SLOT_TEMPLATE, new ItemStack(Items.COBBLESTONE));

        helper.succeedWhen(() -> {
            ItemStack output = printer.inventory().getStackInSlot(PrinterBlockEntity.SLOT_OUTPUT);
            if (output.isEmpty() || !output.is(Items.COBBLESTONE)) {
                throw new GameTestAssertException("No cobblestone printed yet");
            }
            int fu = SpoolItem.getFu(printer.spoolInventory().getStackInSlot(0));
            if (fu != 9) {
                helper.fail("Expected 9 T2 FU after one 1-FU@T1 print (ceil drains one unit), got " + fu);
            }
        });
    }

    private static WinderBlockEntity placePoweredWinder(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.FILAMENT_WINDER.get());
        if (!(helper.getBlockEntity(pos) instanceof WinderBlockEntity winder)) {
            throw new GameTestAssertException("Winder block entity missing");
        }
        winder.getCapability(ForgeCapabilities.ENERGY).ifPresent(energy -> {
            for (int i = 0; i < 60; i++) {
                energy.receiveEnergy(1_000, false);
            }
        });
        return winder;
    }

    @GameTest(template = "empty5", timeoutTicks = 150)
    public static void winderRefusesWindingIntoHigherTierSpool(GameTestHelper helper) {
        WinderBlockEntity winder = placePoweredWinder(helper, new BlockPos(2, 1, 2));
        // cobblestone is T1 material; a T2 spool is up-conversion — hard refusal
        winder.inventory().setStackInSlot(WinderBlockEntity.SLOT_SPOOL, spoolWithFu(2, 0));
        winder.inventory().setStackInSlot(WinderBlockEntity.SLOT_INPUT, new ItemStack(Items.COBBLESTONE, 8));

        helper.runAfterDelay(100, () -> {
            int fu = SpoolItem.getFu(winder.inventory().getStackInSlot(WinderBlockEntity.SLOT_SPOOL));
            int inputLeft = winder.inventory().getStackInSlot(WinderBlockEntity.SLOT_INPUT).getCount();
            if (fu != 0) {
                helper.fail("Up-conversion happened: T2 spool gained " + fu + " FU from T1 material");
            } else if (inputLeft != 8) {
                helper.fail("Winder consumed input without a valid spool: " + inputLeft + " left");
            } else {
                helper.succeed();
            }
        });
    }

    @GameTest(template = "empty5", timeoutTicks = 100)
    public static void nuggetAndConcreteValuesRegistered(GameTestHelper helper) {
        // nuggets = ingot/9 rounded DOWN (lossy by design); concrete joins the 5 FU group
        var goldNugget = com.pgmacdesign.mc3dprint.fu.FuValueRegistry.valueOf(new ItemStack(Items.GOLD_NUGGET));
        var ironNugget = com.pgmacdesign.mc3dprint.fu.FuValueRegistry.valueOf(new ItemStack(Items.IRON_NUGGET));
        var concrete = com.pgmacdesign.mc3dprint.fu.FuValueRegistry.valueOf(new ItemStack(Items.RED_CONCRETE));
        if (goldNugget.isEmpty() || goldNugget.get().fu() != 1 || goldNugget.get().tier() != 2) {
            helper.fail("Gold nugget should be 1 FU @ T2, got " + goldNugget);
        } else if (ironNugget.isEmpty() || ironNugget.get().fu() != 2 || ironNugget.get().tier() != 2) {
            helper.fail("Iron nugget should be 2 FU @ T2, got " + ironNugget);
        } else if (concrete.isEmpty() || concrete.get().fu() != 5 || concrete.get().tier() != 1) {
            helper.fail("Concrete should be 5 FU @ T1, got " + concrete);
        } else {
            helper.succeed();
        }
    }

    @GameTest(template = "empty5", timeoutTicks = 100)
    public static void storageBlockValuesRegistered(GameTestHelper helper) {
        // storage blocks carry their crafting contents' value + tier so a low-tier
        // machine can't cheap-print them and the per-block tier gate refuses them
        var netherite = com.pgmacdesign.mc3dprint.fu.FuValueRegistry.valueOf(new ItemStack(Items.NETHERITE_BLOCK));
        var diamond = com.pgmacdesign.mc3dprint.fu.FuValueRegistry.valueOf(new ItemStack(Items.DIAMOND_BLOCK));
        var iron = com.pgmacdesign.mc3dprint.fu.FuValueRegistry.valueOf(new ItemStack(Items.IRON_BLOCK));
        if (netherite.isEmpty() || netherite.get().fu() != 4500 || netherite.get().tier() != 6) {
            helper.fail("Netherite block should be 4500 FU @ T6, got " + netherite);
        } else if (diamond.isEmpty() || diamond.get().fu() != 450 || diamond.get().tier() != 5) {
            helper.fail("Diamond block should be 450 FU @ T5, got " + diamond);
        } else if (iron.isEmpty() || iron.get().fu() != 180 || iron.get().tier() != 2) {
            helper.fail("Iron block should be 180 FU @ T2, got " + iron);
        } else {
            helper.succeed();
        }
    }

    @GameTest(template = "empty5", timeoutTicks = 150)
    public static void winderRefusesWindingIntoLowerTierSpool(GameTestHelper helper) {
        WinderBlockEntity winder = placePoweredWinder(helper, new BlockPos(2, 1, 2));
        // iron is a T2 material; a T1 spool no longer down-winds — exact tier only
        winder.inventory().setStackInSlot(WinderBlockEntity.SLOT_SPOOL, spoolWithFu(1, 0));
        winder.inventory().setStackInSlot(WinderBlockEntity.SLOT_INPUT, new ItemStack(Items.IRON_INGOT, 4));

        helper.runAfterDelay(100, () -> {
            int fu = SpoolItem.getFu(winder.inventory().getStackInSlot(WinderBlockEntity.SLOT_SPOOL));
            int inputLeft = winder.inventory().getStackInSlot(WinderBlockEntity.SLOT_INPUT).getCount();
            if (fu != 0) {
                helper.fail("T1 spool wound a T2 material: spool gained " + fu + " FU");
            } else if (inputLeft != 4) {
                helper.fail("Winder consumed input without a matching spool: " + inputLeft + " left");
            } else {
                helper.succeed();
            }
        });
    }

    @GameTest(template = "empty5", timeoutTicks = 100)
    public static void powderSnowPricedViaBucket(GameTestHelper helper) {
        // Powder snow's block-item IS the powder_snow_bucket (a SolidBucketItem), and the bucket has
        // no recipe — so valuing the bucket is what gives the printed block an FU cost. Without it the
        // block is unpriced and strict mode silently skips it. Verify both halves of that chain.
        boolean blockItemIsBucket =
                net.minecraft.world.level.block.Blocks.POWDER_SNOW.asItem() == Items.POWDER_SNOW_BUCKET;
        var bucket = com.pgmacdesign.mc3dprint.fu.FuValueRegistry.valueOf(new ItemStack(Items.POWDER_SNOW_BUCKET));
        boolean valued = bucket.isPresent() && bucket.get().fu() == 16 && bucket.get().tier() == 2;
        if (!blockItemIsBucket) {
            helper.fail("Powder snow's block-item should be the powder_snow_bucket, got "
                    + net.minecraft.world.level.block.Blocks.POWDER_SNOW.asItem());
        } else if (!valued) {
            helper.fail("powder_snow_bucket should be 16 FU @ T2, got " + bucket);
        } else {
            helper.succeed();
        }
    }

    @GameTest(template = "empty5", timeoutTicks = 150)
    public static void powderSnowBucketIsNotWindable(GameTestHelper helper) {
        WinderBlockEntity winder = placePoweredWinder(helper, new BlockPos(2, 1, 2));
        // Priced at T2 for printing, but winder-blacklisted: even a matching T2 spool must refuse it,
        // so powder snow can't be laundered into FU despite carrying a print value.
        winder.inventory().setStackInSlot(WinderBlockEntity.SLOT_SPOOL, spoolWithFu(2, 0));
        winder.inventory().setStackInSlot(WinderBlockEntity.SLOT_INPUT, new ItemStack(Items.POWDER_SNOW_BUCKET, 1));

        helper.runAfterDelay(100, () -> {
            int fu = SpoolItem.getFu(winder.inventory().getStackInSlot(WinderBlockEntity.SLOT_SPOOL));
            boolean inputKept = !winder.inventory().getStackInSlot(WinderBlockEntity.SLOT_INPUT).isEmpty();
            if (fu != 0) {
                helper.fail("Blacklisted powder_snow_bucket was wound: spool gained " + fu + " FU");
            } else if (!inputKept) {
                helper.fail("Winder consumed a blacklisted powder_snow_bucket");
            } else {
                helper.succeed();
            }
        });
    }

    @GameTest(template = "empty5", timeoutTicks = 150)
    public static void winderWindsIntoMatchingTierSpool(GameTestHelper helper) {
        WinderBlockEntity winder = placePoweredWinder(helper, new BlockPos(2, 1, 2));
        // iron is 20 FU @ T2 -> into a matching T2 spool it deposits 20 T2 FU, 1:1
        winder.inventory().setStackInSlot(WinderBlockEntity.SLOT_SPOOL, spoolWithFu(2, 0));
        winder.inventory().setStackInSlot(WinderBlockEntity.SLOT_INPUT, new ItemStack(Items.IRON_INGOT, 1));

        helper.succeedWhen(() -> {
            if (!winder.inventory().getStackInSlot(WinderBlockEntity.SLOT_INPUT).isEmpty()) {
                throw new GameTestAssertException("Ingot not wound yet");
            }
            int fu = SpoolItem.getFu(winder.inventory().getStackInSlot(WinderBlockEntity.SLOT_SPOOL));
            if (fu != 20) {
                helper.fail("Expected 20 T2 FU from one T2 iron ingot into a T2 spool, got " + fu);
            }
        });
    }

    // --- 1.2.0 windable expansion: new wind targets + T6-access routes ---

    private static boolean valued(net.minecraft.world.item.Item item, int fu, int tier) {
        var v = com.pgmacdesign.mc3dprint.fu.FuValueRegistry.valueOf(new ItemStack(item));
        return v.isPresent() && v.get().fu() == fu && v.get().tier() == tier;
    }

    /** The newly-valued wind targets land at their intended (abundance-capped) tiers. */
    @GameTest(template = "empty5", timeoutTicks = 40)
    public static void newlyValuedWindablesHaveExpectedTiers(GameTestHelper helper) {
        if (!valued(Items.SADDLE, 25, 3)) {
            helper.fail("saddle should be 25@3");
        } else if (!valued(Items.NAME_TAG, 25, 3)) {
            helper.fail("name_tag should be 25@3");
        } else if (!valued(Items.CREEPER_HEAD, 40, 4) || !valued(Items.ZOMBIE_HEAD, 40, 4)
                || !valued(Items.SKELETON_SKULL, 40, 4) || !valued(Items.PIGLIN_HEAD, 40, 4)) {
            helper.fail("all four charged-creeper heads should be 40@4");
        } else if (!valued(Items.WITHER_SKELETON_SKULL, 40, 4)) {
            // capped at T4 (not its T7 rarity) because a wither-skeleton farm is AFK-automatable
            helper.fail("wither_skeleton_skull should be abundance-capped at 40@4");
        } else if (!valued(Items.DRAGON_EGG, 10000, 7)) {
            // unfarmable 1-per-world trophy: a big wind-only payout, pump-safe at any tier
            helper.fail("dragon_egg should be a 10000@7 wind-only trophy");
        } else if (!valued(Items.PHANTOM_MEMBRANE, 30, 3)) {
            helper.fail("phantom_membrane should be 30@3, level with slime_ball");
        } else {
            helper.succeed();
        }
    }

    /**
     * Phantom membrane is BOTH windable and printable, unlike the wind-only trophies above it in
     * this file. Pins both halves, because a value alone would still be inert behind either tag.
     */
    @GameTest(template = "empty5", timeoutTicks = 40)
    public static void phantomMembraneWindsAndPrints(GameTestHelper helper) {
        ItemStack membrane = new ItemStack(Items.PHANTOM_MEMBRANE);
        if (com.pgmacdesign.mc3dprint.registry.ModItemTags.isWinderBlacklisted(membrane)) {
            helper.fail("phantom_membrane must be windable (not on the winder blacklist)");
        } else if (membrane.is(com.pgmacdesign.mc3dprint.registry.ModItemTags.NO_PRINT)) {
            helper.fail("phantom_membrane must be printable (not on #no_print)");
        } else if (membrane.is(com.pgmacdesign.mc3dprint.registry.ModItemTags.PRINT_RESTRICTED)) {
            helper.fail("phantom_membrane must not be gated to official discs");
        } else {
            helper.succeed();
        }
    }

    /**
     * Items pulled off the winder blacklist now wind, while the launder-prone ones they were
     * grouped with stay blocked. Physically winds bamboo (T1) to prove the tag change loads at
     * runtime, not just in the tag data.
     */
    @GameTest(template = "empty5", timeoutTicks = 150)
    public static void previouslyBlacklistedItemsNowWindable(GameTestHelper helper) {
        boolean bambooFree = !com.pgmacdesign.mc3dprint.registry.ModItemTags
                .isWinderBlacklisted(new ItemStack(Items.BAMBOO));
        boolean creeperFree = !com.pgmacdesign.mc3dprint.registry.ModItemTags
                .isWinderBlacklisted(new ItemStack(Items.CREEPER_HEAD));
        boolean dragonFree = !com.pgmacdesign.mc3dprint.registry.ModItemTags
                .isWinderBlacklisted(new ItemStack(Items.DRAGON_HEAD));
        boolean notchAppleFree = !com.pgmacdesign.mc3dprint.registry.ModItemTags
                .isWinderBlacklisted(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE));
        // regular golden_apple is craftable, so it MUST stay blacklisted (laundering guard)
        boolean plainAppleBlocked = com.pgmacdesign.mc3dprint.registry.ModItemTags
                .isWinderBlacklisted(new ItemStack(Items.GOLDEN_APPLE));
        if (!bambooFree || !creeperFree || !dragonFree || !notchAppleFree) {
            helper.fail("bamboo/creeper_head/dragon_head/enchanted_golden_apple must be off the blacklist");
            return;
        }
        if (!plainAppleBlocked) {
            helper.fail("craftable golden_apple must stay winder-blacklisted");
            return;
        }
        WinderBlockEntity winder = placePoweredWinder(helper, new BlockPos(2, 1, 2));
        winder.inventory().setStackInSlot(WinderBlockEntity.SLOT_SPOOL, spoolWithFu(1, 0));
        winder.inventory().setStackInSlot(WinderBlockEntity.SLOT_INPUT, new ItemStack(Items.BAMBOO, 1));
        helper.succeedWhen(() -> {
            if (!winder.inventory().getStackInSlot(WinderBlockEntity.SLOT_INPUT).isEmpty()) {
                throw new GameTestAssertException("bamboo not wound yet");
            }
            int fu = SpoolItem.getFu(winder.inventory().getStackInSlot(WinderBlockEntity.SLOT_SPOOL));
            if (fu != 2) {
                helper.fail("Expected 2 T1 FU from one bamboo into a T1 spool, got " + fu);
            }
        });
    }
}
