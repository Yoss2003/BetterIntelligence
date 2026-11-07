package com.yoss.betterintelligence;

import com.yoss.betterintelligence.registry.ModPotions;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;

public class DrinkImmunityPotionGoal extends Goal {
    private final Mob mob;
    private boolean isDrinking;
    private int drinkTime;

    // Variable para guardar el arma original (como la ballesta del saqueador)
    private ItemStack originalWeapon = ItemStack.EMPTY;

    public DrinkImmunityPotionGoal(Mob mob) {
        this.mob = mob;
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.mob.getTarget();
        return target != null && target.isAlive() && !this.mob.hasEffect(ModPotions.TACTICAL_IMMUNITY);
    }

    @Override
    public void start() {
        this.isDrinking = true;
        this.drinkTime = 0;

        // 1. Guardamos el arma que tenía en la mano (ej. Ballesta) para no perderla
        this.originalWeapon = this.mob.getMainHandItem().copy();

        // 2. Preparamos y colocamos la poción dorada en la mano principal
        ItemStack potionStack = new ItemStack(Items.POTION);
        potionStack.set(DataComponents.POTION_CONTENTS, new PotionContents(ModPotions.IMMUNITY_POTION));
        this.mob.setItemInHand(InteractionHand.MAIN_HAND, potionStack);
    }

    @Override
    public void tick() {
        if (this.isDrinking) {
            this.drinkTime++;
            // Tarda ~32 ticks en terminar de beber
            if (this.drinkTime >= 32) {
                // Aplicamos el efecto de inmunidad por 3 minutos (3600 ticks)
                this.mob.addEffect(new MobEffectInstance(ModPotions.TACTICAL_IMMUNITY, 3600, 0));

                // Finalizamos el proceso de beber y restauramos su arma original
                finishDrinking();
            }
        }
    }

    private void finishDrinking() {
        this.isDrinking = false;

        // Devolvemos su ballesta u original arma a la mano para que pueda disparar de nuevo
        if (!this.originalWeapon.isEmpty()) {
            this.mob.setItemInHand(InteractionHand.MAIN_HAND, this.originalWeapon);
        } else {
            this.mob.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }

        // Debug visual
        this.mob.setCustomName(Component.literal("§6[IA State]: ¡Inmunidad y Arma Lista!"));
        this.mob.setCustomNameVisible(true);
    }

    @Override
    public boolean canContinueToUse() {
        return this.isDrinking;
    }

    @Override
    public void stop() {
        if (this.isDrinking) {
            finishDrinking();
        }
    }
}