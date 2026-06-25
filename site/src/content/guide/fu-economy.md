---
title: "The FU economy"
category: "Basics"
order: 5
summary: "Why tier is value, how block costs are derived, and the down-only rule that governs every print."
---

FU is **denominated by tier** — a T5 spool stores T5 FU, a T1 spool stores T1 FU. One unit of Tier N FU is worth several units of Tier N-1 (the conversion ratio is set in config). Tier is value, not just capacity, and every spool T1–T8 holds a uniform 100,000 FU.

## Where costs come from

Each block's FU cost and tier are derived from its recipe graph — crafting, plus optionally smelting and stonecutting. A diamond block costs what its diamonds are worth; stone costs almost nothing. A block's material tier sets the **minimum** machine and spool tier needed to print it.

## Two different rules

These often trip people up, so keep them straight:

- **Winding** (making FU) is **exact-tier** — covered in [Filament Units](/guide/filament-units/).
- **Spending** (printing) is **down-only** — a spool pays any cost *at or below* its tier, never above.

So a high-tier spool can print a whole stone house, but a T1 spool can never print netherite.

## The markup

Lower-tier machines pay a higher FU multiplier per block, and printing always costs a little more than the matter is worth. Stack **4 Efficiency modules** to reach exact break-even (1:1) — you can never print matter for *less* than it's worth. See [upgrades](/guide/upgrades/) for module slots.

## Printing food

Food has FU values too, so a printer in item mode can print meals — bread is cheap, cooked and golden food cost more. But food **can't be wound back** into FU, so printing food is a one-way sink, not a loop.
