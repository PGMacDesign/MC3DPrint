package com.pgmacdesign.mc3dprint.gametest;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * EMPIRICAL stair-facing convention test — the permanent, self-checking documentation
 * of "which way does a stair point?".
 *
 * <p>The stair-facing convention has been gotten wrong repeatedly by blueprint authors:
 * is the raised "step" on the {@code facing} side, or the opposite side? Rather than encode
 * a guess, this test settles it from the live game. For {@code minecraft:oak_stairs[half=bottom]}
 * at each horizontal {@code facing} (N/S/E/W) it:
 * <ol>
 *   <li>places the block in the running GameTest level,</li>
 *   <li>reads its actual outline {@link VoxelShape} via {@link BlockState#getShape},</li>
 *   <li>samples the shape's max Y on each of the four horizontal sides
 *       (north/south/east/west quarters of the cell), and</li>
 *   <li>determines which side reaches FULL height (y &rarr; 1.0) — that side is the
 *       raised step; the other horizontal side is the low (slab-only, y &rarr; 0.5) half.</li>
 * </ol>
 *
 * <p><b>Asserted convention (confirmed in-game by PGMac):</b> the raised step is on the
 * {@code facing} side &rarr; <b>a stair ASCENDS TOWARD its {@code facing}</b>. So a climb
 * heading north is built with {@code facing=north}. The
 * {@code CuratedBlueprintGenerator.staircase(...)} helper encodes exactly this, which is why
 * authors can no longer get it wrong.
 *
 * <p>If the shape ever says otherwise, this test FAILS LOUDLY with the per-facing readings
 * instead of silently re-encoding a wrong assumption — treat a failure as "the convention
 * (or vanilla's stair shape) changed; re-derive the helper", not as flakiness.
 *
 * <p>Lives in the GameTest harness (not plain JUnit) because reading a real
 * {@link VoxelShape} needs a live level + the block registry, which only a running
 * GameTest server provides.
 *
 * <pre>
 *   ./gradlew runGameTestServer -q
 * </pre>
 */
@GameTestHolder(MC3DPrint.MOD_ID)
@PrefixGameTestTemplate(false)
public class StairConventionGameTests {

    /** A side reaches "full height" when its sampled max Y is at/above this (slab top = 0.5). */
    private static final double FULL_HEIGHT = 0.99;

    @GameTest(template = "empty5", timeoutTicks = 100)
    public static void bottomStairRaisedStepIsOnFacingSide(GameTestHelper helper) {
        Level level = helper.getLevel();
        BlockPos rel = new BlockPos(2, 1, 2);
        BlockPos abs = helper.absolutePos(rel);

        List<String> log = new ArrayList<>();
        List<String> failures = new ArrayList<>();

        for (Direction facing : new Direction[]{
                Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {

            BlockState stair = Blocks.OAK_STAIRS.defaultBlockState()
                    .setValue(StairBlock.FACING, facing)
                    .setValue(StairBlock.HALF, Half.BOTTOM);
            helper.setBlock(rel, stair);

            // Read the ACTUAL outline shape from the live level (registry-resolved).
            VoxelShape shape = level.getBlockState(abs).getShape(level, abs);

            // Find the side that reaches FULL height. The shape decomposes into axis-aligned
            // boxes (toAabbs); for a bottom stair these are the full-cell bottom slab (y 0→0.5)
            // plus the raised step (y 0.5→1.0) occupying ONE horizontal half. We locate the
            // box(es) reaching y≈1.0 and read their horizontal centre to name the raised side —
            // unambiguous, with no reliance on the subtle max(Axis,a,b) argument order.
            Direction raised = raisedStepSide(shape);

            log.add(String.format("facing=%-5s  raised-step=%s  (boxes: %s)",
                    facing, raised, describeBoxes(shape)));

            if (raised != facing) {
                failures.add(String.format(
                        "facing=%s expected raised step on the FACING side (%s) but the live "
                                + "shape raised the %s side  (boxes: %s)",
                        facing, facing, raised, describeBoxes(shape)));
            }

            helper.setBlock(rel, Blocks.AIR);
        }

        // Permanent documentation: emit the per-facing readings every run.
        System.out.println("[StairConvention] oak_stairs[half=bottom] empirical shape readings:");
        log.forEach(l -> System.out.println("[StairConvention]   " + l));

        if (!failures.isEmpty()) {
            helper.fail("Stair-facing convention CONTRADICTED (expected: ascend toward facing — "
                    + "raised step on the facing side):\n  " + String.join("\n  ", failures)
                    + "\n  Re-derive CuratedBlueprintGenerator.staircase() before trusting it.");
        } else {
            helper.succeed();
        }
    }

    /**
     * The horizontal side on which the stair's outline reaches FULL height — the raised step.
     *
     * <p>Considers only the constituent boxes that reach the cell top ({@code maxY ≈ 1.0}).
     * Their combined horizontal centre, relative to the cell centre (0.5, 0.5), names the
     * side: a box whose centre sits at low z is the north side, high z the south, low x west,
     * high x east. (A straight stair's raised step occupies exactly one horizontal half, so
     * the offset is unambiguous; a corner stair's L-shape would offset diagonally, but the
     * curated set only ever uses {@code shape=straight}.) Returns {@code null} if no box
     * reaches full height, which the caller treats as a contradiction.
     */
    private static Direction raisedStepSide(VoxelShape shape) {
        double sumX = 0, sumZ = 0;
        int n = 0;
        for (AABB box : shape.toAabbs()) {
            if (box.maxY < FULL_HEIGHT) continue; // not the raised step
            sumX += (box.minX + box.maxX) / 2.0;
            sumZ += (box.minZ + box.maxZ) / 2.0;
            n++;
        }
        if (n == 0) return null;
        double cx = sumX / n - 0.5;
        double cz = sumZ / n - 0.5;
        // Pick the dominant horizontal offset → cardinal side.
        if (Math.abs(cz) >= Math.abs(cx)) {
            return cz < 0 ? Direction.NORTH : Direction.SOUTH;
        }
        return cx < 0 ? Direction.WEST : Direction.EAST;
    }

    /** Compact dump of the shape's boxes (for the run log / failure messages). */
    private static String describeBoxes(VoxelShape shape) {
        List<String> parts = new ArrayList<>();
        for (AABB box : shape.toAabbs()) {
            parts.add(String.format("[x%.2f-%.2f y%.2f-%.2f z%.2f-%.2f]",
                    box.minX, box.maxX, box.minY, box.maxY, box.minZ, box.maxZ));
        }
        return String.join(" ", parts);
    }
}
