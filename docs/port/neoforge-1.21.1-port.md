# MC3DPrint — NeoForge 1.21.1 Port Design

**Status:** Design / not started · **Author:** PGMacDesign (drafted for agent handoff)
**Revision:** v2 — hardened after an adversarial red-team/blue-team review (21 verified findings folded in;
see the Appendix changelog).
**Target:** NeoForge **1.21.1** (Java 21). The existing Forge **1.20.1** (Java 17) build **freezes on a
`legacy/1.20.1` branch** and keeps shipping as-is.
**Scope of THIS doc:** **Stage 1 — a single-target NeoForge 1.21.1 port** of one source tree, with each
version-divergent surface pulled behind an internal seam. **Stage 2** (multi-version reunification of
1.20.1 + 1.21.1 from one tree via Stonecutter) is sketched in §1.4 and deferred to its own doc — the
seams built here are what make it cheap.

> This doc is meant to be **executable by a team of agents** (with a human verifier for in-world gates —
> see §6). Every code block is grounded in the *actual* current code (file:line cited) and is the
> *intended shape*, not copy-paste-final. Re-read the real code at execution time; it may have moved.

---

## 0. Load-bearing facts (read first)

1. **Mods are version-locked, not forward-compatible.** A 1.21.1 jar will not run on 1.21.2+. "Support
   newer versions" = one deliberate port to a chosen target. The community-stable targets are **1.20.1**
   (legacy LTS) and **1.21.1** (modern LTS). We target 1.21.1; 1.20.1 stays alive, frozen.
2. **A single source tree cannot compile against two Minecraft versions at once.** `ItemStack.getTag()`
   exists in 1.20.1 and is *deleted* in 1.21.1; `saveAdditional`, `RecipeHolder`, `ResourceLocation`'s
   constructor, the advancement/criteria API, and more all changed. So a "shared `common` module across
   1.20.1 and 1.21.1" is **impossible** — it would reference vanilla symbols that don't exist in one of
   the two. **This is why Stage 1 is single-target** (one tree → 1.21.1), and multi-version waits for
   Stonecutter (Stage 2, §1.4), which strips the non-matching branch *before* `javac`.
3. **Forge is effectively displaced on 1.21+**; the modern ecosystem is **NeoForge**. Forward = NeoForge.
4. **The hard work is structural API *deletions*, not renames:** the **1.20.5 data-components rewrite**
   (item NBT deleted), the **Forge→NeoForge capability model inversion**, the **1.20.2 networking
   rework**, and the **advancement/criteria rewrite**. Each can't hide behind a runtime `if` (the missing
   symbol fails `javac`) — the fix is to localize it behind a seam.
5. **Our blast radius is unusually contained.** Audit confirms **zero mixins, zero access transformers,
   zero datagen code**; creative tabs already on the modern API. Three of the usual worst surfaces are
   absent. Cost concentrates in **item data + capabilities + storage handles + BE persistence**.
6. **We are on official Mojang mappings (mojmap).** Forge 1.20.1 and NeoForge both use mojmap in source,
   so the port is **API-shape changes only, zero identifier renaming** — a large saving.

---

## 1. Strategy

### 1.1 Two stages — and why this doc is only Stage 1
- **Stage 1 (this doc):** convert one source tree to **NeoForge 1.21.1**. Pull every version/loader-divergent
  surface behind an **internal seam** (an interface + the NeoForge impl). One compile target, so the
  "common can't see two vanilla versions" problem (§0.2) never arises. The seams are clean architecture
  that *also* makes Stage 2 cheap.
- **Stage 2 (deferred, §1.4):** reunify 1.20.1 + 1.21.1 into one tree with **Stonecutter** (version axis)
  so both jars build from shared source. The seams from Stage 1 become the toggle points; today's 1.20.1
  code (preserved on `legacy/1.20.1`) becomes the `//? if <1.20.5` branch of each seam.

**The 1.20.1 build during Stage 1:** frozen on `legacy/1.20.1`. It keeps shipping unchanged and is the
**behavioral regression oracle** — Stage 1's NeoForge build must reproduce its behavior (verified by
porting the ~94 gametests + 134-build curated set to run on 1.21.1, see §6).

### 1.2 The seam philosophy
Each divergent surface is pulled behind a small **core interface**; the rest of the mod calls only that
interface and the stable Minecraft API. In Stage 1 there is exactly **one implementation** per seam (the
NeoForge one). The seam still earns its place: it localizes the version-specific code to one file, which
(a) keeps the ~125 other files clean and (b) is precisely the cut-point Stonecutter needs in Stage 2.

> A runtime `BUILD_VERSION` enum the code switches on is impossible — a deleted symbol fails `javac`
> regardless of any runtime branch (§0.2/§0.4). Seams + (later) conditional compilation are the only
> symbol-safe options.

### 1.3 The full surface map (seams + companion migrations)
The original draft claimed "six seams = the whole port." The review found that undercounts. The real map:

