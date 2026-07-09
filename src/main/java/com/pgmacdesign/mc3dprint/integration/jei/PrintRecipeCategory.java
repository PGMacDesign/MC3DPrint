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
    /** {@code rf} = RF to print one, on the lowest machine tier that can print it. */
    public record PrintEntry(ItemStack stack, int baseFu, int tier, int rf) {}

    public static final RecipeType<PrintEntry> TYPE =
            RecipeType.create(MC3DPrint.MOD_ID, "printing", PrintEntry.class);

    private final IDrawable background;
    private final IDrawable icon;

    public PrintRecipeCategory(IGuiHelper guiHelper) {
        this.background = guiHelper.createBlankDrawable(150, 44);
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
    public IDrawable getBackground() {
        return background;
    }

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
        graphics.drawString(font,
                Component.translatable("jei.mc3dprint.rf_cost", String.format("%,d", entry.rf())),
                28, 30, 0xFF707070, false);
    }
}
