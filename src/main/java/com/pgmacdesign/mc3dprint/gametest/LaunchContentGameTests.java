package com.pgmacdesign.mc3dprint.gametest;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.blueprint.Blueprint;
import com.pgmacdesign.mc3dprint.blueprint.BlueprintFileStore;
import com.pgmacdesign.mc3dprint.blueprint.CuratedBlueprints;
import com.pgmacdesign.mc3dprint.registry.ModBlocks;
import com.pgmacdesign.mc3dprint.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
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

    @GameTest(template = "empty5", timeoutTicks = 20)
    public static void extrudiumOreDropsCrystalNotOre(GameTestHelper helper) {
        // Like diamond ore: a plain (no-silk) pickaxe drops the CRYSTAL (never the ore block),
        // and Silk Touch drops the ore block. Regression guard: the silk-touch match_tool
        // condition used the pre-1.20.5 enchantment-predicate schema, which 1.21 silently drops —
        // leaving the ore branch unconditional so it always won, and the ore dropped as itself.
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockState ore = ModBlocks.EXTRUDIUM_ORE.get().defaultBlockState();

        List<ItemStack> plain = Block.getDrops(ore, level, abs, null, null, new ItemStack(Items.IRON_PICKAXE));
        if (plain.stream().noneMatch(s -> s.is(ModItems.EXTRUDIUM_CRYSTAL.get()))
                || plain.stream().anyMatch(s -> s.is(ModItems.EXTRUDIUM_ORE.get()))) {
            helper.fail("iron pick (no silk) must drop the crystal, not the ore — got " + plain);
            return;
        }

        ItemStack silkPick = new ItemStack(Items.IRON_PICKAXE);
        silkPick.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.SILK_TOUCH), 1);
        List<ItemStack> silk = Block.getDrops(ore, level, abs, null, null, silkPick);
        if (silk.stream().noneMatch(s -> s.is(ModItems.EXTRUDIUM_ORE.get()))
                || silk.stream().anyMatch(s -> s.is(ModItems.EXTRUDIUM_CRYSTAL.get()))) {
            helper.fail("silk touch must drop the ore block, not the crystal — got " + silk);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty5", timeoutTicks = 200)
    public static void fuCacheWarmCoversAllCuratedBlueprints(GameTestHelper helper) {
        // PGM-55: the FU cache is warmed off-thread at world-join so the first creative-tab build
        // isn't a ~10s render-thread freeze. Verify the warm runs clean and covers EVERY curated
        // blueprint — also a smoke test that all their blocks derive an FU value without hanging.
        int warmed = CuratedBlueprints.warmFuCache();
        if (warmed != CuratedBlueprints.CURATED_NAMES.size()) {
            helper.fail("FU warm covered " + warmed + " of " + CuratedBlueprints.CURATED_NAMES.size()
                    + " curated blueprints (a blueprint failed to load?)");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty5", timeoutTicks = 40)
    public static void treasureResinLootRollsAndEnchants(GameTestHelper helper) {
        // The Treasure resin's chest loot uses enchant_with_levels + enchant_randomly, whose
        // schema changed in 1.20.5 (treasure/enchantments -> options). If those fields are stale,
        // 1.21 silently drops them; here we roll the table and require an actually-enchanted item,
        // proving the functions parse (options schema) and apply. Mirrors ResinEffects' roll.
        ServerLevel level = helper.getLevel();
        ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE,
                ResourceLocation.fromNamespaceAndPath(MC3DPrint.MOD_ID, "resin/treasure_rare"));
        LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
        if (table == LootTable.EMPTY) {
            helper.fail("treasure_rare loot table missing");
            return;
        }
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 1, 1))))
                .create(LootContextParamSets.CHEST);
        boolean sawEnchanted = false;
        for (int i = 0; i < 60 && !sawEnchanted; i++) {
            for (ItemStack s : table.getRandomItems(params)) {
                if (!s.getEnchantments().isEmpty() || s.has(DataComponents.STORED_ENCHANTMENTS)) {
                    sawEnchanted = true;
                    break;
                }
            }
        }
        if (!sawEnchanted) {
            helper.fail("treasure_rare produced no enchanted item over 60 rolls — enchant functions misparsed");
            return;
        }
        helper.succeed();
    }

    /**
     * The shipped builds that intentionally require another mod, and exactly which.
     * The Coppertide Park set is built from MC Waterslides blocks so it only surfaces
     * when that mod is installed. Anything NOT listed here must stay vanilla-only.
     */
    private static final java.util.Map<String, java.util.Set<String>> EXPECTED_MODDED_BUILDS =
            java.util.Map.of(
                    "water_park_lagoon", java.util.Set.of("mcwaterslides"),
                    "water_park_coppertop_drop", java.util.Set.of("mcwaterslides"),
                    "water_park_lazy_river", java.util.Set.of("mcwaterslides"),
                    "water_park_glasswyrm", java.util.Set.of("mcwaterslides"),
                    "water_park_pendulum_gorge", java.util.Set.of("mcwaterslides"),
                    "water_park_rainbow_racer", java.util.Set.of("mcwaterslides"));

    @GameTest(template = "empty5", timeoutTicks = 200)
    public static void curatedBlueprintRequiredModsGate(GameTestHelper helper) {
        // PGM-57: a blueprint's required mods are derived from its palette/entity namespaces so a
        // modded build only surfaces in creative + world loot once that mod is loaded. Verify every
        // curated build's requiredMods() computes without throwing, that non-listed builds declare
        // nothing (vanilla + mc3dprint only), and that the deliberately modded builds declare
        // EXACTLY their expected mods with availability matching the mod's actual loaded state.
        for (String name : CuratedBlueprints.CURATED_NAMES) {
            java.util.Set<String> mods = CuratedBlueprints.requiredMods(name);
            java.util.Set<String> expected = EXPECTED_MODDED_BUILDS.getOrDefault(name, java.util.Set.of());
            if (!mods.equals(expected)) {
                helper.fail("Curated build '" + name + "' requires mods " + mods
                        + " but expected " + expected
                        + "; unlisted builds must be vanilla/mc3dprint only");
                return;
            }
            boolean allLoaded = true;
            for (String mod : expected) {
                allLoaded &= net.neoforged.fml.ModList.get().isLoaded(mod);
            }
            if (CuratedBlueprints.modsAvailable(name) != allLoaded) {
                helper.fail("Curated build '" + name + "' availability="
                        + CuratedBlueprints.modsAvailable(name)
                        + " disagrees with loaded-state of its required mods " + expected);
                return;
            }
        }
        helper.succeed();
    }
}
