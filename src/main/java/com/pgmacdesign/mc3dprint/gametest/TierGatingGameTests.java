package com.pgmacdesign.mc3dprint.gametest;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.blueprint.Blueprint;
import com.pgmacdesign.mc3dprint.blueprint.BlueprintBlockState;
import com.pgmacdesign.mc3dprint.blueprint.BlueprintFileStore;
import com.pgmacdesign.mc3dprint.fu.SpoolItem;
import com.pgmacdesign.mc3dprint.item.BlueprintDiscItem;
import com.pgmacdesign.mc3dprint.machine.PrinterBlockEntity;
import com.pgmacdesign.mc3dprint.registry.ModBlocks;
import com.pgmacdesign.mc3dprint.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(MC3DPrint.MOD_ID)
@PrefixGameTestTemplate(false)
public class TierGatingGameTests {

    private static PrinterBlockEntity printer(GameTestHelper helper, Block block, BlockPos pos) {
        helper.setBlock(pos, block);
        if (!(helper.getBlockEntity(pos) instanceof PrinterBlockEntity printer)) {
            throw new GameTestAssertException("Printer block entity missing");
        }
        java.util.Optional.ofNullable(printer.getEnergyStorage()).ifPresent(energy -> {
            for (int i = 0; i < 60; i++) {
                energy.receiveEnergy(1_000, false);
            }
        });
        ItemStack spool = new ItemStack(ModItems.SPOOLS.get(0).get());
        SpoolItem.setFu(spool, 400);
        printer.spoolInventory().setStackInSlot(0, spool);
        printer.setAutoStart(true); // tests exercise gating, not the trigger UX
        return printer;
    }

    @GameTest(template = "empty5", timeoutTicks = 150)
    public static void t1RefusesBlueprintMode(GameTestHelper helper) {
        PrinterBlockEntity t1 = printer(helper, ModBlocks.PRINTERS.get(0).get(), new BlockPos(2, 1, 2));

        Blueprint blueprint = Blueprint.builder("gate-test", 1, 1, 1)
                .set(0, 0, 0, BlueprintBlockState.parse("minecraft:stone"))
                .build();
        BlueprintFileStore store = BlueprintFileStore.forServer(helper.getLevel().getServer());
        UUID id = store.save(blueprint);
        ItemStack disc = new ItemStack(ModItems.BLUEPRINT_DISC.get());
        BlueprintDiscItem.writeBlueprint(disc, id, blueprint);
        t1.inventory().setStackInSlot(PrinterBlockEntity.SLOT_TEMPLATE, disc);

        helper.runAfterDelay(60, () -> {
            if (t1.activeJob() != null) {
                helper.fail("T1 must not start structure jobs (no print area)");
                return;
            }
            // T1/T2 have no print area, so any structure points the player at the
            // smallest structure-capable tier (T3) via NEEDS_HIGHER_TIER.
            if (t1.state() != PrinterBlockEntity.State.NEEDS_HIGHER_TIER) {
                helper.fail("Expected NEEDS_HIGHER_TIER on T1 blueprint mode, got " + t1.state());
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty5", timeoutTicks = 150)
    public static void t1RefusesHighTierItem(GameTestHelper helper) {
        PrinterBlockEntity t1 = printer(helper, ModBlocks.PRINTERS.get(0).get(), new BlockPos(2, 1, 2));
        t1.inventory().setStackInSlot(PrinterBlockEntity.SLOT_TEMPLATE, new ItemStack(Items.DIAMOND)); // T5

        helper.runAfterDelay(60, () -> {
            if (t1.state() != PrinterBlockEntity.State.NOT_PRINTABLE) {
                helper.fail("Expected NOT_PRINTABLE for diamond on T1, got " + t1.state());
                return;
            }
            if (!t1.inventory().getStackInSlot(PrinterBlockEntity.SLOT_OUTPUT).isEmpty()) {
                helper.fail("T1 printed a high-tier item");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty5", timeoutTicks = 200)
    public static void t4PrintsTier4Item(GameTestHelper helper) {
        PrinterBlockEntity t4 = printer(helper, ModBlocks.PRINTERS.get(3).get(), new BlockPos(2, 1, 2));
        // emerald is the T4 gem (diamond is T5); a T4 spool pays its T4 cost 1:1
        ItemStack t4Spool = new ItemStack(ModItems.SPOOLS.get(3).get());
        SpoolItem.setFu(t4Spool, 400);
        t4.spoolInventory().setStackInSlot(0, t4Spool);
        t4.inventory().setStackInSlot(PrinterBlockEntity.SLOT_TEMPLATE, new ItemStack(Items.EMERALD));

        helper.succeedWhen(() -> {
            ItemStack output = t4.inventory().getStackInSlot(PrinterBlockEntity.SLOT_OUTPUT);
            if (output.isEmpty() || !output.is(Items.EMERALD)) {
                throw new GameTestAssertException("T4 has not printed the emerald yet");
            }
        });
    }
}
