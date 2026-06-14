# Current FU Economy Map (pre-rebalance)

_Snapshot of the FU/tier economy as it stands before the tier rebalance (workstream #2).
Generated from a read of `FuValueRegistry.java`, `RecipeFuValuator.java`,
`MinecraftRecipeIndex.java`, `FuConversion.java`, the `filament/tier_*.json` tags, and
the printer's strict/itemless gating in `PrinterBlockEntity.java`._

**Source of truth:** the explicit value list is
`FuValueRegistry.defaultEntries()` — each entry is `<id>=<fu>@<tier>`, where `<tier>`
is the **only** thing that sets an item's tier. Precedence:
**explicit config item > explicit config tag > API registration > recipe-derived > unknown**.
Tier is clamped to **1–8** (`FuValue` / `clamp()` in `FuValueRegistry`).

> **Drift finding (read this first).** The `data/mc3dprint/tags/items/filament/tier_*.json`
> files are **NOT consumed by any Java code** — nothing reads them for FU/tier resolution.
> The tier an item prints/winds at comes entirely from the `@tier` suffix in
> `defaultEntries()`. The tag files are documentary/search-grouping only, and they have
> **already drifted** from the real values: `tier_4.json` lists `diamond` and `emerald`,
> but `defaultEntries()` has `emerald=50@4` and **`diamond=50@5`**. There is also **no
> `tier_8.json` file at all**, and `tier_5.json` is empty. The rebalance must decide
> whether to make these tags real (wire them into resolution) or delete them — right now
> they are a lie waiting to mislead.

---

## 1. Per-tier table of every EXPLICITLY-valued item

Every entry below is a hardcoded anchor in `defaultEntries()`. Tag entries (`#minecraft:...`)
price every member of the tag at that FU/tier. Sparsity is the headline: most tiers have
a handful of anchors, **T8 has zero**, T5 has one, T3 has four.

### T1 — bulk fill + stone/wood (41 anchors; the dense tier)

| id | fu | notes |
|----|----|-------|
| `minecraft:cobblestone` | 1 | bulk fill |
| `minecraft:dirt` | 1 | bulk fill |
| `minecraft:gravel` | 1 | bulk fill |
| `minecraft:sand` | 1 | bulk fill |
| `minecraft:soul_sand` | 1 | bulk fill |
| `minecraft:soul_soil` | 1 | bulk fill |
| `minecraft:clay_ball` | 1 | bulk fill |
| `minecraft:netherrack` | 1 | bulk fill |
| `minecraft:coal` | 2 | base so `coal_block` derives to 18@1 (was explicit, now derives) |
| `minecraft:stone` | 3 | stone family |
| `minecraft:sandstone` | 3 | stone family |
| `minecraft:smooth_stone` | 3 | stone family |
| `minecraft:stone_bricks` | 3 | stone family |
| `minecraft:andesite` | 3 | stone family |
| `minecraft:diorite` | 3 | stone family |
| `minecraft:granite` | 3 | stone family |
| `minecraft:calcite` | 3 | stone family |
| `minecraft:tuff` | 3 | stone family |
| `#minecraft:logs` (tag) | 3 | all logs |
| `#minecraft:planks` (tag) | 3 | all planks |
| `minecraft:glass` | 5 | processed building block |
| `minecraft:terracotta` | 5 | processed building block |
| `#minecraft:wool` (tag) | 5 | all wool colors |
| `minecraft:nether_bricks` | 5 | processed building block |
| `minecraft:quartz_block` | 5 | processed building block |
| `minecraft:white_concrete` … `minecraft:black_concrete` | 5 | all **16** concrete colors |

_(Concrete is 16 individual entries; collapsed for readability. Add 16 to the per-color
count for the true anchor total.)_

### T2 — base metals & gems (7 anchors)

| id | fu | notes |
|----|----|-------|
| `minecraft:copper_ingot` | 10 | base material |
| `minecraft:amethyst_shard` | 10 | base material |
| `minecraft:lapis_lazuli` | 10 | base material |
| `minecraft:gold_ingot` | 15 | metal |
| `minecraft:iron_ingot` | 20 | metal |
| `minecraft:gold_nugget` | 1 | ingot/9 floored (lossy by design) |
| `minecraft:iron_nugget` | 2 | ingot/9 floored (lossy by design) |

### T3 — redstone / mob drops (4 anchors — THIN)

| id | fu | notes |
|----|----|-------|
| `minecraft:redstone` | 4 | redstone dust |
| `minecraft:slime_ball` | 30 | mob drop |
| `minecraft:magma_cream` | 30 | mob drop |

_(Only three lines, but `redstone` + the two slimes = the entire explicit T3 set. This is
one of the emptiest "real" tiers.)_

### T4 — emerald (1 anchor)

| id | fu | notes |
|----|----|-------|
| `minecraft:emerald` | 50 | villager-renewable, kept as the T4 gem |

### T5 — diamond (1 anchor — NEAR-EMPTY)

| id | fu | notes |
|----|----|-------|
| `minecraft:diamond` | 50 | mined; gated one tier above emerald (2026-06-11 decision) |

### T6 — netherite family (3 anchors)

| id | fu | notes |
|----|----|-------|
| `minecraft:netherite_ingot` | 500 | `netherite_block` derives to 4500@6 (9×) |
| `minecraft:netherite_scrap` | 125 | |
| `minecraft:ancient_debris` | 125 | |

### T7 — boss/endgame singletons (2 anchors)

| id | fu | notes |
|----|----|-------|
| `minecraft:nether_star` | 1500 | |
| `minecraft:dragon_egg` | 2500 | |

### T8 — **EMPTY** (0 anchors)

No explicit entries, no tag file, nothing derives here (nothing is anchored at T8 to
flow a max-ingredient-tier of 8 upward). **T8 is a completely unused tier today.**

---

## 2. What derives into each tier

Derivation rule (`RecipeFuValuator`): for an item with no explicit/API value,
`fu = floor( sum(cheapest ingredient FU) / outputCount )` (min 1), and
`tier = max ingredient tier of the winning recipe`. Base (explicit) values
short-circuit — anchored items never walk recipes. Crafting is always consulted;
smelting and stonecutting are on by default. So **every tier inherits its derived
population from its anchors** — a tier with no anchor gets almost nothing derived into it.

### Storage blocks (9× / 4× the base, at the base's tier)
- **T1:** `coal_block` = 9 × coal(2) = **18 @ T1**.
- **T2:** `copper_block` = 9 × copper(10) = **90 @ T2**; `iron_block` = 9 × iron(20) =
  **180 @ T2**; `gold_block` = 9 × gold(15) = **135 @ T2**; `lapis_block` = 9 × lapis(10) =
  **90 @ T2**; `amethyst_block` = 4 × shard(10) = **40 @ T2**.
- **T4:** `emerald_block` = 9 × emerald(50) = **450 @ T4**.
- **T5:** `diamond_block` = 9 × diamond(50) = **450 @ T5** (the canonical example in
  `FU-VALUES-AND-COMPAT.md`).
- **T6:** `netherite_block` = 9 × netherite_ingot(500) = **4500 @ T6**.

### Stairs / slabs / walls (stonecutting + crafting, tier inherited from material)
- **T1:** `stone_stairs`, `stone_brick_slab`, `sandstone_wall`, `cobblestone_stairs`,
  `oak_stairs`/`*_slab` (planks T1) — all derive at **T1** at fractions of the base
  (stonecutting 1→1, slab craft 3 in → 6 out, etc.).
- **T1:** `polished_*`, `chiseled_*`, `smooth_*`, brick variants of stone-family blocks —
  all inherit **T1**.

### Tools / processed items
- **T1:** wooden tools, `stick` (planks-derived; **stick is winder-blacklisted** — see §4).
- **T2:** iron/gold/copper tools and blocks-of-ingots derive at **T2** (max-ingredient-tier
  flows from the ingot).
- Glass panes, glass bottles → **T1** (glass T1). Bricks block, `brick` item → derive at
  **T1** from clay.

### Tiers that end up sparse or EMPTY of derived population

| tier | anchors | derived population | verdict |
|------|---------|--------------------|---------|
| T1 | many | huge (all stone/wood/concrete variants, storage of coal, tools) | dense |
| T2 | 7 | good (all metal/copper/lapis/amethyst storage + tools) | healthy |
| **T3** | 3 | **almost none** — redstone components (`redstone_block` = 9×4 = 36@T3, `repeater`/`comparator` derive at T3 via redstone+stone) but no building blocks | **thin** |
| T4 | 1 | only `emerald_block` (450@T4) | sparse |
| **T5** | 1 | only `diamond_block` (450@T5) and diamond tools | **near-empty** |
| **T6** | 3 | only `netherite_block` + netherite tools/armor (derive at T6) | sparse (endgame, OK-ish) |
| **T7** | 2 | **nothing derives in** — `nether_star`/`dragon_egg` are terminal, not crafting ingredients of printable blocks (beacon derives at T7 but is itemed/special) | **empty downstream** |
| **T8** | 0 | **nothing** | **completely unused** |

**Bottom line:** T3, T5, T7 and T8 are the structurally weak tiers. T3 has no building-block
identity, T5 hangs entirely off diamond, and T7/T8 have no material band of their own — the
top of the curve is two boss singletons and an empty tier.

---

## 3. Unvalued common blocks (no explicit AND no derivable value)

These are **itemed** blocks frequently used in real builds that currently resolve to
**no value** — no explicit anchor, and no recipe chain that bottoms out in an anchored
material. In **strict mode (default, `unknownBlocksPrintable=false`)** a blueprint
containing any of these is **NOT_PRINTABLE on every tier**. (Itemless blocks — farmland,
crops, water, wall torches, redstone wire, fire — print free and are *not* the concern
here; see §4.)

Likely-unvalued itemed blocks (need verifying against live recipe data, but have no anchor
upstream):

- **Naturally-generated stone/earth with no craft recipe:** `obsidian`, `crying_obsidian`,
  `bedrock` (creative), `end_stone`, `bone_block`*, `magma_block`*, `dripstone_block`,
  `moss_block`, `mud`, `packed_mud`, `mud_bricks`, `rooted_dirt`, `mycelium`, `podzol`,
  `coarse_dirt`* — many of these only obtainable by mining/silk-touch, no anchored craft
  ingredient. (* some have craft recipes but from un-anchored inputs.)
- **Wood that bottoms out only at logs/planks tags** generally DOES derive (planks are
  anchored), but **stripped logs, wood/hyphae (6-face) blocks** may or may not, depending
  on whether their recipe inputs are tagged `#logs`. Worth auditing per-wood.
- **Plant/organic building blocks:** `mushroom_stem`, `*_mushroom_block`, `hay_block`
  (wheat — wheat unanchored), `dried_kelp_block`, `honeycomb_block`, `sponge`/`wet_sponge`,
  `pumpkin`/`carved_pumpkin`, `melon`.
- **Nether/End decor:** `shroomlight`, `nether_wart_block`, `warped_wart_block`,
  `glowstone` (glowstone_dust unanchored), `sea_lantern` (prismarine/crystals unanchored),
  `prismarine`/`prismarine_bricks`/`dark_prismarine`, `purpur_block`/`purpur_pillar`
  (popped chorus fruit unanchored), `end_stone_bricks`.
- **Ice family:** `ice`, `packed_ice`, `blue_ice` (blue_ice IS craftable from packed_ice,
  but packed_ice from ice — all bottom out at un-anchored `ice`).
- **Coral & sea blocks:** all coral blocks, `tube_coral_block` etc.
- **Glazed terracotta (16 colors):** smelting from dyed terracotta — terracotta IS T1
  anchored, so these likely **derive at T1**; verify, but they may be fine.
- **Functional/decorative itemed blocks:** `bookshelf` (books from leather+paper, both
  unanchored), `bricks` (clay → brick → bricks; clay_ball is anchored at 1@T1, so **bricks
  likely derives** — verify), `target`, `note_block`/`jukebox` (need diamond/redstone +
  wood; may derive), `lodestone` (netherite — derives T6), `respawn_anchor` (crying
  obsidian + glowstone — unanchored).

**Action for rebalance:** the high-frequency offenders that should get explicit anchors so
common builds stop hitting NOT_PRINTABLE are likely: `obsidian`, `glowstone`, `sea_lantern`,
`prismarine*`, `purpur*`, `end_stone`/`end_stone_bricks`, `ice`/`packed_ice`/`blue_ice`,
`honeycomb_block`, `hay_block`, `bone_block`, `dripstone_block`, `moss_block`, `mud_bricks`,
`shroomlight`, `nether_wart_block`. Most are natural-spawn or chain to an un-anchored
primary, so derivation can't save them.

---

## 4. Constraints the rebalance MUST respect

These are hard rules baked into the live code. Breaking any of them re-opens an exploit or
breaks gametests.

1. **Exact-tier winding** (`FuConversion.canWindInto`: `spoolTier == materialTier`). A
   material winds **only** into a spool of its *exact* tier — netherite (T6) needs a T6
   spool, cobblestone (T1) a T1 spool. You can't wind a low-tier material into a high-tier
   spool. **Implication:** if the rebalance moves an item's tier, its winding target moves
   with it; there's no slack.

2. **Down-only spending / print-down** (`FuConversion.canCover`: `spoolTier >= costTier`).
   A spool pays costs at or below its own tier, **never above**. High-tier FU covers
   lower-tier print costs at the compounded ratio; low-tier FU contributes **nothing**
   toward higher-tier costs. Together with (1) this is what stops cobblestone farming from
   ever financing a high-tier print.

3. **Conversion ratio = 4** (config `filamentConversionRatio`, default **4**, range 1–64).
   1 FU at tier N is worth `ratio^(N-1)` base (T1) units (`FuConversion.unitWorth`).
   Down-conversion floors. With ratio 4 the tier gaps compound *fast* — a T7 FU is worth
   4^6 = 4096 T1 FU. The rebalance's per-tier FU numbers interact with this: spacing tiers
   too far apart in raw FU on top of the 4× tier multiplier can make high tiers absurd.

4. **Winder blacklist** (`mc3dprint:winder_blacklist`, tag at
   `data/mc3dprint/tags/items/winder_blacklist.json`, read at runtime via
   `stack.is(ModItemTags.WINDER_BLACKLIST)`). Items here can still be **printed** (and keep
   their FU value) but can **never be wound** back into filament — closes the craft-down
   laundering seam (the canonical case: 1 log = 3 FU → 8 sticks × 1 FU = 8 FU minted from
   rounding). **Ships with only `minecraft:stick` today.** The rebalance is explicitly
   expected to extend this list with the other launder-prone micro-crafts (wooden buttons,
   pressure plates, and any 2-in/many-out craft where `outputCount` rounding mints FU).
   It's a pure data tag — no Java change needed to extend it.

5. **Itemless blocks print free** (`PrinterBlockEntity.isStructuralItemless`: non-air block
   whose `asItem() == AIR`). Farmland, crops, water, wall torches, redstone wire, fire, etc.
   have no obtainable item, so there's no FU to charge and no exploit vector — they're
   always printable as free matter (this is what makes farms/decorated builds print whole).
   The rebalance must keep this carve-out; only **itemed** blocks participate in the FU/tier
   economy and strict-mode gating.

6. **Storage blocks derive 9× / 4× their base, at the base's tier** (no hardcoded entries).
   `diamond_block` = 9 × diamond, `amethyst_block` = 4 × shard, `netherite_block` = 9 ×
   ingot, etc. The rebalance should **not** re-hardcode these — moving the base material's
   FU/tier automatically re-prices every storage/compressed block derived from it. (If a
   specific block needs to break the 9× rule, add an explicit override — explicit beats
   derived.)

7. **Current top-of-curve tier assignments** (the partial decisions already made, to build
   on): **netherite family = T6**, **diamond = T5**, **emerald = T4**, **nether star = T7**,
   **dragon egg = T7**. (Note: the documentary `filament/tier_*.json` tags disagree —
   `tier_4.json` still lists diamond — and must be reconciled or deleted.)

8. **Strict mode is the default** (`unknownBlocksPrintable=false`). Any itemed block left
   without an explicit/API/derived value makes whole blueprints un-printable. The rebalance's
   coverage goal (§3) directly determines how many real builds are printable at all.

### Ripple targets (where moving a tier breaks/asserts things)
- **GameTests** assert specific tiers: `TierGatingGameTests`, `FuTierEconomyGameTests`,
  `RecipeDerivationGameTests` (62 tests currently green — re-run after any tier move).
- **`filament/tier_*.json`** tags (documentary today, drifted — reconcile or delete).
- **Blueprint disc tier label** + **disc tier readout** (player-facing; see roadmap).
- **Multiblock corner blocks** for T6/T7 still need assigning (roadmap workstream).
- **Winder exact-tier rule + printability gating** — both read tier directly from FuValue.
