# Thermal Series 1.20.1 (Team CoFH) — Acquisition-Difficulty Ranking & FU Tier Map

_Research input for MC3DPrint's T1–T8 tier rebalance — the **Thermal Series** soft-dep,
modelled on the draconium compat hook (`integration/draconic/DraconicCompat.java`).
Facts verified against the **1.20.1 branches** of the CoFH GitHub repos (ThermalCore,
ThermalFoundation, ThermalExpansion) — exact item ids and recipe JSON read from source on
2026-06-13 — and the CoFH docs (`oldcofh.github.io`)._

---

## 0. Mod structure & namespaces in 1.20.1 (what to gate on)

Modern Thermal is **unified under one item namespace: `thermal`.** Even though the source is
split across several repos and the *asset folders* differ (`assets/thermal_foundation/...`),
**every item/block registry id is `thermal:*`.** Verified directly from source — the
ThermalFoundation lang keys are `block.thermal.tin_ore`, `item.thermal.tin_ingot`, etc., and
all recipe data lives under `data/thermal/recipes/`.

> **There is no `thermal_foundation:` or `cofh_core:` item id.** `cofh_core` is the shared
> library/registry backbone (energy, fluids, augment framework) and ships **no metals**.
> `thermal_foundation` is an asset/data namespace, not an item namespace.

### Mod ids for `ModList.isLoaded` (the soft-dep gate)

| Mod id | Repo | Role | Holds printable items? |
|---|---|---|---|
| **`thermal`** | ThermalCore + ThermalFoundation | **The one that matters** — all metals, alloys, dusts, gears, fluids, machines, dynamos, ducts register here | **YES — gate on this** |
| `cofh_core` | CoFHCore | Library: RF energy, fluids, augment/holder framework | no |
| `thermal_foundation` | ThermalFoundation | Data/asset pack bundled into `thermal` | no (ids are `thermal:`) |
| `thermal_expansion` | ThermalExpansion | Machines, dynamos, augments — **ids are still `thermal:`** | no separate ns |
| `thermal_innovation` | ThermalInnovation | Tools (drill, saw, capacitor) | no separate ns |
| `thermal_integration` | ThermalIntegration | Cross-mod compat recipes | no |
| `thermal_locomotion` | ThermalLocomotion | Rails, minecarts | no separate ns |

**Recommended gate:** `ModList.get().isLoaded("thermal")`. One id covers Foundation +
Expansion + the parts/alloys we price. (Optionally also register Expansion machine values
behind `isLoaded("thermal_expansion")`, but in practice the base pack `thermal` pulls them
in; machines mostly derive anyway — see §3.)

### Progression outline (the acquisition axis, replacing vanilla's mining-depth axis)

```
ores/raw metals (tin, lead, silver, nickel)      ── shallow/mid mining, vanilla-furnace smelt
   → ingots → gears/plates/blocks                ── vanilla crafting (DERIVE)
   → early alloys (bronze, electrum, invar,       ── dust via vanilla crafting + furnace smelt
       constantan)                                   (DERIVE if smelting-derivation on)
   → SIGNATURE alloys (signalum, lumium,          ── thermal:smelter ONLY (custom → won't derive)
       enderium)                                      ↑ the key ladder; explicit values
   → machines (pulverizer, induction smelter…)    ── machine_frame + rf_coil + gears (DERIVE)
   → dynamos (stirling … numismatic)              ── rf_coil + gears + metal (DERIVE)
   → ducts / augments / fluids                    ── mix of vanilla-craft (derive) + custom
```

The **signature-alloy ladder (signalum → lumium → enderium)** is Thermal's real tech-tier
gate and the headline deliverable of this doc.

---

## How to read each entry

