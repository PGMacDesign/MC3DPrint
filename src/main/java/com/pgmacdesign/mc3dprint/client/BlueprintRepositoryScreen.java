package com.pgmacdesign.mc3dprint.client;

import net.minecraft.ChatFormatting;
import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.blueprint.CuratedBlueprints;
import com.pgmacdesign.mc3dprint.blueprint.repository.RepoEntry;
import com.pgmacdesign.mc3dprint.machine.repository.BlueprintRepositoryMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Library Browser (R-A): a searchable list of catalogued blueprints (left), a
 * detail pane (right), and an action bar — Deposit Disc (left), disc in/out slots
 * (centre), STL to GCODE (right). Lockstep with {@code gen_printer_gui.py:build_repository}.
 */
public class BlueprintRepositoryScreen extends AbstractContainerScreen<BlueprintRepositoryMenu> {
    private static final ResourceLocation TEXTURE = Objects.requireNonNull(
            ResourceLocation.tryParse(MC3DPrint.MOD_ID + ":textures/gui/blueprint_repository.png"));

    private static final int LIST_X = 7, LIST_W = 118, LIST_Y = 36, ROW_H = 13, ROWS = 6;
    private static final int DETAIL_X = 131, DETAIL_W = 110;
    private static final int PREVIEW_X = 135, PREVIEW_Y = 22, PREVIEW_W = 102, PREVIEW_H = 30;
    private static final int ACTION_Y = 132;

    private static final int LABEL = 0xFFC0C0C8;
    private static final int LABEL_DIM = 0xFF7D8597;
    private static final int ACCENT = 0xFF3FE0C0;
    private static final int OFFICIAL = 0xFFBFE9DC;
    private static final int SCANNED = 0xFFD8C08A;
    private static final int PRINTED = 0xFF7BE0A0;
    private static final int ROW_SEL = 0x66174C3C;
    private static final int ROW_HOVER = 0x33FFFFFF;

    // Print-status filter: 0 = all, 1 = printed only, 2 = unprinted only.
    private static final int FILTER_ALL = 0, FILTER_PRINTED = 1, FILTER_UNPRINTED = 2;

    private EditBox search;
    private Button filterButton;
    private int printFilter = FILTER_ALL;
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
        search = new EditBox(font, leftPos + 10, topPos + 19, 58, 14,
                Component.translatable("gui.mc3dprint.repository.search"));
        search.setMaxLength(48);
        search.setBordered(true);
        search.setHint(Component.translatable("gui.mc3dprint.repository.search"));
        search.setResponder(s -> scroll = 0);
        addRenderableWidget(search);

        filterButton = Button.builder(filterLabel(), b -> {
                    printFilter = (printFilter + 1) % 3;
                    scroll = 0;
                    b.setMessage(filterLabel());
                })
                .bounds(leftPos + 72, topPos + 19, 52, 14)
                .tooltip(Tooltip.create(Component.translatable("gui.mc3dprint.repository.filter_tip")))
                .build();
        addRenderableWidget(filterButton);

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

    private Component filterLabel() {
        String key = switch (printFilter) {
            case FILTER_PRINTED -> "gui.mc3dprint.repository.filter_printed";
            case FILTER_UNPRINTED -> "gui.mc3dprint.repository.filter_unprinted";
            default -> "gui.mc3dprint.repository.filter_all";
        };
        return Component.translatable(key);
    }

