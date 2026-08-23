package com.pgmacdesign.mc3dprint.integration.ae2;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLConstructModEvent;
import net.minecraftforge.registries.RegisterEvent;

/**
 * Entry point for the AE2 integration, and the gate that keeps it from running when AE2 is absent.
 *
 * <p>It registers <em>itself</em> rather than being called from {@code MC3DPrint}: this whole
 * source directory is compiled only on nodes that can ship AE2, so the always-present core cannot
 * name any class in it without failing to compile where AE2 does not exist. Forge discovers
 * {@link Mod.EventBusSubscriber} by scanning, which needs no reference from the core.
 *
 * <p><b>Two different absences, and this class handles the second one.</b> Compiling on a node
 * without AE2 is handled by the build, which leaves this directory out entirely. But a node that
 * <em>can</em> have AE2 still runs for players who have not installed it, and Forge loads this
 * annotated class either way. Every {@code appeng} reference therefore lives in
 * {@link Ae2Parts}, which is only touched after the {@link ModList} check below: the JVM loads a
 * class on first use, so an un-taken branch never resolves it. Putting the registration inline
 * here instead crashes mod loading with {@code NoClassDefFoundError: appeng/items/parts/PartItem}
 * for every player on this version who does not run AE2, which is most of them.
 */
@Mod.EventBusSubscriber(modid = MC3DPrint.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class Ae2Registry {

    private static final String AE2 = "ae2";

    private Ae2Registry() {}

    private static boolean ae2Present() {
        return ModList.get().isLoaded(AE2);
    }

    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        if (ae2Present()) {
            Ae2Parts.register(event);
        }
    }

    /**
     * Part models must be declared during PRE-INIT. AE2's {@code PartModels} freezes its set once
     * that phase ends and then throws {@code IllegalStateException: Cannot register models after
     * the pre-initialization phase!}, so doing this from common setup crashes startup for every
     * player who has AE2 installed.
     *
     * <p>Worth knowing why the gametests did not catch it: part models are client-side and the
     * oracle is a dedicated server, so the freeze is never reached there. A green suite says
     * nothing about this particular failure.
     */
    @SubscribeEvent
    public static void onConstructMod(FMLConstructModEvent event) {
        if (ae2Present()) {
            event.enqueueWork(Ae2Parts::registerModels);
        }
    }
}
