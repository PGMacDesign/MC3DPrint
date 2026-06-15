# Phase 2 — Mass Production Build Tracker

> **Goal:** build the remaining curated blueprint candidates one at a time until the
> set reaches 100+. Source specs: `docs/blueprint-candidates.md` (each build's row =
> size/tier, palette, notes). This file is the **source of truth for progress** — it
> survives context compaction and lets any session resume mid-run.

## Status
- **Curated builds before Phase 2:** 29
- **Phase 2 queue:** 103 · **Done:** 94 · **Blocked:** 2 · **Remaining:** 7
- **Next up:** `nether_fortress_bridge`
- **Last completed:** `nether_hub_room`

## Process (per build — follow exactly on resume)
1. Pick the first unchecked `[ ]` build in queue order below.
2. Spawn ONE sub-agent to implement it: use the Phase 0 helpers (`Palette`/`house()`/
   `roomShell()`/`solid()`/`pagodaRoof()` + existing roof/radial/crenellate helpers),
   follow the build's row in `docs/blueprint-candidates.md`, register it (function +
   `builds.put` + append to `CuratedBlueprints.CURATED_NAMES`), regen the `.blueprint`,
   and run `runGameTestServer` green.
3. **Guardrails the build must pass:** printability gate (only FU-valued/structural
   blocks — swap unvalued blocks for valued equivalents; if impossible → BLOCKED) and
   the render-integrity detector (no glass-pane/iron-bar without a connectable
   horizontal neighbor). Plus ASCII-dump self-review (enterable / reads correctly).
4. On green: commit (build files + this tracker) + push, mark `[x]`, update Status
   (Done/Remaining counts, Next up, Last completed).
5. If the agent can't make it valid without an economy change → mark `[BLOCKED: reason]`,
   commit the tracker note, move on. Never silently change FU values.
6. **At each category boundary:** rebuild + redeploy the jar to the Prism instance
   (`.../PrismLauncher/instances/1.20.1/minecraft/mods/`, replace, never duplicate) so a
   current testable build always exists. (No config-delete needed unless FU changed.)

## Queue (in build order)

### A — Per-biome houses (14) · high value, safe, share `house()`
- [x] desert_sandstone_house
- [x] desert_pyramid_shrine
- [x] taiga_log_cabin
- [x] taiga_spruce_longhouse
- [x] snowy_igloo
- [x] snowy_alpine_chalet
- [x] jungle_hut
- [x] jungle_temple_ruin
- [x] mangrove_stilt_hut
- [x] cherry_blossom_pavilion
- [x] badlands_mesa_dwelling
- [BLOCKED: needs mushroom blocks + mycelium valued (no recipe; core to build)] mushroom_island_hut
- [x] hobbit_hole
- [x] treehouse

### F — Functional farms (14) · differentiator; watch for unvalued components
- [x] iron_farm
- [x] mob_xp_tower
- [x] sugarcane_farm_auto
- [x] pumpkin_melon_farm
- [x] cactus_farm
- [x] bamboo_farm
- [x] kelp_farm
- [x] villager_trading_hall
- [x] animal_pen
- [x] chicken_coop_auto
- [BLOCKED: needs beehive/honeycomb_block valued (no recipe; core to build)] bee_apiary
- [x] fishery_pond
- [x] tree_farm
- [x] mushroom_farm

### I — Decorative micro / landmarks (17) · cheap volume, easy
- [x] koi_pond
- [x] gazebo
- [x] pergola_garden
- [x] wishing_well
- [x] statue_pedestal
- [x] obelisk
- [x] stonehenge_ring
- [x] garden_archway
- [x] ruin_pillar
- [x] cemetery_plot
- [x] scarecrow
- [x] flower_shop
- [x] food_stall
- [x] park_bench_lamppost
- [x] hedge_maze_segment
- [x] hot_air_balloon
- [x] dragon_statue (Hard)

### G — Utility rooms (6) · functional shells
- [x] storage_barrel_hall
- [x] brewing_room
- [x] super_smelter
- [x] smithy_workshop
- [x] map_room
- [x] library (Hard)

