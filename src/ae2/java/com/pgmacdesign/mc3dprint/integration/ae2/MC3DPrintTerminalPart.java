package com.pgmacdesign.mc3dprint.integration.ae2;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.parts.AEBasePart;
import com.pgmacdesign.mc3dprint.machine.terminal.MC3DPrintTerminalMenu;
import com.pgmacdesign.mc3dprint.machine.terminal.TerminalDispatcher;
import com.pgmacdesign.mc3dprint.machine.terminal.TerminalRequests;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.phys.Vec3;

/**
 * The MC3DPrint Terminal: an AE2 cable part that lists what the network's printers and
 * fabricators can print and orders it, paid in Filament Units rather than ingredients.
 *
 * <p>A part rather than a block so it sits on cable exactly like AE2's own terminals. It extends
 * {@code AEBasePart}, which lives outside {@code appeng.api}: that is the intended extension point
 * for addons, but it does couple this class to AE2's internals, so it is the first thing to check
 * when moving between AE2 majors.
 */
public class MC3DPrintTerminalPart extends AEBasePart implements IGridTickable {

    private final Ae2TerminalHost host = new Ae2TerminalHost(this);

    public MC3DPrintTerminalPart(IPartItem<?> partItem) {
        super(partItem);
        getMainNode()
                .setIdlePowerUsage(0.5)
                .addService(IGridTickable.class, this);
    }

    /**
     * Ask AE2 to tick us. Without this the dispatcher is never driven: orders would enqueue, sit
     * in QUEUED forever, and nothing would ever bind to a machine or print. The gametests call
     * TerminalDispatcher.tick directly, so they cannot catch its absence, which is exactly how
     * this shipped green the first time.
     *
     * <p>Range rather than a fixed rate so AE2 can back a quiet terminal off: an order book with
     * nothing in it has no reason to be looked at twenty times a second.
     */
    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(5, 40, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        boolean hadWork = !host.queue().open().isEmpty();
        tickTerminal();
        pushSyncToViewers();
        // Busy books get looked at often; an empty one is allowed to idle down.
        return hadWork ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
    }

    Ae2TerminalHost host() {
        return host;
    }

    /**
     * Right-click opens the terminal. Server side only: the menu carries a host that owns the
     * order book, and there is no such thing on the client.
     */
    @Override
    public boolean onUseWithoutItem(Player player, Vec3 hitVec) {
        if (player.level().isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return true;
        }
        if (!getMainNode().isActive()) {
            // No channel or no power. Say so rather than opening an empty catalog, which would
            // read as "your machines are gone" instead of "this terminal is offline".
            serverPlayer.displayClientMessage(
                    Component.translatable("gui.mc3dprint.terminal.offline"), true);
            return true;
        }
        serverPlayer.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.translatable("block.mc3dprint.me_print_terminal");
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                return new MC3DPrintTerminalMenu(id, inv, host);
            }
        });
        if (serverPlayer.level() instanceof ServerLevel level
                && serverPlayer.containerMenu instanceof MC3DPrintTerminalMenu menu) {
            TerminalRequests.sync(serverPlayer, menu, host, level);
        }
        return true;
    }

    /**
     * Pushes fresh state to anyone with this terminal open, so order progress actually moves in
     * the GUI. Sync used to happen only on open and on a click, which left the orders panel
     * frozen at 0/N while the work was really happening.
     */
    private void pushSyncToViewers() {
        if (!(getLevel() instanceof ServerLevel level)) {
            return;
        }
        for (ServerPlayer viewer : level.players()) {
            if (viewer.containerMenu instanceof MC3DPrintTerminalMenu menu && menu.host() == host) {
                TerminalRequests.sync(viewer, menu, host, level);
            }
        }
    }

    /**
     * Drives the order book once per tick. The dispatcher is what binds queued orders to machines
     * and takes finished ones off them, so without this the queue would fill and never move.
     */
    public void tickTerminal() {
        if (getLevel() instanceof ServerLevel level && getMainNode().isActive()) {
            TerminalDispatcher.tick(level, host.queue(), host.machines(level), host.sink());
        }
    }

    /** A flat screen on the cable face, matching the footprint AE2's own terminals use. */
    @Override
    public void getBoxes(IPartCollisionHelper helper) {
        helper.addBox(2, 2, 14, 14, 14, 16);
    }

    /**
     * Where finished orders go. Items are injected into the network's own storage; anything the
     * network will not take is reported as not taken, so the machine holds it rather than paying
     * for something that vanished.
     */
    com.pgmacdesign.mc3dprint.machine.terminal.OrderSink sink() {
        return new Ae2StorageSink(this);
    }

    @Override
    public IPartModel getStaticModels() {
        return Ae2TerminalModels.forState(isPowered(), isActive());
    }
}
