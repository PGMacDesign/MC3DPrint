# Create 1.20.1 (Creators-of-Create) — Acquisition Ranking & FU Tier Map

_Research input for MC3DPrint's modded FU support — the **Create** soft-dep. Ids + recipe types
read from the **`mc1.20.1/dev` branch** of `Creators-of-Create/Create` on 2026-06-14. Verified
via `jq '.type'`._

## 0. Namespace & gate
Item namespace **`create`**; gate `ModList.isLoaded("create")`. Custom recipe types: `create:mixing`
(Mechanical Mixer), `create:pressing` (Mechanical Press), `create:crushing` (Crushing Wheels). Rose
quartz has no datapack recipe (hardcoded in-world redstone conversion) → also a leaf.

## 1. Tiny material surface (don't over-reach)
Create is a building/automation mod. After sweeping the full id list, the entire economy-relevant
material set is **zinc (mined) + andesite alloy + brass + rose quartz** — everything else is kinetic
machinery, decorative stone, or derivable variants.

**Anti-launder = the crushing graph.** `create:crushing` ore-doubles (~1.75–2×) and crushes other
mods' raws via `forge:raw_materials/*` tags, so every `crushed_raw_*` is left UNVALUED. zinc gates
brass + andesite alloy (mass-producible via automation but bottlenecked by the mined ore), so those
stay modest.

## 2. FU values registered (`integration/create/CreateCompat.java`)
| id (`create:`) | FU | Tier | source / rationale |
|---|---:|:--:|---|
| `raw_zinc` | 18 | 2 | mined; `zinc_ingot` derives via `minecraft:smelting` |
| `zinc_ore` | 18 | 2 | worldgen ore block |
| `deepslate_zinc_ore` | 18 | 2 | worldgen variant |
| `andesite_alloy` | 12 | 2 | Mechanical Mixer (andesite + iron/zinc nugget); gateway material |
| `brass_ingot` | 22 | 3 | Mechanical Mixer (copper + zinc) |
| `rose_quartz` | 10 | 3 | in-world redstone conversion (no datapack recipe); redstone-cheap |

**Derives / skipped:** `zinc_ingot` (smelt), all `*_block`/`*_nugget`/`*_sheet` (×9/÷9/press),
`polished_rose_quartz` (sandpaper), `experience_nugget`, all decorative stone (asurine/crimsite/
ochrum/etc.), all kinetic & mechanical blocks.

## 3. Open verify
- **`rose_quartz`** (lowest certainty) — no datapack recipe; inferred T3@10 from redstone derivation.
  Confirm in-game it's redstone-cheap; tunable T2↔T3.
- **`andesite_alloy` T2 vs T3** — trivially mass-produced once you have a mixer; T2 keeps it below
  brass and prevents its spool printing anything rarer. Don't push higher (abundance rule).
- **Chromatic capstones** (`refined_radiance`, `shadow_steel`) deliberately UNVALUED — the only
  T5–T6 candidates if you ever want a Create trophy tier (custom light/dark recipes the valuator
  can't read). Defer.

Cross-mod note: if a later mod also produces zinc, reuse `raw_zinc`=18@T2 as the anchor.
