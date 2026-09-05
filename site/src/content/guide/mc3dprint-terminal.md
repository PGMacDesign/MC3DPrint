---
title: "The MC3DPrint Terminal"
category: "Machines"
order: 9
summary: "An Applied Energistics 2 cable part that lists everything your printers can make and orders it, paid in Filament Units instead of ingredients."
---

The MC3DPrint Terminal is a catalog you order from. It attaches to an ME cable like any AE2 terminal, lists what your network's printers and formed Fabricators (T5-T8) can produce, and takes orders paid in Filament Units.

It requires **Applied Energistics 2**. Without AE2 installed the terminal does not exist, and nothing else in the mod changes.

The terminal **uses a channel**, exactly like AE2's own terminals, so on a busy network it wants a dense cable or a controller. With no channel the terminal goes **offline**: an open screen closes, queued orders stop being handed to machines, and an order already running holds where it is, because its output has nowhere to go until the network is back. Nothing is charged while it holds, and everything resumes when the channel returns.

## It dispatches, it does not craft

The terminal never makes anything itself. Ordering queues a job against a real printer or fabricator, which does the work at its normal speed, its normal RF draw and its normal filament cost.

That matters more than it sounds. Everything that already governs printing keeps governing it: print markup, Efficiency modules, exact-tier spending, the wind-only and restricted lists, strict mode. An order placed at a terminal costs exactly what walking to the machine and printing it by hand would cost, because it is the same code doing the work.

It also means you can watch a fabricator chew through a stack of sixty-four, which an instant crafting-grid terminal would have taken away.

## The catalog

Every item with a Filament Unit value appears, sorted by tier and then by cost.

Items you cannot order stay in the list, greyed, with the reason in the tooltip:

- **Needs a higher-tier machine**, when nothing on the network is big enough yet
- **Wind-only**, for things that recycle for filament but never print
- **Restricted trophy**, for items only ever placed by an official blueprint
- **Not enough filament at this tier**

Nothing is hidden. A greyed row tells you what to go build, and the catalog does not reshuffle under your cursor as your filament rises and falls.

Search matches both the display name and the registry id, so typing a mod id narrows things down when two mods ship an item with the same name.

## The catalog mirrors your network

The terminal lists **what your ME network is already holding**, not the whole item registry. It is a way to reprint what you own, so if the network has no cobblestone in it, cobblestone is not on offer.

Two things follow, and the second one matters more:

- Stock decides what gets **listed**. It never decides what can be **ordered**.
- Every print rule still applies on top. A wind-only item, a restricted trophy or something needing a bigger machine stays greyed and refused even when a drive full of it is plugged in. Blacklisted means blacklisted, whether or not you own one.

The server re-checks stock when the order arrives, not just when the catalog is drawn, so a hand-crafted packet cannot order something the network never had.

Stock is re-read about once a second rather than every tick, so a very large network does not pay for a full contents walk sixty times a second.

## Connecting your printers

A printer or Fabricator joins the terminal's network by **touching it**. Put the machine directly against any AE2 cable or device that belongs to the same network, on any of its six sides, and it is connected. That is the whole rule.

What you do **not** need:

- No MC3D Cable. That carries RF and Filament Units between MC3DPrint machines and has nothing to do with the ME network.
- No ME Interface, storage bus or import bus. The terminal finds machines itself.
- No particular distance. A machine is either touching the network or it is not, so "too far away" is never the reason one is missing.

On a Fabricator it is the **controller** that has to touch the network, not the casing.

The header line counts what it found, for example `4 machines, best T4`. If a machine you built is not in that count, it is not touching the network.

## The tier rail

Down the left edge, one row per tier, showing the filament that could pay a cost at that tier. Hover any row for exact figures.

Each row counts its own tier **and every tier above it**, because spending is down-only: a Tier 3 spool will pay a Tier 1 cost, but a Tier 1 spool will never pay a Tier 3 one. One combined total would be the number that lies to you, since most of it might sit below the tier you are looking at.

A tier is **orderable** when your best machine's tier is at least that tier. A Tier 4 printer prints Tier 1 through Tier 4 and nothing above, no matter how much filament you have. So `Needs a T6 machine; your best is T4` means build or connect a bigger machine: it is not a filament problem and not a wiring problem.

## Orders

Click an item to order one, shift-click for a stack. Orders appear at the bottom with their progress.

Click one of your own orders to cancel it. Orders belong to whoever placed them, so you cannot cancel someone else's.

An order binds to one machine and that machine runs one order at a time, so two orders never spend from the same spools at once. If something interrupts it, the order **holds** rather than failing:

- Out of filament: holds until filament returns, then resumes
- Out of power: holds
- Nowhere to put the output: holds, and importantly spends nothing while waiting
- The machine leaves the network: the order is released and looks for another machine

Filament is spent if and only if an item is delivered. There is no state in which the terminal charges you for something you did not receive.

## Taking a machine back

Load a blueprint disc or drop an item in a printer's template slot and that machine is yours again immediately. Any terminal order it was running is handed back to the queue and looks for somewhere else to run. A terminal order never interrupts something you started by hand.

## Recipe

An AE2 **Crafting Terminal** surrounded by eight **Extrudium Crystals**.
