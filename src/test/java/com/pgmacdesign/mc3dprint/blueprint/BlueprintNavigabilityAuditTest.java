package com.pgmacdesign.mc3dprint.blueprint;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import java.util.zip.GZIPInputStream;

/**
 * Diagnostic audit for the <b>navigability</b> bug class — the gap the sealed-interior
 * {@link BlueprintReachabilityAuditTest reachability audit} explicitly cannot see. That
 * audit flood-fills <em>air</em> 6-ways ignoring step height, so it only catches a build
 * with NO opening at all. It misses the more common playtest complaint: you CAN get in,
 * but once inside you can't actually walk around — a 2-block step you can't climb, a low
 * ceiling that clips your head, a drop-in floor you can't get back out of, or a door whose
 * threshold is blocked so you can't even cross it (the {@code mushroom_island_hut} threshold
 * and the {@code greenhouse} drop-in class).
 *
 * <p>This models a player as a 2-tall body standing on a floor and walking. It builds the
 * set of <b>STANDABLE</b> cells — a cell you could stand in:
 * <ul>
 *   <li>the cell itself is PASSABLE (you can occupy it),</li>
 *   <li>the cell directly above is PASSABLE (2-block headroom — your head fits), and</li>
 *   <li>the cell directly below is a FLOOR (a full solid block, or a slab/stair you stand on).</li>
 * </ul>
 * It then walks a graph over standable cells: from A you may step to an orthogonally-adjacent
 * column B if the floor-height change is {@code <= +1} (step up at most one) or any drop down,
 * and the headroom is maintained along the way (no low ceiling clips you). Ladders and stairs
 * also bridge vertically (a ladder/stair column lets you change floor level by more than one).
 *
 * <p>The flood-fill seeds from every standable cell on the bounding-box exterior (you can walk
 * in from outside) and from every door's interior landing cell. A build is FLAGGED when it has
 * standable interior cells that the fill could NOT reach (you could stand there but can't get
 * there) above a small noise threshold, OR when a door's immediate interior cell is itself not
 * standable-reachable from outside (a blocked threshold). These are CANDIDATES for human review,
 * not auto-fixes — the model is a heuristic (registry-free id classification, simplified body),
 * so it can false-positive on legitimately-vertical or decorative builds.
 *
 * <p>GATED on {@code -DauditNavigability=true} — it does NOT run on a normal build and never
 * asserts/fails. Writes {@code build/blueprint-navigability-audit.txt}.
 *
 * <pre>
 *   ./gradlew test --tests *BlueprintNavigabilityAuditTest* -DauditNavigability=true --rerun-tasks
 * </pre>
 */
class BlueprintNavigabilityAuditTest {

    private static final Path SOURCE_DIR =
            Path.of("src", "main", "resources", "data", "mc3dprint", "blueprints");
    private static final Path OUTPUT = Path.of("build", "blueprint-navigability-audit.txt");

    /**
     * Minimum count of unreachable-but-standable interior cells before a build is flagged
     * for the "trapped pocket" reason. Tuned to clear the inevitable noise from this
     * heuristic — single isolated ledges, a decorative nook a block too high, a 1-cell
     * shelf — while still catching a real room/level you can stand in but can't walk to.
     */
    private static final int UNREACHABLE_THRESHOLD = 6;

    /**
     * Substrings of block ids a player body can occupy (the cell is PASSABLE — you stand
     * IN it). Mirrors the reachability audit's pass-through set plus the explicit list from
     * the audit brief. Anything not matched here, and not air/empty, is treated as SOLID.
     * Glass/leaves are deliberately absent (SOLID — you can't walk through them). Slabs and
     * stairs are NOT passable: you stand ON them (they're floors), the air above is the
     * passable cell.
     */
    private static final String[] PASSABLE = {
            "_door", "_trapdoor", "_fence_gate", "ladder",
            "_carpet", "_pressure_plate", "_button", "lever",
            "torch", "_sign", "_banner", "lantern", "chain", "snow",
            "tripwire", "redstone_wire", "rail", "_sapling", "vine",
            "potted_", "flower_pot", "lily_pad", "sugar_cane", "bamboo",
            "nether_wart", "_mushroom", "grass", "fern", "tall_grass",
            "wheat", "carrots", "potatoes", "beetroots", "_crop", "tulip",
            "orchid", "poppy", "dandelion", "allium", "bluet", "cornflower",
            "daisy", "lily_of_the_valley", "rose", "sunflower", "lilac",
            "peony", "azalea", "sweet_berry"
    };

