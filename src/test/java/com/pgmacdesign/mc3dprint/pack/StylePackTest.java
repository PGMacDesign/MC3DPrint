package com.pgmacdesign.mc3dprint.pack;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards for the built-in style packs (docs/texture-packs.md).
 *
 * 1. Each pack's declared format range must cover EVERY shipped target's
 *    resource pack_format — extend NODE_FORMATS alongside NEOFORGE_NODES in
 *    scripts/build-all.sh when a new version node lands.
 * 2. Every default texture must have a styled counterpart — catches "added a
 *    texture but didn't re-run tools/gen_style_packs.py" drift.
 */
class StylePackTest {

    private static final List<String> STYLES = List.of("blueprint_mode", "dark_mode");

    // Resource pack_format per shipped target (minecraft.wiki/w/Pack_format).
    private static final Map<String, Integer> NODE_FORMATS = Map.of(
            "1.20.1", 15,
            "1.21.1", 34,
            "1.21.8", 64,
            "1.21.9", 69,
            "1.21.10", 69,
            "1.21.11", 75,
            "26.1", 84,
            "26.2", 88);

    private static final List<String> TEXTURE_KINDS = List.of("block", "item", "gui");

    /** Walk up from the test working dir to the repo root (Stonecutter runs
     *  tests from the version-node project dir, not the repo root). */
    private static Path repoRoot() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (dir != null && !Files.isDirectory(dir.resolve("src/main/resources/resourcepacks"))) {
            dir = dir.getParent();
        }
        assertTrue(dir != null, "could not locate repo root from " + System.getProperty("user.dir"));
        return dir;
    }

    @Test
    void manifestRangeCoversEveryShippedTarget() throws IOException {
        for (String style : STYLES) {
            Path meta = repoRoot().resolve("src/main/resources/resourcepacks/" + style + "/pack.mcmeta");
            assertTrue(Files.isRegularFile(meta), style + " is missing pack.mcmeta");
            JsonObject pack = JsonParser.parseString(Files.readString(meta))
                    .getAsJsonObject().getAsJsonObject("pack");
            int min = pack.get("min_format").getAsInt();
            int max = pack.get("max_format").getAsInt();
            int legacy = pack.get("pack_format").getAsInt();
            JsonObject supported = pack.getAsJsonObject("supported_formats");
            int supMin = supported.get("min_inclusive").getAsInt();
            int supMax = supported.get("max_inclusive").getAsInt();
            NODE_FORMATS.forEach((node, format) -> {
                assertTrue(format >= min && format <= max,
                        style + ": node " + node + " (format " + format
                                + ") outside min_format/max_format " + min + ".." + max);
                assertTrue(format >= supMin && format <= supMax,
                        style + ": node " + node + " (format " + format
                                + ") outside supported_formats " + supMin + ".." + supMax);
            });
            // Pin BOTH legacy fields to the actual 1.20.1 format; legacy == supMin
            // alone lets the pair drift together (e.g. both to 20) while 1.20.1
            // clients get the "incompatible" warning.
            int format1201 = NODE_FORMATS.get("1.20.1");
            assertTrue(legacy == format1201 && supMin == format1201,
                    style + ": pack_format (" + legacy + ") and supported_formats minimum ("
                            + supMin + ") must equal the 1.20.1 format " + format1201);
        }
    }

    @Test
    void everyDefaultTextureHasAStyledCounterpart() throws IOException {
        Path root = repoRoot();
        Path defaults = root.resolve("src/main/resources/assets/mc3dprint/textures");
        for (String style : STYLES) {
            Path styled = root.resolve("src/main/resources/resourcepacks/" + style
                    + "/assets/mc3dprint/textures");
            for (String kind : TEXTURE_KINDS) {
                // walk, not list: a future nested texture (block/subdir/foo.png)
                // must trip this guard, not dodge it
                Path defaultKind = defaults.resolve(kind);
                Path styledKind = styled.resolve(kind);
                try (Stream<Path> files = Files.walk(defaultKind)) {
                    files.filter(Files::isRegularFile).filter(p -> {
                        String n = p.getFileName().toString();
                        return n.endsWith(".png") || n.endsWith(".png.mcmeta");
                    }).forEach(p -> {
                        Path relative = defaultKind.relativize(p);
                        assertTrue(Files.isRegularFile(styledKind.resolve(relative)),
                                style + " is missing " + kind + "/" + relative
                                        + "; re-run tools/gen_style_packs.py");
                    });
                }
            }
        }
    }
}
