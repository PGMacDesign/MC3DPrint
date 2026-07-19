# Filament Sorter — design

A logistics block that accepts items and routes each one to the Filament Winder holding a
spool of that item's material tier. Solves the "eight winders, one per tier, feed them all
from one chest" problem, which today needs a hand-built vanilla sort line per tier.

Status: **designed, not built.** Decisions below were settled in a design interview
(2026-07-19/20). Next step is `derive-invariants`, then implementation.

---

## What it does

Items arrive by hopper, pipe, or hand. For each item the sorter reads
`FuValueRegistry.valueOf(stack).tier()` and delivers it to a winder whose spool tier matches
exactly. Items it cannot route are either refused at the door or held visibly, never voided.

The routing rule is forced by the economy, not chosen: `FuConversion.canWindInto` is
`spoolTier == materialTier`. Winding has no down-tier fallback the way printing does, so each
item has exactly one correct destination tier, and "the right winder" is unambiguous.

---

## Decisions

### 1. Discovery rides the cable graph, but the cable never carries items

The sorter floods the MC3D Cable network to *locate* winders, then inserts directly into each
winder's own `IItemHandler`. Nothing is ever stored in or transported through a cable.

A block has six faces and the target is eight winders, so pure adjacency cannot work. The
alternative — teaching cable to carry items — was rejected on three grounds: it would require
adding `IItemHandler` to `canConnectTo`, which makes cable visually snap to every chest and
barrel in every existing world; in-transit items need conservation and rollback handling that
the FU/RF cache deliberately avoids by being fungible; and it is a cable buff, which
`rack-and-cable` rules out.

Direct-touch still works. A sorter with winders stacked around it needs no cable at all, the
same way printers already work both adjacent and networked. Cable is what gets you past six.

### 2. Unroutable items: refuse the permanent, hold the transient

Five distinct failure modes, two of which are permanent:

| Case | Nature | Handling |
|---|---|---|
| No FU value | permanent | refuse at `isItemValid` |
| Winder-blacklisted (`ModItemTags.isWinderBlacklisted`) | permanent | refuse at `isItemValid` |
| No winder of that tier on the network | transient | accept, hold, retry |
| Matching winder's input slot holds a different item | transient | accept, hold, retry |
| Matching winder's spool has no room for the yield | transient | accept, hold, retry |

Refusing the permanent cases at the door is what prevents the jam: `WinderBlockEntity`'s
`isItemValid` returns `true` unconditionally for its input slot, so a winder will happily accept
a stack of sticks and then stall forever. The sorter must not pass that along. Refusing also
covers hand-placement, since menu slots honour `isItemValid`.

Transient cases are held. When the pool fills, upstream backs up. Nothing is voided and there
is no reject side in v1; if that proves annoying in play it is a purely additive change.

`isItemValid` calls `FuValueRegistry.valueOf` per insertion attempt. That is a memoised map hit
once warm, but it does couple the sorter to whatever the valuator work concludes — build this
after that lands, not before.

### 3. Round-robin across same-tier winders, skipping stalled ones

Two T3 winders on one network is now a real case. Selection is round-robin over a
`BlockPos`-sorted candidate list, with a persisted per-tier cursor.

First-with-room was rejected: winding is one item per second (`ticksPerItem` 20), so parallel
same-tier winders are a genuine throughput strategy, and first-with-room leaves the second
winder visibly idle until the first has 64 items queued. It parallelises eventually; it looks
broken while it does.

A winder is a candidate only if its input slot is empty or holds the same item with space
**and** its spool has room for the yield. Routing into a winder that will stall strands items
in a machine doing nothing while a working sibling sits idle. The winder already computes this
check before converting; the sorter mirrors it.

### 4. The flood stays in the cable

A routing capability mirrors the existing `IFilamentSource` pattern:

- a **winder** adds itself
- a **cable** calls `ensureFresh()` and adds every winder across its network, exactly as
  `collectSources` does for racks today
- the **sorter** queries its six faces and unions the results; it never implements the
  capability

