---
title: "Filament Units & the Winder"
category: "Basics"
order: 4
summary: "Turn raw materials into Filament Units on the Winder, then dock the spool to fuel a printer."
---

Every block a printer places is paid for in **Filament Units (FU)**. To make FU, you feed raw materials into a **Filament Winder** to wind them onto a **Filament Spool**, then `Shift+Right Click` the spool onto a printer's side.

A material's FU value is derived from its crafting and smelting recipe graph — stone is nearly free, diamond blocks cost what their diamonds are worth.

## Using the Winder

Put a material in the input slot and an empty (or partial) spool in the spool slot. The Winder consumes the material and winds its FU onto the spool. One universal Winder handles every tier — the **spool's** tier, not the Winder, decides what it accepts. It's an early-game craft: an iron frame around string, a stick, and a smooth-stone base.

## Exact-tier winding

A material only winds into a spool of its **exact** tier:

- Netherite (T6) needs a T6 spool.
- Cobblestone (T1) needs a T1 spool.

A mismatch shows `Requires Tier X Spool` and leaves both items untouched. Items with no FU value show `Can't be converted`.

> Every spool holds 100,000 FU regardless of tier — tier gates which materials it accepts, not how much it stores.

## Recycling and wind-only items

Winding is also a sink for items you can't, or don't want to, print. Many treasures wind for a Filament Unit payout even when the printer won't reproduce them:

- **Wind-only treasure** — saddles, name tags, wither skulls, and the dragon egg recycle into FU but never print back. The dragon egg is a one-time 10,000-unit windfall at Tier 7, since you only ever get one.
- **Mob heads** — creeper, zombie, skeleton, and piglin heads wind at Tier 4 (they still print only as decor from official blueprints, never as loose items).
- **Bamboo** winds at its base tier now. Bones, by contrast, are printable but held back from winding (like other trivially-farmable outputs), so a skeleton farm can't launder them into filament.

## Reaching Tier 6

The T6 spool is the gate to printing netherite, and it costs a single netherite ingot to craft. Once you have one, rare End and loot items give a netherite route that isn't debris mining: **elytra**, **dragon heads**, **enchanted golden apples**, and (on 1.21+) **heavy cores** all wind into a T6 spool. Because winding is equal-value, this converts hard-won rares into netherite without ever converting cheap materials up a tier.

The next step up, the T7 spool, now costs a single **nether star** (down from four), so beating one wither opens Tier 7. Its main winding input is the nether star itself, plus the dragon egg's one-time windfall.

The **T8 spool** tops the ladder and is the one recipe that leaves vanilla behind: **4 Awakened Draconium ingots** and **4 Extrudium Crystals** around a T7 spool. Awakened draconium is also its winding input, at 500 FU a piece, and it is wind-only: it recycles into filament but a printer will never reproduce it, which is what keeps Fusion Crafting from being printed around.

## Finding every item of a tier

MC3DPrint adds a line like `MC3DP: Tier-5 (50 FU)` to the tooltip of every item that has an FU value. Items that can't be freely printed also get a status line, so you can tell at a glance: **Wind-only (can't print)** for recycle-only treasure, **Trophy (prints in official builds only)** for mob heads, and **Print-only (can't wind)** for farmable outputs like bones. The JEI "3D Printing" panel shows the same status, color-coded. JEI indexes tooltips, so you can search that line to list a whole tier at once. Type this into JEI's search box:

```text
tier-5
```

That's the whole trick: no prefix, no quotes. Swap the number for any tier from 1 to 8.

The hyphen matters. JEI splits tooltips on spaces and matches each search word as a fragment, so `tier 5` is read as two separate words and also returns every item whose FU *cost* contains a 5 (Tier 3 items costing 50 FU, Tier 1 items costing 15 FU, and so on). Writing the tier as one word keeps the match exact.

If you have turned JEI's tooltip search off, the tooltip prefix still reaches it: `$tier-5` on 1.21+, or `#tier-5` on 1.20.1 (JEI swapped the tooltip and tag prefixes between those versions).

Combine it with the mod filter to narrow further: `@mc3dprint tier-3` lists only this mod's own Tier 3 items.

## Automation

With **Applied Energistics 2** installed, the **Filament Converter** automates winding straight from an ME network and keeps your docked spools topped up.

For the spending rules — how a spool pays for prints — see [the FU economy](/guide/fu-economy/).
