package com.pgmacdesign.mc3dprint.integration.ae2;

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
public class MC3DPrintTerminalPart extends AEBasePart {

    private final Ae2TerminalHost host = new Ae2TerminalHost(this);

    public MC3DPrintTerminalPart(IPartItem<?> partItem) {
        super(partItem);
        getMainNode().setIdlePowerUsage(0.5);
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
