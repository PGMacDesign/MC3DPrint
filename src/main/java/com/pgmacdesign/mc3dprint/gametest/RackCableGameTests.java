package com.pgmacdesign.mc3dprint.gametest;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.blueprint.Blueprint;
import com.pgmacdesign.mc3dprint.blueprint.BlueprintBlockState;
import com.pgmacdesign.mc3dprint.blueprint.BlueprintFileStore;
import com.pgmacdesign.mc3dprint.fu.IFilamentSource;
import com.pgmacdesign.mc3dprint.fu.SpoolItem;
import com.pgmacdesign.mc3dprint.item.BlueprintDiscItem;
import com.pgmacdesign.mc3dprint.machine.PrinterBlockEntity;
import com.pgmacdesign.mc3dprint.machine.cable.MC3DCableBlockEntity;
import com.pgmacdesign.mc3dprint.machine.rack.FilamentRackBlockEntity;
import com.pgmacdesign.mc3dprint.registry.ModBlocks;
import com.pgmacdesign.mc3dprint.registry.ModCapabilities;
import com.pgmacdesign.mc3dprint.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * The Filament Rack + MC3D Cable: spool storage that doubles as a drainable
 * Filament-Unit reservoir, and a single cable that carries both RF and Filament
 * Units. Verifies the down-only drain contract, direct-touch + cable-relayed
 * filament feeding of a printer, and standard-FE energy relay.
 */
@GameTestHolder(MC3DPrint.MOD_ID)
@PrefixGameTestTemplate(false)
public class RackCableGameTests {

    private static ItemStack spoolWithFu(int tier, int fu) {
        ItemStack spool = new ItemStack(ModItems.SPOOLS.get(tier - 1).get());
        SpoolItem.setFu(spool, fu);
        return spool;
    }

