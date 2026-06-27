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

## Automation

With **Applied Energistics 2** installed, the **Filament Converter** automates winding straight from an ME network and keeps your docked spools topped up.

For the spending rules — how a spool pays for prints — see [the FU economy](/guide/fu-economy/).
