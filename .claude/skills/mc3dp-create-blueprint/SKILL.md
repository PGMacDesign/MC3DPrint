---
name: mc3dp-create-blueprint
description: >-
  Implement an MC3DPrint building spec as a parametric builder and generate its
  .blueprint file. Use this whenever the user wants to turn a building spec into an
  actual MC3DPrint blueprint — "create the blueprint", "implement this build", "add the
  windmill builder", "generate the .blueprint for X" — or right after mc3dp-find-buildings
  produces a spec. Adds a builder to CuratedBlueprintGenerator.java using the existing
  helper library, registers it in the generator map AND CuratedBlueprints.CURATED_NAMES,
  runs the gated generator to write data/mc3dprint/blueprints/<name>.blueprint, then hands
  off to mc3dp-validate-blueprint. Always use this instead of hand-writing blueprint NBT.
---

# Create Blueprint → parametric builder + generated file

Blueprints aren't hand-authored NBT — they're emitted by a procedural generator (the
source of truth). You add a builder method, register it, and run the generator. The
helper library does the geometry; your job is to compose it faithfully to the spec.

## Workflow

1. **Read the spec** (from `docs/blueprint-specs.md` §3, or the user). Note the
   footprint W×L×H, palette, and the block-by-block build.

2. **Read the generator** `src/test/java/com/pgmacdesign/mc3dprint/blueprint/CuratedBlueprintGenerator.java`
   to see the helper library, the palette constants, and the builder style. Reuse
   existing `BlueprintBlockState` constants; add new ones (`private static final
   BlueprintBlockState X = bs("minecraft:...");`) as needed.

3. **Write a builder** `private static Blueprint <name>()`.

   **CRITICAL axis mapping.** Specs write footprint as **W×L×H** (W=width=x, L=depth=z,
   H=height=y), but the builder constructor is `Blueprint.builder(displayName, sizeX,
   sizeY, sizeZ)` = **`builder(name, W, H, L)`**. So a `9×15×12` church is
   `Blueprint.builder("Church", 9, 12, 15)`. Every `b.set(x,y,z,...)` must satisfy
   `0≤x<W`, `0≤y<H`, `0≤z<L` — out-of-range writes throw at generation time.

4. **Apply the conventions** (the reason builds don't print broken — see
   `docs/blueprint-specs.md` §2 and §5):
   - `door2(b, x, y, z, wood, wallFace)` — derives facing from the wall (NORTH→south),
     so doors face *into* the building. Use it, not the raw `door(...)`.
   - `gableEndFill(...)` after `gableRoofX(...)` — closes the triangular gable ends.
   - `chainLantern(b, x, y, z, hangLen)` — a chain up to a backing block then a hanging
     lantern, so lights never float.
   - `window2(...)` — a pane (+ optional sill) so windows always include glass.
   - `crenellate(...)` sits flush on a wall top (no floating battlement).
   - No floating blocks; corner posts terminate at the wall plate (no 1-block nub).

5. **Register in BOTH places** (they must match 1:1, or install/audit breaks):
   - `generateCuratedBlueprints()` map: `builds.put("<name>", <name>());`
   - `CuratedBlueprints.CURATED_NAMES` (`src/main/java/.../blueprint/CuratedBlueprints.java`):
     add `"<name>"`.

6. **Generate the file:**
   ```bash
   ./gradlew test --tests '*CuratedBlueprintGenerator*' -DgenBlueprints=true --rerun-tasks
   ```
   Writes `src/main/resources/data/mc3dprint/blueprints/<name>.blueprint`. The
   `-DgenBlueprints=true` flag is required — without it the generator is
   `@EnabledIfSystemProperty`-skipped and writes nothing (a silent "BUILD SUCCESSFUL,
   0 files" trap). If you renamed/removed builds, delete orphaned `.blueprint` files
   whose names are no longer in `CURATED_NAMES`.

7. **Validate, then ship.** Run `mc3dp-validate-blueprint` (dump + fact-check) before
   trusting the build. When it's clean, the standard cycle is: `./gradlew build` →
   copy the jar over the one in the Prism mods folder → commit & push.

## Helper cheat-sheet

All take `Blueprint.Builder b` first. Coordinates are x=width(east), y=up, z=depth(south).

| Helper | Does |
|---|---|
| `floor(b,y,x0,z0,x1,z1,mat)` | fills a horizontal rectangle |
| `line(b,y,x0,z0,x1,z1,mat)` | a straight run between two points |
| `pillar(b,x,z,y0,y1,mat)` | a vertical column |
| `walls(b,x0,z0,x1,z1,y0,y1,mat)` | 4-face wall ring |
| `corners(b,x0,z0,x1,z1,y0,y1,mat)` | the 4 corner posts |
| `door2(b,x,y,z,wood,wallFace)` | door, facing derived from wall (use this) |
| `window2(b,x,y,z,pane,sillMat)` | glazed window (+ optional sill) |
| `gableRoofX(...)` + `gableEndFill(...)` | pitched roof along X, ends closed |
| `hipRoof / pyramidRoof / gambrelRoofX / flatRoof` | other roof shapes |
| `circleRing / disc / dome` | round towers, lighthouse, observatory caps |
| `crenellate(b,y,x0,z0,x1,z1,wall)` | battlement flush on a wall top |
| `stripedBand(...)` | alternating-material band (awnings, lighthouse stripes) |
| `chainLantern(b,x,y,z,hangLen)` | backed hanging lantern |
| `ramp(...)` | walkable stair ramp (bridges) |
| `timberFrame(...)` | plank wall + stripped-log stud grid |
| `copperPatina(level)` | returns the cut-copper variant for a patina gradient |
| `fenceRing(b,y,x0,z0,x1,z1,fence)` | a fence perimeter |

The authoritative signatures live in the generator — read them there. If a build needs
a shape no helper provides, add a new helper (pure composition over `b.set(...)`) rather
than inlining ad-hoc geometry, so the next build can reuse it.
