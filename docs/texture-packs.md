# Alternate Texture Styles (Resource Packs)

**Status: PLANNED, not implemented.** Decisions aligned 2026-07-11. Implementation is gated
behind the in-world soak pass; nothing below exists in the tree yet.

## What ships

Two optional **built-in resource packs** inside every MC3DPrint jar (all seven NeoForge nodes
plus Forge 1.20.1), appearing in the vanilla Options → Resource Packs screen. The release
pipeline also emits each style as a **standalone universal zip** attached to every GitHub
Release.

| Style | Pack folder | Display name |
|---|---|---|
| Blueprint Mode | `src/main/resources/resourcepacks/blueprint_mode/` | MC3DPrint: Blueprint Mode |
| Dark Mode | `src/main/resources/resourcepacks/dark_mode/` | MC3DPrint: Dark Mode |

## Locked decisions

1. **Scope: mod assets only.** The packs contain only `assets/mc3dprint/...` (32 block + 31
   item textures + 4 GUI textures). No vanilla files, so the packs are inert next to any other
   pack and stack cleanly.
2. **Distribution: built-in AND standalone.** Built-in packs registered from the jar (one
   download, zero player-side version management), plus the same folders zipped by the release
   pipeline as separate downloadable packs. CurseForge/Modrinth resource-pack project pages are
   a later, manual step.
3. **Launch styles: Blueprint Mode + Dark Mode.** Cassette Futurism was rejected (3D printers
   are anachronistic to a CRT-era aesthetic). Synthwave and Workshop Grunge are candidate fast
   follows on the same system.
4. **Full GUI reskins in both styles.** Slot/widget geometry is frozen; styles recolor, never
   move coordinates (the `gen_printer_gui.py` ↔ `PrinterScreen`/`PrinterMenu` lockstep rule).
5. **Blueprint Mode goes full schematic.** Flat blueprint-paper faces, not a tinted recolor.
6. **Default off, no auto-enable.** No boot-time pack injection; discovery happens through the
   guides and release notes, with explicit enable instructions (below).
7. **Universal standalone zips.** One zip per style covering 1.20.1 through 26.2 via a
   dual-era manifest (content is texture-only and identical on every target).

## Style invariants (apply to every current and future style)

1. **Silhouettes survive.** A spool reads as a spool, a disc as a disc, in every style.
2. **The tier hue map survives.** T1 steel, T2 blue, T3 teal, T4 green, T5 gold, T6 orange,
   T7 violet, T8 magenta stay distinguishable at a glance; styles may adjust saturation or
   brightness for contrast against their field, never remap hues.
3. **GUI geometry is frozen.** Reskins recolor pixels inside the existing layout only.
4. **Code-drawn GUI text is light** (`PrinterScreen` LABEL `0xFFC0C0C8`, cyan accent, warm-red
   warn) and a resource pack cannot change it. Every style keeps GUI backgrounds dark-to-mid
   tone wherever text renders.
5. **Animated textures** (`extrudium_ore`, `extrudium_crystal`) keep their frame count and
   timing; only the palette shifts.

## Art specs

### Blueprint Mode

The machine replaced by its own schematic. Blueprint-blue field (cyanotype family, roughly
`#1A3E6E` base with a lighter `#2B5A9E` panel tone), 1px white technical line-work: silhouette
edges, panel seams, dimension ticks, subtle grid on large faces. Flat by design; no bevel/AO
shading. Tier accents render as colored line-work (saturation bumped to read against the blue
field). GUIs: blueprint field with white line frames; slot wells slightly darker; text areas
stay in the dark-mid band per invariant 4. Thematic flagship: this style IS the mod's identity.

### Dark Mode

Matte near-black chassis: BODY ramp compressed and darkened (roughly `#2A2D31` down to
`#101214`), FRAME nearly black. The signature cyan glow stays (slightly thinned), tier accents
stay saturated so information pops against the dark body. GUIs: current console layout with
darker plates and dimmed line details, same geometry.

## Resource pack mechanics (condensed reference)

- A pack is a zip/folder with `pack.mcmeta`, optional `pack.png`, and an `assets/<namespace>/`
  tree. Multiple packs stack; higher in the Selected list wins. All mod jar assets sit at the
  bottom of the stack ("Mod resources"), so any pack carrying `assets/mc3dprint/...` overrides
  the mod's textures with no code.
- Resource `pack_format` per shipped target: 1.20.1 = 15, 1.21.1 = 34, 1.21.8 = 64,
  1.21.9/1.21.10 = 69, 1.21.11 = 75, 26.1 = 84, 26.2 = 88
  (source: minecraft.wiki/w/Pack_format).
- Manifest eras: 1.20.1 reads only `pack_format`; 1.20.2 through 1.21.8 also honor
  `supported_formats`; 1.21.9+ use `min_format`/`max_format` (and formats gained minor
  versions, e.g. 69.0). One file can carry all three; older clients ignore unknown fields.
