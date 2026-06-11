package com.pgmacdesign.mc3dprint.blueprint.io;

import com.pgmacdesign.mc3dprint.blueprint.Blueprint;
import com.pgmacdesign.mc3dprint.blueprint.BlueprintBlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.Map;

/**
 * Exports the native blueprint as a Sponge v2 schematic — v2 rather than v3
 * for the widest WorldEdit version compatibility on 1.20.1.
 */
public final class SpongeSchematicExporter {
    private SpongeSchematicExporter() {}

    /**
     * @param dataVersion the Minecraft data version to stamp (e.g. 3465 for 1.20.1);
     *                    passed in so the exporter stays registry/bootstrap-free
     */
    public static CompoundTag exportV2(Blueprint blueprint, int dataVersion) {
        CompoundTag root = new CompoundTag();
        root.putInt("Version", 2);
        root.putInt("DataVersion", dataVersion);
        root.putShort("Width", (short) blueprint.sizeX());
        root.putShort("Height", (short) blueprint.sizeY());
        root.putShort("Length", (short) blueprint.sizeZ());

        // Sponge's dense array has no "absent" concept — empty positions export as air
        CompoundTag paletteTag = new CompoundTag();
        paletteTag.putInt(BlueprintBlockState.AIR_ID, 0);
        for (int i = 0; i < blueprint.palette().size(); i++) {
            paletteTag.putInt(blueprint.palette().get(i).serialize(), i + 1);
        }
        root.putInt("PaletteMax", blueprint.palette().size() + 1);
        root.put("Palette", paletteTag);

        int width = blueprint.sizeX();
        int length = blueprint.sizeZ();
        int[] indices = new int[width * blueprint.sizeY() * length];
        blueprint.forEachBlock((pos, paletteIndex) ->
                indices[pos.getX() + pos.getZ() * width + pos.getY() * width * length] = paletteIndex + 1);
        root.putByteArray("BlockData", VarInt.encodeAll(indices));

        ListTag blockEntities = new ListTag();
        for (Map.Entry<BlockPos, CompoundTag> entry : blueprint.blockEntities().entrySet()) {
            CompoundTag be = entry.getValue().copy();
            if (be.contains("id")) {
                be.putString("Id", be.getString("id"));
                be.remove("id");
            }
            BlockPos pos = entry.getKey();
            be.putIntArray("Pos", new int[]{pos.getX(), pos.getY(), pos.getZ()});
            blockEntities.add(be);
        }
        root.put("BlockEntities", blockEntities);
        return root;
    }
}
