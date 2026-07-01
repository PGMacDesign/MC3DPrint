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

        for (String name : new String[]{"watchtower", "fishing_hut", "garden_shed"}) {
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

    @GameTest(template = "empty5", timeoutTicks = 200)
    public static void curatedBlueprintRequiredModsGate(GameTestHelper helper) {
        // PGM-57: a blueprint's required mods are derived from its palette/entity namespaces so a
        // modded build (e.g. an AE2 setup) only surfaces in creative + world loot once that mod is
        // loaded. Verify every curated build's requiredMods() computes without throwing, and that
        // our shipped builds — all vanilla + mc3dprint — declare nothing and are available in dev.
        for (String name : CuratedBlueprints.CURATED_NAMES) {
            java.util.Set<String> mods = CuratedBlueprints.requiredMods(name);
            if (!mods.isEmpty()) {
                helper.fail("Curated build '" + name + "' unexpectedly requires mods " + mods
                        + " — shipped builds must be vanilla/mc3dprint only");
                return;
            }
            if (!CuratedBlueprints.modsAvailable(name)) {
                helper.fail("Curated build '" + name + "' reports unavailable with no required mods");
                return;
            }
        }
        helper.succeed();
    }
}
