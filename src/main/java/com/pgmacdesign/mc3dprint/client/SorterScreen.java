package com.pgmacdesign.mc3dprint.client;

//? if <1.21.5 {
import com.mojang.math.Axis;
//?}
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
 * Filament Tier Item Sorter GUI: the nine-slot pool up top, an always-eight-line per-tier readout
 * ("T3  12 waiting · 2 winder(s)"), a status line and a spinning rotor, then the player hotbar.
 * The background is drawn procedurally (no texture) so there is nothing to keep in style-pack
 * lockstep. The readout combines a client-side pool scan (waiting counts) with a server-synced
 * winder census.
 *
 * <p>The player's three main inventory rows are deliberately hidden ({@link SorterMenu} marks those
 * slots inactive) to give the readout its full eight lines without a taller window. They stay in the
 * menu, so shift-clicking out of the pool still fills the whole inventory — {@code moveItemStackTo}
 * ignores {@code Slot#isActive()} while rendering and hit-testing honour it.
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
    private static final int STATUS_X = 8, STATUS_Y = 134;

    // Procedural sorting rotor: a 4-armed cross in a recessed housing, spinning while routing.
    private static final int ROTOR_CX = 150, ROTOR_CY = 138, ROTOR_HOUSING = 26;
    private static final float ROTOR_STEP = 8f; // degrees per client tick while sorting

    private float rotorAngle;

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
        this.inventoryLabelY = SorterMenu.HOTBAR_Y - 11;
    }

    @Override
    protected void containerTick() {
        if (isSorting()) {
            rotorAngle = (rotorAngle + ROTOR_STEP) % 360f;
        }
    }

    /** Per-tier item counts currently sitting in the pool, indexed by tier (0 unused). */
    private int[] waitingByTier() {
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
        return waiting;
    }

    /** True when at least one queued tier has somewhere to go — the rotor's spin condition. */
    private boolean isSorting() {
        int[] waiting = waitingByTier();
        for (int tier = 1; tier <= SorterBlockEntity.MAX_TIER; tier++) {
            if (waiting[tier] > 0 && menu.winderCount(tier) > 0) {
                return true;
            }
        }
        return false;
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

        for (int col = 0; col < SorterBlockEntity.POOL_SLOTS; col++) {
            well(graphics, left + SorterMenu.POOL_X + col * 18, top + SorterMenu.POOL_Y, 18, 18);
        }
        // Only the hotbar row is drawn; the three main rows are hidden (see class javadoc).
        for (int col = 0; col < 9; col++) {
            well(graphics, left + SorterMenu.INV_X + col * 18, top + SorterMenu.HOTBAR_Y, 18, 18);
        }

        well(graphics, left + ROTOR_CX - ROTOR_HOUSING / 2, top + ROTOR_CY - ROTOR_HOUSING / 2,
                ROTOR_HOUSING, ROTOR_HOUSING);
        drawRotor(graphics, left, top, partialTick);
    }

    /** Recessed slot-style well; {@code x}/{@code y} are the well's outer top-left. */
    private static void well(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x - 1, y - 1, x + w - 1, y + h - 1, WELL);
        graphics.fill(x - 1, y - 1, x + w - 1, y, BEVEL_D);
        graphics.fill(x - 1, y - 1, x, y + h - 1, BEVEL_D);
        graphics.fill(x + w - 2, y - 1, x + w - 1, y + h - 1, BEVEL_L);
        graphics.fill(x - 1, y + h - 2, x + w - 1, y + h - 1, BEVEL_L);
    }

    /**
     * Four-armed rotor drawn with plain fills under a rotated pose — procedural on purpose, so the
     * sorter stays texture-free (and therefore style-pack-free). One arm is accented so the spin
     * direction reads at a glance.
     */
    private void drawRotor(GuiGraphics graphics, int left, int top, float partialTick) {
        boolean sorting = isSorting();
        float angle = rotorAngle + (sorting ? partialTick * ROTOR_STEP : 0f);
        int arm = sorting ? LABEL : LABEL_DIM;
        int lead = sorting ? ACCENT : LABEL_DIM;
        //? if >=1.21.5 {
        /*var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate((float) (left + ROTOR_CX), (float) (top + ROTOR_CY));
        pose.rotate((float) Math.toRadians(angle));
        rotorArms(graphics, arm, lead);
        pose.popMatrix();
        *///?} else {
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(left + ROTOR_CX, top + ROTOR_CY, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(angle));
        rotorArms(graphics, arm, lead);
        pose.popPose();
        //?}
    }

    private static void rotorArms(GuiGraphics graphics, int arm, int lead) {
        graphics.fill(4, -2, 11, 2, lead);
        graphics.fill(-11, -2, -4, 2, arm);
        graphics.fill(-2, 4, 2, 11, arm);
        graphics.fill(-2, -11, 2, -4, arm);
        graphics.fill(-3, -3, 3, 3, arm);
    }

    @Override
    //? if >=26.1 {
    /*protected void extractLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    *///?} else {
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    //?}
        RenderCompat.drawString(graphics, font, title, titleLabelX, titleLabelY, LABEL, false);
        RenderCompat.drawString(graphics, font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, LABEL, false);

        int[] waiting = waitingByTier();
        boolean anyRouting = false;
        boolean anyStuck = false;

        // Every tier keeps a fixed row so the readout never reflows as items come and go.
        for (int tier = 1; tier <= SorterBlockEntity.MAX_TIER; tier++) {
            int winders = menu.winderCount(tier);
            int queued = waiting[tier];
            Component text;
            int color;
            if (queued == 0 && winders == 0) {
                text = Component.translatable("gui.mc3dprint.sorter.tier_empty", tier);
                color = LABEL_DIM;
            } else if (winders == 0) {
                text = Component.translatable("gui.mc3dprint.sorter.tier_status_nowinder", tier, queued);
                color = WARN;
                anyStuck = true;
            } else {
                text = Component.translatable("gui.mc3dprint.sorter.tier_status", tier, queued, winders);
                color = queued > 0 ? ACCENT : LABEL;
                anyRouting |= queued > 0;
            }
            RenderCompat.drawString(graphics, font, text, READOUT_X, READOUT_Y + (tier - 1) * LINE_H, color, false);
        }

        Component status;
        int statusColor;
        if (anyRouting) {
            status = Component.translatable("gui.mc3dprint.sorter.sorting");
            statusColor = ACCENT;
        } else if (anyStuck) {
            status = Component.translatable("gui.mc3dprint.sorter.no_route");
            statusColor = WARN;
        } else {
            status = Component.translatable("gui.mc3dprint.sorter.idle");
            statusColor = LABEL_DIM;
        }
        RenderCompat.drawString(graphics, font, status, STATUS_X, STATUS_Y, statusColor, false);
    }
}
