---
name: mc3dp-find-buildings
description: >-
  Research a recognizable Minecraft building and turn it into an implementation-ready
  MC3DPrint blueprint spec. Use this whenever the user wants a NEW curated/sample
  building for the MC3DPrint mod — e.g. "add a windmill blueprint", "find a good castle
  build for the mod", "we need more sample buildings", "research a lighthouse spec",
  "what builds should we add" — or describes a structure they want printable, even
  without saying "spec" or "blueprint". Produces a spec section in the
  docs/blueprint-specs.md format (footprint, tier, palette, block-by-block build) that
  the mc3dp-create-blueprint skill implements directly. Hand the output to that skill next.
---

# Find Buildings → MC3DPrint Blueprint Spec

Turn a building idea (the user's description, or a recognizable Minecraft build type
you research) into a precise, buildable spec. The spec is the contract the
`mc3dp-create-blueprint` skill turns into a `.blueprint`, so it must be unambiguous:
exact dimensions, exact block ids, and a layer-by-layer build.

**Designs are original/parametric.** This is a public repo — research canonical
*proportions and palettes* for a build type (a windmill has a round body + 4 radiating
sails; a church has a nave + steeple), but author your own geometry. Never download or
transcribe a third-party schematic.

## Workflow

1. **Pin the building.** Take the user's description, or research the build type for
   canonical proportions/materials (web search is fine for "what does a Minecraft
   lighthouse look like"). Note the defining feature that makes it read as *that*
   building — the thing a player would notice if it were missing.

2. **Pick the footprint tier.** A structure's W×L footprint must fit the printer's
   `maxFootprint` (see `src/main/java/com/pgmacdesign/mc3dprint/machine/MachineTier.java`):

   | Printer tier | Max footprint | Structures start at |
   |---|---|---|
   | T3 | 3×3 | smallest printable |
   | T4 | 5×5 | |
   | T5 | 9×9 | |
   | T6 | 15×15 | |
   | T7 | 23×23 | |
   | T8 | 33×33 | (impractical; cap at T7 unless asked) |

   Pick the smallest tier the footprint fits. T1/T2 print items only (no structures).

3. **Pick the material/disc tier** = the highest material tier of any block in the
   palette: cobble/dirt/wood = T1; copper/iron = T2; redstone = T3; emerald = T4;
   diamond/netherite = T5; beacon/nether-star = T7. To represent a *higher* material
   tier, concentrate the rich block in one signature feature (a copper dome, an
   iron-block forge core, a diamond vault) so the build still reads as common Minecraft,
   just trimmed with the rich material.

4. **Choose an exact vanilla 1.20.1 palette.** Use real `minecraft:` ids with block
   states (e.g. `minecraft:oak_stairs[facing=north,half=bottom]`). **Verify each id
   exists in 1.20.1** — `BlueprintBlockState.resolve()` silently drops ids the game
   lacks, so a wrong id = a missing block, no error. Common trap: `copper_bulb`,
   `copper_grate`, `crafter`, `tuff_bricks`, the trial/vault blocks are **1.21**, not
   1.20.1. When unsure, check the wiki.

5. **Read the conventions before writing.** Open `docs/blueprint-specs.md` and follow
   its **§2 Design conventions** (door facing by wall, windows always glazed, roofs
   close with filled gable ends, lanterns backed, functional fences/gates) and copy the
   **§3** entry format. These conventions are what keep builds from printing broken.

6. **Write the spec** using the template below and append it to `docs/blueprint-specs.md`
   (§3), or to a scratch file the user names. Then tell the user it's ready for
   `mc3dp-create-blueprint`.

## Spec template

ALWAYS produce this shape (mirrors docs/blueprint-specs.md §3):

```markdown
### `<snake_case_name>` — <Display Name>

- **Footprint / H:** `W×L×H` → **printer T<n>** (W×L fits T<n>'s maxFootprint)
- **Disc tier:** **T<n>** (driven by <richest block>)
- **Palette:** `minecraft:...`, `minecraft:...`, ... (exact 1.20.1 ids w/ states)
- **Build (layer by layer):**
  - **Foundation (y=0):** ...
  - **Walls (y=1..N):** ... + corner posts; **doors** on wall <face> facing into the build
  - **Windows (y=K):** `glass_pane` at (x,y,z), symmetric; never a bare hole
  - **Roof (y=..):** shape (gable/hip/pyramid/flat) + material; **fill gable ends**
  - **Interior / signature:** the defining feature (farm→farmland+water+age-7 crops;
    windmill→radiating sails; well→open over visible water; etc.)
- **Reads as:** one sentence — why this silhouette is recognizably a <building>.
```

## Notes

- Keep H within reach: a tall pitched roof over a wide span needs vertical room; if H is
  tight, cap the roof (hip/flat) rather than letting it clip the walls.
- If the user wants several builds, produce one spec block each and list them.
- The 23 shipped builds in `docs/blueprint-specs.md` are worked examples — match their
  level of detail.
