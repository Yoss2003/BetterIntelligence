package com.yoss.betterintelligence.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.neoforged.neoforge.client.extensions.common.IClientMobEffectExtensions;

import java.util.function.Consumer;

public class TacticalImmunityEffect extends MobEffect {

    public TacticalImmunityEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    public void initializeClient(Consumer<IClientMobEffectExtensions> consumer) {
        consumer.accept(new IClientMobEffectExtensions() {
            @Override
            public boolean isVisibleInGui(net.minecraft.world.effect.MobEffectInstance instance) {
                return true; // Asegura visibilidad nativa
            }
        });
    }
}