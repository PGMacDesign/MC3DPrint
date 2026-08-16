package com.pgmacdesign.mc3dprint.blueprint;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.Set;

/**
 * "Is the block already standing here the one the blueprint wants?" — the comparison every
 * repair/re-print and ghost preview uses, in place of {@code worldState == wantedState}.
 *
 * <p><b>Why exact equality is wrong.</b> A blueprint stores the block state it was authored
 * or scanned with, but Minecraft owns a slice of every state and rewrites it from the
 * surroundings the moment the block exists in a world. A stair placed as
 * {@code shape=straight} becomes {@code inner_left} because of the stair beside it; a fence
 * grows connection booleans; a redstone lamp lights when a neighbour powers it. None of that
 * is a difference the printer created or can control, and re-printing the block cannot change
 * it. Comparing by identity therefore reported a perfectly correct build as wrong: the ghost
 * preview painted the corner stairs of every curated build red, and a repair print refused to
 * start at all, because a non-replaceable block that "doesn't match" reads as an obstruction.
 *
 * <p><b>What's ignored.</b> {@link #WORLD_DERIVED} is the set of properties the game recomputes
 * from the neighbourhood ({@code Block.updateFromNeighbourShapes} and redstone neighbour
 * updates), plus the two a player toggles by using the block ({@code open}, {@code powered}) —
 * a door is the same door whether or not someone left it open. Everything else, notably
 * {@code facing}, {@code half}, {@code axis} and {@code waterlogged}, still has to match
 * exactly, so a genuinely wrong block is still caught.
 *
 * <p><b>The cost.</b> These are ignored per-property, not per-block, so a block that authors
 * one of them deliberately loses it in repair mode: glow lichen keeps whichever faces it
 * already has instead of gaining the blueprint's, and an unlit campfire stays unlit. Skipping
 * a cosmetic touch-up on a block that already exists is a far smaller loss than wedging the
 * whole job, and none of it affects a first print into clear space, which places the
 * blueprint's state verbatim.
 *
 * <p><b>Deliberately NOT here: contents flags.</b> {@code has_record}, {@code has_book} and the
 * brewing stand's {@code has_bottle_*} describe what is inside a block rather than how the world
 * shaped it. A scan strips container contents, so those flags are legitimately false on anything
 * this mod prints, and a world block that has them set really is different from the blueprint's.
 * Widening the rule to cover them is a separate decision from this one.
 */
public final class BlockStateMatch {

    /**
     * Properties whose value in the world says nothing about whether the right block is there.
     * Ordered by why they're here: neighbour-derived connection shape, redstone-derived,
     * player-toggled, then the remaining recomputed odds and ends.
     */
    private static final Set<Property<?>> WORLD_DERIVED = Set.of(
            // connection shape, recomputed by Block.updateFromNeighbourShapes
            BlockStateProperties.STAIRS_SHAPE,
            BlockStateProperties.NORTH,
            BlockStateProperties.EAST,
            BlockStateProperties.SOUTH,
            BlockStateProperties.WEST,
            BlockStateProperties.UP,
            BlockStateProperties.DOWN,
            BlockStateProperties.NORTH_WALL,
            BlockStateProperties.EAST_WALL,
            BlockStateProperties.SOUTH_WALL,
            BlockStateProperties.WEST_WALL,
            BlockStateProperties.NORTH_REDSTONE,
            BlockStateProperties.EAST_REDSTONE,
            BlockStateProperties.SOUTH_REDSTONE,
            BlockStateProperties.WEST_REDSTONE,
            BlockStateProperties.CHEST_TYPE,
            BlockStateProperties.IN_WALL,
            // redstone-derived
            BlockStateProperties.POWER,
            BlockStateProperties.POWERED,
            BlockStateProperties.LIT,
            BlockStateProperties.EXTENDED,
            BlockStateProperties.TRIGGERED,
            // player-toggled by using the block
            BlockStateProperties.OPEN,
            // recomputed from support / cover / surroundings
            BlockStateProperties.SNOWY,
            BlockStateProperties.ATTACHED,
            BlockStateProperties.DISTANCE,
            BlockStateProperties.SIGNAL_FIRE,
            BlockStateProperties.MOISTURE);

    private BlockStateMatch() {}

    /**
     * Whether {@code world} already satisfies {@code wanted}: the same block, agreeing on every
     * property except the ones the game owns. Both arguments are expected in final printed
     * orientation (mirror + rotation already applied).
     */
    public static boolean satisfies(BlockState world, BlockState wanted) {
        if (world == wanted) {
            return true;
        }
        if (wanted == null || world.getBlock() != wanted.getBlock()) {
            return false;
        }
        for (Property<?> property : wanted.getProperties()) {
            if (WORLD_DERIVED.contains(property)) {
                continue;
            }
            if (!world.getValue(property).equals(wanted.getValue(property))) {
                return false;
            }
        }
        return true;
    }
}
