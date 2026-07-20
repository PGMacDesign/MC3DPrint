package com.pgmacdesign.mc3dprint.client;

import com.pgmacdesign.mc3dprint.compat.RenderCompat;
import com.pgmacdesign.mc3dprint.fu.FuValue;
import com.pgmacdesign.mc3dprint.fu.FuValueRegistry;
import com.pgmacdesign.mc3dprint.machine.sorter.SorterBlockEntity;
import com.pgmacdesign.mc3dprint.machine.sorter.SorterMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * Filament Tier Item Sorter GUI: the nine-slot pool up top, a computed per-tier readout
 * ("T3: 12 waiting · 2 winders"), then the player inventory. The background is drawn
 * procedurally (no texture) so there is nothing to keep in style-pack lockstep. The readout
 * combines a client-side pool scan (waiting counts) with a server-synced winder census.
 */
public class SorterScreen extends AbstractContainerScreen<SorterMenu> {

    private static final int PANEL = 0xFF1A1F2B;
    private static final int BEVEL_L = 0xFF2C3342;
    private static final int BEVEL_D = 0xFF0A0D14;
    private static final int WELL = 0xFF10141E;
    private static final int LABEL = 0xFFC0C0C8;
    private static final int LABEL_DIM = 0xFF7D8597;
    private static final int ACCENT = 0xFF3FE0C0;
    private static final int WARN = 0xFFE57A7A;

    private static final int READOUT_X = 8, READOUT_Y = 42, LINE_H = 10;

    public SorterScreen(SorterMenu menu, Inventory playerInventory, Component title) {
        // 26.1 made imageWidth/imageHeight final; dimensions go through the 5-arg super.
        //? if >=26.1 {
        /*super(menu, playerInventory, title, 176, 194);
        *///?} else {
        super(menu, playerInventory, title);
        //?}
        //? if <26.1 {
        this.imageWidth = 176;
        //?}
        //? if <26.1 {
        this.imageHeight = 194;
        //?}
        this.inventoryLabelX = 8;
        this.inventoryLabelY = 101;
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
        graphics.fill(left, top, left + imageWidth, top + imageHeight, PANEL);
        graphics.fill(left, top, left + imageWidth, top + 1, BEVEL_L);
        graphics.fill(left, top, left + 1, top + imageHeight, BEVEL_L);
        graphics.fill(left + imageWidth - 1, top, left + imageWidth, top + imageHeight, BEVEL_D);
        graphics.fill(left, top + imageHeight - 1, left + imageWidth, top + imageHeight, BEVEL_D);

        // pool row wells
        for (int col = 0; col < SorterBlockEntity.POOL_SLOTS; col++) {
            slotWell(graphics, left + SorterMenu.POOL_X + col * 18, top + SorterMenu.POOL_Y);
        }
        // player inventory wells (3 rows + hotbar)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                slotWell(graphics, left + 8 + col * 18, top + 112 + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            slotWell(graphics, left + 8 + col * 18, top + 172);
        }
    }

    private static void slotWell(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, WELL);
        graphics.fill(x - 1, y - 1, x + 17, y, BEVEL_D);
        graphics.fill(x - 1, y - 1, x, y + 17, BEVEL_D);
        graphics.fill(x + 16, y - 1, x + 17, y + 17, BEVEL_L);
        graphics.fill(x - 1, y + 16, x + 17, y + 17, BEVEL_L);
    }

    @Override
    //? if >=26.1 {
    /*protected void extractLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    *///?} else {
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    //?}
        RenderCompat.drawString(graphics, font, title, titleLabelX, titleLabelY, LABEL, false);
        RenderCompat.drawString(graphics, font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, LABEL, false);

        int[] waiting = new int[SorterBlockEntity.MAX_TIER + 1];
        for (int slot = 0; slot < SorterBlockEntity.POOL_SLOTS; slot++) {
            ItemStack stack = menu.poolStack(slot);
            if (stack.isEmpty()) {
                continue;
            }
            Optional<FuValue> value = FuValueRegistry.valueOf(stack);
            value.ifPresent(v -> {
                if (v.tier() >= 1 && v.tier() <= SorterBlockEntity.MAX_TIER) {
                    waiting[v.tier()] += stack.getCount();
                }
            });
        }

        int line = 0;
        for (int tier = 1; tier <= SorterBlockEntity.MAX_TIER; tier++) {
            int winders = menu.winderCount(tier);
            if (waiting[tier] == 0 && winders == 0) {
                continue;
            }
            Component text = winders > 0
                    ? Component.translatable("gui.mc3dprint.sorter.tier_status", tier, waiting[tier], winders)
                    : Component.translatable("gui.mc3dprint.sorter.tier_status_nowinder", tier, waiting[tier]);
            RenderCompat.drawString(graphics, font, text, READOUT_X, READOUT_Y + line * LINE_H,
                    winders > 0 ? LABEL : WARN, false);
            line++;
        }
        if (line == 0) {
            RenderCompat.drawString(graphics, font, Component.translatable("gui.mc3dprint.sorter.idle"),
                    READOUT_X, READOUT_Y, LABEL_DIM, false);
        }
    }
}
