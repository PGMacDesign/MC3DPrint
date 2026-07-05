package com.pgmacdesign.mc3dprint.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.registry.ModDataComponents;
import com.pgmacdesign.mc3dprint.scanner.ScanData;
import com.pgmacdesign.mc3dprint.scanner.ScannerItem;
import net.minecraft.client.Minecraft;
//? if <26.2 {
import net.minecraft.client.renderer.MultiBufferSource;
//?}
//? if >=1.21.11 {
/*import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
*///?} else {
import net.minecraft.client.renderer.RenderType;
//?}
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * WorldEdit-CUI-style selection preview for the Structure Scanner: while the
 * scanner is held, corner A renders as a blue box, corner B as a cyan box, and
 * once both are set the full selection volume is outlined.
 */
//? if >=1.21.5 {
/*@EventBusSubscriber(modid = MC3DPrint.MOD_ID, value = Dist.CLIENT)
*///?} else {
@EventBusSubscriber(modid = MC3DPrint.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
//?}
public final class ScannerSelectionRenderer {
    private ScannerSelectionRenderer() {}

    @SubscribeEvent
    //? if >=1.21.5 {
    /*public static void onRenderLevelStage(RenderLevelStageEvent.AfterTranslucentBlocks event) {
    *///?} else {
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
    //?}
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null) {
            return;
        }
        ItemStack scanner = heldScanner(player);
        if (scanner.isEmpty()) {
            return;
        }
        ScanData data = scanner.get(ModDataComponents.SCAN.get());
        if (data == null) {
            return;
        }
        BlockPos cornerA = data.cornerA().orElse(null);
        BlockPos cornerB = data.cornerB().orElse(null);
        if (cornerA == null && cornerB == null) {
            return;
        }

        //? if >=26.2 {
        /*// 26.2 deleted the immediate MultiBufferSource/renderBuffers path entirely; the
        // per-frame Gizmos API is now the sanctioned way to draw world-space overlay shapes
        // (world coordinates, no camera transform needed). Colors match the legacy path.
        if (cornerA != null) {
            net.minecraft.gizmos.Gizmos.cuboid(new AABB(cornerA).inflate(0.004),
                    net.minecraft.gizmos.GizmoStyle.stroke(0xFF3373FF));
        }
        if (cornerB != null) {
            net.minecraft.gizmos.Gizmos.cuboid(new AABB(cornerB).inflate(0.004),
                    net.minecraft.gizmos.GizmoStyle.stroke(0xFF4DD9FF));
        }
        if (cornerA != null && cornerB != null && !cornerA.equals(cornerB)) {
            AABB bounds = new AABB(
                    Math.min(cornerA.getX(), cornerB.getX()),
                    Math.min(cornerA.getY(), cornerB.getY()),
                    Math.min(cornerA.getZ(), cornerB.getZ()),
                    Math.max(cornerA.getX(), cornerB.getX()) + 1,
                    Math.max(cornerA.getY(), cornerB.getY()) + 1,
                    Math.max(cornerA.getZ(), cornerB.getZ()) + 1).inflate(0.008);
            net.minecraft.gizmos.Gizmos.cuboid(bounds,
                    net.minecraft.gizmos.GizmoStyle.stroke(0xA68CBFFF));
        }
        *///?} else {
        PoseStack poseStack = event.getPoseStack();
        if (poseStack == null) {
            return; // @Nullable on 1.21.9+; never null on the stages we subscribe to pre-1.21.9
        }
        // 1.21.9 removed getCamera() from the event; the camera now lives on the level render state.
        //? if >=1.21.9 {
        /*Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        *///?} else {
        Vec3 camera = event.getCamera().getPosition();
        //?}
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);

        if (cornerA != null) {
            renderBox(poseStack, lines, new AABB(cornerA).inflate(0.004),
                    0.20F, 0.45F, 1.00F, 1.0F);
        }
        if (cornerB != null) {
            renderBox(poseStack, lines, new AABB(cornerB).inflate(0.004),
                    0.30F, 0.85F, 1.00F, 1.0F);
        }
        if (cornerA != null && cornerB != null && !cornerA.equals(cornerB)) {
            AABB bounds = new AABB(
                    Math.min(cornerA.getX(), cornerB.getX()),
                    Math.min(cornerA.getY(), cornerB.getY()),
                    Math.min(cornerA.getZ(), cornerB.getZ()),
                    Math.max(cornerA.getX(), cornerB.getX()) + 1,
                    Math.max(cornerA.getY(), cornerB.getY()) + 1,
                    Math.max(cornerA.getZ(), cornerB.getZ()) + 1).inflate(0.008);
            renderBox(poseStack, lines, bounds, 0.55F, 0.75F, 1.00F, 0.65F);
        }

        poseStack.popPose();
        buffers.endBatch(RenderType.lines());
        //?}
    }

    private static void renderBox(PoseStack poseStack, VertexConsumer consumer, AABB box,
                                  float red, float green, float blue, float alpha) {
        // Hand-rolled in RenderCompat — vanilla's renderLineBox churned owner/signature across
        // 1.21.x and was removed in 1.21.11.
        com.pgmacdesign.mc3dprint.compat.RenderCompat.lineBox(poseStack.last(), consumer,
                box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, red, green, blue, alpha);
    }

    private static ItemStack heldScanner(Player player) {
        if (player.getMainHandItem().getItem() instanceof ScannerItem) {
            return player.getMainHandItem();
        }
        if (player.getOffhandItem().getItem() instanceof ScannerItem) {
            return player.getOffhandItem();
        }
        return ItemStack.EMPTY;
    }
}
