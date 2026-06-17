# Blueprint QA Architecture & Authoring Patterns

**Goal:** make curated blueprints *correct by construction and auto-verified*, so the catalog can scale to hundreds or thousands of builds with **minimal manual playtest QA**. This doc captures the bug taxonomy we hit, the two-track defense, what each track catches, and what still needs a human.

The blueprints are generated deterministically by `CuratedBlueprintGenerator` (gated JUnit, `-DgenBlueprints=true`) — see the `blueprint-pipeline` memory. Every bug below was found by *playing the printed build in-game*; the strategy is to push each class leftward (into a build-time gate or a safe builder) so it never needs a human eye again.

---

## 1. Bug taxonomy (everything found this QA round)

| Class | Concrete bugs seen | Auto-detectable? | Guardrail |
|---|---|---|---|
| **Floating / off-ground** | build shifted up 1, grass footing dropped (grass_block was unpriced) | yes (static) | `-DauditFoundations` |
| **Sealed interior** | modern_glass_villa (both floors), copper_clocktower, greek cella | yes (flood-fill) | `-DauditReachability` (hard-throws, allowlist) |
| **Door/opening over a solid wall** | greek/copper/villa/diamond_vault/sailing_ship — `set(pos, AIR)` is a no-op so "carving" leaves the wall solid | yes (reachability catches the seal) | reachability + **builder fix** |
| **Unbacked ladder** | wizard_tower (won't place), library, 6 towers — wrong `facing` (attaches at `facing.getOpposite()`) | yes (static) | `-DauditLadders` |
| **Stair facing wrong** | tavern_inn, stable_horse, mushroom_island_hut — a stair **ascends toward its `facing`**; authors set it backwards | partial (climb-runs only; roofs cause FPs) | convention doc + **builder** |
| **Plant on invalid soil** | bee_apiary flowers on planks, koi cane on brick, etc. | yes (static rule table) | `-DauditPlantSupport` |
| **Attachment unsupported** | (general case of ladders) redstone/wall-torch/sign/rail/carpet on bad support | yes (real `canSurvive`) | **canSurvive GameTest** (always-on) |
| **Hanging lantern unsupported** | japanese_pagoda + japanese_tea_house eave lanterns under top-half stairs; gazebo/fishing_hut floating; blacksmith/railway under top slabs — all pop on print | yes (static; CENTER-support rule) | **hanging-lantern audit** (always-on) |
| **Gravity block floating** | sand/gravel/concrete_powder/anvil with air below → falls on print | yes (static) | gravity check |
| **Bed embedded / no headroom** | elven_treehouse (glass over the mattress, wedged in wall) | yes (static) | `-DauditBeds` |
| **Blocked navigation** | mushroom_island_hut threshold step, greenhouse drop-into-water, apothecary floating aisle row | yes (step+headroom flood-fill) | **navigability audit** |
| **Farm/redstone doesn't function** | observers watching the wrong cell, fake auto-harvest, crop won't survive, redstone in water; **collection hopper facing the wrong way / dead-ending short of the chest** (sugarcane, bamboo, chicken — found 2026-06-16) | **collection routing: YES** (`FarmCollectionGameTests`); growth/redstone/spawn: mostly NO (needs sim) | routing GameTest + playtest + design review |
| **Aesthetic / "feel"** | floating decor, sparse rooms, weird proportions, immersion | **NO** | human eye |

The first ~10 rows are *mechanical* and should become fully automated. The last two are the irreducible human-QA surface — the goal is to shrink everything else to zero so a human only ever judges *function and taste*.

---

## 2. Two-track defense

### Track A — automated gates (catch regressions at build time)
A suite of checks that read the `.blueprint` NBT (or place the build in a GameTest) and fail on violations. Two execution modes:
- **Gated audits** (`-D<flag>=true`): opt-in diagnostic reports, good for discovery + iteration. Current: `auditFoundations`, `auditLadders`, `auditPlantSupport`, `auditReachability`, `auditBeds`. (Each needs a `systemProperty` line in `build.gradle` — gradle doesn't forward `-D` to forked tests.)
- **Always-on gates** (GameTests in `runGameTestServer`, or ungated JUnit assertions in `./gradlew build`): run on every build, **fail the build** on violation. Current: printability, render-integrity (stub panes), double-block (GameTests); **ladder-support, plant-support, bed-clearance (allowlist: iron_farm), reachability (allowlist: `INTENTIONALLY_SEALED`), gravity-support** (ungated asserting JUnit — promoted 2026-06-16). A new build that floats a ladder/plant/sand, seals a room, or buries a bed now **fails `./gradlew build`** with no human playtest. Foundation + navigability stay opt-in diagnostics (heuristic, false positives).

**The minimal-QA lever:** an author adds a new build → `./gradlew build` runs all always-on gates → mechanical bugs fail immediately, no human playtest needed for that class. The more gates are always-on, the less QA per build.

### Track B — correct-by-construction helpers
The recurring bugs (stair facing, door-over-wall, unbacked ladder, plant-without-soil) all came from **hand-placing blocks and getting a convention wrong**. The fix is to encode the convention in a reusable builder so it *can't* be gotten wrong:
- `staircase(b, fromPos, toPos, material)` → computes the ascent axis + correct `facing` automatically (kills the stair-facing class).
- `doorway(b, wallLoop, openingCells, ...)` → leaves the opening cells unset *in the wall loop* (kills the `set(AIR)`-no-op class).
- `ladderRun(b, col, y0, y1, wallSide, material)` → places the ladder AND its backing, facing computed from the wall side (kills unbacked-ladder).
- `plantBed(b, region, soil, plants)` → always lays valid soil under each plant.
- `loftAccess(...)`, `multiFloor(...)` → standard reachable vertical access with hatch + headroom.

Every helper should be paired with the gate that proves it (defense in depth: the helper makes it right, the gate proves it stayed right).

---

## 3. Convention reference (the things authors/agents kept getting wrong)

- **Stair facing:** a stair **ascends toward its `facing`** (raised step is on the facing side). Walk up heading north → `facing=north`. (NOT "tall riser opposite facing" — that belief was wrong, confirmed in-game.)
- **Ladder facing:** a ladder **attaches to the solid block at `facing.getOpposite()`** (the wall is behind it; `facing` points at the climber). Backing must be a sturdy full block.
- **`Builder.set(pos, AIR)` is a no-op** — air is skipped, so you can't carve an opening by overwriting. Leave opening cells unset in the wall loop instead.
- **Plant support:** flowers/saplings → dirt-family + farmland; crops → farmland; cactus → sand; sugar_cane → dirt/sand + adjacent water; nether_wart → soul_sand; mushrooms are flexible (low light / mycelium-podzol-nylium anywhere).
- **Printer places with `SUPPRESS_DROPS`** → invalid attachments survive placement but pop on the next block update (silent). This is *why* static audits matter: the GameTests don't re-check survival on their own.
- **Structural matter prints free** (`isStructuralMatter`): itemless blocks, BushBlock plants, farmland/path. These are the blocks that can be on bad soil and still *print* (then float/pop) — hence the plant audit.
- **Flammable build → NO raw lava/fire.** Use `campfire` / `lava_cauldron` / `magma_block` / a torch (none of those spread fire) for a hearth/forge look. Raw `lava` (or `fire`/`soul_fire`) within ~2 cells of wood/wool/leaves ignites and burns the build down — even caged behind iron bars across an air gap (this is what burned tavern_inn's spruce shell). Caged in stone/deepslate with no flammable in range is safe. Gated diagnostic: `-DauditFireHazard=true`.

---

## 4. Residual human-QA surface (what gates can't catch — yet)

1. **Farm/redstone function** — does the observer re-fire, does the piston break the right cell, does the crop survive and the drops route to the chest? Requires a redstone/tick simulation or a scripted in-world GameTest per farm. Highest-value *hard* automation target after the mechanical gates are done.
2. **Aesthetics & feel** — proportions, decoration density, immersion, "is this fun to look at / live in." Always human.
3. **Intent** — is a sealed cavity a bug or a design choice (vault core, mob shaft)? Handled today by allowlists; a build could instead declare intent in metadata.

---

## 5. Roadmap to minimal-QA-at-scale

1. ✅ **Promote all reliable audits to always-on hard gates** (with allowlists). *Done 2026-06-16: ladder/plant/bed/reachability/gravity now fail `./gradlew build`.*
2. ✅ **Add a navigability audit** (standable-cell flood-fill: passable + 2-block headroom + floor, step≤1) — catches threshold/drop-in/blocked-aisle that sealed-interior reachability misses. *Done as a gated diagnostic (`-DauditNavigability`); heuristic (≈17 false positives on farms/decor/domes), so report-only for now. It already caught a real one: purpur_tower's door opened into a solid wall.*
3. **Tighten navigability toward a gate** — reduce false positives (model curved/dome headroom, ladder-bridged verticals, mechanical-top exclusions) so it can become always-on with an allowlist.
4. **Build Track-B safe builders** for the bug-prone patterns (`staircase()` with auto-facing, `doorway()` that skips wall cells, `ladderRun()` that places + backs); refactor existing builds onto them so conventions are centralized. *This kills the stair-facing + door-over-wall classes at the source — they recurred most.*
5. 🟡 **Per-farm function tests** (scripted GameTests that run the contraption N ticks and assert the chest fills) — the last big mechanical class. *Partially done 2026-06-16: `FarmCollectionGameTests` auto-tests the **collection-routing** slice (item at the harvest point → hoppers → chest) for sugarcane/kelp/bamboo/cactus/iron/chicken farms; it caught 3 real wrong-facing-hopper bugs. Growth, redstone timing, and mob spawn still need a live playtest — see `docs/farm-test-coverage.md`.*
6. **Build metadata** — declare intent (sealed-on-purpose, decorative-solid, farm-vs-house) so gates self-configure instead of needing per-build allowlists.
7. With 1–6 in place, a new build's QA is: `./gradlew build` (mechanical gates) + a brief human look for taste/function — minutes, not a playthrough.

## 6. Current gate coverage (as of 2026-06-16)
Always-on (fail the build): printability, render-integrity, double-block, **ladder-support, plant-support, bed-clearance, reachability, gravity-support, navigability, fire-hazard, hanging-lantern** (a hanging lantern under air/top-stair/top-slab pops on print — CENTER-support rule; fences/walls pass), **farm collection-routing** (`FarmCollectionGameTests` — 6 farms: item at the harvest point reaches the chest through the real hoppers). Opt-in diagnostics: foundation (`-DauditFoundations`). All currently clean across 132 builds; **76 GameTests green**. The mechanical bug classes from this QA round are now caught automatically; farm **collection routing** is now partly automated (it caught 3 wrong-facing-hopper bugs — see `docs/farm-test-coverage.md`). What remains for human QA is **farm growth/redstone/spawn function** and **aesthetics/feel**.