    /**
     * SOLID overrides — full blocks whose ids contain a {@link #PASSABLE} substring
     * (e.g. {@code grass_block} contains "grass", {@code snow_block} contains "snow",
     * {@code lantern} appearing inside {@code sea_lantern}). Checked FIRST so they stay SOLID.
     */
    private static final String[] SOLID_OVERRIDE = {
            "grass_block", "snow_block", "mushroom_block", "_mushroom_block",
            "sea_lantern", "packed_mud", "mud_bricks"
    };

    /** Cell roles for the standability model. */
    private boolean isPassable(BlueprintBlockState cell) {
        if (cell == null || cell.isAir()) return true;
        String id = cell.blockId();
        for (String s : SOLID_OVERRIDE) {
            if (id.contains(s)) return false;
        }
        for (String s : PASSABLE) {
            if (id.contains(s)) return true;
        }
        return false;
    }

    /** A FLOOR is anything you can stand on top of: a full solid block, OR a slab/stair. */
    private boolean isFloor(BlueprintBlockState cell) {
        if (cell == null || cell.isAir()) return false;
        // Slabs and stairs are non-passable but are stand-on-able floors.
        if (cell.blockId().endsWith("_slab") || cell.blockId().endsWith("_stairs")) return true;
        // Everything else that isn't passable is a full solid block — a floor.
        return !isPassable(cell);
    }

    /** A ladder/stair cell lets you change floor level by more than one (vertical bridge). */
    private boolean isVerticalBridge(BlueprintBlockState cell) {
        if (cell == null) return false;
        String id = cell.blockId();
        return id.contains("ladder") || id.endsWith("_stairs") || id.contains("scaffolding");
    }