- A format mismatch shows a red "made for an older/newer version" warning but is loadable
  behind a confirm; it is advisory, not blocking.
- Overlays (`overlays` block, 1.20.2+) allow per-version override dirs inside one zip. Not
  needed here (our content is identical across targets), but it is the escape hatch if a
  future style ever needs version-divergent files. Known bug: NeoForge 1.21.9/1.21.10
  built-in packs ignore overlays (NeoForge issue #2724, fixed for 1.21.11); irrelevant while
  we avoid overlays.

### The universal `pack.mcmeta` (one per style, shared by built-in and standalone zip)

```json
{
  "pack": {
    "pack_format": 15,
    "supported_formats": { "min_inclusive": 15, "max_inclusive": 88 },
    "min_format": 15,
    "max_format": 88,
    "description": "MC3DPrint: Blueprint Mode"
  }
}
```

`max_*` values are bumped when a new game version node is added; the release smoke check
(below) fails the build if a shipped node's format falls outside the declared range.

## Pipeline design

### Generation (`tools/`)

`tex_common.py` grows a **StyleProfile**: palette ramps (BODY/FRAME/GLOW/TIER/accents) plus
surface-treatment hooks (shade/bevel pass for the default and Dark Mode; a line-art renderer
for Blueprint Mode). Each generator runs once per style; the default style writes
`assets/mc3dprint/` as today, non-default styles write
`src/main/resources/resourcepacks/<style>/assets/mc3dprint/`. Outputs are committed
(reproducible generation, same convention as the existing textures). Each pack folder carries
its `pack.mcmeta` and a generated `pack.png`.

### Registration (per loader)

Client-only, on the mod event bus, gated to `PackType.CLIENT_RESOURCES`, one call per style:
optional (`alwaysActive = false`), `PackSource.BUILT_IN`, `Pack.Position.TOP`.

- **NeoForge 1.21.1 through 1.21.11:** `AddPackFindersEvent#addPackFinders(ResourceLocation,
  PackType, Component, PackSource, boolean, Pack.Position)` with the in-jar path
  `mc3dprint:resourcepacks/<style>`.
- **NeoForge 26.x:** same helper; `ResourceLocation` is renamed `Identifier` (Stonecutter
  guard on the existing `>=26` seam).
- **Forge 1.20.1 (`legacy/1.20.1` branch):** no helper; manual
  `Pack.readMetaAndCreate(id, title, false, id -> new PathPackResources(id,
  modFile.findResource("resourcepacks/<style>"), false), PackType.CLIENT_RESOURCES,
  Pack.Position.TOP, PackSource.BUILT_IN)` inside `event.addRepositorySource(...)` (the
  Create/Aether 1.20.1 pattern). Pack folders copy to the legacy branch verbatim.

`Pack.readMetaAndCreate` returns null if `pack.mcmeta` is missing or unreadable and the pack
silently never appears, so registration logs a warning on null.

### Release pipeline

`scripts/build-all.sh` gains a packaging step: zip each `resourcepacks/<style>/` folder into
`dist/mc3dprint-style-<style>-<ver>.zip`, plus a smoke check that parses each style's
`pack.mcmeta` and asserts the declared format range covers every entry in a node → format
table (extended alongside `NEOFORGE_NODES` when a new node lands).
`.github/workflows/release.yml` attaches the zips to the GitHub Release with the jars.

## Enabling a style (player instructions, source for both doc surfaces)

Built-in (no download):
1. Install MC3DPrint and launch the game.
2. Options → Resource Packs.
3. Find "MC3DPrint: Blueprint Mode" or "MC3DPrint: Dark Mode" in the left (Available) column.
4. Hover it and click the arrow to move it right (Selected), then click Done. The game
   reloads resources and the new look applies immediately. Move it back left to return to the
   default art.

Standalone zip (for players who prefer managed packs):
1. Download the style zip from the GitHub Release page.
2. Drop it into `.minecraft/resourcepacks/` (do not unzip).
3. Enable it in Options → Resource Packs as above.

Both routes end up identical in-game; use one, not both.

### Doc surfaces to update at implementation time

Per the two-surface rule: a "Styles" entry in the Patchouli guide and a matching section in
the website guide plus an FAQ entry ("Can I change how the machines look?"), each carrying the
enable instructions above. Release notes mention the packs once at launch.

## Future styles

Candidates on the same StyleProfile system: **Synthwave** (dark chassis, magenta/violet neon,
mostly a GLOW/TIER ramp swap) and **Workshop Grunge** (worn oil-stained metal, amber glow;
needs noise/wear helpers). Rejected: Cassette Futurism (anachronistic for a 3D-printing mod).
