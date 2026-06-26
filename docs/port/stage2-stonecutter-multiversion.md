# MC3DPrint — Multi-Version Architecture (Stonecutter) · Phases 2–3

**Status:** Phase 2 in progress (branch `stage2/multi-version`) · **Author:** PGMacDesign + agent
**Supersedes:** the Stage-1 port doc (now `docs/port/archive/stage1-neoforge-1.21.1-port-COMPLETE.md`).
**This is the single source of truth** for every remaining step: it takes the project from
"a separate 1.20.1 build and a separate 1.21.1 build" to **one source tree that builds a working
jar for any supported Minecraft version from a single config change**, and stays cheap to extend to
future versions — without ever breaking 1.20.1.

> **The whole goal in one sentence:** drive every version-divergent piece of the mod behind a thin
> **abstraction/seam layer**, and let **Stonecutter** (a compile-time preprocessor) select the correct
> branch per target — so `./gradlew chiseledBuild` emits one jar per `(Minecraft version, loader)` node,
> each correct for its target, and adding a new Minecraft version is a small, localized, mechanical task.

> This doc is meant to be **executable by an agent team with a human verifier** for the in-world gates.
> Where it cites a specific Stonecutter / ModDevGradle API, **pin it against the current upstream docs at
> setup time** — the exact DSL evolves; the architecture here does not.

---

## 0. Load-bearing facts (the constraints that force this design — read first)

These are the immovable truths the whole plan is shaped around. They are why "an abstraction layer + a
runtime config flag" is **not** sufficient on its own.

1. **Mods are version-locked. Each `(MC version, loader)` ships its own jar.** "Support many versions" =
   build many jars from one source, not one jar that runs everywhere.
2. **A single source tree cannot `javac` against two Minecraft versions at once.** `ItemStack.getTag()`
   exists in 1.20.1 and is *deleted* in 1.21.1; `saveAdditional`, `RecipeHolder`, the criteria API, etc.
   all changed shape. An interface compiles against both, but its **two implementations cannot coexist in
   one tree compiled once** — the 1.20.1 body references symbols absent on the 1.21.1 classpath and vice
   versa. **No runtime `if` can fix a missing symbol — it fails at compile.** Therefore the version switch
   must happen **before `javac`**: conditional compilation (**Stonecutter** strips the non-matching
   comment-guarded branch), or per-version source sets. The seam layer **shrinks** the divergent surface;
   Stonecutter is what makes one tree emit two correct jars.
3. **1.20.1 has no NeoForge.** NeoForge forked from Forge at **1.20.2**; there is no NeoForge build for
   1.20.1. So **1.20.1 = Forge**, **1.21.1 (and every future target) = NeoForge**. That gives us **two
   axes**, not one: a **version axis** *and* a **loader axis**. The loader axis is the expensive half — it
   bundles the capability-model inversion, registration, networking, and event-bus differences. (See §8.1
   for the one lever that would remove it.)
4. **We are on official Mojang mappings (mojmap) on both Forge and NeoForge.** The port is **API-shape
   changes only, zero identifier renaming** — a large, permanent saving that also helps future versions.
5. **Stage 1 is done but went native-direct.** The current `main` is a working single-target **NeoForge
   1.21.1** build, but it calls NeoForge APIs **directly** (no seam interfaces — see archived doc / Linear
   PGM-7). The divergent code is at least *localized* (caps in `ModCapabilities` + BE accessors, net in
   `MC3DPrintNetwork`, item data in `ModDataComponents`), which is the right cut-point — but the formal
   abstraction layer must now be **retrofitted**.
6. **The frozen `legacy/1.20.1` branch is the content source for every 1.20.1 branch.** It is the original
   Forge 1.20.1 code, untouched by the port, still building green. Its seam-body code is what a human/agent
   transcribes **inline** into each `//? if FORGE_1_20` guard. Stonecutter reads one shared tree; it does
   **not** pull source from a git branch — the re-co-location is manual, file by file (but bounded to the
   seam surface).

---

## 1. The phase model (what "going forward" means)

