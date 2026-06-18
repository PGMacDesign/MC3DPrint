# MC3DPrint — Task Board

Execution tracking for [MC3DPrint.md](MC3DPrint.md). Design doc is the *what/why*; this file is the *order and status*.

Milestones are sequential-ish — M1 blocks most things, but M3 can start before M2 finishes.

---

## M0 — Project Scaffold ✅

- [x] Lock target: Minecraft 1.20.1 / Forge (NeoForge evaluated & rejected for now)
- [x] Forge MDK 1.20.1-47.4.10 scaffold, clean `./gradlew build`
- [x] Mod skeleton: registries, creative tab, placeholder T1 printer block + blank disc item
- [x] Integration roadmap from top-500 survey (v1/v2/v3 scoping)

## M1 — Blueprint Core (format + importers) ✅

The keystone — everything else consumes this. Pure logic, testable without a running game.

- [x] **Decided (2026-06-10):** blueprint storage = world-level file store (`world/mc3dprint/blueprints/<uuid>.blueprint`, gzipped NBT); discs carry UUID reference + cached metadata. Inline disc NBT would exceed packet/NBT limits on large builds; file store also powers the Server Repository and `.blueprint` sharing directly
- [x] Native blueprint data model: block palette + positions + block entities, NBT-serializable
- [x] Serializer/deserializer round-trip + unit tests
- [x] Importer: vanilla structure `.nbt` (Create schematics use this format)
- [x] Importer: Sponge `.schem` v2/v3 (WorldEdit)
- [x] Exporter: `.schem` (WorldEdit interop is import *and* export)
- [x] Transform ops: rotate 90°/180°/270°, mirror X/Z — with block-state rotation (stairs, etc.)
- [x] Blueprint Disc item: stores blueprint reference, lock/unlock (Shift+Right Click), locked visual indicator
- [x] `.blueprint` file read/write for the disk-based Server Repository (`world/mc3dprint/blueprints/`)

## M2 — T1 Printer Machine (Item Mode) ✅

- [x] Printer BlockEntity + menu/screen GUI
- [x] Forge Energy (RF) buffer, capability exposure, per-tier buffer sizes in config
- [x] Smart Print Slot — Item Mode: drop a craftable item, print a copy
- [x] Print job timing + RF drain; "PAUSED — Insufficient Power" state (pause, never reset)
- [x] I/O: top face input, bottom/front output, sides reserved; vanilla hopper compat
- [x] Output buffer pauses printer when full — never void items
- [x] Config file: base costs, speeds, efficiency values (from Balancing table)

## M3 — Scanner & Capture ✅

- [x] Scanner tool item: two-corner bounding box selection (WorldEdit wand style)
- [x] Scan capture → write to Blank Blueprint Disc
- [x] Scanner tier volume limits (T1 ≈ 7×7×7)
- [x] Capture validation: volume limits, unscannable blocks policy

## M4 — Structure Printing ✅ *(2 partials)*

- [x] Blueprint Mode in Smart Print Slot (slot detects item vs disc)
- [ ] Print queue: multiple jobs, reorder, pause/resume, cancel (configurable refund %) — *partial: sequential jobs w/ persistence done; reorder UI + cancel refund pending (FU refunds need M5)*
- [x] Layer-by-layer bottom-up placement engine
- [x] Pre-clear validation; hologram preview shipped 2026-06-11 (Ghost toggle in GUI: translucent blueprint at the build position, green=clear / red=obstructed, matching blocks hidden; size cap + 16-block render distance in config)
- [x] Power-loss pause/resume mid-print, jobs persist through restarts
- [x] Built-in chunk loading for print volume (release on complete/cancel)
- [x] Print zone conflict detection (bounding box overlap between active jobs)
- [x] Print history log (last N jobs, NBT; GUI display later)

## M5 — Filament System ✅ *(2 partials)*

