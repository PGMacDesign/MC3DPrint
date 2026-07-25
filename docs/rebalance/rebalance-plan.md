# MC3DPrint FU Tier Rebalance — Plan (for sign-off)

_Synthesised from the grill-me session + the three research docs in this folder
(`acquisition-rarity.md`, `utility-ranking.md`, `current-fu-map.md`). Nothing here is
implemented yet — this is the spec to approve before touching `FuValueRegistry`._

---

## 1. Framework (the rules everything else follows)

1. **Tier axis = rarity-first, with utility overrides (decision "B").** Tier reflects
   how hard an item is to **obtain** (find or craft). Recipe derivation already tracks
   this for craftables (tier flows up from the priciest ingredient), so we only hand-add
   explicit `item=fu@tier` entries where derivation can't help or needs correcting.
2. **FU *amount* stays recipe-derived** wherever possible. Explicit amounts are only for
   natural blocks (no recipe) and a few deliberate overrides. Amounts below are proposed
   on the existing scale (cobble 1, stone 3, glass 5, iron 20, gold 15, diamond 50,
   emerald 50, netherite 500) and are tunable — **tiers are the thing to sign off.**
3. **Abundance rule (the key anti-launder guard).** An item's tier is **capped by its
   abundance**: a renewable/farmable resource may not sit at a tier whose spool could
   print something *rarer* than itself. (Why chorus is T4, not T6 — a T6 chorus spool
   could print netherite blocks. A T4 chorus spool tops out at T4.)
4. **Craft-down is fixed two-pronged:** (a) re-value cheap base materials so processing
   is ~FU-neutral (planks → ~1 FU so 4 planks ≈ 1 log); (b) winder-**blacklist** only the
   stragglers that can't be cleanly priced (sticks, etc.). During the build I'll flag any
   recipe where `outputs > input` for a call rather than auto-blacklisting.
5. **Mechanics that constrain all of the above** (unchanged): exact-tier winding,
   down-only spending (print-down), itemless blocks print free, storage blocks derive
   9×/4× their base, winder blacklist tag.

---

## 2. The 8-tier ladder

| Tier | Theme | Key anchors |
|------|-------|-------------|
| **T1** | bulk / infinite | dirt, cobble, sand, gravel, stone family, wood, glass, wool→(moved out), coal, deepslate, tuff, dripstone, calcite, clay, mud, netherrack, basalt, blackstone, soul sand/soil, moss, snow, ice |
| **T2** | early ores / dimension entry | copper, iron, gold, lapis, amethyst, **end stone**, packed ice, magma block |
| **T3** | processing + early friction | redstone, slime, glowstone, quartz, shroomlight, obsidian, crying obsidian, **wool, string**, basic food |
| **T4** | renewable-valuable | emerald, blaze rod, ghast tear, prismarine, totem, **chorus family** (purpur, end rod), hearty food |
| **T5** | deep mining / monument | diamond, ender pearl, sea lantern, sponge, sculk family, **golden apple** |
| **T6** | netherite + high-value finite | netherite family, shulker shell*, trident*, nautilus shell*, **elytra**, **enchanted golden apple** |
| **T7** | boss / heavy-grind | nether star, beacon, dragon egg (wind-only), **base draconium chain** (DE only) |
| **T8** | finite trophies + modded endgame | echo shard, heart of the sea (→ conduit derives here), **awakened draconium** (DE only, wind-only) |

\* flagged for the abundance check — see §8.

---

## 3. Naturally-spawned blocks → explicit entries (the table we built)

These have no recipe, so they need explicit values to be printable. Amounts proposed:

- **T1** — `deepslate`/`cobbled_deepslate`=1, `tuff`=1, `dripstone_block`=1, `pointed_dripstone`=1, `mud`=1, `moss_block`=2, `basalt`/`smooth_basalt`=3, `blackstone`=3, `snow_block`=1, `ice`=1, `clay`=3. (sand/gravel/dirt/netherrack/soul sand/soul soil/sandstone already T1.)
- **T2** — `end_stone`=5, `packed_ice`=5, `magma_block`=5.
- **T3** — `glowstone`=20 (or value `glowstone_dust`=5 and let the block derive), `obsidian`=10, `crying_obsidian`=15, `shroomlight`=10. (quartz/nether_quartz already heading to T3.)
- **T4** — `prismarine`=20, `prismarine_bricks` derives, `dark_prismarine`=25.
- **T5** — `sea_lantern`=50, `sculk`=15, `sculk_vein`=15, `sculk_catalyst`=40, `sculk_sensor`=40, `sculk_shrieker`=40, `sponge`=60. (echo shard is T8; `reinforced_deepslate`, `budding_amethyst` stay unprintable.)

Your locked calls: deepslate **T1**, end stone **T2**, obsidian **T3**, glowstone **T3**, prismarine **T4**, sea lantern **T5**, sculk family **T5**.

---

## 4. Utility overrides (cheap materials, high utility — pinned up)

Derivation rates these ~T2 from cobble/iron/redstone, but their power warrants a floor:

- `hopper` → **T3**, `observer` → **T3**, `dispenser`/`dropper` → **T3**
- `piston` → **T3**, `sticky_piston` → **T4**
- `bookshelf` → **T3** (enchanting power)
- `powered_rail`/`activator_rail`/`detector_rail` → **T3**
- (`note_block`, `target`, `daylight_detector` — optional T3 bumps; low risk if left deriving)

---

## 5. Food (new capability: print-to-eat; winder-blacklisted)

