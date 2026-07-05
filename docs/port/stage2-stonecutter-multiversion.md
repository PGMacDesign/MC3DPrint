# MC3DPrint — Multi-Version Architecture (Stonecutter) · Phases 2–3

**Status:** Phase 3 proven (branch `stage2/multi-version`) · **NeoForge-only, single (version) axis.**

> ### ✅ Phase 3 — the forward ladder is COMPLETE (2026-07-05)
> One tree now builds **seven NeoForge jars**: `1.21.1 · 1.21.8 · 1.21.9 · 1.21.10 · 1.21.11 · 26.1 · 26.2`
> (+ the separate `legacy/1.20.1` Forge jar via `build-all.sh`). Every node compiles main+test and passes
> JUnit; 1.21.1 stays the oracle (105/105 GameTests); forward nodes boot `runGameTestServer` clean.
> Per-node facts: 1.21.9 = NeoForge beta-only (21.9.16-beta), transfer-API + BER render-state waves;
> 1.21.10 = zero new seams; 1.21.11 = the great renames (Identifier, RenderTypes, criterion) — first use of
> **Stonecutter global string replacements** (`build.gradle`); 26.1 = **Java 25**, GUI extract pipeline,
> registry/loot churn, JEI artifact tracks the MC patch version (`jei_mc_version`); 26.2 (beta) = criteria
> split into triggers/predicates, `MultiBufferSource` deleted (scanner overlay → **Gizmos API**).
> **Hard-won rules:** (1) replacement pairs must be single-hop — anything that moves TWICE across versions
> gets guard chains, version-RANGE replacement conditions don't fire reliably; (2) never hand-write nested
> block-comment guards inside an already-commented region — hoist to a class-level helper with a sibling
> `if/elif` chain; (3) never put bare `//` lines as the first content of a guard block.
> The ~890-error data-driven gametest migration stays deferred until a 26.x stable is the declared
> ship target (Linear tracks it). [HUMAN] in-world soaks per node are the remaining ship gates.
**Supersedes:** the Stage-1 port doc (`docs/port/archive/stage1-neoforge-1.21.1-port-COMPLETE.md`).
**This is the single source of truth** for all forward work.

## Decision (LOCKED): drop Forge, NeoForge-only, 1.20.1 is a separate backport branch
The unified Stonecutter tree is **NeoForge, floored at 1.21.1**, and extends *forward* (1.21.x, 1.22, …).
**1.20.1 is NOT in the tree.** It lives as a standalone `legacy/1.20.1` **Forge** branch, maintained by
**manual backports** when desired (data files copy over cheaply; Java changes are hand-ported). Rationale
(researched mid-2026): AE2, Draconic Evolution, and Ender IO are all on **NeoForge 1.21.1** now; **Tinkers'
Construct** is the lone 1.20.1 holdout, and when it ports it will be **NeoForge** — so the Forge/loader axis
was a *temporary* cost, not worth baking into the architecture. (If 1.20.1 ever needs to rejoin the unified
tree, that's a loader-axis project — see the archived doc; we are deliberately not doing it.)

> **The goal in one sentence:** keep the version-divergent pieces behind a thin **seam/abstraction layer** and
> let **Stonecutter** (compile-time preprocessor) select the branch per target, so adding a **future Minecraft
> version** is a small, localized, mechanical task — while the ~125 version-agnostic files stay untouched.

> Where this doc cites a Stonecutter / ModDevGradle API, **pin it against current upstream docs at setup** —
> the DSL evolves; the architecture here does not.

---

## 0. Load-bearing facts (read first)

1. **Version-locked.** Each Minecraft version builds its own jar; "support many versions" = many jars from one source.
2. **One tree can't `javac` against two Minecraft versions at once** (`getTag()` exists in 1.20.1, deleted in
   1.21.1; future versions delete/reshape other symbols). No runtime `if` fixes a missing symbol. So the version
   switch must happen **before `javac`** — **Stonecutter** strips the non-matching comment-guarded branch. The
   seam layer *shrinks* the divergent surface; Stonecutter is the switch.
