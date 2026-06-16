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
- [ ] cactus_farm — no cactus planted; cactus breaks if ANY solid block is horizontally adjacent, and two cacti can't be adjacent (need gaps). Correct design: spaced cactus columns on sand, a "breaker" block at the 2nd segment height beside each so grown segments auto-break and fall into water → hopper → chest (no redstone needed). Also: cobblestone on top of the chests blocks them — use glass or nothing.
- [ ] bamboo_farm — no bamboo planted; observers fire once on plant then never again (watching the wrong cell). Bamboo grows UP; observer must watch the growth cell so it re-fires each growth → piston breaks → water/hopper. Or simpler reliable design.
- [ ] kelp_farm — no kelp planted; a redstone piece broke off on print (redstone can't sit in/under water). Kelp grows UP underwater; redstone control must be in a DRY (glass-enclosed) chamber. Plant kelp on a solid block underwater; harvest at the top; collect.
- [ ] animal_pen — no flooring (add a floor). Feature request: place a spawn egg to add a pig/cow — mobs are ENTITIES (can't print); spawn eggs are items, not placeable blocks. Could optionally stock spawn eggs in a chest (item NBT) — discuss with user.
- [x] sugarcane_farm_auto — water now adjacent to the sand at the soil's own level (y=1) so canSurvive passes (cane no longer pops on print); observers raised to the cane's 2nd block for correct harvest; snapped cane → water → hopper → chest.
- [x] super_smelter — full row of input chests, each with a down-hopper feeding its own furnace; fuel + output extended across the whole bank so all 5 furnaces run in parallel.
- [x] map_room — filled the 4 corner gaps (potted cornflowers at the entry, barrels by the cartography table).
- [x] mineshaft_entrance — was a sealed facade; opened a 2-tall walk-in tunnel mouth + rails leading in + an open dig-down shaft start at the back (surface entrance marker).
