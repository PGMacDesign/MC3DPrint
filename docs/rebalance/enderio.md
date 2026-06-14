# EnderIO 1.20.1 (Team-EnderIO) — Acquisition Ranking & FU Tier Map

_Research input for MC3DPrint's modded FU support — the **EnderIO** soft-dep. Ids + recipe types
read from the **`1.20.1` branch** of `Team-EnderIO/EnderIO` on 2026-06-14
(`alloy_smelting/*.json`, `mods.toml`). Verified via `jq '.type'`._

## 0. Namespace & gate
Gate mod-id `enderio` == item namespace **`enderio`** (single namespace despite the multi-source-set
repo layout). All nine alloys are `enderio:alloy_smelting` leaves — a custom recipe type the valuator
can't read — so each ingot is pinned; blocks ×9 / nuggets ÷9 derive. The Alloy Smelter is 1:1
alloying (no multiplication), so they're safe to value, tiered by input rarity.

## 1. The 1.20 rename trap
EnderIO 1.20 renamed every alloy to **`<name>_alloy_ingot`** — the legacy `conductive_iron` /
`pulsating_iron` / `phased_iron` / `electrical_steel` ids are **GONE** (there is no
`electrical_steel` in this branch). The exact spellings below are load-bearing.

## 2. FU values registered (`integration/enderio/EnderIOCompat.java`)
| id (`enderio:`) | FU | Tier | alloy inputs |
|---|---:|:--:|---|
| `redstone_alloy_ingot` | 8 | 3 | redstone + silicon |
| `copper_alloy_ingot` | 12 | 3 | copper + silicon |
| `conductive_alloy_ingot` | 18 | 3 | copper alloy + iron + redstone (was "conductive iron") |
| `pulsating_alloy_ingot` | 30 | 4 | iron + ender pearl |
| `energetic_alloy_ingot` | 35 | 4 | gold + redstone + glowstone |
| `soularium_ingot` | 30 | 4 | gold + soul sand |
| `dark_steel_ingot` | 40 | 4 | iron + coal + obsidian |
| `vibrant_alloy_ingot` | 55 | 5 | energetic alloy + ender pearl |
| `end_steel_ingot` | 90 | 6 | dark steel + end stone + obsidian (End-gated capstone) |

**Anti-launder:** `enderio:silicon` and all SAG-Mill (`sag_milling`) powders are ore-doubling outputs
→ UNVALUED. `pulsating_crystal`/`vibrant_crystal`/etc. derive (`minecraft:crafting_shaped`).

**Skipped:** all `*_block`/`*_nugget`/grinding balls (derive), machines/conduits/capacitors/gears,
dark_steel tools+doors+bars, fluids/consumables.

## 3. Open verify
- **`grains_of_infinity`** — a genuine `enderio:fire_crafting` ritual leaf (drops on bedrock), NOT
  derivable. Deferred/UNVALUED for now; value ~80/T6 if you want it printable (confirm it isn't
  trivially farmable first). It's the input to `infinity_rod` + End-tier blocks.
- `dark_steel` T4 vs T5 (iron+coal+obsidian — cheap-ish); `end_steel` 90@T6 vs lower. Tunable.

No cross-mod commons collide — EnderIO's "steel" variants (dark/end) are unique materials, not the
generic `forge:ingots/steel` that Tinkers'/Mekanism pin at T3. `forge:silicon` is shared but skipped
everywhere as a multiplication output.
