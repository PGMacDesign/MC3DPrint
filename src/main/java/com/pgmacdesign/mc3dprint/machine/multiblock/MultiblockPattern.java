package com.pgmacdesign.mc3dprint.machine.multiblock;

import com.pgmacdesign.mc3dprint.machine.MachineTier;
import com.pgmacdesign.mc3dprint.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * T5-T8 multiblock layout: the controller sits at the center of an N×N base
 * of Printer Casing blocks (T5 3×3 ... T8 9×9), all on the controller's Y level.
 *
 * T8 additionally requires Draconic Evolution: the four base corners must be
 * Awakened Draconium blocks, and formation refuses outright without DE loaded.
 */
public final class MultiblockPattern {
    public static final String DRACONIC_MOD_ID = "draconicevolution";
    private static final ResourceLocation AWAKENED_DRACONIUM =
            ResourceLocation.tryParse(DRACONIC_MOD_ID + ":awakened_draconium_block");

    private MultiblockPattern() {}

    /** Base edge length for the tier (3, 5, 7, 9). */
    public static int baseEdge(MachineTier tier) {
        return switch (tier) {
            case T5 -> 3;
            case T6 -> 5;
            case T7 -> 7;
            case T8 -> 9;
            default -> throw new IllegalArgumentException(tier + " is not a multiblock tier");
        };
    }

    /** All component offsets relative to the controller (controller itself excluded). */
    public static List<BlockPos> componentOffsets(MachineTier tier) {
        int half = baseEdge(tier) / 2;
        List<BlockPos> offsets = new ArrayList<>();
        for (int x = -half; x <= half; x++) {
            for (int z = -half; z <= half; z++) {
                if (x != 0 || z != 0) {
                    offsets.add(new BlockPos(x, 0, z));
                }
            }
        }
        return offsets;
    }

    private static boolean isCorner(BlockPos offset, MachineTier tier) {
        int half = baseEdge(tier) / 2;
        return Math.abs(offset.getX()) == half && Math.abs(offset.getZ()) == half;
    }

    /** Null when valid; otherwise the user-facing error. */
    @Nullable
    public static Component validate(Level level, BlockPos controller, MachineTier tier) {
        if (tier == MachineTier.T8 && !ModList.get().isLoaded(DRACONIC_MOD_ID)) {
            return Component.translatable("message.mc3dprint.t8_requires_draconic");
        }
        Block awakened = tier == MachineTier.T8 && AWAKENED_DRACONIUM != null
                ? ForgeRegistries.BLOCKS.getValue(AWAKENED_DRACONIUM) : null;

        for (BlockPos offset : componentOffsets(tier)) {
            BlockPos pos = controller.offset(offset);
            Block found = level.getBlockState(pos).getBlock();
            if (tier == MachineTier.T8 && isCorner(offset, tier)) {
                if (awakened == null || found != awakened) {
                    return Component.translatable("message.mc3dprint.multiblock_needs_awakened",
                            pos.getX(), pos.getY(), pos.getZ());
                }
            } else if (found != ModBlocks.PRINTER_CASING.get()) {
                return Component.translatable("message.mc3dprint.multiblock_needs_casing",
                        pos.getX(), pos.getY(), pos.getZ());
            }
        }
        return null;
    }
}
