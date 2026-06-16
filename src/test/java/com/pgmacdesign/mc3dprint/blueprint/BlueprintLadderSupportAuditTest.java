package com.pgmacdesign.mc3dprint.blueprint;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Diagnostic audit for the <b>unbacked ladder</b> bug class surfaced in playtesting
 * (fantasy_wizard_tower, library). A ladder needs a sturdy full block on the side
 * opposite its {@code facing}; if that cell is air (or a non-sturdy block like a
 * fence / pane / slab / stair), the printer's {@code UPDATE_SUPPRESS_DROPS} flag
 * places the ladder anyway, but it pops off as a dropped item on the next block
 * update near it — a SILENT failure the GameTests don't catch (they place without a
 * canSurvive recheck), so the player just can't climb.
 *
 * <p>For every {@code minecraft:ladder} cell in every curated blueprint, this reports
 * what sits on its support side and FLAGS air-backed or non-sturdy-backed ladders.
 * Reads palette strings directly (no Forge registry / server), so the "sturdy" test
 * is a conservative id-denylist heuristic — review flags, don't treat as gospel.
 * OOB support (ladder on the build edge facing outward, backed by world terrain) is
 * reported separately, not auto-flagged.
 *
 * <pre>
 *   ./gradlew test --tests *BlueprintLadderSupportAuditTest* -DauditLadders=true --rerun-tasks
 * </pre>
 */
class BlueprintLadderSupportAuditTest {

    private static final Path SOURCE_DIR =
            Path.of("src", "main", "resources", "data", "mc3dprint", "blueprints");
    private static final Path OUTPUT = Path.of("build", "blueprint-ladder-audit.txt");

    /** Substrings of block ids that do NOT present a sturdy full face a ladder can mount on. */
    private static final String[] NON_STURDY = {
            "fence", "_wall", "wall_torch", "_pane", "iron_bars", "ladder", "_slab",
            "_stairs", "torch", "_sign", "lantern", "chain", "_door", "carpet",
            "scaffolding", "rail", "_button", "lever", "pressure_plate", "vine",
            "redstone_wire", "tripwire", "bell", "campfire", "candle"
    };

    @Test
    @EnabledIfSystemProperty(named = "auditLadders", matches = "true")
    void auditLadders() throws IOException {
        List<Path> files;
        try (var stream = Files.list(SOURCE_DIR)) {
            files = stream.filter(p -> p.toString().endsWith(".blueprint")).sorted().toList();
        }

        List<String> flagged = new ArrayList<>();
        List<String> oob = new ArrayList<>();
        List<String> all = new ArrayList<>();
        int ladderCount = 0;

        for (Path file : files) {
            String name = file.getFileName().toString().replace(".blueprint", "");
            Blueprint bp = readBlueprint(file);
            int sx = bp.sizeX(), sy = bp.sizeY(), sz = bp.sizeZ();

            for (int y = 0; y < sy; y++) {
                for (int z = 0; z < sz; z++) {
                    for (int x = 0; x < sx; x++) {
                        BlueprintBlockState cell = bp.get(x, y, z);
                        if (cell == null || !"minecraft:ladder".equals(cell.blockId())) continue;
                        ladderCount++;
                        String facing = cell.properties().getOrDefault("facing", "north");
                        // support cell = opposite of facing (ladder mounts on the wall behind it)
                        int bx = x, by = y, bz = z;
                        switch (facing) {
                            case "north" -> bz = z + 1; // faces -z, wall on +z
                            case "south" -> bz = z - 1;
                            case "east"  -> bx = x - 1; // faces +x, wall on -x
                            case "west"  -> bx = x + 1;
                            default -> { }
                        }
                        String line = String.format("%-30s ladder (%d,%d,%d) facing=%s -> support ",
                                name, x, y, z, facing);
                        if (bx < 0 || bx >= sx || by < 0 || by >= sy || bz < 0 || bz >= sz) {
                            String l = line + "OOB(world terrain)";
                            all.add(l);
                            oob.add(l);
                            continue;
                        }
                        BlueprintBlockState sup = bp.get(bx, by, bz);
                        String backing = (sup == null || sup.isAir()) ? "AIR" : sup.blockId();
                        String l = line + backing;
                        all.add(l);
                        if ("AIR".equals(backing) || isNonSturdy(backing)) {
                            flagged.add("[UNBACKED] " + l);
                        }
                    }
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Ladder support audit — ").append(ladderCount).append(" ladder cells across ")
                .append(files.size()).append(" builds\n\n");
        sb.append("=== FLAGGED — air/non-sturdy backing (").append(flagged.size()).append(") ===\n");
        flagged.forEach(l -> sb.append(l).append('\n'));
        sb.append("\n=== OOB — backed by world terrain, review (").append(oob.size()).append(") ===\n");
        oob.forEach(l -> sb.append(l).append('\n'));
        sb.append("\n=== ALL LADDERS (").append(all.size()).append(") ===\n");
        all.forEach(l -> sb.append(l).append('\n'));
        Files.createDirectories(OUTPUT.getParent());
        Files.writeString(OUTPUT, sb.toString());
        System.out.println("[LadderAudit] " + flagged.size() + " flagged / " + oob.size()
                + " OOB / " + ladderCount + " ladders -> " + OUTPUT.toAbsolutePath());
    }

    private static boolean isNonSturdy(String blockId) {
        for (String s : NON_STURDY) {
            if (blockId.contains(s)) return true;
        }
        return false;
    }

    private static Blueprint readBlueprint(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            CompoundTag tag = NbtIo.read(new DataInputStream(new GZIPInputStream(in)));
            return BlueprintSerializer.read(tag);
        }
    }
}
