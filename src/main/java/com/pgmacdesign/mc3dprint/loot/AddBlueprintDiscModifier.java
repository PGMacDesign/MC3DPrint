package com.pgmacdesign.mc3dprint.loot;

import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.blueprint.Blueprint;
import com.pgmacdesign.mc3dprint.blueprint.BlueprintFileStore;
import com.pgmacdesign.mc3dprint.blueprint.CuratedBlueprints;
import com.pgmacdesign.mc3dprint.item.BlueprintDiscItem;
import com.pgmacdesign.mc3dprint.registry.ModItems;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * "Blueprints can be found in the wild" — adds a written Blueprint Disc for a
 * random curated blueprint to matched loot tables. Targets and chances live in
 * data JSONs so pack makers control placement.
 */
public class AddBlueprintDiscModifier extends LootModifier {
    public static final Supplier<Codec<AddBlueprintDiscModifier>> CODEC = Suppliers.memoize(() ->
            RecordCodecBuilder.create(instance -> codecStart(instance)
                    .and(Codec.floatRange(0.0F, 1.0F).fieldOf("chance")
                            .forGetter(modifier -> modifier.chance))
                    .and(Codec.STRING.listOf().fieldOf("blueprints")
                            .forGetter(modifier -> modifier.blueprintNames))
                    .apply(instance, AddBlueprintDiscModifier::new)));

    private final float chance;
    private final List<String> blueprintNames;

    public AddBlueprintDiscModifier(LootItemCondition[] conditions, float chance, List<String> blueprintNames) {
        super(conditions);
        this.chance = chance;
        this.blueprintNames = blueprintNames;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        if (blueprintNames.isEmpty() || context.getRandom().nextFloat() >= chance) {
            return generatedLoot;
        }
        String name = blueprintNames.get(context.getRandom().nextInt(blueprintNames.size()));
        UUID id = CuratedBlueprints.uuidFor(MC3DPrint.MOD_ID, name);

        BlueprintFileStore store = BlueprintFileStore.forServer(context.getLevel().getServer());
        Optional<Blueprint> blueprint = store.load(id);
        if (blueprint.isEmpty()) {
            return generatedLoot; // curated set not installed in this world
        }
        ItemStack disc = new ItemStack(ModItems.BLUEPRINT_DISC.get());
        BlueprintDiscItem.writeBlueprint(disc, id, blueprint.get());
        generatedLoot.add(disc);
        return generatedLoot;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC.get();
    }
}
