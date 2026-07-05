package com.pgmacdesign.mc3dprint.loot;

import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
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
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * "Blueprints can be found in the wild" — adds a written Blueprint Disc for a
 * random curated blueprint to matched loot tables. Targets and chances live in
 * data JSONs so pack makers control placement.
 *
 * <p><b>Opt-out pool:</b> when the {@code blueprints} list in the JSON is empty,
 * the modifier draws from {@link CuratedBlueprints#lootBlueprints()} — every
 * curated blueprint minus the explicit {@link CuratedBlueprints#LOOT_EXCLUDED}
 * set. That makes every build (and every future build) loot-available with no
 * per-build wiring. A non-empty list still works as an explicit override.
 */
public class AddBlueprintDiscModifier extends LootModifier {
    public static final Supplier<MapCodec<AddBlueprintDiscModifier>> CODEC = Suppliers.memoize(() ->
            RecordCodecBuilder.mapCodec(instance -> codecStart(instance)
                    .and(Codec.floatRange(0.0F, 1.0F).fieldOf("chance")
                            .forGetter(modifier -> modifier.chance))
                    .and(Codec.STRING.listOf().fieldOf("blueprints")
                            .forGetter(modifier -> modifier.blueprintNames))
                    .apply(instance, AddBlueprintDiscModifier::new)));

    private final float chance;
    private final List<String> blueprintNames;

    // 26.1: LootModifier's ctor + codecStart carry an int priority between the
    // conditions and the modifier's own fields; the codec's ::new binds per node.
    //? if >=26.1 {
    /*public AddBlueprintDiscModifier(LootItemCondition[] conditions, int priority, float chance, List<String> blueprintNames) {
        super(conditions, priority);
        this.chance = chance;
        this.blueprintNames = blueprintNames;
    }
    *///?} else {
    public AddBlueprintDiscModifier(LootItemCondition[] conditions, float chance, List<String> blueprintNames) {
        super(conditions);
        this.chance = chance;
        this.blueprintNames = blueprintNames;
    }
    //?}

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        // Empty list = the opt-out pool (all curated minus LOOT_EXCLUDED); a non-empty
        // list is an explicit override.
        List<String> pool = blueprintNames.isEmpty() ? CuratedBlueprints.lootBlueprints() : blueprintNames;
        // Never loot a build whose required mod(s) aren't installed (palette-derived gate).
        pool = pool.stream().filter(CuratedBlueprints::modsAvailable).toList();
        if (pool.isEmpty() || context.getRandom().nextFloat() >= chance) {
            return generatedLoot;
        }
        String name = pool.get(context.getRandom().nextInt(pool.size()));
        UUID id = CuratedBlueprints.uuidFor(MC3DPrint.MOD_ID, name);

        BlueprintFileStore store = BlueprintFileStore.forServer(context.getLevel().getServer());
        Optional<Blueprint> blueprint = store.load(id);
        if (blueprint.isEmpty()) {
            return generatedLoot; // curated set not installed in this world
        }
        ItemStack disc = new ItemStack(ModItems.BLUEPRINT_DISC.get());
        BlueprintDiscItem.writeBlueprint(disc, id, blueprint.get());
        generatedLoot.add(disc);
        // chest loot context: THIS_ENTITY is the player who opened it
        //? if >=1.21.5 {
        /*if (context.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.THIS_ENTITY)
                instanceof net.minecraft.server.level.ServerPlayer player) {
        *///?} else {
        if (context.getParamOrNull(net.minecraft.world.level.storage.loot.parameters.LootContextParams.THIS_ENTITY)
                instanceof net.minecraft.server.level.ServerPlayer player) {
        //?}
            com.pgmacdesign.mc3dprint.advancement.ModCriteria.LOOT_DISC_FOUND.trigger(player);
        }
        return generatedLoot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC.get();
    }
}
