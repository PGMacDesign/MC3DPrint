package com.pgmacdesign.mc3dprint.gametest;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.blueprint.Blueprint;
import com.pgmacdesign.mc3dprint.blueprint.BlueprintBlockState;
import com.pgmacdesign.mc3dprint.blueprint.BlueprintFileStore;
import com.pgmacdesign.mc3dprint.item.BlueprintDiscItem;
import com.pgmacdesign.mc3dprint.machine.PrinterBlockEntity;
import com.pgmacdesign.mc3dprint.registry.ModBlocks;
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

@GameTestHolder(MC3DPrint.MOD_ID)
@PrefixGameTestTemplate(false)
public class StructurePrintGameTests {

    /** 2x1x2 blueprint: stone at (0,0,0), glass at (1,0,1). */
    private static Blueprint smallBlueprint() {
        return Blueprint.builder("gametest-structure", 2, 1, 2)
                .set(0, 0, 0, BlueprintBlockState.parse("minecraft:stone"))
                .set(1, 0, 1, BlueprintBlockState.parse("minecraft:glass"))
                .build();
    }

    private static ItemStack discFor(GameTestHelper helper, Blueprint blueprint) {
        BlueprintFileStore store = BlueprintFileStore.forServer(helper.getLevel().getServer());
        UUID id = store.save(blueprint);
        ItemStack disc = new ItemStack(ModItems.BLUEPRINT_DISC.get());
        BlueprintDiscItem.writeBlueprint(disc, id, blueprint);
        return disc;
    }

    private static PrinterBlockEntity poweredPrinter(GameTestHelper helper, BlockPos localPos) {
        // T3 — the first tier with a structure print area (3x3)
        helper.setBlock(localPos, ModBlocks.PRINTERS.get(2).get());
        if (!(helper.getBlockEntity(localPos) instanceof PrinterBlockEntity printer)) {
            throw new GameTestAssertException("Printer block entity missing");
        }
        printer.getCapability(ForgeCapabilities.ENERGY).ifPresent(energy -> {
            for (int i = 0; i < 60; i++) {
                energy.receiveEnergy(1_000, false);
            }
        });
        PrinterGameTests.attachLoadedSpool(printer);
        printer.setAutoStart(true); // tests exercise the print path, not the trigger UX
        return printer;
    }

