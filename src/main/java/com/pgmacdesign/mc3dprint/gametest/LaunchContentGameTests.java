package com.pgmacdesign.mc3dprint.gametest;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.blueprint.Blueprint;
import com.pgmacdesign.mc3dprint.blueprint.BlueprintFileStore;
import com.pgmacdesign.mc3dprint.blueprint.CuratedBlueprints;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Optional;

@GameTestHolder(MC3DPrint.MOD_ID)
@PrefixGameTestTemplate(false)
public class LaunchContentGameTests {

    @GameTest(template = "empty5", timeoutTicks = 100)
    public static void curatedBlueprintsInstallIntoWorldStore(GameTestHelper helper) {
        CuratedBlueprints.install(helper.getLevel().getServer());
        BlueprintFileStore store = BlueprintFileStore.forServer(helper.getLevel().getServer());

        for (String name : new String[]{"starter_hut", "watchtower", "storage_shed"}) {
            Optional<Blueprint> blueprint = store.load(CuratedBlueprints.uuidFor(MC3DPrint.MOD_ID, name));
            if (blueprint.isEmpty()) {
                helper.fail("Curated blueprint not installed: " + name);
                return;
            }
            if (blueprint.get().blockCount() == 0) {
                helper.fail("Curated blueprint is empty: " + name);
                return;
            }
        }
        helper.succeed();
    }
}
