package com.pgmacdesign.mc3dprint.blueprint;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * A registry-free block state: block id plus property strings, e.g.
 * {@code minecraft:oak_stairs[facing=east,half=top]}.
 *
 * The blueprint core never touches live registries so it can be unit-tested
 * headless and ported across loaders; states resolve to {@link BlockState}
 * only at placement time via {@link #resolve()}.
 */
public final class BlueprintBlockState {
    public static final String AIR_ID = "minecraft:air";

    private final String blockId;
    private final SortedMap<String, String> properties;

    public BlueprintBlockState(String blockId, Map<String, String> properties) {
        this.blockId = normalizeId(blockId);
        this.properties = new TreeMap<>(properties);
    }

    public BlueprintBlockState(String blockId) {
        this(blockId, Map.of());
    }

    public static String normalizeId(String id) {
        return id.indexOf(':') >= 0 ? id : "minecraft:" + id;
    }

    /** Captures a live BlockState into the registry-free form. Game-side only. */
    public static BlueprintBlockState fromBlockState(BlockState state) {
        ResourceLocation key = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (key == null) {
            throw new IllegalArgumentException("Block has no registry key: " + state);
        }
        SortedMap<String, String> properties = new TreeMap<>();
        for (Property<?> property : state.getProperties()) {
            properties.put(property.getName(), propertyName(state, property));
        }
        return new BlueprintBlockState(key.toString(), properties);
    }

    private static <T extends Comparable<T>> String propertyName(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    /** Parses {@code namespace:path[key=value,...]}. */
    public static BlueprintBlockState parse(String serialized) {
        int bracket = serialized.indexOf('[');
        if (bracket < 0) {
            return new BlueprintBlockState(serialized.trim());
        }
        // Validate the bracket/'=' structure so a malformed palette string ("stone[", "foo[bar]")
        // fails as a format error the caller handles, not an escaping StringIndexOutOfBounds.
        int close = serialized.lastIndexOf(']');
        if (close < bracket) {
            throw new BlueprintFormatException("Malformed block state (no closing ']'): " + serialized);
        }
        String id = serialized.substring(0, bracket).trim();
        String body = serialized.substring(bracket + 1, close).trim();
        SortedMap<String, String> props = new TreeMap<>();
        if (!body.isEmpty()) {
            for (String pair : body.split(",")) {
                int eq = pair.indexOf('=');
                if (eq < 0) {
                    throw new BlueprintFormatException("Malformed block state property (missing '='): " + serialized);
                }
                props.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
            }
        }
        return new BlueprintBlockState(id, props);
    }

    public String blockId() {
        return blockId;
    }

    public Map<String, String> properties() {
        return properties;
    }

    public boolean isAir() {
        return AIR_ID.equals(blockId) || "minecraft:cave_air".equals(blockId) || "minecraft:void_air".equals(blockId);
    }

    /** Serialized canonical form; properties are sorted so equal states serialize identically. */
    public String serialize() {
        if (properties.isEmpty()) {
            return blockId;
        }
        StringBuilder sb = new StringBuilder(blockId).append('[');
        boolean first = true;
        for (Map.Entry<String, String> e : properties.entrySet()) {
            if (!first) sb.append(',');
            sb.append(e.getKey()).append('=').append(e.getValue());
            first = false;
        }
        return sb.append(']').toString();
    }

    /**
     * Resolves to a live {@link BlockState}. Game-side only — requires registries.
     * Unknown blocks or properties resolve to empty so a print can skip them
     * gracefully (e.g. blueprint from a world with mods this world lacks).
     */
    public Optional<BlockState> resolve() {
        ResourceLocation id = ResourceLocation.tryParse(blockId);
        if (id == null || !ForgeRegistries.BLOCKS.containsKey(id)) {
            return Optional.empty();
        }
        Block block = ForgeRegistries.BLOCKS.getValue(id);
        if (block == null) {
            return Optional.empty();
        }
        BlockState state = block.defaultBlockState();
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            state = withProperty(state, entry.getKey(), entry.getValue());
        }
        return Optional.of(state);
    }

    @Nullable
    private static <T extends Comparable<T>> BlockState trySet(BlockState state, Property<T> property, String value) {
        Optional<T> parsed = property.getValue(value);
        return parsed.map(t -> state.setValue(property, t)).orElse(null);
    }

    private static BlockState withProperty(BlockState state, String key, String value) {
        Property<?> property = state.getBlock().getStateDefinition().getProperty(key);
        if (property == null) {
            return state;
        }
        BlockState updated = trySet(state, property, value);
        return updated != null ? updated : state;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BlueprintBlockState other)) return false;
        return blockId.equals(other.blockId) && properties.equals(other.properties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(blockId, properties);
    }

    @Override
    public String toString() {
        return serialize();
    }
}
