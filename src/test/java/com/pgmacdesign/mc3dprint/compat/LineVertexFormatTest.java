package com.pgmacdesign.mc3dprint.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Holds {@link RenderCompat#lineVertex} to the line vertex format of the version it is built for.
 *
 * <p>Minecraft moved line width out of pipeline state and into the vertex, so on versions that made
 * the move every line vertex must carry {@code LineWidth}. Leaving the call off still compiles
 * there, which is what makes this worth a test: the buffer does not reject the short vertex where
 * it is written, it notices when the NEXT vertex begins and throws {@code IllegalStateException:
 * Missing elements in vertex} from inside the render frame, taking the client down. Docking a
 * filament spool did exactly that.
 *
 * <p>Every other version seam in the renderers is a compile-time API split, so a wrong boundary
 * fails the build. This one is a method that is merely optional to call, so nothing but a test
 * notices. It is also why the boundary was wrong once already: it was set at 26.2 from the version
 * the crash was first seen on, when the format actually changed in 26.1.
 *
 * <p>Driving a {@link Proxy} rather than a real buffer keeps this headless and version-agnostic:
 * the assertion is simply that the emitter calls {@code setLineWidth} exactly when the platform's
 * {@code VertexConsumer} has it to call.
 */
class LineVertexFormatTest {

    private static boolean platformVertexCarriesLineWidth() {
        for (Method m : VertexConsumer.class.getMethods()) {
            if (m.getName().equals("setLineWidth")) {
                return true;
            }
        }
        return false;
    }

    /** Records what {@link RenderCompat#lineVertex} calls, chaining like a real consumer. */
    private static VertexConsumer recordingConsumer(List<String> calls) {
        Object[] self = new Object[1];
        Object proxy = Proxy.newProxyInstance(
                VertexConsumer.class.getClassLoader(),
                new Class<?>[] {VertexConsumer.class},
                (p, method, args) -> {
                    calls.add(method.getName());
                    Class<?> ret = method.getReturnType();
                    if (ret.isAssignableFrom(VertexConsumer.class)) {
                        return self[0];
                    }
                    if (ret == void.class || !ret.isPrimitive()) {
                        return null;
                    }
                    return ret == boolean.class ? Boolean.FALSE : 0;
                });
        self[0] = proxy;
        return (VertexConsumer) proxy;
    }

    @Test
    void lineVerticesCarryEveryElementThisVersionsFormatDemands() {
        List<String> calls = new ArrayList<>();
        RenderCompat.lineVertex(new PoseStack().last(), recordingConsumer(calls),
                0f, 0f, 0f, 1f, 1f, 1f, 1f, 0f, 1f, 0f);

        assertEquals(platformVertexCarriesLineWidth(), calls.contains("setLineWidth"),
                "VertexConsumer" + (platformVertexCarriesLineWidth() ? " has " : " has no ")
                        + "setLineWidth on this version, but RenderCompat.lineVertex "
                        + (calls.contains("setLineWidth") ? "calls" : "does not call") + " it. "
                        + "A line vertex missing an element its format demands crashes the client "
                        + "on the NEXT vertex, from inside the render frame. Calls: " + calls);
    }
}
