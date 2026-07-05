package com.pgmacdesign.mc3dprint.loot;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Seeded coverage of the flavor-biased resin pick (catalysts-design Q15). */
class AddCatalystFlavorTest {

    private static final List<String> ALL = List.of("xp", "treasure", "overdrive", "quartermaster", "salting");
    private static final List<AddCatalystModifier.Flavor> FLAVORS = List.of(
            new AddCatalystModifier.Flavor("stronghold_library", List.of("xp")),
            new AddCatalystModifier.Flavor("ancient_city", List.of("xp")),
            new AddCatalystModifier.Flavor("bastion", List.of("treasure")),
            new AddCatalystModifier.Flavor("stronghold", List.of("salting")));

    @Test
    void fullBiasAlwaysDrawsFromTheMatchedPool() {
        RandomSource random = RandomSource.create(42);
        for (int i = 0; i < 200; i++) {
            assertEquals("xp", AddCatalystModifier.pickResin(
                    "minecraft:chests/ancient_city", random, ALL, FLAVORS, 1.0F));
        }
    }

    @Test
    void zeroBiasIsPureUniform() {
        RandomSource random = RandomSource.create(42);
        boolean sawNonFlavored = false;
        for (int i = 0; i < 200; i++) {
            String pick = AddCatalystModifier.pickResin(
                    "minecraft:chests/ancient_city", random, ALL, FLAVORS, 0.0F);
            assertTrue(ALL.contains(pick));
            sawNonFlavored |= !pick.equals("xp");
        }
        assertTrue(sawNonFlavored, "bias 0 must fall back to the uniform pool");
    }

    @Test
    void unmappedTablesStayUniform() {
        RandomSource random = RandomSource.create(7);
        boolean sawNonFlavored = false;
        for (int i = 0; i < 200; i++) {
            String pick = AddCatalystModifier.pickResin(
                    "minecraft:chests/nether_fortress_other", random, ALL, FLAVORS, 1.0F);
            assertTrue(ALL.contains(pick));
            sawNonFlavored |= !pick.equals("xp");
        }
        assertTrue(sawNonFlavored, "unmapped table must use the full pool");
    }

    @Test
    void firstMatchWinsSoSpecificEntriesPrecedeBroadOnes() {
        RandomSource random = RandomSource.create(1);
        // stronghold_library appears before the broad "stronghold" entry → xp, never salting
        for (int i = 0; i < 100; i++) {
            assertEquals("xp", AddCatalystModifier.pickResin(
                    "minecraft:chests/stronghold_library", random, ALL, FLAVORS, 1.0F));
        }
        // any other stronghold table falls through to the broad entry
        for (int i = 0; i < 100; i++) {
            assertEquals("salting", AddCatalystModifier.pickResin(
                    "minecraft:chests/stronghold_corridor", random, ALL, FLAVORS, 1.0F));
        }
    }

    @Test
    void nullTableIdNeverThrows() {
        RandomSource random = RandomSource.create(3);
        assertTrue(ALL.contains(AddCatalystModifier.pickResin(null, random, ALL, FLAVORS, 1.0F)));
    }
}
