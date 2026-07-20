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
./gradlew runGameTestServer -q  # in-world GameTests (gametest/) only; JUnit runs under `build`
```

Two rules that bite if skipped:

1. **Deploy = replace, never duplicate.** Copy the fresh jar into your test instance's
   `mods/` folder, replacing any existing `mc3dprint-*.jar` — never leave two.
2. **fuValues is overrides-only (config no-wipe, since 0.11)**: the toml list merges OVER the
   built-in defaults in code (`FuValueRegistry.loadMerged`; `<id>=off` removes), so FU/economy
   changes apply WITHOUT deleting configs. One-time exception: a toml written by ≤0.10 carries
   the full copied default list, which now pins those values as overrides. Delete it once (dev
   `run/config/mc3dprint-common.toml` + the game instance's `config/`) to migrate.

## Architecture (`src/main/java/com/pgmacdesign/mc3dprint/`)

| Package | What lives there |
|---|---|
| `MC3DPrint.java` | Mod entry; registries + event-listener wiring (incl. compat hooks) |
| `fu/` | **FU economy core** — `FuValueRegistry` (values + tiers), `RelaxationFuValuator`, `FuEvents` |
| `machine/` | Printer block entity, menu, upgrades, **Resin slot + `machine/resin/`**; `machine/multiblock/` fabricator; `machine/repository/` Blueprint Repository; Filament Rack + MC3D Cable |
| `blueprint/` | `.blueprint` GZIP-NBT I/O, `CuratedBlueprints` install, `blueprint/repository/` saved-data |
| `scanner/` | Structure Scanner (capture builds) |
| `integration/` | Soft-dep hooks: `ae2`, `botania`, `create`, `draconic`, `enderio`, `immersiveengineering`, `mekanism`, `thermal`, `tinkers` + `jei`, `patchouli` |
| `network/` | The mod's `SimpleChannel` (repository GUI listing sync) |
| `registry/`, `item/`, `client/`, `command/`, `config/`, `loot/`, `advancement/` | as named |

Textures/GUIs are generated, reproducibly, by `tools/*.py` (PIL). `gen_printer_gui.py`
coords MUST stay in lockstep with `client/PrinterScreen` + `machine/PrinterMenu`. Run
`gen_block_textures.py` before `gen_formed_textures.py`.

## Website (`site/` + `worker/`)

Live at **mc3dprint.dev** (GitHub Pages, custom apex domain; DNS at Squarespace).
- `site/` — **Astro** site (landing, guide, gallery, submit, about) + the 3D **Blueprint
  Viewer** at `/viewer` (vanilla Three.js, served verbatim from `site/public/viewer/`).
  Build: `cd site && npm install --cache ./.npm-cache && npm run build` (global ~/.npm cache
  hits EACCES under sandbox → use `--cache`). Deploy is automatic on push to main
  (`.github/workflows/pages.yml` builds Astro + injects curated blueprints/manifest into
  `/viewer`). Block colors: `BlockColorDumpGameTest` → `site/public/viewer/data/block_colors.json`.
- `worker/` — **Cloudflare Worker** (`mc3dprint-submit.workers.dev`) behind the **Submit a
  Build** page: validates an uploaded `.blueprint` + opens a reviewable PR — no GitHub account
  needed. Deploy: `cd worker && npx wrangler deploy`. Secrets (`GITHUB_TOKEN`, `TURNSTILE_SECRET`)
  live in the **CF dashboard, NOT the repo**. Rate-limited + Turnstile-gated. Full detail:
  memory `github-blueprint-renderer`.

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
- **Resins** (`item/ResinItem`, `machine/resin/`): consumed-per-print blueprint modifiers,
  **official-blueprints-only** (`BlueprintDiscItem.isOfficial` — never player-scanned discs; the
  anti-exploit gate). 6 effects, T1–T2 craftable / T3 loot-only; knobs in config `resin` section.

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

- **Two doc surfaces — keep both in sync with code.** A player-facing change means
  updating BOTH the in-game Patchouli guide (`src/main/resources/.../patchouli_books/guide/`,
  a soft/optional dep) AND the website guide (`site/src/content/guide/*.md` + `site/src/pages/faq.astro`).
  They mirror each other and silently drift from the code — verify claims against the
  Java before writing (e.g. the T5-corner / scanner-off-hand drift fixed 2026-06-25).
- **Blueprints:** `CuratedBlueprintGenerator` (gated JUnit) is the source of truth —
  regen with `./gradlew test --tests '*CuratedBlueprintGenerator*' -DgenBlueprints=true
  --rerun-tasks`; validate/dump ASCII layers with `*BlueprintDumpTest* -DdumpBlueprints=true`.
- **Git:** **all changes land via pull request**: branch → push → open a PR → let CI +
  CodeRabbit run → merge on green. **No direct pushes** to `legacy/1.20.1` or to `main`
  (policy since the 1.0.0 release). Repository administrators can technically bypass the
  branch ruleset, but don't. This branch's PRs target **`legacy/1.20.1`**, never `main`.
  **Never** add `Co-Authored-By: Claude` or "Generated with Claude Code" to commits/PRs.

## Definition of Done

**This branch is one of two lines, and it is the older one.** A feature or fix is **not complete
until it ships on every supported version**: this Forge **1.20.1** line AND all seven NeoForge
Stonecutter nodes (1.21.1 · 1.21.8 · 1.21.9 · 1.21.10 · 1.21.11 · 26.1 · 26.2), which live on
**`main`** (one Stonecutter tree, not a separate branch). "It works on 1.20.1" is a half-done fix.

**The cascade runs both ways.** Work that starts here has to be ported forward to `main` in its own
PR, exactly as `main`'s work gets backported here. Forward-porting is the easier direction to forget,
because nothing on `main` will flag the omission, so do it in the same sitting.

Skip a version only for an **explicitly stated version-specific reason** (an API that exists on one
side only, for example), and write that reason into the PR rather than leaving it to be inferred.

**Code gates** here: `./gradlew build` (compiles + the full JUnit suite) and
`./gradlew runGameTestServer` (the in-world GameTests). On `main`, the matching gates are
`:1.21.1:runGameTestServer` plus `:NODE:test` and a `:NODE:runGameTestServer` boot-smoke on each
forward node.

**Doc gates are separate and manual.** No Gradle task validates either doc surface, so a green build
says nothing about whether the guides still match the code. For any player-facing change, re-read and
update BOTH the Patchouli guide (`src/main/resources/.../patchouli_books/guide/`) and the website
guide (`site/src/content/guide/*.md` + `site/src/pages/faq.astro`), on **both** lines, verifying each
claim against the Java. `cd site && npm run build` only proves the site compiles, not that it is
accurate. See the `fixes-cascade-all-versions` memory for the standing rule.

## Where deeper context lives

`docs/ROADMAP.md` (state + next-up), `docs/catalysts-design.md` (Resin/Catalyst spec +
decisions), `docs/rebalance/` (FU rebalance plan + per-mod research), `docs/blueprint-specs.md`,
`docs/popular-mods-1.20.1.md` (FU-synergy shortlist), and the project memory — index in
`MEMORY.md`; key entries: `active-roadmap`, `fu-economy`, `resin-system`, `upgrade-system`,
`blueprint-pipeline`, `modded-fu-compat`, `winder-blacklist`, `multiblock-corner-blocks`,
`github-blueprint-renderer` (the website + submission Worker), `blueprint-repository`,
`rack-and-cable`.

The **NeoForge port is COMPLETE** and is not a plan any more. `main` is a Stonecutter multi-version
tree building seven NeoForge jars (1.21.1 · 1.21.8 · 1.21.9 · 1.21.10 · 1.21.11 · 26.1 · 26.2); this
branch is the Forge 1.20.1 line that ships alongside them. `main` and `legacy/1.20.1` are the only
long-lived branches, everything else is a short-lived PR branch deleted on merge. The single source
of truth for the node ladder and its guard lore is `docs/port/stage2-stonecutter-multiversion.md`
on `main`. Memory: `neoforge-port-blitz`.
