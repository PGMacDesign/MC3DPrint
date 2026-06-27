package com.pgmacdesign.mc3dprint.compat;

import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.neoforged.neoforge.items.ItemStackHandler;
//? if >=1.21.5 {
/*import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
*///?}

/**
 * Version seam for BlockEntity persistence. In 1.21.5 vanilla replaced the NBT-based
 * {@code saveAdditional(CompoundTag, Provider)} / {@code loadAdditional(CompoundTag, Provider)}
 * with the serialization-target abstraction {@code saveAdditional(ValueOutput)} /
 * {@code loadAdditional(ValueInput)} (the registry Provider is carried inside the target), and
 * NeoForge's {@code ItemStackHandler} moved from {@code INBTSerializable} to
 * {@code ValueIOSerializable} ({@code serialize(ValueOutput)} / {@code deserialize(ValueInput)}).
 *
 * <p>Each BlockEntity keeps a single version-agnostic {@code writeData(Writer)} / {@code readData(Reader)}
 * body and only its thin {@code saveAdditional}/{@code loadAdditional} signature wrapper is guarded.
 * On &lt;1.21.5 these facades are backed by a {@link CompoundTag} + {@link HolderLookup.Provider};
 * on 1.21.5+ they delegate straight to {@code ValueOutput}/{@code ValueInput}. The only divergence
 * lives here.
 */
public final class BeData {
    private BeData() {}

    /**
     * Restore a placed BlockEntity from a captured {@link CompoundTag} (the structure-print
     * path). 1.21.5 replaced {@code loadWithComponents(CompoundTag, Provider)} with
     * {@code loadWithComponents(ValueInput)} — same input wrapped via {@code TagValueInput}.
     */
    public static void loadInto(net.minecraft.world.level.block.entity.BlockEntity be,
                                CompoundTag tag, HolderLookup.Provider provider) {
        normalizeSignText(tag);
        //? if >=1.21.5 {
        /*try (net.minecraft.util.ProblemReporter.ScopedCollector pr =
                new net.minecraft.util.ProblemReporter.ScopedCollector(be.problemPath(),
                        com.mojang.logging.LogUtils.getLogger())) {
            be.loadWithComponents(
                    net.minecraft.world.level.storage.TagValueInput.create(pr, provider, tag));
        }
        *///?} else {
        be.loadWithComponents(tag, provider);
        //?}
    }

    /**
     * Curated blueprints bake sign text in the legacy component format — each line a JSON string
     * like {@code {"text":"…"}} (see {@code CuratedBlueprintGenerator.signFace}). The
     * {@code .blueprint} binaries are shared across all loaders. MC kept reading that JSON form
     * for sign {@code messages} through 1.21.4, but in 1.21.5 the codec switched to reading a bare
     * string as LITERAL text — so on 1.21.5+ the baked JSON renders verbatim on the placed sign.
     * On those versions only, convert each message to the native component-NBT form before the
     * BlockEntity loads. 1.21.1 (and the 1.20.1 legacy branch) still want the JSON, so this is a
     * compile-time no-op there.
     *
     * <p>Idempotent and format-detecting: a native message (a raw literal like {@code Cactus farm},
     * or a player-scanned 1.21.5+ sign) doesn't start with '{' / '[' and passes through untouched,
     * so it is safe to run on every BlockEntity. Covers both {@code minecraft:sign} and
     * {@code minecraft:hanging_sign} (both carry {@code front_text}/{@code back_text}).
     */
    private static void normalizeSignText(CompoundTag tag) {
        //? if >=1.21.5 {
        /*normalizeSignFace(tag, "front_text");
        normalizeSignFace(tag, "back_text");
        *///?}
    }

    private static void normalizeSignFace(CompoundTag tag, String faceKey) {
        if (!NbtCompat.contains(tag, faceKey)) {
            return;
        }
        CompoundTag face = NbtCompat.getCompound(tag, faceKey);
        if (!NbtCompat.contains(face, "messages")) {
            return;
        }
        ListTag messages = NbtCompat.getList(face, "messages", Tag.TAG_STRING);
        ListTag converted = new ListTag();
        boolean changed = false;
        for (int i = 0; i < messages.size(); i++) {
            Tag entry = messages.get(i);
            Tag nativeForm = entry instanceof StringTag ? legacyComponentToNative(NbtCompat.tagAsString(entry)) : null;
            if (nativeForm != null) {
                converted.add(nativeForm);
                changed = true;
            } else {
                converted.add(entry);
            }
        }
        if (changed) {
            face.put("messages", converted);
            tag.put(faceKey, face);
        }
    }

