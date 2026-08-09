# Mystical Agriculture + Mystical Agradditions (BlakeBr0): Acquisition Ranking & FU Tier Map

_Research input for MC3DPrint's modded FU support. Item ids and recipes read from the **`1.20`
branch** of `BlakeBr0/MysticalAgriculture` and `BlakeBr0/MysticalAgradditions` on 2026-08-09
(`lib/ModCrops.java`, `init/ModCrops.java`, `data/*/recipes/**`, `assets/*/lang/en_us.json`)._

The `1.20`, `1.21` and `26.1` branches carry **identical item paths** for everything valued here.
The only diffs across the ladder are cosmetic: 1.20 has six per-tier `<tier>_furnace` items where
1.21 has one `furnace`, and 1.21 adds `awakened_supremium_growth_accelerator`. None are valued, so
one id set covers every node plus the 1.20.1 legacy line.

---

## 0. Namespace & what to gate on

Item namespace: **`mysticalagriculture`**, gate on `ModList.get().isLoaded("mysticalagriculture")`.
Agradditions is a separate mod id (**`mysticalagradditions`**) with its own gate, but note that
**it registers its crops' essences into the `mysticalagriculture:` namespace**, not its own. Its
own namespace holds only the ores, the insanium line, the crux seeds and the paxels.

Crops, seeds and essences are **registered dynamically** from the crop registry, so they do not
appear in the lang file (which carries only generic `mystical_seeds` / `mystical_crop` keys). The
authoritative list is `ModCrops.java`: 138 crops in Mystical Agriculture, 6 more in Agradditions.

### Why this mod is priced unlike the others

Every other compat hook adds materials. This one makes existing materials **farmable**, which is a
head-on collision with the abundance rule rather than an application of it. Two consequences drive
everything below.

**The essence ladder must be flat.** A tier step already multiplies real worth by the conversion
ratio (4, `FuConversion.unitWorth`), and every rung is four essences into one essence one tier up.
Equal FU is therefore exactly break-even, and any climb in the number double-counts the tier. A
10/20/30/40/50 ladder mints 2.00x, 1.50x, 1.33x and 1.25x per rung: 256 inferium worth 2,560 base
units become one supremium worth 12,800. Since printing reaches 1:1 at four Efficiency modules,
that closes into a self-amplifying loop with no farm needed after the first pass.

**Derivation promotes low-tier FU to high-tier worth.** `RelaxationFuValuator` sums ingredient FU
but takes the **max** ingredient tier. Any recipe mixing an essence with a diamond therefore drags
the essence's FU up 256x. This is why gemstones and growth accelerators are pinned rather than
left to derive, against the usual "only value the leaves" rule.

## 1. Acquisition axis

```
T1  inferium essence, inferium ore, soulstone      = first crop, shallow ore, Nether stone
T2  prosperity shard + ore, prudentium essence     = shallow ore, one infusion step up
T3  tertium essence, soulium ore, witherproof      = mid ladder, Nether-gated ore
T4  imperium essence                               = late ladder
T5  supremium essence, every gemstone              = endgame ladder; gemstones carry a diamond
```

### Abundance / anti-launder reasoning

- The ladder tops out at **T5**, never T6. Netherite must stay above farm output, and Mystical
  Agriculture already has a netherite crop.
- **All 138 crop essences are unvalued.** They are farm output and the input to Infusion Crafting;
  pricing them would open a laundering seam and let a printer shortcut the mod's core mechanic.
- **Everything awakened is unvalued.** `awakened_supremium_essence` comes only from the Awakening
  Altar, a custom recipe the valuator cannot read, so that gate holds by itself.
- **`prosperity_gemstone` is pinned to the diamond inside it.** Derived it prices at 66 against a
  50 FU diamond, which is a plain duplicator: craft, wind, print the diamond back, keep 16.

## 2. FU values registered

`integration/mysticalagriculture/MysticalAgricultureCompat.java`

| Item id (`mysticalagriculture:`) | FU | Tier | Source / rationale |
|---|---:|:--:|---|
| `inferium_essence` | 20 | 1 | ladder rung; flat by construction |
| `prudentium_essence` | 20 | 2 | lands on iron (20) |
| `tertium_essence` | 20 | 3 | lands on glowstone (20) |
| `imperium_essence` | 20 | 4 | between chorus (10) and magma cream (30) |
| `supremium_essence` | 20 | 5 | 5,120 base units, about 40% of a diamond |
| `<tier>_farmland` (5) | 20 | 1-5 | recipe takes `minecraft:farmland`, which has no item form, so nothing derives |
| `<tier>_ingot` (5) | 40 | 2-5 | prosperity ingot + 2 essences; derived it mints up to 1.78x. Floored at T2, it carries an iron ingot |
| `<tier>_gemstone` (5) | 50 | 5 | prosperity gemstone + 2 essences; the diamond dominates |
| `<tier>_growth_accelerator` (5) | 16 | 5 | 4 essence + 4 stone + 1 gemstone yielding 3; just under the real 16.8 |
| `prosperity_gemstone` | 50 | 5 | pinned to its diamond, see above |
| `prosperity_shard` | 4 | 2 | mined ore drop, redstone/lapis neighborhood |
| `prosperity_ore`, `deepslate_prosperity_ore` | 4 | 2 | worldgen leaf |
| `inferium_ore`, `deepslate_inferium_ore` | 20 | 1 | worldgen leaf; matches what it smelts into |
| `soulium_ore` | 10 | 3 | worldgen leaf; `soulium_dust` derives by smelting |
| `soulstone_cobble` | 2 | 1 | worldgen leaf; soulstone and its brick/stair/slab family derive |
| `witherproof_block`, `witherproof_glass` | 20 | 3 | built from unvalued wither skeleton essence; unanchored the printer skips them and leaves holes |

