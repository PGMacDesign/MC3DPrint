package com.pgmacdesign.mc3dprint.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every recipe that names an item from ANOTHER mod must carry a matching
 * {@code forge:mod_loaded} condition.
 *
 * <p>Optional-mod content is the mod's normal way of working (Draconic for Tier 8, Patchouli
 * for the Handbook), and an ungated recipe referencing an absent item fails to load with a
 * datapack error on every world that doesn't have that mod. Nothing else catches it: the dev
 * environment has none of those mods installed, so the recipes are skipped by the very
 * condition under test and a missing one only shows up in a player's log.
 */
class ForeignRecipeIngredientsAreGatedTest {

    /** Namespaces that are always present, so they need no condition. */
    private static final Set<String> ALWAYS_PRESENT = Set.of("minecraft", "mc3dprint", "forge", "c");

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
        Path recipes = repoRoot().resolve("src/main/resources/data/mc3dprint/recipes");
        assertTrue(Files.isDirectory(recipes), "no recipe directory at " + recipes);

        Set<String> problems = new TreeSet<>();
        try (Stream<Path> files = Files.walk(recipes)) {
            List<Path> jsons = files.filter(p -> p.toString().endsWith(".json")).toList();
            assertTrue(!jsons.isEmpty(), "no recipes found under " + recipes);
            for (Path file : jsons) {
                JsonElement recipe = JsonParser.parseString(Files.readString(file));
                check(recipe, Set.of(), file.getFileName().toString(), problems);
            }
        }
        assertTrue(problems.isEmpty(), "ungated foreign items in recipes: " + problems);
    }

    /**
     * Walks a recipe document, carrying the conditions seen so far down into the subtree they
     * guard. A {@code forge:conditional} wrapper puts each variant's conditions beside the
     * recipe they gate rather than at the top of the file, so a flat scan would read every
     * item as ungated.
     */
    private static void check(JsonElement node, Set<String> gated, String file, Set<String> problems) {
        if (node.isJsonArray()) {
            for (JsonElement element : node.getAsJsonArray()) {
                check(element, gated, file, problems);
            }
            return;
        }
        if (!node.isJsonObject()) {
            return;
        }
        JsonObject object = node.getAsJsonObject();
        Set<String> inScope = new HashSet<>(gated);
        inScope.addAll(gatedNamespaces(object));
        for (String key : object.keySet()) {
            JsonElement value = object.get(key);
            if (("item".equals(key) || "id".equals(key)) && value.isJsonPrimitive()
                    && value.getAsString().contains(":")) {
                String id = value.getAsString();
                String namespace = id.substring(0, id.indexOf(':'));
                if (!ALWAYS_PRESENT.contains(namespace) && !inScope.contains(namespace)) {
                    problems.add(file + " uses " + id
                            + " without a mod_loaded condition for '" + namespace + "'");
                }
            } else if (!"conditions".equals(key)) {
                check(value, inScope, file, problems);
            }
        }
    }

    private static Set<String> gatedNamespaces(JsonObject recipe) {
        Set<String> out = new TreeSet<>();
        JsonElement conditions = recipe.get("conditions");
        if (conditions == null || !conditions.isJsonArray()) {
            return out;
        }
        for (JsonElement element : conditions.getAsJsonArray()) {
            JsonObject condition = element.getAsJsonObject();
            if (condition.has("type") && "forge:mod_loaded".equals(condition.get("type").getAsString())
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
                .resolve("src/main/resources/data/mc3dprint/recipes/guide_book.json");
        assertTrue(Files.isRegularFile(file), "guide_book recipe is missing");
        JsonObject wrapper = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        JsonObject variant = wrapper.getAsJsonArray("recipes").get(0).getAsJsonObject();

        assertTrue(gatedNamespaces(variant).contains("patchouli"),
                "the Handbook recipe must be gated on Patchouli being installed");

        JsonObject recipe = variant.getAsJsonObject("recipe");
        JsonObject result = recipe.getAsJsonObject("result");
        assertTrue("patchouli:guide_book".equals(result.get("item").getAsString()),
                "result should be Patchouli's book item, got " + result.get("item"));
        // Without the tag the craft yields "Invalid book: no ID defined", the exact failure
        // that makes a plain /give useless, so it is worth pinning here. Forge reads a result
        // "nbt" object through CraftingHelper, which is what makes a bound book craftable at
        // all on this version.
        JsonObject nbt = result.getAsJsonObject("nbt");
        assertTrue(nbt != null && nbt.has("patchouli:book")
                        && "mc3dprint:guide".equals(nbt.get("patchouli:book").getAsString()),
                "result must bind patchouli:book to mc3dprint:guide, got " + nbt);

        JsonArray ingredients = recipe.getAsJsonArray("ingredients");
        Set<String> items = itemIds(ingredients);
        // Count first: itemIds returns a Set, so two Books would collapse to one entry here.
        assertTrue(ingredients.size() == 2,
                "expected exactly two ingredient entries, got " + ingredients);
        assertTrue(items.equals(Set.of("mc3dprint:extrudium_crystal", "minecraft:book")),
                "expected an Extrudium Crystal + a Book, got " + items);
    }
}