    /**
     * A legacy sign message ({@code {"text":"…"}} JSON) re-encoded to the modern component-NBT
     * form (a plain literal collapses to a raw {@code StringTag}; styled text to a compound).
     * Returns {@code null} when {@code value} is already native (not object/array JSON) or fails
     * to parse — caller keeps the original tag. Package-private for {@code BeDataSignTextTest}.
     */
    static Tag legacyComponentToNative(String value) {
        String trimmed = value.trim();
        if (trimmed.isEmpty() || (trimmed.charAt(0) != '{' && trimmed.charAt(0) != '[')) {
            return null;
        }
        try {
            JsonElement json = JsonParser.parseString(trimmed);
            Component component = ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, json).result().orElse(null);
            if (component == null) {
                return null;
            }
            return ComponentSerialization.CODEC.encodeStart(NbtOps.INSTANCE, component).result().orElse(null);
        } catch (RuntimeException e) {
            return null; // not a component — leave the original message untouched
        }
    }

    /** Write side. Mirrors the subset of {@code ValueOutput} the mod's BlockEntities use. */
    public interface Writer {
        void putInt(String key, int value);
        void putLong(String key, long value);
        void putFloat(String key, float value);
        void putDouble(String key, double value);
        void putBoolean(String key, boolean value);
        void putByte(String key, byte value);
        void putShort(String key, short value);
        void putString(String key, String value);
        void putIntArray(String key, int[] value);
        void putUUID(String key, UUID value);
        void putBlockPos(String key, BlockPos value);
        /** Nest an {@code ItemStackHandler} (inventory/spools/upgrades/resins) under {@code key}. */
        void putHandler(String key, ItemStackHandler handler);
        /** Open a nested child writer for hand-rolled compound payloads. */
        Writer child(String key);
        <T> void store(String key, Codec<T> codec, T value);
    }

    /** Read side. Optional-returning to match the 1.21.5+ {@code ValueInput} contract. */
    public interface Reader {
        int getIntOr(String key, int def);
        long getLongOr(String key, long def);
        float getFloatOr(String key, float def);
        double getDoubleOr(String key, double def);
        boolean getBooleanOr(String key, boolean def);
        byte getByteOr(String key, byte def);
        short getShortOr(String key, short def);
        String getStringOr(String key, String def);
        int[] getIntArray(String key);
        /** Present/absent-distinguishing reads (mirror {@code ValueInput}'s Optional getters). */
        Optional<Integer> getInt(String key);
        Optional<Long> getLong(String key);
        Optional<String> getString(String key);
        Optional<UUID> getUUID(String key);
        Optional<BlockPos> getBlockPos(String key);
        /** Read an {@code ItemStackHandler} previously written with {@link Writer#putHandler}. */
        void readHandler(String key, ItemStackHandler handler);
        /** Nested child reader; empty (never null) when the key is absent. */
        Reader childOrEmpty(String key);
        <T> Optional<T> read(String key, Codec<T> codec);
    }

    //? if >=1.21.5 {
    /*public static Writer writer(ValueOutput out) { return new VWriter(out); }
    public static Reader reader(ValueInput in) { return new VReader(in); }

    private record VWriter(ValueOutput out) implements Writer {
        public void putInt(String k, int v) { out.putInt(k, v); }
        public void putLong(String k, long v) { out.putLong(k, v); }
        public void putFloat(String k, float v) { out.putFloat(k, v); }
        public void putDouble(String k, double v) { out.putDouble(k, v); }
        public void putBoolean(String k, boolean v) { out.putBoolean(k, v); }
        public void putByte(String k, byte v) { out.putByte(k, v); }
        public void putShort(String k, short v) { out.putShort(k, v); }
        public void putString(String k, String v) { out.putString(k, v); }
        public void putIntArray(String k, int[] v) { out.putIntArray(k, v); }
        public void putUUID(String k, UUID v) { out.store(k, UUIDUtil.CODEC, v); }
        public void putBlockPos(String k, BlockPos v) { out.store(k, BlockPos.CODEC, v); }
        public void putHandler(String k, ItemStackHandler h) { h.serialize(out.child(k)); }
        public Writer child(String k) { return new VWriter(out.child(k)); }
        public <T> void store(String k, Codec<T> c, T v) { out.store(k, c, v); }
    }

    private record VReader(ValueInput in) implements Reader {
        public int getIntOr(String k, int d) { return in.getIntOr(k, d); }
        public long getLongOr(String k, long d) { return in.getLongOr(k, d); }
        public float getFloatOr(String k, float d) { return in.getFloatOr(k, d); }
        public double getDoubleOr(String k, double d) { return in.getDoubleOr(k, d); }
        public boolean getBooleanOr(String k, boolean d) { return in.getBooleanOr(k, d); }
        public byte getByteOr(String k, byte d) { return in.getByteOr(k, d); }
        public short getShortOr(String k, short d) { return (short) in.getShortOr(k, d); }
        public String getStringOr(String k, String d) { return in.getStringOr(k, d); }
        public int[] getIntArray(String k) { return in.getIntArray(k).orElse(new int[0]); }
        public Optional<Integer> getInt(String k) { return in.getInt(k); }
        public Optional<Long> getLong(String k) { return in.getLong(k); }
        public Optional<String> getString(String k) { return in.getString(k); }
        public Optional<UUID> getUUID(String k) { return in.read(k, UUIDUtil.CODEC); }
        public Optional<BlockPos> getBlockPos(String k) { return in.read(k, BlockPos.CODEC); }
        public void readHandler(String k, ItemStackHandler h) { h.deserialize(in.childOrEmpty(k)); }
        public Reader childOrEmpty(String k) { return new VReader(in.childOrEmpty(k)); }
        public <T> Optional<T> read(String k, Codec<T> c) { return in.read(k, c); }
    }
    *///?} else {
    public static Writer writer(CompoundTag tag, HolderLookup.Provider provider) { return new TagWriter(tag, provider); }
    public static Reader reader(CompoundTag tag, HolderLookup.Provider provider) { return new TagReader(tag, provider); }

    private record TagWriter(CompoundTag tag, HolderLookup.Provider provider) implements Writer {
        public void putInt(String k, int v) { tag.putInt(k, v); }
        public void putLong(String k, long v) { tag.putLong(k, v); }
        public void putFloat(String k, float v) { tag.putFloat(k, v); }
        public void putDouble(String k, double v) { tag.putDouble(k, v); }
        public void putBoolean(String k, boolean v) { tag.putBoolean(k, v); }
        public void putByte(String k, byte v) { tag.putByte(k, v); }
        public void putShort(String k, short v) { tag.putShort(k, v); }
        public void putString(String k, String v) { tag.putString(k, v); }
        public void putIntArray(String k, int[] v) { tag.putIntArray(k, v); }
        public void putUUID(String k, UUID v) { tag.putUUID(k, v); }
        public void putBlockPos(String k, BlockPos v) { tag.put(k, NbtUtils.writeBlockPos(v)); }
        public void putHandler(String k, ItemStackHandler h) { tag.put(k, h.serializeNBT(provider)); }
        public Writer child(String k) {
            CompoundTag c = new CompoundTag();
            tag.put(k, c);
            return new TagWriter(c, provider);
        }
        public <T> void store(String k, Codec<T> c, T v) {
            c.encodeStart(provider.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), v)
                .resultOrPartial()
                .ifPresent(t -> tag.put(k, t));
        }
    }

    private record TagReader(CompoundTag tag, HolderLookup.Provider provider) implements Reader {
        public int getIntOr(String k, int d) { return tag.contains(k) ? tag.getInt(k) : d; }
        public long getLongOr(String k, long d) { return tag.contains(k) ? tag.getLong(k) : d; }
        public float getFloatOr(String k, float d) { return tag.contains(k) ? tag.getFloat(k) : d; }
        public double getDoubleOr(String k, double d) { return tag.contains(k) ? tag.getDouble(k) : d; }
        public boolean getBooleanOr(String k, boolean d) { return tag.contains(k) ? tag.getBoolean(k) : d; }
        public byte getByteOr(String k, byte d) { return tag.contains(k) ? tag.getByte(k) : d; }
        public short getShortOr(String k, short d) { return tag.contains(k) ? tag.getShort(k) : d; }
        public String getStringOr(String k, String d) { return tag.contains(k) ? tag.getString(k) : d; }
        public int[] getIntArray(String k) { return tag.getIntArray(k); }
        public Optional<Integer> getInt(String k) { return tag.contains(k) ? Optional.of(tag.getInt(k)) : Optional.empty(); }
        public Optional<Long> getLong(String k) { return tag.contains(k) ? Optional.of(tag.getLong(k)) : Optional.empty(); }
        public Optional<String> getString(String k) { return tag.contains(k) ? Optional.of(tag.getString(k)) : Optional.empty(); }
        public Optional<UUID> getUUID(String k) { return tag.hasUUID(k) ? Optional.of(tag.getUUID(k)) : Optional.empty(); }
        public Optional<BlockPos> getBlockPos(String k) { return NbtUtils.readBlockPos(tag, k); }
        public void readHandler(String k, ItemStackHandler h) { h.deserializeNBT(provider, NbtCompat.getCompound(tag, k)); }
        public Reader childOrEmpty(String k) { return new TagReader(NbtCompat.getCompound(tag, k), provider); }
        public <T> Optional<T> read(String k, Codec<T> c) {
            if (!tag.contains(k)) return Optional.empty();
            return c.parse(provider.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), tag.get(k)).result();
        }
    }
    //?}
}
