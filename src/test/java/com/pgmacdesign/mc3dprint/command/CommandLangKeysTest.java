package com.pgmacdesign.mc3dprint.command;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every {@code command.mc3dprint.*} / {@code message.mc3dprint.*} key a command class asks
 * {@code Component.translatable} for must exist in en_us.json.
 *
 * <p>A missing key isn't a compile or runtime error: the client just prints the raw key at the
 * player, which is invisible to every other test we run. Command feedback is exactly where
 * that happens, since a command's whole output is translatable text.
 */
class CommandLangKeysTest {

    private static final Pattern TRANSLATABLE = Pattern.compile(
            "\"((?:command|message)\\.mc3dprint\\.[A-Za-z0-9_.]+)\"");

    private static Path repoRoot() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (dir != null && !Files.isDirectory(dir.resolve("src/main/resources/assets"))) {
            dir = dir.getParent();
        }
        assertTrue(dir != null, "could not locate repo root from " + System.getProperty("user.dir"));
        return dir;
    }

    @Test
    void everyCommandFeedbackKeyIsTranslated() throws IOException {
        Path root = repoRoot();
        JsonObject lang = JsonParser.parseString(Files.readString(
                        root.resolve("src/main/resources/assets/mc3dprint/lang/en_us.json")))
                .getAsJsonObject();

        Set<String> missing = new TreeSet<>();
        Path commands = root.resolve("src/main/java/com/pgmacdesign/mc3dprint/command");
        try (Stream<Path> sources = Files.walk(commands)) {
            List<Path> files = sources.filter(p -> p.toString().endsWith(".java")).toList();
            assertTrue(!files.isEmpty(), "no command sources found under " + commands);
            for (Path file : files) {
                Matcher matcher = TRANSLATABLE.matcher(Files.readString(file));
                while (matcher.find()) {
                    String key = matcher.group(1);
                    if (!lang.has(key)) {
                        missing.add(key + " (" + file.getFileName() + ")");
                    }
                }
            }
        }
        assertTrue(missing.isEmpty(), "command feedback keys missing from en_us.json: " + missing);
    }
}
