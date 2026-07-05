package com.pgmacdesign.mc3dprint.integration.jei;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.registry.ModItems;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class PrintRecipeCategory implements IRecipeCategory<PrintRecipeCategory.PrintEntry> {
    public record PrintEntry(ItemStack stack, int baseFu, int tier) {}

    public static final RecipeType<PrintEntry> TYPE =
            RecipeType.create(MC3DPrint.MOD_ID, "printing", PrintEntry.class);

    private final IDrawable background;
    private final IDrawable icon;

    public PrintRecipeCategory(IGuiHelper guiHelper) {
        this.background = guiHelper.createBlankDrawable(150, 32);
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(ModItems.TIER1_PRINTER.get()));
    }

    @Override
    public RecipeType<PrintEntry> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.mc3dprint.printing");
    }

    @Override
    public int getWidth() {
        return 150;
    }

    @Override
    public int getHeight() {
        return 32;
    }

    // getBackground() was deprecated from JEI 19 and removed in JEI 27 (1.21.11+);
    // getWidth()/getHeight() above are the replacement and exist on every JEI we target.
    //? if <1.21.11 {
    @Override
    public IDrawable getBackground() {
        return background;
    }
    //?}

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, PrintEntry entry, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.OUTPUT, 4, 8).addItemStack(entry.stack());
    }

    @Override
    public void draw(PrintEntry entry, IRecipeSlotsView recipeSlotsView, GuiGraphics graphics,
                     double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        graphics.drawString(font,
                Component.translatable("jei.mc3dprint.fu_cost", entry.baseFu()), 28, 6, 0xFF404040, false);
        graphics.drawString(font,
                Component.translatable("jei.mc3dprint.tier_required", entry.tier()), 28, 18, 0xFF707070, false);
    }
}
