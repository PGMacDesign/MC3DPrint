# Applied Energistics 2 — FU/Tier Assignments (Forge 1.20.1)

_Soft-dep economy spec for MC3DPrint, mirroring the vanilla rebalance principles and the
Draconic Evolution compat pattern (`integration/draconic/DraconicCompat.java`). Nothing here
is implemented yet — this is the spec to approve, then wire into an `Ae2Compat` hook gated on
`ModList.isLoaded("ae2")`._

All item ids, recipe types, and mechanics below were verified against the AE2 **`forge/1.20.1`**
source branch (`appeng/api/ids/AE{Item,Block,Part}Ids.java`, the generated recipe JSONs under
`data/ae2/recipes/`, and the 1.20.1 player guide at `guide.appliedenergistics.org/1.20.1`).

---

## 0. Principles applied (recap)

- **Tier = acquisition difficulty (rarity-first); utility overrides bump up.**
- **ABUNDANCE rule (the load-bearing one for AE2).** Certus quartz is **renewable/farmable**
  (budding-block + Growth Accelerator = an automatable, infinite crystal farm). Print-down +
  exact-tier winding means a farmable resource placed too high lets a player wind infinite
  certus into a high-tier spool and launder it into rarer vanilla goods. So the **certus chain
  is capped low** (T2/T3), aligned with vanilla **amethyst (T2)** — another geode-grown renewable
  crystal.
- **Value base resources explicitly; everything STANDARD-crafted derives automatically.** AE2's
  entire ME-network, storage-cell, cable, energy, and autocrafting tree is plain
  `minecraft:crafting_shaped/shapeless` — our `RecipeFuValuator` walks it for free **once the
  custom-recipe leaves are valued**.
- **Custom recipe types can't be auto-derived.** AE2 gates progression behind `ae2:inscriber`
  (processors, printed circuits), `ae2:charger` (charged certus), and `ae2:transform`
  (fluix, budding tiers, entangled singularity). Our valuator only reads vanilla
  crafting/smelting/stonecutting — **these leaves need explicit values or they (and everything
  above them) stay unprintable.**
- **Soft-dep:** values registered ONLY when AE2 is loaded, by `ResourceLocation`, exactly like
  `DraconicCompat`. Nothing goes in the vanilla config default list.

---

## 1. AE2 1.20.1 progression + namespace confirmation

