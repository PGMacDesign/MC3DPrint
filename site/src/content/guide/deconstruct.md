---
title: "Deconstruct Mode"
category: "Machines"
order: 6
summary: "Run any printer or fabricator in reverse — consume a region back into Filament Units at a lossy rate."
---

Every printer and fabricator can run **in reverse**. Select a region and the machine consumes it block by block, banking Filament Units into its docked spools first, then into any [Filament Racks](/guide/filament-rack/) reachable over [MC3D Cable](/guide/mc3d-cable/).

## Arming a region

1. Set **two corners** with the Structure Scanner, exactly like scanning.
2. **Sneak-click the machine** with the scanner. That hands the selection over as its deconstruct region and flips the machine into Deconstruct Mode.

The region obeys the machine's own print footprint (by default a Tier 3 printer deconstructs at most 3×3, a Tier 8 fabricator up to 51×51 — each tier's cap is configurable via `maxFootprint`) and must be within 64 blocks of the machine. Once armed, the region is outlined in the world as a **red wireframe** so the hazard zone is always visible.

Two safety rules keep accidents out:

- **The first job after arming always needs an explicit Start.** Auto mode never fires on a freshly armed region — handing a machine a region can't start dissolving blocks by surprise, even if Auto was left on from printing. After that one Start, Auto resumes as a standing recycler for the region (anything later placed inside gets consumed too — the wireframe is your reminder).
- **Re-arming resets the gate.** A new region, or toggling Print↔Deconstruct, cancels the job and requires a fresh Start.

Deconstruct works on **any blocks in the region** — it doesn't matter whether they were printed, scanned, or built by hand. The wireframe plus the manual first Start exist precisely so you always know what's about to be consumed.

## Lossy by design

Deconstructing credits a **fraction of each block's winding value** (config `deconstruct.yieldFactor`, default **50%**) at its exact tier. That's always strictly worse than mining the block and winding it yourself — recycling is a convenience, not a filament source:

- **Winder-blacklisted** blocks (planks, cactus, bamboo…) are removed at **zero** credit — the anti-laundering tag holds in both directions.
- **Itemless blocks** (water, crops, fire) clear for free, mirroring how printing places them for free.
- **Unvalued blocks** (dragon eggs, strict-mode unknowns) are **skipped in place** — the machine never destroys something for nothing.
- **Containers with items** are skipped in place. Empty them first; the machine never deletes or ejects stored items.
- Unbreakable blocks (bedrock) are skipped.

## Job behavior

Jobs run **top-down** (supported blocks come off before their supports), consume RF per block at the machine's normal rate, and benefit from Speed and RF Efficiency upgrades — but Efficiency modules and resins never boost the yield. Power loss pauses the job without losing progress. When every reachable spool is full, the machine pauses **before** removing the next block, so recovered filament is never voided. Changing the region or flipping back to Print mode cancels the job cleanly — blocks already recycled stay recycled.
