# Catalysts — Design Doc (WORK IN PROGRESS)

> **Status:** Design COMPLETE (grill Q1–Q18 resolved). Nothing implemented yet. This doc is
> the durable record + implementation plan. The "Decisions log" is the authoritative spec;
> the "Implementation Plan" at the bottom is the build sequence.

> **Reader note:** the "Concept", "Naming", "Effect menu" and "Open design questions" sections
> immediately below are the **pre-grill brainstorm**, kept for history. They are partly stale
> (the name is **RESIN** not "Catalyst"; T2 anchor is a single diamond/emerald not nether
> stars/blocks; etc.). The authoritative spec is the **Decisions log (Q1–Q18)** + the
> **Implementation Plan** at the bottom.

## Concept

A new **single-item slot** in the printer / fabricator GUI (bottom-right). The player drops
in a **Catalyst** (working name — see Naming) that modifies the next blueprint print. The
modifier is **blueprint-only** — it does nothing for any non-blueprint operation. With no
catalyst in the slot, prints behave exactly as today.

Three power tiers:
- **T1** — craftable, intentionally weak.
- **T2** — craftable but expensive (nether stars / diamond/emerald blocks / etc.).
- **T3** — **not craftable; world-found only** (loot chests). Strongest effects.

## Naming (TBD)

"Catalyst" technically means *not consumed* in a reaction, but the design consumes one per
print — a tension Patrick flagged. Options:
- **Catalyst** — genre-recognizable; many mods consume "catalysts" despite the chemistry.
  (Recommended: keep it unless we go persistent/charged.)
- **Reagent** / **Infusion** / **Print Charge** / **Additive** / **Primer** — read as
  consumed.

If we make them genuinely *persistent* (never consumed), "Catalyst" becomes accurate.
Naming therefore depends on the Consume-vs-Persist decision below.

---

## Feasibility map (from codebase exploration — expensive to regenerate, keep)

| Need | Verdict | Hook point |
|---|---|---|
| Inject loot into a printed chest/barrel/shulker | **Feasible** | `PrinterBlockEntity` ~L794–806: after `placedBe.load(beData)` + `setChanged()`, grab the container's `IItemHandler` and insert ItemStacks, then `setChanged()` again |
| Extra FU discount, incl. below break-even ("net gain") | **Feasible** | `PrinterBlockEntity.applyEfficiency(int baseFu)` ~L454–461. Currently `Math.max(baseFu, cost)` clamps to 1:1. Catalyst discount compounds *after* the efficiency markup; net-gain requires bypassing that clamp for catalyzed prints |
| "One tier above the build" | **Feasible** | `BlueprintDiscItem.blueprintTier(blueprint)` L89–110 → highest FU tier (1–8); also stored on disc NBT key `Tier` |
| Faster / instant print | **Feasible** | print cadence = `speedAdjusted(MC3DPrintConfig.ticksPerBlock(tier))` ~L719–725 |
| RF discount | **Feasible** | energy drained alongside FU at ~L805–806 |
| New restricted GUI slot | **Feasible** | empty corner `x=212–229, y=152–199` or gap `y=90–151`. Mirror the upgrade-slot `mayPlace`/`maxStackSize=1` pattern (`PrinterMenu` L111–134). New `ItemStackHandler(1)` on the BE with `isItemValid()`. **Coords must stay in lockstep** across `PrinterMenu` / `client/PrinterScreen` / `tools/gen_printer_gui.py` |
| Slot on multiblock fabricator (T5–T8) | **Free** | fabricator reuses `PrinterMenu` + `PrinterBlockEntity` wholesale |
| Spawn entities in the build | **Feasible** | post-print, spawn at world positions |
| T3 world-found only | **Solved pattern** | clone `loot/AddBlueprintDiscModifier` → `AddCatalystModifier`; register in `ModLootModifiers`; add `loot_modifiers/*.json` + entry in `data/forge/loot_modifiers/global_loot_modifiers.json` |
| New item family (3 tiers × N effects) | **Pattern exists** | mirror tiered `buildSpools()` loop in `ModItems`; recipes are JSON; tooltips via `appendHoverText` |

