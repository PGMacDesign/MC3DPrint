# Phase 2 — Mass Production Build Tracker

> **Goal:** build the remaining curated blueprint candidates one at a time until the
> set reaches 100+. Source specs: `docs/blueprint-candidates.md` (each build's row =
> size/tier, palette, notes). This file is the **source of truth for progress** — it
> survives context compaction and lets any session resume mid-run.

## Status
- **Curated builds before Phase 2:** 29
- **Phase 2 queue:** 103 · **Done:** 11 · **Blocked:** 1 · **Remaining:** 91
- **Next up:** `hobbit_hole`
- **Last completed:** `mushroom_island_hut (BLOCKED)`

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
- [ ] hobbit_hole
- [ ] treehouse

### F — Functional farms (14) · differentiator; watch for unvalued components
- [ ] iron_farm
- [ ] mob_xp_tower
- [ ] sugarcane_farm_auto
- [ ] pumpkin_melon_farm
- [ ] cactus_farm
- [ ] bamboo_farm
- [ ] kelp_farm
- [ ] villager_trading_hall
- [ ] animal_pen
- [ ] chicken_coop_auto
- [ ] bee_apiary
- [ ] fishery_pond
- [ ] tree_farm
- [ ] mushroom_farm

### I — Decorative micro / landmarks (17) · cheap volume, easy
- [ ] koi_pond
- [ ] gazebo
- [ ] pergola_garden
- [ ] wishing_well
- [ ] statue_pedestal
- [ ] obelisk
- [ ] stonehenge_ring
- [ ] garden_archway
- [ ] ruin_pillar
- [ ] cemetery_plot
- [ ] scarecrow
- [ ] flower_shop
- [ ] food_stall
- [ ] park_bench_lamppost
- [ ] hedge_maze_segment
- [ ] hot_air_balloon
- [ ] dragon_statue (Hard)

### G — Utility rooms (6) · functional shells
- [ ] storage_barrel_hall
- [ ] brewing_room
- [ ] super_smelter
- [ ] smithy_workshop
- [ ] map_room
- [ ] library (Hard)

### H — Infrastructure / civic / defensive (13)
- [ ] sky_bridge_segment
- [ ] road_path_segment
- [ ] aqueduct_segment
- [ ] mineshaft_entrance
- [ ] railway_station
- [ ] tavern_inn
- [ ] apothecary_shop
- [ ] gatehouse
- [ ] guard_tower
- [ ] drawbridge
- [ ] portcullis_gate
- [ ] stable_horse
- [ ] greenhouse

### B — Architectural styles (17)
- [ ] modern_concrete_house
- [ ] modern_pool_deck
- [ ] cottagecore_cottage
- [ ] torii_gate
- [ ] japanese_tea_house
- [ ] zen_garden
- [ ] japanese_dojo
- [ ] mediterranean_terracotta_villa
- [ ] greek_quartz_temple
- [ ] roman_bath_house
- [ ] fantasy_wizard_tower
- [ ] victorian_townhouse
- [ ] nordic_viking_longhouse
- [ ] copper_clocktower
- [ ] modern_glass_villa (Hard)
- [ ] elven_treehouse (Hard)
- [ ] dwarven_hall (Hard)

### E — Ocean / water (9)
- [ ] dock_pier
- [ ] fishing_hut
- [ ] conduit_shrine
- [ ] ocean_ruins
- [ ] coral_garden
- [ ] prismarine_monument_fragment
- [ ] underwater_dome_base
- [ ] aquarium
- [ ] sailing_ship (Hard)

### C — Nether (8) · watch gilded_blackstone (unvalued)
- [ ] nether_portal_room
- [ ] crimson_warped_hut
- [ ] soul_outpost
- [ ] nether_wart_farm
- [ ] basalt_pillar_cluster
- [ ] nether_hub_room
- [ ] nether_fortress_bridge
- [ ] blackstone_bastion_fragment

### D — End (5) · T7 materials; watch end-block values
- [ ] purpur_tower
- [ ] end_stone_outpost
- [ ] chorus_garden
- [ ] shulker_box_vault
- [ ] end_gateway_shrine

## Blocked log
- mushroom_island_hut — red/brown_mushroom_block, mushroom_stem, mycelium are unvalued (no recipe). Value them (cheap T1 natural) to build faithfully.
