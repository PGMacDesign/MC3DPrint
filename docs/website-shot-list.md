# MC3DPrint Website — Photo & GIF Shot List

Companion to [website-plan.md](website-plan.md). This is the capture checklist. Nothing here
blocks the build (every slot has a generated fallback) — but real shots make it look great.

## How to use

- **Where they go:** drop files into `site/public/<folder>/` as noted per section. The site
  picks them up automatically on the next build (no code change to add a photo).
- **Per-build photos** are named by **build id**: `site/public/builds/<id>.png`. The id is the
  blueprint filename without `.blueprint` (listed below). Match it exactly.
- **Framing tips:** consistent angle (a clean 3/4 / isometric-ish view works best for the gallery),
  good light, minimal clutter behind the build. PNG. A roughly square-ish crop reads best on cards.
- **GIFs:** `-framerate 0.5`–`1.0` (≈1–2s/frame) for smooth playback; capture the *whole* flow
  (8–15 frames), not just the end state. Include a context frame (plan/account) where it matters.

---

## Priority 1 — Hero & gameplay  → `site/public/media/`

The single highest-impact asset is the hero loop. Get this and the homepage transforms.

- [ ] **`hero-scan-print.gif`** — the money shot: scan a build → load the disc → print it
  reconstructing block-by-block. The core loop in one autoplaying clip.
- [ ] **`hero-still.png`** — one striking gameplay still (a mid-print or a finished impressive build) as the GIF's poster/fallback.
- [ ] **`action-scan.png`** — using the Structure Scanner on a build.
- [ ] **`action-printing.png`** — a printer/fabricator mid-print (layers materializing).
- [ ] **`action-fabricator.png`** — a formed multiblock fabricator printing something large.

## Priority 2 — GUI shots  → `site/public/gui/`

Real in-game GUIs for the Guide + feature sections (the generated textures are the fallback).

- [ ] **`gui-printer.png`** — a printer GUI open (disc + spool loaded, upgrades in slots, mid-print).
- [ ] **`gui-winder.png`** — the Filament Winder (Throughput Panel) running.
- [ ] **`gui-repository.png`** — the Blueprint Repository browser (a few catalogued builds, the STL→GCODE button).
- [ ] **`gui-rack.png`** — the Filament Rack with spools docked.
- [ ] **`gui-upgrades.png`** — upgrade modules in a printer's slots (show Speed/Efficiency/etc.).
- [ ] **`gui-resin.png`** — a resin in the printer's resin slot.
- [ ] **`item-discs.png`** — a blank + a written Blueprint Disc in hand / inventory.
- [ ] **`item-spools.png`** — a few filament spools (different tiers — shows the tier colors).

## Priority 3 — Install  → `site/public/media/`

For the Get Started page.

- [ ] **`install-mods-folder.png`** — the jar in the `mods/` folder.
- [ ] **`install-mod-list.png`** — the in-game Mods list showing MC3DPrint.

---

## Priority 4 — Per-build photos (134)  → `site/public/builds/<id>.png`

Each gallery build. Optional/incremental — any you skip fall back to the 3D render. Grouped by
printer tier (T3–T7).

### Tier 3 — 4 builds

- [ ] **Garden Shed** — `builds/garden_shed.png`  _(3×4×3, 34 blocks)_
- [ ] **Obelisk** — `builds/obelisk.png`  _(3×16×3, 124 blocks)_
- [ ] **Ruin Pillar** — `builds/ruin_pillar.png`  _(3×8×3, 20 blocks)_
- [ ] **Scarecrow** — `builds/scarecrow.png`  _(3×6×1, 11 blocks)_

### Tier 4 — 17 builds