| Fact | Value | Verified |
|------|-------|----------|
| **Mod id** (`ModList.isLoaded`) | **`ae2`** | `mods.toml` `modId="ae2"`; `AEConstants.MOD_ID = "ae2"`. NOT `appliedenergistics2` (that's only the legacy id / jar basename). |
| **Item namespace** | **`ae2`** | `AEItemIds.id()` / `AEBlockIds.id()` build `new ResourceLocation("ae2", path)`. All ids are `ae2:<path>`. |

**Progression spine:** mine/farm **certus quartz** (budding blocks, amethyst-like) → **charge**
it in the Charger → throw charged certus + redstone + nether quartz in water to make **fluix**
→ loot processor **presses** from **meteorites** (sky stone) → **inscribe** printed circuits and
**press** the three **processors** → those processors gate **ME networks** (controller, drive,
cables, terminals) → **storage cells** (1k→4k→16k→64k→256k) → **autocrafting** (crafting units,
co-processors, crafting storage, molecular assembler, CPUs) → endgame **spatial storage** and the
**quantum network bridge**.

**The whole tree resolves to just four non-vanilla leaves** the valuator can't read:
`charged_certus_quartz_crystal` (charger), `fluix_crystal` (transform), the three processors +
printed circuits (inscriber), and `silicon` (smelting — derivable, but only if smelting derivation
is on; it is by default). Value those, and **every ME block / cell / cable / CPU derives.**

---

## 2. Base resources needing explicit FU values

FU scale anchors: cobble 1, glass 5, copper 10, gold 15, iron 20, redstone 4, quartz 5,
diamond 50, emerald 50, netherite 500. Storage blocks derive 9×.

### 2a. Certus / fluix crystal chain (ABUNDANCE-CAPPED — these are the launder risk)

| Item id | Tier | FU | Recipe type | Rationale |
|---------|:----:|---:|-------------|-----------|
| `ae2:certus_quartz_crystal` | **2** | **10** | Loot (cluster drop) + `ae2:transform` dup | Renewable/farmable like amethyst → pinned to amethyst's T2/10. Loot-drop + custom transform = not derivable, needs explicit value. **Abundance cap: must not exceed T3.** |
| `ae2:charged_certus_quartz_crystal` | **2** | **12** | `ae2:charger` (custom) | Just energy applied to certus → still renewable/farmable. Charger can't be walked. Slightly above base certus. |
| `ae2:fluix_crystal` | **3** | **15** | `ae2:transform` in water (custom) | charged certus + redstone + nether quartz, all renewable → farmable. Structural backbone of every cable/core, so the whole network's floor. Capped at **T3** (all inputs ≤ T3 renewable). |
| `ae2:certus_quartz_dust` | **2** | **8** | `ae2:inscriber` + bud drop (custom/loot) | Certus ground to dust; feeds silicon smelt. Below crystal (lossy-ish). Needed so `silicon` derives. |
| `ae2:fluix_dust` | **3** | **12** | `ae2:inscriber` (custom) | Fluix ground to dust. |
| `ae2:silicon` | **2** | **10** | `minecraft:smelting` ✅ derivable | **Auto-derives** from `certus_quartz_dust` (8 FU @ T2) — listed for reference; no explicit entry needed if smelting derivation stays on. Optional explicit pin = 10@2. |

> **Note on quantities:** certus crystal `10@2` matches `amethyst_shard=10@2` exactly — deliberate.
> The cluster drops 4 crystals, the bud drops 1 dust; we value the *item*, not the drop count.

### 2b. Sky stone (meteorite) building blocks

| Item id | Tier | FU | Recipe type | Rationale |
|---------|:----:|---:|-------------|-----------|
| `ae2:sky_stone_block` | **2** | **8** | Meteorite worldgen (no recipe) | Rough meteorite block. Mining grind (must locate meteorites) but unlimited worldgen → semi-finite, ~end-stone tier. Display name "Sky Stone". |
| `ae2:smooth_sky_stone_block` | **2** | — | `minecraft:smelting` ✅ derivable | **Auto-derives** from sky_stone_block (smelt). Display name confusingly "Sky Stone Block" — **do not infer ids from display names.** |
| `ae2:sky_stone_brick` / `ae2:smooth_sky_stone_brick` / chiseled / small bricks | **2** | — | vanilla crafting/stonecutting ✅ derivable | All derive from the two base sky stone blocks. No explicit entries. |

> **ID gotcha (load-bearing):** `ae2:sky_stone_block` = the *rough* block (display "Sky Stone");
> `ae2:smooth_sky_stone_block` = the *smelted* block (display "Sky Stone Block"). The display
> names are nearly swapped vs the ids.

### 2c. Processors + printed circuits (the INSCRIBER gate — the real value tier)

These cannot derive (inscriber). They are the single most important explicit values: **every ME
block, cell, and CPU sits above them and derives once these are set.** Per-unit FU = the
consumable vanilla inputs only (the press is one-time meteorite capital, not consumed).

| Item id | Tier | FU | Inputs (per unit) | Rationale |
|---------|:----:|---:|-------------------|-----------|
| `ae2:logic_processor` | **3** | **35** | gold ingot (15) + redstone (4) + printed silicon (~10) | Cheapest processor; automation gate → floor at T3. |
| `ae2:calculation_processor` | **3** | **35** | certus crystal (10) + redstone (4) + printed silicon (~10) | Comparable to logic; T3. |
| `ae2:engineering_processor` | **5** | **70** | **diamond (50)** + redstone (4) + printed silicon (~10) | Diamond input → inherits **diamond's T5**. This is the network's true tier driver. |
| `ae2:printed_logic_processor` | **3** | **18** | `ae2:logic_processor_press` + gold ingot | Inscriber `inscribe`. Press not consumed. ~gold-ish. |
| `ae2:printed_calculation_processor` | **2** | **12** | calc press + certus crystal | ~certus. |
| `ae2:printed_engineering_processor` | **5** | **52** | eng press + diamond | ~diamond → T5. |
| `ae2:printed_silicon` | **2** | **12** | silicon press + silicon | ~silicon. |

> **The presses** (`ae2:logic_processor_press`, `calculation_processor_press`,
> `engineering_processor_press`, `silicon_press`) are **meteorite loot** (Mysterious Cube) — no
> craft path for the first copy; duplication is inscriber-only. **Leave UNPRINTABLE** (see §6).
> They are capital, not consumables — excluded from per-unit FU above.

> **Simplest viable config:** if you'd rather not hand-tune the printed-circuit intermediates,
> value **only the three final processors** (`logic`/`calc`/`engineering`) + `fluix_crystal` +
> `certus_quartz_crystal` + `charged_certus_quartz_crystal`. Those six leaves are enough for the
> entire downstream ME tree to derive. The printed circuits and silicon only matter if a player
> tries to print the intermediates directly.

---

## 3. Derive-vs-custom verdict per family

### Auto-derives (NO explicit entry — the valuator walks vanilla crafting) ✅

Once §2c processors + fluix + certus are valued, **all of these derive** (all are plain
`minecraft:crafting_shaped/shapeless`):

- **ME network:** `ae2:controller`, `ae2:drive`, `ae2:chest`, `ae2:interface`, `ae2:cable_interface`,
  `ae2:pattern_provider`, `ae2:cable_pattern_provider`, `ae2:energy_acceptor`, `ae2:energy_cell`,
  `ae2:dense_energy_cell`, `ae2:quartz_fiber`, the annihilation/formation cores.
- **Cables (all colors + variants):** `ae2:fluix_glass_cable`, `ae2:fluix_covered_cable`,
  `ae2:fluix_smart_cable`, `ae2:fluix_covered_dense_cable`, `ae2:fluix_smart_dense_cable`
  (+ the 16 dyed variants, e.g. `ae2:white_glass_cable`). _Word order: dense is `_covered_dense_`
  / `_smart_dense_`._
- **Storage cells + components:** `ae2:item_cell_housing`, `ae2:cell_component_{1k,4k,16k,64k,256k}`,
  `ae2:item_storage_cell_{1k,4k,16k,64k,256k}`. Higher components craft from 3× the lower
  component + calc processor + quartz glass + redstone/glowstone → derives cleanly up the chain.
- **Autocrafting:** `ae2:crafting_unit`, `ae2:{1k,4k,16k,64k,256k}_crafting_storage`,
  `ae2:crafting_accelerator` (the "Co-Processing Unit"), `ae2:molecular_assembler`.
- **Quartz building blocks:** `ae2:quartz_block`, `ae2:quartz_glass`, `ae2:quartz_vibrant_glass`,
  bricks/pillars/stairs/slabs/walls — all vanilla crafting/stonecutting from certus.
- **Spatial + quantum + P2P:** `ae2:spatial_anchor`, `ae2:spatial_io_port`, `ae2:spatial_pylon`,
  `ae2:quantum_ring`, `ae2:quantum_link`, `ae2:me_p2p_tunnel` — all vanilla crafting (see §6 for the
  cells/singularity exceptions).

> **ID spelling flags** (AE2 is internally inconsistent — verify against your data dump):
> - Assembled cell = `item_storage_cell_1k` (size **suffix**). Component = `cell_component_1k` (suffix).
> - Crafting storage = `1k_crafting_storage` (size **PREFIX** — opposite order!).
> - Co-Processing Unit's registry path is `crafting_accelerator` (not its display name).
> - Quantum Link Chamber = `quantum_link` (not `quantum_link_chamber`).
> - Energy P2P = `fe_p2p_tunnel` (not `energy_p2p_tunnel`).

### Custom recipes — explicit value OR unprintable ⛔ (can't derive)

| Item | Custom recipe type | Decision |
|------|--------------------|----------|
| `ae2:charged_certus_quartz_crystal` | `ae2:charger` | **Explicit** (§2a) |
| `ae2:fluix_crystal` | `ae2:transform` | **Explicit** (§2a) |
| `ae2:certus_quartz_crystal` (dup path) | `ae2:transform` | **Explicit** (§2a) — primary source is loot anyway |
| `ae2:logic/calculation/engineering_processor` | `ae2:inscriber` | **Explicit** (§2c) |
| `ae2:printed_*` circuits, `ae2:printed_silicon` | `ae2:inscriber` | **Explicit** (§2c, or omit per the "simplest viable" note) |
| `ae2:certus_quartz_dust`, `ae2:fluix_dust` | `ae2:inscriber` | **Explicit** (§2a) |
| `ae2:*_processor_press`, `ae2:silicon_press` | meteorite loot / inscriber dup | **Unprintable** (§6) |
| budding tiers (flawed/chipped/damaged) | `ae2:transform` in water | **Unprintable** (§6) — would be a farm-block dupe |
| `ae2:singularity` | Matter Condenser (256k items) | **Unprintable** (§6) |
| `ae2:quantum_entangled_singularity` | `ae2:transform` (explosion) | **Unprintable** (§6) — NBT freq-paired |
| typed P2P tunnels (item/fluid/fe/redstone/light) | in-world attunement | **Unprintable** (only `me_p2p_tunnel` is crafted/derivable) |

---

## 4. Abundance / anti-launder flags 🚩

**Certus quartz is renewable and fully automatable** — this is the #1 economy risk for AE2:

- **Mechanic:** Flawless Budding Quartz (`ae2:flawless_budding_quartz`, meteorite-only, never
  degrades) + **Growth Accelerator** (`ae2:growth_accelerator`) = an infinite, auto-farmable
  certus crystal source. Even without a flawless block, the lower budding tiers are craftable in
  survival (charged certus in water).
- **No crystal-seed dupe vector in 1.20.1.** The old "certus seed grown in water" mechanic was
  **removed** — there are no `ae2:*_crystal_seed` items. The renewability vector is budding-block
  + accelerator only. (Good — one fewer exploit surface.)
- **Consequence for our economy:** because certus is farmable, the **entire certus/charged/fluix
  chain is abundance-capped at T2/T3** (see §2a). Pinning certus crystal to **T2/10 FU = vanilla
  amethyst** guarantees a certus spool can't out-tier and launder into T4+ vanilla goods. **Do not
  let certus, charged certus, or fluix drift above T3.**

**Other farmable flags:**
- **Charged certus, fluix, certus dust, fluix dust** — all downstream of farmable certus → same cap.
- **Budding blocks themselves** — must stay **unprintable** (§6): printing a budding block would be
  a literal printable infinite-resource generator. The growable bud stages
  (`small/medium/large_quartz_bud`, `quartz_cluster`) likewise unprintable / left unvalued.
- **Sky stone** — meteorite-gated but unlimited worldgen; a *grind*, not a farm. T2 is fine; it
  can't out-tier anything rarer.

---

## 5. Utility overrides ⬆️

These derive from processors + fluix, so their FU is already high. The point is to confirm the
**tier floor** lands right (derivation flows tier up from the priciest ingredient — the
engineering processor at T5 pulls most network cores to T5, which is correct). No explicit FU
needed; flagged so the derived result is sanity-checked, and pinned only if derivation undershoots:

| Item | Derived tier driver | Expected tier | Note |
|------|---------------------|:-------------:|------|
| `ae2:controller` | engineering processor (T5) + fluix + sky stone | **T5** | The network heart — high utility, correctly gated by its diamond-bearing processor. |
| `ae2:drive` | 2× engineering processor (T5) | **T5** | Mass storage access. |
| `ae2:crafting_unit` | calc + logic processor (T3) | **T3** | But the useful CPU needs storage components → effective tier rises with the cell tier. |
| `ae2:{16k,64k,256k}_crafting_storage` | high cell components | **T5** | Big autocraft = late-game; derives up correctly via the component chain. |
| `ae2:molecular_assembler` | cores (fluix T3) | **T3** | If derivation lands < T3, **floor it at T3** (it's the autocraft workhorse). |
| `ae2:me_p2p_tunnel` | engineering processor (T5) | **T5** | iron + engineering processor + fluix → T5. P2P is high-utility; T5 floor is appropriate. |
| `ae2:dense_energy_cell` | calc processor + 8× energy cell | **T3–T5** | Derives fine; no override. |
| `ae2:spatial_io_port` / `spatial_pylon` / `quantum_ring` / `quantum_link` | processors + fluix | **T5** | Endgame bridges; derive to T5 via engineering processor. Floor at T5 if undershooting. |

**Recommendation:** ship with **no utility FU overrides** and let derivation run; only add a
`tier` floor (re-register with a higher `@tier`) for `controller`, `drive`, `me_p2p_tunnel`, and
the spatial/quantum blocks **if** a post-implementation FU dump shows them deriving below T5. The
diamond in the engineering processor already does most of this work.

---

## 6. Unprintable candidates ⛔

Leave these **unvalued** — strict mode (`unknownBlocksPrintable=false`, the default) refuses any
structure whose palette contains an unvalued block, so unvalued = unprintable.

| Item id | Why unprintable |
|---------|-----------------|
| `ae2:flawless_budding_quartz` | Meteorite-only, never-degrading **infinite certus generator**. Printing it = printable infinite resource. Also not survival-obtainable (no silk-touch pickup). |
| `ae2:flawed_budding_quartz`, `ae2:chipped_budding_quartz`, `ae2:damaged_budding_quartz` | Degrading-but-still-renewable certus generators. Custom `ae2:transform` recipe; would be a farm-block dupe. |
| `ae2:small_quartz_bud`, `ae2:medium_quartz_bud`, `ae2:large_quartz_bud`, `ae2:quartz_cluster` | Growth stages on budding blocks; not standalone craftables. |
| `ae2:logic_processor_press`, `ae2:calculation_processor_press`, `ae2:engineering_processor_press`, `ae2:silicon_press`, `ae2:name_press` | **Meteorite loot** (Mysterious Cube). No craft path for the first copy → no recipe to derive, and printing them trivializes the meteorite-hunt progression gate. |
| `ae2:spatial_storage_cell_2`, `ae2:spatial_storage_cell_16`, `ae2:spatial_storage_cell_128` | **Dimension-bound via NBT** once used: the stored build lives in AE2's spatial dimension, not the item. A printed copy references the **same** plot id → two cells fighting over one stored volume (exploit) or an empty/inert copy. (Empty cells are vanilla-craftable anyway — no reason to print.) |
| `ae2:singularity` | Matter Condenser only (consumes **256,000** items). No vanilla recipe to derive; printing it bypasses the condenser sink entirely. |
| `ae2:quantum_entangled_singularity` | **NBT `freq`-paired** (`QuantumBridgeBlockEntity.TAG_FREQUENCY`). A printed copy either loses `freq` (inert) or shares it → 3-way frequency collision (undefined/exploit). Source singularity is already condenser-only. |
| `ae2:item_p2p_tunnel`, `ae2:fluid_p2p_tunnel`, `ae2:fe_p2p_tunnel`, `ae2:redstone_p2p_tunnel`, `ae2:light_p2p_tunnel` | Made by in-world **attunement** (right-click conversion), not crafting → no recipe to derive. Only `ae2:me_p2p_tunnel` (the base, vanilla-crafted) is printable. |

> **Not flagged unprintable** but worth a sanity note: `ae2:growth_accelerator` is vanilla-craftable
> and will derive — it's a *machine*, not a resource, so printing it is fine (it still needs power
> and a budding block to do anything). Same for `ae2:charger`, `ae2:inscriber`, `ae2:matter_condenser`,
> `ae2:quantum_ring/link` — derive normally.

---

## 7. Proposed `Ae2Compat` explicit-value set (copy-ready)

Mirror `DraconicCompat`: gate on `ModList.get().isLoaded("ae2")`, register by `ResourceLocation`
in `enqueueWork`. The **minimal** set (six leaves) is enough for the whole tree to derive; the
**full** set adds the printed-circuit intermediates and dusts.

```java
// --- minimal: the leaves that gate everything downstream ---
register("certus_quartz_crystal",          10, 2);  // == amethyst (abundance cap)
register("charged_certus_quartz_crystal",  12, 2);  // charger (custom)
register("fluix_crystal",                  15, 3);  // transform (custom) — network backbone
register("logic_processor",                35, 3);  // inscriber (custom)
register("calculation_processor",          35, 3);  // inscriber (custom)
register("engineering_processor",          70, 5);  // inscriber (custom) — diamond → T5

// --- full: intermediates so the dusts/prints are printable too (optional) ---
register("certus_quartz_dust",              8, 2);
register("fluix_dust",                     12, 3);
register("silicon",                        10, 2);  // also derives from dust via smelting
register("printed_logic_processor",        18, 3);
register("printed_calculation_processor",  12, 2);
register("printed_engineering_processor",  52, 5);
register("printed_silicon",                12, 2);
register("sky_stone_block",                 8, 2);  // smooth/brick variants derive

// UNPRINTABLE (register NOTHING — strict mode refuses them):
//   *_processor_press, silicon_press, name_press            (meteorite loot)
//   *_budding_quartz, *_quartz_bud, quartz_cluster          (infinite certus farm)
//   spatial_storage_cell_{2,16,128}                         (dimension-bound NBT)
//   singularity, quantum_entangled_singularity              (condenser sink / NBT-paired)
//   {item,fluid,fe,redstone,light}_p2p_tunnel               (in-world attunement, no recipe)
```

After implementing, dump the derived FU/tier map and verify: (a) `controller`/`drive`/spatial/
quantum land at **T5**; (b) nothing in the certus chain derives above **T3**; (c) the unprintable
list is genuinely refused under strict mode. Add `@tier` floors only where §5 undershoots.

---

## 8. Open calls for sign-off

1. **Engineering processor at T5 (70 FU)** — driven by its diamond input. This pulls the
   controller/drive/P2P/spatial/quantum to T5. Agree, or push the network endgame to T6 (would
   require an explicit tier floor, since no input is T6)?
2. **Certus crystal at T2/10 (= amethyst).** Locked by the abundance rule. Confirm you're happy
   pinning AE2's foundational resource as low as amethyst (it's the safe call — anything higher
   risks the launder).
3. **Minimal vs full explicit set** — ship six leaves (lean, everything derives) or the full
   thirteen (printed circuits + dusts + sky stone also directly printable)?
4. **Logic vs calculation FU** — I set both to 35. Calc uses certus (cheaper input) so it could be
   ~30; logic uses gold so ~35. Negligible; flag if you want them split.
```