- **T3 (basic):** bread, apple, baked_potato, cookie, dried_kelp, mushroom_stew, beetroot_soup
- **T4 (hearty):** cooked_beef, cooked_porkchop, cooked_chicken, cooked_mutton, cooked_rabbit, cooked_cod, cooked_salmon, golden_carrot, pumpkin_pie, rabbit_stew, suspicious_stew
- **T5 (premium):** golden_apple _(your call)_
- Raw crop items (wheat, raw meats, carrot, potato) stay **low/cheap** (T1–T2) so they print but aren't a back-door.
- **All food added to `mc3dprint:winder_blacklist`** — printable, never windable.

---

## 6. Craft-down fixes

- **Re-values (kill the launder at the source):** `#minecraft:planks` 3 → **1** (4 planks ≈ 1 log); audit other "1 in → many out" base crafts (e.g. consider `log`=4 so 4 planks = 4 = 1 log, exactly neutral).
- **Winder-blacklist additions (stragglers):** `stick` (done) + candidates `bowl`, `ladder`, wooden `button`/`pressure_plate` *if* valued, and all **food** (§5). Hand-curated; I'll flag any other net-FU-gain recipe I hit during the build.

---

## 7. Unprintable (no tier — strict mode refuses them)

- **Real items:** none now — `dragon_egg` moved to **T7 as a wind-only trophy** (windable recycle, never printed via `#no_print`), and `wither_skeleton_skull` to T4 the same way. _(Elytra and enchanted golden apple are now printable — T6.)_
- **Survival-unobtainables:** `bedrock`, `barrier`, `command_block` family, `structure_block`/`jigsaw`/`structure_void`, `spawner`, `reinforced_deepslate`, `budding_amethyst`, `infested_*`, `end_portal_frame`, `light`, `petrified_oak_slab`, `frosted_ice`, `player_head`. (Leave un-priced; strict mode blocks them. Optionally hard-blacklist for clarity.)

---

## 8. High-value / contested items — final tiers

- `diamond` **T5**, `netherite_*` **T6** (already done), `emerald` **T4** (done).
- `wool`/`string` → **T3** (early-game friction; ripples beds/banners/carpets up a tier — fine).
- `chorus_fruit`/`popped_chorus_fruit`/`chorus_flower`/`purpur_block`/`end_rod` → **T4** (abundance-capped).
- `golden_apple` **T5**, `enchanted_golden_apple` **T6**, `elytra` **T6**.
- `nether_star` **T7** (done), `beacon` **T7** (derives via star), `dragon_egg` **T7** wind-only. `wither_skeleton_skull` is now **T4** wind-only (AFK-farmable, so abundance-capped, not T7).
- `heart_of_the_sea` **T8**, `conduit` → **T8** (derives from heart of the sea), `echo_shard` **T8**.
- **⚠ Abundance check before locking:** `shulker_shell` (auto-dupe-farmable), `trident`, `nautilus_shell` are at T6 from the rarity research — but if any are cheaply farmable they could launder into netherite (T6). **Recommendation: cap shulker_shell at T5 (or winder-blacklist it); confirm trident/nautilus aren't farm-trivial.** Flagging for your call, not locked.

---

## 9. Draconium (Draconic Evolution soft-dep)

- **Base chain = Tier 7** (`dust` → `ingot` → `block` + all four ores). Draconium is a
  post-netherite *mined* material, so it fills the otherwise-empty modded T7 band below vanilla
  `nether_star`; pinned at ~250/ingot so a mined block can't launder into multiple nether stars.
  `draconium_dust` is the true leaf (every ore drops it without silk); standard-crafted DE items
  below the fusion tier (`draconium_core`, `wyvern_core`) **derive** from this chain.
- **Ore ids:** `overworld_draconium_ore`, `deepslate_draconium_ore`, `nether_draconium_ore`,
  `end_draconium_ore`. (An earlier build registered a phantom `draconium_ore`, leaving the
  overworld ore unvalued and the deepslate ore missing — fixed.)
- **Awakened draconium = Tier 8, WIND-ONLY.** DE's Fusion-Crafting endgame and the T8 fabricator's
  structural corner. The ingot is valued `500@8` so it *winds* for a recycle payout (~1.3 nether
  stars of down-print), but all four forms (ingot/block/dust/nugget) are on `#no_print`, so the
  printer can never reproduce it and the Fusion-Crafting gate stays intact.
- **No Fusion Crafting derivation** — the valuator can't read DE's custom recipe types, so deeper
  fusion gear (cores, chaos, energy components, draconic chest) stays unvalued/unprintable. Intended.
- All conditional on DE being loaded (FuValueRegistry handles missing items gracefully; the
  `#no_print` modded entries use the optional `required:false` form).

---

## 10. Implementation notes (when approved)

- Add explicit entries to `FuValueRegistry.defaultEntries()`; extend `winder_blacklist.json`.
- **Delete the dead `data/.../tags/items/filament/tier_*.json`** (nothing reads them; already drifted).
- **Gametest ripple:** existing tier/FU assertions (FuTierEconomy, RecipeDerivation, TierGating, StructurePrint) will need updates for any moved item; add coverage for the new anchors + the abundance-cap cases.
- **Config gotcha:** delete `run/config/mc3dprint-common.toml` (and the Prism one) after — the `fuValues` list doesn't merge new defaults.
- Update the in-game guide's FU/tier pages + the README design-notes if any player-facing tier story changes (wool/string/food/chorus).

---

## Open items for your sign-off

1. The **§8 abundance check** on shulker shell / trident / nautilus (cap or blacklist?).
2. Whether to bump the optional utility items (note block, target, daylight detector) or leave them deriving.
3. Exact FU **amounts** in §3 (tiers are the priority; amounts are tunable).

Everything else is settled. On your OK I'll implement §1–§10.
