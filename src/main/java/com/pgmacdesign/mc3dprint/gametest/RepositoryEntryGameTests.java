package com.pgmacdesign.mc3dprint.gametest;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.blueprint.Blueprint;
import com.pgmacdesign.mc3dprint.blueprint.BlueprintBlockState;
import com.pgmacdesign.mc3dprint.blueprint.BlueprintFileStore;
import com.pgmacdesign.mc3dprint.blueprint.repository.RepoEntry;
import com.pgmacdesign.mc3dprint.blueprint.repository.RepositoryIndex;
import com.pgmacdesign.mc3dprint.machine.repository.BlueprintRepositoryBlockEntity;
import com.pgmacdesign.mc3dprint.network.RepositoryRenamePacket;
import com.pgmacdesign.mc3dprint.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * Editing a catalogued blueprint: renaming writes through to both the catalogue and the stored
 * blueprint, removal is limited to the depositor (or an operator), official builds are refused
 * for both, and hostile names never reach storage.
 */
@GameTestHolder(MC3DPrint.MOD_ID)
@PrefixGameTestTemplate(false)
public class RepositoryEntryGameTests {

    private static final BlockPos REPO_POS = new BlockPos(1, 1, 1);

    private static BlueprintRepositoryBlockEntity repository(GameTestHelper helper) {
        helper.setBlock(REPO_POS, ModBlocks.BLUEPRINT_REPOSITORY.get());
        if (!(helper.getBlockEntity(REPO_POS) instanceof BlueprintRepositoryBlockEntity repo)) {
            throw new GameTestAssertException("repository block entity missing");
        }
        return repo;
    }

    /**
     * A NeoForge FakePlayer, not {@code makeMockServerPlayerInLevel}: the latter calls
     * placeNewPlayer, so every test would leave a permanent player on the server and the
     * run never finishes. A FakePlayer is also the honest subject here, since it is exactly
     * the kind of connection-less player the listing send has to tolerate.
     */
    private static ServerPlayer fakePlayer(GameTestHelper helper) {
        return net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(helper.getLevel());
    }

    /** Catalogues a one-block blueprint under {@code name} and returns its id. */
    private static UUID catalogue(GameTestHelper helper, ServerPlayer player, String name,
            boolean official) {
        Blueprint blueprint = Blueprint.builder(name, 1, 1, 1)
                .set(0, 0, 0, BlueprintBlockState.parse("minecraft:stone"))
                .build();
        UUID id = BlueprintFileStore.forServer(helper.getLevel().getServer()).save(blueprint);
        RepositoryIndex.add(player, new RepoEntry(id, name, 1, 1, 1, 1, 1, 10, official,
                official ? null : player.getUUID()));
        return id;
    }

    /** Catalogues a scan attributed to somebody else, for the not-your-entry gate. */
    private static UUID catalogueForStranger(GameTestHelper helper, ServerPlayer player, String name) {
        Blueprint blueprint = Blueprint.builder(name, 1, 1, 1)
                .set(0, 0, 0, BlueprintBlockState.parse("minecraft:stone"))
                .build();
        UUID id = BlueprintFileStore.forServer(helper.getLevel().getServer()).save(blueprint);
        RepositoryIndex.add(player, new RepoEntry(id, name, 1, 1, 1, 1, 1, 10, false,
                UUID.fromString("00000000-0000-0000-0000-00000000beef")));
        return id;
    }

    @GameTest(template = "empty5", timeoutTicks = 40)
    public static void depositorCanRemoveTheirOwnScan(GameTestHelper helper) {
        BlueprintRepositoryBlockEntity repo = repository(helper);
        ServerPlayer player = fakePlayer(helper);
        UUID id = catalogue(helper, player, "Scan @ 4,5,6", false);

        repo.delete(player, id);

        if (RepositoryIndex.find(player, id) != null) {
            throw new GameTestAssertException("the depositor could not remove their own scan");
        }
        // The blueprint FILE survives, so a disc burned earlier still prints and a
        // re-deposit restores the entry. Removal is recoverable, not destructive.
        if (!BlueprintFileStore.forServer(helper.getLevel().getServer()).exists(id)) {
            throw new GameTestAssertException("removing an entry must not delete the blueprint file");
        }
        helper.succeed();
    }

