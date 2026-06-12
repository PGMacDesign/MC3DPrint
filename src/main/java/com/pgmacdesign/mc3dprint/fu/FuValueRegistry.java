package com.pgmacdesign.mc3dprint.fu;

import com.mojang.logging.LogUtils;
import com.pgmacdesign.mc3dprint.config.MC3DPrintConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Filament Unit values, parsed from config so pack makers own the economy.
 *
 * Entry syntax: {@code minecraft:cobblestone=1@1} (item) or
 * {@code #minecraft:logs=3@1} (tag) — FU value @ minimum winder/printer tier.
 * Explicit item entries win over tag entries.
 */
public final class FuValueRegistry {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static Map<Item, FuValue> itemValues;
    private static List<TagEntry> tagValues;

    private record TagEntry(TagKey<Item> tag, FuValue value) {}

    private FuValueRegistry() {}

    public static Optional<FuValue> valueOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        ensureLoaded();
        FuValue direct = itemValues.get(stack.getItem());
        if (direct != null) {
            return Optional.of(direct);
        }
        for (TagEntry entry : tagValues) {
            if (stack.is(entry.tag())) {
                return Optional.of(entry.value());
            }
        }
        return Optional.empty();
    }

    /** Drops the parsed cache (config reload). */
    public static synchronized void invalidate() {
        itemValues = null;
        tagValues = null;
    }

    private static synchronized void ensureLoaded() {
        if (itemValues != null) {
            return;
        }
        Map<Item, FuValue> items = new HashMap<>();
        List<TagEntry> tags = new ArrayList<>();
        for (String entry : MC3DPrintConfig.FU_VALUES.get()) {
            try {
                parseEntry(entry.trim(), items, tags);
            } catch (RuntimeException e) {
                LOGGER.warn("Skipping invalid FU value entry '{}': {}", entry, e.getMessage());
            }
        }
        itemValues = items;
        tagValues = tags;
    }

    private static void parseEntry(String entry, Map<Item, FuValue> items, List<TagEntry> tags) {
        if (entry.isEmpty() || entry.startsWith("//")) {
            return;
        }
        int eq = entry.indexOf('=');
        int at = entry.indexOf('@', eq);
        if (eq < 0 || at < 0) {
            throw new IllegalArgumentException("expected <id>=<fu>@<tier>");
        }
        String id = entry.substring(0, eq).trim();
        FuValue value = new FuValue(
                Integer.parseInt(entry.substring(eq + 1, at).trim()),
                Integer.parseInt(entry.substring(at + 1).trim()));

        if (id.startsWith("#")) {
            ResourceLocation tagId = ResourceLocation.tryParse(id.substring(1));
            if (tagId == null) {
                throw new IllegalArgumentException("bad tag id " + id);
            }
            tags.add(new TagEntry(TagKey.create(Registries.ITEM, tagId), value));
        } else {
            ResourceLocation itemId = ResourceLocation.tryParse(id);
            if (itemId == null || !ForgeRegistries.ITEMS.containsKey(itemId)) {
                throw new IllegalArgumentException("unknown item " + id);
            }
            items.put(ForgeRegistries.ITEMS.getValue(itemId), value);
        }
    }

    public static List<String> defaultEntries() {
        return List.of(
                // 1 FU @ T1 — bulk fill
                "minecraft:cobblestone=1@1", "minecraft:dirt=1@1", "minecraft:gravel=1@1",
                "minecraft:sand=1@1", "minecraft:soul_sand=1@1", "minecraft:soul_soil=1@1",
                "minecraft:clay_ball=1@1", "minecraft:netherrack=1@1",
                // 3 FU @ T1 — stone family & wood
                "minecraft:stone=3@1", "minecraft:sandstone=3@1", "minecraft:smooth_stone=3@1",
                "minecraft:stone_bricks=3@1", "minecraft:andesite=3@1", "minecraft:diorite=3@1",
                "minecraft:granite=3@1", "minecraft:calcite=3@1", "minecraft:tuff=3@1",
                "#minecraft:logs=3@1", "#minecraft:planks=3@1",
                // 5 FU @ T1 — processed building blocks
                "minecraft:glass=5@1", "minecraft:terracotta=5@1", "#minecraft:wool=5@1",
                "minecraft:nether_bricks=5@1", "minecraft:quartz_block=5@1",
                "minecraft:white_concrete=5@1", "minecraft:orange_concrete=5@1",
                "minecraft:magenta_concrete=5@1", "minecraft:light_blue_concrete=5@1",
                "minecraft:yellow_concrete=5@1", "minecraft:lime_concrete=5@1",
                "minecraft:pink_concrete=5@1", "minecraft:gray_concrete=5@1",
                "minecraft:light_gray_concrete=5@1", "minecraft:cyan_concrete=5@1",
                "minecraft:purple_concrete=5@1", "minecraft:blue_concrete=5@1",
                "minecraft:brown_concrete=5@1", "minecraft:green_concrete=5@1",
                "minecraft:red_concrete=5@1", "minecraft:black_concrete=5@1",
                // 10 FU @ T2
                "minecraft:copper_ingot=10@2", "minecraft:amethyst_shard=10@2", "minecraft:lapis_lazuli=10@2",
                // 15-20 FU @ T2 — metals (nuggets = ingot/9 rounded DOWN, lossy by design: 2026-06-11)
                "minecraft:gold_ingot=15@2", "minecraft:iron_ingot=20@2",
                "minecraft:gold_nugget=1@2", "minecraft:iron_nugget=2@2",
                // ~30 FU @ T3
                "minecraft:redstone=4@3", "minecraft:slime_ball=30@3", "minecraft:magma_cream=30@3",
                // 50 FU @ T4 — gems (diamond = emerald by design)
                "minecraft:diamond=50@4", "minecraft:emerald=50@4",
                // T5+
                "minecraft:netherite_ingot=500@5", "minecraft:ancient_debris=125@5",
                "minecraft:nether_star=1500@6",
                "minecraft:dragon_egg=2500@7",
                // storage / material blocks = their crafting contents (9×, amethyst 4×),
                // at the constituent's tier. Without these, a scanned diamond/netherite
                // block falls back to UNKNOWN_BLOCK_FU @ T1 — letting a low-tier machine
                // print high-tier blocks for pennies. Carrying the tier also makes the
                // per-block tier gate refuse them on an under-tier machine.
                "minecraft:coal_block=18@1",
                "minecraft:copper_block=90@2", "minecraft:iron_block=180@2",
                "minecraft:gold_block=135@2", "minecraft:lapis_block=90@2",
                "minecraft:amethyst_block=40@2",
                "minecraft:redstone_block=36@3", "minecraft:slime_block=270@3",
                "minecraft:diamond_block=450@4", "minecraft:emerald_block=450@4",
                "minecraft:netherite_block=4500@5");
    }
}
