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
 * Renaming a catalogued blueprint: scans are retitled through to both the catalogue and the
 * stored blueprint, official builds are refused, and hostile names never reach storage.
 */
@GameTestHolder(MC3DPrint.MOD_ID)
@PrefixGameTestTemplate(false)
public class RepositoryRenameGameTests {

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
        RepositoryIndex.add(player, new RepoEntry(id, name, 1, 1, 1, 1, 1, 10, official));
        return id;
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