    /**
     * Re-depositing a copy of someone else's scan must not transfer the right to remove it.
     * The shared store used to overwrite the entry before reporting "already catalogued", so
     * burning a copy and depositing it reassigned the depositor.
     */
    @GameTest(template = "empty5", timeoutTicks = 40)
    public static void reDepositingCannotStealAnEntry(GameTestHelper helper) {
        BlueprintRepositoryBlockEntity repo = repository(helper);
        ServerPlayer thief = fakePlayer(helper);
        UUID owner = UUID.fromString("00000000-0000-0000-0000-00000000beef");

        Blueprint blueprint = Blueprint.builder("someone's tower", 1, 1, 1)
                .set(0, 0, 0, BlueprintBlockState.parse("minecraft:stone"))
                .build();
        UUID id = BlueprintFileStore.forServer(helper.getLevel().getServer()).save(blueprint);
        RepositoryIndex.add(thief, new RepoEntry(id, "someone's tower", 1, 1, 1, 1, 1, 10, false, owner));

        // The thief deposits their own copy of the same blueprint...
        RepositoryIndex.add(thief, new RepoEntry(id, "someone's tower", 1, 1, 1, 1, 1, 10, false,
                thief.getUUID()));

        RepoEntry after = RepositoryIndex.find(thief, id);
        if (after == null || !owner.equals(after.depositor())) {
            throw new GameTestAssertException("re-deposit reassigned the depositor to "
                    + (after == null ? "<missing>" : String.valueOf(after.depositor())));
        }
        // ...and therefore still can't remove it.
        repo.delete(thief, id);
        if (RepositoryIndex.find(thief, id) == null) {
            throw new GameTestAssertException("a re-deposit bought the right to remove the entry");
        }
        helper.succeed();
    }

    @GameTest(template = "empty5", timeoutTicks = 40)
    public static void someoneElsesScanIsRefused(GameTestHelper helper) {
        BlueprintRepositoryBlockEntity repo = repository(helper);
        ServerPlayer player = fakePlayer(helper);
        UUID id = catalogueForStranger(helper, player, "Someone else's tower");

        // A FakePlayer has no permissions, so this is the plain non-depositor case.
        repo.delete(player, id);

        if (RepositoryIndex.find(player, id) == null) {
            throw new GameTestAssertException("a non-depositor removed someone else's entry");
        }
        helper.succeed();
    }

    @GameTest(template = "empty5", timeoutTicks = 40)
    public static void officialBuildsRefuseToBeRemoved(GameTestHelper helper) {
        BlueprintRepositoryBlockEntity repo = repository(helper);
        ServerPlayer player = fakePlayer(helper);
        UUID id = catalogue(helper, player, "Grand Library", true);

        repo.delete(player, id);

        if (RepositoryIndex.find(player, id) == null) {
            throw new GameTestAssertException("an official build was removed from the library");
        }
        helper.succeed();
    }

    /** An entry catalogued before depositors were tracked has no owner: operator-only. */
    @GameTest(template = "empty5", timeoutTicks = 40)
    public static void unattributedLegacyEntryIsNotRemovableByAPlayer(GameTestHelper helper) {
        BlueprintRepositoryBlockEntity repo = repository(helper);
        ServerPlayer player = fakePlayer(helper);
        Blueprint blueprint = Blueprint.builder("legacy scan", 1, 1, 1)
                .set(0, 0, 0, BlueprintBlockState.parse("minecraft:stone"))
                .build();
        UUID id = BlueprintFileStore.forServer(helper.getLevel().getServer()).save(blueprint);
        // The 9-arg constructor is exactly what pre-existing NBT loads as.
        RepositoryIndex.add(player, new RepoEntry(id, "legacy scan", 1, 1, 1, 1, 1, 10, false));

        repo.delete(player, id);

        if (RepositoryIndex.find(player, id) == null) {
            throw new GameTestAssertException("an unattributed entry was removed by a non-operator");
        }
        helper.succeed();
    }

    @GameTest(template = "empty5", timeoutTicks = 40)
    public static void renamingAScanUpdatesTheCatalogueAndTheBlueprint(GameTestHelper helper) {
        BlueprintRepositoryBlockEntity repo = repository(helper);
        ServerPlayer player = fakePlayer(helper);
        UUID id = catalogue(helper, player, "Scan @ 307,70,10", false);

        repo.rename(player, id, "Chicken farm house");

        RepoEntry entry = RepositoryIndex.find(player, id);
        if (entry == null || !"Chicken farm house".equals(entry.name())) {
            throw new GameTestAssertException("catalogue name was "
                    + (entry == null ? "<missing>" : entry.name()));
        }
        // The stored blueprint carries it too, so a disc burned later isn't stale.
        String stored = BlueprintFileStore.forServer(helper.getLevel().getServer())
                .load(id).map(Blueprint::name).orElse("<missing>");
        if (!"Chicken farm house".equals(stored)) {
            throw new GameTestAssertException("stored blueprint name was " + stored);
        }
        helper.succeed();
    }

