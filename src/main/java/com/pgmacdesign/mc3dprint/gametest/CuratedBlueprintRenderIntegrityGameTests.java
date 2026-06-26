package com.pgmacdesign.mc3dprint.gametest;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.blueprint.Blueprint;
import com.pgmacdesign.mc3dprint.blueprint.BlueprintBlockState;
import com.pgmacdesign.mc3dprint.blueprint.CuratedBlueprints;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Render-integrity guardrail for "stub panes".
 *
 * <p>A glass pane / stained-glass pane / iron bars block is a vanilla
 * {@link IronBarsBlock} (a {@code CrossCollisionBlock}): it draws a small center
 * post plus an arm toward every HORIZONTAL neighbour it can connect to. With ZERO
 * horizontal connections it stays in its center-post default state and renders as
 * an almost-invisible floating stub — it still has a hitbox and is breakable, so it
 * looks like the glass "didn't render."
 *
 * <p>The printer's end-of-job {@code reconcilePlacedShapes}
 * ({@code PrinterBlockEntity#updateFromNeighbourShapes}) correctly recomputes pane
 * connection state from the placed neighbours — but it can only create a connection
 * where a connectable neighbour actually exists. A curated build that authors a pane
 * with no connectable neighbour will therefore always print as a stub. This test
 * catches that at build-data level so a bad window can't ship.
 *
 * <p>For every curated blueprint, for every cell whose resolved block is an
 * {@link IronBarsBlock}, we check its four HORIZONTAL neighbours (±x, ±z) inside the
 * blueprint volume. A pane "connects" to a neighbour when the neighbour's resolved
 * {@link BlockState} satisfies vanilla's connection rule:
 * <ul>
 *   <li>the neighbour is itself an {@link IronBarsBlock} (panes/bars connect to each other), OR</li>
 *   <li>the neighbour is in {@link BlockTags#WALLS} (panes connect to walls), OR</li>
 *   <li>the neighbour presents a sturdy full face toward the pane
 *       ({@code neighbour.isFaceSturdy(level, BlockPos.ZERO, dirFromNeighbourTowardPane)}).</li>
 * </ul>
 * Out-of-bounds, empty ({@code NO_BLOCK}/air), and unresolvable cells are treated as
 * NON-connecting. (We deliberately skip vanilla's rare {@code isExceptionForConnection}
 * carve-out — it isn't exposed in the official mappings and the curated palette uses
 * none of those blocks as window flankers; erring toward "connects" only risks a
 * false PASS for an exotic neighbour, never a false FAIL.)
 *
 * <p>A pane cell with ZERO connecting horizontal neighbours fails the test, listing
 * the blueprint, block id, and local coordinates. This must PASS for all curated
 * builds — it proves no build ships a stub pane.
 *
 * <p>Lives in the GameTest harness (not plain JUnit) because
 * {@link BlueprintBlockState#resolve()} and {@code isFaceSturdy}/tag lookups need the
 * Forge block registries + a live level, which only a running GameTest server provides.
 *
 * <pre>
 *   ./gradlew runGameTestServer -q
 * </pre>
 */
@GameTestHolder(MC3DPrint.MOD_ID)
@PrefixGameTestTemplate(false)
public class CuratedBlueprintRenderIntegrityGameTests {

    @GameTest(template = "empty5", timeoutTicks = 100)
    public static void noCuratedBlueprintShipsAStubPane(GameTestHelper helper) {
        Level level = helper.getLevel();
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
                        BlockState paneState = resolvedOpt.get();
                        if (!(paneState.getBlock() instanceof IronBarsBlock)) continue;

                        // Check the 4 horizontal neighbours; a single connection is enough to render.
                        boolean anyConnection =
                                connects(level, blueprint, x - 1, y, z, Direction.EAST)   // west neighbour faces east toward pane
                                        || connects(level, blueprint, x + 1, y, z, Direction.WEST)  // east neighbour faces west
                                        || connects(level, blueprint, x, y, z - 1, Direction.SOUTH) // north neighbour faces south
                                        || connects(level, blueprint, x, y, z + 1, Direction.NORTH); // south neighbour faces north

                        if (!anyConnection) {
                            String blockId = String.valueOf(BuiltInRegistries.BLOCK.getKey(paneState.getBlock()));
                            failures.add("[" + blueprintName + "] block=" + blockId
                                    + " at (" + x + "," + y + "," + z + ")"
                                    + " — glass pane / iron bars has no horizontal connection"
                                    + " (renders as an invisible stub)");
                        }
                    }
                }
            }
        }

        if (!failures.isEmpty()) {
            helper.fail(
                    failures.size() + " stub pane(s) found across curated blueprints:\n  "
                    + String.join("\n  ", failures));
        } else {
            helper.succeed();
        }
    }

    /**
     * Whether the blueprint cell at {@code (nx,ny,nz)} is a horizontal neighbour a
     * glass pane / iron bars can connect to. {@code towardPane} is the direction
     * from THIS neighbour toward the pane (the neighbour face that must be sturdy).
     * Out-of-bounds / empty / unresolvable → non-connecting.
     */
    private static boolean connects(Level level, Blueprint blueprint,
                                    int nx, int ny, int nz, Direction towardPane) {
        if (nx < 0 || nx >= blueprint.sizeX()
                || ny < 0 || ny >= blueprint.sizeY()
                || nz < 0 || nz >= blueprint.sizeZ()) {
            return false; // out of bounds
        }
        BlueprintBlockState neighbourCell = blueprint.get(nx, ny, nz);
        if (neighbourCell == null || neighbourCell.isAir()) return false; // empty / window opening
        Optional<BlockState> resolvedOpt = neighbourCell.resolve();
        if (resolvedOpt.isEmpty()) return false;
        BlockState neighbour = resolvedOpt.get();

        // Vanilla IronBarsBlock connection rule (sans the inaccessible exception carve-out):
        //   panes/bars connect to other panes/bars, to walls, and to sturdy full faces.
        if (neighbour.getBlock() instanceof IronBarsBlock) return true;
        if (neighbour.is(BlockTags.WALLS)) return true;
        return neighbour.isFaceSturdy(level, BlockPos.ZERO, towardPane);
    }
}
