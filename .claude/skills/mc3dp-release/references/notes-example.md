# Worked example: the 1.1.0 release notes

This is the actual published body for **1.1.0 - Filament Tier Item Sorter**, kept as a
reference for tone and structure. Note how each `## New` entry leads with a bold sentence
and then explains the feature in plain language; how `## Fixed` spends words on the *why*
only where the story is interesting (the valuator perf win) and stays terse elsewhere; and
how `## Downloads` ends with the CurseForge scope note. No em dashes anywhere.

---

## New

**Filament Tier Item Sorter.** A logistics block that routes each item into the Filament Winder holding a spool of that item's material tier. Feed it by hand, hopper or pipe; it finds winders either by direct contact or across an MC3D Cable network. Round-robin across same-tier winders, skipping any that are full or busy, so one sorter can keep a whole bank of winders fed.

- Items are never voided. Anything it cannot route right now is held in the 9-slot pool, and when the pool fills your hoppers back up, which is the signal that a tier is stuck.
- Unvalued and winder-blacklisted items are refused at the door rather than swallowed.
- Runs unpowered. No RF cost, deliberately, because almost nothing else in the ecosystem charges for item logistics.
- The cable is used as topology only. Items never travel through a cable, they go straight from the pool into the winder's own input slot.

**Find any tier in JEI.** Every item with an FU value now shows `MC3DP: Tier-5 (50 FU)`, and typing `tier-5` in JEI's search box lists that whole tier. No prefix, no quotes, works for tiers 1 through 8. The tier is written as one word specifically so JEI's whitespace tokenizer can match it exactly.

**The guidebook finds you.** The Handbook is granted on your first printer craft or your first blueprint, instead of waiting to be crafted.

## Fixed

**Modded startup no longer stalls.** The recipe-derived FU valuator was a top-down memoized search that declined to cache depth-pruned misses, so it re-walked underivable subtrees once per item, at a cost that grew superlinearly with recipe-graph cycle density. On an 84-mod pack a single cold sweep of 15,707 items took about 90 seconds, which surfaced as JEI hanging at `registerRecipes`.

It is now a bottom-up relaxation that reaches a fixed point once. Measured on the same pack: **90.8s to 0.52s, about 175x**. Only 7 of 2,755 item values changed, each by 1 to 2 FU, all of them rounding artifacts the old search produced when it hit its resolve budget.

**"Not Printable" was lying.** An item your printer simply could not reach yet now reads `Requires a Tier N Printer` instead of claiming it was unprintable.

**Filament laundering closed.** `rftoolsbase:dimensionalshard` and the `rftoolsdim:dimensional_*` block family are winder-blacklisted, so they can no longer be wound into filament for far more than they cost.

## Build

- 26.2 NeoForge pin moved to `26.2.0.16-beta` to match what JEI requires there.

## Downloads

Every supported target is attached below:

| Loader | Minecraft |
|---|---|
| Forge | 1.20.1 |
| NeoForge | 1.21.1 · 1.21.8 · 1.21.9 · 1.21.10 · 1.21.11 · 26.1 · 26.2 |

CurseForge carries the soak-tested targets, **Forge 1.20.1** and **NeoForge 1.21.1**. The forward jars are attached here and are usable, but have not had a full in-world soak yet.
