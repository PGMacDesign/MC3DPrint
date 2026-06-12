# FU Values, Strict Mode & Compatibility

How MC3DPrint prices materials, how pack makers override prices, and how other
mods register their own — with or without a hard dependency.

---

## 1. The FU / tier economy in one minute

Every printable material has two numbers:

- **FU (Filament Units)** — how much filament it costs to print (and yields when
  wound). Symmetric: winding a diamond gives exactly what printing one costs.
- **Tier (1–8)** — the minimum machine/spool tier that can handle it. A T1
  machine cannot print a T5 diamond; a T1 spool cannot wind a T5 material.

A material's value comes from the **first** of these sources that has an answer
(strict precedence):

| # | Source | Who sets it |
|---|--------|-------------|
| 1 | Explicit config **item** entry | pack maker (`fuValues`) |
| 2 | Explicit config **tag** entry | pack maker (`fuValues`, `#tag` syntax) |
| 3 | **API** registration | other mods (`MC3DPrintAPI` / IMC) |
| 4 | **Recipe-derived** value | automatic, from crafting/smelting/stonecutting |
| 5 | *(unknown)* | — strict mode refuses it; permissive mode prices it at `unknownBlockFu` |

**Config always wins over the API, which wins over derivation.** So a pack maker
can override anything, and a compat mod can price its items without the base mod
hardcoding them.

### Recipe derivation

If an item has no explicit/API value, MC3DPrint walks its recipes:

```
value(item) = min over recipes of  floor( sum(cheapest ingredient FU) / outputCount )   (min 1)
tier(item)  = max ingredient tier of the winning recipe
```

So `diamond_block` (9 × diamond, diamond = 50 FU @ T5) derives to **450 FU @ T5**
with no hardcoded entry — and a T1 machine still can't print it, because the
tier flows up from the diamonds. Storage blocks, crafted tools, and smeltable
results all resolve this way. Cycles are detected (an item that depends on
itself prunes that path), recursion is capped, and results are cached and
rebuilt on `/reload`.

Derivation reads **crafting** (always), **smelting** (`deriveFromSmelting`,
default on) and **stonecutting** (`deriveFromStonecutting`, default on).
Blasting/smoking/campfire are intentionally ignored (they duplicate smelting),
and fuel slots are treated as free.

---

## 2. Strict mode (the anti-exploit gate)

A scanned blueprint can contain *any* block. Without a gate, a player could scan
an expensive un-priced block and print it on a cheap machine. The
`unknownBlocksPrintable` config closes that:

- **`unknownBlocksPrintable = false` (default — strict).** If a structure's
  palette contains any block with **no** explicit, API, or derived value, the
  whole structure is **NOT_PRINTABLE** on every tier.
- **`unknownBlocksPrintable = true` (permissive).** Such blocks are allowed, but
  priced at `unknownBlockFu` (default **50**, raised from 3) and **clamped to the
  printing machine's own tier** — so they are never cheap on a low-tier machine.

Related config (all under `general`):

| Config | Default | Effect |
|--------|---------|--------|
| `unknownBlocksPrintable` | `false` | strict gate on/off |
| `unknownBlockFu` | `50` | per-block FU for un-priced blocks (permissive only) |
| `deriveFromSmelting` | `true` | derive from smelting recipes |
| `deriveFromStonecutting` | `true` | derive from stonecutting recipes |

---

## 3. For pack makers — overriding values

Edit `config/mc3dprint-common.toml`, the `general.fuValues` list. Each entry is:

```
<item-or-#tag> = <fu> @ <minTier>
```

Examples:

```toml
[general]
fuValues = [
    # price a single item
    "minecraft:obsidian=8@2",

    # price every item in a tag (explicit item entries still beat tag entries)
    "#minecraft:planks=3@1",

    # override a DERIVED value — explicit always wins over derivation
    "minecraft:diamond_block=999@5",

    # price a modded item by its id
    "create:brass_ingot=40@3",
]
```

Rules:

