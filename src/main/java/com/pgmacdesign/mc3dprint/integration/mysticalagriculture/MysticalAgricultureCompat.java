package com.pgmacdesign.mc3dprint.integration.mysticalagriculture;

import com.pgmacdesign.mc3dprint.fu.FuValueRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Mystical Agriculture ({@code mysticalagriculture}) FU values. Registered only when the mod
 * is loaded; stored by {@link ResourceLocation}, so a vanilla-only install never sees them.
 *
 * <p><b>Why this mod is priced differently from every other compat hook.</b> The others add
 * new materials. This one makes existing materials <em>farmable</em>, which is a direct
 * collision with the abundance rule. So the scope here is deliberately narrow: the mod's own
 * progression currency is valued, and its 138 per-crop essences ({@code iron_essence},
 * {@code diamond_essence}, and so on) are left UNVALUED on purpose. Those are the output of
 * a farm and the input to Infusion Crafting; pricing them would both open a laundering seam
 * and let a printer shortcut a mechanic the mod is built around.
 *
 * <p><b>The ladder is flat at 20 FU on purpose, and must stay that way.</b> A tier step
 * already multiplies real worth by the conversion ratio (4), and every rung of the essence
 * ladder is four essences into one essence one tier up. Equal FU is therefore exactly
 * break-even. Rising numbers double-count the tier: at 10/20/30/40/50 the rungs mint
 * 2.00x, 1.50x, 1.33x and 1.25x, so 256 inferium worth 2,560 base units become one supremium
 * worth 12,800, and since printing is 1:1 at four Efficiency modules that closes into a
 * self-amplifying loop. Do not make these numbers climb.
 *
 * <p><b>Gemstones, growth accelerators and ingots are anchored rather than derived,</b> which
 * is the exception to the usual "only value the leaves" rule and is load-bearing. Derivation
 * sums ingredient FU but takes the <em>highest</em> ingredient tier, so the two tier-1 essences
 * in a gemstone recipe carry their 40 FU up to the diamond's tier 5 and conjure 10,240 base
 * units per craft. Derived, {@code inferium_gemstone} prices at 90 against a real cost of 50:
 * craft, wind, and a farm turns into an infinite diamond machine. Anchoring short-circuits the
 * walk (an explicit value is never re-derived) and is the only way to keep the T1 to T5 ladder
 * without opening that. {@code prosperity_gemstone} is pinned to the diamond inside it for the
 * same reason; derived it comes out at 66 against a 50 FU diamond, a plain duplicator.
 * <b>Do not delete these anchors</b> on the grounds that a recipe exists for them.
 *
 * <p><b>Left to derive</b> (do not add anchors, it only creates drift): {@code <tier>_block}
 * and {@code <tier>_ingot_block} (nine of the base at the same tier, exact break-even),
 * {@code prosperity_ingot}, soulstone bricks/stairs/slabs, witherproof bricks, the machines,
 * seed bases and infusion crystals.
 *
 * <p><b>Left unvalued</b>: everything awakened. {@code awakened_supremium_essence} comes only
 * from the Awakening Altar, a custom recipe the valuator cannot read, so the whole awakened
 * line stays unpriced and that progression gate holds.
 *
 * @see MysticalAgradditionsCompat for the Agradditions ores and the trophy-crop guards
 */
public final class MysticalAgricultureCompat {
    private static final String MA = "mysticalagriculture";

    /** Ladder rungs, low to high; the index is the material tier. */
    private static final String[] LADDER =
            {"inferium", "prudentium", "tertium", "imperium", "supremium"};

    /**
     * Flat FU for every rung. Equal numbers are the break-even condition for a
     * four-into-one craft that climbs one tier; see the class javadoc before changing it.
     */
    private static final int ESSENCE_FU = 20;

    /** One registered value: the item's path within {@value #MA}, its FU, its material tier. */
    public record Anchor(String path, int fu, int tier) {}

    private MysticalAgricultureCompat() {}

    /**
     * Every value this hook registers. Separated from {@link #onCommonSetup} so the laws the
     * numbers have to obey (a flat ladder, nothing above tier 5) are unit-testable without the
     * mod present, since the hook itself never fires in tests.
     */
    public static List<Anchor> anchors() {
        List<Anchor> out = new ArrayList<>();
        for (int i = 0; i < LADDER.length; i++) {
            int tier = i + 1;
            out.add(new Anchor(LADDER[i] + "_essence", ESSENCE_FU, tier));
            // Farmland is essence plus vanilla farmland, which has no item form, so the
            // craft cannot price it. Anchored at the essence it carries.
            out.add(new Anchor(LADDER[i] + "_farmland", ESSENCE_FU, tier));
            // An ingot is one prosperity ingot plus two essences. Derived it mints up to
            // 1.78x, because the prosperity ingot's tier-2 FU rides the essence's tier
            // upward. Floored at tier 2 since it carries an iron ingot.
            out.add(new Anchor(LADDER[i] + "_ingot", 40, Math.max(2, tier)));
            // A gemstone is one prosperity gemstone plus two essences: the diamond
            // dominates, so all five price identically and sit at the diamond's tier.
            out.add(new Anchor(LADDER[i] + "_gemstone", 50, 5));
            // Four essences, four stone and a gemstone yielding three. Set just under the
            // real per-unit cost (about 16.8 FU at tier 5) so printing never mints.
            out.add(new Anchor(LADDER[i] + "_growth_accelerator", 16, 5));
        }

        // Worldgen leaves: mined, no recipe, so nothing derives without these.
        out.add(new Anchor("inferium_ore", ESSENCE_FU, 1));
        out.add(new Anchor("deepslate_inferium_ore", ESSENCE_FU, 1));
        out.add(new Anchor("prosperity_ore", 4, 2));
        out.add(new Anchor("deepslate_prosperity_ore", 4, 2));
        out.add(new Anchor("prosperity_shard", 4, 2));
        out.add(new Anchor("soulium_ore", 10, 3));       // soulium_dust derives by smelting
        out.add(new Anchor("soulstone_cobble", 2, 1));   // soulstone and its family derive

        // Pinned to the diamond it contains so winding it is never better than winding
        // the diamond; derived it prices at 66 and duplicates.
        out.add(new Anchor("prosperity_gemstone", 50, 5));

        // Built from wither skeleton essence, which stays unvalued, so without these two
        // anchors the printer would skip them and leave holes in any build that uses them
        // (recordSkippedBlock, not a whole-blueprint failure). Bricks derive 1:1.
        out.add(new Anchor("witherproof_block", 20, 3));
        out.add(new Anchor("witherproof_glass", 20, 3));
        return List.copyOf(out);
    }

    public static void onCommonSetup(FMLCommonSetupEvent event) {
        if (!ModList.get().isLoaded(MA)) {
            return;
        }
        event.enqueueWork(() -> {
            for (Anchor anchor : anchors()) {
                ResourceLocation id = ResourceLocation.tryParse(MA + ":" + anchor.path());
                if (id != null) {
                    FuValueRegistry.registerApiItemValue(id, anchor.fu(), anchor.tier());
                }
            }
        });
    }
}