| Phase | State | Scope | Definition of Done |
|---|---|---|---|
| **Phase 1** | ✅ DONE | Single-target **NeoForge 1.21.1** port of one tree (archived doc, PGM-5…25). | `main` builds a 1.21.1 jar; 93/94 gametests (iron-farm = in-world). |
| **Phase 2** | ▶ CURRENT | Stand up **Stonecutter** + the **seam/abstraction layer**, and **reunify the two versions we already have** — `1.20.1-forge` + `1.21.1-neoforge` — into one tree. A config change selects the target. | **Both** nodes compile; **both** run their gametests at the version baseline (94 each); `chiseledBuild` emits both jars; no regression to either line. |
| **Phase 3** | ⬜ NEXT | **Forward-compatibility framework + runbook**: make adding *any* future Minecraft version a small, localized, documented task. Prove it by adding the next live version (e.g. the current 1.21.x). | Adding a new version node is mechanical (declare node → fill only the seam branches that churned → build → gametest); a written runbook exists; the proof-of-concept third version builds + passes. |

Phases 2 and 3 are the body of this doc (§6 and §7). Everything before them (§2–§5) is the shared
architecture both phases rely on.

---

## 2. The target matrix (the "minor config change")

The build is parameterized over **version × loader**. Each cell is a **Stonecutter node** that produces one jar.

| Node id | Minecraft | Loader | Build plugin | Source of the divergent bodies |
|---|---|---|---|---|
| `1.20.1-forge` | 1.20.1 | **Forge** 47.x | ForgeGradle *or* ModDevGradle `legacyForge` (§3.3) | frozen `legacy/1.20.1` (transcribed inline) |
| `1.21.1-neoforge` | 1.21.1 | **NeoForge** 21.1.x | ModDevGradle `neoForge` | current `main` (native-direct code) |
| *future* `1.21.x-neoforge` | 1.21.x | NeoForge 21.x | ModDevGradle `neoForge` | added in Phase 3, branch from nearest node |

The "minor config change" the project goal describes is: **`stonecutter.active "<node>"`** (for dev) and
**`./gradlew chiseledBuild`** (to emit all nodes). It is a *build-time* selection that emits the right jar
per target — not a runtime flag, and not one jar for all versions (§0.2).

---

## 3. How Stonecutter makes the switch

### 3.1 The model
Stonecutter is a Gradle plugin + a **comment preprocessor**. Version-divergent code lives inline, guarded by
special comments; for the active node, Stonecutter **comments out the non-matching branch before `javac`**,
so deleted/absent symbols never reach the compiler:

```java
// stable code that compiles on every node …
//? if >=1.20.5 {
disc.set(ModDataComponents.BLUEPRINT.get(), data);          // 1.21.1 (data components)
//?} else {
/*disc.getOrCreateTag().put("Blueprint", data.toNbt());*/    // 1.20.1 (item NBT) — from legacy/1.20.1
//?}
```

The active branch is real source; the inactive branch is a comment (so its symbols are never resolved).

### 3.2 Version + loader constants
Nodes are declared in `settings.gradle` / the `stonecutter` block with ids that encode **both** axes
(e.g. `1.20.1-forge`, `1.21.1-neoforge`). Guards check the Minecraft version (`>=1.20.5`, `<1.21`, …) **and**
a loader constant (`FORGE` / `NEOFORGE`) where the divergence is loader-driven rather than version-driven
(caps, registration, networking, event bus). Most guards key off the **version**; a smaller set key off the
**loader**. Keep the constant vocabulary tiny and centralized.

### 3.3 One build system for two loaders (preferred) vs two (fallback)
- **Preferred:** drive **both** loaders from **ModDevGradle** — MDG 2.x exposes a `neoForge { … }` *and* a
  legacy-Forge path; one plugin, per-node configuration selected by the Stonecutter node. This keeps a single
  Gradle model. **Verify MDG's current legacy-Forge support covers Forge 47.x/1.20.1 at setup;** if it does,
  this is the clean path (it also subsumes the Stage-1 ForgeGradle→MDG work already done on `main`).
