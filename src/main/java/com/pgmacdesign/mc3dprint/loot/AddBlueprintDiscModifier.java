package com.pgmacdesign.mc3dprint.loot;

import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.blueprint.Blueprint;
import com.pgmacdesign.mc3dprint.blueprint.BlueprintFileStore;
import com.pgmacdesign.mc3dprint.blueprint.CuratedBlueprints;
import com.pgmacdesign.mc3dprint.blueprint.repository.RepositoryIndex;
import com.pgmacdesign.mc3dprint.config.MC3DPrintConfig;
import com.pgmacdesign.mc3dprint.item.BlueprintDiscItem;
import com.pgmacdesign.mc3dprint.registry.ModItems;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * "Blueprints can be found in the wild": adds a written Blueprint Disc for a
 * curated blueprint to loot. Targets, chance and pool live in data JSONs so pack
 * makers control placement.
 *
 * <p><b>Opt-out pool:</b> when the {@code blueprints} list in the JSON is empty,
 * the modifier draws from {@link CuratedBlueprints#lootBlueprints()}: every
 * curated blueprint minus the explicit {@link CuratedBlueprints#LOOT_EXCLUDED}
 * set. That makes every build (and every future build) loot-available with no
 * per-build wiring. A non-empty list still works as an explicit override.
 *
 * <p><b>Table targeting</b> is a path-prefix match done here rather than a list of
 * {@code loot_table_id} conditions, so a modded structure's chests qualify on the
 * same footing as vanilla's and no structure is silently excluded by omission.
 *
 * <p><b>No duplicates:</b> a build already found is held out of the pool until the
 * cycle completes. The ledger lives at the same scope as the blueprint repository
 * ({@link RepositoryIndex}), because a build withheld from one player has to be
 * re-burnable from a library that player can actually reach.
 */
public class AddBlueprintDiscModifier extends LootModifier {
    public static final Supplier<MapCodec<AddBlueprintDiscModifier>> CODEC = Suppliers.memoize(() ->
            RecordCodecBuilder.mapCodec(instance -> codecStart(instance)
                    .and(Codec.floatRange(0.0F, 1.0F).fieldOf("chance")
                            .forGetter(modifier -> modifier.chance))
                    .and(Codec.STRING.listOf().fieldOf("blueprints")
                            .forGetter(modifier -> modifier.blueprintNames))
                    .and(Codec.STRING.listOf().optionalFieldOf("tables", BlueprintLootPool.DEFAULT_TABLES)
                            .forGetter(modifier -> modifier.tables))
                    .apply(instance, AddBlueprintDiscModifier::new)));

    private final float chance;
    private final List<String> blueprintNames;
    private final List<String> tables;

    // 26.1: LootModifier's ctor + codecStart carry an int priority between the
    // conditions and the modifier's own fields; the codec's ::new binds per node.
    //? if >=26.1 {
    /*public AddBlueprintDiscModifier(LootItemCondition[] conditions, int priority, float chance,
                                    List<String> blueprintNames, List<String> tables) {
        super(conditions, priority);
        this.chance = chance;
        this.blueprintNames = blueprintNames;
        this.tables = tables;
    }
    *///?} else {
    public AddBlueprintDiscModifier(LootItemCondition[] conditions, float chance,
                                    List<String> blueprintNames, List<String> tables) {
        super(conditions);
        this.chance = chance;
        this.blueprintNames = blueprintNames;
        this.tables = tables;
    }
    //?}

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        // First and cheapest: this runs for every loot table in the game, block drops
        // included, because the table filter moved out of the JSON conditions.
        if (!BlueprintLootPool.matchesTable(context.getQueriedLootTableId(), tables)) {
            return generatedLoot;
        }
        float rollChance = BlueprintLootPool.effectiveChance(
                chance, MC3DPrintConfig.BLUEPRINT_CHANCE_MULTIPLIER.get());
        if (rollChance <= 0.0F || context.getRandom().nextFloat() >= rollChance) {
            return generatedLoot;
        }

        List<String> available = BlueprintLootPool.availableFrom(blueprintNames);
        if (available.isEmpty()) {
            return generatedLoot;
        }

        MinecraftServer server = context.getLevel().getServer();
        ServerPlayer player = lootPlayer(context);
        boolean noDuplicates = MC3DPrintConfig.NO_DUPLICATE_BLUEPRINTS.get();

        List<String> pool = available;
        boolean exhausted = false;
        if (noDuplicates) {
            seedFromCatalogue(server, player, available);
            pool = BlueprintLootPool.candidates(available, RepositoryIndex.discoveredIds(server, player));
            if (pool.isEmpty()) {
                // Ledger was already full on entry (the pool shrank, or duplicates were
                // toggled off and back on). Draw from the whole pool, but do NOT clear the
                // ledger here: the blueprint can still fail to load below, and a reset on
                // that path would strand the ledger and announce a cycle that never
                // completed. Deferred to the grant, like every other write.
                exhausted = true;
                pool = available;
            }
        }

        String name = pool.get(context.getRandom().nextInt(pool.size()));
        UUID id = BlueprintLootPool.idFor(name);

        BlueprintFileStore store = BlueprintFileStore.forServer(server);
        Optional<Blueprint> blueprint = store.load(id);
        if (blueprint.isEmpty()) {
            return generatedLoot; // curated set not installed in this world; ledger untouched
        }
        ItemStack disc = new ItemStack(ModItems.BLUEPRINT_DISC.get());
        BlueprintDiscItem.writeBlueprint(disc, id, blueprint.get());
        generatedLoot.add(disc);

        // Only now that the disc is actually in the loot: recording earlier would burn a
        // build on any bail path above and put it permanently out of reach.
        //
        // Recorded whether or not duplicate suppression is on. The config gates the
        // FILTERING, not the bookkeeping, so switching it back on resumes from what has
        // already been found rather than starting the cycle over.
        RepositoryIndex.markDiscovered(server, player, id);
        // At most one reset per roll either way, so this can't loop. Retaining the build
        // just drawn covers the exhausted-on-entry path too, which previously reset with
        // nothing retained and so could repeat the same disc on the very next roll.
        if (noDuplicates && (exhausted
                || BlueprintLootPool.candidates(available,
                        RepositoryIndex.discoveredIds(server, player)).isEmpty())) {
            completeCycle(server, player, id);
        }
        if (player != null) {
            com.pgmacdesign.mc3dprint.advancement.ModCriteria.LOOT_DISC_FOUND.trigger(player);
        }
        return generatedLoot;
    }

    /**
     * One-time copy of the catalogue into the ledger, so a world that already has a
     * blueprint library doesn't re-offer builds it holds. Runs at most once per scope:
     * re-running it after a cycle reset would narrow the pool straight back down, and
     * folding the catalogue in permanently would leave a fully-catalogued library with
     * an always-empty pool.
     */
    private static void seedFromCatalogue(MinecraftServer server, @Nullable ServerPlayer player,
                                          List<String> available) {
        if (RepositoryIndex.isDiscoverySeeded(server, player)) {
            return;
        }
        Set<UUID> curated = BlueprintLootPool.idsFor(available);
        for (UUID catalogued : RepositoryIndex.cataloguedIds(server, player)) {
            if (curated.contains(catalogued)) { // ignore player scans; they're never in the pool
                RepositoryIndex.markDiscovered(server, player, catalogued);
            }
        }
        RepositoryIndex.markDiscoverySeeded(server, player);
    }

    /** Clears the ledger for this scope, retaining {@code keep}, and announces it. */
    private static void completeCycle(MinecraftServer server, @Nullable ServerPlayer player,
                                      @Nullable UUID keep) {
        RepositoryIndex.resetDiscovered(server, player, keep);
        Component message = Component.translatable("message.mc3dprint.blueprints.cycle_complete");
        if (RepositoryIndex.shared()) {
            server.getPlayerList().broadcastSystemMessage(message, false);
        } else if (player != null) {
            player.sendSystemMessage(message);
        }
    }

    /** The player this loot is being generated for, or null (command loot, hopper pulls). */
    @Nullable
    private static ServerPlayer lootPlayer(LootContext context) {
        // chest loot context: THIS_ENTITY is the player who opened it
        //? if >=1.21.5 {
        /*Object entity = context.getOptionalParameter(
                net.minecraft.world.level.storage.loot.parameters.LootContextParams.THIS_ENTITY);
        *///?} else {
        Object entity = context.getParamOrNull(
                net.minecraft.world.level.storage.loot.parameters.LootContextParams.THIS_ENTITY);
        //?}
        return entity instanceof ServerPlayer player ? player : null;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC.get();
    }
}
