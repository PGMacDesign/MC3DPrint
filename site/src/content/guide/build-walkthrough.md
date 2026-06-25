---
title: "Multiblock build walkthrough"
category: "Multiblocks"
order: 2
summary: "Step-by-step layouts for forming every Fabricator tier, plus how to read the forming error messages."
---

Every [Fabricator](/guide/fabricators/) is built the same way. Lay a flat N×N square of `Printer Casing` on one level, place the controller in the center cell, and `Right Click` the controller to form. All base blocks sit on the controller's own Y level.

## Layouts by tier

1. **T5** — 3×3: 4 Printer Casing + 4 corner Diamond Blocks + the T5 Fabricator in the middle. The smallest Fabricator.
2. **T6** — 5×5: 24 Printer Casing + the T6 Fabricator in the center cell.
3. **T7** — 7×7: 48 Printer Casing + the T7 Fabricator. Clear a big flat platform first.
4. **T8** — 9×9: 76 Printer Casing + 4 corner Awakened Draconium blocks + the T8 Fabricator. Requires Draconic Evolution.

## If it won't form

Forming checks every base cell and names the exact coordinates that failed:

- `Wrong/missing block` — a base cell isn't Printer Casing.
- `T5 corner` — a corner isn't a Diamond Block.
- `T8 corner` — a corner isn't Awakened Draconium.

Fix the named position and `Right Click` the controller again.

> The controller must be dead-center and on the same Y as the base, or forming will never succeed no matter how clean your casing is.

See [fabricators](/guide/fabricators/) for tier sizes and crafting details.
