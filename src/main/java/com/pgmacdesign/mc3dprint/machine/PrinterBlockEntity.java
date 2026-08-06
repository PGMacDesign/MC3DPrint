package com.pgmacdesign.mc3dprint.machine;

import com.pgmacdesign.mc3dprint.compat.BeData;
import com.pgmacdesign.mc3dprint.compat.NbtCompat;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.blueprint.Blueprint;
import com.pgmacdesign.mc3dprint.blueprint.BlueprintBlockState;
import com.pgmacdesign.mc3dprint.blueprint.BlueprintFileStore;
import com.pgmacdesign.mc3dprint.blueprint.PrintOrientation;
import com.pgmacdesign.mc3dprint.config.MC3DPrintConfig;
import com.pgmacdesign.mc3dprint.fu.FilamentDrain;
import com.pgmacdesign.mc3dprint.fu.FuConversion;
import com.pgmacdesign.mc3dprint.fu.IFilamentSource;
import com.pgmacdesign.mc3dprint.registry.ModCapabilities;
import com.pgmacdesign.mc3dprint.fu.FuValue;
import com.pgmacdesign.mc3dprint.fu.FuValueRegistry;
import com.pgmacdesign.mc3dprint.fu.SpoolItem;
import com.pgmacdesign.mc3dprint.item.BlueprintDiscItem;
import com.pgmacdesign.mc3dprint.item.ResinItem;
import com.pgmacdesign.mc3dprint.machine.resin.ResinEffects;
import com.pgmacdesign.mc3dprint.machine.upgrade.UpgradeItem;
import com.pgmacdesign.mc3dprint.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
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
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.Container;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.RangedWrapper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
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
    public static final int DATA_ROTATION = 15;
    // Smallest printer tier whose footprint fits the loaded blueprint, surfaced to
    // the GUI so a NEEDS_HIGHER_TIER status can name the tier ("Requires a Tier N
    // Printer"). 0 when no tier fits / not applicable.
    public static final int DATA_REQUIRED_TIER = 16;
    public static final int DATA_DECON_MODE = 17;
    // Matter Calculator: pre-print readout for the loaded disc (0 when no disc/report).
    public static final int DATA_BP_FU_TOTAL = 18;    // summed FU cost across tiers (display units)
    public static final int DATA_BP_RF = 19;          // total RF the job will consume
    public static final int DATA_BP_ETA = 20;         // total ticks at current speed
    public static final int DATA_BP_SHORTFALL = 21;   // lowest tier whose filament coverage fails; 0 = covered
    public static final int DATA_COST_TIER_BASE = 22; // +0..7 — per-tier FU cost (tier units)
    public static final int DATA_AVAIL_TIER_BASE = 30;// +0..7 — exact-tier FU available, docked + network
    public static final int DATA_JOB_ACTIVE = 38;      // 1 while a print OR deconstruct job exists
    public static final int DATA_FU_NETWORK = 39;      // rack/cable FU toward the display tier (docked shows on the gauge)
    public static final int DATA_COUNT = 40;

    /** Newest history entries mirrored to clients for the GUI tooltip. */
    public static final int HISTORY_SYNC_CAP = 8;

    /** Build offsets are clamped to [-MAX_OFFSET, MAX_OFFSET] on each axis. */
    public static final int MAX_OFFSET = 32;

    public enum State {
        IDLE, PRINTING, PAUSED_NO_POWER, PAUSED_OUTPUT_FULL, PAUSED_OBSTRUCTED, ZONE_CONFLICT,
        PAUSED_NO_FILAMENT, NOT_PRINTABLE, AREA_TOO_SMALL, NEEDS_HIGHER_TIER, READY,
        DECONSTRUCTING; // appended last: State syncs/persists by ordinal

        public static State byOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : IDLE;
        }
    }

    private final ItemStackHandler inventory = new ItemStackHandler(SLOT_COUNT) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            // A freshly loaded disc may collide with the world at the current build
            // offsets (a bigger/smaller blueprint, or one overlapping another zone).
            // Re-check obstruction now so OBSTRUCTED shows immediately, instead of
            // only on offset change / GUI open / pressing Start. Cheap no-op unless a
            // disc is loaded with no active job (see recheckObstruction).
            if (slot == SLOT_TEMPLATE) {
                recheckObstruction();
            }
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            // A wound spool stores FU in a component; Item Mode would copy it for a trivial derived
            // cost. The GUI's mayPlace blocks this, but a hopper into the UP-face template slot
            // bypasses the GUI, so reject spools at the capability layer too. (CreativeSpoolItem
            // extends SpoolItem, so this covers both.)
            if (slot == SLOT_TEMPLATE && stack.getItem() instanceof SpoolItem) {
                return false;
            }
            return super.isItemValid(slot, stack);
        }
    };

    // Resin slot (one slot, holds a stack up to 64): a consumed-per-print modifier
    // that improves a blueprint print. Accepts only ResinItem; the effect is applied
    // during the blueprint print loop and only on official/found blueprints.
    private final ItemStackHandler resins = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return stack.getItem() instanceof ResinItem;
        }
    };

    private final MachineTier tier;
    private final ItemStackHandler spools;
    private final ItemStackHandler upgrades;
    private final MachineEnergyStorage energy;

    // multiplicative upgrade factors per module (design: never additive);
    // config-exposed for pack makers per the design doc

    // Item-handler views, exposed per-face by getItemHandler (registered centrally in
    // ModCapabilities). The "all" view is the inventory itself.
    private final IItemHandler inputHandler =
            new RangedWrapper(inventory, SLOT_TEMPLATE, SLOT_TEMPLATE + 1);
    private final IItemHandler outputHandler =
            new RangedWrapper(inventory, SLOT_OUTPUT, SLOT_OUTPUT + 1) {
                @Override
                @Nonnull
                public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
                    return stack; // extract-only face
                }
            };

    // Item Mode
    private int itemProgress;

    // Blueprint Mode
    @Nullable
    private PrintJob activeJob;

    // --- Deconstruct Mode (the printer in reverse; see DeconstructJob) ---
    private boolean deconstructMode;
    @Nullable
    private BlockPos deconstructMin;   // world min corner of the armed region
    @Nullable
    private BlockPos deconstructSize;
    @Nullable
    private DeconstructJob deconstructJob;
    /**
     * Safety arm-gate: true from the moment a region is handed over (or the machine
     * is switched into Deconstruct) until the player presses Start once. While set,
     * Auto NEVER starts a deconstruct — arming a machine can't dissolve blocks by
     * surprise. After that first explicit Start, Auto resumes standing-recycler duty.
     */
    private boolean deconArmedRequiresStart;
    @Nullable
    private transient Blueprint cachedBlueprint;          // lazily loaded for activeJob
    @Nullable
    private transient List<PlacementEntry> placementOrder; // derived from cachedBlueprint
    private int placementCooldown;
    private int retryCooldown;
    private final List<CompoundTag> history = new ArrayList<>();

    // Resin (catalyst) state for the active blueprint job. Captured at job start from
    // the Resin slot IF the disc is official; the resin stays locked in the slot until
    // the job's first committed placement, where it's consumed (Q6). Phase 4 effects
    // read armedResinEffect/armedResinTier on each placed block.
    @Nullable
    private ResinItem.Effect armedResinEffect;
    private int armedResinTier;
    private boolean resinConsumed;
    // Per-print caps (reset at job start). bankedXp persists across jobs and is released
    // when the printed disc is pulled from the output slot (furnace-style XP yield).
    private int saltedThisJob;
    private int treasureThisJob;
    private int bankedXp;
    // Quartermaster shared budgets, pre-counted at job start and drained evenly as each
    // container is stocked (so e.g. 64 coal blocks split exactly across however many furnaces
    // the print contains). The enchanted move-in tools go to the first storage container only.
    private int qmFurnaceRemaining;
    private int qmCoalRemaining;
    private int qmStorageRemaining;
    private int qmFoodRemaining;
    private int qmTorchRemaining;
    private boolean qmToolsGiven;

    private State state = State.IDLE;
    private static final Logger LOGGER = LogUtils.getLogger();
    // Human-readable explanation for the most recent NOT_PRINTABLE; logged once
    // on the transition into that state so the player can see WHY in the log.
    private String notPrintableReason = "";
    // Smallest tier that fits the loaded blueprint's footprint, set alongside
    // NEEDS_HIGHER_TIER so the GUI can name it. 0 = none / not applicable.
    private int requiredTier = 0;
    // Blocks skipped during the current structure job because this machine can't
    // print them (no FU value in strict mode, or a tier above the machine). Reset
    // when a job starts; each distinct type is logged once, summarized on finish.
    private int skippedThisJob = 0;
    private final java.util.Set<String> skippedTypesLogged = new java.util.HashSet<>();

    // blueprint jobs start on a trigger (GUI Start button / redstone rising
    // edge) unless auto-start is enabled; build origin is offset-adjustable
    private boolean autoStart;
    private boolean startRequested;
    private boolean lastRedstoneSignal;
    /** Player who placed the machine — receives print advancements. */
    @Nullable
    private UUID owner;

    // hologram preview: ghost-renders the loaded disc at the build position.
    // Defaults ON for freshly-placed printers (loaded printers restore from NBT).
    private boolean previewEnabled = true;
    @Nullable
    private Blueprint previewBlueprint;
    @Nullable
    private UUID previewBlueprintId;
    private int offsetX;
    private int offsetY;
    private int offsetZ;
    // Build rotation about Y (90° steps); mirror is always NONE. Persists like the offsets.
    private Rotation rotation = Rotation.NONE;
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
                // The exterior spool reels are drawn by the BER from synced BE data
                // (clientSpools), NOT from the open container's slot sync. Pulling a
                // spool out through the GUI mutates the handler here but never sent a
                // block-update packet, so the in-world reels stayed until the chunk
                // reloaded (relog). Syncing on every handler mutation covers the GUI,
                // hopper, attach/detach, and drain paths uniformly.
                syncToClients();
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

    public static void serverTick(Level level, BlockPos pos, BlockState blockState, PrinterBlockEntity printer) {
        printer.tick();
        // Outside tick() on purpose: every branch in there returns early (including the
        // unformed-multiblock bail), and the Redstone Module output has to be reconciled
        // on all of them.
        printer.updateRedstoneOutput();
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

        if (deconstructMode) {
            if (activeJob != null) {
                cancelActiveJob(); // mode epoch: a print job never survives the switch
            }
            tickDeconstructMode();
            if (state != previous) {
                setChanged();
            }
            return;
        }
        if (deconstructJob != null) {
            cancelActiveJob(); // mode switched back to Print mid-deconstruct
        }

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

        // XP Yield: release banked XP once the printed disc is pulled from the output
        // slot (furnace-style — to whoever collects it).
        if (bankedXp > 0 && inventory.getStackInSlot(SLOT_OUTPUT).isEmpty()
                && level instanceof ServerLevel sl) {
            int xp = bankedXp;
            bankedXp = 0;
            setChanged();
            net.minecraft.world.entity.ExperienceOrb.award(sl,
                    net.minecraft.world.phys.Vec3.atCenterOf(worldPosition.above()), xp);
        }
    }

    /** Registry id of an item/stack for diagnostic logs, e.g. "minecraft:diamond". */
    private static String idOf(ItemStack stack) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    // --- #print_restricted trophy gate ---

    private static boolean isRestrictedBlock(BlockState state) {
        Item item = state.getBlock().asItem();
        return item != Items.AIR
                && new ItemStack(item).is(com.pgmacdesign.mc3dprint.registry.ModItemTags.PRINT_RESTRICTED);
    }

    /** True when this block's item is on {@link ModItemTags#NO_PRINT} (wind-only, never printed). */
    private static boolean isNoPrintBlock(BlockState state) {
        Item item = state.getBlock().asItem();
        return item != Items.AIR
                && new ItemStack(item).is(com.pgmacdesign.mc3dprint.registry.ModItemTags.NO_PRINT);
    }

    /** True when the loaded disc is OFFICIAL and its curated allowance lists this block's item. */
    private boolean restrictedAllowedForLoadedDisc(BlockState state) {
        ItemStack disc = inventory.getStackInSlot(SLOT_TEMPLATE);
        if (!isLoadedDisc(disc) || !BlueprintDiscItem.isOfficial(disc)) {
            return false;
        }
        UUID id = BlueprintDiscItem.getBlueprintId(disc).orElse(null);
        return id != null && com.pgmacdesign.mc3dprint.blueprint.CuratedBlueprints
                .restrictedAllowance(id)
                .contains(net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .getKey(state.getBlock().asItem()).toString());
    }

    // --- Item Mode ---

    private void tickItemMode(ItemStack template) {
        // Reset per tick; only the tier-gate branch below re-sets it, so requiredTier()
        // honours its "0 when not applicable" contract for empty/unvalued/printable items.
        requiredTier = 0;
        if (template.isEmpty()) {
            state = State.IDLE;
            itemProgress = 0;
            return;
        }
        // A spool must never be printable: it carries stored FU, so copying it would launder
        // filament for a derived cost. Defense-in-depth choke point behind the handler guard.
        if (template.getItem() instanceof SpoolItem) {
            state = State.NOT_PRINTABLE;
            itemProgress = 0;
            notPrintableReason = idOf(template) + " is a filament spool; printers never duplicate stored filament";
            return;
        }
        // NO_PRINT is the "can wind, can't print" gate: the item is valued (so it winds for a
        // recycle payout) but the printer must never reproduce it. Checked FIRST, ahead of the
        // trophy gate and the FU/tier branch, so an item on BOTH #no_print and #print_restricted
        // (wither_skeleton_skull) reports the wind-only status and NO_PRINT keeps its precedence.
        if (template.is(com.pgmacdesign.mc3dprint.registry.ModItemTags.NO_PRINT)) {
            state = State.NOT_PRINTABLE;
            itemProgress = 0;
            notPrintableReason = idOf(template)
                    + " is recyclable but not printable (wind-only)";
            return;
        }
        // Item mode with a restricted trophy would be straight duplication — refuse
        // regardless of FU value (blueprint mode has the official-allowance gate).
        if (template.is(com.pgmacdesign.mc3dprint.registry.ModItemTags.PRINT_RESTRICTED)) {
            state = State.NOT_PRINTABLE;
            itemProgress = 0;
            notPrintableReason = idOf(template)
                    + " is a restricted trophy item — printers never duplicate it";
            return;
        }
        int fuCost = itemFuCost(template);
        int costTier = itemFuTier(template);
        if (fuCost < 0) {
            itemProgress = 0;
            Optional<FuValue> value = FuValueRegistry.valueOf(template);
            if (value.isEmpty()) {
                state = State.NOT_PRINTABLE;
                notPrintableReason = String.format(
                        "%s has no FU value (unpriced/unknown item — register one via the API/config)",
                        idOf(template));
            } else {
                // Valued but above this machine's tier: a bigger printer WOULD print it, so
                // point the player at the tier they need rather than a dead-end "Not Printable".
                // (itemFuCost only returns <0 for unvalued or tier-too-high, so value present
                // here always means the latter.)
                requiredTier = value.get().tier();
                state = State.NEEDS_HIGHER_TIER;
                notPrintableReason = String.format(
                        "%s is Tier %d, which exceeds this machine's Tier %d",
                        idOf(template), value.get().tier(), tier.number());
            }
        } else if (!canEmitCopy(template)) {
            state = State.PAUSED_OUTPUT_FULL;
        } else if (affordableFu(costTier) < fuCost) {
            state = State.PAUSED_NO_FILAMENT;
        } else if (!energy.hasAtLeast(rfAdjusted(MC3DPrintConfig.itemRfPerTick(tier)))) {
            state = State.PAUSED_NO_POWER;
        } else if (!autoStart && !startRequested) {
            // Item is printable but Auto is off and no manual Start is queued: wait,
            // mirroring blueprint mode. Don't advance progress; show READY.
            state = State.READY;
        } else {
            state = State.PRINTING;
            energy.consume(rfAdjusted(MC3DPrintConfig.itemRfPerTick(tier)));
            itemProgress++;
            if (itemProgress >= maxProgress()) {
                drainFu(fuCost, costTier);
                emitCopy(template);
                itemProgress = 0;
                // A manual Start prints exactly one item; Auto keeps printing.
                if (!autoStart) {
                    startRequested = false;
                }
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
        if (value.isPresent()) {
            return applyEfficiency(value.get().fu());
        }
        // Structural matter (farmland, crops, water, wall torches…) is free — there's
        // no obtainable-as-shown item to charge for. Other unpriced blocks (permissive
        // mode only — strict mode refuses them up front in tryStartJob) cost
        // unknownBlockFu, denominated at the machine's tier so a low-tier machine can't
        // print them cheaply.
        if (isStructuralMatter(state)) {
            return 0;
        }
        return applyEfficiency(MC3DPrintConfig.UNKNOWN_BLOCK_FU.get());
    }

    private int blockFuTier(BlockState state) {
        // No value -> charge at this machine's own tier (never cheap T1 default).
        return blockFuValue(state).map(FuValue::tier).orElse(tier.number());
    }

    private Optional<FuValue> blockFuValue(BlockState state) {
        // A block is charged against its own item. Powder snow's item is the powder_snow_bucket
        // (a SolidBucketItem, registered as the block's item), so valuing that bucket in
        // FuValueRegistry is enough to price the printed block — no special-casing needed here.
        Item item = state.getBlock().asItem();
        return item == Items.AIR ? Optional.empty() : FuValueRegistry.valueOf(new ItemStack(item));
    }

    /**
     * Whether this machine can print the given (resolved) blueprint block. False
     * for blocks with no FU value in strict mode, and for any block whose tier
     * exceeds this machine's tier. Such blocks are SKIPPED during a structure
     * print (never placed), so the structure still builds and the tier / strict
     * anti-exploit gate is preserved (you can't obtain the block by skipping it).
     */
    private boolean canPrintBlock(BlockState resolvedState) {
        // Water flashes to steam in an ultrawarm dimension (the Nether), so a pure water block
        // can't print there — skip it. Routing through canPrintBlock also hides it from the
        // ghost preview (the printability mask reuses this method).
        // 1.21.11 dissolved DimensionType.ultraWarm() into the WATER_EVAPORATES environment attribute.
        //? if >=1.21.11 {
        /*if (isWaterUnplaceableIn(resolvedState, level != null && level.environmentAttributes()
                .getDimensionValue(net.minecraft.world.attribute.EnvironmentAttributes.WATER_EVAPORATES))) {
        *///?} else {
        if (isWaterUnplaceableIn(resolvedState, level != null && level.dimensionType().ultraWarm())) {
        //?}
            return false;
        }
        // No-print (wind-only) blocks are valued but must never print, even from an official disc,
        // so this precedes the trophy allowance below (wither_skeleton_skull is on both tags).
        if (isNoPrintBlock(resolvedState)) {
            return false;
        }
        Optional<FuValue> value = blockFuValue(resolvedState);
        if (value.isPresent()) {
            // Trophy gate: a #print_restricted block prints only from an OFFICIAL disc
            // whose curated blueprint carries an allowance for it. Routing through
            // canPrintBlock covers the print loop, the calculator, and the ghost mask.
            if (isRestrictedBlock(resolvedState) && !restrictedAllowedForLoadedDisc(resolvedState)) {
                return false;
            }
            return value.get().tier() <= tier.number();
        }
        // Structural matter (farmland, crops, water, wall torches, redstone wire, fire…)
        // is an in-world-only state with no obtainable-as-shown item, so it can't be an
        // FU-exploit vector — always printable as free matter (this is what makes
        // farms/decorated builds print whole). An itemed-but-unpriced solid block still
        // respects strict mode (the scan-expensive / print-cheap guard).
        if (isStructuralMatter(resolvedState)) {
            return true;
        }
        return MC3DPrintConfig.UNKNOWN_BLOCKS_PRINTABLE.get();
    }

    /**
     * Whether a (resolved) block is "structural matter" — an in-world-only state that
     * isn't survival-obtainable as the depicted block, so it carries no FU and prints
     * free (like /paste). Three families:
     * <ul>
     *   <li><b>itemless</b> blocks whose {@code asItem()} is AIR (water, fire, …);</li>
     *   <li><b>planted growth</b> — {@link BushBlock} descendants (crops, stems, nether
     *       wart, saplings, flowers): their item is a seed/sapling, never the depicted
     *       grown {@code age=N} block;</li>
     *   <li><b>tilled ground</b> — farmland / dirt-path: their {@code asItem()} exists
     *       but no loot ever drops it (you get dirt), so it's not really obtainable.</li>
     * </ul>
     * Crucially this keys on block <i>type</i>, not on the seed/food item's price, so it
     * stays correct once recipe derivation values {@code carrot}/{@code potato}/etc. The
     * anti-exploit gate is untouched: an unpriced <i>itemed solid</i> (a modded ore a pack
     * forgot to value) is none of these families, so strict mode still refuses it.
     */
    private static boolean isStructuralMatter(BlockState state) {
        if (state.isAir()) {
            return false;
        }
        if (state.getBlock().asItem() == Items.AIR) {
            return true; // water, fire, wall torches, redstone wire, …
        }
        Block block = state.getBlock();
        return block instanceof net.minecraft.world.level.block.BushBlock     // crops/stems/saplings/flowers/wart
                || block instanceof net.minecraft.world.level.block.FarmBlock      // farmland
                || block instanceof net.minecraft.world.level.block.DirtPathBlock; // grass/dirt path
    }

    /**
     * Water can't exist in an ultrawarm dimension (the Nether) — it flashes to steam. A pure
     * water block (source/flowing) printed there must be skipped, so a build's water bed/moat
     * simply doesn't appear. Lava is unaffected (it's native to the Nether). The {@code ultraWarm}
     * flag is passed in (not read here) so the rule is unit/gametest-testable without an actual
     * ultrawarm world.
     */
    public static boolean isWaterUnplaceableIn(BlockState state, boolean ultraWarm) {
        return ultraWarm
                && state.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock
                && state.getFluidState().is(net.minecraft.tags.FluidTags.WATER);
    }

    /**
     * The state to actually place: in an ultrawarm dimension a waterlogged solid loses its water
     * (the block still prints, just dry) to honour the no-water-in-the-Nether rule. Pure water
     * blocks are handled earlier by {@link #isWaterUnplaceableIn} (skipped, never reach here).
     */
    public static BlockState dewaterFor(BlockState state, boolean ultraWarm) {
        var waterlogged = net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED;
        if (ultraWarm && state.hasProperty(waterlogged) && state.getValue(waterlogged)) {
            return state.setValue(waterlogged, false);
        }
        return state;
    }

    /**
     * Public delegation to {@link #isStructuralMatter} for use in GameTests that
     * verify curated-blueprint printability without instantiating a real printer.
     * No behavior change — identical logic, different visibility.
     */
    public static boolean isStructuralMatterForTest(BlockState state) {
        return isStructuralMatter(state);
    }

    private void recordSkippedBlock(BlockState resolvedState) {
        skippedThisJob++;
        String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .getKey(resolvedState.getBlock()).toString();
        if (skippedTypesLogged.add(id)) {
            LOGGER.info("[MC3DP] Tier {} printer at {} is skipping un-printable block {} "
                    + "(no FU value in strict mode, or above this machine's tier)",
                    tier.number(), worldPosition, id);
        }
    }

    /**
     * FU cost to print {@code baseFu} worth of matter on this machine. The tier's
     * innate markup ({@code 1/efficiency − 1}) is shaved an equal share by each
     * Efficiency module, reaching exactly 1:1 (break-even) at {@code maxPerType}
     * modules — and it never drops below 1:1, so printing is never free matter.
     */
    private int applyEfficiency(int baseFu) {
        int maxPerType = MC3DPrintConfig.UPGRADE_MAX_PER_TYPE.get();
        int eff = Math.min(upgradeCount(UpgradeItem.Type.EFFICIENCY), maxPerType);
        double markup = (1.0 / MC3DPrintConfig.efficiency(tier) - 1.0) * (1.0 - (double) eff / maxPerType);
        // epsilon guard: an exact-integer cost shouldn't be ceil'd up by float noise
        int cost = (int) Math.ceil(baseFu * (1.0 + markup) - 1.0e-7);
        return Math.max(baseFu, cost); // never below break-even
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
        return FuConversion.clampToInt(
                FuConversion.fromBase(effectiveFuBase(costTier), costTier, FuConversion.ratio()));
    }

    /** Docked-spool FU toward a {@code costTier} cost, in base units (down-only). */
    private long effectiveFuBase(int costTier) {
        int ratio = FuConversion.ratio();
        long base = 0;
        for (int i = 0; i < spools.getSlots(); i++) {
            ItemStack spool = spools.getStackInSlot(i);
            if (spool.getItem() instanceof SpoolItem spoolItem
                    && FuConversion.canCover(spoolItem.tier(), costTier)) {
                base += FuConversion.toBase(SpoolItem.getFu(spool), spoolItem.tier(), ratio);
            }
        }
        return base;
    }

    /**
     * Affordability for the print gate: docked spools PLUS what adjacent
     * Filament-Unit sources (direct-touch racks, or a cable's whole network of
     * racks) can supply. Mirrors the docked-first/reserve drain so a printer with
     * empty docked spools but a stocked rack still prints.
     */
    private int affordableFu(int costTier) {
        int ratio = FuConversion.ratio();
        List<IFilamentSource> sources = reachableSources();
        long base = 0;
        for (int tier = costTier; tier <= SpoolItem.CAPACITY_BY_TIER.length; tier++) {
            base += FilamentDrain.availableTier(spools, tier, ratio); // docked
            for (IFilamentSource src : sources) {
                base += src.availableExactTier(tier);
            }
        }
        return FuConversion.clampToInt(FuConversion.fromBase(base, costTier, ratio));
    }

    /**
     * Network-only FU (racks direct or via cable) toward a {@code costTier} cost,
     * in tier units — what the gauge tooltip reports beyond the docked spools. A
     * printer whose gauge reads near-empty but has a stocked rack is NOT out of
     * filament; this is the number that says so.
     */
    public int networkFu(int costTier) {
        int ratio = FuConversion.ratio();
        long base = 0;
        for (IFilamentSource src : reachableSources()) {
            for (int t = costTier; t <= SpoolItem.CAPACITY_BY_TIER.length; t++) {
                base += src.availableExactTier(t);
            }
        }
        return FuConversion.clampToInt(FuConversion.fromBase(base, costTier, ratio));
    }

    /**
     * Every distinct Filament-Unit leaf source reachable from this printer:
     * direct-touch racks plus the racks behind any adjacent cable, flattened and
     * identity-deduped so a rack reachable by more than one path counts once.
     * The cable's flood is throttle-cached, so this is a cheap per-call gather.
     */
    private List<IFilamentSource> reachableSources() {
        if (level == null) {
            return List.of();
        }
        Set<IFilamentSource> set = Collections.newSetFromMap(new IdentityHashMap<>());
        collectAdjacentFilamentSources(worldPosition, set);
        // A formed multiblock fabricator pulls from anything adjacent to ANY part of
        // its casing pad — so a rack or cable plugged into the structure (not just the
        // buried controller) feeds it.
        BlockState self = getBlockState();
        if (self.getBlock() instanceof com.pgmacdesign.mc3dprint.machine.multiblock.ControllerBlock
                && self.getValue(com.pgmacdesign.mc3dprint.machine.multiblock.ControllerBlock.FORMED)) {
            for (BlockPos offset : com.pgmacdesign.mc3dprint.machine.multiblock.MultiblockPattern
                    .componentOffsets(tier)) {
                collectAdjacentFilamentSources(worldPosition.offset(offset), set);
            }
        }
        return new ArrayList<>(set);
    }

    private void collectAdjacentFilamentSources(BlockPos center, Set<IFilamentSource> out) {
        for (Direction dir : Direction.values()) {
            IFilamentSource src = level.getCapability(
                    ModCapabilities.FILAMENT_SOURCE, center.relative(dir), dir.getOpposite());
            if (src != null) {
                src.collectSources(out);
            }
        }
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
     * Drains a cost denominated at {@code costTier}, globally tier-smart: it
     * sweeps tier bands from the cost tier upward, spending the cheapest
     * qualifying filament first across docked spools AND every reachable rack —
     * so a high-tier spool is never wasted on a low-tier block because of dock
     * order. Within a tier, docked spools feed before the network. Down-only
     * (tiers below the cost never contribute), with at most one ceil unit of
     * overshoot on the covering spool. The source gather is lazy: a printer that
     * covers the cost from its own docked spools never touches the network.
     */
    private void drainFu(int amount, int costTier) {
        int ratio = FuConversion.ratio();
        long remainingBase = FuConversion.toBase(amount, costTier, ratio);
        List<IFilamentSource> sources = null;
        for (int tier = costTier; tier <= SpoolItem.CAPACITY_BY_TIER.length && remainingBase > 0; tier++) {
            remainingBase = FilamentDrain.drainTier(spools, remainingBase, tier, ratio); // docked first
            if (remainingBase <= 0) {
                break;
            }
            if (sources == null) {
                sources = reachableSources();
            }
            for (IFilamentSource src : sources) {
                if (remainingBase <= 0) {
                    break;
                }
                remainingBase -= src.drainExactTier(tier, remainingBase);
            }
        }
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
                spools.setStackInSlot(i, held.split(1)); // onContentsChanged syncs the reel
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
                spools.setStackInSlot(i, ItemStack.EMPTY); // onContentsChanged syncs the reel
                return spool;
            }
        }
        return ItemStack.EMPTY;
    }

    public ItemStackHandler spoolInventory() {
        return spools;
    }

    public ItemStackHandler resinInventory() {
        return resins;
    }

    /** Q6 lock: the resin committed to the active job can't be pulled until it's
     *  consumed on the first placement (after that the rest of a stack is free).
     *  Drives the GUI slot's mayPickup. */
    public boolean isResinLocked() {
        return activeJob != null && armedResinEffect != null && !resinConsumed;
    }

    /** Effect armed for the active job, or null if this print isn't catalyzed. */
    @Nullable
    public ResinItem.Effect armedResinEffect() {
        return armedResinEffect;
    }

    public int armedResinTier() {
        return armedResinTier;
    }

    /** Capture the slotted resin for this job iff the disc is an official blueprint. */
    private void armResin(ItemStack disc) {
        ItemStack resin = resins.getStackInSlot(0);
        if (!resin.isEmpty() && resin.getItem() instanceof ResinItem r
                && BlueprintDiscItem.isOfficial(disc)) {
            armedResinEffect = r.effect();
            armedResinTier = r.tier();
        } else {
            armedResinEffect = null;
            armedResinTier = 0;
        }
        resinConsumed = false;
        saltedThisJob = 0;
        treasureThisJob = 0;
    }

    private void clearArmedResin() {
        armedResinEffect = null;
        armedResinTier = 0;
        resinConsumed = false;
    }

    @Nullable
    private static ResinItem.Effect parseResinEffect(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        for (ResinItem.Effect e : ResinItem.Effect.values()) {
            if (e.id().equals(id)) {
                return e;
            }
        }
        return null;
    }

    /** Verdant / Ore-Salting transform of the to-be-placed state (pre-setBlock). */
    private BlockState applyPlacementResin(ServerLevel serverLevel, BlockState placedState) {
        if (armedResinEffect == ResinItem.Effect.VERDANT) {
            return ResinEffects.matureState(placedState, armedResinTier);
        }
        if (armedResinEffect == ResinItem.Effect.ORE_SALTING
                && saltedThisJob < MC3DPrintConfig.RESIN_ORE_SALT_MAX.get()
                && ResinEffects.isSaltableHost(placedState)
                && serverLevel.getRandom().nextDouble() < MC3DPrintConfig.RESIN_ORE_SALT_CHANCE.get()) {
            saltedThisJob++;
            return ResinEffects.pickOre(placedState, serverLevel.getRandom(),
                    MC3DPrintConfig.RESIN_ORE_SALT_GEM_SHARE.get());
        }
        return placedState;
    }

    /** Treasure / Quartermaster injection into a freshly-placed container BE. */
    private void applyContainerResin(ServerLevel serverLevel, BlockPos worldPos, BlockEntity placedBe) {
        if (armedResinEffect == ResinItem.Effect.TREASURE) {
            int cap = armedResinTier >= 3 ? MC3DPrintConfig.RESIN_TREASURE_CAP_T3.get()
                    : MC3DPrintConfig.RESIN_TREASURE_CAP_T2.get();
            double chance = armedResinTier >= 3 ? MC3DPrintConfig.RESIN_TREASURE_CHANCE_T3.get()
                    : MC3DPrintConfig.RESIN_TREASURE_CHANCE_T2.get();
            if (treasureThisJob < cap && ResinEffects.isStorageContainer(placedBe)
                    && placedBe instanceof Container container
                    && serverLevel.getRandom().nextDouble() < chance) {
                ResinEffects.fillTreasure(serverLevel, worldPos, container,
                        ResinEffects.treasureTable(armedResinTier, serverLevel.getRandom(),
                                MC3DPrintConfig.RESIN_TREASURE_T2_UNCOMMON.get(),
                                MC3DPrintConfig.RESIN_TREASURE_T3_RARE.get()));
                treasureThisJob++;
            }
        } else if (armedResinEffect == ResinItem.Effect.QUARTERMASTER) {
            if (placedBe instanceof AbstractFurnaceBlockEntity furnace && qmFurnaceRemaining > 0) {
                // hand this furnace its even share of the shared coal-block budget
                int give = (int) Math.ceil((double) qmCoalRemaining / qmFurnaceRemaining);
                ResinEffects.quartermasterFurnace(furnace, give);
                qmCoalRemaining = Math.max(0, qmCoalRemaining - give);
                qmFurnaceRemaining--;
            } else if (placedBe instanceof BrewingStandBlockEntity stand) {
                ResinEffects.quartermasterBrewing(stand);
            } else if ((placedBe instanceof ChestBlockEntity || placedBe instanceof BarrelBlockEntity)
                    && qmStorageRemaining > 0 && placedBe instanceof Container storage) {
                int bread = (int) Math.ceil((double) qmFoodRemaining / qmStorageRemaining);
                int torches = (int) Math.ceil((double) qmTorchRemaining / qmStorageRemaining);
                ResinEffects.quartermasterStorage(storage, bread, torches, !qmToolsGiven,
                        serverLevel.registryAccess());
                qmFoodRemaining = Math.max(0, qmFoodRemaining - bread);
                qmTorchRemaining = Math.max(0, qmTorchRemaining - torches);
                qmStorageRemaining--;
                qmToolsGiven = true;
            }
        }
    }

    /**
     * Would the armed resin do anything on this blueprint? We refuse to arm it (so it's never
     * consumed and stays in the slot) when the build contains zero blocks the effect can touch:
     * Treasure with no containers, Ore Salting with no natural stone, Verdant with no plants,
     * Quartermaster with no furnaces/chests. The refusal is on structural impossibility ONLY,
     * never on bad RNG — a build WITH chests can still roll zero Treasure, and that's the resin
     * working as intended. XP Yield and Overdrive benefit any non-trivial print, so they always arm.
     *
     * Uses the same {@link ResinEffects} target tests as {@link BlueprintDiscItem#resinTargetMask}
     * (which stamps the disc for the GUI warning), but is tier-precise for Verdant since the actual
     * resin tier is known here. Scanning the palette is exact: a state is in it iff a block uses it.
     */
    private boolean resinWouldBenefit(ResinItem.Effect effect, int tier, Blueprint blueprint) {
        if (effect == ResinItem.Effect.XP || effect == ResinItem.Effect.OVERDRIVE) {
            return true;
        }
        for (BlueprintBlockState paletteState : blueprint.palette()) {
            BlockState state = paletteState.resolve().orElse(null);
            if (state == null) {
                continue;
            }
            boolean hit = switch (effect) {
                case VERDANT -> !ResinEffects.matureState(state, tier).equals(state);
                case TREASURE -> ResinEffects.isStorageContainerBlock(state);
                case QUARTERMASTER -> ResinEffects.isQuartermasterTargetBlock(state);
                case ORE_SALTING -> ResinEffects.isSaltableHost(state);
                default -> false;
            };
            if (hit) {
                return true;
            }
        }
        return false;
    }

    /**
     * Pre-count the print's Quartermaster targets and seed the shared budgets, so coal /
     * food / torches split evenly across however many furnaces / storage containers the
     * blueprint actually contains. Called once at job start when a Quartermaster resin is armed.
     */
    private void initQuartermasterBudget(Blueprint blueprint) {
        int paletteSize = blueprint.palette().size();
        byte[] kind = new byte[paletteSize]; // 0 other, 1 furnace, 2 storage
        for (int i = 0; i < paletteSize; i++) {
            BlockState st = blueprint.palette().get(i).resolve().orElse(null);
            if (st == null) {
                continue;
            }
            Block bl = st.getBlock();
            if (bl instanceof AbstractFurnaceBlock) {
                kind[i] = 1;
            } else if (bl instanceof ChestBlock || bl instanceof BarrelBlock) {
                kind[i] = 2;
            }
        }
        int[] counts = new int[3];
        blueprint.forEachBlock((local, paletteIndex) -> counts[kind[paletteIndex]]++);
        qmFurnaceRemaining = counts[1];
        qmStorageRemaining = counts[2];
        qmCoalRemaining = MC3DPrintConfig.RESIN_QM_COAL_BUDGET.get();
        qmFoodRemaining = MC3DPrintConfig.RESIN_QM_FOOD_BUDGET.get();
        qmTorchRemaining = MC3DPrintConfig.RESIN_QM_TORCH_BUDGET.get();
        qmToolsGiven = false;
    }

    /** True while a structure job is running or an item is mid-print. */
    public boolean isActivelyPrinting() {
        return activeJob != null || state == State.PRINTING;
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

    /**
     * True once this machine already holds the cap for that module type: the config
     * maxPerType for the four multiplier modules, and 1 for the binary Redstone Module.
     */
    public boolean upgradeTypeAtCap(UpgradeItem.Type type) {
        return upgradeCount(type) >= type.maxPerMachine();
    }

    /**
     * Installs one module from {@code held} into the first free slot. Rejected when
     * this machine already holds the per-type cap of that module (config maxPerType),
     * even if free slots remain — diversify to fill them.
     */
    public boolean installUpgrade(ItemStack held) {
        if (held.getItem() instanceof UpgradeItem upgrade && upgradeTypeAtCap(upgrade.type())) {
            return false;
        }
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
        return ItemStack.isSameItemSameComponents(output, template) && output.getCount() < output.getMaxStackSize();
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
            // Emit matter, not stored history: the FU cost is keyed on the base item only, so the
            // output must NOT carry value-bearing components (shulker container, bundle_contents,
            // enchantments, etc.) that copyWithCount would clone for free (anti-dupe).
            inventory.setStackInSlot(SLOT_OUTPUT, new ItemStack(template.getItem()));
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
                // keep error states visible until the next trigger attempt. From a
                // clean IDLE, resolve the real status (READY / NEEDS_HIGHER_TIER /
                // OBSTRUCTED) so we never advertise READY for an unprintable job.
                if (state == State.IDLE) {
                    recheckObstruction();
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
            Optional<BlockState> resolvedOpt = blueprint.palette().get(skipCandidate.paletteIndex()).resolve();
            BlockState target = resolvedOpt
                    .map(resolvedState -> resolvedState
                            .mirror(activeJob.orientation().mirror())
                            .rotate(activeJob.orientation().rotation()))
                    .orElse(null);
            // Fast-forward (no tick cost) past blocks we won't place: ones already
            // matching (repair mode), ones we can't resolve (modded world), and
            // ones this machine can't print (skipped — see canPrintBlock).
            boolean unprintable = resolvedOpt.isPresent() && !canPrintBlock(resolvedOpt.get());
            boolean alreadyPlaced = target != null
                    && serverLevel.getBlockState(worldPosFor(skipCandidate.local(), blueprint)) == target;
            if (target != null && !alreadyPlaced && !unprintable) {
                break; // a printable block that still needs placing
            }
            if (unprintable) {
                recordSkippedBlock(resolvedOpt.get());
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
            if (armedResinEffect == ResinItem.Effect.OVERDRIVE) {
                int baseFu = blockFuValue(resolved.get()).map(FuValue::fu).orElse(0);
                if (baseFu > 0) {
                    fuCost = Math.min(fuCost, ResinEffects.overdriveFloor(baseFu, armedResinTier,
                            MC3DPrintConfig.RESIN_OVERDRIVE_T3_BELOW.get()));
                }
            }
            int costTier = blockFuTier(resolved.get());
            if (affordableFu(costTier) < fuCost) {
                state = State.PAUSED_NO_FILAMENT;
                return;
            }
            BlockState placedState = resolved.get()
                    .mirror(activeJob.orientation().mirror())
                    .rotate(activeJob.orientation().rotation());
            // Free-printed crops from a PLAYER scan must plant ungrown: printing an age-7 field free
            // would be a no-grow crop/seed faucet. Official curated discs are trusted content (a
            // player can't inject a mature field into one), so they keep their authored ages.
            // Normalize before Verdant, which is the intended way to print mature. No-op for
            // non-crop states.
            if (!BlueprintDiscItem.isOfficial(disc)) {
                placedState = ResinEffects.ungrownState(placedState);
            }
            placedState = applyPlacementResin(serverLevel, placedState); // Verdant / Ore Salting
            // No water in an ultrawarm dimension: a waterlogged solid prints dry. (Pure water
            // blocks are already skipped by canPrintBlock and never reach here.)
            //? if >=1.21.11 {
            /*placedState = dewaterFor(placedState, serverLevel.environmentAttributes()
                    .getDimensionValue(net.minecraft.world.attribute.EnvironmentAttributes.WATER_EVAPORATES));
            *///?} else {
            placedState = dewaterFor(placedState, serverLevel.dimensionType().ultraWarm());
            //?}
            // Place the exact captured state WITHOUT updateShape, so two-block pieces
            // (beds, doors, tall plants) and support-dependent blocks (crops on farmland)
            // don't self-break before their partner/support lands, and the blueprint's
            // captured connections (fences, stairs, walls) are reproduced verbatim rather
            // than recomputed. Mirrors how /paste and structure blocks place.
            serverLevel.setBlock(worldPos, placedState,
                    Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS);

            CompoundTag beData = blueprint.blockEntities().get(entry.local());
            boolean containerResin = armedResinEffect == ResinItem.Effect.TREASURE
                    || armedResinEffect == ResinItem.Effect.QUARTERMASTER;
            if (beData != null || containerResin) {
                BlockEntity placedBe = serverLevel.getBlockEntity(worldPos);
                if (placedBe != null) {
                    if (beData != null) {
                        BeData.loadInto(placedBe, beData, serverLevel.registryAccess());
                    }
                    if (containerResin) {
                        applyContainerResin(serverLevel, worldPos, placedBe);
                    }
                    placedBe.setChanged();
                    // Push the block-entity data to clients now, otherwise BE-backed
                    // visuals (sign text, etc.) don't appear until the chunk reloads.
                    serverLevel.sendBlockUpdated(worldPos, placedState, placedState, Block.UPDATE_CLIENTS);
                }
            }
            energy.consume(rfPerBlock);
            drainFu(fuCost, costTier);

            // First committed placement: spend the armed resin (Q6 — locked until now,
            // unrefundable after). The effect itself is applied in Phase 4.
            if (armedResinEffect != null && !resinConsumed) {
                resins.extractItem(0, 1, false);
                resinConsumed = true;
                setChanged();
            }

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

        // tier gating: the footprint must fit this machine's print area (and every
        // material must be within tier, checked below). A too-large footprint is a
        // tier problem, not an "area too small" — point the player at the printer
        // tier that WOULD fit it (T1/T2 print no structures → smallest is T3).
        PrintOrientation orientation = currentOrientation();
        BlockPos size = orientation.transformedSize(blueprint.sizeX(), blueprint.sizeY(), blueprint.sizeZ());
        if (footprintTooLarge(size)) {
            return;
        }
        // Validate the palette: rather than refusing the whole job when one block
        // can't be printed, SKIP un-printable blocks during placement (see the
        // fast-forward above) and build the rest. Skipping never places the block,
        // so the tier / strict-mode anti-exploit gate still holds. The only hard
        // failure here is when NOTHING in the blueprint is printable.
        boolean anyPrintable = false;
        BlockState firstUnprintable = null;
        for (BlueprintBlockState paletteState : blueprint.palette()) {
            Optional<BlockState> resolvedOpt = paletteState.resolve();
            if (resolvedOpt.isEmpty()) {
                continue; // unresolvable (modded world) — skipped at placement
            }
            if (canPrintBlock(resolvedOpt.get())) {
                anyPrintable = true;
            } else if (firstUnprintable == null) {
                firstUnprintable = resolvedOpt.get();
            }
        }
        if (!anyPrintable) {
            state = State.NOT_PRINTABLE;
            String example = firstUnprintable == null ? "all blocks"
                    : net.minecraft.core.registries.BuiltInRegistries.BLOCK
                            .getKey(firstUnprintable.getBlock()).toString();
            notPrintableReason = String.format(
                    "no blocks in this blueprint are printable by a Tier %d machine "
                            + "(e.g. %s — unpriced in strict mode, or above this tier)",
                    tier.number(), example);
            return;
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

        skippedThisJob = 0;
        skippedTypesLogged.clear();
        activeJob = new PrintJob(blueprintId, blueprint.name(), origin, orientation, size, blueprint.blockCount());
        cachedBlueprint = blueprint;
        armResin(disc); // catalyze this job iff a resin is slotted and the disc is official
        if (armedResinEffect != null && !resinWouldBenefit(armedResinEffect, armedResinTier, blueprint)) {
            // The build has nothing this resin can affect — leave it in the slot, don't burn it.
            clearArmedResin();
        }
        if (armedResinEffect == ResinItem.Effect.QUARTERMASTER) {
            initQuartermasterBudget(blueprint); // pre-count furnaces/chests to split the shared kit
        }
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
        // Now that the last block has landed, recompute connection-dependent shapes
        // against the finished neighborhood (see reconcilePlacedShapes). Runs once,
        // here at real completion — not on the PAUSED_OUTPUT_FULL re-entries above.
        reconcilePlacedShapes(serverLevel, cachedBlueprint);
        spawnPrintedEntities(serverLevel, cachedBlueprint, BlueprintDiscItem.isOfficial(disc));
        recordHistory(activeJob);

        if (skippedThisJob > 0) {
            LOGGER.info("[MC3DP] Tier {} printer at {} finished a structure — skipped {} "
                    + "un-printable block(s) across {} type(s); the rest was built",
                    tier.number(), worldPosition, skippedThisJob, skippedTypesLogged.size());
        }

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
            if (armedResinEffect != null) { // this print was catalyzed by a resin
                com.pgmacdesign.mc3dprint.advancement.ModCriteria.CATALYZED_PRINT.trigger(player);
            }
        }

        // Mark official builds as printed in the Blueprint Repository library, so the
        // GUI can show a printed marker + filter. Official-only (scans aren't tracked).
        UUID printedId = BlueprintDiscItem.getBlueprintId(disc).orElse(null);
        if (printedId != null && BlueprintDiscItem.isOfficial(disc)) {
            com.pgmacdesign.mc3dprint.blueprint.repository.RepositoryIndex.markPrinted(
                    serverLevel.getServer(), player, printedId);
        }

        releaseJobResources(serverLevel);
        inventory.setStackInSlot(SLOT_OUTPUT, disc.copy());
        inventory.setStackInSlot(SLOT_TEMPLATE, ItemStack.EMPTY);
        // Only pay XP if the resin was actually consumed (a block was placed). Re-printing an
        // already-built official blueprint places nothing and never extracts the resin, so
        // without this gate it would bank XP every no-op cycle for free.
        if (armedResinEffect == ResinItem.Effect.XP && resinConsumed) {
            bankedXp += ResinEffects.bankedXpFor(BlueprintDiscItem.getPrintCost(disc), armedResinTier,
                    MC3DPrintConfig.RESIN_XP_CAP_T1.get(), MC3DPrintConfig.RESIN_XP_CAP_T2.get(),
                    MC3DPrintConfig.RESIN_XP_CAP_T3.get(), MC3DPrintConfig.RESIN_XP_REF.get());
        }
        activeJob = null;
        clearArmedResin();
        cachedBlueprint = null;
        placementOrder = null;
        lastPlacedPos = null;
        state = State.IDLE;
        setChanged();
        syncToClients();
    }

    /**
     * Spawns the blueprint's decorative entities (armor stands, item frames,
     * paintings, regular minecarts/boats) once every block has landed, so they have
     * their support. Handles every print orientation: position via
     * {@link PrintOrientation#transformPoint}, an armor-stand/cart/boat yaw via
     * vanilla {@link net.minecraft.world.entity.Entity#rotate}/{@code mirror}, and a
     * hanging frame/painting's attach block + facing via the block transform +
     * {@link net.minecraft.core.Direction} rotation.
     *
     * <p><b>Contents are official-only and affordability-gated:</b> on an official
     * disc the framed item / armor reproduce (and cost Filament Units, dropping any
     * one piece the printer can't afford); on a player-scanned disc they're stripped
     * so the entity spawns empty (anti-dupe). The base entity always spawns. A fresh
     * UUID is assigned on load (the scan stripped it), and an entity already present
     * at the target is skipped so reprint/repair never duplicates decorations.
     */
    private void spawnPrintedEntities(net.minecraft.server.level.ServerLevel level, Blueprint blueprint,
                                      boolean official) {
        java.util.List<com.pgmacdesign.mc3dprint.blueprint.BlueprintEntity> entities = blueprint.entities();
        if (entities.isEmpty()) {
            return;
        }
        PrintOrientation o = activeJob.orientation();
        BlockPos origin = activeJob.origin();
        int sx = blueprint.sizeX(), sy = blueprint.sizeY(), sz = blueprint.sizeZ();
        for (com.pgmacdesign.mc3dprint.blueprint.BlueprintEntity be : entities) {
            CompoundTag nbt = be.nbt().copy();
            boolean hanging = nbt.contains("TileX");

            double[] t = o.transformPoint(be.x(), be.y(), be.z(), sx, sz);
            double wx = origin.getX() + t[0];
            double wy = origin.getY() + t[1];
            double wz = origin.getZ() + t[2];

            if (hanging) { // re-anchor attach block (local → oriented → world) + rotate facing
                BlockPos ot = o.transform(new BlockPos(NbtCompat.getInt(nbt, "TileX"), NbtCompat.getInt(nbt, "TileY"),
                        NbtCompat.getInt(nbt, "TileZ")), sx, sy, sz);
                nbt.putInt("TileX", origin.getX() + ot.getX());
                nbt.putInt("TileY", origin.getY() + ot.getY());
                nbt.putInt("TileZ", origin.getZ() + ot.getZ());
                if (nbt.contains("Facing")) {
                    net.minecraft.core.Direction d =
                            net.minecraft.core.Direction.from3DDataValue(NbtCompat.getByte(nbt, "Facing"));
                    d = o.rotation().rotate(o.mirror().mirror(d)); // mirror before rotation
                    nbt.putByte("Facing", (byte) d.get3DDataValue());
                }
            }

            prepareEntityContents(nbt, official);
            chargeBestEffort(BlueprintDiscItem.entityBaseItem(nbt)); // base always charged + spawns

            net.minecraft.nbt.ListTag pos = new net.minecraft.nbt.ListTag();
            pos.add(net.minecraft.nbt.DoubleTag.valueOf(wx));
            pos.add(net.minecraft.nbt.DoubleTag.valueOf(wy));
            pos.add(net.minecraft.nbt.DoubleTag.valueOf(wz));
            nbt.put("Pos", pos);
            net.minecraft.world.entity.Entity entity =
                    //? if >=26.2 {
                    /*net.minecraft.world.entity.EntityType.loadEntityRecursive(nbt, level,
                            new net.minecraft.world.entity.EntitySpawnRequest(
                                    net.minecraft.world.entity.EntitySpawnReason.LOAD, false), e -> e);
                    *///?} elif >=1.21.5 {
                    /*net.minecraft.world.entity.EntityType.loadEntityRecursive(
                            nbt, level, net.minecraft.world.entity.EntitySpawnReason.LOAD, e -> e);
                    *///?} else {
                    net.minecraft.world.entity.EntityType.loadEntityRecursive(nbt, level, e -> e);
                    //?}
            if (entity == null) {
                continue;
            }
            if (!hanging) { // armor stand / cart / boat yaw (hanging facing already set above)
                float yaw = entity.rotate(o.rotation());
                yaw += entity.mirror(o.mirror()) - entity.getYRot();
                //? if >=1.21.5 {
                /*entity.snapTo(wx, wy, wz, yaw, entity.getXRot());
                *///?} else {
                entity.moveTo(wx, wy, wz, yaw, entity.getXRot());
                //?}
            }
            // Dedup: skip if a same-type entity already occupies this spot (reprint/repair).
            net.minecraft.world.phys.AABB box = entity.getBoundingBox().inflate(0.3);
            if (!level.getEntities(entity, box, e -> e.getType() == entity.getType()).isEmpty()) {
                continue;
            }
            level.addFreshEntity(entity);
        }
    }

    /**
     * On an official disc, charges (and keeps) the entity's framed item + armor,
     * dropping any single piece the printer can't afford; on a non-official disc,
     * strips all contents so the entity spawns empty. Mutates {@code nbt} in place.
     */
    private void prepareEntityContents(CompoundTag nbt, boolean official) {
        if (!official) {
            nbt.remove("ArmorItems");
            nbt.remove("HandItems");
            nbt.remove("Item");
            return;
        }
        HolderLookup.Provider registries = this.level.registryAccess();
        if (nbt.contains("Item")) { // item frame's framed item
            ItemStack framed = NbtCompat.parseItemStack(registries, NbtCompat.getCompound(nbt, "Item"));
            if (!framed.isEmpty() && !chargeIfAffordable(framed)) {
                nbt.remove("Item");
            }
        }
        for (String slot : new String[]{"ArmorItems", "HandItems"}) {
            if (!nbt.contains(slot)) {
                continue;
            }
            net.minecraft.nbt.ListTag list = NbtCompat.getList(nbt, slot, net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                ItemStack stack = NbtCompat.parseItemStack(registries, NbtCompat.listGetCompound(list, i));
                if (!stack.isEmpty() && !chargeIfAffordable(stack)) {
                    list.set(i, new CompoundTag()); // unpaid → clear the slot
                }
            }
        }
    }

    /** Charges an item if the printer can pay; unvalued items are kept free (designer intent). */
    private boolean chargeIfAffordable(ItemStack stack) {
        // Wind-only items are never reproduced, even as official-blueprint entity contents
        // (an item-frame's framed item or an armor/hand slot). Returning false makes the caller
        // strip the slot, so #no_print holds across ALL print paths, not just item + block mode.
        if (stack.is(com.pgmacdesign.mc3dprint.registry.ModItemTags.NO_PRINT)) {
            return false;
        }
        Optional<FuValue> v = FuValueRegistry.valueOf(stack);
        if (v.isEmpty()) {
            return true; // no FU value → reproduce free on the (official) build
        }
        int cost = applyEfficiency(v.get().fu() * stack.getCount());
        if (affordableFu(v.get().tier()) < cost) {
            return false;
        }
        drainFu(cost, v.get().tier());
        return true;
    }

    /** Charges a (cheap, always-printed) base item for whatever filament is available. */
    private void chargeBestEffort(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        Optional<FuValue> v = FuValueRegistry.valueOf(stack);
        if (v.isPresent()) {
            drainFu(applyEfficiency(v.get().fu() * stack.getCount()), v.get().tier());
        }
    }

    /**
     * After the final block lands, recompute connection-dependent block shapes
     * against the now-complete structure. During placement every block is set with
     * {@link Block#UPDATE_KNOWN_SHAPE} so two-block pieces (beds, doors), supported
     * blocks (crops, torches), and connecting blocks don't self-break before their
     * partner/support/neighbor exists. The side effect is that connecting blocks —
     * glass panes, iron bars, fences, walls, redstone, stair corners — stay frozen
     * in their stored (usually default, unconnected) state, so a pane in a wall slot
     * renders as a floating center stub instead of spanning to the blocks beside it.
     * <p>
     * This single end-of-job pass re-derives each placed block's shape from its
     * finished neighbors — exactly what vanilla's {@code StructureTemplate.placeInWorld}
     * does after a paste. By now every partner/support is present, so doors/beds/crops
     * stay intact while panes/bars/fences connect correctly. {@code UPDATE_SUPPRESS_DROPS}
     * keeps anything that re-derives to air (it won't, since supports exist) from
     * dropping items, and {@code UPDATE_CLIENTS} pushes the corrected render.
     */
    private void reconcilePlacedShapes(ServerLevel serverLevel, Blueprint blueprint) {
        if (placementOrder == null || blueprint == null) {
            return;
        }
        for (PlacementEntry entry : placementOrder) {
            BlockPos pos = worldPosFor(entry.local(), blueprint);
            BlockState current = serverLevel.getBlockState(pos);
            BlockState reconciled = Block.updateFromNeighbourShapes(current, serverLevel, pos);
            // Connecting blocks reconcile to their connected shape; an UNSUPPORTED
            // attachment (a torch/ladder/lantern with no backing) reconciles to AIR.
            // Guard that case: we never delete a block here — a mis-supported fixture
            // is a blueprint data defect to fix at the source, not something the
            // printer should silently erase. So skip no-op and to-air results.
            if (reconciled != current && !reconciled.isAir()) {
                serverLevel.setBlock(pos, reconciled,
                        Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
            }
        }
    }

    // --- Deconstruct Mode: the printer in reverse ---
    //
    // A selected region is consumed block-by-block back into Filament Units at a
    // LOSSY rate (config yieldFactor, hard-capped < 1.0). The lifecycle mirrors
    // printing — same cadence, RF gate, zone claim, chunk tickets, pause states —
    // but runs TOP-DOWN so supported blocks come off before their supports.

    /** Region hand-off outcome, surfaced to the scanner's action bar. */
    public enum RegionResult { SET, TOO_LARGE, TOO_FAR }

    /** Corners of the armed region may sit at most this far from the machine. */
    public static final int DECON_MAX_DISTANCE = 64;

    /**
     * Arms a deconstruct region from a scanner two-corner selection and switches
     * the machine into Deconstruct Mode. Refuses regions wider than this tier's
     * print footprint (a machine can un-print exactly what it could print) or
     * farther than {@link #DECON_MAX_DISTANCE} from the machine.
     */
    public RegionResult setDeconstructRegion(BlockPos a, BlockPos b) {
        BlockPos min = new BlockPos(Math.min(a.getX(), b.getX()),
                Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
        BlockPos size = new BlockPos(Math.abs(a.getX() - b.getX()) + 1,
                Math.abs(a.getY() - b.getY()) + 1, Math.abs(a.getZ() - b.getZ()) + 1);
        if (Math.max(size.getX(), size.getZ()) > MC3DPrintConfig.maxFootprint(tier)) {
            return RegionResult.TOO_LARGE;
        }
        BlockPos max = min.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1);
        if (chebyshev(worldPosition, min) > DECON_MAX_DISTANCE
                || chebyshev(worldPosition, max) > DECON_MAX_DISTANCE) {
            return RegionResult.TOO_FAR;
        }
        cancelActiveJob(); // region epoch: an in-flight job never crosses onto a new region
        startRequested = false; // ...and neither does a pending Start trigger
        deconstructMin = min;
        deconstructSize = size;
        deconstructMode = true;
        deconArmedRequiresStart = true; // fresh region: first job is manual-start only
        state = State.IDLE;
        setChanged();
        syncToClients();
        return RegionResult.SET;
    }

    @Nullable
    public BlockPos deconstructRegionMin() {
        return deconstructMin;
    }

    @Nullable
    public BlockPos deconstructRegionSize() {
        return deconstructSize;
    }

    private static int chebyshev(BlockPos a, BlockPos b) {
        return Math.max(Math.abs(a.getX() - b.getX()),
                Math.max(Math.abs(a.getY() - b.getY()), Math.abs(a.getZ() - b.getZ())));
    }

    public boolean deconstructMode() {
        return deconstructMode;
    }

    /** Toggles Print/Deconstruct. Any in-flight job of either kind is cancelled (mode epoch). */
    public void setDeconstructMode(boolean value) {
        if (deconstructMode == value) {
            return;
        }
        deconstructMode = value;
        if (value) {
            deconArmedRequiresStart = true; // entering Decon re-arms the manual-start gate
        }
        startRequested = false; // a pending Start never crosses a mode switch
        cancelActiveJob();
        state = State.IDLE;
        setChanged();
        syncToClients();
    }

    @Nullable
    public DeconstructJob deconstructJob() {
        return deconstructJob;
    }

    private void tickDeconstructMode() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (deconstructJob == null) {
            // Auto is honored only after the first explicit Start on a freshly armed
            // region — see deconArmedRequiresStart.
            boolean autoAllowed = autoStart && !deconArmedRequiresStart;
            if (!autoAllowed && !startRequested) {
                if (state == State.IDLE || state == State.DECONSTRUCTING) {
                    state = deconstructMin != null ? State.READY : State.IDLE;
                }
                return;
            }
            if (retryCooldown > 0) {
                retryCooldown--;
                return;
            }
            retryCooldown = 20;
            if (startRequested && deconArmedRequiresStart) {
                deconArmedRequiresStart = false; // the one explicit Start disarms the gate
                setChanged();
            }
            startRequested = false;
            tryStartDeconstruct(serverLevel);
            return;
        }
        if (deconstructJob.isComplete()) {
            finishDeconstruct(serverLevel);
            return;
        }

        placementCooldown++;
        if (placementCooldown < speedAdjusted(MC3DPrintConfig.ticksPerBlock(tier))) {
            if (state == State.IDLE) {
                state = State.DECONSTRUCTING;
            }
            return;
        }
        int rfPerBlock = rfAdjusted(MC3DPrintConfig.rfPerBlock(tier));
        if (!energy.hasAtLeast(rfPerBlock)) {
            state = State.PAUSED_NO_POWER;
            return;
        }

        // Fast-forward (no tick cost) past air and skip-in-place positions. Each is
        // processed exactly once — progress is monotonic, so nothing is revisited.
        BlockPos pos = null;
        DeconstructYield yield = null;
        while (!deconstructJob.isComplete()) {
            BlockPos candidate = deconstructJob.posFor(deconstructJob.progress());
            DeconstructYield classified = classifyForDeconstruct(serverLevel, candidate,
                    serverLevel.getBlockState(candidate));
            if (classified.removable()) {
                pos = candidate;
                yield = classified;
                break;
            }
            deconstructJob.advance();
        }
        if (deconstructJob.isComplete()) {
            setChanged();
            finishDeconstruct(serverLevel);
            return;
        }

        // Halt BEFORE removing when the yield has nowhere to go — filament is never voided.
        if (yield.fu() > 0 && insertableFuFor(yield.tier()) < yield.fu()) {
            state = State.PAUSED_OUTPUT_FULL;
            return;
        }

        energy.consume(rfPerBlock);
        if (yield.fu() > 0) {
            creditFu(yield.fu(), yield.tier());
        }
        serverLevel.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS);
        deconstructJob.recordRemoval(yield.fu());
        deconstructJob.advance();

        // un-zap: the head fires and the block dissolves
        serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                12, 0.3, 0.3, 0.3, 0.05);
        serverLevel.playSound(null, pos, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_BREAK,
                net.minecraft.sounds.SoundSource.BLOCKS, 0.6F, 0.8F);
        lastPlacedPos = pos.immutable(); // drives the gantry/beam render
        placementCooldown = 0;
        state = State.DECONSTRUCTING;
        setChanged();
        syncToClients();
    }

    private void tryStartDeconstruct(ServerLevel serverLevel) {
        if (deconstructMin == null || deconstructSize == null) {
            state = State.NOT_PRINTABLE;
            notPrintableReason = "no deconstruct region set — sneak-click this machine "
                    + "with a Structure Scanner holding a two-corner selection";
            return;
        }
        if (footprintTooLarge(deconstructSize)) {
            return; // sets NEEDS_HIGHER_TIER / AREA_TOO_SMALL + requiredTier
        }
        BoundingBox box = deconstructBox(deconstructMin, deconstructSize);
        if (!PrintZoneManager.claim(serverLevel, worldPosition, box)) {
            state = State.ZONE_CONFLICT;
            return;
        }
        deconstructJob = new DeconstructJob(deconstructMin, deconstructSize);
        placementCooldown = 0;
        forceChunks(serverLevel, box, true);
        state = State.DECONSTRUCTING;
        setChanged();
        syncToClients();
    }

    private void finishDeconstruct(ServerLevel serverLevel) {
        PrintZoneManager.release(serverLevel, worldPosition);
        forceChunks(serverLevel, deconstructBox(deconstructJob.min(), deconstructJob.size()), false);
        recordDeconstructHistory(deconstructJob);
        serverLevel.playSound(null, worldPosition, net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP,
                net.minecraft.sounds.SoundSource.BLOCKS, 0.5F, 0.8F);
        deconstructJob = null;
        lastPlacedPos = null;
        state = State.IDLE;
        setChanged();
        syncToClients();
    }

    private static BoundingBox deconstructBox(BlockPos min, BlockPos size) {
        return BoundingBox.fromCorners(min,
                min.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1));
    }

    /**
     * Classification of one region position. {@code removable=false} means skip in
     * place (never machine-destroyed): unbreakables, non-empty containers, and
     * unvalued blocks. Removable-at-zero covers itemless structural blocks (water,
     * crops, fire) and winder-blacklisted items — both yield exactly 0 FU.
     */
    private record DeconstructYield(boolean removable, int fu, int tier) {
        static final DeconstructYield SKIP = new DeconstructYield(false, 0, 1);
        static final DeconstructYield FREE = new DeconstructYield(true, 0, 1);
    }

    /** The upper door/tall-plant half or the bed head: the piece that shares its partner's item. */
    private static boolean isSecondaryDoubleHalf(BlockState state) {
        if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)
                && state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)
                        == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER) {
            return true;
        }
        return state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.BED_PART)
                && state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.BED_PART)
                        == net.minecraft.world.level.block.state.properties.BedPart.HEAD;
    }

    private DeconstructYield classifyForDeconstruct(ServerLevel serverLevel, BlockPos pos, BlockState existing) {
        if (existing.isAir()) {
            return DeconstructYield.SKIP;
        }
        if (existing.getDestroySpeed(serverLevel, pos) < 0) {
            return DeconstructYield.SKIP; // bedrock-class unbreakable
        }
        BlockEntity be = serverLevel.getBlockEntity(pos);
        if (be instanceof net.minecraft.world.Container container && !container.isEmpty()) {
            return DeconstructYield.SKIP; // never delete or eject items
        }
        Item item = existing.getBlock().asItem();
        if (item == Items.AIR) {
            return DeconstructYield.FREE; // itemless structural — symmetric with printing free
        }
        // Two-block pieces (beds, doors, tall plants) resolve BOTH halves to the same item, so
        // pricing each half would double-credit its yield. Yield on the primary half only; the
        // secondary half removes for zero. (Print-side likewise charges per half, unchanged.)
        if (isSecondaryDoubleHalf(existing)) {
            return DeconstructYield.FREE;
        }
        ItemStack stack = new ItemStack(item);
        Optional<FuValue> value = FuValueRegistry.valueOf(stack);
        if (value.isEmpty()) {
            return DeconstructYield.SKIP; // unvalued (strict-mode rare) — never destroyed for 0
        }
        if (com.pgmacdesign.mc3dprint.registry.ModItemTags.isWinderBlacklisted(stack)) {
            return DeconstructYield.FREE; // anti-laundering tag: removable, zero yield
        }
        // Yield derives from the WIND value only — Efficiency modules and resins never
        // touch it, so wind -> print -> deconstruct stays strictly FU-negative.
        int fu = (int) Math.floor(value.get().fu() * MC3DPrintConfig.DECONSTRUCT_YIELD_FACTOR.get());
        return new DeconstructYield(true, fu, value.get().tier());
    }

    /** Reverse of {@link #drainFu}: banks tier-unit FU docked-spools-first, then the network. */
    private void creditFu(int amount, int tier) {
        int ratio = FuConversion.ratio();
        long remainingBase = FuConversion.toBase(amount, tier, ratio);
        remainingBase -= FilamentDrain.fillTier(spools, remainingBase, tier, ratio);
        if (remainingBase > 0) {
            for (IFilamentSource src : reachableSources()) {
                if (remainingBase <= 0) {
                    break;
                }
                remainingBase -= src.insertExactTier(tier, remainingBase);
            }
        }
    }

    /** Tier-unit FU of free exact-tier capacity across docked spools + the network. */
    private int insertableFuFor(int tier) {
        int ratio = FuConversion.ratio();
        long base = FilamentDrain.insertableTier(spools, tier, ratio);
        for (IFilamentSource src : reachableSources()) {
            base += src.insertableExactTier(tier);
        }
        return FuConversion.clampToInt(FuConversion.fromBase(base, tier, ratio));
    }

    private void recordDeconstructHistory(DeconstructJob job) {
        CompoundTag entry = new CompoundTag();
        entry.putString("Name", "Deconstruct (" + job.removed() + " blocks)");
        entry.putInt("Blocks", job.removed());
        entry.putLong("Time", level != null ? level.getGameTime() : 0);
        history.add(0, entry);
        int max = MC3DPrintConfig.PRINT_HISTORY_SIZE.get();
        while (history.size() > max) {
            history.remove(history.size() - 1);
        }
    }

    public void cancelActiveJob() {
        if ((activeJob != null || deconstructJob != null) && level instanceof ServerLevel serverLevel) {
            releaseJobResources(serverLevel);
        }
        // No rollback by design: blocks already placed/removed stay, FU spent/credited stays.
        activeJob = null;
        deconstructJob = null;
        clearArmedResin();
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
        if (deconstructJob != null) {
            forceChunks(serverLevel, deconstructBox(deconstructJob.min(), deconstructJob.size()), false);
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
                // NeoForge dropped Forge's ticket-owner force API; vanilla setChunkForced
                // keeps the print zone loaded for the same job duration.
                serverLevel.setChunkForced(cx, cz, add);
            }
        }
        // The MACHINE's own chunk too: build offsets (±32) or a deconstruct region
        // (up to 64 away) can put the job box in different chunks than the machine —
        // a region kept loaded while the machine that works it unloads would stall
        // the job (and never self-recover after a restart). Idempotent when the
        // machine already sits inside the box footprint.
        serverLevel.setChunkForced(worldPosition.getX() >> 4, worldPosition.getZ() >> 4, add);
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
        // Solids place bottom-up (Y/Z/X) so supports exist before supported blocks; pure
        // liquids (water/lava sources) place LAST, after every solid neighbour is down.
        // Otherwise a source set in the middle of a print flows into cells that haven't
        // printed yet — e.g. the wheat farm's centre water flooding the whole field
        // before its surrounding farmland lands, leaving a pool with no farmland/crops.
        // Placing liquids last lets the field enclose the source first, so it stays put.
        // Waterlogged solids aren't LiquidBlock, so they keep their normal bottom-up slot.
        List<PlacementEntry> solids = new ArrayList<>(blueprint.blockCount());
        List<PlacementEntry> liquids = new ArrayList<>();
        blueprint.forEachBlock((local, paletteIndex) -> {
            PlacementEntry entry = new PlacementEntry(local.immutable(), paletteIndex);
            BlockState state = blueprint.palette().get(paletteIndex).resolve().orElse(null);
            if (state != null
                    && state.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock) {
                liquids.add(entry);
            } else {
                solids.add(entry);
            }
        });
        solids.addAll(liquids);
        return solids;
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

    /** Tier a {@code NEEDS_HIGHER_TIER} state points at; 0 when not applicable. */
    public int requiredTier() {
        return requiredTier;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        // re-claim zone + chunk tickets for a job restored from disk
        if (activeJob != null && level instanceof ServerLevel serverLevel) {
            PrintZoneManager.claim(serverLevel, worldPosition, jobBox());
            forceChunks(serverLevel, jobBox(), true);
        }
        if (deconstructJob != null && level instanceof ServerLevel serverLevel) {
            BoundingBox box = deconstructBox(deconstructJob.min(), deconstructJob.size());
            PrintZoneManager.claim(serverLevel, worldPosition, box);
            forceChunks(serverLevel, box, true);
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
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (activeJob != null) {
            tag.put("ActiveJob", activeJob.save());
        }
        if (lastPlacedPos != null) {
            NbtCompat.putBlockPos(tag, "LastPlaced", lastPlacedPos);
        }
        tag.putInt("State", state.ordinal());

        if (previewEnabled && activeJob == null && level instanceof ServerLevel serverLevel) {
            // Preview can be toggled on with no disc loaded — that's fine, there's
            // just nothing to ghost-render. Only emit Preview data when a disc with
            // a within-cap blueprint is present; otherwise the toggle stays on but
            // the renderer draws nothing (it early-returns on empty preview data).
            ItemStack template = inventory.getStackInSlot(SLOT_TEMPLATE);
            UUID id = isLoadedDisc(template) ? BlueprintDiscItem.getBlueprintId(template).orElse(null) : null;
            if (id == null) {
                // no blueprint to show — clear stale cache, keep the toggle on
                previewBlueprint = null;
                previewBlueprintId = null;
            } else {
                if (previewBlueprint == null || !id.equals(previewBlueprintId)) {
                    previewBlueprint = BlueprintFileStore.forServer(serverLevel.getServer()).load(id).orElse(null);
                    previewBlueprintId = id;
                }
                if (previewBlueprint != null
                        && previewBlueprint.blockCount() <= MC3DPrintConfig.PREVIEW_MAX_BLOCKS.get()) {
                    BlockPos size = currentOrientation().transformedSize(previewBlueprint.sizeX(),
                            previewBlueprint.sizeY(), previewBlueprint.sizeZ());
                    BlockPos origin = worldPosition.offset(
                            -(size.getX() / 2) + offsetX, 1 + offsetY, -(size.getZ() / 2) + offsetZ);
                    tag.put("Preview", com.pgmacdesign.mc3dprint.blueprint.BlueprintSerializer.write(previewBlueprint));
                    NbtCompat.putBlockPos(tag, "PreviewOrigin", origin);
                    tag.putInt("PreviewRotation", rotation.ordinal());
                    // Per-palette-entry printability mask (1 = this machine will print it). The
                    // ghost must show only what actually prints — printability (FU value/tier,
                    // strict mode) is authoritative on the server, so compute it here rather than
                    // re-deriving it on a client whose FU registry may lag recipe derivation.
                    List<BlueprintBlockState> palette = previewBlueprint.palette();
                    int[] printable = new int[palette.size()];
                    for (int i = 0; i < palette.size(); i++) {
                        printable[i] = palette.get(i).resolve().map(this::canPrintBlock).orElse(false) ? 1 : 0;
                    }
                    tag.putIntArray("PreviewPrintable", printable);
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

        // Recent print history for the GUI tooltip — newest first, capped small so the
        // update packet stays lean regardless of the printHistorySize config.
        ListTag historyList = new ListTag();
        for (int i = 0; i < history.size() && i < HISTORY_SYNC_CAP; i++) {
            historyList.add(history.get(i).copy());
        }
        tag.put("History", historyList);

        // Armed deconstruct region -> client, so the renderer can draw the red hazard box.
        tag.putBoolean("DeconOn", deconstructMode);
        if (deconstructMode && deconstructMin != null && deconstructSize != null) {
            NbtCompat.putBlockPos(tag, "DeconMin", deconstructMin);
            NbtCompat.putBlockPos(tag, "DeconSize", deconstructSize);
        }
        // Nest under one key so the client side can recover the whole payload as a CompoundTag:
        // 1.21.5's handleUpdateTag receives a ValueInput (no backing-tag accessor), so we read
        // "D" back via CompoundTag.CODEC and reuse the version-agnostic applyUpdateData body.
        CompoundTag root = new CompoundTag();
        root.put("D", tag);
        return root;
    }

    //? if >=1.21.5 {
    /*@Override
    public void handleUpdateTag(net.minecraft.world.level.storage.ValueInput in) {
        in.read("D", CompoundTag.CODEC).ifPresent(this::applyUpdateData);
    }
    *///?} else {
    @Override
    public void handleUpdateTag(CompoundTag root, HolderLookup.Provider registries) {
        applyUpdateData(NbtCompat.getCompound(root, "D"));
    }
    //?}

    private void applyUpdateData(CompoundTag tag) {
        activeJob = NbtCompat.contains(tag, "ActiveJob") ? PrintJob.load(NbtCompat.getCompound(tag, "ActiveJob")) : null;
        lastPlacedPos = NbtCompat.getBlockPos(tag, "LastPlaced").orElse(null);
        state = State.byOrdinal(NbtCompat.getInt(tag, "State"));

        deconstructMode = NbtCompat.getBoolean(tag, "DeconOn");
        deconstructMin = NbtCompat.getBlockPos(tag, "DeconMin").orElse(null);
        deconstructSize = NbtCompat.getBlockPos(tag, "DeconSize").orElse(null);

        // client mirror of the recent-history slice (the client BE never loads full NBT)
        history.clear();
        ListTag historyList = NbtCompat.getList(tag, "History", Tag.TAG_COMPOUND);
        for (int i = 0; i < historyList.size(); i++) {
            history.add(NbtCompat.listGetCompound(historyList, i).copy());
        }

        clientPreviewOn = NbtCompat.getBoolean(tag, "PreviewOn");
        clientPreview.clear();
        clientPreviewOrigin = null;
        clientPreviewSize = null;
        Optional<BlockPos> previewOriginOpt = NbtCompat.getBlockPos(tag, "PreviewOrigin");
        if (NbtCompat.contains(tag, "Preview") && previewOriginOpt.isPresent()) {
            Blueprint blueprint = com.pgmacdesign.mc3dprint.blueprint.BlueprintSerializer
                    .read(NbtCompat.getCompound(tag, "Preview"));
            BlockPos origin = previewOriginOpt.get();
            // Apply the same orientation the server will print at, so the ghost matches:
            // transform each local position AND rotate the block state (stairs/doors/…).
            Rotation rot = Rotation.values()[NbtCompat.getInt(tag, "PreviewRotation") % Rotation.values().length];
            PrintOrientation o = new PrintOrientation(rot, Mirror.NONE);
            int sx = blueprint.sizeX(), sy = blueprint.sizeY(), sz = blueprint.sizeZ();
            clientPreviewOrigin = origin;
            clientPreviewSize = o.transformedSize(sx, sy, sz);
            // Server-computed printability mask: skip palette entries this machine won't
            // print so the ghost never advertises a block (dragon egg, over-tier, …) that
            // the print silently omits. Absent/mismatched mask → show everything (safe).
            int[] printable = NbtCompat.getIntArray(tag, "PreviewPrintable");
            boolean hasMask = printable.length == blueprint.palette().size();
            blueprint.forEachBlock((local, paletteIndex) -> {
                if (hasMask && printable[paletteIndex] == 0) {
                    return;
                }
                blueprint.palette().get(paletteIndex).resolve().ifPresent(resolvedState ->
                        clientPreview.add(new PreviewBlock(
                                origin.offset(o.transform(local, sx, sy, sz)),
                                resolvedState.mirror(o.mirror()).rotate(o.rotation()))));
            });
        }

        clientSpools.clear();
        ListTag spoolList = NbtCompat.getList(tag, "Spools", Tag.TAG_COMPOUND);
        for (int i = 0; i < spoolList.size(); i++) {
            CompoundTag entry = NbtCompat.listGetCompound(spoolList, i);
            clientSpools.add(entry.contains("Tier")
                    ? new SpoolRenderInfo(NbtCompat.getInt(entry, "Tier"), NbtCompat.getFloat(entry, "Fill"), NbtCompat.getBoolean(entry, "Creative"))
                    : null);
        }
    }

    @Nullable
    @Override
    public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this,
                (be, registryAccess) -> ((PrinterBlockEntity) be).getUpdateTag(registryAccess));
    }

    //? if >=1.21.5 {
    /*@Override
    public void onDataPacket(net.minecraft.network.Connection connection,
                             net.minecraft.world.level.storage.ValueInput input) {
        handleUpdateTag(input);
    }
    *///?} else {
    @Override
    public void onDataPacket(net.minecraft.network.Connection connection,
                             net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket packet,
                             HolderLookup.Provider registries) {
        if (packet.getTag() != null) {
            handleUpdateTag(packet.getTag(), registries);
        }
    }
    //?}

    // TODO(PGM-17): NeoForge moved getRenderBoundingBox to BlockEntityRenderer<T>; the
    // PrinterRenderer (client/) must override getRenderBoundingBox(PrinterBlockEntity) to
    // return this. Kept as a plain accessor so the cull-box logic is preserved here.
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        // The renderer draws well outside the machine's own block: a build-volume gantry
        // during a job, a ghost preview, and — for a formed multiblock — a raised frame
        // of posts/gantry plus perimeter spool reels EVERY frame, idle or not. This cull
        // box must enclose all of it; if it doesn't, the whole render is culled the moment
        // this box leaves the view frustum — which is exactly when you look straight at the
        // tall parts and the machine's own 1-block AABB drops out of view (the reported
        // "gantry/bars vanish when looked at directly, fine in peripheral vision").
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(worldPosition);
        if (activeJob != null) {
            BlockPos size = activeJob.size();
            // +1 on Y: the gantry/head ride just above the volume's top (gantryY = maxY+0.25)
            box = box.minmax(new net.minecraft.world.phys.AABB(
                    net.minecraft.world.phys.Vec3.atLowerCornerOf(activeJob.origin()),
                    net.minecraft.world.phys.Vec3.atLowerCornerOf(
                            activeJob.origin().offset(size.getX(), size.getY() + 1, size.getZ()))));
        } else if (clientPreviewOrigin != null && clientPreviewSize != null) {
            box = box.minmax(new net.minecraft.world.phys.AABB(
                    net.minecraft.world.phys.Vec3.atLowerCornerOf(clientPreviewOrigin),
                    net.minecraft.world.phys.Vec3.atLowerCornerOf(
                            clientPreviewOrigin.offset(clientPreviewSize))));
        }
        if (deconstructMode && deconstructMin != null && deconstructSize != null) {
            // the red hazard wireframe draws over the whole armed region
            box = box.minmax(new net.minecraft.world.phys.AABB(
                    net.minecraft.world.phys.Vec3.atLowerCornerOf(deconstructMin),
                    net.minecraft.world.phys.Vec3.atLowerCornerOf(
                            deconstructMin.offset(deconstructSize))));
        }
        var blockState = getBlockState();
        if (blockState.hasProperty(com.pgmacdesign.mc3dprint.machine.multiblock.ControllerBlock.FORMED)
                && blockState.getValue(com.pgmacdesign.mc3dprint.machine.multiblock.ControllerBlock.FORMED)) {
            // Mirrors PrinterRenderer.renderFormedStructure: posts span +/-half blocks out
            // and rise to topY = 3 + half; spool reels push to the same perimeter. Cover it
            // with a block of headroom to spare.
            int half = com.pgmacdesign.mc3dprint.machine.multiblock.MultiblockPattern.baseEdge(tier()) / 2;
            box = box.minmax(new net.minecraft.world.phys.AABB(
                    worldPosition.getX() - half, worldPosition.getY(), worldPosition.getZ() - half,
                    worldPosition.getX() + half + 1, worldPosition.getY() + 4.0 + half,
                    worldPosition.getZ() + half + 1));
        }
        return box;
    }

    private void syncToClients() {
        if (level != null && !level.isClientSide()) {
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
        boolean rising = powered && !lastRedstoneSignal;
        // Record first, act second. While a Redstone Module machine is broadcasting, a
        // rising edge may well be its OWN output fed back through adjacent dust, so it
        // never queues a start: the self-caused edge would set startRequested, that flag
        // outlives the job, and the print would restart forever. The recording is never
        // skipped, or the field would go stale-false and the first update after the
        // emission drops would read as a fresh rising edge, rebuilding the loop through
        // any held source. Cost: on such a machine a pulse arriving mid-job is ignored
        // rather than queued as a re-run.
        lastRedstoneSignal = powered;
        if (rising && !shouldEmitRedstone()) {
            requestStart();
        }
    }

    /**
     * True while this machine is doing work its Redstone Module should broadcast: a
     * module is installed, a multiblock is formed, and a print or deconstruct is
     * actually advancing. Every paused, error and ready state reads false deliberately,
     * so the signal answers "busy right now" rather than "a job is loaded" and a stall
     * shows up as the signal dropping.
     */
    public boolean shouldEmitRedstone() {
        if (upgradeCount(UpgradeItem.Type.REDSTONE) == 0) {
            return false;
        }
        BlockState blockState = getBlockState();
        if (blockState.hasProperty(com.pgmacdesign.mc3dprint.machine.multiblock.ControllerBlock.FORMED)
                && !blockState.getValue(com.pgmacdesign.mc3dprint.machine.multiblock.ControllerBlock.FORMED)) {
            return false;
        }
        return state == State.PRINTING || state == State.DECONSTRUCTING;
    }

    /**
     * Comparator reading: 0 when nothing is running, otherwise 1 to 15 scaled by how
     * far the current job has got. The 0-versus-1 split is the point: a comparator
     * must be able to tell "idle" from "running but barely started", which a plain
     * {@code round(15 * fraction)} cannot do.
     *
     * <p>Deliberately ungated: unlike the emitted busy signal this needs no Redstone
     * Module, matching the Filament Rack's free fill reading.
     *
     * <p>A job is torn down in the same tick its last unit of work lands (a blueprint
     * print calls tryFinishJob straight after the final placement, and itemProgress is
     * reset the moment it reaches maxProgress), so {@code done == total} is never
     * observable from outside. {@link #scaleProgress} scales over the range that IS
     * observable, which is what makes 15 reachable at all.
     */
    public int comparatorProgress() {
        BlockState blockState = getBlockState();
        if (blockState.hasProperty(com.pgmacdesign.mc3dprint.machine.multiblock.ControllerBlock.FORMED)
                && !blockState.getValue(com.pgmacdesign.mc3dprint.machine.multiblock.ControllerBlock.FORMED)) {
            return 0;
        }
        // Every live-work branch floors at 1. 0 is reserved for "nothing loaded, nothing
        // to do", so a contraption can treat 0 as idle in all three modes rather than
        // mostly-idle. Keying Item Mode on itemProgress alone used to break that twice:
        // the counter is reset inside the tick an item completes (while the machine is
        // still PRINTING and writing the output slot, so a comparator really did observe
        // the 0), and a machine paused on a full output sits at 0 with work still loaded.
        if (deconstructJob != null) {
            return Math.max(1, scaleProgress(deconstructJob.progress(), deconstructJob.totalPositions()));
        }
        if (activeJob != null) {
            return Math.max(1, scaleProgress(activeJob.placed(), activeJob.totalBlocks()));
        }
        if (hasItemWorkLoaded()) {
            return Math.max(1, scaleProgress(itemProgress, maxProgress()));
        }
        return 0;
    }

    /**
     * Item Mode has no job object, so "is there work loaded" has to be read off the
     * machine state instead. PRINTING and the PAUSED_* states both mean an item is
     * in flight or blocked with the template still in the slot.
     *
     * <p>IDLE and READY deliberately do NOT count: a machine holding a template it has
     * not been told to print reads 0, same as an empty one. Blueprint and Deconstruct
     * work is already covered by the job branches above, so this only answers for a
     * plain item template.
     */
    private boolean hasItemWorkLoaded() {
        ItemStack template = inventory.getStackInSlot(SLOT_TEMPLATE);
        if (template.isEmpty() || deconstructMode || isLoadedDisc(template)) {
            return false;
        }
        return switch (state) {
            case PRINTING, PAUSED_NO_POWER, PAUSED_OUTPUT_FULL, PAUSED_OBSTRUCTED, PAUSED_NO_FILAMENT -> true;
            default -> false;
        };
    }

    /**
     * Maps job progress onto 1..15, reserving 0 for "nothing is running".
     *
     * <p>Divides by {@code total - 1}, not {@code total}, and that is deliberate: a job
     * is destroyed in the same tick it finishes its last unit of work, so the highest
     * value anything outside this class can ever read is {@code total - 1}. Dividing by
     * {@code total} would make 15 unreachable in every mode and silently waste the top
     * of the comparator range. Scaling over the observable range instead means a machine
     * on its final block reads 15.
     */
    private static int scaleProgress(int done, int total) {
        if (total <= 0) {
            return 0;
        }
        int lastObservable = Math.max(1, total - 1);
        double fraction = Math.min(1.0, (double) Math.max(0, done) / lastObservable);
        return Mth.clamp(1 + (int) Math.floor(14 * fraction), 1, 15);
    }

    /**
     * Writes {@link PrinterBlock#EMITTING} through to the world, but only when it
     * actually changes. That setBlock IS the neighbor update, so gating it on a real
     * transition is what stops a running machine from poking its six neighbours every
     * single tick. A tick where the predicate is unchanged does zero world work.
     */
    private void updateRedstoneOutput() {
        if (level == null || level.isClientSide()) {
            return;
        }
        BlockState blockState = getBlockState();
        if (!blockState.hasProperty(PrinterBlock.EMITTING)) {
            return;
        }
        boolean emit = shouldEmitRedstone();
        if (blockState.getValue(PrinterBlock.EMITTING) != emit) {
            level.setBlock(worldPosition, blockState.setValue(PrinterBlock.EMITTING, emit), Block.UPDATE_ALL);
        }
    }

    public int offset(int axis) {
        return switch (axis) {
            case 0 -> offsetX;
            case 1 -> offsetY;
            default -> offsetZ;
        };
    }

    /** Adjusts a build offset (0=X, 1=Y, 2=Z); takes effect on the next job. */
    /**
     * Proactively re-evaluate whether the loaded blueprint at the current offsets
     * would be obstructed, so PAUSED_OBSTRUCTED shows up BEFORE the player presses
     * Start. Called on offset changes and when the GUI opens; the build-time check
     * in tryStartJob stays the final authority. No-op unless a disc is loaded and
     * no job is active, and it only flips OBSTRUCTED<->READY so it never clobbers
     * other states (no power / no filament / printing).
     */
    public void recheckObstruction() {
        if (!(level instanceof ServerLevel serverLevel) || activeJob != null) {
            return;
        }
        ItemStack disc = inventory.getStackInSlot(SLOT_TEMPLATE);
        if (!isLoadedDisc(disc)) {
            return;
        }
        UUID blueprintId = BlueprintDiscItem.getBlueprintId(disc).orElse(null);
        if (blueprintId == null) {
            return;
        }
        Optional<Blueprint> loaded = BlueprintFileStore.forServer(serverLevel.getServer()).load(blueprintId);
        if (loaded.isEmpty()) {
            return;
        }
        Blueprint blueprint = loaded.get();
        PrintOrientation orientation = currentOrientation();
        BlockPos size = orientation.transformedSize(blueprint.sizeX(), blueprint.sizeY(), blueprint.sizeZ());
        State previous = state;
        // Surface a footprint/tier mismatch the moment a disc loads (or the build is
        // rotated/offset) instead of waiting for Start — so the GUI never shows READY
        // for a job that can't print here. T1/T2 (zero footprint) fall through here too.
        if (footprintTooLarge(size)) {
            if (state != previous) {
                setChanged();
                syncToClients();
            }
            return;
        }
        // Footprint fits now (e.g. rotated to a smaller profile) — clear any stale
        // tier error before re-checking obstruction.
        if (state == State.NEEDS_HIGHER_TIER || state == State.AREA_TOO_SMALL) {
            state = State.IDLE;
        }
        BlockPos origin = worldPosition.offset(
                -(size.getX() / 2) + offsetX, 1 + offsetY, -(size.getZ() / 2) + offsetZ);
        boolean clear = isAreaClear(serverLevel, blueprint, orientation, origin);
        if (!clear) {
            state = State.PAUSED_OBSTRUCTED;
        } else if (state == State.PAUSED_OBSTRUCTED || state == State.IDLE) {
            state = State.READY;
        }
        if (state != previous) {
            setChanged();
            syncToClients();
        }
    }

    public void adjustOffset(int axis, int delta) {
        switch (axis) {
            case 0 -> offsetX = Mth.clamp(offsetX + delta, -MAX_OFFSET, MAX_OFFSET);
            case 1 -> offsetY = Mth.clamp(offsetY + delta, -MAX_OFFSET, MAX_OFFSET);
            default -> offsetZ = Mth.clamp(offsetZ + delta, -MAX_OFFSET, MAX_OFFSET);
        }
        setChanged();
        recheckObstruction(); // surface obstruction the moment the build area moves
        if (previewEnabled) {
            syncToClients(); // the ghost follows the offsets live
        }
    }

    /** Current print orientation: the persisted Y-rotation, no mirror. */
    private PrintOrientation currentOrientation() {
        return new PrintOrientation(rotation, Mirror.NONE);
    }

    /**
     * If the (already orientation-transformed) {@code size}'s horizontal footprint
     * exceeds this machine's print area, set the error state and return true:
     * {@code NEEDS_HIGHER_TIER} (+ {@link #requiredTier}) when a larger printer
     * would fit it, else {@code AREA_TOO_SMALL} when it's too big for even a Tier 8.
     * Returns false (and leaves state untouched) when it fits.
     */
    private boolean footprintTooLarge(BlockPos size) {
        int footprint = Math.max(size.getX(), size.getZ());
        if (footprint <= MC3DPrintConfig.maxFootprint(tier)) {
            return false;
        }
        MachineTier fit = smallestTierFor(footprint);
        if (fit != null) {
            requiredTier = fit.number();
            state = State.NEEDS_HIGHER_TIER;
        } else {
            requiredTier = 0;
            state = State.AREA_TOO_SMALL;
        }
        return true;
    }

    /** Smallest tier whose print footprint fits {@code footprint}, or null if none (too big for T8). */
    private static MachineTier smallestTierFor(int footprint) {
        for (MachineTier candidate : MachineTier.values()) {
            if (MC3DPrintConfig.maxFootprint(candidate) >= footprint) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Cycles the build rotation clockwise 90° (0→90→180→270→0). Rotation persists
     * across disc swaps like the offsets, and the ghost + obstruction follow live.
     * Only the rotation changes — the X/Y/Z offsets are never touched.
     */
    public void cycleRotation() {
        rotation = rotation.getRotated(Rotation.CLOCKWISE_90);
        setChanged();
        recheckObstruction(); // a rotated footprint may newly clear/obstruct
        if (previewEnabled) {
            syncToClients(); // the ghost re-renders at the new rotation
        }
    }

    /**
     * Toggles the hologram preview. The toggle always works, even with no disc
     * loaded — without a blueprint there is simply nothing to ghost-render (the
     * renderer early-returns on empty preview data). When a disc IS present we
     * resolve and cache its blueprint here and enforce the size cap; if the
     * blueprint is too big the toggle still flips on but we surface why.
     */
    public void togglePreview(@Nullable net.minecraft.world.entity.player.Player player) {
        if (previewEnabled) {
            previewEnabled = false;
        } else {
            previewEnabled = true;
            previewBlueprint = null;
            previewBlueprintId = null;
            ItemStack template = inventory.getStackInSlot(SLOT_TEMPLATE);
            if (isLoadedDisc(template) && level instanceof ServerLevel serverLevel) {
                UUID id = BlueprintDiscItem.getBlueprintId(template).orElse(null);
                Blueprint blueprint = id == null ? null
                        : BlueprintFileStore.forServer(serverLevel.getServer()).load(id).orElse(null);
                if (blueprint != null) {
                    int cap = MC3DPrintConfig.PREVIEW_MAX_BLOCKS.get();
                    if (blueprint.blockCount() > cap) {
                        if (player != null) {
                            com.pgmacdesign.mc3dprint.compat.MsgCompat.actionBar(player, Component.translatable("message.mc3dprint.preview_too_big",
                                    blueprint.blockCount(), cap));
                        }
                    } else {
                        previewBlueprint = blueprint;
                        previewBlueprintId = id;
                    }
                }
            }
        }
        setChanged();
        syncToClients();
    }

    // --- Matter Calculator: cached pre-print cost report for the loaded disc ---
    //
    // Recomputed only when the disc / upgrade loadout / slotted resin changes (the
    // stamp), so the per-tick menu poll never touches the blueprint file. Costs come
    // from the SAME per-block primitives the print path spends through
    // (blockFuCost/blockFuTier + the Overdrive floor), so predicted == consumed.

    /** Per-tier FU cost (tier units), printable block count, and the stamp it was built for. */
    private transient int[] reportPerTier;
    private transient int reportBlocks;
    private transient int reportStamp;

    private void ensureCostReport() {
        ItemStack template = inventory.getStackInSlot(SLOT_TEMPLATE);
        UUID id = isLoadedDisc(template) ? BlueprintDiscItem.getBlueprintId(template).orElse(null) : null;
        ItemStack resin = resins.getStackInSlot(0);
        int stamp = java.util.Objects.hash(id,
                upgradeCount(UpgradeItem.Type.EFFICIENCY),
                upgradeCount(UpgradeItem.Type.SPEED),
                upgradeCount(UpgradeItem.Type.RF_EFFICIENCY),
                resin.getItem(), resin.isEmpty() ? 0 : 1,
                id != null && BlueprintDiscItem.isOfficial(template));
        if (stamp == reportStamp) {
            return;
        }
        reportStamp = stamp;
        reportPerTier = null;
        reportBlocks = 0;
        if (id == null || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        Blueprint blueprint = BlueprintFileStore.forServer(serverLevel.getServer()).load(id).orElse(null);
        if (blueprint == null) {
            return;
        }
        boolean overdrive = !resin.isEmpty() && resin.getItem() instanceof ResinItem resinItem
                && resinItem.effect() == ResinItem.Effect.OVERDRIVE
                && BlueprintDiscItem.isOfficial(template);
        int overdriveTier = overdrive && resin.getItem() instanceof ResinItem ri ? ri.tier() : 0;

        // per-palette-index block counts, then one cost resolve per palette entry
        int[] counts = new int[blueprint.palette().size()];
        blueprint.forEachBlock((local, paletteIndex) -> counts[paletteIndex]++);

        int[] perTier = new int[SpoolItem.CAPACITY_BY_TIER.length];
        int blocks = 0;
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] == 0) {
                continue;
            }
            Optional<BlockState> resolved = blueprint.palette().get(i).resolve();
            if (resolved.isEmpty() || !canPrintBlock(resolved.get())) {
                continue; // skipped at print time — free, no RF, no tick
            }
            int fuCost = blockFuCost(resolved.get());
            if (overdrive) {
                int baseFu = blockFuValue(resolved.get()).map(FuValue::fu).orElse(0);
                if (baseFu > 0) {
                    fuCost = Math.min(fuCost, ResinEffects.overdriveFloor(baseFu, overdriveTier,
                            MC3DPrintConfig.RESIN_OVERDRIVE_T3_BELOW.get()));
                }
            }
            int costTier = blockFuTier(resolved.get());
            perTier[costTier - 1] += fuCost * counts[i];
            blocks += counts[i];
        }
        reportPerTier = perTier;
        reportBlocks = blocks;
    }

    /** Per-tier FU cost of the loaded disc, or null when no report. Test/API surface. */
    @Nullable
    public int[] costReportPerTier() {
        ensureCostReport();
        return reportPerTier == null ? null : reportPerTier.clone();
    }

    /** Total RF the loaded disc's print will consume at the current upgrade loadout. */
    public int costReportRf() {
        ensureCostReport();
        return reportPerTier == null ? 0
                : FuConversion.clampToInt((long) reportBlocks * rfAdjusted(MC3DPrintConfig.rfPerBlock(tier)));
    }

    /** Total ticks the loaded disc's print will take at the current upgrade loadout. */
    public int costReportEta() {
        ensureCostReport();
        return reportPerTier == null ? 0
                : FuConversion.clampToInt((long) reportBlocks * speedAdjusted(MC3DPrintConfig.ticksPerBlock(tier)));
    }

    /** Exact-tier FU available to this printer (docked + network), in tier units. */
    private int exactTierAvailable(int tierNumber) {
        int ratio = FuConversion.ratio();
        long base = FilamentDrain.availableTier(spools, tierNumber, ratio);
        for (IFilamentSource src : reachableSources()) {
            base += src.availableExactTier(tierNumber);
        }
        return FuConversion.clampToInt(FuConversion.fromBase(base, tierNumber, ratio));
    }

    /**
     * Down-only feasibility: cost at tier T may be paid by filament of tier >= T, so
     * coverage holds iff for every T the cumulative need at >= T fits the cumulative
     * supply at >= T (base units). Returns the LOWEST failing tier, or 0 when covered.
     */
    private int shortfallTier() {
        ensureCostReport();
        if (reportPerTier == null) {
            return 0;
        }
        int ratio = FuConversion.ratio();
        long needBase = 0;
        long availBase = 0;
        int worst = 0;
        for (int t = SpoolItem.CAPACITY_BY_TIER.length; t >= 1; t--) {
            needBase += FuConversion.toBase(reportPerTier[t - 1], t, ratio);
            long dockedAndNet = FilamentDrain.availableTier(spools, t, ratio);
            for (IFilamentSource src : reachableSources()) {
                dockedAndNet += src.availableExactTier(t);
            }
            availBase += dockedAndNet;
            if (needBase > availBase) {
                worst = t;
            }
        }
        return worst;
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
            case DATA_ROTATION -> rotation.ordinal();
            case DATA_REQUIRED_TIER -> requiredTier;
            case DATA_DECON_MODE -> deconstructMode ? 1 : 0;
            case DATA_BP_FU_TOTAL -> {
                ensureCostReport();
                if (reportPerTier == null) {
                    yield 0;
                }
                long total = 0;
                for (int c : reportPerTier) {
                    total += c;
                }
                yield FuConversion.clampToInt(total);
            }
            case DATA_BP_RF -> costReportRf();
            case DATA_BP_ETA -> costReportEta();
            case DATA_BP_SHORTFALL -> shortfallTier();
            case DATA_JOB_ACTIVE -> activeJob != null || deconstructJob != null ? 1 : 0;
            case DATA_FU_NETWORK -> networkFu(displayTier());
            default -> {
                if (index >= DATA_COST_TIER_BASE && index < DATA_COST_TIER_BASE + 8) {
                    ensureCostReport();
                    yield reportPerTier == null ? 0 : reportPerTier[index - DATA_COST_TIER_BASE];
                }
                if (index >= DATA_AVAIL_TIER_BASE && index < DATA_AVAIL_TIER_BASE + 8) {
                    yield exactTierAvailable(index - DATA_AVAIL_TIER_BASE + 1);
                }
                yield 0;
            }
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

    // --- Capabilities (exposed raw; registered centrally in ModCapabilities) ---

    public MachineEnergyStorage getEnergyStorage() {
        return energy;
    }

    /**
     * Per-face item handler. Per the I/O design: top inserts (input slot), bottom
     * extracts (output slot), {@code null} side is the combined inventory, and the
     * four horizontal sides are reserved exclusively for docked filament spools —
     * no general item I/O (they render the spinning spools instead).
     */
    @Nullable
    public IItemHandler getItemHandler(@Nullable Direction side) {
        if (side == null) {
            return inventory;
        }
        if (side == Direction.UP) {
            return inputHandler;
        }
        if (side == Direction.DOWN) {
            return outputHandler;
        }
        return null;
    }

    // --- Persistence ---

    //? if >=1.21.5 {
    /*@Override
    protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput out) {
        super.saveAdditional(out);
        writeData(BeData.writer(out));
    }

    @Override
    protected void loadAdditional(net.minecraft.world.level.storage.ValueInput in) {
        super.loadAdditional(in);
        readData(BeData.reader(in));
    }
    *///?} else {
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        writeData(BeData.writer(tag, registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        readData(BeData.reader(tag, registries));
    }
    //?}

    private void writeData(BeData.Writer w) {
        w.putHandler("Inventory", inventory);
        w.putHandler("Spools", spools);
        w.putHandler("Upgrades", upgrades);
        w.putHandler("Resins", resins);
        if (armedResinEffect != null) {
            w.putString("ArmedResin", armedResinEffect.id());
            w.putInt("ArmedResinTier", armedResinTier);
        }
        w.putBoolean("ResinConsumed", resinConsumed);
        w.putInt("SaltedThisJob", saltedThisJob);
        w.putInt("TreasureThisJob", treasureThisJob);
        w.putInt("BankedXp", bankedXp);
        w.putInt("Energy", energy.getEnergyStored());
        w.putInt("Progress", itemProgress);
        w.putInt("State", state.ordinal());
        if (activeJob != null) {
            // PrintJob (un)serializes as a raw CompoundTag; route it through the codec
            // store/read so the seam stays version-agnostic (same on-disk "ActiveJob" compound).
            w.store("ActiveJob", CompoundTag.CODEC, activeJob.save());
        }
        // History is a List<CompoundTag>; a list codec preserves the legacy "History" ListTag shape.
        w.store("History", CompoundTag.CODEC.listOf(), history);
        w.putBoolean("AutoStart", autoStart);
        w.putBoolean("LastRedstone", lastRedstoneSignal);
        w.putInt("OffsetX", offsetX);
        w.putInt("OffsetY", offsetY);
        w.putInt("OffsetZ", offsetZ);
        w.putInt("Rotation", rotation.ordinal());
        if (owner != null) {
            w.putUUID("Owner", owner);
        }
        w.putBoolean("PreviewEnabled", previewEnabled);
        w.putBoolean("DeconMode", deconstructMode);
        w.putBoolean("DeconArm", deconArmedRequiresStart);
        if (deconstructMin != null && deconstructSize != null) {
            w.store("DeconMin", BlockPos.CODEC, deconstructMin);
            w.store("DeconSize", BlockPos.CODEC, deconstructSize);
        }
        if (deconstructJob != null) {
            w.store("DeconJob", CompoundTag.CODEC, deconstructJob.save());
        }
    }

    private void readData(BeData.Reader r) {
        r.readHandler("Inventory", inventory);
        r.readHandler("Spools", spools);
        r.readHandler("Upgrades", upgrades);
        r.readHandler("Resins", resins);
        armedResinEffect = parseResinEffect(r.getStringOr("ArmedResin", ""));
        armedResinTier = r.getIntOr("ArmedResinTier", 0);
        resinConsumed = r.getBooleanOr("ResinConsumed", false);
        saltedThisJob = r.getIntOr("SaltedThisJob", 0);
        treasureThisJob = r.getIntOr("TreasureThisJob", 0);
        bankedXp = r.getIntOr("BankedXp", 0);
        refreshEnergyCapacity();
        energy.setStored(r.getIntOr("Energy", 0));
        itemProgress = r.getIntOr("Progress", 0);
        state = State.byOrdinal(r.getIntOr("State", 0));
        activeJob = r.read("ActiveJob", CompoundTag.CODEC).map(PrintJob::load).orElse(null);
        history.clear();
        r.read("History", CompoundTag.CODEC.listOf()).ifPresent(history::addAll);
        autoStart = r.getBooleanOr("AutoStart", false);
        lastRedstoneSignal = r.getBooleanOr("LastRedstone", false);
        offsetX = Mth.clamp(r.getIntOr("OffsetX", 0), -MAX_OFFSET, MAX_OFFSET);
        offsetY = Mth.clamp(r.getIntOr("OffsetY", 0), -MAX_OFFSET, MAX_OFFSET);
        offsetZ = Mth.clamp(r.getIntOr("OffsetZ", 0), -MAX_OFFSET, MAX_OFFSET);
        rotation = Rotation.values()[r.getIntOr("Rotation", 0) % Rotation.values().length];
        owner = r.getUUID("Owner").orElse(null);
        previewEnabled = r.getBooleanOr("PreviewEnabled", false);
        deconstructMode = r.getBooleanOr("DeconMode", false);
        deconArmedRequiresStart = r.getBooleanOr("DeconArm", deconstructMode); // safe default: re-arm
        deconstructMin = r.read("DeconMin", BlockPos.CODEC).orElse(null);
        deconstructSize = r.read("DeconSize", BlockPos.CODEC).orElse(null);
        deconstructJob = r.read("DeconJob", CompoundTag.CODEC).map(DeconstructJob::load).orElse(null);
    }
}
