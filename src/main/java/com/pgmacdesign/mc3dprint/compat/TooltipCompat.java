package com.pgmacdesign.mc3dprint.compat;

import net.minecraft.network.chat.Component;

import java.util.AbstractList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Version seam for the 1.21.5 tooltip rewrite. {@code appendHoverText}'s
 * {@code List<Component>} sink became a {@code Consumer<Component>}. Rather than
 * touch every body, {@link #sink} adapts the consumer back to a write-only
 * {@code List} whose {@code add} forwards to it — so each method keeps its
 * original body verbatim, including any early {@code return} (a collect-then-flush
 * rewrite would silently drop lines on those branches). Read operations throw:
 * the contract is append-only.
 */
public final class TooltipCompat {
    private TooltipCompat() {}

    /** A write-only {@code List<Component>} view forwarding every {@code add} to {@code consumer}. */
    public static List<Component> sink(Consumer<Component> consumer) {
        return new AbstractList<>() {
            @Override
            public boolean add(Component c) {
                consumer.accept(c);
                return true;
            }

            @Override
            public Component get(int index) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int size() {
                return 0;
            }
        };
    }
}
