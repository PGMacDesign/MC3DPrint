package com.pgmacdesign.mc3dprint.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.pgmacdesign.mc3dprint.machine.PrintJob;
import com.pgmacdesign.mc3dprint.machine.PrinterBlockEntity;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/**
 * Print-job visualization, BuildCraft-quarry-in-reverse: a structural frame
 * around the print volume, X/Y gantry arms, a print head that rides above the
 * last placed block, and a beam from head to placement point. Docked filament
 * spools render on the four side faces — they spin while printing and their
 * winding shrinks toward the axle as filament depletes.
 *
 * Line-based rendering (hologram look) — solid modeled gantry geometry is a
 * later polish pass.
 */
public class PrinterRenderer implements BlockEntityRenderer<PrinterBlockEntity> {
    // frame: printer-blue; head/beam: warm yellow
    private static final float FR = 0.31F, FG = 0.76F, FB = 0.97F, FA = 0.8F;
    private static final float HR = 1.0F, HG = 0.85F, HB = 0.3F;

    /** Spool slot index -> the side face it docks on. */
    private static final net.minecraft.core.Direction[] SPOOL_FACES = {
            net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.EAST,
            net.minecraft.core.Direction.SOUTH, net.minecraft.core.Direction.WEST};

    private static final int CIRCLE_SEGMENTS = 16;
    private static final float FLANGE_RADIUS = 0.30F;
    private static final float AXLE_RADIUS = 0.08F;
    private static final float WINDING_MAX_EXTRA = 0.18F;

    public PrinterRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(PrinterBlockEntity printer, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        renderSpools(printer, partialTick, poseStack, bufferSource);

        PrintJob job = printer.activeJob();
        if (job == null) {
            renderPreview(printer, poseStack, bufferSource);
            return;
        }
        BlockPos machine = printer.getBlockPos();
        BlockPos origin = job.origin();
        BlockPos size = job.size();

        double minX = origin.getX() - machine.getX();
        double minY = origin.getY() - machine.getY();
        double minZ = origin.getZ() - machine.getZ();
        double maxX = minX + size.getX();
        double maxY = minY + size.getY();
        double maxZ = minZ + size.getZ();

        VertexConsumer lines = bufferSource.getBuffer(RenderType.lines());

        // structural frame around the whole print volume
        LevelRenderer.renderLineBox(poseStack, lines, minX, minY, minZ, maxX, maxY, maxZ, FR, FG, FB, FA);

        BlockPos lastPlaced = printer.lastPlacedPos();
        if (lastPlaced != null) {
            double headX = lastPlaced.getX() - machine.getX() + 0.5;
            double headZ = lastPlaced.getZ() - machine.getZ() + 0.5;
            double targetY = lastPlaced.getY() - machine.getY();
            // head rides the gantry plane at the frame top, with a subtle bob
            double bob = 0.06 * Mth.sin((printer.getLevel() != null
                    ? printer.getLevel().getGameTime() : 0) * 0.35F + partialTick * 0.35F);
            double headY = maxY + 0.25 + bob;

            // gantry arms: X arm and Z arm through the head position
            line(poseStack, lines, minX, headY, headZ, maxX, headY, headZ, FR, FG, FB);
            line(poseStack, lines, headX, headY, minZ, headX, headY, maxZ, FR, FG, FB);

            // print head: small box riding the gantry
            AABB head = new AABB(headX - 0.18, headY - 0.18, headZ - 0.18,
                    headX + 0.18, headY + 0.18, headZ + 0.18);
            LevelRenderer.renderLineBox(poseStack, lines, head.minX, head.minY, head.minZ,
                    head.maxX, head.maxY, head.maxZ, HR, HG, HB, 1.0F);

            // beam from head down to the materialization point
            line(poseStack, lines, headX, head.minY, headZ, headX, targetY + 1.0, headZ, HR, HG, HB);
        }
    }

    /**
     * Hologram preview: ghost-renders the loaded blueprint at the build
     * position. Green-white = will be placed, red = obstructed by a wrong
     * block, already-matching blocks are skipped entirely. Ghosts only draw
     * within the configured camera distance — the frame outline marks the
     * full extent without the render cost.
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

        // full-extent frame, same style as the active-print frame
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

    private void renderSpools(PrinterBlockEntity printer, float partialTick,
                              PoseStack poseStack, MultiBufferSource bufferSource) {
        var spools = printer.clientSpools();
        if (spools.isEmpty()) {
            return;
        }
        VertexConsumer lines = bufferSource.getBuffer(RenderType.lines());
        long gameTime = printer.getLevel() != null ? printer.getLevel().getGameTime() : 0;
        boolean printing = printer.state() == PrinterBlockEntity.State.PRINTING;
        // spin fast while printing, creep at idle so docked spools read as "live"
        float angle = (gameTime + partialTick) * (printing ? 0.45F : 0.015F);

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
            float cx = 0.5F + nx * 0.5F;
            float cz = 0.5F + nz * 0.5F;

            float winding = AXLE_RADIUS + WINDING_MAX_EXTRA * Math.max(0.0F, Math.min(1.0F, info.fillFraction()));
            float[] windColor = info.creative()
                    ? new float[]{0.90F, 0.40F, 0.95F}
                    : new float[]{0.31F, 0.76F, 0.97F};

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
                float a = angle + s * (Mth.PI / 2);
                float ux = Mth.cos(a);   // along v
                float uy = Mth.sin(a);   // along world up
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
