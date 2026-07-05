package com.pgmacdesign.mc3dprint.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
//? if >=26.2 {
/*import net.minecraft.advancements.predicates.ContextAwarePredicate;
import net.minecraft.advancements.predicates.entity.EntityPredicate;
import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
*///?} elif >=1.21.11 {
/*import net.minecraft.advancements.criterion.ContextAwarePredicate;
import net.minecraft.advancements.criterion.EntityPredicate;
import net.minecraft.advancements.criterion.SimpleCriterionTrigger;
*///?} else {
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
//?}
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * A conditionless criterion trigger: the advancement fires the moment the mod
 * code calls {@link #trigger(ServerPlayer)}. Used for events vanilla criteria
 * can't express (first item print, first structure print, scanning, FU
 * conversion, loot disc discovery).
 */
public class BasicTrigger extends SimpleCriterionTrigger<BasicTrigger.Instance> {

    @Override
    public Codec<Instance> codec() {
        return Instance.CODEC;
    }

    /** Fires the trigger for {@code player} (the instance predicate is always satisfied). */
    public void trigger(ServerPlayer player) {
        this.trigger(player, instance -> true);
    }

    public record Instance(Optional<ContextAwarePredicate> player)
            implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<Instance> CODEC = RecordCodecBuilder.create(i -> i.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player)
        ).apply(i, Instance::new));
    }
}