- [ ] **Beacon Spire** — `builds/beacon_spire.png`  _(5×12×5, 224 blocks)_
- [ ] **Campsite** — `builds/campfire_site.png`  _(5×3×5, 36 blocks)_
- [ ] **Cemetery Plot** — `builds/cemetery_plot.png`  _(5×4×5, 55 blocks)_
- [ ] **Food Stall** — `builds/food_stall.png`  _(5×5×5, 75 blocks)_
- [ ] **Garden Archway** — `builds/garden_archway.png`  _(5×5×3, 30 blocks)_
- [ ] **Guard Tower** — `builds/guard_tower.png`  _(5×13×5, 171 blocks)_
- [ ] **Market Stall** — `builds/market_stall.png`  _(5×5×4, 51 blocks)_
- [ ] **Mineshaft Entrance** — `builds/mineshaft_entrance.png`  _(5×5×5, 70 blocks)_
- [ ] **Park Bench & Lamppost** — `builds/park_bench_lamppost.png`  _(5×6×3, 32 blocks)_
- [ ] **Portcullis Gate** — `builds/portcullis_gate.png`  _(5×7×3, 79 blocks)_
- [ ] **Road / Path Segment** — `builds/road_path_segment.png`  _(5×4×5, 37 blocks)_
- [ ] **Small Cottage** — `builds/small_cottage.png`  _(5×7×5, 110 blocks)_
- [ ] **Statue Pedestal** — `builds/statue_pedestal.png`  _(5×11×5, 63 blocks)_
- [ ] **Tiered Fountain** — `builds/tiered_fountain.png`  _(5×5×5, 47 blocks)_
- [ ] **Village Well** — `builds/well.png`  _(5×6×5, 80 blocks)_
- [ ] **Watchtower** — `builds/watchtower.png`  _(5×9×5, 167 blocks)_
- [ ] **Wishing Well** — `builds/wishing_well.png`  _(5×7×5, 85 blocks)_

### Tier 5 — 75 builds

