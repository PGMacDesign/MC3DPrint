package com.pgmacdesign.mc3dprint.machine;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.blueprint.Blueprint;
import com.pgmacdesign.mc3dprint.blueprint.BlueprintBlockState;
import com.pgmacdesign.mc3dprint.blueprint.BlueprintFileStore;
import com.pgmacdesign.mc3dprint.blueprint.PrintOrientation;
import com.pgmacdesign.mc3dprint.config.MC3DPrintConfig;
import com.pgmacdesign.mc3dprint.fu.FuConversion;
import com.pgmacdesign.mc3dprint.fu.FuValue;
import com.pgmacdesign.mc3dprint.fu.FuValueRegistry;
import com.pgmacdesign.mc3dprint.fu.SpoolItem;
import com.pgmacdesign.mc3dprint.item.BlueprintDiscItem;
import com.pgmacdesign.mc3dprint.machine.upgrade.UpgradeItem;
import com.pgmacdesign.mc3dprint.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
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

    // Logical container-data indices; synced split into shorts (see SplitContainerData)
    public static final int DATA_PROGRESS = 0;
    public static final int DATA_MAX_PROGRESS = 1;
    public static final int DATA_ENERGY = 2;
    public static final int DATA_MAX_ENERGY = 3;
    public static final int DATA_STATE = 4;
    public static final int DATA_FU = 5;
    public static final int DATA_FU_CAP = 6;
    public static final int DATA_TEMPLATE_COST = 7;
    public static final int DATA_SPOOLS_USED = 8;
    public static final int DATA_SPOOL_SLOTS = 9;
    public static final int DATA_AUTO_START = 10;
    public static final int DATA_OFFSET_X = 11;
    public static final int DATA_OFFSET_Y = 12;
    public static final int DATA_OFFSET_Z = 13;
    public static final int DATA_PREVIEW = 14;
    public static final int DATA_COUNT = 15;

    /** Build offsets are clamped to this range on each axis. */
    public static final int MAX_OFFSET = 64;

    public enum State {
        IDLE, PRINTING, PAUSED_NO_POWER, PAUSED_OUTPUT_FULL, PAUSED_OBSTRUCTED, ZONE_CONFLICT,
        PAUSED_NO_FILAMENT, NOT_PRINTABLE, AREA_TOO_SMALL, READY;

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
    private final ItemStackHandler upgrades;
    private final MachineEnergyStorage energy;

    // multiplicative upgrade factors per module (design: never additive);
    // config-exposed for pack makers per the design doc

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
    private static final Logger LOGGER = LogUtils.getLogger();
    // Human-readable explanation for the most recent NOT_PRINTABLE; logged once
    // on the transition into that state so the player can see WHY in the log.
    private String notPrintableReason = "";

    // blueprint jobs start on a trigger (GUI Start button / redstone rising
    // edge) unless auto-start is enabled; build origin is offset-adjustable
    private boolean autoStart;
    private boolean startRequested;
    private boolean lastRedstoneSignal;
    /** Player who placed the machine — receives print advancements. */
    @Nullable
    private UUID owner;

    // hologram preview: ghost-renders the loaded disc at the build position
    private boolean previewEnabled;
    @Nullable
    private Blueprint previewBlueprint;
    @Nullable
    private UUID previewBlueprintId;
    private int offsetX;
    private int offsetY;
    private int offsetZ;
    private boolean collapsing;
    @Nullable
    private BlockPos lastPlacedPos; // synced; drives the print head + beam render

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
        this.upgrades = new ItemStackHandler(tier.upgradeSlots()) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
                refreshEnergyCapacity();
            }

            @Override
            public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
                return stack.getItem() instanceof UpgradeItem;
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
            // Explain a fresh NOT_PRINTABLE once (not every tick) so the player can
            // diagnose it from the server log instead of guessing.
            if (state == State.NOT_PRINTABLE) {
                LOGGER.info("[MC3DP] Tier {} {} at {} won't print: {}",
                        tier.number(),
                        isLoadedDisc(template) ? "blueprint" : "item",
                        worldPosition,
                        notPrintableReason.isEmpty() ? "no reason recorded" : notPrintableReason);
            }
        }
    }

    /** Registry id of an item/stack for diagnostic logs, e.g. "minecraft:diamond". */
    private static String idOf(ItemStack stack) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    // --- Item Mode ---

    private void tickItemMode(ItemStack template) {
        if (template.isEmpty()) {
            state = State.IDLE;
            itemProgress = 0;
            return;
        }
        int fuCost = itemFuCost(template);
        int costTier = itemFuTier(template);
        if (fuCost < 0) {
            state = State.NOT_PRINTABLE;
            itemProgress = 0;
            Optional<FuValue> value = FuValueRegistry.valueOf(template);
            if (value.isEmpty()) {
                notPrintableReason = String.format(
                        "%s has no FU value (unpriced/unknown item — register one via the API/config, "
                                + "or set unknownBlocksPrintable=true)", idOf(template));
            } else {
                notPrintableReason = String.format(
                        "%s is Tier %d, which exceeds this machine's Tier %d",
                        idOf(template), value.get().tier(), tier.number());
            }
        } else if (!canEmitCopy(template)) {
            state = State.PAUSED_OUTPUT_FULL;
        } else if (effectiveFu(costTier) < fuCost) {
            state = State.PAUSED_NO_FILAMENT;
        } else if (!energy.hasAtLeast(rfAdjusted(MC3DPrintConfig.itemRfPerTick(tier)))) {
            state = State.PAUSED_NO_POWER;
        } else {
            state = State.PRINTING;
            energy.consume(rfAdjusted(MC3DPrintConfig.itemRfPerTick(tier)));
            itemProgress++;
            if (itemProgress >= maxProgress()) {
                drainFu(fuCost, costTier);
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

    /** The tier the item's FU cost is denominated in (1 if it has no value). */
    public int itemFuTier(ItemStack template) {
        return FuValueRegistry.valueOf(template).map(FuValue::tier).orElse(1);
    }

    private int blockFuCost(BlockState state) {
        Optional<FuValue> value = blockFuValue(state);
        // Unknown blocks (permissive mode only — strict mode refuses them up front
        // in tryStartJob) cost unknownBlockFu, denominated at the machine's tier so
        // a low-tier machine can't print them cheaply.
        return applyEfficiency(value.map(FuValue::fu).orElse(MC3DPrintConfig.UNKNOWN_BLOCK_FU.get()));
    }

    private int blockFuTier(BlockState state) {
        // No value -> charge at this machine's own tier (never cheap T1 default).
        return blockFuValue(state).map(FuValue::tier).orElse(tier.number());
    }

    private Optional<FuValue> blockFuValue(BlockState state) {
        Item item = state.getBlock().asItem();
        return item == Items.AIR ? Optional.empty() : FuValueRegistry.valueOf(new ItemStack(item));
    }

    private int applyEfficiency(int baseFu) {
        double cost = baseFu / MC3DPrintConfig.efficiency(tier)
                * Math.pow(MC3DPrintConfig.UPGRADE_EFFICIENCY_FACTOR.get(), upgradeCount(UpgradeItem.Type.EFFICIENCY));
        // epsilon guard: 50/0.75*0.9 is 60.000000000000014 in doubles — don't ceil that to 61
        return (int) Math.ceil(cost - 1.0e-7);
    }

    /** Raw sum of stored spool FU, ignoring tier denominations. Display/debug only. */
    public int totalFu() {
        int total = 0;
        for (int i = 0; i < spools.getSlots(); i++) {
            total += SpoolItem.getFu(spools.getStackInSlot(i));
        }
        return total;
    }

    /**
     * FU available toward a cost denominated at {@code costTier}. Down-only,
     * hard rule: spools at or above the cost tier contribute at the exchange
     * rate (1 tier-N FU = ratio tier-(N-1) FU); spools below it contribute
     * nothing — FU never converts up.
     */
    public int effectiveFu(int costTier) {
        int ratio = FuConversion.ratio();
        long base = 0;
        for (int i = 0; i < spools.getSlots(); i++) {
            ItemStack spool = spools.getStackInSlot(i);
            if (spool.getItem() instanceof SpoolItem spoolItem
                    && FuConversion.canCover(spoolItem.tier(), costTier)) {
                base += FuConversion.toBase(SpoolItem.getFu(spool), spoolItem.tier(), ratio);
            }
        }
        return FuConversion.clampToInt(FuConversion.fromBase(base, costTier, ratio));
    }

    /** Capacity counterpart of {@link #effectiveFu} for the GUI gauge. */
    public int effectiveFuCapacity(int costTier) {
        int ratio = FuConversion.ratio();
        long base = 0;
        for (int i = 0; i < spools.getSlots(); i++) {
            if (spools.getStackInSlot(i).getItem() instanceof SpoolItem spool
                    && FuConversion.canCover(spool.tier(), costTier)) {
                base += FuConversion.toBase(spool.capacity(), spool.tier(), ratio);
            }
        }
        return FuConversion.clampToInt(FuConversion.fromBase(base, costTier, ratio));
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

    /**
     * Drains a cost denominated at {@code costTier} across attached spools in
     * order, converting down at the exchange rate. Spools below the cost tier
     * are skipped (FU never converts up). When a spool's unit is worth more
     * than the remaining cost, a whole unit is consumed (ceil) — at most one
     * high-tier unit of rounding per print.
     */
    private void drainFu(int amount, int costTier) {
        int ratio = FuConversion.ratio();
        long remainingBase = FuConversion.toBase(amount, costTier, ratio);
        for (int i = 0; i < spools.getSlots() && remainingBase > 0; i++) {
            ItemStack spool = spools.getStackInSlot(i);
            if (!(spool.getItem() instanceof SpoolItem spoolItem)
                    || !FuConversion.canCover(spoolItem.tier(), costTier)) {
                continue;
            }
            long stored = SpoolItem.getFu(spool);
            long storedBase = FuConversion.toBase(stored, spoolItem.tier(), ratio);
            long drainUnits = storedBase <= remainingBase
                    ? stored
                    : FuConversion.fromBaseCeil(remainingBase, spoolItem.tier(), ratio);
            int drained = SpoolItem.drain(spool, FuConversion.clampToInt(drainUnits));
            remainingBase -= FuConversion.toBase(drained, spoolItem.tier(), ratio);
            spools.setStackInSlot(i, spool);
        }
        syncToClients(); // the exterior spool render shrinks with the fill level
    }

    /** Spool contents changed externally (e.g. Filament Converter top-off). */
    public void notifySpoolsChanged() {
        setChanged();
        syncToClients();
    }

    /** Attaches a spool from {@code held} (Shift+Right Click on a side). True if accepted. */
    public boolean attachSpool(ItemStack held) {
        for (int i = 0; i < spools.getSlots(); i++) {
            if (spools.getStackInSlot(i).isEmpty()) {
                spools.setStackInSlot(i, held.split(1));
                syncToClients();
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
                syncToClients();
                return spool;
            }
        }
        return ItemStack.EMPTY;
    }

    public ItemStackHandler spoolInventory() {
        return spools;
    }

    // --- Upgrades ---

    public ItemStackHandler upgradeInventory() {
        return upgrades;
    }

    public int upgradeCount(UpgradeItem.Type type) {
        int count = 0;
        for (int i = 0; i < upgrades.getSlots(); i++) {
            if (upgrades.getStackInSlot(i).getItem() instanceof UpgradeItem upgrade
                    && upgrade.type() == type) {
                count++;
            }
        }
        return count;
    }

    /** Installs one module from {@code held} into the first free slot. */
    public boolean installUpgrade(ItemStack held) {
        for (int i = 0; i < upgrades.getSlots(); i++) {
            if (upgrades.getStackInSlot(i).isEmpty()) {
                upgrades.setStackInSlot(i, held.split(1));
                return true;
            }
        }
        return false;
    }

    private void refreshEnergyCapacity() {
        energy.setCapacity((int) Math.min(Integer.MAX_VALUE,
                Math.round(MC3DPrintConfig.energyBuffer(tier)
                        * Math.pow(MC3DPrintConfig.UPGRADE_BUFFER_FACTOR.get(), upgradeCount(UpgradeItem.Type.BUFFER)))));
    }

    private int speedAdjusted(int baseTicks) {
        return Math.max(1, (int) Math.round(baseTicks
                * Math.pow(MC3DPrintConfig.UPGRADE_SPEED_FACTOR.get(), upgradeCount(UpgradeItem.Type.SPEED))));
    }

    private int rfAdjusted(int baseRf) {
        return Math.max(1, (int) Math.round(baseRf
                * Math.pow(MC3DPrintConfig.UPGRADE_RF_FACTOR.get(), upgradeCount(UpgradeItem.Type.RF_EFFICIENCY))));
    }

    private boolean canEmitCopy(ItemStack template) {
        ItemStack output = inventory.getStackInSlot(SLOT_OUTPUT);
        if (output.isEmpty()) {
            return true;
        }
        return ItemStack.isSameItemSameTags(output, template) && output.getCount() < output.getMaxStackSize();
    }

    public void setOwner(@Nullable UUID owner) {
        this.owner = owner;
        setChanged();
    }

    @Nullable
    private net.minecraft.server.level.ServerPlayer ownerPlayer() {
        if (owner == null || level == null || level.getServer() == null) {
            return null;
        }
        return level.getServer().getPlayerList().getPlayer(owner);
    }

    private void emitCopy(ItemStack template) {
        ItemStack output = inventory.getStackInSlot(SLOT_OUTPUT);
        if (output.isEmpty()) {
            inventory.setStackInSlot(SLOT_OUTPUT, template.copyWithCount(1));
        } else {
            output.grow(1);
            inventory.setStackInSlot(SLOT_OUTPUT, output);
        }
        net.minecraft.server.level.ServerPlayer player = ownerPlayer();
        if (player != null) {
            com.pgmacdesign.mc3dprint.advancement.ModCriteria.FIRST_EXTRUSION.trigger(player);
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
            if (!autoStart && !startRequested) {
                // keep error states visible until the next trigger attempt
                if (state == State.IDLE) {
                    state = State.READY;
                }
                return;
            }
            if (retryCooldown > 0) {
                retryCooldown--;
                return;
            }
            retryCooldown = 20;
            startRequested = false; // consumed; re-trigger after fixing any error
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
        if (placementCooldown < speedAdjusted(MC3DPrintConfig.ticksPerBlock(tier))) {
            if (state == State.IDLE) {
                state = State.PRINTING;
            }
            return;
        }

        int rfPerBlock = rfAdjusted(MC3DPrintConfig.rfPerBlock(tier));
        if (!energy.hasAtLeast(rfPerBlock)) {
            state = State.PAUSED_NO_POWER;
            return;
        }

        Blueprint blueprint = cachedBlueprint;

        // repair mode: fast-forward through blocks that already match the
        // blueprint — they cost nothing and are never re-placed. This is what
        // lets a damaged build be fixed by simply printing the disc again.
        while (!activeJob.isComplete()) {
            PlacementEntry skipCandidate = placementOrder.get(activeJob.placed());
            BlockState target = blueprint.palette().get(skipCandidate.paletteIndex()).resolve()
                    .map(resolvedState -> resolvedState
                            .mirror(activeJob.orientation().mirror())
                            .rotate(activeJob.orientation().rotation()))
                    .orElse(null);
            if (target == null
                    || serverLevel.getBlockState(worldPosFor(skipCandidate.local(), blueprint)) != target) {
                break;
            }
            activeJob.setPlaced(activeJob.placed() + 1);
        }
        if (activeJob.isComplete()) {
            setChanged();
            tryFinishJob(serverLevel, disc);
            return;
        }

        PlacementEntry entry = placementOrder.get(activeJob.placed());
        BlockPos worldPos = worldPosFor(entry.local(), blueprint);

        if (!serverLevel.getBlockState(worldPos).canBeReplaced()) {
            state = State.PAUSED_OBSTRUCTED;
            return;
        }

        Optional<BlockState> resolved = blueprint.palette().get(entry.paletteIndex()).resolve();
        // unknown block (e.g. blueprint from a modded world) — skip it, never stall the job
        if (resolved.isPresent()) {
            int fuCost = blockFuCost(resolved.get());
            int costTier = blockFuTier(resolved.get());
            if (effectiveFu(costTier) < fuCost) {
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
            drainFu(fuCost, costTier);

            // zap: the head fires a beam and the block materializes
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                    worldPos.getX() + 0.5, worldPos.getY() + 0.5, worldPos.getZ() + 0.5,
                    8, 0.3, 0.3, 0.3, 0.02);
            serverLevel.playSound(null, worldPos, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_PLACE,
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.6F, 1.4F);
            lastPlacedPos = worldPos.immutable();
            syncToClients();
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
            notPrintableReason = "this tier cannot print structures (zero print footprint)";
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
            if (paletteItem == Items.AIR) {
                continue;
            }
            Optional<FuValue> value = FuValueRegistry.valueOf(new ItemStack(paletteItem));
            // strict mode: a block with NO value (after derivation) can never be
            // printed — closes the 'scan un-priced expensive block, print cheap'
            // exploit. When unknownBlocksPrintable=true such blocks are allowed,
            // but blockFuCost/blockFuTier clamp them to this machine's own tier.
            if (value.isEmpty()) {
                if (!MC3DPrintConfig.UNKNOWN_BLOCKS_PRINTABLE.get()) {
                    state = State.NOT_PRINTABLE;
                    notPrintableReason = String.format(
                            "blueprint block %s has no FU value (strict mode — set "
                                    + "unknownBlocksPrintable=true in the config, or register a value "
                                    + "via the API, to allow it)",
                            net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(paletteItem));
                    return;
                }
                continue; // permissive mode: priced at machine tier, always printable here
            }
            if (value.get().tier() > tier.number()) {
                state = State.NOT_PRINTABLE;
                notPrintableReason = String.format(
                        "blueprint block %s is Tier %d, which exceeds this machine's Tier %d",
                        net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(paletteItem),
                        value.get().tier(), tier.number());
                return;
            }
        }

        // min corner: centered horizontally over the printer, one block up,
        // shifted by the player-configured build offsets
        BlockPos origin = worldPosition.offset(
                -(size.getX() / 2) + offsetX, 1 + offsetY, -(size.getZ() / 2) + offsetZ);
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

        activeJob = new PrintJob(blueprintId, blueprint.name(), origin, orientation, size, blueprint.blockCount());
        cachedBlueprint = blueprint;
        placementOrder = buildPlacementOrder(blueprint);
        placementCooldown = 0;
        forceChunks(serverLevel, box, true);
        state = State.PRINTING;
        setChanged();
        syncToClients();
    }

    /**
     * A position is printable when it's replaceable OR already holds exactly
     * the block the blueprint wants there (repair/fill-in: never destructive,
     * matching blocks are simply skipped at zero cost).
     */
    private boolean isAreaClear(ServerLevel serverLevel, Blueprint blueprint,
                                PrintOrientation orientation, BlockPos origin) {
        boolean[] clear = {true};
        blueprint.forEachBlock((local, paletteIndex) -> {
            if (clear[0]) {
                BlockPos world = origin.offset(orientation.transform(local,
                        blueprint.sizeX(), blueprint.sizeY(), blueprint.sizeZ()));
                BlockState existing = serverLevel.getBlockState(world);
                if (!existing.canBeReplaced()) {
                    BlockState target = blueprint.palette().get(paletteIndex).resolve()
                            .map(resolvedState -> resolvedState
                                    .mirror(orientation.mirror())
                                    .rotate(orientation.rotation()))
                            .orElse(null);
                    if (existing != target) {
                        clear[0] = false;
                    }
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

        // print completion effect — the "done" moment should feel good
        BlockPos size = activeJob.size();
        serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.FIREWORK,
                activeJob.origin().getX() + size.getX() / 2.0,
                activeJob.origin().getY() + size.getY() + 0.5,
                activeJob.origin().getZ() + size.getZ() / 2.0,
                40, size.getX() / 3.0, 0.5, size.getZ() / 3.0, 0.05);
        serverLevel.playSound(null, worldPosition, net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP,
                net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 1.0F);

        net.minecraft.server.level.ServerPlayer player = ownerPlayer();
        if (player != null) {
            com.pgmacdesign.mc3dprint.advancement.ModCriteria.STRUCTURE_PRINTED.trigger(player);
        }

        releaseJobResources(serverLevel);
        inventory.setStackInSlot(SLOT_OUTPUT, disc.copy());
        inventory.setStackInSlot(SLOT_TEMPLATE, ItemStack.EMPTY);
        activeJob = null;
        cachedBlueprint = null;
        placementOrder = null;
        lastPlacedPos = null;
        state = State.IDLE;
        setChanged();
        syncToClients();
    }

    public void cancelActiveJob() {
        if (activeJob != null && level instanceof ServerLevel serverLevel) {
            releaseJobResources(serverLevel);
        }
        activeJob = null;
        cachedBlueprint = null;
        placementOrder = null;
        placementCooldown = 0;
        lastPlacedPos = null;
        setChanged();
        syncToClients();
    }

    private void releaseJobResources(ServerLevel serverLevel) {
        PrintZoneManager.release(serverLevel, worldPosition);
        if (activeJob != null && cachedBlueprint != null) {
            forceChunks(serverLevel, jobBox(), false);
        }
    }

    private BoundingBox jobBox() {
        BlockPos size = activeJob.size();
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
        if (activeJob != null && level instanceof ServerLevel serverLevel) {
            PrintZoneManager.claim(serverLevel, worldPosition, jobBox());
            forceChunks(serverLevel, jobBox(), true);
        }
    }

    // --- Client sync (renderer needs the active job + last placement) ---

    @Nullable
    public BlockPos lastPlacedPos() {
        return lastPlacedPos;
    }

    /** Client-side snapshot of a docked spool for the exterior render. */
    public record SpoolRenderInfo(int tier, float fillFraction, boolean creative) {
    }

    private final List<SpoolRenderInfo> clientSpools = new ArrayList<>();

    /** Docked-spool snapshots, one per spool slot in order (client render). */
    public List<SpoolRenderInfo> clientSpools() {
        return clientSpools;
    }

    /** One ghost block of the hologram preview (client render). */
    public record PreviewBlock(BlockPos pos, BlockState state) {
    }

    private final List<PreviewBlock> clientPreview = new ArrayList<>();
    @Nullable
    private BlockPos clientPreviewOrigin;
    @Nullable
    private BlockPos clientPreviewSize;
    private boolean clientPreviewOn;

    public List<PreviewBlock> clientPreview() {
        return clientPreview;
    }

    public boolean previewShowing() {
        return clientPreviewOn && !clientPreview.isEmpty() && activeJob == null;
    }

    @Nullable
    public BlockPos clientPreviewOrigin() {
        return clientPreviewOrigin;
    }

    @Nullable
    public BlockPos clientPreviewSize() {
        return clientPreviewSize;
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        if (activeJob != null) {
            tag.put("ActiveJob", activeJob.save());
        }
        if (lastPlacedPos != null) {
            tag.put("LastPlaced", net.minecraft.nbt.NbtUtils.writeBlockPos(lastPlacedPos));
        }
        tag.putInt("State", state.ordinal());

        if (previewEnabled && activeJob == null && level instanceof ServerLevel serverLevel) {
            ItemStack template = inventory.getStackInSlot(SLOT_TEMPLATE);
            UUID id = isLoadedDisc(template) ? BlueprintDiscItem.getBlueprintId(template).orElse(null) : null;
            if (id == null) {
                previewEnabled = false; // disc removed — preview dies with it
            } else {
                if (previewBlueprint == null || !id.equals(previewBlueprintId)) {
                    previewBlueprint = BlueprintFileStore.forServer(serverLevel.getServer()).load(id).orElse(null);
                    previewBlueprintId = id;
                }
                if (previewBlueprint != null
                        && previewBlueprint.blockCount() <= MC3DPrintConfig.PREVIEW_MAX_BLOCKS.get()) {
                    BlockPos size = new BlockPos(previewBlueprint.sizeX(), previewBlueprint.sizeY(),
                            previewBlueprint.sizeZ());
                    BlockPos origin = worldPosition.offset(
                            -(size.getX() / 2) + offsetX, 1 + offsetY, -(size.getZ() / 2) + offsetZ);
                    tag.put("Preview", com.pgmacdesign.mc3dprint.blueprint.BlueprintSerializer.write(previewBlueprint));
                    tag.put("PreviewOrigin", net.minecraft.nbt.NbtUtils.writeBlockPos(origin));
                }
            }
        }
        tag.putBoolean("PreviewOn", previewEnabled);

        ListTag spoolList = new ListTag();
        for (int i = 0; i < spools.getSlots(); i++) {
            ItemStack spool = spools.getStackInSlot(i);
            CompoundTag entry = new CompoundTag();
            if (spool.getItem() instanceof SpoolItem spoolItem) {
                entry.putInt("Tier", spoolItem.tier());
                entry.putFloat("Fill", spoolItem.creative() ? 1.0F
                        : (float) SpoolItem.getFu(spool) / Math.max(1, spoolItem.capacity()));
                entry.putBoolean("Creative", spoolItem.creative());
            }
            spoolList.add(entry);
        }
        tag.put("Spools", spoolList);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        activeJob = tag.contains("ActiveJob", Tag.TAG_COMPOUND) ? PrintJob.load(tag.getCompound("ActiveJob")) : null;
        lastPlacedPos = tag.contains("LastPlaced", Tag.TAG_COMPOUND)
                ? net.minecraft.nbt.NbtUtils.readBlockPos(tag.getCompound("LastPlaced")) : null;
        state = State.byOrdinal(tag.getInt("State"));

        clientPreviewOn = tag.getBoolean("PreviewOn");
        clientPreview.clear();
        clientPreviewOrigin = null;
        clientPreviewSize = null;
        if (tag.contains("Preview", Tag.TAG_COMPOUND) && tag.contains("PreviewOrigin", Tag.TAG_COMPOUND)) {
            Blueprint blueprint = com.pgmacdesign.mc3dprint.blueprint.BlueprintSerializer
                    .read(tag.getCompound("Preview"));
            BlockPos origin = net.minecraft.nbt.NbtUtils.readBlockPos(tag.getCompound("PreviewOrigin"));
            clientPreviewOrigin = origin;
            clientPreviewSize = new BlockPos(blueprint.sizeX(), blueprint.sizeY(), blueprint.sizeZ());
            blueprint.forEachBlock((local, paletteIndex) ->
                    blueprint.palette().get(paletteIndex).resolve().ifPresent(resolvedState ->
                            clientPreview.add(new PreviewBlock(origin.offset(local), resolvedState))));
        }

        clientSpools.clear();
        ListTag spoolList = tag.getList("Spools", Tag.TAG_COMPOUND);
        for (int i = 0; i < spoolList.size(); i++) {
            CompoundTag entry = spoolList.getCompound(i);
            clientSpools.add(entry.contains("Tier")
                    ? new SpoolRenderInfo(entry.getInt("Tier"), entry.getFloat("Fill"), entry.getBoolean("Creative"))
                    : null);
        }
    }

    @Nullable
    @Override
    public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this,
                be -> ((PrinterBlockEntity) be).getUpdateTag());
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection connection,
                             net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket packet) {
        if (packet.getTag() != null) {
            handleUpdateTag(packet.getTag());
        }
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        if (activeJob != null) {
            BlockPos size = activeJob.size();
            return new net.minecraft.world.phys.AABB(activeJob.origin(),
                    activeJob.origin().offset(size.getX(), size.getY(), size.getZ()))
                    .minmax(new net.minecraft.world.phys.AABB(worldPosition));
        }
        if (clientPreviewOrigin != null && clientPreviewSize != null) {
            return new net.minecraft.world.phys.AABB(clientPreviewOrigin,
                    clientPreviewOrigin.offset(clientPreviewSize))
                    .minmax(new net.minecraft.world.phys.AABB(worldPosition));
        }
        return super.getRenderBoundingBox();
    }

    private void syncToClients() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public int maxProgress() {
        return speedAdjusted(MC3DPrintConfig.itemPrintTicks(tier));
    }

    public ItemStackHandler inventory() {
        return inventory;
    }

    public boolean autoStart() {
        return autoStart;
    }

    public void setAutoStart(boolean value) {
        autoStart = value;
        setChanged();
    }

    /** Queues a job start for the next tick (GUI Start button or redstone). */
    public void requestStart() {
        startRequested = true;
        retryCooldown = 0;
        setChanged();
    }

    /** Rising-edge redstone triggers a start, WorldEdit-machine style. */
    public void onNeighborSignal(boolean powered) {
        if (powered && !lastRedstoneSignal) {
            requestStart();
        }
        lastRedstoneSignal = powered;
    }

    public int offset(int axis) {
        return switch (axis) {
            case 0 -> offsetX;
            case 1 -> offsetY;
            default -> offsetZ;
        };
    }

    /** Adjusts a build offset (0=X, 1=Y, 2=Z); takes effect on the next job. */
    public void adjustOffset(int axis, int delta) {
        int clamped;
        switch (axis) {
            case 0 -> offsetX = clamped = Mth.clamp(offsetX + delta, -MAX_OFFSET, MAX_OFFSET);
            case 1 -> offsetY = clamped = Mth.clamp(offsetY + delta, -MAX_OFFSET, MAX_OFFSET);
            default -> offsetZ = clamped = Mth.clamp(offsetZ + delta, -MAX_OFFSET, MAX_OFFSET);
        }
        setChanged();
        if (previewEnabled) {
            syncToClients(); // the ghost follows the offsets live
        }
    }

    /** Toggles the hologram preview; validates disc presence and the size cap. */
    public void togglePreview(@Nullable net.minecraft.world.entity.player.Player player) {
        if (previewEnabled) {
            previewEnabled = false;
        } else {
            ItemStack template = inventory.getStackInSlot(SLOT_TEMPLATE);
            if (!isLoadedDisc(template) || !(level instanceof ServerLevel serverLevel)) {
                if (player != null) {
                    player.displayClientMessage(Component.translatable("message.mc3dprint.preview_no_disc"), true);
                }
                return;
            }
            UUID id = BlueprintDiscItem.getBlueprintId(template).orElse(null);
            Blueprint blueprint = id == null ? null
                    : BlueprintFileStore.forServer(serverLevel.getServer()).load(id).orElse(null);
            if (blueprint == null) {
                if (player != null) {
                    player.displayClientMessage(Component.translatable("message.mc3dprint.preview_no_disc"), true);
                }
                return;
            }
            int cap = MC3DPrintConfig.PREVIEW_MAX_BLOCKS.get();
            if (blueprint.blockCount() > cap) {
                if (player != null) {
                    player.displayClientMessage(Component.translatable("message.mc3dprint.preview_too_big",
                            blueprint.blockCount(), cap), true);
                }
                return;
            }
            previewBlueprint = blueprint;
            previewBlueprintId = id;
            previewEnabled = true;
        }
        setChanged();
        syncToClients();
    }

    public ContainerData containerData() {
        return new SplitContainerData(DATA_COUNT, this::dataValue);
    }

    private int dataValue(int index) {
        return switch (index) {
            case DATA_PROGRESS -> activeJob != null ? activeJob.placed() : itemProgress;
            case DATA_MAX_PROGRESS -> activeJob != null ? Math.max(1, activeJob.totalBlocks()) : maxProgress();
            case DATA_ENERGY -> energy.getEnergyStored();
            case DATA_MAX_ENERGY -> energy.getMaxEnergyStored();
            case DATA_STATE -> state.ordinal();
            case DATA_FU -> effectiveFu(displayTier());
            case DATA_FU_CAP -> effectiveFuCapacity(displayTier());
            case DATA_TEMPLATE_COST -> {
                ItemStack template = inventory.getStackInSlot(SLOT_TEMPLATE);
                yield activeJob == null && !template.isEmpty() ? Math.max(0, itemFuCost(template)) : 0;
            }
            case DATA_SPOOLS_USED -> {
                int used = 0;
                for (int i = 0; i < spools.getSlots(); i++) {
                    if (!spools.getStackInSlot(i).isEmpty()) {
                        used++;
                    }
                }
                yield used;
            }
            case DATA_SPOOL_SLOTS -> spools.getSlots();
            case DATA_AUTO_START -> autoStart ? 1 : 0;
            case DATA_OFFSET_X -> offsetX;
            case DATA_OFFSET_Y -> offsetY;
            case DATA_OFFSET_Z -> offsetZ;
            case DATA_PREVIEW -> previewEnabled ? 1 : 0;
            default -> 0;
        };
    }

    /**
     * Tier the FU gauge is denominated in: the template item's cost tier when
     * one is loaded (so gauge and cost label use the same units), else the
     * machine tier.
     */
    private int displayTier() {
        ItemStack template = inventory.getStackInSlot(SLOT_TEMPLATE);
        if (!template.isEmpty() && !isLoadedDisc(template)) {
            Optional<FuValue> value = FuValueRegistry.valueOf(template);
            if (value.isPresent()) {
                return value.get().tier();
            }
        }
        return tier.number();
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
            // Per the I/O design: top inserts, bottom extracts, and the four
            // sides are reserved exclusively for docked filament spools — no
            // general item I/O (they render the spinning spools instead).
            if (side == Direction.UP) {
                return inputCap.cast();
            }
            if (side == Direction.DOWN) {
                return outputCap.cast();
            }
            return LazyOptional.empty();
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
        tag.put("Upgrades", upgrades.serializeNBT());
        tag.putInt("Energy", energy.getEnergyStored());
        tag.putInt("Progress", itemProgress);
        tag.putInt("State", state.ordinal());
        if (activeJob != null) {
            tag.put("ActiveJob", activeJob.save());
        }
        ListTag historyTag = new ListTag();
        history.forEach(historyTag::add);
        tag.put("History", historyTag);
        tag.putBoolean("AutoStart", autoStart);
        tag.putBoolean("LastRedstone", lastRedstoneSignal);
        tag.putInt("OffsetX", offsetX);
        tag.putInt("OffsetY", offsetY);
        tag.putInt("OffsetZ", offsetZ);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putBoolean("PreviewEnabled", previewEnabled);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        inventory.deserializeNBT(tag.getCompound("Inventory"));
        spools.deserializeNBT(tag.getCompound("Spools"));
        upgrades.deserializeNBT(tag.getCompound("Upgrades"));
        refreshEnergyCapacity();
        energy.setStored(tag.getInt("Energy"));
        itemProgress = tag.getInt("Progress");
        state = State.byOrdinal(tag.getInt("State"));
        activeJob = tag.contains("ActiveJob", Tag.TAG_COMPOUND) ? PrintJob.load(tag.getCompound("ActiveJob")) : null;
        history.clear();
        for (Tag t : tag.getList("History", Tag.TAG_COMPOUND)) {
            history.add((CompoundTag) t);
        }
        autoStart = tag.getBoolean("AutoStart");
        lastRedstoneSignal = tag.getBoolean("LastRedstone");
        offsetX = Mth.clamp(tag.getInt("OffsetX"), -MAX_OFFSET, MAX_OFFSET);
        offsetY = Mth.clamp(tag.getInt("OffsetY"), -MAX_OFFSET, MAX_OFFSET);
        offsetZ = Mth.clamp(tag.getInt("OffsetZ"), -MAX_OFFSET, MAX_OFFSET);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        previewEnabled = tag.getBoolean("PreviewEnabled");
    }
}
