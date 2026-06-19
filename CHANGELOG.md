# Changelog

All notable changes to **MC3DPrint** (Minecraft 1.20.1 / Forge). Format loosely follows
[Keep a Changelog](https://keepachangelog.com/). Versions before 0.3.0 predate this file.

## [0.5.0] — 2026-06-19

A testing-phase polish + rebalance pass over the Resin system and the curated farms.

### Added
- **Grand Cathedral** — a new showpiece curated build (13×22×23): twin west-front bell towers with
  ladders, hung bells and spires; a vaulted nave with an arcade of columns, clerestory lancets, a
  rose window, hanging-lantern chandeliers and a processional aisle; a raised chancel with a great
  east window; and a flèche over the crossing.
- **Resin "no-waste" guard** — a resin is no longer consumed on a print it can't affect (Treasure
  with no containers, Ore Salting with no natural stone, Verdant with no plants, Quartermaster with
  no fixtures). The printer leaves the inert resin in the slot, and the disc tooltip warns before you
  print. XP Yield and Overdrive (content-independent) are unaffected.
- **`allowAllDiscsInCreative` config** — toggle whether the creative tab lists every curated
  Blueprint Disc (default) or just a small hand-picked launch set; cosmetic only, world-loot drops
  are unaffected. Documented in the in-game guide (FAQ → "How do I get blueprints?").
- **Overdrive cost preview** — with an Overdrive resin in the slot, a blueprint disc's Print Cost
  tooltip shows the original struck through and the reduced cost beside it (in the printer GUI).
- **Player-scan import for curated builds** — the pumpkin/melon farm is now PGMacDesign's hand-built
  in-game scan (mechanism/redstone preserved byte-for-byte); kelp, cactus and bamboo farms now
  **auto-plant** their first crop instead of leaving it to the player.
- **Acknowledgements** guide page.

### Changed
- **Resin rarity wording: Tier I/II/III → Common / Uncommon / Rare** everywhere player-facing
  (item names, tooltips, Patchouli, docs). Registry IDs unchanged.
- **Treasure loot pools unified to common/uncommon/rare** (was common/rare/epic) and ~**50% more
  loot** per loot-bearing chest.
- **Quartermaster** is far more generous — furnaces share a 64 coal-block budget split evenly, chests
  share 64-food + 64-torch budgets, move-in tools go to the first chest, brewing fuel bumped.
- **Verdant** now matures pumpkin/melon stems; farms (kelp / pumpkin-melon / bamboo / wheat) print
  **ungrown** so a Verdant resin actually has something to grow.
- **T7 printers now have the full 8 upgrade slots** (T8 unchanged at 8).
- Resin footer "used up per print" → "consumable"; guide-book cleanups (split an overflowing page,
  removed the Redstone Clock GUI footer).

### Fixed
- **Redstone repeaters were backwards on every piston farm** (sugarcane / bamboo / kelp) — they
  output away from the pistons, so the pistons never fired. Corrected the facing convention.
- Exterior spool reels not clearing when the last spool is pulled out through the GUI.
- Kelp farm: clock-bus support gap (a dust cell popped on print), front-chest collection + furnace facing.
- Modern glass villa: removed the front water feature and made the roof terrace reachable by ladder.

### Removed
- Archived the auto chicken cooker (`chicken_coop_auto`) — the lava-blade cooker couldn't be made
  reliable; kept under `archive/` for reference.

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

[0.5.0]: https://github.com/PGMacDesign/MC3DPrint/releases/tag/v0.5.0
[0.4.0]: https://github.com/PGMacDesign/MC3DPrint/releases/tag/v0.4.0
[0.3.0]: https://github.com/PGMacDesign/MC3DPrint/releases/tag/v0.3.0