3. **Single axis: version only.** Everything is **NeoForge** now. The loader axis is gone — the heavy
   Forge↔NeoForge differences (capability-model inversion, registration, networking, event bus, config) are no
   longer in the tree; the code already sits on the NeoForge side of all of them. This roughly **halves** the
   complexity of the original two-axis plan.
4. **mojmap on NeoForge** → API-shape changes only, zero identifier renaming across versions.
5. **Today's `main` IS the base node.** It is a working single-target **NeoForge 1.21.1** build. It becomes the
   `1.21.1` Stonecutter node and the source of truth other version nodes branch from.
6. **The seam layer is forward-compat *insurance*, built *incrementally*.** Relative to the 1.21.1 base, almost
   every historical divergence (item NBT→components, BE persistence, criteria, tooltips, recipe format, the
   loader differences) is **already resolved** — the code is on the new side. A seam becomes *active* only when a
   **future** version introduces a **new** divergence. So we **do not** build a big speculative abstraction layer
   up front: we add a version node, let the **compiler enumerate** what diverged, and seam exactly that. The
   abstraction layer **grows to fit reality**, which is the correct (and cheapest-correct) way to do it.
7. **Stonecutter only earns its keep with ≥2 nodes.** With a single 1.21.1 node it is inert (nothing to switch
   between). So the system becomes *real* the moment a second NeoForge version is targeted — that's the first
   place seams and guards are actually exercised and tested.
8. **1.20.1 backports are a separate manual workflow** on `legacy/1.20.1`, outside the unified system (§7.2).

---

## 1. The phase model

| Phase | State | Scope | Definition of Done |
|---|---|---|---|
| **Phase 1** | ✅ DONE | Single-target **NeoForge 1.21.1** port (archived doc, Linear PGM-5…25). | `main` builds a 1.21.1 jar; 93/94 gametests. |
| **Phase 2** | ▶ CURRENT | Make the NeoForge tree **multi-version-capable** and prove it: stand up Stonecutter, pick the **first second NeoForge target**, build the seam layer for whatever diverges between 1.21.1 and that target, emit both jars. | Stonecutter runs; `1.21.1` node reproduces today's build (regression floor); the second node compiles + gametests; `chiseledBuild` emits both jars. |
| **Phase 3** | ⬜ ONGOING | The **forward-compat engine**: a runbook to add *any* subsequent version cheaply (seam layer accretes per version), a CI matrix over all nodes, and the **separate 1.20.1 backport runbook**. | Adding a version is documented + mechanical (proven on ≥1 more node); CI builds+gametests every node; `legacy/1.20.1` stays buildable with a written backport process. |

**The honest dependency (§0.7):** Phase 2's *first* decision is **which second NeoForge version to target**
(the next one you actually want — e.g. a current 1.21.x). Until one is picked, the tree is single-target 1.21.1
and Stonecutter buys nothing. Two ways to run Phase 2:
- **(Recommended) Pick the second target now** → Stonecutter + seams are immediately real and tested.
- **(Scaffold-and-wait)** stand up Stonecutter single-node + conventions now, defer the real seam work to the
  first new version. Lower immediate value (the old Stage-1 doc warned against single-node Stonecutter for
  exactly this reason), but valid if you don't want to commit a second target yet.

---

## 2. Target matrix (the "minor config change")

NeoForge only. Each cell is a **Stonecutter node** → one jar; the config change is `stonecutter.active "<node>"`
(dev) / `./gradlew chiseledBuild` (emit all).

| Node id | Minecraft | Loader | Build plugin | Source of divergent bodies |
|---|---|---|---|---|
| `1.21.1` | 1.21.1 | NeoForge 21.1.x | ModDevGradle | **= today's `main`** (the base) |
| `<second>` *(pick — Phase 2.0)* | e.g. a current 1.21.x | NeoForge 21.x | ModDevGradle | branch the churned seams from `1.21.1` |
| *future…* | 1.22, … | NeoForge | ModDevGradle | branch from nearest node |

