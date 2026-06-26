package com.pgmacdesign.mc3dprint.compat;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The 1.21.5 tooltip seam: {@link TooltipCompat#sink} adapts a {@code Consumer<Component>}
 * back to a write-only {@code List<Component>} so {@code appendHoverText} bodies stay
 * verbatim. These verify the forwarding contract that the appendHoverText guards rely on —
 * pure Java, no Minecraft bootstrap ({@link Component#literal} is registry-free).
 */
class TooltipCompatTest {

    @Test
    void addForwardsEachLineToTheConsumerInOrder() {
        List<Component> captured = new ArrayList<>();
        List<Component> sink = TooltipCompat.sink(captured::add);

        Component a = Component.literal("first");
        Component b = Component.literal("second");
        sink.add(a);
        sink.add(b);

        assertEquals(2, captured.size());
        assertSame(a, captured.get(0), "first line forwarded as-is");
        assertSame(b, captured.get(1), "order preserved");
    }

    @Test
    void addReturnsTrueSoEarlyReturningBodiesBehaveLikeAList() {
        // appendHoverText bodies often do `tooltip.add(x); return;` — add() must report success.
        List<Component> sink = TooltipCompat.sink(c -> {});
        assertEquals(true, sink.add(Component.literal("x")));
    }

    @Test
    void isWriteOnly_readOperationsThrow() {
        // The contract is append-only; a future tooltip body that READS would be a bug, so
        // get()/size() must fail loudly rather than silently reporting an empty/partial list.
        List<Component> sink = TooltipCompat.sink(c -> {});
        sink.add(Component.literal("ignored"));
        assertThrows(UnsupportedOperationException.class, () -> sink.get(0));
        assertEquals(0, sink.size(), "size is fixed at 0 (write-only view)");
    }
}
