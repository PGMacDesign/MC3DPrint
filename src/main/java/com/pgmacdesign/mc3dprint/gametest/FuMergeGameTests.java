package com.pgmacdesign.mc3dprint.gametest;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.fu.FuValue;
import com.pgmacdesign.mc3dprint.fu.FuValueRegistry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The config-no-wipe contract: fuValues is an overrides-only list merged over
 * the built-in defaults — updates ship new defaults without toml edits, config
 * entries win per key, and '=off' removes a value entirely.
 */
@GameTestHolder(MC3DPrint.MOD_ID)
@PrefixGameTestTemplate(false)
public class FuMergeGameTests {

    @GameTest(template = "empty5", timeoutTicks = 20)
    public static void configEntriesMergeOverDefaults(GameTestHelper helper) {
        List<String> defaults = List.of(
                "minecraft:stone=1@1",
                "minecraft:iron_ingot=32@3",
                "#minecraft:planks=2@1");
        List<String> overrides = List.of(
                "minecraft:stone=9@2",          // override an existing default
                "minecraft:diamond=500@5",      // add a value the defaults lack
                "minecraft:iron_ingot=off",     // remove a default
                "#minecraft:planks=4@1",        // tag override REPLACES, never stacks
                "not a valid line");            // malformed -> skipped, never fatal

        Map<Item, FuValue> items = new HashMap<>();
        List<FuValueRegistry.TagEntry> tags = new ArrayList<>();
        int[] counts = FuValueRegistry.loadMerged(defaults, overrides, items, tags);

        assertEq(helper, 3, counts[0], "defaults applied");
        assertEq(helper, 3, counts[1], "overrides applied");
        assertEq(helper, 1, counts[2], "removals");

        assertEq(helper, new FuValue(9, 2), items.get(Items.STONE), "stone overridden");
        assertEq(helper, new FuValue(500, 5), items.get(Items.DIAMOND), "diamond added");
        if (items.containsKey(Items.IRON_INGOT)) {
            helper.fail("iron_ingot should be removed via '=off'");
            return;
        }
        assertEq(helper, 1, tags.size(), "one planks tag entry (replaced, not stacked)");
        assertEq(helper, new FuValue(4, 1), tags.get(0).value(), "planks tag overridden");
        helper.succeed();
    }

    @GameTest(template = "empty5", timeoutTicks = 20)
    public static void emptyConfigYieldsPureDefaults(GameTestHelper helper) {
        Map<Item, FuValue> items = new HashMap<>();
        List<FuValueRegistry.TagEntry> tags = new ArrayList<>();
        int[] counts = FuValueRegistry.loadMerged(FuValueRegistry.defaultEntries(), List.of(), items, tags);
        if (counts[0] <= 0 || counts[1] != 0 || counts[2] != 0) {
            helper.fail("empty config must apply only defaults, got " + counts[0] + "/" + counts[1] + "/" + counts[2]);
            return;
        }
        if (!items.containsKey(Items.COBBLESTONE)) {
            helper.fail("shipped defaults must load (cobblestone missing)");
            return;
        }
        helper.succeed();
    }

    private static void assertEq(GameTestHelper helper, Object expected, Object actual, String what) {
        if (!expected.equals(actual)) {
            throw new net.minecraft.gametest.framework.GameTestAssertException(
                    what + ": expected " + expected + ", got " + actual);
        }
    }
}