- [ ] **Animal Pen** — `builds/animal_pen.png`  _(9×5×9, 145 blocks)_
- [ ] **Apothecary Shop** — `builds/apothecary_shop.png`  _(7×9×9, 274 blocks)_
- [ ] **Aqueduct Segment** — `builds/aqueduct_segment.png`  _(7×9×9, 294 blocks)_
- [ ] **Auto Sugar Cane Farm** — `builds/sugarcane_farm_auto.png`  _(9×6×9, 274 blocks)_
- [ ] **Badlands Mesa Dwelling** — `builds/badlands_mesa_dwelling.png`  _(9×9×7, 320 blocks)_
- [ ] **Bakery** — `builds/bakery.png`  _(7×7×6, 173 blocks)_
- [ ] **Bamboo Farm** — `builds/bamboo_farm.png`  _(9×6×9, 274 blocks)_
- [ ] **Barn** — `builds/barn.png`  _(9×7×7, 225 blocks)_
- [ ] **Basalt Pillar Cluster** — `builds/basalt_pillar_cluster.png`  _(9×14×9, 180 blocks)_
- [ ] **Bee Apiary** — `builds/bee_apiary.png`  _(9×9×9, 192 blocks)_
- [ ] **Blacksmith** — `builds/blacksmith.png`  _(7×6×6, 168 blocks)_
- [ ] **Brewing Room** — `builds/brewing_room.png`  _(7×5×7, 198 blocks)_
- [ ] **Cactus Farm** — `builds/cactus_farm.png`  _(7×4×7, 104 blocks)_
- [ ] **Cherry Blossom Pavilion** — `builds/cherry_blossom_pavilion.png`  _(9×11×9, 310 blocks)_
- [ ] **Cherry Grove Cottage** — `builds/cherry_grove_cottage.png`  _(7×8×7, 181 blocks)_
- [ ] **Chorus Garden** — `builds/chorus_garden.png`  _(9×11×9, 137 blocks)_
- [ ] **Copper Clocktower** — `builds/copper_clocktower.png`  _(9×26×9, 639 blocks)_
- [ ] **Coral Garden** — `builds/coral_garden.png`  _(9×8×9, 648 blocks)_
- [ ] **Crimson & Warped Hut** — `builds/crimson_warped_hut.png`  _(7×9×7, 218 blocks)_
- [ ] **Desert Sandstone House** — `builds/desert_sandstone_house.png`  _(7×7×7, 217 blocks)_
- [ ] **Diamond Vault** — `builds/diamond_vault.png`  _(9×8×9, 571 blocks)_
- [ ] **Drawbridge** — `builds/drawbridge.png`  _(7×6×5, 117 blocks)_
- [ ] **Enchanting Room** — `builds/enchanting_room.png`  _(7×5×7, 200 blocks)_
- [ ] **End Stone Outpost** — `builds/end_stone_outpost.png`  _(9×8×9, 260 blocks)_
- [ ] **Fantasy Wizard Tower** — `builds/fantasy_wizard_tower.png`  _(9×27×9, 702 blocks)_
- [ ] **Fishery Pond** — `builds/fishery_pond.png`  _(7×8×7, 181 blocks)_
- [ ] **Fishing Hut** — `builds/fishing_hut.png`  _(7×10×7, 267 blocks)_
- [ ] **Flower Shop** — `builds/flower_shop.png`  _(7×6×7, 189 blocks)_
- [ ] **Gatehouse** — `builds/gatehouse.png`  _(9×11×7, 382 blocks)_
- [ ] **Gazebo** — `builds/gazebo.png`  _(7×8×7, 187 blocks)_
- [ ] **Greenhouse** — `builds/greenhouse.png`  _(9×9×9, 364 blocks)_
- [ ] **Hedge Maze Segment** — `builds/hedge_maze_segment.png`  _(9×4×9, 204 blocks)_
- [ ] **Hot Air Balloon** — `builds/hot_air_balloon.png`  _(9×13×9, 169 blocks)_
- [ ] **Iron Foundry** — `builds/iron_foundry.png`  _(9×9×9, 386 blocks)_
- [ ] **Japanese Tea House** — `builds/japanese_tea_house.png`  _(9×16×9, 380 blocks)_
- [ ] **Jungle Hut** — `builds/jungle_hut.png`  _(7×8×7, 194 blocks)_
- [ ] **Jungle Temple Ruin** — `builds/jungle_temple_ruin.png`  _(9×8×9, 315 blocks)_
- [ ] **Kelp Farm** — `builds/kelp_farm.png`  _(9×9×9, 379 blocks)_
- [ ] **Koi Pond** — `builds/koi_pond.png`  _(7×3×7, 73 blocks)_
- [ ] **Lighthouse** — `builds/lighthouse.png`  _(9×16×9, 403 blocks)_
- [ ] **Mangrove Stilt Hut** — `builds/mangrove_stilt_hut.png`  _(7×11×7, 262 blocks)_
- [ ] **Map Room** — `builds/map_room.png`  _(7×6×7, 221 blocks)_
- [ ] **Mob XP Tower** — `builds/mob_xp_tower.png`  _(9×24×9, 792 blocks)_
- [ ] **Modern Pool Deck** — `builds/modern_pool_deck.png`  _(9×4×9, 202 blocks)_
- [ ] **Mushroom Grow Chamber** — `builds/mushroom_farm.png`  _(9×5×9, 283 blocks)_
- [ ] **Mushroom Island Hut** — `builds/mushroom_island_hut.png`  _(7×11×7, 222 blocks)_
- [ ] **Nether Hub Room** — `builds/nether_hub_room.png`  _(9×9×9, 217 blocks)_
- [ ] **Nether Portal Room** — `builds/nether_portal_room.png`  _(9×9×9, 278 blocks)_
- [ ] **Nether Wart Farm** — `builds/nether_wart_farm.png`  _(9×5×7, 178 blocks)_
- [ ] **Ocean Ruins** — `builds/ocean_ruins.png`  _(9×6×9, 194 blocks)_
- [ ] **Pergola Garden** — `builds/pergola_garden.png`  _(7×6×7, 115 blocks)_
- [ ] **Plains House** — `builds/plains_house.png`  _(7×8×7, 195 blocks)_
- [ ] **Purpur Tower** — `builds/purpur_tower.png`  _(9×24×9, 675 blocks)_
- [ ] **Redstone Workshop** — `builds/redstone_workshop.png`  _(9×7×9, 309 blocks)_
- [ ] **Savanna Acacia Villa** — `builds/savanna_acacia_villa.png`  _(9×10×9, 274 blocks)_
- [ ] **Shulker Box Vault** — `builds/shulker_box_vault.png`  _(7×6×7, 248 blocks)_
- [ ] **Smithy Workshop** — `builds/smithy_workshop.png`  _(7×6×9, 275 blocks)_
- [ ] **Snowy Alpine Chalet** — `builds/snowy_alpine_chalet.png`  _(9×8×7, 242 blocks)_
- [ ] **Soul Outpost** — `builds/soul_outpost.png`  _(7×8×9, 221 blocks)_
- [ ] **Stone Bridge** — `builds/stone_bridge.png`  _(5×5×9, 100 blocks)_
- [ ] **Stonehenge Ring** — `builds/stonehenge_ring.png`  _(9×7×9, 81 blocks)_
- [ ] **Storage Barrel Hall** — `builds/storage_barrel_hall.png`  _(9×5×9, 324 blocks)_
- [ ] **Storybook Cottage** — `builds/storybook_cottage.png`  _(7×8×7, 203 blocks)_
- [ ] **Super Smelter** — `builds/super_smelter.png`  _(7×5×7, 181 blocks)_
- [ ] **Taiga Log Cabin** — `builds/taiga_log_cabin.png`  _(7×9×7, 196 blocks)_
- [ ] **Taiga Spruce Longhouse** — `builds/taiga_spruce_longhouse.png`  _(9×8×7, 239 blocks)_
- [ ] **Torii Gate** — `builds/torii_gate.png`  _(7×10×3, 59 blocks)_
- [ ] **Tree Farm** — `builds/tree_farm.png`  _(7×3×7, 86 blocks)_
- [ ] **Treehouse** — `builds/treehouse.png`  _(9×14×9, 370 blocks)_
- [ ] **Underwater Conduit Shrine** — `builds/underwater_conduit_shrine.png`  _(7×7×7, 130 blocks)_
- [ ] **Villager Trading Hall** — `builds/villager_trading_hall.png`  _(9×5×9, 305 blocks)_
- [ ] **Wall Battlement Segment** — `builds/wall_battlement_segment.png`  _(9×5×3, 122 blocks)_
- [ ] **Wheat Farm** — `builds/small_farm.png`  _(7×3×7, 102 blocks)_
- [ ] **Windmill** — `builds/windmill.png`  _(7×9×7, 157 blocks)_
- [ ] **Zen Garden** — `builds/zen_garden.png`  _(9×5×9, 129 blocks)_

