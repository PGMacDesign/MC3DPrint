package com.pgmacdesign.mc3dprint.fu;

import com.mojang.logging.LogUtils;
import com.pgmacdesign.mc3dprint.api.FuRegistration;
import com.pgmacdesign.mc3dprint.api.MC3DPrintAPI;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.fml.event.lifecycle.InterModProcessEvent;
import org.slf4j.Logger;

/**
 * Server + lifecycle wiring for recipe-derived FU valuation and the IMC ingress.
 * Registered from {@link com.pgmacdesign.mc3dprint.MC3DPrint}. The client-side
 * recipe bind lives in {@code FuClientBinding} (dist-guarded).
 */
public final class FuEvents {
    private static final Logger LOGGER = LogUtils.getLogger();

    private FuEvents() {}

    /**
     * FORGE bus. Binds recipe data once the server has started — covers headless
     * (gametest / dedicated) servers where no player ever joins.
     */
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        FuValueRegistry.bind(server.getRecipeManager().getRecipes(), server.registryAccess());
    }

    /**
     * FORGE bus. Fires on player join and {@code /reload}, before recipes are
     * synced — re-binds the live recipe data so derivation tracks datapack
     * reloads.
     */
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        MinecraftServer server = event.getPlayerList().getServer();
        FuValueRegistry.bind(server.getRecipeManager().getRecipes(), server.registryAccess());
    }

    /**
     * MOD bus. Consumes {@link MC3DPrintAPI#IMC_REGISTER_FU_VALUE} IMC messages
     * carrying an {@link FuRegistration} payload, so a compat mod can register FU
     * values with no hard dependency on this mod. Parallel-dispatch safe — the
     * registry methods it calls are synchronized.
     */
    public static void onInterModProcess(InterModProcessEvent event) {
        event.getIMCStream(MC3DPrintAPI.IMC_REGISTER_FU_VALUE::equals).forEach(message -> {
            Object payload = message.messageSupplier().get();
            if (payload instanceof FuRegistration reg) {
                MC3DPrintAPI.registerFuValue(reg.item(), reg.fu(), reg.tier());
                LOGGER.debug("IMC: {} registered FU {} for {}", message.senderModId(), reg.fu(), reg.item());
            } else {
                LOGGER.warn("IMC {} from {}: expected FuRegistration payload, got {}",
                        MC3DPrintAPI.IMC_REGISTER_FU_VALUE, message.senderModId(),
                        payload == null ? "null" : payload.getClass().getName());
            }
        });
    }
}
