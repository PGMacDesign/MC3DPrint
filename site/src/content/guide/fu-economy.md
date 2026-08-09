---
title: "The FU economy"
category: "Basics"
order: 5
summary: "Why tier is value, how block costs are derived, and the down-only rule that governs every print."
---

FU is **denominated by tier**: a T5 spool stores T5 FU, a T1 spool stores T1 FU. One unit of Tier N FU is worth several units of Tier N-1 (the conversion ratio is set in config). Tier is value, not just capacity, and every spool T1–T8 holds a uniform 100,000 FU.

## Where costs come from

Each block's FU cost and tier are derived from its recipe graph: crafting, plus optionally smelting and stonecutting. A diamond block costs what its diamonds are worth; stone costs almost nothing. A block's material tier sets the **minimum** machine and spool tier needed to print it.

## Two different rules

The whole economy runs on two rules, and everything else follows from them:

- **Winding** (making FU) is **exact-tier**. A material winds only into a spool of its own tier. Covered in [Filament Units](/guide/filament-units/).
- **Spending** (printing) is **down-only**. A spool pays any cost *at or below* its tier, never above.

### What that gets you

Put those together and matter becomes **interchangeable within a tier**. Sculk and diamond are both T5, so a T5 spool wound from sculk prints diamonds. Copper and gold are both T2, so your useless copper pile prints gold. Nothing special implements this; it is just what the two rules mean.

Down-only spending extends it in one direction: a high-tier spool also pays for anything beneath it, so a T6 spool can print gold, redstone, and stone all day.

| Wind this | Tier | Print this |
|---|---|---|
| 3 copper ingots | T2 | 2 gold ingots |
| 4 end stone | T2 | 1 iron ingot |
| 5 redstone | T3 | 1 glowstone |
| 5 blaze rods | T4 | 4 emeralds |
| 10 sculk | T5 | 3 diamonds |
| 1 spare elytra | T6 | 4 netherite ingots |

Ratios are at exact break-even, which needs a Tier 4+ machine; see [the markup](#the-markup) below. Doing this in practice is [Item Mode](/guide/item-mode/).

### What it stops

The same two rules are the anti-exploit gate. Converting *upward* is impossible: low-tier filament contributes nothing toward a higher-tier cost, so a T1 spool can never print netherite no matter how much cobblestone you feed it. A cobblestone farm stays a cobblestone farm.

## The markup

Lower-tier machines pay a higher FU multiplier per block, and printing always costs a little more than the matter is worth. Stack **4 Efficiency modules** to reach exact break-even (1:1), which only a **Tier 4+** machine has the slots for. You can never print matter for *less* than it's worth. See [upgrades](/guide/upgrades/) for module slots.

## Printing food

Food has FU values too, so a printer in [Item Mode](/guide/item-mode/) can print meals: bread is cheap, cooked and golden food cost more. But food **can't be wound back** into FU, so printing food is a one-way sink, not a loop.

## When a mod makes a trophy farmable

Nether stars and dragon eggs are priced as one-off trophies: 1,500 and 10,000 Filament Units, at the top of the tier ladder. That only holds because you can't farm them.

**Mystical Agradditions** adds crops for both, so when it's installed MC3DPrint stops accepting them at the winder. A Tier 7 spool prints everything at Tier 7 and below, and a renewable Tier 7 payout would make the whole ladder free. Nothing else changes: both stay craftable, usable, and placeable, and the nether star still prints. Install that mod and you get an infinite supply of nether stars for beacons, just not an infinite supply of filament.

This is the only case where another mod's presence changes what MC3DPrint accepts. Without Mystical Agradditions, both wind exactly as before.