Print loop ordering: bottom-up Y, then Z, then X; liquids appended last. Cost per block:
`blockFuCost(state)` → `applyEfficiency()`; structural-itemless blocks are free.

---

## Effect menu (brainstorm — pick the v1 set during grill)

Patrick's two seeds plus additions. Each is feasible per the map above.

- **A. Treasure Infusion** *(seed #1)* — printed storage containers (chest/barrel/shulker)
  have a chance to spawn holding treasure. Knobs: trigger chance, loot tier (`buildTier+1`?),
  which containers, a curated `TREASURE` set (diamonds, nether stars, netherite, enchanted
  gear…) with per-item drop rates.
- **B. Filament Overdrive** *(seed #2)* — extra % FU discount; at the top end can dip *below*
  break-even for a net FU gain. Highest economy risk (laundering surface). Knobs: % off, floor
  (can it go negative-cost?), stacking with Efficiency modules.
- **C. Overclock** — fewer ticks/block for this print (T3 = near-instant).
- **D. Power Saver** — RF cost discount for this print.
- **E. Verdant Growth** — crops/saplings/cane print fully grown; trees as grown trees. Great
  for farm builds. Medium effort (map plant→mature state, or bonemeal post-place).
- **F. Menagerie / Population** — spawn thematic passive mobs in the build (barn→cows,
  coop→chickens, stable→horses, apiary→bees), or generic N animals. Flavorful.
- **G. Forbidden Print (Unsealing)** — temporarily allow printing the intentionally-unvalued
  / unprintable blocks (spawners, dragon egg, survival-unobtainables). Rare T3 unlock; ties
  into existing strict-mode design.
- **H. Quartermaster / Prefill** — furnaces print pre-fueled, brewing stands pre-stocked,
  dispensers/item frames loaded → "move-in ready" builds. Generalization of A to functional
  blocks.
- **I. Ore Salting** — build's stone/deepslate has a chance to print as ore variants.
- **J. XP Yield** — catalyzed print completion drops XP orbs.

---

## Open design questions (grill agenda, dependency order)

1. **Consume vs persist vs charges** *(keystone — gates whether value-generating effects are
   safe).* Recommended: **consumed per print** (value effects can't be permanent infinite
   generators; matches "uses that consumable").
2. **Naming** — depends on #1.
3. **One effect per print, or can a catalyst bundle multiple effects?** (slot count = 1.)
4. **v1 effect set** — which of A–J ship first.
5. **Tier model** — does tier gate *which effects* you can use, or just their *strength*?
   How many distinct items result (effect × tier matrix)?
6. **Treasure specifics** — loot tier, containers, chance, treasure set + per-item rates,
   inject-into-NBT vs drop-in-world.
7. **Overdrive specifics** — below break-even? floor? stack with Efficiency? laundering guard
   (winding is 1:1 exact-tier; printing down-only — a net-gain print is a potential exploit).
8. **T3 loot sourcing** — which vanilla loot tables, drop chance (GLM).
9. **Recipes** — T1/T2 ingredients.
10. **GUI slot** — corner vs gap; exact coords.
11. **Auto-print / scan→print interaction** — does the catalyst apply in auto mode? does
    treasure stack on top of captured chest contents?
12. **Config / strict-mode toggles**, Patchouli guide, creative tab, gametests.

## Decisions log

- **Q1 — Lifecycle: CONSUMED per print.** One catalyst = one catalyzed print, then gone.
  Not persistent, not charged. Rationale: only model that makes value-generating effects
  (Treasure, below-break-even Overdrive) exploit-safe and keeps T3 finds a real decision.
  Naming therefore free to move away from "Catalyst" (which implies not-consumed).

- **Q2 — Name: RESIN.** Item family = "Resin"; GUI slot = the "Resin slot". A throwback to
  SLA/resin printing (FDM's cousin) — thematically apt for a filament(FU)-based printer that
  accepts the *other* 3D-printing medium as a one-shot modifier. No RF/"Flux" collision.
  Caveat baked into the plan: UI/tooltips must frame Resin as a **modifier added to a print**,
  not an alternative bulk print material (avoid filament-vs-resin confusion). Effect-typed
  naming leans into real resin types: *Treasure Resin*, *Overdrive Resin*, etc.

- **Q3 — Mono-effect.** Each resin does exactly ONE thing. One slot, one resin, one effect
  per print — the per-print choice IS "which single effect." Combos, if ever, come later as a
  single hand-authored T3 "Master Resin" special, not a combinatorial system.

- **Q4 — v1 effect set (SIX). Overclock cut.**
  1. **Treasure Infusion** — rare jackpot loot into *storage* blocks (chest/barrel/shulker).
  2. **Overdrive** — extra FU discount; top tier dips below break-even (net gain).
  3. **Verdant Growth** — crops/saplings/cane print grown; trees print grown.
  4. **XP Yield** — catalyzed print BANKS xp on the machine, released as orbs to whoever pulls
     the blueprint disc out (furnace model: `AbstractFurnaceBlockEntity` recipesUsed → pop on
     extraction). Amount scales `~ tier × totalFU / divisor` (exact scale TBD).
  5. **Quartermaster / Prefill** — practical *starter supplies* into *functional* blocks
     (furnaces pre-fueled, dispensers/droppers loaded, brewing stands stocked; a printed chest
     gets a move-in kit: torches/food/tools). Distinct from Treasure: practical, not rare.
     Needs bounded curated content tables.
  6. **Ore Salting** — build's stone/deepslate/etc. has a chance to print as ore variants
     (decorative + minable reward). **Mints ore value** → richer ores tier-locked + low-chance;
     resin must cost more than yielded ore at craftable tiers.
  Deferred to later: Menagerie/Population, Forbidden Print, Power Saver(RF). (Overclock
  rejected outright.)

- **Q5 — Tier model: GATED MATRIX, per-effect tier floors.** Final floor table:

  | Effect | T1 | T2 | T3 |
  |---|:--:|:--:|:--:|
  | Verdant Growth | ✅ | ✅ | — |
  | XP Yield | ✅ | ✅ | ✅ |
  | Treasure Infusion | — | ✅ | ✅ |
  | Overdrive | — | ✅ | ✅ |
  | Quartermaster | — | — | ✅ |
  | Ore Salting | — | — | ✅ |

  _(Verdant T3 removed in Q11 — caps at craftable T2.)_

  Result: **11 items.** T1 = {Verdant, XP}. T2 = {Verdant, XP, Treasure, Overdrive}.
  T3 = {XP, Treasure, Overdrive, Quartermaster, Ore Salting} (found-only). Craft-vs-loot
  split: **T1+T2 craftable (6 recipes), T3 loot-only.** T3 GLM picks one of **5** resins per
  hit. Quartermaster + Ore Salting are T3-exclusive to feel rare; Verdant is the lone
  craftable-capped utility effect.

- **Q6 — Consume timing: RESERVE-AND-LOCK (protected).** On a catalyzed print: resin is
  locked in the slot at job start (non-removable mid-print); consumed the instant the print
  commits its **first placement**; no refund thereafter (pause/resume/cancel all keep it
  spent). If the print never places a block (obstructed / 0 FU at block 0), the resin is NOT
  consumed and stays in the slot. Closes the cancel-at-95%-to-refund exploit while sparing a
  rare T3 from a print that never started.

- **Q7 — Knobs: CONFIG-DRIVEN.** Resin tunables (Overdrive %, Treasure chance, Ore Salting
  per-ore chances, XP divisor, etc.) live in a new catalyst/resin section of
  `MC3DPrintConfig`. Consequence accepted: these are economy values, so tuning them triggers
  the usual delete-stale-config dance (`run/config/` + game-instance `config/`). **Long-term
  pre-launch goal (low priority, NOT in this feature):** solve config-merge so players can
  retune without wiping config — see memory `config-no-wipe-goal`.

- **Q8 — Treasure Infusion model: LOOT-TABLE-BASED, rarity = Common/Uncommon/Rare.**
  - Treasure rolls **MC3DPrint-authored loot tables**, one per rarity (`Common/Uncommon/Rare`,
    unified with the resin rarity scheme) — NOT a hardcoded item list and NOT the 1–8 FU tiers.
    Curated for quality (no junk).
  - **Enchanted gear** via loot functions (`enchant_randomly`/`set_enchantments`):
    Sharpness IV, Protection IV, Unbreaking III, etc.
  - **Modded extensibility, two ways:** (a) other mods/packs inject into our tables via their
    own GLMs; (b) we add modded treasure entries gated by `forge:mod_loaded` loot conditions
    (soft-dep) → e.g. Draconic `draconium_ingot` only when `draconicevolution` loaded.
  - **Ceiling = nether-star / "T7-equivalent."** Never T8/Draconic *infused-crafting* outputs.
    The off-the-cuff "one tier above the build" anchor is DROPPED — quality keys off resin tier.
  - **Resin→rarity weighting:** T2 Treasure Resin → mostly Common + some Rare; T3 → Rare +
    chance of Epic. (Weights in config.)
  - **Hard excludes:** dragon egg, ALL `mc3dprint:*` items, survival-unobtainables.
  - **Trigger:** per-container independent roll, **per-print cap** on # of containers that pop
    (T2 lower chance/cap, T3 higher). Chance + cap in config.
  - **Delivery:** inject into the printed container's NBT, **additive — empty slots only**
    (never overwrite blueprint-captured contents). Containers: chest, trapped chest, barrel,
    shulker (all colors). Excluded: ender chest; hoppers/droppers/dispensers belong to
    Quartermaster.
  - **Bonus:** loot-table contents hot-reload via `/reload` (only numeric chances in toml need
    the config-wipe).

- **Q9 (OPEN) — Resin gated to OFFICIAL/world-found blueprints only.** Patrick: resin must NOT
  work on player-scanned blueprints (anti-exploit: scan a 1-chest build → mass-print treasure).
  Needs a boolean on the disc (`PlayerCreated` set by scanner; curated/loot discs = official).
  **DECIDED:** gate line = `PlayerCreated == false` (official/curated vs player-scanned;
  scanner stamps `PlayerCreated=true`, curated/loot discs stay false/official). Creative-menu
  curated discs read as official (accepted) — fine, creative is admin mode. **Scope = (A) the
  WHOLE resin system** is gated to official blueprints (not just minting effects; Verdant
  included). One rule, book-friendly, pairs "found a resin" with "found a blueprint."
  Implementation: scanner write-path sets the flag; the Resin slot/print-time checks it and
  no-ops (resin not consumed) on player-made blueprints, with a GUI status explaining why.

- **Q10 — Overdrive: T2 = break-even, T3 = 20% below (net gain).**
  - **T2 Overdrive Resin** = "perfect efficiency in a bottle": collapses the lossy markup to
    **exact break-even** (floor = base FU, no net gain), so it's loop-safe to be craftable.
  - **T3 Overdrive Resin** = **~20% below break-even** → genuine minted FU. Bounded by
    T3 being found-only + single-use; further fenced by the official-blueprint gate (Q9),
    the winder-blacklist, and down-only/exact-tier winding.
  - **Final cost floor, non-stacking:** Overdrive sets the floor, doesn't compound under it.
    4 Efficiency modules + T2 → break-even (not below); + T3 → ~0.8×base (not lower).
  - % and floor in config (tune down if any official build turns out to be a windable-block
    loop). Book line: "T2 prints at break-even; T3 prints at a profit."

- **Q11 — Verdant Growth simplified to T1 + T2 (no T3).** All "in-place plant" maturation:
  - **T1**: core food crops mature in place — wheat, carrots, potatoes, beetroot, nether wart.
  - **T2**: everything in-place — + cane, cactus, bamboo, sweet berries, cocoa, melon/pumpkin
    fruit, kelp/seagrass, chorus, giant mushrooms (the old T3 content merged up).
  - **Trees: DEFERRED from v1** (per "even easier/simpler") — no sapling to tree growth, so
    zero build-damage risk and no clearance-check code. Verdant = purely "in-place plants
    mature." Trees-with-clearance is a clean later enhancement. **CONFIRMED: trees dropped.**

- **Q12 — XP Yield: capped, furnace-banked, scales with tier × FU cost.**
  - **Mechanism:** catalyzed print banks XP on the machine; released as orbs to whoever pulls
    the blueprint disc out (furnace model). Multiple prints accumulate; breaking the machine
    pops banked XP so it's never lost.
  - **Formula:** `bankedXP = min( cap[resinTier], round( min(buildTier,7) × printCost ×
    rate[resinTier] ) )`. Uses the disc's stored `Tier` + `PrintCost`. T7/T8 collapse to 7.
  - **Per-tier caps** (anchored to from-scratch level milestones): **T1 = 160 (≈lvl 10),
    T2 = 550 (≈lvl 20), T3 = 1500 (≈lvl 30, level-0→30 jump).** Cap also future-proofs against
    bigger builds (Patrick's explicit ask).
  - **Calibration PINNED (computed via gametest over all 132 builds):** priciest build is
    `diamond_vault` (T6, printCost 32,869) → `MAX_TXC = 197,214`. Use **`REF = 200,000`**.
    Final formula: `bankedXP = min(cap[tier], round(cap[tier] × min(buildTier,7) × printCost /
    200000))`. Config surface = **3 caps + 1 REF**, no decimal rates. Worked: diamond_vault
    +T3 ≈ 1,479 XP (≈lvl 30) ✅; emerald_market_hall(T4) +T3 ≈ 651; purpur_tower(T4) +T3 ≈ 146.
  - **No T8 curated builds exist** (highest is T7 `beacon_spire`, which is cheap) — so the
    min(_,7) clamp is forward-insurance only.
  - **Impl note:** `PrintCost`/`Tier` derive most blocks' FU **from recipes** → only correct
    with RecipeManager bound. Both are already baked on the disc at write-time, so XP reads the
    stored values (no runtime recompute). XP magnitude ref (from 0): 50≈lvl5, 300≈lvl15,
    1395≈lvl30 — ramp cheap early, steep late.

- **Q12b — XP simplified to printCost-only (buildTier multiplier DROPPED).** Supersedes the
  tier×cost calibration in Q12. Final formula: `bankedXP = min(cap[resinTier],
  round(cap[resinTier] × printCost / REF))`. **REF = 33,000** (= max curated printCost,
  diamond_vault). Caps unchanged (T1=160 / T2=550 / T3=1500). No tier math — the min(_,7)
  clamp is gone. Worked (T3): diamond_vault 1,494 (~lvl30)✅, emerald_market_hall 986 (~lvl26),
  copper_clocktower 767 (~lvl23), purpur_tower 221 (~lvl12), small build ~90. Rationale:
  simpler concept/book line AND better spread (top builds cluster 33k/22k/17k vs one 197k
  outlier); "more filament burned = more XP" is the smelting mental model.

- **Q13 — Ore Salting (T3-only, confirmed).** A placed natural-stone block has a chance to come
  out as a mineable ore variant.
  - **Hosts:** stone, deepslate, netherrack (natural stone-types only; NOT cobble/bricks/
    polished/built blocks).
  - **Output = ore variant matching the host:** stone→regular ores, deepslate→`deepslate_*`
    ores, netherrack→nether gold/quartz ore. (Earlier "regular-ores-only" was a mutual
    misread — variants are wanted and look correct.)
  - **Pool:** coal, copper, iron, gold, redstone, lapis (common) + diamond, emerald (rare).
    Ancient debris excluded.
  - **Chances (config):** ~5% salt chance per eligible block; diamond+emerald ≈ 5% of salted
    ores → ~couple diamond ore per 1,000-stone build. Per-print cap on total salted blocks.
  - **Quantity = single ore block per salted cell (1:1 substitution, mineable).** No vein
    clusters, no direct item drops. Real yield knob = salt chance × build size; player mines
    ore for normal drops (redstone/lapis 4–8 each, Fortune applies).
  - **Guardrails:** T3 found-only + single-use + official-blueprint gate + low salt chance +
    per-print cap.

- **Q14 — Quartermaster (T3-only, confirmed): GUARANTEED prefill of functional blocks**
  (deterministic — distinct from Treasure's random roll; "move-in ready").
  - **Furnaces / blast / smoker:** **coal BLOCKS** (~3) in the fuel slot — not loose coal;
    burn far longer, T3-premium.
  - **Brewing stands:** blaze powder + 3 water bottles (ready to brew).
  - **Chests / barrels — move-in kit:** torches, food (bread), **ENCHANTED iron tools**
    (pickaxe/axe/shovel, **Efficiency IV + Unbreaking III**; iron always, never stone), a
    couple coal blocks. T3-only ⇒ enchanted is the only version.
  - **Additive into empty slots only** (never overwrite captured contents). Contents are cheap/
    practical except the enchanted tools (the T3 treat).
  - **Skipped:** dispensers/droppers/hoppers (contraption parts — prefilling could break intended
    behavior), lecterns/composters (niche).
  - One resin per print ⇒ a chest gets EITHER treasure OR a kit, never both. Amounts in config;
    same T3 + single-use + official-blueprint gating.

- **Q15 — T3 loot sourcing: new GLM `AddCatalystModifier` (clone of `AddBlueprintDiscModifier`).**
  - Picks **1 of the 5 T3 resins, uniform-random**; **~10% per qualifying chest**. Chance lives
    in the loot JSON → `/reload`-tunable, no config wipe.
  - **Tables (end-game treasure-hunt set):** `end_city_treasure`, `ancient_city`,
    `nether_bridge` (fortress), `bastion_treasure`, `woodland_mansion`, `stronghold_library`
    (+ corridor/crossing), `buried_treasure`.
  - **TODO to bake into the code when built:** add a code comment to revisit and make the
    resin pick **flavor-biased per table** (e.g. Ore Salting ← ancient_city/mineshafts,
    Treasure ← treasure chests) — more fun, easy later polish. Ship uniform first.

- **Q16 — Crafting (T1/T2 craftable; T3 loot-only). Each resin = Resin Base + tier anchor +
  effect ingredient.**
  - **Resin Base item:** extrudium crystal + slime ball via the **`forge:slimeballs` tag** (any
    variant, incl. modded purple/blue — interchangeable). **NO honey** (annoying to farm).
    Verify exact tag id (`forge:slimeballs` vs `forge:slime_balls`) at impl.
  - **Tier anchor:** T1 = common mats (iron/redstone/gold). T2 = a single **diamond OR emerald**
    (item, not block) via a small custom tag **`mc3dprint:resin_gem`** {diamond, emerald}
    (extensible). **NO nether stars** (too steep for a per-print consumable).
  - **Yield:** T1 = **2** per craft (cheap, softens per-print grind); T2 = **1** (precious gem).
  - **Effect ingredients:** Verdant → bone meal/moss; Treasure → gold; Overdrive → redstone
    block / extrudium; **XP → LAPIS LAZULI** (replaces bottle o' enchanting — that's cleric-trade
    gated and miserable early; lapis is the vanilla enchanting reagent, cheap + mineable +
    recognizable). Optional fancier T2-XP flavor: sculk/echo shard (deferred; keep base lapis).
  - Exact 3×3 shaped patterns drafted at build time.

- **Q17 — GUI: Resin slot in the right-input column GAP (Option A).** Single slot between
  Upgrades (top) and Spools (bottom), labeled "Resin"; reads as the third machine input.
  Appears on the fabricator too (shared `PrinterMenu` — free). `mayPlace` accepts resin items
  only. Reuse the existing status line to show "requires an official blueprint" when a
  player-made blueprint is loaded (Q9 gate). Coords kept in lockstep across `PrinterMenu` /
  `client/PrinterScreen` / `tools/gen_printer_gui.py`. **Patrick will review the render and may
  nudge placement afterward.**

- **Q18 — Resin slot holds a STACK; one consumed per job; Auto continues uncatalyzed when dry.**
  Resins stackable to 64; slot accepts a stack so a supply can feed repeated/Auto prints (drop
  1 for a single catalyzed print). One resin consumed per catalyzed blueprint job (manual or
  Auto), via Q6 timing. When the stack empties mid-Auto, printing **continues uncatalyzed**
  (Auto never halts for lack of resin). Bounded by resin supply + official-blueprint gate.

- **Delivery (standard):** Config = new `RESIN` section in `MC3DPrintConfig` (all numeric knobs;
  GLM drop-chance stays in loot JSON). Creative tab = a "Resins" group (Resin Base + 12 resins)
  after upgrade modules. Patchouli = "Resins" category (intro + per-effect pages + find-T3
  page; soft-dep). Gametests = `CatalystGameTests` forcing RNG knobs to 1.0 to deterministically
  assert each effect + the player-made-blueprint rejection.
  - **Guide framing/voice (Patrick):** spin Resin as **"makes your print finer / better"** — the
    in-game docs should read as "Resin is a way to *improve* your print," not a dry feature list.
    Lean into the SLA-resin "finer detail" metaphor. **Confirm/update the in-game Patchouli docs
    as part of this feature** (per CLAUDE.md's update-the-guide rule) — this is net-new, so it
    must be authored.

---

## Implementation Plan

### v1 scope recap (the 11 resin items)

| Effect | Tiers | Items | Source |
|---|---|---|---|
| Verdant Growth | T1, T2 | 2 | craftable |
| XP Yield | T1, T2, T3 | 3 | T1/T2 craft, T3 loot |
| Treasure Infusion | T2, T3 | 2 | T2 craft, T3 loot |
| Overdrive | T2, T3 | 2 | T2 craft, T3 loot |
| Quartermaster | T3 | 1 | loot only |
| Ore Salting | T3 | 1 | loot only |

Plus **Resin Base** (crafting intermediate) = 12 new items total. **5 T3 resins** in the GLM
pool (XP, Treasure, Overdrive, Quartermaster, Ore Salting). **6 craftable recipes** (2×T1, 4×T2).

### Components & files

- **Items** — `item/ResinItem.java` (`Effect` enum {VERDANT, XP, TREASURE, OVERDRIVE,
  QUARTERMASTER, ORE_SALTING} + `Tier` enum {T1,T2,T3}; tooltip describes effect+tier; stacks 64),
  `item/ResinBaseItem.java` (or plain `Item`). Register in `registry/ModItems.java` via a loop
  over the valid (effect,tier) pairs (mirror `buildSpools()`). Item textures + models generated
  reproducibly by a new `tools/gen_resin_items.py` (tint a shared "resin blob" per effect/tier).
- **Resin slot** — `PrinterBlockEntity`: new `ItemStackHandler resins` (size 1, stack-holding,
  `isItemValid` = ResinItem), `resinInventory()` accessor, NBT save/load, expose via capability
  (extend `allCap` or a side). `machine/PrinterMenu.java`: add `CATALYST_SLOT_X/Y` (gap, Option A)
  + the slot (`mayPlace` = ResinItem). `client/PrinterScreen.java`: draw the well + "Resin" label
  + the "requires official blueprint" status. `tools/gen_printer_gui.py`: add the well at matching
  coords. (Fabricator inherits it free.)
- **Official-blueprint flag (Q9)** — `item/BlueprintDiscItem.java`: `TAG_PLAYER_CREATED`
  ("PlayerCreated") + `isOfficial(stack)` helper. Scanner write-path stamps `PlayerCreated=true`;
  curated install + `loot/AddBlueprintDiscModifier` leave it false (official). Print-time gate +
  slot status read this.
- **Effect engine** — `machine/resin/ResinEffects.java` dispatch, invoked from the blueprint
  print loop hooks in `PrinterBlockEntity.tickBlueprintMode`:
  - *Job start*: if resin present AND blueprint official → reserve+lock (Q6); else no-op + status.
  - *Per-block, pre-setBlock*: Ore Salting substitutes the BlockState; Verdant swaps to mature state.
  - *Cost calc* (`applyEfficiency`/`blockFuCost`): Overdrive applies the post-efficiency floor
    (T2 break-even, T3 0.8×).
  - *Per-block, post-`placedBe.load`*: Treasure / Quartermaster inject into containers (additive,
    empty slots only).
  - *First committed placement*: consume one resin from the stack.
  - *Job done / disc withdrawn*: XP bank on completion, pop orbs on template-slot extraction +
    on block break.
- **Treasure loot tables** — datapack `data/mc3dprint/loot_tables/resin/treasure_{common,rare,
  epic}.json` (curated items + `enchant_randomly`/`set_enchantments` for gear + `forge:mod_loaded`
  conditions for modded entries e.g. Draconic). Resolved at runtime via the server LootTable API.
- **T3 loot GLM** — `loot/AddCatalystModifier.java` (clone of `AddBlueprintDiscModifier`; uniform
  pick of 5 T3 resins; **code comment: TODO flavor-bias per table**). Register in
  `registry/ModLootModifiers.java`. `data/mc3dprint/loot_modifiers/catalysts_*.json` (tables +
  ~10% chance) + entry in `data/forge/loot_modifiers/global_loot_modifiers.json`.
- **Recipes & tags** — `data/mc3dprint/recipes/resin_base.json` (extrudium + `forge:slimeballs`),
  6 resin recipes (Base + tier-anchor + effect ingredient; T1 yield 2 / T2 yield 1). Tag
  `data/mc3dprint/tags/items/resin_gem.json` {diamond, emerald}. (Verify `forge:slimeballs` id.)
- **Config** — `config/MC3DPrintConfig`: `RESIN` section — Treasure {chance, per-print cap,
  rarity weights}, Overdrive {T2 floor=break-even, T3 belowPct=0.20}, OreSalting {saltChance≈0.05,
  per-print cap, gemShare≈0.05}, XP {capT1=160, capT2=550, capT3=1500, REF=33000}, Quartermaster
  {amounts}. (Economy values → delete-stale-config on tuning, per CLAUDE.md.)
- **Client/i18n** — lang keys (item names + tooltips + GUI "Resin"/status), creative tab group.
- **Patchouli** — `resources/.../patchouli_books/guide/...` "Resins" category (intro + per-effect
  + find-T3). Soft-dep.

### Build sequence (each phase compiles + its gametests green before the next)

> **Progress:** ✅ FEATURE COMPLETE — Phases 1–6 all done, 84 gametests green, built as
> `mc3dprint-0.4.0.jar` and deployed to Prism. Phase 5 = `AddCatalystModifier` GLM (uniform
> pick of 5 T3 resins, ~10% in end-game chests; flavor-bias TODO in the class). Phase 6 =
> Patchouli "Resins" category (overview/craft, effects, finding-T3). Deferred follow-ups:
> treasure modded-loot entries (mods extend via their own GLMs); resin-pick flavor-bias;
> in-game tuning of all the numbers + slot-render nudge (Patrick to eyeball).

1. **Scaffold** — ResinItem/Effect/Tier + ResinBase + registration + textures + creative tab +
   recipes + `resin_gem` tag. Resins exist and craft but do nothing yet.
2. **Slot + plumbing** — resin `ItemStackHandler`, menu slot (gap coords, lockstep), screen well +
   label, gen_printer_gui.py. Slot accepts/holds resins; no effect.
3. **Official flag + lifecycle** — `PlayerCreated` (scanner stamps; curated/GLM official),
   `isOfficial`, print-time gate, reserve/lock/consume-on-first-placement (Q6), status line.
   Gametest: rejects player-made, consumes correctly, refund rules.
4. **Effects, one at a time, each + gametest** (forced-chance config): Overdrive → Verdant → XP →
   Treasure (+ loot tables) → Quartermaster → Ore Salting.
5. **T3 loot** — `AddCatalystModifier` + registration + loot_modifier JSONs + GLM entry. Gametest:
   resin drops from a target table.
6. **Guide + polish** — Patchouli pages, lang, full `runGameTestServer` pass, build jar, deploy.

### In-game verify-after-build (not unit-testable)

Slot render placement (Patrick to eyeball, may nudge); effect *feel* + numeric tuning (treasure
rarity, salt density, XP magnitude, overdrive net-gain); enchanted-tool kit; T3 drop frequency
while exploring; that resin truly no-ops on player-made blueprints in-world.

### Economy guardrail summary (why it can't be exploited)

Every value-minting effect is multiply-gated: **official-blueprint-only** (can't catalyze a
self-scanned cheap build) + **consumed per print** + **T3 found-only/unfarmable** (for the
strongest) or **gem-cost craft** (T2) + per-effect caps (Overdrive floor, Treasure/Ore-Salting
per-print caps) + the existing **winder-blacklist** and **down-only/exact-tier winding**.
