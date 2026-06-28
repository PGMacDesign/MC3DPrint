package com.pgmacdesign.mc3dprint.gametest;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.blueprint.Blueprint;
import com.pgmacdesign.mc3dprint.blueprint.BlueprintFileStore;
import com.pgmacdesign.mc3dprint.blueprint.CuratedBlueprints;
import com.pgmacdesign.mc3dprint.registry.ModBlocks;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Optional;

@GameTestHolder(MC3DPrint.MOD_ID)
@PrefixGameTestTemplate(false)
public class LaunchContentGameTests {

    @GameTest(template = "empty5", timeoutTicks = 100)
    public static void curatedBlueprintsInstallIntoWorldStore(GameTestHelper helper) {
        CuratedBlueprints.install(helper.getLevel().getServer());
        BlueprintFileStore store = BlueprintFileStore.forServer(helper.getLevel().getServer());

        for (String name : new String[]{"watchtower", "fishing_hut", "garden_shed"}) {
            Optional<Blueprint> blueprint = store.load(CuratedBlueprints.uuidFor(MC3DPrint.MOD_ID, name));
            if (blueprint.isEmpty()) {
                helper.fail("Curated blueprint not installed: " + name);
                return;
            }
            if (blueprint.get().blockCount() == 0) {
                helper.fail("Curated blueprint is empty: " + name);
                return;
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty5", timeoutTicks = 20)
    public static void extrudiumOreMineableWithIronPickaxe(GameTestHelper helper) {
        // PGM-51: the minecraft vanilla-override tags lived under tags/blocks (plural) and
        // silently didn't load on 1.21 — so extrudium_ore (requiresCorrectToolForDrops) was in
        // no loaded mineable/pickaxe tag and couldn't be mined by ANY pickaxe. After moving to
        // tags/block (singular) + needs_iron_tool, an iron-or-better pickaxe must mine it. This
        // test only passes if those override tags actually load, so it guards the regression.
        BlockState ore = ModBlocks.EXTRUDIUM_ORE.get().defaultBlockState();
        if (!ore.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
            helper.fail("extrudium_ore not in mineable/pickaxe — vanilla-override tags not loading");
            return;
        }
        if (!ore.is(BlockTags.NEEDS_IRON_TOOL) || ore.is(BlockTags.NEEDS_DIAMOND_TOOL)) {
            helper.fail("extrudium_ore must require iron tier, not diamond");
            return;
        }
        if (!new ItemStack(Items.IRON_PICKAXE).isCorrectToolForDrops(ore)
                || !new ItemStack(Items.NETHERITE_PICKAXE).isCorrectToolForDrops(ore)) {
            helper.fail("iron/netherite pickaxes must mine extrudium_ore for drops");
            return;
        }
        if (new ItemStack(Items.STONE_PICKAXE).isCorrectToolForDrops(ore)) {
            helper.fail("a stone pickaxe must NOT mine extrudium_ore (iron-tier gate)");
            return;
        }
        helper.succeed();
    }
}
