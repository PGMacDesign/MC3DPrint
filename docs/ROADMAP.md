# MC3DPrint — Roadmap & Outstanding Items

_Last updated: 2026-06-13 · v0.2.0 · HEAD `44a4de5` · 60 GameTests passing · deployed to Prism._

MC3DPrint is a Forge **1.20.1** tech mod: "WorldEdit for survival." Scan a build
with the Structure Scanner → save it to a Blueprint Disc → print it anywhere with
a tiered printer/fabricator, powered by RF energy + Filament Units (FU). T1–T4 are
single printer blocks; T5–T8 are multiblock fabricators (an N×N Printer Casing base
+ a controller, formed by right-click).

---

## Active workstreams (next up: 2, 3, 4 — see #1 under Long-term)

### 2. Tier rebalance (item → tier mapping, across the board)
- **Now:** `fu/FuValueRegistry.java` — explicit entries + recipe-derivation + strict
  mode (`unknownBlocksPrintable`, default false). Recent: diamond→T5, emerald T4,
  nether star→T7. Search/JEI tags: `data/mc3dprint/tags/items/filament/tier_N.json`.
- **Goal:** a coherent, principled T1–T8 mapping for materials across the board;
  audit where each item lands and whether derivation gives sane results.
- **Ripples to watch:** gametests assert specific tiers (TierGating, FuTierEconomy,
  RecipeDerivation); the filament tier tags; the new **disc tier** label; the winder
  **exact-tier** rule; printability gating; the blueprint disc tier readout.

### 3. Rework the sample (curated) blueprint discs
- **Now:** 23 curated blueprints — 3 originals (starter_hut, storage_shed, watchtower)
  + 20 generated (`test/.../blueprint/CuratedBlueprintGenerator.java`, a JUnit gated by
  `-DgenBlueprints=true`). Auto-installed on server start (`CuratedBlueprints`), in the
  creative tab + village/exploration loot. Some are **wrong or missing things**.
- **Goal:** fix the broken/incomplete builds; likely prettify display names (discs
  currently show the raw builder name, e.g. "small_cottage").
- **How to regenerate:** edit `CuratedBlueprintGenerator` (parametric helpers: floor/
  walls/roof/door/window…), then
  `./gradlew test --tests '*CuratedBlueprintGenerator*' -DgenBlueprints=true`
  (writes `data/mc3dprint/blueprints/<name>.blueprint`). The disc **tier label** now
  helps spot-check contents.

### 4. Overall UI cleanup / improvements
- **Now:** dark tech-console GUI (`tools/gen_printer_gui.py` → `gui/printer.png` +
  `machine.png`), widened to 230 for the upgrade-slot column; `client/PrinterScreen.java`
  draws bars/labels/status/offsets; spool reels tier-colored + spinning
  (`client/PrinterRenderer.java`). Winder GUI is less polished than the printer.
- **Goal:** general polish — alignment, consistency, the winder screen, tooltips,
  status readouts. Coordinates in `gen_printer_gui.py` MUST stay in lockstep with
  `PrinterMenu`/`PrinterScreen`.

---

## Long-term / someday (not next up)

### Guide / documentation system rework
- **Status:** **deprioritized** — the Patchouli guidebook looks great in-game, so this
  is a long-term goal only, revisited if/when we want to support higher MC versions.
- **Now:** Patchouli (soft/optional dependency) renders the "Fabricator's Handbook"
  — book.json + categories (Basics/Machines/Multiblocks/FAQ) + 11 entries + 6
  generated diagram images. Auto-given on first printer craft (`GuidebookAutoGive`,
  `integration/patchouli/`). Needs Patchouli **installed** in the instance to show.
- **If revisited:** go native (own in-mod book GUI, no dep) or a doc library with
  broader MC-version support. Content is already Patchouli JSON + PNGs; a native
  reader could consume a similar schema. Keep TOC + search + FAQ + image pages.

---

## Outstanding / needs in-game review (Patrick to eyeball in Prism)
- **Install Patchouli** in the Prism 1.20.1 instance to actually see the guidebook.
- Verify in-world: T1 **white** docked reel; **obstruction shows before Start** (on
  offset change + GUI open); **upgrade-slot count** on a high-tier fabricator (should
  be 5–8, already scales via `MachineTier.upgradeSlots()=number`); per-tier spool
  colors + spin; the formed-multiblock "one big printer" look + raised gantry.
- Curated disc display names are raw builder strings — fold into workstream 3.
- Emerald is T4 (diamond is T5) — decide in workstream 2 whether to pair them.
- "Duplicate Simple Generator" report was never reproduced in source (likely the
  MC3DP tab + vanilla Search/JEI list showing the same item) — revisit only with a
  screenshot showing two in the *same* MC3DP tab.

---

## Dev workflow & key references
- **Build:** `./gradlew build` · **Tests:** `./gradlew runGameTestServer` (60 GameTests
  + JUnit). Texture-only changes don't need tests but do need a build to repackage.
- **Deploy:** copy `build/libs/mc3dprint-0.2.0.jar` over the jar in the Prism mods
  folder `~/Library/Application Support/PrismLauncher/instances/1.20.1/minecraft/mods/`
  (replace the old one — never leave two mc3dprint jars).
- **Git:** commit → push every change (no Claude/Anthropic attribution in messages).
- **Generators (Python/PIL, reproducible):** `tools/gen_block_textures.py`,
  `gen_item_textures.py`, `gen_formed_textures.py` (run AFTER block textures),
  `gen_printer_gui.py`, `gen_logo.py`, `gen_guide_images.py`.
- **Config gotcha:** the `fuValues` list config does NOT merge new defaults into an
  existing toml — delete `run/config/mc3dprint-common.toml` (dev) + the Prism
  `config/mc3dprint-common.toml` to pick up economy changes.
- **Standing rules (memory):** copy fresh jar to Prism after every build; update the
  in-game guide whenever a player-facing feature changes.
