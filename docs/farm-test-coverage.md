# Farm Test Coverage — what's auto-tested vs. what needs a live playtest

**Companion to** `docs/blueprint-qa-architecture.md` §4 (residual human-QA surface) and §5.5
(roadmap item: per-farm function tests). This doc maps each functional-farm blueprint to the
specific behaviours that are now caught by an automated GameTest vs. the behaviours that still
require a human playing the printed build.

**Source of truth:** `gametest/FarmCollectionGameTests.java`
(`@GameTestHolder(MC3DPrint.MOD_ID)`), run by `./gradlew runGameTestServer -q`.

---

## What the auto-test proves: collection routing

The full survival loop — player plants a crop, it grows on a slow random tick, an
observer/piston snaps it, mobs spawn — is **not** tractable inside a GameTest (no player, no
multi-minute growth, no mob-spawn cycles). The tractable, high-value slice is **collection
routing**: once a product item exists at the harvest point, do the build's hoppers actually
deliver it to the collection chest?

For each covered farm the test:
1. places the **real** curated blueprint into the test world (every cell resolved + placed —
   working hoppers, water, chest), then
2. locates the collection chest (scans for a `ChestBlockEntity`, cross-checked against the
   dump's chest cell), then
3. spawns an `ItemEntity` of the farm's **product** at the harvest point (the cell directly
   above the first collection hopper — where a snapped crop / mob drop lands), seated low so it
   sits at the hopper's suck box even in flooded canals, then
4. ticks forward (≤ 200) and **asserts the chest inventory contains the product** — failing if
   the drop never arrives.

This isolates the leg that has historically broken (wrong-facing hopper, chain that dead-ends
short of the chest). It does **not** test growth, redstone timing, or mob spawning — see the
"needs live playtest" column.

---

## Coverage matrix

| Farm (`blueprint`) | Product | Routing (auto) | Still needs live playtest |
|---|---|---|---|
| `sugarcane_farm_auto` | sugar cane | ✅ hopper → chest | water-sweep of snapped cane down the channel; observer re-fire; piston snap; cane survival |
| `kelp_farm` | dried kelp | ✅ hopper-floor chain → chest | kelp growth + submerged observer/piston snap; furnace smelt |
| `bamboo_farm` | bamboo | ✅ hopper → chest | water-sweep of snapped bamboo down the canal; observer re-fire; piston snap |
| `cactus_farm` | cactus | ✅ hopper → chest | cactus growth into the breaker; water-sweep of the snapped segment |
| `iron_farm` | iron ingot | ✅ hopper-ring funnel → drain → chest | golem spawn (needs villagers); drop down the shaft; lava-cauldron kill |
| `chicken_coop_auto` | cooked chicken | ✅ 3×3 hopper grid funnel → chest | chicken growth; lava-cauldron cook; drop timing |

**Auto-tested = routing only.** A ✅ means: an item placed at the harvest point reaches the
chest through the build's real hoppers within 200 ticks. Everything in the right column is a
live-playtest item — none of it is claimed as covered.

### Not yet routing-tested (other functional builds)

These functional farms are **not** in the auto-routing set yet. Most have no hopper/water
collection-to-chest path (so there's nothing routing to assert), or their collection is
mob/entity-driven in a way the static test doesn't model. Add them to `FARMS` in
`FarmCollectionGameTests.java` if/when a well-defined harvest-point → chest path exists:
`pumpkin_melon_farm` (player breaks fruit → water sheet → trench → hopper — a good next
candidate), `mob_xp_tower`, `villager_trading_hall`, `animal_pen`, `fishery_pond`,
`tree_farm`, `mushroom_farm`, `nether_wart_farm`, `bee_apiary`.

---

## Bugs this test found (2026-06-16)

Building the routing test surfaced **three real collection-routing bugs** in shipped builds,
all now fixed in `CuratedBlueprintGenerator` + regenerated `.blueprint` files:

1. **`sugarcane_farm_auto`** — the collection hopper at the south end of the channel was
   `facing=north`, so it ejected **back up the water channel** (into a water cell, no
   container) instead of into the chest one cell **south**. A hopper ejects toward its
   `facing`; with the chest to the south it must face south. Fixed → `facing=south`. (The
   generator comment even claimed "points north into the chest tucked at the south edge" — an
   inverted mental model of `facing`.)
2. **`bamboo_farm`** — identical bug: south-end collection hopper `facing=north`, chest one
   cell south. Fixed → `facing=south`.
3. **`chicken_coop_auto`** — the 3×3 hopper pen-floor had **every** hopper `facing=south`, but
   only the centre column sits north of the chest. A cooked chicken landing in the left/right
   third chained south to a hopper that then ejected into **air** (the chest is centre-only),
   so two-thirds of the pen never collected. Fixed → the side columns now funnel to centre
   (west→east, east→west) and the centre column chains south into the chest.

The three "correctly-facing" farms (`kelp_farm`, `cactus_farm`, `iron_farm`) passed on the
first run, confirming the test discriminates good routing from bad.

---

## Adding a farm to the routing test

Append a `Farm` entry to `FARMS` in `FarmCollectionGameTests.java`. Coordinates are
blueprint-local, taken straight from `build/blueprint-dumps/<name>.txt` (regenerate with
`./gradlew test --tests '*BlueprintDumpTest*' -DdumpBlueprints=true --rerun-tasks`). The dump
grid is `rows = z` (south), `cols = x` (east); the layer header is the Y. Pick the harvest cell
as the cell **directly above the first collection hopper**, avoiding any lava-cauldron
kill/cook block. Add a matching `@GameTest` method that calls `runRoutingTest`.
