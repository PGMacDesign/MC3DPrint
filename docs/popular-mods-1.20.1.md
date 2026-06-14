# Popular Minecraft 1.20.1 (Forge) Mods — Top 1,000 by Downloads

> **Heads-up on the source.** You asked for CurseForge's top-1,000. CurseForge's catalog-search endpoint (`/v1/mods/search`) is **policy-gated** — a standard Eternal API key can fetch a mod by ID but is returned **403** on bulk search, so the CF ranking can't be enumerated without elevated/approved access. This list is therefore built from **Modrinth's** open API instead. Rankings are by Modrinth download count, which correlates strongly with CurseForge for the tech/material/magic mods that matter for FU planning, but is **not identical** — see caveats below.

## Metadata

| Field | Value |
|---|---|
| Source | Modrinth API `GET /v2/search` |
| Facets | `categories:forge`, `versions:1.20.1`, `project_type:mod` |
| Sort | `index=downloads` (descending) |
| Retrieved | 2026-06-13 |
| Total matching population | **22,273** Forge 1.20.1 mods |
| Captured here | top **1,000** by downloads |
| Download range | 128,586,953 (#1) → 1,310,617 (#1000) |

### Caveats (Modrinth vs CurseForge)

- **Loader filter = Forge.** MC3DPrint is a Forge 1.20.1 mod, so this is the relevant co-install universe. Many of these are also NeoForge/Fabric.
- **CF-heavy mods rank lower or are absent on Modrinth.** Notably **EnderIO** and **Forestry** — both of which you named — do **not** appear in this Modrinth top-1,000. They are older, CurseForge-centric projects. They're still strong FU-synergy targets and are listed in the synergy section regardless of rank.
- **Modrinth download totals are cross-version/cross-loader**, not 1.20.1-Forge-only, so absolute numbers overstate 1.20.1 usage. They're fine for *ranking*.
- **Reproducible:** `curl -G https://api.modrinth.com/v2/search --data-urlencode 'facets=[["categories:forge"],["versions:1.20.1"],["project_type:mod"]]' --data-urlencode index=downloads --data-urlencode limit=100 --data-urlencode offset=N` for N in 0..900.

## Category breakdown (top 1,000)

Modrinth tags, loaders excluded. A mod can carry several tags.

| Category | Count | FU relevance |
|---|---|---|
| adventure | 298 | Low — content/structures |
| decoration | 294 | **High (print targets)** — building blocks are MC3DPrint's core use case; mostly derive |
| game-mechanics | 233 | Low — varies |
| worldgen | 218 | **High** — new ores/gems = raw-material leaves (winder/print) |
| equipment | 171 | **High** — new material tiers (tools/armor) often add raw mats worth FU |
| mobs | 140 | Low — entities, no print surface |
| food | 97 | Medium — we print food (item mode) + winder-blacklist |
| storage | 73 | Medium — storage blocks print; mostly derive |
| technology | 63 | **High** — machines/ingots/alloys; value the custom-recipe leaves (compat hook) |
| transportation | 52 | Low/Med — some craftable blocks |
| magic | 46 | **Medium** — many add crafted reagents/blocks |

> The remaining tags (utility, library, optimization, management, social, economy, cursed, server platforms) have **no** FU surface — they don't add craftable items/blocks.

## FU-synergy shortlist (planning input)

This is the input for the next step — *which* of these to plan Filament-Unit support for. The pattern is the soft-dep compat hook we used for AE2/Thermal/Draconic: gate on `ModList.isLoaded("<id>")`, register only the **custom-recipe leaves** (standard crafting/smelting derives for free), apply abundance caps, leave resource-multiplication outputs unvalued. See `docs/rebalance/` + the `modded-fu-compat` memory.

### Tier A — marquee tech/material mods (plan first)

These add deep material trees and are the highest-value FU targets. Ranks are Modrinth position (— = outside top-1,000 but named/known-significant).

| Mod | Modrinth rank | Tags | FU notes |
|---|---|---|---|
| **Create** | #82 | decoration, technology | Huge. Brass/andesite alloy/zinc + a forest of addons. Mostly mixer/press custom recipes → leaves need values. Abundance-sensitive (rotational scaling). |
| **Applied Energistics 2** | #422 | storage, technology | DONE (certus T2 / fluix+processors T3 / engineering-proc T5). Revisit amounts. |
| **Botania** | #555 | magic, technology | Manasteel/terrasteel/elementium/gaia — Mana Pool custom recipes (won't derive). Strong candidate; abundance-cap flower-farm outputs. |
| **Tinkers' Construct** | #578 | equipment, magic, technology | You named it. Smeltery alloys (cobalt, manyullyn, queen's slime, hepatizon, etc.) are Smeltery-only → leaves. Classic FU target. |
| **Mekanism** | #602 | equipment, storage, technology | Osmium/refined obsidian/glowstone, steel, fissile/antimatter. Heavy resource-multiplication — value base leaves, leave the 5x-ore-processing chain unvalued. |
| **Immersive Engineering** | #621 | equipment, technology | Steel, constantan, electrum, HOP graphite, treated wood. Some derive, alloy-kiln/arc leaves need values. |
| **Thermal Series** | — | technology | DONE (base metals T2 / signalum+lumium T4 / enderium T5). On Modrinth as separate Thermal_* projects; ranks vary. |
| **Draconic Evolution** | — | technology, magic | DONE (draconium base chain T8). CF-heavier. |
| **EnderIO** | — | technology | You named it. NOT in Modrinth top-1,000 (CF-centric). Alloy smelter outputs: dark steel, electrical steel, energetic/vibrant/redstone alloy, end steel, pulsating — all custom-recipe leaves. Strong FU target. |
| **Forestry** | — | technology | You named it. NOT in Modrinth top-1,000 (CF-centric). Bronze, tin/copper, mostly derives; bee/genetics products are the interesting leaves. |

### Tier B — all `technology`-tagged mods in the top 1,000

Auto-extracted. Many are Create addons (cosmetic/mechanic — low FU surface); the non-Create entries are the ones to scan for new materials.

