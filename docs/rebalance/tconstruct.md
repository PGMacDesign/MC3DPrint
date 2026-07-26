# Tinkers' Construct 1.20.1 (SlimeKnights) — Acquisition Ranking & FU Tier Map

_Research input for MC3DPrint's modded FU support — the **Tinkers' Construct** soft-dep,
modelled on the AE2 / Thermal compat hooks (`integration/ae2/Ae2Compat.java`,
`integration/thermal/ThermalCompat.java`). Item ids + metal registrations read from the
**`1.20.1` branch** of `SlimeKnights/TinkersConstruct` on 2026-06-14
(`shared/TinkerMaterials.java`, `assets/tconstruct/lang/en_us.json`)._

---

## 0. Namespace & what to gate on

Everything registers under one item namespace: **`tconstruct`**. Gate the hook on
`ModList.get().isLoaded("tconstruct")`. There is no separate `tinkers:` / `mantle:` item
namespace for metals (Mantle is the shared library; it ships no printable metals).

Metals are registered in `shared/TinkerMaterials.java` via Mantle's
`BLOCKS.registerMetal("<name>", …)`, which produces the trio
`tconstruct:<name>_ingot` / `_nugget` / `_block` (plus an ore/raw pair for mined cobalt).

### The key derivation fact (why we value so few items)

Our `RecipeFuValuator` reads **vanilla recipe *types*** (`minecraft:crafting_*`,
`minecraft:smelting`, `stonecutting`) — including modded recipes that *use* those types.
TC registers every **block (9 ingots ⇄)** and **nugget (1/9 ingot ⇄)** as ordinary
`minecraft:crafting` recipes, so they **derive automatically from the ingot**. We therefore
pin **only the ingot** for each metal.

The **Smeltery alloy recipes** (`tconstruct:alloy`, melting/casting) are a **custom recipe
type the valuator cannot read** — so the alloy ingots are the leaves that need explicit
values. This is the same situation as AE2's Inscriber and Thermal's Induction Smelter.

---

## 1. Acquisition axis (TC's material progression, mapped to vanilla tiers)

TC replaces vanilla's mining-depth axis with a Smeltery progression. Anchors from the
vanilla map: `iron=20@T2`, `obsidian/glowstone=10–20@T3`, `blaze_rod=40@T4`,
`diamond=50@T5`, `netherite_scrap=125@T6`.

```
T3  steel, slimesteel, amethyst bronze, rose gold, pig iron, knight metals
        └─ alloys of iron / copper / gold / slime / amethyst — all farmable inputs
T4  cobalt (Nether-mined) → queen's slime, cinderslime, soulsteel
        └─ Nether / blaze / soul tier; cobalt is the gateway Nether metal
T5  hepatizon   (molten cobalt + molten amethyst bronze)
        └─ top non-debris metal; diamond-adjacent
T6  manyullyn   (molten cobalt + molten ANCIENT DEBRIS / netherite scrap)
        └─ carries debris → must gate at netherite tier, never below
```

### Abundance / anti-launder reasoning

- **steel / cobalt are cross-mod commons.** EnderIO, IE, Mekanism, Forestry all define a
  "steel" and most define cobalt-likes. Pinning steel **T3** and cobalt **T4** here sets the
  anchor so the *same* material can't be arbitraged across mods later (print steel cheaply
  in one mod's tier, melt in another). Keep these values when those mods land.
- **manyullyn at T6, not T5.** It alloys ancient-debris-derived scrap (scrap=125@T6). Down-
  only printing means a T6 spool can't print *up* — but if manyullyn sat at T5, a diamond-
  tier (T5) spool could print a debris-bearing metal, undervaluing ancient debris. T6 closes
  that. (You can't melt printed manyullyn back into separable scrap, so this is balance, not
  a hard dupe — but the tier is still correct.)
- **pig iron is edible** but needs Smeltery + blood (not farm-spammable) and winds back 1:1,
  so it is **not** winder-blacklisted (no laundering gain). Revisit if it ever becomes a
  cheap auto-food in some pack.

---

## 2. FU values registered (`integration/tinkers/TinkersCompat.java`)

