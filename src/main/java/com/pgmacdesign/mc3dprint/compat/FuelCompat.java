package com.pgmacdesign.mc3dprint.compat;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * Version seam for the 1.21.5 fuel-system rewrite. {@code ItemStack.getBurnTime(RecipeType)}
 * gained a required {@link net.minecraft.world.level.block.entity.FuelValues} parameter
 * ({@code getBurnTime(RecipeType, FuelValues)}); the table normally comes from
 * {@code Level.fuelValues()}. When no {@link Level} is in scope (slot-validity checks) we
 * rebuild it from the registries the mod binds at recipe load — on both server and client —
 * so modded data-map fuels are still recognised.
 */
public final class FuelCompat {
    private FuelCompat() {}

    /** Free (un-boosted) furnace burn time of {@code stack}; 0 if it isn't fuel. */
    public static int burnTime(@Nullable Level level, ItemStack stack) {
        //? if >=1.21.5 {
        /*net.minecraft.world.level.block.entity.FuelValues fuels =
                level != null ? level.fuelValues() : boundFuelValues();
        return fuels == null ? 0 : stack.getBurnTime(RecipeType.SMELTING, fuels);
        *///?} else {
        return stack.getBurnTime(RecipeType.SMELTING);
        //?}
    }

    //? if >=1.21.5 {
    /*private static net.minecraft.core.RegistryAccess builtFrom;
    private static net.minecraft.world.level.block.entity.FuelValues cached;

    // Vanilla+modded fuel table from the bound registries; null until recipes sync. Identity-keyed
    // on RegistryAccess so it rebuilds on /reload or world-switch (each binds a fresh access).
    @Nullable
    private static net.minecraft.world.level.block.entity.FuelValues boundFuelValues() {
        net.minecraft.core.RegistryAccess registries =
                com.pgmacdesign.mc3dprint.fu.FuValueRegistry.boundRegistries();
        if (registries == null) {
            return null;
        }
        if (cached == null || builtFrom != registries) {
            cached = net.neoforged.neoforge.common.DataMapHooks.populateFuelValues(
                    registries, net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS);
            builtFrom = registries;
        }
        return cached;
    }
    *///?}
}
