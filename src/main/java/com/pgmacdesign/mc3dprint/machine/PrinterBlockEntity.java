package com.pgmacdesign.mc3dprint.machine;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.blueprint.Blueprint;
import com.pgmacdesign.mc3dprint.blueprint.BlueprintBlockState;
import com.pgmacdesign.mc3dprint.blueprint.BlueprintFileStore;
import com.pgmacdesign.mc3dprint.blueprint.PrintOrientation;
import com.pgmacdesign.mc3dprint.config.MC3DPrintConfig;
import com.pgmacdesign.mc3dprint.fu.FuValue;
import com.pgmacdesign.mc3dprint.fu.FuValueRegistry;
import com.pgmacdesign.mc3dprint.fu.SpoolItem;
import com.pgmacdesign.mc3dprint.item.BlueprintDiscItem;
import com.pgmacdesign.mc3dprint.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.common.world.ForgeChunkManager;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.RangedWrapper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tier 1 printer with the Smart Print Slot:
 * - Drop any item -> Item Mode: drains RF, emits copies (M2; FU cost lands in M5)
 * - Load a written Blueprint Disc -> Blueprint Mode: prints the structure
 *   bottom-up, one block per N ticks, RF per block
 *
 * Blueprint Mode invariants (design doc):
 * - print area must be pre-cleared; obstruction pauses, never overwrites
 * - power loss pauses, progress is never lost, jobs persist through restarts
 * - the print volume is chunk-loaded while a job runs
 * - overlapping print zones refuse to start (PrintZoneManager)
 * - the disc is returned to the output slot on completion
 */
public class PrinterBlockEntity extends BlockEntity implements MenuProvider {
    public static final int SLOT_TEMPLATE = 0;
    public static final int SLOT_OUTPUT = 1;
    public static final int SLOT_COUNT = 2;

    public static final int DATA_PROGRESS = 0;
    public static final int DATA_MAX_PROGRESS = 1;
    public static final int DATA_ENERGY = 2;
    public static final int DATA_MAX_ENERGY = 3;
    public static final int DATA_STATE = 4;
    public static final int DATA_FU = 5;
    public static final int DATA_FU_CAP = 6;
    public static final int DATA_TEMPLATE_COST = 7;
    public static final int DATA_COUNT = 8;

    public enum State {
        IDLE, PRINTING, PAUSED_NO_POWER, PAUSED_OUTPUT_FULL, PAUSED_OBSTRUCTED, ZONE_CONFLICT,
        PAUSED_NO_FILAMENT, NOT_PRINTABLE, AREA_TOO_SMALL;