    @Test
    @EnabledIfSystemProperty(named = "auditNavigability", matches = "true")
    void auditNavigability() throws IOException {
        List<Path> files;
        try (var stream = Files.list(SOURCE_DIR)) {
            files = stream.filter(p -> p.toString().endsWith(".blueprint")).sorted().toList();
        }

        List<String> flagged = new ArrayList<>();
        List<String> all = new ArrayList<>();

        for (Path file : files) {
            String name = file.getFileName().toString().replace(".blueprint", "");
            Blueprint bp = readBlueprint(file);
            int sx = bp.sizeX(), sy = bp.sizeY(), sz = bp.sizeZ();

            BlueprintBlockState[][][] grid = new BlueprintBlockState[sx][sy][sz];
            boolean[][][] passable = new boolean[sx][sy][sz];
            for (int x = 0; x < sx; x++) {
                for (int y = 0; y < sy; y++) {
                    for (int z = 0; z < sz; z++) {
                        BlueprintBlockState c = bp.get(x, y, z);
                        grid[x][y][z] = c;
                        passable[x][y][z] = isPassable(c);
                    }
                }
            }

            // STANDABLE: passable, headroom (above passable, or top of build = open sky),
            // and a floor below (full block, slab, stair, OR world terrain when y==0).
            boolean[][][] standable = new boolean[sx][sy][sz];
            int standableCount = 0;
            for (int x = 0; x < sx; x++) {
                for (int y = 0; y < sy; y++) {
                    for (int z = 0; z < sz; z++) {
                        if (!passable[x][y][z]) continue;
                        boolean headroom = (y + 1 >= sy) || passable[x][y + 1][z];
                        boolean floorBelow = (y == 0) || isFloor(grid[x][y - 1][z]);
                        if (headroom && floorBelow) {
                            standable[x][y][z] = true;
                            standableCount++;
                        }
                    }
                }
            }

            // Reachability flood-fill over standable cells. A move from (x,y,z) to an
            // orthogonally-adjacent column considers candidate floor levels y, y+1 (step up),
            // and y-1..down (drop). A vertical bridge (ladder/stair) at the source or target
            // column lets you change level freely up or down.
            boolean[][][] reached = new boolean[sx][sy][sz];
            Deque<int[]> queue = new ArrayDeque<>();

            // Seeds: exterior standable cells (walk in from outside) + door interior landings.
            List<int[]> doorLandings = new ArrayList<>();
            for (int x = 0; x < sx; x++) {
                for (int y = 0; y < sy; y++) {
                    for (int z = 0; z < sz; z++) {
                        if (!standable[x][y][z]) continue;
                        boolean onSurface = x == 0 || x == sx - 1 || y == 0
                                || z == 0 || z == sz - 1;
                        if (onSurface && !reached[x][y][z]) {
                            reached[x][y][z] = true;
                            queue.add(new int[]{x, y, z});
                        }
                    }
                }
            }
            // Portal interior-landing seeds: for each door (lower half) or fence gate — both
            // are walk-through entries — the standable cell on its interior side (the cell you
            // land in after crossing the threshold). Recorded separately so we can flag a
            // blocked threshold even if the landing isn't on a surface. Seeded as reachable
            // from outside (a portal is, by definition, an exterior opening) so the fill can
            // spread inward from there.
            for (int x = 0; x < sx; x++) {
                for (int y = 0; y < sy; y++) {
                    for (int z = 0; z < sz; z++) {
                        BlueprintBlockState c = grid[x][y][z];
                        if (c == null) continue;
                        boolean isDoor = c.blockId().endsWith("_door")
                                && "lower".equals(c.properties().get("half"));
                        boolean isGate = c.blockId().endsWith("_fence_gate");
                        if (!isDoor && !isGate) continue;
                        String facing = c.properties().getOrDefault("facing", "north");
                        // Interior landing = cell on the OPPOSITE side of the way the door
                        // opens out. We don't know which side is "interior" without geometry,
                        // so check BOTH neighbour columns along the facing axis at this y; the
                        // standable one(s) are landings.
                        int[][] sides;
                        switch (facing) {
                            case "north", "south" -> sides = new int[][]{{x, z - 1}, {x, z + 1}};
                            case "east", "west"   -> sides = new int[][]{{x - 1, z}, {x + 1, z}};
                            default -> sides = new int[][]{};
                        }
                        // The interior floor next to a door can sit one BELOW (you drop in),
                        // LEVEL with, or one ABOVE the door's lower half (you step up onto a
                        // raised sill/floor block — the most common curated pattern: an air
                        // doorway whose interior floor block is the door's own y). Accept a
                        // standable landing at any of y-1, y, y+1 on either side.
                        for (int[] s : sides) {
                            int nx = s[0], nz = s[1];
                            if (nx < 0 || nx >= sx || nz < 0 || nz >= sz) continue;
                            for (int ny = Math.max(0, y - 1); ny <= Math.min(sy - 1, y + 1); ny++) {
                                doorLandings.add(new int[]{nx, ny, nz, x, y, z});
                                if (standable[nx][ny][nz] && !reached[nx][ny][nz]) {
                                    reached[nx][ny][nz] = true;
                                    queue.add(new int[]{nx, ny, nz});
                                }
                            }
                        }
                    }
                }
            }

            int[][] horiz = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            while (!queue.isEmpty()) {
                int[] c = queue.poll();
                int cx = c[0], cy = c[1], cz = c[2];
                boolean srcBridge = isVerticalBridge(grid[cx][cy][cz])
                        || (cy > 0 && isVerticalBridge(grid[cx][cy - 1][cz]));
                for (int[] d : horiz) {
                    int nx = cx + d[0], nz = cz + d[1];
                    if (nx < 0 || nx >= sx || nz < 0 || nz >= sz) continue;
                    // Candidate target floor levels in this neighbour column. Step up +1,
                    // same level, or any drop down. A bridge lets you also rise freely.
                    for (int ny = sy - 1; ny >= 0; ny--) {
                        if (!standable[nx][ny][nz] || reached[nx][ny][nz]) continue;
                        int dyUp = ny - cy;       // >0 means target is higher
                        boolean tgtBridge = isVerticalBridge(grid[nx][ny][nz])
                                || (ny > 0 && isVerticalBridge(grid[nx][ny - 1][nz]));
                        boolean allowed;
                        if (dyUp <= 0) {
                            allowed = true;                       // any drop down (or level)
                        } else if (dyUp == 1) {
                            allowed = true;                       // single step up
                        } else {
                            allowed = srcBridge || tgtBridge;     // >1 up only via ladder/stair
                        }
                        if (!allowed) continue;
                        // Headroom along the move: the source head cell and target head cell
                        // must both be passable (no low ceiling clipping you on either side).
                        boolean srcHead = (cy + 1 >= sy) || passable[cx][cy + 1][cz];
                        boolean tgtHead = (ny + 1 >= sy) || passable[nx][ny + 1][nz];
                        if (!srcHead || !tgtHead) continue;
                        reached[nx][ny][nz] = true;
                        queue.add(new int[]{nx, ny, nz});
                    }
                }
            }

            // Unreachable standable interior cells (you could stand there, can't get there).
            int unreachable = 0;
            int umnX = Integer.MAX_VALUE, umnY = Integer.MAX_VALUE, umnZ = Integer.MAX_VALUE;
            int umxX = Integer.MIN_VALUE, umxY = Integer.MIN_VALUE, umxZ = Integer.MIN_VALUE;
            for (int x = 0; x < sx; x++) {
                for (int y = 0; y < sy; y++) {
                    for (int z = 0; z < sz; z++) {
                        if (standable[x][y][z] && !reached[x][y][z]) {
                            unreachable++;
                            umnX = Math.min(umnX, x); umxX = Math.max(umxX, x);
                            umnY = Math.min(umnY, y); umxY = Math.max(umxY, y);
                            umnZ = Math.min(umnZ, z); umxZ = Math.max(umxZ, z);
                        }
                    }
                }
            }

            // Blocked thresholds: a door whose interior landing cell is not standable-reachable.
            // Dedup by door cell — if EITHER side of a door is a reached standable landing, the
            // door is fine (you can cross it). Flag only doors where NO side landed reachable.
            List<String> blockedDoors = new ArrayList<>();
            // group landings by their door (lx,ly,lz stored at [3],[4],[5])
            java.util.Map<String, Boolean> doorOk = new java.util.LinkedHashMap<>();
            java.util.Map<String, int[]> doorPos = new java.util.LinkedHashMap<>();
            for (int[] l : doorLandings) {
                int nx = l[0], ny = l[1], nz = l[2], dx = l[3], dy = l[4], dz = l[5];
                String key = dx + "," + dy + "," + dz;
                doorPos.putIfAbsent(key, new int[]{dx, dy, dz});
                boolean ok = standable[nx][ny][nz] && reached[nx][ny][nz];
                doorOk.merge(key, ok, (a, b) -> a || b);
            }
            for (var e : doorOk.entrySet()) {
                if (!e.getValue()) {
                    int[] p = doorPos.get(e.getKey());
                    blockedDoors.add(String.format("door(%d,%d,%d)", p[0], p[1], p[2]));
                }
            }

            boolean flag = unreachable >= UNREACHABLE_THRESHOLD || !blockedDoors.isEmpty();
            String line = String.format(
                    "%-34s dims=%dx%dx%d  standable=%d  unreachable=%d%s%s",
                    name, sx, sy, sz, standableCount, unreachable,
                    unreachable > 0 ? String.format("  pocket-bbox=(%d,%d,%d)..(%d,%d,%d)",
                            umnX, umnY, umnZ, umxX, umxY, umxZ) : "",
                    blockedDoors.isEmpty() ? "" : "  blockedDoors=" + blockedDoors);
            all.add(line);
            if (flag) {
                StringBuilder reason = new StringBuilder();
                if (unreachable >= UNREACHABLE_THRESHOLD) {
                    reason.append(String.format("%d standable interior cells unreachable "
                                    + "(bbox (%d,%d,%d)..(%d,%d,%d))",
                            unreachable, umnX, umnY, umnZ, umxX, umxY, umxZ));
                }
                if (!blockedDoors.isEmpty()) {
                    if (reason.length() > 0) reason.append("; ");
                    reason.append("blocked threshold(s): ").append(blockedDoors);
                }
                flagged.add(String.format("[NAV] %-30s %s", name, reason));
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Navigability (walkability) audit — ").append(files.size())
                .append(" builds, unreachable-threshold=").append(UNREACHABLE_THRESHOLD)
                .append(" standable cells\n");
        sb.append("Heuristic, report-only: flags are CANDIDATES for review, not auto-fixes.\n\n");
        sb.append("=== FLAGGED — candidates for review (").append(flagged.size()).append(") ===\n");
        if (flagged.isEmpty()) sb.append("(none above threshold)\n");
        flagged.forEach(l -> sb.append(l).append('\n'));
        sb.append("\n=== ALL (").append(all.size()).append(") ===\n");
        all.forEach(l -> sb.append(l).append('\n'));
        Files.createDirectories(OUTPUT.getParent());
        Files.writeString(OUTPUT, sb.toString());
        System.out.println("[NavigabilityAudit] " + flagged.size() + " flagged / " + all.size()
                + " builds -> " + OUTPUT.toAbsolutePath());
    }

    private static Blueprint readBlueprint(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            CompoundTag tag = NbtIo.read(new DataInputStream(new GZIPInputStream(in)));
            return BlueprintSerializer.read(tag);
        }
    }
}
