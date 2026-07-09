package com.pgmacdesign.mc3dprint.gametest;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.blueprint.io.VarInt;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * End-to-end /mc3dprint import: a real file in the REAL world import dir, run
 * through the REAL command dispatcher. Regression for the path-guard bug where
 * {@code getWorldPath(LevelResource.ROOT)} yields an unnormalized
 * {@code <world>/./…} and the traversal check rejected every file.
 */
@GameTestHolder(MC3DPrint.MOD_ID)
@PrefixGameTestTemplate(false)
public class ImportCommandGameTests {

    @GameTest(template = "empty5", timeoutTicks = 40)
    public static void importCommandAcceptsFileInWorldImportDir(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        try {
            Path dir = server.getWorldPath(LevelResource.ROOT).resolve("mc3dprint").resolve("import");
            Files.createDirectories(dir);

            // minimal valid Sponge v2 schematic: one stone block
            CompoundTag schem = new CompoundTag();
            schem.putInt("Version", 2);
            schem.putInt("DataVersion", 3465);
            schem.putShort("Width", (short) 1);
            schem.putShort("Height", (short) 1);
            schem.putShort("Length", (short) 1);
            CompoundTag palette = new CompoundTag();
            palette.putInt("minecraft:stone", 0);
            schem.put("Palette", palette);
            schem.putByteArray("BlockData", VarInt.encodeAll(new int[]{0}));
            NbtIo.writeCompressed(schem, dir.resolve("gametest-import.schem"));

            // dispatcher.execute returns the command's own result: 1 = imported, 0 = the
            // sendFailure path (e.g. "No importable file …")
            int result = server.getCommands().getDispatcher().execute(
                    "mc3dprint import gametest-import.schem", server.createCommandSourceStack());
            if (result != 1) {
                helper.fail("import command rejected a file that exists in the import dir (result "
                        + result + ")");
                return;
            }
            helper.succeed();
        } catch (Exception e) {
            helper.fail("import command threw: " + e.getMessage());
        }
    }
}
