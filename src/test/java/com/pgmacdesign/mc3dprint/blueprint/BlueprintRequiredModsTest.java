package com.pgmacdesign.mc3dprint.blueprint;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Blueprint#requiredMods()} is pure string logic over the palette + entity ids,
 * so it's testable without any registry/bootstrap. It backs the visibility gate: a build
 * only appears once every mod it names is loaded.
 */
class BlueprintRequiredModsTest {

    @Test
    void vanillaOnlyBuildRequiresNothing() {
        Blueprint bp = Blueprint.builder("Vanilla", 2, 1, 1)
                .set(0, 0, 0, BlueprintBlockState.parse("minecraft:stone"))
                .set(1, 0, 0, BlueprintBlockState.parse("minecraft:oak_stairs[facing=east]"))
                .build();
        assertTrue(bp.requiredMods().isEmpty());
    }

    @Test
    void ownNamespaceIsNotAModRequirement() {
        Blueprint bp = Blueprint.builder("Ours", 1, 1, 1)
                .set(0, 0, 0, BlueprintBlockState.parse("mc3dprint:extrudium_ore"))
                .build();
        assertTrue(bp.requiredMods().isEmpty());
    }

    @Test
    void moddedPaletteDerivesNamespaces() {
        Blueprint bp = Blueprint.builder("AE2 setup", 3, 1, 1)
                .set(0, 0, 0, BlueprintBlockState.parse("minecraft:stone"))
                .set(1, 0, 0, BlueprintBlockState.parse("ae2:controller"))
                .set(2, 0, 0, BlueprintBlockState.parse("mc3dprint:extrudium_ore"))
                .build();
        assertEquals(Set.of("ae2"), bp.requiredMods());
    }

    @Test
    void dedupesAndSortsMultipleMods() {
        Blueprint bp = Blueprint.builder("Multi", 4, 1, 1)
                .set(0, 0, 0, BlueprintBlockState.parse("create:cogwheel[axis=y]"))
                .set(1, 0, 0, BlueprintBlockState.parse("ae2:controller"))
                .set(2, 0, 0, BlueprintBlockState.parse("create:shaft[axis=x]"))
                .set(3, 0, 0, BlueprintBlockState.parse("minecraft:stone"))
                .build();
        assertEquals(Set.of("ae2", "create"), bp.requiredMods());
        assertEquals("[ae2, create]", bp.requiredMods().toString()); // TreeSet → sorted
    }

    @Test
    void entityNamespacesCount() {
        CompoundTag frame = new CompoundTag();
        frame.putString("id", "supplementaries:sign_post");
        Blueprint bp = Blueprint.builder("With entity", 1, 1, 1)
                .set(0, 0, 0, BlueprintBlockState.parse("minecraft:stone"))
                .entity(0.5, 0.0, 0.5, frame)
                .build();
        assertEquals(Set.of("supplementaries"), bp.requiredMods());
    }

    @Test
    void missingNamespaceTreatedAsVanilla() {
        Blueprint bp = Blueprint.builder("No ns", 1, 1, 1)
                .set(0, 0, 0, BlueprintBlockState.parse("stone"))
                .build();
        assertTrue(bp.requiredMods().isEmpty());
    }
}
