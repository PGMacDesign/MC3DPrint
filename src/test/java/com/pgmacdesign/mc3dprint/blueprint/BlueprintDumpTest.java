package com.pgmacdesign.mc3dprint.blueprint;

import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Human-readable dump of every shipped {@code .blueprint} for eyeball / agent
 * fact-checking — the inspection engine behind the {@code /mc3dp-validate-blueprint}
 * skill. Not a normal unit test: gated on {@code -DdumpBlueprints=true} so an
 * ordinary build skips it.
 *
 * <pre>
 *   ./gradlew test --tests *BlueprintDumpTest* -DdumpBlueprints=true
 * </pre>
 *
 * For each curated blueprint it writes {@code build/blueprint-dumps/&lt;name&gt;.txt}
 * containing the dimensions, the full palette legend, and one ASCII grid per Y
 * layer (rows = z south, cols = x east; each cell is a palette-index symbol, '.'
 * = air). That makes missing roofs, floating blocks, open gables, wrong door
 * facings, absent crops/sails, etc. visible without launching the game.
 */
class BlueprintDumpTest {

    private static final Path SOURCE_DIR =
            Path.of("src", "main", "resources", "data", "mc3dprint", "blueprints");
    private static final Path OUTPUT_DIR = Path.of("build", "blueprint-dumps");

    /** Compact per-index symbols: 0-9, A-Z, a-z (62). Air renders as '.'. */
    private static final String SYMBOLS =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    @BeforeAll
    static void bootstrap() {
        try {
            SharedConstants.setVersion(DetectedVersion.BUILT_IN);
            Bootstrap.bootStrap();
        } catch (Throwable ignored) {
            // serialize()/read() are pure string+NBT work; registry not required
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "dumpBlueprints", matches = "true")
    void dumpCuratedBlueprints() throws IOException {
        Files.createDirectories(OUTPUT_DIR);
        List<Path> files;
        try (var stream = Files.list(SOURCE_DIR)) {
            files = stream.filter(p -> p.toString().endsWith(".blueprint")).sorted().toList();
        }
        int dumped = 0;
        for (Path file : files) {
            String name = file.getFileName().toString().replace(".blueprint", "");
            Blueprint bp = readBlueprint(file);
            Files.writeString(OUTPUT_DIR.resolve(name + ".txt"), render(name, bp));
            dumped++;
        }
        System.out.println("[BlueprintDumpTest] dumped " + dumped + " blueprint(s) to "
                + OUTPUT_DIR.toAbsolutePath());
    }

    private static Blueprint readBlueprint(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            CompoundTag tag = NbtIo.read(new DataInputStream(new GZIPInputStream(in)));
            return BlueprintSerializer.read(tag);
        }
    }

    private static String render(String name, Blueprint bp) {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append("  \"").append(bp.name()).append("\"\n");
        sb.append("dims  X(width)=").append(bp.sizeX())
                .append("  Y(height)=").append(bp.sizeY())
                .append("  Z(depth)=").append(bp.sizeZ())
                .append("  blocks=").append(bp.blockCount()).append('\n');

        List<BlueprintBlockState> palette = bp.palette();
        sb.append("\nPalette (").append(palette.size()).append("):\n");
        for (int i = 0; i < palette.size(); i++) {
            sb.append("  ").append(symbol(i)).append(" = ")
                    .append(palette.get(i).serialize()).append('\n');
        }

        sb.append("\nLayers (rows = z 0..").append(bp.sizeZ() - 1)
                .append(" south; cols = x 0..").append(bp.sizeX() - 1)
                .append(" east; '.' = air):\n");
        int[] raw = bp.rawBlocks(); // palette index per cell (NO_BLOCK = air)
        for (int y = 0; y < bp.sizeY(); y++) {
            sb.append("\ny=").append(y).append('\n');
            for (int z = 0; z < bp.sizeZ(); z++) {
                sb.append("  ");
                for (int x = 0; x < bp.sizeX(); x++) {
                    int idx = raw[bp.index(x, y, z)];
                    sb.append(idx == Blueprint.NO_BLOCK ? '.' : symbol(idx));
                }
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    private static char symbol(int index) {
        return index >= 0 && index < SYMBOLS.length() ? SYMBOLS.charAt(index) : '#';
    }
}
