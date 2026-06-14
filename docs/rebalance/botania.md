# Botania 1.20.1 (VazkiiMods) — Acquisition Ranking & FU Tier Map

_Research input for MC3DPrint's modded FU support — the **Botania** soft-dep. Ids + recipe
types read from the **`1.20.x` branch** of `VazkiiMods/Botania` on 2026-06-14
(`LibItemNames.java`, `BotaniaItems.java`, generated recipe JSON). Verified via `jq '.type'`._

## 0. Namespace & gate
Item namespace **`botania`**; gate `ModList.isLoaded("botania")`. Custom recipe types whose
outputs DON'T derive: `botania:mana_infusion` (Mana Pool), `botania:elven_trade` (Alfheim
portal), `botania:terra_plate` (Agglomeration). `runic_altar` makes only runes; `petal_apothecary`
makes flowers — neither contributes materials. `gaia_ingot` is plain `minecraft:crafting` → derives.

## 1. The central call — mana abundance cap
Mana is **passively farmable** (endoflame/hydroangeas/gourmaryllis flowers convert cheap fuel to
mana). So every Mana-Pool leaf is effectively farmable and is pinned in the **T3 obsidian/glowstone
band**, never at its vanilla counterpart's tier. Most important: **`mana_diamond` = T3, not real
diamond's T5** — otherwise a mana farm launders fuel into diamond-printing spools.

Gate escalation = the tech tree: Mana Pool (farmable, T3) → Alfheim elven-trade (T4) → Agglomeration
Plate (terrasteel, 500k mana, T5) → Gaia Guardian boss (life_essence, T6).

## 2. FU values registered (`integration/botania/BotaniaCompat.java`)
| id (`botania:`) | FU | Tier | source / rationale |
|---|---:|:--:|---|
| `manasteel_ingot` | 12 | 3 | Mana Pool (iron + 3000 mana); farmable → capped |
| `mana_pearl` | 14 | 3 | Mana Pool (ender pearl + mana) |
| `mana_diamond` | 16 | 3 | Mana Pool (diamond + mana) — **T3, not T5** (launder block) |
| `quartz_mana` | 10 | 3 | Mana Pool gem ITEM (`mana_quartz` is the block — don't value it) |
| `mana_powder` | 8 | 3 | Mana Pool, cheap |
| `mana_string` | 8 | 3 | Mana Pool (string + mana) |
| `elementium_ingot` | 30 | 4 | Alfheim elven trade |
| `dragonstone` | 30 | 4 | Alfheim elven trade |
| `pixie_dust` | 35 | 4 | Alfheim elven trade |
| `terrasteel_ingot` | 60 | 5 | Agglomeration Plate (manasteel + mana pearl + mana diamond + 500k mana) |
| `life_essence` | 130 | 6 | Gaia Guardian boss drop; feeds gaia_ingot |

**Derives (not pinned):** `gaia_ingot` (crafting from terrasteel + 4×life_essence), all `*_block`
×9 / `*_nugget` ÷9, all manasteel/elementium/terrasteel tools & armor.

**Skipped:** runes, petals, flowers (functional + decorative), livingrock/livingwood/dreamwood,
brews/lenses/baubles/sparks/spreaders/pools — not materials; no ore-multiplication exists in Botania.

## 3. Open verify
- **`quartz_mana` vs `mana_quartz`** — value the gem `quartz_mana`; `mana_quartz` is the block.
- **`life_essence`** — confirm it's a real winder-eligible item (Gaia drop) and not internal-only
  (no en_us lang entry surfaced). If non-windable, pin `gaia_ingot` ~130/T6 directly instead.
- elementium/dragonstone/pixie_dust share the one Alfheim gate; T4 grouping is tunable.

No new cross-mod commons (manasteel/terrasteel/elementium are Botania-exclusive). Tunable as always.