`legacy/1.20.1` (Forge) is **out of this matrix** — separate branch, manual backports (§7.2).

---

## 3. How Stonecutter makes the switch (version axis only)

Version-divergent code lives inline, guarded by comments; for the active node Stonecutter comments out the
non-matching branch **before `javac`**, so absent symbols never reach the compiler:

```java
// stable code that compiles on every node …
//? if >=1.21.2 {
/* future API form */
//?} else {
currentApiForm();        // 1.21.1 base
//?}
```

- **One axis, one constant vocabulary:** guards key off the **Minecraft version** only (`>=1.21.2`, `<1.22`, …).
  No loader constant (everything is NeoForge). Keep the vocabulary tiny and centralized.
- **One build system:** **ModDevGradle** drives every node (all NeoForge) — the old "ModDevGradle vs ForgeGradle"
  question is **moot** now. Per-node MDG config (NeoForge version) is selected by the active Stonecutter node.
- **Pin** the exact Stonecutter + MDG versions/DSL at 2.1 against current upstream docs.

---

## 4. The seam layer — a forward-compat risk register, not an upfront build

Relative to the 1.21.1 base, the seam map is now a **forward-looking churn-risk register**: what is *likely* to
break in a *future* NeoForge/MC version, ranked, so we know where the abstraction will accrue. **We seam reactively**
(§0.6): add a node → compiler flags the divergence → seam exactly that.

| Surface | Future-version churn risk | Why |
|---|---|---|
| **Client / render** (`VertexConsumer`, render pipeline, screen/registration events) | **High** | The 1.21.2+/1.21.5 render-pipeline rewrites reshape `VertexConsumer` (already bitten once in Stage 1). The most likely first real seam. |
| **ItemData / data components** (codecs, component types) | **Medium** | Component codecs and the `createDataComponents` overloads shift across 1.21.x (e.g. single-arg removed in 1.21.2). |
| **BE persistence** (`saveAdditional`/load Provider, NbtIo) | **Medium** | Save/IO signatures occasionally re-touched between versions. |
| **Recipe / advancement / loot codecs** | **Medium** | The ingredient *string vs object* form changed in **1.21.2** (Stage 1 confirmed 1.21.1 still uses objects) — a guaranteed seam the moment we target ≥1.21.2. |
| **Registration, networking, capabilities, config** | **Low** | NeoForge-stable across 1.21.x; likely only churns at major boundaries (1.22). |
| **Data files** (folder names, JSON shapes, namespaces) | **Medium** | Per-version data shape (e.g. the 1.21.2 ingredient form) — handled per-node (§6.4). |
| Companions C1/C2/C7/C8/C9 | **Low** | One-time-resolved at 1.20.5/1.20.2; stable on 1.21.x. |

**Rule:** the smallest seam that contains the divergence (a guarded import/line beats an interface where the
difference is trivial). Don't abstract what hasn't diverged.

> Note: several of these (ingredient form, component overloads, render API) are **known** to change at
> **1.21.2+**. So if the second target is ≥1.21.2, expect those exact seams first — a useful, predictable
> shakedown of the system.

---

## 5. Code → branch mapping

- **`1.21.1` node = today's `main`.** It is the base; its bodies are the default branch of every seam.
- **A new version node** branches the *churned* seam bodies from the nearest existing node and fills what the
  compiler flags; everything non-divergent is shared automatically.
- **No Forge bodies in the tree.** `legacy/1.20.1` is separate (§7.2).

---

## 6. Phase 2 execution plan

Gates: **[AGENT]** headless / **[HUMAN]** in-world.

