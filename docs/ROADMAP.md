# MC3DPrint — Roadmap & Outstanding Items

_Last updated: 2026-06-21 · **v0.7.0** · 94 GameTests passing · 134 curated builds · deployed to Prism._

MC3DPrint is a Forge **1.20.1** tech mod: "WorldEdit for survival." Scan a build
with the Structure Scanner → save it to a Blueprint Disc → print it anywhere with
a tiered printer/fabricator, powered by RF energy + Filament Units (FU). T1–T4 are
single printer blocks; T5–T8 are multiblock fabricators (an N×N Printer Casing base
+ a controller, formed by right-click).

---

## Shipped history

**Through v0.4.0 (Jun 13–18):** FU efficiency rework (break-even at max Efficiency,
4/type cap); curated blueprint set rebuilt + expanded to ~132 builds via the gated
generator + 3 `mc3dp-*` pipeline skills; comprehensive vanilla FU tier rebalance
(`docs/rebalance/`); **AE2 + Thermal + Draconic** modded FU compat (soft-dep, invisible
when absent); the full **Resin** print-modifier system (6 effects, T3 loot, Patchouli
category); project-wide **printite → Extrudium** rename; released v0.3.0 (vanilla) then
v0.4.0.

**Shipped v0.5.0 → v0.7.0 (Jun 19–21):**
- **Filament Rack + MC3D Cable** — Rack = 8-slot spool storage + FU reservoir; Cable =
  dual-carry RF+FU, deliberately weak (2k FE/t — don't buff). Cables plug into **any
  casing** of a formed multiblock, not just the controller. See memory `rack-and-cable`.
- **Web Blueprint Viewer** — static GitHub Pages site (`web/`) + PR-preview bot;
  client-side Three.js + MapColor datagen, fully deterministic (non-AI). Live.
- **Loot rule change** — **every curated blueprint is world loot by default** (opt-out
  only via `CuratedBlueprints.LOOT_EXCLUDED`); new builds are auto-included. One
  `world_blueprints` GLM replaced the old village/exploration tables. STANDING RULE.
- **Decorative entity print support** — armor stands (+armor), item frames, paintings,
  regular minecarts, boats now scan + print, with full orientation. Item-frame contents
  / armor reproduce **only on official blueprints** (anti-dupe gate); player scans print
  the empty entity.
- **Tristan's Pig House** — imported curated build (24×16×16, T7) via the new
  `mc3dp-import-scan` skill.
- **Multiblock corners simplified** — T5/T6/T7 now form from **plain casing**; only T8
  keeps the premium corner (4× Awakened Draconium). See memory `multiblock-corner-blocks`.
- **Extrudium "Stardust" retexture** — animated ore + crystal (closes the long-open #6
  retexture deferral); ore `lightLevel` 4→6.
- **Printer/fabricator Rotate control** — a **Rotate 90°** button on a dedicated row below
  the XYZ offsets: clockwise per tap, footprint-center pivot (offsets untouched), persists
  across disc swaps, live ghost preview. Reuses `PrintOrientation` (block-state + entity
  rotation were already implemented at placement).
- **Blueprint format collapsed to a single version 1** — no v1/v2 variant; optional
  entities carried by key presence; reader is version-tolerant. See memory
  `blueprint-pipeline`. **Rule: never bump the format version pre-release.**
- **GUI/visual polish** — Ghost Mode now **defaults ON** for new printers; cable icon =
  flat-ended wire; filament rack "Spool Bays" face at 32px with lit, centered tier-colored
  spools; printer panel heightened 200→216 for the rotate row.

---

## Active workstreams (next up)

### 4. Overall UI cleanup / improvements — _in progress_
- **Done so far:** dark tech-console printer GUI (`tools/gen_printer_gui.py` →
  `gui/printer.png`), widened to 230 + heightened to 216; tier-colored spinning spool
  reels; the Rotate row + Spool Bays rack face; Ghost default-on.
- **Still the laggard:** the **Filament Winder GUI** is less polished than the printer.
  General polish remains — alignment/consistency, tooltips, status readouts.
- **Lockstep rule:** coordinates in `gen_printer_gui.py` MUST stay in sync with
  `PrinterMenu`/`PrinterScreen` (and likewise for the winder).

### 2. Tier rebalance (item → tier mapping) — _long-term_
- **Now:** `fu/FuValueRegistry.java` — explicit entries + recipe-derivation + strict mode
  (`unknownBlocksPrintable`, default false). Search/JEI tags:
  `data/mc3dprint/tags/items/filament/tier_N.json`.
- **Goal:** a coherent, principled T1–T8 mapping across the board; audit where each item
  lands and whether derivation is sane.
- **Ripples to watch:** gametests assert specific tiers (TierGating, FuTierEconomy,
  RecipeDerivation); the filament tier tags; the disc tier label; the winder exact-tier
  rule; printability gating.

### In-game tuning passes (need eyes-on Prism)
- **Resin numbers** + the resin-slot render — tune the new effect amounts in config.
- **Balancing pass** over all config values (FU costs, RF, speeds) — needs playtesting.
- **Config no-wipe goal** — pre-launch QoL: retune economy/config without deleting the
  toml + reloading (the `fuValues` list doesn't merge). Low priority. Memory
  `config-no-wipe-goal`.

---

## Long-term / someday (not next up)

- **Print queue UX** — sequential jobs + persistence done; reorder UI + cancel-with-refund
  pending (M4).
- **Matter Calculator GUI** — template FU cost + filament gauges exist; full blueprint
  calculator (RF + ETA) pending (M5).
- **Server Blueprint Repository block + GUI** — world file store + import/export + curated
  auto-install cover the owner workflow; a dedicated block/GUI is deferred (M9).
- **Rack & Cable deferred ToDos** — see memory `rack-and-cable`.
- **Resin follow-ups** — flavor-biased T3 resin pick (GLM TODO), modded treasure-loot
  entries, AE2-deep converter integration.
- **Guide/doc system** — Patchouli looks great in-game; going native (no dep) is a
  long-term-only idea, revisited only if supporting higher MC versions.

---

## Launch / human-only steps (see LAUNCH.md)
- **Install Patchouli** in the Prism instance to see the guidebook in-game.
- Live modded-compat testing (Mekanism/Thermal/EnderIO/Flux) — needs a real modpack
  dev instance; not coverable headless.
- Signature/creator blueprints + outreach.
- CurseForge + Modrinth pages (store copy drafted in LAUNCH.md).
- Patrick's full favorite-mod list for further compat evaluation.

---

## Dev workflow & key references
- **Build:** `./gradlew build` · **Tests:** `./gradlew runGameTestServer` (94 GameTests +
  JUnit). Texture-only changes skip tests but still need a build to repackage.
- **Deploy:** copy `build/libs/mc3dprint-0.7.0.jar` over the jar in the Prism mods folder
  `~/Library/Application Support/PrismLauncher/instances/1.20.1/minecraft/mods/` (replace
  the old one — never leave two mc3dprint jars).
- **Git:** commit → push every change, direct to `main` (no Claude/Anthropic attribution).
- **Generators (Python/PIL, reproducible):** `tools/gen_block_textures.py`,
  `gen_item_textures.py`, `gen_formed_textures.py` (run AFTER block textures),
  `gen_printer_gui.py`, `gen_storage_cable_textures.py`, `gen_logo.py`, `gen_guide_images.py`.
- **Config gotcha:** the `fuValues` list does NOT merge new defaults into an existing toml —
  delete `run/config/mc3dprint-common.toml` (dev) + the Prism `config/mc3dprint-common.toml`
  to pick up economy changes.
- **Standing rules (memory):** copy the fresh jar to Prism after every build; update the
  in-game Patchouli guide whenever a player-facing feature changes; never introduce a
  blueprint format v2.
