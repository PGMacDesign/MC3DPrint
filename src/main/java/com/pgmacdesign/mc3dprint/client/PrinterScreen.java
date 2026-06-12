package com.pgmacdesign.mc3dprint.client;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.machine.PrinterBlockEntity;
import com.pgmacdesign.mc3dprint.machine.PrinterMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

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

    // Control strip (Start / Auto / build offsets) between machine and inventory
    private static final int CONTROLS_Y = 70;
    private static final int OFFSETS_Y = 87;

    private Button startButton;
    private Button autoButton;

    public PrinterScreen(PrinterMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 188;
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

        // offsets: [-] value [+] per axis; X at 10, Y at 64, Z at 118
        for (int axis = 0; axis < 3; axis++) {
            int x = 10 + axis * 54;
            int minusId = PrinterMenu.BUTTON_OFFSET_BASE + axis * 2;
            addRenderableWidget(Button.builder(Component.literal("-"), b -> sendButtonClick(minusId))
                    .bounds(leftPos + x, topPos + OFFSETS_Y, 12, 12).build());
            addRenderableWidget(Button.builder(Component.literal("+"), b -> sendButtonClick(minusId + 1))
                    .bounds(leftPos + x + 36, topPos + OFFSETS_Y, 12, 12).build());
        }
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

    @Override
    protected void containerTick() {
        super.containerTick();
        autoButton.setMessage(autoLabel());
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
        if (isHovering(8, OFFSETS_Y, 160, 12, mouseX, mouseY)) {
            graphics.renderTooltip(font,
                    Component.translatable("tooltip.mc3dprint.offsets"), mouseX, mouseY);
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
        int color = menu.state() == PrinterBlockEntity.State.PRINTING ? 0x2E7D32
                : menu.state() == PrinterBlockEntity.State.IDLE
                        || menu.state() == PrinterBlockEntity.State.READY ? 0x404040 : 0xB71C1C;
        graphics.drawString(font, status, 80, 58, color, false);
        int cost = menu.templateCost();
        if (cost > 0) {
            graphics.drawString(font, Component.translatable("gui.mc3dprint.cost", cost), 36, 58, 0x404040, false);
        }
        Component spools = Component.translatable("gui.mc3dprint.spools", menu.spoolsUsed(), menu.spoolSlots());
        int spoolsColor = menu.spoolsUsed() == 0 ? 0xB71C1C : 0x404040;
        graphics.drawString(font, spools, imageWidth - 8 - font.width(spools), inventoryLabelY, spoolsColor, false);

        // offset readouts centered between their -/+ buttons
        String[] axes = {"X", "Y", "Z"};
        for (int axis = 0; axis < 3; axis++) {
            String text = axes[axis] + " " + menu.offset(axis);
            int x = 10 + axis * 54 + 12 + (24 - font.width(text)) / 2;
            graphics.drawString(font, text, x, OFFSETS_Y + 2, 0x404040, false);
        }
    }
}