| Rank | Mod | Downloads | Categories |
|---|---|---|---|
| 17 | [VeinMiner](https://modrinth.com/mod/veinminer) | 53,857,982 | cursed, equipment, game-mechanics, technology, utility |
| 69 | [Nature's Compass](https://modrinth.com/mod/natures-compass) | 20,906,985 | adventure, equipment, game-mechanics, technology, utility, worldgen |
| 80 | [Euphoria Patches](https://modrinth.com/mod/euphoria-patches) | 19,886,939 | technology, utility |
| 82 | [Create](https://modrinth.com/mod/create) | 19,154,845 | decoration, technology, utility |
| 91 | [Quark](https://modrinth.com/mod/quark) | 18,045,472 | game-mechanics, technology, utility |
| 162 | [Immersive Aircraft](https://modrinth.com/mod/immersive-aircraft) | 12,137,127 | adventure, game-mechanics, technology, transportation, utility |
| 196 | [Create: Steam 'n' Rails](https://modrinth.com/mod/create-steam-n-rails) | 9,989,654 | adventure, decoration, technology, transportation, utility |
| 214 | [GraveStone Mod](https://modrinth.com/mod/gravestone-mod) | 9,165,975 | adventure, technology, utility |
| 219 | [Explorer's Compass](https://modrinth.com/mod/explorers-compass) | 9,075,550 | adventure, equipment, game-mechanics, technology, utility, worldgen |
| 227 | [Alternate Current](https://modrinth.com/mod/alternate-current) | 8,762,743 | optimization, technology, utility |
| 231 | [Alex's Caves](https://modrinth.com/mod/alexs-caves) | 8,629,753 | adventure, equipment, food, game-mechanics, magic, mobs, technology, transportation, worldgen |
| 247 | [Create Crafts & Additions](https://modrinth.com/mod/createaddition) | 8,041,520 | decoration, food, storage, technology, transportation, utility |
| 256 | [Iron Furnaces](https://modrinth.com/mod/iron-furnaces) | 7,740,368 | storage, technology |
| 304 | [Immersive Armors](https://modrinth.com/mod/immersive-armors) | 6,459,086 | adventure, equipment, technology, utility |
| 308 | [Botarium](https://modrinth.com/mod/botarium) | 6,387,479 | library, storage, technology, utility |
| 349 | [Create Deco](https://modrinth.com/mod/create-deco) | 5,643,093 | decoration, technology, utility |
| 350 | [Create Slice & Dice](https://modrinth.com/mod/slice-and-dice) | 5,619,238 | food, technology |
| 387 | [Create Big Cannons](https://modrinth.com/mod/create-big-cannons) | 5,136,250 | adventure, equipment, game-mechanics, technology, utility |
| 422 | [Applied Energistics 2](https://modrinth.com/mod/ae2) | 4,691,434 | storage, technology, utility |
| 425 | [Ad Astra](https://modrinth.com/mod/ad-astra) | 4,660,229 | adventure, equipment, food, mobs, technology, transportation, worldgen |
| 439 | [Automobility](https://modrinth.com/mod/automobility) | 4,328,106 | equipment, technology, transportation |
| 452 | [Create: New Age](https://modrinth.com/mod/create-new-age) | 4,180,230 | technology |
| 459 | [Create: Connected](https://modrinth.com/mod/create-connected) | 4,115,848 | decoration, technology, utility |
| 473 | [Create: Central Kitchen](https://modrinth.com/mod/create-central-kitchen) | 3,996,594 | food, technology |
| 475 | [CC: Tweaked](https://modrinth.com/mod/cc-tweaked) | 3,977,099 | technology |
| 481 | [Create: Enchantment Industry](https://modrinth.com/mod/create-enchantment-industry) | 3,888,212 | storage, technology |
| 484 | [Man of Many Planes](https://modrinth.com/mod/man-of-many-planes) | 3,877,841 | adventure, storage, technology, transportation, utility |
| 485 | [SecurityCraft](https://modrinth.com/mod/security-craft) | 3,871,575 | adventure, decoration, equipment, technology, utility |
| 493 | [Create: Diesel Generators](https://modrinth.com/mod/create-diesel-generators) | 3,768,031 | technology |
| 505 | [GD656Killicon](https://modrinth.com/mod/gd656killicon) | 3,671,555 | decoration, equipment, technology |
| 512 | [Create: Bells & Whistles](https://modrinth.com/mod/bellsandwhistles) | 3,567,871 | decoration, equipment, technology, transportation, utility |
| 519 | [Create: Interiors](https://modrinth.com/mod/interiors) | 3,495,820 | decoration, technology, transportation, utility |
| 540 | [Create Jetpack](https://modrinth.com/mod/create-jetpack) | 3,249,285 | equipment, technology |
| 541 | [Create: Structures](https://modrinth.com/mod/create-structures) | 3,245,537 | adventure, technology, worldgen |
| 555 | [Botania](https://modrinth.com/mod/botania) | 3,121,190 | decoration, magic, technology |
| 578 | [Tinkers' Construct](https://modrinth.com/mod/tinkers-construct) | 2,960,295 | equipment, magic, technology |
| 602 | [Mekanism](https://modrinth.com/mod/mekanism) | 2,797,590 | equipment, storage, technology, worldgen |
| 607 | [Create Ore Excavation](https://modrinth.com/mod/create-ore-excavation) | 2,775,077 | technology, worldgen |
| 621 | [Immersive Engineering](https://modrinth.com/mod/immersiveengineering) | 2,659,447 | equipment, technology |
| 622 | [Create: Dreams & Desires](https://modrinth.com/mod/create-dreams-and-desires) | 2,654,240 | decoration, equipment, game-mechanics, technology, utility |
| 648 | [Applied Energistics 2 Wireless Terminals](https://modrinth.com/mod/applied-energistics-2-wireless-terminals) | 2,491,757 | technology |
| 650 | [Rechiseled: Create](https://modrinth.com/mod/rechiseled-create) | 2,471,784 | decoration, technology |
| 705 | [XP Tome](https://modrinth.com/mod/xp-tome) | 2,180,560 | adventure, magic, storage, technology, utility |
| 712 | [Just Hammers](https://modrinth.com/mod/just-hammers) | 2,150,234 | game-mechanics, technology, utility |
| 755 | [Create Contraption Terminals](https://modrinth.com/mod/create-contraption-terminals) | 1,994,944 | game-mechanics, storage, technology, utility |
| 759 | [Ad-Astra: Giselle Addon](https://modrinth.com/mod/ad-astra-giselle-addon) | 1,952,425 | technology |
| 761 | [Clockwork](https://modrinth.com/mod/create-clockwork) | 1,948,489 | adventure, game-mechanics, magic, technology, transportation |
| 766 | [Mekanism Generators](https://modrinth.com/mod/mekanism-generators) | 1,933,313 | technology |
| 786 | [Create Utilities](https://modrinth.com/mod/create-utilities) | 1,861,218 | storage, technology, transportation, utility |
| 787 | [Powah!](https://modrinth.com/mod/powah) | 1,859,535 | storage, technology |
| 791 | [Create: Power Loader](https://modrinth.com/mod/create-power-loader) | 1,853,316 | game-mechanics, technology, utility |
| 802 | [Moving Elevators](https://modrinth.com/mod/moving-elevators) | 1,803,121 | decoration, technology |
| 805 | [Create: Framed](https://modrinth.com/mod/create-framed) | 1,785,371 | decoration, technology, utility |
| 810 | [Estrogen](https://modrinth.com/mod/estrogen) | 1,771,830 | cursed, equipment, food, game-mechanics, minigame, technology, transportation, utility |
| 836 | [Trash Cans](https://modrinth.com/mod/trash-cans) | 1,682,892 | decoration, storage, technology |
| 875 | [Immersive Machinery](https://modrinth.com/mod/immersive-machinery) | 1,573,189 | storage, technology, transportation, utility |
| 888 | [MEGA Cells](https://modrinth.com/mod/mega) | 1,535,251 | storage, technology |
| 900 | [Create: Extended Cogwheels](https://modrinth.com/mod/extended-cogwheels) | 1,504,259 | decoration, game-mechanics, technology |
| 904 | [Etcetera](https://modrinth.com/mod/etcetera) | 1,494,773 | adventure, decoration, equipment, game-mechanics, management, mobs, storage, technology, utility, worldgen |
| 910 | [Minecraft Transit Railway](https://modrinth.com/mod/minecraft-transit-railway) | 1,481,501 | decoration, technology, transportation |
| 919 | [Create Railways Navigator](https://modrinth.com/mod/create-railways-navigator) | 1,461,384 | decoration, technology, transportation, utility |
| 945 | [Create: Misc and Things](https://modrinth.com/mod/create-misc-and-things) | 1,398,931 | decoration, equipment, technology, utility |
| 987 | [ME Requester](https://modrinth.com/mod/merequester) | 1,326,471 | storage, technology, utility |

### Tier C — `magic`-tagged mods in the top 1,000

Magic mods frequently add crafted reagents/alloys (Mana Pool, infusion, ritual) that won't derive and are good abundance-capped FU candidates.

| Rank | Mod | Downloads | Categories |
|---|---|---|---|
| 40 | [Enchantment Descriptions](https://modrinth.com/mod/enchantment-descriptions) | 30,159,196 | magic, utility |
| 81 | [Waystones](https://modrinth.com/mod/waystones) | 19,348,649 | adventure, magic, transportation, worldgen |
| 104 | [Sinytra Connector](https://modrinth.com/mod/connector) | 17,122,383 | cursed, library, magic, utility |
| 195 | [EMIffect](https://modrinth.com/mod/emiffect) | 10,028,826 | cursed, magic, utility |
| 231 | [Alex's Caves](https://modrinth.com/mod/alexs-caves) | 8,629,753 | adventure, equipment, food, game-mechanics, magic, mobs, technology, transportation, worldgen |
| 249 | [Aquamirae](https://modrinth.com/mod/aquamirae) | 7,870,615 | adventure, equipment, food, magic, mobs, worldgen |
| 251 | [Simply Swords](https://modrinth.com/mod/simply-swords) | 7,823,057 | equipment, magic |
| 265 | [Charm of Undying](https://modrinth.com/mod/charm-of-undying) | 7,408,672 | adventure, equipment, magic |
| 274 | [Vein Mining](https://modrinth.com/mod/vein-mining) | 7,226,929 | equipment, magic |
| 285 | [Enderman Overhaul](https://modrinth.com/mod/enderman-overhaul) | 6,974,913 | adventure, equipment, magic, mobs |
| 338 | [Mowzie's Mobs](https://modrinth.com/mod/mowzies-mobs) | 5,826,119 | adventure, magic, mobs |
| 380 | [Ice and Fire](https://modrinth.com/mod/ice-and-fire-dragons) | 5,236,436 | adventure, equipment, magic, mobs, worldgen |
| 381 | [Enchanting Infuser](https://modrinth.com/mod/enchanting-infuser) | 5,232,848 | equipment, magic, utility |
| 409 | [True Ending - Ender Dragon Overhaul](https://modrinth.com/mod/true-ending) | 4,885,901 | magic, mobs |
| 419 | [Goety - The Dark Arts](https://modrinth.com/mod/goety) | 4,734,498 | adventure, equipment, magic, mobs, utility, worldgen |
| 420 | [Ribbits](https://modrinth.com/mod/ribbits) | 4,734,498 | adventure, decoration, economy, magic, mobs, worldgen |
| 437 | [[TACZ]Enchanted Arsenal](https://modrinth.com/mod/enchanted-arsenal) | 4,359,431 | adventure, equipment, magic |
| 448 | [Particle Effects](https://modrinth.com/mod/particle-effects) | 4,216,817 | decoration, game-mechanics, magic, mobs, utility |
| 461 | [Icarus](https://modrinth.com/mod/icarus) | 4,110,149 | adventure, equipment, game-mechanics, magic, transportation, utility |
| 477 | [Forgiving Void](https://modrinth.com/mod/forgiving-void) | 3,914,125 | adventure, game-mechanics, magic |
| 478 | [Iron's Spells 'n Spellbooks](https://modrinth.com/mod/irons-spells-n-spellbooks) | 3,899,754 | adventure, equipment, game-mechanics, magic, mobs |
| 496 | [Mutant Monsters](https://modrinth.com/mod/mutant-monsters) | 3,733,443 | equipment, magic, mobs |
| 530 | [EMI Enchants](https://modrinth.com/mod/emienchants) | 3,338,407 | magic, utility |
| 555 | [Botania](https://modrinth.com/mod/botania) | 3,121,190 | decoration, magic, technology |
| 578 | [Tinkers' Construct](https://modrinth.com/mod/tinkers-construct) | 2,960,295 | equipment, magic, technology |
| 653 | [Relics](https://modrinth.com/mod/relics-mod) | 2,430,929 | adventure, equipment, magic |
| 663 | [Goety: Revelation](https://modrinth.com/mod/goety-revelation) | 2,385,808 | equipment, magic, mobs |
| 665 | [Ender Dragon Fight Remastered](https://modrinth.com/mod/edf-remastered) | 2,376,719 | adventure, game-mechanics, magic, mobs |
| 688 | [Mermod](https://modrinth.com/mod/mermod) | 2,265,597 | adventure, magic |
| 705 | [XP Tome](https://modrinth.com/mod/xp-tome) | 2,180,560 | adventure, magic, storage, technology, utility |
| 706 | [Enigmatic Legacy](https://modrinth.com/mod/enigmatic-legacy) | 2,180,508 | adventure, equipment, magic |
| 717 | [MC Dungeons Armors](https://modrinth.com/mod/mcda) | 2,125,710 | adventure, equipment, magic |
| 742 | [Enhanced Celestials](https://modrinth.com/mod/enhanced-celestials) | 2,028,256 | adventure, magic |
| 761 | [Clockwork](https://modrinth.com/mod/create-clockwork) | 1,948,489 | adventure, game-mechanics, magic, technology, transportation |
| 801 | [RunicLib](https://modrinth.com/mod/runiclib) | 1,806,258 | game-mechanics, library, magic, utility |
| 813 | [Realm RPG: Fallen Adventurers](https://modrinth.com/mod/realm-rpg-fallen-adventurers) | 1,764,093 | adventure, decoration, equipment, food, game-mechanics, magic, mobs, utility, worldgen |
| 846 | [Storage Delight](https://modrinth.com/mod/storage-delight) | 1,661,260 | adventure, decoration, food, magic, storage, utility |
| 855 | [Ars Nouveau's Flavors & Delight](https://modrinth.com/mod/arsdelight) | 1,636,046 | adventure, decoration, food, magic |
| 859 | [MC Dungeons Weapons](https://modrinth.com/mod/mcdw) | 1,625,290 | adventure, equipment, magic |
| 887 | [Allurement](https://modrinth.com/mod/allurement!) | 1,536,540 | adventure, equipment, game-mechanics, magic, utility |
| 893 | [Magic Vibe Decorations (Crystals, Halloween)](https://modrinth.com/mod/magic-vibe-decorations) | 1,513,661 | adventure, decoration, magic |
| 915 | [Runelic](https://modrinth.com/mod/runelic) | 1,466,817 | adventure, magic, utility |
| 936 | [The End of Herobrine](https://modrinth.com/mod/endofherobrine) | 1,421,913 | adventure, game-mechanics, magic, mobs |
| 950 | [Dragon Mounts: Legacy](https://modrinth.com/mod/dragon-mounts-legacy) | 1,390,058 | adventure, game-mechanics, magic, mobs, transportation, utility |
| 951 | [Soul Fire'd](https://modrinth.com/mod/soul-fire-d) | 1,385,892 | adventure, equipment, game-mechanics, magic |
| 998 | [Savage & Ravage](https://modrinth.com/mod/savage-and-ravage) | 1,311,371 | adventure, decoration, equipment, game-mechanics, magic, mobs, worldgen |

### Tier D — `worldgen` + `equipment` material mods (new ores / tiers)

Mods tagged **worldgen AND equipment** usually add an ore→ingot→tool tier — i.e. fresh raw-material leaves to value. (Filtered to reduce noise.)

| Rank | Mod | Downloads | Categories |
|---|---|---|---|
| 69 | [Nature's Compass](https://modrinth.com/mod/natures-compass) | 20,906,985 | adventure, equipment, game-mechanics, technology, utility, worldgen |
| 99 | [Dungeons and Taverns](https://modrinth.com/mod/dungeons-and-taverns) | 17,486,597 | adventure, equipment, game-mechanics, mobs, worldgen |
| 159 | [Deeper and Darker](https://modrinth.com/mod/deeperdarker) | 12,325,482 | adventure, equipment, mobs, worldgen |
| 219 | [Explorer's Compass](https://modrinth.com/mod/explorers-compass) | 9,075,550 | adventure, equipment, game-mechanics, technology, utility, worldgen |
| 231 | [Alex's Caves](https://modrinth.com/mod/alexs-caves) | 8,629,753 | adventure, equipment, food, game-mechanics, magic, mobs, technology, transportation, worldgen |
| 249 | [Aquamirae](https://modrinth.com/mod/aquamirae) | 7,870,615 | adventure, equipment, food, magic, mobs, worldgen |
| 284 | [[Let's Do] Beachparty](https://modrinth.com/mod/lets-do-beachparty) | 6,982,657 | adventure, decoration, equipment, food, mobs, worldgen |
| 324 | [[Let's Do] Meadow](https://modrinth.com/mod/lets-do-meadow) | 6,003,416 | decoration, equipment, food, game-mechanics, mobs, utility, worldgen |
| 325 | [The Aether](https://modrinth.com/mod/aether) | 5,992,321 | adventure, decoration, equipment, food, game-mechanics, mobs, transportation, utility, worldgen |
| 346 | [Better Archeology](https://modrinth.com/mod/better-archeology) | 5,715,729 | adventure, decoration, equipment, game-mechanics, worldgen |
| 363 | [Galosphere](https://modrinth.com/mod/galosphere) | 5,446,773 | adventure, decoration, equipment, game-mechanics, mobs, worldgen |
| 380 | [Ice and Fire](https://modrinth.com/mod/ice-and-fire-dragons) | 5,236,436 | adventure, equipment, magic, mobs, worldgen |
| 384 | [Vanilla Backport](https://modrinth.com/mod/vanillabackport) | 5,201,318 | adventure, equipment, mobs, worldgen |
| 419 | [Goety - The Dark Arts](https://modrinth.com/mod/goety) | 4,734,498 | adventure, equipment, magic, mobs, utility, worldgen |
| 425 | [Ad Astra](https://modrinth.com/mod/ad-astra) | 4,660,229 | adventure, equipment, food, mobs, technology, transportation, worldgen |
| 458 | [Blossom Blade](https://modrinth.com/mod/blossom-blade) | 4,132,492 | adventure, decoration, equipment, worldgen |
| 513 | [Hybrid Aquatic](https://modrinth.com/mod/hybrid-aquatic) | 3,553,016 | adventure, equipment, food, mobs, worldgen |
| 526 | [The Endergetic Expansion](https://modrinth.com/mod/endergetic) | 3,368,544 | adventure, decoration, equipment, food, game-mechanics, mobs, transportation, worldgen |
| 534 | [Medieval Buildings](https://modrinth.com/mod/medieval-buildings) | 3,306,486 | adventure, decoration, equipment, social, worldgen |
| 542 | [Marium's Soulslike Weaponry](https://modrinth.com/mod/mariums-soulslike-weaponry) | 3,219,516 | adventure, equipment, mobs, worldgen |
| 565 | [Delightful](https://modrinth.com/mod/delightful) | 3,069,593 | equipment, food, storage, utility, worldgen |
| 566 | [Jaden's Nether Expansion](https://modrinth.com/mod/jadens-nether-expansion) | 3,067,427 | adventure, decoration, equipment, food, game-mechanics, mobs, utility, worldgen |
| 581 | [Towers of the Wild Modded](https://modrinth.com/mod/totw-modded) | 2,946,284 | adventure, decoration, equipment, game-mechanics, transportation, worldgen |
| 599 | [ATi Structures - Vanilla Edition](https://modrinth.com/mod/ati-structures-vanilla-edition) | 2,807,294 | adventure, equipment, worldgen |
| 602 | [Mekanism](https://modrinth.com/mod/mekanism) | 2,797,590 | equipment, storage, technology, worldgen |
| 639 | [Underground Worlds](https://modrinth.com/mod/underground-worlds) | 2,557,614 | adventure, decoration, equipment, mobs, storage, worldgen |
| 646 | [Mobs of Mythology](https://modrinth.com/mod/mobs-of-mythology) | 2,506,350 | adventure, equipment, mobs, worldgen |
| 669 | [Deep Aether](https://modrinth.com/mod/deep-aether) | 2,359,920 | adventure, decoration, equipment, mobs, worldgen |
| 676 | [Atmospheric](https://modrinth.com/mod/atmospheric) | 2,295,618 | adventure, decoration, equipment, food, mobs, worldgen |
| 678 | [The Undergarden](https://modrinth.com/mod/the-undergarden) | 2,292,965 | adventure, equipment, food, mobs, worldgen |
| 687 | [Upgrade Aquatic](https://modrinth.com/mod/upgrade-aquatic) | 2,267,324 | adventure, decoration, equipment, food, game-mechanics, mobs, worldgen |
| 693 | [Paragliders](https://modrinth.com/mod/paragliders) | 2,235,657 | adventure, equipment, game-mechanics, transportation, worldgen |
| 707 | [Autumnity](https://modrinth.com/mod/autumnity) | 2,176,053 | adventure, decoration, equipment, food, game-mechanics, mobs, worldgen |
| 714 | [Medieval Buildings [End Edition]](https://modrinth.com/mod/medieval-buildings-end-edition) | 2,141,196 | adventure, decoration, equipment, mobs, worldgen |
| 722 | [Vintage Delight](https://modrinth.com/mod/vintage-delight) | 2,106,460 | equipment, food, game-mechanics, worldgen |
| 744 | [The Conjurer](https://modrinth.com/mod/the-conjurer) | 2,022,559 | equipment, mobs, worldgen |
| 750 | [Aether: Lost Content Addon](https://modrinth.com/mod/aether-lost-content) | 2,007,552 | adventure, equipment, mobs, worldgen |
| 772 | [Blue Skies](https://modrinth.com/mod/blue-skies) | 1,903,378 | adventure, decoration, equipment, food, game-mechanics, mobs, worldgen |
| 774 | [Terramity](https://modrinth.com/mod/terramity) | 1,898,867 | adventure, decoration, equipment, game-mechanics, mobs, worldgen |
| 779 | [Bosses'Rise](https://modrinth.com/mod/bossesrise) | 1,873,008 | adventure, equipment, mobs, worldgen |
| 782 | [Dungeon's Delight](https://modrinth.com/mod/dungeons_delight) | 1,866,230 | adventure, decoration, equipment, food, game-mechanics, mobs, storage, worldgen |
| 784 | [Superb Warfare](https://modrinth.com/mod/superb-warfare) | 1,864,688 | adventure, equipment, mobs, worldgen |
| 796 | [The Aether: Redux](https://modrinth.com/mod/the-aether-redux) | 1,842,450 | adventure, decoration, equipment, food, game-mechanics, mobs, utility, worldgen |
| 797 | [Spelunkery](https://modrinth.com/mod/spelunkery) | 1,833,728 | adventure, decoration, equipment, food, game-mechanics, utility, worldgen |
| 813 | [Realm RPG: Fallen Adventurers](https://modrinth.com/mod/realm-rpg-fallen-adventurers) | 1,764,093 | adventure, decoration, equipment, food, game-mechanics, magic, mobs, utility, worldgen |
| 815 | [Enderite Mod](https://modrinth.com/mod/enderite-mod) | 1,755,871 | adventure, equipment, game-mechanics, storage, utility, worldgen |
| 825 | [Stalwart Dungeons](https://modrinth.com/mod/stalwart-dungeons) | 1,711,971 | adventure, equipment, worldgen |
| 842 | [End's Phantasm](https://modrinth.com/mod/ends-phantasm) | 1,672,404 | adventure, decoration, equipment, food, game-mechanics, mobs, worldgen |
| 868 | [Mischief Illagers](https://modrinth.com/mod/mischief-illagers) | 1,605,058 | adventure, equipment, mobs, worldgen |
| 877 | [Cracker's Wither Storm Mod](https://modrinth.com/mod/crackers-wither-storm-mod) | 1,565,918 | adventure, cursed, equipment, game-mechanics, mobs, worldgen |
| 896 | [Dungeon Now Loading](https://modrinth.com/mod/dungeon-now-loading) | 1,508,451 | adventure, decoration, equipment, game-mechanics, mobs, worldgen |
| 897 | [The Midnight Lurker](https://modrinth.com/mod/the-midnight-lurker) | 1,507,113 | adventure, cursed, equipment, mobs, worldgen |
| 899 | [Sully's Mod](https://modrinth.com/mod/sullysmod) | 1,506,212 | adventure, decoration, equipment, food, mobs, worldgen |
| 904 | [Etcetera](https://modrinth.com/mod/etcetera) | 1,494,773 | adventure, decoration, equipment, game-mechanics, management, mobs, storage, technology, utility, worldgen |
| 916 | [Enlightend](https://modrinth.com/mod/enlightend) | 1,466,292 | adventure, decoration, equipment, food, game-mechanics, mobs, worldgen |
| 946 | [ATi Structures](https://modrinth.com/mod/ati-structures-fabricforge) | 1,397,495 | adventure, decoration, equipment, mobs, worldgen |
| 948 | [tetra](https://modrinth.com/mod/tetra) | 1,393,362 | adventure, equipment, game-mechanics, worldgen |
| 959 | [Vic's Point Blank](https://modrinth.com/mod/vics-point-blank) | 1,372,281 | adventure, equipment, mobs, worldgen |
| 993 | [Unusual End](https://modrinth.com/mod/unusual_end) | 1,320,606 | adventure, equipment, worldgen |
| 998 | [Savage & Ravage](https://modrinth.com/mod/savage-and-ravage) | 1,311,371 | adventure, decoration, equipment, game-mechanics, magic, mobs, worldgen |

## Full ranked list — top 1,000 (Forge, 1.20.1, by downloads)

| # | Mod | Downloads | Categories |
|---|---|---|---|
| 1 | [Cloth Config API](https://modrinth.com/mod/cloth-config) | 128,586,953 | library |
| 2 | [Entity Culling](https://modrinth.com/mod/entityculling) | 121,089,496 | optimization |
| 3 | [FerriteCore](https://modrinth.com/mod/ferrite-core) | 117,271,399 | optimization, utility |
| 4 | [ImmediatelyFast](https://modrinth.com/mod/immediatelyfast) | 95,300,284 | optimization |
| 5 | [YetAnotherConfigLib (YACL)](https://modrinth.com/mod/yacl) | 92,280,209 | library, management, utility |
| 6 | [Xaero's Minimap](https://modrinth.com/mod/xaeros-minimap) | 84,820,372 | adventure, transportation, utility |
| 7 | [Architectury API](https://modrinth.com/mod/architectury-api) | 79,720,694 | library |
| 8 | [[ETF] Entity Texture Features](https://modrinth.com/mod/entitytexturefeatures) | 79,105,716 | decoration, utility |
| 9 | [[EMF] Entity Model Features](https://modrinth.com/mod/entity-model-features) | 74,851,052 | decoration, mobs, utility |
| 10 | [Xaero's World Map](https://modrinth.com/mod/xaeros-world-map) | 74,651,111 | adventure, transportation, utility |
| 11 | [AppleSkin](https://modrinth.com/mod/appleskin) | 69,474,410 | food, utility |
| 12 | [Not Enough Animations](https://modrinth.com/mod/not-enough-animations) | 66,753,124 | adventure, decoration |
| 13 | [ModernFix](https://modrinth.com/mod/modernfix) | 62,676,432 | optimization, utility |
| 14 | [3D Skin Layers](https://modrinth.com/mod/3dskinlayers) | 57,045,030 | adventure, decoration |
| 15 | [Continuity](https://modrinth.com/mod/continuity) | 56,595,831 | decoration, utility |
| 16 | [Just Enough Items (JEI)](https://modrinth.com/mod/jei) | 56,400,994 | library, utility |
| 17 | [VeinMiner](https://modrinth.com/mod/veinminer) | 53,857,982 | cursed, equipment, game-mechanics, technology, utility |
| 18 | [Jade 🔍](https://modrinth.com/mod/jade) | 53,729,250 | library, utility |
| 19 | [Geckolib](https://modrinth.com/mod/geckolib) | 52,102,718 | game-mechanics, library, utility |
| 20 | [Collective](https://modrinth.com/mod/collective) | 51,788,608 | library |
| 21 | [Dynamic FPS](https://modrinth.com/mod/dynamic-fps) | 51,547,601 | management, optimization, utility |
| 22 | [Simple Voice Chat](https://modrinth.com/mod/simple-voice-chat) | 51,363,969 | adventure, social, utility |
| 23 | [FancyMenu](https://modrinth.com/mod/fancymenu) | 49,234,521 | utility |
| 24 | [Konkrete](https://modrinth.com/mod/konkrete) | 48,623,199 | library |
| 25 | [Puzzles Lib](https://modrinth.com/mod/puzzles-lib) | 48,388,228 | library |
| 26 | [Balm](https://modrinth.com/mod/balm) | 46,370,266 | library |
| 27 | [No Chat Reports](https://modrinth.com/mod/no-chat-reports) | 45,773,274 | management, social, utility |
| 28 | [Mouse Tweaks](https://modrinth.com/mod/mouse-tweaks) | 44,662,152 | storage, utility |
| 29 | [Sound Physics Remastered](https://modrinth.com/mod/sound-physics-remastered) | 42,230,112 | adventure, utility |
| 30 | [CreativeCore](https://modrinth.com/mod/creativecore) | 39,571,083 | library |
| 31 | [Melody](https://modrinth.com/mod/melody) | 39,482,387 | library |
| 32 | [Chat Heads](https://modrinth.com/mod/chat-heads) | 38,172,214 | decoration, social |
| 33 | [Bookshelf](https://modrinth.com/mod/bookshelf-lib) | 37,198,049 | library, utility |
| 34 | [Kotlin for Forge](https://modrinth.com/mod/kotlin-for-forge) | 35,194,347 | library |
| 35 | [Memory Leak Fix](https://modrinth.com/mod/memoryleakfix) | 33,278,892 | optimization |
| 36 | [Essential Mod](https://modrinth.com/mod/essential) | 32,338,640 | management, social, utility |
| 37 | [Moonlight Lib](https://modrinth.com/mod/moonlight) | 31,579,991 | library |
| 38 | [TerraBlender](https://modrinth.com/mod/terrablender) | 31,175,494 | library, worldgen |
| 39 | [BadOptimizations](https://modrinth.com/mod/badoptimizations) | 30,673,735 | optimization |
| 40 | [Enchantment Descriptions](https://modrinth.com/mod/enchantment-descriptions) | 30,159,196 | magic, utility |
| 41 | [Inventory Profiles Next](https://modrinth.com/mod/inventory-profiles-next) | 30,055,378 | storage, utility |
| 42 | [AmbientSounds](https://modrinth.com/mod/ambientsounds) | 29,610,460 | decoration |
| 43 | [Searchables](https://modrinth.com/mod/searchables) | 29,523,793 | library, utility |
| 44 | [Controlling](https://modrinth.com/mod/controlling) | 29,495,121 | utility |
| 45 | [Iceberg](https://modrinth.com/mod/iceberg) | 29,199,967 | library |
| 46 | [Clumps](https://modrinth.com/mod/clumps) | 29,126,221 | storage, utility |
| 47 | [Patchouli](https://modrinth.com/mod/patchouli) | 28,735,451 | library, utility |
| 48 | [Shulker Box Tooltip](https://modrinth.com/mod/shulkerboxtooltip) | 28,504,670 | storage, utility |
| 49 | [Model Gap Fix](https://modrinth.com/mod/modelfix) | 28,369,669 | optimization, utility |
| 50 | [YUNG's API](https://modrinth.com/mod/yungs-api) | 28,088,171 | library, worldgen |
| 51 | [Oculus](https://modrinth.com/mod/oculus) | 28,013,535 | decoration, optimization |
| 52 | [Resourceful Lib](https://modrinth.com/mod/resourceful-lib) | 27,970,576 | library |
| 53 | [Cobblemon](https://modrinth.com/mod/cobblemon) | 27,710,893 | adventure, game-mechanics, mobs, worldgen |
| 54 | [BetterF3](https://modrinth.com/mod/betterf3) | 27,495,390 | decoration, game-mechanics, utility |
| 55 | [libIPN](https://modrinth.com/mod/libipn) | 27,420,487 | library |
| 56 | [Embeddium](https://modrinth.com/mod/embeddium) | 27,399,139 | optimization |
| 57 | [Fzzy Config](https://modrinth.com/mod/fzzy-config) | 26,788,592 | game-mechanics, library, management, utility |
| 58 | [Distant Horizons](https://modrinth.com/mod/distanthorizons) | 26,549,680 | optimization, utility |
| 59 | [Cherished Worlds](https://modrinth.com/mod/cherished-worlds) | 25,658,232 | management, utility |
| 60 | [SuperMartijn642's Config Lib](https://modrinth.com/mod/supermartijn642s-config-lib) | 25,372,696 | library |
| 61 | [Biomes O' Plenty](https://modrinth.com/mod/biomes-o-plenty) | 25,216,002 | adventure, decoration, worldgen |
| 62 | [Curios API](https://modrinth.com/mod/curios) | 24,941,928 | adventure, equipment, library, utility |
| 63 | [CoroUtil](https://modrinth.com/mod/coroutil) | 24,461,861 | library |
| 64 | [playerAnimator](https://modrinth.com/mod/playeranimator) | 22,192,210 | library |
| 65 | [EMI](https://modrinth.com/mod/emi) | 22,058,362 | library, utility |
| 66 | [Packet Fixer](https://modrinth.com/mod/packet-fixer) | 21,646,743 | optimization, utility |
| 67 | [Roughly Enough Items (REI)](https://modrinth.com/mod/rei) | 21,172,337 | — |
| 68 | [Visual Workbench](https://modrinth.com/mod/visual-workbench) | 21,049,864 | utility |
| 69 | [Nature's Compass](https://modrinth.com/mod/natures-compass) | 20,906,985 | adventure, equipment, game-mechanics, technology, utility, worldgen |
| 70 | [MidnightLib](https://modrinth.com/mod/midnightlib) | 20,845,588 | library, utility |
| 71 | [Better Advancements](https://modrinth.com/mod/better-advancements) | 20,729,299 | utility |
| 72 | [[TaCZ] Timeless and Classics Zero](https://modrinth.com/mod/timeless-and-classics-zero) | 20,695,684 | adventure, equipment |
| 73 | [Polymorph](https://modrinth.com/mod/polymorph) | 20,497,550 | utility |
| 74 | [Supplementaries](https://modrinth.com/mod/supplementaries) | 20,403,596 | decoration, game-mechanics, storage, utility |
| 75 | [Cubes Without Borders](https://modrinth.com/mod/cubes-without-borders) | 20,397,673 | optimization, utility |
| 76 | [Carry On](https://modrinth.com/mod/carry-on) | 20,227,124 | game-mechanics, storage, transportation, utility |
| 77 | [Forgified Fabric API](https://modrinth.com/mod/forgified-fabric-api) | 19,976,378 | library |
| 78 | [Handcrafted](https://modrinth.com/mod/handcrafted) | 19,946,727 | decoration, utility |
| 79 | [Remove Reloading Screen](https://modrinth.com/mod/rrls) | 19,933,185 | cursed, optimization, utility |
| 80 | [Euphoria Patches](https://modrinth.com/mod/euphoria-patches) | 19,886,939 | technology, utility |
| 81 | [Waystones](https://modrinth.com/mod/waystones) | 19,348,649 | adventure, magic, transportation, worldgen |
| 82 | [Create](https://modrinth.com/mod/create) | 19,154,845 | decoration, technology, utility |
| 83 | [Resourceful Config](https://modrinth.com/mod/resourceful-config) | 19,143,814 | library |
| 84 | [CustomSkinLoader](https://modrinth.com/mod/customskinloader) | 19,114,628 | decoration |
| 85 | [NetherPortalFix](https://modrinth.com/mod/netherportalfix) | 18,562,334 | game-mechanics, utility |
| 86 | [Let Me Despawn](https://modrinth.com/mod/lmd) | 18,503,024 | optimization |
| 87 | [YUNG's Better Nether Fortresses](https://modrinth.com/mod/yungs-better-nether-fortresses) | 18,436,470 | adventure, decoration, worldgen |
| 88 | [Athena](https://modrinth.com/mod/athena-ctm) | 18,170,168 | decoration, library |
| 89 | [AttributeFix](https://modrinth.com/mod/attributefix) | 18,156,867 | utility |
| 90 | [Terralith](https://modrinth.com/mod/terralith) | 18,053,709 | worldgen |
| 91 | [Quark](https://modrinth.com/mod/quark) | 18,045,472 | game-mechanics, technology, utility |
| 92 | [Drippy Loading Screen](https://modrinth.com/mod/drippy-loading-screen) | 17,873,336 | utility |
| 93 | [Lootr](https://modrinth.com/mod/lootr) | 17,813,824 | utility |
| 94 | [Farmer's Delight](https://modrinth.com/mod/farmers-delight) | 17,752,361 | decoration, equipment, food |
| 95 | [YUNG's Better Ocean Monuments](https://modrinth.com/mod/yungs-better-ocean-monuments) | 17,700,906 | adventure, decoration, worldgen |
| 96 | [Comforts](https://modrinth.com/mod/comforts) | 17,694,133 | adventure, decoration |
| 97 | [YUNG's Better Dungeons](https://modrinth.com/mod/yungs-better-dungeons) | 17,606,264 | adventure, decoration, worldgen |
| 98 | [e4mc](https://modrinth.com/mod/e4mc) | 17,548,846 | social, utility |
| 99 | [Dungeons and Taverns](https://modrinth.com/mod/dungeons-and-taverns) | 17,486,597 | adventure, equipment, game-mechanics, mobs, worldgen |
| 100 | [Chipped](https://modrinth.com/mod/chipped) | 17,466,313 | decoration |
| 101 | [Fast IP Ping](https://modrinth.com/mod/fast-ip-ping) | 17,448,146 | optimization |
| 102 | [Dynamic Crosshair](https://modrinth.com/mod/dynamiccrosshair) | 17,401,603 | utility |
| 103 | [Better Third Person](https://modrinth.com/mod/better-third-person) | 17,125,385 | adventure, game-mechanics |
| 104 | [Sinytra Connector](https://modrinth.com/mod/connector) | 17,122,383 | cursed, library, magic, utility |
| 105 | [Open Parties and Claims](https://modrinth.com/mod/open-parties-and-claims) | 17,092,877 | library, utility |
| 106 | [Rhino](https://modrinth.com/mod/rhino) | 16,991,228 | library, utility |
| 107 | [Physics Mod](https://modrinth.com/mod/physicsmod) | 16,923,871 | decoration, utility |
| 108 | [Lithostitched](https://modrinth.com/mod/lithostitched) | 16,902,248 | library, utility, worldgen |
| 109 | [spark](https://modrinth.com/mod/spark) | 16,791,678 | utility |
| 110 | [Traveler's Backpack](https://modrinth.com/mod/travelersbackpack) | 16,698,461 | adventure, decoration, equipment, management, storage |
| 111 | [GlitchCore](https://modrinth.com/mod/glitchcore) | 16,633,189 | library |
| 112 | [Kiwi 🥝](https://modrinth.com/mod/kiwi) | 16,549,247 | library |
| 113 | [YUNG's Better Jungle Temples](https://modrinth.com/mod/yungs-better-jungle-temples) | 16,468,587 | adventure, decoration, worldgen |
| 114 | [YUNG's Better Mineshafts](https://modrinth.com/mod/yungs-better-mineshafts) | 16,462,707 | adventure, decoration, worldgen |
| 115 | [KubeJS](https://modrinth.com/mod/kubejs) | 16,039,247 | library, utility |
| 116 | [Sodium Options API](https://modrinth.com/mod/sodium-options-api) | 15,994,382 | optimization, utility |
| 117 | [YUNG's Better End Island](https://modrinth.com/mod/yungs-better-end-island) | 15,903,103 | adventure, decoration, worldgen |
| 118 | [Modern UI](https://modrinth.com/mod/modern-ui) | 15,787,015 | library, optimization, social, utility |
| 119 | [Particle Rain](https://modrinth.com/mod/particle-rain) | 15,555,944 | decoration |
| 120 | [Chat Animation [Smooth Chat]](https://modrinth.com/mod/chatanimation) | 15,487,387 | decoration, social, utility |
| 121 | [Tom's Simple Storage Mod](https://modrinth.com/mod/toms-storage) | 15,267,337 | storage |
| 122 | [Freecam](https://modrinth.com/mod/freecam) | 15,055,236 | utility |
| 123 | [Prism](https://modrinth.com/mod/prism-lib) | 14,878,993 | decoration, library, utility |
| 124 | [Sodium Dynamic Lights](https://modrinth.com/mod/sodium-dynamic-lights) | 14,777,868 | decoration, optimization, utility |
| 125 | [Almanac](https://modrinth.com/mod/almanac) | 14,770,804 | library, utility |
| 126 | [YUNG's Better Strongholds](https://modrinth.com/mod/yungs-better-strongholds) | 14,734,304 | adventure, decoration, worldgen |
| 127 | [Amendments](https://modrinth.com/mod/amendments) | 14,678,417 | — |
| 128 | [Yeetus Experimentus](https://modrinth.com/mod/yeetus-experimentus) | 14,636,378 | utility, worldgen |
| 129 | [M.R.U](https://modrinth.com/mod/mru) | 14,631,507 | library |
| 130 | [I18nUpdateMod](https://modrinth.com/mod/i18nupdatemod) | 14,599,260 | utility |
| 131 | [YUNG's Better Witch Huts](https://modrinth.com/mod/yungs-better-witch-huts) | 14,448,366 | adventure, decoration, worldgen |
| 132 | [Cristel Lib](https://modrinth.com/mod/cristel-lib) | 14,206,193 | cursed, library, worldgen |
| 133 | [Item Highlighter](https://modrinth.com/mod/item-highlighter) | 14,155,822 | adventure, equipment, game-mechanics, utility |
| 134 | [Default Options](https://modrinth.com/mod/default-options) | 14,117,977 | management, utility |
| 135 | [Better Combat](https://modrinth.com/mod/better-combat) | 13,914,188 | adventure, equipment, game-mechanics, library |
| 136 | [Towns and Towers](https://modrinth.com/mod/towns-and-towers) | 13,841,183 | adventure, worldgen |
| 137 | [Wavey Capes](https://modrinth.com/mod/wavey-capes) | 13,675,334 | decoration |
| 138 | [Advancement Plaques](https://modrinth.com/mod/advancement-plaques) | 13,645,057 | adventure, game-mechanics, utility |
| 139 | [Chloride (Embeddium++/Sodium++)](https://modrinth.com/mod/chloride) | 13,573,542 | optimization, utility |
| 140 | [Cut Through](https://modrinth.com/mod/cut-through) | 13,560,389 | adventure, equipment, game-mechanics |
| 141 | [Chunky](https://modrinth.com/mod/chunky) | 13,551,296 | optimization, utility, worldgen |
| 142 | [Tectonic](https://modrinth.com/mod/tectonic) | 13,526,650 | adventure, worldgen |
| 143 | [YUNG's Better Desert Temples](https://modrinth.com/mod/yungs-better-desert-temples) | 13,525,848 | adventure, decoration, worldgen |
| 144 | [EnhancedVisuals](https://modrinth.com/mod/enhancedvisuals) | 13,522,510 | adventure, decoration |
| 145 | [InvMove](https://modrinth.com/mod/invmove) | 13,444,984 | transportation, utility |
| 146 | [SuperMartijn642's Core Lib](https://modrinth.com/mod/supermartijn642s-core-lib) | 13,423,821 | library |
| 147 | [Sophisticated Backpacks](https://modrinth.com/mod/sophisticated-backpacks) | 13,297,676 | storage |
| 148 | [YUNG's Bridges](https://modrinth.com/mod/yungs-bridges) | 13,151,117 | adventure, decoration, worldgen |
| 149 | [L_Ender's Cataclysm](https://modrinth.com/mod/l_enders-cataclysm) | 13,035,677 | adventure, equipment, mobs |
| 150 | [Easy Anvils](https://modrinth.com/mod/easy-anvils) | 13,028,457 | equipment, game-mechanics |
| 151 | [Sophisticated Core](https://modrinth.com/mod/sophisticated-core) | 12,795,715 | library, storage |
| 152 | [ShatterLib | OctoLib](https://modrinth.com/mod/shatterbyte-lib) | 12,722,764 | library |
| 153 | [Citadel](https://modrinth.com/mod/citadel) | 12,624,878 | library |
| 154 | [Sounds](https://modrinth.com/mod/sound) | 12,446,168 | game-mechanics, social, utility |
| 155 | [Another Furniture](https://modrinth.com/mod/another-furniture) | 12,405,117 | decoration, utility |
| 156 | [Global Packs](https://modrinth.com/mod/globalpacks) | 12,383,200 | utility |
| 157 | [Pick Up Notifier](https://modrinth.com/mod/pick-up-notifier) | 12,363,159 | utility |
| 158 | [What Are They Up To (Watut)](https://modrinth.com/mod/what-are-they-up-to) | 12,330,703 | social, utility |
| 159 | [Deeper and Darker](https://modrinth.com/mod/deeperdarker) | 12,325,482 | adventure, equipment, mobs, worldgen |
| 160 | [Fastload](https://modrinth.com/mod/fastload) | 12,254,423 | optimization, worldgen |
| 161 | [CorgiLib](https://modrinth.com/mod/corgilib) | 12,227,605 | library |
| 162 | [Immersive Aircraft](https://modrinth.com/mod/immersive-aircraft) | 12,137,127 | adventure, game-mechanics, technology, transportation, utility |
| 163 | [Starlight (Forge)](https://modrinth.com/mod/starlight-forge) | 11,969,794 | optimization |
| 164 | [Zeta](https://modrinth.com/mod/zeta) | 11,886,822 | — |
| 165 | [Polytone](https://modrinth.com/mod/polytone) | 11,695,137 | — |
| 166 | [First-person Model](https://modrinth.com/mod/first-person-model) | 11,603,865 | decoration, equipment, social |
| 167 | [Durability Tooltip](https://modrinth.com/mod/durability-tooltip) | 11,585,940 | utility |
| 168 | [JourneyMap](https://modrinth.com/mod/journeymap) | 11,560,670 | adventure, utility |
| 169 | [Not Enough Crashes](https://modrinth.com/mod/notenoughcrashes) | 11,423,608 | utility |
| 170 | [Alex's Mobs](https://modrinth.com/mod/alexs-mobs) | 11,373,250 | adventure, equipment, mobs |
| 171 | [BisectHosting Server Integration Menu](https://modrinth.com/mod/bisect-mod) | 11,327,649 | library, utility |
| 172 | [Elytra Slot](https://modrinth.com/mod/elytra-slot) | 11,312,997 | equipment, transportation |
| 173 | [JamLib](https://modrinth.com/mod/jamlib) | 11,286,672 | library |
| 174 | [Exposure](https://modrinth.com/mod/exposure) | 11,230,909 | adventure, game-mechanics, minigame, utility |
| 175 | [Make Bubbles Pop](https://modrinth.com/mod/make_bubbles_pop) | 11,152,248 | adventure, decoration |
| 176 | [Bad Wither No Cookie - Reloaded](https://modrinth.com/mod/bad-wither-no-cookie) | 11,146,883 | game-mechanics |
| 177 | [Easy Magic](https://modrinth.com/mod/easy-magic) | 11,072,713 | utility |
| 178 | [BetterGrassify](https://modrinth.com/mod/bettergrassify) | 11,045,869 | optimization, utility |
| 179 | [AI Improvements](https://modrinth.com/mod/ai-improvements) | 11,020,556 | optimization |
| 180 | [JustEnoughCharacters](https://modrinth.com/mod/justenoughcharacters) | 10,847,772 | utility |
| 181 | [Fusion (Connected Textures)](https://modrinth.com/mod/fusion-connected-textures) | 10,842,943 | decoration, library |
| 182 | [IMBlocker](https://modrinth.com/mod/imblocker-original) | 10,768,939 | utility |
| 183 | [Boat Item View](https://modrinth.com/mod/boat-item-view) | 10,681,030 | adventure, decoration, equipment, game-mechanics, management, optimization, transportation, utility |
| 184 | [Guard Villagers](https://modrinth.com/mod/guard-villagers) | 10,659,500 | mobs, utility |
| 185 | [Artifacts](https://modrinth.com/mod/artifacts) | 10,600,758 | adventure, worldgen |
| 186 | [CraftPresence](https://modrinth.com/mod/craftpresence) | 10,529,121 | library, utility |
| 187 | [Crash Assistant](https://modrinth.com/mod/crash-assistant) | 10,525,916 | management, optimization, social, utility |
| 188 | [Particle Core](https://modrinth.com/mod/particle-core) | 10,471,710 | game-mechanics, management, optimization, utility |
| 189 | [Necronomicon API](https://modrinth.com/mod/necronomicon) | 10,448,297 | library |
| 190 | [YUNG's Extras](https://modrinth.com/mod/yungs-extras) | 10,393,644 | adventure, decoration, worldgen |
| 191 | [TxniLib](https://modrinth.com/mod/txnilib) | 10,385,853 | library |
| 192 | [Leaves Be Gone](https://modrinth.com/mod/leaves-be-gone) | 10,378,854 | optimization, utility |
| 193 | [ServerCore](https://modrinth.com/mod/servercore) | 10,230,187 | optimization, utility |
| 194 | [FallingTree](https://modrinth.com/mod/fallingtree) | 10,140,725 | utility |
| 195 | [EMIffect](https://modrinth.com/mod/emiffect) | 10,028,826 | cursed, magic, utility |
| 196 | [Create: Steam 'n' Rails](https://modrinth.com/mod/create-steam-n-rails) | 9,989,654 | adventure, decoration, technology, transportation, utility |
| 197 | [Smarter Farmers (farmers replant)](https://modrinth.com/mod/smarter-farmers-farmers-replant) | 9,929,315 | food |
| 198 | [Ping Wheel](https://modrinth.com/mod/ping-wheel) | 9,870,079 | game-mechanics, social, utility |
| 199 | [Dynamic Trees](https://modrinth.com/mod/dynamictrees) | 9,845,970 | adventure, decoration, game-mechanics, utility, worldgen |
| 200 | [Forge Config Screens](https://modrinth.com/mod/forge-config-screens) | 9,815,311 | library, management, utility |
| 201 | [[Let's Do] Vinery](https://modrinth.com/mod/lets-do-vinery) | 9,801,366 | adventure, decoration, food, game-mechanics, mobs, utility |
| 202 | [Villager Names](https://modrinth.com/mod/villager-names-serilum) | 9,737,001 | adventure, game-mechanics, social |
| 203 | [Spartan Weaponry](https://modrinth.com/mod/spartan-weaponry) | 9,689,175 | equipment, game-mechanics |
| 204 | [RightClickHarvest](https://modrinth.com/mod/rightclickharvest) | 9,657,613 | food |
| 205 | [MmmMmmMmmMmm](https://modrinth.com/mod/mmmmmmmmmmmm) | 9,600,055 | decoration, game-mechanics, utility |
| 206 | [Tips](https://modrinth.com/mod/tips) | 9,462,651 | adventure, utility |
| 207 | [Rebind Narrator](https://modrinth.com/mod/rebind-narrator) | 9,436,028 | management |
| 208 | [AzureLib](https://modrinth.com/mod/azurelib) | 9,388,188 | game-mechanics, library, utility |
| 209 | [Accessories](https://modrinth.com/mod/accessories) | 9,384,820 | adventure, equipment, library, utility |
| 210 | [Connector Extras](https://modrinth.com/mod/connector-extras) | 9,355,174 | utility |
| 211 | [Resourcify](https://modrinth.com/mod/resourcify) | 9,306,768 | management, utility |
| 212 | [Naturalist](https://modrinth.com/mod/naturalist) | 9,207,993 | adventure, mobs, worldgen |
| 213 | [Sodium Extras](https://modrinth.com/mod/sodium-extras) | 9,187,128 | optimization, utility |
| 214 | [GraveStone Mod](https://modrinth.com/mod/gravestone-mod) | 9,165,975 | adventure, technology, utility |
| 215 | [Starter Kit](https://modrinth.com/mod/starter-kit) | 9,155,555 | adventure, management, utility |
| 216 | [Yes Steve Model](https://modrinth.com/mod/yes-steve-model) | 9,109,066 | decoration |
| 217 | [Shoulder Surfing Reloaded](https://modrinth.com/mod/shoulder-surfing-reloaded) | 9,103,497 | adventure, utility |
| 218 | [Do a Barrel Roll](https://modrinth.com/mod/do-a-barrel-roll) | 9,093,386 | equipment, transportation |
| 219 | [Explorer's Compass](https://modrinth.com/mod/explorers-compass) | 9,075,550 | adventure, equipment, game-mechanics, technology, utility, worldgen |
| 220 | [XaeroPlus](https://modrinth.com/mod/xaeroplus) | 9,016,501 | adventure, transportation, utility |
| 221 | [LootJS: KubeJS Addon](https://modrinth.com/mod/lootjs) | 9,016,285 | utility |
| 222 | [Creeper Overhaul](https://modrinth.com/mod/creeper-overhaul) | 8,966,995 | adventure, mobs |
| 223 | [When Dungeons Arise](https://modrinth.com/mod/when-dungeons-arise) | 8,900,396 | adventure, worldgen |
| 224 | [ChoiceTheorem's Overhauled Village](https://modrinth.com/mod/ct-overhaul-village) | 8,886,724 | adventure, economy, utility, worldgen |
| 225 | [Advanced Netherite](https://modrinth.com/mod/advanced-netherite) | 8,804,950 | adventure, equipment, library |
| 226 | [Legendary Tooltips](https://modrinth.com/mod/legendary-tooltips) | 8,804,135 | adventure, decoration, equipment, game-mechanics, utility |
| 227 | [Alternate Current](https://modrinth.com/mod/alternate-current) | 8,762,743 | optimization, technology, utility |
| 228 | [Highlight](https://modrinth.com/mod/highlight) | 8,715,677 | decoration |
| 229 | [Macaw's Fences and Walls](https://modrinth.com/mod/macaws-fences-and-walls) | 8,666,278 | decoration |
| 230 | [Resource Pack Overrides](https://modrinth.com/mod/resource-pack-overrides) | 8,642,121 | library, utility |
| 231 | [Alex's Caves](https://modrinth.com/mod/alexs-caves) | 8,629,753 | adventure, equipment, food, game-mechanics, magic, mobs, technology, transportation, worldgen |
| 232 | [The Lost Cities](https://modrinth.com/mod/the-lost-cities) | 8,620,712 | adventure, worldgen |
| 233 | [Macaw's Windows](https://modrinth.com/mod/macaws-windows) | 8,610,495 | decoration |
| 234 | [Oh The Biomes We've Gone](https://modrinth.com/mod/oh-the-biomes-weve-gone) | 8,607,183 | adventure, decoration, food, mobs, worldgen |
| 235 | [Diagonal Fences](https://modrinth.com/mod/diagonal-fences) | 8,601,750 | decoration, utility |
| 236 | [Client Tweaks](https://modrinth.com/mod/client-tweaks) | 8,581,365 | game-mechanics, utility |
| 237 | [Zume](https://modrinth.com/mod/zume) | 8,533,866 | utility |
| 238 | [Neat](https://modrinth.com/mod/neat) | 8,500,402 | utility |
| 239 | [Paxi](https://modrinth.com/mod/paxi) | 8,399,993 | management, utility |
| 240 | [Spartan Shields](https://modrinth.com/mod/spartan-shields) | 8,329,591 | equipment, game-mechanics |
| 241 | [Geophilic](https://modrinth.com/mod/geophilic) | 8,287,094 | adventure, worldgen |
| 242 | [Better Mods Button](https://modrinth.com/mod/better-mods-button) | 8,252,356 | utility |
| 243 | [Pehkui](https://modrinth.com/mod/pehkui) | 8,241,406 | game-mechanics, library |
| 244 | [Traveler's Titles](https://modrinth.com/mod/travelers-titles) | 8,109,908 | adventure, decoration, utility |
| 245 | [Crafting Tweaks](https://modrinth.com/mod/crafting-tweaks) | 8,083,437 | management, utility |
| 246 | [Incendium](https://modrinth.com/mod/incendium) | 8,053,456 | adventure, worldgen |
| 247 | [Create Crafts & Additions](https://modrinth.com/mod/createaddition) | 8,041,520 | decoration, food, storage, technology, transportation, utility |
| 248 | [Overflowing Bars](https://modrinth.com/mod/overflowing-bars) | 7,948,012 | game-mechanics, management, utility |
| 249 | [Aquamirae](https://modrinth.com/mod/aquamirae) | 7,870,615 | adventure, equipment, food, magic, mobs, worldgen |
| 250 | [Hardcore Revival](https://modrinth.com/mod/hardcore-revival) | 7,833,186 | adventure, game-mechanics |
| 251 | [Simply Swords](https://modrinth.com/mod/simply-swords) | 7,823,057 | equipment, magic |
| 252 | [MES - Moog's End Structures](https://modrinth.com/mod/mes-moogs-end-structures) | 7,822,303 | adventure, decoration, worldgen |
| 253 | [Touhou Little Maid](https://modrinth.com/mod/touhou-little-maid) | 7,819,128 | decoration, mobs, utility |
| 254 | [Regions Unexplored](https://modrinth.com/mod/regions-unexplored) | 7,813,716 | adventure, decoration, worldgen |
| 255 | [bad packets](https://modrinth.com/mod/badpackets) | 7,807,371 | library |
| 256 | [Iron Furnaces](https://modrinth.com/mod/iron-furnaces) | 7,740,368 | storage, technology |
| 257 | [Nature's Spirit](https://modrinth.com/mod/natures-spirit) | 7,729,566 | adventure, decoration, food, worldgen |
| 258 | [Stoneworks](https://modrinth.com/mod/stoneworks) | 7,712,492 | decoration |
| 259 | [Load My F***ing Tags](https://modrinth.com/mod/lmft) | 7,671,117 | cursed, utility |
| 260 | [UniLib](https://modrinth.com/mod/unilib) | 7,523,389 | library, utility |
| 261 | [Create: Copycats+](https://modrinth.com/mod/copycats) | 7,515,143 | decoration, utility |
| 262 | [End's Delight](https://modrinth.com/mod/ends-delight) | 7,509,837 | food, worldgen |
| 263 | [Ambient Environment](https://modrinth.com/mod/ambient-environment) | 7,444,690 | — |
| 264 | [Xaero Zoomout](https://modrinth.com/mod/xaero-zoomout) | 7,430,889 | adventure, transportation, utility |
| 265 | [Charm of Undying](https://modrinth.com/mod/charm-of-undying) | 7,408,672 | adventure, equipment, magic |
| 266 | [Macaw's Bridges](https://modrinth.com/mod/macaws-bridges) | 7,391,309 | decoration |
| 267 | [Subtle Effects](https://modrinth.com/mod/subtle-effects) | 7,391,200 | adventure, decoration, game-mechanics |
| 268 | [Cull Leaves](https://modrinth.com/mod/cull-leaves) | 7,382,813 | optimization |
| 269 | [Trading Post](https://modrinth.com/mod/trading-post) | 7,360,958 | utility |
| 270 | [Fast Paintings](https://modrinth.com/mod/fast-paintings) | 7,359,290 | optimization |
| 271 | [Iris/Oculus & GeckoLib Compat](https://modrinth.com/mod/geckoanimfix) | 7,290,986 | — |
| 272 | [Obscure API](https://modrinth.com/mod/obscure-api) | 7,254,972 | library, utility |
| 273 | [Log Begone](https://modrinth.com/mod/log-begone) | 7,245,852 | utility |
| 274 | [Vein Mining](https://modrinth.com/mod/vein-mining) | 7,226,929 | equipment, magic |
| 275 | [EMI Loot](https://modrinth.com/mod/emi-loot) | 7,179,181 | library, management, utility |
| 276 | [Dynamic Trees - Biomes O' Plenty](https://modrinth.com/mod/dynamic-trees-biomes-o-plenty) | 7,173,973 | adventure, decoration, game-mechanics, utility, worldgen |
| 277 | [Explorify](https://modrinth.com/mod/explorify) | 7,173,296 | adventure, worldgen |
| 278 | [Item Borders](https://modrinth.com/mod/item-borders) | 7,168,297 | adventure, decoration, equipment, game-mechanics, utility |
| 279 | [Dynamic Trees Plus](https://modrinth.com/mod/dynamictreesplus) | 7,155,417 | adventure, decoration, game-mechanics, utility, worldgen |
| 280 | [Chef's Delight - Farmer's Delight Villagers](https://modrinth.com/mod/chefs-delight) | 7,067,019 | decoration, food, mobs, worldgen |
| 281 | [Just Enough Effect Descriptions (JEED)](https://modrinth.com/mod/just-enough-effect-descriptions-jeed) | 7,034,980 | utility |
| 282 | [Double Doors](https://modrinth.com/mod/double-doors) | 7,008,540 | game-mechanics, utility |
| 283 | [VillagersPlus](https://modrinth.com/mod/villagersplus) | 6,998,511 | adventure, decoration, economy, mobs, utility, worldgen |
| 284 | [[Let's Do] Beachparty](https://modrinth.com/mod/lets-do-beachparty) | 6,982,657 | adventure, decoration, equipment, food, mobs, worldgen |
| 285 | [Enderman Overhaul](https://modrinth.com/mod/enderman-overhaul) | 6,974,913 | adventure, equipment, magic, mobs |
| 286 | [Ocean's Delight](https://modrinth.com/mod/oceans-delight) | 6,973,572 | adventure, decoration, food, game-mechanics, utility |
| 287 | [Embeddium (Rubidium) Extra](https://modrinth.com/mod/rubidium-extra) | 6,956,032 | cursed, optimization, utility |
| 288 | [Villages&Pillages](https://modrinth.com/mod/villages-and-pillages) | 6,932,696 | adventure, decoration, worldgen |
| 289 | [Just Zoom](https://modrinth.com/mod/just-zoom) | 6,929,498 | utility |
| 290 | [Fog Overrides](https://modrinth.com/mod/fogoverrides) | 6,893,569 | decoration, utility |
| 291 | [Dynamic Trees - Quark](https://modrinth.com/mod/dynamic-trees-quark) | 6,868,063 | adventure, decoration, game-mechanics, utility, worldgen |
| 292 | [[Let's Do] HerbalBrews](https://modrinth.com/mod/lets-do-herbalbrews) | 6,826,024 | adventure, decoration, food, worldgen |
| 293 | [Zombie Awareness](https://modrinth.com/mod/zombie-awareness) | 6,794,957 | adventure, game-mechanics, mobs |
| 294 | [Just Enough Breeding (JEBr)](https://modrinth.com/mod/justenoughbreeding) | 6,738,028 | management, mobs, utility |
| 295 | [Animal Feeding Trough](https://modrinth.com/mod/animal_feeding_trough) | 6,644,585 | adventure, decoration, mobs, utility |
| 296 | [Magnum Torch](https://modrinth.com/mod/magnum-torch) | 6,633,133 | utility |
| 297 | [Cave Dust](https://modrinth.com/mod/cave-dust) | 6,575,512 | decoration |
| 298 | [Ageing Spawners](https://modrinth.com/mod/ageing-spawners) | 6,574,087 | game-mechanics |
| 299 | [Immersive Melodies](https://modrinth.com/mod/immersive-melodies) | 6,558,962 | adventure, decoration, equipment, game-mechanics, social |
| 300 | [EMI Enchanting](https://modrinth.com/mod/emi-enchanting) | 6,514,123 | game-mechanics, library, management, utility |
| 301 | [Drip Sounds](https://modrinth.com/mod/dripsounds) | 6,500,856 | decoration |
| 302 | [Trek](https://modrinth.com/mod/trek) | 6,478,928 | adventure, worldgen |
| 303 | [Philips Ruins](https://modrinth.com/mod/philips-ruins) | 6,463,450 | adventure, cursed, decoration, game-mechanics, library, worldgen |
| 304 | [Immersive Armors](https://modrinth.com/mod/immersive-armors) | 6,459,086 | adventure, equipment, technology, utility |
| 305 | [Custom Villager Trades](https://modrinth.com/mod/custom-villager-trades) | 6,453,769 | game-mechanics, management, utility |
| 306 | [[Let's Do] API](https://modrinth.com/mod/do-api) | 6,441,115 | library, storage, utility |
| 307 | [TslatEntityStatus](https://modrinth.com/mod/tslatentitystatus) | 6,390,238 | game-mechanics, mobs, utility |
| 308 | [Botarium](https://modrinth.com/mod/botarium) | 6,387,479 | library, storage, technology, utility |
| 309 | [Structory: Towers](https://modrinth.com/mod/structory-towers) | 6,353,293 | adventure, decoration, worldgen |
| 310 | [Global Server Config](https://modrinth.com/mod/global-server-config) | 6,280,897 | utility |
| 311 | [Sparse Structures](https://modrinth.com/mod/sparsestructures) | 6,238,178 | adventure, game-mechanics, management, utility, worldgen |
| 312 | [Not Enough Recipe Book [NERB]](https://modrinth.com/mod/notenoughrecipebook) | 6,217,764 | game-mechanics, optimization, utility |
| 313 | [FPS Reducer](https://modrinth.com/mod/fps-reducer) | 6,210,695 | optimization |
| 314 | [Radical Cobblemon Trainers](https://modrinth.com/mod/rctmod) | 6,156,815 | adventure, game-mechanics, mobs |
| 315 | [Twigs](https://modrinth.com/mod/twigs) | 6,141,717 | adventure, decoration, game-mechanics, storage, utility, worldgen |
| 316 | [Illager Invasion](https://modrinth.com/mod/illager-invasion) | 6,135,902 | adventure, mobs, worldgen |
| 317 | [Cobbreeding](https://modrinth.com/mod/cobbreeding) | 6,113,282 | game-mechanics |
| 318 | [Macaw's Doors](https://modrinth.com/mod/macaws-doors) | 6,095,935 | decoration |
| 319 | [Dynamic Lights](https://modrinth.com/mod/dynamic-lights) | 6,068,886 | adventure, equipment, game-mechanics, utility |
| 320 | [Emotecraft](https://modrinth.com/mod/emotecraft) | 6,048,965 | adventure, decoration, game-mechanics, social |
| 321 | [[Let's Do] Farm & Charm](https://modrinth.com/mod/lets-do-farm-charm) | 6,025,518 | decoration, food, transportation, worldgen |
| 322 | [Snow! Real Magic! ⛄](https://modrinth.com/mod/snow-real-magic) | 6,019,636 | decoration, game-mechanics, worldgen |
| 323 | [Reactive Music](https://modrinth.com/mod/reactive-music) | 6,018,192 | adventure, decoration, utility |
| 324 | [[Let's Do] Meadow](https://modrinth.com/mod/lets-do-meadow) | 6,003,416 | decoration, equipment, food, game-mechanics, mobs, utility, worldgen |
| 325 | [The Aether](https://modrinth.com/mod/aether) | 5,992,321 | adventure, decoration, equipment, food, game-mechanics, mobs, transportation, utility, worldgen |
| 326 | [Spawn Animations](https://modrinth.com/mod/spawn-animations) | 5,990,909 | adventure, game-mechanics, mobs |
| 327 | [Kambrik](https://modrinth.com/mod/kambrik) | 5,956,268 | utility |
| 328 | [Caelus API](https://modrinth.com/mod/caelus) | 5,937,289 | game-mechanics, library, transportation |
| 329 | [Nyf's Spiders](https://modrinth.com/mod/nyfs-spiders) | 5,934,255 | adventure, cursed, mobs |
| 330 | [Structory](https://modrinth.com/mod/structory) | 5,923,768 | adventure, decoration, worldgen |
| 331 | [Ksyxis](https://modrinth.com/mod/ksyxis) | 5,902,080 | management, optimization, utility, worldgen |
| 332 | [Oh The Trees You'll Grow](https://modrinth.com/mod/oh-the-trees-youll-grow) | 5,899,539 | library, worldgen |
| 333 | [Bountiful](https://modrinth.com/mod/bountiful) | 5,897,310 | adventure, economy, game-mechanics, utility, worldgen |
| 334 | [Macaw's Roofs](https://modrinth.com/mod/macaws-roofs) | 5,894,714 | decoration |
| 335 | [AdoraBuild: Structures](https://modrinth.com/mod/adorabuild-structures) | 5,892,956 | adventure, decoration, worldgen |
| 336 | [Cluttered](https://modrinth.com/mod/cluttered) | 5,881,727 | decoration |
| 337 | [InventoryHUD+](https://modrinth.com/mod/inventoryhudplus) | 5,879,914 | adventure, management, storage, utility |
| 338 | [Mowzie's Mobs](https://modrinth.com/mod/mowzies-mobs) | 5,826,119 | adventure, magic, mobs |
| 339 | [Blueprint](https://modrinth.com/mod/blueprint) | 5,808,411 | library |
| 340 | [Neruina - Ticking Entity Fixer](https://modrinth.com/mod/neruina) | 5,804,552 | utility |
| 341 | [Cobblemon Capture XP](https://modrinth.com/mod/cobblemon-capture-xp) | 5,780,153 | game-mechanics |
| 342 | [LAN World Plug-n-Play (mcwifipnp)](https://modrinth.com/mod/mcwifipnp) | 5,773,415 | — |
| 343 | [MVS - Moog's Voyager Structures](https://modrinth.com/mod/moogs-voyager-structures) | 5,770,594 | adventure, decoration, worldgen |
| 344 | [Macaw's Stairs](https://modrinth.com/mod/macaws-stairs) | 5,755,531 | decoration |
| 345 | [Cobblemon Fight or Flight Reborn](https://modrinth.com/mod/cobblemon-fight-or-flight-reborn) | 5,748,485 | game-mechanics, mobs |
| 346 | [Better Archeology](https://modrinth.com/mod/better-archeology) | 5,715,729 | adventure, decoration, equipment, game-mechanics, worldgen |
| 347 | [Roughly Enough Professions (REP)](https://modrinth.com/mod/roughly-enough-professions-rep) | 5,713,833 | — |
| 348 | [[Let's Do] Candlelight - Farm&Charm compat](https://modrinth.com/mod/lets-do-candlelight-farmcharm-compat) | 5,645,738 | decoration, food |
| 349 | [Create Deco](https://modrinth.com/mod/create-deco) | 5,643,093 | decoration, technology, utility |
| 350 | [Create Slice & Dice](https://modrinth.com/mod/slice-and-dice) | 5,619,238 | food, technology |
| 351 | [Async Locator](https://modrinth.com/mod/async-locator) | 5,613,479 | library, optimization, utility |
| 352 | [Just Enough Resources (JER)](https://modrinth.com/mod/just-enough-resources-jer) | 5,567,568 | utility |
| 353 | [More Mob Variants](https://modrinth.com/mod/more-mob-variants) | 5,565,280 | decoration, mobs, social |
| 354 | [Bartering Station](https://modrinth.com/mod/bartering-station) | 5,533,517 | game-mechanics, mobs, utility |
| 355 | [Smooth Swapping](https://modrinth.com/mod/smooth-swapping) | 5,531,913 | decoration |
| 356 | [YDM's Weapon Master](https://modrinth.com/mod/weaponmaster) | 5,529,751 | decoration |
| 357 | [Platform](https://modrinth.com/mod/platform) | 5,471,377 | library, utility |
| 358 | [Sit](https://modrinth.com/mod/bl4cks-sit) | 5,466,991 | adventure, game-mechanics |
| 359 | [KleeSlabs](https://modrinth.com/mod/kleeslabs) | 5,464,114 | game-mechanics, utility |
| 360 | [EMI Ores](https://modrinth.com/mod/emi-ores) | 5,451,444 | utility, worldgen |
| 361 | [Jump Over Fences](https://modrinth.com/mod/jump-over-fences) | 5,451,035 | utility |
| 362 | [You Shall Not Spawn!](https://modrinth.com/mod/you-shall-not-spawn) | 5,447,809 | management, mobs, optimization, utility |
| 363 | [Galosphere](https://modrinth.com/mod/galosphere) | 5,446,773 | adventure, decoration, equipment, game-mechanics, mobs, worldgen |
| 364 | [Open Loader](https://modrinth.com/mod/open-loader) | 5,427,869 | library, utility |
| 365 | [Serene Seasons](https://modrinth.com/mod/serene-seasons) | 5,416,940 | food, game-mechanics |
| 366 | [Formations (Structure Library)](https://modrinth.com/mod/formations) | 5,399,824 | adventure, library, worldgen |
| 367 | [Rechiseled](https://modrinth.com/mod/rechiseled) | 5,393,421 | decoration |
| 368 | [Bygone Nether](https://modrinth.com/mod/bygone-nether) | 5,389,020 | adventure, worldgen |
| 369 | [EMI Trades](https://modrinth.com/mod/emitrades) | 5,361,042 | economy, utility |
| 370 | [Better Compatibility Checker](https://modrinth.com/mod/better-compatibility-checker) | 5,357,065 | game-mechanics, management, utility |
| 371 | [Tool Stats](https://modrinth.com/mod/tool-stats) | 5,349,827 | adventure, equipment, utility |
| 372 | [Nether Chested](https://modrinth.com/mod/nether-chested) | 5,346,242 | management, storage, utility |
| 373 | [Difficulty Lock](https://modrinth.com/mod/difficulty-lock) | 5,330,430 | management, utility |
| 374 | [CobbleDollars [Cobblemon Addon]](https://modrinth.com/mod/cobbledollars) | 5,306,240 | adventure, economy, game-mechanics, utility |
| 375 | [Tree Harvester](https://modrinth.com/mod/tree-harvester) | 5,303,352 | game-mechanics, utility |
| 376 | [Macaw's Furniture](https://modrinth.com/mod/macaws-furniture) | 5,281,927 | decoration, storage |
| 377 | [Every Compat (Wood Good)](https://modrinth.com/mod/every-compat) | 5,249,771 | library, utility |
| 378 | [Immersive UI](https://modrinth.com/mod/immersive-ui) | 5,249,404 | decoration, utility |
| 379 | [[Let's Do] Bakery - Farm&Charm Compat](https://modrinth.com/mod/lets-do-bakery-farmcharm-compat) | 5,243,834 | decoration, food, worldgen |
| 380 | [Ice and Fire](https://modrinth.com/mod/ice-and-fire-dragons) | 5,236,436 | adventure, equipment, magic, mobs, worldgen |
| 381 | [Enchanting Infuser](https://modrinth.com/mod/enchanting-infuser) | 5,232,848 | equipment, magic, utility |
| 382 | [Combat Roll](https://modrinth.com/mod/combat-roll) | 5,231,847 | adventure, game-mechanics, library |
| 383 | [Simple Rich Discord Presence](https://modrinth.com/mod/srdp) | 5,217,056 | social, utility |
| 384 | [Vanilla Backport](https://modrinth.com/mod/vanillabackport) | 5,201,318 | adventure, equipment, mobs, worldgen |
| 385 | [[Let's Do] NetherVinery](https://modrinth.com/mod/lets-do-nethervinery) | 5,161,470 | adventure, decoration, food, storage |
| 386 | [Grass Overhaul](https://modrinth.com/mod/grass-overhaul) | 5,149,367 | decoration, utility, worldgen |
| 387 | [Create Big Cannons](https://modrinth.com/mod/create-big-cannons) | 5,136,250 | adventure, equipment, game-mechanics, technology, utility |
| 388 | [Radium](https://modrinth.com/mod/radium) | 5,135,688 | cursed, optimization |
| 389 | [Full Brightness Toggle](https://modrinth.com/mod/full-brightness-toggle) | 5,110,164 | utility |
| 390 | [End Remastered](https://modrinth.com/mod/endrem) | 5,094,891 | adventure |
| 391 | [Spawn](https://modrinth.com/mod/spawn-mod) | 5,083,895 | adventure, decoration, food, mobs, worldgen |
| 392 | [Fast Scrolling](https://modrinth.com/mod/fast-scrolling) | 5,073,192 | utility |
| 393 | [Simple Hats](https://modrinth.com/mod/simple-hats) | 5,046,063 | decoration, equipment, social |
| 394 | [Advanced Loot Info](https://modrinth.com/mod/advanced-loot-info) | 5,018,385 | utility |
| 395 | [AzureLib Armor](https://modrinth.com/mod/azurelib-armor) | 4,980,797 | library, utility |
| 396 | [Formations Nether](https://modrinth.com/mod/formations-nether) | 4,976,970 | adventure, worldgen |
| 397 | [Hearths](https://modrinth.com/mod/hearths) | 4,971,579 | adventure, worldgen |
| 398 | [The Lost Castle](https://modrinth.com/mod/the-lost-castle) | 4,969,856 | adventure, worldgen |
| 399 | [Hearth & Home](https://modrinth.com/mod/hearth-and-home) | 4,969,570 | adventure, decoration, game-mechanics |
| 400 | [Critters and Companions](https://modrinth.com/mod/critters-and-companions) | 4,967,014 | mobs |
| 401 | [Structure Layout Optimizer](https://modrinth.com/mod/structure-layout-optimizer) | 4,951,339 | optimization, worldgen |
| 402 | [[Let's Do] Brewery - Farm&Charm Compat](https://modrinth.com/mod/lets-do-brewery-farmcharm-compat) | 4,938,286 | — |
| 403 | [Nullscape](https://modrinth.com/mod/nullscape) | 4,936,452 | worldgen |
| 404 | [WI Zoom](https://modrinth.com/mod/wi-zoom) | 4,907,970 | utility |
| 405 | [InvMoveCompats](https://modrinth.com/mod/invmovecompats) | 4,906,617 | — |
| 406 | [Sodium Options Mod Compat](https://modrinth.com/mod/sodium-options-mod-compat) | 4,905,224 | decoration, optimization, utility |
| 407 | [Valkyrien Skies](https://modrinth.com/mod/valkyrien-skies) | 4,902,182 | game-mechanics, library |
| 408 | [YUNG's Menu Tweaks](https://modrinth.com/mod/yungs-menu-tweaks) | 4,888,802 | decoration, game-mechanics, utility |
| 409 | [True Ending - Ender Dragon Overhaul](https://modrinth.com/mod/true-ending) | 4,885,901 | magic, mobs |
| 410 | [Bridging Mod](https://modrinth.com/mod/bridging-mod) | 4,882,714 | adventure, game-mechanics, utility |
| 411 | [Legendary Monsters](https://modrinth.com/mod/legendary-monsters) | 4,854,482 | adventure, equipment, mobs |
| 412 | [Cobweb](https://modrinth.com/mod/cobweb) | 4,844,018 | library |
| 413 | [Library Ferret](https://modrinth.com/mod/library-ferret) | 4,803,604 | economy, equipment, library, utility |
| 414 | [Explorations](https://modrinth.com/mod/explorations) | 4,775,615 | adventure, decoration, worldgen |
| 415 | [Decorative Blocks](https://modrinth.com/mod/decorative-blocks) | 4,764,105 | decoration |
| 416 | [SmartBrainLib](https://modrinth.com/mod/smartbrainlib) | 4,760,704 | library, mobs, utility |
| 417 | [Bulk Villager Trading](https://modrinth.com/mod/bulk-villager-trading) | 4,757,369 | economy, game-mechanics, utility |
| 418 | [Camera Overhaul](https://modrinth.com/mod/cameraoverhaul) | 4,750,736 | decoration, utility |
| 419 | [Goety - The Dark Arts](https://modrinth.com/mod/goety) | 4,734,498 | adventure, equipment, magic, mobs, utility, worldgen |
| 420 | [Ribbits](https://modrinth.com/mod/ribbits) | 4,734,498 | adventure, decoration, economy, magic, mobs, worldgen |
| 421 | [Visuality: Reforged](https://modrinth.com/mod/visuality-forge) | 4,705,854 | decoration |
| 422 | [Applied Energistics 2](https://modrinth.com/mod/ae2) | 4,691,434 | storage, technology, utility |
| 423 | [Configured Defaults](https://modrinth.com/mod/configured-defaults) | 4,687,269 | utility |
| 424 | [Trade Cycling](https://modrinth.com/mod/trade-cycling) | 4,683,790 | utility |
| 425 | [Ad Astra](https://modrinth.com/mod/ad-astra) | 4,660,229 | adventure, equipment, food, mobs, technology, transportation, worldgen |
| 426 | [Small Ships](https://modrinth.com/mod/small-ships) | 4,628,895 | adventure, game-mechanics, storage, transportation |
| 427 | [Formations Overworld](https://modrinth.com/mod/formations-overworld) | 4,627,976 | adventure, worldgen |
| 428 | [Sawmill](https://modrinth.com/mod/universal-sawmill) | 4,587,288 | — |
| 429 | [Boat Break Fix](https://modrinth.com/mod/boat-break-fix) | 4,553,814 | game-mechanics |
| 430 | [Species](https://modrinth.com/mod/species) | 4,505,263 | adventure, game-mechanics, mobs, utility, worldgen |
| 431 | [Additional Structures](https://modrinth.com/mod/additional-structures) | 4,471,763 | adventure, worldgen |
| 432 | [Saturn](https://modrinth.com/mod/saturn) | 4,471,235 | optimization |
| 433 | [Night Config Fixes](https://modrinth.com/mod/night-config-fixes) | 4,451,593 | library, management, utility |
| 434 | [Snowy Spirit](https://modrinth.com/mod/snowy-spirit) | 4,399,630 | transportation |
| 435 | [Ender's Delight](https://modrinth.com/mod/enders-delight) | 4,374,337 | decoration, equipment, food |
| 436 | [Falling Leaves (NeoForge/Forge)](https://modrinth.com/mod/fallingleavesforge) | 4,363,880 | decoration |
| 437 | [[TACZ]Enchanted Arsenal](https://modrinth.com/mod/enchanted-arsenal) | 4,359,431 | adventure, equipment, magic |
| 438 | [TaCZ addon](https://modrinth.com/mod/taczaddon) | 4,349,275 | utility |
| 439 | [Automobility](https://modrinth.com/mod/automobility) | 4,328,106 | equipment, technology, transportation |
| 440 | [AsyncParticles](https://modrinth.com/mod/asyncparticles) | 4,321,294 | game-mechanics, optimization |
| 441 | [Ecologics](https://modrinth.com/mod/ecologics) | 4,305,736 | food, worldgen |
| 442 | [From The Fog](https://modrinth.com/mod/from-the-fog) | 4,288,453 | adventure, cursed, worldgen |
| 443 | [Dramatic Doors](https://modrinth.com/mod/dramatic-doors) | 4,284,839 | decoration |
| 444 | [Macaw's Paintings](https://modrinth.com/mod/macaws-paintings) | 4,277,233 | decoration |
| 445 | [Macaw's Paths and Pavings](https://modrinth.com/mod/macaws-paths-and-pavings) | 4,265,448 | decoration |
| 446 | [Rubidium](https://modrinth.com/mod/rubidium) | 4,232,947 | optimization |
| 447 | [Better Clouds](https://modrinth.com/mod/better-clouds) | 4,218,415 | decoration, optimization, utility |
| 448 | [Particle Effects](https://modrinth.com/mod/particle-effects) | 4,216,817 | decoration, game-mechanics, magic, mobs, utility |
| 449 | [EEEAB 's Mobs](https://modrinth.com/mod/eeeab-s-mobs) | 4,211,660 | adventure, equipment, mobs |
| 450 | [HT's TreeChop](https://modrinth.com/mod/treechop) | 4,201,141 | game-mechanics, utility |
| 451 | [Structure Gel API](https://modrinth.com/mod/structure-gel-api) | 4,186,584 | utility, worldgen |
| 452 | [Create: New Age](https://modrinth.com/mod/create-new-age) | 4,180,230 | technology |
| 453 | [MaFgLib](https://modrinth.com/mod/mafglib) | 4,163,839 | library |
| 454 | [Construction Wand](https://modrinth.com/mod/construction-wand) | 4,158,472 | equipment |
| 455 | [Kaleidoscope Cookery](https://modrinth.com/mod/kaleidoscope-cookery) | 4,150,858 | decoration, food |
| 456 | [Easy Shulker Boxes](https://modrinth.com/mod/easy-shulker-boxes) | 4,150,334 | storage, utility |
| 457 | [Repurposed Structures - Neoforge/Forge](https://modrinth.com/mod/repurposed-structures-forge) | 4,148,099 | worldgen |
| 458 | [Blossom Blade](https://modrinth.com/mod/blossom-blade) | 4,132,492 | adventure, decoration, equipment, worldgen |
| 459 | [Create: Connected](https://modrinth.com/mod/create-connected) | 4,115,848 | decoration, technology, utility |
| 460 | [Meet Your Fight](https://modrinth.com/mod/meet-your-fight) | 4,114,469 | adventure, equipment, utility |
| 461 | [Icarus](https://modrinth.com/mod/icarus) | 4,110,149 | adventure, equipment, game-mechanics, magic, transportation, utility |
| 462 | [Friends&Foes (Forge/NeoForge)](https://modrinth.com/mod/friends-and-foes-forge) | 4,095,860 | adventure, mobs, worldgen |
| 463 | [SimpleTMs: TMs and TRs for Cobblemon](https://modrinth.com/mod/simpletms-tms-and-trs-for-cobblemon) | 4,095,010 | adventure, game-mechanics, utility |
| 464 | [Jade Addons (Neo/Forge)](https://modrinth.com/mod/jade-addons-forge) | 4,087,103 | utility |
| 465 | [TrashSlot](https://modrinth.com/mod/trashslot) | 4,083,311 | game-mechanics, management, utility |
| 466 | [Luna](https://modrinth.com/mod/luna) | 4,065,156 | library, management |
| 467 | [In-Game Account Switcher](https://modrinth.com/mod/in-game-account-switcher) | 4,056,939 | management, social, utility |
| 468 | [Crosshair Bobbing](https://modrinth.com/mod/xbob) | 4,054,829 | decoration, utility |
| 469 | [More Delight (for Farmer's Delight)](https://modrinth.com/mod/more-delight) | 4,052,074 | adventure, decoration, food, storage, utility |
| 470 | [Elytra Trims](https://modrinth.com/mod/elytra-trims) | 4,022,451 | decoration, equipment |
| 471 | [Mindful Darkness](https://modrinth.com/mod/mindful-darkness) | 4,017,998 | game-mechanics, utility |
| 472 | [Draggable Lists](https://modrinth.com/mod/draggable-lists) | 4,002,096 | decoration, utility |
| 473 | [Create: Central Kitchen](https://modrinth.com/mod/create-central-kitchen) | 3,996,594 | food, technology |
| 474 | [Tiny Item Animations](https://modrinth.com/mod/tiny-item-animations) | 3,977,908 | decoration, management, utility |
| 475 | [CC: Tweaked](https://modrinth.com/mod/cc-tweaked) | 3,977,099 | technology |
| 476 | [Modpack Update Checker](https://modrinth.com/mod/modpack-update-checker) | 3,922,251 | utility |
| 477 | [Forgiving Void](https://modrinth.com/mod/forgiving-void) | 3,914,125 | adventure, game-mechanics, magic |
| 478 | [Iron's Spells 'n Spellbooks](https://modrinth.com/mod/irons-spells-n-spellbooks) | 3,899,754 | adventure, equipment, game-mechanics, magic, mobs |
| 479 | [Canary](https://modrinth.com/mod/canary) | 3,897,584 | optimization |
| 480 | [Better Recipe Book](https://modrinth.com/mod/brb) | 3,889,653 | utility |
| 481 | [Create: Enchantment Industry](https://modrinth.com/mod/create-enchantment-industry) | 3,888,212 | storage, technology |
| 482 | [Immersive Paintings](https://modrinth.com/mod/immersive-paintings) | 3,886,820 | decoration, game-mechanics, social, utility |
| 483 | [Pixelmon](https://modrinth.com/mod/pixelmon) | 3,881,736 | adventure, decoration, worldgen |
| 484 | [Man of Many Planes](https://modrinth.com/mod/man-of-many-planes) | 3,877,841 | adventure, storage, technology, transportation, utility |
| 485 | [SecurityCraft](https://modrinth.com/mod/security-craft) | 3,871,575 | adventure, decoration, equipment, technology, utility |
| 486 | [Void Totem](https://modrinth.com/mod/voidtotem) | 3,865,845 | equipment, transportation, utility |
| 487 | [Client Sort](https://modrinth.com/mod/clientsort) | 3,863,340 | storage, utility |
| 488 | [Biome Replacer](https://modrinth.com/mod/biome-replacer) | 3,854,570 | utility, worldgen |
| 489 | [Luki's Grand Capitals](https://modrinth.com/mod/lukis-grand-capitals) | 3,842,904 | adventure, decoration, worldgen |
| 490 | [Ash API](https://modrinth.com/mod/ash-api) | 3,802,871 | library |
| 491 | [Longer Chat History](https://modrinth.com/mod/longer-chat-history) | 3,787,733 | social, utility |
| 492 | [Iris & Oculus Flywheel Compat](https://modrinth.com/mod/iris-flw-compat) | 3,780,329 | optimization |
| 493 | [Create: Diesel Generators](https://modrinth.com/mod/create-diesel-generators) | 3,768,031 | technology |
| 494 | [[Let's Do] WilderNature](https://modrinth.com/mod/lets-do-wildernature) | 3,750,756 | food, mobs |
| 495 | [Cobblemon Poképedia: Cobblepedia](https://modrinth.com/mod/cobblepedia) | 3,747,678 | utility |
| 496 | [Mutant Monsters](https://modrinth.com/mod/mutant-monsters) | 3,733,443 | equipment, magic, mobs |
| 497 | [Better Tridents](https://modrinth.com/mod/better-tridents) | 3,727,543 | equipment, utility |
| 498 | [Transparent](https://modrinth.com/mod/transparent) | 3,726,618 | decoration, utility |
| 499 | [My Nether's Delight](https://modrinth.com/mod/my-nethers-delight) | 3,705,109 | adventure, decoration, food, worldgen |
| 500 | [Extreme sound muffler](https://modrinth.com/mod/extreme_sound_muffler) | 3,703,559 | utility |
| 501 | [EMI professions (EMIP)](https://modrinth.com/mod/emi-professions-(emip)) | 3,699,665 | utility |
| 502 | [WTHIT](https://modrinth.com/mod/wthit) | 3,689,567 | library, utility |
| 503 | [Redirected](https://modrinth.com/mod/redirected) | 3,685,381 | optimization |
| 504 | [Load My Resources](https://modrinth.com/mod/load-my-resources) | 3,674,617 | utility |
| 505 | [GD656Killicon](https://modrinth.com/mod/gd656killicon) | 3,671,555 | decoration, equipment, technology |
| 506 | [The Man From The Fog](https://modrinth.com/mod/the-man-from-the-fog) | 3,666,119 | adventure, cursed, mobs |
| 507 | [Just Enough Professions (JEP)](https://modrinth.com/mod/just-enough-professions-jep) | 3,658,742 | — |
| 508 | [Dismount Entity](https://modrinth.com/mod/dismount-entity) | 3,654,014 | game-mechanics, utility |
| 509 | [[Let's Do] BloomingNature](https://modrinth.com/mod/lets-do-bloomingnature) | 3,622,987 | adventure, decoration, mobs, worldgen |
| 510 | [Harvest with ease](https://modrinth.com/mod/harvest-with-ease) | 3,612,625 | food, game-mechanics, utility |
| 511 | [GPUBooster](https://modrinth.com/mod/gputape) | 3,610,843 | library, optimization, utility |
| 512 | [Create: Bells & Whistles](https://modrinth.com/mod/bellsandwhistles) | 3,567,871 | decoration, equipment, technology, transportation, utility |
| 513 | [Hybrid Aquatic](https://modrinth.com/mod/hybrid-aquatic) | 3,553,016 | adventure, equipment, food, mobs, worldgen |
| 514 | [Supplementaries Squared](https://modrinth.com/mod/supplementaries-squared) | 3,542,800 | decoration, utility |
| 515 | [Prefab](https://modrinth.com/mod/prefab) | 3,542,180 | decoration, worldgen |
| 516 | [Krypton Reno](https://modrinth.com/mod/krypton-fnp) | 3,531,861 | management, optimization, utility |
| 517 | [Net Music](https://modrinth.com/mod/net-music) | 3,531,547 | decoration, utility |
| 518 | [Icterine](https://modrinth.com/mod/icterine) | 3,504,501 | optimization, utility |
| 519 | [Create: Interiors](https://modrinth.com/mod/interiors) | 3,495,820 | decoration, technology, transportation, utility |
| 520 | [Tidal Towns](https://modrinth.com/mod/tidal-towns) | 3,427,377 | adventure, worldgen |
| 521 | [Smooth Boot (Reloaded)](https://modrinth.com/mod/smooth-boot-reloaded) | 3,414,166 | optimization |
| 522 | [Corpse](https://modrinth.com/mod/corpse) | 3,414,142 | adventure, storage, utility |
| 523 | [Better Nether Map](https://modrinth.com/mod/better-nether-map) | 3,411,988 | adventure, equipment, utility |
| 524 | [Colorful Hearts](https://modrinth.com/mod/colorful-hearts) | 3,377,461 | decoration |
| 525 | [Superflat World No Slimes](https://modrinth.com/mod/superflat-world-no-slimes) | 3,368,750 | management, mobs, utility |
| 526 | [The Endergetic Expansion](https://modrinth.com/mod/endergetic) | 3,368,544 | adventure, decoration, equipment, food, game-mechanics, mobs, transportation, worldgen |
| 527 | [Diagonal Windows](https://modrinth.com/mod/diagonal-windows) | 3,361,333 | cursed, decoration |
| 528 | [Plasmo Voice](https://modrinth.com/mod/plasmo-voice) | 3,357,215 | adventure, social, utility |
| 529 | [When Dungeons Arise: Seven Seas](https://modrinth.com/mod/when-dungeons-arise-seven-seas) | 3,339,625 | adventure, mobs, worldgen |
| 530 | [EMI Enchants](https://modrinth.com/mod/emienchants) | 3,338,407 | magic, utility |
| 531 | [Macaw's Holidays](https://modrinth.com/mod/macaws-holidays) | 3,332,608 | decoration |
| 532 | [CERBON's API](https://modrinth.com/mod/cerbons-api) | 3,323,483 | library |
| 533 | [Universal Bone Meal](https://modrinth.com/mod/universal-bone-meal) | 3,315,299 | food, utility |
| 534 | [Medieval Buildings](https://modrinth.com/mod/medieval-buildings) | 3,306,486 | adventure, decoration, equipment, social, worldgen |
| 535 | [Lighty](https://modrinth.com/mod/lighty) | 3,286,170 | utility |
| 536 | [Better Than Mending](https://modrinth.com/mod/better-than-mending) | 3,284,515 | equipment, utility |
| 537 | [Rotten Creatures](https://modrinth.com/mod/rottencreatures) | 3,279,501 | mobs |
| 538 | [Macaw's Trapdoors](https://modrinth.com/mod/macaws-trapdoors) | 3,268,398 | decoration |
| 539 | [Dungeons and Taverns Ancient City Overhaul](https://modrinth.com/mod/dungeons-and-taverns-ancient-city-overhaul) | 3,263,553 | adventure, worldgen |
| 540 | [Create Jetpack](https://modrinth.com/mod/create-jetpack) | 3,249,285 | equipment, technology |
| 541 | [Create: Structures](https://modrinth.com/mod/create-structures) | 3,245,537 | adventure, technology, worldgen |
| 542 | [Marium's Soulslike Weaponry](https://modrinth.com/mod/mariums-soulslike-weaponry) | 3,219,516 | adventure, equipment, mobs, worldgen |
| 543 | [Emoji Type](https://modrinth.com/mod/emoji-type) | 3,215,164 | social, utility |
| 544 | [MCA Reborn](https://modrinth.com/mod/minecraft-comes-alive-reborn) | 3,201,271 | adventure, game-mechanics, mobs |
| 545 | [Brewin' And Chewin'](https://modrinth.com/mod/brewin-and-chewin) | 3,197,010 | food |
| 546 | [Remove Stardust Labs Intro Message](https://modrinth.com/mod/remove-terralith-intro-message) | 3,169,553 | utility |
| 547 | [Ok Zoomer - It's Zoom!](https://modrinth.com/mod/ok-zoomer) | 3,168,451 | game-mechanics, utility |
| 548 | [Item Obliterator](https://modrinth.com/mod/item-obliterator) | 3,161,027 | game-mechanics, management, optimization, utility |
| 549 | [Sophisticated Storage](https://modrinth.com/mod/sophisticated-storage) | 3,136,913 | storage |
| 550 | [Epic Fight](https://modrinth.com/mod/epic-fight) | 3,132,041 | adventure, equipment, game-mechanics, library, mobs |
| 551 | [Ixeris](https://modrinth.com/mod/ixeris) | 3,131,932 | optimization |
| 552 | [CraftTweaker](https://modrinth.com/mod/crafttweaker) | 3,128,497 | library, utility |
| 553 | [Pufferfish's Skills](https://modrinth.com/mod/skills) | 3,123,138 | adventure, game-mechanics, management, utility |
| 554 | [Ube's Delight](https://modrinth.com/mod/ubes-delight) | 3,122,685 | decoration, food, worldgen |
| 555 | [Botania](https://modrinth.com/mod/botania) | 3,121,190 | decoration, magic, technology |
| 556 | [TaCZ: TES Compat](https://modrinth.com/mod/tacz-tes-compat) | 3,115,655 | game-mechanics, utility |
| 557 | [Screenshot Viewer](https://modrinth.com/mod/screenshot-viewer) | 3,110,152 | game-mechanics, utility |
| 558 | [Iron Chests: Restocked](https://modrinth.com/mod/ironchests) | 3,099,950 | storage, utility |
| 559 | [Better Days](https://modrinth.com/mod/betterdays) | 3,094,387 | game-mechanics, utility |
| 560 | [Huge Structure Blocks](https://modrinth.com/mod/huge-structure-blocks) | 3,091,904 | optimization, utility |
| 561 | [EpheroLib](https://modrinth.com/mod/epherolib) | 3,090,641 | library |
| 562 | [Hide Key Binding](https://modrinth.com/mod/hide-key-binding) | 3,087,517 | utility |
| 563 | [[Let's Do Addon] Structures](https://modrinth.com/mod/lets-do-addon-structures) | 3,087,109 | decoration, food, worldgen |
| 564 | [Female Gender Mod](https://modrinth.com/mod/female-gender) | 3,069,749 | decoration |
| 565 | [Delightful](https://modrinth.com/mod/delightful) | 3,069,593 | equipment, food, storage, utility, worldgen |
| 566 | [Jaden's Nether Expansion](https://modrinth.com/mod/jadens-nether-expansion) | 3,067,427 | adventure, decoration, equipment, food, game-mechanics, mobs, utility, worldgen |
| 567 | [Crate Delight](https://modrinth.com/mod/crate-delight) | 3,053,989 | adventure, decoration, food |
| 568 | [TexTrue's Embeddium Options](https://modrinth.com/mod/textrues-embeddium-options) | 3,038,802 | utility |
| 569 | [Building Wands](https://modrinth.com/mod/building-wands) | 3,030,015 | equipment, utility |
| 570 | [The Bumblezone - NeoForge/Forge](https://modrinth.com/mod/the-bumblezone) | 3,025,074 | mobs, worldgen |
| 571 | [Server Browser](https://modrinth.com/mod/server-browser) | 3,016,492 | management, social, utility |
| 572 | [Fishing Real](https://modrinth.com/mod/fishing-real) | 3,004,417 | utility |
| 573 | [Dynamic Trim](https://modrinth.com/mod/dynamic-trim) | 2,999,029 | decoration, equipment |
| 574 | [Ars Nouveau](https://modrinth.com/mod/ars-nouveau) | 2,980,440 | — |
| 575 | [Tiny Skeletons](https://modrinth.com/mod/tiny-skeletons) | 2,973,788 | adventure |
| 576 | [No Telemetry](https://modrinth.com/mod/no-telemetry) | 2,973,482 | — |
| 577 | [Stony Cliffs Are Cool](https://modrinth.com/mod/stony-cliffs-are-cool) | 2,964,585 | worldgen |
| 578 | [Tinkers' Construct](https://modrinth.com/mod/tinkers-construct) | 2,960,295 | equipment, magic, technology |
| 579 | [Toni's Immersive Lanterns](https://modrinth.com/mod/immersive-lanterns) | 2,959,157 | decoration, equipment, game-mechanics, utility |
| 580 | [Chunks fade in](https://modrinth.com/mod/chunks-fade-in) | 2,952,409 | cursed, decoration, utility, worldgen |
| 581 | [Towers of the Wild Modded](https://modrinth.com/mod/totw-modded) | 2,946,284 | adventure, decoration, equipment, game-mechanics, transportation, worldgen |
| 582 | [Creeper Firework](https://modrinth.com/mod/creeper-firework) | 2,944,033 | game-mechanics, mobs |
| 583 | [TDmon](https://modrinth.com/mod/tdmon) | 2,933,047 | adventure, mobs |
| 584 | [Joy of Painting](https://modrinth.com/mod/joy-of-painting) | 2,919,871 | decoration, game-mechanics, social |
| 585 | [Diagonal Walls](https://modrinth.com/mod/diagonal-walls) | 2,868,964 | cursed, decoration |
| 586 | [Autochef's Delight](https://modrinth.com/mod/autochefs-delight) | 2,861,555 | food, optimization, utility |
| 587 | [PolyLib](https://modrinth.com/mod/polylib) | 2,848,676 | library |
| 588 | [YUNG's Cave Biomes](https://modrinth.com/mod/yungs-cave-biomes) | 2,847,025 | adventure, decoration, food, mobs, worldgen |
| 589 | [Lionfish-API](https://modrinth.com/mod/lionfish-api) | 2,843,997 | library, utility |
| 590 | [Cull Less Leaves Reforged](https://modrinth.com/mod/cull-less-leaves-reforged) | 2,836,535 | optimization |
| 591 | [Screenshot to Clipboard](https://modrinth.com/mod/screenshot-to-clipboard) | 2,835,672 | utility |
| 592 | [Vanilla Refresh](https://modrinth.com/mod/vanilla-refresh) | 2,832,799 | game-mechanics, social, utility |
| 593 | [Illage and Spillage: Respillaged](https://modrinth.com/mod/illage-and-spillage-respillaged) | 2,832,055 | mobs |
| 594 | [Nether Depths Upgrade](https://modrinth.com/mod/nether-depths-upgrade) | 2,831,076 | adventure, decoration, food, mobs, worldgen |
| 595 | [Create: Design n' Decor](https://modrinth.com/mod/create-design-n-decor) | 2,829,261 | decoration, economy, storage, utility |
| 596 | [Crabber's Delight](https://modrinth.com/mod/crabbers-delight) | 2,828,653 | food |
| 597 | [Mantle](https://modrinth.com/mod/mantle) | 2,822,143 | library, utility |
| 598 | [Macaw's Lights and Lamps](https://modrinth.com/mod/macaws-lights-and-lamps) | 2,812,049 | decoration |
| 599 | [ATi Structures - Vanilla Edition](https://modrinth.com/mod/ati-structures-vanilla-edition) | 2,807,294 | adventure, equipment, worldgen |
| 600 | [AAA Particles](https://modrinth.com/mod/aaa-particles) | 2,806,627 | decoration, library |
| 601 | [Miner's Delight](https://modrinth.com/mod/miners-delight) | 2,802,945 | decoration, food |
| 602 | [Mekanism](https://modrinth.com/mod/mekanism) | 2,797,590 | equipment, storage, technology, worldgen |
| 603 | [ClearDespawn](https://modrinth.com/mod/cleardespawn) | 2,789,113 | utility |
| 604 | [Building But Better](https://modrinth.com/mod/building-but-better) | 2,780,485 | decoration, equipment |
| 605 | [Flerovium](https://modrinth.com/mod/flerovium) | 2,777,937 | optimization |
| 606 | [Cobblemon Integrations](https://modrinth.com/mod/cobblemon-integrations) | 2,776,428 | game-mechanics, mobs, utility |
| 607 | [Create Ore Excavation](https://modrinth.com/mod/create-ore-excavation) | 2,775,077 | technology, worldgen |
| 608 | [Lightman's Currency](https://modrinth.com/mod/lightmans-currency) | 2,773,656 | economy, library, utility |
| 609 | [Incubation](https://modrinth.com/mod/incubation) | 2,765,925 | decoration, food, storage, utility |
| 610 | [Panda's Falling Trees](https://modrinth.com/mod/pandas-falling-trees) | 2,752,129 | game-mechanics, utility |
| 611 | [Better Climbing](https://modrinth.com/mod/better-climbing) | 2,747,368 | game-mechanics, transportation, utility |
| 612 | [Too Fast](https://modrinth.com/mod/too-fast) | 2,744,681 | utility |
| 613 | [CraterLib](https://modrinth.com/mod/craterlib) | 2,736,614 | library, utility |
| 614 | [ToadLib](https://modrinth.com/mod/toadlib) | 2,734,318 | library, optimization, utility |
| 615 | [Create Goggles](https://modrinth.com/mod/create-goggles) | 2,711,423 | decoration, equipment, utility |
| 616 | [Storage Drawers](https://modrinth.com/mod/storagedrawers) | 2,710,576 | storage |
| 617 | [MNS - Moog's Nether Structures](https://modrinth.com/mod/mns-moogs-nether-structures) | 2,705,459 | adventure, decoration, worldgen |
| 618 | [Spyglass Improvements](https://modrinth.com/mod/spyglass-improvements) | 2,703,781 | adventure, equipment, game-mechanics, utility |
| 619 | [Yuushya Townscape](https://modrinth.com/mod/yuushya-townscape) | 2,688,281 | decoration |
| 620 | [Leave My Bars Alone](https://modrinth.com/mod/leave-my-bars-alone) | 2,677,751 | food, mobs, utility |
| 621 | [Immersive Engineering](https://modrinth.com/mod/immersiveengineering) | 2,659,447 | equipment, technology |
| 622 | [Create: Dreams & Desires](https://modrinth.com/mod/create-dreams-and-desires) | 2,654,240 | decoration, equipment, game-mechanics, technology, utility |
| 623 | [Projectile Damage Attribute](https://modrinth.com/mod/projectile-damage-attribute) | 2,635,030 | adventure, equipment, game-mechanics, library |
| 624 | [Healing Campfire](https://modrinth.com/mod/healing-campfire) | 2,627,588 | adventure, game-mechanics, utility |
| 625 | [Eldritch End](https://modrinth.com/mod/eldritch-end) | 2,618,960 | — |
| 626 | [Connected Glass](https://modrinth.com/mod/connected-glass) | 2,617,248 | decoration |
| 627 | [Better Animations Collection](https://modrinth.com/mod/better-animations-collection) | 2,603,410 | decoration, mobs |
| 628 | [MSS - Moog's Soaring Structures](https://modrinth.com/mod/mss-moogs-soaring-structures) | 2,603,048 | adventure, decoration, worldgen |
| 629 | [CodeChicken Lib](https://modrinth.com/mod/codechicken-lib) | 2,599,665 | library |
| 630 | [Distraction Free Recipes (EMI / REI / JEI)](https://modrinth.com/mod/distraction-free-recipes) | 2,597,741 | decoration, optimization, utility |
| 631 | [Carved Wood](https://modrinth.com/mod/carved-wood) | 2,592,208 | decoration, storage |
| 632 | [Origins](https://modrinth.com/mod/origins) | 2,590,019 | adventure |
| 633 | [Inventory Totem](https://modrinth.com/mod/inventory-totem) | 2,589,065 | game-mechanics, utility |
| 634 | [RSInfinityBooster](https://modrinth.com/mod/rsinfinitybooster) | 2,586,457 | storage |
| 635 | [Common Network](https://modrinth.com/mod/common-network) | 2,584,581 | library, utility |
| 636 | [Better ModList](https://modrinth.com/mod/better-modlist) | 2,572,023 | game-mechanics, management, utility |
| 637 | [More Axolotl Variants API](https://modrinth.com/mod/mavapi) | 2,571,824 | library, mobs |
| 638 | [CalcMod](https://modrinth.com/mod/calcmod) | 2,568,508 | utility |
| 639 | [Underground Worlds](https://modrinth.com/mod/underground-worlds) | 2,557,614 | adventure, decoration, equipment, mobs, storage, worldgen |
| 640 | [Particular ✨ Reforged](https://modrinth.com/mod/particular-reforged) | 2,553,170 | game-mechanics |
| 641 | [Gardens of the Dead](https://modrinth.com/mod/gardens-of-the-dead) | 2,548,784 | decoration, worldgen |
| 642 | [More Axolotl Variants Mod](https://modrinth.com/mod/mavm) | 2,544,492 | mobs |
| 643 | [AstikorCarts Redux](https://modrinth.com/mod/astikorcarts-redux) | 2,536,985 | adventure, equipment, storage, transportation, utility |
| 644 | [PlayerRevive](https://modrinth.com/mod/playerrevive) | 2,530,965 | adventure, game-mechanics |
| 645 | [Epic Fight - Sword Soaring](https://modrinth.com/mod/epic-fight-sword-soaring) | 2,527,397 | adventure, equipment |
| 646 | [Mobs of Mythology](https://modrinth.com/mod/mobs-of-mythology) | 2,506,350 | adventure, equipment, mobs, worldgen |
| 647 | [Cobblemon Spawn Notification](https://modrinth.com/mod/cobblemon-spawn-notification) | 2,494,285 | game-mechanics, mobs, utility |
| 648 | [Applied Energistics 2 Wireless Terminals](https://modrinth.com/mod/applied-energistics-2-wireless-terminals) | 2,491,757 | technology |
| 649 | [Almost Unified](https://modrinth.com/mod/almostunified) | 2,491,338 | library, utility |
| 650 | [Rechiseled: Create](https://modrinth.com/mod/rechiseled-create) | 2,471,784 | decoration, technology |
| 651 | [Abridged](https://modrinth.com/mod/abridged) | 2,463,436 | adventure, worldgen |
| 652 | [Dusty Decorations](https://modrinth.com/mod/dusty-decorations) | 2,456,162 | decoration, worldgen |
| 653 | [Relics](https://modrinth.com/mod/relics-mod) | 2,430,929 | adventure, equipment, magic |
| 654 | [Sounds Be Gone!](https://modrinth.com/mod/soundsbegone) | 2,426,052 | game-mechanics, management, utility |
| 655 | [Vivecraft](https://modrinth.com/mod/vivecraft) | 2,422,877 | game-mechanics, library, utility |
| 656 | [Customizable Player Models](https://modrinth.com/mod/custom-player-models) | 2,420,088 | decoration, social |
| 657 | [Simple Discord RPC](https://modrinth.com/mod/simple-discord-rpc) | 2,408,468 | library, utility |
| 658 | [Antique Atlas 4](https://modrinth.com/mod/antique-atlas-4) | 2,404,264 | adventure, library, social, utility |
| 659 | [Rustic Delight](https://modrinth.com/mod/rustic-delight) | 2,401,642 | adventure, decoration, food, worldgen |
| 660 | [Nether's Delight](https://modrinth.com/mod/nethers-delight) | 2,390,931 | decoration, equipment, food |
| 661 | [Dungeons and Taverns Stronghold Overhaul](https://modrinth.com/mod/dungeons-and-taverns-stronghold-overhaul) | 2,387,700 | adventure, worldgen |
| 662 | [Seamless Loading Screen ](https://modrinth.com/mod/seamless-loading-screen) | 2,386,157 | decoration |
| 663 | [Goety: Revelation](https://modrinth.com/mod/goety-revelation) | 2,385,808 | equipment, magic, mobs |
| 664 | [Bow Infinity Fix](https://modrinth.com/mod/bow-infinity-fix) | 2,377,204 | game-mechanics, utility |
| 665 | [Ender Dragon Fight Remastered](https://modrinth.com/mod/edf-remastered) | 2,376,719 | adventure, game-mechanics, magic, mobs |
| 666 | [CTOV - Friends and Foes Compat](https://modrinth.com/mod/ctov-friends-and-foes-compat) | 2,372,826 | economy, mobs, worldgen |
| 667 | [Friendly Fire](https://modrinth.com/mod/friendly-fire) | 2,368,624 | utility |
| 668 | [TorchMaster](https://modrinth.com/mod/torchmaster) | 2,367,449 | decoration, mobs, utility |
| 669 | [Deep Aether](https://modrinth.com/mod/deep-aether) | 2,359,920 | adventure, decoration, equipment, mobs, worldgen |
| 670 | [Eureka! Ships! for Valkyrien Skies (Forge/Fabric)](https://modrinth.com/mod/eureka) | 2,349,722 | adventure, game-mechanics, transportation |
| 671 | [FullStack Watchdog](https://modrinth.com/mod/fullstack-watchdog) | 2,341,853 | utility |
| 672 | [Adorable Hamster Pets](https://modrinth.com/mod/adorable-hamster-pets) | 2,336,819 | adventure, decoration, food, game-mechanics, mobs, storage, utility, worldgen |
| 673 | [Pufferfish's Attributes](https://modrinth.com/mod/attributes) | 2,324,037 | game-mechanics, library, management, utility |
| 674 | [Pineapple Delight](https://modrinth.com/mod/pineapple-delight) | 2,317,673 | food |
| 675 | [Mythic Mounts](https://modrinth.com/mod/mythic-mounts) | 2,296,120 | adventure, mobs, storage, transportation, worldgen |
| 676 | [Atmospheric](https://modrinth.com/mod/atmospheric) | 2,295,618 | adventure, decoration, equipment, food, mobs, worldgen |
| 677 | [Swampier Swamps](https://modrinth.com/mod/swampier-swamps) | 2,295,279 | decoration, game-mechanics, mobs, worldgen |
| 678 | [The Undergarden](https://modrinth.com/mod/the-undergarden) | 2,292,965 | adventure, equipment, food, mobs, worldgen |
| 679 | [ItemPhysic](https://modrinth.com/mod/itemphysic) | 2,291,500 | game-mechanics |
| 680 | [Moog's Structure Lib (moogs_structures)](https://modrinth.com/mod/moogs-structure-lib) | 2,287,075 | utility, worldgen |
| 681 | [Raised](https://modrinth.com/mod/raised) | 2,285,908 | utility |
| 682 | [[TACZ]LesRaisins Append Pack](https://modrinth.com/mod/lesraisins-weapon) | 2,280,215 | adventure, equipment |
| 683 | [PandaLib](https://modrinth.com/mod/pandalib) | 2,279,767 | library |
| 684 | [Hopo Better Underwater Ruins](https://modrinth.com/mod/hopo-better-underwater-ruins) | 2,272,397 | adventure, decoration, worldgen |
| 685 | [ObsidianUI](https://modrinth.com/mod/obsidianui) | 2,269,672 | library, utility |
| 686 | [Immersive Messages API](https://modrinth.com/mod/immersive-messages-api) | 2,269,308 | decoration, game-mechanics, library, utility |
| 687 | [Upgrade Aquatic](https://modrinth.com/mod/upgrade-aquatic) | 2,267,324 | adventure, decoration, equipment, food, game-mechanics, mobs, worldgen |
| 688 | [Mermod](https://modrinth.com/mod/mermod) | 2,265,597 | adventure, magic |
| 689 | [Bosses of Mass Destruction Forge](https://modrinth.com/mod/bosses-of-mass-destruction-forge) | 2,260,904 | adventure, mobs, worldgen |
| 690 | [Structurify](https://modrinth.com/mod/structurify) | 2,239,287 | adventure, game-mechanics, management, utility, worldgen |
| 691 | [Elytra Physics](https://modrinth.com/mod/elytra-physics) | 2,238,744 | decoration, equipment |
| 692 | [Armor Statues](https://modrinth.com/mod/armor-statues) | 2,235,860 | decoration, equipment, game-mechanics, utility |
| 693 | [Paragliders](https://modrinth.com/mod/paragliders) | 2,235,657 | adventure, equipment, game-mechanics, transportation, worldgen |
| 694 | [Music Maker](https://modrinth.com/mod/music-maker-mod) | 2,231,718 | decoration, game-mechanics, social |
| 695 | [Max Health Fix](https://modrinth.com/mod/max-health-fix) | 2,230,413 | adventure, equipment, utility |
| 696 | [Plushie Buddies](https://modrinth.com/mod/plushie-buddies) | 2,224,597 | adventure, decoration |
| 697 | [It Takes a Pillage](https://modrinth.com/mod/it-takes-a-pillage) | 2,217,842 | adventure, game-mechanics, mobs, worldgen |
| 698 | [Boatload](https://modrinth.com/mod/boatload) | 2,215,724 | adventure, game-mechanics, storage, transportation, utility |
| 699 | [Decorative Blocks](https://modrinth.com/mod/decorative-blocks-fork) | 2,214,821 | decoration |
| 700 | [Scribble](https://modrinth.com/mod/scribble) | 2,211,330 | equipment, game-mechanics, social, utility |
| 701 | [Tweakerge](https://modrinth.com/mod/tweakerge) | 2,193,905 | cursed, game-mechanics, utility |
| 702 | [No Report Button](https://modrinth.com/mod/nrb) | 2,185,398 | utility |
| 703 | [Hellion's Sniffer+](https://modrinth.com/mod/hellions-sniffer+) | 2,183,537 | decoration, mobs, storage, transportation |
| 704 | [Fast Noise](https://modrinth.com/mod/zfastnoise) | 2,182,276 | optimization, worldgen |
| 705 | [XP Tome](https://modrinth.com/mod/xp-tome) | 2,180,560 | adventure, magic, storage, technology, utility |
| 706 | [Enigmatic Legacy](https://modrinth.com/mod/enigmatic-legacy) | 2,180,508 | adventure, equipment, magic |
| 707 | [Autumnity](https://modrinth.com/mod/autumnity) | 2,176,053 | adventure, decoration, equipment, food, game-mechanics, mobs, worldgen |
| 708 | [Day Counter (Original)](https://modrinth.com/mod/daycounter) | 2,173,849 | decoration, game-mechanics, utility |
| 709 | [Snow Under Trees](https://modrinth.com/mod/snow-under-trees) | 2,170,498 | worldgen |
| 710 | [[Let's Do] Furniture](https://modrinth.com/mod/lets-do-furniture) | 2,154,698 | decoration, storage |
| 711 | [Elysium API](https://modrinth.com/mod/elysium-api) | 2,151,477 | library, utility, worldgen |
| 712 | [Just Hammers](https://modrinth.com/mod/just-hammers) | 2,150,234 | game-mechanics, technology, utility |
| 713 | [Dark Paintings](https://modrinth.com/mod/dark-paintings) | 2,149,458 | adventure, utility |
| 714 | [Medieval Buildings [End Edition]](https://modrinth.com/mod/medieval-buildings-end-edition) | 2,141,196 | adventure, decoration, equipment, mobs, worldgen |
| 715 | [Leawind's Third Person](https://modrinth.com/mod/leawind-third-person) | 2,128,578 | adventure, utility |
| 716 | [Euphonium](https://modrinth.com/mod/euphonium) | 2,125,751 | decoration |
| 717 | [MC Dungeons Armors](https://modrinth.com/mod/mcda) | 2,125,710 | adventure, equipment, magic |
| 718 | [Despawn Tweaks](https://modrinth.com/mod/despawn-tweaks) | 2,113,273 | optimization, utility |
| 719 | [In Control!](https://modrinth.com/mod/in-control) | 2,113,181 | management, mobs, utility |
| 720 | [Bushier Flowers](https://modrinth.com/mod/bushier-flowers) | 2,107,831 | decoration, game-mechanics, utility, worldgen |
| 721 | [[ESF] Entity Sound Features](https://modrinth.com/mod/esf) | 2,107,117 | mobs, utility |
| 722 | [Vintage Delight](https://modrinth.com/mod/vintage-delight) | 2,106,460 | equipment, food, game-mechanics, worldgen |
| 723 | [Tom's Trading Network](https://modrinth.com/mod/toms-trading-network) | 2,105,167 | economy, management, utility |
| 724 | [Ore Excavation](https://modrinth.com/mod/ore-excavation) | 2,099,034 | equipment, game-mechanics, utility |
| 725 | [Passable Foliage 🌳](https://modrinth.com/mod/passable-foliage) | 2,097,263 | game-mechanics, utility |
| 726 | [A Good Place](https://modrinth.com/mod/a-good-place) | 2,093,736 | decoration, utility |
| 727 | [CTOV - Beautify Compat](https://modrinth.com/mod/ctov-beautify-compat) | 2,085,400 | economy, worldgen |
| 728 | [Dungeons and Taverns Pillager Outpost Overhaul](https://modrinth.com/mod/dungeons-and-taverns-pillager-outpost-overhaul) | 2,084,024 | adventure, worldgen |
| 729 | [Radiant Gear](https://modrinth.com/mod/radiant-gear) | 2,081,834 | utility |
| 730 | [Immersive Weathering](https://modrinth.com/mod/immersive-weathering) | 2,079,139 | decoration, utility, worldgen |
| 731 | [Plushie Mod](https://modrinth.com/mod/plushie) | 2,076,026 | decoration |
| 732 | [Command Keys](https://modrinth.com/mod/commandkeys) | 2,072,905 | utility |
| 733 | [[Let's Do Addon] Compat](https://modrinth.com/mod/lets-do-addon-compat) | 2,063,077 | adventure, food, game-mechanics |
| 734 | [GuideME](https://modrinth.com/mod/guideme) | 2,061,574 | library, utility |
| 735 | [LuckPerms](https://modrinth.com/mod/luckperms) | 2,057,539 | management, utility |
| 736 | [Iron Chests](https://modrinth.com/mod/iron-chests) | 2,052,338 | storage, utility |
| 737 | [Block Runner](https://modrinth.com/mod/block-runner) | 2,044,360 | utility |
| 738 | [Search on MCMOD](https://modrinth.com/mod/search-on-mcmod) | 2,041,547 | utility |
| 739 | [#CarrasconLib](https://modrinth.com/mod/carrasconlib) | 2,037,366 | library |
| 740 | [Wither Spawn Fix](https://modrinth.com/mod/wither-spawn-fix) | 2,036,523 | mobs, optimization, utility |
| 741 | [Valhelsia Core](https://modrinth.com/mod/valhelsia-core) | 2,031,765 | library |
| 742 | [Enhanced Celestials](https://modrinth.com/mod/enhanced-celestials) | 2,028,256 | adventure, magic |
| 743 | [Diet](https://modrinth.com/mod/diet) | 2,025,627 | food, game-mechanics |
| 744 | [The Conjurer](https://modrinth.com/mod/the-conjurer) | 2,022,559 | equipment, mobs, worldgen |
| 745 | [iChunUtil](https://modrinth.com/mod/ichunutil) | 2,021,917 | library |
| 746 | [Tax Free Levels](https://modrinth.com/mod/tax-free-levels) | 2,014,363 | game-mechanics |
| 747 | [Duckling](https://modrinth.com/mod/duckling) | 2,013,824 | food, mobs |
| 748 | [Fast Item Frames](https://modrinth.com/mod/fast-item-frames) | 2,013,363 | decoration, optimization |
| 749 | [World Play Time](https://modrinth.com/mod/world-play-time) | 2,009,348 | decoration, management, utility |
| 750 | [Aether: Lost Content Addon](https://modrinth.com/mod/aether-lost-content) | 2,007,552 | adventure, equipment, mobs, worldgen |
| 751 | [Create Stuff 'N Additions](https://modrinth.com/mod/create-stuff-additions) | 2,005,296 | equipment, game-mechanics, transportation, utility |
| 752 | [ItemPhysic Lite](https://modrinth.com/mod/itemphysic-lite) | 2,003,897 | decoration |
| 753 | [Neko's Enchanted Books](https://modrinth.com/mod/nekos-enchanted-books) | 2,002,833 | utility |
| 754 | [[Let's Do Addon] Corn Expansion](https://modrinth.com/mod/lets-do-addon-corn-expansion) | 2,000,976 | adventure, decoration, food |
| 755 | [Create Contraption Terminals](https://modrinth.com/mod/create-contraption-terminals) | 1,994,944 | game-mechanics, storage, technology, utility |
| 756 | [MonoLib](https://modrinth.com/mod/monolib) | 1,987,064 | library |
| 757 | [Farmer's Cutting: Biomes O' Plenty](https://modrinth.com/mod/farmers-cutting-biomes-o-plenty) | 1,979,159 | adventure, decoration, utility |
| 758 | [Infinite Trading](https://modrinth.com/mod/infinite-trading) | 1,953,522 | game-mechanics, utility |
| 759 | [Ad-Astra: Giselle Addon](https://modrinth.com/mod/ad-astra-giselle-addon) | 1,952,425 | technology |
| 760 | [Easy Elytra Takeoff](https://modrinth.com/mod/easy-elytra-takeoff) | 1,952,400 | game-mechanics, transportation, utility |
| 761 | [Clockwork](https://modrinth.com/mod/create-clockwork) | 1,948,489 | adventure, game-mechanics, magic, technology, transportation |
| 762 | [Surveyor Map Framework](https://modrinth.com/mod/surveyor) | 1,946,031 | library, utility |
| 763 | [CobblemonRider](https://modrinth.com/mod/cobblemonrider1.5) | 1,942,101 | game-mechanics, mobs |
| 764 | [Anytag](https://modrinth.com/mod/anytag) | 1,937,163 | decoration, social, utility |
| 765 | [Mob Sunscreen](https://modrinth.com/mod/mob-sunscreen) | 1,937,053 | — |
| 766 | [Mekanism Generators](https://modrinth.com/mod/mekanism-generators) | 1,933,313 | technology |
| 767 | [Better Brightness Slider](https://modrinth.com/mod/better-brightness-slider) | 1,922,421 | decoration, utility |
| 768 | [Seamless](https://modrinth.com/mod/seamless) | 1,918,336 | — |
| 769 | [Horse Expert](https://modrinth.com/mod/horse-expert) | 1,916,800 | equipment, utility |
| 770 | [Legacy: [Let's Do] Brewery](https://modrinth.com/mod/lets-do-brewery) | 1,914,030 | decoration, equipment, food |
| 771 | [XXL Packets](https://modrinth.com/mod/xxl-packets) | 1,910,316 | optimization, utility |
| 772 | [Blue Skies](https://modrinth.com/mod/blue-skies) | 1,903,378 | adventure, decoration, equipment, food, game-mechanics, mobs, worldgen |
| 773 | [XPlus Autofish (Fabric / Forge / NeoForge)](https://modrinth.com/mod/x+-autofish) | 1,899,528 | adventure, game-mechanics, utility |
| 774 | [Terramity](https://modrinth.com/mod/terramity) | 1,898,867 | adventure, decoration, equipment, game-mechanics, mobs, worldgen |
| 775 | [SDM Shop](https://modrinth.com/mod/sdm-shop) | 1,896,920 | economy, game-mechanics, management, social, utility |
| 776 | [cat_jam](https://modrinth.com/mod/cat_jam) | 1,890,594 | decoration, mobs |
| 777 | [Auudio](https://modrinth.com/mod/auudio) | 1,887,824 | library |
| 778 | [Better Biome Reblend](https://modrinth.com/mod/bbrb) | 1,875,340 | optimization, utility, worldgen |
| 779 | [Bosses'Rise](https://modrinth.com/mod/bossesrise) | 1,873,008 | adventure, equipment, mobs, worldgen |
| 780 | [Adorn](https://modrinth.com/mod/adorn) | 1,868,040 | decoration, storage |
| 781 | [Hopo Better Mineshaft](https://modrinth.com/mod/hopo-better-mineshaft) | 1,867,927 | adventure, decoration, game-mechanics, worldgen |
| 782 | [Dungeon's Delight](https://modrinth.com/mod/dungeons_delight) | 1,866,230 | adventure, decoration, equipment, food, game-mechanics, mobs, storage, worldgen |
| 783 | [Amplified Nether](https://modrinth.com/mod/amplified-nether) | 1,865,653 | worldgen |
| 784 | [Superb Warfare](https://modrinth.com/mod/superb-warfare) | 1,864,688 | adventure, equipment, mobs, worldgen |
| 785 | [SDM UI Lib](https://modrinth.com/mod/sdm-ui-lib) | 1,864,384 | decoration, library, utility |
| 786 | [Create Utilities](https://modrinth.com/mod/create-utilities) | 1,861,218 | storage, technology, transportation, utility |
| 787 | [Powah!](https://modrinth.com/mod/powah) | 1,859,535 | storage, technology |
| 788 | [CoFH Core](https://modrinth.com/mod/cofh-core) | 1,855,460 | library, utility |
| 789 | [ParCool!](https://modrinth.com/mod/parcool) | 1,855,266 | game-mechanics, minigame, transportation, utility |
| 790 | [VerdantVibes](https://modrinth.com/mod/verdantvibes) | 1,854,686 | decoration, game-mechanics, worldgen |
| 791 | [Create: Power Loader](https://modrinth.com/mod/create-power-loader) | 1,853,316 | game-mechanics, technology, utility |
| 792 | [Scholar](https://modrinth.com/mod/scholar) | 1,850,741 | decoration, utility |
| 793 | [Lost Cities Modern Tweaks](https://modrinth.com/mod/lost-cities-modern-tweaks) | 1,848,276 | adventure, worldgen |
| 794 | [Ctrl Q](https://modrinth.com/mod/ctrl-q) | 1,848,114 | game-mechanics, utility |
| 795 | [My Server Is Compatible](https://modrinth.com/mod/my-server-is-compatible) | 1,846,900 | utility |
| 796 | [The Aether: Redux](https://modrinth.com/mod/the-aether-redux) | 1,842,450 | adventure, decoration, equipment, food, game-mechanics, mobs, utility, worldgen |
| 797 | [Spelunkery](https://modrinth.com/mod/spelunkery) | 1,833,728 | adventure, decoration, equipment, food, game-mechanics, utility, worldgen |
| 798 | [Cobblemon Counter](https://modrinth.com/mod/cobblemon-counter) | 1,833,372 | game-mechanics |
| 799 | [3D Placeable Food](https://modrinth.com/mod/3d-placeable-food) | 1,812,617 | decoration, food |
| 800 | [Weapons of miracles](https://modrinth.com/mod/weapons-of-miracles) | 1,806,792 | adventure, equipment |
| 801 | [RunicLib](https://modrinth.com/mod/runiclib) | 1,806,258 | game-mechanics, library, magic, utility |
| 802 | [Moving Elevators](https://modrinth.com/mod/moving-elevators) | 1,803,121 | decoration, technology |
| 803 | [Majrusz Library](https://modrinth.com/mod/majrusz-library) | 1,799,904 | game-mechanics, library, utility |
| 804 | [Perception](https://modrinth.com/mod/perception) | 1,785,564 | adventure, decoration, utility |
| 805 | [Create: Framed](https://modrinth.com/mod/create-framed) | 1,785,371 | decoration, technology, utility |
| 806 | [Valhelsia Furniture](https://modrinth.com/mod/valhelsia-furniture) | 1,784,245 | decoration, storage, utility |
| 807 | [Geophilic Reforged](https://modrinth.com/mod/geophilic-reforged) | 1,782,415 | adventure, worldgen |
| 808 | [World Preview](https://modrinth.com/mod/world-preview) | 1,776,838 | utility, worldgen |
| 809 | [LibX](https://modrinth.com/mod/libx) | 1,776,578 | library |
| 810 | [Estrogen](https://modrinth.com/mod/estrogen) | 1,771,830 | cursed, equipment, food, game-mechanics, minigame, technology, transportation, utility |
| 811 | [Smooth Gui](https://modrinth.com/mod/smooth-gui) | 1,768,383 | decoration |
| 812 | [Bagus Lib](https://modrinth.com/mod/bagus-lib) | 1,767,292 | library, mobs |
| 813 | [Realm RPG: Fallen Adventurers](https://modrinth.com/mod/realm-rpg-fallen-adventurers) | 1,764,093 | adventure, decoration, equipment, food, game-mechanics, magic, mobs, utility, worldgen |
| 814 | [Open Parties and Claims PvP Support](https://modrinth.com/mod/opacpvp) | 1,760,650 | management, utility |
| 815 | [Enderite Mod](https://modrinth.com/mod/enderite-mod) | 1,755,871 | adventure, equipment, game-mechanics, storage, utility, worldgen |
| 816 | [Hamsters](https://modrinth.com/mod/hamsters) | 1,753,910 | adventure, mobs, worldgen |
| 817 | [Rare Ice](https://modrinth.com/mod/rare-ice) | 1,750,134 | — |
| 818 | [Delete Worlds To Trash](https://modrinth.com/mod/delete-worlds-to-trash) | 1,740,570 | management, utility |
| 819 | [Untitled Duck Mod](https://modrinth.com/mod/untitled-duck-mod) | 1,738,360 | worldgen |
| 820 | [Ritchie's Projectile Library](https://modrinth.com/mod/rpl) | 1,730,197 | library |
| 821 | [AeroBlender](https://modrinth.com/mod/aeroblender) | 1,729,957 | library, worldgen |
| 822 | [Equipment Compare](https://modrinth.com/mod/equipment-compare) | 1,719,841 | adventure, decoration, equipment, game-mechanics, utility |
| 823 | [Botany Pots](https://modrinth.com/mod/botany-pots) | 1,714,817 | food |
| 824 | [Cosmopolitan](https://modrinth.com/mod/cosmopolitan) | 1,713,990 | food, storage, worldgen |
| 825 | [Stalwart Dungeons](https://modrinth.com/mod/stalwart-dungeons) | 1,711,971 | adventure, equipment, worldgen |
| 826 | [Neapolitan](https://modrinth.com/mod/neapolitan) | 1,711,210 | adventure, decoration, food, mobs, utility |
| 827 | [Mysterious Mountain Lib](https://modrinth.com/mod/mmlib) | 1,710,244 | library |
| 828 | [Armor Stand Arms](https://modrinth.com/mod/armor-stand-arms) | 1,707,837 | decoration, equipment, game-mechanics, utility |
| 829 | [ElevatorMod](https://modrinth.com/mod/elevatormod) | 1,707,466 | decoration, transportation |
| 830 | [L2 Library](https://modrinth.com/mod/l2library) | 1,706,390 | library |
| 831 | [Echo Chest](https://modrinth.com/mod/echo-chest) | 1,698,137 | management, storage, utility |
| 832 | [Jupiter](https://modrinth.com/mod/jupiter) | 1,692,395 | game-mechanics, library, utility |
| 833 | [WITS (What Is This Structure?)](https://modrinth.com/mod/wits) | 1,688,577 | utility, worldgen |
| 834 | [Inventory Essentials](https://modrinth.com/mod/inventory-essentials) | 1,687,197 | management, utility |
| 835 | [ItemLocks](https://modrinth.com/mod/itemlocks) | 1,683,715 | equipment, storage, utility |
| 836 | [Trash Cans](https://modrinth.com/mod/trash-cans) | 1,682,892 | decoration, storage, technology |
| 837 | [The Knocker](https://modrinth.com/mod/the-knocker) | 1,679,802 | cursed, mobs |
| 838 | [Cobblemon Unchained](https://modrinth.com/mod/cobblemon-unchained) | 1,677,636 | game-mechanics, mobs |
| 839 | [Create Encased](https://modrinth.com/mod/create-encased) | 1,676,547 | decoration |
| 840 | [SlashBlade:Resharped](https://modrinth.com/mod/slashblade-resharped) | 1,676,307 | adventure, decoration, equipment |
| 841 | [ContingameIME](https://modrinth.com/mod/contingameime) | 1,675,676 | utility |
| 842 | [End's Phantasm](https://modrinth.com/mod/ends-phantasm) | 1,672,404 | adventure, decoration, equipment, food, game-mechanics, mobs, worldgen |
| 843 | [RuOK](https://modrinth.com/mod/ruok) | 1,672,173 | optimization |
| 844 | [ExtraSounds Next](https://modrinth.com/mod/extrasoundsforge) | 1,666,176 | — |
| 845 | [MCPitanLib](https://modrinth.com/mod/mcpitanlibarch) | 1,661,461 | library |
| 846 | [Storage Delight](https://modrinth.com/mod/storage-delight) | 1,661,260 | adventure, decoration, food, magic, storage, utility |
| 847 | [SeasonHud](https://modrinth.com/mod/seasonhud) | 1,657,286 | game-mechanics, management, utility |
| 848 | [Shut Up GL Error](https://modrinth.com/mod/shut-up-gl-error) | 1,655,978 | game-mechanics, optimization |
| 849 | [Gensokyo Delight ~ Youkais' Feasts](https://modrinth.com/mod/gensokyo-delight-youkais-feasts) | 1,653,166 | decoration, food |
| 850 | [Nature's Delight](https://modrinth.com/mod/natures-delight) | 1,651,622 | food |
| 851 | [Stack Refill](https://modrinth.com/mod/stack-refill) | 1,647,275 | utility |
| 852 | [Mob Lassos](https://modrinth.com/mod/mob-lassos) | 1,637,719 | equipment, game-mechanics, mobs, transportation |
| 853 | [RyoamicLights](https://modrinth.com/mod/ryoamiclights) | 1,636,933 | adventure, decoration, utility |
| 854 | [Lost Cities Filter](https://modrinth.com/mod/lostcitiesfilter) | 1,636,413 | optimization |
| 855 | [Ars Nouveau's Flavors & Delight](https://modrinth.com/mod/arsdelight) | 1,636,046 | adventure, decoration, food, magic |
| 856 | [Woodworks](https://modrinth.com/mod/woodworks) | 1,634,450 | decoration, storage, utility, worldgen |
| 857 | [Improved Village Placement](https://modrinth.com/mod/improved-village-placement) | 1,632,610 | adventure, utility, worldgen |
| 858 | [CTOV - Chef's delight Compat](https://modrinth.com/mod/ctov-chefs-delight-compat) | 1,628,119 | food, game-mechanics, worldgen |
| 859 | [MC Dungeons Weapons](https://modrinth.com/mod/mcdw) | 1,625,290 | adventure, equipment, magic |
| 860 | [Axes Are Weapons](https://modrinth.com/mod/axes-are-weapons) | 1,623,716 | equipment, game-mechanics |
| 861 | [Selfexpression](https://modrinth.com/mod/selfexpression) | 1,623,567 | cursed, decoration, equipment, social |
| 862 | [Fragmentum](https://modrinth.com/mod/fragmentum) | 1,621,891 | library |
| 863 | [Riding Partners：Reforged](https://modrinth.com/mod/riding-partnersreforged) | 1,620,905 | mobs, transportation |
| 864 | [Clayworks](https://modrinth.com/mod/clayworks) | 1,620,238 | decoration, game-mechanics, utility |
| 865 | [Bigger Stacks](https://modrinth.com/mod/biggerstacks) | 1,620,089 | game-mechanics, library, storage, utility |
| 866 | [Legacy: [Let's Do] Bakery](https://modrinth.com/mod/lets-do-bakery) | 1,607,726 | decoration, food, storage, worldgen |
| 867 | [Guns ++](https://modrinth.com/mod/guns++) | 1,607,113 | adventure, equipment |
| 868 | [Mischief Illagers](https://modrinth.com/mod/mischief-illagers) | 1,605,058 | adventure, equipment, mobs, worldgen |
| 869 | [Mobtimizations - Entity Performance Fixes](https://modrinth.com/mod/mobtimizations) | 1,603,803 | mobs, optimization, utility |
| 870 | [Dragon Drops Elytra](https://modrinth.com/mod/dragon-drops-elytra) | 1,601,854 | adventure, game-mechanics, social |
| 871 | [[TACZ] LesRaisins Tactical Equipements](https://modrinth.com/mod/lr-tactical) | 1,599,263 | adventure, equipment |
| 872 | [FramedBlocks](https://modrinth.com/mod/framedblocks) | 1,593,880 | decoration |
| 873 | [CreateBetterFps](https://modrinth.com/mod/createbetterfps) | 1,584,784 | optimization |
| 874 | [William Wythers' Overhauled Overworld](https://modrinth.com/mod/wwoo) | 1,574,849 | adventure, worldgen |
| 875 | [Immersive Machinery](https://modrinth.com/mod/immersive-machinery) | 1,573,189 | storage, technology, transportation, utility |
| 876 | [Trail&Tales Delight](https://modrinth.com/mod/trailtales-delight) | 1,567,905 | decoration, food, storage |
| 877 | [Cracker's Wither Storm Mod](https://modrinth.com/mod/crackers-wither-storm-mod) | 1,565,918 | adventure, cursed, equipment, game-mechanics, mobs, worldgen |
| 878 | [Additional Additions](https://modrinth.com/mod/addadd) | 1,560,235 | adventure, equipment, food, game-mechanics, utility |
| 879 | [River Redux](https://modrinth.com/mod/river-redux) | 1,556,319 | adventure, worldgen |
| 880 | [Polymorphic Energistics](https://modrinth.com/mod/polymorphic-energistics) | 1,554,684 | utility |
| 881 | [Dark Utilities](https://modrinth.com/mod/dark-utilities) | 1,552,337 | adventure, equipment |
| 882 | [KubeJS Additions](https://modrinth.com/mod/kubejs-additions) | 1,548,951 | game-mechanics, library, management, utility |
| 883 | [Create: Oxidized](https://modrinth.com/mod/create_oxidized) | 1,547,021 | game-mechanics, utility |
| 884 | [Colorwheel](https://modrinth.com/mod/colorwheel) | 1,544,869 | optimization |
| 885 | [CrashExploitFixer](https://modrinth.com/mod/crashexploitfixer) | 1,543,268 | management, optimization, utility |
| 886 | [Deltabox Lib](https://modrinth.com/mod/deltaboxlib) | 1,537,037 | game-mechanics, library, worldgen |
| 887 | [Allurement](https://modrinth.com/mod/allurement!) | 1,536,540 | adventure, equipment, game-mechanics, magic, utility |
| 888 | [MEGA Cells](https://modrinth.com/mod/mega) | 1,535,251 | storage, technology |
| 889 | [Hopo Better Ruined Portals](https://modrinth.com/mod/hopo-better-ruined-portals) | 1,532,473 | adventure, cursed, worldgen |
| 890 | [Beautify!](https://modrinth.com/mod/beautify) | 1,528,880 | decoration, game-mechanics, utility, worldgen |
| 891 | [Tough As Nails](https://modrinth.com/mod/tough-as-nails) | 1,526,294 | equipment, food, game-mechanics |
| 892 | [GravelMiner](https://modrinth.com/mod/gravelminer) | 1,523,667 | game-mechanics, utility |
| 893 | [Magic Vibe Decorations (Crystals, Halloween)](https://modrinth.com/mod/magic-vibe-decorations) | 1,513,661 | adventure, decoration, magic |
| 894 | [Cerulean](https://modrinth.com/mod/cerulean-advancements) | 1,513,086 | optimization |
| 895 | [Re:Deco](https://modrinth.com/mod/redeco) | 1,512,662 | decoration, storage, utility |
| 896 | [Dungeon Now Loading](https://modrinth.com/mod/dungeon-now-loading) | 1,508,451 | adventure, decoration, equipment, game-mechanics, mobs, worldgen |
| 897 | [The Midnight Lurker](https://modrinth.com/mod/the-midnight-lurker) | 1,507,113 | adventure, cursed, equipment, mobs, worldgen |
| 898 | [Panorama Screens](https://modrinth.com/mod/panorama-screens) | 1,506,773 | utility |
| 899 | [Sully's Mod](https://modrinth.com/mod/sullysmod) | 1,506,212 | adventure, decoration, equipment, food, mobs, worldgen |
| 900 | [Create: Extended Cogwheels](https://modrinth.com/mod/extended-cogwheels) | 1,504,259 | decoration, game-mechanics, technology |
| 901 | [Casualness Delight](https://modrinth.com/mod/casualness-delight) | 1,498,132 | food |
| 902 | [Abnormals Delight](https://modrinth.com/mod/abnormals-delight) | 1,497,381 | adventure, decoration, equipment, food, game-mechanics, storage |
| 903 | [Simple Snowy Fix (Forge / Fabric)](https://modrinth.com/mod/simple-snowy-fix-(forge-fabric)) | 1,495,513 | optimization, worldgen |
| 904 | [Etcetera](https://modrinth.com/mod/etcetera) | 1,494,773 | adventure, decoration, equipment, game-mechanics, management, mobs, storage, technology, utility, worldgen |
| 905 | [Pro Placer](https://modrinth.com/mod/pro-placer) | 1,494,553 | game-mechanics, utility |
| 906 | [Aether Addon: Enhanced Extinguishing](https://modrinth.com/mod/aether-enhanced-extinguishing) | 1,492,265 | decoration, game-mechanics |
| 907 | [Festive Delight - Christmas eve](https://modrinth.com/mod/festive-delight) | 1,488,572 | decoration, food |
| 908 | [Awesome Dungeon Ocean](https://modrinth.com/mod/awesome-dungeon-edition-ocean) | 1,487,824 | adventure, mobs, worldgen |
| 909 | [More Overlays Updated](https://modrinth.com/mod/more-overlays-updated) | 1,485,400 | game-mechanics, mobs, utility |
| 910 | [Minecraft Transit Railway](https://modrinth.com/mod/minecraft-transit-railway) | 1,481,501 | decoration, technology, transportation |
| 911 | [Transmog](https://modrinth.com/mod/transmog) | 1,480,960 | equipment, utility |
| 912 | [Players Drop Heads](https://modrinth.com/mod/players-drop-heads) | 1,479,259 | decoration, game-mechanics, social |
| 913 | [Fish of Thieves](https://modrinth.com/mod/fish-of-thieves) | 1,469,121 | adventure, decoration, food, mobs, worldgen |
| 914 | [Universal Enchants](https://modrinth.com/mod/universal-enchants) | 1,467,009 | equipment, game-mechanics, utility |
| 915 | [Runelic](https://modrinth.com/mod/runelic) | 1,466,817 | adventure, magic, utility |
| 916 | [Enlightend](https://modrinth.com/mod/enlightend) | 1,466,292 | adventure, decoration, equipment, food, game-mechanics, mobs, worldgen |
| 917 | [Smelting Plus](https://modrinth.com/mod/smelting-plus) | 1,464,058 | game-mechanics, utility |
| 918 | [Animation Overhaul](https://modrinth.com/mod/animationoverhaul) | 1,461,948 | decoration |
| 919 | [Create Railways Navigator](https://modrinth.com/mod/create-railways-navigator) | 1,461,384 | decoration, technology, transportation, utility |
| 920 | [[Let's Do] Camping](https://modrinth.com/mod/lets-do-camping) | 1,459,863 | decoration, storage |
| 921 | [qrafty's Jungle Villages](https://modrinth.com/mod/qraftys-jungle-villages) | 1,458,501 | adventure, worldgen |
| 922 | [[Let's Do Addon] EMI Compat](https://modrinth.com/mod/lets-do-emi-compat) | 1,449,748 | utility |
| 923 | [IBE Editor](https://modrinth.com/mod/ibe-editor) | 1,448,671 | utility |
| 924 | [Xenon](https://modrinth.com/mod/xenon-forge) | 1,448,434 | optimization, utility |
| 925 | [LDLib](https://modrinth.com/mod/ldlib) | 1,448,384 | library, utility |
| 926 | [[NoCube's] Undergarden Delight](https://modrinth.com/mod/undergarden-delight) | 1,448,033 | adventure, decoration, equipment, food, game-mechanics, management, storage, transportation, utility |
| 927 | [Terra Curio](https://modrinth.com/mod/terra-curio) | 1,447,678 | adventure, equipment |
| 928 | [Companion 🐕](https://modrinth.com/mod/companion) | 1,446,017 | game-mechanics, mobs, utility |
| 929 | [CTOV - Farmer Delight Compat](https://modrinth.com/mod/ctov-farmers-delight-compat) | 1,444,604 | decoration, worldgen |
| 930 | [Satisfying Buttons](https://modrinth.com/mod/satisfying-buttons) | 1,441,457 | — |
| 931 | [Uranus](https://modrinth.com/mod/uranus) | 1,439,246 | library, utility |
| 932 | [Cultural Delights](https://modrinth.com/mod/cultural-delights) | 1,433,817 | adventure, food, worldgen |
| 933 | [KubeJS Create](https://modrinth.com/mod/kubejs-create) | 1,433,356 | library, utility |
| 934 | [Inventory Particles](https://modrinth.com/mod/inventory-particles) | 1,431,933 | cursed, decoration, equipment, food, game-mechanics, storage, utility |
| 935 | [Punchy!](https://modrinth.com/mod/punchy-fpa) | 1,429,527 | decoration, equipment, game-mechanics |
| 936 | [The End of Herobrine](https://modrinth.com/mod/endofherobrine) | 1,421,913 | adventure, game-mechanics, magic, mobs |
| 937 | [Paladin's Furniture Mod](https://modrinth.com/mod/paladins-furniture) | 1,418,930 | decoration, storage, utility |
| 938 | [YDM's MobHealthBar](https://modrinth.com/mod/ydms-mobhealthbar) | 1,417,574 | decoration |
| 939 | [Youkai's Homecoming](https://modrinth.com/mod/youkaishomecoming) | 1,412,995 | adventure, food, minigame, mobs, worldgen |
| 940 | [Naturally Trimmed](https://modrinth.com/mod/naturally-trimmed) | 1,411,604 | adventure, decoration, equipment, game-mechanics, mobs |
| 941 | [Aether Villages](https://modrinth.com/mod/aether-villages) | 1,404,100 | adventure, worldgen |
| 942 | [Auto HUD](https://modrinth.com/mod/autohud) | 1,403,830 | utility |
| 943 | [Fog](https://modrinth.com/mod/fog) | 1,403,332 | game-mechanics, utility |
| 944 | [Fancy Block Particles - Renewed](https://modrinth.com/mod/fbp-renewed) | 1,400,380 | decoration |
| 945 | [Create: Misc and Things](https://modrinth.com/mod/create-misc-and-things) | 1,398,931 | decoration, equipment, technology, utility |
| 946 | [ATi Structures](https://modrinth.com/mod/ati-structures-fabricforge) | 1,397,495 | adventure, decoration, equipment, mobs, worldgen |
| 947 | [Gardener's Dream](https://modrinth.com/mod/gardeners-dream) | 1,395,549 | decoration, game-mechanics, utility |
| 948 | [tetra](https://modrinth.com/mod/tetra) | 1,393,362 | adventure, equipment, game-mechanics, worldgen |
| 949 | [Legacy: [Let's Do] Candlelight](https://modrinth.com/mod/lets-do-candlelight) | 1,392,737 | decoration, food, game-mechanics, utility, worldgen |
| 950 | [Dragon Mounts: Legacy](https://modrinth.com/mod/dragon-mounts-legacy) | 1,390,058 | adventure, game-mechanics, magic, mobs, transportation, utility |
| 951 | [Soul Fire'd](https://modrinth.com/mod/soul-fire-d) | 1,385,892 | adventure, equipment, game-mechanics, magic |
| 952 | [Get It Together, Drops!](https://modrinth.com/mod/get-it-together-drops) | 1,385,322 | optimization, utility |
| 953 | [Unnamed Desert](https://modrinth.com/mod/unnamed-desert) | 1,381,361 | adventure, worldgen |
| 954 | [Customizable Elytra](https://modrinth.com/mod/customizable-elytra) | 1,380,213 | decoration, equipment |
| 955 | [Dimensional Sync Fixes](https://modrinth.com/mod/dimensional-sync-fixes) | 1,377,867 | management, utility |
| 956 | [Odyssey Quests](https://modrinth.com/mod/odyssey-quests) | 1,377,019 | adventure, utility |
| 957 | [Artifacts crafting](https://modrinth.com/mod/artifacts-crafting) | 1,374,961 | utility |
| 958 | [Better Biome Blend](https://modrinth.com/mod/better-biome-blend) | 1,373,840 | optimization |
| 959 | [Vic's Point Blank](https://modrinth.com/mod/vics-point-blank) | 1,372,281 | adventure, equipment, mobs, worldgen |
| 960 | [Sunflower Delight](https://modrinth.com/mod/sunflower-delight) | 1,371,610 | food |
| 961 | [FootprintParticle](https://modrinth.com/mod/footprintparticle) | 1,370,525 | decoration |
| 962 | [Barbeque's Delight [Forge/NeoForge]](https://modrinth.com/mod/barbeques-delight-forge) | 1,369,121 | decoration, food, storage, utility |
| 963 | [Village Spawn Point](https://modrinth.com/mod/village-spawn-point) | 1,367,549 | adventure, game-mechanics, worldgen |
| 964 | [Better Ping Display [Forge/NeoForge]](https://modrinth.com/mod/better-ping-display) | 1,366,612 | utility |
| 965 | [Tool Trims](https://modrinth.com/mod/tool-trims) | 1,363,533 | adventure, decoration, equipment, game-mechanics |
| 966 | [Drink Beer Refill](https://modrinth.com/mod/drink-beer-refill) | 1,362,273 | food |
| 967 | [Better Villages](https://modrinth.com/mod/better-village) | 1,360,913 | adventure, decoration, mobs, worldgen |
| 968 | [Quad](https://modrinth.com/mod/quad) | 1,359,825 | game-mechanics, library, utility |
| 969 | [Figura](https://modrinth.com/mod/figura) | 1,358,713 | decoration, social, utility |
| 970 | [Dungeons+](https://modrinth.com/mod/dungeons+) | 1,356,335 | adventure, mobs, worldgen |
| 971 | [Horseman](https://modrinth.com/mod/horseman) | 1,355,926 | game-mechanics, transportation, utility |
| 972 | [Spiky Spikes](https://modrinth.com/mod/spiky-spikes) | 1,355,492 | game-mechanics, mobs, utility |
| 973 | [Kaleidoscope Doll](https://modrinth.com/mod/kaleidoscope-doll) | 1,355,432 | decoration |
| 974 | [AddurDisc](https://modrinth.com/mod/addurdisc) | 1,354,714 | decoration, utility |
| 975 | [YDM's Fennec Fox](https://modrinth.com/mod/ydms-fennec-fox) | 1,352,515 | mobs |
| 976 | [L2 Complements](https://modrinth.com/mod/l2-complements) | 1,350,020 | utility |
| 977 | [Atlas Lib](https://modrinth.com/mod/atlas-lib) | 1,346,581 | library |
| 978 | [Pig Pen Cipher](https://modrinth.com/mod/pig-pen-cipher) | 1,346,280 | utility |
| 979 | [DropConfirm](https://modrinth.com/mod/drop-confirm) | 1,346,051 | game-mechanics, utility |
| 980 | [Anvil Never Too Expensive](https://modrinth.com/mod/ante) | 1,337,994 | adventure, equipment, game-mechanics, management, utility |
| 981 | [Lios Overhauled Villages](https://modrinth.com/mod/lios-overhauled-villages) | 1,336,898 | adventure, worldgen |
| 982 | [Better Smithing Table](https://modrinth.com/mod/bettersmithingtable) | 1,335,944 | utility |
| 983 | [Aether Addon: Protect Your Moa](https://modrinth.com/mod/aether-protect-your-moa) | 1,335,313 | adventure, decoration, equipment, game-mechanics, storage, transportation |
| 984 | [Block Swap](https://modrinth.com/mod/block-swap) | 1,335,154 | utility, worldgen |
| 985 | [Armortip](https://modrinth.com/mod/armortip) | 1,332,253 | equipment, management |
| 986 | [Sneak Through Berries](https://modrinth.com/mod/sneak-through-berries) | 1,327,417 | game-mechanics |
| 987 | [ME Requester](https://modrinth.com/mod/merequester) | 1,326,471 | storage, technology, utility |
| 988 | [BaguetteLib](https://modrinth.com/mod/baguettelib) | 1,326,126 | — |
| 989 | [Ships](https://modrinth.com/mod/ships) | 1,325,636 | adventure, mobs, worldgen |
| 990 | [World Host](https://modrinth.com/mod/world-host) | 1,324,521 | social, utility |
| 991 | [Shield Expansion](https://modrinth.com/mod/shield-expansion) | 1,321,405 | adventure, utility |
| 992 | [Stylish Effects](https://modrinth.com/mod/stylish-effects) | 1,321,038 | utility |
| 993 | [Unusual End](https://modrinth.com/mod/unusual_end) | 1,320,606 | adventure, equipment, worldgen |
| 994 | [Mekanism Tools](https://modrinth.com/mod/mekanism-tools) | 1,320,551 | equipment |
| 995 | [Chunk Activity Tracker](https://modrinth.com/mod/chunk-activity-tracker) | 1,320,193 | library |
| 996 | [Maidsoul Kitchen](https://modrinth.com/mod/maidsoul-kitchen) | 1,318,488 | — |
| 997 | [Villager Transportation](https://modrinth.com/mod/villager-transportation) | 1,312,156 | adventure, mobs, transportation |
| 998 | [Savage & Ravage](https://modrinth.com/mod/savage-and-ravage) | 1,311,371 | adventure, decoration, equipment, game-mechanics, magic, mobs, worldgen |
| 999 | [Unsafe World Random Access Detector](https://modrinth.com/mod/uwrad) | 1,311,111 | utility |
| 1000 | [Does It Tick?](https://modrinth.com/mod/does-it-tick) | 1,310,617 | management, optimization, utility |

_Generated 2026-06-13 from Modrinth API. 1,000 mods. Regenerate via the curl/jq in Caveats above._