        public static State byOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : IDLE;
        }
    }

    private final ItemStackHandler inventory = new ItemStackHandler(SLOT_COUNT) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    private final MachineTier tier;
    private final ItemStackHandler spools;
    private final MachineEnergyStorage energy;

    private final LazyOptional<MachineEnergyStorage> energyCap = LazyOptional.of(this::energyStorage);
    private final LazyOptional<IItemHandler> inputCap =
            LazyOptional.of(() -> new RangedWrapper(inventory, SLOT_TEMPLATE, SLOT_TEMPLATE + 1));
    private final LazyOptional<IItemHandler> outputCap =
            LazyOptional.of(() -> new RangedWrapper(inventory, SLOT_OUTPUT, SLOT_OUTPUT + 1) {
                @Override
                @Nonnull
                public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
                    return stack; // extract-only face
                }
            });
    private final LazyOptional<IItemHandler> allCap = LazyOptional.of(() -> inventory);

    // Item Mode
    private int itemProgress;

    // Blueprint Mode
    @Nullable
    private PrintJob activeJob;
    @Nullable
    private transient Blueprint cachedBlueprint;          // lazily loaded for activeJob
    @Nullable
    private transient List<PlacementEntry> placementOrder; // derived from cachedBlueprint
    private int placementCooldown;
    private int retryCooldown;
    private final List<CompoundTag> history = new ArrayList<>();

    private State state = State.IDLE;
    private boolean collapsing;

    private record PlacementEntry(BlockPos local, int paletteIndex) {}

    /** Set while a formed multiblock is being collapsed to an item — suppresses content drops. */
    public void markCollapsing() {
        this.collapsing = true;
    }

    public boolean isCollapsing() {
        return collapsing;
    }

    public PrinterBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.PRINTER.get(), pos, blockState);
        this.tier = blockState.getBlock() instanceof PrinterBlock printerBlock
                ? printerBlock.tier() : MachineTier.T1;
        this.spools = new ItemStackHandler(tier.spoolSlots()) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
            }

            @Override
            public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
                return stack.getItem() instanceof SpoolItem;
            }
        };
        this.energy = new MachineEnergyStorage(
                MC3DPrintConfig.energyBuffer(tier),
                MC3DPrintConfig.maxEnergyReceive(tier),
                this::setChanged);
    }

    public MachineTier tier() {
        return tier;
    }

    private MachineEnergyStorage energyStorage() {
        return energy;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState blockState, PrinterBlockEntity printer) {
        printer.tick();
    }

    private void tick() {
        // multiblock controllers only operate while formed
        if (getBlockState().hasProperty(com.pgmacdesign.mc3dprint.machine.multiblock.ControllerBlock.FORMED)
                && !getBlockState().getValue(com.pgmacdesign.mc3dprint.machine.multiblock.ControllerBlock.FORMED)) {
            if (activeJob != null) {
                cancelActiveJob();
            }
            state = State.IDLE;
            return;
        }
        State previous = state;
        ItemStack template = inventory.getStackInSlot(SLOT_TEMPLATE);

        if (isLoadedDisc(template)) {
            tickBlueprintMode(template);
        } else {
            if (activeJob != null) {
                cancelActiveJob(); // disc removed mid-print
            }
            tickItemMode(template);
        }

        if (state != previous) {
            setChanged();
        }
    }

    // --- Item Mode ---

    private void tickItemMode(ItemStack template) {
        if (template.isEmpty()) {
            state = State.IDLE;
            itemProgress = 0;
            return;
        }
        int fuCost = itemFuCost(template);
        if (fuCost < 0) {
            state = State.NOT_PRINTABLE;
            itemProgress = 0;
        } else if (!canEmitCopy(template)) {
            state = State.PAUSED_OUTPUT_FULL;
        } else if (totalFu() < fuCost) {
            state = State.PAUSED_NO_FILAMENT;
        } else if (!energy.hasAtLeast(MC3DPrintConfig.itemRfPerTick(tier))) {
            state = State.PAUSED_NO_POWER;
        } else {
            state = State.PRINTING;
            energy.consume(MC3DPrintConfig.itemRfPerTick(tier));
            itemProgress++;
            if (itemProgress >= MC3DPrintConfig.itemPrintTicks(tier)) {
                drainFu(fuCost);
                emitCopy(template);
                itemProgress = 0;
            }
        }
    }

    // --- Filament ---

    /**
     * FU cost to print this item after tier efficiency, or -1 if it has no FU
     * value or its material tier exceeds this machine's tier (not printable).
     */
    public int itemFuCost(ItemStack template) {
        return FuValueRegistry.valueOf(template)
                .filter(v -> v.tier() <= tier.number())
                .map(v -> applyEfficiency(v.fu()))
                .orElse(-1);
    }

    private int blockFuCost(BlockState state) {
        Item item = state.getBlock().asItem();
        int base = item == Items.AIR
                ? MC3DPrintConfig.UNKNOWN_BLOCK_FU.get()
                : FuValueRegistry.valueOf(new ItemStack(item)).map(FuValue::fu)
                        .orElse(MC3DPrintConfig.UNKNOWN_BLOCK_FU.get());
        return applyEfficiency(base);
    }

    private int applyEfficiency(int baseFu) {
        return (int) Math.ceil(baseFu / MC3DPrintConfig.efficiency(tier));
    }

    public int totalFu() {
        int total = 0;
        for (int i = 0; i < spools.getSlots(); i++) {
            total += SpoolItem.getFu(spools.getStackInSlot(i));
        }
        return total;
    }

    public int fuCapacity() {
        int total = 0;
        for (int i = 0; i < spools.getSlots(); i++) {
            if (spools.getStackInSlot(i).getItem() instanceof SpoolItem spool) {
                total += spool.capacity();
            }
        }
        return total;
    }

    /** Drains across attached spools in order (auto-switch per design). */
    private void drainFu(int amount) {
        int remaining = amount;
        for (int i = 0; i < spools.getSlots() && remaining > 0; i++) {
            ItemStack spool = spools.getStackInSlot(i);
            remaining -= SpoolItem.drain(spool, remaining);
            spools.setStackInSlot(i, spool);
        }
    }

    /** Attaches a spool from {@code held} (Shift+Right Click on a side). True if accepted. */
    public boolean attachSpool(ItemStack held) {
        for (int i = 0; i < spools.getSlots(); i++) {
            if (spools.getStackInSlot(i).isEmpty()) {
                spools.setStackInSlot(i, held.split(1));
                return true;
            }
        }
        return false;
    }

    /** Removes and returns the last attached spool, or empty. */
    public ItemStack detachSpool() {
        for (int i = spools.getSlots() - 1; i >= 0; i--) {
            ItemStack spool = spools.getStackInSlot(i);
            if (!spool.isEmpty()) {
                spools.setStackInSlot(i, ItemStack.EMPTY);
                return spool;
            }
        }
        return ItemStack.EMPTY;
    }

    public ItemStackHandler spoolInventory() {
        return spools;
    }

    private boolean canEmitCopy(ItemStack template) {
        ItemStack output = inventory.getStackInSlot(SLOT_OUTPUT);
        if (output.isEmpty()) {
            return true;
        }
        return ItemStack.isSameItemSameTags(output, template) && output.getCount() < output.getMaxStackSize();
    }

    private void emitCopy(ItemStack template) {
        ItemStack output = inventory.getStackInSlot(SLOT_OUTPUT);
        if (output.isEmpty()) {
            inventory.setStackInSlot(SLOT_OUTPUT, template.copyWithCount(1));
        } else {
            output.grow(1);
            inventory.setStackInSlot(SLOT_OUTPUT, output);
        }
    }

    // --- Blueprint Mode ---

    private static boolean isLoadedDisc(ItemStack stack) {
        return stack.getItem() instanceof BlueprintDiscItem && BlueprintDiscItem.hasBlueprint(stack);
    }

    private void tickBlueprintMode(ItemStack disc) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        UUID discBlueprint = BlueprintDiscItem.getBlueprintId(disc).orElse(null);
        if (discBlueprint == null) {
            return;
        }

        if (activeJob != null && !activeJob.blueprintId().equals(discBlueprint)) {
            cancelActiveJob(); // disc swapped mid-print
        }

        if (activeJob == null) {
            if (retryCooldown > 0) {
                retryCooldown--;
                return;
            }
            retryCooldown = 20;
            tryStartJob(serverLevel, disc, discBlueprint);
            return;
        }

        if (activeJob.isComplete()) {
            tryFinishJob(serverLevel, disc);
            return;
        }

        if (!ensureBlueprintLoaded(serverLevel)) {
            return; // blueprint file missing — stay paused (state set inside)
        }

        placementCooldown++;
        if (placementCooldown < MC3DPrintConfig.ticksPerBlock(tier)) {
            if (state == State.IDLE) {
                state = State.PRINTING;
            }
            return;
        }

        int rfPerBlock = MC3DPrintConfig.rfPerBlock(tier);
        if (!energy.hasAtLeast(rfPerBlock)) {
            state = State.PAUSED_NO_POWER;
            return;
        }

        PlacementEntry entry = placementOrder.get(activeJob.placed());
        Blueprint blueprint = cachedBlueprint;
        BlockPos worldPos = worldPosFor(entry.local(), blueprint);

        if (!serverLevel.getBlockState(worldPos).canBeReplaced()) {
            state = State.PAUSED_OBSTRUCTED;
            return;
        }

        Optional<BlockState> resolved = blueprint.palette().get(entry.paletteIndex()).resolve();
        // unknown block (e.g. blueprint from a modded world) — skip it, never stall the job
        if (resolved.isPresent()) {
            int fuCost = blockFuCost(resolved.get());
            if (totalFu() < fuCost) {
                state = State.PAUSED_NO_FILAMENT;
                return;
            }
            BlockState placedState = resolved.get()
                    .mirror(activeJob.orientation().mirror())
                    .rotate(activeJob.orientation().rotation());
            serverLevel.setBlock(worldPos, placedState, Block.UPDATE_ALL);

            CompoundTag beData = blueprint.blockEntities().get(entry.local());
            if (beData != null) {
                BlockEntity placedBe = serverLevel.getBlockEntity(worldPos);
                if (placedBe != null) {
                    placedBe.load(beData);
                    placedBe.setChanged();
                }
            }
            energy.consume(rfPerBlock);
            drainFu(fuCost);
        }

        activeJob.setPlaced(activeJob.placed() + 1);
        placementCooldown = 0;
        state = State.PRINTING;
        setChanged();

        if (activeJob.isComplete()) {
            tryFinishJob(serverLevel, disc);
        }
    }

    private void tryStartJob(ServerLevel serverLevel, ItemStack disc, UUID blueprintId) {
        BlueprintFileStore store = BlueprintFileStore.forServer(serverLevel.getServer());
        Optional<Blueprint> loaded = store.load(blueprintId);
        if (loaded.isEmpty()) {
            state = State.IDLE; // unknown disc; nothing to do
            return;
        }
        Blueprint blueprint = loaded.get();

        // tier gating: structure printing needs a print area, the footprint
        // must fit, and every material must be within this machine's tier
        if (tier.maxFootprint() == 0) {
            state = State.NOT_PRINTABLE;
            return;
        }
        PrintOrientation orientation = PrintOrientation.NONE;
        BlockPos size = orientation.transformedSize(blueprint.sizeX(), blueprint.sizeY(), blueprint.sizeZ());
        if (size.getX() > tier.maxFootprint() || size.getZ() > tier.maxFootprint()) {
            state = State.AREA_TOO_SMALL;
            return;
        }
        for (BlueprintBlockState paletteState : blueprint.palette()) {
            Item paletteItem = paletteState.resolve().map(s -> s.getBlock().asItem()).orElse(Items.AIR);
            if (paletteItem != Items.AIR && FuValueRegistry.valueOf(new ItemStack(paletteItem))
                    .map(v -> v.tier() > tier.number()).orElse(false)) {
                state = State.NOT_PRINTABLE;
                return;
            }
        }

        // min corner: centered horizontally over the printer, one block up
        BlockPos origin = worldPosition.offset(-(size.getX() / 2), 1, -(size.getZ() / 2));
        BoundingBox box = BoundingBox.fromCorners(origin,
                origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1));

        if (!PrintZoneManager.claim(serverLevel, worldPosition, box)) {
            state = State.ZONE_CONFLICT;
            return;
        }
        if (!isAreaClear(serverLevel, blueprint, orientation, origin)) {
            PrintZoneManager.release(serverLevel, worldPosition);
            state = State.PAUSED_OBSTRUCTED;
            return;
        }

        activeJob = new PrintJob(blueprintId, blueprint.name(), origin, orientation, blueprint.blockCount());
        cachedBlueprint = blueprint;
        placementOrder = buildPlacementOrder(blueprint);
        placementCooldown = 0;
        forceChunks(serverLevel, box, true);
        state = State.PRINTING;
        setChanged();
    }

    private boolean isAreaClear(ServerLevel serverLevel, Blueprint blueprint,
                                PrintOrientation orientation, BlockPos origin) {
        boolean[] clear = {true};
        blueprint.forEachBlock((local, paletteIndex) -> {
            if (clear[0]) {
                BlockPos world = origin.offset(orientation.transform(local,
                        blueprint.sizeX(), blueprint.sizeY(), blueprint.sizeZ()));
                if (!serverLevel.getBlockState(world).canBeReplaced()) {
                    clear[0] = false;
                }
            }
        });
        return clear[0];
    }

    private void tryFinishJob(ServerLevel serverLevel, ItemStack disc) {
        if (!inventory.getStackInSlot(SLOT_OUTPUT).isEmpty()) {
            state = State.PAUSED_OUTPUT_FULL; // disc waits to eject; job already complete
            return;
        }
        recordHistory(activeJob);
        releaseJobResources(serverLevel);
        inventory.setStackInSlot(SLOT_OUTPUT, disc.copy());
        inventory.setStackInSlot(SLOT_TEMPLATE, ItemStack.EMPTY);
        activeJob = null;
        cachedBlueprint = null;
        placementOrder = null;
        state = State.IDLE;
        setChanged();
    }

    public void cancelActiveJob() {
        if (activeJob != null && level instanceof ServerLevel serverLevel) {
            releaseJobResources(serverLevel);
        }
        activeJob = null;
        cachedBlueprint = null;
        placementOrder = null;
        placementCooldown = 0;
        setChanged();
    }

    private void releaseJobResources(ServerLevel serverLevel) {
        PrintZoneManager.release(serverLevel, worldPosition);
        if (activeJob != null && cachedBlueprint != null) {
            forceChunks(serverLevel, jobBox(), false);
        }
    }

    private BoundingBox jobBox() {
        Blueprint bp = cachedBlueprint;
        PrintOrientation orientation = activeJob.orientation();
        BlockPos size = orientation.transformedSize(bp.sizeX(), bp.sizeY(), bp.sizeZ());
        return BoundingBox.fromCorners(activeJob.origin(),
                activeJob.origin().offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1));
    }

    private void forceChunks(ServerLevel serverLevel, BoundingBox box, boolean add) {
        int minChunkX = box.minX() >> 4;
        int maxChunkX = box.maxX() >> 4;
        int minChunkZ = box.minZ() >> 4;
        int maxChunkZ = box.maxZ() >> 4;
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                ForgeChunkManager.forceChunk(serverLevel, MC3DPrint.MOD_ID, worldPosition, cx, cz, add, false);
            }
        }
    }

    private boolean ensureBlueprintLoaded(ServerLevel serverLevel) {
        if (cachedBlueprint != null && placementOrder != null) {
            return true;
        }
        Optional<Blueprint> loaded = BlueprintFileStore.forServer(serverLevel.getServer())
                .load(activeJob.blueprintId());
        if (loaded.isEmpty()) {
            state = State.PAUSED_OBSTRUCTED; // blueprint file vanished; hold the job
            return false;
        }
        cachedBlueprint = loaded.get();
        placementOrder = buildPlacementOrder(cachedBlueprint);
        return true;
    }

    private static List<PlacementEntry> buildPlacementOrder(Blueprint blueprint) {
        List<PlacementEntry> order = new ArrayList<>(blueprint.blockCount());
        blueprint.forEachBlock((local, paletteIndex) ->
                order.add(new PlacementEntry(local.immutable(), paletteIndex)));
        return order;
    }

    private BlockPos worldPosFor(BlockPos local, Blueprint blueprint) {
        return activeJob.origin().offset(activeJob.orientation()
                .transform(local, blueprint.sizeX(), blueprint.sizeY(), blueprint.sizeZ()));
    }

    private void recordHistory(PrintJob job) {
        CompoundTag entry = new CompoundTag();
        entry.putString("Name", job.blueprintName());
        entry.putInt("Blocks", job.totalBlocks());
        entry.putLong("Time", level != null ? level.getGameTime() : 0);
        history.add(0, entry);
        int max = MC3DPrintConfig.PRINT_HISTORY_SIZE.get();
        while (history.size() > max) {
            history.remove(history.size() - 1);
        }
    }

    public List<CompoundTag> history() {
        return history;
    }

    @Nullable
    public PrintJob activeJob() {
        return activeJob;
    }

    public State state() {
        return state;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        // re-claim zone + chunk tickets for a job restored from disk
        if (activeJob != null && level instanceof ServerLevel serverLevel && ensureBlueprintLoaded(serverLevel)) {
            PrintZoneManager.claim(serverLevel, worldPosition, jobBox());
            forceChunks(serverLevel, jobBox(), true);
        }
    }

    public int maxProgress() {
        return MC3DPrintConfig.itemPrintTicks(tier);
    }

    public ItemStackHandler inventory() {
        return inventory;
    }

    public ContainerData containerData() {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case DATA_PROGRESS -> activeJob != null ? activeJob.placed() : itemProgress;
                    case DATA_MAX_PROGRESS -> activeJob != null ? Math.max(1, activeJob.totalBlocks()) : maxProgress();
                    case DATA_ENERGY -> energy.getEnergyStored();
                    case DATA_MAX_ENERGY -> energy.getMaxEnergyStored();
                    case DATA_STATE -> state.ordinal();
                    case DATA_FU -> totalFu();
                    case DATA_FU_CAP -> fuCapacity();
                    case DATA_TEMPLATE_COST -> {
                        ItemStack template = inventory.getStackInSlot(SLOT_TEMPLATE);
                        yield activeJob == null && !template.isEmpty() ? Math.max(0, itemFuCost(template)) : 0;
                    }
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                if (index == DATA_PROGRESS) {
                    itemProgress = value;
                }
            }

            @Override
            public int getCount() {
                return DATA_COUNT;
            }
        };
    }

    // --- MenuProvider ---

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int windowId, Inventory playerInventory, Player player) {
        return new PrinterMenu(windowId, playerInventory, this);
    }

    // --- Capabilities ---

    @Override
    @Nonnull
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ENERGY) {
            return energyCap.cast();
        }
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            if (side == null) {
                return allCap.cast();
            }
            // Top inserts the template; every other face is extract-only output.
            // Sides will be reclaimed for filament spools in M5.
            return side == Direction.UP ? inputCap.cast() : outputCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        energyCap.invalidate();
        inputCap.invalidate();
        outputCap.invalidate();
        allCap.invalidate();
    }

    // --- Persistence ---

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Inventory", inventory.serializeNBT());
        tag.put("Spools", spools.serializeNBT());
        tag.putInt("Energy", energy.getEnergyStored());
        tag.putInt("Progress", itemProgress);
        tag.putInt("State", state.ordinal());
        if (activeJob != null) {
            tag.put("ActiveJob", activeJob.save());
        }
        ListTag historyTag = new ListTag();
        history.forEach(historyTag::add);
        tag.put("History", historyTag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        inventory.deserializeNBT(tag.getCompound("Inventory"));
        spools.deserializeNBT(tag.getCompound("Spools"));
        energy.setStored(tag.getInt("Energy"));
        itemProgress = tag.getInt("Progress");
        state = State.byOrdinal(tag.getInt("State"));
        activeJob = tag.contains("ActiveJob", Tag.TAG_COMPOUND) ? PrintJob.load(tag.getCompound("ActiveJob")) : null;
        history.clear();
        for (Tag t : tag.getList("History", Tag.TAG_COMPOUND)) {
            history.add((CompoundTag) t);
        }
    }
}
