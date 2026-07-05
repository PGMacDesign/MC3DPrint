package com.pgmacdesign.mc3dprint.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Verifies the legacy→native sign-message conversion that {@link BeData} runs on 1.21.5+.
 * Curated blueprints bake each sign line as a 1.20.1 JSON-component string ({@code {"text":"…"}});
 * from 1.21.5 the sign codec reads a bare string as LITERAL text, so without conversion the JSON
 * would render verbatim. The conversion re-encodes via {@link ComponentSerialization}, the exact
 * codec the sign uses to read its {@code messages}, so a round-trip here proves the fix.
 */
class BeDataSignTextTest {

    /** False when the component codec can't class-init in this headless env (see bootstrap()). */
    private static boolean codecUsable;

    @BeforeAll
    static void bootstrap() {
        try {
            SharedConstants.setVersion(DetectedVersion.BUILT_IN);
            Bootstrap.bootStrap();
        } catch (Throwable ignored) {
            // Full bootstrap fails under NeoForge's headless JUnit env, where FML's mod
            // list is absent. Pre-1.21.9 that was fine: plain text components were
            // registry-free. 1.21.9 wired Dialog/ClickEvent codecs into Style's static
            // init, which touches BuiltInRegistries — probe below and skip if unusable.
        }
        try {
            ComponentSerialization.CODEC.parse(NbtOps.INSTANCE,
                    net.minecraft.nbt.StringTag.valueOf("probe")).result();
            codecUsable = true;
        } catch (Throwable t) {
            codecUsable = false;
        }
    }

    @org.junit.jupiter.api.BeforeEach
    void requireCodec() {
        org.junit.jupiter.api.Assumptions.assumeTrue(codecUsable,
                "component codec needs a bootstrapped registry on this MC version; "
                        + "covered by the 1.21.1 oracle node");
    }

    /** What the sign codec would read back from a converted message tag. */
    private static String readBack(Tag tag) {
        Component c = ComponentSerialization.CODEC.parse(NbtOps.INSTANCE, tag).result().orElseThrow();
        return c.getString();
    }

    @Test
    void convertsLegacyJsonLineToNativeText() {
        Tag converted = BeData.legacyComponentToNative("{\"text\":\"Hello\"}");
        assertNotNull(converted, "a JSON-component line must convert");
        assertEquals("Hello", readBack(converted));
    }

    @Test
    void convertsEmptyLegacyLineToBlank() {
        Tag converted = BeData.legacyComponentToNative("{\"text\":\"\"}");
        assertNotNull(converted);
        assertEquals("", readBack(converted));
    }

    @Test
    void leavesAlreadyNativeLiteralUntouched() {
        // A native (raw) message — e.g. a player-scanned 1.21.5+ sign — isn't JSON; pass through.
        assertNull(BeData.legacyComponentToNative("Cactus farm"));
        assertNull(BeData.legacyComponentToNative(""));
    }
}
