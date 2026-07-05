package com.pgmacdesign.mc3dprint.client;

//? if <1.21.5 {
import com.mojang.math.Axis;
//?}
import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.compat.RenderCompat;
import com.pgmacdesign.mc3dprint.fu.SpoolItem;
import com.pgmacdesign.mc3dprint.machine.WinderBlockEntity;
import com.pgmacdesign.mc3dprint.machine.WinderMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/**
 * Filament Winder GUI — the "Throughput Panel" layout: energy + filament gauges
 * flank a centre group of input -> spinning reel -> spool, with a live status
 * line and three info cards (Material / Spool / Rate) above the player inventory.
 * Coords stay in lockstep with {@code tools/gen_printer_gui.py:build_machine}.
 */
public class WinderScreen extends AbstractContainerScreen<WinderMenu> {
    private static final ResourceLocation TEXTURE = Objects.requireNonNull(
            ResourceLocation.tryParse(MC3DPrint.MOD_ID + ":textures/gui/machine.png"));

    private static final int ENERGY_X = 11, ENERGY_Y = 18, ENERGY_W = 12, ENERGY_H = 50;
    private static final int FU_X = 177, FU_Y = 18, FU_W = 12, FU_H = 50;

    // Spinning reel sprite parked in the spare top-right of machine.png.
    private static final int REEL_U = 208, REEL_V = 0, REEL_SIZE = 32;
    private static final int REEL_CX = 100, REEL_CY = 38;
    private static final float REEL_STEP = 9f; // degrees per client tick while winding

    private static final int STATUS_Y = 55;
    // Three info-card wells: x origins, shared y/size (match build_machine).
    private static final int[] CARD_X = {10, 71, 132};
    private static final int CARD_Y = 68, CARD_W = 58, CARD_H = 28, CARD_PAD = 4;

    // Dark tech-console code colors.
    private static final int LABEL = 0xFFC0C0C8;
    private static final int LABEL_DIM = 0xFF7D8597;
    private static final int ACCENT = 0xFF3FE0C0;
    private static final int ACCENT_DIM = 0xFF9FE9D8;
    private static final int WARN = 0xFFE57A7A;
    private static final int FILL_ENERGY = 0xFFD32F2F;
    private static final int FILL_FILAMENT = 0xFF4FC3F7;

    private float reelAngle;

    public WinderScreen(WinderMenu menu, Inventory playerInventory, Component title) {
        // 26.1 made imageWidth/imageHeight final; dimensions go through the 5-arg super.
        //? if >=26.1 {
        /*super(menu, playerInventory, title, 200, 188);
        *///?} else {
        super(menu, playerInventory, title);
        //?}
        //? if <26.1 {
        this.imageWidth = 200;
        //?}
        //? if <26.1 {
        this.imageHeight = 188;
        //?}
        this.inventoryLabelX = 19;
        this.inventoryLabelY = 97;
    }

    @Override
    protected void containerTick() {
        if (isWinding()) {
            reelAngle = (reelAngle + REEL_STEP) % 360f;
        }
    }

    private boolean isWinding() {
        return menu.status() == WinderBlockEntity.STATUS_OK
                && !menu.inputStack().isEmpty()
                && menu.spoolStack().getItem() instanceof SpoolItem
                && menu.spoolFu() < menu.spoolCapacity()
                && menu.energy() > 0
                && menu.yieldPerItem() > 0;
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

        if (isHovering(ENERGY_X, ENERGY_Y, ENERGY_W, ENERGY_H, mouseX, mouseY)) {
            RenderCompat.tooltip(graphics, font,
                    Component.translatable("tooltip.mc3dprint.energy", menu.energy(), menu.maxEnergy()),
                    mouseX, mouseY);
        }
        if (isHovering(FU_X, FU_Y, FU_W, FU_H, mouseX, mouseY)) {
            RenderCompat.tooltip(graphics, font,
                    Component.translatable("tooltip.mc3dprint.fu", menu.spoolFu(), menu.spoolCapacity()),
                    mouseX, mouseY);
        }
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

        drawReel(graphics, left, top, partialTick);
    }

    /** The spool-end reel sprite, spinning while winding and dimmed/still when idle. */
    private void drawReel(GuiGraphics graphics, int left, int top, float partialTick) {
        boolean winding = isWinding();
        float angle = reelAngle + (winding ? partialTick * REEL_STEP : 0f);
        int tint = winding ? 0xFFFFFFFF : 0xFF8C8C8C; // idle reel dimmed (~0.55 grey)
        //? if >=1.21.5 {
        /*var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate((float) (left + REEL_CX), (float) (top + REEL_CY));
        pose.rotate((float) Math.toRadians(angle));
        RenderCompat.blitColored(graphics, TEXTURE, -REEL_SIZE / 2, -REEL_SIZE / 2, REEL_U, REEL_V, REEL_SIZE, REEL_SIZE, tint);
        pose.popMatrix();
        *///?} else {
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(left + REEL_CX, top + REEL_CY, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(angle));
        RenderCompat.blitColored(graphics, TEXTURE, -REEL_SIZE / 2, -REEL_SIZE / 2, REEL_U, REEL_V, REEL_SIZE, REEL_SIZE, tint);
        pose.popPose();
        //?}
    }

    @Override
    //? if >=26.1 {
    /*protected void extractLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    *///?} else {
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    //?}
        RenderCompat.drawString(graphics, font, title, titleLabelX, titleLabelY, LABEL, false);
        RenderCompat.drawString(graphics, font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, LABEL, false);