- [x] Filament Winder block: materials → FU. Single universal winder; the spool gates it — a material only winds into a spool of its exact tier (revised 2026-06-11, was a T1–T4 winder ladder)
- [x] FU value tables (symmetric conversion, group-based) — config-exposed (`fuValues`, item + #tag syntax)
- [x] Filament Spool items T1–T8 with capacities
- [x] Spool side attachment (Shift+Right Click), auto-switch on depletion *(slots-by-tier lands with M6 tiers; T1 = 1 dock)*
- [x] Tiered FU, down-conversion ONLY at 4:1 per tier (ratio revised from 16:1) — up-conversion hard-blocked per original requirement (anti-cobblestone-exploit); config `filamentConversionRatio`. Implemented 2026-06-11
- [x] Switch print costs from placeholder to FU economy + per-tier efficiency (M2 dupe closed)
- [ ] Matter Calculator GUI panel — *partial: template FU cost + filament gauges in GUI; full blueprint calculator (RF + ETA) pending*

## M6 — Tiers, Multiblocks & Progression ✅

- [x] T2–T4 single-block printers (print area scaling, item tier gating)
- [x] Item tier requirement system (T1 can't print a diamond sword)
- [x] Multiblock framework: formation by controller right-click, validation *(rendered combined geometry → M7 visuals)*
- [x] Collapse-to-item: break controller → single item drop → place to reform intact (full machine state in item NBT)
- [x] T5–T8 multiblock structures (9×9 → 33×33 print areas; casing bases 3×3 → 9×9)
- [x] Printite ore: End-only worldgen (data-driven), crystal item, glow speck texture *(animated shimmer → M7; final name TBD)*
- [x] Upgrade system: Speed/Efficiency/RF Efficiency/Buffer, multiplicative stacking, slots = tier *(Matter Density deferred — needs input-side mechanics; slot-grid GUI → M7 GUI pass)*
- [x] T8 Draconic gate (soft dep: formation requires DE + Awakened Draconium corners)

## M7 — Visuals & Feel ✅ *(needs eyes-on runClient pass; 2 deferrals)*

- [x] Structural frame render around print volume (line/hologram style)
- [x] X/Y gantry arms + print head animation (rides last placement; speed follows tier/upgrade cadence) — *solid modeled geometry is a later polish pass*
- [x] Zap/beam effect + block materialization, spark particles, per-block sound
- [x] Spool spin animation on machine exterior — line-art spools on side faces: spin while printing, winding shrinks with fill (2026-06-11); solid block models remain a polish pass; GUI FU fill bars ✅ (M5)
- [x] Print completion effect (firework burst + chime); hologram idle animation *(stretch, deferred)*

## M8 — v1 Integrations ✅ *(AE2-deep + live compat testing flagged)*

- [x] JEI: "3D Printing" category (item, base FU cost, required spool tier; printers/fabricators **and the Filament Winder** as catalysts) — *disc-contents browsing + RF column pending*
- [x] Tier discovery: tier-colored "3D Print: Tier N (F FU)" tooltip on every convertible item (config-driven, shows in inventory/creative/JEI) + searchable `mc3dprint:filament/tier_1..7` item tags (`$`-search in JEI). *Tags mirror the default economy — they don't auto-track `fuValues` config overrides; tooltip + JEI category do.*
- [x] Patchouli guidebook (soft dep, data-driven; 2 categories / 6 entries) — auto-given on first printer craft (2026-06-11)
- [x] Filament Converter block: pulls filtered items from ANY adjacent inventory (incl. ME/RS interfaces), winds FU straight into docked spools on adjacent printers — *AE2-API deep integration (network listening, autocrafting triggers) deferred: needs testing against real AE2*
- [x] Create: schematic import UX via `/mc3dprint import <file>.nbt` (vanilla structure format)
- [x] WorldEdit: `.schem` import (`/mc3dprint import`) + export (`/mc3dprint export` with disc in hand)
- [ ] Compat testing: Mekanism/Thermal/EnderIO/Flux pipes & energy — *needs a real modpack dev instance; not coverable headless*

## M9 — World Content, Multiplayer & Launch ✅ *(code complete; human launch steps in LAUNCH.md)*

- [x] Crafting recipes for the full progression chain (29 recipes, T1→T8 + tools/spools/upgrades) — *not on the original list, but a survival-mode launch blocker*

- [x] Blueprint Disc loot injection via global loot modifiers (villages/dungeon + mineshaft/temples; chances + targets data-driven) — *End city tier waits on higher-tier curated content*
- [x] Curated pre-built blueprint set (starter hut, watchtower, storage shed) — bundled as data, auto-installed per world with deterministic UUIDs; pack makers can ship their own the same way
- [ ] Signature/creator blueprints + outreach *(human task — see LAUNCH.md)*
- [x] Advancement tree — full 8-node spec tree (root → First Extrusion / Architect → Fabricator / Matter Matters / Found in the Wild → Printite → T7 Online → Draconic, hidden) with custom criteria triggers (2026-06-11)
- [x] Remote Terminal block (sneak-click printer to pair, opens its GUI from anywhere; multiple terminals per printer)
- [ ] Server Blueprint Repository block + GUI tab — *partial: world file store + /mc3dprint import/export + curated auto-install cover the server-owner workflow; dedicated block/GUI deferred to the GUI pass*
- [ ] Balancing pass over all config values — *needs human playtesting (see LAUNCH.md)*
- [ ] CurseForge + Modrinth pages — *store copy drafted in LAUNCH.md; publishing is a human step*

---

## Post-M9: Testing & QoL (Jun 2026)

- [x] Creative Energy Source — infinite RF to neighbors + extract-only cap; creative menu only, no recipe, drops nothing
- [x] Creative Filament Spool — acts as a T8 spool, always full, never depletes; winders skip it; creative menu only
- [x] Simple Generator (registry id `clock_generator`) — 10 RF/t from furnace fuel at 10× burn time (configs `general.clockGeneratorRfPerTick`, `general.clockGeneratorBurnMultiplier`), craftable, hopper-feedable, so the mod works with no other RF mod
- [x] Scanner selection preview — corner A (blue) / corner B (cyan) boxes + full selection outline rendered in-world while holding the scanner
- [x] Gate T8 fabricator + T8 spool behind Draconic Evolution (creative tab, JEI, recipes via `forge:conditional`)
- [x] Gate Filament Converter behind AE2 (creative tab, JEI, recipe) — *note: converter also works with plain chests/RS; easy to un-gate if that's wanted later*

## Post-Audit Features (2026-06-11)

- [x] Repair printing: re-running a disc skips blocks that already match (zero cost) and fills only what's missing; never overwrites mismatches
- [x] Hologram preview: per-printer Ghost toggle, ghost blocks within `previewRenderDistance` (default 16) of the camera, full-extent frame outline, `previewMaxBlocks` cap (default 10k)
- [x] Recipe-derived FU valuation: items/blocks with no explicit value are priced from their crafting/smelting/stonecutting recipes (memoized recursive graph, cycle detection, depth cap 12); storage blocks (`diamond_block` 450@5, etc.) now derive instead of being hardcoded. Strict mode (`unknownBlocksPrintable`, default false) refuses un-priced blocks — closes the scan-cheap-print exploit. Pack-maker override via `fuValues` always wins; cross-mod registration via `MC3DPrintAPI` (direct) + Forge IMC (`register_fu_value`, no hard dep). See [FU-VALUES-AND-COMPAT.md](FU-VALUES-AND-COMPAT.md). (2026-06-12)

## Audit Findings (2026-06-11) — see [AUDIT-2026-06-11.md](AUDIT-2026-06-11.md)

Requirements from the original design doc that were rewritten/dropped without a record during the autonomous build:

- [x] Advancements: *First Extrusion*, *Fabricator*, *Found in the Wild* added; *Matter Matters* (1,000 FU wound, tracked per player) and *Architect* (real scan) retargeted to custom criteria triggers; machines track their placer as owner — done 2026-06-11
- [x] Patchouli: handbook auto-given on first printer craft (soft dep, once per player); I/O Setup, Blueprint Discs & Files, and Upgrade Modules entries added — done 2026-06-11
- [x] Upgrade modifier rates → config (`upgrades` section: speed/efficiency/rf/buffer factors); tooltips read live values — done 2026-06-11
- [x] ~~Tiered upgrade items~~ — **decided 2026-06-11 (Patrick): keep flat**, doc amended
- [x] FU table: concrete family @ 5 FU added; nuggets = ingot/9 rounded down (gold 1 FU, iron 2 FU @ T2) — decided 2026-06-11 (Patrick): lossy is fine
- [x] I/O: sides now spool-exclusive per spec (no item handler on sides; bottom = output, top = input) — decided + implemented 2026-06-11
- [x] Spool exterior render: docked spools drawn on side faces, spinning while printing, winding radius shrinks with fill level (line-art pass; solid models later) — closes the M7 "spool spin animation" deferral

## Open Decisions

- [x] Blueprint storage: ✅ file store + disc reference (see M1)
- [x] Final ore name → **Extrudium** (chosen 2026-06-18; project-wide rename from the Printite placeholder shipped)
- [x] ~~Clarify the 16:1 down-conversion rule~~ — resolved 2026-06-11: tiered FU, down-only at 4:1 per tier, up-conversion hard-blocked (see design doc)
- [ ] Patrick's full favorite-mod list for compat evaluation

## v2 / v3 Backlog

See [Integration Roadmap in the design doc](MC3DPrint.md#integration-roadmap--v2--v3-stretch-goals) — CraftTweaker/KubeJS, Jade/TOP, FTB Quests, MineColonies, CC: Tweaked, loot-injection targets, and the v3 fun list. Plus design-doc stretch goals: Deconstruct Mode, batch sequencing, printer skins.
