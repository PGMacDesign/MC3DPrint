package com.pgmacdesign.mc3dprint.gametest;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.blueprint.Blueprint;
import com.pgmacdesign.mc3dprint.blueprint.BlockStateMatch;
import com.pgmacdesign.mc3dprint.item.BlueprintDiscItem;
import com.pgmacdesign.mc3dprint.machine.PrinterBlockEntity;
import com.pgmacdesign.mc3dprint.scanner.ScanOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Minecraft owns part of every placed block state: stair shape, fence connections and
 * redstone-driven flags are recomputed from the surroundings, and open/closed is whatever the
 * last player to touch the door left behind. A blueprint stores the state it was authored with,
 * so on any build containing stairs those two disagree the moment the build exists.
 *
 * <p>Comparing them by identity made a correct build read as wrong: the ghost preview lit every
 * corner stair red, and a re-print refused to start, since a non-replaceable block that doesn't
 * match is treated as an obstruction. These pin the tolerant comparison that replaced it.
 */
@GameTestHolder(MC3DPrint.MOD_ID)
@PrefixGameTestTemplate(false)
public class WorldDerivedStateGameTests {

    /**
     * The property set itself: game-owned properties are ignored, everything that identifies
     * the block or its placement still has to agree.
     */
    @GameTest(template = "empty5", timeoutTicks = 40)
    public static void gameOwnedPropertiesAreIgnoredAndTheRestAreNot(GameTestHelper helper) {
        BlockState straight = Blocks.OAK_STAIRS.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        BlockState corner = straight.setValue(BlockStateProperties.STAIRS_SHAPE, StairsShape.INNER_LEFT);
        expect(true, straight, corner, "a stair reshaped by its neighbours is still the same stair");
        expect(false, straight.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH), straight,
                "facing is authored, not derived");
        expect(false, straight.setValue(BlockStateProperties.HALF, Half.TOP), straight,
                "half is authored, not derived");
        expect(false, Blocks.SPRUCE_STAIRS.defaultBlockState(), straight, "a different block never matches");

        BlockState door = Blocks.OAK_DOOR.defaultBlockState();
        expect(true, door.setValue(BlockStateProperties.OPEN, true), door, "an open door is the same door");
        expect(false, door.setValue(BlockStateProperties.DOOR_HINGE, DoorHingeSide.RIGHT), door,
                "hinge side is authored, not derived");

        BlockState fence = Blocks.OAK_FENCE.defaultBlockState();
        expect(true, fence.setValue(BlockStateProperties.NORTH, true), fence, "fence connections are derived");

        // A gate drops three pixels when a wall turns up beside it, recomputed from the
        // neighbourhood exactly like a stair's shape.
        BlockState gate = Blocks.OAK_FENCE_GATE.defaultBlockState();
        expect(true, gate.setValue(BlockStateProperties.IN_WALL, true), gate,
                "a gate lowered by the wall next to it is the same gate");
        expect(false, gate.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST), gate,
                "gate facing is authored, not derived");

        BlockState lamp = Blocks.REDSTONE_LAMP.defaultBlockState();
        expect(true, lamp.setValue(BlockStateProperties.LIT, true), lamp, "lit follows the redstone around it");
        helper.succeed();
    }

    private static void expect(boolean matches, BlockState world, BlockState wanted, String why) {
        if (BlockStateMatch.satisfies(world, wanted) != matches) {
            throw new GameTestAssertException((matches ? "expected a match: " : "expected no match: ") + why);
        }
    }

    /**
     * The bug as played: print a build, let the world reshape a stair, print the same disc again.
     * Before the tolerant comparison this stalled on the very first cell — the stair was not
     * replaceable and "did not match", so the whole job was refused as obstructed.
     */
    @GameTest(template = "empty5", timeoutTicks = 400)
    public static void aReshapedStairDoesNotObstructAReprint(GameTestHelper helper) {
        // 2x2x2 at origin (1,2,1) — the geometry a printer at (2,1,2) prints into.
        BlockState stair = Blocks.OAK_STAIRS.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        for (int dx = 0; dx < 2; dx++) {
            for (int dz = 0; dz < 2; dz++) {
                helper.setBlock(new BlockPos(1 + dx, 2, 1 + dz), Blocks.STONE);
                helper.setBlock(new BlockPos(1 + dx, 3, 1 + dz), stair);
            }
        }
        Blueprint blueprint = ScanOperation.capture(helper.getLevel(),
                helper.absolutePos(new BlockPos(1, 2, 1)),
                helper.absolutePos(new BlockPos(2, 3, 2)), "reshaped-stair-test");

        // Leave the build standing and hand the printer the same disc — a repair re-print. One
        // stair now carries the shape a neighbour would have given it, which no reprint can undo.
        BlockPos reshaped = new BlockPos(1, 3, 1);
        helper.setBlock(reshaped, stair.setValue(BlockStateProperties.STAIRS_SHAPE, StairsShape.OUTER_RIGHT));

        java.util.UUID id = com.pgmacdesign.mc3dprint.blueprint.BlueprintFileStore
                .forServer(helper.getLevel().getServer()).save(blueprint);

        BlockPos printerPos = new BlockPos(2, 1, 2);
        helper.setBlock(printerPos, com.pgmacdesign.mc3dprint.registry.ModBlocks.PRINTERS.get(2).get());
        if (!(helper.getBlockEntity(printerPos) instanceof PrinterBlockEntity printer)) {
            throw new GameTestAssertException("printer block entity missing");
        }
        net.minecraft.world.item.ItemStack spool = new net.minecraft.world.item.ItemStack(
                com.pgmacdesign.mc3dprint.registry.ModItems.SPOOLS.get(0).get());
        com.pgmacdesign.mc3dprint.fu.SpoolItem.setFu(spool, 100_000);
        printer.spoolInventory().setStackInSlot(0, spool);
        printer.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ENERGY)
                .ifPresent(energy -> {
                    for (int i = 0; i < 60; i++) {
                        energy.receiveEnergy(1_000, false);
                    }
                });
        net.minecraft.world.item.ItemStack disc = new net.minecraft.world.item.ItemStack(
                com.pgmacdesign.mc3dprint.registry.ModItems.BLUEPRINT_DISC.get());
        BlueprintDiscItem.writeBlueprint(disc, id, blueprint);
        printer.inventory().setStackInSlot(PrinterBlockEntity.SLOT_TEMPLATE, disc);

        printer.setAutoStart(true);
        printer.requestStart();

        helper.succeedWhen(() -> {
            if (printer.state() == PrinterBlockEntity.State.PAUSED_OBSTRUCTED) {
                throw new GameTestAssertException("a stair the world reshaped obstructed its own re-print");
            }
            // The job has to actually run to completion, not merely avoid the obstruction state.
            if (!printer.inventory().getStackInSlot(PrinterBlockEntity.SLOT_OUTPUT)
                    .is(com.pgmacdesign.mc3dprint.registry.ModItems.BLUEPRINT_DISC.get())) {
                throw new GameTestAssertException("re-print never finished (disc not ejected)");
            }
            // ...and the stair is still standing. Its shape is deliberately not asserted: the
            // job's closing reconcile pass re-derives connection shapes from the finished
            // neighbourhood, which is the one thing allowed to change it.
            helper.assertBlockPresent(Blocks.OAK_STAIRS, reshaped);
        });
    }
}
