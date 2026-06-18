package com.pgmacdesign.mc3dprint.gametest;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.blueprint.Blueprint;
import com.pgmacdesign.mc3dprint.blueprint.BlueprintBlockState;
import com.pgmacdesign.mc3dprint.blueprint.BlueprintFileStore;
import com.pgmacdesign.mc3dprint.item.BlueprintDiscItem;
import com.pgmacdesign.mc3dprint.item.ResinItem;
import com.pgmacdesign.mc3dprint.machine.PrinterBlockEntity;
import com.pgmacdesign.mc3dprint.machine.resin.ResinEffects;
import com.pgmacdesign.mc3dprint.registry.ModBlocks;
import com.pgmacdesign.mc3dprint.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * Phase 4 resin-effect coverage: deterministic logic asserts for the math-y effects
 * (Overdrive floor, XP formula, plant maturation, ore mapping, treasure table choice),
 * plus integration prints that prove the gate (player-made blueprints reject resin),
 * the consume lifecycle, and two observable effects (Verdant, Quartermaster).
 */
@GameTestHolder(MC3DPrint.MOD_ID)
@PrefixGameTestTemplate(false)
public class CatalystGameTests {

    // ----------------------------------------------------------------- logic

    @GameTest(template = "empty5", timeoutTicks = 40)
    public static void overdriveFloorMath(GameTestHelper helper) {
        assertEq(helper, ResinEffects.overdriveFloor(100, 2, 0.20), 100, "T2 overdrive = break-even");
        assertEq(helper, ResinEffects.overdriveFloor(100, 3, 0.20), 80, "T3 overdrive = 20% below");
        assertEq(helper, ResinEffects.overdriveFloor(0, 3, 0.20), 0, "structural (0 FU) stays free");
        helper.succeed();
    }

    @GameTest(template = "empty5", timeoutTicks = 40)
    public static void xpFormulaCapsAndScales(GameTestHelper helper) {
        assertEq(helper, ResinEffects.bankedXpFor(33000, 3, 160, 550, 1500, 33000), 1500, "T3 max build hits cap");
        assertEq(helper, ResinEffects.bankedXpFor(16500, 3, 160, 550, 1500, 33000), 750, "half-cost build scales");
        assertEq(helper, ResinEffects.bankedXpFor(33000, 1, 160, 550, 1500, 33000), 160, "T1 capped low");
        assertEq(helper, ResinEffects.bankedXpFor(0, 3, 160, 550, 1500, 33000), 0, "no cost = no XP");
        helper.succeed();
    }

    @GameTest(template = "empty5", timeoutTicks = 40)
    public static void verdantMatureLogic(GameTestHelper helper) {
        BlockState wheat = ResinEffects.matureState(Blocks.WHEAT.defaultBlockState(), 1);
        assertEq(helper, wheat.getValue(CropBlock.AGE), 7, "wheat matures to age 7 at T1");
        BlockState wart = ResinEffects.matureState(Blocks.NETHER_WART.defaultBlockState(), 1);
        assertEq(helper, wart.getValue(NetherWartBlock.AGE), 3, "nether wart matures at T1");
        // sweet berries are T2-only: untouched at T1, matured at T2
        BlockState berryT1 = ResinEffects.matureState(Blocks.SWEET_BERRY_BUSH.defaultBlockState(), 1);
        assertEq(helper, berryT1.getValue(SweetBerryBushBlock.AGE), 0, "sweet berries untouched at T1");
        BlockState berryT2 = ResinEffects.matureState(Blocks.SWEET_BERRY_BUSH.defaultBlockState(), 2);
        assertEq(helper, berryT2.getValue(SweetBerryBushBlock.AGE), 3, "sweet berries mature at T2");
        helper.succeed();
    }

    @GameTest(template = "empty5", timeoutTicks = 40)
    public static void oreSaltingMapping(GameTestHelper helper) {
        assertTrue(helper, ResinEffects.isSaltableHost(Blocks.STONE.defaultBlockState()), "stone is saltable");
        assertTrue(helper, ResinEffects.isSaltableHost(Blocks.DEEPSLATE.defaultBlockState()), "deepslate is saltable");
        assertTrue(helper, !ResinEffects.isSaltableHost(Blocks.COBBLESTONE.defaultBlockState()), "cobble is NOT saltable");
        RandomSource rng = RandomSource.create(7);
        BlockState common = ResinEffects.pickOre(Blocks.STONE.defaultBlockState(), rng, 0.0);
        assertTrue(helper, common.getBlock() != Blocks.STONE, "stone salts to an ore (gemShare 0)");
        BlockState gem = ResinEffects.pickOre(Blocks.STONE.defaultBlockState(), rng, 1.0);
        assertTrue(helper, gem.getBlock() == Blocks.DIAMOND_ORE || gem.getBlock() == Blocks.EMERALD_ORE,
                "gemShare 1.0 yields diamond/emerald ore");
        helper.succeed();
    }

    @GameTest(template = "empty5", timeoutTicks = 40)
    public static void treasureTableChoice(GameTestHelper helper) {
        RandomSource rng = RandomSource.create(1);
        assertTrue(helper, ResinEffects.treasureTable(2, rng, 0.0, 0.0).getPath().endsWith("treasure_common"),
                "T2 with 0 rare-chance -> common table");
        assertTrue(helper, ResinEffects.treasureTable(3, rng, 0.0, 0.0).getPath().endsWith("treasure_rare"),
                "T3 base -> rare table");
        assertTrue(helper, ResinEffects.treasureTable(3, rng, 0.0, 1.0).getPath().endsWith("treasure_epic"),
                "T3 with epic-chance 1 -> epic table");
        helper.succeed();
    }

    // ------------------------------------------------------------- integration

