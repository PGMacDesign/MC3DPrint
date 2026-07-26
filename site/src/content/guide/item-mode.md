---
title: "Item Mode & converting materials"
category: "Basics"
order: 6
summary: "Drop any item into a printer to make copies of it, and use the tier system to turn one material into another."
---

A printer has one **Smart Print Slot**, and what you put in it decides the mode. Load a written Blueprint Disc and you get **Blueprint Mode**, which builds a structure. Drop in a plain item and you get **Item Mode**, which makes copies of that item.

Item Mode is what turns MC3DPrint from a build-reprinting mod into a material converter.

## Using Item Mode

1. Dock a filament spool on the printer's side (`Shift+Right Click`), and give the machine RF.
2. Put any valued item in the Smart Print Slot. This is the **template**.
3. Press **Start**, or flip **Auto** on.

Copies stack up in the output slot below. A manual Start prints exactly one; with Auto on, the printer keeps going until it runs out of filament, power, or output room.

The template is **never consumed**. It sits in the slot as a pattern and you can pull it back out whenever you like, so one diamond is enough to print diamonds forever, as long as you can pay for them.

> Copies come out as **clean base items**. Enchantments, stored contents, and other value-bearing data are stripped, so printing a copy of an enchanted book or a full shulker box will never clone what's inside it.

## What it costs

A copy costs that item's own FU value, drawn from a docked spool at that item's tier, plus RF per tick while it runs. The GUI shows the per-copy cost before you start.

Because printing carries a markup, a copy costs slightly more filament than the item is worth until you fit **four Efficiency modules**, which brings it to exact 1:1 break-even. You can never print an item for *less* than its value.

## Converting one material into another

Here is where it gets interesting. Filament is denominated by **tier**, not by item. Winding is exact-tier and spending is down-only, which together mean one thing:

**Within a tier, matter is interchangeable.**

Wind sculk and you get Tier 5 filament. Diamond is also Tier 5. So a Tier 5 spool wound from sculk will happily print diamonds. Nothing special is required; it falls straight out of the two rules.

Some examples, all at exact break-even:

| Wind this | Tier | Print this |
|---|---|---|
| 3 copper ingots | T2 | 2 gold ingots |
| 4 end stone | T2 | 1 iron ingot |
| 5 redstone | T3 | 1 glowstone |
| 5 blaze rods | T4 | 4 emeralds |
| 10 sculk | T5 | 3 diamonds |
| 1 spare elytra | T6 | 4 netherite ingots |

Spending is down-only, so a high-tier spool also pays for anything beneath it: a Tier 6 spool can print gold, iron, redstone, and stone all day. What it can't do is work upward. No amount of Tier 1 filament will ever add up to a diamond, which is what keeps a cobblestone farm from quietly becoming a netherite farm.

That asymmetry is the whole economy. Converting sideways is free of charge (beyond the markup); converting upward is impossible.

## What Item Mode refuses

Not everything copies. The status line tells you which rule you hit:

- **Not Printable** covers items with no FU value (in strict mode, the default), filament spools (they store filament, so copying one would launder it), restricted trophies like mob heads, and **wind-only** treasure such as saddles, name tags, wither skulls, and the dragon egg. Wind-only items still recycle into filament at the Winder; they just never come back out of a printer.
- **Requires a Tier N Printer** means the item is fine but the machine is too small. Tier 3 string won't print in a Tier 2 printer. Use a bigger machine.
- **No Filament** means you have filament, but not at a tier that can pay. Remember it only spends downward.

A few items go the other way and are **print-only**, valued and printable but barred from winding, like bones and planks. That stops a trivially automated farm from laundering its output into filament.

## Where to go next

- [Filament Units & the Winder](/guide/filament-units/) for how winding works and which items are wind-only.
- [The FU economy](/guide/fu-economy/) for tier values, cost derivation, and the down-only rule in full.
- [Upgrade modules](/guide/upgrades/) for the Efficiency modules that get you to break-even.
