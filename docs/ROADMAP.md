# MC3DPrint — Roadmap & Outstanding Items

_Last updated: 2026-06-13 · **v0.3.0** · HEAD `31a65f0` · 65 GameTests passing · deployed to Prism._

**Recently shipped (2026-06-13):** FU efficiency rework (break-even at max Efficiency
modules, 4/type cap); curated blueprint set rebuilt (23 builds + dump/validate tool) +
3 `mc3dp-*` pipeline skills; netherite→T6; winder blacklist; T5 multiblock corners =
diamond; print-bug fixes (itemless blocks print, captured-state placement, obstruction
on disc-load); **comprehensive vanilla FU tier rebalance** (food printing, abundance
caps, naturally-spawned blocks, utility overrides, unprintables, draconium T8 — see
`docs/rebalance/`); **AE2 + Thermal modded FU compat** (soft-dep, invisible when absent);
**released v0.3.0** (jar at `6b1155d`, vanilla only).

**⚠ OPEN:** version bump for the modded compat — repo HEAD has AE2/Thermal but is still
labeled `0.3.0`; user deciding **0.3.1 vs 0.4.0**, then bump `gradle.properties` + rebuild.
Next workstream is **UI cleanup** (printer/winder GUI polish). Rebalance amounts are tunable.

MC3DPrint is a Forge **1.20.1** tech mod: "WorldEdit for survival." Scan a build
with the Structure Scanner → save it to a Blueprint Disc → print it anywhere with
a tiered printer/fabricator, powered by RF energy + Filament Units (FU). T1–T4 are
single printer blocks; T5–T8 are multiblock fabricators (an N×N Printer Casing base
+ a controller, formed by right-click).

---

## Active workstreams (next up: 2 tier rebalance, 4 UI cleanup — #3 blueprints ✅ done; #1 long-term)

### 2. Tier rebalance (item → tier mapping, across the board)
- **Now:** `fu/FuValueRegistry.java` — explicit entries + recipe-derivation + strict
  mode (`unknownBlocksPrintable`, default false). Recent: diamond→T5, emerald T4,
  nether star→T7. Search/JEI tags: `data/mc3dprint/tags/items/filament/tier_N.json`.
- **Goal:** a coherent, principled T1–T8 mapping for materials across the board;
  audit where each item lands and whether derivation gives sane results.
- **Ripples to watch:** gametests assert specific tiers (TierGating, FuTierEconomy,
  RecipeDerivation); the filament tier tags; the new **disc tier** label; the winder
  **exact-tier** rule; printability gating; the blueprint disc tier readout.

### 3. Rework the sample (curated) blueprint discs — ✅ DONE (2026-06-13)
- **Shipped:** the 23-build set was rebuilt from scratch (`test/.../CuratedBlueprintGenerator.java`,
  14 new parametric helpers), spanning footprint T3–T7 and material T1–T5/T7. Systemic
  defects fixed (inverted doors, open gables, floating lanterns/battlements, sail-less
  windmill, sealed well, un-walkable bridge). Full audit + specs in `docs/blueprint-specs.md`.
- **Tooling added:** `CuratedBlueprints.install` now refreshes curated content on change;
  `BlueprintDumpTest` (`-DdumpBlueprints=true`) renders ASCII layer maps; the three
  `mc3dp-*` skills under `.claude/skills/` are the find→create→validate pipeline.
- **Regenerate:** `./gradlew test --tests '*CuratedBlueprintGenerator*' -DgenBlueprints=true --rerun-tasks`.
- **Left for in-game review:** print the new set in Prism and eyeball; prettify raw disc
  display names if still desired.

### 4. Overall UI cleanup / improvements
- **Now:** dark tech-console GUI (`tools/gen_printer_gui.py` → `gui/printer.png` +
  `machine.png`), widened to 230 for the upgrade-slot column; `client/PrinterScreen.java`
  draws bars/labels/status/offsets; spool reels tier-colored + spinning
  (`client/PrinterRenderer.java`). Winder GUI is less polished than the printer.
- **Goal:** general polish — alignment, consistency, the winder screen, tooltips,
  status readouts. Coordinates in `gen_printer_gui.py` MUST stay in lockstep with
  `PrinterMenu`/`PrinterScreen`.

### 5. Catalysts / "Resin" system — DESIGNED, ready to build
- **What:** a consumed-per-print **Resin** slot on the printer/fabricator that improves a
  blueprint print. 6 effects (Verdant Growth, XP Yield, Treasure Infusion, Overdrive,
  Quartermaster, Ore Salting) over 3 tiers = 11 resin items + a Resin Base intermediate.
  Works ONLY on official/found blueprints (not player-scanned); T3 resins are loot-only.
- **Full spec + phased build plan:** `docs/catalysts-design.md` (grill Q1–Q18 resolved;
  all hooks mapped to real `PrinterBlockEntity` lines). Economy is multiply-gated (official-
  blueprint + consumed + T3-found/gem-craft + caps + winder-blacklist).
- **Status:** design complete, no code yet. Build sequence: scaffold → slot → official-flag +
  lifecycle → effects (one-at-a-time + gametests) → T3 loot GLM → Patchouli guide → deploy.

### 6. Rename `printite` → `Extrudium` (project-wide) — ✅ DONE (2026-06-18)
- **Shipped:** coordinated id rename across all of `src` + `tools` — registry consts
  (`EXTRUDIUM_ORE`/`EXTRUDIUM_CRYSTAL`), ids (`extrudium_ore`/`extrudium_crystal`), worldgen,
  loot, recipes, tags, advancements, lang, models/blockstates/textures, generators. Zero
  `printite` left in code/resources; compileJava + 84 GameTests green; built + deployed `0.4.0`.
- **Note:** a registry-id rename is a breaking change — existing worlds drop old `printite`
  entries on load (fine pre-launch). The **retexture** half (memory `printite-revamp`) is still
  open: Extrudium reuses the old art for now.

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
