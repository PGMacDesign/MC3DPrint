package com.pgmacdesign.mc3dprint.machine;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Lets an optional integration hang per-machine state off a printer's lifecycle without the core
 * ever naming that integration.
 *
 * <p>This exists for one specific shape of problem: AE2 needs a printer to own a real grid node so
 * cables connect to it, and a grid node has to be created when the machine loads and destroyed when
 * it goes away. The core cannot import {@code appeng} (most versions ship without AE2 on the
 * classpath at all), and the AE2 source set cannot reach into the block entity's private lifecycle
 * from outside. A neutral hook is the seam.
 *
 * <p><b>Attachments are global, not per-machine.</b> Each one is handed the machine on every load
 * and unload and keeps its own side table, so an implementation that forgets to drop a machine on
 * unload leaks rather than corrupting anything.
 */
public final class MachineAttachments {

    /** Reacts to a machine entering and leaving the world. Both halves must be idempotent. */
    public interface Attachment {
        void onLoad(PrinterBlockEntity machine);

        void onUnload(PrinterBlockEntity machine);
    }

    // Copy-on-write because registration happens once during setup while reads happen on every
    // block-entity load, on the server thread and, for a client-side machine, off it.
    private static final List<Attachment> ATTACHMENTS = new CopyOnWriteArrayList<>();

    private MachineAttachments() {}

    public static void register(Attachment attachment) {
        ATTACHMENTS.add(attachment);
    }

    static void load(PrinterBlockEntity machine) {
        for (Attachment attachment : ATTACHMENTS) {
            attachment.onLoad(machine);
        }
    }

    /**
     * Called from both {@code setRemoved} and {@code onChunkUnloaded}, which is why implementations
     * must tolerate being told twice: breaking a machine in a chunk that then unloads fires both,
     * and only one of them means the machine is actually gone.
     */
    static void unload(PrinterBlockEntity machine) {
        for (Attachment attachment : ATTACHMENTS) {
            attachment.onUnload(machine);
        }
    }
}
