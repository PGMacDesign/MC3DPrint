package com.pgmacdesign.mc3dprint.loot;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.blueprint.CuratedBlueprints;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The pure half of blueprint world-loot: table matching, pool narrowing and chance
 * scaling, all free of server state so they can be driven directly from JUnit.
 * {@link AddBlueprintDiscModifier} supplies the world-facing half (the ledger reads
 * and writes, the grant, the cycle reset).
 */
public final class BlueprintLootPool {

    /** Path prefixes a table must start with to carry blueprints, absent an explicit list. */
    public static final List<String> DEFAULT_TABLES = List.of("chests/", "archaeology/");

    private BlueprintLootPool() {}

    /**
     * Whether a queried loot table can carry a blueprint.
     *
     * <p>Matches on the PATH only, anchored at the start, and ignores the namespace
     * entirely so a modded structure's {@code somemod:chests/tower} qualifies exactly
     * like a vanilla one. Deliberately not the {@code toString().contains()} shape the
     * resin flavor matcher uses: substring matching would pull in {@code blocks/chest}
     * and any {@code xchests/} path a mod happens to name.
     */
    public static boolean matchesTable(@Nullable ResourceLocation tableId, List<String> prefixes) {
        if (tableId == null) {
            return false;
        }
        String path = tableId.getPath();
        for (String prefix : prefixes) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The roll probability actually used: the data value scaled by the config multiplier
     * and clamped, so no configuration can invert the roll or push it out of range.
     */
    public static float effectiveChance(float base, double multiplier) {
        double scaled = base * multiplier;
        if (scaled <= 0.0D) {
            return 0.0F;
        }
        return scaled >= 1.0D ? 1.0F : (float) scaled;
    }

    /**
     * The builds this world can actually hand out: the configured list, or the whole
     * opt-out pool ({@link CuratedBlueprints#lootBlueprints()}, everything minus
     * {@link CuratedBlueprints#LOOT_EXCLUDED}) when that list is empty, with any build
     * whose required mods are missing removed.
     *
     * <p>The single definition of the candidate pool, shared by the loot roll, the
     * {@code /mc3dprint discovered} command and the tests, so cycle completion is always
     * measured against the same set the roll draws from. Measured against the full
     * curated list instead, a server missing an optional mod could never finish a cycle.
     */
    public static List<String> availableFrom(List<String> configured) {
        List<String> names = configured.isEmpty() ? CuratedBlueprints.lootBlueprints() : configured;
        return names.stream()
                .filter(CuratedBlueprints::modsAvailable)
                .filter(name -> !UNLOADABLE.contains(idFor(name)))
                .toList();
    }

    // Curated files that failed to install into this world's store. CuratedBlueprints.install
    // skips a blueprint it can't read rather than aborting server start, so a build can pass
    // the mod gate and still be unloadable. Left in the pool it can never be granted, which
    // both wastes rolls and stalls no-duplicate cycles forever, since completion is measured
    // by the pool emptying. Discovered lazily on the first failed load.
    private static final Set<UUID> UNLOADABLE = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** Drops a build that isn't in the world store from the pool for the rest of this world. */
    public static void markUnloadable(UUID id) {
        UNLOADABLE.add(id);
    }

    /**
     * Clears what we learned about unloadable builds. Called when the curated set is
     * (re)installed, so a store that has just been repopulated, or a different world
     * loaded in the same session, starts from a clean slate.
     */
    public static void resetUnloadable() {
        UNLOADABLE.clear();
    }

    /**
     * The builds still findable this cycle: {@code available} minus anything already in
     * the discovery ledger. An empty result means the cycle is complete, which is why
     * {@code available} must already be mod-filtered by the caller: measured against the
     * full curated list, a server missing an optional mod could never finish a cycle.
     */
    public static List<String> candidates(List<String> available, Set<UUID> discovered) {
        if (discovered.isEmpty()) {
            return available;
        }
        List<String> out = new ArrayList<>(available.size());
        for (String name : available) {
            if (!discovered.contains(idFor(name))) {
                out.add(name);
            }
        }
        return out;
    }

    /** The deterministic curated UUID for a build name. */
    public static UUID idFor(String name) {
        return CuratedBlueprints.uuidFor(MC3DPrint.MOD_ID, name);
    }

    /** The curated UUIDs of every build in {@code names}, for seeding and reverse lookups. */
    public static Set<UUID> idsFor(List<String> names) {
        Set<UUID> out = new java.util.HashSet<>(names.size());
        for (String name : names) {
            out.add(idFor(name));
        }
        return out;
    }
}
