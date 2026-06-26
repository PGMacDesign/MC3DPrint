package com.pgmacdesign.mc3dprint.compat;

import net.minecraft.world.InteractionResult;
//? if >=1.21.5 {
/*import net.minecraft.world.item.ItemStack;
*///?} else {
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;
//?}

/**
 * Version seam for the block/item interaction API. The 1.21.2–1.21.4 rewrite unified the
 * interaction result types: {@code Block.useItemOn} returned {@code ItemInteractionResult}
 * pre-1.21.4 and now returns {@code InteractionResult}; {@code InteractionResult.sidedSuccess}
 * was dropped (the unified {@code SUCCESS} auto-sides); and {@code Item.use} returned
 * {@code InteractionResultHolder<ItemStack>} but now returns a bare {@code InteractionResult}
 * (the resulting stack is taken from the player's hand, mutated in place).
 *
 * <p>Call sites reference the {@code ITEM_*} constants (whose static type is the version-correct
 * result type) and the {@code holder*} / {@code sidedSuccess} helpers; the only divergence lives
 * here. The {@code useItemOn} / {@code use} override signatures still need a per-method
 * Stonecutter guard on their RETURN TYPE, but their bodies stay version-agnostic.
 */
public final class InteractionCompat {
    private InteractionCompat() {}

    // ---- Block.useItemOn results. Static type tracks the version's useItemOn return type so a
    //      guarded-signature override can `return InteractionCompat.ITEM_*` with no inner guard. ----
    //? if >=1.21.5 {
    /*public static final InteractionResult ITEM_SUCCESS = InteractionResult.SUCCESS;
    public static final InteractionResult ITEM_CONSUME = InteractionResult.CONSUME;
    public static final InteractionResult ITEM_FAIL = InteractionResult.FAIL;
    public static final InteractionResult ITEM_PASS = InteractionResult.TRY_WITH_EMPTY_HAND;
    *///?} else {
    public static final ItemInteractionResult ITEM_SUCCESS = ItemInteractionResult.SUCCESS;
    public static final ItemInteractionResult ITEM_CONSUME = ItemInteractionResult.CONSUME;
    public static final ItemInteractionResult ITEM_FAIL = ItemInteractionResult.FAIL;
    public static final ItemInteractionResult ITEM_PASS = ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    //?}

    /**
     * Block empty-hand interaction success that swings the arm client-side. 1.21.5 removed
     * {@code sidedSuccess}; the unified {@code SUCCESS} handles siding itself.
     */
    public static InteractionResult sidedSuccess(boolean isClientSide) {
        //? if >=1.21.5 {
        /*return InteractionResult.SUCCESS;
        *///?} else {
        return InteractionResult.sidedSuccess(isClientSide);
        //?}
    }

    // ---- Item.use results. 1.21.4 dropped InteractionResultHolder<ItemStack>; use() now returns
    //      a bare InteractionResult. Static type tracks the version's use() return type. ----
    //? if >=1.21.5 {
    /*public static InteractionResult holderSuccess(ItemStack stack) { return InteractionResult.SUCCESS; }
    public static InteractionResult holderSuccess(ItemStack stack, boolean isClientSide) { return InteractionResult.SUCCESS; }
    public static InteractionResult holderConsume(ItemStack stack) { return InteractionResult.CONSUME; }
    public static InteractionResult holderFail(ItemStack stack) { return InteractionResult.FAIL; }
    public static InteractionResult holderPass(ItemStack stack) { return InteractionResult.PASS; }
    *///?} else {
    public static InteractionResultHolder<ItemStack> holderSuccess(ItemStack stack) { return InteractionResultHolder.sidedSuccess(stack, true); }
    public static InteractionResultHolder<ItemStack> holderSuccess(ItemStack stack, boolean isClientSide) { return InteractionResultHolder.sidedSuccess(stack, isClientSide); }
    public static InteractionResultHolder<ItemStack> holderConsume(ItemStack stack) { return InteractionResultHolder.consume(stack); }
    public static InteractionResultHolder<ItemStack> holderFail(ItemStack stack) { return InteractionResultHolder.fail(stack); }
    public static InteractionResultHolder<ItemStack> holderPass(ItemStack stack) { return InteractionResultHolder.pass(stack); }
    //?}
}
