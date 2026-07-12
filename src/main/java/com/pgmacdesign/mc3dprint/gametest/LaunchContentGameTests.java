package com.pgmacdesign.mc3dprint.gametest;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.blueprint.Blueprint;
import com.pgmacdesign.mc3dprint.blueprint.BlueprintFileStore;
import com.pgmacdesign.mc3dprint.blueprint.CuratedBlueprints;
import com.pgmacdesign.mc3dprint.registry.ModBlocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

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

    @GameTest(template = "empty5", timeoutTicks = 200)
    public static void curatedBlueprintRequiredModsGate(GameTestHelper helper) {
        // PGM-57: a blueprint's required mods are derived from its palette/entity namespaces so a
        // modded build (e.g. an AE2 setup) only surfaces in creative + world loot once that mod is
        // loaded. Verify every curated build's requiredMods() computes without throwing, and that
        // our shipped builds — all vanilla + mc3dprint — declare nothing and are available in dev.
        for (String name : CuratedBlueprints.CURATED_NAMES) {
            java.util.Set<String> mods = CuratedBlueprints.requiredMods(name);
            if (!mods.isEmpty()) {
                helper.fail("Curated build '" + name + "' unexpectedly requires mods " + mods
                        + " — shipped builds must be vanilla/mc3dprint only");
                return;
            }
            if (!CuratedBlueprints.modsAvailable(name)) {
                helper.fail("Curated build '" + name + "' reports unavailable with no required mods");
                return;
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty5", timeoutTicks = 20)
    public static void everyToolGatedBlockIsPickaxeMineable(GameTestHelper helper) {
        // Recurring-bug guardrail (blueprint_repository / filament_rack / redstone_clock were
        // missing): machineProperties() sets requiresCorrectToolForDrops(), so a block that
        // ISN'T in a mineable tag has NO correct tool and silently drops nothing when broken.
        // Every mod block that requires the correct tool must be in mineable/pickaxe (all mod
        // machines are pickaxe-mined). Iterates the live registry so a NEW block added without
        // its tag fails here, not in a player's world.
        StringBuilder missing = new StringBuilder();
        for (var holder : ModBlocks.BLOCKS.getEntries()) {
            BlockState state = holder.get().defaultBlockState();
            if (state.requiresCorrectToolForDrops() && !state.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
                missing.append(' ').append(holder.getId());
            }
        }
        if (!missing.isEmpty()) {
            helper.fail("tool-gated block(s) missing from mineable/pickaxe (break to nothing):"
                    + missing + " — add each to data/minecraft/tags/blocks/mineable/pickaxe.json");
            return;
        }
        // The two machine blocks this fix restored must be iron-tier mineable (like the others),
        // pinning the tier so it can't drift to any-pickaxe or up to diamond.
        for (Block machine : new Block[]{ModBlocks.BLUEPRINT_REPOSITORY.get(), ModBlocks.FILAMENT_RACK.get()}) {
            BlockState state = machine.defaultBlockState();
            if (!new ItemStack(Items.IRON_PICKAXE).isCorrectToolForDrops(state)) {
                helper.fail("iron pickaxe must mine " + BuiltInRegistries.BLOCK.getKey(machine) + " for drops");
                return;
            }
            if (new ItemStack(Items.STONE_PICKAXE).isCorrectToolForDrops(state)) {
                helper.fail(BuiltInRegistries.BLOCK.getKey(machine) + " must require iron tier, not stone");
                return;
            }
        }
        helper.succeed();
    }
}
