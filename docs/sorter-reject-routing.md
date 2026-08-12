# Filament Tier Item Sorter: reject routing

Implementation plan for [#68](https://github.com/PGMacDesign/MC3DPrint/issues/68).

## The gap

The sorter refuses un-windable items **at insertion**, so they never enter the block. That
pushes the problem upstream: a mixed stream cannot be pointed at the sorter, and the source
chest slowly fills with items it will not take. For a block whose job is sorting, "pre-sort
before you get here" is a poor answer.

## Approach: push to an adjacent inventory, add no slots

When an un-windable item arrives and an adjacent non-mod inventory would accept it, take the
item and push it there. When there is no such inventory, refuse at the door exactly as today.

The block gains **no new slots and no GUI**. That is the deliberate difference from a reject
buffer carved into the block itself: three visible buffer slots would need menu changes, screen
changes, a regenerated GUI texture, a third range in `quickMoveStack`, and a new NBT key, none
of which move the user any closer to "get the junk out of my sorter".

Consequences of adding no slots:

- `SorterMenu` and `SorterScreen` are untouched. The GUI already spends its three player
  inventory rows as undrawn `HiddenSlot`s to buy readout lines; there is no room for more, and
  now none is needed.
- The external capability handler stays 9 slots and stays insert-only, so the one-way funnel
  holds literally rather than in spirit.
- Save data is unchanged. The pool already persists, and nothing else needs to.
- With no adjacent inventory the behaviour is byte-for-byte what it is today, so no existing
  build changes behaviour on update.

### Push on the tick, not inside the insert call

The obvious implementation is to push to the chest inside `insertItem`. Do not. Two problems:

1. **Simulation.** Hoppers and pipes call `insertItem` with `simulate = true` to plan a move.
   A push performed during a simulated insert duplicates the item.
2. **Reentrancy.** If the neighbour is another sorter, its `insertItem` can push back, and two
   sorters facing each other recurse until the stack overflows and the server dies.

Both are avoidable with a re-entrancy flag and careful simulate handling, but neither has to
exist. Split the decision from the action instead:

- **At the door** (`isItemValid`), answer a read-only question: would a dump target take this?
  Safe in any calling context, correct under simulation, no cross-block writes.
- **On the tick** (`serverTick`), perform the actual push. This is where the block already
  mutates neighbours when it feeds winders.

Junk therefore occupies a pool slot between arriving and being pushed, normally for a single
tick.

### Choosing a dump target

Scan the six faces for an item handler via `TransferCompat.findItems(level, pos, side)`, which
is already version-guarded for the 1.21.9+ handler change. Take the first that accepts the
stack. No facing property, no orientation state, no configuration.

Two exclusions matter:

- **The mod's own machines are never dump targets.** `WinderBlockEntity`'s input slot accepts
  any item (`isItemValid` only filters the spool slot), so a sorter beside a winder, which is
  the normal layout, would otherwise push bedrock straight into it and jam it. Exclude by block
  entity type: printer, casing, converter, clock generator, redstone clock, rack, cable,
  repository, sorter, winder.
- **The MC3D Cable needs no special case** beyond that exclusion. It is registered for energy,
  filament source and winder routing only, never for an item capability, so it can never be
  selected and never competes for a face.

### Known caveat: pipe loops

A pipe configured to both pull from the dump target and insert into the sorter will ping-pong
junk forever. This is not guarded against, for the same reason the direction setting was
dropped: `insertItem` carries no direction, so preventing it means recording an arrival face
per stack and persisting it, and that only closes the tight case (out one face and back in
another loops identically). The loop is benign: nothing is voided, the sorter draws no power,
it is self-limiting once the target fills, and routable items keep moving throughout. Document
it on both doc surfaces rather than carrying permanent state to half-prevent it.

## Invariants

### Sorter reject routing

1. **Never void** — every item the sorter accepts is either routed to a winder, pushed to a
   dump target, or still in the pool.
   - Guarded by: push into the target first, then shrink the pool by exactly the accepted
     count, mirroring `placeIntoRoundRobin`. Never extract-then-insert.
   - Test: fill the dump target to one item short of capacity, push two, assert
     pool count plus target count is conserved.

2. **Door honesty** — junk is accepted only when a dump target would take it; with no dump
   target the sorter refuses exactly as before.
   - Guarded by: `isItemValid` consults a dump-target probe cached per tick, defaulting to
     "no target" before the first tick has run.
   - Test: bare sorter refuses sticks and bedrock (the existing `refusesBlacklistedAndUnvalued`
     covers this and must pass unchanged); sorter with an adjacent chest accepts both.

3. **Simulate purity** — no `insertItem(..., simulate = true)` call mutates any block.
   - Guarded by: the door probe simulates against the target and writes nothing; the real push
     happens only in `serverTick`.
   - Test: simulate-insert junk, assert the chest and the pool are both unchanged.

4. **No self-feeding** — a dump target is never one of the mod's own machines.
   - Guarded by: block-entity-type exclusion set, checked before probing.
   - Test: sorter flanked only by a winder refuses junk, and the winder's input slot stays
     empty.

5. **Routing is never starved** — junk awaiting a push cannot stop routable items from moving.
   - Guarded by: the tick drains junk before routing, so junk holds pool slots for at most one
     tick while the target has room; once the target is full the door refuses further junk.
   - Test: fill eight pool slots with junk and one with cobblestone, tick once, assert the
     cobblestone reached its winder.

6. **Stranded items recover** — an item that entered the pool valid and later became unroutable
   is removed rather than held forever.
   - Guarded by: the tick's drain treats "in the pool but no longer acceptable" the same as
     newly arrived junk.
   - Test: insert cobblestone, block it at runtime via `ModItemTags.blockWinding`, tick, assert
     it lands in the dump target.

Invariant 6 closes an existing dead end rather than one this change introduces. `routeOneItem`
already re-validates the door on every pass and skips items that fail it, and the pool is not
externally extractable, so such an item is stuck until a player opens the GUI. The
`InsertOnlyHandler.setStackInSlot` javadoc records this. Once a push path exists, reusing it is
a few lines.

## Work

### `machine/sorter/SorterBlockEntity.java`

- Split `acceptable(stack)` into `routable(stack)` (the current predicate: has an FU value and
  is not winder-blacklisted) and an acceptance test that is `routable(stack) || dumpTargetHasRoom(stack)`.
- Add a six-face dump-target scan using `TransferCompat.findItems`, with the block-entity-type
  exclusion set. Refresh once per tick alongside `recountWinders`, and cache it. The door reads
  the cache rather than probing capabilities on every hopper attempt, which happens every tick
  per neighbour.
- Add `drainRejects()`, called at the top of `tick()` before routing: for each pool slot holding
  a non-routable stack, push into the dump target and shrink by the accepted count.
- No changes to `writeData` / `readData`, `pool()`, `getItemHandler`, or `InsertOnlyHandler`.

### `config/MC3DPrintConfig.java`

Optional. If added, one boolean under the existing `sorter` section next to
`SORTER_MAX_PER_TICK`, defaulting to enabled, so the behaviour can be turned off without
removing the block. Recommend including it: it is three lines and makes the one behavioural
change on update revertible.

### Tests

New gametests in `SorterGameTests`, one per invariant above. The existing
`refusesBlacklistedAndUnvalued` and `poolInsertOnlyExternally` must pass **unchanged**; if
either needs editing, the design has drifted.

### Documentation

Both surfaces, per the standing rule that they mirror each other:

- `src/main/resources/assets/mc3dprint/patchouli_books/guide/en_us/entries/machines/filament_item_sorter.json`
- `site/src/content/guide/filament-item-sorter.md`

Cover: attach a chest to any face to catch un-windable items, no chest means the old
refuse-at-the-door behaviour, the sorter's own machines are never used as the target, and the
pipe-loop caveat.

### Versions

Ships to all seven NeoForge nodes and to `legacy/1.20.1`.

`TransferCompat.findItems` already carries the 1.21.9+ guard, so the forward nodes should need
no new guarded regions. The legacy branch is a separate single-target codebase and needs the
Forge 1.20.1 capability form written directly rather than guarded.

Verify with `:1.21.1:runGameTestServer` as the oracle, `:NODE:test` plus a boot smoke on each
forward node, and the legacy build. CI covers the JUnit matrix and the 1.21.1 oracle on every
PR.

## Decisions

1. **The config toggle ships.** Three lines, and it makes the one behavioural change on update
   revertible without removing the block.
2. **The drain is unbounded and separate from the routing budget.** It is capped implicitly at
   nine pushes per tick, one per pool slot, each a single `insertItem` call, so there is nothing
   to protect against. Sharing `sorterMaxPerTick` with routing would let a flood of junk consume
   the routing budget, which is precisely what invariant 5 forbids.
