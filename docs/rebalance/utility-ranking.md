# Utility Ranking for the Tier Rebalance

_Vanilla 1.20.1 blocks/items ranked by gameplay UTILITY, with a "must-gate" flag and a suggested minimum tier band. Companion to workstream #2 (tier rebalance) — pairs with the FU/rarity model in `docs/FU-VALUES-AND-COMPAT.md` and the current defaults in `FuValueRegistry.defaultEntries()`._

---

## How to read this

MC3DPrint already has two orthogonal anti-exploit gates:

1. **Rarity / FU cost** — winding a material yields exactly what printing it costs, and recipe derivation flows the tier **up** from the most expensive ingredient. So `diamond_block` is already T5, a beacon is already gated by its nether-star ingredient, etc. Most things "price themselves" correctly via derivation.
2. **Strict mode** (`unknownBlocksPrintable=false`) refuses any un-priced block outright, and the **winder exact-tier rule** + **WINDER_BLACKLIST** close the laundering side.

This document is about the **third** axis the first two don't cover: **utility**. A block can be made of dirt-cheap materials (so derivation lands it at T1–T2) yet be *game-defining* — a player would happily spam-print it, and printing it cheaply trivializes a progression gate that vanilla intends you to earn. The fix for those is an **explicit FU override** (`item=fu@tier`) that pins the tier higher than derivation would, because **utility — not rarity — argues for the gate**.

Conversely, plenty of high-utility blocks are *already* expensive (beacon, conduit) or are *meant* to be spammed (chests, rails, building blocks) — those are fine cheap, or fine at their derived tier.

### The three columns

- **Must-gate rank** — how badly does cheap mass-printing of this hurt progression / game feel?
  - **low** = fine to be cheap; spamming it is the intended play pattern, or it's already expensive by rarity.
  - **medium** = give it a respectable floor so it isn't trivial, but it doesn't need a hard late-game wall.
  - **high** = a player would *love* to spam this and doing so meaningfully skips a progression gate — pin it up explicitly.
- **Suggested min tier band** — rough floor. "derived" means leave it to recipe derivation (it already lands sensibly). A band like **T3–T4** means "don't let it fall below T3; T4 is defensible."

### Tier band cheat-sheet (from `MachineTier` + current defaults)

| Band | What lives there today | Machine |
|------|------------------------|---------|
| T1 | cobble/dirt/sand/stone/wood/glass/wool/concrete/terracotta/quartz | single printer |
| T2 | copper, amethyst, lapis, gold, iron, base metals | single printer |
| T3 | redstone, slime, magma cream | single printer (first footprint tier) |
| T4 | emerald | single printer |
| T5 | diamond | **multiblock** fabricator |
| T6 | netherite family | multiblock |
| T7 | nether star, dragon egg | multiblock |
| T8 | (capstone — nothing native yet) | multiblock |

Note the **T2/T3 cliff**: T1–T2 are trivially accessible single printers, T3 is the first footprint-capable printer and the first "you've invested" tier. **The single most important lever in this rebalance is deciding what is allowed to sit at T1–T2 vs. what must clear the T3 line.**

---

## TL;DR — the lists that matter

### Must-gate HIGH (pin these up explicitly; derivation under-tiers them)

These are cheap-material or otherwise low-deriving blocks whose *utility* makes mass-printing a real progression skip. Override each to at least the listed tier.

