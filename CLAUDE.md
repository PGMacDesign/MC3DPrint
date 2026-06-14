# MC3DPrint — Project Instructions

Forge **1.20.1** tech mod: "WorldEdit for survival." Scan a build → save to a Blueprint
Disc → print it anywhere with a tiered printer/fabricator, powered by RF + tiered **Filament
Units (FU)**. T1–T4 = single printer blocks; T5–T8 = N×N multiblock fabricators.

- **Stack:** Java 17, Forge 47.4.10, official Mojang mappings. mod id `mc3dprint`, MIT, solo (PGMacDesign).
- **Public repo** (`PGMacDesign/MC3DPrint`): no secrets/PII, original content only. `.env` is gitignored.

## Build · Test · Deploy

```bash
./gradlew build                 # full build + tests → build/libs/mc3dprint-<ver>.jar
./gradlew compileJava -q        # fast compile check
./gradlew runGameTestServer -q  # GameTests (gametest/) + JUnit (test/.../fu, blueprint)
```

Two rules that bite if skipped:

1. **Deploy = replace, never duplicate.** Copy the fresh jar over the existing one in the
   Prism mods folder — never leave two `mc3dprint-*.jar`:
   `~/Library/Application Support/PrismLauncher/instances/1.20.1/minecraft/mods/`
2. **After ANY FU/economy change, delete stale config.** The `fuValues` list does **not**
   merge new defaults into an existing toml. Delete both `run/config/mc3dprint-common.toml`
   (dev) and the Prism `config/mc3dprint-common.toml`, or your changes won't load.

## Architecture (`src/main/java/com/pgmacdesign/mc3dprint/`)

| Package | What lives there |
|---|---|
| `MC3DPrint.java` | Mod entry; registries + event-listener wiring (incl. compat hooks) |
| `fu/` | **FU economy core** — `FuValueRegistry` (values + tiers), `RecipeFuValuator`, `FuEvents` |
| `machine/` | Printer block entity, menu, upgrades; `machine/multiblock/` fabricator |
| `blueprint/` | `.blueprint` GZIP-NBT I/O, `CuratedBlueprints` install |
| `scanner/` | Structure Scanner (capture builds) |
| `integration/` | Soft-dep hooks: `ae2`, `thermal`, `tinkers`, `draconic`, `jei`, `patchouli` |
| `registry/`, `item/`, `client/`, `command/`, `config/`, `loot/`, `advancement/` | as named |

Textures/GUIs are generated, reproducibly, by `tools/*.py` (PIL). `gen_printer_gui.py`
coords MUST stay in lockstep with `client/PrinterScreen` + `machine/PrinterMenu`. Run
`gen_block_textures.py` before `gen_formed_textures.py`.

## FU economy invariants (don't break these)

- **Winding (item→FU) is 1:1 exact-tier. Spending (print) is down-only.** Printing is lossy
  by design, reaching exact 1:1 break-even only at the **max (4) Efficiency** modules;
  upgrades capped 4/type.
- **Itemless structural blocks print free** (`PrinterBlockEntity.isStructuralItemless()` —
  farmland/crops/water/torches: non-air block whose `asItem()==AIR`).
- **2-block / stateful placement** uses `UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE |
  UPDATE_SUPPRESS_DROPS` so beds/doors don't self-break and captured connections reproduce.
- **Abundance rule:** a farmable resource can't sit at a tier whose spool could print
  something rarer (why chorus=T4, manyullyn=T6). Some items are intentionally **unvalued**
  (strict mode → unprintable): dragon egg, wither skeleton skull, survival-unobtainables.
- Tier tests assert specific values (`gametest/`, `fu/`) — update them with any rebalance.

## Modded FU compat pattern

To value an optional mod's items, add `integration/<mod>/<Mod>Compat.java`: an
`onCommonSetup(FMLCommonSetupEvent)` that **returns early unless
`ModList.get().isLoaded("<id>")`**, then `event.enqueueWork(...)` →
`FuValueRegistry.registerApiItemValue(ResourceLocation, fu, tier)`. Wire it in the
`MC3DPrint` constructor. **No gradle dependency** — it's pure ResourceLocation strings, so
it's invisible (no crash/warn/config) when the mod is absent. Only value **custom-recipe
leaves** (Smeltery/Inscriber/etc.); standard crafting/smelting derives for free. Research +
exact values: `docs/rebalance/{ae2,thermal,tconstruct}.md`.

## Conventions

- **Update the in-game Patchouli guide** (`src/main/resources/.../patchouli_books/guide/`)
  whenever a player-facing feature changes. Patchouli is a soft/optional dep.
- **Blueprints:** `CuratedBlueprintGenerator` (gated JUnit) is the source of truth —
  regen with `./gradlew test --tests '*CuratedBlueprintGenerator*' -DgenBlueprints=true
  --rerun-tasks`; validate/dump ASCII layers with `*BlueprintDumpTest* -DdumpBlueprints=true`.
- **Git:** commit → push every change, direct to `main` (solo repo). **Never** add
  `Co-Authored-By: Claude` or "Generated with Claude Code" to commits/PRs.

## Where deeper context lives

`docs/ROADMAP.md` (state + next-up), `docs/rebalance/` (FU rebalance plan + per-mod
research), `docs/blueprint-specs.md`, `docs/popular-mods-1.20.1.md` (FU-synergy shortlist),
and the project memory (`active-roadmap`, `fu-economy`, `upgrade-system`,
`blueprint-pipeline`, `modded-fu-compat`, `winder-blacklist`, `multiblock-corner-blocks`).
