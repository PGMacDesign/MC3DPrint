# MC3DPrint — Launch Checklist & Store Copy

## Pre-launch verification (human, in-game)

- [ ] `runClient` eyes-on pass: GUI layouts, gantry/frame render, textures, Patchouli book rendering, JEI category
- [ ] Multiplayer smoke test on a dedicated server (2 players: zone conflicts, remote terminal, disc trading)
- [ ] Compat instance: AE2 + Mekanism + Thermal + Create + WorldEdit installed — energy input, ME interface → Filament Converter, `/mc3dprint import` of a real Create schematic and WorldEdit `.schem`
- [ ] Balancing playtest: FU values, RF rates, print speeds (all in `mc3dprint-common.toml`)
- [ ] Survival progression walkthrough: craft chain T1 → T8 (T8 with DE installed)

## Release engineering

- [ ] Version `0.1.0` → bump per semver in `gradle.properties`
- [ ] `./gradlew build` artifact from `build/libs/mc3dprint-<version>.jar` (reobf jar)
- [ ] Tag release on GitHub, attach jar + changelog
- [ ] CurseForge + Modrinth project pages (copy below), game version 1.20.1, loader Forge, license MIT
- [ ] Creator outreach for signature blueprints (see design doc — easter-egg statues/builds in End loot)

## Store description draft

**MC3DPrint — WorldEdit for survival players.**

Scan any structure with the handheld Scanner, save it to a Blueprint Disc, and print it anywhere — block by block, bottom-up, with a working gantry and print head. No commands, no creative mode.

- **8 machine tiers**: desktop printers (T1–T4) to room-scale multiblock Fabricators (T5–T8, Draconic Evolution prestige tier)
- **Filament economy**: wind materials into FU on physical spools; symmetric costs, no dupes, tier-gated progression
- **RF powered**: works with every FE/RF mod; power loss pauses, never resets
- **Automation-friendly**: hopper-compatible from T1, Filament Converter keeps spools topped up from any inventory or ME/RS interface
- **Multiplayer-ready**: print zone conflict detection, built-in chunk loading, Remote Terminals
- **Interop**: import WorldEdit `.schem` and Create schematics, export your scans back to `.schem`
- **Find blueprints in the wild**: village and exploration loot ships printable structures
- JEI + Patchouli guidebook included

## Community

- [ ] Blueprint-sharing site plan (GitHub-backed repo + frontend) — own it from day one
- [ ] CurseForge page calls for community `.blueprint` submissions
