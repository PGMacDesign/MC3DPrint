---
title: "Power, filament & I/O"
category: "Basics"
order: 2
summary: "Wire up a printer's faces for discs, filament spools and RF, then arm it and start a print."
---

A printer has dedicated faces for everything it needs. Get the I/O right and hoppers or pipes can feed it hands-free.

## Faces & I/O

- **Top**: input: templates and discs go in here.
- **Bottom**: output: finished prints and ejected discs come out here.
- **Sides**: filament spools only.

Hoppers work from Tier 1, and any pipe mod that respects inventory faces will route to the correct side.

## Power & filament

RF cables connect on **any face**. Spools dock on the sides: `Shift+Right Click` the printer with a spool in hand, or `Shift+Click` it into the spool docks in the GUI. Docked spools render on the machine and visibly shrink as their [Filament Units](/guide/filament-units/) deplete.

No RF mod installed? The **Simple Generator** burns furnace fuel into a trickle of RF (right-click or hopper it fuel), so MC3DPrint runs standalone. For serious throughput, bring your own power.

## Starting a print

Loading a disc arms the printer and shows `Ready`. From there:

- Press `Start` (or pulse redstone) to begin.
- Or flip `Auto` on to start the moment a disc lands.

Use the X/Y/Z offsets in the GUI to move the build area. The default is centered directly above the printer.

## Reading progress with a comparator

Put a **comparator** against any printer or fabricator and it reads out the job's progress, exactly like one against a [Filament Rack](/guide/filament-rack/) reads its fill level. No upgrade needed: reading a machine is always free.

- **0** means nothing is loaded and nothing is to do: the machine is empty, or it is holding something it has not been told to start.
- **1 to 15** means there is work loaded, climbing to 15 as the job places its final block.

That split is the whole rule, and it holds in all three modes (blueprint, item and deconstruct): **0 is idle, anything above 0 means work is loaded**. A **stalled** machine still counts as loaded, so it keeps a non-zero reading rather than dropping to 0. On a fabricator you read the **controller**, not the casings.

Because a stall keeps the comparator non-zero, a comparator alone cannot tell you the machine has *stopped moving*. That is the other half of the pair: the [Redstone Module](/guide/upgrades/) emits full power only while the machine is genuinely working and drops to 0 the moment it stalls.

If a print won't begin, check [the FAQ](/faq).