    private static FilamentRackBlockEntity placeRack(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.FILAMENT_RACK.get());
        if (!(helper.getBlockEntity(pos) instanceof FilamentRackBlockEntity rack)) {
            throw new GameTestAssertException("Rack block entity missing at " + pos);
        }
        return rack;
    }

    // --- Rack as a Filament-Unit source ---

    @GameTest(template = "empty5", timeoutTicks = 100)
    public static void rackDrainsExactTier(GameTestHelper helper) {
        FilamentRackBlockEntity rack = placeRack(helper, new BlockPos(2, 1, 2));
        rack.spools().setStackInSlot(0, spoolWithFu(3, 100)); // 100 T3 FU = 1600 base (ratio 4)

        // A spool reports only at its EXACT tier — the printer's tier sweep is what
        // down-converts, so a T3 spool is 0 at every other tier band.
        if (rack.availableExactTier(3) != 1600) {
            helper.fail("Expected 1600 base at exact T3, got " + rack.availableExactTier(3));
            return;
        }
        if (rack.availableExactTier(1) != 0 || rack.availableExactTier(4) != 0) {
            helper.fail("T3 spool must report only at tier 3");
            return;
        }
        // draining 400 base from the T3 band leaves 1200 base (75 T3 FU) on the spool
        long drained = rack.drainExactTier(3, 400);
        if (drained != 400) {
            helper.fail("Expected to drain 400 base, got " + drained);
            return;
        }
        if (SpoolItem.getFu(rack.spools().getStackInSlot(0)) != 75) {
            helper.fail("Spool should have 75 T3 FU left, got "
                    + SpoolItem.getFu(rack.spools().getStackInSlot(0)));
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty5", timeoutTicks = 100)
    public static void rackInsertAndLifoRemoval(GameTestHelper helper) {
        FilamentRackBlockEntity rack = placeRack(helper, new BlockPos(2, 1, 2));
        rack.insertSpool(spoolWithFu(1, 100));
        rack.insertSpool(spoolWithFu(2, 200));
        if (rack.spoolCount() != 2) {
            helper.fail("Expected 2 shelved spools, got " + rack.spoolCount());
            return;
        }
        ItemStack popped = rack.removeSpool(); // LIFO -> the T2 spool
        if (!(popped.getItem() instanceof SpoolItem spool) || spool.tier() != 2) {
            helper.fail("LIFO removal should return the last-inserted (T2) spool");
            return;
        }
        helper.succeed();
    }

    // --- Cable relays Filament Units from a rack across a gap ---

    @GameTest(template = "empty5", timeoutTicks = 100)
    public static void cableRelaysFilamentFromRack(GameTestHelper helper) {
        BlockPos rackPos = new BlockPos(1, 1, 2);
        FilamentRackBlockEntity rack = placeRack(helper, rackPos);
        rack.spools().setStackInSlot(0, spoolWithFu(1, 500));
        helper.setBlock(new BlockPos(2, 1, 2), ModBlocks.MC3DCABLE.get());
        BlockPos farCablePos = new BlockPos(3, 1, 2);
        helper.setBlock(farCablePos, ModBlocks.MC3DCABLE.get());

        if (!(helper.getBlockEntity(farCablePos) instanceof MC3DCableBlockEntity farCable)) {
            throw new GameTestAssertException("Far cable block entity missing");
        }
        // Draining the far cable floods the network and drains the rack two cables away.
        long drained = ((IFilamentSource) farCable).drainExactTier(1, 100);
        if (drained != 100) {
            helper.fail("Cable should relay 100 base from the rack, got " + drained);
            return;
        }
        if (SpoolItem.getFu(rack.spools().getStackInSlot(0)) != 400) {
            helper.fail("Rack spool should drop to 400 FU via the cable, got "
                    + SpoolItem.getFu(rack.spools().getStackInSlot(0)));
            return;
        }
        helper.succeed();
    }

    // --- Cable relays RF as standard Forge Energy ---

    @GameTest(template = "empty5", timeoutTicks = 120)
    public static void cableRelaysEnergyToPrinter(GameTestHelper helper) {
        BlockPos printerPos = new BlockPos(2, 1, 2);
        helper.setBlock(printerPos, ModBlocks.TIER1_PRINTER.get());
        helper.setBlock(new BlockPos(3, 1, 2), ModBlocks.MC3DCABLE.get());
        helper.setBlock(new BlockPos(4, 1, 2), ModBlocks.CREATIVE_ENERGY_SOURCE.get());

        if (!(helper.getBlockEntity(printerPos) instanceof PrinterBlockEntity printer)) {
            throw new GameTestAssertException("Printer block entity missing");
        }
        helper.succeedWhen(() -> printer.getCapability(ForgeCapabilities.ENERGY).ifPresent(energy -> {
            if (energy.getEnergyStored() <= 0) {
                throw new GameTestAssertException("Cable did not relay RF to the printer");
            }
        }));
    }

    // --- Real print: a printer with EMPTY docked spools prints by pulling from an adjacent rack ---

    @GameTest(template = "empty5", timeoutTicks = 300)
    public static void printerPrintsFromAdjacentRack(GameTestHelper helper) {
        BlockPos printerPos = new BlockPos(2, 1, 2);
        helper.setBlock(printerPos, ModBlocks.PRINTERS.get(2).get()); // T3 (first structure-printing tier)
        if (!(helper.getBlockEntity(printerPos) instanceof PrinterBlockEntity printer)) {
            throw new GameTestAssertException("Printer block entity missing");
        }
        printer.getCapability(ForgeCapabilities.ENERGY).ifPresent(energy -> {
            for (int i = 0; i < 60; i++) {
                energy.receiveEnergy(1_000, false);
            }
        });
        printer.setAutoStart(true);

        // No docked spool — the ONLY filament source is the directly-touching rack.
        FilamentRackBlockEntity rack = placeRack(helper, new BlockPos(1, 1, 2));
        rack.spools().setStackInSlot(0, spoolWithFu(3, 6000));
        int initialFu = SpoolItem.getFu(rack.spools().getStackInSlot(0));

        Blueprint blueprint = Blueprint.builder("gametest-rack-feed", 2, 1, 2)
                .set(0, 0, 0, BlueprintBlockState.parse("minecraft:stone"))
                .set(1, 0, 1, BlueprintBlockState.parse("minecraft:glass"))
                .build();
        BlueprintFileStore store = BlueprintFileStore.forServer(helper.getLevel().getServer());
        UUID id = store.save(blueprint);
        ItemStack disc = new ItemStack(ModItems.BLUEPRINT_DISC.get());
        BlueprintDiscItem.writeBlueprint(disc, id, blueprint);
        printer.inventory().setStackInSlot(PrinterBlockEntity.SLOT_TEMPLATE, disc);

        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(1, 2, 1));
            helper.assertBlockPresent(Blocks.GLASS, new BlockPos(2, 2, 2));
            int now = SpoolItem.getFu(rack.spools().getStackInSlot(0));
            if (now >= initialFu) {
                throw new GameTestAssertException("Rack was not drained during the print: " + now);
            }
        });
    }

    /**
     * Tier-smart selection: a printer with a docked T4 spool AND an adjacent rack
     * holding a T1 spool, printing only T1-cost blocks, must spend the rack's
     * cheap T1 and leave the docked T4 untouched — even though the T4 is "docked
     * first" and could cover the cost. Proves selection is lowest-tier-global, not
     * dock-order. (Old slot-order logic would have wasted the T4 on stone.)
     */
    @GameTest(template = "empty5", timeoutTicks = 300)
    public static void tierSmartPrefersCheapRackOverDockedHighTier(GameTestHelper helper) {
        BlockPos printerPos = new BlockPos(2, 1, 2);
        helper.setBlock(printerPos, ModBlocks.PRINTERS.get(2).get()); // T3
        if (!(helper.getBlockEntity(printerPos) instanceof PrinterBlockEntity printer)) {
            throw new GameTestAssertException("Printer block entity missing");
        }
        printer.getCapability(ForgeCapabilities.ENERGY).ifPresent(energy -> {
            for (int i = 0; i < 60; i++) {
                energy.receiveEnergy(1_000, false);
            }
        });
        printer.setAutoStart(true);

        // Docked: an expensive T4 spool. Adjacent rack: a cheap T1 spool.
        printer.spoolInventory().setStackInSlot(0, spoolWithFu(4, 20_000));
        FilamentRackBlockEntity rack = placeRack(helper, new BlockPos(1, 1, 2));
        rack.spools().setStackInSlot(0, spoolWithFu(1, 500));

        // T1-only blueprint so the cheapest qualifying tier is always T1.
        Blueprint blueprint = Blueprint.builder("gametest-tier-smart", 2, 1, 2)
                .set(0, 0, 0, BlueprintBlockState.parse("minecraft:stone"))
                .set(1, 0, 1, BlueprintBlockState.parse("minecraft:stone"))
                .build();
        BlueprintFileStore store = BlueprintFileStore.forServer(helper.getLevel().getServer());
        UUID id = store.save(blueprint);
        ItemStack disc = new ItemStack(ModItems.BLUEPRINT_DISC.get());
        BlueprintDiscItem.writeBlueprint(disc, id, blueprint);
        printer.inventory().setStackInSlot(PrinterBlockEntity.SLOT_TEMPLATE, disc);

        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(1, 2, 1));
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(2, 2, 2));
            if (SpoolItem.getFu(rack.spools().getStackInSlot(0)) >= 500) {
                throw new GameTestAssertException("Cheap T1 rack spool should have been spent");
            }
            if (SpoolItem.getFu(printer.spoolInventory().getStackInSlot(0)) != 20_000) {
                throw new GameTestAssertException("Docked T4 spool must stay untouched for T1 blocks, got "
                        + SpoolItem.getFu(printer.spoolInventory().getStackInSlot(0)));
            }
        });
    }
}
