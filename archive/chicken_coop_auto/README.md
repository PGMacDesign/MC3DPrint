# Archived: `chicken_coop_auto` (Auto Chicken Cooker)

**Archived 2026-06-18.** Scrapped from the live game — the lava-blade cooker could not be
made to work reliably in-game (chickens flap-fall slowly and often died *in* the lava,
burning the drop; the earlier campfire version killed but didn't cook). Kept here for
reference in case we revisit an auto-cooker design.

Nothing in this directory is compiled or loaded by the mod. It does **not** render in game.

## Contents
- `chicken_coop_auto.blueprint` — the last built blueprint (5×9×5 lava-blade drop).
- `chickenCoopAuto.generator.java.txt` — the `CuratedBlueprintGenerator` method that built it.

## To revive
1. Paste the method from the `.java.txt` back into `CuratedBlueprintGenerator`.
2. Re-add `builds.put("chicken_coop_auto", chickenCoopAuto());` to `generateCuratedBlueprints()`.
3. Re-add `"chicken_coop_auto"` to `CuratedBlueprints.CURATED_NAMES` and the
   `ModCreativeTabs` farm-builds set.
4. Restore the `FarmCollectionGameTests` `FARMS` entry + `chickenCoopRoutesToChest` `@GameTest`.
5. Restore the `chicken_coop_auto` allowlist entries in `BlueprintReachabilityAuditTest`
   (intentional sealed kill shaft) and `BlueprintFireHazardAuditTest` (intentional lava blade).
6. Move `chicken_coop_auto.blueprint` back to `src/main/resources/data/mc3dprint/blueprints/`,
   then regen: `./gradlew test --tests '*CuratedBlueprintGenerator*' -DgenBlueprints=true --rerun-tasks`.

## Lessons (if redesigning)
- A campfire floor kills but **doesn't cook** (no on-fire flag → raw drops).
- A lava blade cooks (death while on-fire) but timing is unreliable for slow-falling chickens.
- A reliable auto-cooker likely needs the kill to happen in a confined 1-tall space where the
  chicken is *forced* onto the hopper (not the lava), or a fundamentally different mechanic.
