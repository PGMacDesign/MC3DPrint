package com.pgmacdesign.mc3dprint.gametest;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.api.MC3DPrintAPI;
import com.pgmacdesign.mc3dprint.blueprint.Blueprint;
import com.pgmacdesign.mc3dprint.blueprint.BlueprintBlockState;
import com.pgmacdesign.mc3dprint.blueprint.BlueprintFileStore;
import com.pgmacdesign.mc3dprint.fu.FuValue;
import com.pgmacdesign.mc3dprint.fu.FuValueRegistry;
import com.pgmacdesign.mc3dprint.item.BlueprintDiscItem;
import com.pgmacdesign.mc3dprint.machine.PrinterBlockEntity;
import com.pgmacdesign.mc3dprint.registry.ModBlocks;
import com.pgmacdesign.mc3dprint.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Optional;
import java.util.UUID;

/**
 * Recipe-derived FU valuation: storage blocks and crafted items get a value
 * from their recipe graph (no hardcoded entry), strict mode refuses un-priced
 * blocks, and the cross-mod API registers a value for a previously-unknown item.
 */
@GameTestHolder(MC3DPrint.MOD_ID)
@PrefixGameTestTemplate(false)
public class RecipeDerivationGameTests {

    private static FuValue value(net.minecraft.world.item.Item item) {
        Optional<FuValue> v = FuValueRegistry.valueOf(new ItemStack(item));
        if (v.isEmpty()) {
            throw new GameTestAssertException("no FU value for " + item);
        }
        return v.get();
    }

    private static void expect(GameTestHelper helper, String label,
                               net.minecraft.world.item.Item item, int fu, int tier) {
        FuValue v = value(item);
        if (v.fu() != fu || v.tier() != tier) {
            helper.fail(label + " should derive to " + fu + "@" + tier + ", got " + v.fu() + "@" + v.tier());
        }
    }

    @GameTest(template = "empty5", timeoutTicks = 100)
    public static void tntDerivesFromGunpowderTo54T3(GameTestHelper helper) {
        // 5 x gunpowder (10@3) + 4 x sand (1@1) = 54, tier carried up to gunpowder's 3.
        // Guards the whole reason gunpowder is valued at all: before it had a value, TNT
        // was unpriced and strict mode refused to print it.
        expect(helper, "tnt", Items.TNT, 54, 3);
        helper.succeed();
    }

    @GameTest(template = "empty5", timeoutTicks = 100)
    public static void fireworkRocketDerivesTo4T3(GameTestHelper helper) {
        // 1 gunpowder (10@3) + 1 paper (2@1) = 12, but the recipe yields THREE rockets and
        // the valuator divides by output count -> 4@3. Deliberately asserted: rockets are the
        // cheapest thing gunpowder unlocks, so a future gunpowder re-price should have to
        // notice it moved this too.
        expect(helper, "firework_rocket", Items.FIREWORK_ROCKET, 4, 3);
        helper.succeed();
    }

    @GameTest(template = "empty5", timeoutTicks = 100)
    public static void diamondBlockDerivesTo450T5(GameTestHelper helper) {
        // 9 x diamond (50@5) / 1 -> 450, tier carried up to 5. No explicit entry.
        expect(helper, "diamond_block", Items.DIAMOND_BLOCK, 450, 5);
        helper.succeed();
    }

    @GameTest(template = "empty5", timeoutTicks = 100)
    public static void emptyRecipeBindDoesNotClobberDerivation(GameTestHelper helper) {
        // 1.21.5+ sends clients an empty recipe map; in single-player the client bind
        // shares this static registry and must NOT wipe the server's derived values.
        // purpur_block is recipe-derived (popped_chorus_fruit) — present before.
        if (FuValueRegistry.valueOf(new ItemStack(Items.PURPUR_BLOCK)).isEmpty()) {
            helper.fail("purpur_block should be derived before the empty bind");
            return;
        }
        var server = helper.getLevel().getServer();
        var goodRecipes = server.getRecipeManager().getRecipes();
        try {
            // simulate the spurious empty client bind
            FuValueRegistry.bind(java.util.List.of(), server.registryAccess());
            if (FuValueRegistry.valueOf(new ItemStack(Items.PURPUR_BLOCK)).isEmpty()) {
                helper.fail("empty bind wiped recipe-derived values — the clobber guard failed");
                return;
            }
        } finally {
            // restore the real binding for any later test
            FuValueRegistry.bind(goodRecipes, server.registryAccess());
        }
        helper.succeed();
    }

    @GameTest(template = "empty5", timeoutTicks = 100)
    public static void netheriteBlockDerivesTo4500T6(GameTestHelper helper) {
        expect(helper, "netherite_block", Items.NETHERITE_BLOCK, 4500, 6);
        helper.succeed();
    }

    @GameTest(template = "empty5", timeoutTicks = 100)
    public static void storageBlocksDeriveAcrossTheTable(GameTestHelper helper) {
        expect(helper, "iron_block", Items.IRON_BLOCK, 180, 2);
        expect(helper, "gold_block", Items.GOLD_BLOCK, 135, 2);
        expect(helper, "copper_block", Items.COPPER_BLOCK, 90, 2);
        expect(helper, "lapis_block", Items.LAPIS_BLOCK, 90, 2);
        expect(helper, "redstone_block", Items.REDSTONE_BLOCK, 36, 3);
        expect(helper, "slime_block", Items.SLIME_BLOCK, 270, 3);
        expect(helper, "emerald_block", Items.EMERALD_BLOCK, 450, 4);
        expect(helper, "amethyst_block", Items.AMETHYST_BLOCK, 40, 2);
        // coal itself is now a base (2@1) so coal_block derives from 9 coal -> 18@1
        expect(helper, "coal_block", Items.COAL_BLOCK, 18, 1);
        helper.succeed();
    }