    /** Absolute indices into menu.entries() that pass the search query + print filter. */
    private List<Integer> visibleIndices() {
        List<RepoEntry> entries = menu.entries();
        String q = search == null ? "" : search.getValue().toLowerCase().trim();
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            RepoEntry e = entries.get(i);
            boolean matchesSearch = q.isEmpty() || e.name().toLowerCase().contains(q) || ("t" + e.tier()).equals(q);
            if (matchesSearch && matchesFilter(e)) {
                out.add(i);
            }
        }
        return out;
    }

    /** Printed/unprinted filtering applies only to official builds (scans aren't tracked). */
    private boolean matchesFilter(RepoEntry e) {
        if (printFilter == FILTER_ALL) {
            return true;
        }
        if (!e.official()) {
            return false;
        }
        boolean printed = menu.isPrinted(e.id());
        return printFilter == FILTER_PRINTED ? printed : !printed;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        renderRowTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        renderList(graphics, mouseX, mouseY);
        renderDetail(graphics);
    }

    private void renderList(GuiGraphics graphics, int mouseX, int mouseY) {
        int left = leftPos + LIST_X;
        int top = topPos + LIST_Y;
        if (menu.entries().isEmpty()) {
            graphics.drawWordWrap(font, Component.translatable("gui.mc3dprint.repository.empty"),
                    left + 4, top + 4, LIST_W - 8, LABEL_DIM);
            return;
        }
        List<Integer> visible = visibleIndices();
        int max = Math.min(ROWS, visible.size() - scroll);
        for (int i = 0; i < max; i++) {
            int absolute = visible.get(scroll + i);
            RepoEntry entry = menu.entries().get(absolute);
            int rowY = top + i * ROW_H;
            boolean hovered = mouseX >= left && mouseX < left + LIST_W && mouseY >= rowY && mouseY < rowY + ROW_H;
            if (absolute == menu.selectedIndex()) {
                graphics.fill(left, rowY, left + LIST_W, rowY + ROW_H, ROW_SEL);
            } else if (hovered) {
                graphics.fill(left, rowY, left + LIST_W, rowY + ROW_H, ROW_HOVER);
            }
            graphics.drawString(font, "T" + entry.tier(), left + 2, rowY + 3, tierColor(entry.tier()), false);
            String name = font.plainSubstrByWidth(displayName(entry), LIST_W - 44);
            graphics.drawString(font, name, left + 20, rowY + 3, LABEL, false);
            // printed marker (green dot) left of the official/scan dot, official builds only
            if (entry.official() && menu.isPrinted(entry.id())) {
                graphics.fill(left + LIST_W - 13, rowY + 4, left + LIST_W - 9, rowY + 8, PRINTED);
            }
            graphics.fill(left + LIST_W - 6, rowY + 4, left + LIST_W - 2, rowY + 8,
                    entry.official() ? OFFICIAL : SCANNED);
        }

        // library-wide printed progress footer (official builds only)
        Component progress = Component.translatable("gui.mc3dprint.repository.printed_count",
                menu.printedCount(), CuratedBlueprints.CURATED_NAMES.size());
        graphics.drawString(font, progress, left + 2, topPos + 116, PRINTED, false);
    }

    private void renderDetail(GuiGraphics graphics) {
        int x = leftPos + DETAIL_X;
        RepoEntry entry = selected();
        if (entry == null) {
            graphics.drawString(font, Component.translatable("gui.mc3dprint.repository.select_hint"),
                    leftPos + PREVIEW_X + 4, topPos + PREVIEW_Y + PREVIEW_H / 2 - 4, LABEL_DIM, false);
            return;
        }
        graphics.drawWordWrap(font, Component.literal(displayName(entry)),
                leftPos + PREVIEW_X + 4, topPos + PREVIEW_Y + 4, PREVIEW_W - 8, ACCENT);

        int y = topPos + 56;
        line(graphics, x, y, "gui.mc3dprint.repository.size",
                entry.sizeX() + "x" + entry.sizeY() + "x" + entry.sizeZ(), LABEL);
        line(graphics, x, y += 12, "gui.mc3dprint.repository.blocks", Integer.toString(entry.blockCount()), LABEL);
        line(graphics, x, y += 12, "gui.mc3dprint.repository.tier", "T" + entry.tier(), tierColor(entry.tier()));
        line(graphics, x, y += 12, "gui.mc3dprint.repository.cost", entry.cost() + " FU", LABEL);
        graphics.drawString(font, Component.translatable(entry.official()
                        ? "gui.mc3dprint.repository.official" : "gui.mc3dprint.repository.scanned"),
                x + 4, y += 12, entry.official() ? OFFICIAL : SCANNED, false);
        // print status (official builds are the only ones tracked)
        if (entry.official()) {
            boolean printed = menu.isPrinted(entry.id());
            graphics.drawString(font, Component.translatable(printed
                            ? "gui.mc3dprint.repository.printed_yes" : "gui.mc3dprint.repository.printed_no"),
                    x + 4, y + 12, printed ? PRINTED : LABEL_DIM, false);
        }
    }

    private void line(GuiGraphics graphics, int x, int y, String key, String value, int valueColor) {
        graphics.drawString(font, Component.translatable(key), x + 4, y, LABEL_DIM, false);
        graphics.drawString(font, value, x + 50, y, valueColor, false);
    }

    private void renderRowTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int left = leftPos + LIST_X;
        int top = topPos + LIST_Y;
        if (mouseX < left || mouseX >= left + LIST_W || mouseY < top || mouseY >= top + ROWS * ROW_H) {
            return;
        }
        List<Integer> visible = visibleIndices();
        int row = (mouseY - top) / ROW_H;
        if (scroll + row >= visible.size()) {
            return;
        }
        RepoEntry entry = menu.entries().get(visible.get(scroll + row));
        String status = entry.official()
                ? (menu.isPrinted(entry.id()) ? " · printed" : "")
                : " · scan";
        graphics.renderTooltip(font, List.of(
                Component.literal(displayName(entry)).getVisualOrderText(),
                Component.literal("T" + entry.tier() + " · " + entry.cost() + " FU" + status).getVisualOrderText()),
                mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, LABEL, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, LABEL, false);

        int collected = (int) menu.entries().stream().filter(RepoEntry::official).count();
        Component counter = Component.translatable("gui.mc3dprint.repository.collected",
                collected, CuratedBlueprints.CURATED_NAMES.size());
        graphics.drawString(font, counter, imageWidth - 14 - font.width(counter), titleLabelY, OFFICIAL, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int left = leftPos + LIST_X;
        int top = topPos + LIST_Y;
        if (mouseX >= left && mouseX < left + LIST_W && mouseY >= top && mouseY < top + ROWS * ROW_H) {
            List<Integer> visible = visibleIndices();
            int row = (int) ((mouseY - top) / ROW_H);
            if (scroll + row < visible.size()) {
                int absolute = visible.get(scroll + row);
                menu.setSelectedIndex(absolute);
                clickButton(BlueprintRepositoryMenu.SELECT_BASE + absolute);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int size = visibleIndices().size();
        if (size > ROWS) {
            scroll = Math.max(0, Math.min(size - ROWS, scroll - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Let the focused search box consume typing (incl. 'e') instead of closing the GUI.
        if (search != null && search.isFocused() && keyCode != 256) {
            return search.keyPressed(keyCode, scanCode, modifiers) || search.canConsumeInput();
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (search != null && search.isFocused()) {
            return search.charTyped(c, modifiers);
        }
        return super.charTyped(c, modifiers);
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

    /** Tier accent ramp matching the disc tooltip (BlueprintDiscItem.tierFormat). */
    private static int tierColor(int tier) {
        ChatFormatting format = switch (tier) {
            case 2 -> ChatFormatting.BLUE;
            case 3 -> ChatFormatting.AQUA;
            case 4 -> ChatFormatting.GREEN;
            case 5 -> ChatFormatting.GOLD;
            case 6 -> ChatFormatting.RED;
            case 7 -> ChatFormatting.LIGHT_PURPLE;
            case 8 -> ChatFormatting.DARK_PURPLE;
            default -> ChatFormatting.GRAY;
        };
        Integer color = format.getColor();
        return color == null ? LABEL : 0xFF000000 | color;
    }
}
