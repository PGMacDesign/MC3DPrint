---
title: "Upgrade modules"
category: "Machines"
order: 2
summary: "Five module types tune a printer's speed, FU cost, RF cost, buffer, and redstone status output, with a cap of four per type (one for Redstone)."
---

Every printer has upgrade slots that grow with its tier: a Tier 3 printer has 3, topping out at 8. To install a module, `Shift+Right Click` it onto the machine, or drop it into an upgrade slot in the GUI.

## The five types

- **Speed**: faster printing, ×0.8 print time per module.
- **Efficiency**: less FU per print; trims the tier's print markup down toward break-even.
- **RF Efficiency**: ×0.85 RF cost per module.
- **Buffer**: ×1.5 RF buffer per module.
- **Redstone**: emits a full-strength redstone signal while the machine is working. Capped at 1 per machine.

You can install at most **4 of any one type** per machine. The Redstone Module is the exception: **1 per machine**, and a second copy is refused even when the machine has slots free.

## Redstone output

The Redstone Module is the only module with no numeric effect. The other four scale a rate; this one is a status output, so it changes nothing about how the print itself runs.

While it is installed, the machine emits a **full-strength (15) weak redstone signal from all six faces whenever it is actively working**, meaning printing or deconstructing, and no signal at all otherwise.

A **paused** machine reads 0: out of power, output full, obstructed, out of filament, in a zone conflict, waiting, or idle. The signal answers one question, "is this machine busy right now", which is what makes it usable as a stall alarm: wire the inverted signal into a lamp and the lamp lights the moment a print stalls.

On a multiblock fabricator, **only the controller emits**, not the casings, so wire it from above or below the controller (the horizontal faces are casing on a formed fabricator). This is the same rule the redstone START input already follows.

It **will not restart itself**. Printers start on a redstone rising edge, so a machine that also emits redstone would otherwise be a loop waiting to happen. While the machine is emitting, an incoming rising edge never queues a start, so wiring its own output back into itself (or just running dust beside it) cannot cause an infinite reprint. The tradeoff worth knowing: on a machine with this module, a redstone pulse that arrives mid-print is ignored rather than queued as a re-run.

### Module or comparator: which do I want?

Every printer already answers a [comparator](/guide/io-setup/) with its job progress, for free. So why craft this module? Because the two deliberately disagree, and they disagree exactly where it matters:

| Machine is | Module signal | Comparator |
|---|---|---|
| Idle | 0 | 0 |
| Printing | 15 | 1 to 15 |
| **Stalled** | **0** | **stays non-zero** |

The comparator answers "is a job loaded, and how far in is it". The module answers "is this machine actually moving right now". A machine that runs out of power, fills its output or gets obstructed still has work loaded, so the comparator stays above 0, while the module's signal drops to 0.

That makes the choice easy: use the **comparator** for a progress bar or a "job finished" trigger, and the **module** when you want to detect a stall. Inverted into a lamp, the module lights the moment a print stops moving, which is something the comparator cannot tell you.

One consequence worth expecting rather than reporting as a bug: if you print on `Auto` into a hopper that drains slower than the machine fills the output slot, the module's signal will **blink**. That is accurate, not a glitch. The machine really is alternating between printing and being paused with a full output, and the signal is faithfully reporting it. Give the output somewhere faster to drain and it goes solid.

## How stacking works

Speed, RF Efficiency, and Buffer stack **multiplicatively**: two Speed modules give 0.8 × 0.8 = 64% print time.

Efficiency is **linear**: each module shaves a quarter of the tier's print markup, so 4 modules reach exact break-even.

> Only Tier 4+ machines have 4 slots, so only they can hit 1:1, and a Tier 4 must spend all four slots on Efficiency to get there.

All these rates live in the config for pack makers who want to retune them. For the bigger picture on why printing carries a markup at all, see [the FU economy](/guide/fu-economy/).
