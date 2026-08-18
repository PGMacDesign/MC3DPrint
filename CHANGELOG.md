# Changelog

All notable changes to **MC3DPrint**. Format loosely follows
[Keep a Changelog](https://keepachangelog.com/). Versions before 0.3.0 predate this file.

## [1.3.0] - 2026-08-14

Playtest hardening: an un-print for Deconstruct Mode, a library you can curate, and two
duplication holes closed.

### Added
- **Redstone Module**: a fifth printer upgrade, capped at 1 per machine (the other four cap at 4). The
  machine emits a full-strength weak signal from all six faces while it is actively printing or
  deconstructing, and nothing when idle or paused (no power, output full, obstructed, out of filament,
  zone conflict), so an inverted signal makes a stall alarm. On a fabricator only the controller emits,
  and the machine never restarts itself: while emitting, an incoming rising edge does not queue a start.
- **Comparator output on printers and fabricators**: put a comparator against any machine and it reads
  the job's progress. `0` means nothing loaded and nothing to do, `1-15` means work is loaded and
  climbs to 15 on the final block. No upgrade needed, matching the Filament Rack: reading a machine is
  free. On a fabricator read the controller, not the casings.
- **Un-print the last build**: flip a machine that has just printed into **Decon** with no region of
  its own and it arms an un-print of that build. The status line reads *Ready: Un-print*, and Start
  takes the build back as filament. It is **masked** to the print itself, so only positions that
  print filled, and which still hold the block it put there, are eligible: the terrain underneath, a
  chest you put inside it, a wall you extended, all left alone. One-shot, and a cancelled or partial
  print still counts.
- **`/mc3dprint guide`**: hands out the Fabricator's Handbook. Ungated for yourself, since the book
  is documentation rather than loot; level 2 to hand it to other players.
- **Handbook recipe**: an `Extrudium Crystal` plus a `Book`, shapeless. The auto-give was one-shot per
  player, and the book belongs to Patchouli so `/give` produces an unbound copy, which left anyone who
  lost theirs with no way back.
- **Rename player scans** from the Blueprint Repository. `Scan @ 307,70,10` stops telling builds apart
  the moment a library holds two. The new title sticks in the library and in the stored blueprint, so a
  disc burned later carries it. Official builds keep their shipped names.
- **Remove a deposited scan** from the repository, with a two-click confirm. Whoever deposited it can
  remove it, operators can remove anything, official builds never. Only the catalogue entry goes: the
  blueprint file stays, so a disc burned earlier still prints and re-depositing restores the entry.
- **Filament Item Sorter** pushes un-windable items to an adjacent inventory instead of jamming.
- **Blueprint loot**: plentiful, location-agnostic drops that never hand you a build you already have.
- **Filament Unit values** for Mystical Agriculture and Agradditions, Tinkers' necrotic bone
  (15 @ T2, wind-only), and gunpowder (10 @ T3, print-only).
- **Phantom membrane** is valued at 30 @ T3, level with slime, and is both windable and printable.
  It sits with slime rather than up at blaze rod because a phantom farm is AFK-automatable and the
  abundance rule keys off farmability, not how tedious the insomnia mechanic feels.

### Changed
- **Scaffolding is scan-only.** It is how you reach the top corners of a build, so it lands in most
  hand scans. It is still captured, but it adds no cost, never raises the blueprint's tier, and is
  never built. Previously a Tier 1 cottage scanned from a scaffold tower came back as a Tier 3
  blueprint, demanding a machine it never called for. Applied when quoting and printing rather than
  when scanning, so discs scanned before this update are quoted correctly too.
- **The Auto button now doubles as the mode indicator**: it is hidden in Deconstruct Mode and shown in
  Print Mode. The Print/Decon toggle names the mode you are in, which reads just as easily as the mode
  it switches you to, and nothing else on the panel broke the tie.
- A deconstruct that stalls with nowhere to put its filament says **Spool Missing or Full** instead of
  *Output Full*, which sent players looking for a blocked output slot that deconstruct does not have.
- The Blueprint Repository's burn button reads **STL to Disc** instead of *STL to GCODE*.
- Base draconium drops from 250 FU to 40, still Tier 7.
- The Tier 8 fabricator and spool take **awakened draconium**, not dragon eggs.

### Fixed
- **Crops and seeds no longer print for free.** Any planted block, in any mod, printed at zero cost
  because the free-print rule keyed on the `BushBlock` type that every crop in the game descends from.
  Scanning a field of a mod's valuable crops and printing it therefore handed them over for nothing:
  Mystical Agriculture essence crops were the report, but the hole was general. The rule asked what
  kind of block it was when the only safe question is what it is worth, and its stated reason (that a
  crop's item is a seed, "never the depicted grown block") was not true to begin with. A crop block's
  `asItem()` already returns its planting item, so ordinary pricing always had the right thing to
  charge. Planted growth now costs its seed, vanilla plants and saplings are valued at the Tier 1 floor
  and winder-blacklisted so a farm cannot launder itself into filament, and a modded crop whose seed
  carries no value is refused by strict mode instead of being given away. No per-mod blocklist is
  involved, and none should be. Only genuinely itemless blocks (water, fire) and tilled ground
  (farmland, dirt paths) stay free.
- **Duplication via a placed item.** Captured block-entity contents were only cleared through
  `Clearable`, which modded block entities need not implement. An item placed on a wall with Draconic
  Evolution's `placed_item` survived the clear and printed back at no cost, once per print. The clear
  is now verified rather than trusted: if item data survives it, the whole payload is dropped and the
  block prints bare.
- **Duplication via itemless blocks.** Any block with no item form printed free as "structural
  matter", and that check ran ahead of the strict-mode gate, so it bypassed it entirely. Itemless
  blocks that own a block entity are now never printable, whatever `unknownBlocksPrintable` says.
- A cell the print **skips** no longer has to be clear, so a block standing where scaffolding was
  captured cannot pause the whole job as *Obstructed*.
- **A finishing machine no longer strands its neighbour's job.** Jobs used to hold their chunks with
  vanilla's `setChunkForced`, which was one boolean per chunk with no record of who asked for it, so
  two machines whose job areas shared a chunk fought over it: the first to finish unforced the chunk
  out from under the one still working, whose block entity then stopped ticking and hung with no
  error and no way to recover. A chunk is 16 blocks while build offsets
  reach 32 and deconstruct regions reach 64, so any print farm could hit it. Claims are now
  reference-counted per machine and a chunk is released only when the last job using it is done.
  (1.20.1 was never affected: Forge's chunk manager tracks an owner, and only the NeoForge port
  lost that.)
- **Blocks the game reshapes no longer read as wrong.** Minecraft owns part of every placed state:
  stair shape, fence and wall connections, redstone-driven flags, and whether a door was left open.
  Those were compared against the blueprint exactly, so on any build with stairs the ghost preview lit
  the corner pieces red and a re-print refused to start as *Obstructed*, over blocks that were already
  correct and that no re-print could change. Placement (facing, half, hinge, axis) still has to match.
- The scanner arms a fabricator's Deconstruct region from **any casing**. On a Tier 8 pad the
  controller is 1 block of 81, so every other click silently overwrote a scanner corner instead.
- Re-depositing a copy of someone else's scan no longer takes over the entry, which would have handed
  over the right to remove it.
- Renaming a scan needs the same depositor-or-operator permission as removing one. On a shared library
  any player could previously retitle anyone's build, and the rename rewrites the stored blueprint.
- The client's operator check uses the real permission level, so the rename and delete controls are
  offered to an operator in Survival.
- An unreadable un-print arming clears its region instead of degrading into a whole-box deconstruct.
- The Filament Item Sorter's `setStackInSlot` was an unfiltered side door into the routing pool.
- A re-placed fabricator restores the formed look rather than only its active casings.
- Deconstruct Mode with no region no longer wedges on a permanent *Not Printable*.
- A comparator no longer pulses `0` between items in Item Mode.

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