`integration/mysticalagriculture/MysticalAgradditionsCompat.java`

| Item id (`mysticalagradditions:`) | FU | Tier | Source / rationale |
|---|---:|:--:|---|
| `nether_inferium_ore`, `end_inferium_ore` | 20 | 1 | dimension variants, same drop |
| `nether_prosperity_ore`, `end_prosperity_ore` | 4 | 2 | dimension variants, same drop |

**Derived automatically (not pinned):** `<tier>_block` and `<tier>_ingot_block` (nine of the base
at the same tier, exact break-even), `prosperity_ingot` (4 shards + iron, all tier 2), soulstone
bricks/stairs/slabs, witherproof bricks (4 blocks into 4 bricks), the machines, seed bases,
infusion crystals.

**Intentionally UNVALUED:** all 138 crop essences, all seeds, the entire awakened supremium line,
and insanium.

## 3. The two guards

**Vanilla trophies, gated on Agradditions.** `minecraft:nether_star` (1500 @ T7) and
`minecraft:dragon_egg` (10000 @ T7) are priced in `FuValueRegistry` as items that cannot be
farmed; the dragon egg's comment says so in as many words. Agradditions adds tier-6 crops for
both (27 essences into 3 shards or chunks, into 1 item), which makes a T7 spool renewable, and a
T7 spool prints everything at T7 and below. `MysticalAgradditionsCompat` therefore calls
`ModItemTags.blockWinding` on both. This cannot live in the `winder_blacklist` data tag, because
tags are unconditional and would penalise every pack that does not run Agradditions.

Printing is deliberately left alone. It carries a markup, and the only routes to T7 filament are
the two items now barred plus draconium, so there is no profitable print loop to close.

**Insanium, barred both ways.** Its recipe is four supremium essences plus an infusion crystal,
all priced, so the valuator reaches it and hands it roughly 1,100 FU at **T5** (the tier comes
from the diamond in the crystal). That is farm output landing at diamond tier, on a number nobody
chose. Omitting an item does not make it unvalued; only an explicit anchor stops the walk. Barred
instead: winding via the `mysticalagradditions:insanium_` prefix in
`ModItemTags.WINDER_BLACKLIST_ID_PREFIXES` (one entry covers all eleven forms), printing via
eleven `required: false` entries in the `no_print` tag. The cost of the print bar is that a
scanned base containing insanium blocks prints with holes where they were; unprintable blocks
are skipped per block (`recordSkippedBlock`), not treated as a whole-blueprint failure.

## 4. What the anchors were checked against

The valuation rule was replayed over all 485 parseable Mystical Agriculture and Agradditions
recipes (crafting, smelting, stonecutting) using the live vanilla anchors, then every item was
compared against what its ingredients actually cost in base units. That is where the ladder,
gemstone, growth accelerator and ingot anchors come from: without them those four families mint
between 1.47x and 2.34x, and all four are fed directly by farmed essences, so each is an
unbounded loop rather than a rounding artifact. With the anchors in place none of them appear.

**Eighteen derived items still price above their ingredients, and that is deliberate.** The cause
is the valuation rule itself: `RelaxationFuValuator` sums ingredient FU but takes the max
ingredient tier, so any recipe mixing tiers promotes the cheaper ingredient's FU. Running the same
audit over the 1,203 vanilla 1.21.1 recipes as a control shows the effect is **larger in vanilla**
than here: copper bulb 5.63x, detector rail 3.67x, compass 3.50x, clock 3.37x, dropper 2.48x,
ender chest 2.15x, against a Mystical Agriculture worst case of 4.00x (`upgrade_base`, which
needs a diamond per craft). Chasing these inside a mod compat hook would hold Mystical
Agriculture to a standard vanilla does not meet, and would not fix the general case. If the
promotion rule is ever revisited, it is an economy-wide change, not a compat one.

## 5. Open verification (in-game, with both mods installed)

- Confirm `soulstone_cobble` is the mined form and `soulstone` is the smelted one, not the
  reverse. The loot tables suggest cobble, but this was read from recipes, not played.
- Confirm `<tier>_farmland` has no working crafting recipe on the target version (the JSON
  references `minecraft:farmland`, which has no item form in vanilla). If some version does make
  it craftable, the anchors here still win, but the doc claim should be corrected.
- Confirm the growth accelerator recipe still yields 3 on the shipped version; the per-unit value
  assumes it.
- Check that no third-party pack datapack re-enables a crop we assumed disabled. The essence
  recipes carry `mysticalagriculture:crop_enabled` conditions, so a pack can turn individual
  crops off, but not on beyond the registry.

All numbers are **tunable**. Related: `docs/rebalance/{ae2,thermal,tconstruct}.md`,
`.claude/skills/mc3dp-mod-filament-unit-compat/references/fu-model.md`, and the
`modded-fu-compat` memory.
