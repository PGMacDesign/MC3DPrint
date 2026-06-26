package com.pgmacdesign.mc3dprint.client;

import com.pgmacdesign.mc3dprint.machine.RedstoneClockMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Config screen for the Redstone Clock: shows the interval and four buttons
 * (-10 / -1 / +1 / +10 s) that adjust it (clamped 1–60 server-side). The panel
 * is drawn with fills (charcoal console look) — no GUI texture needed.
 */
public class RedstoneClockScreen extends AbstractContainerScreen<RedstoneClockMenu> {
    // Charcoal tech-console palette (matches gen_printer_gui.py).
    private static final int FIELD = 0xFF10141E;
    private static final int PANEL = 0xFF1A1F2B;
    private static final int BEVEL_LIGHT = 0xFF2C3342;
    private static final int BEVEL_DARK = 0xFF0A0D14;
    private static final int ACCENT = 0xFF3FE0C0;
    private static final int LABEL = 0xFFC0C0C8;

    public RedstoneClockScreen(RedstoneClockMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 84;
    }

    @Override
    protected void init() {
        super.init();
        int cy = topPos + 44;
        // [-10] [-1]  value  [+1] [+10]
        addRenderableWidget(Button.builder(Component.literal("-10"), b -> click(RedstoneClockMenu.BUTTON_MINUS_10))
                .bounds(leftPos + 18, cy, 28, 16).build());
        addRenderableWidget(Button.builder(Component.literal("-1"), b -> click(RedstoneClockMenu.BUTTON_MINUS_1))
                .bounds(leftPos + 48, cy, 22, 16).build());
        addRenderableWidget(Button.builder(Component.literal("+1"), b -> click(RedstoneClockMenu.BUTTON_PLUS_1))
                .bounds(leftPos + 106, cy, 22, 16).build());
        addRenderableWidget(Button.builder(Component.literal("+10"), b -> click(RedstoneClockMenu.BUTTON_PLUS_10))
                .bounds(leftPos + 130, cy, 28, 16).build());
    }

    private void click(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int left = leftPos;
        int top = topPos;
        int w = imageWidth;
        int h = imageHeight;
        // panel + 1px bevel frame + cyan accent line (the console identity)
        graphics.fill(left, top, left + w, top + h, PANEL);
        graphics.fill(left, top, left + w, top + 1, BEVEL_DARK);
        graphics.fill(left, top, left + 1, top + h, BEVEL_DARK);
        graphics.fill(left, top + h - 1, left + w, top + h, BEVEL_DARK);
        graphics.fill(left + w - 1, top, left + w, top + h, BEVEL_DARK);
        graphics.fill(left + 1, top + 1, left + w - 1, top + 2, BEVEL_LIGHT);
        graphics.fill(left + 2, top + 2, left + w - 2, top + 3, ACCENT);
        // recessed value field behind the big number
        graphics.fill(left + 70, top + 40, left + 106, top + 60, FIELD);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, 6, LABEL, false);
        Component prompt = Component.translatable("gui.mc3dprint.redstone_clock.interval");
        graphics.drawString(font, prompt, (imageWidth - font.width(prompt)) / 2, 22, LABEL, false);
        // big interval value, centred in the recessed field at panel y=44
        Component value = Component.translatable("gui.mc3dprint.redstone_clock.seconds", menu.intervalSeconds());
        graphics.drawString(font, value, (imageWidth - font.width(value)) / 2, 47, ACCENT, false);
    }
}
