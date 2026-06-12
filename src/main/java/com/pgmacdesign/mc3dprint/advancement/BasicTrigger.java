package com.pgmacdesign.mc3dprint.advancement;

import com.google.gson.JsonObject;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * A conditionless criterion trigger: the advancement fires the moment the mod
 * code calls {@link #trigger}. Used for events vanilla criteria can't express
 * (first item print, first structure print, scanning, FU conversion, loot
 * disc discovery).
 */
public class BasicTrigger extends SimpleCriterionTrigger<BasicTrigger.Instance> {
    private final ResourceLocation id;

    public BasicTrigger(ResourceLocation id) {
        this.id = id;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    protected Instance createInstance(JsonObject json, ContextAwarePredicate predicate,
                                      DeserializationContext context) {
        return new Instance(id, predicate);
    }

    public void trigger(ServerPlayer player) {
        this.trigger(player, instance -> true);
    }

    public static class Instance extends AbstractCriterionTriggerInstance {
        public Instance(ResourceLocation id, ContextAwarePredicate predicate) {
            super(id, predicate);
        }
    }
}
