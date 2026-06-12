# MC3DPrint Visual Revamp — Shared Design Brief

Single source of truth for the overnight visual overhaul. Every implementation
agent (textures, GUI, animation) builds to THIS palette and these rules so the
result is one cohesive set. Synthesized from 4 research passes (real-printer
references, tech-mod conventions, MC 1.20.1 technical playbook, asset audit).

## North star
**Sleek desktop FDM 3D printer + cyan "magic" glow.** Blocks read instantly as
3D printers (machined light-grey body, dark metal frame/rails, a glowing cyan
hotend/nozzle, a colored filament spool dot). The GUI is a **dark tech-console**.
Unique vs other tech mods (not rusty-industrial), but familiar (vanilla-friendly
pixel art). Magic = the cyan emissive hotend/filament.

## Resolution
- **32×32** hero pieces: T1–T4 printers, T5–T8 fabricators (+active), filament
  winder, filament spools T1–T8, blueprint discs (blank+written), scanner.
- **16×16** secondary: printer casing, converter, remote terminal, simple/clock
  generator, creative energy source, printite ore+crystal, the 4 upgrade items,
  creative spool. (Casing/fabricator *_active variants follow their base res.)
- Models stay `cube_all` (blocks) / `item/generated` (items) — resolution is
  texture-only, no model changes. **Do NOT attempt custom block geometry** — it
  can't be visually verified headlessly; defer to a user runClient pass.

## Palette ramps (hex) — quantize finished art to the union of these
Machined light-grey body (sleek printer shell):
`#F4F6F8 #DCE1E6 #BCC4CC #9AA3AD #6E767F`
Dark metal frame / rails / GUI panel:
`#5A6068 #3C4148 #272B30 #15181C`
Hero magic glow — hotend/nozzle/active filament/print (CONSTANT cyan, all tiers):
`#FFFFFF(1px core) #BFE9FF #5CC8FF #1E7FCF(falloff)`
GUI console base (dark): field `#10141E`, panel `#1A1F2B`, bevel-light `#2C3342`,
bevel-dark `#0A0D14`, label text `#C0C0C8`, accent line/status `#3FE0C0`.

## Tier accent (chassis trim / indicator dot per tier — NOT the glow)
The hero glow is always cyan; TIER is shown by a small accent stripe/dot so tiers
are distinguishable: T1 `#8A94A0` steel · T2 `#4F9BE8` blue · T3 `#34C0C0` teal ·
T4 `#46C66B` green · T5 `#E0B43A` gold · T6 `#E87A3A` orange · T7 `#9B6BE8` violet ·
T8 `#E84FB0` draconic-magenta. Higher tiers also get slightly more detail (vents,
a second bevel, a brighter/larger hotend glow).

## Filament spool colors (the spool coil = its tier accent above)
Spool body coil uses the tier accent hue (3 shades: highlight/base/shadow arc);
dark hub hole dead-center; thin grey flange rim. Creative spool = magenta + sheen.

## Shading rules (the "clean machined tech" look — obey universally)
- **Single light, top-left.** Lit edges (lightest shade) top+left; shadow shades
  bottom+right. Never two light directions.
- **Tight ramps, flat fills.** 4–5 shades/material, flat between bevels — no
  airbrush gradients. 32px may add ONE extra step for rounded extruder/spool.
- **1px bevel + AO.** Light rim top-left, shadow rim bottom-right; a 1px darkest
  pixel only in inner corners/seams (fake AO). Never full black outline ("sticker").
- **Glow ≤10–15% of the texture**, additive, breaks the light rule on purpose
  (symmetric, white center fading radially) — that asymmetry = "emissive/magic."
  Cap brightest at the 1px white core; add a 1px softer halo. Idle = dim, active = bright.
- **Kill noise.** Every pixel earns its place; deliberate 1px lines (rails, feed,
  layer arcs), no random speckle. Dither only between adjacent ramp steps on large
  flat areas, sparingly.
- Budget by res: 16px = silhouette + 1 glow px + ≤2 seams. 32px = + LCD/vents/fan,
  spool layer arcs, bed thickness, nozzle cone. Redraw for 32px, don't upscale.