- **Explicit wins over derived.** Listing `diamond_block` here overrides the
  450 FU it would otherwise derive.
- **Item beats tag.** A `minecraft:oak_planks=2@1` entry overrides a
  `#minecraft:planks=3@1` entry for oak specifically.
- **Removing an entry re-enables derivation** for that item (if a recipe exists).
- The list does **not** merge new mod defaults into an existing file. After a
  mod update that changes defaults, delete `mc3dprint-common.toml` to regenerate
  it (you'll lose manual edits — back them up).

---

## 4. For compat-mod authors — registering values for your items

You have two ways in. Pick based on whether you want a hard dependency.

### Option A — direct API call (hard dependency on MC3DPrint)

Add MC3DPrint to your build, then call `MC3DPrintAPI` from your
`FMLCommonSetupEvent` or `InterModEnqueueEvent` listener:

```java
import com.pgmacdesign.mc3dprint.api.MC3DPrintAPI;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

private void onCommonSetup(FMLCommonSetupEvent event) {
    event.enqueueWork(() -> {
        // by id (item need not exist yet)
        MC3DPrintAPI.registerFuValue(
                new ResourceLocation("yourmod", "ruby"), 60, 4);

        // by Item instance
        MC3DPrintAPI.registerFuValue(YourItems.RUBY.get(), 60, 4);

        // by tag — applies to every item in the tag
        MC3DPrintAPI.registerTagFuValue(
                TagKey.create(Registries.ITEM, new ResourceLocation("yourmod", "gems")),
                60, 4);
    });
}
```

`fu` is clamped to ≥ 1, `tier` to 1–8. Re-register every launch (registrations
survive config reloads but not restarts). A pack maker's config still overrides
anything you register here.

### Option B — IMC (no hard dependency)

Send an `InterModComms` message — your mod needs **no** compile-time dependency
on MC3DPrint. Send the payload from your `InterModEnqueueEvent`:

```java
import net.minecraftforge.fml.InterModComms;
import net.minecraft.resources.ResourceLocation;

private void enqueueImc(InterModEnqueueEvent event) {
    InterModComms.sendTo("mc3dprint", "register_fu_value", () ->
            new com.pgmacdesign.mc3dprint.api.FuRegistration(
                    new ResourceLocation("yourmod", "ruby"), 60, 4));
}
```

| Field | Value |
|-------|-------|
| Target mod id | `mc3dprint` |
| Method key | `register_fu_value` (also `MC3DPrintAPI.IMC_REGISTER_FU_VALUE`) |
| Payload | `com.pgmacdesign.mc3dprint.api.FuRegistration(ResourceLocation item, int fu, int tier)` |

If you'd rather not reference `FuRegistration` at all, you can ship a tiny
record with the same component shape — but referencing the real class (it lives
in the small, stable `com.pgmacdesign.mc3dprint.api` package) is simplest if you
have the jar at compile time only.

MC3DPrint consumes these during its own `InterModProcessEvent`. Clamping and
precedence are identical to the direct API.

---

## 5. Quick reference — what derives vs. what's a base value

**Base values** (hardcoded defaults, the roots of derivation): bulk fill
(cobblestone/dirt/…), stone family, wood (`#logs`/`#planks`), processed building
blocks (glass/concrete/wool/quartz/…), ingots & gems (copper/iron/gold/lapis/
amethyst shard/emerald/diamond/netherite ingot), nuggets, redstone, slime/magma,
coal, ancient debris, nether star, dragon egg.

**Derived** (no hardcoded entry — comes from recipes): all storage blocks
(`diamond_block` 450@5, `netherite_block` 4500@5, `iron_block` 180@2,
`gold_block` 135@2, `copper_block` 90@2, `lapis_block` 90@2, `redstone_block`
36@3, `slime_block` 270@3, `emerald_block` 450@4, `amethyst_block` 40@2,
`coal_block` 18@1), plus crafted tools/items and smeltable results wherever a
recipe chain bottoms out in a base value.
