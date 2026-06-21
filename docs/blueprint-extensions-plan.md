# Blueprint Pipeline Extensions — Plan

Three extensions to what blueprints distribute, gate, and capture. Status as of 2026-06-20.

| # | Feature | Status |
|---|---------|--------|
| 1 | World-loot distribution (opt-out) | **Shipped** (local; pending commit) |
| 2 | Restricted items + per-blueprint allowlist | **Planned** |
| 3 | Decorative entity support (armor stands, frames, paintings, carts, boats) | **Built (grilled); pending in-game test** |

---

## 1. World-loot distribution — opt-out (SHIPPED)

**Rule (standing, per PGMacDesign):** *every* curated blueprint is available as world loot, and
every blueprint added going forward is too — automatically. The only way one stays out of loot is
an explicit decision to exclude it.

**Mechanism:**
- `CuratedBlueprints.lootBlueprints()` = `CURATED_NAMES` minus `CuratedBlueprints.LOOT_EXCLUDED`
  (a `Set<String>`, currently empty). This is the loot pool.
- `loot/AddBlueprintDiscModifier`: when the JSON's `blueprints` list is **empty**, it draws from
  `lootBlueprints()`. A non-empty list is an explicit override (kept for pack-maker flexibility).
- `data/mc3dprint/loot_modifiers/world_blueprints.json` — one modifier, `blueprints: []`, `chance`
  `0.12` per matched chest, across ~35 vanilla chest tables (dungeons, mineshafts, temples, igloos,
  mansions, shipwrecks, ocean ruins, ruined portals, strongholds, ancient cities, village job-site
  + house chests, nether fortress + all bastion chests, End-city treasure). Replaced the prior two
  hand-picked lists (`village_blueprints` / `exploration_blueprints`, ~23 builds) → now all 133.

**Operations:**
- **Remove a build from loot:** add its name to `LOOT_EXCLUDED` (one line).
- **Add a build to loot:** nothing — it's automatic once it's in `CURATED_NAMES`.
- **Tune frequency:** the single `chance` in `world_blueprints.json`.

**Note:** distribution is flat (any build can appear in any of the listed chests). Build-tier →
chest-tier bucketing was considered and deliberately *not* done (the rule is "everywhere"); revisit
only if early-game chests yielding end-game trophies proves a problem.

---

## 2. Restricted items + per-blueprint allowlist (PLANNED)

**Goal:** keep special items (mob heads/skulls, other trophies) **unprintable and unwindable by
default**, but printable on **specific blueprints PGMacDesign designates** — so a unique build can
include a dragon head without making dragon heads generally craftable-by-print.

**Design (mirrors the Resin official-only gate):**
- **Restricted set** — designated **per item, case by case** (no category rules like "all skulls").
  A restricted item is winder-blacklisted (never → Filament Units) and skipped by strict mode
  everywhere by default.
- **Per-blueprint allowlist** — baked **into the `.blueprint` file** (a new format field; shares the
  version bump with feature #3). A restricted item places **only if** `BlueprintDiscItem.isOfficial`
  **AND** the blueprint's allowlist contains it. Player-scanned/imported discs are non-official, so
  a scanned build containing a dragon head silently skips it — the anti-exploit gate.
- **Cost / quote accuracy** — keep the item's Filament-Unit value as the **print cost** when allowed.
  Make **both** the quote (`BlueprintDiscItem.blueprintPrintCost`) **and** the per-block charge
  **allowlist-aware**, so a restricted item counts toward the quote and is charged *only* when it
  will actually place. Verified prerequisite: the quote (`TAG_PRINT_COST`) is **display-only** —
  Filament Units are charged per-placed-block (`drainFu` fires after `setBlock`; skipped blocks hit
  a no-charge fast-forward), so no Filament Units can ever be "stolen" for a block that doesn't
  print. The remaining work is purely making the *quote number* honest for restricted items.

**Migration:** `dragon_head` / `creeper_head` (currently globally valued at 250@6 / 40@4 as an
interim so the Pig House prints) become **restricted + cost-only**. `twisting_vines` stays a normal
renewable (printable + winder-blacklisted, like kelp). Values are still uncommitted.

---

## 3. Decorative entity support (BUILDING NOW)

**Problem:** armor stands, item frames, and paintings are **entities**, not blocks or block-entities.
The scanner only reads `getBlockEntity`, the format has no entity field, and the printer never spawns
entities — so they're invisible to the whole scan→store→print pipeline (this is why Tristan's Pig
House printed without its armor stands + armor). Armor on a stand is the entity's equipment NBT,
which never gets a chance to matter.

