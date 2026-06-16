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
- [x] bamboo_farm — rebuilt mirroring the fixed sugarcane farm: observer raised to the growth cell (re-fires each growth, not just once on plant) → piston break → water catch-column → hopper → chest; player plants bamboo on the soil cells (bamboo can't print, like cactus/kelp). A sign explains.
- [x] kelp_farm — redstone now rides a dedicated DRY stone shelf one column out (no longer on top of the observer/piston, no longer touching the water column → no wash-off on print); observer raised to the growth cell (y=2, 2nd kelp segment) so it re-fires each growth and breaks low (base regrows); piston snaps the cut stalk into the central water canal → hopper → chest; player plants kelp on the submerged stone grow-blocks (kelp can't print). Signs explain.
- [x] animal_pen — now prints a full grass_block floor at y=0 (grass_block became FU-valued this session, so the old "no floor/player terrain" rationale is obsolete); trough water + shelter plank floor preserved via overwrite ordering. **Spawn-egg feature DEFERRED** — left out intentionally; it's a balance call (spawn eggs are creative-only items). Awaiting Patrick's decision on whether to stock a couple in a chest (item NBT).
- [x] tree_farm — tore out the fake observer/piston/dispenser auto-harvester (+ pointless hopper-under-dirt) and rebuilt as an HONEST semi-auto plantation (7×7×3): grass floor, oak-fence ring + gate, 6 oak saplings on true 2-block spacing with OPEN AIR above (trees grow into open world), chest+2-hopper collection sump by the gate, corner lanterns for light, sign explaining "plant spaced / chop & REPLANT / store". No fake auto, nothing caps the saplings.

- [x] apothecary_shop — removed the floating head-height herb row (potted_* + hanging lantern at y=2 across z=4 that blocked the aisle); relocated the 5 bloom types onto surfaces (bookshelf tops, barrel tops, counter); central aisle (x=2..4) now clear at y=1 and y=2 front-to-back; the one hanging lantern moved off the aisle against the west wall.

- [x] greenhouse — central aisle water channel (x=4, y=0) that the door dumped you into is now covered with flush waterlogged TOP slabs (walk above the water, dry feet, flush with the floor — no entry pit, walk in/out freely); each planter bed got its own y=1 water source so crops stay irrigated. No stairs/2nd level needed (single-storey).
- [x] tavern_inn — rebuilt the interior stair: moved it one bay east of the chimney (x=2), flipped it to `facing=south` so walking north actually ascends (it was facing=north = a riser-wall you'd jump/headbonk — the root cause), and extended it to a 6-step run (y=1..6) so the top step sits flush with the y=6 deck (no 1-block hop). Hatch re-derived (open at (2,6,6)/(2,6,7)) for full headroom; clean landing into the north room. Furnished both guest rooms (carpet rugs, barrel/chest nightstands w/ potted blooms, extra lanterns, bookshelves); both beds intact. Verified climb layer-by-layer in the dump.
- [ ] stable_horse (Horse Stable) — the gate + ladder don't actually lead to the 2nd floor; no way up without breaking blocks. Make the ladder/access actually reach the upper level.

## Strategy note (farms)
Regrowing crops (cactus/bamboo/kelp/cane✓/melon✓) → make them actually work. Non-regrowing (trees) → simplify to honest semi-auto. Remaining unflagged farms to proactively check: villager_trading_hall, fishery_pond, mushroom_farm.
- [x] sugarcane_farm_auto — water now adjacent to the sand at the soil's own level (y=1) so canSurvive passes (cane no longer pops on print); observers raised to the cane's 2nd block for correct harvest; snapped cane → water → hopper → chest.
- [x] super_smelter — full row of input chests, each with a down-hopper feeding its own furnace; fuel + output extended across the whole bank so all 5 furnaces run in parallel.
- [x] map_room — filled the 4 corner gaps (potted cornflowers at the entry, barrels by the cartography table).
- [x] mineshaft_entrance — was a sealed facade; opened a 2-tall walk-in tunnel mouth + rails leading in + an open dig-down shaft start at the back (surface entrance marker).
