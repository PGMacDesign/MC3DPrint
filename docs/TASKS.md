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
- [x] Pre-clear validation *(server-side done; hologram preview render moved to M7 visuals)*
- [x] Power-loss pause/resume mid-print, jobs persist through restarts
- [x] Built-in chunk loading for print volume (release on complete/cancel)
- [x] Print zone conflict detection (bounding box overlap between active jobs)
- [x] Print history log (last N jobs, NBT; GUI display later)

## M5 — Filament System ✅ *(2 partials)*

- [x] Filament Winder block: materials → FU, winder tier gating
- [x] FU value tables (symmetric conversion, group-based) — config-exposed (`fuValues`, item + #tag syntax)
- [x] Filament Spool items T1–T8 with capacities
- [x] Spool side attachment (Shift+Right Click), auto-switch on depletion *(slots-by-tier lands with M6 tiers; T1 = 1 dock)*
- [ ] ~~Tier down-conversion 16:1~~ — *superseded: FU is one universal unit; winder tier gating provides the progression gate. 16:1 rule needs design clarification (see Open Decisions)*
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
- [ ] Spool spin animation on machine exterior — *deferred (needs custom block models)*; GUI FU fill bars ✅ (M5)
- [x] Print completion effect (firework burst + chime); hologram idle animation *(stretch, deferred)*

## M8 — v1 Integrations ✅ *(AE2-deep + live compat testing flagged)*

- [x] JEI: "3D Printing" category (item, base FU cost, required tier; printers/fabricators as catalysts) — *disc-contents browsing + RF column pending*
- [x] Patchouli guidebook (soft dep, data-driven; 2 categories / 3 entries) — *auto-give waits on crafting recipes (M9)*
- [x] Filament Converter block: pulls filtered items from ANY adjacent inventory (incl. ME/RS interfaces), winds FU straight into docked spools on adjacent printers — *AE2-API deep integration (network listening, autocrafting triggers) deferred: needs testing against real AE2*
- [x] Create: schematic import UX via `/mc3dprint import <file>.nbt` (vanilla structure format)
- [x] WorldEdit: `.schem` import (`/mc3dprint import`) + export (`/mc3dprint export` with disc in hand)
- [ ] Compat testing: Mekanism/Thermal/EnderIO/Flux pipes & energy — *needs a real modpack dev instance; not coverable headless*

## M9 — World Content, Multiplayer & Launch

- [ ] Blueprint Disc loot injection, tiered by structure (villages → End cities)
- [ ] Curated pre-built blueprint set
- [ ] Signature/creator blueprints + outreach *(easter eggs)*
- [ ] Advancement tree (First Extrusion → Draconic Fabricator)
- [ ] Remote Terminal block (pair to printer, full remote control)
- [ ] Server Blueprint Repository block + GUI tab
- [ ] Balancing pass over all config values
- [ ] CurseForge + Modrinth pages, blueprint sharing site plan

---

## Open Decisions

- [x] Blueprint storage: ✅ file store + disc reference (see M1)
- [ ] Final ore name (Printite is placeholder)
- [ ] Clarify the 16:1 down-conversion rule from the design doc — contradicts universal FU; currently superseded by winder tier gating
- [ ] Patrick's full favorite-mod list for compat evaluation

## v2 / v3 Backlog

See [Integration Roadmap in the design doc](MC3DPrint.md#integration-roadmap--v2--v3-stretch-goals) — CraftTweaker/KubeJS, Jade/TOP, FTB Quests, MineColonies, CC: Tweaked, loot-injection targets, and the v3 fun list. Plus design-doc stretch goals: Deconstruct Mode, batch sequencing, printer skins.
