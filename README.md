# MC3DPrint

[![CurseForge downloads](https://img.shields.io/curseforge/dt/1587177?logo=curseforge&label=CurseForge&color=f16436)](https://www.curseforge.com/minecraft/mc-mods/mc3dprint)
[![CurseForge version](https://img.shields.io/curseforge/v/1587177?logo=curseforge&label=latest&color=f16436)](https://www.curseforge.com/minecraft/mc-mods/mc3dprint/files)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1%20%7C%201.21.1--26.2-62B47A)](https://www.minecraft.net)
[![Loader](https://img.shields.io/badge/loader-Forge%20%7C%20NeoForge-1D2731)](https://neoforged.net)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue)](LICENSE)
[![Website](https://img.shields.io/badge/website-mc3dprint.dev-5cc8ff)](https://mc3dprint.dev)

**Wind anything. Print anything.** A Minecraft tech mod with a material economy at
its center, for **Forge 1.20.1** and **NeoForge 1.21.1 through 26.2**: wind any item into tiered **Filament Units (FU)**,
then spend that filament to print anything else at the same tier or below. Wind
copper, print gold. Wind sculk, print diamonds.

The same filament also prints *structures*. Scan any build with the Structure
Scanner and reprint it anywhere, block state and connections intact, powered by FU
plus **Redstone Flux (RF)**. WorldEdit for survival, with no creative mode and no
commands.

### 🌐 [mc3dprint.dev](https://mc3dprint.dev)

The website has the guide, a browsable **blueprint gallery**, an interactive
**3D viewer**, and a **"Submit a Build"** page where anyone can contribute a
blueprint to the mod: **no GitHub account and no coding required.** Free and
open source (MIT).

---

## How it works

1. **Wind** any valued item into **Filament Units** with the **Filament Winder**.
   FU is denominated at that item's own material tier.
2. **Pick a template.** Drop an item into a printer's Smart Print Slot to print
   copies of it (**Item Mode**), or load a **Blueprint Disc** to build a whole
   structure (**Blueprint Mode**). Discs come from the **Structure Scanner**, from
   world loot, or from the 130+ curated builds the mod ships.
3. **Print** anywhere: supply RF and a docked spool, and it prints, block state and
   connections preserved.

Eight tiers run from a desktop single-block printer (Tiers 1–4) up to multiblock
**Fabricators** (Tiers 5–8, an N×N Printer Casing base + controller, formed by
right-click), ending at the Draconic-powered Tier 8.

## Turning one material into another

Filament is denominated by **tier**, not by item, and a spool doesn't remember what
you wound into it. Winding is **exact-tier** and spending is **down-only**, and
those two rules together mean matter is **interchangeable within a tier**:

| Wind this | Tier | Print this |
|---|---|---|
| 3 copper ingots | T2 | 2 gold ingots |
| 4 end stone | T2 | 1 iron ingot |
| 5 redstone | T3 | 1 glowstone |
| 5 blaze rods | T4 | 4 emeralds |
| 10 sculk | T5 | 3 diamonds |
| 1 spare elytra | T6 | 4 netherite ingots |

Ratios are at exact break-even, which takes four Efficiency modules and therefore a
**Tier 4 or higher** printer, since upgrade slots scale with tier. Below that, printing
carries a markup and costs slightly more than the matter is worth.

Converting *upward* is impossible by construction: low-tier filament contributes
nothing toward a higher-tier cost, so a cobblestone farm can never become a
netherite farm. That asymmetry is the whole anti-dupe design, and the rationale is
in [Design notes](#design-notes-economy--anti-exploit-rationale) below.

## Features

- **Convert materials**: wind anything into FU, print anything else at that tier
  or below, with copies made in Item Mode from a template that's never consumed.
- **Scan & print** any structure, with block states, orientation, and connections intact.
- **A real economy**: FU is wound from real items; printing is lossy by design and
  reaches 1:1 break-even only with max Efficiency upgrades, so it can't be a dupe exploit.
- **130+ curated builds**: houses, towers, monuments, gardens, and **working
  auto-farms** (sugar cane, kelp, bamboo, pumpkin/melon, cactus, iron, mob-XP and more).
- **Blueprint Discs in world loot**: every curated build can drop as treasure.
- **Resins**: one-shot print modifiers (grow crops, stock chests, bank XP, cheaper prints…).
- **Upgrade modules**: tune Speed, Efficiency, RF Efficiency, and Buffer.
- **Blueprint Repository**: a block that catalogues your discs and re-burns copies on demand.
- **Filament Rack + MC3D Cable**: spool storage / FU reservoir, and a dual-carry RF+FU cable.
- **Modded compatibility**: soft-dep FU values for AE2, Thermal, Tinkers', Mekanism,
  Create, Botania, EnderIO, Immersive Engineering, and Draconic Evolution.
- **In-game guide** via Patchouli, plus JEI recipe/FU integration.

## Install

MC3DPrint is a standard Forge/NeoForge mod. Every supported version ships from the
same release:

| Minecraft | Loader | On CurseForge |
|---|---|---|
| 1.20.1 | Forge 47.4.10+ | yes |
| 1.21.1 | NeoForge | yes |
| 1.21.8 · 1.21.9 · 1.21.10 · 1.21.11 · 26.1 · 26.2 | NeoForge | GitHub release only |

CurseForge carries the two soak-tested builds; the forward jars are attached to every
GitHub release and get promoted to CurseForge as each one clears its in-world soak.

1. Install **Minecraft** with the matching loader from the table above.
2. Download the jar for your version, `mc3dprint-<version>-forge-1.20.1.jar` or
   `mc3dprint-<version>-neoforge-<mc>.jar`, from
   [**CurseForge**](https://www.curseforge.com/minecraft/mc-mods/mc3dprint) or the
   [**GitHub Releases**](https://github.com/PGMacDesign/MC3DPrint/releases/latest) page.
3. Drop it into your instance's `mods/` folder (replace any older copy; don't keep two).

**Optional:** [Patchouli](https://www.curseforge.com/minecraft/mc-mods/patchouli)
(in-game guide book) and JEI (recipe/FU lookup).

Full walkthrough: **[mc3dprint.dev/getting-started](https://mc3dprint.dev/getting-started)**.

## Contribute a build

Built something worth shipping? You don't need to know Git or code:

1. Go to **[mc3dprint.dev/submit](https://mc3dprint.dev/submit)**.
2. Drag in your `.blueprint` file. It renders in 3D right in your browser.
3. Add your name for credit and submit.

Behind the scenes it opens a reviewable pull request (with an auto-rendered preview).
If it's a good fit, it ships with the mod and you're credited. You can also browse
and preview every curated build in the [gallery](https://mc3dprint.dev/gallery).

## Build from source

`main` is a [Stonecutter](https://stonecutter.kikugie.dev/) multi-version tree: one
source tree that builds every NeoForge jar, so Gradle tasks are **node-scoped**. The
Forge 1.20.1 jar is built from the separate `legacy/1.20.1` branch.

```bash
./gradlew :1.21.1:test               # JUnit suite for one node (122 tests on every node)
./gradlew :1.21.8:compileJava -q     # fast compile check
./gradlew :1.21.1:runGameTestServer  # in-world GameTests (the 1.21.1 node is the oracle)
./gradlew :1.21.8:assemble -x test   # one jar → versions/1.21.8/build/libs/
```

To build everything at once, `./scripts/build-all.sh` writes all eight shippable jars
into `dist/`: one per NeoForge node plus the Forge 1.20.1 jar (built in a throwaway
worktree off `legacy/1.20.1`). It needs a JDK 21 launcher, and JDK 17 for the legacy
Forge build; both are auto-detected. This is what the release workflow runs.

Textures and GUIs are generated reproducibly by the `tools/*.py` scripts (PIL).
Patchouli diagram art is generated by `tools/gen_guide_images.py`.

### Contributing code

Since the 1.0.0 release, **all code changes land through pull requests**: no direct pushes
to `main`. Branch from `main`, open a PR, and merge only after CI and CodeRabbit are green.
The same PR-only flow applies to the `legacy/1.20.1` Forge backport branch.

---

## Blueprints & found builds

Scan your own builds with the Structure Scanner, or **find Blueprint Discs in world
loot**. The mod ships a curated library of **130+ builds** plus **working auto-farms**:
the redstone-timed harvesters run on a built-in **Redstone Clock** block; the crop farms
print already planted (a **Verdant** resin makes them print fully grown). Found and curated
discs count as **official**: and only official discs accept Resins (a disc you scanned
yourself never will, which is the anti-exploit gate).

In **creative**, the `allowAllDiscsInCreative` config toggles between handing out every
curated disc (default) and only a small hand-picked launch set. Cosmetic only; world-loot
drops are unaffected.

## Upgrade modules

Printers take **upgrade modules** in slots that grow with tier: a Tier 3 printer has
3, topping out at **8 at Tier 8**. Four module types, capped at 4 of each per machine:

- **Speed**: faster printing
- **Efficiency**: less FU per block (4 reach exact 1:1 break-even)
- **RF Efficiency**: less RF per print
- **Buffer**: a bigger RF tank

Speed, RF Efficiency and Buffer stack **multiplicatively** (two Speed modules = 0.8 ×
0.8 = 64% print time); Efficiency is linear.

## Modded compatibility

Optional **soft-dependency** hooks value other mods' items so they wind into filament
and print: **AE2, Thermal Series, Tinkers' Construct, Mekanism, Create, Botania,
EnderIO, Immersive Engineering**, and **Draconic Evolution** (which also powers the
Tier 8 Fabricator). The hooks are pure `ResourceLocation` strings with **no hard
dependency**: completely invisible (no crash, warning, or config) when a mod isn't
installed. **JEI** (FU/recipe viewer) and **Patchouli** (the in-game guide) are also
supported.

## In-game guide

Player-facing documentation lives in the Patchouli guidebook ("Fabricator's Handbook"):
`src/main/resources/assets/mc3dprint/patchouli_books/guide/`. The book explains **how**
to use the mod, concisely. The **why** (design rationale and anti-exploit reasoning)
lives below so it stays in the repo without cluttering the in-game pages.

---

## Design notes (economy & anti-exploit rationale)

These notes record *why* the FU economy and tier system work the way they do. This
reasoning used to live in the guidebook but was moved here to keep the in-game pages
focused on how-to; the rules themselves are still documented in the book.

### Tier material ladder

Material tiers gate both which machine can print a block and which spool can fund
it. As of the current balance pass, **netherite is Tier 6** (it moved up from
Tier 5). Tier 5's multiblock corners are **Diamond Blocks**; Tier 8's corners are
**Awakened Draconium** (Draconic Evolution).

### Filament Unit cost derivation

Each block's FU cost (and its material tier) is derived from its recipe graph
(crafting, plus optionally smelting and stonecutting). A diamond block costs what
its diamonds are worth; stone costs almost nothing. Deriving cost from recipes
keeps the economy self-consistent: there's no hand-maintained price table to
drift, and a block's material tier falls out of the same graph, so cheap blocks
stay cheap and rare blocks stay gated to higher-tier machines and spools.

### Exact-tier winding (making FU)

The Filament Winder is strict: a material only winds into a spool of its **exact**
tier (netherite → Tier 6 spool, cobblestone → Tier 1 spool). This is deliberate.
It stops cheap, mass-farmed blocks (e.g. cobblestone from a cobble farm) from
ever filling a high-tier spool and undercutting the economy.

The player-facing upside is the same rule read forward: a spool stores a *tier*,
not a *material*, so everything at that tier is interchangeable. Sculk and diamond
are both Tier 5, so a sculk-wound spool prints diamonds. That's a feature, not a
leak, and the abundance rule below is what keeps it honest.

### The abundance rule (why tiers are where they are)

Because everything in a tier is interchangeable, tier assignment *is* the balance
lever. The rule: **a farmable resource can't sit at a tier whose spool could print
something rarer than the resource itself.** That's why chorus fruit is capped at
Tier 4 (a Tier 6 chorus spool could print netherite) and why tridents and nautilus
shells stay at Tier 5 rather than Tier 6.

Tier 5 is a deliberate exception in spirit: sculk and ender pearls are farmable and
sit alongside diamond. Reaching a Tier 5 spool at all is a real investment, so the
leeway is intentional. Tier 2 needs no such care, since everything it can reach
(iron, gold) is already farmable in vanilla.

Survival-unobtainable blocks stay intentionally **unvalued**, so strict mode
refuses them outright.

### Down-only spending (printing)

At print time the rule relaxes one direction only: a spool can pay any cost **at
or below** its own tier, never above. High-tier FU covers low-tier blocks
(down-converted at the configured ratio); low-tier FU contributes nothing toward
higher-tier blocks. So a high-tier spool can print a whole stone house, but a
Tier 1 spool can never print netherite.

Together, exact-tier winding and down-only spending stop cobblestone-farming from
ever reaching a high-tier spool, while letting your best spools print anything
below them.

### Printing always costs a markup

Lower-tier machines are deliberately less efficient: a Tier 1 printer pays a
higher FU multiplier per block than a Tier 8 Fabricator. Printing always costs a
little **more** than the matter is worth; that markup is the price of
convenience. Stacking 4 Efficiency modules erases it and prints at exact
break-even (1:1), but you can never print matter for **less** than it's worth,
so printing can't be turned into a duplication exploit. Only Tier 4+ machines
have enough slots to reach 1:1, and a Tier 4 must spend all four slots on
Efficiency to do it.

### Strict mode / unknown blocks

In strict mode (the default), un-priced blocks (those with no derivable FU value)
can't be printed. This closes the "scan something expensive, print it cheap"
exploit where an unrecognized but valuable block would otherwise print for free.
Pack makers can set `unknownBlocksPrintable=true` to price unknowns instead of
refusing them. For structure prints the machine skips any block it can't print
(too high a tier, or un-priced) and builds the rest; only an item print, or a
structure where nothing is printable, shows **Not Printable** outright.

### Resins (print modifiers)

A **Resin** is a one-shot modifier dropped into the printer's Resin slot to make a
blueprint print come out better; it's consumed each print. Six effects: **Verdant**
(plants print grown), **XP Yield** (the print banks experience), **Treasure** (printed
chests may hold loot), **Overdrive** (the print costs less filament), **Quartermaster**
(printed furnaces/chests arrive stocked), and **Ore Salting** (printed stone hides ore
veins). Resins craft from a **Resin Base** (Extrudium Crystal + a slimeball); **Common**
and **Uncommon** are craftable, **Rare** is found only, in end-game loot.

Several effects mint value (loot, free ore, net-gain FU), so each is **multiply-gated**
against duplication: a resin works **only on official/found blueprints**, never ones you
scanned yourself; it's **consumed per print**; the strongest resins are **unfarmable,
loot-only**; and value effects are **capped per print**. All chances and amounts are
configurable.

### Spool capacity

Every filament spool holds a uniform **100,000 FU**, regardless of tier. A spool's
*tier* governs what it can print and how it's wound (see above), **not** how much it
holds. (Earlier versions scaled capacity by tier; that was dropped in favor of a single,
predictable number.) Capacity, conversion ratios, and per-tier print efficiencies are all
configurable for pack makers.

---

## License

MIT © PGMacDesign. Free to use, modify, and share.
