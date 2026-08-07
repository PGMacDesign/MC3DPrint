package com.pgmacdesign.mc3dprint.gametest;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.blueprint.CuratedBlueprints;
import com.pgmacdesign.mc3dprint.blueprint.repository.RepoEntry;
import com.pgmacdesign.mc3dprint.blueprint.repository.RepositoryData;
import com.pgmacdesign.mc3dprint.blueprint.repository.RepositoryIndex;
import com.pgmacdesign.mc3dprint.item.BlueprintDiscItem;
import com.pgmacdesign.mc3dprint.loot.BlueprintLootPool;
import com.pgmacdesign.mc3dprint.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * World-loot behaviour for Blueprint Discs: that the modifier still fires now that
 * table targeting is a path-prefix match rather than a list of conditions, that a
 * build already found is held out of the pool, and that the cycle resets cleanly
 * once everything findable has been found.
 *
 * <p>These drive the real loot pipeline ({@code LootTable#getRandomItems} runs the
 * global loot modifiers), so they cover the wiring an isolated unit test cannot.
 * The pure pool/matcher laws live in {@code BlueprintLootPoolTest}.
 *
 * <p>Scope here is the SHARED ledger, which is the default and needs no player, so
 * these also exercise the playerless path (command loot, hopper pulls).
 */
@GameTestHolder(MC3DPrint.MOD_ID)
@PrefixGameTestTemplate(false)
public class BlueprintLootGameTests {

    private static final ResourceLocation DUNGEON =
            new ResourceLocation("minecraft", "chests/simple_dungeon");
    private static final ResourceLocation RESIN_TABLE =
            new ResourceLocation(MC3DPrint.MOD_ID, "resin/treasure_rare");

    @GameTest(template = "empty5", timeoutTicks = 200)
    public static void chestTablesStillYieldBlueprints(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        freshLedger(server);
        // 200 rolls at the shipped rate; seeing none would mean the modifier stopped
        // firing when its loot_table_id conditions were replaced by the prefix match.
        int seen = 0;
        for (int i = 0; i < 200 && seen == 0; i++) {
            seen += discIds(roll(helper, DUNGEON)).size();
        }
        if (seen == 0) {
            helper.fail("no Blueprint Disc in 200 simple_dungeon rolls; the loot modifier is not firing");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty5", timeoutTicks = 200)
    public static void nonChestTablesNeverYieldBlueprints(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        freshLedger(server);
        // resin/treasure_rare is a chest-param table whose path is NOT under chests/,
        // so the prefix match must skip it however many times it is rolled.
        for (int i = 0; i < 200; i++) {
            if (!discIds(roll(helper, RESIN_TABLE)).isEmpty()) {
                helper.fail("a table outside chests/ produced a Blueprint Disc; the prefix match is not anchored");
                return;
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty5", timeoutTicks = 400)
    public static void foundBuildsAreHeldOutUntilTheCycleCompletes(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        RepositoryData data = freshLedger(server);
        List<String> available = availableBuilds();
        if (available.size() < 4) {
            helper.fail("expected a curated pool larger than 3, got " + available.size());
            return;
        }
        // Leave exactly three findable so the cycle completes in a handful of rolls.
        List<String> remaining = available.subList(available.size() - 3, available.size());
        for (String name : available.subList(0, available.size() - 3)) {
            data.markDiscovered(BlueprintLootPool.idFor(name));
        }

        Set<UUID> collected = new LinkedHashSet<>();
        List<UUID> order = new ArrayList<>();
        for (int i = 0; i < 400 && collected.size() < 3; i++) {
            for (UUID id : discIds(roll(helper, DUNGEON))) {
                order.add(id);
                collected.add(id);
                if (collected.size() == 3) {
                    break;
                }
            }
        }
        if (collected.size() < 3) {
            helper.fail("only drew " + collected.size() + " of the 3 remaining builds in 400 rolls");
            return;
        }
        if (order.size() != collected.size()) {
            helper.fail("a build repeated before the cycle completed: drew " + order.size()
                    + " discs for " + collected.size() + " distinct builds");
            return;
        }
        Set<UUID> expected = new LinkedHashSet<>(BlueprintLootPool.idsFor(remaining));
        if (!collected.equals(expected)) {
            helper.fail("drew builds outside the undiscovered set");
            return;
        }
        // Completing the cycle clears the ledger but retains the build just granted,
        // so the very next roll cannot hand back the same disc.
        UUID completer = order.get(order.size() - 1);
        Set<UUID> after = RepositoryIndex.discoveredIds(server, null);
        if (!after.equals(Set.of(completer))) {
            helper.fail("after completion the ledger held " + after.size()
                    + " entries; expected only the build that completed the cycle");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty5", timeoutTicks = 100)
    public static void cycleResetKeepsOnlyTheRetainedBuild(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        RepositoryData data = freshLedger(server);
        List<String> available = availableBuilds();
        UUID keep = BlueprintLootPool.idFor(available.get(0));
        for (String name : available) {
            data.markDiscovered(BlueprintLootPool.idFor(name));
        }
        data.resetDiscovered(keep);
        if (!data.discovered().equals(Set.of(keep))) {
            helper.fail("reset was not atomic: ledger held " + data.discovered().size() + " entries");
            return;
        }
        // And a reset with nothing retained empties it outright.
        data.resetDiscovered(null);
        if (!data.discovered().isEmpty()) {
            helper.fail("reset with no retained build left the ledger non-empty");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty5", timeoutTicks = 100)
    public static void recordingTheSameBuildTwiceIsANoOp(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        RepositoryData data = freshLedger(server);
        UUID id = BlueprintLootPool.idFor(availableBuilds().get(0));
        if (!data.markDiscovered(id)) {
            helper.fail("first record should report the build as newly discovered");
            return;
        }
        if (data.markDiscovered(id)) {
            helper.fail("re-recording reported the build as new; the ledger is not a set");
            return;
        }
        if (data.discovered().size() != 1) {
            helper.fail("re-recording duplicated the entry");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty5", timeoutTicks = 300)
    public static void catalogueSeedsTheLedgerOnceAndNotAgainAfterAReset(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        RepositoryData data = freshLedger(server);
        String catalogued = availableBuilds().get(0);
        UUID cataloguedId = BlueprintLootPool.idFor(catalogued);
        data.add(new RepoEntry(cataloguedId, catalogued, 3, 3, 3, 27, 1, 100, true));

        // Arm the one-time copy so the next roll performs it.
        data.clearDiscoverySeeded();
        rollUntilDisc(helper, 200);
        if (!RepositoryIndex.discoveredIds(server, null).contains(cataloguedId)) {
            helper.fail("the catalogued build was not seeded into the ledger on the first roll");
            return;
        }
        if (!data.isDiscoverySeeded()) {
            helper.fail("the seed did not record that it had run");
            return;
        }

        // A completed cycle clears the ledger; the catalogue must NOT be re-applied,
        // or a fully-catalogued library would leave the pool permanently empty.
        data.resetDiscovered(null);
        rollUntilDisc(helper, 200);
        Set<UUID> after = RepositoryIndex.discoveredIds(server, null);
        if (after.contains(cataloguedId) && after.size() == 1) {
            helper.fail("the catalogue was re-seeded after a reset; the pool would never refill");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty5", timeoutTicks = 100)
    public static void poolTracksTheModGateExactly(GameTestHelper helper) {
        // A build is in the pool if and only if its required mods are loaded. Cycle
        // completion is measured against this same set: against the full curated list a
        // server missing an optional mod (Coppertide Park needs MC Waterslides) could
        // never finish a cycle, because those builds are permanently unfindable.
        List<String> available = availableBuilds();
        for (String name : CuratedBlueprints.lootBlueprints()) {
            boolean inPool = available.contains(name);
            boolean allowed = CuratedBlueprints.modsAvailable(name);
            if (inPool != allowed) {
                helper.fail(name + (inPool
                        ? " is in the loot pool without its required mods"
                        : " is missing from the loot pool despite its mods being loaded"));
                return;
            }
        }
        helper.succeed();
    }

    // ----------------------------------------------------------------- helpers

    private static List<String> availableBuilds() {
        return CuratedBlueprints.lootBlueprints().stream()
                .filter(CuratedBlueprints::modsAvailable).toList();
    }

    /** Empties the shared ledger and marks it seeded, so each test starts from a known state. */
    private static RepositoryData freshLedger(MinecraftServer server) {
        RepositoryData data = RepositoryData.get(server);
        data.resetDiscovered(null);
        data.markDiscoverySeeded();
        return data;
    }

    private static void rollUntilDisc(GameTestHelper helper, int attempts) {
        for (int i = 0; i < attempts; i++) {
            if (!discIds(roll(helper, DUNGEON)).isEmpty()) {
                return;
            }
        }
    }

    private static Set<UUID> discIds(List<ItemStack> loot) {
        Set<UUID> out = new LinkedHashSet<>();
        for (ItemStack stack : loot) {
            if (stack.is(ModItems.BLUEPRINT_DISC.get())) {
                BlueprintDiscItem.getBlueprintId(stack).ifPresent(out::add);
            }
        }
        return out;
    }

    private static List<ItemStack> roll(GameTestHelper helper, ResourceLocation table) {
        ServerLevel level = helper.getLevel();
        LootTable lootTable = level.getServer().getLootData().getLootTable(table);
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN,
                        Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 1, 1))))
                .create(LootContextParamSets.CHEST);
        return lootTable.getRandomItems(params);
    }
}
