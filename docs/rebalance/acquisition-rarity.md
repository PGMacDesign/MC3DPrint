# Vanilla Minecraft 1.20.1 — Acquisition-Difficulty Ranking for the Tier Rebalance

_Research input for MC3DPrint's T1–T8 tier rebalance (workstream #2, `docs/ROADMAP.md`).
Facts verified against [minecraft.wiki](https://minecraft.wiki) for **Java Edition 1.20.1**._

---

## Why this document exists (the laundering threat model)

MC3DPrint prints blocks anywhere, paid for in **Filament Units (FU)**. You **wind** a
material into a spool **of that material's exact tier**, and a spool can print anything
**at or below** its tier ("print-down"). Therefore:

> **An item's TIER = the tier of spool needed to print it = the tier of the materials a
> player must obtain and wind to make that spool.**

The exploit to prevent is **laundering**: if a valuable item is priced at a *low* tier, a
player winds cheap low-tier junk (cobble, dirt) into a low-tier spool and prints the
valuable item for almost nothing. So **tier must track how hard the item is to OBTAIN**,
not how "nice" it looks. This is exactly why the current economy has **diamond = T5** and
**netherite family = T6** even though both are "just a gem/ingot."

This doc ranks vanilla materials on a **1–8 acquisition-difficulty scale** so the
rebalance (`FuValueRegistry.java` + `data/mc3dprint/tags/items/filament/tier_N.json`) can
tier them coherently. The 1–8 scale is aligned to the mod's existing machine tiers and
current anchors:

| Tier | Difficulty band | Current anchors (from `FuValueRegistry.defaultEntries()`) |
|------|-----------------|------------------------------------------------------------|
| **T1** | Trivial — surface/early mining, infinite | cobble, dirt, sand, gravel, stone family, wood, glass, wool, concrete |
| **T2** | Easy ore tier — shallow ores, smelting | copper, iron, gold, lapis, amethyst shard, redstone (low) |
| **T3** | Mob/processing tier — drops + Nether intro | redstone, slime, magma cream, glowstone, quartz |
| **T4** | Renewable-valuable — villager/raid economy, Nether mob loot | **emerald**, blaze rods, ghast tears, prismarine, totem |
| **T5** | Deep / high-value mining + ocean-monument loot | **diamond**, sea lantern, sponge*, nautilus, ender pearl |
| **T6** | Netherite tier + End-renewable boss loot | **netherite family**, shulker shell, chorus/purpur, end rod, trident |
| **T7** | Boss / heavy-grind finite-adjacent | **nether star**, **dragon egg**, wither skeleton skull, beacon, dragon head, conduit, elytra |
| **T8** | Finite chest-only / one-per-world / mob-head trophies | echo shard, swift sneak, disc 5/pigstep, ench. golden apple, heart of the sea, mob heads, sponge (if you'd rather gate it here) |
| **—** | **UNOBTAINABLE in survival → should be UNPRINTABLE, not tiered** | reinforced deepslate, budding amethyst, spawner, bedrock, barrier, end portal frame, etc. |

> The bands are guidance, not a straitjacket. Where an item is genuinely ambiguous
> (sponge, ender pearl) I give the recommended tier **and** the reasoning so the rebalance
> can place it deliberately.

**Renewable vs finite is the single most important axis for laundering risk.** A finite or
boss-only item that a player can *only* get a handful of per world is catastrophic to
under-tier — if it's cheap to print, the player effectively duplicates a one-of-a-kind
item. Renewable-but-grindy items are less dangerous (the player could farm them anyway),
but still must be tiered to the grind, not the recipe.

---

## How to read each entry

`acquisition path · gating (dimension / structure / boss / depth) · renewable vs finite ·
TIER (1–8) · one-line justification`. **Laundering-risk** call-outs are flagged inline.

---

## T1 — Trivial (infinite, surface or shallow, no gating)

Everything here is effectively unlimited and obtained with no progression. These are the
correct **winding feedstock** for low-tier spools — and exactly what a launderer would try
to use, so nothing valuable may sit at T1.

| Item / group | Acquisition | Gating | Renewable? | Tier | Justification |
|---|---|---|---|---|---|
| Dirt, coarse dirt, mud, clay | dig surface | none | yes (infinite terrain) | **1** | unlimited bulk fill |
| Sand, red sand, gravel | dig surface | none | yes | **1** | unlimited; gravel→flint trivial |
| Cobblestone, stone, deepslate, cobbled deepslate | mine, or cobble generator | none | **yes (cobble gen — truly infinite)** | **1** | the canonical infinite block |
| Stone family: granite, diorite, andesite, tuff, calcite | mine (calcite/tuff in geodes/deepslate) | none | yes | **1** | common worldgen |
| Smooth stone, stone bricks, sandstone, smooth/red sandstone | craft/smelt from above | none | yes | **1** | processed T1 |
| Wood: all 8 log/plank families, stripped, bark, slabs/stairs/fences | chop trees | none | **yes (tree farm)** | **1** | infinite via saplings |
| Wool (all 16), carpet, string→wool | sheep shear / craft | none | yes (sheep regrow wool) | **1** | trivial farm |
| Glass, glass panes, tinted? (tinted needs amethyst→T2) | smelt sand | none | yes | **1** | sand is infinite |
| Terracotta + 16 dyed, glazed terracotta | smelt clay + dye | none | yes | **1** | clay renewable (dripstone/mud) |
| Concrete + concrete powder (all 16) | sand+gravel+dye | none | yes | **1** | bulk colored fill |
| Netherrack, soul sand, soul soil, basalt, blackstone | mine Nether (but trivially common) | **Nether** | yes (soul sand barterable) | **1** | Nether bulk; gating is access only |
| Dyes (most), bone meal, ink, cocoa | farm/mobs | none | yes | **1** | trivial |
| Dirt-path, farmland | place/till | none | yes (drops dirt) | **1** | see note: no distinct item, drops dirt |
| Snow, ice, packed ice (Silk Touch), powder snow (bucket) | gather cold biomes | none | yes (snow regen; ice w/ Silk Touch) | **1** | trivial w/ Silk Touch |
| Coal | mine coal ore (very shallow, abundant) | none | **yes (wither sk. farm? no — coal is mined; charcoal renewable)** | **1** | _current: `coal=2@1`; coal_block derives 18@1._ Charcoal (smelt logs) is the renewable equivalent. |

**Laundering note:** these are the *spools you wind to launder with*. The whole rebalance
exists to keep valuables **above** this band.

---

## T2 — Early ores & smelted metals (shallow mining + smelting)

The first "you had to mine and smelt" tier. Renewable in the sense that ore keeps
generating in unexplored chunks; copper/iron also have a slow renewable mob path
(drowned drop copper; iron golems / iron farms for iron).

| Item / group | Acquisition | Gating | Renewable? | Tier | Justification |
|---|---|---|---|---|---|
| Copper ingot, raw copper, copper block, cut/oxidized/waxed variants | mine copper ore (shallow→deepslate), smelt; **drowned drop raw copper** | depth: shallow | **yes (drowned farm)** | **2** | _current: `copper_ingot=10@2`._ Abundant, low value |
| Iron ingot, raw iron, iron block, nuggets | mine iron ore, smelt; **iron golem / iron farm** | depth: shallow-mid | **yes (iron farm — fully renewable)** | **2** | _current: `iron_ingot=20@2`, `iron_nugget=2@2`._ Backbone metal |
| Gold ingot, raw gold, gold block, nuggets | mine gold ore (deeper / badlands / Nether gold ore); **zombified piglin farm, bartering input** | depth: mid (or Nether) | **yes (zombified piglin / Nether gold farms)** | **2** | _current: `gold_ingot=15@2`, `gold_nugget=1@2`._ Renewable, soft |
| Lapis lazuli, lapis block | mine lapis ore (mid depth) | depth: mid | finite per chunk / unbounded explore | **2** | _current: `lapis_lazuli=10@2`._ Mined only, no mob source |
| Amethyst shard, amethyst block, amethyst cluster (Silk Touch / fully grown) | break amethyst clusters in geodes | none (geodes shallow-ish) | **yes (budding amethyst regrows clusters)** | **2** | _current: `amethyst_shard=10@2`._ Renewable via budding (the budding block itself is unobtainable — see UNPRINTABLE) |
| Redstone dust, redstone block | mine redstone ore (deep-ish); witch drop | depth: deep | **yes (witch farm)** | **2–3** | _current: `redstone=4@3`._ Cheap per-unit but deep; the **@3** in current config reflects the deep-mining gate — keep redstone at **T3** even though FU is low. |
| Flint | gravel break | none | yes | **2** | trivial but separate item |
| Quartz (Nether) — _see T3_ | — | Nether | — | — | — |

**Iconic-but-easy flag:** gold *looks* premium but is one of the **most renewable metals**
(Nether gold farms, zombified piglin gold farms, bartering). Keep it **T2**. Same for
iron (iron farms make it infinite). Under-tiering would be safe here only because the
inputs are also cheap — but a gold **block** print at T1 would be a laundering win, so the
derived-tier-flows-up rule (gold_block → 135@2) must hold.

---

## T3 — Mob drops, processing, Nether-intro materials

Drops from common hostile mobs, slime/magma, and the easy half of the Nether. All
renewable via farms; the gate is "you've built a mob farm or gone to the Nether."

| Item / group | Acquisition | Gating | Renewable? | Tier | Justification |
|---|---|---|---|---|---|
| Redstone (if not placed at T2 above) | deep mining / witch | depth: deep | yes | **3** | _current `@3`._ Deep-mining gate |
| Slimeball, slime block | slimes (swamp/slime chunks) / slime farm; **panda sneeze** | slime chunk / swamp | **yes (slime farm)** | **3** | _current: `slime_ball=30@3`; slime_block derives 270@3._ Locating slime chunks is the gate |
| Magma cream, magma block | magma cubes (Nether) **or** craft (blaze powder + slimeball) | **Nether** | yes | **3** | _current: `magma_cream=30@3`._ Needs Nether or blaze+slime |
| Glowstone, glowstone dust | mine Nether ceiling; **witch drop** (NOT bartering in 1.20.1) | **Nether** (block); witch (dust) | **yes (witch farm for dust)** | **3** | Nether-gated block; dust renewable via witches. _Correction: glowstone dust is **not** a piglin-barter drop in 1.20.1 (removed 1.16.2)._ |
| Nether quartz, quartz block, smooth/chiseled/pillar quartz | mine Nether quartz ore; **piglin bartering (~2.56%, 5–12)** | **Nether** | **yes (bartering + abundant ore)** | **3** | _current: `quartz_block=5@1` — consider bumping the **raw quartz** to T3 for the Nether gate, though processed quartz blocks as cheap build material at T1/T3 is a design choice; flag for review._ |
| Gunpowder, bone, rotten flesh, spider eye, ender pearl-? (no), arrows | common mob farms | none | yes | **3** | trivial mob farm loot |
| Nether brick, red nether brick, nether wart block | craft from netherrack/wart | **Nether** | yes (nether wart farm) | **3** | Nether processing |
| Nether wart | harvest in fortress / farm | **Nether (fortress to start)** | **yes (farms on soul sand)** | **3** | needs one fortress seed, then infinite |
| Honey block, honeycomb block | bees | none | yes | **3** | bee farm |
| Sponge (placement note) — _see T5/T8_ | — | monument | — | — | — |

**Design call-out (quartz):** `quartz_block` is currently `5@1` (cheap build material).
That's fine for *building*, but note it lets a T1 spool print a Nether-sourced look. Not a
laundering risk (quartz has no high-value use), just flagging the inconsistency vs the
Nether gate. Leave as-is unless you want strict dimensional gating.

---

## T4 — Renewable-valuable: villager/raid economy + Nether mob loot

The "you've engaged a real economy or farmed the early Nether" tier. **Emerald lives here**
(current `emerald=50@4`). Everything is renewable but requires a built farm or trading hall.

| Item / group | Acquisition | Gating | Renewable? | Tier | Justification |
|---|---|---|---|---|---|
| **Emerald, emerald block** | **villager trading** (primary); mine emerald ore (mountains only, very rare) | none (trade) / mountain biome (ore) | **yes — villager trading is fully renewable** | **4** | _current: `emerald=50@4`; emerald_block derives 450@4._ Renewability is **why it sits below diamond.** Mined emerald is rarer than diamond, but the trade path makes it renewable → T4 is correct. |
| Blaze rod, blaze powder | blaze drop (Nether fortress); **blaze spawner farm** | **Nether fortress** | **yes (spawner farm)** | **4** | fortress-gated but heavily farmable; feeds eyes of ender, brewing |
| Ghast tear | ghast drop (Nether) | **Nether** | yes (ghast farm, awkward) | **4** | renewable but low rate; brewing ingredient |
| Prismarine, prismarine bricks, dark prismarine, prismarine shard/crystal | guardian drops at **ocean monument**; **guardian farm** | **ocean monument** | **yes (guardian farm — fully renewable)** | **4** | monument-gated but farmable; shards are the renewable root |
| Sea lantern | craft (4 shard + 5 crystal) or mine monument | **ocean monument** | yes (via guardian farm) | **4–5** | renewable but monument-gated; pair with prismarine. Put at **T4** if guardian-farmed; **T5** if you treat the monument as a diamond-equivalent gate |
| **Totem of Undying** | **evoker drop** (woodland mansion + **raids**) | mansion / **raid** | **yes (raid farm — renewable since 1.14)** | **4** | _Iconic, looks legendary, but renewable via raid farms._ T4 keeps it honest; do **not** under-tier — a cheap totem print is a strong laundering target. Could justify **T5** for the raid-setup effort. |
| Bell, banner patterns (loot), village/raid loot | raids, villages | village / raid | mostly renewable | **4** | raid economy |
| Crying obsidian, respawn anchor | **piglin bartering (~8.53%, 1–3)** | **Nether (bartering)** | **yes (bartering — "only renewable source")** | **4** | renewable via gold farm → barter |
| Soul speed books, spectral arrows | piglin bartering | **Nether (bartering)** | yes | **4** | bartering outputs |

**Laundering-risk highlights at T4:** **Totem of Undying** is the big one — it is the most
"valuable-feeling" renewable item in the game. Make sure its tier (and the
**derived/explicit FU**) is high enough that you can't wind a few raids' worth of cobble to
print one. Recommend **explicit entry**, not derivation (it has no crafting recipe to
derive from — it would fall through to *unknown* and, in strict mode, become NOT_PRINTABLE,
which is actually a safe default). **Decide deliberately:** either give totem an explicit
high FU @ T4–T5, or leave it unprintable.

---

## T5 — Deep / high-value mining + ocean-monument & enderman loot

The diamond tier. The gate is "deep mining (Y<0), an ocean monument, or an enderman farm."

| Item / group | Acquisition | Gating | Renewable? | Tier | Justification |
|---|---|---|---|---|---|
| **Diamond, diamond block** | mine diamond ore (deepslate, **Y≈-58 optimal**) | **depth (deep)** | **finite per chunk / unbounded explore (NOT farmable)** | **5** | _current: `diamond=50@5`; diamond_block derives 450@5._ The reference T5 anchor. Not renewable → never under-tier. |
| Ender pearl | enderman drop; **piglin bartering (~2.13%, 2–4)** | none (endermen common) / Nether | **yes (enderman farm OR bartering)** | **5** | renewable but the enderman/End association makes it feel high; **T5** matches the eye-of-ender progression. Could argue **T4** since farmable — but keep **T5** to gate End access. |
| Eye of ender | craft (pearl + blaze powder) | Nether + enderman | yes | **5** | consumed on use; gateway to End |
| Sea lantern (if not at T4) | see T4 | ocean monument | yes | **5** | see T4 note |
| **Sponge / wet sponge** | **ocean monument** sponge rooms + **elder guardian** drop | **ocean monument** | **FINITE — elder guardians don't respawn; regular guardians don't drop sponge** | **5–8** | _**Correction & laundering flag:** sponge is **NOT renewable.**_ Each monument has a fixed sponge supply; you must find new monuments. Iconic + finite = **laundering-sensitive**. Recommend **T5 minimum**, and consider **T8** (finite chest/structure band) if you want to be strict. |
| Obsidian, crying obsidian (obsidian via water+lava or bartering) | water on lava source; **bartering** | none | **yes (renewable)** | **5** | _current docs example prices obsidian `8@2` — but its **use** (nether portals, end crystals, enchanting tables) is high-value. Tiering at T2 is fine for build material; flag if you consider portal-frame printing._ |

**Laundering-risk highlights at T5:** **sponge** is the sleeper — players think "ocean
junk," but it's genuinely finite. **Do not let sponge sit below T5.** Diamond is the
anchor and must never drop below T5.

---

## T6 — Netherite tier + End-renewable boss loot

"You've beaten the Nether's hardest mining (ancient debris) or reached the End's outer
islands." Netherite is finite; the End-renewable loot here is renewable-but-deep.

| Item / group | Acquisition | Gating | Renewable? | Tier | Justification |
|---|---|---|---|---|---|
| **Netherite ingot, scrap, block; ancient debris** | mine ancient debris (**Y≈16**, needs diamond pick), smelt→scrap, 4 scrap + 4 gold→ingot | **Nether + deep + needs diamond tier** | **FINITE (never regenerates in-place; unbounded only via new chunks)** | **6** | _current: `netherite_ingot=500@6`, `scrap=125@6`, `ancient_debris=125@6`; netherite_block derives 4500@6._ Finite + hardest mining → the T6 anchor. Never under-tier. |
| Netherite Upgrade smithing template | bastion loot; **duplicable (1 + 7 diamond + 1 netherite → 2)** | bastion | **yes (duplication)** | **6** | renewable via duplication; gate is bastion + diamonds |
| **Shulker shell, shulker box** | shulker drop in **End city**; **shulker duplication farm** | **End city** | **yes (duplication mechanic — resolved as renewable)** | **6** | _Correction: shulkers self-duplicate → shells are **renewable/farmable**._ Still End-gated and grindy → T6. |
| Chorus fruit, popped chorus fruit, chorus flower | grows on **End outer islands**; **chorus farm** | **End (outer islands)** | **yes (chorus farm)** | **6** | renewable but requires crossing the End gateway (post-dragon) |
| Purpur block/pillar/slab/stairs | craft from popped chorus fruit | **End (outer islands)** | yes | **6** | renewable via chorus farm |
| End rod | craft (blaze rod + popped chorus fruit) | Nether + End | yes | **6** | two-dimension recipe |
| End stone, end stone bricks | mine in the End | **End** | yes (central island regenerates on dragon respawn) | **6** | End-gated bulk; could argue T5, but End access is the real gate. Consider **T5** if you want it cheaper as a build block. |
| **Trident** | **natural drowned** spawn holding one (Java 6.25% spawn × 8.5% drop) | none (river/ocean) | **yes — but extremely grindy** | **6** | renewable in theory, brutal in practice. Iconic weapon → keep high. Zombie-converted drowned never carry tridents. |
| Nautilus shell | fishing / drowned / wandering trader | none | **yes (fishing — fully renewable)** | **5–6** | renewable; pair with conduit progression. **T5** is defensible; place with trident/conduit at **T6** if you want the conduit set coherent |

**Laundering-risk highlights at T6:** **trident** (iconic, finite-feeling, brutal grind)
and **shulker shell** (the storage-tier item everyone wants). Both renewable but
deep — under-tiering either is a strong launder. Keep at **T6**.

---

## T7 — Boss / heavy-grind / finite-adjacent

The "you fought a boss or did a structure-clear grind" tier. Mix of renewable-boss
(nether star, dragon head) and strictly-finite (dragon egg, elytra).

| Item / group | Acquisition | Gating | Renewable? | Tier | Justification |
|---|---|---|---|---|---|
| **Nether star** | **Wither boss** drop (always 1) | **boss (Wither)** — needs 3 wither sk. skulls + 4 soul sand | **yes (Wither is resummonable; soul sand barterable)** | **7** | _current: `nether_star=1500@7`._ Renewable boss but enormous setup → T7 anchor. Gates beacons. |
| Beacon | craft (nether star + 5 glass + 3 obsidian) | boss (via star) | yes (renewable inputs) | **7** | inherits the star's tier via derivation |
| **Wither skeleton skull** | wither skeleton rare drop (**2.5% base / 5.5% Looting III**); **100% via charged creeper** | **Nether fortress** | **yes (fortress farm)** | **7** | renewable but the rate + its role gating the nether star = high. Keep at **T7** so you can't cheaply print the skulls that summon the Wither. **Major laundering target** — printing skulls trivializes nether-star access. |
| **Dragon egg** | drops **only on the FIRST** ender dragon kill | **boss (dragon), one-time** | **FINITE — exactly 1 per world, NEVER renewable** | **7** | _current: `dragon_egg=2500@7`._ The most exploit-sensitive item in the game. **Printing it = duplicating a one-of-a-kind item.** Consider **T8 or UNPRINTABLE** rather than merely T7 — see recommendation below. |
| Dragon head | one per **End ship** | **End city / ship** | **yes (renewable per wiki — one per ship, ships generate in new End regions)** | **7** | renewable via exploration but a trophy; keep high |
| **Elytra** | **only** from item frame in **End ship** treasure room | **End city / ship** | **FINITE per structure (wiki: Renewable: No); unbounded only via exploration** | **7** | _Iconic + finite._ One per ship, no other source, **not craftable.** Strong laundering target. **T7 minimum; consider T8.** |
| Conduit | craft (heart of the sea + 8 nautilus shells) | **buried treasure (for heart)** | **NON-renewable (gated by finite heart of the sea)** | **7** | inherits finiteness from heart of the sea |
| Enchanted books (rare: Mending, Soul Speed, etc.) | trades / fishing / loot | varies | mostly renewable (villager trades) | **7** | Mending via librarian is renewable; tier by effort |

**Laundering-risk highlights at T7 — the danger zone:**
- **Dragon egg** — finite, 1 per world. *Strongest case in the entire game for making it
  UNPRINTABLE.* If printable at all, it must be T8 and ideally explicitly blacklisted from
  the winder (you can't wind cheap junk into a T7/T8 dragon-egg spool).
- **Elytra** — finite per ship, no craft. High demand → highest practical launder value.
  T7+ and consider blacklisting.
- **Wither skeleton skull** — gates the nether star; printing skulls collapses boss
  progression. Keep at T7.

---

## T8 — Finite chest-only, one-per-structure, and trophy heads

The top band: items with **no renewable source at all in 1.20.1** — loot-chest-only or
charged-creeper-only. These are the **most laundering-sensitive items in the game** because
a player may only ever legitimately get a few per world. Strongly prefer **explicit high-FU
@ T8 + winder blacklist**, or **UNPRINTABLE**, over derivation.

| Item / group | Acquisition | Gating | Renewable? | Tier | Justification |
|---|---|---|---|---|---|
| **Echo shard** | **ancient city loot chests only** (1–3, 30.4%) | **ancient city** | **FINITE — no mob/block/craft source** | **8** | chest-only; crafts recovery compass. Under-tiering = printing un-farmable loot |
| Recovery compass | craft (4 echo shard + compass) | ancient city | finite (via echo shard) | **8** | inherits echo shard finiteness |
| **Swift Sneak (enchanted book)** | **ancient city loot only** (23.7%) | **ancient city** | **FINITE — not enchantable/tradable** | **8** | chest-only enchant; the only Swift Sneak source |
| **Music Disc 5** | craft 9 **Disc Fragment 5** (ancient city, 29.8% each) | **ancient city** | **FINITE** | **8** | chest-fragment-only |
| **Music Disc Pigstep** | **bastion remnant chests** (~5.6%) | **bastion** | **FINITE** | **8** | chest-only |
| Music Disc otherside | monster room / stronghold / ancient city chests | structures | **FINITE** | **8** | chest-only |
| Music Disc Relic | brush suspicious gravel in **Trail Ruins** | trail ruins | **FINITE** | **8** | one-time archaeology |
| Creeper-pool discs (13, cat, blocks, chirp, far, mall, mellohi, stal, strad, ward, 11, wait) | **creeper killed by skeleton** | none | **yes (skeleton-vs-creeper farm)** | **6–7** | _these 12 ARE renewable_ — tier below the chest discs. Place at T6–T7, **not** T8 |
| **Enchanted Golden Apple** ("god apple") | **loot chests only** — **NOT craftable in 1.20.1** | structures | **FINITE** (renewable ominous-vault path is 1.21, not 1.20.1) | **8** | _Iconic, hugely valuable, finite._ **Top-3 laundering target.** Recommend T8 + blacklist or UNPRINTABLE |
| **Heart of the Sea** | **buried treasure chests only** (1/chest) | **buried treasure** | **FINITE** (unbounded only via exploration) | **8** | the conduit bottleneck; chest-only |
| **Mob heads** — zombie/skeleton/creeper/piglin heads | **charged-creeper kill** (100%, but 1 head per explosion) | none (need charged creeper) | **yes — but throughput-limited** | **7–8** | renewable via lightning/trident-channeling farms but very slow; trophies. T7–T8 |
| Player head | **command/creative only** in survival | — | **UNOBTAINABLE in survival** | **— UNPRINTABLE** | see UNPRINTABLE section |
| Banner patterns (loot variants), other rare chest loot | structures | structures | finite | **7–8** | tier by structure |

**Laundering-risk highlights at T8 — the most dangerous items to under-tier:**
1. **Enchanted Golden Apple** — finite, no craft, extremely valuable. Print-laundering it
   is a game-breaker.
2. **Heart of the Sea** — finite, gates conduits.
3. **Echo shard / Swift Sneak / Disc 5 / Pigstep** — all ancient-city/bastion chest-only;
   ancient cities **never regenerate**.

For all of these: prefer **explicit FU @ T8** (so derivation can't accidentally underprice
them) **and** a **winder blacklist entry** (so even a T8 spool can't be made from cheap
materials and then used to print them). Or simply make them **UNPRINTABLE** — for true
one-per-world trophies that's the cleanest answer.

---

## UNPRINTABLE — unobtainable in survival (do NOT tier these; gate them out)

These blocks **cannot be obtained as an item in survival at all** (Silk Touch does not
help). Under strict mode (`unknownBlocksPrintable = false`, the default) they already fall
through to NOT_PRINTABLE — which is the **correct** behavior. **Recommendation: leave them
unpriced so strict mode blocks them, and explicitly blacklist them so permissive-mode packs
can't accidentally enable them either.** Printing these would let players fabricate blocks
that are otherwise impossible to place in survival.

| Block | Why unobtainable | Action |
|---|---|---|
| **Reinforced deepslate** | drops nothing, not craftable, Silk Touch fails | **UNPRINTABLE** (hard) |
| **Budding amethyst** | drops **nothing** when broken (even Silk Touch); only plain amethyst block obtainable | **UNPRINTABLE** |
| **Spawner (monster spawner)** | Silk Touch does **not** work; drops only XP | **UNPRINTABLE** |
| **Infested blocks** (silverfish) | Silk Touch drops the **normal** variant, never the infested item | **UNPRINTABLE** |
| **Bedrock** | indestructible in survival | **UNPRINTABLE** |
| **Barrier** | not even in creative inventory by default; `/give` only | **UNPRINTABLE** |
| **Command / structure / jigsaw block** | op/command only | **UNPRINTABLE** |
| **Light block** | creative/`/give` only, can't be mined | **UNPRINTABLE** |
| **End portal frame** | like bedrock — unobtainable even w/ Silk Touch | **UNPRINTABLE** |
| **Structure void** | map-maker block, creative only | **UNPRINTABLE** |
| **Petrified oak slab** | legacy, not craftable, commands only | **UNPRINTABLE** |
| **Frosted ice** | no item form in Java at all | **UNPRINTABLE** |
| **Nether portal / end portal / end gateway** (block forms) | tile blocks, no item form | **UNPRINTABLE** |
| **Chorus plant (stem block)** | breaking only drops chorus fruit, never the block item | **UNPRINTABLE** (the *flower* IS obtainable — keep flower printable at T6) |
| **Player head** | command/creative only in survival | **UNPRINTABLE** |
| **Dragon egg** _(special case)_ | obtainable but **exactly 1 per world, never renewable** | **strongly consider UNPRINTABLE** despite being a real item — see T7 |

**Edge cases that are NOT hard-unobtainable (you get a substitute):** farmland and dirt
path break into **dirt** (no distinct item, but no material lost) — treat as **T1 dirt**,
not unprintable.

---

## Cross-cutting findings & corrections to bake into the rebalance

These corrected several common assumptions during research — apply them when setting tiers:

1. **Glowstone dust is NOT a piglin-barter drop in 1.20.1** (removed in 1.16.2). Its
   renewable path is **witch farms**. Don't tier it as "easily barterable."
2. **Sponge is NOT renewable** — elder guardians don't respawn and regular guardians don't
   drop it. Treat as finite (T5–T8), not as cheap ocean junk.
3. **Saddle and Name Tag are NOT craftable in Java 1.20.1.** Saddle's renewable source is
   the **leatherworker villager trade** (6 emeralds); name tag is **fishing + wandering
   trader**. Both renewable but trade/fish-gated → ~T4–T5. (Crafting recipes you may
   remember are Bedrock-only.)
4. **Enchanted Golden Apple is finite in 1.20.1** — the renewable "ominous vault" path is a
   **1.21** feature. In 1.20.1 it is **chest-only → T8**.
5. **Trident's only 1.20.1 source is natural drowned** (Trial Chambers are 1.21). Zombie-
   converted drowned never carry tridents → genuinely grindy renewable.
6. **Shulkers self-duplicate** → shulker shells are **renewable** (resolves the "are shells
   finite?" question). Still T6 for the End gate + grind.
7. **Dragon egg is exactly 1 per world, never renewable** (Bedrock's 2nd-kill egg does NOT
   apply to Java). This is the single strongest UNPRINTABLE candidate among "real" items.
8. **Soul sand is barterable** (~4.26%) — this is *why* the **nether star** and **wither
   rose** count as renewable; keep the nether star at T7 anyway (the wither-skull + boss
   setup is the real gate).
9. **The 12 creeper-pool music discs are renewable**; only Pigstep, 5, otherside, and Relic
   are finite. Tier the creeper discs (T6–T7) below the chest discs (T8).
10. **Wither rose** (not in current config) — obtained **only when the Wither kills a mob**;
    renewable via a caged-Wither farm but boss-setup-gated → **T7**.

---

## Recommended explicit-entry / blacklist actions for `FuValueRegistry`

The derivation engine handles storage blocks and crafted items well (it flows tier up from
constituents). The items that need **explicit, deliberate** handling — because they have no
recipe to derive from, or because derivation would underprice them — are:

**Add explicit high-FU entries (no recipe to derive → would be NOT_PRINTABLE otherwise, but
make the intent explicit if you want them printable at all):**
- Totem of Undying (T4–T5), shulker shell (T6), trident (T6), nautilus shell (T5),
  heart of the sea (T8), echo shard (T8), nether star is already explicit (T7), dragon egg
  already explicit (T7 — consider raising/blacklisting).

**Strong UNPRINTABLE / winder-blacklist candidates (one-per-world or finite trophies):**
- **Dragon egg** (1/world), **elytra** (finite, no craft), **enchanted golden apple**
  (finite, no craft), **echo shard / swift sneak book / disc 5 / pigstep** (chest-only),
  **heart of the sea** (chest-only), **wither skeleton skull** (gates the nether star),
  **mob heads** (charged-creeper trophies).

**Leave to strict-mode NOT_PRINTABLE (and optionally hard-blacklist):**
- All UNPRINTABLE-section blocks (reinforced deepslate, budding amethyst, spawner, bedrock,
  barrier, end portal frame, light/command/structure blocks, player head, etc.).

---

_Sources: minecraft.wiki pages for each item (Blaze, Ghast Tear, Wither Skeleton Skull,
Nether Star, Ancient Debris, Glowstone, Nether Quartz, Bartering, Ender Pearl, Chorus
Fruit, Shulker, Elytra, Dragon Egg, End Stone, Prismarine Shard, Sea Lantern, Sponge, Heart
of the Sea, Nautilus Shell, Trident, Conduit, Echo Shard, Sculk family, Reinforced
Deepslate, Music Disc, Swift Sneak, Warden, Totem of Undying, Enchanted Golden Apple, Name
Tag, Saddle, Mob head, Spawner, Budding Amethyst, Barrier, Light Block, End Portal Frame),
verified for Java Edition 1.20.1 on 2026-06-13. Tier anchors cross-checked against
`src/main/java/com/pgmacdesign/mc3dprint/fu/FuValueRegistry.java` and
`docs/FU-VALUES-AND-COMPAT.md`._
