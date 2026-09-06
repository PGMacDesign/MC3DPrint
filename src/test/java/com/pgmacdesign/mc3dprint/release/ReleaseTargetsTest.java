package com.pgmacdesign.mc3dprint.release;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The release workflow publishes one CurseForge file per built jar, from a matrix that repeats the
 * node list {@code scripts/build-all.sh} already owns. Two lists of the same thing drift, and this
 * one drifts silently: adding a Stonecutter node without a matching matrix entry produces a jar
 * that is attached to the GitHub release and then never reaches CurseForge, with every job green.
 *
 * <p>Also pins the Java metadata per target, because that is the field a copy-pasted matrix row
 * gets wrong, and a wrong value there is only visible on the CurseForge page.
 */
class ReleaseTargetsTest {

    private static Path repoRoot() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (dir != null && !Files.isDirectory(dir.resolve(".github/workflows"))) {
            dir = dir.getParent();
        }
        if (dir == null) {
            fail("could not locate the repo root from " + System.getProperty("user.dir"));
        }
        return dir;
    }

    /** Nodes the build script actually produces NeoForge jars for. */
    private static List<String> buildScriptNodes(Path root) throws IOException {
        String script = Files.readString(root.resolve("scripts/build-all.sh"));
        Matcher decl = Pattern.compile("^NEOFORGE_NODES=\\(([^)]*)\\)", Pattern.MULTILINE)
                .matcher(script);
        assertTrue(decl.find(), "NEOFORGE_NODES not found in scripts/build-all.sh");
        List<String> nodes = new ArrayList<>();
        Matcher each = Pattern.compile("\"([^\"]+)\"").matcher(decl.group(1));
        while (each.find()) {
            nodes.add(each.group(1));
        }
        assertTrue(nodes.size() >= 2, "expected several NeoForge nodes, got " + nodes);
        return nodes;
    }

    private record Row(String target, String loader, String game, int java) {}

    /** Rows of the publish matrix, parsed from the workflow. */
    private static List<Row> matrixRows(Path root) throws IOException {
        String workflow = Files.readString(root.resolve(".github/workflows/release.yml"));
        Matcher m = Pattern.compile(
                "\\{\\s*target:\\s*\"([^\"]+)\",\\s*loader:\\s*(\\w+),\\s*"
                        + "game:\\s*\"([^\"]+)\",\\s*java:\\s*(\\d+)\\s*}").matcher(workflow);
        List<Row> rows = new ArrayList<>();
        while (m.find()) {
            rows.add(new Row(m.group(1), m.group(2), m.group(3), Integer.parseInt(m.group(4))));
        }
        assertTrue(!rows.isEmpty(), "no publish matrix rows parsed from release.yml");
        return rows;
    }

    @Test
    void everyBuiltJarHasACurseForgePublishTarget() throws IOException {
        Path root = repoRoot();
        Set<String> expected = new LinkedHashSet<>();
        // The legacy Forge line is built from its own branch, not from a Stonecutter node.
        expected.add("forge-1.20.1");
        for (String node : buildScriptNodes(root)) {
            expected.add("neoforge-" + node);
        }
        Set<String> actual =
                new LinkedHashSet<>(matrixRows(root).stream().map(Row::target).toList());
        assertEquals(expected, actual,
                "the CurseForge publish matrix and the build script disagree; a jar is either"
                        + " built and never published, or published and never built");
    }

    @Test
    void eachTargetDeclaresTheJavaItsLineActuallyUses() throws IOException {
        List<String> wrong = new ArrayList<>();
        for (Row row : matrixRows(repoRoot())) {
            // Forge 1.20.1 is Java 17; 1.21.x is 21; the 26.x line moved to 25 (build.gradle's
            // nodeJavaVersion is the source of truth for that split).
            int want = row.target().startsWith("forge-") ? 17
                    : (row.game().startsWith("26.") ? 25 : 21);
            if (row.java() != want) {
                wrong.add(row.target() + " declares java " + row.java() + ", expected " + want);
            }
        }
        assertTrue(wrong.isEmpty(), "wrong Java metadata: " + String.join("; ", wrong));
    }

    @Test
    void theGameVersionMatchesTheTargetItPublishes() throws IOException {
        List<String> wrong = new ArrayList<>();
        for (Row row : matrixRows(repoRoot())) {
            // The whole point of the matrix: a row must not publish one node's jar under another
            // node's Minecraft version, which is exactly what a copy-pasted block gets wrong.
            String suffix = row.loader() + "-" + row.game();
            if (!row.target().equals(suffix)) {
                wrong.add(row.target() + " publishes as " + suffix);
            }
        }
        assertTrue(wrong.isEmpty(),
                "target and game version disagree, so a jar would be listed under the wrong"
                        + " Minecraft version: " + String.join("; ", wrong));
    }
}