        drawStatusLine(graphics);
        drawMaterialCard(graphics);
        drawSpoolCard(graphics);
        drawRateCard(graphics);
    }

    private void drawStatusLine(GuiGraphics graphics) {
        Component msg;
        int color;
        int status = menu.status();
        if (status == WinderBlockEntity.STATUS_WRONG_TIER) {
            msg = Component.translatable("gui.mc3dprint.winder.requires_tier", menu.requiredTier());
            color = WARN;
        } else if (status == WinderBlockEntity.STATUS_NOT_CONVERTIBLE) {
            msg = Component.translatable("gui.mc3dprint.winder.not_convertible");
            color = WARN;
        } else if (isWinding()) {
            msg = Component.translatable("gui.mc3dprint.winder.winding");
            color = ACCENT;
        } else if (menu.inputStack().isEmpty()) {
            msg = Component.translatable("gui.mc3dprint.winder.idle");
            color = LABEL_DIM;
        } else if (menu.spoolStack().getItem() instanceof SpoolItem && menu.spoolFu() >= menu.spoolCapacity()) {
            msg = Component.translatable("gui.mc3dprint.winder.spool_full");
            color = LABEL_DIM;
        } else if (menu.energy() <= 0) {
            msg = Component.translatable("gui.mc3dprint.winder.no_power");
            color = WARN;
        } else {
            msg = Component.translatable("gui.mc3dprint.winder.ready");
            color = ACCENT;
        }
        int x = (imageWidth - font.width(msg)) / 2;
        RenderCompat.drawString(graphics, font, msg, x, STATUS_Y, color, false);
    }

    private void drawMaterialCard(GuiGraphics graphics) {
        int x = CARD_X[0];
        drawCardHeader(graphics, x, Component.translatable("gui.mc3dprint.winder.material"));
        ItemStack input = menu.inputStack();
        if (input.isEmpty()) {
            drawCardValue(graphics, x, "—");
            return;
        }
        String name = font.plainSubstrByWidth(input.getHoverName().getString(),
                (int) ((CARD_W - 2 * CARD_PAD) / 0.9f));
        drawCardValue(graphics, x, name);
        int tier = menu.requiredTier();
        int yield = menu.yieldPerItem();
        String sub = "T" + tier + (yield > 0 ? " · " + fmtK(yield) + " FU" : "");
        drawCardSub(graphics, x, sub);
    }

    private void drawSpoolCard(GuiGraphics graphics) {
        int x = CARD_X[1];
        drawCardHeader(graphics, x, Component.translatable("gui.mc3dprint.winder.spool"));
        if (!(menu.spoolStack().getItem() instanceof SpoolItem spool)) {
            drawCardValue(graphics, x, "—");
            return;
        }
        int fu = menu.spoolFu();
        int cap = Math.max(1, menu.spoolCapacity());
        drawCardValue(graphics, x, fmtK(fu) + " / " + fmtK(cap));
        int pct = (int) ((long) fu * 100 / cap);
        drawCardSub(graphics, x, "T" + spool.tier() + " · " + pct + "%");
    }

    private void drawRateCard(GuiGraphics graphics) {
        int x = CARD_X[2];
        drawCardHeader(graphics, x, Component.translatable("gui.mc3dprint.winder.rate"));
        int yield = menu.yieldPerItem();
        int rate = yield > 0 ? Math.round(yield * 20f / menu.maxProgress()) : 0;
        if (rate <= 0) {
            drawCardValue(graphics, x, "—");
            return;
        }
        drawCardValue(graphics, x, fmtK(rate) + " FU/s");
        int room = Math.max(0, menu.spoolCapacity() - menu.spoolFu());
        int secs = room / rate;
        drawCardSub(graphics, x, "~" + fmtK(secs) + "s full");
    }

    private void drawCardHeader(GuiGraphics graphics, int x, Component header) {
        drawScaled(graphics, header.getString(), x + CARD_PAD, CARD_Y + 3, 0.75f, LABEL_DIM);
    }

    private void drawCardValue(GuiGraphics graphics, int x, String value) {
        drawScaled(graphics, value, x + CARD_PAD, CARD_Y + 11, 0.9f, LABEL);
    }

    private void drawCardSub(GuiGraphics graphics, int x, String sub) {
        drawScaled(graphics, sub, x + CARD_PAD, CARD_Y + 20, 0.75f, ACCENT_DIM);
    }

    private void drawScaled(GuiGraphics graphics, String text, int x, int y, float scale, int color) {
        //? if >=1.21.5 {
        /*var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate((float) x, (float) y);
        pose.scale(scale, scale);
        RenderCompat.drawString(graphics, font, text, 0, 0, color, false);
        pose.popMatrix();
        *///?} else {
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(scale, scale, 1f);
        RenderCompat.drawString(graphics, font, text, 0, 0, color, false);
        pose.popPose();
        //?}
    }

    /** Compact FU formatter: 850, 12k, 1.2k, 3.4M. */
    private static String fmtK(int v) {
        if (v < 1000) {
            return Integer.toString(v);
        }
        if (v < 1_000_000) {
            return v % 1000 == 0 ? (v / 1000) + "k" : String.format("%.1fk", v / 1000.0);
        }
        return String.format("%.1fM", v / 1_000_000.0);
    }
}
