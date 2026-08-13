package com.pgmacdesign.mc3dprint.client;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.compat.RenderCompat;
import com.pgmacdesign.mc3dprint.blueprint.CuratedBlueprints;
import com.pgmacdesign.mc3dprint.blueprint.repository.RepoEntry;
import com.pgmacdesign.mc3dprint.machine.repository.BlueprintRepositoryMenu;
import com.pgmacdesign.mc3dprint.network.RepositoryRenamePacket;
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
import java.util.UUID;

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
    // Detail-column rename row. Shares the y the print-status line uses for official
    // builds; a scan never draws that line, and only scans can be renamed.
    private static final int RENAME_Y = 116;
    /** How long a primed Delete stays armed (5s at 20 tps). */
    private static final int DELETE_CONFIRM_TICKS = 100;

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
    private EditBox renameBox;
    private Button renameButton;
    private Button deleteButton;
    /** Ticks left on the delete confirm; 0 = idle, >0 = armed and counting down. */
    private int deleteConfirmTicks;
    /** The entry the rename box is currently editing, so typing isn't clobbered every frame. */
    @Nullable
    private UUID renameTarget;
    private int printFilter = FILTER_ALL;
    private int scroll;

    public BlueprintRepositoryScreen(BlueprintRepositoryMenu menu, Inventory playerInventory, Component title) {
        // 26.1 made imageWidth/imageHeight final; dimensions go through the 5-arg super.
        //? if >=26.1 {
        /*super(menu, playerInventory, title, 248, 186);
        *///?} else {
        super(menu, playerInventory, title);
        //?}
        //? if <26.1 {
        this.imageWidth = 248;
        //?}
        //? if <26.1 {
        this.imageHeight = 186;
        //?}
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

        // Rename, for player scans only. Sits in the detail column on the row the
        // print-status line uses for official builds, which a scan never draws.
        renameBox = new EditBox(font, leftPos + DETAIL_X + 2, topPos + RENAME_Y, 54, 12,
                Component.translatable("gui.mc3dprint.repository.rename"));
        renameBox.setMaxLength(RepositoryRenamePacket.MAX_NAME_LENGTH);
        renameBox.setHint(Component.translatable("gui.mc3dprint.repository.rename"));
        addRenderableWidget(renameBox);
        renameButton = Button.builder(Component.translatable("gui.mc3dprint.repository.rename_set"),
                        b -> submitRename())
                .bounds(leftPos + DETAIL_X + 58, topPos + RENAME_Y, 24, 12)
                .tooltip(Tooltip.create(Component.translatable("gui.mc3dprint.repository.rename_tip")))
                .build();
        addRenderableWidget(renameButton);
        deleteButton = Button.builder(deleteLabel(), b -> clickDelete())
                .bounds(leftPos + DETAIL_X + 84, topPos + RENAME_Y, 24, 12)
                .tooltip(Tooltip.create(Component.translatable("gui.mc3dprint.repository.delete_tip")))
                .build();
        addRenderableWidget(deleteButton);
        syncRenameWidgets();
    }

    /**
     * Shows the rename row only for a selected player scan, and refills it when the selection
     * moves. Refilling is gated on the target actually changing so it never overwrites what
     * the player is mid-way through typing.
     */
    private void syncRenameWidgets() {
        RepoEntry entry = selected();
        boolean renameable = entry != null && !entry.official();
        if (renameBox == null || renameButton == null || deleteButton == null) {
            return;
        }
        renameBox.visible = renameable;
        renameBox.active = renameable;
        renameButton.visible = renameable;
        renameButton.active = renameable;
        // Delete shows only when this player could actually use it. The server re-checks;
        // hiding it just avoids offering a button that would always refuse.
        boolean deletable = renameable && mayDelete(entry);
        deleteButton.visible = deletable;
        deleteButton.active = deletable;
        if (!deletable && deleteConfirmTicks > 0) {
            resetDeleteConfirm();
        }
        if (!renameable) {
            renameTarget = null;
            return;
        }
        if (!entry.id().equals(renameTarget)) {
            renameTarget = entry.id();
            resetDeleteConfirm(); // a pending confirm never carries to a different build
            renameBox.setValue(entry.name());
            renameBox.setCursorPosition(0);
            renameBox.setHighlightPos(0);
        }
    }

    private void submitRename() {
        RepoEntry entry = selected();
        if (entry == null || entry.official() || renameBox == null || renameTarget == null) {
            return;
        }
        // Send the id the BOX was filled for, and only if it's still the selected build. The
        // listing is name-sorted, so a rename re-sorts it; keying the send off the selection
        // alone could retitle whichever build slid into that row.
        if (!renameTarget.equals(entry.id())) {
            return;
        }
        String name = RepositoryRenamePacket.sanitize(renameBox.getValue());
        if (name.isEmpty() || name.equals(entry.name())) {
            return; // nothing to do; the server would reject a blank anyway
        }
        sendRename(new RepositoryRenamePacket(renameTarget, name));
        renameBox.setFocused(false);
    }

    /** 1.21.2 split the client send out of PacketDistributor into ClientPacketDistributor. */
    private static void sendRename(RepositoryRenamePacket packet) {
        //? if >=1.21.8 {
        /*net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(packet);
        *///?} else {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(packet);
        //?}
    }

    /** The depositor, or an operator — mirrors the server's gate in the repository block. */
    private boolean mayDelete(RepoEntry entry) {
        if (minecraft == null || minecraft.player == null) {
            return false;
        }
        // canUseGameMasterBlocks() is "permission level >= 2" and lives on Player in every
        // node, unlike hasPermissions(int), which 1.21.11 replaced with a PermissionSet.
        return entry.depositedBy(minecraft.player.getUUID())
                || minecraft.player.canUseGameMasterBlocks();
    }

    /**
     * Two-click delete: the first click arms the button and relabels it, a second within
     * {@link #DELETE_CONFIRM_TICKS} sends. Removing a catalogued build shouldn't be one
     * stray click on a 24px button, and a modal for it would be heavier than it deserves.
     */
    private void clickDelete() {
        if (deleteConfirmTicks > 0) {
            resetDeleteConfirm();
            clickButton(BlueprintRepositoryMenu.BUTTON_DELETE);
            return;
        }
        deleteConfirmTicks = DELETE_CONFIRM_TICKS;
        deleteButton.setMessage(deleteLabel());
    }

    private void resetDeleteConfirm() {
        deleteConfirmTicks = 0;
        if (deleteButton != null) {
            deleteButton.setMessage(deleteLabel());
        }
    }

    private Component deleteLabel() {
        return Component.translatable(deleteConfirmTicks > 0
                ? "gui.mc3dprint.repository.delete_confirm" : "gui.mc3dprint.repository.delete");
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (deleteConfirmTicks > 0 && --deleteConfirmTicks == 0) {
            resetDeleteConfirm(); // armed too long: fall back to safe
        }
        syncRenameWidgets();
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
        renderRowTooltip(graphics, mouseX, mouseY);
    }

    @Override
    //? if >=26.1 {
    /*public void extractBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
    *///?} else {
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
    //?}
        RenderCompat.blit(graphics, TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        renderList(graphics, mouseX, mouseY);
        renderDetail(graphics);
    }

    private void renderList(GuiGraphics graphics, int mouseX, int mouseY) {
        int left = leftPos + LIST_X;
        int top = topPos + LIST_Y;
        if (menu.entries().isEmpty()) {
            RenderCompat.drawWordWrap(graphics, font, Component.translatable("gui.mc3dprint.repository.empty"),
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
            RenderCompat.drawString(graphics, font, "T" + entry.tier(), left + 2, rowY + 3, tierColor(entry.tier()), false);
            String name = font.plainSubstrByWidth(displayName(entry), LIST_W - 44);
            RenderCompat.drawString(graphics, font, name, left + 20, rowY + 3, LABEL, false);
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
        RenderCompat.drawString(graphics, font, progress, left + 2, topPos + 116, PRINTED, false);
    }

    private void renderDetail(GuiGraphics graphics) {
        int x = leftPos + DETAIL_X;
        RepoEntry entry = selected();
        if (entry == null) {
            RenderCompat.drawString(graphics, font, Component.translatable("gui.mc3dprint.repository.select_hint"),
                    leftPos + PREVIEW_X + 4, topPos + PREVIEW_Y + PREVIEW_H / 2 - 4, LABEL_DIM, false);
            return;
        }
        RenderCompat.drawWordWrap(graphics, font, Component.literal(displayName(entry)),
                leftPos + PREVIEW_X + 4, topPos + PREVIEW_Y + 4, PREVIEW_W - 8, ACCENT);

        int y = topPos + 56;
        line(graphics, x, y, "gui.mc3dprint.repository.size",
                entry.sizeX() + "x" + entry.sizeY() + "x" + entry.sizeZ(), LABEL);
        line(graphics, x, y += 12, "gui.mc3dprint.repository.blocks", Integer.toString(entry.blockCount()), LABEL);
        line(graphics, x, y += 12, "gui.mc3dprint.repository.tier", "T" + entry.tier(), tierColor(entry.tier()));
        line(graphics, x, y += 12, "gui.mc3dprint.repository.cost", entry.cost() + " FU", LABEL);
        RenderCompat.drawString(graphics, font, Component.translatable(entry.official()
                        ? "gui.mc3dprint.repository.official" : "gui.mc3dprint.repository.scanned"),
                x + 4, y += 12, entry.official() ? OFFICIAL : SCANNED, false);
        // print status (official builds are the only ones tracked)
        if (entry.official()) {
            boolean printed = menu.isPrinted(entry.id());
            RenderCompat.drawString(graphics, font, Component.translatable(printed
                            ? "gui.mc3dprint.repository.printed_yes" : "gui.mc3dprint.repository.printed_no"),
                    x + 4, y + 12, printed ? PRINTED : LABEL_DIM, false);
        }
    }

    private void line(GuiGraphics graphics, int x, int y, String key, String value, int valueColor) {
        RenderCompat.drawString(graphics, font, Component.translatable(key), x + 4, y, LABEL_DIM, false);
        RenderCompat.drawString(graphics, font, value, x + 50, y, valueColor, false);
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
        RenderCompat.tooltipLines(graphics, font, List.of(
                Component.literal(displayName(entry)).getVisualOrderText(),
                Component.literal("T" + entry.tier() + " · " + entry.cost() + " FU" + status).getVisualOrderText()),
                mouseX, mouseY);
    }

    @Override
    //? if >=26.1 {
    /*protected void extractLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    *///?} else {
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    //?}
        RenderCompat.drawString(graphics, font, title, titleLabelX, titleLabelY, LABEL, false);
        RenderCompat.drawString(graphics, font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, LABEL, false);

        int collected = (int) menu.entries().stream().filter(RepoEntry::official).count();
        Component counter = Component.translatable("gui.mc3dprint.repository.collected",
                collected, CuratedBlueprints.CURATED_NAMES.size());
        RenderCompat.drawString(graphics, font, counter, imageWidth - 14 - font.width(counter), titleLabelY, OFFICIAL, false);
    }

    //? if >=1.21.9 {
    /*@Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (selectRowAt(event.x(), event.y())) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }
    *///?} else {
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (selectRowAt(mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    //?}

    private boolean selectRowAt(double mouseX, double mouseY) {
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
        return false;
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

    /** Whichever text field currently has focus (search or rename), or null. */
    @Nullable
    private EditBox focusedBox() {
        if (search != null && search.isFocused()) {
            return search;
        }
        if (renameBox != null && renameBox.visible && renameBox.isFocused()) {
            return renameBox;
        }
        return null;
    }

    //? if >=1.21.9 {
    /*@Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        // Let a focused text field consume typing (incl. 'e') instead of closing the GUI.
        EditBox box = focusedBox();
        if (box != null && event.key() != 256) {
            if (box == renameBox && (event.key() == 257 || event.key() == 335)) {
                submitRename(); // Enter commits, same as the button
                return true;
            }
            return box.keyPressed(event) || box.canConsumeInput();
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        EditBox box = focusedBox();
        return box != null ? box.charTyped(event) : super.charTyped(event);
    }
    *///?} else {
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Let a focused text field consume typing (incl. 'e') instead of closing the GUI.
        EditBox box = focusedBox();
        if (box != null && keyCode != 256) {
            if (box == renameBox && (keyCode == 257 || keyCode == 335)) {
                submitRename(); // Enter commits, same as the button
                return true;
            }
            return box.keyPressed(keyCode, scanCode, modifiers) || box.canConsumeInput();
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        EditBox box = focusedBox();
        return box != null ? box.charTyped(c, modifiers) : super.charTyped(c, modifiers);
    }
    //?}

    @Nullable
    private RepoEntry selected() {
        int i = menu.selectedIndex();
        List<RepoEntry> entries = menu.entries();
        return i >= 0 && i < entries.size() ? entries.get(i) : null;
    }

    private static String displayName(RepoEntry entry) {
        return entry.name() == null || entry.name().isEmpty() ? "?" : entry.name();
    }

    /** Tier accent ramp matching the disc tooltip (BlueprintDiscItem.tierFormat). Colors are
     * the vanilla formatting-code ARGBs, inlined because 26.2 dropped ChatFormatting.getColor(). */
    private static int tierColor(int tier) {
        return switch (tier) {
            case 2 -> 0xFF5555FF; // BLUE
            case 3 -> 0xFF55FFFF; // AQUA
            case 4 -> 0xFF55FF55; // GREEN
            case 5 -> 0xFFFFAA00; // GOLD
            case 6 -> 0xFFFF5555; // RED
            case 7 -> 0xFFFF55FF; // LIGHT_PURPLE
            case 8 -> 0xFFAA00AA; // DARK_PURPLE
            default -> 0xFFAAAAAA; // GRAY
        };
    }
}
