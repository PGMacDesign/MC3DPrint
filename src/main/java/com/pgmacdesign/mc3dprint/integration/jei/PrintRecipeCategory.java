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
    public int getWidth() {
        return 150;
    }

    @Override
    public int getHeight() {
        return 54;
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
        // Line 1 — FU. For a wind-only item the number is a wind YIELD, not a print cost.
        Component fuLine = entry.noPrint()
                ? Component.translatable("jei.mc3dprint.fu_wind_yield", entry.baseFu())
                : Component.translatable("jei.mc3dprint.fu_cost", entry.baseFu());
        com.pgmacdesign.mc3dprint.compat.RenderCompat.drawString(graphics, font, fuLine, 28, 4, 0xFF404040, false);
        // Line 2 — tier
        com.pgmacdesign.mc3dprint.compat.RenderCompat.drawString(graphics, font,
                Component.translatable("jei.mc3dprint.tier_required", entry.tier()), 28, 16, 0xFF707070, false);
        // Line 3 — RF for a printable item; wind-only items can't print, so say so instead.
        Component thirdLine = entry.noPrint()
                ? Component.translatable("jei.mc3dprint.cant_print")
                : Component.translatable("jei.mc3dprint.rf_cost", String.format("%,d", entry.rf()));
        com.pgmacdesign.mc3dprint.compat.RenderCompat.drawString(graphics, font, thirdLine, 28, 28, 0xFF707070, false);
        // Line 4 — machine-use status, color-coded so printability reads at a glance.
        String statusKey;
        int color;
        if (entry.noPrint()) {
            statusKey = "jei.mc3dprint.status_wind_only";
            color = 0xFFB8860B; // gold: recycle-only
        } else if (entry.trophy()) {
            statusKey = "jei.mc3dprint.status_trophy";
            color = 0xFF707070; // gray: official-disc print only
        } else if (!entry.windable()) {
            statusKey = "jei.mc3dprint.status_print_only";
            color = 0xFF1565C0; // blue: print but not wind
        } else {
            statusKey = "jei.mc3dprint.status_print_wind";
            color = 0xFF2E7D32; // green: fully usable
        }
        com.pgmacdesign.mc3dprint.compat.RenderCompat.drawString(graphics, font,
                Component.translatable(statusKey), 28, 42, color, false);
    }
}
