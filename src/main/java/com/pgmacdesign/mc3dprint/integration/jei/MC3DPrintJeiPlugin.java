package com.pgmacdesign.mc3dprint.integration.jei;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.fu.FuValueRegistry;
import com.pgmacdesign.mc3dprint.machine.multiblock.MultiblockPattern;
import com.pgmacdesign.mc3dprint.registry.ModCreativeTabs;
import com.pgmacdesign.mc3dprint.registry.ModItems;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * "3D Printer Recipes" category: every item with an FU value, its print cost,
 * and the required machine tier. Non-negotiable per the design doc.
 */
@JeiPlugin
public class MC3DPrintJeiPlugin implements IModPlugin {
    public static final ResourceLocation PLUGIN_ID = Objects.requireNonNull(
            ResourceLocation.tryParse(MC3DPrint.MOD_ID + ":jei_plugin"));

    @Override
    public ResourceLocation getPluginUid() {
        return PLUGIN_ID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new PrintRecipeCategory(
                registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        List<PrintRecipeCategory.PrintEntry> entries = new ArrayList<>();
        ForgeRegistries.ITEMS.forEach(item -> {
            ItemStack stack = new ItemStack(item);
            FuValueRegistry.valueOf(stack).ifPresent(value ->
                    entries.add(new PrintRecipeCategory.PrintEntry(stack, value.fu(), value.tier())));
        });
        registration.addRecipes(PrintRecipeCategory.TYPE, entries);
    }

    /** Mirror the creative-tab gating: hide T8 content without DE, converter without AE2. */
    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        java.util.List<ItemStack> hidden = new ArrayList<>();
        if (!ModList.get().isLoaded(MultiblockPattern.DRACONIC_MOD_ID)) {
            hidden.add(new ItemStack(ModItems.FABRICATORS.get(3).get()));
            hidden.add(new ItemStack(ModItems.SPOOLS.get(7).get()));
        }
        if (!ModList.get().isLoaded(ModCreativeTabs.AE2_MOD_ID)) {
            hidden.add(new ItemStack(ModItems.FILAMENT_CONVERTER.get()));
        }
        if (!hidden.isEmpty()) {
            runtime.getIngredientManager().removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, hidden);
        }
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        ModItems.PRINTERS.forEach(printer ->
                registration.addRecipeCatalyst(new ItemStack(printer.get()), PrintRecipeCategory.TYPE));
        ModItems.FABRICATORS.forEach(fabricator ->
                registration.addRecipeCatalyst(new ItemStack(fabricator.get()), PrintRecipeCategory.TYPE));
        // the Filament Winder accepts the same items — browsing it shows every
        // convertible material and the spool tier each one needs
        registration.addRecipeCatalyst(new ItemStack(ModItems.FILAMENT_WINDER.get()), PrintRecipeCategory.TYPE);
    }
}