> ### ✅ Phase 2.1 spike — DONE & PROVEN (2026-06-26, commit `49ab52b`)
> The full forward toolchain is verified working together on branch `stage2/multi-version`:
> - **Gradle 8.8 → 9.6.1** (Stonecutter requires Gradle 9). **ModDevGradle compiles cleanly on Gradle 9** (verified).
> - **Stonecutter 0.9.6** (`dev.kikugie.stonecutter`, kikugie releases maven) applied with a single `1.21.1` node.
> - **Hard requirement discovered:** Stonecutter needs **Java 21 as Gradle's launcher JVM** (MDG only needed 21 for
>   the toolchain). Documented in `gradle.properties`. Use a JDK 21 `JAVA_HOME` / `org.gradle.java.home`.
> - **Layout fix:** Stonecutter runs each node as a subproject (`versions/<node>/`); pinned the `test` task
>   `workingDir` to `rootProject.projectDir` (the blueprint-audit tests use root-relative paths).
> - **Regression floor:** on the `1.21.1` node (Java 21), `./gradlew build` is **GREEN incl. JUnit** (76 pass /
>   3 gated-skip); `runGameTestServer` runs all **94**, **92 pass**. The 2 fails = the known iron-farm in-world
>   item **+ one new Stonecutter run-layout follow-up** (`curatedblueprintsinstallintoworldstore`: blueprints load
>   from the classpath fine, but the gametest server's world-store *write* under `versions/1.21.1/run/` fails —
>   a run-dir issue, not a mod regression). **Follow-up:** fix the gametest-server run-dir/world-store path under
>   the subproject layout to restore 93/94.
>
> **Net:** the build-system question is resolved (one toolchain: Gradle 9.6.1 + Java 21 + MDG + Stonecutter).
> The single node is an inert preprocessor until a 2nd NeoForge target is chosen (§2.0).

- **2.0 — Pick the second NeoForge target.** Decide the next version to support (§1). *This gates real seam work;*
  the 2.1 scaffold above is already done.
- **2.1 — Stonecutter + MDG scaffold; lock the regression floor.** ✅ **DONE (see spike box).** Remaining:
  fix the one run-layout gametest to get a clean 93/94.
- **2.2 — Add the second node; seam the divergences.** Declare the second node; run `compileJava` and let it
  **enumerate** what diverged; seam exactly those surfaces (per §4/§5), fill both branches. Iterate until both
  nodes compile. **[AGENT] Gate:** `compileJava` green on **both** active nodes.

