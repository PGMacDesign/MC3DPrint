package com.pgmacdesign.mc3dprint.client;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.machine.WinderBlockEntity;
import com.pgmacdesign.mc3dprint.machine.WinderMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.Objects;

public class WinderScreen extends AbstractContainerScreen<WinderMenu> {
    private static final ResourceLocation TEXTURE = Objects.requireNonNull(
            ResourceLocation.tryParse(MC3DPrint.MOD_ID + ":textures/gui/machine.png"));

    private static final int ENERGY_X = 11, ENERGY_Y = 18, ENERGY_W = 12, ENERGY_H = 50;
    private static final int FU_X = 153, FU_Y = 18, FU_W = 12, FU_H = 50;
    private static final int ARROW_X = 80, ARROW_Y = 36, ARROW_W = 22, ARROW_H = 15;

    // Dark tech-console code colors (see VISUAL-REVAMP-BRIEF). Labels are light on
    // the new charcoal panel; error message is a warm red that reads on dark.
    private static final int LABEL = 0xFFC0C0C8;
    private static final int WARN = 0xFFE57A7A;
    private static final int FILL_ENERGY = 0xFFD32F2F;
    private static final int FILL_FILAMENT = 0xFF4FC3F7;
    private static final int FILL_PROGRESS = 0xCC4FC3F7;
    private static final int SHIMMER = 0xFFBFE9FF;

    public WinderScreen(WinderMenu menu, Inventory playerInventory, Component title) {
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

        if (isHovering(ENERGY_X, ENERGY_Y, ENERGY_W, ENERGY_H, mouseX, mouseY)) {
            graphics.renderTooltip(font,
                    Component.translatable("tooltip.mc3dprint.energy", menu.energy(), menu.maxEnergy()),
                    mouseX, mouseY);
        }
        if (isHovering(FU_X, FU_Y, FU_W, FU_H, mouseX, mouseY)) {
            graphics.renderTooltip(font,
                    Component.translatable("tooltip.mc3dprint.fu", menu.spoolFu(), menu.spoolCapacity()),
                    mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int left = leftPos;
        int top = topPos;
        graphics.blit(TEXTURE, left, top, 0, 0, imageWidth, imageHeight);

        int energyPixels = (int) ((long) menu.energy() * ENERGY_H / menu.maxEnergy());
        if (energyPixels > 0) {
            graphics.fill(left + ENERGY_X, top + ENERGY_Y + ENERGY_H - energyPixels,
                    left + ENERGY_X + ENERGY_W, top + ENERGY_Y + ENERGY_H, FILL_ENERGY);
        }

        int cap = Math.max(1, menu.spoolCapacity());
        int fuPixels = (int) ((long) menu.spoolFu() * FU_H / cap);
        if (fuPixels > 0) {
            graphics.fill(left + FU_X, top + FU_Y + FU_H - fuPixels,
                    left + FU_X + FU_W, top + FU_Y + FU_H, FILL_FILAMENT);
        }

        if (menu.status() != WinderBlockEntity.STATUS_OK) {
            // wrong-tier / non-convertible input: red X over the (empty) progress channel
            drawRedX(graphics, left + ARROW_X + ARROW_W / 2 - 1, top + ARROW_Y + ARROW_H / 2 - 1, 6);
        } else {
            int progressPixels = menu.progress() * ARROW_W / menu.maxProgress();
            if (progressPixels > 0) {
                graphics.fill(left + ARROW_X, top + ARROW_Y,
                        left + ARROW_X + progressPixels, top + ARROW_Y + ARROW_H, FILL_PROGRESS);
                if (progressPixels < ARROW_W) {
                    graphics.fill(left + ARROW_X + progressPixels - 1, top + ARROW_Y,
                            left + ARROW_X + progressPixels, top + ARROW_Y + ARROW_H, SHIMMER);
                }
            }
        }
    }

    private static void drawRedX(GuiGraphics graphics, int cx, int cy, int half) {
        int red = 0xFFE53935;
        for (int t = -half; t <= half; t++) {
            graphics.fill(cx + t, cy + t, cx + t + 2, cy + t + 2, red);
            graphics.fill(cx + t, cy - t, cx + t + 2, cy - t + 2, red);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Draw the title + inventory label ourselves in light text instead of
        // super's dark-grey (invisible on the new charcoal console).
        graphics.drawString(font, title, titleLabelX, titleLabelY, LABEL, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, LABEL, false);

        int status = menu.status();
        if (status == WinderBlockEntity.STATUS_OK) {
            return;
        }
        Component message = status == WinderBlockEntity.STATUS_WRONG_TIER
                ? Component.translatable("gui.mc3dprint.winder.requires_tier", menu.requiredTier())
                : Component.translatable("gui.mc3dprint.winder.not_convertible");
        int x = (imageWidth - font.width(message)) / 2;
        graphics.drawString(font, message, x, 58, WARN, false);
    }
}
