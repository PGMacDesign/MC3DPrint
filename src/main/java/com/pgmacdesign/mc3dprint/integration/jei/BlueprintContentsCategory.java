package com.pgmacdesign.mc3dprint.integration.jei;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.registry.ModItems;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * "Blueprint Contents": one page per curated blueprint — the written disc plus
 * the block manifest a print will place (distinct items with counts, most
 * numerous first). Answers "what do I need spooled to print this?" from JEI,
 * away from any printer.
 */
public class BlueprintContentsCategory implements IRecipeCategory<BlueprintContentsCategory.BlueprintEntry> {

    /** {@code contents} is pre-sorted desc by count and capped at {@link #GRID_SLOTS}. */
    public record BlueprintEntry(ItemStack disc, List<ItemStack> contents,
                                 int totalBlocks, int distinctTypes, int fuTotal, int tier) {}

    public static final RecipeType<BlueprintEntry> TYPE =
            RecipeType.create(MC3DPrint.MOD_ID, "blueprint_contents", BlueprintEntry.class);

    static final int GRID_COLS = 8;
    static final int GRID_ROWS = 3;
    static final int GRID_SLOTS = GRID_COLS * GRID_ROWS;

    private final IDrawable background;
    private final IDrawable icon;

    public BlueprintContentsCategory(IGuiHelper guiHelper) {
        this.background = guiHelper.createBlankDrawable(150, 88);
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(ModItems.BLUEPRINT_DISC.get()));
    }

    @Override
    public RecipeType<BlueprintEntry> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.mc3dprint.blueprint_contents");
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
    public void setRecipe(IRecipeLayoutBuilder builder, BlueprintEntry entry, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.OUTPUT, 4, 4).addItemStack(entry.disc());
        for (int i = 0; i < entry.contents().size() && i < GRID_SLOTS; i++) {
            builder.addSlot(RecipeIngredientRole.INPUT,
                            3 + (i % GRID_COLS) * 18, 30 + (i / GRID_COLS) * 18)
                    .addItemStack(entry.contents().get(i));
        }
    }

    @Override
    public void draw(BlueprintEntry entry, IRecipeSlotsView recipeSlotsView, GuiGraphics graphics,
                     double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        graphics.drawString(font,
                Component.translatable("jei.mc3dprint.bp_stats", entry.totalBlocks(), entry.tier()),
                26, 4, 0xFF404040, false);
        graphics.drawString(font,
                Component.translatable("jei.mc3dprint.fu_cost", String.format("%,d", entry.fuTotal())),
                26, 15, 0xFF707070, false);
        if (entry.distinctTypes() > GRID_SLOTS) {
            graphics.drawString(font,
                    Component.translatable("jei.mc3dprint.bp_more", entry.distinctTypes() - GRID_SLOTS),
                    100, 15, 0xFF707070, false);
        }
    }
}
