package com.pgmacdesign.mc3dprint.registry;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * AE2 part models must sit on the OUTWARD face of their host cable.
 *
 * <p>AE2 authors part models with that face at {@code z = 0} and the block interior at {@code z =
 * 16}, which is the opposite of the convention its own {@code IPartCollisionHelper} uses: AE2's
 * {@code AbstractReportingPart.getBoxes} is {@code addBox(2, 2, 14, 14, 14, 16)}. Ours was written
 * to the collision convention, so the hit box landed on the face the player clicked while the panel
 * rendered on the far side of the cable, which reads in game as the part passing through the block.
 *
 * <p>Nothing else can catch this. The GameTest oracle is a dedicated server and never bakes a
 * model, so the only previous detector was somebody placing one and looking at it.
 */
class PartModelConventionTest {

    /** Deepest an AE2 face part may extend into the block, in model pixels. */
    private static final double MAX_DEPTH = 4.0;

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
    void everyPartModelHugsTheOutwardFace() throws IOException {
        Path parts = repoRoot().resolve("src/main/resources/assets/mc3dprint/models/part");
        if (!Files.isDirectory(parts)) {
            return; // no part models on this build
        }
        try (Stream<Path> files = Files.list(parts)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                JsonObject model = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
                if (!model.has("elements")) {
                    continue;
                }
                for (var el : model.getAsJsonArray("elements")) {
                    JsonArray from = el.getAsJsonObject().getAsJsonArray("from");
                    JsonArray to = el.getAsJsonObject().getAsJsonArray("to");
                    double zFrom = from.get(2).getAsDouble();
                    double zTo = to.get(2).getAsDouble();
                    assertTrue(zFrom <= 0.001,
                            f.getFileName() + " starts at z=" + zFrom + "; an AE2 part model's"
                                    + " outward face is z=0, so this renders through the block to"
                                    + " the opposite side of the one that was clicked");
                    assertTrue(zTo <= MAX_DEPTH,
                            f.getFileName() + " reaches z=" + zTo + ", deeper than " + MAX_DEPTH
                                    + " into the block");
                }
            }
        }
    }
}
