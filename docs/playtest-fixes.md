# Playtest Fixes — post-launch QA round (2026-06-16)

Running list of issues found during live testing. **Process:** fix one at a time → check it →
check it off here → commit + push (build files + this doc) → next. The jar is **NOT** redeployed
during active testing (user has the game open) — redeploy once the user signals they're done.
Leftover unchecked items carry over to the next session.

## Done
- [x] jungle_hut — grounded (floor y0, door sill y1), no stilt float; **grass_block made printable** (was unprintable/allowlisted, floated the footing)
- [x] cherry_blossom_pavilion — added a walk-in entrance, then a matching door on the opposite side (symmetry)
- [x] badlands_mesa_dwelling — dropped filler foundation (door sill y1), moved entrance to the open face
- [x] hot_air_balloon — removed the 2 purposeless plank base courses (basket grounded)
- [x] hobbit_hole — revamped: grounded, round walk-in door, grass berm, bed no longer in the wall
- [x] treehouse — hollowed the central trunk for interior room + moved ladder clear of the front door
- [x] snowy_igloo — closed the dome roof gap + tunnel entrance + standing headroom
- [x] desert_pyramid_shrine — grander 4-tier pyramid, facade motif, treasure chamber
- [x] taiga_spruce_longhouse / snowy_alpine_chalet / nordic_viking_longhouse — dropped redundant filler foundation course (door reachable)
- [x] jungle_temple_ruin — fixed door/glass upper-half overlap
- [x] underwater_dome_base — added a walk-in iron door, removed the unnecessary surrounding water
- [x] (infra) signText() helper + printer syncs block-entity data on print; deterministic BE serialization; double-block + foundation guardrails
- [x] storage_barrel_hall — removed entrance + center hanging lamps, tucked the other 3 into the corner nooks (walkways clear)

## Needs rework (farms)
- [x] iron_farm — **lava-cauldron kill** (water-safe, no obsidian, contained) + **2×2 drop** for 1.4-wide golems; hopper ring → chest. (lava_cauldron confirmed printable = itemless/structural.)
- [x] chicken_coop_auto — redesigned with contained **lava cauldrons** (no raw lava → no leak/obsidian); glass pen on a hopper grid, toss-in gap, accessible front chest, explanatory signs.
- [x] mob_xp_tower — clarity rework: walk-in kill chamber (door), AFK spot + iron-bar kill slot, glass viewing window, accessible chest, dry landing (removed base water), explanatory signs.

## Open (new findings, in order)
- [x] pumpkin_melon_farm — rearchitected for sideways growth: stems on hydrated farmland → fruit grows on adjacent dirt growth blocks (only valid spawn cell) → flowing-water sweep → hopper → chest (semi-auto; full-auto piston-crush can't fit one lane). Observers kept as growth indicators.
- [x] cactus_farm — rebuilt: spaced sand grow-blocks with air/water on all sides (so cactus survives), cobblestone breaker at the grow-into height, water sweep → hopper → chest, chests unblocked above. Cactus itself isn't FU-valued/structural so it can't print (like bamboo/kelp) — the player plants into the working mechanism (a sign explains).
- [ ] bamboo_farm — no bamboo planted; observers fire once on plant then never again (watching the wrong cell). Bamboo grows UP; observer must watch the growth cell so it re-fires each growth → piston breaks → water/hopper. Or simpler reliable design.
- [ ] kelp_farm — no kelp planted; a redstone piece broke off on print (redstone can't sit in/under water). Kelp grows UP underwater; redstone control must be in a DRY (glass-enclosed) chamber. Plant kelp on a solid block underwater; harvest at the top; collect.
- [ ] animal_pen — no flooring (add a floor). Feature request: place a spawn egg to add a pig/cow — mobs are ENTITIES (can't print); spawn eggs are items, not placeable blocks. Could optionally stock spawn eggs in a chest (item NBT, creative-only items) — awaiting user decision.
- [ ] tree_farm — fundamentally over-engineered/can't full-auto: trees don't regrow (sapling consumed on harvest), observers/hopper-under-dirt won't work. Per user, NOT a rabbit hole to chase — SIMPLIFY to an honest semi-auto plantation: spaced saplings on dirt with growth room + log/sapling collection (water/hoppers) + player replants. Reliable, not fake.

- [ ] apothecary_shop — a row of potted plants floats in mid-air and blocks the walkway. Put them on a shelf/counter/surface (or remove the ones blocking the path) so they're not floating and you can walk through.

- [ ] greenhouse — entering drops you into the central water channel with no way out. Fix: top the water with bottom slabs (waterlogged path) so you walk above it, OR move the door + give a dry walk-in path; may need a step/stairs up a level.
- [ ] tavern_inn — can't reach the stairs to the 2nd floor; even if reached, head hits the ceiling (need more headroom/taller 2nd floor); 2nd-floor inn rooms are sparse — furnish them.
- [ ] stable_horse (Horse Stable) — the gate + ladder don't actually lead to the 2nd floor; no way up without breaking blocks. Make the ladder/access actually reach the upper level.

## Strategy note (farms)
Regrowing crops (cactus/bamboo/kelp/cane✓/melon✓) → make them actually work. Non-regrowing (trees) → simplify to honest semi-auto. Remaining unflagged farms to proactively check: villager_trading_hall, fishery_pond, mushroom_farm.
- [x] sugarcane_farm_auto — water now adjacent to the sand at the soil's own level (y=1) so canSurvive passes (cane no longer pops on print); observers raised to the cane's 2nd block for correct harvest; snapped cane → water → hopper → chest.
- [x] super_smelter — full row of input chests, each with a down-hopper feeding its own furnace; fuel + output extended across the whole bank so all 5 furnaces run in parallel.
- [x] map_room — filled the 4 corner gaps (potted cornflowers at the entry, barrels by the cartography table).
- [x] mineshaft_entrance — was a sealed facade; opened a 2-tall walk-in tunnel mouth + rails leading in + an open dig-down shaft start at the back (surface entrance marker).
