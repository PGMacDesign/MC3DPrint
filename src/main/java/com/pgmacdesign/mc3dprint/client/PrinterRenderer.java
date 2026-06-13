package com.pgmacdesign.mc3dprint.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.machine.PrintJob;
import com.pgmacdesign.mc3dprint.machine.PrinterBlockEntity;
import com.pgmacdesign.mc3dprint.machine.multiblock.ControllerBlock;
import com.pgmacdesign.mc3dprint.machine.multiblock.MultiblockPattern;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Print-job visualization — "defined but magical." A textured dark-metal frame
 * + rails surround the print volume; a small machined extruder head travels the
 * gantry tracking the actual placement progress with ease-in/ease-out and
 * partialTick interpolation; a glowing cyan filament strand trails the head
 * (a capped ring buffer of recently-laid segments fading oldest->dim) and the
 * hotend glows brightest at the nozzle. Idle = a slow breathing pulse on the
 * hotend with a near-still head.
 *
 * <p>Two RenderTypes carry the geometry:
 * <ul>
 *   <li>{@link RenderType#entitySolid} (lit by world light) for the structural
 *       frame, rails and head — the "defined" jump from the old wireframe.</li>
 *   <li>{@link RenderType#eyes} (additive, fullbright, ignores world light) for
 *       the cyan hotend + laid-filament glow — the "magical" emissive part.</li>
 * </ul>
 *
 * <p>All interpolation and the laid-filament trail are computed client-side and
 * stored in {@link #HEAD_STATES} keyed by block position, so the block entity
 * (and the 58 GameTests over its print/economy logic) is untouched. We only
 * read {@link PrinterBlockEntity#lastPlacedPos()}, already synced for the head.
 */
public class PrinterRenderer implements BlockEntityRenderer<PrinterBlockEntity> {
    // Dark-metal texture for frame/rails/head, reused from the block set.
    private static final ResourceLocation METAL =
            new ResourceLocation(MC3DPrint.MOD_ID, "textures/block/printer_casing.png");
    // Vanilla 1x1 fully-white, fully-opaque texture. Feeding the additive
    // RenderType.eyes a white texel lets the vertex color (cyan) drive the glow
    // output cleanly — a dark/transparent casing texel would muddy or hide it.
    private static final ResourceLocation WHITE =
            new ResourceLocation("minecraft", "textures/misc/white.png");

    // Hero cyan glow (brief palette: #5CC8FF core, falling to #1E7FCF).
    private static final float GLOW_R = 0.36F, GLOW_G = 0.78F, GLOW_B = 1.00F;
    // Frame tint: a slight cool grey multiply over the metal texture.
    private static final float FRAME_R = 0.62F, FRAME_G = 0.66F, FRAME_B = 0.72F;
    // Head tint: lighter machined grey so it reads against the darker frame.
    private static final float HEAD_R = 0.82F, HEAD_G = 0.85F, HEAD_B = 0.90F;

    private static final float RAIL = 0.025F;  // half-thickness of frame struts/rails
    private static final float HEAD = 0.13F;   // half-size of the extruder head box
    private static final int MAX_TRAIL = 64;   // capped laid-filament ring buffer

    /** Spool slot index -> the side face it docks on. */
    private static final net.minecraft.core.Direction[] SPOOL_FACES = {
            net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.EAST,
            net.minecraft.core.Direction.SOUTH, net.minecraft.core.Direction.WEST};

    private static final int CIRCLE_SEGMENTS = 16;
    private static final float FLANGE_RADIUS = 0.30F;
    private static final float AXLE_RADIUS = 0.08F;
    private static final float WINDING_MAX_EXTRA = 0.18F;

    /**
     * Wound-filament accent color per spool tier (T1..T8), normalized from the
     * brief's RGB accents. Index 0 = T1. Used to tint a docked spool's reel so
     * its tier reads at a glance; creative spools stay magenta (see below).
     */
    private static final float[][] TIER_COLORS = {
            {138 / 255f, 148 / 255f, 160 / 255f}, // T1
            {79 / 255f, 155 / 255f, 232 / 255f},  // T2
            {52 / 255f, 192 / 255f, 192 / 255f},  // T3
            {70 / 255f, 198 / 255f, 107 / 255f},  // T4
            {224 / 255f, 180 / 255f, 58 / 255f},  // T5
            {232 / 255f, 122 / 255f, 58 / 255f},  // T6
            {155 / 255f, 107 / 255f, 232 / 255f}, // T7
            {232 / 255f, 79 / 255f, 176 / 255f},  // T8
    };

    private static float[] tierColor(int tier) {
        int idx = Mth.clamp(tier - 1, 0, TIER_COLORS.length - 1);
        return TIER_COLORS[idx];
    }

    /**
     * Per-printer client render state: the smoothed head position and the trail
     * of laid filament segments. Keyed by the BE position. A static map is the
     * standard pattern for per-BE client-only animation state that must not live
     * on the (server-authoritative, save-synced) block entity.
     */
    private static final class HeadState {
        // current/previous smoothed head position, in machine-local space.
        double curX, curY, curZ;
        double prevX, prevY, prevZ;
        // the head's destination (block-center above the last placement).
        double tgtX, tgtY, tgtZ;
        boolean initialized;
        // null until the first placement we've seen; lets us detect new segments.
        BlockPos lastSeen;
        // ring buffer of laid segments (local block-center coords), newest last.
        final Deque<double[]> trail = new ArrayDeque<>();

        void retarget(double x, double y, double z) {
            tgtX = x;
            tgtY = y;
            tgtZ = z;
            if (!initialized) {
                curX = prevX = x;
                curY = prevY = y;
                curZ = prevZ = z;
                initialized = true;
            }
        }

        void addSegment(double x, double y, double z) {
            trail.addLast(new double[]{x, y, z});
            while (trail.size() > MAX_TRAIL) {
                trail.removeFirst(); // drop the oldest = farthest-cooled segment
            }
        }
    }

    private static final Map<BlockPos, HeadState> HEAD_STATES = new HashMap<>();

    public PrinterRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(PrinterBlockEntity printer, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        renderSpools(printer, partialTick, poseStack, bufferSource);
        renderFormedStructure(printer, partialTick, poseStack, bufferSource, packedLight);

        PrintJob job = printer.activeJob();
        if (job == null) {
            // Job finished/idle — forget any cached head animation for this BE so
            // a fresh job restarts cleanly and the map doesn't grow unbounded.
            HEAD_STATES.remove(printer.getBlockPos());
            renderPreview(printer, poseStack, bufferSource);
            return;
        }

        BlockPos machine = printer.getBlockPos();
        BlockPos origin = job.origin();
        BlockPos size = job.size();

        // Print-volume bounds in machine-local space (block coords relative to BE).
        double minX = origin.getX() - machine.getX();
        double minY = origin.getY() - machine.getY();
        double minZ = origin.getZ() - machine.getZ();
        double maxX = minX + size.getX();
        double maxY = minY + size.getY();
        double maxZ = minZ + size.getZ();

        // Gantry plane sits just above the print volume's top.
        double gantryY = maxY + 0.25;

        // Grab each VertexConsumer exactly once per frame per RenderType.
        VertexConsumer solid = bufferSource.getBuffer(RenderType.entitySolid(METAL));
        VertexConsumer glow = bufferSource.getBuffer(RenderType.eyes(WHITE));

        renderFrame(poseStack, solid, packedLight, minX, minY, minZ, maxX, maxY, maxZ, gantryY);

        // --- Head tracking + interpolation (client-side) ---
        HeadState hs = HEAD_STATES.computeIfAbsent(machine, p -> new HeadState());
        BlockPos lastPlaced = printer.lastPlacedPos();
        if (lastPlaced != null) {
            double targetX = lastPlaced.getX() - machine.getX() + 0.5;
            double targetZ = lastPlaced.getZ() - machine.getZ() + 0.5;
            // Head rides the gantry plane; the strand drops to the placed layer.
            if (!lastPlaced.equals(hs.lastSeen)) {
                // A new block was placed: lay a glowing segment at its top face,
                // then retarget the head over it. Done on the placement *event*,
                // not every frame, so the trail advances one segment per block.
                hs.addSegment(targetX, lastPlaced.getY() - machine.getY() + 1.0, targetZ);
                hs.lastSeen = lastPlaced;
                // shift cur->prev so partialTick can ease from where we were.
                hs.prevX = hs.curX;
                hs.prevY = hs.curY;
                hs.prevZ = hs.curZ;
            }
            hs.retarget(targetX, gantryY, targetZ);
        } else {
            // Job just started, nothing placed yet: park the head centered on top.
            hs.retarget((minX + maxX) / 2.0, gantryY, (minZ + maxZ) / 2.0);
        }

        // Ease-in/ease-out toward the target with a fixed smoothing factor, then
        // partialTick-lerp between last frame's and this frame's eased position
        // so motion is frame-smooth rather than 20Hz-steppy. We advance the eased
        // state once per render (prev<-cur, cur<-ease(cur,tgt)).
        hs.prevX = hs.curX;
        hs.prevY = hs.curY;
        hs.prevZ = hs.curZ;
        final double ease = 0.22; // smoothing per frame; small = lazier, smoother
        hs.curX = easeToward(hs.curX, hs.tgtX, ease);
        hs.curY = easeToward(hs.curY, hs.tgtY, ease);
        hs.curZ = easeToward(hs.curZ, hs.tgtZ, ease);
        double headX = Mth.lerp(partialTick, hs.prevX, hs.curX);
        double headY = Mth.lerp(partialTick, hs.prevY, hs.curY);
        double headZ = Mth.lerp(partialTick, hs.prevZ, hs.curZ);

        // Rails through the live head position so the gantry "tracks" it.
        renderRails(poseStack, solid, packedLight, minX, minZ, maxX, maxZ, gantryY, headX, headZ);

        // The extruder head: a small machined box at the smoothed position.
        box(poseStack, solid, headX - HEAD, headY - HEAD, headZ - HEAD,
                headX + HEAD, headY + HEAD, headZ + HEAD,
                HEAD_R, HEAD_G, HEAD_B, 1.0F, packedLight);

        renderFilament(poseStack, glow, printer, hs, headX, headY, headZ, partialTick);
    }

    /** Eight struts of the print-volume frame plus a top-rim ring (textured, lit). */
    private void renderFrame(PoseStack poseStack, VertexConsumer solid, int light,
                             double minX, double minY, double minZ,
                             double maxX, double maxY, double maxZ, double gantryY) {
        // Four vertical corner posts.
        post(poseStack, solid, light, minX, minY, minZ, maxY);
        post(poseStack, solid, light, maxX, minY, minZ, maxY);
        post(poseStack, solid, light, minX, minY, maxZ, maxY);
        post(poseStack, solid, light, maxX, minY, maxZ, maxY);
        // Top frame rim (four beams) at the print-volume top.
        beamX(poseStack, solid, light, minX, maxY, minZ, maxX);
        beamX(poseStack, solid, light, minX, maxY, maxZ, maxX);
        beamZ(poseStack, solid, light, minX, maxY, minZ, maxZ);
        beamZ(poseStack, solid, light, maxX, maxY, minZ, maxZ);
        // Bottom frame rim so the cage reads as enclosed.
        beamX(poseStack, solid, light, minX, minY, minZ, maxX);
        beamX(poseStack, solid, light, minX, minY, maxZ, maxX);
        beamZ(poseStack, solid, light, minX, minY, minZ, maxZ);
        beamZ(poseStack, solid, light, maxX, minY, minZ, maxZ);
        // Gantry-plane rim, a touch above the top, where the head rides.
        beamX(poseStack, solid, light, minX, gantryY, minZ, maxX);
        beamX(poseStack, solid, light, minX, gantryY, maxZ, maxX);
        beamZ(poseStack, solid, light, minX, gantryY, minZ, maxZ);
        beamZ(poseStack, solid, light, maxX, gantryY, minZ, maxZ);
    }

    /** The two cross rails (X-arm and Z-arm) intersecting over the head. */
    private void renderRails(PoseStack poseStack, VertexConsumer solid, int light,
                             double minX, double minZ, double maxX, double maxZ,
                             double gantryY, double headX, double headZ) {
        // X-arm spans the full X extent at the head's Z; Z-arm spans Z at head's X.
        beamX(poseStack, solid, light, minX, gantryY, headZ, maxX);
        beamZ(poseStack, solid, light, headX, gantryY, minZ, maxZ);
    }

    /** Glowing cyan hotend + the laid-filament trail (additive, fullbright). */
    private void renderFilament(PoseStack poseStack, VertexConsumer glow,
                                PrinterBlockEntity printer, HeadState hs,
                                double headX, double headY, double headZ, float partialTick) {
        long gameTime = printer.getLevel() != null ? printer.getLevel().getGameTime() : 0;
        boolean printing = printer.state() == PrinterBlockEntity.State.PRINTING;

        // Idle = a slow breathing pulse on the hotend; printing = steady bright.
        float pulse = printing
                ? 1.0F
                : 0.45F + 0.30F * (0.5F + 0.5F * Mth.sin((gameTime + partialTick) * 0.10F));

        // Hotend: a small bright billboard-ish cross of quads at the nozzle. We
        // draw two perpendicular vertical quads so it reads as a glowing core
        // from any angle without needing true billboarding.
        float hot = pulse;
        double nozzleY = headY - HEAD; // bottom of the head box
        double r = 0.06; // hotend glow half-size
        // quad facing along Z
        emissiveQuad(poseStack, glow,
                headX - r, nozzleY - r, headZ, headX + r, nozzleY - r, headZ,
                headX + r, nozzleY + r, headZ, headX - r, nozzleY + r, headZ,
                GLOW_R, GLOW_G, GLOW_B, hot);
        // quad facing along X
        emissiveQuad(poseStack, glow,
                headX, nozzleY - r, headZ - r, headX, nozzleY - r, headZ + r,
                headX, nozzleY + r, headZ + r, headX, nozzleY + r, headZ - r,
                GLOW_R, GLOW_G, GLOW_B, hot);

        // Hot strand: a thin bright drop from the nozzle down to the layer it's
        // currently laying (the most recent trail segment), brightest at the top.
        double[] newest = hs.trail.peekLast();
        if (printing && newest != null) {
            strandQuad(poseStack, glow, headX, nozzleY, headZ,
                    newest[0], newest[1], newest[2], GLOW_R, GLOW_G, GLOW_B, 0.9F * hot);
        }

        // Laid trail: connect consecutive segments with thin glowing quads that
        // fade oldest->dim ("cooling filament"). Oldest at the deque head.
        int n = hs.trail.size();
        if (n >= 2) {
            double[] prev = null;
            int i = 0;
            for (double[] seg : hs.trail) {
                if (prev != null) {
                    // brightness ramps from dim (old, low i) to bright (new, high i)
                    float t = (float) i / (float) (n - 1);
                    float a = 0.12F + 0.55F * t;
                    strandQuad(poseStack, glow, prev[0], prev[1], prev[2],
                            seg[0], seg[1], seg[2], GLOW_R, GLOW_G, GLOW_B, a);
                }
                prev = seg;
                i++;
            }
        }
    }

    /** Eases {@code from} toward {@code to} by factor {@code f} (frame-rate-naive
     * but visually smooth at MC's render cadence). */
    private static double easeToward(double from, double to, double f) {
        return from + (to - from) * f;
    }

    // --- textured solid box primitives (NEW_ENTITY vertex format) ---

    private void post(PoseStack poseStack, VertexConsumer c, int light,
                      double x, double minY, double z, double maxY) {
        box(poseStack, c, x - RAIL, minY, z - RAIL, x + RAIL, maxY, z + RAIL,
                FRAME_R, FRAME_G, FRAME_B, 1.0F, light);
    }

    private void beamX(PoseStack poseStack, VertexConsumer c, int light,
                       double x0, double y, double z, double x1) {
        box(poseStack, c, x0, y - RAIL, z - RAIL, x1, y + RAIL, z + RAIL,
                FRAME_R, FRAME_G, FRAME_B, 1.0F, light);
    }

    private void beamZ(PoseStack poseStack, VertexConsumer c, int light,
                       double x, double y, double z0, double z1) {
        box(poseStack, c, x - RAIL, y - RAIL, z0, x + RAIL, y + RAIL, z1,
                FRAME_R, FRAME_G, FRAME_B, 1.0F, light);
    }

    /**
     * Emits a textured, lit axis-aligned box (6 quads) into a NEW_ENTITY-format
     * buffer. The texture is mapped 0..1 per face (a flat metal look — the source
     * is a 16px casing tile). Winding is CCW when viewed from outside so faces
     * aren't back-culled. {@code light} is the world packed light for this BE.
     */
    private void box(PoseStack poseStack, VertexConsumer c,
                     double x0, double y0, double z0, double x1, double y1, double z1,
                     float r, float g, float b, float a, int light) {
        var pose = poseStack.last();
        var mat = pose.pose();
        var norm = pose.normal();
        float fx0 = (float) x0, fy0 = (float) y0, fz0 = (float) z0;
        float fx1 = (float) x1, fy1 = (float) y1, fz1 = (float) z1;

        // -Z face (north), normal (0,0,-1)
        quad(c, mat, norm, r, g, b, a, light, 0, 0, -1,
                fx1, fy0, fz0, fx0, fy0, fz0, fx0, fy1, fz0, fx1, fy1, fz0);
        // +Z face (south), normal (0,0,1)
        quad(c, mat, norm, r, g, b, a, light, 0, 0, 1,
                fx0, fy0, fz1, fx1, fy0, fz1, fx1, fy1, fz1, fx0, fy1, fz1);
        // -X face (west), normal (-1,0,0)
        quad(c, mat, norm, r, g, b, a, light, -1, 0, 0,
                fx0, fy0, fz0, fx0, fy0, fz1, fx0, fy1, fz1, fx0, fy1, fz0);
        // +X face (east), normal (1,0,0)
        quad(c, mat, norm, r, g, b, a, light, 1, 0, 0,
                fx1, fy0, fz1, fx1, fy0, fz0, fx1, fy1, fz0, fx1, fy1, fz1);
        // +Y face (up), normal (0,1,0)
        quad(c, mat, norm, r, g, b, a, light, 0, 1, 0,
                fx0, fy1, fz1, fx1, fy1, fz1, fx1, fy1, fz0, fx0, fy1, fz0);
        // -Y face (down), normal (0,-1,0)
        quad(c, mat, norm, r, g, b, a, light, 0, -1, 0,
                fx0, fy0, fz0, fx1, fy0, fz0, fx1, fy0, fz1, fx0, fy0, fz1);
    }

    /** One textured/lit quad (4 verts, UV 0..1 over the tile). */
    private void quad(VertexConsumer c, org.joml.Matrix4f mat, org.joml.Matrix3f norm,
                      float r, float g, float b, float a, int light,
                      float nx, float ny, float nz,
                      float x0, float y0, float z0, float x1, float y1, float z1,
                      float x2, float y2, float z2, float x3, float y3, float z3) {
        c.vertex(mat, x0, y0, z0).color(r, g, b, a).uv(0, 0)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(norm, nx, ny, nz).endVertex();
        c.vertex(mat, x1, y1, z1).color(r, g, b, a).uv(1, 0)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(norm, nx, ny, nz).endVertex();
        c.vertex(mat, x2, y2, z2).color(r, g, b, a).uv(1, 1)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(norm, nx, ny, nz).endVertex();
        c.vertex(mat, x3, y3, z3).color(r, g, b, a).uv(0, 1)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(norm, nx, ny, nz).endVertex();
    }

    // --- emissive glow primitives (NEW_ENTITY format, FULL_BRIGHT light) ---

    /** A flat emissive quad at fullbright (additive via RenderType.eyes). */
    private void emissiveQuad(PoseStack poseStack, VertexConsumer c,
                              double ax, double ay, double az, double bx, double by, double bz,
                              double cx, double cy, double cz, double dx, double dy, double dz,
                              float r, float g, float b, float a) {
        var pose = poseStack.last();
        var mat = pose.pose();
        var norm = pose.normal();
        int fb = LightTexture.FULL_BRIGHT;
        // UV center of the 1x1 white texture: the eyes shader additively blends
        // (white texel * vertex color), so the cyan vertex color is the output.
        c.vertex(mat, (float) ax, (float) ay, (float) az).color(r, g, b, a).uv(0.5F, 0.5F)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(fb).normal(norm, 0, 0, 1).endVertex();
        c.vertex(mat, (float) bx, (float) by, (float) bz).color(r, g, b, a).uv(0.5F, 0.5F)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(fb).normal(norm, 0, 0, 1).endVertex();
        c.vertex(mat, (float) cx, (float) cy, (float) cz).color(r, g, b, a).uv(0.5F, 0.5F)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(fb).normal(norm, 0, 0, 1).endVertex();
        c.vertex(mat, (float) dx, (float) dy, (float) dz).color(r, g, b, a).uv(0.5F, 0.5F)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(fb).normal(norm, 0, 0, 1).endVertex();
    }

    /**
     * A thin glowing strand quad between two points, given a small constant
     * width on the horizontal plane. Used for the hot drop and the laid trail.
     */
    private void strandQuad(PoseStack poseStack, VertexConsumer c,
                            double x0, double y0, double z0, double x1, double y1, double z1,
                            float r, float g, float b, float a) {
        // Build a thin ribbon: offset both endpoints perpendicular to the segment
        // in the XZ plane (falls back to X if the segment is purely vertical).
        double dx = x1 - x0, dz = z1 - z0;
        double len = Math.sqrt(dx * dx + dz * dz);
        double px, pz;
        if (len < 1.0e-4) { // vertical drop — give it width along X
            px = 0.018;
            pz = 0.0;
        } else {
            px = -dz / len * 0.018;
            pz = dx / len * 0.018;
        }
        emissiveQuad(poseStack, c,
                x0 - px, y0, z0 - pz, x1 - px, y1, z1 - pz,
                x1 + px, y1, z1 + pz, x0 + px, y0, z0 + pz,
                r, g, b, a);
    }

    /**
     * Hologram preview: ghost-renders the loaded blueprint at the build
     * position. Unchanged from the wireframe-era renderer — this behavior works
     * and the brief says keep it as-is.
     */
    private void renderPreview(PrinterBlockEntity printer, PoseStack poseStack,
                               MultiBufferSource bufferSource) {
        if (!printer.previewShowing() || printer.getLevel() == null) {
            return;
        }
        BlockPos origin = printer.clientPreviewOrigin();
        BlockPos size = printer.clientPreviewSize();
        BlockPos machine = printer.getBlockPos();
        if (origin == null || size == null) {
            return;
        }

        // full-extent frame, drawn as lines so it stays cheap at distance
        VertexConsumer frameLines = bufferSource.getBuffer(RenderType.lines());
        LevelRenderer.renderLineBox(poseStack, frameLines,
                origin.getX() - machine.getX(), origin.getY() - machine.getY(), origin.getZ() - machine.getZ(),
                origin.getX() - machine.getX() + size.getX(),
                origin.getY() - machine.getY() + size.getY(),
                origin.getZ() - machine.getZ() + size.getZ(),
                0.55F, 0.75F, 1.00F, 0.65F);

        var minecraft = net.minecraft.client.Minecraft.getInstance();
        var camera = minecraft.gameRenderer.getMainCamera().getPosition();
        int maxDistance = com.pgmacdesign.mc3dprint.config.MC3DPrintConfig.PREVIEW_RENDER_DISTANCE.get();
        double maxDistanceSq = (double) maxDistance * maxDistance;
        var dispatcher = minecraft.getBlockRenderer();
        var level = printer.getLevel();

        for (PrinterBlockEntity.PreviewBlock ghost : printer.clientPreview()) {
            BlockPos pos = ghost.pos();
            if (camera.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > maxDistanceSq) {
                continue;
            }
            net.minecraft.world.level.block.state.BlockState existing = level.getBlockState(pos);
            if (existing == ghost.state()) {
                continue; // already correct — repair mode will skip it too
            }
            boolean blocked = !existing.canBeReplaced();

            poseStack.pushPose();
            poseStack.translate(pos.getX() - machine.getX(), pos.getY() - machine.getY(),
                    pos.getZ() - machine.getZ());
            // slight shrink so ghost faces never z-fight with real neighbors
            poseStack.translate(0.5, 0.5, 0.5);
            poseStack.scale(0.95F, 0.95F, 0.95F);
            poseStack.translate(-0.5, -0.5, -0.5);

            MultiBufferSource ghostBuffers = blocked
                    ? type -> new GhostVertexConsumer(bufferSource.getBuffer(RenderType.translucent()),
                            1.0F, 0.35F, 0.35F, 150)
                    : type -> new GhostVertexConsumer(bufferSource.getBuffer(RenderType.translucent()),
                            0.65F, 1.0F, 0.70F, 140);
            dispatcher.renderSingleBlock(ghost.state(), poseStack, ghostBuffers,
                    LevelRenderer.getLightColor(level, pos),
                    net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                    net.minecraftforge.client.model.data.ModelData.EMPTY, null);
            poseStack.popPose();
        }
    }

    /**
     * The machine at rest. When the controller is FORMED, draw a static raised
     * printer superstructure (chunky corner posts + a top gantry rim + a parked
     * extruder head) scaled to the N×N footprint, rising out of the textured
     * corner posts on the casings below. This is the MACHINE — it never runs the
     * traveling-print animation, so it can't desync from the actual print, which
     * keeps rendering its own cage at the (offset) build volume. Idle = a slow
     * breathing glow on the parked nozzle; printing = a steady bright glow.
     */
    private void renderFormedStructure(PrinterBlockEntity printer, float partialTick,
                                       PoseStack poseStack, MultiBufferSource bufferSource,
                                       int packedLight) {
        var state = printer.getBlockState();
        if (!state.hasProperty(ControllerBlock.FORMED) || !state.getValue(ControllerBlock.FORMED)) {
            return;
        }
        int half = MultiblockPattern.baseEdge(printer.tier()) / 2;
        // Local coords: casings extend `half` each way from the controller [0,1]
        // block. Put posts on the CENTERS of the four corner casings so they rise
        // straight out of the textured corner posts below.
        double pcMinX = -half + 0.5, pcMaxX = half + 0.5;
        double pcMinZ = -half + 0.5, pcMaxZ = half + 0.5;
        double cx = 0.5, cz = 0.5;                   // controller center
        double baseY = 1.0;                          // top of the casing layer
        double topY = baseY + 2.0 + half;            // taller for bigger tiers
        double s = 0.11;                             // chunky strut half-thickness

        VertexConsumer solid = bufferSource.getBuffer(RenderType.entitySolid(METAL));
        // four corner posts
        strut(poseStack, solid, packedLight, pcMinX - s, baseY, pcMinZ - s, pcMinX + s, topY, pcMinZ + s);
        strut(poseStack, solid, packedLight, pcMaxX - s, baseY, pcMinZ - s, pcMaxX + s, topY, pcMinZ + s);
        strut(poseStack, solid, packedLight, pcMinX - s, baseY, pcMaxZ - s, pcMinX + s, topY, pcMaxZ + s);
        strut(poseStack, solid, packedLight, pcMaxX - s, baseY, pcMaxZ - s, pcMaxX + s, topY, pcMaxZ + s);
        // top rim over the perimeter rail
        strut(poseStack, solid, packedLight, pcMinX - s, topY - s, pcMinZ - s, pcMaxX + s, topY + s, pcMinZ + s);
        strut(poseStack, solid, packedLight, pcMinX - s, topY - s, pcMaxZ - s, pcMaxX + s, topY + s, pcMaxZ + s);
        strut(poseStack, solid, packedLight, pcMinX - s, topY - s, pcMinZ - s, pcMinX + s, topY + s, pcMaxZ + s);
        strut(poseStack, solid, packedLight, pcMaxX - s, topY - s, pcMinZ - s, pcMaxX + s, topY + s, pcMaxZ + s);
        // gantry bridge across the center + a parked extruder head beneath it
        strut(poseStack, solid, packedLight, pcMinX - s, topY - s, cz - s, pcMaxX + s, topY + s, cz + s);
        box(poseStack, solid, cx - HEAD, topY - 2 * HEAD, cz - HEAD, cx + HEAD, topY, cz + HEAD,
                HEAD_R, HEAD_G, HEAD_B, 1.0F, packedLight);

        // Parked nozzle glow: breathing at idle, steady-bright while printing.
        long gameTime = printer.getLevel() != null ? printer.getLevel().getGameTime() : 0;
        boolean printing = printer.state() == PrinterBlockEntity.State.PRINTING;
        float pulse = printing ? 1.0F
                : 0.40F + 0.30F * (0.5F + 0.5F * Mth.sin((gameTime + partialTick) * 0.10F));
        VertexConsumer glow = bufferSource.getBuffer(RenderType.eyes(WHITE));
        double nozY = topY - 2 * HEAD;
        double r = 0.08;
        emissiveQuad(poseStack, glow, cx - r, nozY - r, cz, cx + r, nozY - r, cz,
                cx + r, nozY + r, cz, cx - r, nozY + r, cz, GLOW_R, GLOW_G, GLOW_B, pulse);
        emissiveQuad(poseStack, glow, cx, nozY - r, cz - r, cx, nozY - r, cz + r,
                cx, nozY + r, cz + r, cx, nozY + r, cz - r, GLOW_R, GLOW_G, GLOW_B, pulse);
    }

    /** A chunky textured strut (axis-aligned box) for the formed superstructure. */
    private void strut(PoseStack poseStack, VertexConsumer c, int light,
                       double x0, double y0, double z0, double x1, double y1, double z1) {
        box(poseStack, c, x0, y0, z0, x1, y1, z1, FRAME_R, FRAME_G, FRAME_B, 1.0F, light);
    }

    private void renderSpools(PrinterBlockEntity printer, float partialTick,
                              PoseStack poseStack, MultiBufferSource bufferSource) {
        var spools = printer.clientSpools();
        if (spools.isEmpty()) {
            return;
        }
        // When formed as a multiblock, push the reels out to the structure's
        // perimeter so they show on the outer sides instead of being buried
        // against the surrounding casings.
        var blockState = printer.getBlockState();
        float reach = 0.5F;
        if (blockState.hasProperty(ControllerBlock.FORMED) && blockState.getValue(ControllerBlock.FORMED)) {
            reach += MultiblockPattern.baseEdge(printer.tier()) / 2;
        }
        VertexConsumer lines = bufferSource.getBuffer(RenderType.lines());
        long gameTime = printer.getLevel() != null ? printer.getLevel().getGameTime() : 0;
        boolean printing = printer.state() == PrinterBlockEntity.State.PRINTING;
        // spin fast while printing; a docked reel still visibly rotates at rest
        // (the old 0.015 idle rate was imperceptible).
        float angle = (gameTime + partialTick) * (printing ? 0.45F : 0.12F);

        for (int slot = 0; slot < spools.size() && slot < SPOOL_FACES.length; slot++) {
            PrinterBlockEntity.SpoolRenderInfo info = spools.get(slot);
            if (info == null) {
                continue;
            }
            var face = SPOOL_FACES[slot];
            float nx = face.getStepX();
            float nz = face.getStepZ();
            // orthonormal basis on the face plane: u = up, v = normal × up
            float vx = -nz, vz = nx;
            float cx = 0.5F + nx * reach;
            float cz = 0.5F + nz * reach;

            float winding = AXLE_RADIUS + WINDING_MAX_EXTRA * Math.max(0.0F, Math.min(1.0F, info.fillFraction()));
            // wound filament tinted by spool tier; creative stays magenta
            float[] windColor = info.creative()
                    ? new float[]{0.90F, 0.40F, 0.95F}
                    : tierColor(info.tier());

            for (float depth : new float[]{0.04F, 0.20F}) { // two flanges
                circle(poseStack, lines, cx + nx * depth, 0.5F, cz + nz * depth,
                        vx, vz, FLANGE_RADIUS, 0.55F, 0.57F, 0.62F);
            }
            for (float depth : new float[]{0.08F, 0.12F, 0.16F}) { // wound filament between them
                circle(poseStack, lines, cx + nx * depth, 0.5F, cz + nz * depth,
                        vx, vz, winding, windColor[0], windColor[1], windColor[2]);
            }
            // four rotating spokes make the spin visible
            float spokeDepth = 0.12F;
            for (int s = 0; s < 4; s++) {
                float ang = angle + s * (Mth.PI / 2);
                float ux = Mth.cos(ang);   // along v
                float uy = Mth.sin(ang);   // along world up
                line(poseStack, lines,
                        cx + nx * spokeDepth, 0.5, cz + nz * spokeDepth,
                        cx + nx * spokeDepth + vx * ux * winding,
                        0.5 + uy * winding,
                        cz + nz * spokeDepth + vz * ux * winding,
                        windColor[0], windColor[1], windColor[2]);
            }
        }
    }

    /** Line circle on the plane spanned by world-up and (vx, vz). */
    private static void circle(PoseStack poseStack, VertexConsumer consumer,
                               float cx, float cy, float cz, float vx, float vz,
                               float radius, float r, float g, float b) {
        for (int i = 0; i < CIRCLE_SEGMENTS; i++) {
            float a1 = (float) (2 * Math.PI * i / CIRCLE_SEGMENTS);
            float a2 = (float) (2 * Math.PI * (i + 1) / CIRCLE_SEGMENTS);
            line(poseStack, consumer,
                    cx + vx * Mth.cos(a1) * radius, cy + Mth.sin(a1) * radius, cz + vz * Mth.cos(a1) * radius,
                    cx + vx * Mth.cos(a2) * radius, cy + Mth.sin(a2) * radius, cz + vz * Mth.cos(a2) * radius,
                    r, g, b);
        }
    }

    private static void line(PoseStack poseStack, VertexConsumer consumer,
                             double x1, double y1, double z1, double x2, double y2, double z2,
                             float r, float g, float b) {
        var pose = poseStack.last();
        float dx = (float) (x2 - x1);
        float dy = (float) (y2 - y1);
        float dz = (float) (z2 - z1);
        float length = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0e-4F) {
            return;
        }
        dx /= length;
        dy /= length;
        dz /= length;
        consumer.vertex(pose.pose(), (float) x1, (float) y1, (float) z1)
                .color(r, g, b, 0.9F).normal(pose.normal(), dx, dy, dz).endVertex();
        consumer.vertex(pose.pose(), (float) x2, (float) y2, (float) z2)
                .color(r, g, b, 0.9F).normal(pose.normal(), dx, dy, dz).endVertex();
    }

    @Override
    public boolean shouldRenderOffScreen(PrinterBlockEntity printer) {
        return true; // the frame extends well beyond the machine block
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