> #### ▶ Phase 2.2 — core seam pass COMPLETE ✅ (2026-06-26)
> Second node = **1.21.8** (latest stable 1.21.x; crosses BOTH the 1.21.2 *and* 1.21.5 rewrite waves — the
> hardest possible target, chosen deliberately for max forward-compat). `:1.21.1` base stayed at **0 errors at
> every commit** (regression floor intact). `:1.21.8` core errors: **736 → 0**; main AND test source sets compile
> on both nodes, and **ONE tree now builds both jars** (`:1.21.8:assemble` + `:1.21.1:assemble` →
> `versions/<node>/build/libs/mc3dprint-0.10.0.jar`, 667K / 749K). All on `stage2/multi-version`.
>
> **Seam shims built** (`src/main/java/com/pgmacdesign/mc3dprint/compat/`):
> - **`NbtCompat`** — CompoundTag read API (1.21.5 Optional getters) + `getUUID/putUUID/hasUUID`,
>   `putBlockPos/getBlockPos` (UUIDUtil/BlockPos codecs replace removed `putUUID`/`NbtUtils.writeBlockPos`),
>   `keySet` (was `getAllKeys`), `getByteArray`, `listGetCompound` (ListTag.getCompound(int) now Optional).
> - **`BeData`** — BlockEntity persistence facade for the 1.21.5 `ValueOutput`/`ValueInput` rewrite. Each BE keeps
>   ONE version-agnostic `writeData(Writer)`/`readData(Reader)` body; only the `saveAdditional`/`loadAdditional`
>   signature wrapper is guarded. Backs <1.21.5 with `CompoundTag`+`Provider`, 1.21.5+ with `ValueOutput`/`ValueInput`.
> - **`InteractionCompat`** — the 1.21.2/1.21.4 interaction-result unification (`ItemInteractionResult`/
>   `InteractionResultHolder` → `InteractionResult`; `sidedSuccess` dropped). `ITEM_*`/`holder*` constants whose
>   static TYPE tracks the version; only the `useItemOn`/`use` override RETURN-TYPE line needs a per-method guard.
>
> **Seams DONE (both nodes green):** ✅ **persistence** — all 9 BlockEntities migrated to `BeData`, plus the
> 1.21.5 client-sync churn (`handleUpdateTag(ValueInput)`, `onDataPacket` arg drop; Printer nests its render payload
> under one key so the `ValueInput` side recovers it via `CompoundTag.CODEC`). ✅ **interaction + block API** — all
> block/item files: `useItemOn` return type, `sidedSuccess`, `onRemove`→`affectNeighborsAfterRemoval`,
> `DirectionProperty`→`EnumProperty<Direction>`, `Item.use`/`ScannerItem.scan` `InteractionResultHolder`→`InteractionResult`.
>
> ✅ **raw-NBT sweep DONE** — `getUUID/putUUID`, `writeBlockPos/readBlockPos`, `getAllKeys`→`keySet`, `getByteArray`,
> `getAsString`→`tagAsString`, and ListTag `getInt/getDouble/getList(int)` (also Optional on 1.21.5) routed through
> NbtCompat across PrintJob, RepositoryIndex/Data, RepoEntry, BlueprintSerializer, Vanilla/SpongeImporter, RemoteTerminal.
>
> ✅ **registry + event-bus DONE** — `RegistryCompat.blockEntityType` (Builder.of→ctor), `@EventBusSubscriber`
> `bus=` dropped (6 sites), `RegistryCompat.item/block` (get→getValue), `ServerPlayer.server`→`getServer`.
> ✅ **render DONE** — `RenderCompat` (blit/blitColored/tooltip*), `pose()`→`Matrix3x2fStack` per-site guards,
> `BlockEntityRenderer.render(…,Vec3)`, `renderLineBox`→`ShapeRenderer`, `shouldRenderOffScreen()` arg drop,
> `model.data` move, ghost-block `RenderType.translucent()`→`translucentMovingBlock()` + `renderSingleBlock`
> tail `(ModelData,RenderType)`→`(BlockAndTintGetter,BlockPos)`. **[HUMAN] verify the print-preview ghost visually.**
>
> ✅ **recipe API DONE** — `RecipeCompat` (ingredients via `placementInfo().ingredients()`, items via
> `Ingredient.items()`); `bind()` carries a version-neutral `Collection<RecipeHolder<?>>` and `MinecraftRecipeIndex`
> filters by `Recipe.getType()` (replacing the moved `getAllRecipesFor`); result via uniform `assemble(EMPTY_INPUT)`
> (= removed `getResultItem`, both nodes); `RecipesUpdatedEvent`→`RecipesReceivedEvent`/`getRecipeMap`. The
> correctness-sensitive FU valuator (`RecipeFuValuator`) was left untouched. **[PORT]** 1.21.5+ stops syncing recipes
> to clients by default (server must opt in via `OnDatapackSyncEvent#sendRecipes`) → remote-client FU tooltips degrade;
> single-player unaffected (integrated server's own bind holds the full set). Flagged in `FuClientBinding`.
> ✅ **SavedData DONE** — `RepositoryData`: `SavedData.Factory`+`save(CompoundTag)` → `SavedDataType` + a
> `CompoundTag.CODEC.xmap` codec; imperative load/save bodies unchanged, on-disk shape byte-identical.
> ✅ **item / BE data DONE** — `saveWithId`/`entity.save`/`saveWithoutMetadata`/`setBlockEntityData` →
> `TagValueOutput.createWithContext`+`buildResult` (ScanOperation, RemoteTerminal, Controller); `loadWithComponents`
> via `BeData.loadInto` (TagValueInput); `ItemStack.parseOptional`→`NbtCompat.parseItemStack` (ItemStack.CODEC).
> ✅ **entity-spawn DONE** — `loadEntityRecursive` +`EntitySpawnReason.LOAD`, `moveTo`→`snapTo`,
> `EntityType.BOAT`→`instanceof Boat` (Printer structure-print).
> ✅ **fuel DONE** — `FuelCompat` (`getBurnTime(RecipeType,FuelValues)`; static `isFuel` rebuilds the table via
> `DataMapHooks.populateFuelValues(boundRegistries)`); `getCraftingRemainingItem`→`getCraftingRemainder`.
> ✅ **tooltips DONE** — `TooltipCompat.sink` (appendHoverText `List<Component>`→`Consumer<Component>` via a
> write-only forwarding List — bodies verbatim incl. early returns) across 7 Item sites. **[PORT]** the 3 BLOCK-level
> tooltips (RedstoneClock/ClockGenerator/CreativeEnergy) are DROPPED on 1.21.8 — `Block.appendHoverText` was removed
> entirely in 1.21.5; restore via a `TooltipBlockItem` (BlockItem subclass) + ModItems registration. Flagged in-code.
> ✅ **block signatures DONE** — `updateShape` param reorder (MC3DCable), `neighborChanged` `BlockPos`→`Orientation`
> (PrinterBlock, Controller — fromPos was unused). ✅ **render tail DONE** — `RenderLevelStageEvent.Stage`→nested
> `AfterTranslucentBlocks` event, `renderLineBox`→`ShapeRenderer` (ScannerSelectionRenderer). ✅ **misc renames DONE** —
> `AnvilUpdateEvent.setCost`→`setXpCost`, `LootContext.getParamOrNull`→`getOptionalParameter`,
> `WorldVersion.getDataVersion().getVersion()`→`dataVersion().version()`. ✅ **JUnit tests DONE** — getList/getIntArray
> routed through NbtCompat.
>
> **[HUMAN] in-world verification deferred** (per "test later"): structure-print of BEs (signs/chests) + decorative
> entities (armor stands/item frames/boats) across all 4 rotations + mirror; blueprint-repository persistence across
> restart; clock-generator fuel-slot validity; print-preview ghost. Risks documented in the shim javadocs.
>
> #### ▶ Phase 2.3 — runtime verification (2026-06-26): mod LOADS on 1.21.8 ✅
> `runGameTestServer` caught two runtime bugs that compile-green hid (commit `0668396`):
> 1. **Registration crash (was fatal).** 1.21.2 made the registry id MANDATORY on every `Block`/`Item` `Properties` —
>    `Properties.effectiveDrops` throws *"Block id not set"* at construction, cascading into an unbound-holder NPE in
>    `ModItems`. Fix: `ModBlocks`/`ModItems` use `DeferredRegister.createBlocks/createItems` +
>    `registerBlock`/`registerItem(name, factory, props)`, which stamp the id via `props.setId(key)` before the
>    factory runs. The helper is a no-op on 1.21.1, so the call sites are **unguarded and correct on both nodes**.
> 2. **JUnit raw-NBT assertions.** `SpongeSchematicTest`/`VanillaStructureImporterTest` asserted against
>    `be.getString("id")`, which returns `Optional` on 1.21.5 — compiles via `assertEquals(Object,Object)`, fails at
>    runtime (`"Optional[minecraft:chest]"`). Routed through `NbtCompat`. **Lesson: `compileTestJava` green ≠ tests
>    pass; run `:NODE:test`.** Added `TooltipCompatTest`. Both nodes: compileJava + compileTestJava + test all green.
>
> **OPEN [PORT] — gametests deferred on forward nodes (the next real task):** the 1.21.8 `runGameTestServer` runs
> only 1 test (a vanilla default) because the 21 holders are **deliberately excluded** from the forward-node compile —
> `build.gradle:158`: `if (stonecutter.current.project != '1.21.1') { compileJava exclude '**/gametest/**' }` (the
> `TEMP (Phase 2.2)` exclusion). Root cause: **NeoForge 21.8 removed `@GameTestHolder` + `@PrefixGameTestTemplate`**
> (`net.neoforged.neoforge.gametest` now only has `GameTestHooks`/`BlockPosValueConverter`); the 1.21.5 rewrite made
> GameTest **data-driven** — new vanilla `GameTestInstance` / `TestData` / `TestEnvironmentDefinition`.
>
> **Recipe (researched + de-risked):** per test method, register its `Consumer<GameTestHelper>` to
> `Registries.TEST_FUNCTION` (`TestFunctionLoader.registerLoader`), then register a
> `FunctionGameTestInstance(functionKey, TestData)` via the mod-bus `net.neoforged.neoforge.event.RegisterGameTestsEvent`
> (`registerTest`/`registerEnvironment` — NeoForge kept a code path, no JSON). Annotations stay `//? if <1.21.5`; the
> registration table is `//? if >=1.21.5`; then drop the exclusion.
>
> **Measured cost (2026-06-26): ~890 compile errors** when the exclusion is lifted — it is NOT just registration. The
> test *bodies* churned hard: `GameTestAssertException` now takes `Component` not `String` (~400 errors),
> `GameTestHelper.getBlockEntity` changed (~80), Optional getters, etc. And it's **all-or-nothing** (the glob is
> `**/gametest/**`). This is the single biggest remaining piece — on par with a large slice of the 736-error core pass —
> and it's regression *tooling* on a node now 5 releases behind latest (26.2). **Recommendation: defer it** (or do it
> once against the eventual ship-target version). 1.21.8 is shippable without it — the 1.21.1 base node runs all 94
> gametests as the regression oracle, and the [HUMAN] soak is the real functional gate.
> **NEXT for a shippable 1.21.8:** only the [HUMAN] in-world soak. Strategically, the next *forward* target is likely 26.2, not 1.21.8.
>
> **Established conventions for the remaining fan-out:**
> - Files edited while **active node = `1.21.1`** → plain code is 1.21.1, the 1.21.5+ variant goes in
>   `//? if >=1.21.5 { /* … */ //?} else { <plain> //?}`. After writing NEW guards, re-run *Set active project to <node>*
>   so Stonecutter re-toggles the new file before compiling.
> - A type that exists ONLY on 1.21.1 (`ItemInteractionResult`, `InteractionResultHolder`) → its **import must be
>   guarded** `//? if <1.21.5 {`.
> - Parallel agents do **source-only edits, NO gradle** (shared Stonecutter active-node state); the orchestrator
>   runs the single integration compile + fixes residuals. Partition by file to avoid write conflicts.
> - Commit cadence: reset active to `1.21.1` (= vcsVersion) before every commit so the tree is in canonical form.
>
> **Compat shims (`…/compat/`):** `NbtCompat`, `BeData`, `InteractionCompat`, `RegistryCompat`, `RenderCompat`,
> `RecipeCompat`, `FuelCompat`, `TooltipCompat`.
> **Commits (branch `stage2/multi-version`):** `77cee40` NBT call-sites · `b88adc1` BeData facade · `189fd00`
> all-BE persistence · `863b76a` InteractionCompat · `8ac6b2c` block/item interaction · `18d1989`
> FilamentConverterBlock · `499f7d6` raw-NBT sweep · `b7bdba9` registry+event-bus · `a6e925e` RenderCompat+WinderScreen ·
> `b855313` all screens+renderers · `f8fe024` registry-lookup+ServerPlayer.
- **2.3 — Data per node.** Reconcile any data-shape differences between the two NeoForge versions (e.g. the
  1.21.2 ingredient form). **[AGENT] Gate:** each node's datapack loads with **zero** parse errors.
