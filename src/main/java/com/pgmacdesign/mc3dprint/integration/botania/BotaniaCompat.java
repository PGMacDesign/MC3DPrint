package com.pgmacdesign.mc3dprint.integration.botania;

import com.pgmacdesign.mc3dprint.fu.FuValueRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * Botania ({@code botania}) FU values. Registered ONLY when Botania is loaded; stored by
 * {@link ResourceLocation}, so a vanilla-only install never sees them.
 *
 * <p><b>The central call is the mana abundance cap.</b> Mana is passively farmable (endoflame /
 * hydroangeas / gourmaryllis generating flowers turn cheap fuel into mana), so every Mana-Pool
 * ({@code botania:mana_infusion}) output is effectively farmable. They are pinned in the T3
 * obsidian/glowstone band — in particular {@code mana_diamond} stays T3, NOT real diamond's T5,
 * so a mana farm can't launder fuel into something that out-prints diamond.
 *
 * <p><b>Gate escalation tracks the tech tree:</b> Mana Pool (farmable, T3) → Alfheim elven trade
 * (elementium / dragonstone / pixie_dust, T4) → Terrestrial Agglomeration Plate (terrasteel,
 * 500k mana, T5) → Gaia Guardian boss (life_essence, T6). Only {@code mana_infusion},
 * {@code elven_trade}, and {@code terra_plate} are custom recipe types the valuator can't read;
 * everything else (blocks ×9, nuggets ÷9, tools/armor, gaia_ingot via plain crafting) DERIVES.
 *
 * <p>The gem ITEM is {@code botania:quartz_mana} ({@code botania:mana_quartz} is the decorative
 * block — do not value it). {@code gaia_ingot} is intentionally not pinned: it is
 * {@code minecraft:crafting} from terrasteel + 4×life_essence, so it derives. (If
 * {@code life_essence} turns out non-windable in-game, pin {@code gaia_ingot} ~130/T6 instead.)
 */
public final class BotaniaCompat {
    private static final String BOT = "botania";

    private BotaniaCompat() {}

    public static void onCommonSetup(FMLCommonSetupEvent event) {
        if (!ModList.get().isLoaded(BOT)) {
            return;
        }
        event.enqueueWork(() -> {
            // T3 — Mana Pool leaves; mana is farmable, so capped in the obsidian/glowstone band
            register("manasteel_ingot", 12, 3);
            register("mana_pearl", 14, 3);
            register("mana_diamond", 16, 3);   // NOT T5 — caps the diamond-launder vector
            register("quartz_mana", 10, 3);    // the gem item (mana_quartz is the block)
            register("mana_powder", 8, 3);
            register("mana_string", 8, 3);
            // T4 — Alfheim elven-trade gate (requires reaching Alfheim)
            register("elementium_ingot", 30, 4);
            register("dragonstone", 30, 4);
            register("pixie_dust", 35, 4);
            // T5 — Terrestrial Agglomeration Plate (manasteel + mana pearl + mana diamond + 500k mana)
            register("terrasteel_ingot", 60, 5);
            // T6 — Gaia Guardian boss drop; gaia_ingot derives from this + terrasteel
            register("life_essence", 130, 6);
        });
    }

    private static void register(String path, int fu, int tier) {
        ResourceLocation id = ResourceLocation.tryParse(BOT + ":" + path);
        if (id != null) {
            FuValueRegistry.registerApiItemValue(id, fu, tier);
        }
    }
}
