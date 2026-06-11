# MC3DPrint — Task Board

Execution tracking for [MC3DPrint.md](MC3DPrint.md). Design doc is the *what/why*; this file is the *order and status*.

Milestones are sequential-ish — M1 blocks most things, but M3 can start before M2 finishes.

---

## M0 — Project Scaffold ✅

- [x] Lock target: Minecraft 1.20.1 / Forge (NeoForge evaluated & rejected for now)
- [x] Forge MDK 1.20.1-47.4.10 scaffold, clean `./gradlew build`
- [x] Mod skeleton: registries, creative tab, placeholder T1 printer block + blank disc item
- [x] Integration roadmap from top-500 survey (v1/v2/v3 scoping)

## M1 — Blueprint Core (format + importers)

The keystone — everything else consumes this. Pure logic, testable without a running game.

- [x] **Decided (2026-06-10):** blueprint storage = world-level file store (`world/mc3dprint/blueprints/<uuid>.blueprint`, gzipped NBT); discs carry UUID reference + cached metadata. Inline disc NBT would exceed packet/NBT limits on large builds; file store also powers the Server Repository and `.blueprint` sharing directly
- [ ] Native blueprint data model: block palette + positions + block entities, NBT-serializable
- [ ] Serializer/deserializer round-trip + unit tests
- [ ] Importer: vanilla structure `.nbt` (Create schematics use this format)
- [ ] Importer: Sponge `.schem` v2/v3 (WorldEdit)
- [ ] Exporter: `.schem` (WorldEdit interop is import *and* export)
- [ ] Transform ops: rotate 90°/180°/270°, mirror X/Z — with block-state rotation (stairs, etc.)
- [ ] Blueprint Disc item: stores blueprint reference, lock/unlock (Shift+Right Click), locked visual indicator
- [ ] `.blueprint` file read/write for the disk-based Server Repository (`world/mc3dprint/blueprints/`)

## M2 — T1 Printer Machine (Item Mode)

- [ ] Printer BlockEntity + menu/screen GUI
- [ ] Forge Energy (RF) buffer, capability exposure, per-tier buffer sizes in config
- [ ] Smart Print Slot — Item Mode: drop a craftable item, print a copy
- [ ] Print job timing + RF drain; "PAUSED — Insufficient Power" state (pause, never reset)
- [ ] I/O: top face input, bottom/front output, sides reserved; vanilla hopper compat
- [ ] Output buffer pauses printer when full — never void items
- [ ] Config file: base costs, speeds, efficiency values (from Balancing table)

## M3 — Scanner & Capture

- [ ] Scanner tool item: two-corner bounding box selection (WorldEdit wand style)
- [ ] Scan capture → write to Blank Blueprint Disc
- [ ] Scanner tier volume limits (T1 ≈ 7×7×7)
- [ ] Capture validation: volume limits, unscannable blocks policy

## M4 — Structure Printing

- [ ] Blueprint Mode in Smart Print Slot (slot detects item vs disc)
- [ ] Print queue: multiple jobs, reorder, pause/resume, cancel (configurable refund %)
- [ ] Layer-by-layer bottom-up placement engine
- [ ] Pre-clear validation + hologram preview (green = clear, red = obstructed)
- [ ] Power-loss pause/resume mid-print, queue persists through restarts
- [ ] Built-in chunk loading for print volume (release on complete/pause/cancel)
- [ ] Print zone conflict detection (bounding box overlap between active jobs)
- [ ] Print history log (last N jobs)

## M5 — Filament System

- [ ] Filament Winder block: materials → FU, winder tier gating
- [ ] FU value tables (symmetric conversion, group-based) — config-exposed
- [ ] Filament Spool items T1–T8 with capacities
- [ ] Spool side attachment (Shift+Right Click), spool slots by tier, auto-switch on depletion
- [ ] Tier down-conversion, one step, 16:1 — no tier-up, ever
- [ ] Switch print costs from placeholder to FU economy + per-tier efficiency
- [ ] Matter Calculator GUI panel (FU cost, RF cost, ETA before committing)

## M6 — Tiers, Multiblocks & Progression

- [ ] T2–T4 single-block printers (print area scaling, item tier gating)
- [ ] Item tier requirement system (T1 can't print a diamond sword)
- [ ] Multiblock framework: formation by controller right-click, validation, rendered structure
- [ ] Collapse-to-item: break controller → single item drop → place to reform intact
- [ ] T5–T8 multiblock structures (9×9 → 33×33)
- [ ] Printite ore: End-only worldgen, ore/ingot items, animated shimmer texture *(final name TBD)*
- [ ] Upgrade/expansion slot system: Speed, Efficiency, Matter Density, RF Efficiency, Buffer — tiered, multiplicative stacking
- [ ] T8 Draconic gate (soft dep: only with DE installed)

## M7 — Visuals & Feel

- [ ] Structural frame render around print volume
- [ ] X/Y gantry arms + print head animation (FDM-style, speed scales with tier/upgrades)
- [ ] Zap/beam effect + block materialization, particle trail, per-block sound
- [ ] Spool spin animation on machine exterior; GUI FU fill bars
- [ ] Print completion effect *(stretch)*; hologram idle animation *(stretch)*

## M8 — v1 Integrations

- [ ] JEI: "3D Printer Recipes" category (item, FU cost, RF cost, required tier); disc contents browsable
- [ ] Patchouli guidebook, auto-given on first printer craft
- [ ] AE2: Filament Unit Converter block (hidden without AE2), ME-driven matter sourcing, autocrafting triggers
- [ ] Create: schematic import UX (table/slot that converts Create schematics → Blueprint Discs)
- [ ] WorldEdit: `.schem` import/export UX
- [ ] Compat testing: Mekanism/Thermal/EnderIO/Flux pipes & energy, RS pattern after AE2

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
- [ ] Patrick's full favorite-mod list for compat evaluation

## v2 / v3 Backlog

See [Integration Roadmap in the design doc](MC3DPrint.md#integration-roadmap--v2--v3-stretch-goals) — CraftTweaker/KubeJS, Jade/TOP, FTB Quests, MineColonies, CC: Tweaked, loot-injection targets, and the v3 fun list. Plus design-doc stretch goals: Deconstruct Mode, batch sequencing, printer skins.