| Block | Why it's dangerous cheap | Suggested floor |
|-------|--------------------------|-----------------|
| **Hopper** | The keystone of all automation. 5 iron + chest. Spam-printing hoppers = unlimited item logistics for nearly free. Derives ~T2. | **T3** (T4 defensible) |
| **Observer** | Cheapest reliable update detector; the backbone of most "modern" redstone/auto-farms. Cobble + redstone + quartz → derives ~T3. | **T3 floor, lean T4** |
| **Piston / Sticky Piston** | Movement is the core of contraptions (flying machines, hidden doors, farms). Cobble/iron/redstone → derives low. Sticky adds a slime ball. | **T3** (sticky T4) |
| **Sculk Sensor / Calibrated Sculk Sensor** | Wireless redstone. Trivializes huge swaths of signal-routing design. Normally deep-dark-gated. | **T4–T5** |
| **Shulker Box** | Portable 27-slot storage that survives breaking — the single biggest QoL/storage multiplier in the game. Vanilla gates it hard behind the End + shulkers. Derives only from chest + 2 shells, but the *shells* should keep it up. | **T5** (End-gated; don't let it slip below diamond-equivalent) |
| **Respawn Anchor** | Nether spawn-setting. Crying obsidian + glowstone. Strong utility, normally a deliberate nether project. | **T4–T5** |
| **Lodestone** | Permanent compass anchoring / nav network. Netherite-gated in vanilla (chiseled stone + netherite ingot) so derivation *should* land T6 — verify it does; if a pack reprices netherite low, pin it. | **T6** (verify derivation) |
| **Conduit** | "Beacon of the sea" — water breathing/haste/night vision AoE. Heart of the Sea + 8 nautilus shells; both un-priced today, so **strict mode refuses it** — but if anyone prices the shells cheap, it must not fall low. | **T5+** (or leave un-priced) |
| **Beacon** | Full-game power buff. Already nether-star-gated (T7 via the star) **but only if the player prints the whole beacon block** — see the pyramid caveat below. | star keeps the beacon itself T7; **the pyramid is the real risk** |
| **Sculk Shrieker (can_summon)** | Warden summoning / deep-dark mechanic. Niche but griefy if printable cheap; the natural (summoning) variant isn't obtainable anyway. | **T5+** or leave un-priced |

### The "Beacon pyramid" trap (utility-not-rarity, called out separately because it's easy to miss)

A beacon is only useful sitting on a **9/34/83/164-block pyramid of mineral blocks** (iron/gold/diamond/emerald/netherite blocks). The nether star gates the *beacon block* at T7 — but the **pyramid blocks derive from their base material**:

- `iron_block` → 9× iron → ~180 FU @ **T2**
- `diamond_block` → 9× diamond → 450 FU @ **T5**
- `netherite_block` → 9× ingot → 4500 FU @ **T6**

So a full **iron** beacon pyramid (the cheapest valid one, 164 iron blocks) is printable on a **T2** machine. That's arguably fine — iron is the *intended* cheap pyramid and the star is the real gate — **but flag it**: if the rebalance wants the beacon experience to feel earned, the cheapest acceptable lever is leaving the pyramid at its derived tiers (iron T2) and trusting the star. Do **not** accidentally let `diamond_block`/`netherite_block` slip below T5/T6 in a tag sweep, or you cheapen the "max beacon" flex. **Action: none required if derivation is intact; just don't regress the mineral blocks.**

### Utility-not-rarity cases (the headline of this report)

Blocks that are **very useful AND made from cheap materials**, so rarity-based derivation under-tiers them. These are exactly where an explicit override earns its keep:

| Block | Cheap because | Utility argues for | Override to |
|-------|---------------|--------------------|-------------|
| **Hopper** | iron + chest | unlimited cheap automation | **T3+** |
| **Observer** | cobble + redstone + quartz | wireless-ish update detection, auto-farm backbone | **T3+** |
| **Piston / sticky** | cobble + iron + redstone (+slime) | all contraption movement | **T3 / T4** |
| **Dispenser / Dropper** | cobble + redstone (+ bow) | farm actuators, item routing | **T2–T3** |
| **Note Block / Daylight Detector / Target** | wood/quartz/redstone/hay | cheap but high-utility redstone primitives — fine low, but worth a T2 floor so they're not literally free | **T2** |
| **Rails (powered/detector/activator)** | iron/gold + redstone/stick | minecart logistics at scale | **T2–T3** (gold/redstone keep powered/activator above plain) |
| **Sculk Sensor** | (deep-dark, but if priced) | wireless redstone | **T4–T5** |
| **Bookshelf** | 6 planks + 3 books (leather + paper) | enchanting power — see enchanting cluster | **T2–T3** |

Everything else either prices itself correctly (rare ingredients) or is *meant* to be cheap (building/storage blocks).

---

## Full ranking by category

Legend: **MG** = must-gate rank (low/med/high). **Band** = suggested minimum tier. "derived" = leave to recipe derivation.

### Power / utility blocks

| Block | What it does / why players want it | MG | Band |
|-------|-------------------------------------|----|----|
| **Beacon** | Full-map status buffs (haste/speed/jump/resist/regen). | low* | **T7** (already star-gated). *Pyramid is the caveat above. |
| **Conduit** | Underwater haste + breathing + night vision + hostile-mob damage AoE. | high | **T5+** or leave un-priced (Heart of the Sea / nautilus shells). |
| **Enchanting Table** | The enchanting engine. Obsidian + diamond + book → derives ~**T5** (diamonds). | low | **derived (T5)** — diamond keeps it honest. |
| **Bookshelf** | +1 enchant power each; 15 = max-level table. Cheap (planks + books). | med | **T2–T3** — utility-not-rarity; don't let a roomful be free. |
| **Anvil** | Repair, rename, combine enchants. 3 iron blocks + 4 iron → derives ~**T2**. | med | **T2–T3** — heavy QoL; a small floor is fair. |
| **Grindstone** | Disenchant + repair, recover XP. Stone + wood + sticks → ~**T1**. | low | **derived / T1–T2**. |
| **Smithing Table** | Netherite + trim upgrades. Iron + planks → ~**T2**. | low | **derived** (the *netherite template* is the real gate, not the table). |
| **Brewing Stand** | All potions. Blaze rod + cobble → blaze rod un-priced ⇒ **strict refuses it**. | med | leave un-priced **or** ≥**T3** if priced (blaze rod is a nether gate). |
| **Cauldron** | Water/lava/potion storage, dye/leather wash. 7 iron → ~**T2**. | low | **derived / T2**. |
| **Lodestone** | Compass anchor for permanent navigation. Netherite-gated → **T6**. | high | **T6** (verify derivation holds it there). |
| **Respawn Anchor** | Set spawn in the Nether. Crying obsidian + glowstone. | high | **T4–T5**. |
| **Bell** | Raid alert, decorative ping. Ingot + wood; ingot is gold-ish → ~**T2**. | low | **derived / T2**. |
| **Lightning Rod** | Diverts lightning, charges blocks, redstone pulse on strike. 3 copper → ~**T2**. | low | **derived / T2**. |
| **Sculk Sensor** | Vibration-driven **wireless redstone**. | high | **T4–T5** (utility, not its trivial craft). |
| **Calibrated Sculk Sensor** | Frequency-filtered wireless redstone — even stronger. | high | **T4–T5**. |
| **Sculk Shrieker** | Warden-summoning shrieker (the dangerous variant only spawns naturally). | high | **T5+** or leave un-priced. |
| **Sculk Catalyst / Sculk / Veins** | Spread XP-harvest blocks; niche, mostly decorative for most players. | low | **derived / leave un-priced**. |

### Storage / automation

| Block | What it does / why players want it | MG | Band |
|-------|-------------------------------------|----|----|
| **Chest / Trapped Chest** | Baseline 27-slot storage. Meant to be spammed. | low | **T1** (planks). |
| **Barrel** | Chest-equivalent that opens flush; planks + slabs. | low | **T1**. |
| **Ender Chest** | Shared cross-location inventory (8 obsidian + eye of ender). Eye un-priced ⇒ **strict refuses** today. | med | leave un-priced **or** ≥**T4** if priced (obsidian + eye = a real gate). |
| **Shulker Box** | Portable, contents-preserving 27-slot box — top-tier storage/QoL. End-gated. | high | **T5** — don't let it slip below diamond-equivalent. |
| **Hopper** | Item-transfer keystone of ALL automation. | high | **T3+** (T4 defensible). |
| **Dropper** | Pushes items without auto-pull; spawner-farm actuator. | med | **T2–T3**. |
| **Dispenser** | Fires/places items (arrows, water, TNT) — farm + trap actuator. | med | **T2–T3** (bow keeps it above dropper). |
| **Furnace** | Baseline smelting. | low | **T1** (cobble). |
| **Blast Furnace** | 2× ore/metal smelting. Furnace + iron + smooth stone. | low | **T2** (iron). |
| **Smoker** | 2× food smelting. Furnace + logs. | low | **T1–T2**. |
| **Observer** | Block-update detector — auto-farm / contraption backbone. | high | **T3+**. |
| **Comparator** | Reads container fullness, redstone math. Quartz + redstone + stone. | med | **T2–T3** (quartz). |
| **Repeater** | Delay/lock/extend redstone signal. Stone + redstone + torch. | med | **T2**. |
| **Piston** | Moves up to 12 blocks — core of all contraptions. | high | **T3**. |
| **Sticky Piston** | Piston that pulls back — flying machines, doors, retractable farms. | high | **T4** (slime ball). |
| **Crafter (1.21)** | **NOT IN 1.20.1.** Auto-crafting block; flag for the future-version branch only. Would be **high / T4+** when targeted. | n/a | **N/A in 1.20.1** — ignore until a 1.21 target. |

### Transport / redstone

| Block | What it does / why players want it | MG | Band |
|-------|-------------------------------------|----|----|
| **Rail** | Minecart track. Iron + stick; spammed by design. | low | **T2** (iron floor). |
| **Powered Rail** | Accelerates/brakes carts — long-haul transit. Gold + redstone + stick. | med | **T2–T3** (gold + redstone). |
| **Detector Rail** | Cart-presence trigger. Iron + stone pressure plate + redstone. | med | **T2–T3**. |
| **Activator Rail** | Activates command/TNT/hopper carts. Iron + redstone torch + stick. | med | **T2–T3**. |
| **Redstone Block** | Always-on power + compact storage; 9 redstone → ~**T3**. | low | **derived (T3)**. |
| **Redstone Lamp** | Toggleable light. Glowstone + redstone → glowstone keeps it ~**T2+**. | low | **derived / T2**. |
| **Redstone Torch** | Inverter / power source; redstone + stick. | low | **derived / T2**. |
| **Target** | Redstone output scaled by hit accuracy; hay + redstone. | low | **T2** floor. |
| **Daylight Detector** | Day/night & inverted signal; glass + quartz + slabs. | low | **T2** (quartz). |
| **Note Block** | Music + redstone pulse; planks + redstone. | low | **T2** floor (cheap but useful). |
| **Tripwire Hook** | String-trip trigger; iron + stick + planks. | low | **T2**. |
| **Lever / Button / Pressure Plate** | Basic inputs; trivially cheap, fine. | low | **T1–T2**. |

### Lighting / decor prized by builders

These are **builder demand**, not progression power — almost all are fine cheap. The point of including them is that bulk-printing decorative blocks is *the headline feature* of MC3DPrint, so under-pricing here is a feature, not a leak. The only nuance is keeping the *rare-source* ones from being launderable into FU below their natural tier.

| Block | Why builders want it | MG | Band |
|-------|----------------------|----|----|
| **Sea Lantern** | Bright, clean underwater/modern light. Prismarine shards + crystals (un-priced today ⇒ strict refuses). | low | leave un-priced **or** **T2–T3** if priced. |
| **Glowstone** | Warm full-bright light; nether-sourced (or 4 dust). | low | **T2** (nether gate ≈ T2-ish). |
| **Shroomlight** | Soft orange nether light; huge-fungus drop, no recipe ⇒ likely un-priced. | low | leave un-priced or **T2**. |
| **Froglight (3)** | Pearlescent/verdant/ochre — premium builder light; frog + magma cube only, no recipe. | low | leave un-priced or **T3** (rare-source). |
| **Amethyst Block / Budding / Clusters** | Trendy purple light + sound; shards already **T2** (10@2). Budding amethyst is non-obtainable in survival. | low | shards **T2**; **leave budding un-priced** (can't get it legit). |
| **Copper (all oxidation + waxed + cut/stairs/slab/grate/bulb/door/trapdoor)** | Huge modern-build palette + the **Copper Bulb** (light + redstone). Copper ingot **T2**. | low | **derived / T2** — fine cheap; it's a building-block showcase. |
| **Terracotta / Glazed Terracotta** | Patterned color palette; terracotta **T1** (5@1). | low | **T1**. |
| **Concrete (all 16)** | Solid saturated color — the builder workhorse. Already **T1** (5@1). | low | **T1**. |
| **Deepslate Bricks / Tiles / Polished** | Dark stone palette; derives from deepslate/cobbled deepslate ~**T1**. | low | **T1**. |
| **Quartz (block/smooth/pillar/bricks/stairs/slab)** | Clean white build staple; quartz block **T1** (5@1). | low | **T1**. |
| **Prismarine / Bricks / Dark** | Aquatic build palette; shard/crystal-sourced (un-priced today). | low | leave un-priced or **T2**. |

---

## Recommendations / actions for workstream #2

1. **Add explicit overrides for the utility-not-rarity set.** Derivation will under-tier these; pin them with `item=fu@tier`:
   - `minecraft:hopper` → **T3+**
   - `minecraft:observer` → **T3+**
   - `minecraft:piston` → **T3**, `minecraft:sticky_piston` → **T4**
   - `minecraft:bookshelf` → **T2–T3**
   - `minecraft:anvil` → **T2–T3**
   - (optional) `dispenser`/`dropper` → **T2–T3**, `note_block`/`target`/`daylight_detector` → **T2** floors
   - rails: keep `powered_rail`/`activator_rail` a notch above plain `rail` via their gold/redstone derivation; add a T2 floor if needed.
2. **Verify, don't regress, the rarity-gated set.** Beacon (star), enchanting table (diamond), lodestone (netherite), shulker box (End), netherite/diamond blocks — confirm derivation lands them where expected and that **no tag sweep accidentally lowers `diamond_block`/`netherite_block`** (beacon-pyramid integrity).
3. **Decide the un-priced borderline blocks** explicitly (they're refused by strict mode today, which is a *safe* default): conduit, ender chest, brewing stand, sea lantern, prismarine, shroomlight, froglight, budding amethyst, sculk shrieker. Either keep them un-priced (clean, strict-safe) or, if you want them printable, price them at the floors above — don't let them land cheap by accident if someone flips `unknownBlocksPrintable` on.
4. **Leave non-obtainable blocks un-priced on purpose:** budding amethyst, naturally-summoning sculk shrieker, command blocks, spawners, etc. — pricing them would create survival-illegal acquisition.
5. **Sanity-check against existing gametests** (`TierGating`, `FuTierEconomy`, `RecipeDerivation`): any override that changes a tier these assert will need the test updated in lockstep (noted in the roadmap ripple list).

---

## One-line rationale per "high" gate (for commit/PR copy)

- **Hopper / Observer / Pistons** — automation primitives; cheap mats, game-defining utility ⇒ explicit T3/T4.
- **Sculk sensors** — wireless redstone shouldn't be a T1 trivial print.
- **Shulker box** — best-in-game portable storage; keep it End-tier (T5).
- **Respawn anchor / Lodestone / Conduit** — deliberate mid/late-game projects; keep them above the single-printer cliff.
- **Beacon pyramid** — star gates the beacon; just don't let the mineral blocks regress below their derived tiers.
