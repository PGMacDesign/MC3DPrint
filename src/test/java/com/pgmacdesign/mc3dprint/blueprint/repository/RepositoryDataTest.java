package com.pgmacdesign.mc3dprint.blueprint.repository;

import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepositoryDataTest {

    private static RepoEntry sample(String name) {
        return new RepoEntry(UUID.nameUUIDFromBytes(name.getBytes()), name, 5, 3, 7, 42, 4, 1234, true);
    }

    @Test
    void repoEntryNbtRoundTrips() {
        RepoEntry original = sample("castle_keep");
        assertEquals(original, RepoEntry.fromNbt(original.toNbt()));
    }

    @Test
    void repoEntryBufRoundTrips() {
        RepoEntry original = new RepoEntry(UUID.randomUUID(), "my scan", 1, 2, 3, 9, 2, 80, false);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        original.toBuf(buf);
        assertEquals(original, RepoEntry.fromBuf(buf));
    }

    @Test
    void addReturnsTrueOnceThenDeduplicates() {
        RepositoryData data = new RepositoryData();
        RepoEntry entry = sample("windmill");
        assertTrue(data.add(entry), "first add is new");
        assertFalse(data.add(entry), "re-deposit of the same blueprint is a no-op");
        assertEquals(1, data.entries().size());
        assertTrue(data.contains(entry.id()));
    }

    @Test
    void savedDataSurvivesRoundTrip() {
        RepositoryData data = new RepositoryData();
        data.add(sample("a_house"));
        data.add(sample("b_tower"));

        CompoundTag tag = data.save(new CompoundTag());
        RepositoryData restored = RepositoryData.load(tag);

        assertEquals(2, restored.entries().size());
        assertTrue(restored.contains(sample("a_house").id()));
        assertTrue(restored.contains(sample("b_tower").id()));
    }
}
