package com.pgmacdesign.mc3dprint.client;

import com.pgmacdesign.mc3dprint.machine.terminal.CatalogEntry;
import com.pgmacdesign.mc3dprint.machine.terminal.MC3DPrintTerminalMenu;
import com.pgmacdesign.mc3dprint.machine.terminal.PrintEligibility;
import com.pgmacdesign.mc3dprint.machine.terminal.PrintRequest;
import com.pgmacdesign.mc3dprint.network.TerminalOrderPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

import java.util.List;

/**
 * The MC3DPrint Terminal: a catalog you order from, paid in Filament Units.
 *
 * <p>Laid out like an AE2 terminal on purpose, so the muscle memory transfers: search at the top, a
 * scrollable grid below it, orders down the right. Two things are ours rather than AE2's, and both
 * exist to make the economy legible instead of mysterious.
 *
 * <p><b>The tier rail</b> down the left shows, per tier, the Filament Units that could pay a cost
 * at that tier. Spending is down-only, so each row includes its own tier and everything above it: a
 * Tier 3 spool pays a Tier 1 cost, a Tier 1 spool never pays a Tier 3 one. One grand total would be
 * the number that lies, since most of it may sit below the tier being looked at.
 *
 * <p><b>Greyed rows keep their reason.</b> An item you cannot order still appears, with "Tier 6" or
 * "wind-only" in its tooltip. Hiding it would make the catalog change shape as filament rises and
 * falls, reshuffling the grid under the cursor, and would remove the only place in game most of
 * these rules are visible.
 */
public class MC3DPrintTerminalScreen extends AbstractContainerScreen<MC3DPrintTerminalMenu> {

    private static final int PANEL = 0xFF1A1F2B;
    private static final int BEVEL_L = 0xFF2C3342;
    private static final int BEVEL_D = 0xFF0A0D14;
    private static final int WELL = 0xFF10141E;
    private static final int LABEL = 0xFFC0C0C8;
    private static final int LABEL_DIM = 0xFF7D8597;
    private static final int ACCENT = 0xFF3FE0C0;
    private static final int WARN = 0xFFE57A7A;
    /** Wash drawn over a row that exists but cannot be ordered. */
    private static final int GREY_WASH = 0xB0121620;

    private static final int WIDTH = 258, HEIGHT = 222;
    private static final int GRID_X = 42, GRID_Y = 36, CELL = 18;
    private static final int RAIL_X = 8, RAIL_Y = 36, RAIL_W = 30, RAIL_ROW = 13;
    private static final int ORDERS_X = 8, ORDERS_Y = 160, ORDER_LINE = 10;
    private static final int SEARCH_X = 42, SEARCH_Y = 20, SEARCH_W = 140, SEARCH_H = 12;

    private EditBox searchBox;

    public MC3DPrintTerminalScreen(MC3DPrintTerminalMenu menu, Inventory playerInventory,
                                   Component title) {
        // 26.1 made imageWidth/imageHeight final; dimensions go through the 5-arg super.
        //? if >=26.1 {
        /*super(menu, playerInventory, title, WIDTH, HEIGHT);
        *///?} else {
        super(menu, playerInventory, title);
        //?}
        //? if <26.1 {
        this.imageWidth = WIDTH;
        this.imageHeight = HEIGHT;
        //?}
    }

    @Override
    protected void init() {
        super.init();
        // No player inventory is drawn, so the vanilla label would point at nothing.
        this.inventoryLabelY = Integer.MIN_VALUE;
        searchBox = new EditBox(this.font, leftPos + SEARCH_X, topPos + SEARCH_Y,
                SEARCH_W, SEARCH_H, Component.translatable("gui.mc3dprint.terminal.search"));
        searchBox.setMaxLength(64);
        searchBox.setBordered(false);
        searchBox.setTextColor(LABEL);
        searchBox.setValue(menu.search());
        searchBox.setResponder(menu::setSearch);
        addRenderableWidget(searchBox);
        setInitialFocus(searchBox);
    }

    @Override
    //? if >=26.1 {
    /*public void extractBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
    *///?} else {
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
    //?}
        int x = leftPos;
        int y = topPos;
        g.fill(x, y, x + WIDTH, y + HEIGHT, PANEL);
        g.fill(x, y, x + WIDTH, y + 1, BEVEL_L);
        g.fill(x, y, x + 1, y + HEIGHT, BEVEL_L);
        g.fill(x, y + HEIGHT - 1, x + WIDTH, y + HEIGHT, BEVEL_D);
        g.fill(x + WIDTH - 1, y, x + WIDTH, y + HEIGHT, BEVEL_D);

