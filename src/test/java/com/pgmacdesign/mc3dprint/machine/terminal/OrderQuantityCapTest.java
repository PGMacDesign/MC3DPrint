package com.pgmacdesign.mc3dprint.machine.terminal;

import com.pgmacdesign.mc3dprint.network.TerminalOrderPacket;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The order ceiling lived as two separate literals, and they stopped agreeing: the queue moved to
 * 9999 while the packet stayed at 1024, so typing 9999 produced an order for 1024 with nothing
 * anywhere reporting a problem. The screen showed the number it sent, not the number that arrived.
 */
class OrderQuantityCapTest {

    @Test
    void theWireCapMatchesTheQueueCap() {
        assertEquals(TerminalRequests.MAX_ORDER_QUANTITY, TerminalOrderPacket.MAX_QUANTITY,
                "the packet clamps on read, so a lower cap here silently shrinks an accepted order");
    }

    @Test
    void theCapIsTheAdvertisedNineThousandNineHundredAndNinetyNine() {
        // Pinned to the number the GUI's four-character field can express, so raising one without
        // the other cannot go unnoticed.
        assertEquals(9999, TerminalRequests.MAX_ORDER_QUANTITY);
    }
}
