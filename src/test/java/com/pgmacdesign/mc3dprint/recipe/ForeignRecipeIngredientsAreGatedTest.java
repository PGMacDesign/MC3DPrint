package com.pgmacdesign.mc3dprint.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every recipe that names an item from ANOTHER mod must carry a matching
 * {@code neoforge:mod_loaded} condition.
 *
 * <p>Optional-mod content is the mod's normal way of working (Draconic for Tier 8, Patchouli
 * for the Handbook), and an ungated recipe referencing an absent item fails to load with a
 * datapack error on every world that doesn't have that mod. Nothing else catches it: the dev
 * environment has none of those mods installed, so the recipes are skipped by the very
 * condition under test and a missing one only shows up in a player's log.
 */
class ForeignRecipeIngredientsAreGatedTest {

    /** Namespaces that are always present, so they need no condition. */
    private static final Set<String> ALWAYS_PRESENT = Set.of("minecraft", "mc3dprint", "c");

    private static Path repoRoot() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (dir != null && !Files.isDirectory(dir.resolve("src/main/resources/data"))) {
            dir = dir.getParent();
        }
        assertTrue(dir != null, "could not locate repo root from " + System.getProperty("user.dir"));
        return dir;
    }

    @Test
    void everyForeignItemHasAModLoadedCondition() throws IOException {
        Path recipes = repoRoot().resolve("src/main/resources/data/mc3dprint/recipe");
        assertTrue(Files.isDirectory(recipes), "no recipe directory at " + recipes);

        Set<String> problems = new TreeSet<>();
        try (Stream<Path> files = Files.walk(recipes)) {
            List<Path> jsons = files.filter(p -> p.toString().endsWith(".json")).toList();
            assertTrue(!jsons.isEmpty(), "no recipes found under " + recipes);
            for (Path file : jsons) {
                JsonObject recipe = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                Set<String> gated = gatedNamespaces(recipe);
                for (String id : itemIds(recipe)) {
                    String namespace = id.substring(0, Math.max(0, id.indexOf(':')));
                    if (!ALWAYS_PRESENT.contains(namespace) && !gated.contains(namespace)) {
                        problems.add(file.getFileName() + " uses " + id
                                + " without a mod_loaded condition for '" + namespace + "'");
                    }
                }
            }
        }
        assertTrue(problems.isEmpty(), "ungated foreign items in recipes: " + problems);
    }

    private static Set<String> gatedNamespaces(JsonObject recipe) {
        Set<String> out = new TreeSet<>();
        JsonElement conditions = recipe.get("neoforge:conditions");
        if (conditions == null || !conditions.isJsonArray()) {
            return out;
        }
        for (JsonElement element : conditions.getAsJsonArray()) {
            JsonObject condition = element.getAsJsonObject();
            if (condition.has("type") && "neoforge:mod_loaded".equals(condition.get("type").getAsString())
                    && condition.has("modid")) {
                out.add(condition.get("modid").getAsString());
            }
        }
        return out;
    }

    /** Every namespaced id under an "item" or "id" key, at any depth. */
    private static Set<String> itemIds(JsonElement node) {
        Set<String> out = new TreeSet<>();
        collectIds(node, out);
        return out;
    }

    private static void collectIds(JsonElement node, Set<String> out) {
        if (node.isJsonObject()) {
            JsonObject object = node.getAsJsonObject();
            for (String key : object.keySet()) {
                JsonElement value = object.get(key);
                if (("item".equals(key) || "id".equals(key)) && value.isJsonPrimitive()
                        && value.getAsString().contains(":")) {
                    out.add(value.getAsString());
                } else {
                    collectIds(value, out);
                }
            }
        } else if (node.isJsonArray()) {
            for (JsonElement element : node.getAsJsonArray()) {
                collectIds(element, out);
            }
        }
    }

    /** The Handbook recipe specifically: gated, and its result bound to this mod's book. */
    @Test
    void guideBookRecipeIsGatedAndBindsTheBook() throws IOException {
        Path file = repoRoot()
                .resolve("src/main/resources/data/mc3dprint/recipe/guide_book.json");
        assertTrue(Files.isRegularFile(file), "guide_book recipe is missing");
        JsonObject recipe = JsonParser.parseString(Files.readString(file)).getAsJsonObject();

        assertTrue(gatedNamespaces(recipe).contains("patchouli"),
                "the Handbook recipe must be gated on Patchouli being installed");

        JsonObject result = recipe.getAsJsonObject("result");
        assertTrue("patchouli:guide_book".equals(result.get("id").getAsString()),
                "result should be Patchouli's book item, got " + result.get("id"));
        // Without the component the craft yields "Invalid book: no ID defined" — the exact
        // failure that made a plain /give useless, so it is worth pinning here.
        JsonObject components = result.getAsJsonObject("components");
        assertTrue(components != null && components.has("patchouli:book")
                        && "mc3dprint:guide".equals(components.get("patchouli:book").getAsString()),
                "result must bind patchouli:book to mc3dprint:guide, got " + components);

        JsonArray ingredients = recipe.getAsJsonArray("ingredients");
        Set<String> items = itemIds(ingredients);
        assertTrue(items.equals(Set.of("mc3dprint:extrudium_crystal", "minecraft:book")),
                "expected an Extrudium Crystal + a Book, got " + items);
    }
}