- **Fallback:** if MDG can't drive Forge 1.20.1 cleanly, Stonecutter supports **per-node build logic** — the
  `1.20.1-forge` node uses **ForgeGradle 6**, the NeoForge nodes use **ModDevGradle**. Messier (two plugins,
  conditional `build.gradle`) but fully supported. Decide at §6.0 against the real plugin versions.

> **Do not hand-wave this.** The build-system choice is the riskiest setup decision; resolve it concretely in
> Phase 2.0 with a spike that gets an empty-ish shell of **both** nodes building before any seam work.

---

## 4. The abstraction / seam layer — where forward-compatibility actually comes from

A seam is a thin **core interface** that the rest of the mod calls; the version/loader-divergent code lives
in **one place per seam**, behind the interface, guarded by Stonecutter. The payoff is **scope**: a new
Minecraft version (or a loader difference) touches only these ~handful of files, never the ~125 others.

The surface map is inherited from the Stage-1 analysis (still accurate — the API divergences are the same
facts). For each seam, note **what drives its divergence** — this tells you whether a *new version* will
likely disturb it (version-driven) or whether it's mostly a one-time *loader* cost.

| # | Seam | What it hides | Divergence driver | Notes |
|---|---|---|---|---|
| 1 | **ItemData** | item NBT ↔ data components | **version** (1.20.5) | The big version churn point; future versions may touch component codecs. |
| 2 | **Capabilities** (energy + FU + item-handler) | Forge caps/`LazyOptional` ↔ NeoForge `BlockCapability` | **loader** (mostly) | Heaviest loader cost; stable across NeoForge versions once paid. |
| 3 | **Net** | `SimpleChannel` ↔ `CustomPacketPayload` | **both** (1.20.2 rework + loader) | |
| 4 | **Registration + mod entry/event bus** | `RegistryObject`↔`DeferredHolder`; `@Mod` ctor; bus enum | **loader** (mostly) | |
| 5 | **BE persistence** | `saveAdditional(tag)` ↔ `(tag, HolderLookup.Provider)` | **version** (1.20.5) | Future versions occasionally re-touch save signatures. |
| 6 | **Client / render** | `MenuScreens.register`↔event; `VertexConsumer` shape | **version** (and 1.21.5 looms) | Highest future-churn risk (render pipeline rewrites). |
| 7 | **Storage handles** | `net.minecraftforge.{energy,items}` ↔ `net.neoforged.neoforge.*` | **loader** | Import-swap only per node; a mod-owned wrapper if the APIs ever diverge. |
| C1 | `BlockEntityTag` ↔ `DataComponents.BLOCK_ENTITY_DATA` | item BE-data convention | **version** (1.20.5) | |
| C2 | Advancement criteria (`Codec`/`SimpleInstance`/`TRIGGER_TYPE`) | **version** (1.20.2) | |
| C3 | Config (`ForgeConfigSpec` ↔ `ModConfigSpec`) | **loader** | |
| C4 | Recipe-derivation (`getAllRecipesFor`/`RecipeHolder`/`RecipeInput`) | **version** (1.21) | |
| C5 | Patchouli book stamp | **soft-dep + version** | still interim (raw CustomData) — see §8.3 |
| C6 | JEI plugin (15.x ↔ 19.x) | **both** | |
| C7 | FU public API + IMC ingress | **loader** | public contract — keep stable |
| C8 | `appendHoverText(Level)` ↔ `(Item.TooltipContext)` | **version** (1.20.5) | |
| C9 | Anvil custom-name mutators ↔ `DataComponents.CUSTOM_NAME` | **version** (1.20.5) | |
| § | **Data files** (folder names, recipe/loot/criteria JSON, forge→neoforge namespaces) | **version** | Stonecutter can template data too, or generate per-node — see §6.4 |

**Design rule:** a seam earns its place by localizing divergence. Where a difference is a one-line import
swap (seam 7) a full interface is overkill — a guarded import is enough. Where it's a true rewrite (caps,
net, item data) the interface pays for itself. **Bias toward the smallest seam that contains the divergence.**

---

## 5. Mapping the code we already have into branches

We are not writing two implementations from scratch — both already exist, on two git branches:

