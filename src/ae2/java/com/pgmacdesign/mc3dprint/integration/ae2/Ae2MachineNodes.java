package com.pgmacdesign.mc3dprint.integration.ae2;

import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.util.AECableType;
import appeng.capabilities.Capabilities;
import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.machine.MachineAttachments;
import com.pgmacdesign.mc3dprint.machine.PrinterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gives every printer and formed Fabricator a real AE2 grid node, so an ME cable connects to one
 * the way it connects to any other AE2 machine.
 *
 * <p>Before this, a machine was found by scanning the six neighbours of every grid node. That
 * worked, but nothing about it was visible: the cable drew no connection to the machine, so the
 * only way to know a printer was wired in was to open the terminal and count the header. The guide
 * said "put the machine against a cable and it is connected" while the world showed a cable
 * ending in mid-air next to it.
 *
 * <p><b>The node carries no channel</b> ({@link GridFlags#REQUIRE_CHANNEL} is deliberately not
 * set) and draws no idle power. A printer is not powered by AE2 and never was: it runs on RF and
 * filament, and the terminal part is the thing that pays a channel. Flagging the machine would
 * silently cost every existing network one channel per printer on update.
 *
 * <p>The node lives here rather than on the block entity because {@code PrinterBlockEntity} is
 * shared with versions that have no AE2 at all. {@link MachineAttachments} is the seam.
 *
 * <p><b>Forge, not NeoForge.</b> 1.20.1 has no capability registration event, so the host is
 * attached per block entity through {@link AttachCapabilitiesEvent} and answered from the same
 * side table the NeoForge line keys by dimension and position.
 */
public final class Ae2MachineNodes implements MachineAttachments.Attachment {

    private static final ResourceLocation KEY =
            new ResourceLocation(MC3DPrint.MOD_ID, "ae2_grid_node");

    /**
     * One entry per loaded machine. Keyed by dimension and position rather than by the block
     * entity, because a block entity can be replaced in place (a formed Fabricator's controller
     * does exactly that) and the stale key would then hold a node pointing at nothing.
     */
    private static final Map<Key, Holder> NODES = new ConcurrentHashMap<>();

    private record Key(ResourceKey<Level> dimension, BlockPos pos) {}

    private Ae2MachineNodes() {}

    static void install() {
        MachineAttachments.register(new Ae2MachineNodes());
    }

    /** Called from the gated event subscriber, so this class is only loaded when AE2 is present. */
    static void attach(AttachCapabilitiesEvent<net.minecraft.world.level.block.entity.BlockEntity> event) {
        if (!(event.getObject() instanceof PrinterBlockEntity machine)) {
            return;
        }
        event.addCapability(KEY, new ICapabilityProvider() {
            @Nonnull
            @Override
            public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> capability,
                                                     @Nullable Direction side) {
                if (capability != Capabilities.IN_WORLD_GRID_NODE_HOST) {
                    return LazyOptional.empty();
                }
                Key key = keyOf(machine);
                Holder holder = key == null ? null : NODES.get(key);
                // Resolved per query rather than cached in the provider: the node is created a tick
                // after the block entity exists, so a provider built eagerly would answer empty
                // forever for the machine that needs it most.
                return holder == null ? LazyOptional.empty()
                        : LazyOptional.of(() -> holder).cast();
            }
        });
    }

    @Nullable
    private static Key keyOf(PrinterBlockEntity machine) {
        Level level = machine.getLevel();
        return level == null ? null : new Key(level.dimension(), machine.getBlockPos().immutable());
    }

    @Override
    public void onLoad(PrinterBlockEntity machine) {
        Level level = machine.getLevel();
        // Client levels have no grid. Creating a node there would build a second, parallel network
        // out of render state.
        if (level == null || level.isClientSide()) {
            return;
        }
        Key key = keyOf(machine);
        if (key == null || NODES.containsKey(key)) {
            return;
        }
        Holder holder = new Holder(machine);
        NODES.put(key, holder);
        holder.node.create(level, machine.getBlockPos());
    }

    @Override
    public void onUnload(PrinterBlockEntity machine) {
        Level level = machine.getLevel();
        // Same guard as onLoad, and it matters more here. NODES is keyed by dimension and position,
        // which a single-player client level shares with the server one, so an unguarded client
        // unload would destroy the SERVER's node for that machine.
        if (level == null || level.isClientSide()) {
            return;
        }
        Key key = keyOf(machine);
        if (key == null) {
            return;
        }
        Holder holder = NODES.remove(key);
        // Removal is idempotent on purpose: breaking a machine in a chunk that then unloads calls
        // this twice, and destroying a node twice throws.
        if (holder != null) {
            holder.node.destroy();
        }
    }

    /** The per-machine node and the host face AE2 asks for it through. */
    private static final class Holder implements IInWorldGridNodeHost {

        private static final IGridNodeListener<Holder> LISTENER = new IGridNodeListener<>() {
            @Override
            public void onSaveChanges(Holder holder, IGridNode node) {
                // The node holds nothing worth persisting: it is rebuilt from scratch on load and
                // carries no channel, owner-scoped storage or configuration.
            }
        };

        private final IManagedGridNode node;
        private final BlockPos pos;

        Holder(PrinterBlockEntity machine) {
            this.pos = machine.getBlockPos().immutable();
            this.node = GridHelper.createManagedNode(this, LISTENER)
                    .setInWorldNode(true)
                    .setExposedOnSides(Set.of(Direction.values()))
                    // No REQUIRE_CHANNEL: see the class javadoc. A printer joins the grid to be
                    // seen and connected to, not to consume network capacity.
                    .setFlags()
                    .setIdlePowerUsage(0.0D)
                    .setVisualRepresentation(machine.getBlockState().getBlock());
        }

        @Override
        public IGridNode getGridNode(Direction side) {
            return node.getNode();
        }

        @Override
        public AECableType getCableConnectionType(Direction side) {
            // GLASS is the plain connection every unremarkable machine draws. SMART would render
            // channel indicators on a node that deliberately carries no channel.
            return AECableType.GLASS;
        }
    }

    /**
     * Where a machine node sits, or null if the node belongs to anything else on the grid.
     *
     * <p>Read off the holder rather than off the node's owner-as-block-entity, because the owner
     * here IS the holder: the node is not owned by the block entity, which cannot name AE2 types.
     */
    @Nullable
    static BlockPos machinePos(IGridNode node) {
        return node.getOwner() instanceof Holder holder ? holder.pos : null;
    }
}
