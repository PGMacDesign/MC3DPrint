package com.pgmacdesign.mc3dprint.pack;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Patchouli page titles are drawn centred on a fixed-width page and are NOT wrapped or ellipsised:
 * an over-long one simply runs off the edge mid-word, which is how "The Converter Is Not AE2" got
 * shipped reading "The Converter Is Not AE".
 *
 * <p>The limit is characters rather than measured pixels because the book uses Minecraft's default
 * font and nothing here can measure it without a client. It is deliberately a little conservative.
 */
class GuidebookLayoutTest {

    /** Longest page title that fits without clipping on a default-width Patchouli page. */
    private static final int MAX_TITLE = 20;

    private static Path repoRoot() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (dir != null && !Files.isDirectory(dir.resolve("src/main/resources/assets"))) {
            dir = dir.getParent();
        }
        if (dir == null) {
            fail("could not locate the repo root from " + System.getProperty("user.dir"));
        }
        return dir;
    }

    @Test
    void noPageTitleRunsOffThePage() throws IOException {
        Path entries = repoRoot().resolve(
                "src/main/resources/assets/mc3dprint/patchouli_books/guide/en_us/entries");
        // Asserted, not skipped. repoRoot() only looks for src/main/resources/assets, which other
        // tracked assets keep satisfying, so a missing guide directory used to make this test pass
        // while validating nothing at all.
        assertTrue(Files.isDirectory(entries), "guide entries directory is missing: " + entries);
        List<String> tooLong = new ArrayList<>();
        try (Stream<Path> files = Files.walk(entries)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                JsonObject entry = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
                if (!entry.has("pages")) {
                    continue;
                }
                for (var page : entry.getAsJsonArray("pages")) {
                    JsonObject obj = page.getAsJsonObject();
                    if (!obj.has("title")) {
                        continue;
                    }
                    String title = obj.get("title").getAsString();
                    if (title.length() > MAX_TITLE) {
                        tooLong.add(f.getFileName() + ": \"" + title + "\" ("
                                + title.length() + " chars)");
                    }
                }
            }
        }
        assertTrue(tooLong.isEmpty(),
                "page titles longer than " + MAX_TITLE + " characters clip on the page: "
                        + String.join("; ", tooLong));
    }

    /** Characters that fit on one rendered line, measured off a screenshot at the default font. */
    private static final int CHARS_PER_LINE = 30;
    /** Lines a page body gets: first page loses room to the entry title and its registry-id subtitle. */
    private static final int LINES_FIRST = 12, LINES_TITLED = 15, LINES_PLAIN = 17;

    /**
     * Patchouli does not scroll or paginate a text page: a body too long for the box is shrunk and
     * then simply cut off mid-sentence, which is how a whole guide chapter shipped ending in
     * "does not exist." with the rest of the sentence gone.
     *
     * <p>Counted in estimated rendered lines rather than characters because the formatting macros
     * ({@code $(l)}, {@code $(br2)}, {@code $(li)}) are directives, not glyphs: a raw character
     * count measures the markup as if the reader could see it.
     */
    @Test
    void noPageBodyOverflowsTheBox() throws IOException {
        Path entries = repoRoot().resolve(
                "src/main/resources/assets/mc3dprint/patchouli_books/guide/en_us/entries");
        assertTrue(Files.isDirectory(entries), "guide entries directory is missing: " + entries);
        List<String> tooLong = new ArrayList<>();
        try (Stream<Path> files = Files.walk(entries)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                JsonObject entry = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
                if (!entry.has("pages")) {
                    continue;
                }
                var pages = entry.getAsJsonArray("pages");
                for (int i = 0; i < pages.size(); i++) {
                    JsonObject obj = pages.get(i).getAsJsonObject();
                    if (!obj.has("text")) {
                        continue;
                    }
                    int budget = i == 0 ? LINES_FIRST
                            : (obj.has("title") ? LINES_TITLED : LINES_PLAIN);
                    int used = renderedLines(obj.get("text").getAsString());
                    if (used > budget) {
                        tooLong.add(f.getFileName() + " page " + i + " needs " + used
                                + " lines but has " + budget);
                    }
                }
            }
        }
        assertTrue(tooLong.isEmpty(),
                "page bodies that run off the page: " + String.join("; ", tooLong));
    }

    /** Estimated rendered line count: macros stripped, breaks honoured, greedy word wrap. */
    private static int renderedLines(String text) {
        String body = text.replace("$(br2)", "\n\n").replace("$(br)", "\n")
                .replace("$(li)", "\n").replaceAll("\\$\\([^)]*\\)", "");
        int total = 0;
        for (String para : body.split("\n", -1)) {
            if (para.isBlank()) {
                total++;
                continue;
            }
            int used = 0;
            int wrapped = 0;
            for (String word : para.trim().split("\\s+")) {
                int add = word.length() + (used > 0 ? 1 : 0);
                if (used + add > CHARS_PER_LINE) {
                    wrapped++;
                    used = word.length();
                } else {
                    used += add;
                }
            }
            total += wrapped + 1;
        }
        return total;
    }
}
