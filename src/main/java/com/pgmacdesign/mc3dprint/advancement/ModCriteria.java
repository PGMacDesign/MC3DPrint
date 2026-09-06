package com.pgmacdesign.mc3dprint.advancement;

import com.pgmacdesign.mc3dprint.MC3DPrint;
//? if >=26.2 {
/*import net.minecraft.advancements.triggers.CriterionTrigger;
*///?} else {
import net.minecraft.advancements.CriterionTrigger;
//?}
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Custom advancement triggers, per the design doc's progression table. */
public final class ModCriteria {
    public static final DeferredRegister<CriterionTrigger<?>> TRIGGERS =
            DeferredRegister.create(Registries.TRIGGER_TYPE, MC3DPrint.MOD_ID);

    /** "First Extrusion" — complete the first item print. */
    public static final BasicTrigger FIRST_EXTRUSION = register("first_extrusion");
    /** "Fabricator" — complete the first structure print. */
    public static final BasicTrigger STRUCTURE_PRINTED = register("structure_printed");
    /** "Architect" — scan a structure onto a disc. */
    public static final BasicTrigger STRUCTURE_SCANNED = register("structure_scanned");
    /** "Matter Matters" — convert 1,000 FU total at winders. */
    public static final BasicTrigger FU_CONVERTED = register("fu_converted");
    /** "Found in the Wild" — discover a blueprint disc in world loot. */
    public static final BasicTrigger LOOT_DISC_FOUND = register("loot_disc_found");
    /** "Refined Print" — complete a blueprint print catalyzed by a Resin. */
    public static final BasicTrigger CATALYZED_PRINT = register("catalyzed_print");
    /**
     * "T7 Online" and "Draconic Fabricator": awarded when the multiblock actually FORMED.
     *
     * <p>These used vanilla's {@code placed_block}, which fires the moment the controller is put
     * down. Placing a controller is not an achievement: the casing square, the corner blocks and
     * the right-click that validates them are the whole task, and a player could earn both by
     * dropping a block on the ground and never building anything.
     */
    public static final BasicTrigger T7_FORMED = register("t7_formed");
    public static final BasicTrigger T8_FORMED = register("t8_formed");

    /** Cumulative wound-FU threshold for Matter Matters (design doc: 1,000). */
    public static final int MATTER_MATTERS_FU = 1_000;
    /** Player persisted-NBT key tracking cumulative wound FU. */
    public static final String TAG_FU_WOUND = MC3DPrint.MOD_ID + ":FuWound";

    /** Registers a singleton trigger under {@code mc3dprint:<name>} and returns it. */
    private static BasicTrigger register(String name) {
        BasicTrigger trigger = new BasicTrigger();
        TRIGGERS.register(name, () -> trigger);
        return trigger;
    }

    private ModCriteria() {}
}
