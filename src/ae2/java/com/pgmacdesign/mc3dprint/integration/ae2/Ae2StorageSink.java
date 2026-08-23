package com.pgmacdesign.mc3dprint.integration.ae2;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import com.pgmacdesign.mc3dprint.machine.terminal.OrderSink;
import net.minecraft.world.item.ItemStack;

/**
 * Delivers finished orders into the ME network the terminal is attached to.
 *
 * <p><b>Simulate then modulate.</b> The machine asks how much would be taken before it charges for
 * anything, which is what keeps a full network from turning filament into nothing. AE2 gives that
 * for free through {@link Actionable#SIMULATE}, so the probe is exact rather than a guess.
 *
 * <p>An offline or disconnected grid accepts nothing rather than throwing. The machine treats "took
 * none" as a reason to hold the order, so a network that goes down mid-order pauses it instead of
 * failing it, which matches how every other interruption in this mod behaves.
 */
final class Ae2StorageSink implements OrderSink {

    private final MC3DPrintTerminalPart part;

    Ae2StorageSink(MC3DPrintTerminalPart part) {
        this.part = part;
    }

    @Override
    public int accept(ItemStack stack) {
        return insert(stack, Actionable.MODULATE);
    }

    @Override
    public int simulate(ItemStack stack) {
        return insert(stack, Actionable.SIMULATE);
    }

    private int insert(ItemStack stack, Actionable mode) {
        if (stack.isEmpty()) {
            return 0;
        }
        MEStorage storage = storage();
        if (storage == null) {
            return 0;
        }
        AEItemKey key = AEItemKey.of(stack);
        if (key == null) {
            return 0;
        }
        long inserted = storage.insert(key, stack.getCount(), mode,
                IActionSource.ofMachine(part));
        return (int) Math.max(0, Math.min(inserted, stack.getCount()));
    }

    private MEStorage storage() {
        IGrid grid = part.getMainNode().getGrid();
        if (grid == null || !part.getMainNode().isActive()) {
            return null;
        }
        IStorageService service = grid.getService(IStorageService.class);
        return service == null ? null : service.getInventory();
    }
}
