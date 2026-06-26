package com.pgmacdesign.mc3dprint.machine;

import com.pgmacdesign.mc3dprint.config.MC3DPrintConfig;
import com.pgmacdesign.mc3dprint.fu.FuConversion;
import com.pgmacdesign.mc3dprint.fu.FuValue;
import com.pgmacdesign.mc3dprint.fu.FuValueRegistry;
import com.pgmacdesign.mc3dprint.fu.SpoolItem;
import com.pgmacdesign.mc3dprint.registry.ModBlockEntities;
import com.pgmacdesign.mc3dprint.registry.ModItemTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.RangedWrapper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Filament Winder: converts materials into FU wound onto a spool. RF is
 * consumed at winding per the design doc. A single universal winder handles
 * every tier; the gate is the spool — a material only winds into a spool of
 * its exact tier (netherite needs a T6 spool, cobblestone a T1 spool), which
 * closes the print-tier bypass without a winder tier ladder.
 *
 * <p>A second gate is the {@link ModItemTags#WINDER_BLACKLIST} item tag:
 * items that can still be printed but must never be wound (e.g. sticks, to
 * stop FU laundering through cheap micro-crafts). See that tag's javadoc.
 */
public class WinderBlockEntity extends BlockEntity implements MenuProvider {
    public static final int SLOT_INPUT = 0;
    public static final int SLOT_SPOOL = 1;
    public static final int SLOT_COUNT = 2;

    public static final int DATA_PROGRESS = 0;
    public static final int DATA_MAX_PROGRESS = 1;
    public static final int DATA_ENERGY = 2;
    public static final int DATA_MAX_ENERGY = 3;
    public static final int DATA_SPOOL_FU = 4;
    public static final int DATA_SPOOL_CAP = 5;
    public static final int DATA_REQUIRED_TIER = 6;
    public static final int DATA_STATUS = 7;
    public static final int DATA_YIELD = 8;   // FU produced per item with the current input+spool
    public static final int DATA_COUNT = 9;

    /** Winder status surfaced to the GUI. */
    public static final int STATUS_OK = 0;          // empty, or material + matching spool
    public static final int STATUS_WRONG_TIER = 1;  // material present, spool tier doesn't match
    public static final int STATUS_NOT_CONVERTIBLE = 2; // input item has no FU value

    private final ItemStackHandler inventory = new ItemStackHandler(SLOT_COUNT) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            if (slot == SLOT_SPOOL) {
                return stack.getItem() instanceof SpoolItem;
            }
            return true;
        }
    };

    private final MachineEnergyStorage energy = new MachineEnergyStorage(
            MC3DPrintConfig.WINDER_ENERGY_BUFFER.get(),
            MC3DPrintConfig.WINDER_MAX_ENERGY_RECEIVE.get(),
            this::setChanged);

    private final IItemHandler inputHandler =
            new RangedWrapper(inventory, SLOT_INPUT, SLOT_INPUT + 1);

    private int progress;
    /** Player who placed the winder — accumulates Matter Matters progress. */
    @Nullable
    private java.util.UUID owner;

    public WinderBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.FILAMENT_WINDER.get(), pos, blockState);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState blockState, WinderBlockEntity winder) {
        winder.tick();
    }

    private void tick() {
        ItemStack input = inventory.getStackInSlot(SLOT_INPUT);
        ItemStack spool = inventory.getStackInSlot(SLOT_SPOOL);

        // exact-tier rule (hard product rule): a material only winds into a
        // spool of its own tier — no tiering up, and no lossy tiering down here.
        // The WINDER_BLACKLIST check sits in the SAME guard as the "no FU value"
        // check: a blacklisted input (e.g. a stick) still has an FU value — it
        // just must never be wound, to stop FU laundering. See ModItemTags.
        Optional<FuValue> value = FuValueRegistry.valueOf(input);
        if (value.isEmpty()
                || input.is(ModItemTags.WINDER_BLACKLIST)
                || !(spool.getItem() instanceof SpoolItem spoolItem) || spoolItem.creative()
                || !FuConversion.canWindInto(value.get().tier(), spoolItem.tier())
                || !energy.hasAtLeast(MC3DPrintConfig.WINDER_RF_PER_ITEM.get())) {
            progress = 0;
            return;
        }
        long yield = FuConversion.windYield(value.get().fu(), value.get().tier(),
                spoolItem.tier(), FuConversion.ratio());
        if (SpoolItem.getFu(spool) + yield > spoolItem.capacity()) {
            progress = 0; // full yield must fit — no FU is ever voided
            return;
        }

        progress++;
        if (progress >= MC3DPrintConfig.WINDER_TICKS_PER_ITEM.get()) {
            progress = 0;
            energy.consume(MC3DPrintConfig.WINDER_RF_PER_ITEM.get());
            SpoolItem.fill(spool, FuConversion.clampToInt(yield));
            creditFuConverted(value.get().fu());
            input.shrink(1);
            inventory.setStackInSlot(SLOT_INPUT, input);
            inventory.setStackInSlot(SLOT_SPOOL, spool);
            setChanged();
        }
    }

    public ItemStackHandler inventory() {
        return inventory;
    }

    /** Tier of spool the input material needs (its own tier), 0 if none/non-valued. */
    public int requiredSpoolTier() {
        return FuValueRegistry.valueOf(inventory.getStackInSlot(SLOT_INPUT))
                .map(FuValue::tier).orElse(0);
    }

    /** FU produced per item with the current input+spool, 0 if the pair can't wind. */
    public int currentYield() {
        ItemStack input = inventory.getStackInSlot(SLOT_INPUT);
        ItemStack spool = inventory.getStackInSlot(SLOT_SPOOL);
        Optional<FuValue> value = FuValueRegistry.valueOf(input);
        if (value.isEmpty()
                || input.is(ModItemTags.WINDER_BLACKLIST)
                || !(spool.getItem() instanceof SpoolItem spoolItem) || spoolItem.creative()
                || !FuConversion.canWindInto(value.get().tier(), spoolItem.tier())) {
            return 0;
        }
        return FuConversion.clampToInt(FuConversion.windYield(
                value.get().fu(), value.get().tier(), spoolItem.tier(), FuConversion.ratio()));
    }

    /** {@link #STATUS_OK}/{@link #STATUS_WRONG_TIER}/{@link #STATUS_NOT_CONVERTIBLE} for the GUI. */
    public int winderStatus() {
        ItemStack input = inventory.getStackInSlot(SLOT_INPUT);
        if (input.isEmpty()) {
            return STATUS_OK;
        }
        Optional<FuValue> value = FuValueRegistry.valueOf(input);
        if (value.isEmpty()) {
            return STATUS_NOT_CONVERTIBLE;
        }
        // Blacklisted inputs reuse STATUS_NOT_CONVERTIBLE on purpose: from the
        // player's view a stick simply "can't be converted" here. Note this is
        // a blacklist hit, NOT an unpriced item — the stack has an FU value,
        // it's just barred from winding. Checked before the wrong-tier branch so
        // it reports not-convertible regardless of the docked spool's tier.
        if (input.is(ModItemTags.WINDER_BLACKLIST)) {
            return STATUS_NOT_CONVERTIBLE;
        }
        ItemStack spool = inventory.getStackInSlot(SLOT_SPOOL);
        boolean matched = spool.getItem() instanceof SpoolItem spoolItem && !spoolItem.creative()
                && FuConversion.canWindInto(value.get().tier(), spoolItem.tier());
        return matched ? STATUS_OK : STATUS_WRONG_TIER;
    }

    public ContainerData containerData() {
        return new SplitContainerData(DATA_COUNT, this::dataValue);
    }

    private int dataValue(int index) {
        ItemStack spool = inventory.getStackInSlot(SLOT_SPOOL);
        return switch (index) {
            case DATA_PROGRESS -> progress;
            case DATA_MAX_PROGRESS -> MC3DPrintConfig.WINDER_TICKS_PER_ITEM.get();
            case DATA_ENERGY -> energy.getEnergyStored();
            case DATA_MAX_ENERGY -> energy.getMaxEnergyStored();
            case DATA_SPOOL_FU -> SpoolItem.getFu(spool);
            case DATA_SPOOL_CAP -> spool.getItem() instanceof SpoolItem s ? s.capacity() : 0;
            case DATA_REQUIRED_TIER -> requiredSpoolTier();
            case DATA_STATUS -> winderStatus();
            case DATA_YIELD -> currentYield();
            default -> 0;
        };
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int windowId, Inventory playerInventory, Player player) {
        return new WinderMenu(windowId, playerInventory, this);
    }

    // --- Capabilities (exposed raw; registered centrally in ModCapabilities) ---

    public IEnergyStorage getEnergyStorage() {
        return energy;
    }

    /** {@code null} side is the full inventory; any face exposes only the input slot. */
    public IItemHandler getItemHandler(@Nullable Direction side) {
        return side == null ? inventory : inputHandler;
    }

    public void setOwner(@Nullable java.util.UUID owner) {
        this.owner = owner;
        setChanged();
    }

    /** Accumulates wound FU on the owner toward the Matter Matters advancement. */
    private void creditFuConverted(int fu) {
        if (owner == null || level == null || level.getServer() == null) {
            return;
        }
        net.minecraft.server.level.ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
        if (player == null) {
            return;
        }
        CompoundTag root = player.getPersistentData();
        CompoundTag persisted = root.getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
        int total = persisted.getInt(com.pgmacdesign.mc3dprint.advancement.ModCriteria.TAG_FU_WOUND) + fu;
        persisted.putInt(com.pgmacdesign.mc3dprint.advancement.ModCriteria.TAG_FU_WOUND, total);
        root.put(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG, persisted);
        if (total >= com.pgmacdesign.mc3dprint.advancement.ModCriteria.MATTER_MATTERS_FU) {
            com.pgmacdesign.mc3dprint.advancement.ModCriteria.FU_CONVERTED.trigger(player);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Inventory", inventory.serializeNBT());
        tag.putInt("Energy", energy.getEnergyStored());
        tag.putInt("Progress", progress);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        inventory.deserializeNBT(tag.getCompound("Inventory"));
        energy.setStored(tag.getInt("Energy"));
        progress = tag.getInt("Progress");
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
    }
}