| # | Surface | Hides / changes | Severity | Section |
|---|---|---|---|---|
| 1 | **ItemData** | item NBT ↔ data components | High (wide) | §3.1 |
| 2 | **Capabilities (energy + FU + item-handler)** | Forge caps/`LazyOptional` ↔ NeoForge `BlockCapability` | **High (true rewrite)** | §3.2 |
| 3 | **Net** | `SimpleChannel` ↔ `CustomPacketPayload` | Medium | §3.3 |
| 4 | **Registration + mod-entry/event-bus** | `RegistryObject`↔`DeferredHolder`; `IForgeMenuType`↔`IMenuTypeExtension`; `@Mod` ctor; `@EventBusSubscriber` | Medium | §3.4 |
| 5 | **BE persistence** | `saveAdditional(tag)` ↔ `(tag, HolderLookup.Provider)` | Medium-High (all 11 BEs) | §3.5 |
| 6 | **Client/render** | `MenuScreens.register` ↔ `RegisterMenuScreensEvent`; vertex emission | Medium (1.21.5 risk) | §3.6 |
| 7 | **Storage handles** | `net.minecraftforge.{energy,items}.*` ↔ `net.neoforged.neoforge.{energy,items}.*` | Low (package swap, single-target) | §3.5/§3.7 |
| C1 | **`BlockEntityTag` item convention** | ↔ `DataComponents.BLOCK_ENTITY_DATA` | Medium | §3.7 |
| C2 | **Advancement criteria** | pre-1.20.2 trigger API ↔ `Codec`/`SimpleInstance`/`TRIGGER_TYPE` | Medium (true rewrite) | §3.7 |
| C3 | **Config spec** | `ForgeConfigSpec` ↔ `ModConfigSpec` | Medium (rename) | §3.7 |
| C4 | **Recipe-derivation adapter** | `getAllRecipesFor` ↔ `RecipeHolder`/`RecipeInput` | Medium | §3.7 |
| C5 | **Patchouli book stamp** | `getOrCreateTag().putString("patchouli:book")` ↔ Patchouli component API | Low (soft-dep) | §3.7 |
| C6 | **JEI plugin body** | `ForgeRegistries`+`fml` imports + JEI 15→19 API | Medium | §3.7 |
| C7 | **FU public API + IMC ingress** | Forge lifecycle/event packages ↔ NeoForge | Medium (public contract) | §3.7 |

**Single-target simplification (vs the v1 draft):** because Stage 1 has one compile target, surface 7
collapses to a **package import swap** (`net.minecraftforge.energy/items.*` → `net.neoforged.neoforge.*`);
the API surface is drop-in-compatible (same ctors, same overridable methods — verified). No mod-owned
`EnergyTank`/`ItemSlots` abstraction is needed *for Stage 1*. (Stage 2 is where, *if* you share these
across loaders, you either Stonecutter-guard the import or introduce the wrapper — decide then, §1.4.)

### 1.4 Stage 2 sketch (deferred — its own doc)
Once Stage 1 ships, reunify with **Stonecutter** (preprocessor, version axis) on the single NeoForge
project, declaring `1.20.1` and `1.21.1` versions. Each seam's body gets a `//? if <1.20.5 { …old… }
//?} else { …new… }` guard; the `legacy/1.20.1` code supplies the old branch. If you also want to keep
the **Forge** loader for 1.20.1 (vs NeoForge-1.20.1), add the loader axis then (MultiLoader-Template or
Stonecutter loader constants) — and that is when surface 7 may need a mod-owned wrapper or a guarded
import. **Do not stand up Stonecutter or any multi-loader split during Stage 1** — it adds a matrix you
can't yet test and buys nothing until a second target exists.

---

## 2. Stage 1 project setup

**No multi-project split.** Stage 1 is the **existing single Gradle project, converted in place on a
`port/neoforge-1.21.1` branch**, from ForgeGradle 6 / Forge 47.4.10 / Java 17 → **ModDevGradle / NeoForge
21.1.x / Java 21**. `legacy/1.20.1` holds the frozen Forge build.

### 2.1 `build.gradle` (key changes)
```groovy
plugins { id 'net.neoforged.moddev' version '2.+' }      // was net.minecraftforge.gradle
java.toolchain.languageVersion = JavaLanguageVersion.of(21)   // was 17 — 1.21.1 requires Java 21
neoForge {
    version = "21.1.92"                                  // pin a current 1.21.1 NeoForge build at port time
    runs {
        client       { client() }
        server       { server() }
        gameTestServer {
            type = "gameTestServer"
            systemProperty 'neoforge.enabledGameTestNamespaces', mod_id   // NB: neoforge. prefix
        }
    }
    mods { mc3dprint { sourceSet sourceSets.main } }
}
repositories { maven { url = "https://maven.blamejared.com/" } }
dependencies {
    // JEI: version family AND artifact id change (forge -> neoforge)
    compileOnly "mezz.jei:jei-1.21.1-common-api:19.+"
    compileOnly "mezz.jei:jei-1.21.1-neoforge-api:19.+"
    runtimeOnly "mezz.jei:jei-1.21.1-neoforge:19.+"
}
```
> **`neoforge.enabledGameTestNamespaces`** (note the `neoforge.` prefix, replacing the current
> `forge.enabledGameTestNamespaces` at `build.gradle:86,90,98`) must be set on **client, server, AND
> gameTestServer** runs. If the namespace isn't enabled, **zero** gametests register, zero fail, exit 0 —
> `runGameTestServer` reports green while running nothing. The Phase-4 gate (§6) guards against this.

