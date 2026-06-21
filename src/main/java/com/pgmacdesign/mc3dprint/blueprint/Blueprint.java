package com.pgmacdesign.mc3dprint.blueprint;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.ObjIntConsumer;

/**
 * The native blueprint: a dense volume of palette indices plus block entity data.
 *
 * Positions are blueprint-local (origin at the min corner). Index {@code -1}
 * means "no block here" — the printer skips it. Explicit air imports as -1;
 * the design doc requires pre-cleared print areas, so air is never printed.
 */
public final class Blueprint {
    public static final int NO_BLOCK = -1;

    private final String name;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final List<BlueprintBlockState> palette;
    private final int[] blocks; // palette index per position, NO_BLOCK for empty
    private final Map<BlockPos, CompoundTag> blockEntities;
    private final List<BlueprintEntity> entities; // armor stands, frames, paintings

    Blueprint(String name, int sizeX, int sizeY, int sizeZ,
              List<BlueprintBlockState> palette, int[] blocks,
              Map<BlockPos, CompoundTag> blockEntities, List<BlueprintEntity> entities) {
        if (blocks.length != sizeX * sizeY * sizeZ) {
            throw new IllegalArgumentException("Block array length " + blocks.length
                    + " does not match volume " + sizeX + "x" + sizeY + "x" + sizeZ);
        }
        this.name = name;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.palette = List.copyOf(palette);
        this.blocks = blocks;
        this.blockEntities = Map.copyOf(blockEntities);
        this.entities = List.copyOf(entities);
    }

    public static Builder builder(String name, int sizeX, int sizeY, int sizeZ) {
        return new Builder(name, sizeX, sizeY, sizeZ);
    }

    public String name() {
        return name;
    }

    public int sizeX() {
        return sizeX;
    }

    public int sizeY() {
        return sizeY;
    }

    public int sizeZ() {
        return sizeZ;
    }

    public List<BlueprintBlockState> palette() {
        return palette;
    }

    public Map<BlockPos, CompoundTag> blockEntities() {
        return blockEntities;
    }

    public List<BlueprintEntity> entities() {
        return entities;
    }

    int[] rawBlocks() {
        return blocks;
    }

    public int index(int x, int y, int z) {
        if (x < 0 || x >= sizeX || y < 0 || y >= sizeY || z < 0 || z >= sizeZ) {
            throw new IndexOutOfBoundsException("(" + x + "," + y + "," + z + ") outside "
                    + sizeX + "x" + sizeY + "x" + sizeZ);
        }
        return (y * sizeZ + z) * sizeX + x;
    }

    @Nullable
    public BlueprintBlockState get(int x, int y, int z) {
        int paletteIndex = blocks[index(x, y, z)];
        return paletteIndex == NO_BLOCK ? null : palette.get(paletteIndex);
    }

    /** Count of placeable (non-empty) blocks. */
    public int blockCount() {
        int count = 0;
        for (int b : blocks) {
            if (b != NO_BLOCK) count++;
        }
        return count;
    }

    /**
     * Visits every placeable block bottom-up (Y, then Z, then X) — the print
     * order. Consumer receives the local position and palette index.
     */
    public void forEachBlock(ObjIntConsumer<BlockPos> consumer) {
        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    int paletteIndex = blocks[(y * sizeZ + z) * sizeX + x];
                    if (paletteIndex != NO_BLOCK) {
                        consumer.accept(new BlockPos(x, y, z), paletteIndex);
                    }
                }
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Blueprint other)) return false;
        return sizeX == other.sizeX && sizeY == other.sizeY && sizeZ == other.sizeZ
                && name.equals(other.name)
                && palette.equals(other.palette)
                && Arrays.equals(blocks, other.blocks)
                && blockEntities.equals(other.blockEntities)
                && entities.equals(other.entities);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, sizeX, sizeY, sizeZ, palette, Arrays.hashCode(blocks),
                blockEntities, entities);
    }

    public static final class Builder {
        private final String name;
        private final int sizeX;
        private final int sizeY;
        private final int sizeZ;
        private final List<BlueprintBlockState> palette = new ArrayList<>();
        private final Map<BlueprintBlockState, Integer> paletteLookup = new HashMap<>();
        private final int[] blocks;
        private final Map<BlockPos, CompoundTag> blockEntities = new HashMap<>();
        private final List<BlueprintEntity> entities = new ArrayList<>();

        private Builder(String name, int sizeX, int sizeY, int sizeZ) {
            if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
                throw new IllegalArgumentException("Blueprint dimensions must be positive: "
                        + sizeX + "x" + sizeY + "x" + sizeZ);
            }
            this.name = name;
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
            this.blocks = new int[sizeX * sizeY * sizeZ];
            Arrays.fill(this.blocks, NO_BLOCK);
        }

        /** Air states are treated as empty and skipped. */
        public Builder set(int x, int y, int z, BlueprintBlockState state) {
            if (state.isAir()) {
                return this;
            }
            int paletteIndex = paletteLookup.computeIfAbsent(state, s -> {
                palette.add(s);
                return palette.size() - 1;
            });
            blocks[(y * sizeZ + z) * sizeX + x] = paletteIndex;
            return this;
        }

        /**
         * Genuinely empties a cell — sets it back to the {@link Blueprint#NO_BLOCK}
         * sentinel so the printer skips it.
         *
         * <p>This is the counterpart {@link #set} cannot provide: {@code set(...)} treats
         * air states as empty and silently <em>ignores</em> them (see {@link #set}), so
         * {@code set(x,y,z, air)} is a no-op that leaves any previously placed block in
         * place. That made the common "place a wall, then carve a doorway/window opening"
         * pattern a silent failure — the carve did nothing and the door printed into a
         * solid wall. {@code clear} fixes that: it always writes {@link Blueprint#NO_BLOCK},
         * so an author can place a wall and then open it afterward.
         *
         * <p>Serialization/determinism are unaffected: an unset cell is already the array
         * default ({@link Blueprint#NO_BLOCK}), so a cleared cell round-trips identically
         * to one that was never written. A stale block-entity for this cell (if any) is
         * also dropped, since the cell no longer holds a block. Out-of-range coordinates
         * throw, matching the rest of the builder.
         */
        public Builder clear(int x, int y, int z) {
            blocks[(y * sizeZ + z) * sizeX + x] = NO_BLOCK;
            blockEntities.remove(new BlockPos(x, y, z));
            return this;
        }

        public Builder blockEntity(int x, int y, int z, CompoundTag data) {
            blockEntities.put(new BlockPos(x, y, z), data);
            return this;
        }

        /** Adds a decorative entity at blueprint-local (continuous) coordinates. */
        public Builder entity(double x, double y, double z, CompoundTag nbt) {
            entities.add(new BlueprintEntity(x, y, z, nbt));
            return this;
        }

        public int sizeX() {
            return sizeX;
        }

        public int sizeY() {
            return sizeY;
        }

        public int sizeZ() {
            return sizeZ;
        }

        public Blueprint build() {
            return new Blueprint(name, sizeX, sizeY, sizeZ, palette, blocks, blockEntities, entities);
        }
    }
}
