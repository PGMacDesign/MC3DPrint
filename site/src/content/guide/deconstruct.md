---
title: "Deconstruct Mode"
category: "Machines"
order: 6
summary: "Run any printer or fabricator in reverse: consume a region back into Filament Units at a lossy rate."
---

Every printer and fabricator can run **in reverse**. Select a region and the machine consumes it block by block, banking Filament Units into its docked spools first, then into any [Filament Racks](/guide/filament-rack/) reachable over [MC3D Cable](/guide/mc3d-cable/).

## Un-printing the last build

The common case needs no scanner at all. Print something, decide you don't want it, and flip the machine to **Decon**: with no region of its own armed, it arms an **un-print** of the build it last printed and the status line reads **Ready: Un-print**. Press Start and the build comes back as filament.

An un-print is **masked** to the print itself. Only positions that print filled, and which still hold the block it put there, are eligible. The terrain the build sits on, a chest you placed inside it, a wall you extended, a block you swapped out: all left alone. That is the difference between un-printing a build and consuming the box it occupied.

Three things worth knowing:

- It is **one-shot**. When the job finishes the machine disarms itself, because the build it targeted no longer exists.
- A **cancelled or partial print counts**. The placement is recorded when the job starts, so a print you stopped halfway is still un-printable.
- Cells the print found **already correct** are left alone. Printing repairs matching blocks for free rather than replacing them, so those blocks were never the machine's work: print a stone build into a stone hillside and the un-print takes the build, not the hill.
- Handing over a **scanner selection replaces it** with a plain region, which consumes everything in the box. The wireframe is the same either way; the status line is what tells you which kind of region is armed.

If the blueprint file behind the print is gone (the disc was from another world), the machine refuses to arm rather than falling back to eating the whole box.

## Arming a region by hand

1. Set **two corners** with the Structure Scanner, exactly like scanning.
2. **Sneak-click the machine** with the scanner. That hands the selection over as its deconstruct region and flips the machine into Deconstruct Mode. On a Tier 5+ fabricator, sneak-clicking **any casing** of the formed pad arms the controller buried in the middle, so you never have to find the controller block itself.

A machine in Deconstruct Mode with **no armed region** and no print to undo shows **No Region Set** in the status line; it isn't broken, it's waiting for a selection. Hand it one, or toggle the GUI's **Mode** button back to Print.

The region obeys the machine's own print footprint (by default a Tier 3 printer deconstructs at most 3×3, a Tier 8 fabricator up to 51×51; each tier's cap is configurable via `maxFootprint`) and must be within 64 blocks of the machine. Once armed, the region is outlined in the world as a **red wireframe** so the hazard zone is always visible.

Two safety rules keep accidents out:

- **The first job after arming always needs an explicit Start.** Auto mode never fires on a freshly armed region. Handing a machine a region can't start dissolving blocks by surprise, even if Auto was left on from printing. After that one Start, Auto resumes as a standing recycler for the region (anything later placed inside gets consumed too; the wireframe is your reminder).
- **Re-arming resets the gate.** A new region, or toggling Print↔Deconstruct, cancels the job and requires a fresh Start.

Deconstruct works on **any blocks in the region**: it doesn't matter whether they were printed, scanned, or built by hand. The wireframe plus the manual first Start exist precisely so you always know what's about to be consumed.

## Lossy by design

Deconstructing credits a **fraction of each block's winding value** (config `deconstruct.yieldFactor`, default **50%**) at its exact tier. That's always strictly worse than mining the block and winding it yourself. Recycling is a convenience, not a filament source:

- **Winder-blacklisted** blocks (planks, cactus, bamboo…) are removed at **zero** credit: the anti-laundering tag holds in both directions.
- **Itemless blocks** (water, crops, fire) clear for free, mirroring how printing places them for free.
- **Unvalued blocks** (spawners, budding amethyst, strict-mode unknowns) are **skipped in place**: the machine never destroys something for nothing.
- **Containers with items** are skipped in place. Empty them first; the machine never deletes or ejects stored items.
- Unbreakable blocks (bedrock) are skipped.

> **Wind-only treasure is not protected.** Skipping is decided purely by whether a block has an FU value, and wind-only items like the **dragon egg** are valued (they just can't be printed back). So a dragon egg inside an armed region is consumed like anything else, crediting half its winding value. Move anything you care about outside the region before you start.

## Job behavior

Jobs run **top-down** (supported blocks come off before their supports), consume RF per block at the machine's normal rate, and benefit from Speed and RF Efficiency upgrades, but Efficiency modules and resins never boost the yield. Power loss pauses the job without losing progress. When every reachable spool is full, the machine pauses **before** removing the next block, so recovered filament is never voided. Changing the region or flipping back to Print mode cancels the job cleanly. Blocks already recycled stay recycled.
