package com.pgmacdesign.mc3dprint.machine.repository;

import com.pgmacdesign.mc3dprint.blueprint.repository.RepoEntry;
import com.pgmacdesign.mc3dprint.item.BlueprintDiscItem;
import com.pgmacdesign.mc3dprint.registry.ModItems;
import com.pgmacdesign.mc3dprint.registry.ModMenuTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class BlueprintRepositoryMenu extends AbstractContainerMenu {
    public static final int BUTTON_DEPOSIT = 0;
    public static final int BUTTON_BURN = 1;
    public static final int BUTTON_DELETE = 2;
    public static final int SELECT_BASE = 100; // row select buttons are SELECT_BASE + rowIndex

    // Layout (lockstep with BlueprintRepositoryScreen + the painted texture).
    // The IN/OUT slots sit centred between the Deposit (left) and STL-to-Disc
    // (right) buttons. Only the hotbar is shown — the 3 main inventory rows are
    // hidden to free vertical space (so they aren't accessible from this GUI).
    public static final int SLOT_IN_X = 106, SLOT_IN_Y = 133;
    public static final int SLOT_OUT_X = 124, SLOT_OUT_Y = 133;
    private static final int INV_X = 43, HOTBAR_Y = 164;

    @Nullable
    private final BlueprintRepositoryBlockEntity repository;

    // Client mirror of the catalogue (filled by RepositoryListingPacket) + selection.
    private List<RepoEntry> entries = new ArrayList<>();
    private Set<UUID> printed = Set.of();
    private int selectedIndex = -1;
    /**
     * The client's selection by identity. The listing is sorted by NAME, so a rename re-sorts
     * it and a bare index would silently start pointing at a different build.
     */
    @Nullable
    private UUID selectedClientId;
    // Server-side selection, resolved to a concrete blueprint id.
    @Nullable
    private UUID selectedId;

    public BlueprintRepositoryMenu(int windowId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(windowId, playerInventory, clientBlockEntity(playerInventory, buf));
    }

    public BlueprintRepositoryMenu(int windowId, Inventory playerInventory,
                                   @Nullable BlueprintRepositoryBlockEntity repository) {
        super(ModMenuTypes.BLUEPRINT_REPOSITORY.get(), windowId);
        this.repository = repository;

        IItemHandler inv = repository != null ? repository.inventory()
                : new ItemStackHandler(BlueprintRepositoryBlockEntity.SLOT_COUNT);
        addSlot(new SlotItemHandler(inv, BlueprintRepositoryBlockEntity.SLOT_IN, SLOT_IN_X, SLOT_IN_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() == ModItems.BLANK_BLUEPRINT_DISC.get()
                        || stack.getItem() instanceof BlueprintDiscItem;
            }
        });
        addSlot(new SlotItemHandler(inv, BlueprintRepositoryBlockEntity.SLOT_OUT, SLOT_OUT_X, SLOT_OUT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });

        // Hotbar only (indices 0-8); the 3 main rows are intentionally not added.
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, INV_X + col * 18, HOTBAR_Y));
        }
    }

    @Nullable
    private static BlueprintRepositoryBlockEntity clientBlockEntity(Inventory inv, FriendlyByteBuf buf) {
        return inv.player.level().getBlockEntity(buf.readBlockPos())
                instanceof BlueprintRepositoryBlockEntity be ? be : null;
    }

    // --- client view ---
    public List<RepoEntry> entries() {
        return entries;
    }

    public void setEntries(List<RepoEntry> entries, Set<UUID> printed) {
        this.entries = entries;
        this.printed = printed;
        // Follow the selected build to its new row rather than keeping a stale index.
        selectedIndex = -1;
        if (selectedClientId != null) {
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i).id().equals(selectedClientId)) {
                    selectedIndex = i;
                    break;
                }
            }
            if (selectedIndex < 0) {
                selectedClientId = null;
            }
        }
    }

    public boolean isPrinted(UUID id) {
        return printed.contains(id);
    }

    public int printedCount() {
        return printed.size();
    }

    public int selectedIndex() {
        return selectedIndex;
    }

    public void setSelectedIndex(int index) {
        this.selectedIndex = index;
        this.selectedClientId = index >= 0 && index < entries.size() ? entries.get(index).id() : null;
    }

    /** The selected build's id on the client, stable across a re-sorted listing. */
    @Nullable
    public UUID selectedClientId() {
        return selectedClientId;
    }

    public ItemStack inputStack() {
        return slots.get(BlueprintRepositoryBlockEntity.SLOT_IN).getItem();
    }

    // --- server actions ---
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (repository == null || !(player instanceof ServerPlayer sp)) {
            return false;
        }
        if (id == BUTTON_DEPOSIT) {
            repository.deposit(sp);
            return true;
        }
        if (id == BUTTON_BURN) {
            repository.burn(sp, selectedId);
            return true;
        }
        if (id == BUTTON_DELETE) {
            // selectedId is resolved server-side on the select click, so the removal target
            // never depends on a client-supplied id.
            repository.delete(sp, selectedId);
            return true;
        }
        if (id >= SELECT_BASE) {
            int idx = id - SELECT_BASE;
            List<RepoEntry> list = repository.listingFor(sp);
            selectedId = (idx >= 0 && idx < list.size()) ? list.get(idx).id() : null;
            return true;
        }
        return false;
    }

    /**
     * Applies a rename request from this menu's viewer. Called only from the network handler,
     * which has already established that this menu is the player's open container.
     */
    public void renameFromClient(ServerPlayer player, UUID id, String rawName) {
        if (repository != null) {
            repository.rename(player, id, rawName);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = slots.get(slotIndex);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack moved = slot.getItem();
        ItemStack original = moved.copy();
        int machineSlots = BlueprintRepositoryBlockEntity.SLOT_COUNT;
        if (slotIndex < machineSlots) {
            if (!moveItemStackTo(moved, machineSlots, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(moved, BlueprintRepositoryBlockEntity.SLOT_IN,
                BlueprintRepositoryBlockEntity.SLOT_IN + 1, false)) {
            return ItemStack.EMPTY;
        }
        if (moved.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        if (repository == null || repository.getLevel() == null) {
            return false;
        }
        return repository.getLevel().getBlockEntity(repository.getBlockPos()) == repository
                && player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(repository.getBlockPos())) <= 64.0;
    }
}
