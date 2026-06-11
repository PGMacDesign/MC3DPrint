package com.pgmacdesign.mc3dprint.gametest;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.blueprint.Blueprint;
import com.pgmacdesign.mc3dprint.blueprint.BlueprintBlockState;
import com.pgmacdesign.mc3dprint.blueprint.BlueprintFileStore;
import com.pgmacdesign.mc3dprint.scanner.ScanOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(MC3DPrint.MOD_ID)
@PrefixGameTestTemplate(false)
public class ScannerGameTests {

    @GameTest(template = "empty5")
    public static void scanCapturesBlocksAndStates(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.STONE);
        helper.setBlock(new BlockPos(2, 1, 1), Blocks.OAK_STAIRS.defaultBlockState());
        helper.setBlock(new BlockPos(1, 2, 1), Blocks.GLASS);
        // (2,2,1) left as air

        BlockPos absA = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos absB = helper.absolutePos(new BlockPos(2, 2, 1));
        Blueprint blueprint = ScanOperation.capture(helper.getLevel(), absA, absB, "gametest");

        helper.assertTrue(blueprint.sizeX() == 2 && blueprint.sizeY() == 2 && blueprint.sizeZ() == 1,
                "Wrong scan dimensions");
        helper.assertTrue(blueprint.blockCount() == 3, "Expected 3 captured blocks, got " + blueprint.blockCount());

        BlueprintBlockState stone = blueprint.get(0, 0, 0);
        helper.assertTrue(stone != null && "minecraft:stone".equals(stone.blockId()), "Stone not captured at 0,0,0");
        BlueprintBlockState stairs = blueprint.get(1, 0, 0);
        helper.assertTrue(stairs != null && "minecraft:oak_stairs".equals(stairs.blockId())
                && stairs.properties().containsKey("facing"), "Stairs state not captured with properties");
        helper.assertTrue(blueprint.get(1, 1, 0) == null, "Air position must be empty");
        helper.succeed();
    }

    @GameTest(template = "empty5")
    public static void scanCapturesBlockEntityData(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.CHEST);

        BlockPos abs = helper.absolutePos(new BlockPos(1, 1, 1));
        Blueprint blueprint = ScanOperation.capture(helper.getLevel(), abs, abs, "chest-test");

        helper.assertTrue(blueprint.blockEntities().size() == 1, "Chest block entity not captured");
        String id = blueprint.blockEntities().values().iterator().next().getString("id");
        helper.assertTrue("minecraft:chest".equals(id), "Captured block entity id was " + id);
        helper.succeed();
    }

    @GameTest(template = "empty5")
    public static void scannedBlueprintRoundTripsThroughFileStore(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.STONE);
        BlockPos abs = helper.absolutePos(new BlockPos(1, 1, 1));
        Blueprint blueprint = ScanOperation.capture(helper.getLevel(), abs, abs, "store-test");

        BlueprintFileStore store = BlueprintFileStore.forServer(helper.getLevel().getServer());
        UUID id = store.save(blueprint);
        Blueprint loaded = store.load(id).orElse(null);
        store.delete(id);

        helper.assertTrue(blueprint.equals(loaded), "Blueprint changed across file store round trip");
        helper.succeed();
    }
}
