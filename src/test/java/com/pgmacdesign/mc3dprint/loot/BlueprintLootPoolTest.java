package com.pgmacdesign.mc3dprint.loot;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure-side laws of blueprint world loot: table matching, chance clamping, pool narrowing. */
class BlueprintLootPoolTest {

    private static final List<String> TABLES = BlueprintLootPool.DEFAULT_TABLES;

    private static ResourceLocation id(String namespace, String path) {
        return new ResourceLocation(namespace, path);
    }

    @Test
    void matchesEveryNamespace() {
        assertTrue(BlueprintLootPool.matchesTable(id("minecraft", "chests/simple_dungeon"), TABLES));
        assertTrue(BlueprintLootPool.matchesTable(id("somemod", "chests/tower"), TABLES),
                "a modded structure chest must qualify exactly like a vanilla one");
        assertTrue(BlueprintLootPool.matchesTable(id("minecraft", "chests/trial_chambers/reward"), TABLES));
        assertTrue(BlueprintLootPool.matchesTable(id("minecraft", "archaeology/trail_ruins_rare"), TABLES));
    }

    @Test
    void matchIsAnchoredAtTheStartOfThePath() {
        assertFalse(BlueprintLootPool.matchesTable(id("minecraft", "blocks/chest"), TABLES),
                "block drops must never carry blueprints");
        assertFalse(BlueprintLootPool.matchesTable(id("foo", "xchests/bar"), TABLES),
                "a substring match would pull in unrelated modded tables");
        assertFalse(BlueprintLootPool.matchesTable(id("minecraft", "entities/villager"), TABLES));
        assertFalse(BlueprintLootPool.matchesTable(id("minecraft", "gameplay/fishing/treasure"), TABLES));
    }

    @Test
    void nullTableNeverMatches() {
        assertFalse(BlueprintLootPool.matchesTable(null, TABLES));
    }

    @Test
    void emptyPrefixListMatchesNothing() {
        assertFalse(BlueprintLootPool.matchesTable(id("minecraft", "chests/igloo_chest"), List.of()));
    }

    @Test
    void chanceIsClampedBothWays() {
        assertEquals(0.0F, BlueprintLootPool.effectiveChance(0.4F, 0.0D));
        assertEquals(1.0F, BlueprintLootPool.effectiveChance(0.4F, 10.0D));
        assertEquals(1.0F, BlueprintLootPool.effectiveChance(1.0F, 1.0D));
        assertEquals(0.8F, BlueprintLootPool.effectiveChance(0.4F, 2.0D), 1.0E-6F);
        assertEquals(0.2F, BlueprintLootPool.effectiveChance(0.4F, 0.5D), 1.0E-6F);
    }

    @Test
    void candidatesDropOnlyDiscoveredBuilds() {
        List<String> available = List.of("well", "bakery", "windmill");
        Set<UUID> discovered = Set.of(BlueprintLootPool.idFor("bakery"));
        assertEquals(List.of("well", "windmill"), BlueprintLootPool.candidates(available, discovered));
    }

    @Test
    void candidatesReturnsTheInputWhenNothingIsDiscovered() {
        List<String> available = List.of("well", "bakery");
        assertSame(available, BlueprintLootPool.candidates(available, Set.of()));
    }

    @Test
    void exhaustedPoolIsEmptyWhichIsHowCycleCompletionIsDetected() {
        List<String> available = List.of("well", "bakery");
        Set<UUID> discovered = BlueprintLootPool.idsFor(available);
        assertTrue(BlueprintLootPool.candidates(available, discovered).isEmpty());
    }

    @Test
    void discoveriesOutsideTheAvailableSetNeverShrinkIt() {
        // A mod-gated build discovered while its mod was installed must not keep the pool
        // from refilling once that mod is gone, or the cycle could never complete.
        List<String> available = List.of("well", "bakery");
        Set<UUID> discovered = Set.of(BlueprintLootPool.idFor("water_park_lagoon"));
        assertEquals(available, BlueprintLootPool.candidates(available, discovered));
    }

    @Test
    void idsAreStableAndDistinctPerBuild() {
        assertEquals(BlueprintLootPool.idFor("well"), BlueprintLootPool.idFor("well"));
        assertFalse(BlueprintLootPool.idFor("well").equals(BlueprintLootPool.idFor("bakery")));
        assertEquals(2, BlueprintLootPool.idsFor(List.of("well", "bakery")).size());
    }
}
