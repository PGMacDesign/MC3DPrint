package com.pgmacdesign.mc3dprint.gametest;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.fu.FuValue;
import com.pgmacdesign.mc3dprint.fu.FuValueRegistry;
import com.pgmacdesign.mc3dprint.fu.SpoolItem;
import com.pgmacdesign.mc3dprint.machine.WinderBlockEntity;
import com.pgmacdesign.mc3dprint.machine.cable.MC3DCableBlock;
import com.pgmacdesign.mc3dprint.machine.sorter.SorterBlockEntity;
import com.pgmacdesign.mc3dprint.registry.ModBlockTags;
import com.pgmacdesign.mc3dprint.registry.ModBlocks;
import com.pgmacdesign.mc3dprint.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The Filament Tier Item Sorter: routes pooled items to the winder holding a spool of that item's
 * material tier. Covers routing by tier, permanent-reject at the door (blacklist + unvalued),
 * round-robin distribution across same-tier winders, stalled-winder skip, cable-reach parity with
 * direct-touch, the transient-hold-never-void guarantee, the insert-only funnel, and that
 * setStackInSlot is not a side door around the pool filter while rollback restores still are.
 *
 * <p>Winders are placed with NO energy on purpose, so a routed item accumulates visibly in the
 * input slot instead of being wound away before it can be asserted.
 */
@GameTestHolder(MC3DPrint.MOD_ID)
@PrefixGameTestTemplate(false)
public class SorterGameTests {

    /** Cobblestone's material tier, resolved live (its default value is T1, but read it to be safe). */
    private static int cobbleTier() {
        return FuValueRegistry.valueOf(new ItemStack(Items.COBBLESTONE))
                .map(FuValue::tier)
                .orElseThrow(() -> new GameTestAssertException("cobblestone has no FU value in this test env"));
    }

    private static ItemStack blankSpool(int tier) {
        return new ItemStack(ModItems.SPOOLS.get(tier - 1).get());
    }

    private static ItemStack fullSpool(int tier) {
        ItemStack spool = blankSpool(tier);
        if (spool.getItem() instanceof SpoolItem s) {
            SpoolItem.setFu(spool, s.capacity());
        }
        return spool;
    }

