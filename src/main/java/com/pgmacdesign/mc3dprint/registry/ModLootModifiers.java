package com.pgmacdesign.mc3dprint.registry;

import com.mojang.serialization.MapCodec;
import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.loot.AddBlueprintDiscModifier;
import com.pgmacdesign.mc3dprint.loot.AddCatalystModifier;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class ModLootModifiers {
    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> LOOT_MODIFIERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, MC3DPrint.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<AddBlueprintDiscModifier>> ADD_BLUEPRINT_DISC =
            LOOT_MODIFIERS.register("add_blueprint_disc", AddBlueprintDiscModifier.CODEC);

    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<AddCatalystModifier>> ADD_CATALYST =
            LOOT_MODIFIERS.register("add_catalyst", AddCatalystModifier.CODEC);

    private ModLootModifiers() {}
}
