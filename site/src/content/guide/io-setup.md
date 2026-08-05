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

- **0** means nothing is running, whether the machine is empty, armed and waiting, or paused.
- **1 to 15** tracks the job, climbing to 15 as it places its final block.

That split matters: 0 is reserved for "not running" so a comparator can tell an idle machine from one that has only just started. It works in all three modes (blueprint, item and deconstruct), and on a fabricator you read the **controller**, not the casings.

If you want a plain busy signal instead of a progress bar, the [Redstone Module](/guide/upgrades/) makes the machine emit full power while it works and nothing while it is idle or stalled.

If a print won't begin, check [the FAQ](/faq).