`acquisition path · custom-vs-vanilla recipe (does it DERIVE?) · renewable? · TIER (1–8) ·
proposed explicit FU (only where it won't derive) · justification`. Tier anchors are the
mod's vanilla anchors: T1 cobble(1) / T2 iron(20),copper(10),gold(15) / T3 redstone(4),
quartz(5) / T4 emerald(50),blaze(40) / T5 diamond(50),ender_pearl(40) / T6 netherite(500) /
T7 nether_star(1500) / T8 echo_shard(500),draconium(250).

> **Derivation reminder (from `RecipeFuValuator`):** an item with an explicit value
> short-circuits. Otherwise `value = min over recipes of floor(Σ cheapest-ingredient FU /
> outputCount)`, `tier = max ingredient tier`. **CRAFTING is always consulted; SMELTING and
> STONECUTTING only when `deriveFromSmelting` / `deriveFromStonecutting` config flags are
> on.** Custom recipe types (`thermal:smelter`, `thermal:pulverizer`, `thermal:press`,
> `thermal:crucible`, etc.) are **invisible** to the valuator. So anything whose *only* route
> is a Thermal machine recipe will **not derive** and must be priced explicitly or stay
> NOT_PRINTABLE.

---

## T2 — Base metals: tin, lead, silver, nickel (the iron-tier band)

Thermal's four new metals slot directly into the vanilla **copper/iron/gold** band. All have
full **vanilla furnace/blast smelting** recipes from raw/ore/dust, and standard worldgen
ores (overworld, deepslate variants). They are renewable in the same "unbounded via new
chunks" sense as vanilla ores; tin and silver also have the standard `forge:ores/*` tags so
modpacks farm them via ore-doubling (see §4).

| Item id | Acquisition | Recipe → DERIVE? | Renewable? | Tier | Proposed FU | Justification |
|---|---|---|---|---|---|---|
| `thermal:raw_tin` | mine tin ore (shallow/mid) | base resource | yes (worldgen) | **2** | **8** | tin ≈ copper-band; very common |
| `thermal:tin_ingot` | smelt raw/ore (vanilla furnace) | smelting (derives if flag) — **price explicit** | yes | **2** | **10** | copper-tier value; explicit so it doesn't need smelting-flag |
| `thermal:tin_ore` / `deepslate_tin_ore` | mine | smelting→ingot | yes | **2** | **derive** (≈10) | ore = ingot value |
| `thermal:raw_lead` | mine lead ore | base resource | yes | **2** | **9** | slightly denser than tin, low value |
| `thermal:lead_ingot` | smelt | explicit | yes | **2** | **12** | between copper and iron; key for enderium (§ signature) |
| `thermal:lead_ore` / `deepslate_lead_ore` | mine | smelting→ingot | yes | **2** | **derive** | — |
| `thermal:raw_silver` | mine silver ore | base resource | yes | **2** | **12** | rarer than tin/lead, feeds all 3 signature alloys |
| `thermal:silver_ingot` | smelt | explicit | yes | **2** | **15** | ≈ gold-tier; silver is the common thread of signalum+lumium+enderium |
| `thermal:silver_ore` / `deepslate_silver_ore` | mine | smelting→ingot | yes | **2** | **derive** | — |
| `thermal:raw_nickel` | mine nickel ore | base resource | yes | **2** | **12** | feeds invar/constantan |
| `thermal:nickel_ingot` | smelt | explicit | yes | **2** | **15** | invar/constantan input |
| `thermal:nickel_ore` / `deepslate_nickel_ore` | mine | smelting→ingot | yes | **2** | **derive** | — |

**Derived from the above (all VANILLA crafting → DERIVE automatically, no explicit needed):**

