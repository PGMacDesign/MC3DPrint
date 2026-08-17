package com.pgmacdesign.mc3dprint.gametest;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.blueprint.Blueprint;
import com.pgmacdesign.mc3dprint.blueprint.BlueprintBlockState;
import com.pgmacdesign.mc3dprint.blueprint.BlueprintFileStore;
import com.pgmacdesign.mc3dprint.fu.SpoolItem;
import com.pgmacdesign.mc3dprint.item.BlueprintDiscItem;
import com.pgmacdesign.mc3dprint.machine.PrinterBlockEntity;
import com.pgmacdesign.mc3dprint.registry.ModBlocks;
import com.pgmacdesign.mc3dprint.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * A running job keeps its chunks loaded even when a neighbouring machine finishes.
 *
 * <p>Vanilla's {@code setChunkForced} is one boolean per chunk with no owner, so machines calling
 * it directly fought over shared chunks: the first to finish unforced the chunk out from under the
 * one still working, its block entity stopped ticking, and the job hung with no error. A chunk is
 * 16 blocks while build offsets reach 32 and deconstruct regions reach 64, so two machines on a
 * print farm collide easily. It also made the GameTest suite intermittently red, because several
 * tests share a chunk in the runner's grid.
 */
@GameTestHolder(MC3DPrint.MOD_ID)
@PrefixGameTestTemplate(false)
public class ChunkTicketGameTests {

    /** A printer whose blueprint is a 1x{@code height}x1 column directly above it. */
    private static PrinterBlockEntity printerAt(GameTestHelper helper, BlockPos pos, int height) {
        Blueprint.Builder builder = Blueprint.builder("chunk-ticket-test-" + height, 1, height, 1);
        for (int y = 0; y < height; y++) {
            builder.set(0, y, 0, BlueprintBlockState.parse("minecraft:iron_block"));
        }
        Blueprint blueprint = builder.build();
        UUID id = BlueprintFileStore.forServer(helper.getLevel().getServer()).save(blueprint);

        helper.setBlock(pos, ModBlocks.PRINTERS.get(2).get()); // T3
        if (!(helper.getBlockEntity(pos) instanceof PrinterBlockEntity printer)) {
            throw new GameTestAssertException("printer block entity missing at " + pos);
        }
        java.util.Optional.ofNullable(printer.getEnergyStorage()).ifPresent(energy -> {
            for (int i = 0; i < 60; i++) {
                energy.receiveEnergy(1_000, false);
            }
        });
        // T5 spool: spending is down-only, so it covers an iron block whatever tier that lands on.
        ItemStack spool = new ItemStack(ModItems.SPOOLS.get(4).get());
        SpoolItem.setFu(spool, 100_000);
        printer.spoolInventory().setStackInSlot(0, spool);

        ItemStack disc = new ItemStack(ModItems.BLUEPRINT_DISC.get());
        BlueprintDiscItem.writeBlueprint(disc, id, blueprint);
        printer.inventory().setStackInSlot(PrinterBlockEntity.SLOT_TEMPLATE, disc);
        printer.setAutoStart(true);
        return printer;
    }

    /**
     * A spot in the template that shares {@code chunk} with the anchor and is far enough away that
     * the two print columns cannot overlap into a zone conflict. The runner places templates at
     * arbitrary coordinates, so a 5x5 can straddle a chunk boundary; the pair has to be chosen
     * against the real placement rather than assumed.
     */
    private static BlockPos neighbourSharingChunk(GameTestHelper helper, BlockPos anchor, ChunkPos chunk) {
        for (int dx = 4; dx >= -4; dx--) {
            for (int dz = 4; dz >= -4; dz--) {
                if (Math.abs(dx) + Math.abs(dz) < 2) {
                    continue;
                }
                BlockPos candidate = anchor.offset(dx, 0, dz);
                if (candidate.getX() < 0 || candidate.getX() > 4
                        || candidate.getZ() < 0 || candidate.getZ() > 4) {
                    continue; // empty5 is a 5x5 footprint
                }
                if (chunk.equals(new ChunkPos(helper.absolutePos(candidate)))) {
                    return candidate;
                }
            }
        }
        return null;
    }

    /**
     * Two machines in one chunk. The short job finishes first; the chunk must stay held for the
     * long job that is still running, and be released only once that one finishes too.
     */
    @GameTest(template = "empty5", timeoutTicks = 800)
    public static void aFinishingNeighbourDoesNotDropTheChunkOutFromUnderARunningJob(
            GameTestHelper helper) {
        BlockPos anchor = new BlockPos(1, 1, 1);
        ChunkPos chunk = new ChunkPos(helper.absolutePos(anchor));
        BlockPos neighbour = neighbourSharingChunk(helper, anchor, chunk);
        if (neighbour == null) {
            throw new GameTestAssertException(
                    "no second spot in this template shares a chunk with the anchor");
        }
        PrinterBlockEntity longJob = printerAt(helper, anchor, 4);
        PrinterBlockEntity shortJob = printerAt(helper, neighbour, 1);

        helper.startSequence()
                .thenWaitUntil(() -> {
                    if (shortJob.activeJob() != null || shortJob.lastPrint() == null) {
                        throw new GameTestAssertException("short job has not finished yet");
                    }
                    if (longJob.activeJob() == null) {
                        throw new GameTestAssertException("long job is not running yet");
                    }
                })
                .thenExecute(() -> {
                    // The short job has released; the long job is still going and still needs this.
                    if (!helper.getLevel().getForcedChunks().contains(chunk.toLong())) {
                        throw new GameTestAssertException(
                                "a finishing neighbour released the chunk the running job needs");
                    }
                })
                .thenWaitUntil(() -> {
                    if (longJob.activeJob() != null) {
                        throw new GameTestAssertException("long job has not finished yet");
                    }
                })
                .thenSucceed();
        // Deliberately not asserted here: that the chunk is unforced once both finish. The runner
        // packs several tests into a chunk, so any of them holding a job of its own would make a
        // global "not forced" check fail for reasons that have nothing to do with this code.
    }
}
