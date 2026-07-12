package com.pgmacdesign.mc3dprint.blueprint;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Disk-backed blueprint storage: one gzipped-NBT {@code .blueprint} file per
 * blueprint under {@code world/mc3dprint/blueprints/}. Discs reference
 * blueprints by UUID; the payload never travels in item NBT.
 *
 * Server-side only. All access must come from the server thread (or be
 * externally synchronized) — no internal locking.
 */
public final class BlueprintFileStore {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String EXTENSION = ".blueprint";

    // Bound the decompressed NBT so a gzip bomb or a corrupt/oversized file can't OOM the
    // server thread on load. 64 MB sits comfortably above any real blueprint (the largest
    // curated builds are a few hundred KB).
    private static final long MAX_BLUEPRINT_BYTES = 64L * 1024 * 1024;
    private static final Pattern FILE_NAME = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\" + EXTENSION);

    private final Path directory;

    public BlueprintFileStore(Path directory) {
        this.directory = directory;
    }

    public static BlueprintFileStore forServer(MinecraftServer server) {
        return new BlueprintFileStore(server.getWorldPath(LevelResource.ROOT)
                .resolve("mc3dprint").resolve("blueprints"));
    }

    public Path directory() {
        return directory;
    }

    public Path pathFor(UUID id) {
        return directory.resolve(id + EXTENSION);
    }

    /** Persists the blueprint under a fresh UUID and returns it. */
    public UUID save(Blueprint blueprint) {
        UUID id = UUID.randomUUID();
        save(id, blueprint);
        return id;
    }

    public void save(UUID id, Blueprint blueprint) {
        try {
            Files.createDirectories(directory);
            NbtIo.writeCompressed(BlueprintSerializer.write(blueprint), pathFor(id));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write blueprint " + id, e);
        }
    }

    public Optional<Blueprint> load(UUID id) {
        Path path = pathFor(id);
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        try {
            CompoundTag tag = NbtIo.readCompressed(path, NbtAccounter.create(MAX_BLUEPRINT_BYTES));
            return Optional.of(BlueprintSerializer.read(tag));
        } catch (IOException | RuntimeException e) {
            // A corrupt/truncated/oversized file must degrade to "no blueprint"; this runs on
            // the printer's serverTick with no try/catch, so throwing here crashes the ticking
            // block entity (and crash-loops if the job auto-starts).
            LOGGER.warn("Failed to read blueprint {}: {}", id, e.toString());
            return Optional.empty();
        }
    }

    public boolean exists(UUID id) {
        return Files.isRegularFile(pathFor(id));
    }

    public boolean delete(UUID id) {
        try {
            return Files.deleteIfExists(pathFor(id));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete blueprint " + id, e);
        }
    }

    public List<UUID> list() {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(directory)) {
            List<UUID> ids = new ArrayList<>();
            files.forEach(path -> {
                String fileName = path.getFileName().toString();
                if (FILE_NAME.matcher(fileName).matches()) {
                    ids.add(UUID.fromString(fileName.substring(0, fileName.length() - EXTENSION.length())));
                }
            });
            return ids;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list blueprints in " + directory, e);
        }
    }
}
