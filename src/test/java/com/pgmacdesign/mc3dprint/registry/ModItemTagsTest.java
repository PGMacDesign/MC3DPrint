package com.pgmacdesign.mc3dprint.registry;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Id-only half of the winder gate: the prefix families and the runtime bars compat hooks
 * install. The tag half needs a live item registry and is covered by the gametests.
 */
class ModItemTagsTest {

    private static ResourceLocation id(String namespace, String path) {
        return new ResourceLocation(namespace, path);
    }

    @AfterEach
    void clearRuntimeBars() {
        ModItemTags.clearRuntimeWinderBlocks();
    }

    @Test
    void insaniumIsBarredInEveryForm() {
        for (String form : new String[] {"essence", "ingot", "nugget", "gemstone", "block",
                "ingot_block", "gemstone_block", "farmland", "coal", "coal_block", "apple"}) {
            assertTrue(ModItemTags.isIdWinderBlocked(id("mysticalagradditions", "insanium_" + form)),
                    "insanium_" + form + " must never wind; it is farm output that would reach tier 5");
        }
    }

    @Test
    void prefixMatchDoesNotLeakToNeighbours() {
        assertFalse(ModItemTags.isIdWinderBlocked(id("mysticalagradditions", "supremium_coal")),
                "the supremium line is valued and windable; only insanium is barred");
        assertFalse(ModItemTags.isIdWinderBlocked(id("mysticalagriculture", "supremium_essence")));
        assertFalse(ModItemTags.isIdWinderBlocked(id("someothermod", "insanium_essence")),
                "the prefix carries a namespace, so an unrelated mod's item is untouched");
    }

    @Test
    void rftoolsPrefixStillApplies() {
        assertTrue(ModItemTags.isIdWinderBlocked(id("rftoolsdim", "dimensional_blank")));
        assertFalse(ModItemTags.isIdWinderBlocked(id("rftoolsdim", "dimlet_workbench")));
    }

    @Test
    void trophiesWindUntilACompatHookBarsThem() {
        ResourceLocation netherStar = id("minecraft", "nether_star");
        ResourceLocation dragonEgg = id("minecraft", "dragon_egg");

        assertFalse(ModItemTags.isIdWinderBlocked(netherStar),
                "without Agradditions the trophy values stand and both stay windable");
        assertFalse(ModItemTags.isIdWinderBlocked(dragonEgg));

        ModItemTags.blockWinding(netherStar);
        ModItemTags.blockWinding(dragonEgg);

        assertTrue(ModItemTags.isIdWinderBlocked(netherStar));
        assertTrue(ModItemTags.isIdWinderBlocked(dragonEgg));
    }

    @Test
    void runtimeBarsAreIdempotentAndExact() {
        ResourceLocation netherStar = id("minecraft", "nether_star");
        ModItemTags.blockWinding(netherStar);
        ModItemTags.blockWinding(netherStar);

        assertTrue(ModItemTags.isIdWinderBlocked(netherStar));
        assertFalse(ModItemTags.isIdWinderBlocked(id("minecraft", "nether_star_block")),
                "runtime bars are exact ids, never prefixes");
        assertFalse(ModItemTags.isIdWinderBlocked(id("minecraft", "diamond")),
                "barring a trophy must not touch ordinary build materials");
    }
}
