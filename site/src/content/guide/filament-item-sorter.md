---
title: "The Filament Tier Item Sorter"
category: "Machines"
order: 7
summary: "A passive, unpowered logistics block that routes each item to the Filament Winder holding a spool of that item's material tier."
---

Winding is exact-tier: cobblestone only winds into a T1 spool, netherite only into a T6. Feeding eight winders from one chest normally means hand-building a vanilla sort line per tier.

The Filament Tier Item Sorter does it for you. Drop items into its 9-slot pool — by hopper, pipe, or hand — and each one is delivered to the winder holding a spool of that item's tier.

## Finding winders

The sorter checks all six of its faces. A winder touching it is found directly, with no cable at all.

To reach more than six winders, run [MC3D Cable](/guide/mc3d-cable/): the sorter reads every winder across the connected network. The cable only reports *where the winders are* — items are never stored in or transported through a cable. They go straight from the pool into the winder's own input slot.

> Direct-touch and cable-reach are the same code path, so an unpowered, cable-less build behaves exactly like a networked one.

A newly wired winder is picked up within about five seconds (the cable's topology refresh). Spool swaps and fill levels are read live, so those are seen instantly.

## What it refuses

Two failure modes are permanent, so they are turned away at the door and never enter the pool at all:

- Items with **no Filament Unit value**
- Items on the **winder blacklist** (sticks and similar laundering-prone items)

This is deliberate. A winder's input slot accepts anything, so a stack of sticks would sit in it and jam that winder forever. The sorter refuses to pass them along — including when you try to shift-click them in by hand.

## What it holds

Everything else is accepted and retried:

- No winder of that tier on the network yet
- The matching winder's input slot holds a different item
- The matching winder's spool has no room for the yield

Items are **never voided**. When the pool fills, your hoppers back up — that visible backup is the signal. The GUI shows a computed per-tier readout (how many items are waiting and how many winders it can see for that tier), so you can tell at a glance which tier is stuck.

The pool is a one-way funnel: a hopper can feed it, but nothing can pull items back out. Open the GUI if you need to retrieve something by hand.

## Sharing the load

Two winders on the same tier both get fed. Winding is one item per second, so parallel same-tier winders are a real throughput strategy — the sorter alternates between them round-robin rather than filling the first one's 64-slot queue while the second sits idle.

Any winder that would stall is skipped without wasting its turn: a full spool or an occupied input slot just means the item goes to the next candidate.

## Speed and power

It routes up to **4 items per tick** by default, configurable from 1 to 64 in `mc3dprint-common.toml` (`[sorter] maxRoutedPerTick`). One stack is the ceiling, since a winder's single input slot takes at most a stack.

It costs **no RF**. The block runs completely unpowered — the item already pays its 200 RF at the winder, and taxing the same item twice for one logical operation isn't worth it. MC3D Cable still visually attaches to it so your runs look right.
