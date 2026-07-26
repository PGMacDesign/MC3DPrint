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
    /**
     * {@code rf} = RF to print one, on the lowest machine tier that can print it.
     * The machine-use flags decide how the card reads: {@code noPrint} items (on
     * {@code #no_print}) are wind-only, so their FU is a wind yield and they show no RF;
     * {@code trophy} items ({@code #print_restricted}) print only from official discs;
     * {@code windable} is false for winder-blacklisted (print-only) items.
     */
    public record PrintEntry(ItemStack stack, int baseFu, int tier, int rf,
                             boolean noPrint, boolean trophy, boolean windable) {}

    public static final RecipeType<PrintEntry> TYPE =
            RecipeType.create(MC3DPrint.MOD_ID, "printing", PrintEntry.class);

    private final IDrawable background;
    private final IDrawable icon;

    public PrintRecipeCategory(IGuiHelper guiHelper) {
        this.background = guiHelper.createBlankDrawable(150, 54);
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
        // Line 1 — FU. For a wind-only item the number is a wind YIELD, not a print cost.
        Component fuLine = entry.noPrint()
                ? Component.translatable("jei.mc3dprint.fu_wind_yield", entry.baseFu())
                : Component.translatable("jei.mc3dprint.fu_cost", entry.baseFu());
        graphics.drawString(font, fuLine, 28, 4, 0xFF404040, false);
        // Line 2 — tier
        graphics.drawString(font,
                Component.translatable("jei.mc3dprint.tier_required", entry.tier()), 28, 16, 0xFF707070, false);
        // Line 3 — RF cost, printable items only. A wind-only item has no RF cost, and the status
        // line below already reads "No print", so a separate "Can't print" here just says it twice.
        // Skip the row and let the status line take it, so wind-only cards read as three tight lines.
        int statusY = 42;
        if (entry.noPrint()) {
            statusY = 28;
        } else {
            graphics.drawString(font,
                    Component.translatable("jei.mc3dprint.rf_cost", String.format("%,d", entry.rf())),
                    28, 28, 0xFF707070, false);
        }
        // Status line — machine-use. Print-ability and wind-ability are shown as INDEPENDENT
        // parts ("Prints/Trophy/No print · Winds/No wind") so a trophy item that is also winder-
        // blacklisted can't collapse into a misleading "windable trophy", and the compact form fits
        // the 150px card. Color keys off the most restrictive axis.
        String printKey = entry.noPrint() ? "jei.mc3dprint.p_noprint"
                : entry.trophy() ? "jei.mc3dprint.p_trophy" : "jei.mc3dprint.p_print";
        String windKey = entry.windable() ? "jei.mc3dprint.w_wind" : "jei.mc3dprint.w_nowind";
        int color = entry.noPrint() ? 0xFFB8860B          // gold: can't print
                : !entry.windable() ? 0xFF1565C0           // blue: can't wind
                : entry.trophy() ? 0xFF707070              // gray: official-disc print only
                : 0xFF2E7D32;                              // green: fully usable
        graphics.drawString(font, Component.translatable("jei.mc3dprint.mstatus",
                Component.translatable(printKey), Component.translatable(windKey)), 28, statusY, color, false);
    }
}
