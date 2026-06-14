# Immersive Engineering 1.20.1 (BluSunrize) — Acquisition Ranking & FU Tier Map

_Research input for MC3DPrint's modded FU support — the **Immersive Engineering** soft-dep. Ids +
recipe types read from the **`1.20.1` branch** of `BluSunrize/ImmersiveEngineering` on 2026-06-14
(`assets/.../lang/en_us.json`, generated recipe JSON). Verified via `jq '.type'`._

## 0. Namespace & gate
Item namespace **`immersiveengineering`**; gate `ModList.isLoaded("immersiveengineering")`. Custom
recipe types whose outputs DON'T derive: `immersiveengineering:blast_furnace` (steel),
`immersiveengineering:alloy` / `arc_furnace` (constantan, electrum, hop graphite),
`immersiveengineering:metal_press` (plates — skipped), `immersiveengineering:crusher` (ore-doubling).

**Verbatim spelling matters:** IE uses `raw_<metal>` / `ore_<metal>` / `deepslate_ore_<metal>` and
`ingot_<metal>` (NOT `<metal>_ore`/`<metal>_ingot`). A wrong path silently registers nothing.

## 1. Classification
- **Mined** (aluminum/nickel/lead/silver/uranium): pin raw + ore blocks; `ingot_*` derives via
  vanilla smelting.
- **Alloy/refined leaves** (steel/constantan/electrum/hop graphite): custom furnaces → pin the ingot.
- **Anti-launder:** the Crusher ore-doubles and the Arc Furnace recycles, so all `dust_*` / `slag*` /
  `grit_*` are UNVALUED. `plate_*` (Metal Press, 1:1) + `sheetmetal_*`/wires/components skipped.
- IE **copper** aliases vanilla (`minecraft:copper_ingot`) — needs nothing.

## 2. FU values registered (`integration/immersiveengineering/ImmersiveEngineeringCompat.java`)
| id (`immersiveengineering:`) | FU | Tier | source / rationale |
|---|---:|:--:|---|
| `raw_aluminum`, `ore_aluminum`, `deepslate_ore_aluminum` | 18 | 2 | mined; ingot derives |
| `raw_nickel`, `ore_nickel`, `deepslate_ore_nickel` | 18 | 2 | mined; cross-mod (Thermal) |
| `raw_lead`, `ore_lead`, `deepslate_ore_lead` | 18 | 2 | mined; cross-mod (Thermal/Mekanism) |
| `raw_silver`, `ore_silver`, `deepslate_ore_silver` | 18 | 2 | mined; cross-mod (Thermal) |
| `raw_uranium`, `ore_uranium`, `deepslate_ore_uranium` | 25 | 3 | deeper/nuclear (matches Mekanism) |
| `ingot_steel` | 25 | 3 | Blast Furnace; **cross-mod steel anchor** |
| `ingot_constantan` | 20 | 3 | Arc/Alloy (copper + nickel) |
| `ingot_electrum` | 20 | 3 | Arc/Alloy (gold + silver) |
| `ingot_hop_graphite` | 25 | 3 | Arc Furnace (coke dust) |

**Derives (not pinned):** `ingot_aluminum/nickel/lead/silver/uranium`, all `storage_*` blocks,
`raw_block_*`, all `nugget_*`.

## 3. Open verify
- `ingot_hop_graphite` tier (niche electrode material) — T3 by depth; could be T2. Tunable.
- constantan/electrum at T3 vs strict Thermal parity (T2). Confirm desired band.
- `plate_*` intentionally skipped despite a custom recipe type → they won't auto-derive (effectively
  unwindable). If you want plates windable later, pin each at its parent ingot's tier.

**New cross-mod commons anchored here** (added to the skill's `fu-model.md`): aluminum T2~18,
constantan T3~20, electrum T3~20 — reuse when Thermal/other mods also define them.
