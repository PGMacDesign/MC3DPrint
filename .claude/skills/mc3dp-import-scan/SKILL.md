---
name: mc3dp-import-scan
description: >-
  Import a player-SCANNED MC3DPrint build (one hand-built in-game and captured with the
  Structure Scanner, NOT generated from a spec) into the curated blueprint set. Use this
  whenever the user has a real .blueprint scan to add — "I scanned a build, add it", "import
  this scan", "I built a castle in-game and scanned it", "here's the scanned file, make it
  curated", "add my hand-built farm to the mod", or hands over a UUID-named .blueprint from a
  world's mc3dprint/blueprints folder. This is the SCAN path; for turning a written spec into
  a procedural builder use mc3dp-create-blueprint instead. The skill copies the scan into the
  generator, strips scaffolding (the user's corner-scanning crutch) and container contents,
  guards the FU economy (infers a value for any unvalued block, or raises it as a question if
  valuing it would break the economy), then generates, validates, and tests.
---

# Import a player scan → curated blueprint

A scanned build is the **source of truth** — the player built it in-world and captured it, so
unlike `mc3dp-create-blueprint` you do **not** rebuild it procedurally. You copy the raw scan
in verbatim and apply only the few normalizations that make it shippable. The interesting work
is the **FU-economy guard** (does every block print? does valuing a new block break anything?)
and stripping the **scaffolding** the user placed to reach into corners while scanning.

This is exactly how `tristans_castle` and `pumpkin_melon_farm` got in — read those two builders
in `CuratedBlueprintGenerator.java` first; you're adding a third of the same shape.

## What to get from the user first

- **The scan file** — its path. Raw scans land in a world at
  `<world>/mc3dprint/blueprints/<uuid>.blueprint` with random UUID names. If they only give a
  world name, list that folder and take the newest file (and confirm it's the right one by its
  dims via the triage script below).
- **The curated name** — the snake_case id (`grand_observatory`) and the display title
  (`Grand Observatory`). The scan's own name is a useless `Scan @ x,y,z`.
- **Anything special** — working redstone/mechanism to preserve, a clock interval, a farm that
  should print ungrown, an intended printer tier. Most static builds need nothing here.

## Workflow

1. **Triage the scan** before touching Java — decode it and see what's inside:
   ```bash
   python3 .claude/skills/mc3dp-import-scan/scripts/triage_scan.py <path-to-scan.blueprint>
   ```
   It prints dims, min printer tier, the full palette, the **scaffolding cells** to strip, any
   **block-entities carrying container Items** to strip, and the **distinct block-id list** —
   your FU checklist for step 6.

2. **Copy the scan in** to `src/test/resources/scanned/<name>.blueprint` (this is the path
   `loadScannedBlueprint("<name>")` reads). Keep the original UUID file untouched in the world.

3. **Write a builder** `private static Blueprint <name>()` in
   `src/test/java/.../blueprint/CuratedBlueprintGenerator.java`. Copy the `tristansCastle()`
   shape: `loadScannedBlueprint("<name>")`, loop every cell copying states through, copy
   block-entities. Apply the normalizations below. Re-title via `Blueprint.builder("<Display
   Title>", scan.sizeX(), scan.sizeY(), scan.sizeZ())`.

   - **Strip scaffolding** — see the dedicated section below. This is non-negotiable for scans.
   - **Strip container contents** — `tag.remove("Items")` on every block-entity, so a printed
     chest/furnace arrives empty (Quartermaster/Treasure resins fill them, not the scan).
   - **Plant crops young** if it's a farm — reuse `youngStem(...)` / model it on
     `normalizeScannedFarm(...)`, so the print is ungrown and a Verdant resin matures it.
   - **Preserve everything else verbatim** — redstone wiring, repeater delays, the baked
     `mc3dprint:redstone_clock` interval, sign text, stair/door states. That fidelity is the
     whole point of a scan; don't "tidy" it.

4. **Register in BOTH places** (they must match 1:1 or install/audit breaks):
   - `generateCuratedBlueprints()` builds map: `builds.put("<name>", <name>());`
   - `CuratedBlueprints.CURATED_NAMES` (`src/main/java/.../blueprint/CuratedBlueprints.java`).

5. **Generate the file:**
   ```bash
   ./gradlew test --tests '*CuratedBlueprintGenerator*' -DgenBlueprints=true --rerun-tasks
   ```
   Writes `src/main/resources/data/mc3dprint/blueprints/<name>.blueprint`. Without
   `-DgenBlueprints=true` the generator is `@EnabledIfSystemProperty`-skipped and writes nothing
   (a silent "BUILD SUCCESSFUL, 0 files" trap).

6. **Guard the FU economy** — the most important step. See the decision matrix below. The
   authoritative oracle is the printability gametest:
   ```bash
   ./gradlew runGameTestServer -q
   ```
   `CuratedBlueprintPrintabilityGameTests` FAILS listing any block in your build that has no FU
   value and isn't structural matter — i.e. it would silently fail to print in strict mode.
   Resolve every one (value it, confirm it derives, or raise a question) — never just add it to
   the test's `KNOWN_UNVALUED_BLOCKS` exemption to dodge the failure.

7. **Validate the geometry** — hand off to `mc3dp-validate-blueprint` (ASCII dump + checklist)
   to confirm the scan imported intact (right dims, no accidental holes from stripping, mechanism
   present). For a farm, confirm the harvest mechanism and collection routing survived.

8. **If you changed FU values**, the `fuValues` list does **not** merge into an existing config —
   delete the dev `run/config/mc3dprint-common.toml` AND the game instance's
   `config/mc3dprint-common.toml` so the new value loads (see CLAUDE.md). Scalar config changes
   don't need this; the `fuValues` list does.

9. **Ship** — `./gradlew build`, copy the jar over the Prism mods folder (replace, never
   duplicate), then commit & push (no Claude attribution).

## The scaffolding rule (always strip it)

The user places **`minecraft:scaffolding`** as a temporary platform to reach the far corners and
upper edges of a build the scanner box can't otherwise cover. It is **scanning apparatus, never
part of the build**, so it must never ship. Strip every scaffolding cell on import (don't try to
detect "only the corner ones" — all scaffolding in a scan is a crutch) and **report the count**
you removed so the user can catch a false positive in the rare case a build genuinely uses
scaffolding as decor.

In the builder, skip the cell instead of copying it (an unset cell is `NO_BLOCK`, which the
printer skips):

```java
BlueprintBlockState st = scan.get(x, y, z);
if (st != null && !st.blockId().equals("minecraft:scaffolding")) {
    b.set(x, y, z, st);   // scaffolding cells fall through → left empty
}
```

Count what you skip and report it: *"Stripped 47 scaffolding cells (corner-scan apparatus)."*
If the count is surprisingly high or zero when you expected some, say so — it's a signal the
scan or the strip is off.

## FU economy guard — value it, or raise the question

Printing is **down-only** and winding is **1:1 exact-tier**, so a block's FU value isn't
cosmetic — it decides what its filament can print and whether it can be laundered. For every
distinct block in the scan (the triage list), it falls into one of these:

| Case | What it means | Action |
|---|---|---|
| **Has a value** | `FuValueRegistry.valueOf` resolves it | nothing to do |
| **Derives** | made via a vanilla recipe type (crafting/smelting/stonecutting) — most blocks | nothing to do; the `RecipeFuValuator` prices it automatically |
| **Itemless structural** | `asItem() == AIR` (water, farmland, crops, wall torches, tripwire) | prints free; nothing to do |
| **Unvalued leaf** | no value, no recipe, not structural (the gametest flags it) | **infer a value + tier**, OR **raise a question** — see below |

When you hit an unvalued leaf, **infer** a value+tier from its acquisition rarity, anchored to
the vanilla ladder (iron 20@T2, diamond 50@T5, netherite_scrap 125@T6 …) — see
`docs/rebalance/acquisition-rarity.md` and the `mc3dp-mod-filament-unit-compat` skill's
`references/fu-model.md`. Add it to the `fuValues` list in `FuValueRegistry.java` as
`"minecraft:<id>=<fu>@<tier>"`.

**But stop and raise it as a question to the user — do not silently value — when valuing it
would risk breaking the economy:**

- **Abundance / laundering** — the block is farmable or cheaply renewable, and the tier you'd
  need would let its spool print something *rarer* than itself (the abundance rule). If you
  value it, it almost certainly also needs adding to the **winder blacklist** (`ModItemTags` /
  the `WinderBlockEntity` tag) so it can't be wound back into FU — flag that explicitly. (This
  is exactly why `cactus`/`kelp` are `2@1` + blacklisted, and `powder_snow_bucket` is `16@2` +
  blacklisted.)