**Scope (grilled):** decorative entities only — `armor_stand`, `item_frame`, `glow_item_frame`,
`painting`, plus **regular `minecart` and regular `boat`** (thematic props). Explicitly **NOT**
mobs/players/items, and **not** container variants (`chest_boat`, `chest_minecart`, …) — each is a
distinct `EntityType`, so they're excluded for free.

**Four coordinated changes:**

1. **Scanner** (`scanner/ScanOperation`) — after blocks, `level.getEntities(null, aabb)` filtered to
   the allowed types; for each, store relative position + `entity.save(new CompoundTag())` (captures
   armor-stand equipment, framed item, painting motive + facing).
2. **Format** (`blueprint/Blueprint` + `BlueprintSerializer`) — add an `Entities` list of
   `{double[3] relPos, CompoundTag nbt}`. **Bump `FORMAT_VERSION` 1 → 2** and **relax the reader to
   accept v1 *and* v2** (v1 → empty entities) so the 133 existing curated files and any v1 player
   scans still load. Regenerate curated files to v2.
3. **Printer** (`machine/PrinterBlockEntity`) — after the blocks land (entities need their support),
   spawn each via `EntityType.loadEntityRecursive`/`create`, positioned at `origin + relPos`.
4. **Economy** — a printed entity costs Filament Units = its contents (stand + armor pieces; frame +
   framed item; painting). Fold entity cost into `blueprintPrintCost` and the per-entity charge.

**Grilled decisions (resolved):**
- **Full orientation, always-print.** Position uses `PrintOrientation.transformPoint` (continuous
  analogue of the block transform — proven center-consistent by a test across all 8 orientations).
  Yaw uses vanilla `Entity.rotate`/`mirror`; a hanging frame/painting's attach block transforms via
  the block transform and its `Facing` via `Direction` rotation. No skip-on-rotation; cardinal-snap
  kept only as a conceptual fallback (unused — the proper transform handles everything).
- **Contents are official-only + affordability-gated.** Official disc reproduces the framed item /
  armor (charged; any one piece the printer can't afford is dropped, unvalued items reproduce free as
  designer intent). Player-scanned disc strips all contents → entity spawns empty (closes the
  scan-and-print item-duplication vector). Base entity item always spawns + charges.
- **Dedup on spawn** — a same-type entity already at the target is skipped, so reprint/repair never
  multiplies decorations.
- **NBT** — preserve decorative state (poses, flags, names, rotations); strip
  `Pos`/`UUID`/`Motion`/`Passengers`/`Leash`. Hanging `TileX/Y/Z` stored blueprint-local, re-anchored
  on print.
- **Back-compat** — reader accepts v1 (entities empty) and v2; existing 133 curated files stay v1 (no
  mass regen; pre-release anyway).

**Invariants:**
- A v1 file always reads (entities empty); a v2 file round-trips entities byte-stable (deterministic
  sort).
- An entity spawns at most once per print, only after its supporting blocks exist, never duplicated on
  reprint.
- A player never receives entity contents they didn't pay for; scanned discs never reproduce contents.
- Only the allowed decorative types are ever captured or spawned (no mob/item duplication).

**Pending:** in-game re-scan test (the existing `tristans_pig_house` predates entity capture — re-scan
to verify stands/frames/carts/boats print). Then commit.
