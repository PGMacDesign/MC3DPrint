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
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Renders each shelved spool as its actual item model, flat against the rack's
 * front face in a 2×4 grid. Using the real spool models means each tier shows
 * its own colored coil for free — no tinting needed — and the shelf visibly
 * fills as spools are added.
 */
public class FilamentRackRenderer implements BlockEntityRenderer<FilamentRackBlockEntity> {
    private static final int COLUMNS = 4;
    private static final float SPOOL_SCALE = 0.18F;
    // Bay centers on the 32px Concept-A front face (px {4,12,20,28} → face/32 − 0.5).
    // Must stay in lockstep with RACK_COLS/RACK_ROWS in tools/gen_storage_cable_textures.py.
    private static final float[] COLUMN_X = {-0.375F, -0.125F, 0.125F, 0.375F};
    private static final float ROW_Y = 0.21875F; // px {9,23} → ±this
    // The item's FIXED display transform seats the spool up-and-left of the bay
    // center; nudge it down-right to center it in the circle. (Tunable dials.)
    private static final float NUDGE_X = 0.015F;  // right
    private static final float NUDGE_Y = 0.015F;  // down

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
            // Sit each spool centered in its painted bay (Concept A grid).
            float x = COLUMN_X[col] + NUDGE_X;
            float y = ((row == 0) ? ROW_Y : -ROW_Y) - NUDGE_Y; // top / bottom row

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
