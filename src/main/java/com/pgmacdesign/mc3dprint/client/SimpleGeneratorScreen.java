package com.pgmacdesign.mc3dprint.client;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.compat.RenderCompat;
import com.pgmacdesign.mc3dprint.machine.SimpleGeneratorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.Objects;

public class SimpleGeneratorScreen extends AbstractContainerScreen<SimpleGeneratorMenu> {
    private static final ResourceLocation TEXTURE = Objects.requireNonNull(
            ResourceLocation.tryParse(MC3DPrint.MOD_ID + ":textures/gui/simple_generator.png"));

    // Lockstep with SimpleGeneratorMenu slots + gen_simple_generator_gui.py wells/channels.
    private static final int ENERGY_X = 152, ENERGY_Y = 18, ENERGY_W = 14, ENERGY_H = 54;
    private static final int FLAME_X = 81, FLAME_Y = 36, FLAME_W = 14, FLAME_H = 14;

    private static final int LABEL = 0xFFC0C0C8;
    private static final int FILL_ENERGY = 0xFF4FC3F7;   // cyan, matches winder energy/teal family
    private static final int FILL_FLAME = 0xFFFF9D3F;    // warm orange ember
    private static final int TICKS_PER_SECOND = 20;

    public SimpleGeneratorScreen(SimpleGeneratorMenu menu, Inventory playerInventory, Component title) {
        // 26.1 made imageWidth/imageHeight final; dimensions go through the 5-arg super.
        //? if >=26.1 {
        /*super(menu, playerInventory, title, 176, 166);
        *///?} else {
        super(menu, playerInventory, title);
        //?}
        //? if <26.1 {
        this.imageWidth = 176;
        //?}
        //? if <26.1 {
        this.imageHeight = 166;
        //?}
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    //? if >=26.1 {
    /*public void extractRenderState(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        extractTooltip(graphics, mouseX, mouseY);
    *///?} else {
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    //?}

        if (isHovering(ENERGY_X, ENERGY_Y, ENERGY_W, ENERGY_H, mouseX, mouseY)) {
            RenderCompat.tooltip(graphics, font,
                    Component.translatable("tooltip.mc3dprint.energy", menu.energy(), menu.maxEnergy()),
                    mouseX, mouseY);
        }
        if (isHovering(FLAME_X, FLAME_Y, FLAME_W, FLAME_H, mouseX, mouseY)) {
            RenderCompat.tooltip(graphics, font,
                    Component.translatable("tooltip.mc3dprint.generator_fuel_left",
                            menu.burnRemaining() / TICKS_PER_SECOND),
                    mouseX, mouseY);
        }
    }

    @Override
    //? if >=26.1 {
    /*public void extractBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
    *///?} else {
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
    //?}
        int left = leftPos;
        int top = topPos;
        RenderCompat.blit(graphics, TEXTURE, left, top, 0, 0, imageWidth, imageHeight);

        // RF bar, filled bottom-up
        int energyPixels = (int) ((long) menu.energy() * ENERGY_H / menu.maxEnergy());
        if (energyPixels > 0) {
            graphics.fill(left + ENERGY_X, top + ENERGY_Y + ENERGY_H - energyPixels,
                    left + ENERGY_X + ENERGY_W, top + ENERGY_Y + ENERGY_H, FILL_ENERGY);
        }

        // flame, filled bottom-up by remaining/total burn
        int burnTotal = menu.burnTotal();
        if (burnTotal > 0) {
            int flamePixels = (int) ((long) menu.burnRemaining() * FLAME_H / burnTotal);
            if (flamePixels > 0) {
                graphics.fill(left + FLAME_X, top + FLAME_Y + FLAME_H - flamePixels,
                        left + FLAME_X + FLAME_W, top + FLAME_Y + FLAME_H, FILL_FLAME);
            }
        }
    }

    @Override
    //? if >=26.1 {
    /*protected void extractLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    *///?} else {
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    //?}
        RenderCompat.drawString(graphics, font, title, titleLabelX, titleLabelY, LABEL, false);
        RenderCompat.drawString(graphics, font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, LABEL, false);

        Component gen = Component.translatable("gui.mc3dprint.generator.rate", menu.genRate());
        RenderCompat.drawString(graphics, font, gen, 8, 20, LABEL, false);
        Component stored = Component.translatable("gui.mc3dprint.generator.stored", menu.energy(), menu.maxEnergy());
        RenderCompat.drawString(graphics, font, stored, 8, 32, LABEL, false);
    }
}
