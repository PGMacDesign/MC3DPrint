---
title: "Blueprint Discs & files"
category: "Basics"
order: 3
summary: "How written discs reference saved scans, read their tier and print cost, and import or export as files."
---

The whole loop is simple: [scan a build](/guide/scanning/), save it to a disc, then print it anywhere a printer can reach. Scans are stored as blueprints in the world save; a Blueprint Disc is just the key that references one.

## Working with discs

- `Shift+Right Click` a written disc to **lock** it against being overwritten.
- Discs also turn up in **any structure chest** and in **archaeology** finds: print what you find, no scanning required. See [finding blueprints](#finding-blueprints-in-the-world) below.
- To **rename** a disc, drop it in an Anvil and type a name (costs 1 level of XP).

## Tier and print cost

Every written disc shows its **tier**: the highest material tier among its blocks. One diamond block in a pile of stone still reads as that tier, and that's the lowest [machine tier](/guide/printer-tiers/) that can print all of it.

Each disc also shows a **Print Cost** in that top tier. Free structural blocks like water and crops cost nothing toward it. See [the FU economy](/guide/fu-economy/) for how costs are derived.

## Finding blueprints in the world

Any chest a structure generates can hold a Blueprint Disc, vanilla or modded, plus the suspicious sand and gravel you brush at archaeology sites. Nothing is region-locked: a desert house is as likely to turn up in a taiga village as in a pyramid.

**Loot never repeats a build you already have.** Once found, a build is held out of the pool until every findable build has been found, at which point the catalogue resets and they can appear again. So each disc you dig up is one you have not seen.

Whether "found" is tracked per player or for the whole server follows your library setting. With the default shared Blueprint Repository the server works through one set together, which is coherent because anyone can re-burn anything anyone found. Set `blueprintRepositoryIsShared = false` and each player collects their own set.

Two knobs live under `[loot]` in `mc3dprint-common.toml`:

- `blueprintChanceMultiplier` scales the drop rate (`1.0` ships as the default).
- `noDuplicateBlueprints` turns the no-repeat rule off. Finds are still recorded while it is off, so turning it back on resumes where you left off.

### Operator commands

- `/mc3dprint discovered list` reports how many builds are found and how many remain.
- `/mc3dprint discovered add <build>` marks one as already found. Useful on a world that predates this system, where discs sitting loose in a chest were never catalogued.
- `/mc3dprint discovered reseed` re-syncs the ledger from the Blueprint Repository after depositing a batch of discs.
- `/mc3dprint discovered reset` clears the ledger so every build is findable again.

## Cross-mod builds

Some curated builds are made from **another mod's blocks** and only appear when that mod is installed. The **Coppertide Park** set (a geyser lagoon, drop tower, lazy river, enclosed glass serpentine, pendulum half-pipe, and four-lane racer) is built from [MC Waterslides](https://pgmacdesign.github.io/MCWaterSlides/) blocks: install that mod and the six park discs join the creative list and world loot; without it they stay hidden. Their print costs derive from the mod's own recipes automatically.

## Import & export (operators)

- `/mc3dprint import <file>` reads `.schem`, `.litematic`, and `.nbt` files from `world/mc3dprint/import/`.
- `/mc3dprint export` writes the disc in your hand out to a `.schem`.

Shipped blueprints live in `world/mc3dprint/blueprints/`.
