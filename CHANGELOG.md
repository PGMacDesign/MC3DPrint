# Changelog

All notable changes to **MC3DPrint** (Minecraft 1.20.1 / Forge). Format loosely follows
[Keep a Changelog](https://keepachangelog.com/). Versions before 0.3.0 predate this file.

## [Unreleased]

### Added
- **Redstone Module**: a fifth printer upgrade, capped at 1 per machine (the other four cap at 4). The
  machine emits a full-strength weak signal from all six faces while it is actively printing or
  deconstructing, and nothing when idle or paused (no power, output full, obstructed, out of filament,
  zone conflict), so an inverted signal makes a stall alarm. On a fabricator only the controller emits,
  and the machine never restarts itself: while emitting, an incoming rising edge does not queue a start.

## [0.7.0] — 2026-06-20

Spool storage + a unified transport cable, with a tier-smart filament economy pass.

### Added
- **Filament Rack** — an 8-slot (2×4) bookshelf for the non-stacking Filament Spools: right-click to
  shelve a spool, empty-hand to pop the last one back (LIFO). A block-entity renderer shows each
  shelved spool's own tier-colored model so the shelf fills visibly, and it emits a comparator signal
  scaled to its fill. It also doubles as a drainable FU reservoir for adjacent machines.
- **MC3D Cable** — one connected-texture cable that carries BOTH RF and FU. RF rides standard Forge
  Energy (capped at `cable.transferRate`, default 2000 FE/t — deliberately modest, but it powers any
  mod's FE machines, not just this one's). FU is pulled on demand. Lossless; cheap recipe (yields 6).
- Patchouli guide entries for the Filament Rack and MC3D Cable.

### Changed
- **Printer filament draw is now globally tier-smart**, not dock-order. The printer sweeps tier bands
  from the block's cost tier upward, spending the cheapest qualifying spool first across its docked
  spools AND every reachable rack — so a high-tier spool is never wasted on a low-tier block because
  of where it was docked. Docked spools feed first only as the within-tier tiebreak. The print
  affordability gate counts network supply too, so a printer with empty docked spools still prints
  from a connected rack (direct-touch, or wired via cable).

### Performance
- Cable network membership (which racks / FE acceptors are reachable) is recomputed lazily on a
  ~100-tick throttle and cached as positions only; spool contents and energy are read live, so a
  spool draining or a rack refilling needs no cache invalidation. Replaces the previous
  flood-the-network-every-block behavior.

## [0.5.0] — 2026-06-19

A testing-phase polish + rebalance pass over the Resin system and the curated farms.

### Added
- **Tristan's Castle** — a new curated build imported from PGMacDesign's in-world scan (22×10×19;
  stone bricks, dark oak, powder snow, tripwire). Needs a Tier 7+ fabricator (footprint).
- **Powder snow is now printable with a cost** — its block-item is the `powder_snow_bucket` (which
  has no recipe), so it had no FU value and strict mode refused it. Valued at 16 FU @ T2 and added to
  the winder blacklist: it prints with a cost but can't be wound/laundered into FU.
- **`unlockScannerSize` config** — opt-in override (default off) that raises the Structure Scanner's
  per-axis cap from the flat `t1MaxEdge` (33) up to the largest printable footprint (T8=51 with
  Draconic Evolution, else T7=33), so a very large in-world build can be captured and printed.
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
- **Bigger prints on the top tiers** — print footprint caps raised: **T7 23→33, T8 33→51** (kept odd
  for a centered controller). The fabricator frames are unchanged; only the projected print zone grows.
- **Scanner decoupled from the print cap** — scanning is now a flat per-axis cap (default **33**,
  `t1MaxEdge` config), independent of machine tier and of Draconic Evolution (no more 23-vs-33 split).
  Hand-scans stay a sane size while official/curated discs can print larger builds on a high-tier
  fabricator.
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
