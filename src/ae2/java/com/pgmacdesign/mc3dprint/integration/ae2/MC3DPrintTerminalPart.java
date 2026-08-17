package com.pgmacdesign.mc3dprint.integration.ae2;

import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.parts.AEBasePart;

/**
 * The MC3DPrint Terminal: an AE2 cable part that lists what the network's printers and
 * fabricators can print and orders it, paid in Filament Units rather than ingredients.
 *
 * <p>A part rather than a block so it sits on cable exactly like AE2's own terminals. It extends
 * {@code AEBasePart}, which lives outside {@code appeng.api}: that is the intended extension point
 * for addons, but it does couple this class to AE2's internals, so it is the first thing to check
 * when moving between AE2 majors.
 */
public class MC3DPrintTerminalPart extends AEBasePart {

    public MC3DPrintTerminalPart(IPartItem<?> partItem) {
        super(partItem);
        getMainNode().setIdlePowerUsage(0.5);
    }

    /** A flat screen on the cable face, matching the footprint AE2's own terminals use. */
    @Override
    public void getBoxes(IPartCollisionHelper helper) {
        helper.addBox(2, 2, 14, 14, 14, 16);
    }

    @Override
    public IPartModel getStaticModels() {
        return Ae2TerminalModels.forState(isPowered(), isActive());
    }
}
