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
- [x] animal_pen — now prints a full grass_block floor at y=0 (grass_block became FU-valued this session, so the old "no floor/player terrain" rationale is obsolete); trough water + shelter plank floor preserved via overwrite ordering. **Spawn-egg decision RESOLVED** (Patrick chose "empty chest only"): added an unstocked feed/storage chest in the NW shelter corner (1,1,1) — no creative-only spawn eggs handed out; you bring your own.
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
- [x] **church** — FIXED (you chose reachable): opened a 2-tall base doorway from the nave into the steeple (2,1-2,1), ran a ladder up the 1×1 shaft (1,1-5,1) backed by the north wall, relocated the glowstone out of the shaft, removed the belfry plank floor, and re-hung the bell ceiling-attached at (1,7,1) (anchored to stone at (1,8,1)) so it's ringable from the top rung. — was a sealed stone shaft.
- [x] **barn** — NO FIX NEEDED (analysis was wrong). Re-verified against `pillar(b, x, z, y0, y1, mat)`: `pillar(b,7,5,1,2,HAY)` is x=7/z=5/**y=1..2** — the hay is floor-level corner bales, NOT floating at y=5 (the audit agent misread the signature). Dump confirms hay only in y=1/y=2; y=4-6 are pure roof. It's a sound single-storey barn (walls y=1..2, gambrel roof y=3..6 — no vertical room for a loft, and none needed). Left as-is.
- [x] **nether_wart_farm** — FIXED: consolidated the brewing station into the NE corner (capping the x=6 walkway dead-end) + moved the chest to cap the x=3 dead-end; restored the deleted wart cells so all 3 beds are whole; walkways clear end-to-end. — was: a `water_cauldron` overstamped a crop cell + NE furniture boxed in the walkway.

## Pre-retest sweep — unbacked ladders (2026-06-16)
Before the next in-game test, swept ALL 132 builds for the unbacked-ladder class (a ladder prints via the printer's `UPDATE_SUPPRESS_DROPS` even when its support cell is air, then **pops on the next block update** — a SILENT failure the GameTests don't catch). Added a reusable gated audit: `./gradlew test --tests '*BlueprintLadderSupportAuditTest*' -DauditLadders=true --rerun-tasks` → `build/blueprint-ladder-audit.txt`. It found **6 builds the manual multi-level audit had wrongly cleared** (that audit checked the *facing* side, not the *support* side = `facing.getOpposite()`). All fixed; audit now 0-flagged:
- [x] copper_clocktower / purpur_tower — ladder `facing=north` mounted against the NORTH wall (support side z+1 was air); flipped to `facing=south` so it attaches to the wall.
- [x] guard_tower — ladder was dead-center (air on all sides); relocated to the N-wall corner column `facing=south` + moved the deck hatch to match.
- [x] mangrove_stilt_hut — backing post is SOUTH of the ladder but it was `facing=south` (attaches north=air); flipped to `facing=north`.
- [x] sailing_ship — quarterdeck ladder refaced to back onto the raised-deck plank; hold ladder moved off the centerline onto the hull side (a ladder can't hang off a deck underside).
- [x] victorian_townhouse — the wall courses skipped the inter-story floor levels, leaving 2 rungs unbacked; added matching brick backing at those cells.

Root cause across most: the `facing` was set TOWARD the backing wall instead of away from it (a ladder attaches to `facing.getOpposite()`). Config verified current (game-instance `mc3dprint-common.toml` already has the grass_block/mycelium FU values), jar rebuilt + redeployed.

*Confirmed OK (spot-checked, no action):* villager_trading_hall, fishery_pond, koi_pond, small_farm, bee_apiary, scarecrow (farms); fishing_hut, japanese_tea_house, railway_station, victorian_townhouse (the buried-door audit flags are all **legit reachable raised entrances**, not traps); lighthouse, watchtower, guard_tower, copper_clocktower, purpur_tower, manor_house, victorian_townhouse, copper_observatory, windmill, japanese_pagoda (multi-level access all sound). **No stair-facing bugs of the tavern/stable kind remain** — every other interior climb is a ladder; the failures above are backing/run-length/destination, not facing.

---

## Retest round 2 (2026-06-16, second in-game pass)
PGMac retested in-game; this round's findings + the audit-surfaced extras. **Built 3 of the requested guardrails as gated audits** (plant-support, reachability, bed-clearance) — see the `blueprint-qa-audits` memory — and they surfaced more instances than were spotted by eye.

**STAIR FACING CONVENTION CORRECTED:** I had it backwards. A stair **ascends toward its `facing`** (raised step on the facing side), so a northward climb = `facing=north`. (My earlier "tall riser opposite facing" was wrong; PGMac confirmed `facing=south`-for-northward read backwards on both tavern_inn and stable_horse.)

- [x] tavern_inn — flipped the 6-step climb to `facing=north` (kept the position/hatch/landing fixes).
- [x] stable_horse — replaced the (mis-oriented) loft stairs with a wall-backed **ladder** up the west-middle stall (PGMac preferred a ladder — more authentic); ladder audit 0-flagged.
- [x] mushroom_island_hut — threshold step into the hut: the agent set `facing=north` (riser toward the entering player → still blocked); corrected to `facing=south` (player enters walking south).
- [x] greek_quartz_temple — dropped the useless solid bottom plinth (verified empty) and re-based down so it's enterable; also fixed a latent sealed cella door (`set(air)` no-op).
- [x] elven_treehouse — bed was embedded (glass over the foot + wedged in the wall); relocated into open floor with headroom. Bed audit clean.
- [x] modern_glass_villa — was sealed on both floors; added a ground-floor door + a backed ladder to the 2nd floor. Reachable.
- [x] copper_clocktower — sealed; added a base door into the shaft (climb the existing ladder up).
- [x] bee_apiary — flowers were on planks (floated); laid grass_block under each → garden beds.
- [x] cherry_grove_cottage — pink_petals on planks → moss_block patches.
- [x] koi_pond — sugar_cane on stone_bricks → dirt + adjacent water.
- [x] cemetery_plot — dead_bush on a wall → relocated to ground.
- [x] greenhouse — 2 stray crops sat on corner lanterns → removed (bed layout intact).
- [x] diamond_vault — iron door opened into a solid inner wall (sealed); carved a real doorway + opened the cage so the chamber is enterable.
- [x] sailing_ship — cabin roof-hatch sat over a wall → recentered it; below-deck bilge is intentional (allowlisted).

**Guardrails now in place (gated audits):** `-DauditFoundations`, `-DauditLadders`, `-DauditPlantSupport`, `-DauditReachability` (hard-throws, with an intentional-seal allowlist), `-DauditBeds`. All currently clean (bed audit's only flags are iron_farm's by-design villager beds). 69 gametests green, 132 blueprints, jar rebuilt + redeployed.

**Recurring bug class noted:** `Builder.set(pos, AIR)` is a no-op, so "carving" an opening by overwriting a wall with air leaves it solid (door/hatch into stone). Fix by skipping those cells in the wall loop. Hit on greek/copper/villa/diamond_vault/sailing_ship — the reachability audit catches it.

---

## Retest round 3 (2026-06-16, third in-game pass) — NO jar redeploy (Patrick actively testing)
New bug class: **a fire source (raw `lava`/`fire`) next to flammable blocks (wood/wool/leaves/etc.) ignites and burns the build down.** "Boxing" lava in cobble is NOT enough — lava ignites flammables a couple blocks away. Safe heat sources near wood: campfire, lava_cauldron, magma_block, torch/lantern (none spread fire).
- [x] **tavern_inn** — DONE: campfire hearth replaces the lava firebox (campfires don't spread fire); cobble surround + grate + chimney kept. — the caged-lava hearth burned the whole (spruce) tavern down. Replace the lava firebox with a **campfire** (no fire spread); keep the cobble surround + chimney + grate.
- [x] **flammable-near-fire guardrail (audit built)** — `BlueprintFireHazardAuditTest` (`-DauditFireHazard`) flags raw `lava`/`fire` within Chebyshev≤2 of flammable blocks. Convention documented in qa-architecture §3. Found 2 real hazards (below); iron_foundry lava is SAFE (encased in deepslate/iron, no wood). Promote to always-on gate after the 2 fixes.
- [x] **blacksmith** — DONE: forge lava → magma_block (glowing hot bed, no fire spread).
- [x] **smithy_workshop** — DONE: forge lava → lava_cauldron (contained molten crucible, no fire spread).
- [x] **fire-hazard audit promoted to always-on gate** — 0 flagged; raw lava near flammables now fails the build.
- [x] **modern_glass_villa** — DONE: cleared the smooth-quartz slab the entrance-fix left in the door approach; flat walk-through.
- [x] **copper_clocktower** — DONE: lowered interior shaft floor y=1→y=0 (flat walk-in to the shaft, then climb the ladder).
- [ ] **CORRECTED THRESHOLD RULE (Patrick):** a stair just inside the door does NOT work — your head hits the door's upper half stepping up. The fix is a FLAT walk-through: the cell you step into must be at the SAME level as the door sill (leave it empty / level it), with 2-block headroom through the open door. Applies to mushroom_island_hut (the stair I added is wrong → flatten) + copper + any raised-floor entry.
- [x] **mushroom_island_hut** — DONE: removed the wrong threshold stair; cleared to a flat mycelium walk-in at sill level.
- [x] **navigability gate — flat walk-through enforced** — every door must walk in flat at sill level (≤½-block slab/drop-in OK; full step-up or blocker fails). Flagged + fixed 7 builds (copper, mushroom_island_hut, modern_glass_villa, purpur_tower, mushroom_farm, redstone_workshop, barn); fence-gates/slab/carpet thresholds correctly NOT flagged. Always-on gate, green.
- [x] **bee_apiary** — DONE: removed the chains; the two aisle lanterns now hang directly off the y=4 honeycomb beam (at y=3, one block above head) so the aisle is walkable.

---

## Retest round 4 (2026-06-16, fourth in-game pass) — NO jar redeploy (Patrick actively testing)
New bug class: **a hanging lantern under a non-sturdy support (a stair/slab) pops off on print** — same attachment-support family as the unbacked-ladder class. A `lantern[hanging=true]` needs the block above it to present a sturdy DOWN face (full block, or a chain — chains float + support center). Top-half stairs / slabs do NOT, so the printer places the lantern via suppress-drops and it drops on the next block update.
- [x] **japanese_pagoda** — DONE: the 16 eave-corner lanterns hung directly under the upturned-corner bracket **stairs** (top-half `dark_oak_stairs`) → all broke off on print. Each now hangs off a **chain** (chain at ey-2, lantern at ey-3), mirroring the build's own interior soul-lantern. Interior soul-lantern was already fine.
- [x] **(guardrail) BlueprintHangingLanternAuditTest** — DONE: always-on gate. A hanging lantern uses CENTER support, so it flags exactly the cases that genuinely fail — **air/OOB above, a top-half stair, or a top slab** (fences/walls pass: their centre post covers the column). 0-flagged now; fails the build on any future break.
- [x] **iron_farm — zombie cell** — DONE: rebuilt as a FULLY ENCLOSED 1×2×3 glass box on the east wall (interior x=11/z=5-6, walled y=10..12 on every deck-facing side, stone corner posts, perimeter east wall, roof cap). The zombie can't step out onto the deck and fall now; glass keeps villager line-of-sight; the 1×2 fits a boat. Verified in the dump; reachability stayed green.
- [x] **iron_farm — wash water floods the room** — DONE: a 2×2 layer of **wall signs** in the drop mouth (y=3, each attached to the surrounding ceiling brick) dams the wash water at the ceiling — shaft above fills, collection room (y=1..2) stays dry, golem/items still fall through. Verified in the dump.
- [x] **(cascade) 5 more builds with the unsupported-hanging-lantern class** — DONE (surfaced by the new audit, none were tested in-game): **japanese_tea_house** (8 eave lanterns under top-half stairs → chain, identical fix to the pagoda), **gazebo** (4 floating → chain at the y=4 air cell above each), **fishing_hut** (2 porch lanterns floating → chain at the eave course), **blacksmith** + **railway_station** (lanterns under top slabs → solid anchor block re-stamped above, flush with the slab roof, keeps the lantern at height). Audit 0-flagged.
- [x] **mob_xp_tower — REIMAGINE (Patrick)** — DONE: reworked the spawn deck. A solid BASIN floor (y=18, 7×7 minus the central 3×3 hole) holds a 1-deep water FRAME (y=19) fed by 4 sources at the arm mid-edges that flow INWARD and drain down the open 3×3 hole; four DRY 2×2 corner PADS (y=19) are the only spawnable surface. Mobs spawn on the dark pads, step off into the flushing frame, get swept to the hole and down the shaft. Also made the roof a FULL stone block (slabs leak skylight) so the chamber stays light-0. Verified in the dump; build + 76 gametests green. *(was: drop hole never punched + flat water pool that won't sweep and mobs won't spawn on)* (the 2×2/3×3 "hole" cells are solid, so mobs can't fall to the kill chamber — Patrick punched one by hand to confirm intent); (2) the platform water just sits in a pool — mobs won't spawn on water AND a flat pool won't sweep them anywhere. Rethink the whole spawn-deck → drop → kill flow: real open drop hole, DRY spawnable platform with water that actually flushes mobs into the hole (sources at the rim flowing inward to the single low point), or an alternative that works. Screenshots: water pooling on the deck + sitting in the corners.
- [x] **pumpkin_melon_farm — REIMAGINE from scratch (Patrick)** — DONE: rebuilt 9×9×4. The flood is gone — the ONLY water is a single 1-wide channel down the centre (x=4, y=1) on a hopper line → chest. Stems are OUTER (x=2,6), the dirt growth blocks INNER (x=3,5, beside the channel) so a broken gourd drops into the channel → hopper → chest. (Pushed back on the vertical sticky-piston auto-harvest: the dirt growth block sits *between* the fruit the observer must watch and the piston below it, so there's no clean observer→piston link — it needs fragile multi-block redstone that won't reproduce in a compact print. Shipped the robust minimal-water semi-auto instead.) Verified in the dump; build + 76 gametests green.
- [ ] ~~pumpkin_melon REIMAGINE detail~~ — current build is "massively screwed up / useless" (screenshot: a big flat water pool, no working mechanism). Redesign. Patrick's suggested design: sticky pistons UNDER the dirt growth blocks; when a pumpkin/melon grows onto an adjacent dirt block, the observer (watching the grow cell) fires → pulse routed DOWN → triggers the sticky piston to shoot UP into the block the fruit formed on, breaking it → fruit drops to a water sweep → hopper → chest. Build around that piston-up-break mechanic (the earlier "sideways growth + flowing-water sweep" wasn't enough).
- [x] **sugarcane_farm_auto — cane breaks on one side + needs a full hopper floor (Patrick)** — DONE: (1) removed the y=2 water (the cane's OWN level — it flowed into the not-yet-settled cane during the print and washed a row out); kept the single y=1 water line beside the soil so canSurvive still passes ("water down one layer"). (2) Replaced the single end-hopper with a full hopper LINE under the whole channel (x=4, z=1..7) all facing south — sucks cut cane from the water above wherever it lands and chains it to the chest. Verified in the dump; build + 76 gametests (incl. sugarcane collection routing) green. *(original two problems below)*
  - two problems: (1) the cane on one side broke the instant the build finished, BEFORE the water was placed (player can manually replant and it holds → likely the water is one layer too high / on the cane's level so the cane pops, or the soil/water relation is off — water should be one layer DOWN from the cane's planting level so it irrigates without touching the cane stalk); (2) there's no hopper floor under the water channel — harvested cane floats down the water and FALLS THROUGH. Need hoppers across the WHOLE bottom (under the water), all pointing toward the front chest, so cut cane routes to the chest instead of dropping out. Screenshots: cane on one side, underside showing no hoppers.
- [x] **cactus_farm — REIMAGINE from scratch (Patrick)** — DONE: rebuilt as a compact 5×5×4. The flooded pool is gone — two cacti 2 apart, ONE shared breaker between them, a tight 7-cell water MOAT (just rings the bases so segments land in water, not on the cactus that would destroy them), and a connected HOPPER FLOOR under the whole moat that sinks the catch and chains it to the chest. Verified in the dump; build + 76 gametests (incl. cactus collection routing — re-fixed when the first moat-only version isolated the water) green. *(original detail below)*
- [ ] ~~cactus REIMAGINE detail~~ — "a certified mess… WAY too much water," useless. Cactus needs: sand grow-blocks each with the 4 horizontal neighbors EMPTY (cactus breaks if any side block is adjacent), a breaker (a block) at the cactus's grow-into height so the grown segment pops, and the drop routed to a hopper→chest. NO big water pool — cactus collection is usually a hopper directly under the breaker, not a water sweep. Keep it compact and dry.

## Status: Patrick DONE testing (2026-06-16) → finish ALL queued items, then rebuild + REDEPLOY the jar + push, then notify.
