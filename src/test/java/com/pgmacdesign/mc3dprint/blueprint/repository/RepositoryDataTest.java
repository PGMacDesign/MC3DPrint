package com.pgmacdesign.mc3dprint.blueprint.repository;

import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    void depositorSurvivesBothRoundTrips() {
        UUID depositor = UUID.randomUUID();
        RepoEntry original = new RepoEntry(UUID.randomUUID(), "my scan", 1, 2, 3, 9, 2, 80, false,
                depositor);

        assertEquals(original, RepoEntry.fromNbt(original.toNbt()));
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        original.toBuf(buf);
        assertEquals(original, RepoEntry.fromBuf(buf));

        assertTrue(original.depositedBy(depositor));
        assertFalse(original.depositedBy(UUID.randomUUID()));
    }

    /**
     * Entries catalogued before depositors were tracked have no Depositor tag. They must load
     * as unattributed rather than throwing, and must never look like anyone's to remove.
     */
    @Test
    void entryWithoutADepositorLoadsUnattributed() {
        RepoEntry legacy = new RepoEntry(UUID.randomUUID(), "old scan", 1, 1, 1, 1, 1, 10, false);
        CompoundTag tag = legacy.toNbt();
        assertFalse(tag.contains("Depositor"), "an unattributed entry writes no Depositor tag");

        RepoEntry loaded = RepoEntry.fromNbt(tag);
        assertEquals(legacy, loaded);
        assertNull(loaded.depositor());
        assertFalse(loaded.depositedBy(UUID.randomUUID()));

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        legacy.toBuf(buf);
        assertEquals(legacy, RepoEntry.fromBuf(buf));
    }

    @Test
    void removeDropsTheEntryAndReportsWhetherItWasThere() {
        RepositoryData data = new RepositoryData();
        RepoEntry entry = sample("shed");
        data.add(entry);

        assertTrue(data.remove(entry.id()), "removing a catalogued entry reports true");
        assertFalse(data.contains(entry.id()));
        assertFalse(data.remove(entry.id()), "removing it again is a no-op");
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

    @Test
    void markPrintedDeduplicatesAndSurvivesRoundTrip() {
        RepositoryData data = new RepositoryData();
        UUID id = sample("windmill").id();
        assertTrue(data.markPrinted(id), "first mark is new");
        assertFalse(data.markPrinted(id), "re-printing the same build is a no-op");
        assertTrue(data.printed().contains(id));

        RepositoryData restored = RepositoryData.load(data.save(new CompoundTag()));
        assertEquals(1, restored.printed().size());
        assertTrue(restored.printed().contains(id));
    }
}
