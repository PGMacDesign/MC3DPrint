package com.pgmacdesign.mc3dprint.blueprint;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the correct-by-construction blueprint helpers actually encode the conventions
 * they claim — so future authors can trust them to make the two recurring bug classes
 * (wrong stair facing, openings that don't breach the wall) unbuildable.
 *
 * <p>Pure data assertions on the registry-free {@link Blueprint} / {@link BlueprintBlockState}
 * model (block ids + property strings + the NO_BLOCK sentinel), so these run as plain JUnit
 * with no live level — fast and headless.
 *
 * <ul>
 *   <li>{@link #builderClearGenuinelyEmptiesACell()} — {@code set} then {@code clear} leaves
 *       the cell empty (the root-cause fix for door-over-wall: {@code set(pos, air)} is a no-op).</li>
 *   <li>{@code staircase} — every step faces the climb direction and consecutive steps go +1/+1
 *       (matches the empirical convention from {@code StairConventionGameTests}).</li>
 *   <li>{@code ladderRun} — every rung's support cell ({@code facing.getOpposite()}) is the
 *       {@code backingDir} cell (would PASS {@code BlueprintLadderSupportAuditTest}).</li>
 *   <li>{@code doorway} — door cells are doors and the cells behind are EMPTY (a real opening).</li>
 * </ul>
 */
class CorrectByConstructionHelpersTest {

    private static final BlueprintBlockState STONE = BlueprintBlockState.parse("minecraft:stone");
    private static final BlueprintBlockState OAK_STAIRS = BlueprintBlockState.parse("minecraft:oak_stairs");

    // -----------------------------------------------------------------
    //  Deliverable 2 — Builder.clear genuinely empties a cell
    // -----------------------------------------------------------------

    @Test
    void builderClearGenuinelyEmptiesACell() {
        Blueprint.Builder b = Blueprint.builder("clear_test", 3, 3, 3);

        b.set(1, 1, 1, STONE);
        Blueprint afterSet = b.build();
        assertNotNull(afterSet.get(1, 1, 1), "set should have placed the block");
        assertEquals("minecraft:stone", afterSet.get(1, 1, 1).blockId());

        b.clear(1, 1, 1);
        Blueprint afterClear = b.build();
        assertNull(afterClear.get(1, 1, 1),
                "clear must genuinely empty the cell (NO_BLOCK), unlike set(pos, air)");
        // The cleared cell round-trips identically to a never-written cell: blockCount drops to 0.
        assertEquals(0, afterClear.blockCount(),
                "a cleared cell must not count as a placeable block");
    }

    @Test
    void setAirIsStillANoOpSoClearIsTheOnlyWayToCarve() {
        // Documents WHY clear exists: set(pos, air) silently leaves the prior block.
        Blueprint.Builder b = Blueprint.builder("air_noop", 1, 1, 1);
        b.set(0, 0, 0, STONE);
        b.set(0, 0, 0, BlueprintBlockState.parse("minecraft:air"));
        assertNotNull(b.build().get(0, 0, 0), "set(air) is a no-op — block must remain");
        b.clear(0, 0, 0);
        assertNull(b.build().get(0, 0, 0), "clear removes it");
    }

    // -----------------------------------------------------------------
    //  Deliverable 4a — staircase() faces the climb dir, steps +1/+1
    // -----------------------------------------------------------------

    @Test
    void staircaseFacesClimbDirectionAndStepsOnePerOne() {
        for (Direction dir : new Direction[]{
                Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {

            // Centre the start so a 4-step climb stays in-bounds in every direction.
            int sx = 5, sy = 1, sz = 5, steps = 4;
            Blueprint.Builder b = Blueprint.builder("stair_" + dir, 11, 11, 11);
            BlockPos top = CuratedBlueprintGenerator.staircase(b, sx, sy, sz, dir, steps, OAK_STAIRS);
            Blueprint bp = b.build();

            int dx = dir.getStepX();
            int dz = dir.getStepZ();
            for (int i = 0; i < steps; i++) {
                int cx = sx + dx * i;
                int cy = sy + i;
                int cz = sz + dz * i;
                BlueprintBlockState cell = bp.get(cx, cy, cz);
                assertNotNull(cell, "missing step " + i + " for dir " + dir);
                assertEquals("minecraft:oak_stairs", cell.blockId(), "step " + i + " block id");
                // EMPIRICAL CONVENTION: a stair ascends toward its facing → facing == climb dir.
                assertEquals(dir.getName(), cell.properties().get("facing"),
                        "step " + i + " facing must equal climb direction " + dir);
                assertEquals("bottom", cell.properties().get("half"), "step " + i + " half");
            }

            // Consecutive steps advance +1 along the axis and +1 in y (a walkable climb).
            for (int i = 1; i < steps; i++) {
                BlockPos prev = new BlockPos(sx + dx * (i - 1), sy + (i - 1), sz + dz * (i - 1));
                BlockPos cur = new BlockPos(sx + dx * i, sy + i, sz + dz * i);
                int horiz = Math.abs(cur.getX() - prev.getX()) + Math.abs(cur.getZ() - prev.getZ());
                assertEquals(1, horiz, "consecutive steps must advance exactly 1 horizontally");
                assertEquals(1, cur.getY() - prev.getY(), "consecutive steps must rise exactly 1");
            }

            // Returned top position is the last step.
            assertEquals(new BlockPos(sx + dx * (steps - 1), sy + (steps - 1), sz + dz * (steps - 1)),
                    top, "staircase must return the top step position for dir " + dir);
        }
    }

    // -----------------------------------------------------------------
    //  Deliverable 4b — ladderRun() rungs are backed on backingDir
    // -----------------------------------------------------------------

    @Test
    void ladderRunBacksEveryRungAgainstBackingDir() {
        for (Direction backing : new Direction[]{
                Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {

            int x = 4, z = 4, y0 = 1, y1 = 5;
            Blueprint.Builder b = Blueprint.builder("ladder_" + backing, 9, 9, 9);
            // Provide the backing wall via the helper so the column is self-contained.
            CuratedBlueprintGenerator.ladderRun(b, x, y0, y1, z, backing, STONE);
            Blueprint bp = b.build();

            for (int y = y0; y <= y1; y++) {
                BlueprintBlockState ladder = bp.get(x, y, z);
                assertNotNull(ladder, "missing ladder rung at y=" + y + " for backing " + backing);
                assertEquals("minecraft:ladder", ladder.blockId(), "rung at y=" + y);

                // The ladder mounts opposite its facing; that support cell MUST be backingDir
                // and MUST hold a (sturdy) block — exactly the BlueprintLadderSupportAuditTest rule.
                String facing = ladder.properties().get("facing");
                Direction supportDir = Direction.byName(facing).getOpposite();
                assertEquals(backing, supportDir,
                        "ladder support side (opposite facing) must be the backing dir " + backing);

                BlueprintBlockState support = bp.get(x + backing.getStepX(), y, z + backing.getStepZ());
                assertNotNull(support, "ladder rung at y=" + y + " must be backed by a solid block");
                assertEquals("minecraft:stone", support.blockId(), "backing block at y=" + y);
            }
        }
    }

    // -----------------------------------------------------------------
    //  Deliverable 4c — doorway() places doors AND carves a real opening
    // -----------------------------------------------------------------

    @Test
    void doorwayPlacesDoorsAndCarvesARealOpening() {
        for (Direction into : new Direction[]{
                Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {

            int x = 5, y = 1, z = 5, depth = 3;
            Blueprint.Builder b = Blueprint.builder("door_" + into, 11, 11, 11);
            // Fill the passage with solid wall FIRST, so the carve has something to remove —
            // this is the real-world ordering (wall, then door) that used to silently fail.
            int dx = into.getStepX();
            int dz = into.getStepZ();
            for (int k = 0; k <= depth; k++) {
                b.set(x + dx * k, y, z + dz * k, STONE);
                b.set(x + dx * k, y + 1, z + dz * k, STONE);
            }

            CuratedBlueprintGenerator.doorway(b, x, y, z, into, depth, "oak");
            Blueprint bp = b.build();

            // Door cells are doors facing `into`, lower + upper.
            BlueprintBlockState lower = bp.get(x, y, z);
            BlueprintBlockState upper = bp.get(x, y + 1, z);
            assertNotNull(lower, "missing lower door for into=" + into);
            assertNotNull(upper, "missing upper door for into=" + into);
            assertEquals("minecraft:oak_door", lower.blockId());
            assertEquals("minecraft:oak_door", upper.blockId());
            assertEquals(into.getName(), lower.properties().get("facing"), "door facing");
            assertEquals("lower", lower.properties().get("half"));
            assertEquals("upper", upper.properties().get("half"));

            // The passageDepth cells behind the door (both rows) are GENUINELY EMPTY — a
            // real breach, not a door printed into solid wall.
            for (int k = 1; k <= depth; k++) {
                int px = x + dx * k;
                int pz = z + dz * k;
                assertNull(bp.get(px, y, pz),
                        "passage cell k=" + k + " row y must be cleared for into=" + into);
                assertNull(bp.get(px, y + 1, pz),
                        "passage cell k=" + k + " row y+1 must be cleared for into=" + into);
            }
        }
    }

    @Test
    void doorwayCarveSurvivesOutOfBoundsPassage() {
        // A passage that runs off the edge must not throw — it just stops at the boundary.
        Blueprint.Builder b = Blueprint.builder("door_edge", 4, 3, 4);
        b.set(1, 1, 0, STONE);
        b.set(1, 2, 0, STONE);
        // into=NORTH from z=0 would carve z=-1 etc. (out of bounds) — must be a safe no-op.
        CuratedBlueprintGenerator.doorway(b, 1, 1, 0, Direction.NORTH, 2, "oak");
        Blueprint bp = b.build();
        assertTrue(bp.get(1, 1, 0) != null && bp.get(1, 1, 0).blockId().equals("minecraft:oak_door"),
                "door still placed even when the carve runs out of bounds");
    }
}