This keeps BFS and the topology cache in the cable, where both already exist and are already
throttled, and makes discovery a single uniform loop with no `instanceof` branching.

The cable's `RECOMPUTE_INTERVAL` of 100 ticks is not a problem here. The cache stores positions
only; contents are read live and removed block entities are skipped via `isRemoved`. So spool
swaps, spool fill level, and input-slot state are all seen instantly. Only a *newly wired*
winder takes up to ~5s to appear, which is a non-event for how people build. Worth one
opportunistic refresh when the sorter is holding unroutable items, since that is exactly when
someone is wiring a winder up and watching.

The sorter must read the winder's spool tier through direct `instanceof WinderBlockEntity`
access. `SLOT_SPOOL` is exposed on no face — `getItemHandler` returns a `RangedWrapper` over
the input slot for every non-null direction — so capabilities cannot reach it. This matches
`FilamentConverterBlockEntity.findSpoolWithRoom`, which reaches into `PrinterBlockEntity` the
same way.

### 5. One general pool

Nine slots, unstructured. Per-tier slots were rejected because a slot holds one item type while
tiers are not item-unique: string and redstone are both T3, so two T3 items would contend for
one slot and a T3 jam would block unrelated T3 traffic.

The GUI shows a computed per-tier readout — "T3: 12 waiting, 2 winders", "T7: 4 waiting, no
winder found" — which is the information per-tier slots would have conveyed physically, without
their contention problem. This is a router, not a warehouse; visibly backing up is the signal.

### 6. Fast, and free

Up to N items routed per tick, N configurable, default 4.

The Filament Converter's pacing is the wrong template: one item per `ticksPerItem` delivers one
item per second against eight winders' eight per second of demand, starving seven of them. The
64-item input slots hide this for about a minute. The converter is slow because it is
*doing* something; the sorter only moves items between inventories.

**No RF cost.** The block runs unpowered. Most logistics mods do not charge for item movement;
the cost is the up-front build. The item already pays 200 RF at the winder, and taxing the same
item twice for one logical operation is hard to justify. This also removes the energy storage,
the capability registration, the GUI power bar, and the out-of-power stall state.

### 7. A block tag makes the cable attach

Dropping RF has a side effect: `canConnectTo` returns true only for another cable, an energy
capability, or `FILAMENT_SOURCE`. A sorter with none of those gets no cable arm rendered toward
it. This is **cosmetic only** — `collectNeighbors` scans all six directions unconditionally and
never consults the blockstate flags, which drive the model and hitbox alone — but a cable that
visibly refuses to attach reads as a bug, especially when the winders on the same run do attach.

Fix is a `mc3dprint:cable_connectable` block tag as an additional `canConnectTo` clause, with
the sorter tagged. Data-driven, so future blocks that should attach without carrying energy or
filament just get tagged, and pack authors can extend it.

Deliberately kept separate from the routing capability: "should the cable visually attach" and
"does this block implement capability X" are different concerns. The tag is block-level and
therefore not side-aware, which is correct for the sorter's omnidirectional discovery; anything
needing per-face control uses the capability path instead.

Note this is the repo's first `data/mc3dprint/tags/block/` directory. **Singular `block/`** —
the plural form silently fails to load on 1.21 (PGM-51).

---

## Invariants

Derived 2026-07-20. The sorter runs entirely on the server tick thread, so the hazards are NOT
data races — they are **staleness across ticks** (a cached winder position that broke or
re-tiered since the flood) and **interleaving** (a hopper filling a winder's single input slot
the same tick the sorter targets it). The laws below are written against those two.

### Buffer pool + door filter
1. **Permanent-reject at the door** — an unvalued or winder-blacklisted item never enters the
   pool.
   - Guarded by: `isItemValid` returns false when `FuValueRegistry.valueOf` is empty or
     `ModItemTags.isWinderBlacklisted` is true; enforced for both hopper insertion and menu
     shift-click (menu slots honour `isItemValid`).
   - Test: push a stick (blacklisted, but valued) and an unvalued junk item at the pool; assert
     neither is accepted and both back up upstream.
