# FU model — anchors, rules, and cross-mod commons

Read this when calibrating values in step 4. The economy invariants here are the *reasons*
behind the tiering; understand them rather than pattern-matching numbers.

## Always read the live anchors
The authoritative FU values live in `fu/FuValueRegistry.java` → `defaultEntries()`. Read that
method in the repo before assigning numbers — it changes between balance passes, and the
target MC version may differ. The table below is a **2026-06 / 1.20.1 snapshot** for quick
orientation only.

| Tier | Vanilla anchors (id = FU) | Feel |
|---|---|---|
| T1 | coal=2, moss_block=2, deepslate/tuff/basalt-family (low) | naturally-spawned bulk |
| T2 | copper=10, amethyst=10, lapis=10, gold=15, **iron=20** | shallow-mid mining, base metals |
| T3 | redstone=4, quartz=5, string=8, **obsidian=10**, glowstone=20, slime_ball=30, wool=30 | refined / Nether-lite |
| T4 | magma_cream=30, **blaze_rod=40**, emerald=50, ghast_tear=50, totem=200; chorus capped here | Nether / blaze / End-plant |
| T5 | ender_pearl=40, **diamond=50**, sea_lantern=50, shulker_shell=80, golden_apple=300 | deep / End |
| T6 | **netherite_scrap=125**, netherite_ingot=500, ench_golden_apple=1500, elytra=2000 | ancient-debris tier, finite |
| T7 | nether_star (beacon) | boss-gated |
| T8 | echo_shard=500, heart_of_the_sea=800, draconium (DE) | deep-dark / endgame / modded |

Magnitudes scale super-linearly with rarity within a tier — a value is "how much filament you
get for winding it / what a print costs." Match the neighbourhood of the nearest vanilla anchor;
don't invent precision.

## The classification rule (what to value)
Value ONLY custom-machine-recipe **leaves** and **mined raws/ores**. Everything reachable
through vanilla recipe *types* (`minecraft:crafting_*`, `smelting`, `stonecutting`) is priced
automatically by `RecipeFuValuator`, including modded recipes of those types. So:
- Pin the **ingot**; `*_block` (×9) and `*_nugget` (÷9) and `*_plate`/`*_gear` derive.
- A mined metal: value the **raw** + **ore block** (the ore so it prints); the ingot derives
  from vanilla smelting (pin it too for clarity if you like — explicit always wins).
- Leave **resource-multiplication** outputs (ore-doubling dusts, extractor latex, etc.)
  UNVALUED — that's the anti-launder firewall.

## The abundance rule (what tier)
Printing is **down-only** and winding is **1:1 exact-tier**. So a spool of tier *N* can print
anything at tier ≤ *N*. Therefore: **a farmable/cheap item must not sit at a tier whose spool
could print something rarer than that item.** If it could, a farm launders cheap mats into
scarce goods. Examples already in the codebase:
- chorus fruit → **T4**, not higher: a T6 chorus spool could print netherite.
- AE2 certus quartz (auto-farms via budding) → pinned **T2/T3**.
- Tinkers' manyullyn → **T6**: it alloys ancient-debris-derived scrap, so it must gate at the
  netherite tier, never below — otherwise a T5 (diamond) spool prints a debris-bearing metal.

When a mod's signature mechanic is mass-producing a material, pin that material LOW (or skip it)
rather than letting it ride a deep tech tree upward.

## Cross-mod common materials (keep these consistent)
Many tech mods define the same material. Pin it to the SAME tier/value everywhere so it can't be
printed cheaply under one mod's tier and consumed under another's. Running list — extend it when
you anchor a new common:

| Material | Tier | ~FU | Anchored in (reuse these exact values) |
|---|:--:|---:|---|
| steel (ingot) | T3 | ~25 | Tinkers' + Mekanism — confirmed cross-mod |
| bronze (ingot) | T3 | ~22 | Tinkers' + Mekanism |
| cobalt (ingot/raw) | T4 | ~30 | Tinkers' (`tconstruct:cobalt_ingot`) |
| tin (raw/ingot) | T2 | ~18 | Thermal + Mekanism |
| lead (raw/ingot) | T2 | ~18 | Thermal + Mekanism |
| osmium (raw/ingot) | T2 | ~18 | Mekanism |
| signalum / lumium | T4 | 35 / 40 | Thermal |
| enderium | T5 | ~90 | Thermal |
| (add electrum, invar, constantan as mods land) | | | Thermal valued silver/nickel T2 |

When EnderIO / IE add their own steel/bronze, reuse the values above. Same for any shared alloy.

## Tier ceiling
`FuValueRegistry.clamp` bounds tier to 1–8 and FU to ≥1. T8 normally needs a modded endgame
material (draconium); don't push a mainstream mod's items to T8 without a reason as strong as
"requires a fusion-crafted / boss-tier input."