### 2.2 `META-INF/mods.toml` → `META-INF/neoforge.mods.toml`
```toml
modLoader="javafml"
loaderVersion="[4,)"          # NeoForge FML loader range (not Forge's [47,))
license="MIT"
[[mods]]
modId="mc3dprint"
version="${mod_version}"
displayName="MC3DPrint"
authors="PGMacDesign"
[[dependencies.mc3dprint]]
    modId="neoforge"
    type="required"           # 1.21.x uses type=required/optional, not mandatory=true
    versionRange="[21.1,)"
    ordering="NONE"
    side="BOTH"
[[dependencies.mc3dprint]]
    modId="minecraft"
    type="required"
    versionRange="[1.21.1,1.21.2)"
    ordering="NONE"
    side="BOTH"
[[dependencies.mc3dprint]]
    modId="patchouli"
    type="optional"
    versionRange="[0,)"
    side="BOTH"
```

### 2.3 Toolchain notes
- **Java 17 → 21** is mandatory for 1.21.1. Update CI and the Prism instance’s Java.
- **JEI coordinates change both the version family (15.x→19.x) and the artifact id (`forge`→`neoforge`).**
- **Drop the dead `data` run config** — it's the template default; no `GatherDataEvent` handler exists.
- **No mixins / ATs / datagen** — confirmed absent; keep it that way.

---

## 3. The seams (interface + NeoForge impl)

> For each seam, the existing 1.20.1 code (on `legacy/1.20.1`) is the future `//? if <1.20.5` branch for
> Stage 2 — so the Forge-side bodies are documented for that future, but **Stage 1 only ships the NeoForge
> impl**. Keep the existing static helper methods (`BlueprintDiscItem.getBlueprintId`, `SpoolItem.getFu`,
> …) as the public API and re-point their *bodies* at the seam, so the ~18 `PrinterBlockEntity` call sites
> and the repository/loot/import/test sites need **zero edits**.

### 3.1 Seam 1 — ItemData (item NBT → data components)

> **The blueprint blob is NOT on the ItemStack.** It lives on disk in `BlueprintFileStore`
> (`world/mc3dprint/blueprints/<uuid>.blueprint` via `NbtIo.writeCompressed`), keyed by UUID
> (`BlueprintDiscItem.java:33-35`, `BlueprintFileStore.java:21-22`). The disc carries only a **UUID
> reference + cached metadata** (9 scalar/array fields). `NbtIo.writeCompressed/readCompressed` ports to
> 1.21.1 unchanged — **no component for the blob.**
>
> **Resin and Upgrade items carry ZERO stack NBT** (`ResinItem.java:53-54`, `UpgradeItem.java:42` — `final`
> fields, one registered item per combination). They need **no** accessor and port for free.

**Raw NBT call-site inventory (≈32 sites — see also the two Patchouli writes in §3.7/C5, which are NOT
disc/spool/scanner data):** `BlueprintDiscItem.java:76,88,97-104,117,127-147,377,391,408-427`;
`SpoolItem.java:56,61`; `ScannerItem.java:59-64,84-86,168-172` + the client read at
`ScannerSelectionRenderer.java:45-49`. Two raw outliers to also fix: `StructurePrintGameTests.java:377`,
`BlueprintRepositoryBlockEntity.java:89,155-160`.

**Core interface** (`core/data/ItemData.java`): `readBlueprint→Optional<BlueprintRef>`, `writeBlueprint`
(false if locked), `clearBlueprint`, `isLocked`/`setLocked`, `readFu`/`writeFu`, `readScannerSelection`/
`writeScannerCorner`/`clearScannerSelection`. (Full interface body as in v1; unchanged.)

**NeoForge impl** — one `DeferredRegister.createDataComponents("mc3dprint")`, the disc's 9 fields wrapped
in **one** `BlueprintData` component (atomic), plus `LOCKED` (Boolean), `FU` (Integer), `SCAN`. The
`Codec` is a straight `RecordCodecBuilder` (10 `fieldOf`s, `resinTargetMask` as `optionalFieldOf(-1)` to
preserve the legacy "unknown → assume beneficial" default).

**The StreamCodec — hand-write it, do NOT use `composite`.** `BlueprintData` has **10** fields. Vanilla
`StreamCodec.composite` tops out at **6** codec/getter pairs and `NeoForgeStreamCodecs.composite` at **7**
(fixed overload counts on the 1.21.1 target — *not* a mappings thing). Ten fields exceed both, so write
it by hand over `RegistryFriendlyByteBuf` (mirrors the legacy `CompoundTag` fields 1:1):
```java
public static final StreamCodec<RegistryFriendlyByteBuf, BlueprintData> STREAM_CODEC =
    new StreamCodec<>() {
        public BlueprintData decode(RegistryFriendlyByteBuf b) {
            UUID id = b.readUUID(); String name = b.readUtf();
            int sx=b.readVarInt(), sy=b.readVarInt(), sz=b.readVarInt();
            int bc=b.readVarInt(), tier=b.readVarInt(), cost=b.readVarInt();
            boolean pc=b.readBoolean(); int mask=b.readVarInt();
            return new BlueprintData(id,name,sx,sy,sz,bc,tier,cost,pc,mask);
        }
        public void encode(RegistryFriendlyByteBuf b, BlueprintData d) {
            b.writeUUID(d.id()); b.writeUtf(d.name());
            b.writeVarInt(d.sizeX()); b.writeVarInt(d.sizeY()); b.writeVarInt(d.sizeZ());
            b.writeVarInt(d.blockCount()); b.writeVarInt(d.tier()); b.writeVarInt(d.printCost());
            b.writeBoolean(d.playerCreated()); b.writeVarInt(d.resinTargetMask());
        }
    };
```
(The same cap bites `RepoEntry` at 9 fields in §3.3 — same hand-written fix. The cap rule lives here.)
Registration: `.registerComponentType("blueprint", b -> b.persistent(CODEC).networkSynchronized(STREAM_CODEC))`.
Read/write becomes `disc.get/set/remove(BLUEPRINT)`, `spool.getOrDefault/set(FU)`; **absence of the
`BLUEPRINT` component** is the "empty disc" signal. Keep the `instanceof SpoolItem && !creative()` guard so
`CreativeSpoolItem` never gets an `FU` component. Components must be **immutable + value-equal**.

