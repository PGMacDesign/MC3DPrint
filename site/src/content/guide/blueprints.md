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

Each disc also shows a **Print Cost** in that top tier. Free structural blocks cost nothing toward it: water, fire, farmland and dirt paths. Crops are not free; a planted block costs whatever you would have planted, so a field of wheat costs seeds and a crop whose seed carries no value is refused outright in the default strict mode (permissive packs that set `unknownBlocksPrintable` print it at the unknown-block rate instead). See [the FU economy](/guide/fu-economy/) for how costs are derived.

## Import & export (operators)

- `/mc3dprint import <file>` reads `.schem` and `.nbt` files from `world/mc3dprint/import/`.
- `/mc3dprint export` writes the disc in your hand out to a `.schem`.

Shipped blueprints live in `world/mc3dprint/blueprints/`.
