package com.yoss.betterintelligence.registry;

import com.yoss.betterintelligence.effect.TacticalImmunityEffect;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.Potion;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public class ModPotions {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, "betterintelligence");
    public static final DeferredRegister<Potion> POTIONS = DeferredRegister.create(Registries.POTION, "betterintelligence");

    public static final DeferredHolder<MobEffect, MobEffect> TACTICAL_IMMUNITY = EFFECTS.register("tactical_immunity",
            () -> new TacticalImmunityEffect(MobEffectCategory.BENEFICIAL, 0x7D26CD));

    // INMUNIDAD I (Base): 3 Minutos (3600 ticks)
    public static final DeferredHolder<Potion, Potion> IMMUNITY_POTION = POTIONS.register("immunity",
            () -> new Potion("immunity", new MobEffectInstance(TACTICAL_IMMUNITY, 3600, 0)));

    // INMUNIDAD I (Larga): 8 Minutos (9600 ticks) - Se logra con Redstone
    public static final DeferredHolder<Potion, Potion> LONG_IMMUNITY_POTION = POTIONS.register("long_immunity",
            () -> new Potion("immunity", new MobEffectInstance(TACTICAL_IMMUNITY, 9600, 0)));

    // INMUNIDAD II (Fuerte): 1.5 Minutos (1800 ticks) - Se logra con Piedra Luminosa
    public static final DeferredHolder<Potion, Potion> STRONG_IMMUNITY_POTION = POTIONS.register("strong_immunity",
            () -> new Potion("immunity", new MobEffectInstance(TACTICAL_IMMUNITY, 1800, 1))); // <-- El '1' indica Nivel II
}