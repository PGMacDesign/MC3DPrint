package com.pgmacdesign.mc3dprint.blueprint;

import com.pgmacdesign.mc3dprint.compat.NbtCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.SortedMap;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Property-style fuzz over the blueprint I/O layer: for many SEEDED random blueprints,
 * {@code read(write(bp))} must reproduce {@code bp} exactly, and every
 * {@link BlueprintBlockState} must survive a serialize/parse round-trip. This locks the
 * registry-free serializer the audit hardened, across the whole space of dims, palettes,
 * block-entity tags, and decorative entities the hand-written cases can't enumerate.
 *
 * <p>Deterministic: fixed seeds, seeded {@link Random}, and property tokens drawn from a
 * safe alphabet (no {@code [ ] , =} or whitespace) so parse/serialize is unambiguous.
 */
class BlueprintRoundTripFuzzTest {

    private static final int SEEDS = 200;

    // Safe id/token alphabet: parse splits on '[', ']', ',', '=' and trims whitespace,
    // so those must not appear inside ids, keys, or values.
    private static final String TOKEN_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789_";

    @Test
    void blueprintRoundTripsForManySeeds() {
        for (int seed = 0; seed < SEEDS; seed++) {
            Blueprint original = randomBlueprint(new Random(seed));
            Blueprint restored = BlueprintSerializer.read(BlueprintSerializer.write(original));
            assertEquals(original, restored, "blueprint round trip diverged for seed " + seed);
        }
    }

    @Test
    void blockStateParseSerializeRoundTripsForManySeeds() {
        for (int seed = 0; seed < SEEDS; seed++) {
            Random random = new Random(seed * 31L + 7L);
            BlueprintBlockState state = randomState(random);
            // serialize -> parse reproduces the state exactly
            assertEquals(state, BlueprintBlockState.parse(state.serialize()),
                    "state parse round trip diverged for seed " + seed);
            // and the canonical string is stable through parse
            assertEquals(state.serialize(), BlueprintBlockState.parse(state.serialize()).serialize(),
                    "state serialize is not stable for seed " + seed);
        }
    }

    // ============================ generators ============================

    private static Blueprint randomBlueprint(Random random) {
        int sizeX = 1 + random.nextInt(6);
        int sizeY = 1 + random.nextInt(4);
        int sizeZ = 1 + random.nextInt(6);
        Blueprint.Builder builder = Blueprint.builder("fuzz_" + random.nextInt(10_000), sizeX, sizeY, sizeZ);

        // a small source palette; set() dedups and derives the real palette from usage
        int paletteSize = 1 + random.nextInt(6);
        List<BlueprintBlockState> source = new ArrayList<>();
        for (int i = 0; i < paletteSize; i++) {
            source.add(randomState(random));
        }
        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    if (random.nextDouble() < 0.5) {
                        builder.set(x, y, z, source.get(random.nextInt(source.size())));
                    }
                }
            }
        }

        // random block-entity tags at random in-bounds positions
        int beCount = random.nextInt(4);
        for (int i = 0; i < beCount; i++) {
            BlockPos pos = new BlockPos(random.nextInt(sizeX), random.nextInt(sizeY), random.nextInt(sizeZ));
            builder.blockEntity(pos.getX(), pos.getY(), pos.getZ(), randomCompound(random));
        }

        // random decorative entities, added in the serializer's own sort order so the
        // round-tripped list (which write() sorts) compares equal to the source list.
        int entityCount = random.nextInt(4);
        List<double[]> positions = new ArrayList<>();
        List<CompoundTag> tags = new ArrayList<>();
        for (int i = 0; i < entityCount; i++) {
            positions.add(new double[]{random.nextDouble() * 8, random.nextDouble() * 8, random.nextDouble() * 8});
            CompoundTag tag = randomCompound(random);
            tag.putString("id", "test:ent_" + random.nextInt(20));
            tags.add(tag);
        }
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < entityCount; i++) {
            order.add(i);
        }
        order.sort(Comparator
                .comparingDouble((Integer i) -> positions.get(i)[0])
                .thenComparingDouble(i -> positions.get(i)[1])
                .thenComparingDouble(i -> positions.get(i)[2])
                .thenComparing(i -> NbtCompat.getString(tags.get(i), "id")));
        for (int i : order) {
            double[] p = positions.get(i);
            builder.entity(p[0], p[1], p[2], tags.get(i));
        }

        return builder.build();
    }

    private static BlueprintBlockState randomState(Random random) {
        String id = "test:" + token(random, 3 + random.nextInt(6));
        int propCount = random.nextInt(4);
        SortedMap<String, String> props = new TreeMap<>();
        for (int i = 0; i < propCount; i++) {
            props.put(token(random, 1 + random.nextInt(5)), token(random, 1 + random.nextInt(5)));
        }
        return new BlueprintBlockState(id, props);
    }

    private static CompoundTag randomCompound(Random random) {
        CompoundTag tag = new CompoundTag();
        int fields = random.nextInt(4);
        for (int i = 0; i < fields; i++) {
            String key = "k" + i;
            switch (random.nextInt(3)) {
                case 0 -> tag.putInt(key, random.nextInt());
                case 1 -> tag.putString(key, token(random, 1 + random.nextInt(6)));
                default -> tag.putByte(key, (byte) random.nextInt(2));
            }
        }
        return tag;
    }

    private static String token(Random random, int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(TOKEN_CHARS.charAt(random.nextInt(TOKEN_CHARS.length())));
        }
        return sb.toString();
    }
}
