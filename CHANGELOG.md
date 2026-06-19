# Changelog

All notable changes to **MC3DPrint** (Minecraft 1.20.1 / Forge). Format loosely follows
[Keep a Changelog](https://keepachangelog.com/). Versions before 0.3.0 predate this file.

## [0.4.0] — 2026-06-18

### Added
- **Resins — print modifiers.** A consumed-per-print Resin slot on every printer/fabricator that
  refines the next blueprint print. Six effects across three rarities — Common / Uncommon /
  Rare (11 items + a Resin Base):
  - **Verdant Growth** (Common–Uncommon) — in-place plants print fully grown
  - **XP Yield** (Common–Rare) — the print banks XP, released when you pull the disc from the output slot
  - **Treasure Infusion** (Uncommon–Rare) — printed chests/barrels/shulkers may hold loot (common/uncommon/rare pools)
  - **Overdrive** (Uncommon–Rare) — cheaper prints; Uncommon = break-even, Rare = ~20% below (a net FU gain)
  - **Quartermaster** (Rare) — printed furnaces/brewing-stands/chests arrive stocked (incl. enchanted iron tools)
  - **Ore Salting** (Rare) — printed natural stone can come out as mineable ore veins
  - Resins work **only on official/found blueprints** (never player-scanned). Common/Uncommon
    craftable, Rare loot-only (~10% in end-game chests). New Patchouli "Resins" guide category and a
    "Refined Print" advancement. All chances/amounts in the `resin` config section.
- **AE2 + Thermal modded FU compat** — soft-dependency hooks valuing those mods' items (invisible when absent).

### Changed
- **Renamed the signature ore `printite` → `Extrudium`** project-wide (registry IDs, worldgen, loot,
  recipes, tags, advancements, lang, models/textures). **Breaking:** existing worlds drop old
  `printite` blocks/items on load.

## [0.3.0] — 2026-06-13

### Added
- Curated blueprint set rebuilt with parametric generators + ASCII dump/validate tooling;
  blueprints can be found in village & exploration loot.

### Changed
- **FU efficiency rework** — printing is lossy by default, reaching exact 1:1 break-even only with
  4 Efficiency modules (capped 4 per type per machine).
- **Comprehensive vanilla FU tier rebalance** — netherite → T6; abundance caps; naturally-spawned
  blocks, utility overrides, and unprintables priced; T5 multiblock corners = Diamond Blocks.
- **Winder blacklist** — items that craft down cheaper than their input can't be wound into FU.

### Fixed
- Itemless structural blocks now print (free); captured block-state placement; obstruction
  re-check when a disc is loaded.

[0.4.0]: https://github.com/PGMacDesign/MC3DPrint/releases/tag/v0.4.0
[0.3.0]: https://github.com/PGMacDesign/MC3DPrint/releases/tag/v0.3.0