- **Should-be-unprintable** — the block is a survival-unobtainable, a one-of-a-kind, or
  deliberately unvalued (dragon egg, wither skeleton skull, command blocks, spawners,
  budding amethyst, infested/technical blocks). Valuing it makes printable something the design
  intentionally refuses. Ask before doing it; the answer may be "swap the block" or "ship it
  unprintable and exempt it."
- **Block-item mismatch** — the placed block's `asItem()` is a different item than you'd expect
  (powder snow → bucket, etc.), so the value must target the *item* that backs it, and that
  item may need the blacklist too. Confirm the backing item before valuing.

Frame the question with the concrete tradeoff, e.g.: *"`budding_amethyst` has no FU value and is
survival-unobtainable by design. I can value it (~T4) to make it print, but that makes a normally
unobtainable block craftable-by-print. Value it, swap it for `amethyst_block`, or ship the build
with it exempted/unprintable?"* Then do what they choose.

## Gotchas

- **Axis order**: `Blueprint.builder(name, sizeX, sizeY, sizeZ)` — use the scan's own
  `scan.sizeX()/sizeY()/sizeZ()`; don't transpose. Out-of-range `b.set` throws at generation.
- **Don't dodge the gametest** with `KNOWN_UNVALUED_BLOCKS` — that exemption is for blocks
  intentionally unprintable; using it to silence a real gap reintroduces the silent-skip bug.
- **Footprint vs tier**: min tier = smallest whose `MachineTier.maxFootprint` ≥ max(sizeX,
  sizeZ). A wide scan may need a T7/T8 fabricator; tell the user if so.
- **Keep functional NBT**: only strip `Items` from block-entities; the clock interval, sign
  text, and other functional tags must survive or a mechanism breaks.
- **Update the Patchouli guide** only if this import changes a player-facing feature (a new
  showcase category, etc.); a single new build usually doesn't.
