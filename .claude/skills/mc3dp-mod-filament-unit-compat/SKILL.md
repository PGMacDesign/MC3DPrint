---
name: mc3dp-mod-filament-unit-compat
description: >-
  Use this skill WHENEVER the user wants a third-party Minecraft mod's items to take part
  in MC3DPrint's filament/printer economy — i.e. to give that mod's items Filament Unit
  (FU) values and material tiers so they wind into filament and print on the tiered
  printer/fabricator. Trigger on ANY request pairing a mod name (Mekanism, Immersive
  Engineering, EnderIO, Thermal, Applied Energistics/AE2, Botania, Create, Powah, Silent
  Gear, Ars Nouveau, Tinkers' Construct, Draconic Evolution, Mystical Agriculture, etc.)
  with making its items printable, windable, valued, tiered, balanced, or "supported" —
  including terse asks like "add FU support for Mekanism," "value IE's steel for
  filament," "none of Powah's items print," or "wire up Botania." This is the default
  path for adding any mod's FU values; don't hand-assign them without it. Accepts an
  optional target Minecraft version. Skip ONLY for rebalancing VANILLA FU tiers (no mod
  involved), debugging mod crashes/conflicts, and printer RF or upgrade-module mechanics.
---

# MC3DPrint — Mod Filament Unit (FU) Compat

Encodes the exact procedure used to add AE2, Thermal, and Tinkers' Construct FU support.
Goal: given a mod name, produce a **soft-dependency** hook that gives that mod's obtainable
items FU values + material tiers, so they wind into filament and print — **invisible when
the mod isn't installed**, and balanced so cheap items can't launder into rarer ones.

Work only in the MC3DPrint repo (`mod_id=mc3dprint`). If you're not there, stop and say so.

## The one parameter: target Minecraft version

Default to the repo's own version: read `minecraft_version` from `gradle.properties`
(currently `1.20.1`). Override only if the user names a version ("…for 1.21"). The version
controls **which branch of the target mod's GitHub repo you read IDs from** and which
loader API applies. The FU anchors you calibrate against always come from the **live**
`fu/FuValueRegistry.java` in this repo — never hardcode them.

## Why this is fiddly (read before starting)

Three non-obvious failure modes the steps below exist to prevent:

1. **A wrong item id is a *silent* no-op.** `registerApiItemValue` stores by
   `ResourceLocation`; an id that matches nothing simply never fires — no crash, no warning.
   So a typo means "I added support" but nothing actually got valued. **Always read ids from
   the mod's source**, never from memory.
2. **Most items must NOT be valued — they derive.** Our `RecipeFuValuator` already prices
   anything made through vanilla recipe *types* (`minecraft:crafting_*`, `smelting`,
   `stonecutting`) — including modded recipes that use those types. Valuing a derivable item
   by hand creates drift. Only the **custom-machine-recipe leaves** need explicit values.
3. **The economy is launder-able.** Printing is down-only and winding is 1:1, so if you
   tier a farmable item too high, its spool can print something rarer than itself. Tiering
   is a balance decision, not a guess — see the abundance rule in `references/fu-model.md`.

## Workflow

### 1. Scope the mod
- Get the **mod id** (the `ModList.isLoaded` gate) and the **item namespace**. They're often
  the same but not always (Thermal: many repos/asset folders, one `thermal:` item ns). The
  lang-file keys are the source of truth for the item namespace — see step 2.
- Find the mod's **GitHub repo** (owner/name) and confirm a branch exists for the target MC
  version. If you can't find authoritative source, tell the user — do not proceed on guessed ids.

### 2. Pull ground-truth ids from source
Use the bundled helper — it does the gh-api + lang-parse dance:
```bash
.claude/skills/mc3dp-mod-filament-unit-compat/scripts/fetch_mod_ids.sh <owner/repo> <branch> [name-filter]
```
It lists every `<namespace>:<path>` item/block id (parsed from `assets/*/lang/en_us.json`)
and the candidate registration/recipe source files. Read the registration class(es) it points
to so you understand **how each item is obtained** (mined ore? machine alloy? plain craft?) —
acquisition drives both classification (step 3) and tier (step 4). `gh` must be authenticated;
if it 404s, the branch name is wrong — list branches with `gh api repos/<owner/repo>/branches`.

### 3. Classify every candidate item
Sort each id into exactly one bucket:
- **VALUE it** — a *leaf* produced by a custom machine recipe the valuator can't read
  (Smeltery alloy, AE2 Inscriber, Thermal Induction Smelter, DE Fusion, mechanical
  press/mixer, etc.), or a **mined raw/ore** (worldgen leaf, no recipe). These are the only
  things you register.
- **SKIP — derives** — blocks (9 ingots), nuggets (1/9 ingot), and anything craftable/smeltable
  through vanilla recipe types. Pin the ingot; let block/nugget/plate/gear derive.
- **SKIP — anti-launder** — resource-multiplication outputs (ore-doubling dusts/plates/slag,
  tree/crop extractor products). Keeping them out of the FU graph stops infinite-mat exploits.
- **SKIP — unobtainable/joke** — render-only or creative-only items (e.g. `fake_ingot`,
  `cheese_ingot`). Leaving them unvalued makes them unprintable in strict mode (safe).

### 4. Calibrate FU value + material tier
Read the live anchors and rules in **`references/fu-model.md`** (it has the per-tier vanilla
FU magnitudes and the cross-mod common-material table). For each item you're valuing:
- Place it on the **acquisition-rarity** ladder relative to vanilla anchors (iron=20@T2,
  obsidian/glowstone=10–20@T3, blaze_rod=40@T4, diamond=50@T5, netherite_scrap=125@T6, …).
- Apply the **abundance rule**: a farmable input's product can't sit at a tier whose spool
  prints something rarer than that product. (Why manyullyn=T6: it alloys ancient debris.)
- Reuse **cross-mod commons** — steel=T3, cobalt=T4 — so the same material can't arbitrage
  across mods. Check `references/fu-model.md` for the running list and add to it.
- Numbers are tunable; aim for a coherent ladder, not false precision.

### 5. Emit the four artifacts
1. **Compat class** — copy `assets/Compat.java.template` to
   `src/main/java/com/pgmacdesign/mc3dprint/integration/<mod>/<Mod>Compat.java`, fill the
   placeholders, group `register(...)` calls by tier with one-line rationale comments. Mirror
   the depth of `integration/tinkers/TinkersCompat.java`'s javadoc (classification + tiering +
   anti-launder notes belong in the class doc).
