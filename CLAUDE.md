# MC3DPrint — Project Instructions

**NeoForge 1.21.1** tech mod: "WorldEdit for survival." Scan a build → save to a Blueprint
Disc → print it anywhere with a tiered printer/fabricator, powered by RF + tiered **Filament
Units (FU)**. T1–T4 = single printer blocks; T5–T8 = N×N multiblock fabricators.

- **Stack:** Java 21 (26.x nodes: Java 25 toolchain), NeoForge, official Mojang mappings. mod id `mc3dprint`,
  MIT, solo (PGMacDesign). `main` is the NeoForge 1.21.1 line; the **multi-version** branch (Stonecutter) builds
  **seven NeoForge jars from one tree** (1.21.1 · 1.21.8 · 1.21.9 · 1.21.10 · 1.21.11 · 26.1 · 26.2);
  **1.20.1 Forge** (Java 17, Forge 47.4.10) lives on the `legacy/1.20.1` backport branch. See the
  multi-version note at the bottom of this file.
- **Public repo** (`PGMacDesign/MC3DPrint`): no secrets/PII, original content only. `.env` is gitignored.
  **Nothing that points at private systems or the owner goes in committed files** — no Linear links
  or ticket ids (`PGM-…`), no personal info (emails), no internal infra ids beyond what a deploy file
  genuinely needs. Keep tracker/issue references in Linear and in local `~/.claude` memory, not in the
  repo. (Pre-existing bare `PGM-…` code comments are grandfathered — low-risk, don't add more.)

## Build · Test · Deploy

On the **multi-version (Stonecutter) branch**, tasks are node-scoped and need a **Java 21 launcher
JVM** (`JAVA_HOME=~/.gradle/jdks/eclipse_adoptium-21-*/jdk-21*/Contents/Home`). Edit at active node
`1.21.1` (plain code = 1.21.1; the 1.21.5+ variant lives in `//? if >=1.21.5 {/*…*///?} else {…//?}`
guards); after writing new guards run `"Set active project to <node>"` to re-toggle before compiling;
reset active → `1.21.1` before every commit. `:NODE:test` green ≠ runtime-correct — also
`runGameTestServer` to catch registration/NBT bugs the compiler can't.

```bash
./gradlew :1.21.8:compileJava -q        # fast compile check (or :1.21.1)
./gradlew :1.21.8:test                  # JUnit (test/.../fu, blueprint, compat)
./gradlew :1.21.8:assemble -x test      # build jar → versions/1.21.8/build/libs/mc3dprint-<ver>.jar
./gradlew :1.21.1:runGameTestServer     # in-world GameTests (gametest/); 124/124 green on 1.21.1 (the oracle;
                                        # forward nodes exclude gametest/ and boot-smoke only)
# Single-target main/legacy branches use the un-scoped form: ./gradlew build
```

**Release builds — all versions at once:** `./scripts/build-all.sh [--version X.Y.Z]` produces every
shippable jar into `dist/`: one `mc3dprint-<ver>-neoforge-<node>.jar` per Stonecutter node (all seven)
plus `-forge-1.20.1.jar` (built in a throwaway worktree off the `legacy/1.20.1` branch). Launcher needs
JDK 21, the legacy Forge build JDK 17 — both auto-detected (override `MC3DP_JDK21`/`MC3DP_JDK17`); the
26.x nodes compile on a Java 25 toolchain Gradle/foojay provisions itself.
`.github/workflows/release.yml` runs this on every published GitHub Release and attaches ALL jars to it
(CurseForge auto-publish stays limited to soak-tested targets). Extend the `NEOFORGE_NODES` array in the
script to add a future version node.

Multi-version guard lore that bites: replacement pairs (`build.gradle` stonecutter block) must be
single-hop — an API that moves TWICE across versions needs `if/elif` guard chains (version-range
replacement conditions don't fire); never hand-nest block-comment guards inside an already-commented
region (hoist a class-level helper with a sibling chain); never start a guard block with bare `//` lines.

Two rules that bite if skipped:

1. **Deploy = replace, never duplicate.** Copy the fresh jar into your test instance's
   `mods/` folder, replacing any existing `mc3dprint-*.jar` — never leave two.
2. **fuValues is overrides-only (config no-wipe, since 0.11)** — the toml list merges OVER
   built-in defaults in code (`FuValueRegistry.loadMerged`; `<id>=off` removes), so FU/economy
   changes apply WITHOUT deleting configs. One-time exception: a toml written by ≤0.10 carries
   the full copied default list, which now pins those values as overrides — delete it once
   (dev `run/config/mc3dprint-common.toml` + game instance `config/`) to migrate.

## Architecture (`src/main/java/com/pgmacdesign/mc3dprint/`)

| Package | What lives there |
|---|---|
| `MC3DPrint.java` | Mod entry; registries + event-listener wiring (incl. compat hooks) |
| `fu/` | **FU economy core** — `FuValueRegistry` (values + tiers), `RecipeFuValuator`, `FuEvents` |
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
- **Git:** commit → push every change, direct to `main` (solo repo; a ruleset requires PRs
  for non-admins but admin/Patrick bypasses, so direct push still works). **Never** add
  `Co-Authored-By: Claude` or "Generated with Claude Code" to commits/PRs.

## Where deeper context lives

`docs/ROADMAP.md` (state + next-up), `docs/catalysts-design.md` (Resin/Catalyst spec +
decisions), `docs/rebalance/` (FU rebalance plan + per-mod research), `docs/blueprint-specs.md`,
`docs/popular-mods-1.20.1.md` (FU-synergy shortlist), and the project memory — index in
`MEMORY.md`; key entries: `active-roadmap`, `fu-economy`, `resin-system`, `upgrade-system`,
`blueprint-pipeline`, `modded-fu-compat`, `winder-blacklist`, `multiblock-corner-blocks`,
`github-blueprint-renderer` (the website + submission Worker), `blueprint-repository`,
`rack-and-cable`.

The **multi-version build**'s forward ladder is COMPLETE (2026-07-05): seven NeoForge nodes green from
one tree, per-node [HUMAN] in-world soaks are the remaining ship gates. The single source of truth is
`docs/port/stage2-stonecutter-multiversion.md` (Phase-3 box at the top = per-node facts + guard lore). Stage 1 (the completed single-target
NeoForge 1.21.1 port, Linear PGM-5…25) is archived at `docs/port/archive/stage1-neoforge-1.21.1-port-COMPLETE.md`.
Memory: `neoforge-port-blitz`.
