package com.pgmacdesign.mc3dprint.blueprint;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;

import java.util.zip.GZIPInputStream;

/**
 * Audit for the <b>navigability</b> bug class — the gap the sealed-interior
 * {@link BlueprintReachabilityAuditTest reachability audit} explicitly cannot see. That
 * audit flood-fills <em>air</em> 6-ways ignoring step height, so it only catches a build
 * with NO opening at all. It misses the more common playtest complaint: you CAN get in,
 * but once inside you can't actually walk around — a 2-block step you can't climb, a low
 * ceiling that clips your head, a drop-in floor you can't get back out of, or a door whose
 * threshold is blocked so you can't even cross it (the {@code mushroom_island_hut} threshold
 * and the {@code greenhouse} drop-in class; and the {@code purpur_tower} second-floor trap
 * this gate was hardened to protect against regressing).
 *
 * <p>This models a player as a 2-tall body standing on a floor and walking. It builds the
 * set of <b>STANDABLE</b> cells — a cell you could stand in:
 * <ul>
 *   <li>the cell itself is PASSABLE (you can occupy it),</li>
 *   <li>the cell directly above is PASSABLE (2-block headroom — your head fits) <em>or</em>
 *       the head obstruction is a thin curving-roof lip (see {@link #hasHeadroom}), and</li>
 *   <li>the cell directly below is a FLOOR (a full solid block, or a slab/stair you stand on).</li>
 * </ul>
 * It then walks a graph over standable cells: from A you may step to an orthogonally-adjacent
 * column B if the floor-height change is {@code <= +1} (step up at most one) or any drop down,
 * and the headroom is maintained along the way (no low ceiling clips you). Ladders and stairs
 * also bridge vertically (a ladder/stair column lets you change floor level by more than one).
 *
 * <p>The flood-fill seeds from every standable cell on the bounding-box exterior (you can walk
 * in from outside) and from every door's interior landing cell. A build is FLAGGED when it has
 * standable <em>interior</em> cells that the fill could NOT reach (you could stand there but
 * can't get there) above a small noise threshold, OR when a door's immediate interior cell is
 * itself not standable-reachable from outside (a blocked threshold).
 *
 * <h3>Precision (two model fixes + an exempt list) so this can be a hard gate</h3>
 * The raw heuristic false-positived on three structure classes that are <em>not</em> bugs.
 * Two are fixed in the model itself (honestly reducing false positives), the third is a small
 * justified allowlist of genuine non-rooms:
 * <ol>
 *   <li><b>Roof / dome / pillar / decor tops (model fix — {@link #isRooftop}).</b> A standable
 *       cell whose <em>entire column above to the build top is open</em> (passable all the way
 *       up — open to sky, no ceiling) is an <em>exterior</em> surface: the top of a snow dome,
 *       a copper observatory cap, a stonehenge lintel, a pergola lattice, a basalt spire, a
 *       statue, a ship's rigging, a farm-machine top. A player walking the interior never needs
 *       to reach a rooftop, so these are excluded from the unreachable-interior count. This is
 *       not an allowlist — it correctly reclassifies exterior tops on EVERY build and matches
 *       how the reachability audit sees them (un-enclosed).</li>
 *   <li><b>Curving / dome-lip headroom (model fix — {@link #hasHeadroom}).</b> A 1-block-thick
 *       curving roof (an igloo/observatory dome) can clip the head of a floor cell at the very
 *       lip even though the player clearly walks the room. When the head obstruction is a thin
 *       roof shell (passable again one cell higher) and the floor is continuous, the cell still
 *       counts as standable / traversable — the curve, not a solid 2-block-thick wall. A genuine
 *       low blocked passage (head AND head+1 both solid) is NOT credited, so real low-ceiling
 *       traps still flag.</li>
 *   <li><b>Genuinely enclosed non-rooms ({@link #NAV_EXEMPT}).</b> A few builds enclose a
 *       standable pocket <em>by design</em> that is not a room a player traverses — a sealed
 *       villager-breeding box on top of a farm, a barred treasure cage you view through a door,
 *       a sealed mob-spawn shaft. These are NOT rooftop (they have a ceiling) and NOT a curve
 *       artifact, so the model can't honestly reclassify them; they're allowlisted with a
 *       per-entry justification, reusing the reachability audit's INTENTIONALLY_SEALED reasoning
 *       where it overlaps. Only genuine non-rooms are listed — never a build with a real interior.</li>
 * </ol>
 *
 * <h3>Status: ALWAYS-ON hard gate</h3>
 * The two model fixes + the exempt list drove the raw heuristic from 17 false positives down to a
 * single FLAGGED build: {@code snowy_igloo} — and that one was <b>not</b> a false positive but a
 * genuine navigability defect: its spruce door sat in the dome's springing edge (z=8) and the only
 * floor cell just inside it (z=7) had a 2-block-thick snow roof course directly overhead (head at
 * y+1 AND y+2 both snow_block), so a 2-tall player couldn't step from the doorway into the bed-room
 * — a 1-block-headroom threshold pinch, the same class as the purpur_tower second-floor trap. The
 * dome-lip headroom fix deliberately did NOT paper it over (it credits only a 1-block-thick curving
 * roof, not a 2-thick pinch). That defect is now <b>fixed</b> in {@code snowyIgloo()} (the lower
 * head course over the door's interior landing is carved with {@code Builder.clear}, restoring
 * 2-block headroom while the y=3 course keeps the dome closed), so FLAGGED is empty.
 *
 * <p>With FLAGGED empty, this audit is promoted to an <b>always-on hard gate</b> that runs on every
 * {@code ./gradlew build}, mirroring {@link BlueprintReachabilityAuditTest}: any non-exempt FLAGGED
 * build throws an {@link AssertionError} and fails the build. The EXEMPT section is still written
 * (intentional non-room pockets do NOT fail). To clear a new flag: fix the geometry, or — only for
 * a genuine non-room cavity — add it to {@link #NAV_EXEMPT} with a justification (never a real
 * room). Writes {@code build/blueprint-navigability-audit.txt} for inspection.
 *
 * <pre>
 *   ./gradlew build                                                    # runs as a gate
 *   ./gradlew test --tests *BlueprintNavigabilityAuditTest* --rerun-tasks
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
     * shelf — while still catching a real room/level you can stand in but can't walk to
     * (e.g. the purpur_tower second floor, ~20+ standable cells, the regression this gate
     * protects).
     */
    private static final int UNREACHABLE_THRESHOLD = 6;

    /**
     * Builds whose only unreachable-standable cells are an enclosed pocket that is NOT a
     * navigable room <b>by design</b> — the audit detecting them is correct, but they are not
     * defects, so they're split into a separate {@code [NAV·EXEMPT]} section and do NOT fail
     * the gate. Each entry is a deliberate non-room cavity (verified against the ASCII dumps):
     * <ul>
     *   <li>{@code iron_farm} — the flagged cells are the sealed villager-breeding pods on the
     *       top deck of the golem farm (a roofed glass box with no door; the only downward
     *       connection is the 2&times;2 golem-fall hole). The one genuine walkable room — the
     *       lower iron-collection chamber — has its own door and IS reached, so it is not in the
     *       flagged set. A machine compartment, not a room.</li>
     *   <li>{@code diamond_vault} — the flagged cells are the floor of the iron-barred treasure
     *       cage: a diamond display ringed by iron bars, capped by slab + tinted glass, meant to
     *       be viewed and entered only through the single south iron door. A deliberate caged
     *       display, not a walk-around room. (Its reachability — air can reach the cage through
     *       the door — is a separate, fixed concern; the bars make the standable floor a
     *       non-traversable cage by design.)</li>
     *   <li>{@code mob_xp_tower} — the flagged cells are the air at the top of the sealed
     *       mob-spawn drop shaft, capped by a solid stone-slab lid with no opening (mobs fall
     *       down the chute; a player never walks in). The AFK/collection room is at the base and
     *       is reached. Already on the reachability {@code INTENTIONALLY_SEALED} allowlist for
     *       the same shaft; the navigability rationale is identical.</li>
     * </ul>
     * NOTE on what is deliberately NOT here: every dome / curved-roof / open-air monument that
     * the raw heuristic used to flag (snowy_igloo, copper_observatory, underwater_dome_base,
     * desert_pyramid_shrine, bee_apiary, pergola_garden, chorus_garden, basalt_pillar_cluster,
     * stonehenge_ring, statue_pedestal, end_gateway_shrine, the auto sugarcane/kelp farms, the
     * sailing_ship rigging) is now cleared <em>honestly by the model</em> (rooftop + dome-lip
     * fixes), not by allowlist — so a future regression that walls off a real room in any of
     * them would still flag.
     */
    private static final Set<String> NAV_EXEMPT = Set.of(
            "iron_farm",
            "diamond_vault",
            "mob_xp_tower"
    );

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

    /**
     * Headroom test for a body standing with feet at {@code (x,y,z)} — model fix #2.
     * Normally the head cell {@code (x,y+1,z)} must be passable. A 1-block-thick CURVING
     * roof (a dome lip) is also accepted: if the head block is solid but it is a thin roof
     * shell — the cell one higher {@code (x,y+2,z)} is passable (so it's a single roof
     * course, not a 2+-block-thick wall/floor above) — the cell still has headroom (the
     * player rounds the curve / crouches under the lip of a domed roof). A genuine low
     * blocked passage, where both {@code y+1} and {@code y+2} are solid, is NOT credited,
     * so real low-ceiling traps still flag.
     */
    private boolean hasHeadroom(boolean[][][] passable, int x, int y, int z, int sy) {
        if (y + 1 >= sy) return true;          // open sky above
        if (passable[x][y + 1][z]) return true; // ordinary 2-tall clearance
        // Dome-lip tolerance: a single solid roof course with open space just above it.
        return (y + 2 < sy) && passable[x][y + 2][z];
    }

    /**
     * Rooftop test — model fix #1. True when the entire column strictly above {@code (x,y,z)}
     * up to the build top is passable: the cell is open to sky with no ceiling, i.e. it sits
     * on the EXTERIOR top surface of the build (a roof/dome/pillar/decor/rigging top), not
     * inside a room. Such cells are excluded from the unreachable-INTERIOR count — a player
     * walking the interior is never expected to reach a rooftop, and the reachability audit
     * likewise treats them as un-enclosed.
     */
    private boolean isRooftop(boolean[][][] passable, int x, int y, int z, int sy) {
        for (int yy = y + 1; yy < sy; yy++) {
            if (!passable[x][yy][z]) return false;
        }
        return true;
    }

    @Test
    void auditNavigability() throws IOException {
        List<Path> files;
        try (var stream = Files.list(SOURCE_DIR)) {
            files = stream.filter(p -> p.toString().endsWith(".blueprint")).sorted().toList();
        }

        List<String> flagged = new ArrayList<>();    // real navigability defects
        List<String> exempt = new ArrayList<>();      // intentional non-room pockets
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

            // STANDABLE: passable, headroom (above passable, top of build = open sky, OR a
            // 1-block dome-lip — see hasHeadroom), and a floor below (full block, slab, stair,
            // OR world terrain when y==0).
            boolean[][][] standable = new boolean[sx][sy][sz];
            int standableCount = 0;
            for (int x = 0; x < sx; x++) {
                for (int y = 0; y < sy; y++) {
                    for (int z = 0; z < sz; z++) {
                        if (!passable[x][y][z]) continue;
                        boolean headroom = hasHeadroom(passable, x, y, z, sy);
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
            List<int[]> doorLandings = new ArrayList<>();
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
                        // must both clear (no low ceiling clipping you on either side). Uses the
                        // dome-lip-tolerant headroom test so a curving roof doesn't sever a path
                        // through a room a player obviously walks.
                        if (!hasHeadroom(passable, cx, cy, cz, sy)) continue;
                        if (!hasHeadroom(passable, nx, ny, nz, sy)) continue;
                        reached[nx][ny][nz] = true;
                        queue.add(new int[]{nx, ny, nz});
                    }
                }
            }

            // Unreachable standable INTERIOR cells (you could stand there, can't get there).
            // Rooftop cells (open to sky — a roof/dome/pillar/decor top) are exterior surface,
            // not interior rooms, so they are excluded (model fix #1).
            int unreachable = 0;
            int umnX = Integer.MAX_VALUE, umnY = Integer.MAX_VALUE, umnZ = Integer.MAX_VALUE;
            int umxX = Integer.MIN_VALUE, umxY = Integer.MIN_VALUE, umxZ = Integer.MIN_VALUE;
            for (int x = 0; x < sx; x++) {
                for (int y = 0; y < sy; y++) {
                    for (int z = 0; z < sz; z++) {
                        if (standable[x][y][z] && !reached[x][y][z]
                                && !isRooftop(passable, x, y, z, sy)) {
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
            // A door that only opens onto a rooftop landing (e.g. a roof-access trapdoor pattern)
            // is not a blocked-interior threshold, so rooftop-only landings are ignored.
            java.util.Map<String, Boolean> doorOk = new java.util.LinkedHashMap<>();
            java.util.Map<String, int[]> doorPos = new java.util.LinkedHashMap<>();
            for (int[] l : doorLandings) {
                int nx = l[0], ny = l[1], nz = l[2], dx = l[3], dy = l[4], dz = l[5];
                String key = dx + "," + dy + "," + dz;
                doorPos.putIfAbsent(key, new int[]{dx, dy, dz});
                boolean ok = standable[nx][ny][nz] && reached[nx][ny][nz];
                doorOk.merge(key, ok, (a, b) -> a || b);
            }
            List<String> blockedDoors = new ArrayList<>();
            for (var e : doorOk.entrySet()) {
                if (!e.getValue()) {
                    int[] p = doorPos.get(e.getKey());
                    blockedDoors.add(String.format("door(%d,%d,%d)", p[0], p[1], p[2]));
                }
            }

            boolean defect = unreachable >= UNREACHABLE_THRESHOLD || !blockedDoors.isEmpty();
            String line = String.format(
                    "%-34s dims=%dx%dx%d  standable=%d  unreachable=%d%s%s",
                    name, sx, sy, sz, standableCount, unreachable,
                    unreachable > 0 ? String.format("  pocket-bbox=(%d,%d,%d)..(%d,%d,%d)",
                            umnX, umnY, umnZ, umxX, umxY, umxZ) : "",
                    blockedDoors.isEmpty() ? "" : "  blockedDoors=" + blockedDoors);
            all.add(line);
            if (defect) {
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
                if (NAV_EXEMPT.contains(name)) {
                    exempt.add(String.format("[NAV·EXEMPT] %-28s %s", name, reason));
                } else {
                    flagged.add(String.format("[NAV] %-30s %s", name, reason));
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Navigability (walkability) audit — ").append(files.size())
                .append(" builds, unreachable-threshold=").append(UNREACHABLE_THRESHOLD)
                .append(" standable cells\n");
        sb.append("Model-improved (rooftop + dome-lip fixes) + NAV_EXEMPT allowlist. ALWAYS-ON "
                + "hard gate: any non-exempt FLAGGED build fails the build.\n");
        sb.append("FLAGGED builds are real navigability defects to fix (a room you can stand in "
                + "but can't walk to / a blocked threshold).\n\n");
        sb.append("=== FLAGGED — navigability defects to fix (").append(flagged.size())
                .append(") ===\n");
        if (flagged.isEmpty()) {
            sb.append("(none — gate-ready: every build is walkable, a curved-roof/rooftop "
                    + "model case, or on the NAV_EXEMPT allowlist)\n");
        }
        flagged.forEach(l -> sb.append(l).append('\n'));
        sb.append("\n=== EXEMPT — intentional non-room pockets, allowlisted (")
                .append(exempt.size()).append(") ===\n");
        exempt.forEach(l -> sb.append(l).append('\n'));
        sb.append("\n=== ALL (").append(all.size()).append(") ===\n");
        all.forEach(l -> sb.append(l).append('\n'));
        Files.createDirectories(OUTPUT.getParent());
        Files.writeString(OUTPUT, sb.toString());
        System.out.println("[NavigabilityAudit] " + flagged.size() + " flagged / "
                + exempt.size() + " exempt / " + all.size() + " builds -> "
                + OUTPUT.toAbsolutePath());

        // Guardrail: any non-exempt build with a real navigability defect — a standable
        // room/level you can't actually walk to, or a blocked door threshold — fails the
        // build. Fail loudly so the audit is a real gate, not just a report. To clear a new
        // flag: fix the geometry (carve the pinch / raise the ceiling / add the step), or —
        // only if the pocket is a genuine non-room cavity by design — add it to NAV_EXEMPT
        // with a per-entry justification (never allowlist a build that has a real room).
        if (!flagged.isEmpty()) {
            throw new AssertionError("Non-navigable build(s) — fix the geometry or, if the "
                    + "pocket is an intentional non-room cavity, add to NAV_EXEMPT:\n"
                    + String.join("\n", flagged));
        }
    }

    private static Blueprint readBlueprint(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            CompoundTag tag = NbtIo.read(new DataInputStream(new GZIPInputStream(in)));
            return BlueprintSerializer.read(tag);
        }
    }
}
