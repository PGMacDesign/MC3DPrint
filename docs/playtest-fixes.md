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
- [ ] chicken_coop_auto — lava leaks (1-block air moat around it) AND is sealed away from the chickens (can't cook). Redesign with a contained **lava cauldron** cooker (now proven printable) + accessible chest.
- [ ] mob_xp_tower — mechanism still unclear/messy. Clarity rework (clear spawn→water→drop→kill→collect path + access).

## Open (new findings, in order)
- [ ] super_smelter — only the one furnace near the input chest is used. Need a full **row of input chests, each with a hopper below pointing forward** into its furnace (or the hopper feeding it), so every furnace in the row gets fed. (Build otherwise fantastic.)