### H — Infrastructure / civic / defensive (13)
- [x] sky_bridge_segment
- [x] road_path_segment
- [x] aqueduct_segment
- [x] mineshaft_entrance
- [x] railway_station
- [x] tavern_inn
- [x] apothecary_shop
- [x] gatehouse
- [x] guard_tower
- [x] drawbridge
- [x] portcullis_gate
- [x] stable_horse
- [x] greenhouse

### B — Architectural styles (17)
- [x] modern_concrete_house
- [x] modern_pool_deck
- [x] cottagecore_cottage
- [x] torii_gate
- [x] japanese_tea_house
- [x] zen_garden
- [x] japanese_dojo
- [x] mediterranean_terracotta_villa
- [x] greek_quartz_temple
- [x] roman_bath_house
- [x] fantasy_wizard_tower
- [x] victorian_townhouse
- [x] nordic_viking_longhouse
- [x] copper_clocktower
- [x] modern_glass_villa (Hard)
- [x] elven_treehouse (Hard)
- [x] dwarven_hall (Hard)

### E — Ocean / water (9)
- [x] dock_pier
- [x] fishing_hut
- [x] conduit_shrine (conduit block unvalued/loot-derived → left empty sea-lantern mount for player)
- [x] ocean_ruins
- [x] coral_garden (coral/kelp/sea_pickle unvalued → built as valued bright-concrete reef per "easiest path to color" intent)
- [x] prismarine_monument_fragment
- [x] underwater_dome_base
- [x] aquarium (coral/kelp unvalued → valued concrete reef inside the glass water tank)
- [x] sailing_ship (Hard)

### C — Nether (8) · watch gilded_blackstone (unvalued)
- [x] nether_portal_room (portal frame only; player lights it)
- [x] crimson_warped_hut (nylium/wart-block unvalued → two-tone via crimson/warped planks+stems)
- [x] soul_outpost (soul_lantern + soul_campfire both print — valued via derivation)
- [x] nether_wart_farm (nether_wart prints as structural crop on soul_sand)
- [x] basalt_pillar_cluster
- [x] nether_hub_room (blue_ice prints; 4 unlit obsidian portal frames)
- [ ] nether_fortress_bridge
- [ ] blackstone_bastion_fragment

### D — End (5) · T7 materials; watch end-block values
- [ ] purpur_tower
- [ ] end_stone_outpost
- [ ] chorus_garden
- [ ] shulker_box_vault
- [ ] end_gateway_shrine

## Notes — unvalued blocks found during the run (for a morning economy decision)
These natural/decorative blocks have **no FU value and no recipe**, so builds can't use them
(they silently won't print). Agents swapped them where possible; where one is a build's core
identity it was BLOCKED. **Valuing them as cheap T1 naturals would unblock those builds and
enrich nature/garden ones.** Your call (economy frozen until you approve):
- **leaves** (all), **vines**, **bamboo** — canopies, hedges, pergolas, jungle/overgrown looks
- **red/brown_mushroom_block, mushroom_stem, mycelium** — blocks mushroom_island_hut
- **mangrove_roots, muddy_mangrove_roots** — swamp texture (CORRECTION: mud / mud_bricks / packed_mud ARE valued — usable)
- **\*_froglight** (ochre/verdant/pearlescent) — swamp/decor glow
- **beehive, bee_nest, honeycomb_block, flowers** — block bee_apiary (honeycomb is shear/loot-only so honeycomb_block + beehive can't derive; bee_nest is worldgen-only). Apiary identity = hives + honeycomb + flowers, so no faithful build without them.
- **pink_petals** — cherry-blossom ground (swapped to pink_carpet)
- **grass_block, dirt, podzol, coarse_dirt** — earthen roofs/ground (grass_block is allowlisted but still won't print)
- already-known loot-only: **gilded_blackstone, bell, reinforced_deepslate**

## Blocked log
- mushroom_island_hut — red/brown_mushroom_block, mushroom_stem, mycelium are unvalued (no recipe). Value them (cheap T1 natural) to build faithfully.
- bee_apiary — beehive, bee_nest, honeycomb_block are unvalued and underivable (honeycomb is shear/loot-only → no leaf to derive from; bee_nest is worldgen-only). These ARE the apiary's identity, so an empty wooden frame isn't an apiary. Value honeycomb (leaf) → honeycomb_block/beehive derive; bee_nest still needs a direct value.
