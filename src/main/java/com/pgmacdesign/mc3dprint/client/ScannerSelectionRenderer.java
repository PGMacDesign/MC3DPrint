package com.pgmacdesign.mc3dprint.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.scanner.ScannerItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

/**
 * WorldEdit-CUI-style selection preview for the Structure Scanner: while the
 * scanner is held, corner A renders as a blue box, corner B as a cyan box, and
 * once both are set the full selection volume is outlined.
 */
@Mod.EventBusSubscriber(modid = MC3DPrint.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ScannerSelectionRenderer {
    private ScannerSelectionRenderer() {}

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null) {
            return;
        }
        ItemStack scanner = heldScanner(player);
        if (scanner.isEmpty() || scanner.getTag() == null) {
            return;
        }
        CompoundTag tag = scanner.getTag();
        BlockPos cornerA = readCorner(tag, ScannerItem.TAG_CORNER_A);
        BlockPos cornerB = readCorner(tag, ScannerItem.TAG_CORNER_B);
        if (cornerA == null && cornerB == null) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
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
    }

    private static void renderBox(PoseStack poseStack, VertexConsumer consumer, AABB box,
                                  float red, float green, float blue, float alpha) {
        LevelRenderer.renderLineBox(poseStack, consumer, box, red, green, blue, alpha);
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

    @Nullable
    private static BlockPos readCorner(CompoundTag tag, String key) {
        if (!tag.contains(key, CompoundTag.TAG_COMPOUND)) {
            return null;
        }
        return NbtUtils.readBlockPos(tag.getCompound(key));
    }
}