    @GameTest(template = "empty5", timeoutTicks = 100)
    public static void craftedItemDerivesAndInheritsTier(GameTestHelper helper) {
        // diamond_sword = 2 diamond (50@5) + 1 stick; sticks have no explicit FU
        // entry but DERIVE a value from planks (#planks=3@1) — that derived stick
        // value is exactly why sticks are on the winder blacklist (printable, but
        // not windable back into filament). value = floor((50+50+3)/1) = 103, tier = 5.
        FuValue sword = value(Items.DIAMOND_SWORD);
        if (sword.tier() != 5) {
            helper.fail("diamond_sword should inherit T5 from diamond, got tier " + sword.tier());
            return;
        }
        if (sword.fu() < 100) {
            helper.fail("diamond_sword FU should be >= 100 (2 diamonds + stick), got " + sword.fu());
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty5", timeoutTicks = 100)
    public static void derivedT5ItemGatedByMachineTier(GameTestHelper helper) {
        // diamond_sword derives to T5; the per-tier gate must refuse it on a T4
        // machine (cost -1) — proving the derived tier flows into item gating.
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, ModBlocks.PRINTERS.get(3).get()); // T4 (highest single block)
        if (!(helper.getBlockEntity(pos) instanceof PrinterBlockEntity printer)) {
            throw new GameTestAssertException("Printer block entity missing");
        }
        int t4cost = printer.itemFuCost(new ItemStack(Items.DIAMOND_SWORD));
        if (t4cost != -1) {
            helper.fail("T4 machine must refuse a derived-T5 diamond_sword (cost -1), got " + t4cost);
            return;
        }
        // the cost denomination tier comes straight from the derived value (T5)
        if (printer.itemFuTier(new ItemStack(Items.DIAMOND_SWORD)) != 5) {
            helper.fail("diamond_sword cost tier should be 5, got "
                    + printer.itemFuTier(new ItemStack(Items.DIAMOND_SWORD)));
            return;
        }
        helper.succeed();
    }

    private static PrinterBlockEntity poweredT3(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.PRINTERS.get(2).get()); // T3 (first with a print area)
        if (!(helper.getBlockEntity(pos) instanceof PrinterBlockEntity printer)) {
            throw new GameTestAssertException("Printer block entity missing");
        }
        java.util.Optional.ofNullable(printer.getEnergyStorage()).ifPresent(energy -> {
            for (int i = 0; i < 60; i++) {
                energy.receiveEnergy(1_000, false);
            }
        });
        PrinterGameTests.attachLoadedSpool(printer);
        printer.setAutoStart(true);
        return printer;
    }

    private static ItemStack discFor(GameTestHelper helper, Blueprint blueprint) {
        BlueprintFileStore store = BlueprintFileStore.forServer(helper.getLevel().getServer());
        UUID id = store.save(blueprint);
        ItemStack disc = new ItemStack(ModItems.BLUEPRINT_DISC.get());
        BlueprintDiscItem.writeBlueprint(disc, id, blueprint);
        return disc;
    }

    @GameTest(template = "empty5", timeoutTicks = 100)
    public static void strictModeRefusesUnpricedBlock(GameTestHelper helper) {
        // bedrock has no recipe and no explicit/API value; with the default
        // strict mode (unknownBlocksPrintable=false) the whole structure is
        // NOT_PRINTABLE — the core anti-exploit gate.
        if (com.pgmacdesign.mc3dprint.config.MC3DPrintConfig.UNKNOWN_BLOCKS_PRINTABLE.get()) {
            helper.fail("test assumes default strict mode (unknownBlocksPrintable=false)");
            return;
        }
        if (FuValueRegistry.valueOf(new ItemStack(Items.BEDROCK)).isPresent()) {
            helper.fail("bedrock should have no FU value");
            return;
        }
        PrinterBlockEntity printer = poweredT3(helper, new BlockPos(2, 1, 2));
        Blueprint blueprint = Blueprint.builder("gametest-bedrock", 1, 1, 1)
                .set(0, 0, 0, BlueprintBlockState.parse("minecraft:bedrock"))
                .build();
        printer.inventory().setStackInSlot(PrinterBlockEntity.SLOT_TEMPLATE, discFor(helper, blueprint));

        helper.runAfterDelay(40, () -> {
            if (printer.state() != PrinterBlockEntity.State.NOT_PRINTABLE) {
                helper.fail("strict mode must refuse an un-priced bedrock structure, got " + printer.state());
                return;
            }
            helper.assertBlockNotPresent(net.minecraft.world.level.block.Blocks.BEDROCK, new BlockPos(2, 2, 2));
            helper.succeed();
        });
    }

    @GameTest(template = "empty5", timeoutTicks = 100)
    public static void apiRegistrationValuesUnknownItem(GameTestHelper helper) {
        // a vanilla item with no FU value; the API gives it one, consulted at the
        // documented precedence (API > derived). Use a stable id unlikely to be
        // priced by the default economy or any recipe path.
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(Items.FEATHER);
        if (FuValueRegistry.valueOf(new ItemStack(Items.FEATHER)).isPresent()) {
            helper.fail("precondition: feather should start unvalued");
            return;
        }
        MC3DPrintAPI.registerFuValue(id, 42, 3);
        FuValue v = value(Items.FEATHER);
        if (v.fu() != 42 || v.tier() != 3) {
            helper.fail("API registration should value feather 42@3, got " + v.fu() + "@" + v.tier());
            return;
        }
        helper.succeed();
    }
}
