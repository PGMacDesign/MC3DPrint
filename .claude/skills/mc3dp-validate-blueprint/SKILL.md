---
name: mc3dp-validate-blueprint
description: >-
  Fact-check MC3DPrint curated blueprints by inspecting their ACTUAL generated output.
  Use this whenever the user wants to verify/validate/check/fact-check a blueprint or the
  curated set — "is this build right?", "check the blueprints for defects", "validate the
  curated set", "did the farm actually get crops?", "why does the windmill look wrong" —
  or automatically right after mc3dp-create-blueprint edits a build, before committing.
  Runs the BlueprintDumpTest tool to render each .blueprint as an ASCII layer map, checks
  it against the spec and the build conventions, reports defects, and FIXES them in
  CuratedBlueprintGenerator.java, then regenerates to confirm.
---

# Validate Blueprint → dump, fact-check, fix

A blueprint can compile and still print wrong (open roof, floating lantern, sail-less
windmill, door facing the wrong way). The only reliable check is to look at the *actual
generated volume*. The dump tool decodes each `.blueprint` into a human-readable ASCII
layer map so you can verify it layer by layer without launching the game.

## Workflow

1. **Dump the blueprints:**
   ```bash
   ./gradlew test --tests '*BlueprintDumpTest*' -DdumpBlueprints=true --rerun-tasks
   ```
   Writes `build/blueprint-dumps/<name>.txt` for every shipped build. (Generate first
   with `mc3dp-create-blueprint` if a build's source changed but its file is stale.)

2. **Read each dump under review.** Format:
   - `dims X(width)/Y(height)/Z(depth)` and a total block count.
   - **Palette legend:** symbol → exact block id+state (so you can read a door's `facing`,
     a stair's orientation, whether glass is present, whether the rich signature block is
     in the palette at all).
   - **One grid per Y layer**, bottom (y=0) up. Rows = z (0..depth-1, going south), cols =
     x (0..width-1, going east). `.` = air; each other cell is a palette-index symbol.
   - Read bottom-up like building it: foundation → walls → roof. A roof layer with `.`
     where the silhouette should close is an open roof; a block with `.` directly below it
     and no neighbor support is floating.

3. **Run the checklist** against each build's spec (`docs/blueprint-specs.md` §3) and the
   §2 conventions:
   - **Roof/gables close** — top layers seal the silhouette; gable triangles filled; domes
     are stacked *filled discs* (a ring stack leaves apex holes).
   - **No floating blocks** — every non-air cell is supported, except a deliberate hanging
     lantern *under a chain that reaches a solid block above*. Scan for a cell with air
     below and no adjacent support.
   - **No holes** in solid floors/walls (only windows/doors are gaps); e.g. a second-story
     floor must be complete.
   - **Doors face in** — legend facing matches the wall: north wall (z=0)→`south`,
     south (z=max)→`north`, west (x=0)→`east`, east (x=max)→`west`.
   - **Windows glazed** — panes/glass present where the spec places windows, not bare holes.
   - **Lanterns backed** — a chain/solid/fence in the cell directly above a `hanging=true`
     lantern.
   - **Signature feature present & correct** — farm has farmland + a water source + age-7
     crops; windmill has sails radiating from the hub (blades in the outer/upper layers,
     not just a tower); well is open over visible water; bridge has a walkable stair ramp
     (not a 1-block ledge); battlements sit flush on the wall top (no y-gap); a
     high-material build actually contains its signature block (copper / iron_block /
     redstone / emerald_block / diamond_block / beacon) in the palette.
   - **Footprint sane** — fills a believable silhouette within the dims, not collapsed to a
     sliver or mostly empty.

4. **Fix defects in the generator** `CuratedBlueprintGenerator.java`. Prefer fixing the
   shared **helper** when the defect is systemic (e.g. every gabled build has open ends →
   fix `gableEndFill`, not each call site). Keep the code compiling and the block ids
   valid 1.20.1.

5. **Regenerate and re-dump** the changed builds (`mc3dp-create-blueprint`'s generate
   command, then step 1 again) and re-run the checklist to confirm the fix — don't assume
   a source edit produced the intended volume.

6. **Report** a concise per-build verdict (PASS, or the defect + the fix). When clean, ship:
   `./gradlew build` → copy the jar to the Prism mods folder → commit & push.

## Reading a dump — worked example

```
well  "Village Well"   dims X=5 Y=6 Z=5
Palette: 0=cobblestone  1=water[level=0]  2=cobblestone_wall  3=oak_fence
         4=cobblestone_slab[type=top]  5=chain[axis=y]  6=lantern[hanging=true]
y=0   00000 / 01110 / 01110 / 01110 / 00000   → cobble ring, 3×3 water center ✓
y=4   3...3 / ..... / ..5.. / ..... / 3...3    → chain (5) at center, fence posts (3) at corners
y=5   44444 (×5)                               → slab canopy above the chain ✓
```
The lantern (`6`, on y=3) hangs from the chain (`5`, y=4) which reaches the slab canopy
(`4`, y=5) — properly backed, open sides, water visible. That's a PASS.

A FAIL looks like a lantern symbol with `.` in the cell directly above it across every
layer (floating), or a roof layer that's mostly `.` where the build should close.
