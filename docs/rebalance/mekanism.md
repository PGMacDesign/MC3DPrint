# Mekanism 1.20.1 (Mekanism Team) — Acquisition Ranking & FU Tier Map

_Research input for MC3DPrint's modded FU support — the **Mekanism** soft-dep, modelled on the
AE2 / Thermal / Tinkers' compat hooks. Item ids + recipe types read from the **`1.20.x`
branch** of `mekanism/Mekanism` on 2026-06-14 (`assets/mekanism/lang/en_us.json` +
`data/mekanism/recipes/...`)._

---

## 0. Namespace & what to gate on
Item namespace: **`mekanism`**. Gate on `ModList.get().isLoaded("mekanism")`. The sub-mods
(`mekanismgenerators`, `mekanismtools`, `mekanismadditions`) have their own ids but all the
**materials** live in `mekanism:` — nothing else needs valuing.

### The derivation fact
Raw→ingot for the mined metals is **plain `minecraft:smelting`** (verified:
`osmium/ingot/from_raw_smelting.json` → `"type":"minecraft:smelting"`), so
`ingot_osmium/tin/lead/uranium` DERIVE from the raws, as do every `block_*` (×9) and nugget.
The alloys come from `mekanism:metallurgic_infusing` (verified: `bronze/ingot/from_infusing.json`)
and the refined metals from the Osmium Compressor — custom types our valuator can't read, so
those are the leaves we pin.

## 1. Acquisition axis (mapped to vanilla tiers)
```
T2  osmium / tin / lead (raw + ore)   — iron-abundance base metals (osmium spawns all Y)
T2  fluorite (gem + ore)              — common deepslate gem
T3  uranium (raw + ore)               — deeper, nuclear feedstock
T3  steel, bronze, infused alloy      — Metallurgic Infuser commons
T4  refined glowstone                 — glowstone + liquid osmium (Osmium Compressor)
T5  refined obsidian, reinforced alloy— obsidian + osmium; reinforced carries diamond
T6  atomic alloy                      — top control circuitry
T8  antimatter pellet                 — SPS-only pinnacle (trophy tier)
```
### Abundance / anti-launder reasoning
- **The entire ore-multiplication graph is UNVALUED.** `dust_*` (2x), `clump_*` /
  `dirty_dust_*` (3x), `shard_*` (4x), `crystal_*` (5x) are all multiplication intermediates —
  valuing any of them would let a printed spool launder infinite multiplied material. This is
  the single most important call for Mekanism.
- **osmium = T2.** It generates at every Y level (iron-class abundance), so its spool must not
  reach a tier that prints something rarer. T2 caps it at the base-metal band.
- **steel = T3** reuses the cross-mod anchor (Tinkers' already pins steel T3). Same material
  can't be printed cheap under one mod and consumed under another.

## 2. FU values registered (`integration/mekanism/MekanismCompat.java`)
| Item id (`mekanism:`) | FU | Tier | Source / rationale |
|---|---:|:--:|---|
| `raw_osmium`, `osmium_ore`, `deepslate_osmium_ore` | 18 | 2 | mined; ingot derives (smelt) |
| `raw_tin`, `tin_ore`, `deepslate_tin_ore` | 18 | 2 | mined; cross-mod common (Thermal tin) |
| `raw_lead`, `lead_ore`, `deepslate_lead_ore` | 18 | 2 | mined; cross-mod common (Thermal lead) |
| `fluorite_gem`, `fluorite_ore`, `deepslate_fluorite_ore` | 10 | 2 | deepslate gem |
| `raw_uranium`, `uranium_ore`, `deepslate_uranium_ore` | 25 | 3 | deeper, nuclear feedstock |
| `ingot_steel` | 25 | 3 | Metallurgic Infuser; cross-mod anchor |
| `ingot_bronze` | 22 | 3 | Metallurgic Infuser; cross-mod common |
| `alloy_infused` | 12 | 3 | iron + redstone |
| `ingot_refined_glowstone` | 35 | 4 | glowstone + liquid osmium |
| `ingot_refined_obsidian` | 60 | 5 | obsidian + liquid osmium |
| `alloy_reinforced` | 70 | 5 | carries diamond + refined obsidian |
| `alloy_atomic` | 130 | 6 | top control circuitry |
| `pellet_antimatter` | 600 | 8 | SPS-only pinnacle (trophy) |

**Derived automatically (not pinned):** `ingot_osmium/tin/lead/uranium` (vanilla smelt of raw),
all `block_*` (×9) and nuggets.

**Intentionally UNVALUED:**
- **Multiplication graph** — all `dust_*`, `clump_*`, `dirty_dust_*`, `crystal_*`, `shard_*`.
- **Mass-production** — `salt`, `block_salt`, `lithium`, `lithium_bucket` (evaporation/brine),
  `hdpe_pellet` (bio).
- **Nuclear intermediates** — `pellet_plutonium`, `pellet_polonium` (reactor; antimatter is the
  single endgame capstone we price). `block_raw_*` (storage, ×9). Machines / ducts / tools.

## 3. Open verification (in-game, with Mekanism installed)
- Confirm osmium really wants **T2** in your pack (some packs treat it as a premium metal). It's
  tunable — bump to T3 if osmium feels too cheap to print.
- Decide whether `pellet_antimatter` at T8 is desired, or drop it (it's not a build material).
  Optionally add `pellet_plutonium`/`pellet_polonium` at T6–T7 if you want the full nuclear set.
- Re-confirm `alloy_atomic` inputs (recipe may include antimatter/plutonium) to validate T6.

All numbers are **tunable**. Related: `docs/rebalance/{ae2,thermal,tconstruct}.md`,
the skill's `references/fu-model.md`, and the `modded-fu-compat` memory.
