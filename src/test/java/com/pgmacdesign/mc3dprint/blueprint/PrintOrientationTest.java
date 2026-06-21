package com.pgmacdesign.mc3dprint.blueprint;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrintOrientationTest {

    private static final int SX = 3, SY = 2, SZ = 5;

    @Test
    void noneIsIdentity() {
        BlockPos pos = new BlockPos(1, 1, 4);
        assertEquals(pos, PrintOrientation.NONE.transform(pos, SX, SY, SZ));
        assertEquals(new BlockPos(SX, SY, SZ), PrintOrientation.NONE.transformedSize(SX, SY, SZ));
    }

    @Test
    void rotation90SwapsFootprint() {
        PrintOrientation rot = new PrintOrientation(Rotation.CLOCKWISE_90, Mirror.NONE);
        assertEquals(new BlockPos(SZ, SY, SX), rot.transformedSize(SX, SY, SZ));
    }

    @Test
    void rotationsStayInBoundsAndAreBijective() {
        for (Rotation rotation : Rotation.values()) {
            for (Mirror mirror : Mirror.values()) {
                PrintOrientation orientation = new PrintOrientation(rotation, mirror);
                BlockPos size = orientation.transformedSize(SX, SY, SZ);
                Set<BlockPos> seen = new HashSet<>();
                for (int x = 0; x < SX; x++) {
                    for (int y = 0; y < SY; y++) {
                        for (int z = 0; z < SZ; z++) {
                            BlockPos out = orientation.transform(new BlockPos(x, y, z), SX, SY, SZ);
                            assertTrue(out.getX() >= 0 && out.getX() < size.getX(),
                                    rotation + "/" + mirror + " X out of bounds: " + out);
                            assertTrue(out.getY() >= 0 && out.getY() < size.getY());
                            assertTrue(out.getZ() >= 0 && out.getZ() < size.getZ(),
                                    rotation + "/" + mirror + " Z out of bounds: " + out);
                            assertTrue(seen.add(out), rotation + "/" + mirror + " collision at " + out);
                        }
                    }
                }
                assertEquals(SX * SY * SZ, seen.size());
            }
        }
    }

    @Test
    void fourRotationsReturnHome() {
        PrintOrientation orientation = PrintOrientation.NONE;
        BlockPos pos = new BlockPos(2, 0, 3);
        // applying CW90 transform four times in sequence on a cube returns the original
        int n = 7; // cube so footprint doesn't swap
        BlockPos current = pos;
        PrintOrientation cw90 = new PrintOrientation(Rotation.CLOCKWISE_90, Mirror.NONE);
        for (int i = 0; i < 4; i++) {
            current = cw90.transform(current, n, 1, n);
        }
        assertEquals(pos, current);
        assertEquals(Rotation.NONE, orientation.rotated(Rotation.NONE).rotation());
    }

    @Test
    void rotation180IsPointReflection() {
        PrintOrientation rot = new PrintOrientation(Rotation.CLOCKWISE_180, Mirror.NONE);
        assertEquals(new BlockPos(SX - 1, 0, SZ - 1), rot.transform(new BlockPos(0, 0, 0), SX, SY, SZ));
        assertEquals(new BlockPos(0, 1, 0), rot.transform(new BlockPos(SX - 1, 1, SZ - 1), SX, SY, SZ));
    }

    @Test
    void mirrorFlipsExpectedAxis() {
        PrintOrientation leftRight = new PrintOrientation(Rotation.NONE, Mirror.LEFT_RIGHT);
        assertEquals(new BlockPos(1, 0, SZ - 1), leftRight.transform(new BlockPos(1, 0, 0), SX, SY, SZ));

        PrintOrientation frontBack = new PrintOrientation(Rotation.NONE, Mirror.FRONT_BACK);
        assertEquals(new BlockPos(SX - 1, 0, 2), frontBack.transform(new BlockPos(0, 0, 2), SX, SY, SZ));
    }

    @Test
    void continuousTransformMatchesBlockTransformAtCenters() {
        // The entity (continuous) transform must agree with the block transform for a
        // block center idx+0.5 — that consistency is what keeps printed entities aligned
        // with the rotated/mirrored blocks under them, for all 8 orientations.
        for (Rotation rotation : Rotation.values()) {
            for (Mirror mirror : Mirror.values()) {
                PrintOrientation o = new PrintOrientation(rotation, mirror);
                for (int x = 0; x < SX; x++) {
                    for (int z = 0; z < SZ; z++) {
                        BlockPos block = o.transform(new BlockPos(x, 0, z), SX, SY, SZ);
                        double[] point = o.transformPoint(x + 0.5, 1.0, z + 0.5, SX, SZ);
                        assertEquals(block.getX() + 0.5, point[0], 1e-9, rotation + "/" + mirror + " X");
                        assertEquals(1.0, point[1], 1e-9);
                        assertEquals(block.getZ() + 0.5, point[2], 1e-9, rotation + "/" + mirror + " Z");
                    }
                }
            }
        }
    }
}