        // Search well
        g.fill(x + SEARCH_X - 2, y + SEARCH_Y - 2, x + SEARCH_X + SEARCH_W + 2,
                y + SEARCH_Y + SEARCH_H, WELL);
        // Grid well
        g.fill(x + GRID_X - 1, y + GRID_Y - 1,
                x + GRID_X + MC3DPrintTerminalMenu.COLUMNS * CELL + 1,
                y + GRID_Y + MC3DPrintTerminalMenu.VISIBLE_ROWS * CELL + 1, WELL);
        // Tier rail well
        g.fill(x + RAIL_X - 1, y + RAIL_Y - 1, x + RAIL_X + RAIL_W + 1,
                y + RAIL_Y + MC3DPrintTerminalMenu.MAX_TIER * RAIL_ROW + 1, WELL);
        // Orders well
        g.fill(x + ORDERS_X - 1, y + ORDERS_Y - 1, x + WIDTH - 8, y + HEIGHT - 8, WELL);

        drawTierRail(g, x, y);
        drawGrid(g, x, y);
        drawOrders(g, x, y);
    }

    private void drawTierRail(GuiGraphics g, int x, int y) {
        for (int tier = 1; tier <= MC3DPrintTerminalMenu.MAX_TIER; tier++) {
            int ry = y + RAIL_Y + (tier - 1) * RAIL_ROW;
            int fu = menu.fuAtTier(tier);
            boolean reachable = tier <= menu.bestMachineTier();
            int colour = !reachable ? LABEL_DIM : (fu > 0 ? ACCENT : WARN);
            // Tier left, amount right-aligned inside the rail. Both were drawn at the same
            // coordinate before, which stacked them on top of each other.
            String label = "T" + tier;
            String amount = abbreviate(fu);
            g.drawString(font, label, x + RAIL_X + 2, ry + 2, colour, false);
            g.drawString(font, amount, x + RAIL_X + RAIL_W - 2 - font.width(amount), ry + 2,
                    colour, false);
        }
    }

    private void drawGrid(GuiGraphics g, int x, int y) {
        for (int row = 0; row < MC3DPrintTerminalMenu.VISIBLE_ROWS; row++) {
            for (int col = 0; col < MC3DPrintTerminalMenu.COLUMNS; col++) {
                int cell = row * MC3DPrintTerminalMenu.COLUMNS + col;
                CatalogEntry entry = menu.entryAt(cell);
                if (entry == null) {
                    continue;
                }
                int cx = x + GRID_X + col * CELL;
                int cy = y + GRID_Y + row * CELL;
                ItemStack stack = entry.stack();
                g.renderItem(stack, cx + 1, cy + 1);
                if (!entry.orderable()) {
                    // Wash rather than skip: the row stays where it is so the grid does not
                    // reshuffle as filament moves, and the tooltip still explains why.
                    g.fill(cx, cy, cx + CELL, cy + CELL, GREY_WASH);
                }
            }
        }
    }

    private void drawOrders(GuiGraphics g, int x, int y) {
        List<MC3DPrintTerminalMenu.OrderView> orders = menu.orders();
        if (orders.isEmpty()) {
            g.drawString(font, Component.translatable("gui.mc3dprint.terminal.no_orders"),
                    x + ORDERS_X + 2, y + ORDERS_Y + 2, LABEL_DIM, false);
            return;
        }
        int shown = Math.min(orders.size(), visibleOrderRows());
        for (int i = 0; i < shown; i++) {
            MC3DPrintTerminalMenu.OrderView o = orders.get(i);
            int oy = y + ORDERS_Y + 2 + i * ORDER_LINE;
            int colour = switch (o.status()) {
                case COMPLETE -> ACCENT;
                case CANCELLED -> WARN;
                case HELD -> LABEL_DIM;
                default -> LABEL;
            };
            // The x is the affordance: without it nothing tells the player a row is clickable.
            String line = (o.status().isTerminal() ? "  " : "x ")
                    + o.delivered() + "/" + o.quantity() + " "
                    + new ItemStack(o.item()).getHoverName().getString();
            if (o.status() == PrintRequest.Status.HELD && o.reason() != null) {
                line += " (" + o.reason() + ")";
            }
            g.drawString(font, trim(line, WIDTH - ORDERS_X - 16), x + ORDERS_X + 2, oy, colour, false);
        }
    }

    @Override
    //? if >=26.1 {
    /*protected void extractLabels(GuiGraphics g, int mouseX, int mouseY) {
    *///?} else {
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
    //?}
        g.drawString(font, title, 8, 7, LABEL, false);
        String machines = menu.machineCount() == 0
                ? Component.translatable("gui.mc3dprint.terminal.no_machines").getString()
                : menu.machineCount() + " machines, best T" + menu.bestMachineTier();
        g.drawString(font, machines, WIDTH - 8 - font.width(machines), 7,
                menu.machineCount() == 0 ? WARN : LABEL_DIM, false);
    }

    @Override
    //? if >=26.1 {
    /*public void extractRenderState(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    *///?} else {
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
    //?}
        renderCatalogTooltip(g, mouseX, mouseY);
    }

    /** The quote: cost, tier, and, when it cannot be ordered, why. */
    private void renderCatalogTooltip(GuiGraphics g, int mouseX, int mouseY) {
        int railTier = tierUnder(mouseX, mouseY);
        if (railTier > 0) {
            g.renderComponentTooltip(font,
                    java.util.List.of(
                            Component.literal("Tier " + railTier),
                            Component.literal(menu.fuAtTier(railTier) + " FU available")
                                    .withStyle(st -> st.withColor(ACCENT)),
                            Component.literal(railTier <= menu.bestMachineTier()
                                    ? "Within reach of your best machine"
                                    : "No machine on this network can print Tier " + railTier)
                                    .withStyle(st -> st.withColor(
                                            railTier <= menu.bestMachineTier() ? LABEL_DIM : WARN))),
                    mouseX, mouseY);
            return;
        }
        CatalogEntry entry = entryUnder(mouseX, mouseY);
        if (entry == null) {
            return;
        }
        java.util.List<Component> lines = new java.util.ArrayList<>();
        lines.add(entry.stack().getHoverName());
        lines.add(Component.literal(entry.fuCost() + " FU @ T" + entry.tier())
                .withStyle(s -> s.withColor(ACCENT)));
        if (entry.verdict() != PrintEligibility.Verdict.OK) {
            lines.add(Component.literal(describe(entry.verdict()))
                    .withStyle(s -> s.withColor(WARN)));
        } else if (!entry.affordable()) {
            lines.add(Component.translatable("gui.mc3dprint.terminal.not_enough_filament")
                    .withStyle(s -> s.withColor(WARN)));
        } else {
            lines.add(Component.translatable("gui.mc3dprint.terminal.click_to_order")
                    .withStyle(s -> s.withColor(LABEL_DIM)));
        }
        g.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    private static String describe(PrintEligibility.Verdict verdict) {
        return switch (verdict) {
            case SPOOL -> "Spools are never printed";
            case WIND_ONLY -> "Wind-only: recyclable, never printable";
            case RESTRICTED -> "Restricted trophy: official blueprints only";
            case UNVALUED -> "No FU value";
            case NEEDS_HIGHER_TIER -> "Needs a higher-tier machine";
            case OK -> "";
        };
    }

    // 1.21.9 replaced the mouse callback with an event object, and moved hasShiftDown onto it.
    // Reading shift off the event is also the more correct of the two: it is the modifier state of
    // THIS click rather than whatever the keyboard happens to hold when the handler runs.
    //? if >=1.21.9 {
    /*@Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (orderAt(event.x(), event.y(), event.hasShiftDown())) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }
    *///?} else {
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Left button only. Right- and middle-clicks used to place orders too, which spends
        // filament on a gesture every other inventory screen treats as something else.
        if (button == 0 && orderAt(mouseX, mouseY, hasShiftDown())) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    //?}

    /** How many order rows the panel has room to draw. Drawing and hit testing share it. */
    private static int visibleOrderRows() {
        return (HEIGHT - 8 - ORDERS_Y) / ORDER_LINE;
    }

    /** The order row under the cursor, or null. Clicking one cancels it. */
    @Nullable
    private MC3DPrintTerminalMenu.OrderView orderRowUnder(double mouseX, double mouseY) {
        int ox = leftPos + ORDERS_X;
        int oy = topPos + ORDERS_Y + 2;
        if (mouseX < ox || mouseX >= leftPos + WIDTH - 8 || mouseY < oy) {
            return null;
        }
        int row = (int) ((mouseY - oy) / ORDER_LINE);
        List<MC3DPrintTerminalMenu.OrderView> orders = menu.orders();
        // Bounded by what is DRAWN, not by how many orders exist. The panel shows a fixed number
        // of rows, so indexing the whole list let a click on blank space below the last drawn row
        // cancel an order the player could not see.
        int shown = Math.min(orders.size(), visibleOrderRows());
        return row >= 0 && row < shown ? orders.get(row) : null;
    }

    /** Shared by both mouseClicked shapes, so the ordering rule exists once. */
    private boolean orderAt(double mouseX, double mouseY, boolean shift) {
        // An order row cancels; only the placer's own orders are cancellable, and the server
        // enforces that, so a refused click simply does nothing visible.
        MC3DPrintTerminalMenu.OrderView row = orderRowUnder(mouseX, mouseY);
        if (row != null && !row.status().isTerminal()) {
            sendOrder(TerminalOrderPacket.cancel(row.id()));
            return true;
        }
        CatalogEntry entry = entryUnder((int) mouseX, (int) mouseY);
        if (entry == null || !entry.orderable()) {
            return false;
        }
        int qty = shift ? entry.stack().getMaxStackSize() : 1;
        sendOrder(TerminalOrderPacket.order(BuiltInRegistries.ITEM.getKey(entry.item()), qty));
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dy) {
        if (overGrid((int) mouseX, (int) mouseY)) {
            menu.setScrollRow(menu.scrollRow() - (int) Math.signum(dy));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, dy);
    }

    // While the search box has focus it must consume normal typing. Without this, E closes the
    // screen mid-word, which is the single most irritating bug a search field can have. 1.21.9
    // replaced the key/char callbacks with event objects, so both shapes are carried.
    //? if >=1.21.9 {
    /*@Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (searchBox != null && searchBox.isFocused() && event.key() != 256) {
            return searchBox.keyPressed(event) || searchBox.canConsumeInput();
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        if (searchBox != null && searchBox.isFocused()) {
            return searchBox.charTyped(event);
        }
        return super.charTyped(event);
    }
    *///?} else {
    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (searchBox != null && searchBox.isFocused() && key != 256) {
            return searchBox.keyPressed(key, scan, mods) || searchBox.canConsumeInput();
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (searchBox != null && searchBox.isFocused()) {
            return searchBox.charTyped(c, mods);
        }
        return super.charTyped(c, mods);
    }
    //?}

    /** The rail tier under the cursor (1-based), or 0. The rail abbreviates, so hover gives exact. */
    private int tierUnder(int mouseX, int mouseY) {
        int rx = leftPos + RAIL_X;
        int ry = topPos + RAIL_Y;
        if (mouseX < rx || mouseX >= rx + RAIL_W || mouseY < ry
                || mouseY >= ry + MC3DPrintTerminalMenu.MAX_TIER * RAIL_ROW) {
            return 0;
        }
        return (mouseY - ry) / RAIL_ROW + 1;
    }

    private boolean overGrid(int mouseX, int mouseY) {
        int gx = leftPos + GRID_X;
        int gy = topPos + GRID_Y;
        return mouseX >= gx && mouseX < gx + MC3DPrintTerminalMenu.COLUMNS * CELL
                && mouseY >= gy && mouseY < gy + MC3DPrintTerminalMenu.VISIBLE_ROWS * CELL;
    }

    private CatalogEntry entryUnder(int mouseX, int mouseY) {
        if (!overGrid(mouseX, mouseY)) {
            return null;
        }
        int col = (mouseX - (leftPos + GRID_X)) / CELL;
        int row = (mouseY - (topPos + GRID_Y)) / CELL;
        return menu.entryAt(row * MC3DPrintTerminalMenu.COLUMNS + col);
    }

    /** 1.20.1 sends client-to-server through PacketDistributor; the split came in 1.21.2. */
    private static void sendOrder(TerminalOrderPacket packet) {
        com.pgmacdesign.mc3dprint.network.MC3DPrintNetwork.CHANNEL.sendToServer(packet);
    }

    /** Compact FU for the rail, which is 18px wide and cannot show six digits. */
    private static String abbreviate(int fu) {
        if (fu >= 1_000_000) {
            return (fu / 1_000_000) + "M";
        }
        if (fu >= 1_000) {
            return (fu / 1_000) + "k";
        }
        return Integer.toString(fu);
    }

    private String trim(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        return font.plainSubstrByWidth(text, maxWidth - font.width("…")) + "…";
    }
}
