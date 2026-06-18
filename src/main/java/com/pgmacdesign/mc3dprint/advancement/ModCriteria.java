package com.pgmacdesign.mc3dprint.advancement;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Custom advancement triggers, per the design doc's progression table. */
public final class ModCriteria {
    /** "First Extrusion" — complete the first item print. */
    public static final BasicTrigger FIRST_EXTRUSION = new BasicTrigger(id("first_extrusion"));
    /** "Fabricator" — complete the first structure print. */
    public static final BasicTrigger STRUCTURE_PRINTED = new BasicTrigger(id("structure_printed"));
    /** "Architect" — scan a structure onto a disc. */
    public static final BasicTrigger STRUCTURE_SCANNED = new BasicTrigger(id("structure_scanned"));
    /** "Matter Matters" — convert 1,000 FU total at winders. */
    public static final BasicTrigger FU_CONVERTED = new BasicTrigger(id("fu_converted"));
    /** "Found in the Wild" — discover a blueprint disc in world loot. */
    public static final BasicTrigger LOOT_DISC_FOUND = new BasicTrigger(id("loot_disc_found"));
    /** "Refined Print" — complete a blueprint print catalyzed by a Resin. */
    public static final BasicTrigger CATALYZED_PRINT = new BasicTrigger(id("catalyzed_print"));

    /** Cumulative wound-FU threshold for Matter Matters (design doc: 1,000). */
    public static final int MATTER_MATTERS_FU = 1_000;
    /** Player persisted-NBT key tracking cumulative wound FU. */
    public static final String TAG_FU_WOUND = MC3DPrint.MOD_ID + ":FuWound";

    public static void register() {
        CriteriaTriggers.register(FIRST_EXTRUSION);
        CriteriaTriggers.register(STRUCTURE_PRINTED);
        CriteriaTriggers.register(STRUCTURE_SCANNED);
        CriteriaTriggers.register(FU_CONVERTED);
        CriteriaTriggers.register(LOOT_DISC_FOUND);
        CriteriaTriggers.register(CATALYZED_PRINT);
    }

    private static ResourceLocation id(String path) {
        return Objects.requireNonNull(ResourceLocation.tryParse(MC3DPrint.MOD_ID + ":" + path));
    }

    private ModCriteria() {}
}