    /**
     * A rename is not display-only: it rewrites the stored blueprint, so on the default shared
     * library an ungated one would let any player retitle a build somebody else contributed and
     * lose the original name. Same depositor-or-operator gate as removal.
     */
    @GameTest(template = "empty5", timeoutTicks = 40)
    public static void someoneElsesScanRefusesToBeRenamed(GameTestHelper helper) {
        BlueprintRepositoryBlockEntity repo = repository(helper);
        ServerPlayer player = fakePlayer(helper);
        UUID id = catalogueForStranger(helper, player, "Someone else's tower");

        // A FakePlayer has no permissions, so this is the plain non-depositor case.
        repo.rename(player, id, "mine now");

        RepoEntry entry = RepositoryIndex.find(player, id);
        if (entry == null || !"Someone else's tower".equals(entry.name())) {
            throw new GameTestAssertException("a non-depositor renamed someone else's entry to "
                    + (entry == null ? "<missing>" : entry.name()));
        }
        // The stored blueprint is what a re-burn stamps on a disc; it must be untouched too.
        String stored = BlueprintFileStore.forServer(helper.getLevel().getServer())
                .load(id).map(Blueprint::name).orElse("<missing>");
        if (!"Someone else's tower".equals(stored)) {
            throw new GameTestAssertException("a refused rename still rewrote the blueprint: " + stored);
        }
        helper.succeed();
    }

    /** An entry catalogued before depositors were tracked has no owner: operator-only. */
    @GameTest(template = "empty5", timeoutTicks = 40)
    public static void unattributedLegacyEntryIsNotRenamableByAPlayer(GameTestHelper helper) {
        BlueprintRepositoryBlockEntity repo = repository(helper);
        ServerPlayer player = fakePlayer(helper);
        Blueprint blueprint = Blueprint.builder("legacy scan", 1, 1, 1)
                .set(0, 0, 0, BlueprintBlockState.parse("minecraft:stone"))
                .build();
        UUID id = BlueprintFileStore.forServer(helper.getLevel().getServer()).save(blueprint);
        // The 9-arg constructor is exactly what pre-existing NBT loads as.
        RepositoryIndex.add(player, new RepoEntry(id, "legacy scan", 1, 1, 1, 1, 1, 10, false));

        repo.rename(player, id, "mine now");

        RepoEntry entry = RepositoryIndex.find(player, id);
        if (entry == null || !"legacy scan".equals(entry.name())) {
            throw new GameTestAssertException("an unattributed entry was renamed to "
                    + (entry == null ? "<missing>" : entry.name()));
        }
        helper.succeed();
    }

    @GameTest(template = "empty5", timeoutTicks = 40)
    public static void officialBuildsRefuseToBeRenamed(GameTestHelper helper) {
        BlueprintRepositoryBlockEntity repo = repository(helper);
        ServerPlayer player = fakePlayer(helper);
        UUID id = catalogue(helper, player, "Grand Library", true);

        repo.rename(player, id, "mine now");

        RepoEntry entry = RepositoryIndex.find(player, id);
        if (entry == null || !"Grand Library".equals(entry.name())) {
            throw new GameTestAssertException("an official build was renamed to "
                    + (entry == null ? "<missing>" : entry.name()));
        }
        helper.succeed();
    }

    @GameTest(template = "empty5", timeoutTicks = 40)
    public static void blankAndHostileNamesNeverReachStorage(GameTestHelper helper) {
        BlueprintRepositoryBlockEntity repo = repository(helper);
        ServerPlayer player = fakePlayer(helper);
        UUID id = catalogue(helper, player, "Scan @ 1,2,3", false);

        repo.rename(player, id, "   ");
        if (!"Scan @ 1,2,3".equals(RepositoryIndex.find(player, id).name())) {
            throw new GameTestAssertException("a blank name was accepted");
        }

        // Formatting codes and newlines would break the one-line row and let a player inject
        // colour into the listing; both are stripped before anything is stored.
        repo.rename(player, id, "§cred\nname");
        String stored = RepositoryIndex.find(player, id).name();
        if (stored.contains("§") || stored.contains("\n")) {
            throw new GameTestAssertException("unsanitised name stored: " + stored);
        }
        if (!"cred name".equals(stored)) {
            throw new GameTestAssertException("expected 'cred name', got '" + stored + "'");
        }

        // Over-long input is capped rather than rejected.
        repo.rename(player, id, "x".repeat(RepositoryRenamePacket.MAX_NAME_LENGTH + 40));
        int length = RepositoryIndex.find(player, id).name().length();
        if (length != RepositoryRenamePacket.MAX_NAME_LENGTH) {
            throw new GameTestAssertException("name length was " + length);
        }
        helper.succeed();
    }
}