2. **No item loss or duplication on routing** — an item leaves the pool only after its insertion
   into a winder is committed, and only by the exact accepted count.
   - Guarded by: insert into the winder's `IItemHandler` first, decrement the pool by
     `accepted = requested - remainder` (drop-before-stage inverted: stage the destination, then
     drop from source); never `extract-then-insert`.
   - Test: target a winder whose input slot fills (a second hopper races it) between candidate
     selection and insert; assert the un-inserted remainder stays in the pool and total item
     count across pool+winder is conserved.
3. **Buffered state survives unload; only topology is transient** — pool contents and every
   per-tier cursor persist across save/chunk-unload/restart; the reachable-winder set is never
   persisted and is rebuilt on load.
   - Guarded by: pool `ItemStackHandler` and cursor array in `writeData`/`readData`; topology
     held only in memory.
   - Test: fill the pool, set a cursor mid-cycle, save+reload; assert pool and cursors restore
     and routing resumes without a lost or duplicated item.
4. **A full pool backs up, never voids** — when the pool cannot accept more, insertion fails
   cleanly and upstream stalls; no item is ever dropped or destroyed to make room.
   - Guarded by: `insertItem` returns the un-accepted remainder; no overflow/void path exists.
   - Test: fill all nine slots with an item that has no reachable winder; assert further hopper
     insertion is refused (remainder returned) and nothing is destroyed.

### Router (per-tick placement)
1. **Route only to an exact-tier, non-stalled winder** — an item is inserted only into a winder
   whose spool tier equals the item's material tier AND which would actually consume it (input
   slot empty or same-item-with-space, and spool has room for the full yield).
   - Guarded by: mirror the winder's own acceptance gate (`WinderBlockEntity.tick`
     tier + input-slot + `SpoolItem.getFu + yield <= capacity`) before inserting.
   - Test: offer a T3 item with the only T3 winder's spool full; assert the item is held, not
     inserted into a winder that would stall on it.
2. **Cached positions are re-validated live at insert time** — a winder position from the cached
   topology is used only after confirming, that tick, that it is still loaded, still a
   `WinderBlockEntity`, and still the tier the router thinks it is.
   - Guarded by: `isRemoved` check + `instanceof WinderBlockEntity` + live re-read of the spool
     tier at use (never trust the cached snapshot's contents — mirrors the cable's
     positions-only cache contract).
   - Test: cache a T3 winder, then break it / swap its spool to T5 before the routing tick;
     assert no insert into the removed block and no T3 item routed to the now-T5 winder.
3. **Bounded work per tick** — at most N (config, default 4) insertions happen per tick, with no
   unbounded scan of the network on the hot path.
   - Guarded by: a hard per-tick placement counter; the expensive flood stays in the cable's
     throttled cache, not the router loop.
   - Test: connect many winders and a full pool; assert no more than N items move per tick and
     tick time stays flat as the network grows.
4. **Per-slot contention is atomic and non-destructive** — concurrent routers or hoppers
   targeting the same winder input slot can never overfill it or lose an item; each writer
   commits exactly what the slot accepted.
   - Guarded by: single source of truth is the winder's input `ItemStackHandler`; every insert
     honours the returned remainder, so a losing writer simply retains its item.
   - Test: two sorters route the same item type to one winder in the same tick; assert the slot
     holds a legal stack and the loser's item remains in its pool.

### Round-robin cursor
1. **The cursor advances exactly once per successful placement** — a routed item moves the
   cursor for its tier by one; an attempt that places nothing leaves the cursor untouched.
   - Guarded by: advance only on a committed insert, inside the success branch.
   - Test: with two T3 winders, route four T3 items; assert a 2/2 split, and assert a tick that
     places nothing (all candidates stalled) does not advance the cursor.
2. **Stall-skip never burns a winder's turn** — when the cursor's next candidate is full or
   occupied, it is skipped within the same attempt and the next candidate tried, without that
   full winder consuming a turn.
   - Guarded by: iterate candidates from the cursor, skipping stalled ones, advancing the cursor
     only past the winder that actually took the item.
   - Test: three T3 winders, the cursor's next one full; assert the item lands in the following
     winder and the full one does not gain or waste a turn.
