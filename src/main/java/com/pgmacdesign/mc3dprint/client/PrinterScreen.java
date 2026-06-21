package com.pgmacdesign.mc3dprint.client;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.config.MC3DPrintConfig;
import com.pgmacdesign.mc3dprint.item.BlueprintDiscItem;
import com.pgmacdesign.mc3dprint.machine.PrinterBlockEntity;
import com.pgmacdesign.mc3dprint.machine.PrinterMenu;
import com.pgmacdesign.mc3dprint.machine.resin.ResinEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class PrinterScreen extends AbstractContainerScreen<PrinterMenu> {
    private static final ResourceLocation TEXTURE = java.util.Objects.requireNonNull(
            ResourceLocation.tryParse(MC3DPrint.MOD_ID + ":textures/gui/printer.png"));

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

    // Control strip (Start / Auto / build offsets / rotate) between machine and inventory
    private static final int CONTROLS_Y = 70;
    private static final int OFFSETS_Y = 87;
    private static final int ROTATE_Y = 101;   // dedicated rotate row, just below the XYZ row

    // Dark tech-console code colors (see VISUAL-REVAMP-BRIEF "GUI — dark tech-console").
    // The panel is now charcoal, so the old dark-grey label colors were flipped to
    // light text + an accent-cyan / warm-red status. All ARGB, drawn shadowless.
    private static final int LABEL = 0xFFC0C0C8;   // light label text
    private static final int ACCENT = 0xFF3FE0C0;  // accent cyan (printing/ready status)
    private static final int WARN = 0xFFE57A7A;    // warm red (paused/error states)
    private static final int FILL_ENERGY = 0xFFD32F2F;   // energy bar = red
    private static final int FILL_FILAMENT = 0xFF4FC3F7;  // filament bar = cyan
    private static final int FILL_PROGRESS = 0xCC4FC3F7;  // progress arrow = cyan
    private static final int SHIMMER = 0xFFBFE9FF;        // 1px leading-edge shimmer

    private Button startButton;
    private Button autoButton;
    private Button previewButton;
    private Button rotateButton;

    public PrinterScreen(PrinterMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        // Widened from 176 to fit the upgrade-slot column + "Upgrades" header.
        // Heightened from 200 to host the dedicated Rotate row below the XYZ offsets.
        this.imageWidth = 230;
        this.imageHeight = 216;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        startButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.mc3dprint.start"),
                        b -> sendButtonClick(PrinterMenu.BUTTON_START))
                .bounds(leftPos + 8, topPos + CONTROLS_Y, 40, 14).build());
        autoButton = addRenderableWidget(Button.builder(
                        autoLabel(),
                        b -> sendButtonClick(PrinterMenu.BUTTON_AUTO))
                .bounds(leftPos + 52, topPos + CONTROLS_Y, 52, 14).build());
        previewButton = addRenderableWidget(Button.builder(
                        previewLabel(),
                        b -> sendButtonClick(PrinterMenu.BUTTON_PREVIEW))
                .bounds(leftPos + 108, topPos + CONTROLS_Y, 60, 14).build());

        // offsets: [-] value [+] per axis; X at 10, Y at 64, Z at 118
        for (int axis = 0; axis < 3; axis++) {
            int x = 10 + axis * 54;
            int minusId = PrinterMenu.BUTTON_OFFSET_BASE + axis * 2;
            addRenderableWidget(Button.builder(Component.literal("-"), b -> sendButtonClick(minusId))
                    .bounds(leftPos + x, topPos + OFFSETS_Y, 12, 12).build());
            addRenderableWidget(Button.builder(Component.literal("+"), b -> sendButtonClick(minusId + 1))
                    .bounds(leftPos + x + 36, topPos + OFFSETS_Y, 12, 12).build());
        }

        // dedicated Rotate row below the XYZ offsets: one tap = clockwise 90°
        rotateButton = addRenderableWidget(Button.builder(
                        rotateLabel(),
                        b -> sendButtonClick(PrinterMenu.BUTTON_ROTATE))
                .bounds(leftPos + 10, topPos + ROTATE_Y, 100, 14).build());
    }

    private Component rotateLabel() {
        return Component.translatable("gui.mc3dprint.rotate", menu.rotationDegrees());
    }

    private void sendButtonClick(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    private Component autoLabel() {
        return Component.translatable(menu.autoStart()
                ? "gui.mc3dprint.auto_on" : "gui.mc3dprint.auto_off");
    }

    private Component previewLabel() {
        return Component.translatable(menu.preview()
                ? "gui.mc3dprint.preview_on" : "gui.mc3dprint.preview_off");
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        autoButton.setMessage(autoLabel());
        previewButton.setMessage(previewLabel());
        rotateButton.setMessage(rotateLabel());
        startButton.active = !menu.autoStart();
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
            java.util.List<Component> lines = new java.util.ArrayList<>();
            lines.add(Component.translatable("tooltip.mc3dprint.fu", menu.fu(), menu.fuCapacity()));
            lines.add(Component.translatable("tooltip.mc3dprint.spools_docked",
                    menu.spoolsUsed(), menu.spoolSlots()));
            if (menu.spoolsUsed() == 0) {
                lines.add(Component.translatable("tooltip.mc3dprint.fu_no_spools"));
            }
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
    }

    /**
     * Disc tooltip augments when a resin is slotted (resins apply only to official discs):
     *
     *  - "No effect" warning: if the slotted resin has nothing to act on in this build (Treasure
     *    with no containers, Ore Salting with no stone, …), we append a warning so the player
     *    knows it would be wasted BEFORE printing. The printer also refuses to consume an inert
     *    resin server-side; this is just the heads-up. Driven by the disc's cached resin-target
     *    mask (the client never has the full block data).
     *  - Overdrive cost preview: with an Overdrive resin slotted, the "Print Cost" line is
     *    rewritten in place — original struck through, the Overdrive-reduced cost beside it. Only
     *    a cost that actually drops is shown (Rare prints below break-even; Uncommon is exactly
     *    break-even, so its base cost is unchanged and the line is left as-is).
     */
    @Override
    protected java.util.List<Component> getTooltipFromContainerItem(ItemStack stack) {
        java.util.List<Component> tooltip = new java.util.ArrayList<>(super.getTooltipFromContainerItem(stack));
        if (!(stack.getItem() instanceof BlueprintDiscItem)
                || !BlueprintDiscItem.hasBlueprint(stack) || !BlueprintDiscItem.isOfficial(stack)) {
            return tooltip;
        }
        com.pgmacdesign.mc3dprint.item.ResinItem.Effect slotted = menu.slottedResinEffect();
        if (slotted != null
                && !BlueprintDiscItem.maskBenefits(slotted, BlueprintDiscItem.getResinTargets(stack))) {
            tooltip.add(Component.translatable("tooltip.mc3dprint.resin_no_effect")
                    .withStyle(ChatFormatting.GOLD));
        }

        int odTier = menu.overdriveResinTierInSlot();
        if (odTier <= 0) {
            return tooltip;
        }
        int cost = BlueprintDiscItem.getPrintCost(stack);
        int tier = BlueprintDiscItem.getTier(stack);
        if (cost <= 0 || tier <= 0) {
            return tooltip;
        }
        int reduced = ResinEffects.overdriveFloor(cost, odTier, MC3DPrintConfig.RESIN_OVERDRIVE_T3_BELOW.get());
        if (reduced >= cost) {
            return tooltip; // Uncommon Overdrive = break-even: no base reduction to preview
        }
        // Rebuild the exact normal cost line (same key/args/style) so we can swap it in place.
        Component normal = Component.translatable("tooltip.mc3dprint.disc_print_cost", cost, tier)
                .withStyle(ChatFormatting.LIGHT_PURPLE);
        Component original = Component.literal(Integer.toString(cost))
                .withStyle(ChatFormatting.GRAY, ChatFormatting.STRIKETHROUGH);
        Component savings = Component.literal(Integer.toString(reduced)).withStyle(ChatFormatting.GREEN);
        Component overdriveLine = Component.translatable("tooltip.mc3dprint.disc_print_cost_overdrive",
                original, savings, tier).withStyle(ChatFormatting.LIGHT_PURPLE);
        for (int i = 0; i < tooltip.size(); i++) {
            if (tooltip.get(i).equals(normal)) {
                tooltip.set(i, overdriveLine);
                break;
            }
        }
        return tooltip;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int left = leftPos;
        int top = topPos;
        graphics.blit(TEXTURE, left, top, 0, 0, imageWidth, imageHeight);

        // Energy fill (red), bottom-up
        int energyPixels = (int) ((long) menu.energy() * ENERGY_HEIGHT / menu.maxEnergy());
        if (energyPixels > 0) {
            graphics.fill(left + ENERGY_X, top + ENERGY_Y + ENERGY_HEIGHT - energyPixels,
                    left + ENERGY_X + ENERGY_WIDTH, top + ENERGY_Y + ENERGY_HEIGHT,
                    FILL_ENERGY);
        }

        // Progress fill (cyan), left-to-right, with a 1px brighter shimmer at the leading edge
        int progressPixels = menu.progress() * ARROW_WIDTH / menu.maxProgress();
        if (progressPixels > 0) {
            graphics.fill(left + ARROW_X, top + ARROW_Y,
                    left + ARROW_X + progressPixels, top + ARROW_Y + ARROW_HEIGHT,
                    FILL_PROGRESS);
            if (progressPixels < ARROW_WIDTH) {
                graphics.fill(left + ARROW_X + progressPixels - 1, top + ARROW_Y,
                        left + ARROW_X + progressPixels, top + ARROW_Y + ARROW_HEIGHT, SHIMMER);
            }
        }

        // Filament fill (cyan), bottom-up
        int cap = Math.max(1, menu.fuCapacity());
        int fuPixels = (int) ((long) menu.fu() * FU_HEIGHT / cap);
        if (fuPixels > 0) {
            graphics.fill(left + FU_X, top + FU_Y + FU_HEIGHT - fuPixels,
                    left + FU_X + FU_WIDTH, top + FU_Y + FU_HEIGHT, FILL_FILAMENT);
        }

        // Spool-slot wells — drawn here (not baked into the texture) so a tier shows
        // exactly its spool count (T1=1 … T4+=4), not four empty wells. The well sprite
        // is the first baked upgrade well (18×18 incl. rim) at (UPGRADE_SLOT_X-1, -Y-1).
        int wellU = PrinterMenu.UPGRADE_SLOT_X - 1;
        int wellV = PrinterMenu.UPGRADE_SLOT_Y - 1;
        for (int i = 0; i < menu.spoolSlots(); i++) {
            int sx = PrinterMenu.SPOOL_SLOT_X + (i % PrinterMenu.SPOOL_COLS) * PrinterMenu.SPOOL_COL_STEP;
            int sy = PrinterMenu.SPOOL_SLOT_Y + (i / PrinterMenu.SPOOL_COLS) * PrinterMenu.SPOOL_ROW_STEP;
            graphics.blit(TEXTURE, left + sx - 1, top + sy - 1, wellU, wellV, 18, 18);
        }

        // Resin-slot well (same baked sprite) in the gap between upgrades and spools.
        graphics.blit(TEXTURE, left + PrinterMenu.RESIN_SLOT_X - 1, top + PrinterMenu.RESIN_SLOT_Y - 1,
                wellU, wellV, 18, 18);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Draw the title + inventory label ourselves in light text instead of
        // super's dark-grey (which is invisible on the new charcoal console).
        graphics.drawString(font, title, titleLabelX, titleLabelY, LABEL, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, LABEL, false);

        Component status = switch (menu.state()) {
            case IDLE -> Component.translatable("gui.mc3dprint.state.idle");
            case READY -> Component.translatable("gui.mc3dprint.state.ready");
            case PRINTING -> Component.translatable("gui.mc3dprint.state.printing");
            case PAUSED_NO_POWER -> Component.translatable("gui.mc3dprint.state.paused_no_power");
            case PAUSED_OUTPUT_FULL -> Component.translatable("gui.mc3dprint.state.paused_output_full");
            case PAUSED_OBSTRUCTED -> Component.translatable("gui.mc3dprint.state.paused_obstructed");
            case ZONE_CONFLICT -> Component.translatable("gui.mc3dprint.state.zone_conflict");
            case PAUSED_NO_FILAMENT -> Component.translatable("gui.mc3dprint.state.paused_no_filament");
            case NOT_PRINTABLE -> Component.translatable("gui.mc3dprint.state.not_printable");
            case AREA_TOO_SMALL -> Component.translatable("gui.mc3dprint.state.area_too_small");
        };
        // Status lights accent cyan when printing/ready, warm red for paused/error,
        // neutral light grey when idle.
        int color = switch (menu.state()) {
            case PRINTING, READY -> ACCENT;
            case IDLE -> LABEL;
            default -> WARN;
        };
        // Keep the status text from running into the upgrade column on the right.
        // If a localized string is wide, shift its draw x left so it never crosses
        // the column edge; never push it left of the status anchor at 80.
        int statusRightEdge = PrinterMenu.UPGRADE_SLOT_X - 4;
        int statusX = Math.min(80, statusRightEdge - font.width(status));
        graphics.drawString(font, status, statusX, 58, color, false);
        int cost = menu.templateCost();
        if (cost > 0) {
            graphics.drawString(font, Component.translatable("gui.mc3dprint.cost", cost), 36, 58, LABEL, false);
        }
        // "Spools X/Y" — smaller and moved DOWN to sit just above the spool-slot grid
        // (roughly in line with the 2nd inventory row), right-aligned over the grid. The
        // gap left above it (below the upgrade column) is reserved for a future readout.
        Component spools = Component.translatable("gui.mc3dprint.spools", menu.spoolsUsed(), menu.spoolSlots());
        int spoolsColor = menu.spoolsUsed() == 0 ? WARN : LABEL;
        float spoolScale = 0.85f;
        float spoolRightEdge = imageWidth - 8;   // right-aligned to the panel margin
        float spoolTopY = PrinterMenu.SPOOL_SLOT_Y - 16;   // just above the grid (≈ 2nd inv row)
        graphics.pose().pushPose();
        graphics.pose().scale(spoolScale, spoolScale, 1f);
        graphics.drawString(font, spools,
                Math.round(spoolRightEdge / spoolScale - font.width(spools)),
                Math.round(spoolTopY / spoolScale), spoolsColor, false);
        graphics.pose().popPose();

        // "Resin" label over the resin slot. Turns warm-red when a resin is slotted but
        // the loaded blueprint is player-made (resin won't apply — the Q9 gate).
        Component resinLabel = Component.translatable("gui.mc3dprint.resin");
        int resinColor = menu.resinBlockedByPlayerBlueprint() ? WARN : LABEL;
        graphics.drawString(font, resinLabel, PrinterMenu.RESIN_SLOT_X - 3,
                PrinterMenu.RESIN_SLOT_Y - 10, resinColor, false);

        // "Upgrades" header over the upgrade-slot column (only when this tier has slots)
        if (menu.upgradeSlotCount() > 0) {
            graphics.drawString(font, Component.translatable("gui.mc3dprint.upgrades"),
                    PrinterMenu.UPGRADE_SLOT_X, PrinterMenu.UPGRADE_SLOT_Y - 10, LABEL, false);
        }

        // offset readouts centered between their -/+ buttons
        String[] axes = {"X", "Y", "Z"};
        for (int axis = 0; axis < 3; axis++) {
            String text = axes[axis] + " " + menu.offset(axis);
            int x = 10 + axis * 54 + 12 + (24 - font.width(text)) / 2;
            graphics.drawString(font, text, x, OFFSETS_Y + 2, LABEL, false);
        }
    }
}
