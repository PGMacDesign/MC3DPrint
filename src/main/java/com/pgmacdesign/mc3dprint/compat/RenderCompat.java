package com.pgmacdesign.mc3dprint.compat;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
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

    /** Draws an item icon; 26.1 renamed the {@code render*} family to nouns, so this is {@code item}. */
    public static void item(GuiGraphics g, net.minecraft.world.item.ItemStack stack, int x, int y) {
        //? if >=26.1 {
        /*g.item(stack, x, y);
        *///?} else {
        g.renderItem(stack, x, y);
        //?}
    }

    /** Shadow-controlled string draw; 26.1 renamed the {@code drawString} family to {@code text}. */
    public static void drawString(GuiGraphics g, Font font, Component text, int x, int y, int color, boolean shadow) {
        //? if >=26.1 {
        /*g.text(font, text, x, y, color, shadow);
        *///?} else {
        g.drawString(font, text, x, y, color, shadow);
        //?}
    }

    /** String overload of {@link #drawString(GuiGraphics, Font, Component, int, int, int, boolean)}. */
    public static void drawString(GuiGraphics g, Font font, String text, int x, int y, int color, boolean shadow) {
        //? if >=26.1 {
        /*g.text(font, text, x, y, color, shadow);
        *///?} else {
        g.drawString(font, text, x, y, color, shadow);
        //?}
    }

    /** Word-wrapped shadowless draw; 26.1 renamed {@code drawWordWrap} to {@code textWithWordWrap},
     *  whose short overload defaults to a drop shadow — pass the explicit no-shadow flag to keep
     *  the pre-26.1 (shadowless) appearance. */
    public static void drawWordWrap(GuiGraphics g, Font font, FormattedText text, int x, int y, int width, int color) {
        //? if >=26.1 {
        /*g.textWithWordWrap(font, text, x, y, width, color, false);
        *///?} else {
        g.drawWordWrap(font, text, x, y, width, color);
        //?}
    }

    /**
     * Emits a 12-edge wireframe box into a {@code RenderType.lines()} buffer — a hand-rolled,
     * version-neutral replacement for {@code LevelRenderer/ShapeRenderer.renderLineBox}, whose
     * owner and signature changed at 1.21.5 and 1.21.9 and which was removed outright in
     * 1.21.11 (superseded by the debug-only Gizmos API, wrong tool for gameplay overlays).
     * Normals follow the lines-shader convention: each vertex's normal is the edge direction.
     */
    public static void lineBox(com.mojang.blaze3d.vertex.PoseStack.Pose pose,
                               com.mojang.blaze3d.vertex.VertexConsumer lines,
                               double x0, double y0, double z0, double x1, double y1, double z1,
                               float r, float g, float b, float a) {
        float fx0 = (float) x0, fy0 = (float) y0, fz0 = (float) z0;
        float fx1 = (float) x1, fy1 = (float) y1, fz1 = (float) z1;
        // 4 edges along X
        edge(pose, lines, fx0, fy0, fz0, fx1, fy0, fz0, r, g, b, a, 1, 0, 0);
        edge(pose, lines, fx0, fy1, fz0, fx1, fy1, fz0, r, g, b, a, 1, 0, 0);
        edge(pose, lines, fx0, fy0, fz1, fx1, fy0, fz1, r, g, b, a, 1, 0, 0);
        edge(pose, lines, fx0, fy1, fz1, fx1, fy1, fz1, r, g, b, a, 1, 0, 0);
        // 4 edges along Y
        edge(pose, lines, fx0, fy0, fz0, fx0, fy1, fz0, r, g, b, a, 0, 1, 0);
        edge(pose, lines, fx1, fy0, fz0, fx1, fy1, fz0, r, g, b, a, 0, 1, 0);
        edge(pose, lines, fx0, fy0, fz1, fx0, fy1, fz1, r, g, b, a, 0, 1, 0);
        edge(pose, lines, fx1, fy0, fz1, fx1, fy1, fz1, r, g, b, a, 0, 1, 0);
        // 4 edges along Z
        edge(pose, lines, fx0, fy0, fz0, fx0, fy0, fz1, r, g, b, a, 0, 0, 1);
        edge(pose, lines, fx1, fy0, fz0, fx1, fy0, fz1, r, g, b, a, 0, 0, 1);
        edge(pose, lines, fx0, fy1, fz0, fx0, fy1, fz1, r, g, b, a, 0, 0, 1);
        edge(pose, lines, fx1, fy1, fz0, fx1, fy1, fz1, r, g, b, a, 0, 0, 1);
    }

    private static void edge(com.mojang.blaze3d.vertex.PoseStack.Pose pose,
                             com.mojang.blaze3d.vertex.VertexConsumer c,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float r, float g, float b, float a, float nx, float ny, float nz) {
        lineVertex(pose, c, x0, y0, z0, r, g, b, a, nx, ny, nz);
        lineVertex(pose, c, x1, y1, z1, r, g, b, a, nx, ny, nz);
    }

    /**
     * Width for our line overlays. Line width used to be pipeline state set once for the whole
     * {@code RenderType.lines()} pass; 1.21.11 moved it into the vertex, so every vertex now
     * carries its own. Kept at one value so the wireframes look the same as before the move.
     */
    private static final float LINE_WIDTH = 2.0F;

    /**
     * One line vertex, with every element the target version's line format demands.
     *
     * <p>1.21.11 added {@code LINE_WIDTH} to the lines vertex format
     * ({@code POSITION_COLOR_NORMAL_LINE_WIDTH}). A vertex missing it is not rejected where it is
     * written: the buffer only notices when the NEXT vertex starts, and then throws
     * {@code IllegalStateException: Missing elements in vertex} from inside the render frame,
     * which takes the client down. Docking a spool crashed the game for exactly this reason.
     *
     * <p>Both of the mod's line emitters go through here so the version split exists once. Adding
     * a second one that writes {@code addVertex(...).setColor(...).setNormal(...)} by hand would
     * compile on every version and crash only on the ones whose format demands the element, and
     * only while that overlay is on screen. {@code LineVertexFormatTest} guards the boundary.
     */
    public static void lineVertex(com.mojang.blaze3d.vertex.PoseStack.Pose pose,
                                  com.mojang.blaze3d.vertex.VertexConsumer c,
                                  float x, float y, float z,
                                  float r, float g, float b, float a,
                                  float nx, float ny, float nz) {
        //? if >=1.21.11 {
        /*c.addVertex(pose, x, y, z).setColor(r, g, b, a).setNormal(pose, nx, ny, nz)
                .setLineWidth(LINE_WIDTH);
        *///?} else {
        c.addVertex(pose, x, y, z).setColor(r, g, b, a).setNormal(pose, nx, ny, nz);
        //?}
    }
}
