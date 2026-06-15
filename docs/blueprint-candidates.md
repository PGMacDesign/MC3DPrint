# MC3DPrint — Curated Blueprint Candidate Bank (Research-Backed)

> **Purpose.** Data-backed candidate pool to grow the curated blueprint set from **23 → 100+**
> before launch. Sourced from a multi-agent research sweep (Planet Minecraft / GrabCraft /
> minecraft-schematics download counts, r/Minecraftbuilds + r/DetailCraft upvote signals,
> YouTube tutorial demand, Minecraft Wiki). Every candidate is **deduped against the existing
> 23** and against the other agents.
>
> **These are IDEAS, not files.** All builds are recreated *originally* in
> `src/test/.../CuratedBlueprintGenerator.java` as parametric vanilla structures. Sources are
> cited for **proportions, palette, and popularity only** — nothing is copied (public repo,
> original-content rule).

## How to read

- **Size → tier** = footprint bucket gated by the **larger** horizontal dim (W or L):
  Small `≤5×5` (T3–T4) · Medium `≤9×9` (T5) · Large `≤15×15` (T6) / `≤23×23` (T7) · XL `≤33×33` (T8).
- **Mat tier** = richest-block disc tier (T1 cobble/wood/wool/glass · T2 copper/iron/lantern ·
  T3 redstone/blackstone · T4 emerald/lapis · T5 diamond/netherite/beacon/prismarine ·
  T7 nether-star/purpur/end accents).
- **Recreatable** = parametric-code difficulty: **Easy** (grid/repetition) · **Med** (asymmetry,
  multi-piece) · **Hard** (organic curves, complex silhouette).
- **Static-printable** column (functional builds only): whether a static block-paste reproduces a
  *working* build. Redstone wiring is just blocks, so it prints working; mobs/items are NOT
  captured — the player adds villagers/animals/fuel after printing.

---

## 1. Key demand findings (why these picks)

1. **Houses dominate.** r/Minecraftbuilds flair counts (past month): House/Base **66**, Megabuild
   29, Nature/Terrain 27, Towns 25. The existing 23 are house-heavy (good) but **100%
   overworld-medieval** — no biome variety, no nether/end/ocean, no modern/Japanese.
