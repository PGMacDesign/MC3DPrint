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
        registration.addRecipeCategories(
                new PrintRecipeCategory(registration.getJeiHelpers().getGuiHelper()),
                new BlueprintContentsCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        List<PrintRecipeCategory.PrintEntry> entries = new ArrayList<>();
        ForgeRegistries.ITEMS.forEach(item -> {
            ItemStack stack = new ItemStack(item);
            FuValueRegistry.valueOf(stack).ifPresent(value -> {
                // RF on the lowest machine that can print this material tier
                var machine = com.pgmacdesign.mc3dprint.machine.MachineTier.byNumber(value.tier());
                int rf = com.pgmacdesign.mc3dprint.config.MC3DPrintConfig.itemRfPerTick(machine)
                        * com.pgmacdesign.mc3dprint.config.MC3DPrintConfig.itemPrintTicks(machine);
                // Machine-use flags mirror the printer/winder gates so the card reads honestly.
                boolean noPrint = stack.is(com.pgmacdesign.mc3dprint.registry.ModItemTags.NO_PRINT);
                boolean trophy = !noPrint
                        && stack.is(com.pgmacdesign.mc3dprint.registry.ModItemTags.PRINT_RESTRICTED);
                boolean windable = !com.pgmacdesign.mc3dprint.registry.ModItemTags.isWinderBlacklisted(stack);
                entries.add(new PrintRecipeCategory.PrintEntry(
                        stack, value.fu(), value.tier(), rf, noPrint, trophy, windable));
            });
        });
        registration.addRecipes(PrintRecipeCategory.TYPE, entries);
        registration.addRecipes(BlueprintContentsCategory.TYPE, blueprintContents());
    }

    /** One Blueprint-Contents page per curated build whose required mods are present. */
    private static List<BlueprintContentsCategory.BlueprintEntry> blueprintContents() {
        List<BlueprintContentsCategory.BlueprintEntry> pages = new ArrayList<>();
        for (String name : com.pgmacdesign.mc3dprint.blueprint.CuratedBlueprints.CURATED_NAMES) {
            java.util.Set<String> mods = com.pgmacdesign.mc3dprint.blueprint.CuratedBlueprints.requiredMods(name);
            if (!mods.stream().allMatch(id -> ModList.get().isLoaded(id))) {
                continue;
            }
            com.pgmacdesign.mc3dprint.blueprint.CuratedBlueprints.loadBundled(name).ifPresent(blueprint -> {
                // count blocks per palette entry, then collapse to per-item counts
                int[] counts = new int[blueprint.palette().size()];
                blueprint.forEachBlock((local, paletteIndex) -> counts[paletteIndex]++);
                java.util.Map<net.minecraft.world.item.Item, Integer> byItem = new java.util.LinkedHashMap<>();
                int total = 0;
                for (int i = 0; i < counts.length; i++) {
                    if (counts[i] == 0) {
                        continue;
                    }
                    var state = blueprint.palette().get(i);
                    if (state.isAir()) {
                        continue;
                    }
                    total += counts[i];
                    ResourceLocation blockId = ResourceLocation.tryParse(state.blockId());
                    if (blockId == null || !ForgeRegistries.BLOCKS.containsKey(blockId)) {
                        continue;
                    }
                    var block = ForgeRegistries.BLOCKS.getValue(blockId);
                    var item = block == null ? net.minecraft.world.item.Items.AIR : block.asItem();
                    if (item != net.minecraft.world.item.Items.AIR) {
                        byItem.merge(item, counts[i], Integer::sum);
                    }
                }
                List<ItemStack> contents = byItem.entrySet().stream()
                        .sorted(java.util.Map.Entry.<net.minecraft.world.item.Item, Integer>comparingByValue().reversed())
                        .limit(BlueprintContentsCategory.GRID_SLOTS)
                        .map(e -> new ItemStack(e.getKey(), Math.min(e.getValue(), 999)))
                        .toList();

                ItemStack disc = new ItemStack(ModItems.BLUEPRINT_DISC.get());
                com.pgmacdesign.mc3dprint.item.BlueprintDiscItem.writeBlueprint(disc,
                        com.pgmacdesign.mc3dprint.blueprint.CuratedBlueprints.uuidFor(MC3DPrint.MOD_ID, name),
                        blueprint);
                pages.add(new BlueprintContentsCategory.BlueprintEntry(disc, contents, total,
                        byItem.size(),
                        com.pgmacdesign.mc3dprint.item.BlueprintDiscItem.getPrintCost(disc),
                        com.pgmacdesign.mc3dprint.item.BlueprintDiscItem.getTier(disc)));
            });
        }
        return pages;
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
        // Blueprint Contents pages hang off the machines that print them and the
        // Repository that stores them
        ModItems.PRINTERS.forEach(printer ->
                registration.addRecipeCatalyst(new ItemStack(printer.get()), BlueprintContentsCategory.TYPE));
        ModItems.FABRICATORS.forEach(fabricator ->
                registration.addRecipeCatalyst(new ItemStack(fabricator.get()), BlueprintContentsCategory.TYPE));
        registration.addRecipeCatalyst(new ItemStack(ModItems.BLUEPRINT_REPOSITORY.get()),
                BlueprintContentsCategory.TYPE);
    }
}
