package com.pgmacdesign.mc3dprint.integration;

import com.pgmacdesign.mc3dprint.integration.mysticalagriculture.MysticalAgricultureCompat;
import com.pgmacdesign.mc3dprint.integration.mysticalagriculture.MysticalAgricultureCompat.Anchor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The laws the Mystical Agriculture numbers have to obey. The compat hook only fires when the
 * mod is loaded, so nothing else in the suite can catch a bad FU value or tier here.
 *
 * <p>The flat-ladder assertion is the important one. A tier step already multiplies real worth
 * by the conversion ratio, and each rung is a four-into-one craft climbing one tier, so equal
 * FU is exactly break-even and a climbing ladder mints at every rung.
 */
class MysticalAgricultureAnchorsTest {

    private static final int RATIO = 4;
    private static final List<String> LADDER =
            List.of("inferium", "prudentium", "tertium", "imperium", "supremium");

    private static final Map<String, Anchor> BY_PATH = MysticalAgricultureCompat.anchors().stream()
            .collect(Collectors.toMap(Anchor::path, Function.identity()));

    private static Anchor anchor(String path) {
        Anchor a = BY_PATH.get(path);
        assertNotNull(a, path + " is no longer registered");
        return a;
    }

    /** Base-unit worth: 1 FU at tier N is ratio^(N-1) tier-1 units. */
    private static long worth(Anchor a) {
        return (long) a.fu() * (long) Math.pow(RATIO, a.tier() - 1);
    }

    @Test
    void everyAnchorPathIsRegisteredOnce() {
        List<Anchor> all = MysticalAgricultureCompat.anchors();
        assertEquals(all.size(), BY_PATH.size(), "a duplicate path would silently overwrite a value");
    }

    @Test
    void ladderEssencesClimbOneTierPerRung() {
        for (int i = 0; i < LADDER.size(); i++) {
            assertEquals(i + 1, anchor(LADDER.get(i) + "_essence").tier(),
                    LADDER.get(i) + " must sit at tier " + (i + 1));
        }
    }

    @Test
    void ladderFuIsFlatSoNoRungMints() {
        int base = anchor("inferium_essence").fu();
        for (String rung : LADDER) {
            assertEquals(base, anchor(rung + "_essence").fu(),
                    rung + " breaks the flat ladder. Four essences craft into one essence one "
                            + "tier up, and a tier step already multiplies worth by " + RATIO
                            + ", so any climb in the number mints filament on every rung.");
        }
        // The law the flat number encodes, asserted directly rather than by proxy.
        for (int i = 1; i < LADDER.size(); i++) {
            long spent = RATIO * worth(anchor(LADDER.get(i - 1) + "_essence"));
            long got = worth(anchor(LADDER.get(i) + "_essence"));
            assertTrue(got <= spent, LADDER.get(i) + " is worth " + got
                    + " base units but costs " + spent + "; crafting up must never profit");
        }
    }

    @Test
    void nothingReachesTheNetheriteTier() {
        for (Anchor a : MysticalAgricultureCompat.anchors()) {
            assertTrue(a.tier() >= 1 && a.tier() <= 5, a.path()
                    + " sits at tier " + a.tier()
                    + "; farm output must stay below netherite (tier 6) and the trophy tiers");
            assertTrue(a.fu() >= 1, a.path() + " must carry a positive FU value");
        }
    }

    @Test
    void gemstonesNeverOutValueTheDiamondTheyContain() {
        int prosperity = anchor("prosperity_gemstone").fu();
        for (String rung : LADDER) {
            Anchor gem = anchor(rung + "_gemstone");
            assertEquals(5, gem.tier(), "a gemstone carries a diamond, so it sits at diamond tier");
            assertTrue(gem.fu() <= prosperity, rung
                    + "_gemstone at " + gem.fu() + " exceeds the prosperity gemstone it is made "
                    + "from (" + prosperity + "), which makes crafting and winding it a diamond "
                    + "duplicator fed by farmed essence");
        }
    }

    @Test
    void growthAcceleratorStaysUnderItsGemstoneShare() {
        // The recipe consumes one gemstone and yields three accelerators.
        int perUnit = anchor("inferium_gemstone").fu() / 3;
        for (String rung : LADDER) {
            assertTrue(anchor(rung + "_growth_accelerator").fu() <= perUnit, rung
                    + "_growth_accelerator must not exceed a third of the gemstone it consumes");
        }
    }

    @Test
    void ingotsAreFlooredAtTheTierOfTheIronTheyCarry() {
        for (String rung : LADDER) {
            assertTrue(anchor(rung + "_ingot").tier() >= 2, rung
                    + "_ingot contains an iron ingot, so it can never sit below tier 2");
        }
    }

    @Test
    void witherproofBlocksAreAnchoredBecauseTheirEssenceIsNot() {
        // Both are built from wither skeleton essence, which stays unvalued on purpose. Without
        // these the printer skips them and leaves holes in any build that uses them.
        assertEquals(anchor("witherproof_block").tier(), anchor("witherproof_glass").tier());
        assertTrue(anchor("witherproof_block").fu() > 0);
    }
}