2. **Wire it** — add one line to the `MC3DPrint` constructor next to the other compat hooks:
   `modEventBus.addListener(...integration.<mod>.<Mod>Compat::onCommonSetup);`
3. **Research doc** — copy `assets/rebalance-doc.md.template` to `docs/rebalance/<mod>.md`;
   record namespace/gate, the derivation reasoning, the value table, and open verification items.
   Match `docs/rebalance/tconstruct.md` for structure.
4. **Memory** — append the mod to the "Supported so far" line of the `modded-fu-compat`
   project memory (`~/.claude/projects/<project>/memory/modded-fu-compat.md`); note any new
   cross-mod common you anchored.

No gradle dependency is ever added — the hook is pure `ResourceLocation` strings, so it stays
invisible when the mod is absent. That invisibility is the whole point; don't import the mod's
classes.

### 6. Verify, then commit
- `./gradlew compileJava -q` must pass. Then `./gradlew runGameTestServer -q` must stay green —
  it will, because the hook is mod-gated (the target mod isn't on the test classpath), but run
  it to prove no regression in the vanilla tier tests.
- List the **in-game verification items** (ids you're least sure about — speculative obtain
  paths, recent-build alloys) in the doc, since the mod isn't loaded in tests.
- Commit → push following repo convention (conventional message, **no Claude attribution**),
  unless the user is batching several mods.

## Done when
A reviewer can read `<Mod>Compat.java` + `docs/rebalance/<mod>.md` and see: which ids were
valued and why, what was deliberately skipped (and why), the tier rationale, and that
`compileJava` + `runGameTestServer` are green. Report the value table + open verify items to
the user concisely.
