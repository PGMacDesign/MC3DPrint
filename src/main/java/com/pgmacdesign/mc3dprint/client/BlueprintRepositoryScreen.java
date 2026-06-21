package com.pgmacdesign.mc3dprint.client;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.blueprint.repository.RepoEntry;
import com.pgmacdesign.mc3dprint.machine.repository.BlueprintRepositoryMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;

/**
 * Library Browser (R-A): a list of catalogued blueprints (left), a detail pane
 * (right), and an action bar — Deposit Disc (left), the disc in/out slots
 * (centre), STL to GCODE (right). Only the hotbar is shown to keep the panel
 * compact. Lockstep with {@code gen_printer_gui.py:build_repository}.
 */
public class BlueprintRepositoryScreen extends AbstractContainerScreen<BlueprintRepositoryMenu> {
    private static final ResourceLocation TEXTURE = Objects.requireNonNull(
            ResourceLocation.tryParse(MC3DPrint.MOD_ID + ":textures/gui/blueprint_repository.png"));

    private static final int LIST_X = 7, LIST_Y = 18, LIST_W = 118, ROW_H = 13, ROWS = 8;
    private static final int DETAIL_X = 131, DETAIL_Y = 18, DETAIL_W = 110;
    private static final int PREVIEW_X = 135, PREVIEW_Y = 22, PREVIEW_W = 102, PREVIEW_H = 30;
    private static final int ACTION_Y = 132;

    private static final int LABEL = 0xFFC0C0C8;
    private static final int LABEL_DIM = 0xFF7D8597;
    private static final int ACCENT = 0xFF3FE0C0;
    private static final int OFFICIAL = 0xFFBFE9DC;
    private static final int SCANNED = 0xFFD8C08A;
    private static final int ROW_SEL = 0x66174C3C;
    private static final int ROW_HOVER = 0x33FFFFFF;

    private int scroll;

    public BlueprintRepositoryScreen(BlueprintRepositoryMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 248;
        this.imageHeight = 186;
        this.inventoryLabelX = 43;
        this.inventoryLabelY = 154;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                        Component.translatable("gui.mc3dprint.repository.deposit"),
                        b -> clickButton(BlueprintRepositoryMenu.BUTTON_DEPOSIT))
                .bounds(leftPos + 8, topPos + ACTION_Y, 84, 18).build());
        addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                        Component.translatable("gui.mc3dprint.repository.burn"),
                        b -> clickButton(BlueprintRepositoryMenu.BUTTON_BURN))
                .bounds(leftPos + 156, topPos + ACTION_Y, 84, 18).build());
    }

    private void clickButton(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        renderList(graphics, mouseX, mouseY);
        renderDetail(graphics);
    }

    private void renderList(GuiGraphics graphics, int mouseX, int mouseY) {
        List<RepoEntry> entries = menu.entries();
        int left = leftPos + LIST_X;
        int top = topPos + LIST_Y;
        if (entries.isEmpty()) {
            graphics.drawWordWrap(font, Component.translatable("gui.mc3dprint.repository.empty"),
                    left + 4, top + 6, LIST_W - 8, LABEL_DIM);
            return;
        }
        int max = Math.min(ROWS, entries.size() - scroll);
        for (int i = 0; i < max; i++) {
            int index = scroll + i;
            RepoEntry entry = entries.get(index);
            int rowY = top + i * ROW_H;
            boolean hovered = mouseX >= left && mouseX < left + LIST_W && mouseY >= rowY && mouseY < rowY + ROW_H;
            if (index == menu.selectedIndex()) {
                graphics.fill(left, rowY, left + LIST_W, rowY + ROW_H, ROW_SEL);
            } else if (hovered) {
                graphics.fill(left, rowY, left + LIST_W, rowY + ROW_H, ROW_HOVER);
            }
            graphics.drawString(font, "T" + entry.tier(), left + 2, rowY + 3, ACCENT, false);
            String name = font.plainSubstrByWidth(displayName(entry), LIST_W - 38);
            graphics.drawString(font, name, left + 20, rowY + 3, LABEL, false);
            graphics.fill(left + LIST_W - 6, rowY + 4, left + LIST_W - 2, rowY + 8,
                    entry.official() ? OFFICIAL : SCANNED);
        }
    }

    private void renderDetail(GuiGraphics graphics) {
        int x = leftPos + DETAIL_X;
        RepoEntry entry = selected();
        if (entry == null) {
            graphics.drawString(font, Component.translatable("gui.mc3dprint.repository.select_hint"),
                    leftPos + PREVIEW_X + 4, topPos + PREVIEW_Y + PREVIEW_H / 2 - 4, LABEL_DIM, false);
            return;
        }
        // Full name, word-wrapped inside the preview box (no more truncation).
        graphics.drawWordWrap(font, Component.literal(displayName(entry)),
                leftPos + PREVIEW_X + 4, topPos + PREVIEW_Y + 4, PREVIEW_W - 8, ACCENT);

        int y = topPos + 58;
        line(graphics, x, y, "gui.mc3dprint.repository.size",
                entry.sizeX() + "x" + entry.sizeY() + "x" + entry.sizeZ());
        line(graphics, x, y += 13, "gui.mc3dprint.repository.blocks", Integer.toString(entry.blockCount()));
        line(graphics, x, y += 13, "gui.mc3dprint.repository.tier", "T" + entry.tier());
        line(graphics, x, y += 13, "gui.mc3dprint.repository.cost", entry.cost() + " FU");
        graphics.drawString(font, Component.translatable(entry.official()
                        ? "gui.mc3dprint.repository.official" : "gui.mc3dprint.repository.scanned"),
                x + 4, y + 13, entry.official() ? OFFICIAL : SCANNED, false);
    }

    private void line(GuiGraphics graphics, int x, int y, String key, String value) {
        graphics.drawString(font, Component.translatable(key), x + 4, y, LABEL_DIM, false);
        graphics.drawString(font, value, x + 50, y, LABEL, false);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, LABEL, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, LABEL, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        List<RepoEntry> entries = menu.entries();
        int left = leftPos + LIST_X;
        int top = topPos + LIST_Y;
        if (mouseX >= left && mouseX < left + LIST_W && mouseY >= top && mouseY < top + ROWS * ROW_H) {
            int i = (int) ((mouseY - top) / ROW_H);
            int index = scroll + i;
            if (index >= 0 && index < entries.size()) {
                menu.setSelectedIndex(index);
                clickButton(BlueprintRepositoryMenu.SELECT_BASE + index);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int size = menu.entries().size();
        if (size > ROWS) {
            scroll = Math.max(0, Math.min(size - ROWS, scroll - (int) Math.signum(delta)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Nullable
    private RepoEntry selected() {
        int i = menu.selectedIndex();
        List<RepoEntry> entries = menu.entries();
        return i >= 0 && i < entries.size() ? entries.get(i) : null;
    }

    private static String displayName(RepoEntry entry) {
        return entry.name() == null || entry.name().isEmpty() ? "?" : entry.name();
    }
}
