package com.pgmacdesign.mc3dprint.compat;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
//? if >=1.21.5 {
/*import net.minecraft.client.renderer.RenderPipelines;
*///?}

/**
 * Version seam for the 2D GUI render API, which the 1.21.2 + 1.21.5 render-pipeline rewrites
 * reshaped heavily:
 * <ul>
 *   <li>{@code GuiGraphics.blit(ResourceLocation, x, y, u, v, w, h)} now requires a
 *       {@code RenderPipeline} and an explicit texture size: {@code blit(GUI_TEXTURED, rl, x, y,
 *       (float)u, (float)v, w, h, texW, texH)}.</li>
 *   <li>The immediate-mode {@code renderTooltip(Font, …)} overloads became the deferred
 *       {@code setTooltipForNextFrame(…)} family (the {@code Font} arg is implicit for the simple
 *       overloads).</li>
 * </ul>
 * Call sites route through these helpers; the divergence is localized here. (The
 * {@code PoseStack}→{@code Matrix3x2fStack} change for {@code GuiGraphics.pose()} and the
 * {@code BlockEntityRenderer.render} {@code Vec3} param are per-site signature guards, not helpers.)
 */
public final class RenderCompat {
    private RenderCompat() {}

    /** Texture size the legacy 7-arg {@code blit} implied (GUI sheets are 256×256). */
    private static final int TEX = 256;

    /** Blit a 256×256-sheet GUI texture region (legacy 7-arg blit). */
    public static void blit(GuiGraphics g, ResourceLocation tex, int x, int y, int u, int v, int w, int h) {
        //? if >=1.21.5 {
        /*g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, (float) u, (float) v, w, h, TEX, TEX);
        *///?} else {
        g.blit(tex, x, y, u, v, w, h);
        //?}
    }

    /** Blit a 256×256-sheet GUI texture region with an ARGB tint. 1.21.5 removed
     *  {@code GuiGraphics.setColor}; the tint is now a blit argument. */
    public static void blitColored(GuiGraphics g, ResourceLocation tex, int x, int y, int u, int v, int w, int h, int argb) {
        //? if >=1.21.5 {
        /*g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, (float) u, (float) v, w, h, TEX, TEX, argb);
        *///?} else {
        float a = ((argb >> 24) & 0xFF) / 255f, r = ((argb >> 16) & 0xFF) / 255f,
                gr = ((argb >> 8) & 0xFF) / 255f, b = (argb & 0xFF) / 255f;
        g.setColor(r, gr, b, a);
        g.blit(tex, x, y, u, v, w, h);
        g.setColor(1f, 1f, 1f, 1f);
        //?}
    }

    /** Single-line component tooltip. */
    public static void tooltip(GuiGraphics g, Font font, Component text, int x, int y) {
        //? if >=1.21.5 {
        /*g.setTooltipForNextFrame(text, x, y);
        *///?} else {
        g.renderTooltip(font, text, x, y);
        //?}
    }

    /** Pre-wrapped (FormattedCharSequence) multi-line tooltip. */
    public static void tooltipLines(GuiGraphics g, Font font, List<FormattedCharSequence> lines, int x, int y) {
        //? if >=1.21.5 {
        /*g.setTooltipForNextFrame(lines, x, y);
        *///?} else {
        g.renderTooltip(font, lines, x, y);
        //?}
    }

    /** Component-list tooltip (the old {@code renderComponentTooltip}). */
    public static void tooltipComponents(GuiGraphics g, Font font, List<Component> lines, int x, int y) {
        //? if >=1.21.5 {
        /*g.setTooltipForNextFrame(font, lines, java.util.Optional.empty(), net.minecraft.world.item.ItemStack.EMPTY, x, y);
        *///?} else {
        g.renderComponentTooltip(font, lines, x, y);
        //?}
    }
}
