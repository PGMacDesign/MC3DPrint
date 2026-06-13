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

@GameTestHolder(MC3DPrint.MOD_ID)
@PrefixGameTestTemplate(false)
public class FilamentGameTests {

    @GameTest(template = "empty5", timeoutTicks = 150)
    public static void winderWindsItemOntoSpool(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, ModBlocks.FILAMENT_WINDER.get());
        if (!(helper.getBlockEntity(pos) instanceof WinderBlockEntity winder)) {
            throw new GameTestAssertException("Winder block entity missing");
        }
        winder.getCapability(ForgeCapabilities.ENERGY).ifPresent(energy -> {
            for (int i = 0; i < 20; i++) {
                energy.receiveEnergy(1_000, false);
            }
        });
        winder.inventory().setStackInSlot(WinderBlockEntity.SLOT_INPUT, new ItemStack(Items.COBBLESTONE, 4));
        winder.inventory().setStackInSlot(WinderBlockEntity.SLOT_SPOOL, new ItemStack(ModItems.SPOOLS.get(0).get()));

        // 4 cobblestone @ 1 FU each, 20 ticks per item
        helper.succeedWhen(() -> {
            ItemStack spool = winder.inventory().getStackInSlot(WinderBlockEntity.SLOT_SPOOL);
            if (SpoolItem.getFu(spool) < 4) {
                throw new GameTestAssertException("Spool holds " + SpoolItem.getFu(spool) + " FU, expected 4");
            }
            if (!winder.inventory().getStackInSlot(WinderBlockEntity.SLOT_INPUT).isEmpty()) {
                throw new GameTestAssertException("Input not fully consumed");
            }
        });
    }

    @GameTest(template = "empty5", timeoutTicks = 150)
    public static void winderRefusesMaterialWithoutMatchingSpoolTier(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, ModBlocks.FILAMENT_WINDER.get());
        if (!(helper.getBlockEntity(pos) instanceof WinderBlockEntity winder)) {
            throw new GameTestAssertException("Winder block entity missing");
        }
        winder.getCapability(ForgeCapabilities.ENERGY).ifPresent(energy -> {
            for (int i = 0; i < 20; i++) {
                energy.receiveEnergy(1_000, false);
            }
        });
        // diamond is 50 FU @ tier 5 — a T1 spool can't hold it (exact-tier rule)
        winder.inventory().setStackInSlot(WinderBlockEntity.SLOT_INPUT, new ItemStack(Items.DIAMOND));
        winder.inventory().setStackInSlot(WinderBlockEntity.SLOT_SPOOL, new ItemStack(ModItems.SPOOLS.get(0).get()));

        helper.runAfterDelay(80, () -> {
            ItemStack spool = winder.inventory().getStackInSlot(WinderBlockEntity.SLOT_SPOOL);
            if (SpoolItem.getFu(spool) != 0) {
                helper.fail("T1 spool wound a tier-4 material");
                return;
            }
            if (winder.inventory().getStackInSlot(WinderBlockEntity.SLOT_INPUT).isEmpty()) {
                helper.fail("Diamond was consumed without a matching T4 spool");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty5", timeoutTicks = 60)
    public static void winderSurfacesWrongTierStatus(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, ModBlocks.FILAMENT_WINDER.get());
        if (!(helper.getBlockEntity(pos) instanceof WinderBlockEntity winder)) {
            throw new GameTestAssertException("Winder block entity missing");
        }
        // diamond (T5) + T1 spool: wrong tier — GUI shows red X + "Requires Tier 5 Spool"
        winder.inventory().setStackInSlot(WinderBlockEntity.SLOT_INPUT, new ItemStack(Items.DIAMOND));
        winder.inventory().setStackInSlot(WinderBlockEntity.SLOT_SPOOL, new ItemStack(ModItems.SPOOLS.get(0).get()));
        if (winder.winderStatus() != WinderBlockEntity.STATUS_WRONG_TIER) {
            helper.fail("Expected WRONG_TIER, got " + winder.winderStatus());
            return;
        }
        if (winder.requiredSpoolTier() != 5) {
            helper.fail("Expected required tier 5, got " + winder.requiredSpoolTier());
            return;
        }
        // rotten flesh has no FU value at all (mob drop, no recipe to derive from)
        winder.inventory().setStackInSlot(WinderBlockEntity.SLOT_INPUT, new ItemStack(Items.ROTTEN_FLESH));
        if (winder.winderStatus() != WinderBlockEntity.STATUS_NOT_CONVERTIBLE) {
            helper.fail("Expected NOT_CONVERTIBLE for rotten flesh, got " + winder.winderStatus());
            return;
        }
        // a matching T5 spool clears the status
        winder.inventory().setStackInSlot(WinderBlockEntity.SLOT_INPUT, new ItemStack(Items.DIAMOND));
        winder.inventory().setStackInSlot(WinderBlockEntity.SLOT_SPOOL, new ItemStack(ModItems.SPOOLS.get(4).get()));
        if (winder.winderStatus() != WinderBlockEntity.STATUS_OK) {
            helper.fail("Expected OK with a matching T5 spool, got " + winder.winderStatus());
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty5", timeoutTicks = 150)
    public static void printerWithoutFilamentPauses(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, ModBlocks.TIER1_PRINTER.get());
        if (!(helper.getBlockEntity(pos) instanceof PrinterBlockEntity printer)) {
            throw new GameTestAssertException("Printer block entity missing");
        }
        printer.getCapability(ForgeCapabilities.ENERGY).ifPresent(energy -> {
            for (int i = 0; i < 60; i++) {
                energy.receiveEnergy(1_000, false);
            }
        });
        printer.inventory().setStackInSlot(PrinterBlockEntity.SLOT_TEMPLATE, new ItemStack(Items.STONE));

        helper.runAfterDelay(100, () -> {
            if (printer.state() != PrinterBlockEntity.State.PAUSED_NO_FILAMENT) {
                helper.fail("Expected PAUSED_NO_FILAMENT, got " + printer.state());
                return;
            }
            if (!printer.inventory().getStackInSlot(PrinterBlockEntity.SLOT_OUTPUT).isEmpty()) {
                helper.fail("Printed without filament");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty5", timeoutTicks = 150)
    public static void printerRejectsItemWithoutFuValue(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, ModBlocks.TIER1_PRINTER.get());
        if (!(helper.getBlockEntity(pos) instanceof PrinterBlockEntity printer)) {
            throw new GameTestAssertException("Printer block entity missing");
        }
        printer.getCapability(ForgeCapabilities.ENERGY).ifPresent(energy ->
                energy.receiveEnergy(1_000, false));
        PrinterGameTests.attachLoadedSpool(printer);
        // rotten flesh has no FU value and no recipe to derive one — not printable
        printer.inventory().setStackInSlot(PrinterBlockEntity.SLOT_TEMPLATE, new ItemStack(Items.ROTTEN_FLESH));

        helper.runAfterDelay(40, () -> {
            if (printer.state() != PrinterBlockEntity.State.NOT_PRINTABLE) {
                helper.fail("Expected NOT_PRINTABLE, got " + printer.state());
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty5", timeoutTicks = 200)
    public static void printingDrainsExpectedFu(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, ModBlocks.TIER1_PRINTER.get());
        if (!(helper.getBlockEntity(pos) instanceof PrinterBlockEntity printer)) {
            throw new GameTestAssertException("Printer block entity missing");
        }
        printer.getCapability(ForgeCapabilities.ENERGY).ifPresent(energy -> {
            for (int i = 0; i < 60; i++) {
                energy.receiveEnergy(1_000, false);
            }
        });
        ItemStack spool = new ItemStack(ModItems.SPOOLS.get(0).get());
        SpoolItem.setFu(spool, 100);
        printer.spoolInventory().setStackInSlot(0, spool);
        printer.setAutoStart(true); // item-mode gates on Auto; this test wants a print
        printer.inventory().setStackInSlot(PrinterBlockEntity.SLOT_TEMPLATE, new ItemStack(Items.STONE));

        // stone = 3 FU base, T1 efficiency 50% -> 6 FU per copy
        helper.succeedWhen(() -> {
            if (printer.inventory().getStackInSlot(PrinterBlockEntity.SLOT_OUTPUT).isEmpty()) {
                throw new GameTestAssertException("No output yet");
            }
            int fu = printer.totalFu();
            if (fu > 94) {
                throw new GameTestAssertException("FU not drained, still " + fu);
            }
        });
    }
}
