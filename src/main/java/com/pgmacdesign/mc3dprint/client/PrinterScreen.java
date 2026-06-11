package com.pgmacdesign.mc3dprint.client;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.machine.PrinterBlockEntity;
import com.pgmacdesign.mc3dprint.machine.PrinterMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class PrinterScreen extends AbstractContainerScreen<PrinterMenu> {
    private static final ResourceLocation TEXTURE = java.util.Objects.requireNonNull(
            ResourceLocation.tryParse(MC3DPrint.MOD_ID + ":textures/gui/machine.png"));

    // Energy bar geometry (must match the frame drawn in the texture)
    private static final int ENERGY_X = 11;
    private static final int ENERGY_Y = 18;
    private static final int ENERGY_WIDTH = 12;
    private static final int ENERGY_HEIGHT = 50;

    // Filament bar geometry
    private static final int FU_X = 153;
    private static final int FU_Y = 18;
    private static final int FU_WIDTH = 12;
    private static final int FU_HEIGHT = 50;

    // Progress arrow geometry
    private static final int ARROW_X = 80;
    private static final int ARROW_Y = 36;
    private static final int ARROW_WIDTH = 22;
    private static final int ARROW_HEIGHT = 15;

    public PrinterScreen(PrinterMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);

        if (isHovering(ENERGY_X, ENERGY_Y, ENERGY_WIDTH, ENERGY_HEIGHT, mouseX, mouseY)) {
            graphics.renderTooltip(font,
                    Component.translatable("tooltip.mc3dprint.energy", menu.energy(), menu.maxEnergy()),
                    mouseX, mouseY);
        }
        if (isHovering(FU_X, FU_Y, FU_WIDTH, FU_HEIGHT, mouseX, mouseY)) {
            graphics.renderTooltip(font,
                    Component.translatable("tooltip.mc3dprint.fu", menu.fu(), menu.fuCapacity()),
                    mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int left = leftPos;
        int top = topPos;
        graphics.blit(TEXTURE, left, top, 0, 0, imageWidth, imageHeight);

        // Energy fill, bottom-up
        int energyPixels = (int) ((long) menu.energy() * ENERGY_HEIGHT / menu.maxEnergy());
        if (energyPixels > 0) {
            graphics.fill(left + ENERGY_X, top + ENERGY_Y + ENERGY_HEIGHT - energyPixels,
                    left + ENERGY_X + ENERGY_WIDTH, top + ENERGY_Y + ENERGY_HEIGHT,
                    0xFFD32F2F);
        }

        // Progress fill, left-to-right
        int progressPixels = menu.progress() * ARROW_WIDTH / menu.maxProgress();
        if (progressPixels > 0) {
            graphics.fill(left + ARROW_X, top + ARROW_Y,
                    left + ARROW_X + progressPixels, top + ARROW_Y + ARROW_HEIGHT,
                    0xCC4FC3F7);
        }

        // Filament fill, bottom-up
        int cap = Math.max(1, menu.fuCapacity());
        int fuPixels = (int) ((long) menu.fu() * FU_HEIGHT / cap);
        if (fuPixels > 0) {
            graphics.fill(left + FU_X, top + FU_Y + FU_HEIGHT - fuPixels,
                    left + FU_X + FU_WIDTH, top + FU_Y + FU_HEIGHT, 0xFF4FC3F7);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        Component status = switch (menu.state()) {
            case IDLE -> Component.translatable("gui.mc3dprint.state.idle");
            case PRINTING -> Component.translatable("gui.mc3dprint.state.printing");
            case PAUSED_NO_POWER -> Component.translatable("gui.mc3dprint.state.paused_no_power");
            case PAUSED_OUTPUT_FULL -> Component.translatable("gui.mc3dprint.state.paused_output_full");
            case PAUSED_OBSTRUCTED -> Component.translatable("gui.mc3dprint.state.paused_obstructed");
            case ZONE_CONFLICT -> Component.translatable("gui.mc3dprint.state.zone_conflict");
            case PAUSED_NO_FILAMENT -> Component.translatable("gui.mc3dprint.state.paused_no_filament");
            case NOT_PRINTABLE -> Component.translatable("gui.mc3dprint.state.not_printable");
        };
        int color = menu.state() == PrinterBlockEntity.State.PRINTING ? 0x2E7D32
                : menu.state() == PrinterBlockEntity.State.IDLE ? 0x404040 : 0xB71C1C;
        graphics.drawString(font, status, 80, 60, color, false);
        int cost = menu.templateCost();
        if (cost > 0) {
            graphics.drawString(font, Component.translatable("gui.mc3dprint.cost", cost), 36, 60, 0x404040, false);
        }
    }
}
