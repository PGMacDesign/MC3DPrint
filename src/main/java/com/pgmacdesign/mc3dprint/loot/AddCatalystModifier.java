package com.pgmacdesign.mc3dprint.loot;

import com.pgmacdesign.mc3dprint.compat.RegistryCompat;

import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.pgmacdesign.mc3dprint.MC3DPrint;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.List;
import java.util.function.Supplier;

/**
 * "T3 resins can be found in the wild" — adds one Tier-3 Resin to matched
 * end-game loot tables. The resin list + chance + target tables live in data JSONs
 * so pack makers control placement.
 *
 * <p><b>Flavor bias</b> (catalysts-design Q15): the optional {@code flavors} list maps
 * loot-table substrings to themed resin pools — with probability {@code flavor_bias}
 * (default 0.75) a matching table draws from its themed pool (ancient city → XP,
 * bastion → Treasure, …); otherwise, and for unmapped tables, the pick stays uniform
 * over the full list, so every resin remains obtainable everywhere.
 */
public class AddCatalystModifier extends LootModifier {

    /** One themed pool: applies when the queried table id CONTAINS {@code tableContains}. */
    public record Flavor(String tableContains, List<String> resins) {
        public static final Codec<Flavor> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("table_contains").forGetter(Flavor::tableContains),
                Codec.STRING.listOf().fieldOf("resins").forGetter(Flavor::resins))
                .apply(instance, Flavor::new));
    }

    public static final Supplier<MapCodec<AddCatalystModifier>> CODEC = Suppliers.memoize(() ->
            RecordCodecBuilder.mapCodec(instance -> codecStart(instance)
                    .and(Codec.floatRange(0.0F, 1.0F).fieldOf("chance")
                            .forGetter(modifier -> modifier.chance))
                    .and(Codec.STRING.listOf().fieldOf("resins")
                            .forGetter(modifier -> modifier.resinIds))
                    .and(Flavor.CODEC.listOf().optionalFieldOf("flavors", List.of())
                            .forGetter(modifier -> modifier.flavors))
                    .and(Codec.floatRange(0.0F, 1.0F).optionalFieldOf("flavor_bias", 0.75F)
                            .forGetter(modifier -> modifier.flavorBias))
                    .apply(instance, AddCatalystModifier::new)));

    private final float chance;
    private final List<String> resinIds;
    private final List<Flavor> flavors;
    private final float flavorBias;

    // 26.1: LootModifier's ctor + codecStart carry an int priority between the
    // conditions and the modifier's own fields; the codec's ::new binds per node.
    //? if >=26.1 {
    /*public AddCatalystModifier(LootItemCondition[] conditions, int priority, float chance,
                               List<String> resinIds, List<Flavor> flavors, float flavorBias) {
        super(conditions, priority);
        this.chance = chance;
        this.resinIds = resinIds;
        this.flavors = flavors;
        this.flavorBias = flavorBias;
    }
    *///?} else {
    public AddCatalystModifier(LootItemCondition[] conditions, float chance,
                               List<String> resinIds, List<Flavor> flavors, float flavorBias) {
        super(conditions);
        this.chance = chance;
        this.resinIds = resinIds;
        this.flavors = flavors;
        this.flavorBias = flavorBias;
    }
    //?}

    /**
     * The pick, extracted pure for seeded tests: a mapped table draws from its themed
     * pool with probability {@code flavorBias}, else (and for unmapped tables) uniform
     * over the full list. First matching flavor wins — order specific entries (e.g.
     * "stronghold_library") before broad ones ("stronghold").
     */
    public static String pickResin(String tableId, net.minecraft.util.RandomSource random,
                                   List<String> resins, List<Flavor> flavors, float flavorBias) {
        if (tableId != null && !flavors.isEmpty() && random.nextFloat() < flavorBias) {
            for (Flavor flavor : flavors) {
                if (tableId.contains(flavor.tableContains()) && !flavor.resins().isEmpty()) {
                    return flavor.resins().get(random.nextInt(flavor.resins().size()));
                }
            }
        }
        return resins.get(random.nextInt(resins.size()));
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        if (resinIds.isEmpty() || context.getRandom().nextFloat() >= chance) {
            return generatedLoot;
        }
        String table = context.getQueriedLootTableId() != null
                ? context.getQueriedLootTableId().toString() : null;
        String id = pickResin(table, context.getRandom(), resinIds, flavors, flavorBias);
        Item item = RegistryCompat.item(ResourceLocation.fromNamespaceAndPath(MC3DPrint.MOD_ID, id));
        if (item == null || item == Items.AIR) {
            return generatedLoot; // typo'd / removed id — skip silently rather than crash a chest
        }
        generatedLoot.add(new ItemStack(item));
        return generatedLoot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC.get();
    }
}