### Tier 6 — 32 builds

- [ ] **Aquarium** — `builds/aquarium.png`  _(11×8×9, 524 blocks)_
- [ ] **Blackstone Bastion Fragment** — `builds/blackstone_bastion_fragment.png`  _(11×11×11, 357 blocks)_
- [ ] **Copper Observatory** — `builds/copper_observatory.png`  _(11×13×11, 506 blocks)_
- [ ] **Desert Pyramid Shrine** — `builds/desert_pyramid_shrine.png`  _(15×14×15, 1283 blocks)_
- [ ] **Dock Pier** — `builds/dock_pier.png`  _(11×6×7, 207 blocks)_
- [ ] **Dwarven Hall** — `builds/dwarven_hall.png`  _(13×14×11, 1023 blocks)_
- [ ] **Elven Treehouse** — `builds/elven_treehouse.png`  _(11×16×11, 472 blocks)_
- [ ] **Emerald Market Hall** — `builds/emerald_market_hall.png`  _(13×10×13, 541 blocks)_
- [ ] **End Gateway Shrine** — `builds/end_gateway_shrine.png`  _(11×7×11, 257 blocks)_
- [ ] **Grand Library** — `builds/library.png`  _(11×14×11, 767 blocks)_
- [ ] **Greek Quartz Temple** — `builds/greek_quartz_temple.png`  _(11×14×11, 582 blocks)_
- [ ] **Hobbit Hole** — `builds/hobbit_hole.png`  _(11×8×9, 450 blocks)_
- [ ] **Horse Stable** — `builds/stable_horse.png`  _(9×10×11, 422 blocks)_
- [ ] **Iron Farm** — `builds/iron_farm.png`  _(13×14×13, 1077 blocks)_
- [ ] **Japanese Dojo** — `builds/japanese_dojo.png`  _(11×12×9, 360 blocks)_
- [ ] **Japanese Pagoda** — `builds/japanese_pagoda.png`  _(11×33×11, 732 blocks)_
- [ ] **Manor House** — `builds/manor_house.png`  _(13×11×11, 767 blocks)_
- [ ] **Mediterranean Terracotta Villa** — `builds/mediterranean_terracotta_villa.png`  _(11×11×9, 328 blocks)_
- [ ] **Modern Concrete House** — `builds/modern_concrete_house.png`  _(11×9×9, 402 blocks)_
- [ ] **Modern Glass Villa** — `builds/modern_glass_villa.png`  _(15×12×11, 796 blocks)_
- [ ] **Nether Fortress Bridge** — `builds/nether_fortress_bridge.png`  _(5×9×13, 207 blocks)_
- [ ] **Nordic Viking Longhouse** — `builds/nordic_viking_longhouse.png`  _(11×12×15, 596 blocks)_
- [ ] **Prismarine Monument Fragment** — `builds/prismarine_monument_fragment.png`  _(13×9×13, 608 blocks)_
- [ ] **Pumpkin & Melon Farm** — `builds/pumpkin_melon_farm.png`  _(9×5×10, 278 blocks)_
- [ ] **Railway Station** — `builds/railway_station.png`  _(13×9×9, 424 blocks)_
- [ ] **Roman Bath House** — `builds/roman_bath_house.png`  _(13×8×9, 508 blocks)_
- [ ] **Sky Bridge Segment** — `builds/sky_bridge_segment.png`  _(5×6×12, 123 blocks)_
- [ ] **Snowy Igloo** — `builds/snowy_igloo.png`  _(9×5×12, 188 blocks)_
- [ ] **Tavern Inn** — `builds/tavern_inn.png`  _(11×17×13, 939 blocks)_
- [ ] **Underwater Dome Base** — `builds/underwater_dome_base.png`  _(11×9×11, 317 blocks)_
- [ ] **Victorian Townhouse** — `builds/victorian_townhouse.png`  _(11×21×9, 981 blocks)_
- [ ] **Village Church** — `builds/church.png`  _(9×12×15, 645 blocks)_

### Tier 7 — 6 builds

- [ ] **Castle Keep** — `builds/castle_keep.png`  _(21×16×21, 2024 blocks)_
- [ ] **Dragon Statue** — `builds/dragon_statue.png`  _(15×22×17, 965 blocks)_
- [ ] **Grand Cathedral** — `builds/grand_cathedral.png`  _(13×22×23, 1574 blocks)_
- [ ] **Sailing Ship** — `builds/sailing_ship.png`  _(7×18×19, 647 blocks)_
- [ ] **Tristan's Castle** — `builds/tristans_castle.png`  _(22×10×19, 1183 blocks)_
- [ ] **Tristan's Pig House** — `builds/tristans_pig_house.png`  _(24×16×16, 1452 blocks)_

---

_Total: 134 per-build photos + ~15 hero/GUI/install shots. The hero GIF is the one to prioritize._
