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
 * last placed block, and a beam from head to placement point.
 *
 * Line-based rendering (hologram look) — solid modeled gantry geometry is a
 * later polish pass.
 */
public class PrinterRenderer implements BlockEntityRenderer<PrinterBlockEntity> {
    // frame: printer-blue; head/beam: warm yellow
    private static final float FR = 0.31F, FG = 0.76F, FB = 0.97F, FA = 0.8F;
    private static final float HR = 1.0F, HG = 0.85F, HB = 0.3F;

    public PrinterRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(PrinterBlockEntity printer, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        PrintJob job = printer.activeJob();
        if (job == null) {
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