    private static SorterBlockEntity placeSorter(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.FILAMENT_ITEM_SORTER.get());
        if (!(helper.getBlockEntity(pos) instanceof SorterBlockEntity sorter)) {
            throw new GameTestAssertException("Sorter block entity missing at " + pos);
        }
        return sorter;
    }

    /** Places a winder with the given docked spool and NO energy (so it never winds the routed item). */
    private static WinderBlockEntity placeWinder(GameTestHelper helper, BlockPos pos, ItemStack spool) {
        helper.setBlock(pos, ModBlocks.FILAMENT_WINDER.get());
        if (!(helper.getBlockEntity(pos) instanceof WinderBlockEntity winder)) {
            throw new GameTestAssertException("Winder block entity missing at " + pos);
        }
        winder.inventory().setStackInSlot(WinderBlockEntity.SLOT_SPOOL, spool);
        return winder;
    }

    private static int inputCount(WinderBlockEntity winder) {
        return winder.inventory().getStackInSlot(WinderBlockEntity.SLOT_INPUT).getCount();
    }

    /** A vanilla chest, the stand-in for any non-MC3DPrint inventory used as a reject target. */
    private static Container placeChest(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, Blocks.CHEST);
        if (!(helper.getBlockEntity(pos) instanceof Container container)) {
            throw new GameTestAssertException("Chest container missing at " + pos);
        }
        return container;
    }

    private static int countIn(Container container, Item item) {
        int total = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.getItem() == item) {
                total += stack.getCount();
            }
        }
        return total;
    }

    // --- 1. Route by tier, direct touch ---

    @GameTest(template = "empty5", timeoutTicks = 120)
    public static void routesByTierDirectTouch(GameTestHelper helper) {
        int tier = cobbleTier();
        SorterBlockEntity sorter = placeSorter(helper, new BlockPos(2, 1, 2));
        WinderBlockEntity winder = placeWinder(helper, new BlockPos(3, 1, 2), blankSpool(tier));

        sorter.pool().setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 1));

        helper.succeedWhen(() -> {
            if (inputCount(winder) != 1) {
                throw new GameTestAssertException("winder should have 1 routed cobblestone, got " + inputCount(winder));
            }
            if (!sorter.pool().getStackInSlot(0).isEmpty()) {
                throw new GameTestAssertException("pool slot should be empty after routing");
            }
        });
    }

    // --- 2. Permanent-reject at the door: blacklisted + unvalued never enter the pool ---

    @GameTest(template = "empty5", timeoutTicks = 40)
    public static void refusesBlacklistedAndUnvalued(GameTestHelper helper) {
        SorterBlockEntity sorter = placeSorter(helper, new BlockPos(2, 1, 2));

        ItemStack sticks = new ItemStack(Items.STICK, 8);          // valued BUT winder-blacklisted
        ItemStack bedrock = new ItemStack(Items.BEDROCK, 4);       // no FU value
        ItemStack cobble = new ItemStack(Items.COBBLESTONE, 4);    // valued + windable

        if (sorter.pool().insertItem(0, sticks, false).getCount() != 8) {
            helper.fail("blacklisted sticks must be refused at the door");
            return;
        }
        if (sorter.pool().insertItem(1, bedrock, false).getCount() != 4) {
            helper.fail("unvalued bedrock must be refused at the door");
            return;
        }
        if (!sorter.pool().insertItem(2, cobble, false).isEmpty()) {
            helper.fail("valued, windable cobblestone must be accepted");
            return;
        }
        helper.succeed();
    }

    // --- 3. Round-robin: four items split 2/2 across two same-tier winders ---

    @GameTest(template = "empty5", timeoutTicks = 120)
    public static void roundRobinTwoWinders(GameTestHelper helper) {
        int tier = cobbleTier();
        SorterBlockEntity sorter = placeSorter(helper, new BlockPos(2, 1, 2));
        WinderBlockEntity a = placeWinder(helper, new BlockPos(1, 1, 2), blankSpool(tier));
        WinderBlockEntity b = placeWinder(helper, new BlockPos(3, 1, 2), blankSpool(tier));

        sorter.pool().setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 4));

        helper.succeedWhen(() -> {
            if (inputCount(a) != 2 || inputCount(b) != 2) {
                throw new GameTestAssertException("expected an even 2/2 split, got a=" + inputCount(a)
                        + " b=" + inputCount(b));
            }
            if (!sorter.pool().getStackInSlot(0).isEmpty()) {
                throw new GameTestAssertException("pool should be drained");
            }
        });
    }

    // --- 4. Stalled-winder skip: a full-spool winder is skipped, never routed into ---

    @GameTest(template = "empty5", timeoutTicks = 120)
    public static void skipsStalledWinderFullSpool(GameTestHelper helper) {
        int tier = cobbleTier();
        SorterBlockEntity sorter = placeSorter(helper, new BlockPos(2, 1, 2));
        // stalled: docked spool is full, so it cannot take the yield
        WinderBlockEntity stalled = placeWinder(helper, new BlockPos(1, 1, 2), fullSpool(tier));
        WinderBlockEntity working = placeWinder(helper, new BlockPos(3, 1, 2), blankSpool(tier));

        sorter.pool().setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 4));

        helper.succeedWhen(() -> {
            if (inputCount(working) != 4) {
                throw new GameTestAssertException("all 4 items should land in the working winder, got "
                        + inputCount(working));
            }
            if (inputCount(stalled) != 0) {
                throw new GameTestAssertException("the stalled (full-spool) winder must receive nothing, got "
                        + inputCount(stalled));
            }
        });
    }

    // --- 5. Cable-reach parity: a winder reachable only across cable routes identically ---

    @GameTest(template = "empty5", timeoutTicks = 200)
    public static void cableReachMatchesDirectTouch(GameTestHelper helper) {
        int tier = cobbleTier();
        SorterBlockEntity sorter = placeSorter(helper, new BlockPos(2, 1, 2));
        helper.setBlock(new BlockPos(3, 1, 2), ModBlocks.MC3DCABLE.get());
        // The winder is two blocks away, adjacent ONLY to the cable — never to the sorter.
        WinderBlockEntity winder = placeWinder(helper, new BlockPos(4, 1, 2), blankSpool(tier));

        sorter.pool().setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 1));

        helper.succeedWhen(() -> {
            if (inputCount(winder) != 1) {
                throw new GameTestAssertException("cable-reached winder should receive the item, got "
                        + inputCount(winder));
            }
        });
    }

    // --- 6. Transient hold: with no matching winder, the item is kept, never voided ---

    @GameTest(template = "empty5", timeoutTicks = 60)
    public static void heldWhenNoMatchingWinder(GameTestHelper helper) {
        SorterBlockEntity sorter = placeSorter(helper, new BlockPos(2, 1, 2));
        sorter.pool().setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 3));

        // No winder anywhere: after plenty of ticks the item is still in the pool (held, not lost).
        helper.runAfterDelay(40, () -> {
            ItemStack held = sorter.pool().getStackInSlot(0);
            if (held.getItem() != Items.COBBLESTONE || held.getCount() != 3) {
                helper.fail("unroutable items must be held intact, found " + held);
                return;
            }
            helper.succeed();
        });
    }

    // --- 7. Insert-only funnel: the external handler accepts inserts but never extracts ---

    // --- 7b. setStackInSlot is NOT a side door around the pool filter ---

    @GameTest(template = "empty5", timeoutTicks = 40)
    public static void setStackInSlotHonoursThePoolFilter(GameTestHelper helper) {
        SorterBlockEntity sorter = placeSorter(helper, new BlockPos(2, 1, 2));
        var external = sorter.getItemHandler(Direction.UP);
        if (!(external instanceof net.neoforged.neoforge.items.IItemHandlerModifiable m)) {
            helper.fail("the external handler must stay IItemHandlerModifiable for the 1.21.9+ bridge");
            return;
        }
        // Below 1.21.9 this exact object is what the item capability hands to other mods, so an
        // unfiltered setStackInSlot let anything past isItemValid and into the routing pool.
        m.setStackInSlot(0, new ItemStack(Items.STICK, 8));      // valued BUT winder-blacklisted
        if (!sorter.pool().getStackInSlot(0).isEmpty()) {
            helper.fail("blacklisted sticks must not enter the pool via setStackInSlot");
            return;
        }
        m.setStackInSlot(1, new ItemStack(Items.BEDROCK, 4));    // no FU value
        if (!sorter.pool().getStackInSlot(1).isEmpty()) {
            helper.fail("unvalued bedrock must not enter the pool via setStackInSlot");
            return;
        }
        m.setStackInSlot(2, new ItemStack(Items.COBBLESTONE, 4)); // valued + windable
        if (sorter.pool().getStackInSlot(2).getCount() != 4) {
            helper.fail("valid cobblestone must still be settable");
            return;
        }
        // Clearing a slot must keep working: an empty stack is not "invalid".
        m.setStackInSlot(2, ItemStack.EMPTY);
        if (!sorter.pool().getStackInSlot(2).isEmpty()) {
            helper.fail("setStackInSlot must still be able to clear a slot");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty5", timeoutTicks = 40)
    public static void rollbackRestoreBypassesTheFilter(GameTestHelper helper) {
        SorterBlockEntity sorter = placeSorter(helper, new BlockPos(2, 1, 2));
        var external = sorter.getItemHandler(Direction.UP);
        if (!(external instanceof com.pgmacdesign.mc3dprint.compat.SnapshotRestorable r)) {
            helper.fail("the external handler must implement SnapshotRestorable for transaction revert");
            return;
        }
        // The mirror of the test above: a transaction revert has to restore EXACTLY what it
        // snapshotted, including stacks the filter would refuse today (an item's FU value or
        // blacklist status can move between reloads). Filtering here would void player items.
        r.restoreSlot(0, new ItemStack(Items.STICK, 8));
        if (sorter.pool().getStackInSlot(0).getCount() != 8) {
            helper.fail("restoreSlot must write through unfiltered so a revert cannot void items");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty5", timeoutTicks = 40)
    public static void poolInsertOnlyExternally(GameTestHelper helper) {
        SorterBlockEntity sorter = placeSorter(helper, new BlockPos(2, 1, 2));
        var external = sorter.getItemHandler(Direction.UP);

        if (!external.insertItem(0, new ItemStack(Items.COBBLESTONE, 5), false).isEmpty()) {
            helper.fail("external face should accept a valid insert");
            return;
        }
        if (!external.extractItem(0, 64, false).isEmpty()) {
            helper.fail("external face must never extract — the pool is a one-way funnel");
            return;
        }
        if (sorter.pool().getStackInSlot(0).getCount() != 5) {
            helper.fail("the inserted items must remain in the pool");
            return;
        }
        helper.succeed();
    }

    // --- 9. Reject routing: un-windable items out to an adjacent non-MC3DPrint inventory ---
    //
    // The negative half of the door is already covered by refusesBlacklistedAndUnvalued above,
    // which places a bare sorter with no neighbours. It must keep passing untouched: with no
    // reject target, junk is refused exactly as it was before this feature existed.

    @GameTest(template = "empty5", timeoutTicks = 120)
    public static void acceptsJunkWhenRejectTargetAdjacent(GameTestHelper helper) {
        SorterBlockEntity sorter = placeSorter(helper, new BlockPos(2, 1, 2));
        Container chest = placeChest(helper, new BlockPos(3, 1, 2));

        // The door reads a face cache refreshed on tick, so let one tick populate it first.
        helper.runAfterDelay(2, () -> {
            if (!sorter.pool().insertItem(0, new ItemStack(Items.STICK, 8), false).isEmpty()) {
                helper.fail("blacklisted sticks must be accepted when an adjacent chest can take them");
            }
        });
        helper.succeedWhen(() -> {
            int inChest = countIn(chest, Items.STICK);
            if (inChest != 8) {
                throw new GameTestAssertException("chest should hold the 8 rejected sticks, got " + inChest);
            }
            if (!sorter.pool().getStackInSlot(0).isEmpty()) {
                throw new GameTestAssertException("the pool slot must be clear once the push lands");
            }
        });
    }

    @GameTest(template = "empty5", timeoutTicks = 120)
    public static void rejectPushConservesItemsWhenTargetNearlyFull(GameTestHelper helper) {
        SorterBlockEntity sorter = placeSorter(helper, new BlockPos(2, 1, 2));
        Container chest = placeChest(helper, new BlockPos(3, 1, 2));

        // Room for exactly 4 more sticks, and nowhere else for them to go.
        chest.setItem(0, new ItemStack(Items.STICK, 60));
        for (int i = 1; i < chest.getContainerSize(); i++) {
            chest.setItem(i, new ItemStack(Items.STONE, 64));
        }

        helper.runAfterDelay(2, () -> sorter.pool().insertItem(0, new ItemStack(Items.STICK, 8), false));
        helper.succeedWhen(() -> {
            int inChest = countIn(chest, Items.STICK);
            int inPool = sorter.pool().getStackInSlot(0).getCount();
            if (inChest != 64) {
                throw new GameTestAssertException("the chest stick slot should top out at 64, got " + inChest);
            }
            if (inPool != 4) {
                throw new GameTestAssertException("the 4 sticks that did not fit must stay pooled, got " + inPool);
            }
            if ((inChest - 60) + inPool != 8) {
                throw new GameTestAssertException("a partial push created or destroyed items");
            }
        });
    }

    @GameTest(template = "empty5", timeoutTicks = 80)
    public static void simulatedInsertWritesNothing(GameTestHelper helper) {
        SorterBlockEntity sorter = placeSorter(helper, new BlockPos(2, 1, 2));
        Container chest = placeChest(helper, new BlockPos(3, 1, 2));

        // Pipes call insertItem with simulate=true to plan a move. If the door pushed to the
        // chest instead of merely asking whether it would fit, that plan would duplicate items.
        helper.runAfterDelay(2, () -> {
            ItemStack sticks = new ItemStack(Items.STICK, 8);
            if (!sorter.getItemHandler(Direction.UP).insertItem(0, sticks, true).isEmpty()) {
                helper.fail("a simulated insert of acceptable junk should report full acceptance");
                return;
            }
            if (countIn(chest, Items.STICK) != 0) {
                helper.fail("simulate must not push anything into the chest");
                return;
            }
            if (!sorter.pool().getStackInSlot(0).isEmpty()) {
                helper.fail("simulate must not write into the pool");
                return;
            }
            if (sticks.getCount() != 8) {
                helper.fail("simulate must not mutate the caller's stack");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty5", timeoutTicks = 80)
    public static void winderIsNeverARejectTarget(GameTestHelper helper) {
        int tier = cobbleTier();
        SorterBlockEntity sorter = placeSorter(helper, new BlockPos(2, 1, 2));
        WinderBlockEntity winder = placeWinder(helper, new BlockPos(3, 1, 2), blankSpool(tier));

        // A winder's input slot accepts ANY item — only its spool slot is filtered — so without
        // the own-machine exclusion the sorter would push junk into the winder beside it and
        // jam it. That layout is the normal one, not a corner case.
        helper.runAfterDelay(2, () -> {
            if (sorter.pool().insertItem(0, new ItemStack(Items.STICK, 8), false).getCount() != 8) {
                helper.fail("a winder must not count as a reject target, so junk stays refused");
                return;
            }
            if (inputCount(winder) != 0) {
                helper.fail("junk must never be pushed into a winder's input slot");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty5", timeoutTicks = 120)
    public static void junkDoesNotStarveRouting(GameTestHelper helper) {
        int tier = cobbleTier();
        SorterBlockEntity sorter = placeSorter(helper, new BlockPos(2, 1, 2));
        WinderBlockEntity winder = placeWinder(helper, new BlockPos(1, 1, 2), blankSpool(tier));
        placeChest(helper, new BlockPos(3, 1, 2));

        // Eight slots of junk against one routable item: the drain is unbounded precisely so it
        // cannot spend the routing budget and leave the sorter's actual job undone.
        for (int slot = 0; slot < 8; slot++) {
            sorter.pool().setStackInSlot(slot, new ItemStack(Items.BEDROCK, 1));
        }
        sorter.pool().setStackInSlot(8, new ItemStack(Items.COBBLESTONE, 1));

        helper.succeedWhen(() -> {
            if (inputCount(winder) != 1) {
                throw new GameTestAssertException(
                        "the routable item must still reach its winder, got " + inputCount(winder));
            }
        });
    }

    @GameTest(template = "empty5", timeoutTicks = 120)
    public static void strandedPoolItemRecoversToRejectTarget(GameTestHelper helper) {
        SorterBlockEntity sorter = placeSorter(helper, new BlockPos(2, 1, 2));
        Container chest = placeChest(helper, new BlockPos(3, 1, 2));

        // Stands in for an item that entered the pool valid and later lost its FU value or was
        // blacklisted. routeOneItem skips such an item and the pool is not externally
        // extractable, so before reject routing only a player opening the GUI could clear it.
        // Written straight into the pool rather than by mutating the live blacklist, which is
        // shared state across concurrently running tests.
        sorter.pool().setStackInSlot(0, new ItemStack(Items.BEDROCK, 3));

        helper.succeedWhen(() -> {
            int inChest = countIn(chest, Items.BEDROCK);
            if (inChest != 3) {
                throw new GameTestAssertException(
                        "a stranded pool item must be pushed to the reject target, chest has " + inChest);
            }
            if (!sorter.pool().getStackInSlot(0).isEmpty()) {
                throw new GameTestAssertException("the pool slot must be cleared once the item is pushed out");
            }
        });
    }

    // --- 8. The cable_connectable tag loads, and the cable renders an arm toward the sorter ---

    @GameTest(template = "empty5", timeoutTicks = 40)
    public static void cableAttachesToSorter(GameTestHelper helper) {
        // Guards the tag-directory trap: the tag lives in data/mc3dprint/tags/block/ (SINGULAR).
        // The plural "blocks/" form silently fails to load, which would leave this tag empty
        // and the cable visibly refusing to attach — reading as a bug to the player.
        if (!ModBlocks.FILAMENT_ITEM_SORTER.get().defaultBlockState().is(ModBlockTags.CABLE_CONNECTABLE)) {
            helper.fail("sorter is not in mc3dprint:cable_connectable — check that"
                    + " data/mc3dprint/tags/block/ (singular) actually loaded");
            return;
        }
        // Place the cable first, then the sorter, so the sorter's placement drives the
        // cable's updateShape. The sorter sits at -X of the cable, i.e. its WEST arm.
        BlockPos cablePos = new BlockPos(3, 1, 2);
        helper.setBlock(cablePos, ModBlocks.MC3DCABLE.get());
        helper.setBlock(new BlockPos(2, 1, 2), ModBlocks.FILAMENT_ITEM_SORTER.get());
        if (!helper.getBlockState(cablePos).getValue(MC3DCableBlock.WEST)) {
            helper.fail("cable should render an arm toward the adjacent sorter");
            return;
        }
        helper.succeed();
    }
}