    @GameTest(template = "empty5", timeoutTicks = 200)
    public static void verdantMaturesAndConsumesOnOfficialBlueprint(GameTestHelper helper) {
        PrinterBlockEntity printer = poweredPrinter(helper, new BlockPos(2, 1, 2));
        printer.resinInventory().setStackInSlot(0, resin(helper, ResinItem.Effect.VERDANT, 1));
        printer.inventory().setStackInSlot(PrinterBlockEntity.SLOT_TEMPLATE,
                discFor(helper, wheatBlueprint(), false)); // official

        BlockPos wheatPos = new BlockPos(2, 2, 2);
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.WHEAT, wheatPos);
            int age = helper.getBlockState(wheatPos).getValue(CropBlock.AGE);
            if (age != 7) {
                throw new GameTestAssertException("Verdant should print wheat at age 7, got " + age);
            }
            if (!printer.resinInventory().getStackInSlot(0).isEmpty()) {
                throw new GameTestAssertException("Resin should be consumed after a catalyzed print");
            }
        });
    }

    @GameTest(template = "empty5", timeoutTicks = 200)
    public static void resinNoOpsAndIsRetainedOnPlayerBlueprint(GameTestHelper helper) {
        PrinterBlockEntity printer = poweredPrinter(helper, new BlockPos(2, 1, 2));
        printer.resinInventory().setStackInSlot(0, resin(helper, ResinItem.Effect.VERDANT, 1));
        printer.inventory().setStackInSlot(PrinterBlockEntity.SLOT_TEMPLATE,
                discFor(helper, wheatBlueprint(), true)); // player-made -> not resin-eligible

        BlockPos wheatPos = new BlockPos(2, 2, 2);
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.WHEAT, wheatPos);
            int age = helper.getBlockState(wheatPos).getValue(CropBlock.AGE);
            if (age != 0) {
                throw new GameTestAssertException("Player-made blueprint must NOT mature wheat, got age " + age);
            }
            if (printer.resinInventory().getStackInSlot(0).isEmpty()) {
                throw new GameTestAssertException("Resin must NOT be consumed on a player-made blueprint");
            }
        });
    }

    @GameTest(template = "empty5", timeoutTicks = 200)
    public static void quartermasterStocksFurnace(GameTestHelper helper) {
        PrinterBlockEntity printer = poweredPrinter(helper, new BlockPos(2, 1, 2));
        printer.resinInventory().setStackInSlot(0, resin(helper, ResinItem.Effect.QUARTERMASTER, 3));
        printer.inventory().setStackInSlot(PrinterBlockEntity.SLOT_TEMPLATE,
                discFor(helper, furnaceBlueprint(), false)); // official

        BlockPos furnacePos = new BlockPos(2, 2, 2);
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.FURNACE, furnacePos);
            if (!(helper.getBlockEntity(furnacePos) instanceof AbstractFurnaceBlockEntity furnace)) {
                throw new GameTestAssertException("Furnace BE missing after print");
            }
            ItemStack fuel = furnace.getItem(1);
            if (fuel.getItem() != Blocks.COAL_BLOCK.asItem() || fuel.getCount() <= 0) {
                throw new GameTestAssertException("Quartermaster should stock the furnace with coal blocks, got " + fuel);
            }
        });
    }

    // ----------------------------------------------------------------- helpers

    private static Blueprint wheatBlueprint() {
        return Blueprint.builder("gametest-resin-wheat", 1, 1, 1)
                .set(0, 0, 0, BlueprintBlockState.parse("minecraft:wheat[age=0]"))
                .build();
    }

    private static Blueprint furnaceBlueprint() {
        return Blueprint.builder("gametest-resin-furnace", 1, 1, 1)
                .set(0, 0, 0, BlueprintBlockState.parse("minecraft:furnace"))
                .build();
    }

    private static ItemStack discFor(GameTestHelper helper, Blueprint blueprint, boolean playerCreated) {
        BlueprintFileStore store = BlueprintFileStore.forServer(helper.getLevel().getServer());
        UUID id = store.save(blueprint);
        ItemStack disc = new ItemStack(ModItems.BLUEPRINT_DISC.get());
        BlueprintDiscItem.writeBlueprint(disc, id, blueprint, playerCreated);
        return disc;
    }

    private static ItemStack resin(GameTestHelper helper, ResinItem.Effect effect, int tier) {
        for (var ro : ModItems.RESINS) {
            if (ro.get() instanceof ResinItem r && r.effect() == effect && r.tier() == tier) {
                return new ItemStack(ro.get());
            }
        }
        throw new GameTestAssertException("No resin item registered for " + effect + " t" + tier);
    }

    private static PrinterBlockEntity poweredPrinter(GameTestHelper helper, BlockPos localPos) {
        helper.setBlock(localPos, ModBlocks.PRINTERS.get(2).get()); // T3 (first with a print area)
        if (!(helper.getBlockEntity(localPos) instanceof PrinterBlockEntity printer)) {
            throw new GameTestAssertException("Printer block entity missing");
        }
        printer.getCapability(ForgeCapabilities.ENERGY).ifPresent(energy -> {
            for (int i = 0; i < 60; i++) {
                energy.receiveEnergy(1_000, false);
            }
        });
        PrinterGameTests.attachLoadedSpool(printer);
        printer.setAutoStart(true);
        return printer;
    }

    private static void assertEq(GameTestHelper helper, int actual, int expected, String what) {
        if (actual != expected) {
            throw new GameTestAssertException(what + ": expected " + expected + " but got " + actual);
        }
    }

    private static void assertTrue(GameTestHelper helper, boolean cond, String what) {
        if (!cond) {
            throw new GameTestAssertException("Expected true: " + what);
        }
    }
}
