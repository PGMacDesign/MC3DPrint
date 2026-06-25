---
title: "Upgrade modules"
category: "Machines"
order: 2
summary: "Four module types tune a printer's speed, FU cost, RF cost, and buffer, with a cap of four per type."
---

Every printer has upgrade slots that grow with its tier — a Tier 3 printer has 3, topping out at 8 on a Tier 8 fabricator. To install a module, `Shift+Right Click` it onto the machine, or drop it into an upgrade slot in the GUI.

## The four types

- **Speed** — faster printing, ×0.8 print time per module.
- **Efficiency** — less FU per print; trims the tier's print markup down toward break-even.
- **RF Efficiency** — ×0.85 RF cost per module.
- **Buffer** — ×1.5 RF buffer per module.

You can install at most **4 of any one type** per machine.

## How stacking works

Speed, RF Efficiency, and Buffer stack **multiplicatively** — two Speed modules give 0.8 × 0.8 = 64% print time.

Efficiency is **linear**: each module shaves a quarter of the tier's print markup, so 4 modules reach exact break-even.

> Only Tier 4+ machines have 4 slots, so only they can hit 1:1 — and a Tier 4 must spend all four slots on Efficiency to get there.

All these rates live in the config for pack makers who want to retune them. For the bigger picture on why printing carries a markup at all, see [the FU economy](/guide/fu-economy/).
