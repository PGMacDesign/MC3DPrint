package com.pgmacdesign.mc3dprint.gametest;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.blueprint.Blueprint;
import com.pgmacdesign.mc3dprint.blueprint.BlueprintBlockState;
import com.pgmacdesign.mc3dprint.blueprint.CuratedBlueprints;
import com.pgmacdesign.mc3dprint.fu.FuValueRegistry;
import com.pgmacdesign.mc3dprint.machine.PrinterBlockEntity;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Guardrail: every block in every curated blueprint must be printable — i.e. it
 * either has an FU value OR is "structural matter" (farmland, crops, water, wall
 * torches, …) OR is air (empty cell, skipped). Any block that passes none of those
 * checks would be silently skipped at print time (strict mode), so a blueprint
 * containing one would not reproduce faithfully.
 *
 * <p>Printability is evaluated with the EXACT same production logic the real
 * printer uses:
 * <ul>
 *   <li>{@link FuValueRegistry#valueOf(ItemStack)} — resolves against the live
 *       registry + bound RecipeManager (available in a GameTest server).</li>
 *   <li>{@link PrinterBlockEntity#isStructuralMatterForTest(BlockState)} — the
 *       same private logic exposed via a package-private accessor so we don't
 *       duplicate the three-family check.</li>
 * </ul>
 *
 * <p>This test lives in the GameTest harness (not plain JUnit) because
 * {@link BlueprintBlockState#resolve()} requires Forge block registries to be
 * loaded, and {@link FuValueRegistry#valueOf} needs the bound RecipeManager for
 * recipe-derived values (storage blocks, tools, stairs, etc.). Both are available
 * in a running GameTest server; neither is available in a headless JUnit environment.
 *
 * <p>Run as part of the full test suite:
 * <pre>
 *   ./gradlew runGameTestServer -q
 * </pre>
 */
@GameTestHolder(MC3DPrint.MOD_ID)
@PrefixGameTestTemplate(false)
public class CuratedBlueprintPrintabilityGameTests {

    /**
     * Block ids that are known to lack an FU value and are NOT structural matter,
     * so they silently fail to print on the default (strict) config. Listing one
     * here EXEMPTS it from the gate — the gate's real job is to fail the moment a
     * <i>new</i> curated build introduces a NEW unvalued block, forcing a conscious
     * "value it, derive it, or swap the block" decision instead of a silent hole.
     *
     * <p>Two groups, both surfaced for review (see docs/blueprint-candidates.md):
     * <ul>
     *   <li><b>INTENTIONAL</b> — survival-unobtainable by design (FU invariant);
     *       a decorative core meant to be un-printable in survival.</li>
     *   <li><b>PRE-LAUNCH ECONOMY GAP</b> — the existing curated set uses these and
     *       they silently won't print on default config. Fix before launch by
     *       valuing the leaf ingredient (e.g. the 16 dyes → all dyed glass/terracotta
     *       derive; wheat item → hay; flint → fletching_table) or swapping the block.
     *       Remove from this set once valued.</li>
     * </ul>
     */
    private static final Set<String> KNOWN_UNVALUED_BLOCKS = Set.of(
            // --- INTENTIONAL (keep unvalued) ---
            "minecraft:reinforced_deepslate",   // diamond_vault decorative core; survival-unobtainable
            // --- PRE-LAUNCH ECONOMY GAPS (value the leaf or swap; then delete from here) ---
            // NOTE: dyed glass/terracotta and oxidized copper are now resolved via
            // cosmetic-variant FU normalization (see FuValueRegistry.canonicalCosmeticVariant),
            // so they were removed from this set — the gate now verifies that fix and
            // re-fails if normalization ever regresses.
            "minecraft:grass_block",            // natural ground (campfire_site, barn) — value or make structural
            "minecraft:hay_block",              // derives from wheat item, which is unvalued (barn/market/farm)
            "minecraft:target",                 // redstone + hay; hay unvalued (redstone_workshop)
            "minecraft:fletching_table",        // 4 planks + 2 flint; flint unvalued
            "minecraft:cartography_table",      // 4 planks + 2 paper; paper unvalued
            "minecraft:gilded_blackstone",      // bastion loot, not craftable (no recipe to derive)
            "minecraft:bell"                    // village loot, not craftable in survival
    );

    /**
     * For each curated blueprint, resolves every palette entry against live
     * registries and applies the same printability decision the real printer
     * uses.  Fails with a detailed report listing blueprint name, block id,
     * and local (x,y,z) coordinates for every silently-unprintable block found.
     *
     * <p>The test uses a tiny template ({@code empty5}) because it doesn't need
     * to actually place any blocks — it only inspects palette+volume data.
     * The test completes synchronously in tick 1 and {@link GameTestHelper#succeed()
     * succeeds} immediately once the assertion passes.
     */
    @GameTest(template = "empty5", timeoutTicks = 100)
    public static void allCuratedBlueprintBlocksArePrintable(GameTestHelper helper) {
        List<String> failures = new ArrayList<>();

        for (String blueprintName : CuratedBlueprints.CURATED_NAMES) {
            Optional<Blueprint> maybeBlueprint = CuratedBlueprints.loadBundled(blueprintName);
            if (maybeBlueprint.isEmpty()) {
                failures.add("[" + blueprintName + "] MISSING — blueprint file not found on classpath");
                continue;
            }

            Blueprint blueprint = maybeBlueprint.get();
            List<BlueprintBlockState> palette = blueprint.palette();

            // Check each palette entry once (not every block position) — the
            // printability decision depends only on block type, not coordinates.
            // We record failing positions anyway for actionable diagnostics.
            //
            // Two-pass: first find which palette indices are unprintable, then
            // collect their (x,y,z) positions from the volume.

            boolean[] paletteUnprintable = new boolean[palette.size()];
            for (int i = 0; i < palette.size(); i++) {
                BlueprintBlockState bbs = palette.get(i);
                if (bbs.isAir()) {
                    // air = NO_BLOCK placeholder; printer never touches it
                    paletteUnprintable[i] = false;
                    continue;
                }

                Optional<BlockState> resolvedOpt = bbs.resolve();
                if (resolvedOpt.isEmpty()) {
                    // Unknown block id — this would be silently skipped at placement;
                    // flag it so curated blueprints don't reference unregistered blocks.
                    paletteUnprintable[i] = true;
                    failures.add("[" + blueprintName + "] palette[" + i + "] = "
                            + bbs.serialize() + " — could not resolve block id");
                    continue;
                }

                BlockState resolved = resolvedOpt.get();

                // Mirror of PrinterBlockEntity.canPrintBlock() in strict mode:
                //   1. Has an FU value → printable (tier gating N/A here; we test
                //      existence of a value, not tier vs machine tier, because the
                //      curated blueprints are supposed to contain only valued blocks
                //      or structural matter regardless of which machine tier prints them)
                //   2. Is structural matter → printable (costs 0 FU)
                //   3. Otherwise → NOT printable (strict mode refuses it)
                boolean hasFuValue = resolved.getBlock().asItem() != Items.AIR
                        && FuValueRegistry.valueOf(new ItemStack(resolved.getBlock().asItem())).isPresent();
                boolean isStructural = PrinterBlockEntity.isStructuralMatterForTest(resolved);
                String blockId = String.valueOf(ForgeRegistries.BLOCKS.getKey(resolved.getBlock()));
                boolean knownGap = KNOWN_UNVALUED_BLOCKS.contains(blockId);

                paletteUnprintable[i] = !hasFuValue && !isStructural && !knownGap;
            }

            // Collect positions for unprintable palette entries
            if (hasAnyTrue(paletteUnprintable)) {
                blueprint.forEachBlock((pos, paletteIndex) -> {
                    if (paletteIndex >= 0 && paletteIndex < paletteUnprintable.length
                            && paletteUnprintable[paletteIndex]) {
                        BlueprintBlockState bbs = palette.get(paletteIndex);
                        failures.add("[" + blueprintName + "] block=" + bbs.serialize()
                                + " at (" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + ")"
                                + " — no FU value and not structural matter"
                                + " (would be silently skipped in strict mode)");
                    }
                });
            }
        }

        if (!failures.isEmpty()) {
            helper.fail(
                    failures.size() + " silently-unprintable block(s) found across curated blueprints:\n  "
                    + String.join("\n  ", failures));
        } else {
            helper.succeed();
        }
    }

    private static boolean hasAnyTrue(boolean[] arr) {
        for (boolean b : arr) {
            if (b) return true;
        }
        return false;
    }
}
