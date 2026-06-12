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
 * spool of its own tier (netherite → T5 spool, never a T1 spool).
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
        if (netherite.isEmpty() || netherite.get().fu() != 4500 || netherite.get().tier() != 5) {
            helper.fail("Netherite block should be 4500 FU @ T5, got " + netherite);
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
}