- **2.4 — Gametest parity on both nodes.** **[AGENT] Gate:** each node prints its baseline count and passes
  (1.21.1 = 93/94 until the iron-farm in-world item; the second node at its own baseline).
- **2.5 — Emit both jars.** `./gradlew chiseledBuild`. **[HUMAN] Gate:** each jar loads in its target client; a
  survival scan→print works on both.

### 6.4 Data files across versions
Within 1.21.x the data shape is mostly stable; the known break is the **1.21.2 recipe-ingredient form**
(object→string) and similar. Prefer **per-node resource roots** (share what's identical, override what differs)
over standing up datagen (the mod deliberately has none). Decide concretely at 2.3.

---

## 7. Phase 3 — the forward-compat engine

### 7.1 The "add a version" runbook (`docs/port/RUNBOOK-add-a-version.md`)
The exact, ordered steps to add a NeoForge node: declare the Stonecutter node + bump deps → run `compileJava`
and let failures **enumerate** the churned seams → fill only those seam branches → reconcile data → gametest →
`chiseledBuild`. With a worked example from the Phase-2 second node. **Churn-localization guarantee:** evidence
the new version touched only seam files + data, never the ~125 core files — if it didn't, that's a missing seam.

### 7.2 The "backport to 1.20.1" runbook (separate, manual)
`legacy/1.20.1` (Forge) is **not** a Stonecutter node. Backporting:
- **Data / blueprints / FU-compat hooks:** copy the file onto `legacy/1.20.1` (loader-agnostic — cheap, near-free).
- **Java changes:** hand-port to Forge-1.20.1 API terms on the branch (no shared-code mechanism exists across the
  loader+version gap — that's why it's out of the unified tree).
- Keep `legacy/1.20.1` **buildable** (it still compiles green on Java 17 / ForgeGradle) so backports can ship.

### 7.3 CI matrix
`chiseledBuild` + per-node `runGameTestServer` in CI for every NeoForge node (Java 21). Optionally a separate
`legacy/1.20.1` Forge build+gametest job (Java 17) so backports don't rot.

---

## 8. Open decisions / levers

- **8.1 — RESOLVED:** Drop Forge; NeoForge-only; 1.20.1 = separate `legacy/1.20.1` backport branch.
- **8.2 — Second NeoForge target (Phase 2.0):** which version (e.g. a current 1.21.x). The single open input.
  If ≥1.21.2, expect the ingredient-form / component-overload / render seams first.
- **8.3 — Scaffold-now vs wait-for-second-target (§1):** recommend pick-a-target so Stonecutter is real.
- **8.4 — Inherited Stage-1 TODOs** (fold each into the seam where it lives): iron-farm gametest (PGM-23,
  in-world), real Patchouli 1.21 component API (C5), JEI-19 `getBackground` deprecation (C6), the
  `BlockCapabilityCache` cable optimization (decision 5.2).

---

## 9. Definition of done

- One **NeoForge** source tree on `stage2/multi-version` → `main`. `./gradlew chiseledBuild` emits a correct jar
  for every node (`1.21.1` + each chosen newer version). Adding a node is documented + mechanical (§7.1), proven.
- Version divergence is localized behind seams guarded by Stonecutter; the core ~125 files are version-agnostic.
- Each node passes its gametest baseline (1.21.1 → 94/94 once the iron-farm in-world item is fixed).
- `legacy/1.20.1` stays buildable with a written backport process (§7.2); it is explicitly *not* in the unified tree.
- Zero mixins/ATs/datagen introduced beyond what's justified. No `Co-Authored-By: Claude` / "Generated with Claude Code".
- Doc surfaces (Patchouli guide + website guide) verified against the shipped node(s), not assumed.

---

## Appendix — relationship to the Stage-1 doc
`docs/port/archive/stage1-neoforge-1.21.1-port-COMPLETE.md` is the completed single-target port and the detailed
reference for every per-surface API change (the §3.x seam bodies, C1–C9 companions, exact file:line inventory).
This doc reuses that as the content of the `1.21.1` base node and adds the **multi-version architecture, the
Stonecutter mechanism, and the forward-compat process** on top. The archived doc's loader-axis material
(Forge↔NeoForge) is **no longer in scope** — kept only as reference for the separate `legacy/1.20.1` line.
