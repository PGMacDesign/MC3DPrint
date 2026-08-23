package com.pgmacdesign.mc3dprint.integration.ae2;

import appeng.api.parts.IPartModel;
import appeng.api.parts.PartModels;
import appeng.parts.PartModel;
import com.pgmacdesign.mc3dprint.MC3DPrint;
import net.minecraft.resources.ResourceLocation;

/**
 * The three visual states an AE2 screen part has: unpowered, powered but with no channel, and
 * fully online. AE2 asks for one on every render, so they are built once here.
 *
 * <p>Every model an addon part can return has to be declared to {@link PartModels} during startup
 * or AE2 will not bake it, and the part renders as a missing model with no other complaint.
 */
final class Ae2TerminalModels {

    private static final ResourceLocation BASE = id("part/mc3dprint_terminal_base");
    private static final ResourceLocation OFF = id("part/mc3dprint_terminal_off");
    private static final ResourceLocation ON = id("part/mc3dprint_terminal_on");
    private static final ResourceLocation HAS_CHANNEL = id("part/mc3dprint_terminal_has_channel");

    private static final IPartModel MODEL_OFF = new PartModel(BASE, OFF);
    private static final IPartModel MODEL_ON = new PartModel(BASE, ON);
    private static final IPartModel MODEL_HAS_CHANNEL = new PartModel(BASE, HAS_CHANNEL);

    private Ae2TerminalModels() {}

    static void register() {
        PartModels.registerModels(BASE, OFF, ON, HAS_CHANNEL);
    }

    /** Powered and on the grid is the lit state; powered without a channel is the dim one. */
    static IPartModel forState(boolean powered, boolean active) {
        if (powered && active) {
            return MODEL_HAS_CHANNEL;
        }
        return powered ? MODEL_ON : MODEL_OFF;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MC3DPrint.MOD_ID, path);
    }
}