    @GameTest(template = "empty5", timeoutTicks = 300)
    public static void printsStructureFromDiscAndEjectsDisc(GameTestHelper helper) {
        BlockPos printerPos = new BlockPos(2, 1, 2);
        PrinterBlockEntity printer = poweredPrinter(helper, printerPos);
        printer.inventory().setStackInSlot(PrinterBlockEntity.SLOT_TEMPLATE, discFor(helper, smallBlueprint()));

        // origin = printer + (-1, 1, -1) for a 2x1x2 blueprint -> stone at (1,2,1), glass at (2,2,2)
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(1, 2, 1));
            helper.assertBlockPresent(Blocks.GLASS, new BlockPos(2, 2, 2));
            ItemStack output = printer.inventory().getStackInSlot(PrinterBlockEntity.SLOT_OUTPUT);
            if (!(output.getItem() instanceof BlueprintDiscItem)) {
                throw new GameTestAssertException("Disc not ejected to output after completion");
            }
            if (!printer.inventory().getStackInSlot(PrinterBlockEntity.SLOT_TEMPLATE).isEmpty()) {
                throw new GameTestAssertException("Template slot should be empty after completion");
            }
        });
    }

    @GameTest(template = "empty5", timeoutTicks = 200)
    public static void manualModeWaitsForStartTrigger(GameTestHelper helper) {
        BlockPos printerPos = new BlockPos(2, 1, 2);
        PrinterBlockEntity printer = poweredPrinter(helper, printerPos);
        printer.setAutoStart(false); // default shipping behavior: trigger required
        printer.inventory().setStackInSlot(PrinterBlockEntity.SLOT_TEMPLATE, discFor(helper, smallBlueprint()));

        helper.runAfterDelay(60, () -> {
            if (printer.activeJob() != null) {
                helper.fail("Job started without a trigger in manual mode");
                return;
            }
            if (printer.state() != PrinterBlockEntity.State.READY) {
                helper.fail("Expected READY while awaiting trigger, got " + printer.state());
                return;
            }
            printer.requestStart(); // the GUI Start button / redstone edge calls this
        });
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(1, 2, 1));
            helper.assertBlockPresent(Blocks.GLASS, new BlockPos(2, 2, 2));
        });
    }

    @GameTest(template = "empty5", timeoutTicks = 300)
    public static void buildOffsetsShiftTheOrigin(GameTestHelper helper) {
        BlockPos printerPos = new BlockPos(2, 1, 2);
        PrinterBlockEntity printer = poweredPrinter(helper, printerPos);
        printer.adjustOffset(0, 1);  // X +1
        printer.adjustOffset(1, 1);  // Y +1
        printer.adjustOffset(2, -1); // Z -1
        printer.inventory().setStackInSlot(PrinterBlockEntity.SLOT_TEMPLATE, discFor(helper, smallBlueprint()));

        // default origin (1,2,1) shifted by (+1,+1,-1) -> stone at (2,3,0), glass at (3,3,1)
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(2, 3, 0));
            helper.assertBlockPresent(Blocks.GLASS, new BlockPos(3, 3, 1));
        });
    }

    @GameTest(template = "empty5", timeoutTicks = 200)
    public static void refusesToStartWhenAreaObstructed(GameTestHelper helper) {
        BlockPos printerPos = new BlockPos(2, 1, 2);
        PrinterBlockEntity printer = poweredPrinter(helper, printerPos);
        helper.setBlock(new BlockPos(1, 2, 1), Blocks.OBSIDIAN); // blocks the stone target
        printer.inventory().setStackInSlot(PrinterBlockEntity.SLOT_TEMPLATE, discFor(helper, smallBlueprint()));

        helper.runAfterDelay(120, () -> {
            if (printer.state() != PrinterBlockEntity.State.PAUSED_OBSTRUCTED) {
                helper.fail("Expected PAUSED_OBSTRUCTED, got " + printer.state());
                return;
            }
            helper.assertBlockNotPresent(Blocks.GLASS, new BlockPos(2, 2, 2));
            if (printer.activeJob() != null) {
                helper.fail("Job must not start while obstructed");
                return;
            }
            helper.succeed();
        });
    }

    /** 3x1x3 solid stone — long enough that the first job is still running during the assertion. */
    private static Blueprint slowBlueprint() {
        Blueprint.Builder builder = Blueprint.builder("gametest-slow", 3, 1, 3);
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                builder.set(x, 0, z, BlueprintBlockState.parse("minecraft:stone"));
            }
        }
        return builder.build();
    }

    @GameTest(template = "empty5", timeoutTicks = 200)
    public static void overlappingPrintZonesConflict(GameTestHelper helper) {
        PrinterBlockEntity first = poweredPrinter(helper, new BlockPos(1, 1, 1));
        PrinterBlockEntity second = poweredPrinter(helper, new BlockPos(2, 1, 1));

        first.inventory().setStackInSlot(PrinterBlockEntity.SLOT_TEMPLATE, discFor(helper, slowBlueprint()));
        // let the first claim its zone, then load the second
        helper.runAfterDelay(10, () ->
                second.inventory().setStackInSlot(PrinterBlockEntity.SLOT_TEMPLATE, discFor(helper, slowBlueprint())));

        helper.runAfterDelay(40, () -> {
            if (first.activeJob() == null) {
                helper.fail("First printer should still be printing, state " + first.state());
                return;
            }
            if (second.state() != PrinterBlockEntity.State.ZONE_CONFLICT) {
                helper.fail("Expected ZONE_CONFLICT on second printer, got " + second.state());
                return;
            }
            helper.succeed();
        });
    }
}