- **`1.21.1-neoforge` branch content = today's `main`.** The native-direct NeoForge code becomes the
  `>=1.20.5 / NEOFORGE` body of each seam.
- **`1.20.1-forge` branch content = today's `legacy/1.20.1`.** Its Forge bodies are **transcribed inline**
  into the `<1.20.5 / FORGE` guards. (Stonecutter reads one tree; this is a manual, file-by-file
  re-co-location, but bounded to the seam surface — not all 125 files.)

The retrofit per seam: (a) define the core interface / guarded call site, (b) move today's NeoForge body into
the `NEOFORGE` branch, (c) transcribe the matching `legacy/1.20.1` body into the `FORGE` branch, (d) verify
**both** nodes compile, (e) verify **both** nodes' gametests.

---

## 6. Phase 2 execution plan (the work we're doing now)

Gates are **[AGENT]** (closable headlessly) or **[HUMAN]** (in-world). Do not advance until green.

- **2.0 — Stonecutter + build-system scaffold.** Add Stonecutter; declare nodes `1.20.1-forge` and
  `1.21.1-neoforge`; resolve the build-system question (§3.3) with a spike. Pin Stonecutter + MDG versions.
  **[AGENT] Gate:** an *empty-ish shell* of **both** nodes configures and reaches `compileJava` start (even if
  source still fails) — i.e. the dual-node Gradle model works.
- **2.1 — Establish the two oracles.** Confirm the `1.21.1-neoforge` node reproduces today's `main` (94
  gametests, 93 pass) and the `1.20.1-forge` node reproduces `legacy/1.20.1` (94 gametests, all pass), each as
  a *single-node* build before guards are introduced. **[AGENT] Gate:** both baselines reproduced.
- **2.2 — Retrofit seams + guard the divergent surface.** For each seam in §4, in dependency order
  (registration + item-data first, mirroring Stage 1): introduce the core interface / guarded site, place the
  NeoForge body, transcribe the Forge body from `legacy/1.20.1`. Work seam-by-seam; after each, **both** nodes
  must still compile. **[AGENT] Gate per seam:** `compileJava` green on **both** active nodes.
- **2.3 — Data files per node (§6.4).** Handle the 1.21 vs 1.20.1 data-format split (folder names, recipe
  ingredient/result shape, criteria JSON, forge↔neoforge namespaces). **[AGENT] Gate:** each node's datapack
  loads with zero parse errors (the loot-modifier / recipe-format class of bug from Stage 1).
- **2.4 — Gametest parity on both nodes.** `runGameTestServer` on each node. **[AGENT] Gate:** each prints its
  baseline count (94) and passes (1.20.1 = 94/94; 1.21.1 = 93/94 until the iron-farm in-world item, PGM-23).
- **2.5 — Emit both jars.** `./gradlew chiseledBuild` produces `mc3dprint-<ver>-<loader>-<modver>.jar` for both
  nodes. **[HUMAN] Gate:** each jar loads in its target client; a survival scan→print works on both.

### 6.4 Data files across versions
Data diverges by version (folder depluralization, recipe `item`→`id`, ingredient object vs string in 1.21.2+,
criteria codecs, `forge:`→`neoforge:`/`c:` namespaces). Two options, decide at 2.3:
- **(a) Per-node resource roots** (Stonecutter/MDG source-set selection): keep a `1.20.1` and a `1.21` data
  tree, share what's identical. Simple, explicit, more duplication.
- **(b) Generated data** (datagen) producing the right shape per node. Less duplication, more machinery; the
  mod currently has **no** datagen (deliberately). Lean **(a)** unless duplication becomes painful.

---

## 7. Phase 3 — forward-compatibility framework + the "add a version" runbook

Phase 3 turns "we support two versions" into "we can support **any** future version cheaply, and 1.20.1 never
breaks." It is mostly **process + proof**, built on the Phase-2 seam layer.

**Deliverables:**
1. **The runbook** (`docs/port/RUNBOOK-add-a-version.md`): the exact, ordered steps to add a node —
   declare the Stonecutter node + loader; bump deps; run `compileJava` and let the failures enumerate the
   churned seams; fill only those seam branches; reconcile data files; gametest; ship. With a worked example.
