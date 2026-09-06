---
title: "Mod integrations"
category: "Integrations"
order: 1
summary: "Every mod MC3DPrint knows about is a soft dependency: AE2 adds the Terminal, Draconic Evolution unlocks Tier 8, and ten mods contribute Filament Unit values."
---

MC3DPrint has **no required dependencies**. Every mod on this page is a soft dependency: if you have it, MC3DPrint knows about it; if you do not, nothing happens at all.

"Nothing" means nothing. No crash, no missing-item warnings, no stray config entries, and nothing extra in the creative tab. A mod that is absent is invisible.

## Values come for free

Most modded items get a Filament Unit value without anyone writing one. MC3DPrint reads ordinary crafting and smelting recipes and derives the value from the ingredients, so a mod's whole tree usually prices itself from a handful of raw materials.

What it **cannot** read is a custom machine: a Smeltery alloy, an Inscriber press, a reagent chamber. Those are dead ends, so MC3DPrint pins them by hand and lets everything downstream derive.

Pinned values follow the same [abundance rule](/guide/fu-economy/) as everything else: a material cannot sit at a tier whose spool could print something rarer than the material itself. That is why a farmable alloy lands near iron and a Nether-mined one lands near blaze rods, regardless of what its own mod charges for it.

## Two mods change what the machines can do

### Applied Energistics 2

AE2 adds the [MC3DPrint Terminal](/guide/mc3dprint-terminal/), a cable part that lists everything your networked printers and formed Fabricators can make and orders it paid in Filament Units instead of ingredients. It dispatches rather than crafts: an order queues a job on a real machine, which does the work at its normal speed, RF draw and filament cost.

The terminal uses a channel, like AE2's own terminals. Finished items go into ME storage, and orders belong to whoever placed them.

### Draconic Evolution

Draconic Evolution unlocks the **Tier 8 Fabricator**, the largest machine in the mod at 9×9. Its four base corners must be Awakened Draconium blocks, so without Draconic Evolution installed Tier 8 cannot form and Tier 7 is the ceiling. The Tier 8 spool and fabricator both take awakened draconium to craft.

## Material values

Ten mods contribute pinned values: the items MC3DPrint cannot work out on its own, because a custom machine makes them or they are mined and have no recipe to read.

| Mod | Tier band | What it adds |
|---|---|---|
| Applied Energistics 2 | 2-5 | The MC3DPrint Terminal, plus certus, the backbone and Inscriber processors |
| Botania | 3-6 | Mana infusion and ritual altar materials |
| Create | 2-3 | Zinc, andesite alloy, brass, rose quartz |
| Draconic Evolution | 7-8 | The Tier 8 Fabricator, plus draconium and awakened draconium |
| Ender IO | 3-6 | The Alloy Smelter ladder |
| Immersive Engineering | 2-3 | Mined metals and the Arc Furnace line |
| Mekanism | 2-6, 8 | Mined ores, alloys, and the antimatter pellet at the top |
| Mystical Agriculture | 1-5 | The essence ladder, with Agradditions' ores alongside it |
| Thermal Series | 2, 4-5 | Mined metals and the Induction Smelter alloys |
| Tinkers' Construct | 3-6 | Smeltery alloys, plus cobalt |

Cross-mod materials are deliberately kept level. Steel is steel whether Tinkers', Ender IO or Immersive Engineering made it, so the same material cannot be arbitraged from one mod into another. Anything left unvalued is refused by strict mode rather than printed for free.

## Tooling

**Just Enough Items** gets two extra recipe categories: what any item costs to print (its FU price and the machine tier it needs), and what is inside a Blueprint Disc so you can check a build before printing it.

**Patchouli** provides the in-game Fabricator's Handbook, craftable from an Extrudium Crystal and a Book, or handed out with `/mc3dprint guide`.

Both are optional. Without JEI you lose the lookup, not the mechanic; without Patchouli there is no book, and nothing else changes.
