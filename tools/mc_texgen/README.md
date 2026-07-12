# mc-texgen

A generic, config-driven Minecraft texture pipeline. It takes a mod's default
textures (plus optional semantic hints) and produces:

1. **Hi-res upscaled packs** using pixel-art scalers (EPX/Scale2x, Scale3x) that
   sharpen edges instead of blurring them.
2. **labPBR 1.3 companion maps** (`_n` normal, `_s` specular) so Iris/Oculus
   shaders render bump, reflection and emissive glow on the mod's blocks.

Pure Python + PIL, no third-party deps, fully deterministic (same input bytes
always give the same output bytes: no wall-clock, no unseeded RNG). Incubated in
MC3DPrint but written to lift into any mod repo by writing one config file.

## Stage architecture

Each stage is a small module you can test or swap in isolation:

```
ingest  ->  masks  ->  { upscale , pbr , detail }  ->  assemble
```

- **`ingest`** walks `<root>/<ns>/textures/<kind>` into `TextureRecord`s and
  exposes per-frame iteration for animation strips (square frames stacked
  vertically, sibling `.png.mcmeta`).
- **`masks`** is the secret weapon: one explainable HSV threshold ladder
  classifies every pixel into `GLOW / TIER / METAL / FRAME / BODY / TRANSPARENT`.
  Everything downstream keys off the class, not raw colour. All thresholds live
  in the config's `semantics` block, with an optional per-texture override map.
- **`upscale`** implements EPX/Scale2x and Scale3x (RGBA, edge-clamped). The
  `upscale(img, factor)` dispatcher composes them (2, 4, 8 via Scale2x; 3, 6 via
  Scale3x; other factors fall back to NEAREST).
- **`pbr`** builds the labPBR `_n` / `_s` maps from the base texture + its mask.
- **`detail`** (opt-in) synthesises resolution-aware detail: deterministic
  brushed-metal micro-noise on `METAL`, seam re-inking on `FRAME`.
- **`assemble`** writes the resource pack: dual-era `pack.mcmeta`, `pack.png`,
  the texture tree, animation `.mcmeta` carried through.

## labPBR 1.3 field layout

Getting one channel wrong makes a shader render nonsense, so the exact layout
(implemented in `pbr.py`) is:

`_n` (normal) RGBA
- **R** = tangent-space normal X, `(x*0.5+0.5)*255`
- **G** = tangent-space normal Y (Z is reconstructed by the shader from X,Y)
- **B** = ambient occlusion, 255 = no occlusion
- **A** = height for parallax, 255 = surface, 0 = deepest

`_s` (specular) RGBA
- **R** = perceptual smoothness (per mask class)
- **G** = F0/reflectance: 0-229 linear dielectric, 230-254 hardcoded metal IDs
  (`METAL` uses 230 = iron)
- **B** = 0-64 porosity (non-metal), 65-255 subsurface
- **A** = emission. **THE gotcha: 255 = OFF** (backwards-compat), only 0-254 is a
  real emission strength. Every non-glow pixel MUST be 255, never 0.

## Running each phase

```bash
# Phase 0: labPBR _n/_s add-on for the EXISTING resolution (small, shippable).
python3 -m tools.mc_texgen --config mc3dprint --phase pbr

# Phase 1: an upscaled pack (writes to the config's out_dir by default).
python3 -m tools.mc_texgen --config mc3dprint --phase upscale --scale 4

# Both: an upscaled pack WITH matching _n/_s recomputed at the new resolution.
python3 -m tools.mc_texgen --config mc3dprint --phase both --scale 4 --pbr
```

Tests: `python3 tools/test_mc_texgen.py` (plain asserts, no pytest).

## Reusing in another repo: write a config

The whole package is repo-agnostic. To use it in another mod:

1. Copy `configs/mc3dprint.py` to `configs/<yourmod>.py` (or keep a standalone
   `.mctg.py` anywhere and pass its path to `--config`).
2. Repoint `source_root` and `namespace` at your assets, set `kinds` /
   `pbr_kinds` / `scales`.
3. Retune the `semantics` thresholds against your palette. The fastest way is to
   run the `pbr` phase and open `docs/texgen-previews/masks_blocks.png`: it is a
   colour-coded overlay of the classification, so you can see at a glance whether
   glow/metal/frame are landing where you expect and nudge the thresholds.

No code changes required; the pipeline reads everything from the config.
