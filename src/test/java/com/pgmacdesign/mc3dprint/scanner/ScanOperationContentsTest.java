package com.pgmacdesign.mc3dprint.scanner;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The residual-contents check behind the scan-time anti-dupe guard.
 *
 * <p>Clearing a captured block entity through {@code Clearable} only works for block entities
 * that implement it. Draconic Evolution's {@code placed_item} does not (its
 * {@code TilePlacedItem} implements {@code IInteractTile} only), so an item placed on a wall
 * survived the clear and printed back for free, once per print. The scan now verifies the clear
 * instead of trusting it, and this pins the detector that verification rests on.
 */
class ScanOperationContentsTest {

    private static CompoundTag stack(String id) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putByte("Count", (byte) 1);
        return tag;
    }

    /** A block entity's own {@code id} carries no count, so a bare BE tag is not item data. */
    @Test
    void aPlainBlockEntityTagIsNotItemData() {
        CompoundTag be = new CompoundTag();
        be.putString("id", "minecraft:sign");
        be.putInt("x", 4);
        assertFalse(ScanOperation.holdsItemData(be));
    }

    @Test
    void cosmeticNbtIsNotItemData() {
        CompoundTag be = new CompoundTag();
        be.putString("id", "minecraft:sign");
        CompoundTag front = new CompoundTag();
        ListTag messages = new ListTag();
        messages.add(net.minecraft.nbt.StringTag.valueOf("Chicken farm"));
        front.put("messages", messages);
        be.put("front_text", front);
        assertFalse(ScanOperation.holdsItemData(be), "sign text must survive a scan");
    }

    @Test
    void aNestedStackIsFound() {
        CompoundTag be = new CompoundTag();
        be.putString("id", "draconicevolution:placed_item");
        CompoundTag inner = new CompoundTag();
        inner.put("stack0", stack("ae2:matter_cannon"));
        be.put("Contents", inner);
        assertTrue(ScanOperation.holdsItemData(be), "an item nested at any depth must be found");
    }

    @Test
    void aStackInsideAListIsFound() {
        CompoundTag be = new CompoundTag();
        be.putString("id", "some:display");
        ListTag items = new ListTag();
        items.add(stack("minecraft:diamond"));
        be.put("Items", items);
        assertTrue(ScanOperation.holdsItemData(be));
    }

    /** 1.21+ serializes the count lowercase; both spellings have to trip the check. */
    @Test
    void theModernLowercaseCountIsAlsoRecognised() {
        CompoundTag be = new CompoundTag();
        be.putString("id", "some:display");
        CompoundTag modern = new CompoundTag();
        modern.putString("id", "ae2:matter_cannon");
        modern.putInt("count", 1);
        be.put("held", modern);
        assertTrue(ScanOperation.holdsItemData(be));
    }
}