| Item id pattern | Recipe (verified) | Derives to (tier) |
|---|---|---|
| `thermal:<metal>_block` | 9 ingot, `minecraft:crafting_shaped` | 9× ingot @ **T2** (e.g. tin_block ≈ 90) |
| `thermal:raw_<metal>_block` | 9 raw, `crafting_shaped` | 9× raw @ **T2** |
| `thermal:<metal>_nugget` | ingot→9 nuggets / 9 nuggets→ingot | ⌊ingot/9⌋ @ **T2** |
| `thermal:<metal>_gear` | 4 ingot + 1 iron nugget, `crafting_shaped` (verified for tin/copper) | ≈4× ingot @ **T2** |
| `thermal:<metal>_plate` | press (custom) **or** has a crafting route — verify; if press-only, won't derive | see §3 |
| `thermal:<metal>_dust` | **pulverizer only** (`thermal:pulverizer`) — **NO vanilla craft** → **won't derive** | see §4 abundance note |

> **Tin/silver/nickel/lead `_ingot` get explicit values** (above) so the metal economy is
> solid even with smelting-derivation off. Their blocks/gears/nuggets then derive cleanly.

---

## Early alloys: bronze, electrum, invar, constantan (T2–T3 band)

These are the "combine two base metals" alloys. **They DERIVE through vanilla recipe types** —
the dust is made by a `minecraft:crafting_shapeless` recipe, and the ingot has a
`minecraft:smelting` recipe from that dust. So with `deriveFromSmelting = true` the ingots
price themselves; with it off, price the ingots explicitly (cheap).

Verified recipes (1.20.1 source):

| Alloy | Dust recipe (`crafting_shapeless`, DERIVES) | Ingot route | Tier | Proposed FU (ingot, explicit fallback) |
|---|---|---|---|---|
| **Bronze** | 3 copper dust + 1 tin dust → 4 `thermal:bronze_dust` | dust→ingot `minecraft:smelting` (also `thermal:smelter` 3 copper+1 tin→4) | **2** | **11** (≈ (3·copper+tin)/4) |
| **Electrum** | 1 gold dust + 1 silver dust → 2 `thermal:electrum_dust` | smelting; also smelter 1 gold+1 silver→2 | **2** | **15** (gold+silver /2) |
| **Invar** | 2 iron dust + 1 nickel dust → 3 `thermal:invar_dust` | smelting; smelter 2 iron+1 ferrous→3 | **2–3** | **18** |
| **Constantan** | copper dust + nickel dust → 2 `thermal:constantan_dust` | smelting; smelter copper+nickel→2 | **2–3** | **13** |

