---
title: "Power, filament & I/O"
category: "Basics"
order: 2
summary: "Wire up a printer's faces for discs, filament spools and RF, then arm it and start a print."
---

A printer has dedicated faces for everything it needs. Get the I/O right and hoppers or pipes can feed it hands-free.

## Faces & I/O

- **Top** — input: templates and discs go in here.
- **Bottom** — output: finished prints and ejected discs come out here.
- **Sides** — spool docks, loaded by hand or from the GUI (not exposed to item automation).

Hoppers work from Tier 1, and any pipe mod that respects inventory faces will route discs into the top and pull prints out of the bottom. Only those two faces accept automated item I/O — the side spool docks are filled manually.

## Power & filament

RF cables connect on **any face**. Spools dock on the sides: `Shift+Right Click` the printer with a spool in hand, or `Shift+Click` it into the spool docks in the GUI. Docked spools render on the machine and visibly shrink as their [Filament Units](/guide/filament-units/) deplete.

No RF mod installed? The **Simple Generator** burns furnace fuel into a trickle of RF — right-click or hopper it fuel — so MC3DPrint runs standalone. For serious throughput, bring your own power.

## Starting a print

Loading a disc arms the printer and shows `Ready`. From there:

- Press `Start` (or pulse redstone) to begin.
- Or flip `Auto` on to start the moment a disc lands.

Use the X/Y/Z offsets in the GUI to move the build area — the default is centered directly above the printer.

If a print won't begin, check [the FAQ](/faq).