## Per-asset design (what each hero texture depicts)
- **Printer block face (T1–T4):** dark square frame; a light-grey extruder
  carriage on a horizontal rail ~⅓ down; a glowing cyan nozzle dot under it; a
  small tier-accent spool dot top-corner with a 1px feed line; a 2px light-grey
  bed slab lower third. Higher tier = more vents + brighter hotend.
- **Fabricator (T5–T8):** same printer language, heavier/denser frame, bigger
  tier-accent core + larger cyan hotend; *_active = hotend/core lit full, faint
  panel glow (regenerate via the formed-texture generator over the new bases).
- **Filament winder:** machined body with a horizontal spindle + a partial
  tier-less spool being wound, a small cyan feed glow.
- **Spool item:** filament-color donut, dark hub hole, 2–3 concentric layer arcs,
  top-left sheen, grey flange rim. Must read as a donut/reel (visible hole).
- **Blueprint disc:** a dark data disc; blank = empty face, written = a small
  cyan holographic schematic grid/structure glyph.
- **Scanner:** handheld scanner/wand, dark body + cyan scan lens/emitter.

## GUI — dark tech-console (printer.png + machine.png, 256² sheets)
- Charcoal panel (`#1A1F2B`) with a 1px top bevel-light / bottom bevel-dark; a
  thin `#3FE0C0` accent line along the panel top = "console" identity.
- Recessed dark slots (inset shadow) with a slightly lighter inset so item icons
  stay legible. Keep all existing slot/bar/button COORDINATES from PrinterScreen/
  WinderScreen (don't move slots — just retheme the sheet + label colors).
- Energy bar = red fill; **filament bar = cyan vertical fill** in a recessed well;
  progress arrow = cyan left-to-right with a 1px brighter shimmer at the fill edge.
- Labels `#C0C0C8` shadowless on dark; the STATUS readout lights `#3FE0C0` when
  PRINTING/READY (so the eye knows where to look). Add 2–3 tiny corner status LEDs
  (green=ready, amber=working) if space allows.
- Window pixel dimensions and slot positions are FIXED (PrinterScreen 176×200,
  WinderScreen 176×166, slots/bars/buttons per the asset audit) — retheme only.

## Print animation (PrinterRenderer revamp — "defined but magical")
Replace the wireframe `RenderType.lines()` poles with textured/emissive geometry:
- **Structural frame + rails:** thin TEXTURED `RenderType.solid()` boxes (dark
  metal), lit by world light — the "defined" jump from wireframe.
- **Extruder head:** a small solid machined box that physically travels the gantry
  tracking print progress, with **ease-in/ease-out** at direction changes and
  `partialTick` lerp for smooth motion.
- **Glowing filament strand + hotend:** thin emissive quads via `RenderType.eyes`
  (or entityTranslucentEmissive) at `LightTexture.FULL_BRIGHT`, cyan, brightest at
  the nozzle, cooling down-strand. A capped ring buffer (≤64) of laid segments
  trailing behind the head, fading oldest→dim = "lays glowing filament."
- **Idle:** slow breathing pulse on the hotend glow; near-still head.
- Keep spool exterior render but upgrade from pure lines to slim textured/emissive
  reels if cheap; keep the ghost preview as-is (works).
- Performance: grab each `VertexConsumer` once per RenderType per frame; implement
  `getRenderBoundingBox`; cap the segment buffer; balance push/popPose.

## Mod logo
Create `src/main/resources/logo.png` (a clean ~128×128 or 256×256 mark: a stylized
3D-printer + cyan filament glyph with "MC3DPrint" feel) and reference it via
`logoFile="logo.png"` in META-INF/mods.toml.

## Hard constraints for all agents
- Verify textures by RENDERING a scaled PIL preview composite and reading it back;
  iterate if it looks noisy/garish. (You can't see it in-world — the preview is
  your check.) Save generators under `tools/` so it's reproducible.
- All 58 GameTests must still pass; `./gradlew build` must compile. Deploy the jar
  to the Prism mods folder. Commit (conventional, NO Claude attribution) + push.
- Stay in your assigned lane (textures vs GUI vs animation) to avoid collisions.
