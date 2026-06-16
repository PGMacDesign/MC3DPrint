package com.pgmacdesign.mc3dprint.gametest;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.blueprint.Blueprint;
import com.pgmacdesign.mc3dprint.blueprint.BlueprintBlockState;
import com.pgmacdesign.mc3dprint.blueprint.CuratedBlueprints;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Integrity guardrail for "broken vertical double blocks".
 *
 * <p>Several vanilla blocks occupy TWO stacked cells and store which half they are in
 * the {@link BlockStateProperties#DOUBLE_BLOCK_HALF} property
 * ({@link DoubleBlockHalf#LOWER}/{@link DoubleBlockHalf#UPPER}): <b>doors</b>
 * ({@code DoorBlock}) and <b>tall plants</b> ({@code DoublePlantBlock} — tall grass,
 * large fern, sunflower, rose bush, lilac, peony, …). The two halves are a single
 * logical block: each half requires its partner directly above/below it. When the
 * world receives a neighbour update and a half finds the wrong block (or air) where
 * its partner should be, the game <em>breaks the block</em> — it pops off and drops.
 *
 * <p>This is exactly the "Jungle Temple Ruin" failure: a curated build authored a door
 * whose UPPER cell was later overwritten by a glass block (last-write-wins in the
 * {@code Builder}), so the printed result was a lower door half with glass directly
 * above it — two blocks in one logical door — and the door self-broke at print end when
 * {@code reconcilePlacedShapes} ran neighbour updates. The printer cannot fix this: it
 * can only reconcile connection STATE, not invent the missing partner half or remove a
 * colliding block. So a build that authors a mismatched double block will always print
 * broken. This test catches that at build-data level.
 *
 * <p>For every curated blueprint, for every cell whose resolved {@link BlockState} has
 * the {@code DOUBLE_BLOCK_HALF} property, we require the partner cell — directly above a
 * {@code LOWER} half, directly below an {@code UPPER} half — to be the SAME block in the
 * opposite half. Anything else (a different block, air, out-of-bounds, unresolvable)
 * fails the test, listing the blueprint, block id, the offending half's coordinates, and
 * what was found in the partner cell.
 *
 * <p>Lives in the GameTest harness (not plain JUnit) because
 * {@link BlueprintBlockState#resolve()} needs the Forge block registries, which only a
 * running GameTest server provides.
 *
 * <pre>
 *   ./gradlew runGameTestServer -q
 * </pre>
 */
@GameTestHolder(MC3DPrint.MOD_ID)
@PrefixGameTestTemplate(false)
public class CuratedBlueprintDoubleBlockIntegrityGameTests {

    @GameTest(template = "empty5", timeoutTicks = 100)
    public static void noCuratedBlueprintShipsABrokenVerticalDouble(GameTestHelper helper) {
        List<String> failures = new ArrayList<>();

        for (String blueprintName : CuratedBlueprints.CURATED_NAMES) {
            Optional<Blueprint> maybeBlueprint = CuratedBlueprints.loadBundled(blueprintName);
            if (maybeBlueprint.isEmpty()) {
                failures.add("[" + blueprintName + "] MISSING — blueprint file not found on classpath");
                continue;
            }

            Blueprint blueprint = maybeBlueprint.get();
            int sx = blueprint.sizeX(), sy = blueprint.sizeY(), sz = blueprint.sizeZ();

            for (int y = 0; y < sy; y++) {
                for (int z = 0; z < sz; z++) {
                    for (int x = 0; x < sx; x++) {
                        BlueprintBlockState cell = blueprint.get(x, y, z);
                        if (cell == null || cell.isAir()) continue;

                        Optional<BlockState> resolvedOpt = cell.resolve();
                        if (resolvedOpt.isEmpty()) continue; // unresolvable handled by the printability gate
                        BlockState state = resolvedOpt.get();
                        if (!state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) continue;

                        DoubleBlockHalf half = state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF);
                        // Partner is directly above a LOWER half, directly below an UPPER half.
                        int partnerY = (half == DoubleBlockHalf.LOWER) ? y + 1 : y - 1;
                        DoubleBlockHalf wantHalf =
                                (half == DoubleBlockHalf.LOWER) ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER;

                        String blockId = String.valueOf(ForgeRegistries.BLOCKS.getKey(state.getBlock()));
                        BlockState partner = resolveAt(blueprint, x, partnerY, z);

                        boolean ok = partner != null
                                && partner.getBlock() == state.getBlock()
                                && partner.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                                && partner.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == wantHalf;

                        if (!ok) {
                            failures.add("[" + blueprintName + "] block=" + blockId
                                    + " (" + half.getSerializedName() + " half) at (" + x + "," + y + "," + z + ")"
                                    + " — partner " + wantHalf.getSerializedName() + " half expected at ("
                                    + x + "," + partnerY + "," + z + ") but found " + describe(blueprint, x, partnerY, z)
                                    + " (block will self-break on print)");
                        }
                    }
                }
            }
        }

        if (!failures.isEmpty()) {
            helper.fail(
                    failures.size() + " broken vertical double block(s) found across curated blueprints:\n  "
                    + String.join("\n  ", failures));
        } else {
            helper.succeed();
        }
    }

    /** Resolve the cell at (x,y,z) to a BlockState, or null if out-of-bounds/air/unresolvable. */
    private static BlockState resolveAt(Blueprint blueprint, int x, int y, int z) {
        if (x < 0 || x >= blueprint.sizeX()
                || y < 0 || y >= blueprint.sizeY()
                || z < 0 || z >= blueprint.sizeZ()) {
            return null;
        }
        BlueprintBlockState cell = blueprint.get(x, y, z);
        if (cell == null || cell.isAir()) return null;
        return cell.resolve().orElse(null);
    }

    /** Human-readable description of what occupies a cell, for failure diagnostics. */
    private static String describe(Blueprint blueprint, int x, int y, int z) {
        if (x < 0 || x >= blueprint.sizeX()
                || y < 0 || y >= blueprint.sizeY()
                || z < 0 || z >= blueprint.sizeZ()) {
            return "<out of bounds>";
        }
        BlueprintBlockState cell = blueprint.get(x, y, z);
        if (cell == null || cell.isAir()) return "<air/empty>";
        return cell.resolve()
                .map(s -> String.valueOf(ForgeRegistries.BLOCKS.getKey(s.getBlock())))
                .orElse("<unresolvable:" + cell.serialize() + ">");
    }
}
