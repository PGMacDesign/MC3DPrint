---
title: "Blueprint Discs & files"
category: "Basics"
order: 3
summary: "How written discs reference saved scans, read their tier and print cost, and import or export as files."
---

The whole loop is simple: [scan a build](/guide/scanning/), save it to a disc, then print it anywhere a printer can reach. Scans are stored as blueprints in the world save; a Blueprint Disc is just the key that references one.

## Working with discs

- `Shift+Right Click` a written disc to **lock** it against being overwritten.
- Discs also turn up in **village and exploration loot**: print what you find, no scanning required.
- To **rename** a disc, drop it in an Anvil and type a name (costs 1 level of XP).

## Tier and print cost

Every written disc shows its **tier**: the highest material tier among its blocks. One diamond block in a pile of stone still reads as that tier, and that's the lowest [machine tier](/guide/printer-tiers/) that can print all of it.

Each disc also shows a **Print Cost** in that top tier. Free structural blocks like water and crops cost nothing toward it. See [the FU economy](/guide/fu-economy/) for how costs are derived.

## Cross-mod builds

Some curated builds are made from **another mod's blocks** and only appear when that mod is installed. The **Coppertide Park** set (a geyser lagoon, drop tower, lazy river, enclosed glass serpentine, pendulum half-pipe, and four-lane racer) is built from [MC Waterslides](https://pgmacdesign.github.io/MCWaterSlides/) blocks: install that mod and the six park discs join the creative list and world loot; without it they stay hidden. Their print costs derive from the mod's own recipes automatically.

## Import & export (operators)

- `/mc3dprint import <file>` reads `.schem`, `.litematic`, and `.nbt` files from `world/mc3dprint/import/`.
- `/mc3dprint export` writes the disc in your hand out to a `.schem`.

Shipped blueprints live in `world/mc3dprint/blueprints/`.