### 3.2 Seam 2 — Capabilities (energy + FU + **item-handler**) — the true rewrite

NeoForge has **no `LazyOptional`, no `Capability` token**; BEs do **not** override `getCapability`. Caps
are `BlockCapability<T, Direction>` registered externally in `RegisterCapabilitiesEvent`; queries return
`@Nullable T`. Delete all 8 `getCapability`/`invalidateCaps` overrides; each BE keeps only its storage
object + an accessor.

**Three cap dimensions (v1 missed ITEM_HANDLER — it's load-bearing):**
- **ENERGY** — 7 BEs expose (Cable, Printer, FilamentConverter, Winder, ClockGenerator, CreativeEnergy,
  Casing-forwards). Built-in `Capabilities.EnergyStorage.BLOCK`.
- **FILAMENT_SOURCE** (custom) — Cable + FilamentRack. Becomes
  `BlockCapability.createSided(rl("filament_source"), IFilamentSource.class)`.
- **ITEM_HANDLER** — **exposed** by `PrinterBlockEntity:2060-2073` (per-face: UP=input, DOWN=output,
  null=all), `WinderBlockEntity:228-229` (null=all, else=input), `ClockGeneratorBlockEntity:240-241`
  (fuel); **queried cross-BE** by `FilamentConverterBlockEntity:137-138` (raids neighbor inventories —
  core converter function) and `ClockGeneratorBlock:59` (drops fuel on break). Built-in
  `Capabilities.ItemHandler.BLOCK`. **Omitting this ships a silently-broken converter + dead hopper I/O
  faces** — and the v1 Phase-3b gate couldn't catch it.

**Core interfaces** (`caps/`):
```java
public interface CapabilityRegistrar {                        // called once at setup
    <T extends BlockEntity> void registerEnergy(BlockEntityType<T> t, BiFunction<T,Direction,IEnergyStorage> p);
    <T extends BlockEntity> void registerFilament(BlockEntityType<T> t, BiFunction<T,Direction,IFilamentSource> p);
    <T extends BlockEntity> void registerItemHandler(BlockEntityType<T> t, BiFunction<T,Direction,IItemHandler> p);
}
public interface CapAccess {                                  // replaces every be.getCapability(...).orElse(null)
    Optional<IEnergyStorage> energyAt(Level l, BlockPos p, Direction side);
    Optional<IFilamentSource> filamentAt(Level l, BlockPos p, Direction side);
    Optional<IItemHandler>    itemHandlerAt(Level l, BlockPos p, Direction side);
    Supplier<IEnergyStorage>  energyCacheAt(Level l, BlockPos p, Direction side);  // hot path, see gotcha
    void invalidate(Level l, BlockPos p);
}
```
`registerAll(r)` registers all three dimensions (energy ×7, filament ×2, item-handler ×3 with the
printer's per-face `Direction` branch preserved). NeoForge impl: `event.registerBlockEntity(cap, type,
provider::apply)`; queries via `level.getCapability(cap, pos, side)`; invalidation
`level.invalidateCapabilities(pos)`. Reroute the converter pull (`:137`) and clock-break drop (`:59`)
through `itemHandlerAt`.

**Storage handles (surface 7):** `MachineEnergyStorage`/`CableEnergyStorage` `extends EnergyStorage` and
the `ItemStackHandler` fields just swap package `net.minecraftforge.{energy,items}.*` →
`net.neoforged.neoforge.{energy,items}.*` (same ctors/fields/overridable methods — verified). `IEnergyStorage`/
`ItemStackHandler`/`SlotItemHandler`/`RangedWrapper` are API-compatible drop-ins on 1.21.1. **Single-target
⇒ import swap only, no abstraction.** (Stage 2 cross-loader sharing is where a wrapper or guarded import
would be decided — §1.4.)

**Hardest single thing — the cable's per-tick neighbor push** (`MC3DCableBlockEntity.transferEnergy`,
:108-151). Today it re-does `getBlockEntity(pos).getCapability(ENERGY,face)` each tick over a throttled
position cache (`energyFaces`, refreshed every `RECOMPUTE_INTERVAL=100` ticks). The idiomatic NeoForge
per-tick path is `BlockCapabilityCache<IEnergyStorage,Direction>` (auto-invalidating, needs `ServerLevel`,
holds a listener you must drop on refloods). Hide it behind `CapAccess.energyCacheAt(...)` returning a
`Supplier<IEnergyStorage>`. **Verify the reflood path clears the cache map** (else listener leak — §5).

### 3.3 Seam 3 — Net (SimpleChannel → CustomPacketPayload)

Exactly **one packet, S2C only** (no `sendToServer` anywhere — GUI actions go through vanilla
`containerMenu`). Channel `MC3DPrintNetwork.java:17-19`; packet `RepositoryListingPacket:19-52`; only send
site `BlueprintRepositoryBlockEntity.java:73-76`; client sink `ClientRepositoryHandler.apply`.

**Core facade — stated in loader-neutral terms** (the v1 prose gloss was ambiguous):
```java
public interface Net {
    <T> void register(Class<T> type, BiConsumer<T, FriendlyByteBuf> encode,
                      Function<FriendlyByteBuf, T> decode, Consumer<T> clientHandler);
    void sendToPlayer(ServerPlayer p, Object msg);
}
```
The `CustomPacketPayload.Type` / `StreamCodec` construction and the `RegistryFriendlyByteBuf` upcast are
**confined to the NeoForge impl** (`RegistryFriendlyByteBuf extends FriendlyByteBuf`, so the plain
signature compiles). NeoForge impl: payload `record … implements CustomPacketPayload` with a `Type` +
`StreamCodec`, registered via `RegisterPayloadHandlersEvent` → `registrar("1").playToClient(...)`, sent via
`PacketDistributor.sendToPlayer(player, payload)`.

**Gotchas:** `DistExecutor.unsafeRunWhenOn` is removed — keep `ClientRepositoryHandler` off the server
classpath via a `Dist.CLIENT`-guarded reference. `RepoEntry` has **9** fields → hand-write `encode/decode`
over `RegistryFriendlyByteBuf` (vanilla `StreamCodec.composite` caps at **6** pairs / NeoForge's at **7** —
a **fixed overload count, not mappings-dependent**; the v1 "in some mappings" wording was wrong).

### 3.4 Seam 4 — Registration + mod-entry/event-bus wiring

**Registry handles:** `RegistryObject` → `DeferredHolder` (drop-in; `.get()`/`.getId()` unchanged);
`ForgeRegistries.X` → `BuiltInRegistries.X` / the typed `DeferredRegister.createBlocks/createItems`. **5
`IForgeMenuType.create` → `IMenuTypeExtension.create`** (same `MenuSupplier` shape). Loot:
`IGlobalLootModifier` codec → `MapCodec` in 1.21 (verify the two codecs in `loot/`). Single-target ⇒
**all registration is just the NeoForge-native rename in place** (no Architectury, no `RegistrySupplier` —
the v1 §5.1 "where does registration live" fork is moot with one tree).

**§3.4.1 Mod-entry & event-bus wiring** (no other seam owns these — all per the single NeoForge entry):
- 6 `@Mod.EventBusSubscriber` → top-level `@EventBusSubscriber`, `Bus.FORGE` → `Bus.GAME` (`Bus.MOD`
  unchanged): `BlueprintAnvilHandler:16`, `ModCapabilities:18`, `FuClientBinding:17`, `FilamentTooltip:23`,
  `ClientSetup:13`, `ScannerSelectionRenderer:30`.
- `@Mod` ctor `MC3DPrint(FMLJavaModLoadingContext)` → NeoForge-injected `IEventBus modBus` / `ModContainer`
  / `Dist`.
- 6 `MinecraftForge.EVENT_BUS.addListener` calls (`MC3DPrint.java:56-71`) → `NeoForge.EVENT_BUS`.

**Gotchas:** `new ResourceLocation(ns,path)` is removed → `ResourceLocation.fromNamespaceAndPath` (hits
`ModItemTags:51`, `ModCreativeTabs:134`, the channel id, every component name). `ResourceLocation.tryParse`
**survives** (don't "fix" it). `ForgeRegistries.ITEMS.getValue(rl)` → `BuiltInRegistries.ITEM.get(rl)`
(`ModCreativeTabs:133`).

### 3.5 Seam 5 — BE persistence shim

**Every BlockEntity serializes ItemStacks** via `ItemStackHandler.serializeNBT()` (Printer 4 handlers
`:2092-2095`, Rack `:138`, Repository `:64`). 1.21.1 requires a `HolderLookup.Provider` on those calls
(item NBT embeds component refs); scalars (`putInt/putUUID/putString`) don't.

Extract each BE body into `writeState(tag, provider)` / `readState(tag, provider)` helpers. NeoForge
override signatures: `saveAdditional(CompoundTag, HolderLookup.Provider)` and **`loadAdditional(CompoundTag,
HolderLookup.Provider)`** (note `loadAdditional`, *not* `load`). Thread the provider into
`getUpdateTag(Provider)` / `handleUpdateTag(tag, Provider)` and forward `onDataPacket`'s provider.

> **StackIo seam note (corrected):** in Stage 1 (single target) the handlers are NeoForge `ItemStackHandler`
> directly — `h.serializeNBT(provider)` / `h.deserializeNBT(provider, tag)` — so **no `StackIo` indirection
> is needed yet**. (The `StackIo` interface only matters in Stage 2, to bridge 1.20.1's no-arg
> `serializeNBT()` vs 1.21.1's `(Provider)` form; if introduced then, its signature must operate on the
> mod-owned wrapper or on `Tag`/`Provider`, **never** take a loader-specific `ItemStackHandler` param.)

**Audit item:** `PrintJob`/`activeJob.save()` (`PrinterBlockEntity.java:2108`) + `previewBlueprint` may
carry block/item refs inside the payload — if so they also need the `Provider` (§5). `RepoEntry.toNbt/fromNbt`
is pure scalars — no provider.

### 3.6 Seam 6 — Client/render

`ClientSetup.java`: 5 `MenuScreens.register` (in `FMLClientSetupEvent`) → **`RegisterMenuScreensEvent`**
(dedicated event, no `enqueueWork`); 2 `registerBlockEntityRenderer` (in `EntityRenderersEvent`, NeoForge
package). All 5 Screens are `GuiGraphics`-era and **portable** (only `graphics.blit(...)` arg order shifts —
minor). Forge-package import swaps: `ItemStackHandler` (`FilamentRackRenderer:16`), `ModelData`
(`PrinterRenderer:506`), `RenderLevelStageEvent` (`ScannerSelectionRenderer`).

**1.21.5 forward-proofing:** the render-pipeline rewrite (a future target, not 1.21.1) will reshape
`VertexConsumer` (`endVertex()` removed). Isolate raw vertex emission behind a `RenderBridge` so the
future rewrite is one file. `GhostVertexConsumer` + the `PrinterRenderer` `.vertex()…endVertex()` chains
route through it. On 1.21.1 the bridge is the current builder chain lifted verbatim.

### 3.7 Companion migrations (each needs a §6 gate)

These are real, compile-blocking, and were **absent from the v1 six-seam table**. Each is small and
mechanical but must be enumerated so an agent doesn't skip it.

- **C1 — `"BlockEntityTag"` convention (deleted 1.20.5+).** `ControllerBlock:74-75` (collapse a formed
  fabricator into an item carrying BE state) + `FabricatorBlockItem:32`; `RemoteTerminalBlock:86,99`
  (pair terminal↔printer). → `DataComponents.BLOCK_ENTITY_DATA` (`BlockEntityType`-aware `CustomData`) +
  custom components. NeoForge BlockItem component handling.
- **C2 — Advancement criteria (true rewrite).** `BasicTrigger.java` uses the deleted pre-1.20.2 API
  (`getId()`, `createInstance(JsonObject,…)`, `AbstractCriterionTriggerInstance`, single-arg
  `CriteriaTriggers.register`). → implement `codec()` returning `Codec<Instance>` with the `Instance` a
  `SimpleCriterionTrigger.SimpleInstance` record (near-trivial — just `Optional<ContextAwarePredicate>
  player`); register via `DeferredRegister.create(Registries.TRIGGER_TYPE, MOD_ID)`. The **6 `.trigger(player)`
  call sites are unchanged** (`PrinterBlockEntity:988,1317,1319`; `WinderBlockEntity:262`; `ScannerItem:159`;
  `AddBlueprintDiscModifier:75`). **Folder rename:** `data/mc3dprint/advancements/` → `advancement/`
  (singular, 10 JSON files) — same 1.21 depluralization family as `recipes`/`loot_tables`.
- **C3 — Config spec.** `MC3DPrintConfig.java` (~58 `ForgeConfigSpec.{Int,Double,Boolean,Config}Value`
  fields) → `ModConfigSpec` (`net.neoforged.neoforge.common`); every value type/builder method is
  same-shape (`defineInRange`/`define`/`comment`/`push`/`pop`/`build`). Registration + reload listener
  already live in the `@Mod` entry: `context.registerConfig` → `ModContainer#registerConfig`;
  `ModConfig`/`ModConfigEvent` → `net.neoforged.fml.config.*`. Mechanical rename, **not** a data migration.
- **C4 — Recipe-derivation adapter.** `fu/MinecraftRecipeIndex.java` (the live `RecipeManager` →
  `RecipeFuValuator.RecipeGraph` adapter): `getAllRecipesFor` now yields `List<RecipeHolder<T>>` (unwrap
  `holder.value()`); the `<C extends Container, T extends Recipe<C>>` bound must drop `Container` (`Recipe`
  is now `Recipe<? extends RecipeInput>`); `getResultItem(registryAccess)` → `getResultItem(HolderLookup.Provider)`.
  ~145-line surface, fails loud at first compile.
- **C5 — Patchouli book stamp (soft-dep).** **Two** raw writes (v1 missed the second):
  `GuidebookAutoGive.java:50` and `ModCreativeTabs.java:137` both do
  `book.getOrCreateTag().putString("patchouli:book", "mc3dprint:guide")`. → Patchouli 1.21 API
  `stack.set(PatchouliDataComponents.BOOK, rl)` or `ItemModBook.forBook(...)` — **not** a generic
  `CustomData` — behind the existing `ModList.isLoaded("patchouli")` guard (no gradle dep, so guard via the
  compat shim). **Don't over-correct:** `getPersistentData()` reads (`GuidebookAutoGive`,
  `WinderBlockEntity:256`, `RepositoryIndex:123`) **survive** 1.20.5 — only `ItemStack.getTag/setTag` were
  deleted.