2. **Top single-build demand gaps:** modern concrete/glass house (WiederDude modern-house tutorial
   ~9M views; tops nearly every "house ideas" list), the **Japanese set** (Torii Gate Collection
   28.4k dl, Big Torii 20.7k dl; dense pagoda/zen/tea-house tutorial ecosystem), **cherry-blossom**
   builds (post-1.20 TikTok/YT/Pinterest wave), and **per-biome starter houses** (ibxtoycat "house
   for every biome"; dedicated desert/savanna/taiga/mangrove guides).
3. **Functional builds are the on-brand goldmine.** Iron farm = #1 most-requested build on every
   platform; villager trading hall ranked #2 must-build; auto chicken coop hit 9.9k upvotes. They
   **print as static shells that actually work** — the differentiator no schematic site has.
4. **Ships** are very popular (Ethyria Ship Pack ~60k dl, Epic Ships ~48k, Flachet Galleon ~27k) but
   **Hard** to parametrize. **Statues** go viral (Alduin dragon 210k dl) but are organic/**Hard**.
5. **Tier spread is bottom-heavy** (~14 of 23 at mat T1–T2; almost nothing at T7). End (purpur →
   T7), Greek/Roman quartz + modern villa (T5–T6), and nether (T3) fill the empty upper tiers.

---

## 2. Existing 23 — theme tags & gaps

Almost all overworld/medieval/village; ~14 at mat T1–T2.

| Build | Biome/Dim | Theme | Footprint | Mat |
|---|---|---|---|---|
| garden_shed | plains | rustic utility | Small | T1 |
| campfire_site | plains | camp/decor | Small | T1 |
| well | plains | village micro | Small | T1 |
| market_stall | plains | commerce | Small | T1 |
| small_cottage | plains | cottage | Small | T1 |
| beacon_spire | overworld | endgame landmark | Med | T5 |
| watchtower | plains | defensive | Small | T1–T2 |
| plains_house | plains | village house | Med | T1 |
| wheat_farm / small_farm | plains | agriculture | Med | T1 |
| bakery | plains | commerce | Med | T1–T2 |
| blacksmith | plains | industry | Med | T2 |
| windmill | plains | agriculture | Med | T1 |
| stone_bridge | overworld | infrastructure | Med | T1 |
| barn | plains | agriculture | Med | T1 |
| iron_foundry | overworld | industrial | Med | T2 |
| redstone_workshop | overworld | tech | Med | T3 |
| diamond_vault | underground | treasure | Med | T5 |
| lighthouse | coast | landmark | Med | T1–T2 |
| manor_house | plains | estate | Large | T1–T2 |
| copper_observatory | overworld | steampunk | Med | T2 |
| emerald_market_hall | plains | commerce | Large | T4 |
| church | plains | civic | Large | T1–T2 |
| castle_keep | plains | defensive | Large | T1–T2 |

**Missing entirely:** all non-plains biomes, nether, end, ocean/underwater, modern, Japanese/Asian,
Mediterranean, Greek/Roman, fantasy; most decorative micro-builds; functional farms beyond wheat;
utility rooms; most infrastructure; T7 material tier.

---

## 3. Candidate bank (~95 new → ~118 total)

### A. Per-biome starter houses (16) — fills the whole biome gap; #1 demand category
| Name | Size→tier | Mat | Palette | Popularity | Recreatable |
|---|---|---|---|---|---|
| desert_sandstone_house | 7×7 → T5 | T1 | cut/smooth sandstone, terracotta, acacia trapdoors | per-biome guides (thespike desert) | Easy |
| desert_pyramid_shrine | 9×9 → T5 | T1 | sandstone variants, orange/blue terracotta | desert-temple recognizability | Easy |
| savanna_acacia_villa | 9×9 → T5 | T1 | acacia, cut sandstone, stone brick | savanna-house guides | Easy |
| taiga_log_cabin | 7×7 → T5 | T1 | spruce logs, stone, campfire chimney | cabin builds perennial | Easy |
| taiga_spruce_longhouse | 9×7 → T5 | T1 | spruce beams, hay, cobblestone | royalcdkeys Taiga Longhouse | Easy |
| snowy_igloo | 5×5 → T4 | T1 | snow, packed/blue ice, spruce trapdoor | igloo recognizability | Easy |
| snowy_alpine_chalet | 9×7 → T5 | T1 | spruce, snow, stone brick, glass | alpine trending 2024 | Med |
| jungle_hut | 7×7 → T5 | T1 | jungle planks/logs, leaves, bamboo, ladders | jungle/bamboo lists | Med |
| jungle_temple_ruin | 9×9 → T5 | T3 | mossy cobble, chiseled stone, vines | jungle-temple recognizability | Med |
| mangrove_stilt_hut | 7×7 → T5 | T1 | mangrove planks/roots, mud brick, frog-light | royalcdkeys Swamp Stilt | Med |
| cherry_grove_cottage ⭐ | 7×7 → T5 | T1 | cherry planks/logs, white terracotta, petals | strong post-1.20 wave | Easy |
| cherry_blossom_pavilion ⭐ | 9×9 → T5 | T1 | cherry, stripped cherry, petals, lanterns | cherry-pavilion ideas | Easy |
| badlands_mesa_dwelling | 9×7 → T5 | T1 | multi-terracotta, red sand, dark oak | royalcdkeys Badlands Cliff | Med |
| mushroom_island_hut | 7×7 → T5 | T1 | mushroom blocks/stem, mycelium, spruce | mooshroom novelty | Med |
| hobbit_hole | 9×9 → T5 | T1 | grass roof, dark-oak ring door, terracotta | every starter-base list | Med |
| treehouse | 9×9 → T5 | T1 | oak logs/leaves, trapdoors, ladders, vines | r/Minecraftbuilds 17k upvotes | Med |

### B. Architectural styles (18)
| Name | Size→tier | Mat | Palette | Popularity | Recreatable |
|---|---|---|---|---|---|
| modern_concrete_house ⭐ | 11×9 → T6 | T1 | white/gray concrete, glass panes, quartz, dark oak | top of nearly every list; WiederDude ~9M views | Med |
| modern_glass_villa | 15×11 → T6 | T5 | concrete, glass, quartz, sea lanterns | tiered villa featured widely | Hard |
| modern_pool_deck | 9×9 → T5 | T1 | quartz, smooth stone, concrete, water | modern companion | Easy |
| japanese_pagoda ⭐ | 9×9 → T5 | T1 | dark oak/spruce, red wool, lanterns | dedicated pagoda tutorials | Med |
| torii_gate ⭐ | 7×3 → T5 | T1 | dark oak, red wool/concrete, stripped logs | Torii Collection 28.4k dl, Big Torii 20.7k | Easy |
| japanese_tea_house | 7×7 → T5 | T1 | spruce, dark oak, white wool, leaves | blockyideas 17 Japanese builds | Med |
| japanese_dojo | 11×9 → T6 | T1 | dark oak, white wool, paper-glass, lanterns | Pinterest dojo boards | Med |
| zen_garden | 9×9 → T5 | T1 | gravel, sand, bamboo, stone lantern | multiple zen tutorials | Easy |
| mediterranean_terracotta_villa | 11×9 → T6 | T1 | white concrete/terracotta, orange-glazed roof | bricksblocks Med. guide | Med |
| greek_quartz_temple | 11×11 → T6 | T5 | quartz block/pillar/stairs, chiseled quartz | PMC roman-greek collection | Med |
| roman_bath_house | 13×9 → T6 | T5 | quartz, smooth stone, prismarine, water | Roman TikTok trend | Med |
| fantasy_wizard_tower | 9×9 → T5 | T4 | stone brick, deepslate, purple glass, end rods | fantasy-house notoriety | Med |
| elven_treehouse | 11×11 → T6 | T4 | birch, quartz, leaves, blue glass, sea lanterns | LOTR elven style | Hard |
| dwarven_hall | 13×11 → T6 | T2 | stone brick, deepslate, iron blocks, gold accents | LOTR/Moria style | Hard |
| cottagecore_cottage | 7×7 → T5 | T1 | oak/spruce, cobble, flowers, hay, leaves | cottagecore perennial | Easy |
| victorian_townhouse | 11×9 → T6 | T2 | brick, granite trim, bay windows, copper roof | urban street builds | Med |
| nordic_viking_longhouse | 11×15 → T7 | T3 | spruce, stone brick, hay, shields (item frames) | medieval cluster | Med |
| copper_clocktower | 9×9 → T5 | T2 | copper (all oxidation), deepslate, lanterns | pairs copper_observatory | Med |

### C. Nether (8)
| Name | Size→tier | Mat | Palette | Popularity | Recreatable |
|---|---|---|---|---|---|
| nether_portal_room | 9×9 → T5 | T3 | obsidian, crying obsidian, blackstone, gold, chains | portal-hub builds featured | Easy |
| blackstone_bastion_fragment | 11×11 → T6 | T3 | polished/gilded blackstone, basalt, chains, gold | bastion-inspired bases | Med |
| crimson_warped_hut | 7×7 → T5 | T3 | crimson/warped planks+stems, nether brick, shroomlight | nether-wood zoning guides | Easy |
| soul_outpost | 7×9 → T5 | T3 | blackstone, soul soil/sand, soul lanterns, basalt | soul-fire guides | Easy |
| nether_wart_farm | 9×7 → T5 | T3 | soul sand, nether brick, nether wart, fences | brewing staple | Easy |
| nether_fortress_bridge | 13×5 → T6 | T3 | nether brick + fence/stairs | fortress recognizability | Med |
| nether_hub_room | 9×9 → T5 | T3 | obsidian, blackstone, quartz, blue ice (4 portals, ≤4 tall) | Wiki Nether-hub; survival meta essential | Med |
| basalt_pillar_cluster | 9×9 → T5 | T3 | basalt variants, magma block | basalt-delta aesthetic | Easy |

### D. End (5) — fills the empty T7 material tier
| Name | Size→tier | Mat | Palette | Popularity | Recreatable |
|---|---|---|---|---|---|
| purpur_tower | 9×9 → T5 | T7 | purpur block/pillar/stairs, end-stone brick, end rods | End City aesthetic | Med |
| end_stone_outpost | 9×9 → T5 | T7 | end stone/brick, purpur, end rods, chorus | toxigon End-base tips | Easy |
| chorus_garden | 9×9 → T5 | T7 | end stone, chorus plant/flower, purpur, end rods | End decor | Easy |
| end_gateway_shrine | 11×11 → T6 | T7 | end-stone brick, purpur, obsidian, end rods | End-portal-hub popularity | Med |
| shulker_box_vault | 7×7 → T5 | T7 | purpur, end-stone brick, shulker boxes, iron bars | End storage builds | Easy |

### E. Ocean / water (9)
| Name | Size→tier | Mat | Palette | Popularity | Recreatable |
|---|---|---|---|---|---|
| prismarine_monument_fragment | 13×13 → T6 | T5 | prismarine variants, sea lanterns | monument recognizability | Med |
| conduit_shrine | 7×7 → T5 | T5 | prismarine, sea lanterns, conduit | 4netplayers conduit guide | Easy |
| dock_pier | 11×7 → T6 | T1 | spruce planks/logs, fences, barrels, lanterns | coastal staple; multiple guides | Easy |
| fishing_hut | 7×7 → T5 | T1 | spruce, oak, barrels, lanterns, trapdoors | cottagecore/lakeside trend | Easy |
| sailing_ship | 13×5 → T6 | T1 | oak/spruce planks, wool sails, ladders | ship packs 48–60k dl | Hard |
| underwater_dome_base | 11×11 → T6 | T5 | glass, white concrete, prismarine, sea lanterns | thegamer underwater bases | Med |
| ocean_ruins | 9×9 → T5 | T1 | cracked/mossy stone brick, gravel, sand | ocean-ruins recognizability | Easy |
| coral_garden | 9×9 → T5 | T1 | coral blocks/fans, sea pickles, sand, kelp | "easiest path to color" | Easy |
| aquarium | 11×9 → T6 | T2 | glass, chiseled quartz, coral, kelp, sea lanterns | WhatIfGaming display build | Med |

### F. Functional farms (14) — print working shells (player adds mobs/items)
| Name | Size→tier | Mat | Palette | Demand | Static-printable |
|---|---|---|---|---|---|
| iron_farm ⭐ | ~15×15 → T7 | T1 | beds, hoppers, lava, glass, stone | **#1 most-requested build, every platform** | Yes (add villagers) |
| mob_xp_tower | 9×9 spawn → T5 | T1 | dark blocks, water channels, hoppers | very high; "how do I build a mob farm" | Yes (spawns on print) |
| sugarcane_farm_auto | 9×9 → T5 | T3 | sugar cane, observers, pistons, hoppers | Tier-1 must-build | Yes (works) |
| pumpkin_melon_farm | 9×9 → T5 | T3 | farmland, observers, pistons, hoppers | high; villager-trade staple | Yes (works) |
| cactus_farm | 7×7 → T5 | T1 | sand, cactus, fence posts, hoppers | Tier-1 easy farm | Yes (works) |
| bamboo_farm | 7×7 → T5 | T3 | bamboo, mud, observers, pistons, hoppers | Tier-1 post-1.20 | Yes (works) |
| kelp_farm | 9×9 → T5 | T1 | kelp, water, observers, pistons, furnaces | high (food/fuel/XP) | Yes (works) |
| villager_trading_hall ⭐ | 9×9 → T5 | T1 | beds, workstations, trapdoors, walls | **#2 must-build mid-game** | Yes (add villagers) |
| animal_pen | 9×9 → T5 | T1 | fences, gates, grass, troughs, lanterns | beginner essential | Yes (add animals) |
| chicken_coop_auto | 5×5 → T4 | T2 | glass, hoppers, dispensers, slab, lava | r/Minecraftbuilds 9.9k upvotes | Yes (add chickens) |
| bee_apiary | 9×9 → T5 | T1 | honeycomb, oak, flowers, fences | popular; cottagecore | Partly (add bees) |
| fishery_pond | 7×7 → T5 | T1 | spruce, barrels, water, lanterns | lakeside trend | Easy/decor |
| tree_farm | 7×7 → T5 | T1 | logs, leaves, observers, pistons | "most efficient farm" | Yes (replant) |
| mushroom_farm | 9×9 → T5 | T3 | mushroom blocks, mycelium, dispensers, hoppers | underground staple | Yes (works) |

### G. Utility rooms (7) — survival-base wishlist; functional shells
| Name | Size→tier | Mat | Palette | Demand | Static-printable |
|---|---|---|---|---|---|
| storage_barrel_hall | 9×9 → T5 | T1 | barrels, stripped logs, item frames, lanterns | r/Minecraft 1–10k upvote staple | Yes (empty) |
| enchanting_room | 5×5 → T4 | T1 | enchanting table + 15 bookshelves, lapis, lanterns | Wiki canonical; high demand | Yes (works) |
| brewing_room | 7×7 → T5 | T3 | brewing stands, cauldron, soul sand+wart, netherrack | Coohom dedicated guides | Yes (add fuel) |
| super_smelter | 7×7 → T5 | T2 | furnaces, hoppers, chests, repeaters, comparators | 10+ first-page tutorials | Yes (works) |
| smithy_workshop | 7×9 → T5 | T2 | anvil, smithing table, grindstone, lava forge | post-1.20 surge | Yes (works) |
| map_room | 7×7 → T5 | T1 | item frames (maps), dark oak, banners, carpets | Bedrock achievement drives demand | Yes (blank frames) |
| library | 11×11 → T6 | T1 | dark oak, bookshelves, chiseled bookshelves, lanterns | r/DetailCraft favorite; slblu 20k upvotes | Med |

### H. Infrastructure / civic / defensive (14)
| Name | Size→tier | Mat | Palette | Popularity | Recreatable |
|---|---|---|---|---|---|
| sky_bridge_segment | 5×11 → T6 | T1 | stone brick stairs/slabs, fences, lanterns (tileable) | most-searched build type | Easy |
| road_path_segment | 5×5 → T4 | T1 | dirt path, gravel, cobble, slabs (tileable) | minecraft.net "5 path designs" | Easy |
| aqueduct_segment | 7×9 → T5 | T1 | stone brick, waterlogged stairs, water (tileable) | Roman/medieval niche | Med |
| mineshaft_entrance | 5×5 → T4 | T1 | logs, cobble, trapdoors, rails, lantern | survival Let's Play staple | Easy |
| railway_station | 13×9 → T6 | T2 | iron, stone, canopy, clock tower, rails | royalcdkeys; minecart hub | Med |
| tavern_inn | 11×13 → T6 | T3 | spruce/dark oak, stone brick, barrels, fireplace | 300+ schematics catalogued | Med |
| apothecary_shop | 7×9 → T5 | T3 | spruce/dark oak, cauldrons, brewing stands, flower pots | PMC apothecary tag | Med |
| gatehouse | 9×7 → T5 | T1 | stone brick, iron bars, chains, lanterns | YT gatehouse guides | Med |
| wall_battlement_segment | 9×3 → T5 | T1 | stone brick, walls/stairs (tileable crenellations) | minecircles merlon guide | Easy |
| guard_tower | 5×5 → T4 | T1 | stone brick stairs, oak, lanterns, iron bars | minecircles tower guide | Med |
| drawbridge | 7×5 → T5 | T1 | spruce, chains, fences, iron bars, water | exitlag castle guide | Med |
| portcullis_gate | 5×3 → T4 | T2 | stone brick arch, iron bars, chains, dark oak | castle-gate guides | Easy |
| stable_horse | 9×11 → T6 | T2 | oak logs/planks, hay, fences, gates | PMC stable tag | Easy |
| greenhouse | 9×9 → T5 | T1 | oak/spruce frame, glass, flower pots, farmland | top greenhouse 10.5k dl | Med |

### I. Decorative micro / landmarks (18) — cheap volume, mostly Small
| Name | Size→tier | Mat | Palette | Popularity | Recreatable |
|---|---|---|---|---|---|
| tiered_fountain | 5×5 → T4 | T1 | stone brick stairs/slabs, water, glowstone | top fountain ~7.5k dl | Easy |
| koi_pond | 7×7 → T5 | T2 | stone slabs/buttons, lilypads, bamboo, water | cottagecore garden | Easy |
| gazebo | 7×7 → T5 | T1 | stone brick, oak, glass panes, lanterns | comfycurio 15 gazebo ideas; Whispering Vale 26.9k dl | Easy |
| pergola_garden | 7×7 → T5 | T1 | oak/spruce fences, leaves, flowers, path | garden-design lists | Easy |
| wishing_well | 3×3 → T3 | T1 | mossy cobble, oak fence/roof, chain, bucket, water | TikTok #wishingwell trend | Easy |
| statue_pedestal | 5×5 → T4 | T2 | stone, quartz, iron blocks (knight/villager bust) | GrabCraft statue category; 3,900-asset packs | Med |
| obelisk | 3×3 → T3 | T3 | smooth/chiseled stone, gold capstone | GrabCraft obelisk 11.6k views; monolith pack 2.5k dl | Easy |
| stonehenge_ring | 9×9 → T5 | T3 | stone/stone brick trilithons, grass | gportal "beginner-friendly" landmark | Med |
| garden_archway | 5×3 → T4 | T1 | stone brick, oak fences, leaves, flowers | sportskeeda 10 archways; Archway Pack 13.8k dl | Easy |
| ruin_pillar | 3×3 → T3 | T2 | cracked/mossy stone brick, cobble, vines | Overgrown tag (20.7k dl top) | Easy |
| cemetery_plot | 5×5 → T4 | T1 | stone/cobble headstones, podzol, dead bushes, cobwebs | chunkshift graveyard guide; TikTok | Easy |
| scarecrow | 2×2 → T3 | T1 | fence, jack-o-lantern, hay bale, trapdoor hat | MixedKreations how-to | Easy |
| flower_shop | 7×7 → T5 | T1 | oak, glass, flowers, flower pots, wool awning | cottagecore commerce | Easy |
| food_stall | 5×5 → T4 | T1 | oak/spruce, wool awning, barrels, campfire | market-set companion | Easy |
| park_bench_lamppost | 5×3 → T4 | T1–T2 | stairs/slabs (bench), stone wall, chain, lantern | GamerEmpire 16 lamp designs; viral TikTok | Easy |
| hedge_maze_segment | 9×9 → T5 | T1 | leaves, path, stone-brick edging (tileable) | castle-garden trend | Easy |
| hot_air_balloon | 9×9 → T5 | T1 | patterned wool, barrels, trapdoors, stripped logs | WhatIfGaming popular decor | Med |
| dragon_statue | 7×5 → T4 | T3 | dark prismarine, obsidian, stone brick, chains | Sportskeeda statue lists; iconic | Hard |

---

## 4. Strategic recommendations

1. **Lead with the per-biome house family (Section A).** One coherent, easy-to-recreate set that
   fills the entire biome gap *and* hits the #1 demand category (houses). ~16 builds at fairly
   uniform effort.
2. **Functional shells (F + G) are the differentiator.** "Print a working iron farm / trading hall
   / super smelter shell" is exactly the survival use-case the mod sells — and nothing on
   PMC/YouTube competes on *printability*. Prioritize alongside the houses.
3. **Use End + Greek/Roman + modern villa to fix the tier spread** (empty T7 material, thin T5–T6).
4. **Decorative micro (Section I) is cheap volume** — ~18 small T3–T4 builds to pad toward 100 fast
   without piling more onto the over-indexed T1-medieval cluster.
5. **Defer the Hard ones** (grand ships, dragon statue, elven treehouse, dwarven hall, modern glass
   villa, grand library) unless you want marquee showpieces — they cost the most hand-coding.

**Scoreboard:** 23 existing + ~95 new = **~118 candidates** → clears 100+ with margin to cut the
weakest and still hit the goal.

---

## 5. Source notes & caveats

- **Site access:** Planet Minecraft, minecraft-schematics.com, and Reddit block direct fetches
  (HTTP 403); their numbers came from Google-indexed snippets (≤7 days stale). GrabCraft individual
  pages and Minecraft Wiki fetched cleanly. YouTube per-video counts aren't exposed in search
  snippets — channel-level totals and aggregator citations were used as the demand proxy.
- **Popularity = directional, not exact.** Download/upvote figures are real but indicative; use them
  to rank priority, not as precise truth.
- **Recreatability** assumes parametric Java generation; revisit per build during implementation.
- Selected source anchors: Sportskeeda build roundups, RoyalCDKeys "50 builds 2025", WhatIfGaming
  "80+ building ideas", PCGamesN "70 house ideas", blockyideas Japanese builds, minecircles castle
  guides, comfycurio gazebos, GamerEmpire lamp posts, Minecraft Wiki (farms/rooms/nether-hub),
  PMC tag listings (torii/ship/overgrown/windmill/farmhouse/statue), GrabCraft (obelisk/statues).
