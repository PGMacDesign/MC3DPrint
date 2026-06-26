package com.pgmacdesign.mc3dprint.loot;

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
 * "T3 resins can be found in the wild" — adds one random Tier-3 Resin to matched
 * end-game loot tables. The resin list + chance + target tables live in data JSONs
 * so pack makers control placement.
 *
 * <p>TODO (flavor-bias, see docs/catalysts-design.md Q15): bias the resin pick by the
 * matched loot table — e.g. Ore-Salting from ancient_city/mineshafts, Treasure from
 * treasure chests — instead of the current uniform-random pick. It's a fun, easy
 * follow-up; uniform is the intentional v1 behavior.
 */
public class AddCatalystModifier extends LootModifier {
    public static final Supplier<MapCodec<AddCatalystModifier>> CODEC = Suppliers.memoize(() ->
            RecordCodecBuilder.mapCodec(instance -> codecStart(instance)
                    .and(Codec.floatRange(0.0F, 1.0F).fieldOf("chance")
                            .forGetter(modifier -> modifier.chance))
                    .and(Codec.STRING.listOf().fieldOf("resins")
                            .forGetter(modifier -> modifier.resinIds))
                    .apply(instance, AddCatalystModifier::new)));

    private final float chance;
    private final List<String> resinIds;

    public AddCatalystModifier(LootItemCondition[] conditions, float chance, List<String> resinIds) {
        super(conditions);
        this.chance = chance;
        this.resinIds = resinIds;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        if (resinIds.isEmpty() || context.getRandom().nextFloat() >= chance) {
            return generatedLoot;
        }
        // Uniform pick for now (see class-level TODO about flavor-biasing per table).
        String id = resinIds.get(context.getRandom().nextInt(resinIds.size()));
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(MC3DPrint.MOD_ID, id));
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