| Item id (`tconstruct:`) | FU | Tier | Source / rationale |
|---|---:|:--:|---|
| `steel_ingot` | 25 | 3 | Smeltery; iron(20)+carbon, the workhorse — cross-mod anchor |
| `slimesteel_ingot` | 30 | 3 | Smeltery; iron + skyslime (slime_ball=30@T3) |
| `amethyst_bronze_ingot` | 22 | 3 | Smeltery; copper(10)+amethyst(10) |
| `rose_gold_ingot` | 22 | 3 | Smeltery; gold(15)+copper(10) |
| `pig_iron_ingot` | 24 | 3 | Smeltery; iron+clay+blood — edible, left windable |
| `knightslime_ingot` | 30 | 3 | Smeltery; slime + iron + gold |
| `knightmetal_ingot` | 30 | 3 | ⚠ verify obtain path (cluster block exists) |
| `cobalt_ore` | 30 | 4 | Nether worldgen block — valued so it prints |
| `raw_cobalt` | 30 | 4 | mined leaf; `cobalt_ingot` derives from smelting |
| `cobalt_ingot` | 30 | 4 | pinned for clarity (also derives from raw) |
| `queens_slime_ingot` | 45 | 4 | Smeltery; gold + magma slime + blaze |
| `cinderslime_ingot` | 45 | 4 | Smeltery; blaze/cinder tier |
| `soulsteel_ingot` | 45 | 4 | Smeltery; soul tier |
| `hepatizon_ingot` | 70 | 5 | Smeltery; cobalt + amethyst bronze (diamond=50@T5) |
| `manyullyn_ingot` | 130 | 6 | Smeltery; cobalt + ancient debris (scrap=125@T6) |

**Derived automatically (not pinned):** every `*_block` (×9) and `*_nugget` (÷9) via TC's
own `minecraft:crafting` recipes; `cobalt_ingot` via smelting (pinned anyway).

**Intentionally UNVALUED** (strict mode → unprintable, safe): `cheese_ingot` /
`fake_ingot` (joke / render-only items);
`debris_nugget` / `netherite_nugget` / `copper_nugget` (derive). No tool parts, tool
materials (datapack-defined, not items), or molten fluids are valued.

---

## 3. Open verification (in-game, with TC installed)

- Confirm `knightmetal_ingot` is actually obtainable in standalone TC 1.20.1 (it has a
  `knightmetal_cluster` block — may be grown/mined rather than alloyed). If unobtainable the
  value is simply inert; if obtainable, T3 is fine.
- Spot-check that `steel_ingot` / `cobalt_ingot` have no stray `minecraft:crafting` recipe
  that would let derivation undercut the pinned value (explicit API value wins regardless).
- Re-confirm `cinderslime` / `soulsteel` alloy inputs to validate their T4 placement; both
  are recent additions and recipe details may shift between TC builds.

All numbers are **tunable** — same as the vanilla rebalance. Related:
[`thermal.md`](thermal.md), [`ae2.md`](ae2.md), `docs/rebalance/rebalance-plan.md`,
and the `modded-fu-compat` memory.

---

## Correction: the bones do NOT derive

An earlier pass listed `necrotic_bone`, `venombone`, `blazing_bone` and `necronium_bone` as
"crafting-derived" and left them unvalued on that basis. That was wrong, and verified so
against `TConstruct-1.20.1-3.11.2.166`:

| item | producing recipe types | valuator-readable? |
|---|---|---|
| `necrotic_bone` | `tconstruct:severing`, `tconstruct:casting_basin` | no |
| `blazing_bone` | `tconstruct:casting_table`, `casting_basin`, `material_fluid` | no |

None are types `RelaxationFuValuator` can read, so they fall through to unvalued exactly like
the Smeltery alloys. That left them both unprintable *and* unwindable.

`necrotic_bone` is now pinned at **`15@2`, wind-only** (`#no_print`):

- **T2, not the skull's T4.** A wither-skeleton farm is AFK-automatable and Severing yields
  2 bones per kill. T2's ceiling is iron (`20@2`), already farm-trivial, so nothing a
  necrotic-bone spool can reach beats its own source.
- **Wind-only**, mirroring vanilla `wither_skeleton_skull`. Printing it would mint the Necrotic
  modifier's material (life steal) and Slimeskulls straight out of filament.
- Valuing it unlocks **no derived chain**: no vanilla-readable recipe uses it as an ingredient,
  so it is a pure winder input.

`venombone`, `blazing_bone` and `necronium_bone` remain unvalued and would each need their own
pin if they should be windable too.