- **C6 — JEI plugin body (not just a coordinate bump).** `MC3DPrintJeiPlugin.java` uses
  `net.minecraftforge.fml.ModList` + `ForgeRegistries.ITEMS.forEach` (won't compile on 1.21.1 at all) plus
  JEI **15.x → 19.x** API churn (`IRecipeRegistration.addRecipes`, `IIngredientManager.removeIngredientsAtRuntime`/
  `VanillaTypes`, the `IRecipeCategory` contract in `PrintRecipeCategory.java`). Fixes: `ForgeRegistries.ITEMS`
  → `BuiltInRegistries.ITEM`; `net.minecraftforge.fml.ModList` → `net.neoforged.fml.ModList`; + a JEI
  15→19 audit. (`PLUGIN_ID`/`ResourceLocation.tryParse` is fine — don't touch.)
- **C7 — FU public API + IMC ingress.** `api/MC3DPrintAPI`, `api/FuRegistration` import only
  `net.minecraft.*` — they port clean. But `fu/FuEvents` (`onInterModProcess(InterModProcessEvent)` +
  `onServerStarted` + `onDatapackSync`) uses Forge-package events that move
  (`net.minecraftforge.fml.*`→`net.neoforged.fml.*`; `net.minecraftforge.event.*`→`net.neoforged.neoforge.event[.server].*`).
  The **public javadoc** telling third parties to send IMC from `FMLCommonSetupEvent`/`InterModEnqueueEvent`
  is a **contract deliverable** — update it to name the NeoForge packages so no-hard-dep compat mods keep
  compiling against the 1.21.1 jar.

> **Correct the v1 §2 label:** "integration/ — ResourceLocation strings, already loader-agnostic" is true
> only for the FU-compat hooks (`ae2`, `thermal`, …). It does **not** cover `integration/jei` (C6) or
> `api/`+IMC (C7), both of which carry loader-coupled code. Scope the label accordingly.

---

## 4. Cross-cutting

- **Java 21** mandatory; update CI + Prism instance.
- **JEI** per-version + per-loader coordinates (15.x-forge → 19.x-neoforge).
- **Gametests:** the 21 holders use Forge `@GameTestHolder`/`RegisterGameTestsEvent` → NeoForge
  equivalents. Set `neoforge.enabledGameTestNamespaces` (§2.1). **Gate must assert test count > 0** (a
  green exit running 0 tests is a false pass). This suite is the regression oracle — first-class.
- **`ResourceLocation` constructor sweep** (`fromNamespaceAndPath`) + the `tags/recipes/advancements` →
  `tag/recipe/advancement` data-folder depluralization (hits loot/recipes/advancements JSON).
- **`Block.use` → `useWithoutItem`/`useItemOn`** across 1.20.6 — audit every machine block's interaction
  override during Phase 3.
- **No mixins / ATs / datagen** — keep it that way.

---

## 5. Open decisions
- **5.1 — `PrintJob` provider audit (§3.5):** does `PrintJob`/`previewBlueprint` serialize item/block refs?
  If yes, thread `HolderLookup.Provider`. Resolve in Phase 3e.
- **5.2 — `BlockCapabilityCache` lifecycle (§3.2):** confirm the cable's reflood clears the cache map (no
  listener leak).
- **5.3 — When to start Stage 2 (Stonecutter multi-version):** only after Stage 1 ships green. Out of
  scope here.
- **5.4 — Keep Forge-1.20.1 vs NeoForge-1.20.1 for the legacy line:** decide at Stage 2; affects whether
  surface 7 needs a wrapper. Not a Stage 1 concern.

---

## 6. Execution plan (Stage 1, for an agent team + a human verifier)

Gates are tagged **[AGENT]** (closable by `runGameTestServer`/`build`) or **[HUMAN]** (interactive 1.21.1
client — Patrick). **Do not advance a phase until its gate is green.** Line 8's "executable by a team of
agents" holds *with a human verifier for the in-world gates*.

**Phase 0 — Branch & baseline.** Cut `port/neoforge-1.21.1` (and confirm `legacy/1.20.1` holds the frozen
Forge build). Record the 1.20.1 green state: `./gradlew build` + `runGameTestServer -q`. **[AGENT] Gate:**
baseline recorded; legacy branch builds.

**Phase 1 — Toolchain conversion.** Swap ForgeGradle→ModDevGradle, Java 17→21, `mods.toml`→
`neoforge.mods.toml`, JEI coords, the `@Mod` ctor + event-bus wiring (§3.4.1). Mod loads as an empty-ish
shell. **[AGENT] Gate:** `:build` compiles; the mod loads in a 1.21.1 client to the title screen with no
errors. (No "same jar" claim — the build target changed.)

**Phase 2 — Seam interfaces.** Land all six seam interfaces (`ItemData`, `CapAccess`/`CapabilityRegistrar`
incl. item-handler, `Net`, registration helpers, persistence helpers, `ClientBootstrap`/`RenderBridge`)
and the `core` package. Re-point the existing static helpers at the seams. **[AGENT] Gate:** compiles
against the interfaces (impls may be stubs).

**Phase 3 — NeoForge impls.** Seam impls + companion migrations. **Ordering within Phase 3 (not fully
flat):** land **3d (registration/BE types)** and **3a (data components)** *first* — 3b's in-world gate
needs registered `BlockEntityType`s (NeoForge `registerBlockEntity` needs the resolved type) and 3e's
round-trip needs 3a's component shape. **File-ownership:** seams **3b (caps) and 3e (persistence) co-edit
the same 6 BEs** (Printer, MC3DCable, FilamentRack, Winder, ClockGenerator, FilamentConverter) — either
sequence 3e after 3b on those files, or give both seams' shared-BE work to one agent and parallelize only
the disjoint seams. (3f render edits are isolated in `client/` — no collision.)
- **3a ItemData** → `ModDataComponents` + impl. **[AGENT] Gate:** disc/spool/scanner round-trip in a
  gametest; tooltips read back.
- **3b Capabilities** (energy + FU + **item-handler**) → `NeoCaps`, delete 8 `getCapability` overrides,
  `BlockCapabilityCache` cable path. **[AGENT] partial gate:** a gametest asserts cap registration +
  energy/FU transfer math + item-handler presence. **[HUMAN] gate:** in 1.21.1 — RF flows cable→printer;
  FU drains rack→printer; multiblock casing forwards; a hopper inserts into the printer UP face & extracts
  from DOWN; the converter pulls a filtered item from an adjacent chest; clock fuel drops on break.
- **3c Net** → payload + impl. **[AGENT] Gate:** repository listing syncs S2C (gametest or logged).
- **3d Registration** + §3.4.1 wiring + loot codecs. **[AGENT] Gate:** all blocks/items/BEs/menus/tabs
  register; creative tab populated.
- **3e Persistence** across 11 BEs + the `PrintJob` audit (§5.1). **[AGENT] Gate:** a printer with
  contents/energy/active job survives save/load/chunk-reload.
- **3f Client/render** → `ClientBootstrap` + `RenderBridge` + 3 import swaps. **[HUMAN] Gate:** 5 screens
  open; printer hologram + rack item render; scanner box draws.
- **3g `BlockEntityTag`** (C1). **[HUMAN] Gate:** fabricator collapse/place + terminal pairing round-trip.
- **3h Companion surfaces** C2–C7 (advancements, config, recipe adapter, Patchouli, JEI, IMC). **[AGENT]
  Gates:** a custom advancement grants in a gametest; `mc3dprint-common.toml` generates + `FuValueRegistry`
  reload fires; FU derivation still values blocks (printability gametest green); JEI plugin compiles + the
  recipe category renders **[HUMAN]**; a stub IMC send is picked up.

**Phase 4 — Gametest parity.** Port all 21 holders. **[AGENT] Gate:** `runGameTestServer` green **AND
printed test count > 0 AND == the 1.20.1 count** (a 0-tests green exit is a FAILED gate).

**Phase 5 — In-game soak. [HUMAN]** Full survival playthrough on 1.21.1: scan→wind→print all tiers,
multiblock form, resins, repository, rack+cable network, JEI + Patchouli soft-deps load. **Gate:** an
end-to-end survival print works; no console errors. (Consolidation point for all deferred [HUMAN] gates.)

**Phase 6 — Ship.** Release a 1.21.1 NeoForge jar; keep the 1.20.1 Forge jar from `legacy/1.20.1`. Tick
CurseForge game-versions **only** for versions actually built+tested (1.20.1 and 1.21.1).

---

## 7. Definition of done
- `:build` green on NeoForge 1.21.1; `runGameTestServer` green with **count > 0 == 1.20.1's**.
- A full survival scan→print works in 1.21.1 (multiblock, resins, repository, rack+cable, RF+FU+item I/O).
- All §3.7 companion surfaces migrated (advancements grant, config loads, recipes derive, JEI renders).
- Zero mixins/ATs/datagen introduced. No `Co-Authored-By: Claude` / "Generated with Claude Code".
- Doc surfaces (Patchouli + website guide) unaffected — verify, don't assume.

---

## Appendix — adversarial-review changelog (v1 → v2)
A red-team/blue-team pass (5 lenses, every finding independently verified) produced 21 confirmed issues.
The structural ones reshaped the plan:
- **Dropped the premature `common`/`forge`/`neoforge` split.** A single `common` can't compile against two
  Minecraft versions (`getTag` exists in 1.20.1, deleted in 1.21.1) **or** hold loader-subclassed types
  (`EnergyStorage`, the `ItemStackHandler` family) — and the v1 build files were plain-MultiLoader
  mislabeled "Architectury." Replaced with **single-target Stage 1 + deferred Stonecutter Stage 2**, which
  dissolves both blockers (one compile target ⇒ storage handles are an import swap; no toolchain split).
- **Added the ITEM_HANDLER capability dimension** to Seam 2 (3 exposers + 2 cross-BE queriers; v1 would
  have shipped a silently-broken converter and dead hopper faces).
- **Fixed the `BlueprintData` StreamCodec** (10 fields exceed `composite`'s 6/7-pair cap → hand-written
  encode/decode) and the "some mappings" misattribution.
- **Enumerated 7 companion surfaces** the v1 six-seam table missed (BlockEntityTag, advancement criteria,
  config spec, recipe adapter, Patchouli book writes, JEI plugin body, IMC/api) — each now has a gate.
- **Execution honesty:** behavior-based oracle (not "same jar"); 3d→3b and 3a→3e gate ordering; 3b/3e
  shared-BE file-ownership; AGENT vs HUMAN gate tagging; `neoforge.enabledGameTestNamespaces` + a
  test-count-> 0 assertion to prevent a silent false-green.
