package com.pgmacdesign.mc3dprint.release;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Pins the NeoForge dependency floor each Stonecutter node advertises in its mods.toml.
 *
 * <p>A node compiles against exactly one NeoForge build but publishes a {@code versionRange}, and
 * that range is a promise to the loader: anything inside it is claimed to work. On the 26.x lines
 * that promise is easy to break, because NeoForge still renames API within a line. 26.1 moved
 * {@code BlockEvent.BreakEvent} to {@code event.level.block.BreakBlockEvent} between 26.1.2.20-beta
 * and 26.1.2.21-beta; a jar built on a later 26.1 but advertising {@code [26.1,)} loads on
 * 26.1.0.19-beta and dies with NoClassDefFoundError during mod construction, which reads to the
 * player as "the mod is broken" rather than "your loader is too old".
 *
 * <p>So for 26.x the floor is the build we compile against: it is the only version whose API
 * surface we have actually linked against. The 1.21.x lines keep their looser historical floors,
 * where the API has been frozen for the life of the line.
 */
class NodeLoaderRangeTest {

    private static Path repoRoot() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (dir != null && !Files.isDirectory(dir.resolve("versions"))) {
            dir = dir.getParent();
        }
        if (dir == null) {
            fail("could not locate the repo root from " + System.getProperty("user.dir"));
        }
        return dir;
    }

    private record Node(String name, String version, String range) {}

    private static List<Node> nodes() throws IOException {
        Path versions = repoRoot().resolve("versions");
        List<Node> found = new ArrayList<>();
        try (var dirs = Files.list(versions)) {
            for (Path dir : dirs.sorted().toList()) {
                Path props = dir.resolve("gradle.properties");
                if (!Files.isRegularFile(props)) {
                    continue;
                }
                Properties p = new Properties();
                try (Reader r = Files.newBufferedReader(props)) {
                    p.load(r);
                }
                String version = p.getProperty("neo_version");
                String range = p.getProperty("neo_version_range");
                assertTrue(version != null && range != null,
                        dir.getFileName() + " must declare neo_version and neo_version_range");
                found.add(new Node(dir.getFileName().toString(), version, range));
            }
        }
        assertTrue(found.size() >= 2, "expected several Stonecutter nodes, got " + found);
        return found;
    }

    /** Lower bound of an inclusive maven range like {@code [26.1.2.77,)}. */
    private static String floorOf(Node node) {
        String range = node.range();
        assertTrue(range.startsWith("[") && range.contains(","),
                node.name() + " declares an unexpected range shape: " + range);
        return range.substring(1, range.indexOf(',')).trim();
    }

    /**
     * Orders NeoForge versions: dotted numeric parts first, then a pre-release suffix, which sorts
     * before the same number with no suffix ({@code 26.1.2.21-beta} precedes {@code 26.1.2.21}).
     */
    private static int compareVersions(String a, String b) {
        String[] aSplit = a.split("-", 2);
        String[] bSplit = b.split("-", 2);
        String[] aNums = aSplit[0].split("\\.");
        String[] bNums = bSplit[0].split("\\.");
        for (int i = 0; i < Math.max(aNums.length, bNums.length); i++) {
            int an = i < aNums.length ? Integer.parseInt(aNums[i]) : 0;
            int bn = i < bNums.length ? Integer.parseInt(bNums[i]) : 0;
            if (an != bn) {
                return Integer.compare(an, bn);
            }
        }
        boolean aPre = aSplit.length > 1;
        boolean bPre = bSplit.length > 1;
        if (aPre != bPre) {
            return aPre ? -1 : 1;
        }
        return aPre ? aSplit[1].compareTo(bSplit[1]) : 0;
    }

    @Test
    void everyNodeAdvertisesARangeThatContainsItsOwnBuild() throws IOException {
        for (Node node : nodes()) {
            assertTrue(compareVersions(floorOf(node), node.version()) <= 0,
                    node.name() + " advertises " + node.range()
                            + ", which excludes the NeoForge build it compiles against ("
                            + node.version() + ")");
        }
    }

    @Test
    void theChurningTwentySixNodesFloorAtTheBuildTheyCompileAgainst() throws IOException {
        for (Node node : nodes()) {
            if (!node.name().startsWith("26.")) {
                continue;
            }
            assertEquals(node.version(), floorOf(node),
                    "node " + node.name() + " claims to run on NeoForge older than the "
                            + node.version() + " it links against; 26.x renames API mid-line, so "
                            + "an older loader is missing classes this jar references");
        }
    }
}
