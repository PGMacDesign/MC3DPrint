---
date: 2026-06-06
tags: [minecraft, mod-idea, 3d-printing, fabrication, multiblock, redstone-flux]
type: idea
status: active
---

# MC3DPrint — Minecraft 3D Printing Machine Mod

A tech mod centered on a tiered 3D Printing Machine capable of fabricating items, tools, and entire structures. The core fantasy: *WorldEdit for survival mode players.* Scan a building, save it as a blueprint, print it anywhere — no commands required.

**Target platform: Minecraft 1.20.1 / Forge** ✅ *(locked 2026-06-10 — all integration targets verified available on Forge 1.20.1)*

---

## Table of Contents

1. [[#Machine Tiers]]
2. [[#Power — Redstone Flux]]
3. [[#Filament System]]
4. [[#Enhancements & Expansion Slots]]
5. [[#Resins (Print Modifiers)]]
6. [[#I/O Design]]
7. [[#Print Head Animation]]
8. [[#Scanner Tool & Blueprint Discs]]
9. [[#Printer GUI]]
10. [[#Custom Ore — Extrudium]]
11. [[#Mod Compatibility]]
12. [[#World Loot & Blueprint Discovery]]
13. [[#Multiplayer & Server Features]]
14. [[#Community & Launch Strategy]]
15. [[#Balancing & Tuning]]
16. [[#Stretch Goals]]
17. [[#Open Questions]]

---

## Machine Tiers

Tiers 1–4 are single craftable blocks. Tiers 5–8 are true multiblock structures built in-world. Tier 8 requires Draconic Evolution.

| Tier | Structure | Footprint | Capability |
|------|-----------|-----------|------------|
| 1 | Single block | 1×1 | Basic items (tools, weapons) |
| 2 | Single block | 1×1 | Unlocks structure scanning & blueprints |
| 3 | Single block | 3×3 print area | Small structures, mid-tier items |
| 4 | Single block | 5×5 print area | Larger structures, advanced items |
| 5 | Multiblock | 9×9 | Complex builds, elite items; **requires Extrudium** |
| 6 | Multiblock | 15×15 | Large structures, exotic materials |
| 7 | Multiblock | 23×23 | Maximum base-game size; legendary items & massive builds |
| 8 ⭐ | Multiblock | 33×33 | **Draconic tier** — Awakened Draconium required; pattern-consistent prestige tier |

- Machine complexity and material cost scales with tier
- **T1–T4:** Single craftable blocks — craft in a crafting table, place in the world
- **T5–T8:** True **multiblock structures** — multiple different blocks placed in a specific pattern in-world, then right-click the controller to form the machine. No crafting table recipe.
- No durability on machines — ever. Permanent investments, not maintenance burdens.

### Multiblock Portability — Collapse to Item

A key quality-of-life feature: multiblock machines are fully portable without disassembly.

**Formation:**
1. Place component blocks in the correct layout
2. Right-click the controller block → multiblock forms into a single logical unit
3. Component blocks become part of the structure (rendered geometry) — the machine is now "one thing"

**Relocation (the important part):**
- Break the controller block → entire multiblock collapses instantly
- Drops as a **single inventory item** (e.g., `Tier 5 Fabricator`) — not scattered components
- Walk to the new location, place the item → machine reforms automatically, fully intact
- No inventory tetris, no lost components, no rebuilding from scratch

**Why this matters:** Without this, players with large T6/T7 machines would face a massive penalty for wanting to print in a different area. The multiblock system is about *effort to build*, not *effort to move*. Build it once, relocate freely.

---

## Power — Redstone Flux

- Powered by **Redstone Flux (RF)** — compatible with Thermal Expansion, Mekanism, EnderIO, and all standard RF mods
- RF cost per block placed scales with tier and print complexity
- Power-loss mid-print: job **pauses** and resumes exactly where it left off when power is restored — no matter lost, no partial mess
- GUI displays "PAUSED — Insufficient Power" clearly
- Internal RF buffer included per machine; size scales with tier

---

## Filament System

All fabrication is powered by **Filament Units (FU)** — a unified measurement of stored material potential. There is no separate "Raw Matter" pool. FU is the single currency for both storing and spending material.

### The Flow

1. Feed materials into the **Filament Winder** → converts to FU, winds onto a blank spool
2. Load the spool onto the printer sides (Shift+Right Click)
3. Printer draws FU from the spool as it prints
4. RF is consumed at both steps — winding and printing

### Filament Unit Values

Conversion is **symmetric** — what a material produces in FU is exactly what it costs to print that material. A diamond yields 50 FU. Printing a diamond costs 50 FU. No arbitrage.

Materials within the same group share identical FU values — e.g. cobblestone, dirt, gravel, and sand are all interchangeable as 1 FU inputs.

| FU Value | Materials (all equivalent) | Required Spool Tier |
|----------|---------------------------|---------------------|
| **1 FU** | Cobblestone, Dirt, Gravel, Sand, Gravel, Soul Sand, Soul Soil, Clay Ball | T1 |
| **3 FU** | Stone, Sandstone, Smooth Stone, Stone Bricks, Andesite, Diorite, Granite, Calcite, Tuff | T1 |
| **3 FU** | Oak/Spruce/Birch/Jungle/Acacia/Dark Oak/Mangrove/Cherry Log or Planks | T1 |
| **5 FU** | Glass, Terracotta, Concrete, Wool, Nether Bricks, Quartz Block | T1 |
| **10 FU** | Copper Ingot, Amethyst Shard, Lapis Lazuli (per unit) | T2 |
| **15 FU** | Gold Ingot, Gold Nugget ×9 | T2 |
| **20 FU** | Iron Ingot, Iron Nugget ×9 | T2 |
| **30 FU** | Redstone Dust ×8, Slimeball, Magma Cream | T3 |
| **50 FU** | Emerald *(villager-renewable — the T4 gem)* | T4 |
| **50 FU** | Diamond *(mined — gated above emerald, 2026-06-11)* | T5 |
| **500 FU** | Netherite Ingot, Ancient Debris ×4 | T5 |
| **1,500 FU** | Nether Star | T6 |
| **2,500 FU** | Dragon Egg | T7 |

*All values are placeholders — expect significant rebalancing during playtesting.*

**Key grouping rules:**
- Same rarity tier = same FU value (cobblestone = dirt = gravel = sand). Diamond and emerald share the 50 FU value but sit at different tiers — diamond T5 (mined), emerald T4 (renewable)
- Stone variants (sandstone, stone bricks, etc.) = same as base stone
- All wood types = same value regardless of species
- Nugget ×9 = ingot value (consistent with vanilla crafting ratios)

> **Recipe-derived values, strict mode & the compat API.** Any item with no
> explicit value is now **priced automatically from its crafting/smelting/
> stonecutting recipe** (e.g. `diamond_block` derives to 450 FU @ T5 from 9
> diamonds — no hardcoded entry). Un-priced blocks are refused by default
> (**strict mode**, `unknownBlocksPrintable=false`), closing the
> scan-expensive-block / print-cheap exploit. Pack makers override any value via
> `fuValues`; other mods register their own via `MC3DPrintAPI` or Forge IMC.
> Full details — derivation rules, config toggles, override syntax, and the
> compat-mod API/IMC surface — in **[FU-VALUES-AND-COMPAT.md](FU-VALUES-AND-COMPAT.md)**.

**One universal winder, spool-tier gating** (revised 2026-06-11 — replaced the
T1–T4 winder ladder with a single block): there is exactly **one** Filament
Winder, and it accepts any material. The gate is the **spool**, not the winder
— a material only winds into a spool of its **exact tier**. Netherite (T5)
needs a T5 spool in the machine; a T1 spool won't wind it. Cobblestone (T1)
needs a T1 spool. This closes the cobblestone-to-nether-star bypass without a
winder progression ladder.

### Tier Conversion (Down-Only, and Only at Print Time)

FU is **denominated by spool tier** — a tier-S spool holds tier-S FU.

- **Winding is exact-tier** — a material winds *only* into a same-tier spool
  (T2 iron → T2 spool, 1:1). No tiering up (cobblestone can never reach a T2
  spool) and no tiering down at the winder either, so you can't launder a
  high-tier material into a pile of cheap low-tier FU.
- **Printing down-converts automatically** — higher-tier FU covers lower-tier
  costs at a **4:1 ratio per tier step** (revised 2026-06-11 from 16:1 — too
  steep), compounding across steps (T3 → T1 = 16:1). A docked T7 spool pays
  T1-denominated costs at 4⁶ FU of value per unit. Config:
  `general.filamentConversionRatio`, default 4.
- **Cannot tier up — ever.** A spool below an item's cost tier contributes
  nothing toward printing it. Combined with exact-tier winding, this keeps
  cobblestone farming from trivializing the economy.

### Filament Spool

FU is stored on physical **Filament Spool** items, wound by the Filament Winder and attached to the printer sides.

**Spool capacities:**

| Spool Tier | FU Capacity |
|------------|-------------|
| T1 | 500 FU |
| T2 | 2,000 FU |
| T3 | 6,000 FU |
| T4 | 20,000 FU |
| T5 | 75,000 FU |
| T6 | 250,000 FU |
| T7 | 1,000,000 FU |
| T8 | 5,000,000 FU |

**Spool slots by tier:**

| Tier | Right | Left | Total |
|------|-------|------|-------|
| T1 | 1 | 0 | 1 |
| T2 | 1 | 1 | 2 |
| T3 | 2 | 1 | 3 |
| T4+ | 2 | 2 | 4 |

- Attached via `Shift + Right Click` on the side of the printer
- Auto-switches to backup spool when one depletes — no print interruption
- Depletion with no backup pauses the printer, never loses progress
- Visual: spool spins on the machine exterior while printing; GUI shows FU remaining as fill bar + %

### Print Costs & Efficiency

- Each item/structure has a base FU cost (symmetric with material values)
- Lower-tier machines are less efficient — they consume more FU than the theoretical minimum
- Higher-tier machines approach 1:1 efficiency
- Efficiency Upgrades reduce FU consumption further
- Matter Calculator in the GUI shows exact FU cost, RF cost, and ETA before committing to a print
---

## Item Tier Requirements

Higher-tier machines aren't just for bigger print areas — they're also required to print higher-tier items. A T1 printer cannot print a diamond sword. Period.

| Tier Required | Printable Items (examples) |
|--------------|---------------------------|
| T1 | Cobblestone, dirt, gravel, wood planks, sticks, torches, basic food |
| T2 | Stone tools, iron tools & armor, buckets, rails, basic redstone components |
| T3 | Gold tools/armor, lapis, chain armor, crossbows, books, maps |
| T4 | Diamond tools & armor, enchanting table, ender chest, anvil |
| T5 | Netherite tools & armor, beacons, tridents, elytra |
| T6 | Nether Star crafted items (conduit, beacon pyramid components), end-game gear |
| T7 | Highest-tier crafted vanilla items; modded legendary gear |
| T8 ⭐ | Draconic Evolution top-tier gear (with DE installed) |

**Structure printing** is governed separately by the print volume (footprint) — but item printing is always gated by tier regardless of machine size.

---

## Enhancements & Expansion Slots

Each printer has expansion slots for upgrade modules. Slot count mirrors tier (T3 = 3 slots, T7 = 7 slots, T8 = 8 slots).

| Upgrade | Effect |
|---------|--------|
| Speed Upgrade | Increases print head movement speed |
| Efficiency Upgrade | Reduces Matter cost per print |
| Matter Density Upgrade | Better conversion rate for low-value inputs |
| RF Efficiency Upgrade | Reduces RF consumption per block placed |
| Buffer Upgrade | Increases internal Matter storage capacity |

- ~~Upgrades are themselves tiered (T1 Speed Upgrade vs. T5 Speed Upgrade)~~
  **Decided 2026-06-11 (Patrick): upgrades stay FLAT** — one item per type;
  scaling comes from slot count (slots = machine tier) and multiplicative
  stacking, not item tiers
- Modifiers stack **multiplicatively**, not additively — prevents runaway values
- All base values and modifier rates exposed in config for pack makers

---

## Resins (Print Modifiers)

A **Resin** is a consumed-per-print modifier dropped into the printer/fabricator's **Resin slot** (a single stack-holding slot beside the upgrade and spool columns). It refines the *next* blueprint print, then is used up. Resins work **only on official/found blueprints** — never player-scanned ones — the anti-exploit gate that stops a player scanning a cheap build and mass-printing value.

Six effects, gated across three rarities (Common / Uncommon / Rare). *Internally these are
tiers 1–3; players see rarity names so "tier" stays the printer/spool axis.*

| Effect | Rarity | What it does |
|--------|--------|--------------|
| Verdant Growth | Common, Uncommon | in-place plants (crops, nether wart, sweet berries, cocoa) print fully grown |
| XP Yield | Common–Rare | the print banks XP (furnace-style), released when the disc is pulled from the output slot |
| Treasure Infusion | Uncommon, Rare | printed chests/barrels/shulkers may spawn holding loot (common/uncommon/rare loot pools) |
| Overdrive | Uncommon, Rare | the print costs less filament — Uncommon = break-even, Rare = ~20% below (net FU gain) |
| Quartermaster | Rare | printed furnaces/brewing-stands/chests arrive stocked (fuel, a move-in kit with enchanted iron tools) |
| Ore Salting | Rare | printed natural stone has a chance to come out as a mineable ore vein |

- **Crafting:** a shared **Resin Base** (Extrudium Crystal + any `forge:slimeballs`) + a rarity ingredient (a diamond/emerald makes it Uncommon) + an effect ingredient. **Common/Uncommon are craftable; Rare is loot-only**, found at ~10% in end-game chests (end cities, ancient cities, fortresses, bastions, mansions, strongholds, buried treasure) via a global loot modifier.
- **Lifecycle:** reserved when a catalyzed job starts, consumed on its first placed block (a print that can't even start doesn't waste a rare resin); the slot holds a stack, so Auto-printing keeps catalyzing until it runs dry, then prints normally.
- **Anti-exploit:** every value-minting effect is multiply-gated — official-blueprints-only + consumed-per-print + (Rare unfarmable / Uncommon gem-cost) + per-print caps + the existing winder-blacklist and down-only/exact-tier winding — so none can become a duplication engine.
- All chances, caps, and amounts live in the `resin` section of the config. Full design + decision record: `docs/catalysts-design.md`.

---

## I/O Design

Simple, intuitive, automation-friendly from day one. **Anti-AE2-Inscriber** by design.

- **Top face** — accepts any input: matter materials, blueprint discs, anything going *in*
- **Bottom / Front face** — output/extraction: finished items, ejected discs, anything coming *out*
- No side-specific requirements. No "silicone goes in the right, redstone goes in the top" nonsense.
- **Sides are reserved exclusively for Filament Spool attachment** (Shift+Right Click) — not general I/O faces. *Enforced 2026-06-11: sides expose no item handler; output is the bottom face (printers have no facing, so "front" collapsed into bottom). Docked spools render on the side faces — spinning while printing, winding shrinking toward the axle as filament depletes.*
- Works with vanilla hoppers at T1 — zero gatekeeping on early automation
- Compatible with any pipe mod respecting standard inventory faces: EnderIO conduits, Thermal ducts, Mekanism pipes, AE2 import/export buses, RS cables, etc.
- Output buffer pauses the printer if full — never voids items
- Blueprint disc returns to output slot after a completed print (stays loaded if repeat-print is queued)

---

## Print Head Animation

Inspired by the BuildCraft Quarry — but in reverse. The quarry destroys top-down; this builds bottom-up.

1. **Structural Frame** — spawns rendered geometry around the entire print volume when a job starts (not real placeable blocks — won't interfere with the build)
2. **X/Y Gantry Arms** — extend across the frame, visibly connected to the print head
3. **Print Head** — rides the gantry, moving layer by layer (X → Y → Z, exactly like FDM 3D printing)
4. **Zap Effect** — at each position, the head fires a laser/beam downward; block materializes on contact
5. **Particle trail** on moving gantry arms; satisfying sound cue per block placed
6. Animation speed increases visibly with machine tier and Speed Upgrades

---

## Scanner Tool & Blueprint Discs

### Scanner Tool
A craftable handheld tool used to capture structures as portable blueprints.

**Workflow:**
1. Craft the Scanner (mid-tier, requires tech components + Extrudium at higher scanner tiers)
2. Right-click two corners to define a bounding box (WorldEdit wand style)
3. Trigger scan — structure is captured
4. Output: **Blueprint Disc** — a physical inventory item storing the schematic

**Scanner Tiers:**
- T1 Scanner: small volumes (up to ~7×7×7)
- Higher-tier scanners: larger capture volumes matching printer tier capabilities

### Blueprint Discs

- Portable — carry in inventory, store in chests, trade with other players
- Not locked to any specific printer — works in any compatible machine of sufficient tier
- **Shift + Right Click** to lock/unlock a disc — locked discs cannot be overwritten or deleted
- Locked discs show a visual indicator (icon + border color in GUI)
- **Blueprint Transform** — right-click to open transform menu: rotate 90°/180°/270°, mirror on X or Z axis

### Blueprint Library Block
- Storage block for organizing multiple Blueprint Discs
- Acts as a local personal library
- Higher-tier version: **Server Blueprint Repository** (see Multiplayer section)

---

## Printer GUI

All controls in one clean interface:

- **Smart Print Slot** — accepts both items *and* Blueprint Discs in the same slot
  - Drop a **craftable item** → prints one copy of that item (Item Mode)
  - Load a **Blueprint Disc** → runs the full blueprint (Blueprint Mode)
  - Slot icon changes dynamically based on what's loaded
  - No mode toggle, no confusion — the slot figures it out
  - Works at every tier: T1 uses item drops exclusively; T2+ supports both
- **Matter Gauge** — current pool level and input conversion rate
- **RF Gauge** — current power level and consumption rate
- **Matter Calculator** — input a loaded blueprint → shows exact matter cost, RF required, estimated print time at current tier + upgrades
- **Hologram Preview** — renders a ghost outline of the structure in-world before printing; green = clear, red = obstructed
- **Transform Controls** — rotate and mirror the blueprint before printing
- **Print Queue** — queue multiple jobs, reorder, pause/resume, cancel (cancel refunds a configurable % of matter)
- **Print History Log** — simple last-N-jobs list: blueprint name, timestamp, matter consumed
- **Upgrade Slots** — expansion module management
- Queue persists through server restarts

---

## Custom Ore — Extrudium

> ⚠️ *Name placeholder: currently **"Extrudium"** — rename TBD. Find/replace when finalized.*

- **Dimension:** The End only — zero overworld generation, keeps world gen clean
- **Rarity:** Rare (comparable to ancient debris)
- **Used for:** T6/T7 machine components and higher-tier Scanner crafting — one ore, one purpose
- **Visual:** Darker blue base (deeper/darker than lapis lazuli) with emerald-green glowing speckled inclusions — distinct from both Draconic Evolution's purple and vanilla End materials. The glowing specks match the exact green of emerald. Animated shimmer/glow on the specks, premium look, easy to spot in the End's dark terrain.
- Ingot/gem form carries a subtle shimmer in inventory as well

---

## Mod Compatibility

Soft dependencies only — mod works standalone, integrations activate when the target mod is present.

All integration targets verified on Forge 1.20.1 (June 2026):

| Mod | 1.20.1 Build | Integration |
|-----|--------------|-------------|
| **Applied Energistics 2** | 15.4.x | Pull matter directly from ME network; store blueprint discs in ME storage; trigger prints via AE2 autocrafting *(top priority)*; **Filament Unit Converter** (see below) |
| **Draconic Evolution** | 3.1.2.x | Unlocks Tier 8 machine via Awakened Draconium; DE energy system support |
| **Refined Storage** | v1.12.x | Network-driven matter sourcing, same pattern as AE2. **Target the RS1 API** — RS 2.0 is a separate codebase on newer MC versions |
| **Thermal Expansion** | 11.0.x | RF compatibility; shared augment/upgrade language |
| **Mekanism** | 10.4.x | RF/energy compatibility; pipe input for matter feed |
| **EnderIO** | 6.2.x-beta | Conduit compatibility for I/O automation. ⚠️ Beta on 1.20.1 — lower priority; standard capability I/O should cover it without dedicated code |
| **Patchouli** | 1.20.1-85 | In-game guidebook (see Community section) |
| **JEI** | 15.20.x | Recipe integration (see Community section) |
| **Create** | 0.5.1.x / 6.0.x | Import Create schematics (vanilla structure `.nbt`) as Blueprint Discs — interop with the Schematicannon ecosystem, not competition *(promoted from v2, 2026-06)* |
| **WorldEdit** | 7.2.15 | `.schem` (Sponge schematic) import/export for Blueprint Discs — closes the loop on the "WorldEdit for survival" tagline *(promoted from v2, 2026-06)* |

> **BuildCraft** — dropped from the integration list. No 1.20.x port exists (1.12.2 was its last era). It remains *animation inspiration only* for the quarry-style print head.

> **NeoForge** — evaluated 2026-06, staying on Forge. On 1.20.1, NeoForge 47.x is renamed Forge (no API benefit), and moving to NeoForge's real home (1.21.1) would cost Thermal Expansion entirely (dead at 1.20.1) and leave the T8 gate on a beta-only Draconic Evolution. Revisit as a future port once DE stabilizes on 1.21.x.

### Integration Roadmap — v2 / v3 Stretch Goals

From a survey of the top 500 mods on 1.20.1 by total downloads (2026-06). Download counts included for prioritization.

**v2 — natural fits:**

> Create (191M) and WorldEdit (62M) were originally v2 candidates — **promoted to v1 scope** (2026-06). Both are blueprint-format interop and share the importer pipeline.

| Mod | Downloads | Integration idea |
|-----|-----------|------------------|
| **CraftTweaker / KubeJS** | 220M / 147M | Scriptable FU values, tier gates, print recipes. What makes pack makers adopt the mod — arguably v1.5 |
| **Jade / The One Probe** | 239M / 96M | Look-at overlays: job progress, FU/RF levels, pause reason. Cheap to build, every pack runs one |
| **FTB Quests** | 203M | Quest task/trigger hooks so packs can gate progression on prints |
| **MineColonies + Structurize** | 84M / 72M | Print colony buildings from the Structurize blueprint format — huge builder audience |
| **CC: Tweaked (+ Advanced Peripherals)** | 78M / 46M | Printer as computer peripheral: start/queue/monitor prints from Lua. Automated print farms |
| **Structure-mod loot injection** | — | Seed Blueprint Disc loot into YUNG's structures (~15 mods in top 500), When Dungeons Arise (104M), The Lost Cities (86M) |

**v3 — fun / wild:**

| Mod | Downloads | Integration idea |
|-----|-----------|------------------|
| **Tinkers' Construct** | 190M | Print casts and tool parts |
| **Immersive Engineering** | 187M | IE blueprint items printable; aesthetic kinship with the gantry multiblock |
| **Botania** | 165M | Mana-to-FU conversion flower |
| **Building Gadgets** | 145M | Template format interop |
| **Compact Machines** | 94M | Pre-fab compact machine interiors — "base in a box" blueprints |
| **ProjectE** | 76M | EMC↔FU exchanger — config-gated, **off by default** (balance grenade, meme value real) |
| **Xaero's / JourneyMap** | 222M / 335M | Print-zone map overlays and printer waypoints |

*More mods to evaluate — Patrick will provide a full list.*

### AE2 Integration — Filament Unit Converter

A dedicated block that bridges the ME network directly into the filament system. Only exists / is visible if Applied Energistics 2 is installed (soft dependency — hidden entirely without AE2).

**Concept:** End-game automation. The Filament Unit Converter attaches to the ME network and can export filament directly out of the Filament Winder or other converters — essentially giving the network an infinite filament supply as long as the required materials exist in storage. The spool never runs out as long as the network has stock.

**Appearance:** Looks like an attachment onto the AE2 system — a metal-framed block with a physical filament spool protruding from it, slowly spinning while active.

**GUI:**
- Player specifies which items/materials to auto-convert into filament
- Listens to the ME network for available stock of configured items
- Automatically pulls and converts items according to the configured rules
- Responds in real-time — if the network gains more stock, conversion resumes

**Behavior:**
- Works seamlessly with the full ME system (import/export buses, autocrafting, channels, etc.)
- Keeps the printer's filament topped up without manual spool management
- Only processes conversions for items the player has explicitly configured — no accidental consumption
- Hidden / uncraftable / non-functional if AE2 is not loaded

**Integration philosophy:**
- Capability APIs (Forge) or standard interfaces (Fabric) for clean integration
- No hard requirements on any external mod
- AE2 is the launch-day priority: T7 printer + ME network + autocrafting is an end-game setup people will go wild for

---

## World Loot & Blueprint Discovery

Blueprints can be *found* in the wild — making exploration rewarding at every stage of progression.

### Tiered Loot Placement

| Blueprint Tier | Loot Locations |
|---------------|----------------|
| T1–T2 | Village chests, dungeon loot |
| T3–T4 | Mineshafts, desert/jungle temples |
| T5 | Nether fortresses, bastion remnants |
| T6 | End cities, End ships |
| T7–T8 | Ultra-rare End city drops; potential boss loot |

- Each found blueprint labeled: *"Requires Tier X Printer"*
- Blueprint Discs are **reusable physical items** (not single-use consumables) — can be kept, traded, or stored in a Blueprint Library
- Pre-built structure blueprints included as a curated set (villages, towers, dungeons, etc.) — nice to have, but player-provided blueprints are the killer feature

### Signature Blueprints — Creator Easter Eggs

Specialty rare blueprints hidden in high-tier loot:
- **Figurines/Statues** of popular Minecraft YouTubers — prints a life-size decorative statue
  - Example: Chip & Milo figurines, found in End chests, T6 required
- **Signature Builds** — a creator's iconic base/house as a printable blueprint
- Each labeled with the creator's name and tier requirement

**Strategy:** Reach out to creators pre-launch, offer their likeness/build as an in-mod easter egg in exchange for a video feature. A YouTuber discovering their own statue blueprint mid-video is exactly the kind of moment that goes viral.

---

## Multiplayer & Server Features

### Print Zone Conflict Detection
- Mod-internal only — no dependency on external claim mods
- If a print job is active and another printer attempts to print into the same area, it throws an error and refuses to start
- Simple bounding box overlap detection — not a full land claim system

### Built-in Chunk Loading
- When a print job starts, the printer automatically force-loads all chunks within the print volume
- Chunk loading is released when the job completes, pauses, or is cancelled
- No external chunk loader mod required — works reliably for large T6/T7 prints out of the box

### Remote Terminal Block
- Craftable block that pairs to a specific printer and provides full remote control
- Load blueprints, check matter/RF levels, monitor job progress, start/stop/queue prints from a distance
- Multiple terminals can link to one printer
- Essential for builds where the printer multiblock is embedded inside a larger structure

### Server Blueprint Repository
- Server-wide shared Blueprint Library accessible to all players
- Blueprints stored as individual files on disk: `world/mc3dprint/blueprints/*.blueprint`
- Server owner manages the folder directly — add, remove, organize
- Modpack makers can pre-populate with curated content at pack assembly time
- In-game: accessible via a Server Repository block or as a dedicated tab in the Blueprint Library GUI

---

## Community & Launch Strategy

### Blueprint Sharing Platform
- Companion website for uploading/downloading `.blueprint` files
- Own this from day one — the community will build it themselves if you don't
- Implementation options: GitHub-backed repo with clean frontend, or dedicated site
- In-mod hook: optional "Browse Community Blueprints" button in GUI
- CurseForge project page calls this out explicitly to seed the community at launch

### In-Game Documentation — Patchouli Guidebook
- Auto-given to the player on first printer component craft
- Covers: tier progression, matter system, scanner usage, I/O setup, enhancements, blueprint format
- Standard for well-regarded tech mods — sets a professional tone and reduces support burden

### JEI Integration
- Custom "3D Printer Recipes" category showing: item, matter cost, RF cost, required tier
- Blueprint disc contents browsable as recipes
- Non-negotiable — players expect it and will complain loudly if it's absent
- JEI only — REI is Fabric-focused; on Forge 1.20.1, JEI is the standard

### Custom Advancement Tree
- Dedicated tab with a full progression tree as a built-in roadmap

| Advancement | Trigger |
|-------------|---------|
| *First Extrusion* | Complete first item print |
| *Architect* | Scan your first structure |
| *Fabricator* | Print your first structure |
| *Matter Matters* | Convert 1,000 matter points |
| *T7 Online* | Build a Tier 7 printer |
| *Found in the Wild* | Discover a blueprint in world loot |
| *Draconic Fabricator* | Build a Tier 8 printer *(hidden)* |

---

## Balancing & Tuning

### Placeholder Speed & Efficiency Values

Start conservative — easier to buff than nerf post-release. All values exposed in config.

| Tier | Blocks/sec | Matter Efficiency | RF/block |
|------|-----------|-------------------|----------|
| 1 | 0.5 | 50% | 100 RF |
| 2 | 1.0 | 55% | 90 RF |
| 3 | 2.0 | 65% | 75 RF |
| 4 | 4.0 | 75% | 60 RF |
| 5 | 8.0 | 85% | 45 RF |
| 6 | 12.0 | 92% | 30 RF |
| 7 | 20.0 | 98% | 15 RF |
| 8 ⭐ | 30.0 | 99% | 10 RF |

*Heavy revision expected during playtesting.*

### Locked Design Decisions
- ✅ Minecraft 1.20.1 / Forge — all integration targets verified on this version
- ✅ No machine durability — permanent investments, not maintenance
- ✅ No tier-up matter conversion — prevents cobblestone farm exploits
- ✅ Soft dependencies only — standalone mod, integrations are bonuses
- ✅ Multiplicative modifier stacking — prevents runaway enhancement values
- ✅ Power-loss pauses, never resets — no punishment for infrastructure mistakes

---

## Stretch Goals

- **Print Completion Effect** — visual flourish on job finish: beam of light, fireworks, sound cue. The "done" moment should feel *good*.
- **Hologram Idle Animation** — when no job is running, printer displays a slowly rotating hologram of the last blueprint printed. Purely cosmetic, looks great.
- **Printer Color / Skin System** — cosmetic dye or skin options for the printer frame. Drives screenshots and community engagement.
- **Deconstruct Mode** — printer runs in reverse, breaking down a structure and converting it back into matter points. Useful for clean demolition and resource recovery.
- **Batch Blueprint Sequencing** — chain multiple blueprints into a single automated sequence (print building A, then B, then C) without manual intervention.

---

## Open Questions

- **Mod loader & version:** ✅ **Minecraft 1.20.1, Forge only** — 1.20.1 is the modpack-standard version where every integration target overlaps (including Draconic Evolution, the usual deal-breaker). Architect the codebase to avoid locking out Fabric/Architectury in the future. Don't use Forge-only APIs where a cross-platform abstraction exists. Future-proof without building for two platforms today.
- **Blueprint file format:** NBT schematics, JSON, or custom `.blueprint` format? **New constraint (2026-06):** v1 must *import* vanilla structure `.nbt` (Create schematics) and Sponge `.schem` (WorldEdit) — so whatever the native format is, it needs an importer pipeline from day one.
- **Print area:** ✅ Must be pre-cleared — the printer cannot replace existing blocks. Players are responsible for clearing the space before printing.
- **Mod list:** Patrick to provide full list of favorite mods for compatibility evaluation and T8 tier candidates
- **Ore name:** Extrudium is a placeholder — final name TBD
