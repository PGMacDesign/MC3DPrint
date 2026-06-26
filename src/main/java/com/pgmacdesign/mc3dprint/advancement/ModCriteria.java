package com.pgmacdesign.mc3dprint.advancement;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import net.minecraft.advancements.CriterionTrigger;
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
