<!--
  CurseForge store copy for MC3DPrint.
  - Paste the DESCRIPTION block below into the CurseForge "Description" field (set the editor to Markdown).
  - Use the SUMMARY line for the short "Summary" / tagline field.
  - Set the CurseForge License field to MIT to match.
  - Don't add direct jar/download links to the description (off-site download links are the #1 first-review rejection).
-->

# Summary (short tagline field)

Wind any material into filament, print any other material back, and scan and reprint entire builds.

---

# Description (paste into the Markdown editor)

# MC3DPrint: Wind Anything, Print Anything

**MC3DPrint** is a tech mod built around a material economy. Wind any item into
tiered **Filament Units (FU)** on the Filament Winder, then spend that filament to
print anything else at the same tier or below.

Wind your surplus copper, print gold. Wind sculk, print diamonds. Wind a spare
elytra, print netherite ingots. A spool stores a *tier*, not a *material*, so
everything within a tier is interchangeable:

| Wind this | Tier | Print this |
|---|---|---|
| 3 copper ingots | T2 | 2 gold ingots |
| 4 end stone | T2 | 1 iron ingot |
| 5 redstone | T3 | 1 glowstone |
| 5 blaze rods | T4 | 4 emeralds |
| 10 sculk | T5 | 3 diamonds |
| 1 spare elytra | T6 | 4 netherite ingots |

Converting *upward* is impossible by design: low-tier filament contributes nothing
toward a higher-tier cost, so a cobblestone farm can never become a netherite farm.
Printing also carries a markup until you fit max Efficiency upgrades, which means
printing can never become a duplication exploit.

The same filament prints **structures**, too. Scan any build with the **Structure
Scanner**, save it to a **Blueprint Disc**, and print it back anywhere with a tiered
printer or multiblock fabricator, block states and connections intact. No creative
mode, no commands: it brings the convenience of schematic tools into legitimate
survival play.

## Features

- **Convert one material into another**: wind anything into FU, print anything else at that tier or below.
- **Item Mode**: drop any item into a printer to run off copies. The template is never consumed.
- **Scan & print** any structure, with block states, orientation, and connections preserved.
- **Eight tiers**: desktop single-block printers (T1–T4) scaling up to N×N multiblock fabricators (T5–T8).
- **A real economy**: Filament Units are wound from items at an exact-tier rate; printing is lossy by design and only reaches break-even with max efficiency upgrades.
- **130+ curated builds** included: houses, towers, monuments, gardens, and working automatic farms (sugar cane, kelp, bamboo, pumpkin/melon, cactus, iron, mob-XP, and more).
- **Blueprint Discs in world loot**: curated builds can drop as treasure.
- **Resins**: one-shot print modifiers (grow crops on print, stock chests, bank XP, cheaper prints, and more).
- **Upgrade modules**: tune print Speed, FU Efficiency, RF Efficiency, and RF buffer, or add a Redstone Module so the machine emits a signal while it is working (invert it for a stall alarm).
- **Blueprint Repository**: a block that catalogues your discs and re-burns copies on demand.
- **In-game guide** via Patchouli, plus JEI recipe/cost integration.

## How it works

1. **Wind** any valued item into Filament Units with the Filament Winder. FU is denominated at that item's own material tier.
2. **Pick a template**: drop an item into the printer's Smart Print Slot to print copies of it, or load a Blueprint Disc to build a whole structure.
3. **Print** anywhere: supply RF and a docked spool, and it prints. A spool pays for anything at its tier or below.

## Mod compatibility

MC3DPrint adds optional, soft-dependency support so other mods' items can be wound
into filament and printed, including **AE2, Thermal Series, Tinkers' Construct,
Mekanism, Create, Botania, EnderIO, Immersive Engineering**, and **Draconic
Evolution** (which also powers the top-tier fabricator). These hooks are completely
invisible when a mod isn't installed: no crashes, warnings, or config.

## Requirements

- **Minecraft** 1.20.1 (Java Edition) with **Forge** 47.4.10 or newer, **or**
- **Minecraft** 1.21.1 / 1.21.8 / 1.21.9 / 1.21.10 / 1.21.11 / 26.1 / 26.2 with **NeoForge**
- Optional: **Patchouli** (in-game guide book), **JEI** (recipe/cost lookup)

## Links

- **Website & guide:** https://mc3dprint.dev
- **Source code (open source, MIT):** https://github.com/PGMacDesign/MC3DPrint

MC3DPrint is free and open source under the MIT license. Created by PGMacDesign.
Community build submissions are welcome through the website. No account or coding required.