2. **A churn-localization guarantee:** evidence (from the proof version) that a new version touched only seam
   files + data, never the ~125 core files. If it didn't, that's a missing seam — add it.
3. **Proof-of-concept third node:** add the next live Minecraft version (pick at Phase-3 start — e.g. the
   current 1.21.x) end-to-end via the runbook. **[AGENT] Gate:** third node compiles + gametests; **[HUMAN]
   Gate:** loads in-world.
4. **Regression safety for 1.20.1:** the `1.20.1-forge` node stays in the chiseled build + gametest matrix
   forever, so any change that would break it fails CI. (This is the structural guarantee that "we don't
   break 1.20.1.")
5. **CI matrix:** `chiseledBuild` + per-node `runGameTestServer` in CI (Java 21 already set; add Java 17 for
   the Forge 1.20.1 node) so every push validates **all** nodes.

**Forward-compat is cheap, not free (§0):** Stonecutter selects branches you write; each new version still
needs a human to fill the churned seam branches. The win is that the work is *localized and enumerated by the
compiler*, and 1.20.1 is *protected by the matrix*.

---

## 8. Open decisions / levers

- **8.1 — The loader-axis lever (biggest knob).** Keeping 1.20.1 forces **Forge** (no NeoForge for 1.20.1),
  which is the entire loader axis (caps inversion, registration, net, event bus, config). **Default
  (assumed): keep `1.20.1-forge`** — the project goal is explicitly "don't break 1.20.1." *If* that floor were
  ever relaxed to **NeoForge-1.20.2**, the loader axis disappears and the design collapses to a pure
  version-axis Stonecutter setup (markedly simpler). **← Confirm this default.**
- **8.2 — Build system:** ModDevGradle-for-both vs ModDevGradle + ForgeGradle (§3.3). Resolve in 2.0.
- **8.3 — Seam vs native-direct retrofit cost:** Stage 1 went native-direct; Phase 2 pays the seam retrofit
  now. Worth it for the multi-version goal; quantify per-seam in 2.2.
- **8.4 — Data strategy:** per-node roots vs datagen (§6.4).
- **8.5 — Inherited Stage-1 TODOs that ride along:** iron-farm gametest (PGM-23, in-world), real Patchouli
  1.21 component API (C5), JEI-19 `getBackground` deprecation (C6), and the `BlockCapabilityCache` cable
  optimization (decision 5.2). Fold each into the seam where it lives.

---

## 9. Definition of done (the whole project)

- One source tree on `stage2/multi-version` → `main`. `./gradlew chiseledBuild` emits a correct jar for every
  node: **`1.20.1-forge`** and **`1.21.1-neoforge`** (plus the Phase-3 proof node).
- Every divergent surface is behind a seam guarded by Stonecutter; the core ~125 files are version/loader-agnostic.
- Each node passes its gametest baseline (1.20.1 = 94/94; 1.21.1 = 93/94 → 94/94 once the iron-farm in-world item is fixed).
- Adding a new Minecraft version is a documented, localized, mechanical task (the §7 runbook), proven once.
- 1.20.1 is protected by the build+gametest matrix and cannot silently break.
- Zero mixins/ATs/datagen introduced beyond what's justified. No `Co-Authored-By: Claude` / "Generated with Claude Code".
- Doc surfaces (Patchouli guide + website guide) verified against each node, not assumed.

---

## Appendix — relationship to the Stage-1 doc
Stage 1 (`docs/port/archive/stage1-neoforge-1.21.1-port-COMPLETE.md`) is the completed single-target port and
the detailed reference for **every per-surface API change** (the §3.x seam bodies, the C1–C9 companions, the
exact file:line inventory). This doc does **not** re-derive that detail — it reuses it as the content for the
`>=1.20.5 / NEOFORGE` branches and adds the **multi-version architecture, the loader axis, the Stonecutter
mechanism, and the forward-compat process** on top. When implementing a seam, read the archived doc's matching
§ for the API specifics, then place it behind the guard per §4–§5 here.
