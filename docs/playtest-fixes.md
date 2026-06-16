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
- [x] stable_horse (Horse Stable) — the old north-wall ladder dead-ended into the solid loft slab at the eave (roof at (2,5,1) gave zero climb headroom there → couldn't ascend without breaking blocks). Replaced with a 4-step `facing=south` stair run in the front-half west stall climbing north ((2,1,8)→(2,2,7)→(2,3,6)→(2,4,5)) flush onto the loft's south edge, landing on the standable loft cell (2,4,4) (z≥3, roof y=8 = 3 blocks headroom). Reachable from the south bay / the aisle gate at (3,1,8); verified the full climb layer-by-layer in the dump. (Note: became a stair, not a ladder — the access works; tell me if you want the ladder aesthetic back.)

## Strategy note (farms)
Regrowing crops (cactus/bamboo/kelp/cane✓/melon✓) → make them actually work. Non-regrowing (trees) → simplify to honest semi-auto. Remaining unflagged farms to proactively check: villager_trading_hall, fishery_pond, mushroom_farm.
- [x] sugarcane_farm_auto — water now adjacent to the sand at the soil's own level (y=1) so canSurvive passes (cane no longer pops on print); observers raised to the cane's 2nd block for correct harvest; snapped cane → water → hopper → chest.
- [x] super_smelter — full row of input chests, each with a down-hopper feeding its own furnace; fuel + output extended across the whole bank so all 5 furnaces run in parallel.
- [x] map_room — filled the 4 corner gaps (potted cornflowers at the entry, barrels by the cartography table).
- [x] mineshaft_entrance — was a sealed facade; opened a 2-tall walk-in tunnel mouth + rails leading in + an open dig-down shaft start at the back (surface entrance marker).

---

## Deploy + verification (all 8 reported items done, 2026-06-16)
All 8 open items above ✓ fixed/committed/pushed. Rebuilt `mc3dprint-0.4.0.jar`, **redeployed** to the Prism 1.20.1 instance (replaced in place, checksums match, no duplicate). `./gradlew build` + `runGameTestServer` green — **all 69 gametests pass, 132 blueprints**. No regressions.

## Cascade analysis (per Patrick's request — "do these problems hit other builds I haven't tested?")
Grounded in the foundation audit (`-DauditFoundations=true`) + a 3-way read-only code review of farms, raised entrances, and multi-level access.

**A) Did MY changes regress anything? No.** The global changes are isolated or net-positive:
- `grass_block` now FU-valued (1@1) → builds that use it now print their grass floors/footings instead of dropping them (fixes the floating class; **foundation audit now flags 0 floating builds**). It's the lowest tier so it can't bump any build's disc tier; abundance-safe to wind (dirt-tier).
- Deterministic block-entity serialization + printer `sendBlockUpdated()` on place → signs/containers sync correctly; no gameplay change to other builds.
- Per-build geometry fixes are local. 69/69 gametests + 132 blueprints confirm no regression.

**APPROVED PLAN (2026-06-16):** fix wizard_tower + library + nether_wart_farm cosmetic now; mushroom_farm → honest semi-auto (like tree_farm); make castle_keep top, barn hayloft, AND church belfry reachable; animal_pen → add an empty chest only (no stocked spawn eggs). stable_horse stays a stair. Then rebuild + redeploy the jar again.

**B) Other builds with the SAME bug classes you suspected — yes, a handful (now being fixed per the approved plan):**

*LIKELY-BROKEN (objective bugs):*
- [x] **fantasy_wizard_tower** — FIXED: added a deepslate-bricks backing spine behind the ladder (y1-8, y10-16; the y9/y17 deck discs already back it), so the ladder places and the climb reaches the observatory deck. — the climb ladder was **unbacked**: it's `facing=north` up the center of a round tower, so the cell behind it is interior air the whole run → the ladder has no support face and **pops off on print (won't place)**. You literally can't climb the tower. Fix: add a solid backing spine behind the ladder (clear of the y9/y17 hatches), or hug the ladder to the ring wall at each taper. *(Worst one.)*
- [x] **library** — FIXED: extended the mezzanine ladder to the deck layer (y=1..4, no more 2-block mantle) AND added a dark_oak_planks backing post at (7,1..3,8) so the previously-unbacked lower rungs have a real support face (no longer relying on suppress-drops). — mezzanine ladder ran only y=1..3 but the gallery walking surface is y=5 → a **2-block mantle** with nothing to grab (ladder ends 2 short). Fix: extend the ladder to fill the hatch at the deck layer (y≤mezzY).
- [x] **mushroom_farm** — FIXED: tore out the fake observer/piston/water harvester; rebuilt as an honest dark grow chamber (sealed roof = no skylight, mycelium floor, spaced red+brown mushrooms, door + chest). Mushrooms spread on dark mycelium; player reaps & replants. Sign + javadoc corrected to describe the spread mechanic (chamber is deliberately too short for giant mushrooms). — was fake auto-harvest (observers watch mushrooms that never change state in place).

*SUSPECT (likely needs a decision, not clearly a bug):*
- [x] **castle_keep** — FIXED: added a stone-brick rooftop deck at y=14 across the keep interior (hatch left open at the ladder column) + extended the ladder to y=14, so it climbs flush onto a crenellated battlement (ringed by the y=15 merlons). Great-hall chandelier below untouched. — was a ladder to nowhere (hollow open-top tower).
- [ ] **church** — belfry bell has **no internal access** (sealed stone shaft). Probably intentional (real belfries are sealed; H=12 leaves no stair room) — confirm whether you want it reachable.
- [x] **barn** — NO FIX NEEDED (analysis was wrong). Re-verified against `pillar(b, x, z, y0, y1, mat)`: `pillar(b,7,5,1,2,HAY)` is x=7/z=5/**y=1..2** — the hay is floor-level corner bales, NOT floating at y=5 (the audit agent misread the signature). Dump confirms hay only in y=1/y=2; y=4-6 are pure roof. It's a sound single-storey barn (walls y=1..2, gambrel roof y=3..6 — no vertical room for a loft, and none needed). Left as-is.
- [x] **nether_wart_farm** — FIXED: consolidated the brewing station into the NE corner (capping the x=6 walkway dead-end) + moved the chest to cap the x=3 dead-end; restored the deleted wart cells so all 3 beds are whole; walkways clear end-to-end. — was: a `water_cauldron` overstamped a crop cell + NE furniture boxed in the walkway.

*Confirmed OK (spot-checked, no action):* villager_trading_hall, fishery_pond, koi_pond, small_farm, bee_apiary, scarecrow (farms); fishing_hut, japanese_tea_house, railway_station, victorian_townhouse (the buried-door audit flags are all **legit reachable raised entrances**, not traps); lighthouse, watchtower, guard_tower, copper_clocktower, purpur_tower, manor_house, victorian_townhouse, copper_observatory, windmill, japanese_pagoda (multi-level access all sound). **No stair-facing bugs of the tavern/stable kind remain** — every other interior climb is a ladder; the failures above are backing/run-length/destination, not facing.
