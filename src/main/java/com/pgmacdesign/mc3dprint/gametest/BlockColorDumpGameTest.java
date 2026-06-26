package com.pgmacdesign.mc3dprint.gametest;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TreeMap;

/**
 * Dumps every block's MapColor to {@code site/public/viewer/data/block_colors.json} — the
 * deterministic RGB source the web Blueprint Viewer tints voxels with. MapColor
 * is the palette Minecraft paints in-game maps with: license-clean (RGB facts,
 * not Mojang art) and the closest "true" flat color per block.
 *
 * <p>Lives in the GameTest harness, not plain JUnit, because a populated block
 * registry needs a real server launch (a headless JUnit bootstrap can't init
 * Forge networking). It's a no-op unless {@code MC3DP_DUMP_COLORS} is set in the
 * environment, so the normal {@code runGameTestServer} suite skips the write:
 *
 * <pre>
 *   MC3DP_DUMP_COLORS=1 ./gradlew runGameTestServer
 * </pre>
 *
 * Regenerate only when the Minecraft version bumps. Blocks with
 * {@link MapColor#NONE} (glass, air, barriers…) are skipped so the viewer's own
 * heuristic/fallback handles them instead of painting them black.
 */
@GameTestHolder(MC3DPrint.MOD_ID)
@PrefixGameTestTemplate(false)
public class BlockColorDumpGameTest {

    @GameTest(template = "empty5", timeoutTicks = 200)
    public void dumpBlockColors(GameTestHelper helper) {
        String flag = System.getenv("MC3DP_DUMP_COLORS");
        if (!"1".equals(flag) && !"true".equals(flag)) {
            helper.succeed(); // opt-in only; normal test runs don't rewrite the file
            return;
        }

        TreeMap<String, int[]> table = new TreeMap<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            MapColor mc;
            try {
                mc = block.defaultBlockState().getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            } catch (Exception e) {
                continue; // a few blocks read the level; let the viewer fall back
            }
            if (mc == null || mc == MapColor.NONE) {
                continue;
            }
            int col = mc.col;
            table.put(id.toString(), new int[]{(col >> 16) & 0xFF, (col >> 8) & 0xFF, col & 0xFF});
        }

        writeJson(table);
        helper.succeed();
    }

    private static void writeJson(TreeMap<String, int[]> table) {
        // runGameTestServer's working dir is <repo>/run, so hop up to the repo root.
        Path cwd = Path.of("").toAbsolutePath();
        Path root = "run".equals(cwd.getFileName().toString()) ? cwd.getParent() : cwd;
        Path out = root.resolve("site").resolve("public").resolve("viewer")
                       .resolve("data").resolve("block_colors.json");

        StringBuilder sb = new StringBuilder("{\n");
        int i = 0, n = table.size();
        for (var e : table.entrySet()) {
            int[] c = e.getValue();
            sb.append("  \"").append(e.getKey()).append("\": [")
              .append(c[0]).append(", ").append(c[1]).append(", ").append(c[2]).append(']')
              .append(++i < n ? ",\n" : "\n");
        }
        sb.append("}\n");

        try {
            Files.createDirectories(out.getParent());
            Files.writeString(out, sb.toString());
            System.out.println("[MC3DPrint] Wrote " + n + " block colors to " + out);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write block_colors.json", e);
        }
    }
}