> **Abundance caveat:** the dusts (`bronze_dust` etc.) are themselves crafted from **metal
> dusts**, and metal dust comes from the **pulverizer (custom, won't derive)**. So the dust
> chain only derives if the *constituent metal dusts* are priced. Either (a) leave metal
> dusts unpriced → early-alloy dusts won't derive → price the **alloy ingots** explicitly
> (recommended, values above), or (b) price metal dusts ≈ ⌊ingot × 1.0⌋ and let everything
> flow. **Recommend (a)** — fewer entries, and it keeps the pulverizer's ore-doubling out of
> the FU economy (see §4).

---

## The SIGNATURE ALLOY LADDER — signalum → lumium → enderium (the key deliverable)

These three are Thermal's tech "tiers." **Their ONLY production route is the
`thermal:smelter` (Induction Smelter) custom recipe type** — confirmed from source, type
field `"type": "thermal:smelter"`. **The valuator cannot read this recipe type, so these
WILL NOT derive.** They **must be priced explicitly** (like the draconium chain) or they fall
through to NOT_PRINTABLE.

Exact 1.20.1 induction-smelter recipes (read from
`data/thermal/recipes/machines/smelter/smelter_alloy_*.json`):

| Alloy | Induction Smelter recipe (verified 1.20.1) | Implied vanilla-cost of inputs | Tier | Proposed FU (ingot) |
|---|---|---|---|---|
| **Signalum** | 3 copper + 1 silver + **4 redstone** → **4** `thermal:signalum_ingot` (12 000 RF) | (3·10 + 15 + 4·4)/4 = **15.25** | **T4** | **35** |
| **Lumium** | 3 tin + 1 silver + **2 glowstone** → **4** `thermal:lumium_ingot` (12 000 RF) | (3·10 + 15 + 2·glow)/4 ≈ **14** | **T4** | **40** |
| **Enderium** | 3 lead + 1 **diamond dust** + 2 **ender pearls** → **2** `thermal:enderium_ingot` (16 000 RF) | (3·12 + 50 + 2·40)/2 = **83** | **T5** | **90** |

**Why these tiers (rarity-first, utility-override):**

- **Signalum @ T4** — gated on **redstone** (the redstone-control alloy) + silver. Raw-input
  cost is low (~15), but Signalum is the **machine-augment / redstone-control tier** of
  Thermal: it's "the common ingredient for somewhat-advanced devices," gates machine locks
  and the **flux ducts / RF transport** that define mid-game. **Utility override bumps it to
  T4** (above its ~T2 raw cost) so it sits at the emerald/blaze progression gate, not the
  iron band. FU **35** keeps it just under emerald(50).
- **Lumium @ T4** — gated on **tin + silver + glowstone (Nether)**. Raw cost ~14, but the
  glowstone dependency adds a Nether gate, and it's the "controllable-light"/cell tier.
  Pair it with signalum at **T4**, FU **40**. (Lumium has few uses, so even-with-utility it
  doesn't out-rank signalum's transport role; equal T4 is correct.)
- **Enderium @ T5** — **the end-game alloy.** Recipe literally consumes **diamond dust + 2
  ender pearls** per 2 ingots, plus lead — so its raw cost (~83) already lands in the
  diamond band, and CoFH docs call it "the highest tiers of machines and devices." This is
  the **diamond/ender-pearl-tier** signature alloy → **T5**, FU **90** (just above diamond's
  50, reflecting the multi-material end-game craft). This is the Thermal equivalent of the
  draconium step in the DE chain.

**The ladder, locked:**

```
T2  base metals + early alloys (bronze/electrum/invar/constantan)  — iron-tier band
T4  SIGNALUM ≈ LUMIUM      — redstone/glowstone-gated tech tier (emerald/blaze band)
T5  ENDERIUM               — diamond + ender-pearl end-game alloy (diamond band)
```

**Explicit entries to register (signature alloys + their derived forms):**

| Item id | FU | Tier | Note |
|---|---|---|---|
| `thermal:signalum_ingot` | 35 | 4 | explicit (smelter-only) |
| `thermal:signalum_block` | 315 | 4 | 9× — **derives** from ingot (vanilla 3×3) once ingot priced |
| `thermal:signalum_nugget` | 3 | 4 | derives |
| `thermal:signalum_gear` | ~140 | 4 | derives (4 ingot+nugget) |
| `thermal:signalum_dust`/`_plate`/`_coin` | — | 4 | press/pulverizer custom → may not derive; price `_dust`≈35 if needed |
| `thermal:lumium_ingot` | 40 | 4 | explicit |
| `thermal:lumium_block` | 360 | 4 | derives |
| `thermal:lumium_nugget` | 4 | 4 | derives |
| `thermal:lumium_gear` | ~160 | 4 | derives |
| `thermal:enderium_ingot` | 90 | 5 | explicit |
| `thermal:enderium_block` | 810 | 5 | derives (9×) |
| `thermal:enderium_nugget` | 10 | 5 | derives |
| `thermal:enderium_gear` | ~360 | 5 | derives |
| `thermal:enderium_dust`/`_plate`/`_coin` | — | 5 | custom-recipe forms; price `_dust`≈90 if you want them printable |

> **Anti-derive trap:** because the smelter recipe is invisible, the ingot has **no readable
> recipe at all** → without an explicit entry it is NOT_PRINTABLE and, worse, every block/
> gear/nugget that depends on it also fails to derive. **Register the three `_ingot` values
> first** — everything downstream then derives via the vanilla block/gear/nugget recipes.

---

## 3. Machines / dynamos / ducts / devices — mostly DERIVE

Almost every Thermal **machine and dynamo is built with vanilla `minecraft:crafting_shaped`**
from a small set of building blocks, all of which themselves derive:

Verified crafting recipes (1.20.1):
- `thermal:machine_frame` = iron + `forge:glass` + **tin gear** (`crafting_shaped`) → derives
  (~iron + glass + 4 tin ingot).
- `thermal:rf_coil` = gold + redstone (`crafting_shaped`) → derives (~gold + 2 redstone).
- `thermal:machine_pulverizer` = `machine_frame` + 2 copper gear + `rf_coil` + piston + flint
  (`crafting_shaped`) → **derives**, tier = max(input tiers) ≈ **T2–T3**.
- `thermal:dynamo_stirling` = `rf_coil` + iron gear + iron + redstone + stone
  (`crafting_shaped`) → **derives**, ≈ **T2**.

So **the whole machine/dynamo line derives for free** once base metals/gears are priced. No
explicit entries needed for: pulverizer, induction smelter, sawmill, smelter, insolator,
centrifuge, press, crucible, refinery, brewer, bottler, chiller, furnace, charger; the
dynamos (stirling, compression, magmatic, numismatic, lapidary, disenchantment, gourmand);
energy cells; fluid cells; the device family (tree extractor, fisher, collector, breeder,
etc.). They price up to the tier of their most advanced ingredient (a numismatic dynamo or
resonant-tier energy cell that uses **enderium** will correctly land at **T5**).

**Tier of each machine just follows its inputs** — base machines ~T2–T3, anything using
signalum ~T4, anything using enderium ~T5. No manual tiering required.

### What does NOT derive in this section (flag list)

These use **custom recipe types** (not crafting) as their *only* route — price explicit or
leave NOT_PRINTABLE:

| Item / group | Why it won't derive | Recommendation |
|---|---|---|
| Metal **`_dust`** (tin/lead/silver/nickel/copper/iron/gold) | pulverizer-only (`thermal:pulverizer`), no vanilla craft | **leave unpriced** (anti-launder, §4) or price ≈ ingot |
| **`_plate`** parts | press-only (`thermal:press`) | leave unpriced unless a plate recipe needs them |
| **`rich_slag` / `slag`** | pulverizer/smelter byproduct | leave unpriced (byproduct) |
| **Fluid-derived items** (e.g. some glass, redstone/glowstone/ender "energized" forms) | fluid recipes / smelter | price the *block* form explicitly if desired |
| Augments (`thermal:*_augment`) | mix of crafting + custom | most derive; spot-check the high-tier ones |

---

## 4. Abundance / anti-launder flags

Thermal's defining feature is **resource multiplication**, which is exactly the laundering
threat the FU economy guards against. Flag these:

1. **Pulverizer ore-doubling (the big one).** `thermal:pulverizer` turns 1 ore → **2 dust**,
   and the **Induction Smelter with sand/rich-slag** can push ore→**>2 ingots**. If metal
   **dusts** were priced at ~ingot value, a player could pulverize cheap ore into dust and
   the FU economy would value the doubled output as if it were two ingots — *but FU never
   converts up and winding needs exact tier, so this isn't a direct launder of FU.* The real
   risk is **under-pricing dust**: a T2 dust spool printing T2 dusts is fine, but if dust
   feeds a higher-tier recipe cheaply it could underprice that output. **Mitigation: leave
   metal dusts UNPRICED** (they're pulverizer-only and don't need to be printable). This also
   keeps the doubling mechanic entirely outside the FU graph.
2. **Tree extractor → rubber/latex/sap (`thermal:latex_bucket`, `thermal:rubber`,
   `thermal:cured_rubber`).** Fully renewable tree farm. These are **T1-band bulk** at most;
   ensure rubber/cured_rubber sit at **T1–T2** and never feed a higher-tier print. Most are
   crafting-derived from latex (a fluid) → likely won't derive → **leave unpriced or price
   T1 (~3–5)**.
3. **Insolator (phytogro) crop/sapling farming + `thermal:phytogro`/`bioblend`.** Renewable
   fertilizer that mass-produces crops, saplings, even some ores via " pure " seeds. Keep all
   phyto outputs at the tier of the *vanilla* item they produce — never let phytogro itself
   sit at a tier that could print a rarer crop output. `phytogro`/`bioblend` → **T1 (~3)**.
4. **Mob "rod/powder" farms — `blitz_rod`, `blizz_rod`, `basalz_rod` + powders.** Renewable
   via apparatus/spawning. They map to elemental dusts (blaze-like). Tier at **T3–T4**
   (blaze-rod band, current `blaze_rod=40@4`); these are crafting/spawn-gated. Powders derive
   from rods. **Do not under-tier** — they feed pyrotheum/cryotheum/aerotheum/petrotheum.
5. **Pyrotheum / Cryotheum / Aerotheum / Petrotheum dust.** Crafted (blaze/blizz/blitz/basalz
   powder + sulfur/redstone/niter + …). Mostly **crafting → derive** from the rod/powder
   tiers (~T3–T4). Pyrotheum dust is an Enderium-smelter input but enderium is priced
   explicitly, so no leak. Fine to let derive.
6. **`sawdust` / `sawmill` wood-doubling** → T1 bulk; leave unpriced or T1.
7. **Sulfur, niter, apatite, cinnabar, ruby, sapphire** (new ores/dusts): ruby & sapphire are
   gem ores → **T2** (~12–15, gold-band); sulfur/niter/apatite are industrial dust ores →
   **T1–T2** (~3–8); cinnabar (mercury, ore-tripling reagent) → **T2 (~10)** but it's an
   Induction-Smelter reagent so usually pulverizer/smelter-sourced → may stay unpriced.

> **Net guidance:** the cleanest anti-launder posture is to **price only ingots/ores/gems and
> the three signature alloy ingots explicitly, and leave all pulverizer/press/fluid
> byproducts (dusts, plates, slag, latex) UNPRICED.** That keeps Thermal's multiplication
> mechanics entirely out of the FU graph while still letting every *block* a player would
> want to print derive correctly.

---

## 5. Utility overrides (progression-defining items bumped above raw cost)

Per the vanilla principle "utility OVERRIDES bump cheap-but-powerful items up":

| Item | Raw-cost tier | Override tier | Why |
|---|---|---|---|
| **Signalum ingot** | ~T2 (raw ≈15) | **T4** | redstone-control / flux-duct / augment tech gate |
| **Lumium ingot** | ~T2 (raw ≈14) | **T4** | Nether-glowstone gate + light/cell tier |
| **Enderium ingot** | ~T5 (raw ≈83) | **T5** (no override needed) | diamond+ender end-game alloy; raw cost already places it |
| **Induction Smelter** (`thermal:machine_smelter`) | derives ~T2 | leave derived; it's *the* alloy gate, but its frame/coil inputs are cheap — acceptable | the machine being cheap is fine; the **alloys** it makes are the gated outputs |
| **Pulverizer** (`thermal:machine_pulverizer`) | derives ~T2–T3 | leave derived | ore-doubling gate, but printing the machine doesn't launder; the *dusts* are kept unpriced |
| **Numismatic/Resonant-tier dynamos & energy cells** | derive to **T5** (use enderium) | leave derived | correctly inherit enderium's T5 |
| **Blitz/Blizz/Basalz rods** | ~T3 | **T4** | feed elemental thermo-dusts; blaze-rod-band |

The machines themselves don't need utility bumps — printing a pulverizer or induction smelter
is harmless (you still need power and ore). The override matters for **signalum/lumium**,
whose raw cost badly understates their progression role; pinning them at **T4** is the single
most important tiering decision in this doc after enderium's T5.

---

## Summary — explicit entries for `ThermalCompat.onCommonSetup` (mirror `DraconicCompat`)

Gate: `ModList.get().isLoaded("thermal")`. Register by `ResourceLocation` via
`FuValueRegistry.registerApiItemValue(id, fu, tier)`. **Only these need explicit values**
(everything else derives via vanilla crafting, or is intentionally left unpriced):

```
// Base metal ingots (T2) — so the chain is solid with smelting-derivation off
thermal:tin_ingot       = 10 @2
thermal:lead_ingot      = 12 @2
thermal:silver_ingot    = 15 @2
thermal:nickel_ingot    = 15 @2
// Raw metals (T2)
thermal:raw_tin         = 8  @2
thermal:raw_lead        = 9  @2
thermal:raw_silver      = 12 @2
thermal:raw_nickel      = 12 @2
// Early alloy ingots (T2) — optional; derive if deriveFromSmelting=true
thermal:bronze_ingot    = 11 @2
thermal:electrum_ingot  = 15 @2
thermal:invar_ingot     = 18 @2   // (T3 acceptable)
thermal:constantan_ingot= 13 @2
// SIGNATURE ALLOYS (the ladder) — MUST be explicit (smelter-only, won't derive)
thermal:signalum_ingot  = 35 @4
thermal:lumium_ingot    = 40 @4
thermal:enderium_ingot  = 90 @5
// New gems (T2)
thermal:ruby            = 14 @2
thermal:sapphire        = 14 @2
```

Then **let derivation handle**: all `_block` (9×), `_nugget` (÷9), `_gear` (4 ingot+nugget),
ores (→ingot), and every machine/dynamo/duct/device (machine_frame + rf_coil + gears). And
**leave UNPRICED** (anti-launder / byproducts): all `_dust`, `_plate`, `slag`/`rich_slag`,
`latex`/`rubber`/`sap`, `phytogro`/`bioblend`, sawdust — pulverizer/press/fluid outputs that
don't need to be printable.

**One-line takeaways:**
1. **Namespace is `thermal`** for every item — gate on `isLoaded("thermal")`. No
   `thermal_foundation:`/`cofh_core:` item ids exist.
2. **Signalum/Lumium = T4, Enderium = T5** — the ladder. All three are **induction-smelter-
   only (`thermal:smelter`)** so they **don't derive** and need explicit FU.
3. **Base metals (tin/lead/silver/nickel) = T2**, iron-band; early alloys (bronze/electrum/
   invar/constantan) T2–T3 and **derive** via vanilla dust-craft + furnace-smelt.
4. **Machines/dynamos/ducts derive for free** (all `minecraft:crafting_shaped` from
   machine_frame/rf_coil/gears).
5. **Leave dusts/plates/slag/rubber/phyto UNPRICED** to keep Thermal's ore-doubling, tree,
   and crop farms out of the FU economy (anti-launder).

---

_Sources: CoFH GitHub 1.20.1 branches — `CoFH/ThermalCore`, `CoFH/ThermalFoundation`,
`CoFH/ThermalExpansion` (item ids from `assets/thermal*/lang/en_us.json`; recipes from
`data/thermal/recipes/...`, including `machines/smelter/smelter_alloy_{signalum,lumium,
enderium}.json`, `bronze_dust_4.json`, `*_from_dust_smelting.json`, `machine_frame.json`,
`rf_coil.json`, `machine_pulverizer.json`, `dynamo_stirling.json`, `parts/*_gear.json`,
`storage/tin_block.json`); CoFH docs `oldcofh.github.io/docs/thermal-foundation/alloys/*` and
`.../thermal-expansion/machines/induction-smelter/`. Read 2026-06-13. Tier anchors cross-
checked against `src/main/java/com/pgmacdesign/mc3dprint/fu/FuValueRegistry.java` and the
draconium pattern in `integration/draconic/DraconicCompat.java`._
