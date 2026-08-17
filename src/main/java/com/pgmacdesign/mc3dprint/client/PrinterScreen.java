package com.pgmacdesign.mc3dprint.client;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.compat.RenderCompat;
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
    private Button modeButton;

    public PrinterScreen(PrinterMenu menu, Inventory playerInventory, Component title) {
        // 26.1 made imageWidth/imageHeight final; dimensions go through the 5-arg super.
        //? if >=26.1 {
        /*super(menu, playerInventory, title, 230, 216);
        *///?} else {
        super(menu, playerInventory, title);
        //?}
        // Widened from 176 to fit the upgrade-slot column + "Upgrades" header.
        // Heightened from 200 to host the dedicated Rotate row below the XYZ offsets.
        //? if <26.1 {
        this.imageWidth = 230;
        //?}
        //? if <26.1 {
        this.imageHeight = 216;
        //?}
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        // One slot, two verbs: Start when idle, Cancel while a job runs (cancel is
        // safe — no rollback, and a repair restart re-covers placed blocks for free).
        startButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.mc3dprint.start"),
                        b -> sendButtonClick(menu.jobActive()
                                ? PrinterMenu.BUTTON_CANCEL : PrinterMenu.BUTTON_START))
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

        modeButton = addRenderableWidget(Button.builder(
                        modeLabel(),
                        b -> sendButtonClick(PrinterMenu.BUTTON_MODE))
                .bounds(leftPos + 114, topPos + ROTATE_Y, 56, 14).build());
    }

    private Component modeLabel() {
        return Component.translatable(menu.deconstructMode()
                ? "gui.mc3dprint.mode_deconstruct" : "gui.mc3dprint.mode_print");
    }

    /** 12_400 -> "12.4k"; keeps the one-line calculator readout narrow. */
    private static String compact(int value) {
        if (value < 10_000) {
            return Integer.toString(value);
        }
        if (value < 1_000_000) {
            return String.format("%.1fk", value / 1000.0);
        }
        return String.format("%.1fM", value / 1_000_000.0);
    }

    /** Ticks elapsed -> "12s" / "5m" / "2h" (coarse — it's a history stamp, not a timer). */
    private static String agoText(long ticks) {
        long seconds = Math.max(0, ticks / 20);
        if (seconds < 60) {
            return seconds + "s";
        }
        if (seconds < 3600) {
            return (seconds / 60) + "m";
        }
        return (seconds / 3600) + "h";
    }

    /** Ticks -> "~42s" / "~3m10s". */
    private static String etaText(int ticks) {
        int seconds = Math.max(1, ticks / 20);
        if (seconds < 60) {
            return seconds + "s";
        }
        return (seconds / 60) + "m" + (seconds % 60) + "s";
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
        // Doubles as the mode indicator. The Print/Decon button names the mode you are IN,
        // but a button that says "Decon" reads just as easily as one that switches you to
        // Decon, and there is nothing else on the panel to break the tie. Auto only ever
        // shows in Print mode, so its presence answers the question with no label to parse.
        // (Auto still governs deconstructs; toggling it means flipping back to Print first.)
        autoButton.visible = !menu.deconstructMode();
        previewButton.setMessage(previewLabel());
        rotateButton.setMessage(rotateLabel());
        modeButton.setMessage(modeLabel());
        boolean jobActive = menu.jobActive();
        startButton.setMessage(Component.translatable(jobActive
                ? "gui.mc3dprint.cancel" : "gui.mc3dprint.start"));
        // Auto normally owns Start, so the button greys out while it is on. Deconstruct is the
        // exception: a freshly armed region ALWAYS waits for one explicit Start (arming a
        // machine must never dissolve blocks on its own), and with Auto both on and hidden here
        // there would be no way to give it — the machine would sit armed and unstartable.
        startButton.active = jobActive || !menu.autoStart() || menu.deconstructMode();
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

        if (isHovering(ENERGY_X, ENERGY_Y, ENERGY_WIDTH, ENERGY_HEIGHT, mouseX, mouseY)) {
            RenderCompat.tooltip(graphics, font,
                    Component.translatable("tooltip.mc3dprint.energy", menu.energy(), menu.maxEnergy()),
                    mouseX, mouseY);
        }
        // Print history: hover the (empty) output slot for the machine's recent jobs.
        // With an item in the slot, the item tooltip wins; hidden when the client
        // doesn't have the backing BE (e.g. a Remote Terminal in an unloaded chunk).
        if (isHovering(PrinterMenu.OUTPUT_SLOT_X - 1, PrinterMenu.OUTPUT_SLOT_Y - 1, 18, 18, mouseX, mouseY)
                && !menu.getSlot(1).hasItem() && menu.printerBlockEntity() != null) {
            var lines = new java.util.ArrayList<Component>();
            var entries = menu.printerBlockEntity().history();
            if (entries.isEmpty()) {
                lines.add(Component.translatable("tooltip.mc3dprint.history_empty"));
            } else {
                lines.add(Component.translatable("tooltip.mc3dprint.history_header"));
                long now = minecraft != null && minecraft.level != null ? minecraft.level.getGameTime() : 0;
                for (var entry : entries) {
                    lines.add(Component.translatable("tooltip.mc3dprint.history_entry",
                            com.pgmacdesign.mc3dprint.compat.NbtCompat.getString(entry, "Name"),
                            com.pgmacdesign.mc3dprint.compat.NbtCompat.getInt(entry, "Blocks"),
                            agoText(now - com.pgmacdesign.mc3dprint.compat.NbtCompat.getLong(entry, "Time"))));
                }
            }
            RenderCompat.tooltipComponents(graphics, font, lines, mouseX, mouseY);
        }
        if (isHovering(FU_X, FU_Y, FU_WIDTH, FU_HEIGHT, mouseX, mouseY) && menu.blueprintFuTotal() > 0) {
            // Matter Calculator: full pre-print breakdown on the FU gauge
            var lines = new java.util.ArrayList<Component>();
            lines.add(Component.translatable("tooltip.mc3dprint.calc_header",
                    menu.blueprintFuTotal(), compact(menu.blueprintRf()), etaText(menu.blueprintEtaTicks())));
            int shortfall = menu.shortfallTier();
            for (int t = 1; t <= 8; t++) {
                int need = menu.costForTier(t);
                if (need > 0) {
                    Component line = Component.translatable("tooltip.mc3dprint.calc_tier",
                            t, need, menu.availForTier(t));
                    lines.add(t == shortfall
                            ? line.copy().withStyle(net.minecraft.ChatFormatting.RED) : line);
                }
            }
            if (shortfall > 0) {
                lines.add(Component.translatable("tooltip.mc3dprint.calc_shortfall", shortfall)
                        .withStyle(net.minecraft.ChatFormatting.RED));
            }
            lines.add(Component.translatable("tooltip.mc3dprint.fu", menu.fu(), menu.fuCapacity()));
            RenderCompat.tooltipComponents(graphics, font, lines, mouseX, mouseY);
            return;
        }
        if (isHovering(FU_X, FU_Y, FU_WIDTH, FU_HEIGHT, mouseX, mouseY)) {
            java.util.List<Component> lines = new java.util.ArrayList<>();
            lines.add(Component.translatable("tooltip.mc3dprint.fu", menu.fu(), menu.fuCapacity()));
            if (menu.networkFu() > 0) {
                // gauge fill is docked-only; a stocked rack/cable network still feeds prints
                lines.add(Component.translatable("tooltip.mc3dprint.fu_network",
                        String.format("%,d", menu.networkFu())));
            }
            lines.add(Component.translatable("tooltip.mc3dprint.spools_docked",
                    menu.spoolsUsed(), menu.spoolSlots()));
            if (menu.spoolsUsed() == 0 && menu.networkFu() == 0) {
                lines.add(Component.translatable("tooltip.mc3dprint.fu_no_spools"));
            }
            RenderCompat.tooltipComponents(graphics, font, lines, mouseX, mouseY);
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
    //? if >=26.1 {
    /*public void extractBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
    *///?} else {
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
    //?}
        int left = leftPos;
        int top = topPos;
        RenderCompat.blit(graphics, TEXTURE, left, top, 0, 0, imageWidth, imageHeight);

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
            RenderCompat.blit(graphics, TEXTURE, left + sx - 1, top + sy - 1, wellU, wellV, 18, 18);
        }

        // Resin-slot well (same baked sprite) in the gap between upgrades and spools.
        RenderCompat.blit(graphics, TEXTURE, left + PrinterMenu.RESIN_SLOT_X - 1, top + PrinterMenu.RESIN_SLOT_Y - 1,
                wellU, wellV, 18, 18);
    }

    @Override
    //? if >=26.1 {
    /*protected void extractLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    *///?} else {
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    //?}
        // Draw the title + inventory label ourselves in light text instead of
        // super's dark-grey (which is invisible on the new charcoal console).
        RenderCompat.drawString(graphics, font, title, titleLabelX, titleLabelY, LABEL, false);
        RenderCompat.drawString(graphics, font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, LABEL, false);

        Component status = switch (menu.state()) {
            case IDLE -> Component.translatable("gui.mc3dprint.state.idle");
            // An armed un-print says so: the player needs to know Start will undo the last
            // build, not consume the whole boxed region.
            case READY -> Component.translatable(menu.unprintArmed()
                    ? "gui.mc3dprint.state.ready_unprint" : "gui.mc3dprint.state.ready");
            case PRINTING -> Component.translatable("gui.mc3dprint.state.printing");
            case PAUSED_NO_POWER -> Component.translatable("gui.mc3dprint.state.paused_no_power");
            // One state, two causes. Printing, it means the output slot is occupied; a
            // deconstruct has no output slot, and the only thing that raises it there is
            // filament with nowhere to go. "Output Full" sent players hunting for a
            // blocked pipe or a missing chest, so name the actual blocker per mode.
            case PAUSED_OUTPUT_FULL -> Component.translatable(menu.deconstructMode()
                    ? "gui.mc3dprint.state.paused_spool_full" : "gui.mc3dprint.state.paused_output_full");
            case PAUSED_OBSTRUCTED -> Component.translatable("gui.mc3dprint.state.paused_obstructed");
            case ZONE_CONFLICT -> Component.translatable("gui.mc3dprint.state.zone_conflict");
            case PAUSED_NO_FILAMENT -> Component.translatable("gui.mc3dprint.state.paused_no_filament");
            case NOT_PRINTABLE -> Component.translatable("gui.mc3dprint.state.not_printable");
            case NEEDS_HIGHER_TIER -> Component.translatable("gui.mc3dprint.state.needs_higher_tier", menu.requiredTier());
            case AREA_TOO_SMALL -> Component.translatable("gui.mc3dprint.state.area_too_small");
            case DECONSTRUCTING -> Component.translatable("gui.mc3dprint.state.deconstructing");
            case NO_REGION -> Component.translatable("gui.mc3dprint.state.no_region");
        };
        // Status lights accent cyan when printing/ready, warm red for paused/error,
        // neutral light grey when idle.
        int color = switch (menu.state()) {
            case PRINTING, READY, DECONSTRUCTING -> ACCENT;
            case IDLE -> LABEL;
            default -> WARN;
        };
        // The row at y=58 is shared: quote on the left (x=36), status on the right.
        // The status is RIGHT-aligned against the FU gauge's LEFT edge; the row
        // crosses the gauge's vertical span, so anchoring to the upgrade column let
        // long statuses ("Obstructed") paint over the gauge (soak finding). The
        // earlier anchor-at-80 collided the other way (quote ran under "Ready").
        int statusRightEdge = FU_X - 4;
        int statusX = statusRightEdge - font.width(status);
        RenderCompat.drawString(graphics, font, status, statusX, 58, color, false);
        // An error/paused status owns the whole row: the cost/ETA quote is meaningless
        // for a job that can't run, and long statuses ("Build Too Large") physically
        // overlap the quote text (soak finding). WARN color == error/paused here.
        // Deconstruct Mode also suppresses it — the quote describes a PRINT of the
        // loaded disc, which is not what Start does in that mode.
        boolean quoteHidden = color == WARN || menu.deconstructMode();
        int cost = quoteHidden ? 0 : menu.templateCost();
        // Whatever ends up on the left may never run under the status text: degrade
        // the quote (full → FU-only → nothing) until it fits. The gauge tooltip
        // always carries the full breakdown, so dropping the ETA loses nothing.
        int quoteLimit = statusX - 6;
        if (cost > 0) {
            Component itemCost = Component.translatable("gui.mc3dprint.cost", cost);
            if (36 + font.width(itemCost) <= quoteLimit) {
                RenderCompat.drawString(graphics, font, itemCost, 36, 58, LABEL, false);
            }
        } else if (!quoteHidden && menu.blueprintFuTotal() > 0) {
            // Matter Calculator: compact cost/ETA line for the loaded disc; warm-red when
            // filament coverage fails (details live in the FU gauge tooltip).
            Component calc = Component.translatable("gui.mc3dprint.bp_cost",
                    compact(menu.blueprintFuTotal()), etaText(menu.blueprintEtaTicks()));
            if (36 + font.width(calc) > quoteLimit) {
                calc = Component.translatable("gui.mc3dprint.cost", compact(menu.blueprintFuTotal()));
            }
            if (36 + font.width(calc) <= quoteLimit) {
                RenderCompat.drawString(graphics, font, calc, 36, 58,
                        menu.shortfallTier() > 0 ? WARN : LABEL, false);
            }
        }
        // "Spools X/Y" — smaller and moved DOWN to sit just above the spool-slot grid
        // (roughly in line with the 2nd inventory row), right-aligned over the grid. The
        // gap left above it (below the upgrade column) is reserved for a future readout.
        Component spools = Component.translatable("gui.mc3dprint.spools", menu.spoolsUsed(), menu.spoolSlots());
        int spoolsColor = menu.spoolsUsed() == 0 ? WARN : LABEL;
        float spoolScale = 0.85f;
        float spoolRightEdge = imageWidth - 8;   // right-aligned to the panel margin
        float spoolTopY = PrinterMenu.SPOOL_SLOT_Y - 16;   // just above the grid (≈ 2nd inv row)
        //? if >=1.21.5 {
        /*var pose = graphics.pose();
        pose.pushMatrix();
        pose.scale(spoolScale, spoolScale);
        RenderCompat.drawString(graphics, font, spools,
                Math.round(spoolRightEdge / spoolScale - font.width(spools)),
                Math.round(spoolTopY / spoolScale), spoolsColor, false);
        pose.popMatrix();
        *///?} else {
        var pose = graphics.pose();
        pose.pushPose();
        pose.scale(spoolScale, spoolScale, 1f);
        RenderCompat.drawString(graphics, font, spools,
                Math.round(spoolRightEdge / spoolScale - font.width(spools)),
                Math.round(spoolTopY / spoolScale), spoolsColor, false);
        pose.popPose();
        //?}

        // "Resin" label over the resin slot. Turns warm-red when a resin is slotted but
        // the loaded blueprint is player-made (resin won't apply — the Q9 gate).
        Component resinLabel = Component.translatable("gui.mc3dprint.resin");
        int resinColor = menu.resinBlockedByPlayerBlueprint() ? WARN : LABEL;
        RenderCompat.drawString(graphics, font, resinLabel, PrinterMenu.RESIN_SLOT_X - 3,
                PrinterMenu.RESIN_SLOT_Y - 10, resinColor, false);

        // "Upgrades" header over the upgrade-slot column (only when this tier has slots)
        if (menu.upgradeSlotCount() > 0) {
            RenderCompat.drawString(graphics, font, Component.translatable("gui.mc3dprint.upgrades"),
                    PrinterMenu.UPGRADE_SLOT_X, PrinterMenu.UPGRADE_SLOT_Y - 10, LABEL, false);
        }

        // offset readouts centered between their -/+ buttons
        String[] axes = {"X", "Y", "Z"};
        for (int axis = 0; axis < 3; axis++) {
            String text = axes[axis] + " " + menu.offset(axis);
            int x = 10 + axis * 54 + 12 + (24 - font.width(text)) / 2;
            RenderCompat.drawString(graphics, font, text, x, OFFSETS_Y + 2, LABEL, false);
        }
    }
}