3. **The cursor is topology-change-safe** — adding or removing a winder can never point the
   cursor out of range or make it systematically skip a winder.
   - Guarded by: store a monotonic per-tier counter and take it `mod (current candidate count)`
     at use time; never a persisted absolute index into a list whose length changes.
   - Test: run a cycle, remove one winder of that tier, continue; assert no index-out-of-range
     and that surviving winders still receive an even share over time.

### Topology view (delegated to the cable capability)
1. **The sorter owns no persistent topology** — reachable winders come from the cable's
   throttled flood (or direct adjacency), unioned across the sorter's six faces each use; the
   sorter never maintains its own long-lived network cache.
   - Guarded by: query the routing capability per face; the flood and its ~5s throttle live in
     `MC3DCableBlockEntity`, matching `collectSources`.
   - Test: wire a winder onto the network; assert it becomes a routing target within the cable's
     refresh window without the sorter implementing its own BFS.
2. **A winder reachable by multiple paths is counted once** — a winder the sorter can reach by
   two cable routes (or cable + direct touch) appears once in the candidate set, so it is never
   double-served against its single input slot in one tick.
   - Guarded by: identity dedup of the collected winders (the `IdentityHashMap`-backed set idiom
     `PrinterBlockEntity.reachableSources` already uses).
   - Test: build a loop so one winder is reachable two ways; assert it appears once and receives
     at most its slot's worth per tick.
3. **Direct-touch and cable-reach are the same code path** — a winder adjacent to the sorter and
   a winder across cable are discovered and validated identically, so an unpowered/cable-less
   build behaves like a networked one.
   - Guarded by: both answer the same routing capability; face query unions adjacent BEs and
     cable-forwarded sets without a special case.
   - Test: place winders directly around a sorter with no cable; assert routing works exactly as
     the cabled case.

**Decided during derivation (2026-07-20):** cursor advances only on successful placement,
stalled candidates skipped without cost (Cursor #1/#2); routed winding stays **uncredited** to
any player's advancement NBT, matching hopper-fed winders today (no attribution machinery in v1);
the pool is **insert-only from external faces** — a one-way funnel, never externally extractable,
so nothing a player funnels in can be pulled back out and unexpectedly destroyed.

---

## Implementation checklist

Follows the Blueprint Repository as the newest-machine template.

1. `machine/sorter/SorterBlock`, `SorterBlockEntity`, `SorterMenu`; `client/SorterScreen`
2. Registries: `ModBlocks` (`machineProperties()`), `ModItems`, `ModBlockEntities`,
   `ModMenuTypes`, screen binding in `ClientSetup`
3. **`data/minecraft/tags/block/mineable/pickaxe.json`** immediately after `ModBlocks` —
   `machineProperties()` sets `requiresCorrectToolForDrops`, and
   `LaunchContentGameTests.everyToolGatedBlockIsPickaxeMineable` will fail CI without it. This
   step has been missed three times
4. `data/mc3dprint/loot_table/blocks/<id>.json`, `data/mc3dprint/recipe/<id>.json`
5. New: routing capability in `ModCapabilities`; `collectWinders` on `WinderBlockEntity` and
   `MC3DCableBlockEntity`; `cable_connectable` tag + `canConnectTo` clause
6. Textures via `tools/`, then `gen_item_model_defs.py`, then `gen_style_packs.py`; blockstate
   and models hand-written
7. `lang/en_us.json`, `ModCreativeTabs`
8. `SorterGameTests` — routing by tier, blacklist refusal, round-robin distribution, stalled-winder
   skip, cable-reach vs direct-touch
9. Patchouli entry under `patchouli_books/guide/en_us/entries/machines/`, then
   `site/src/content/guide/<slug>.md` (category `Machines`, unique `order`)
10. Cascade to `legacy/1.20.1` per the standing rule

## Open

- Block name (working title "Filament Sorter")
- Comparator output on pool fullness, as the Filament Rack has
- No facing property; discovery is omnidirectional
