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
- [ ] sugarcane_farm_auto — all cane breaks on print. Root cause: water is at y=0 (floor) but the sand soil is at y=1, so the soil has NO horizontally-adjacent water at its own level → cane fails canSurvive on the print's neighbor reconcile. Fix: put water adjacent to each sand block at y=1; also raise the observers to the cane's 2nd-block height for correct harvest timing; verify collection still reaches the hopper.
- [x] super_smelter — full row of input chests, each with a down-hopper feeding its own furnace; fuel + output extended across the whole bank so all 5 furnaces run in parallel.
- [x] map_room — filled the 4 corner gaps (potted cornflowers at the entry, barrels by the cartography table).
- [x] mineshaft_entrance — was a sealed facade; opened a 2-tall walk-in tunnel mouth + rails leading in + an open dig-down shaft start at the back (surface entrance marker).
