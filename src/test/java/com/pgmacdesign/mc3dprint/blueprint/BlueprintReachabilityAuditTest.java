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
 * Diagnostic audit for the <b>sealed interior</b> bug class surfaced in playtesting
 * (modern_glass_villa reportedly has no door to either floor; copper_clocktower may be
 * sealed). If a build encloses interior air with no opening — no door, no gap, no
 * trapdoor — the printed structure is impossible to enter, a defect the GameTests don't
 * catch because they only verify block placement, not navigability.
 *
 * <p>The check models a player body occupying air: every cell is classified PASSABLE
 * (air, doors, trapdoors, fence gates, ladders, plants, decor you can stand in) or SOLID
 * (everything else — full blocks, stairs, slabs, walls, fences, <b>glass</b> and
 * <b>leaves</b> included, since you can't walk through them). It then 3D flood-fills
 * (6-connectivity) every PASSABLE cell reachable from the bounding-box surface, treating
 * out-of-bounds as open air. Any PASSABLE cell NOT reached is enclosed interior space; a
 * build is FLAGGED {@code [SEALED]} when its enclosed unreachable volume meets the noise
 * threshold ({@value #SEALED_THRESHOLD} cells), with the pocket bounding box and the
 * count of distinct sealed pockets reported.
 *
 * <p>A handful of builds seal an interior <b>on purpose</b> (a mob-spawn shaft, a machinery
 * cavity, a decorative balloon envelope, a hull bilge — see {@link #INTENTIONALLY_SEALED}).
 * Those are split off into a separate {@code [SEALED·OK]} allowlisted section and do NOT
 * fail the test. Everything else that seals an interior is treated as a real navigability
 * defect: the test throws an {@link AssertionError} so the audit is a clean guardrail, not
 * just a report. To clear a new flag: add a real opening, or — if the seal is intentional —
 * add the build to the allowlist with a justification.
 *
 * <p>Reads palette strings directly (no Forge registry / running server), so passability
 * is a conservative id heuristic: when unsure, a block is treated SOLID, which avoids a
 * false "sealed" by never leaking the flood-fill through an ambiguous block.
 *
 * <p><b>Known limitation:</b> this catches FULLY-sealed interiors only. It does NOT catch
 * step-up / threshold nav issues — a door you can technically enter but a 1-block ledge in
 * front of blocks you — because the flood-fill ignores step height (it connects air cells
 * 6-ways regardless of vertical reachability). Decorative solid builds (statues, obelisks,
 * monuments) have ~0 interior air and are expected NOT to flag.
 *
 * <p>Runs as an ALWAYS-ON hard gate on every {@code ./gradlew build}: any non-allowlisted
 * sealed interior throws an {@link AssertionError} and fails the build. Writes
 * {@code build/blueprint-reachability-audit.txt} for inspection.
 */
class BlueprintReachabilityAuditTest {

    private static final Path SOURCE_DIR =
            Path.of("src", "main", "resources", "data", "mc3dprint", "blueprints");
    private static final Path OUTPUT = Path.of("build", "blueprint-reachability-audit.txt");

    /**
     * Minimum enclosed unreachable passable volume (cells) before a build is flagged.
     * Set to 10 to clear thin decorative/structural voids that aren't rooms — a 1-wide
     * beacon-beam column (8 cells) or an internal farm stem channel (6 cells) — while
     * still catching the smallest genuine sealed room (~26 cells).
     */
    private static final int SEALED_THRESHOLD = 10;

    /**
     * Substrings of block ids a player body can occupy (the cell counts as PASSABLE).
     * Conservative: anything not matched here — and not air/empty — is SOLID, so the
     * flood-fill can't leak out through an ambiguous block and falsely clear a sealed
     * interior. Glass, glass panes and leaves are deliberately absent (SOLID): you can't
     * walk through them. Stairs/slabs/walls/fences are SOLID too — a body stands ON them,
     * the air above is the passable cell.
     */
    private static final String[] PASSABLE = {
            "_door", "_trapdoor", "_fence_gate", "ladder", "scaffolding",
            "_button", "lever", "_pressure_plate", "_carpet", "snow",
            "torch", "_sign", "_banner", "tripwire", "redstone_wire",
            "rail", "_sapling", "vine", "potted_", "flower_pot", "lily_pad",
            "sugar_cane", "bamboo", "nether_wart", "_mushroom", "lantern",
            "chain", "grass", "fern", "tall_grass", "wheat", "carrots",
            "potatoes", "beetroots", "_crop", "tulip", "orchid", "poppy",
            "dandelion", "allium", "bluet", "cornflower", "daisy", "lily_of_the_valley",
            "rose", "sunflower", "lilac", "peony", "azalea", "sweet_berry"
    };

    /**
     * SOLID overrides — full blocks whose ids happen to contain a {@link #PASSABLE}
     * substring (e.g. {@code grass_block} contains "grass", {@code snow_block} contains
     * "snow"). Checked FIRST so they stay SOLID; if such a wall block leaked through as
     * passable the flood-fill could falsely clear a genuinely sealed interior.
     */
    private static final String[] SOLID_OVERRIDE = {
            "grass_block", "snow_block", "mushroom_block", "sea_lantern", "_mushroom_block"
    };

    /**
     * Builds whose enclosed interior is sealed <b>by design</b> — the audit detecting them
     * is correct, but they are NOT defects, so they're excluded from the FLAGGED list and
     * reported separately for transparency. Each entry is a deliberate, non-room cavity:
     * <ul>
     *   <li>{@code mob_xp_tower} — the internal mob-spawn shaft is meant to be a sealed,
     *       dark drop chute (mobs fall through it); a player never walks in.</li>
     *   <li>{@code super_smelter} — the internal furnace/hopper machinery cavity; a
     *       packed redstone/smelting core, not a room.</li>
     *   <li>{@code gatehouse} — the portcullis-mechanism channels (the vertical slots the
     *       gate rides in) are intentionally enclosed.</li>
     *   <li>{@code hot_air_balloon} — the balloon envelope is a sealed decorative sphere
     *       (a hollow wool/fabric shell), not an interior space.</li>
     *   <li>{@code victorian_townhouse} — the sealed attic roof-void above the top floor
     *       (dead space inside the roof pitch), not a habitable room.</li>
     *   <li>{@code sailing_ship} — the below-deck hull/bilge void: a 1-block-tall cavity
     *       between the keel (y=1) and the deck (y=3), the hollow hull below the
     *       waterline. It is decorative ship structure (no deck hatch leads into it and
     *       it's too short to stand in); the captain's cabin above it IS reachable via the
     *       roof hatch. Only the bilge is sealed, by design.</li>
     * </ul>
     * NOTE: {@code diamond_vault} is deliberately NOT here — its sealed interior was a real
     * bug (the iron door butted against a solid inner wall) and has been fixed so the vault
     * chamber is reachable through the door.
     */
    private static final Set<String> INTENTIONALLY_SEALED = Set.of(
            "mob_xp_tower",
            "super_smelter",
            "gatehouse",
            "hot_air_balloon",
            "victorian_townhouse",
            "sailing_ship",
            // iron_farm — a mob farm: the player only enters the walk-in COLLECTION room
            // (door). The sealed pockets are all by design — the lava-blade KILL column
            // (walled so the golem can't escape), the drop SHAFT + glass SPAWN ENCLOSURE
            // above the lava blade (mobs spawn and fall; the player never goes up there),
            // and the zombie containment cell. Same intent as mob_xp_tower's spawn shaft.
            "iron_farm"
    );

    @Test
    void auditReachability() throws IOException {
        List<Path> files;
        try (var stream = Files.list(SOURCE_DIR)) {
            files = stream.filter(p -> p.toString().endsWith(".blueprint")).sorted().toList();
        }

        List<String> flagged = new ArrayList<>();      // unexpected sealed builds (defects)
        List<String> allowlisted = new ArrayList<>();   // intentionally-sealed builds
        List<String> all = new ArrayList<>();
        int openBuilds = 0;

        for (Path file : files) {
            String name = file.getFileName().toString().replace(".blueprint", "");
            Blueprint bp = readBlueprint(file);
            int sx = bp.sizeX(), sy = bp.sizeY(), sz = bp.sizeZ();

            // 1. Classify every cell: PASSABLE (true) vs SOLID (false).
            boolean[][][] passable = new boolean[sx][sy][sz];
            for (int x = 0; x < sx; x++) {
                for (int y = 0; y < sy; y++) {
                    for (int z = 0; z < sz; z++) {
                        BlueprintBlockState cell = bp.get(x, y, z);
                        passable[x][y][z] = isPassable(cell);
                    }
                }
            }

            // 2. Flood-fill PASSABLE cells reachable from the bounding-box surface
            //    (any passable cell on an outer face touches open air / out-of-bounds).
            boolean[][][] reached = new boolean[sx][sy][sz];
            Deque<int[]> queue = new ArrayDeque<>();
            for (int x = 0; x < sx; x++) {
                for (int y = 0; y < sy; y++) {
                    for (int z = 0; z < sz; z++) {
                        boolean onSurface = x == 0 || x == sx - 1 || y == 0 || y == sy - 1
                                || z == 0 || z == sz - 1;
                        if (onSurface && passable[x][y][z] && !reached[x][y][z]) {
                            reached[x][y][z] = true;
                            queue.add(new int[]{x, y, z});
                        }
                    }
                }
            }
            int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
            while (!queue.isEmpty()) {
                int[] c = queue.poll();
                for (int[] d : dirs) {
                    int nx = c[0] + d[0], ny = c[1] + d[1], nz = c[2] + d[2];
                    if (nx < 0 || nx >= sx || ny < 0 || ny >= sy || nz < 0 || nz >= sz) continue;
                    if (passable[nx][ny][nz] && !reached[nx][ny][nz]) {
                        reached[nx][ny][nz] = true;
                        queue.add(new int[]{nx, ny, nz});
                    }
                }
            }

            // 3. Any PASSABLE cell not reached is enclosed interior. Count it, and group
            //    the unreachable cells into distinct pockets (separate flood-fill over the
            //    unreached set) to report how many sealed cavities exist.
            int unreachable = 0;
            for (int x = 0; x < sx; x++) {
                for (int y = 0; y < sy; y++) {
                    for (int z = 0; z < sz; z++) {
                        if (passable[x][y][z] && !reached[x][y][z]) unreachable++;
                    }
                }
            }

            int pockets = 0;
            int pmnX = Integer.MAX_VALUE, pmnY = Integer.MAX_VALUE, pmnZ = Integer.MAX_VALUE;
            int pmxX = Integer.MIN_VALUE, pmxY = Integer.MIN_VALUE, pmxZ = Integer.MIN_VALUE;
            if (unreachable > 0) {
                boolean[][][] seen = new boolean[sx][sy][sz];
                for (int x = 0; x < sx; x++) {
                    for (int y = 0; y < sy; y++) {
                        for (int z = 0; z < sz; z++) {
                            if (!passable[x][y][z] || reached[x][y][z] || seen[x][y][z]) continue;
                            pockets++;
                            seen[x][y][z] = true;
                            queue.add(new int[]{x, y, z});
                            while (!queue.isEmpty()) {
                                int[] c = queue.poll();
                                pmnX = Math.min(pmnX, c[0]); pmxX = Math.max(pmxX, c[0]);
                                pmnY = Math.min(pmnY, c[1]); pmxY = Math.max(pmxY, c[1]);
                                pmnZ = Math.min(pmnZ, c[2]); pmxZ = Math.max(pmxZ, c[2]);
                                for (int[] d : dirs) {
                                    int nx = c[0] + d[0], ny = c[1] + d[1], nz = c[2] + d[2];
                                    if (nx < 0 || nx >= sx || ny < 0 || ny >= sy
                                            || nz < 0 || nz >= sz) continue;
                                    if (passable[nx][ny][nz] && !reached[nx][ny][nz]
                                            && !seen[nx][ny][nz]) {
                                        seen[nx][ny][nz] = true;
                                        queue.add(new int[]{nx, ny, nz});
                                    }
                                }
                            }
                        }
                    }
                }
            }

            String line;
            if (unreachable >= SEALED_THRESHOLD) {
                line = String.format(
                        "%-34s dims=%dx%dx%d  enclosed=%d cells  pockets=%d  "
                                + "bbox=(%d,%d,%d)..(%d,%d,%d)",
                        name, sx, sy, sz, unreachable, pockets,
                        pmnX, pmnY, pmnZ, pmxX, pmxY, pmxZ);
                if (INTENTIONALLY_SEALED.contains(name)) {
                    allowlisted.add("[SEALED·OK] " + line);
                } else {
                    flagged.add("[SEALED] " + line);
                }
            } else {
                line = String.format("%-34s dims=%dx%dx%d  enclosed=%d cells",
                        name, sx, sy, sz, unreachable);
                if (unreachable == 0) openBuilds++;
            }
            all.add(line);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Reachability (sealed-interior) audit — ").append(files.size())
                .append(" builds, threshold=").append(SEALED_THRESHOLD).append(" enclosed cells\n\n");
        sb.append("=== FLAGGED — UNEXPECTED sealed/unreachable interior (")
                .append(flagged.size()).append(") ===\n");
        if (flagged.isEmpty()) {
            sb.append("(none — every sealed build is either fixed or on the "
                    + "INTENTIONALLY_SEALED allowlist)\n");
        }
        flagged.forEach(l -> sb.append(l).append('\n'));
        sb.append("\n=== ALLOWLISTED — intentionally sealed by design (")
                .append(allowlisted.size()).append(") ===\n");
        allowlisted.forEach(l -> sb.append(l).append('\n'));
        sb.append("\nINFO: ").append(openBuilds)
                .append(" builds fully open/reachable (0 enclosed cells)\n");
        sb.append("\n=== ALL (").append(all.size()).append(") ===\n");
        all.forEach(l -> sb.append(l).append('\n'));
        Files.createDirectories(OUTPUT.getParent());
        Files.writeString(OUTPUT, sb.toString());
        System.out.println("[ReachabilityAudit] " + flagged.size() + " UNEXPECTED-flagged / "
                + allowlisted.size() + " allowlisted / " + openBuilds + " fully-open / "
                + all.size() + " builds -> " + OUTPUT.toAbsolutePath());

        // Guardrail: any build that seals an interior and ISN'T on the allowlist is a
        // navigability defect (a printed structure you can't enter). Fail loudly so the
        // audit is a real gate, not just a report.
        if (!flagged.isEmpty()) {
            throw new AssertionError("Unexpected sealed-interior build(s) — add a real "
                    + "opening or, if intentional, add to INTENTIONALLY_SEALED:\n"
                    + String.join("\n", flagged));
        }
    }

    /** Empty cells and pass-throughable block ids are PASSABLE; everything else is SOLID. */
    private static boolean isPassable(BlueprintBlockState cell) {
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

    private static Blueprint readBlueprint(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            CompoundTag tag = NbtIo.read(new DataInputStream(new GZIPInputStream(in)));
            return BlueprintSerializer.read(tag);
        }
    }
}
