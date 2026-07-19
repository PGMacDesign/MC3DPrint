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

To be derived via the `derive-invariants` skill before implementation. This block is stateful
and multi-writer (buffered inventory, round-robin cursor, cached topology, concurrent insertion
from pipes and hands), so it needs them.

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
