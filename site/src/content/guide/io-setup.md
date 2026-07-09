---
title: "Power, filament & I/O"
category: "Basics"
order: 2
summary: "Wire up a printer's faces for discs, filament spools and RF, then arm it and start a print."
---

A printer has dedicated faces for everything it needs. Get the I/O right and hoppers or pipes can feed it hands-free.

## The Matter Calculator

With a disc loaded, the GUI answers "can I afford this?" before you commit: the cost line shows total Filament Units and estimated time, and hovering the FU gauge breaks the job down — FU needed per tier vs. what's on hand (docked spools plus the rack network), total RF, and the ETA at your current upgrade loadout. If a tier can't be covered the readout turns red and names the missing tier. Predictions track Efficiency modules and a slotted Overdrive resin exactly — what it quotes is what the job consumes.

While a job runs, **Start becomes Cancel**. Cancelling is always safe: placed blocks and spent Filament Units stand (nothing is rolled back), the disc stays loaded, and restarting runs as a repair — blocks that already match re-cover at zero cost, so a cancelled-and-restarted print never costs more than printing it once. There's no separate refund step because the printer pays per block as it places, never up front.

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
