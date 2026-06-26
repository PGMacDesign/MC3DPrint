package com.pgmacdesign.mc3dprint.registry;

import com.mojang.serialization.Codec;
import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.loot.AddBlueprintDiscModifier;
import com.pgmacdesign.mc3dprint.loot.AddCatalystModifier;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModLootModifiers {
    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> LOOT_MODIFIERS =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, MC3DPrint.MOD_ID);

    public static final RegistryObject<Codec<AddBlueprintDiscModifier>> ADD_BLUEPRINT_DISC =
            LOOT_MODIFIERS.register("add_blueprint_disc", AddBlueprintDiscModifier.CODEC);

    public static final RegistryObject<Codec<AddCatalystModifier>> ADD_CATALYST =
            LOOT_MODIFIERS.register("add_catalyst", AddCatalystModifier.CODEC);

    private ModLootModifiers() {}
}
