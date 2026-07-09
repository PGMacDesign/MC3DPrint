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

The region obeys the machine's own print footprint (a Tier 3 printer deconstructs at most 3×3, a Tier 8 fabricator up to 51×51) and must be within 64 blocks of the machine. Start the job with the Start button, Auto mode, or a redstone pulse — identical to printing.

## Lossy by design

Deconstructing credits a **fraction of each block's winding value** (config `deconstruct.yieldFactor`, default **50%**) at its exact tier. That's always strictly worse than mining the block and winding it yourself — recycling is a convenience, not a filament source:

- **Winder-blacklisted** blocks (planks, cactus, bamboo…) are removed at **zero** credit — the anti-laundering tag holds in both directions.
- **Itemless blocks** (water, crops, fire) clear for free, mirroring how printing places them for free.
- **Unvalued blocks** (dragon eggs, strict-mode unknowns) are **skipped in place** — the machine never destroys something for nothing.
- **Containers with items** are skipped in place. Empty them first; the machine never deletes or ejects stored items.
- Unbreakable blocks (bedrock) are skipped.

## Job behavior

Jobs run **top-down** (supported blocks come off before their supports), consume RF per block at the machine's normal rate, and benefit from Speed and RF Efficiency upgrades — but Efficiency modules and resins never boost the yield. Power loss pauses the job without losing progress. When every reachable spool is full, the machine pauses **before** removing the next block, so recovered filament is never voided. Changing the region or flipping back to Print mode cancels the job cleanly — blocks already recycled stay recycled.
