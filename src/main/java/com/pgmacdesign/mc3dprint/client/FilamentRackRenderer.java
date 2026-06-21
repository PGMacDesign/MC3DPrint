package com.pgmacdesign.mc3dprint.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.pgmacdesign.mc3dprint.machine.rack.FilamentRackBlock;
import com.pgmacdesign.mc3dprint.machine.rack.FilamentRackBlockEntity;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

/**
 * Renders each shelved spool as its actual item model, flat against the rack's
 * front face in a 2×4 grid. Using the real spool models means each tier shows
 * its own colored coil for free — no tinting needed — and the shelf visibly
 * fills as spools are added.
 */
public class FilamentRackRenderer implements BlockEntityRenderer<FilamentRackBlockEntity> {
    private static final int COLUMNS = 4;
    private static final float SPOOL_SCALE = 0.18F;

    private final ItemRenderer itemRenderer;

    public FilamentRackRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(FilamentRackBlockEntity rack, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        Direction facing = rack.getBlockState().getValue(FilamentRackBlock.FACING);
        // The rack is a full opaque cube, so the light sampled at its OWN position is
        // the buried (=0) interior light — which rendered every spool pitch black.
        // Sample the air block in front of the face instead, where the spools sit.
        Level level = rack.getLevel();
        int light = packedLight;
        if (level != null) {
            light = LevelRenderer.getLightColor(level, rack.getBlockPos().relative(facing));
        }
        ItemStackHandler spools = rack.spools();
        for (int i = 0; i < spools.getSlots(); i++) {
            ItemStack spool = spools.getStackInSlot(i);
            if (spool.isEmpty()) {
                continue;
            }
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            // Match the painted cubby grid: centers at ±0.375/±0.125 across, ±0.22 up.
            float x = -0.375F + col * 0.25F;
            float y = (row == 0) ? 0.22F : -0.22F; // top / bottom row

            pose.pushPose();
            pose.translate(0.5, 0.5, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot())); // front face -> local +Z
            pose.translate(x, y, 0.52F); // just proud of the front face
            pose.scale(SPOOL_SCALE, SPOOL_SCALE, SPOOL_SCALE);
            itemRenderer.renderStatic(spool, ItemDisplayContext.FIXED, light, packedOverlay,
                    pose, buffers, level, 0);
            pose.popPose();
        }
    }
}
