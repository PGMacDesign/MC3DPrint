package com.pgmacdesign.mc3dprint.fu;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks the FU tooltip line into a shape JEI can filter on.
 *
 * <p>JEI indexes each tooltip line as whitespace-split tokens and matches a query token as a
 * <em>substring</em> of an indexed token. So the tier has to survive that split as ONE token: with
 * "Tier 5" the index gets "tier" and "5", and searching for it also returns every item whose FU
 * cost happens to contain a 5 (50, 15, 250...). With "Tier-5" the index gets "tier-5" and the
 * search is exact.
 *
 * <p>The hyphen therefore looks like a typo and is not one — this test is what stops someone
 * "fixing" it. Quoting the query does not rescue the spaced form, because the tooltip index is
 * already split by the time the query is parsed.
 */
class FuTooltipSearchTest {

    private static final String KEY = "tooltip.mc3dprint.fu_value";

    @Test
    void tierSurvivesJeiTokenSplitAsOneToken() throws IOException {
        String format = langValue(KEY);
        assertNotNull(format, KEY + " is missing from en_us.json");

        String rendered = format.replaceFirst("%s", "5").replaceFirst("%s", "50");
        String tierToken = Arrays.stream(rendered.toLowerCase().split("\\s+"))
                .filter(token -> token.contains("tier"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no token contains \"tier\": " + rendered));

        assertTrue(tierToken.contains("5"),
                "the tier number must sit in the SAME whitespace-delimited token as \"tier\" or JEI "
                        + "cannot filter on it exactly; got token \"" + tierToken + "\" from \"" + rendered + "\"");
    }

    @Test
    void eachTierYieldsADistinctSearchToken() throws IOException {
        String format = langValue(KEY);
        for (int tier = 1; tier <= 8; tier++) {
            String rendered = format.replaceFirst("%s", String.valueOf(tier)).replaceFirst("%s", "1,000");
            String token = Arrays.stream(rendered.toLowerCase().split("\\s+"))
                    .filter(t -> t.contains("tier"))
                    .findFirst()
                    .orElseThrow();
            // The FU cost lives in its own token, so the tier token must not pick up its digits.
            assertEquals("tier-" + tier, token,
                    "tier " + tier + " must index as a single unambiguous token");
        }
    }

    private static String langValue(String key) throws IOException {
        Path lang = repoRoot().resolve("src/main/resources/assets/mc3dprint/lang/en_us.json");
        String json = Files.readString(lang, StandardCharsets.UTF_8);
        int at = json.indexOf('"' + key + '"');
        if (at < 0) {
            return null;
        }
        int open = json.indexOf('"', json.indexOf(':', at) + 1);
        return json.substring(open + 1, json.indexOf('"', open + 1));
    }

    private static Path repoRoot() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (dir != null && !Files.isDirectory(dir.resolve("src/main/resources/assets/mc3dprint/lang"))) {
            dir = dir.getParent();
        }
        assertNotNull(dir, "could not locate the repo root from " + System.getProperty("user.dir"));
        return dir;
    }
}
