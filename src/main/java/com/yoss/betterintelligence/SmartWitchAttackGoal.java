package com.yoss.betterintelligence;

import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownLingeringPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class SmartWitchAttackGoal extends Goal {
    private final Witch witch;
    private LivingEntity target;
    private int attackTime = -1;
    private final int attackInterval;

    public SmartWitchAttackGoal(Witch witch, int attackInterval) {
        this.witch = witch;
        this.attackInterval = attackInterval;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity living = this.witch.getTarget();
        if (living == null || !living.isAlive()) {
            return false;
        }
        this.target = living;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse() || (this.target != null && this.target.isAlive() && !this.witch.getNavigation().isDone());
    }

    @Override
    public void stop() {
        this.target = null;
        this.attackTime = -1;
    }

    @Override
    public void tick() {
        // --- EVASIÓN TÁCTICA DE CREEPERS ACTIVOS ---
        for (Creeper creeper : this.witch.level().getEntitiesOfClass(
                net.minecraft.world.entity.monster.Creeper.class,
                this.witch.getBoundingBox().inflate(6.0D))) {
            if (creeper.isIgnited() || creeper.getSwellDir() > 0) {
                Vec3 fleeDir = this.witch.position().subtract(creeper.position()).normalize();
                // Huye a velocidad de sprint (1.6D)
                this.witch.getNavigation().moveTo(this.witch.getX() + fleeDir.x * 6.0D, this.witch.getY(), this.witch.getZ() + fleeDir.z * 6.0D, 1.6D);
                return;
            }
        }

        double distSq = this.witch.distanceToSqr(this.target);
        boolean hasLineOfSight = this.witch.getSensing().hasLineOfSight(this.target);

        // --- REPOSICIONAMIENTO / KITING CONTRA HORDAS ---
        if (hasLineOfSight) {
            if (distSq < 64.0D) { // Si el enemigo está a menos de 8 bloques
                Vec3 retreatDir = this.witch.position().subtract(this.target.position()).normalize();
                // Retrocede rápido (1.6D) para mantener la distancia
                this.witch.getNavigation().moveTo(this.witch.getX() + retreatDir.x * 6.0D, this.witch.getY(), this.witch.getZ() + retreatDir.z * 6.0D, 1.6D);
            } else {
                this.witch.getNavigation().stop();
            }
        } else {
            this.witch.getNavigation().moveTo(this.target, 1.0D);
        }

        this.witch.getLookControl().setLookAt(this.target, 30.0F, 30.0F);

        if (--this.attackTime <= 0) {
            if (distSq <= 100.0D && hasLineOfSight) {
                performSmartPotionAttack(this.target);
                this.attackTime = this.attackInterval;
            }
        }
    }

    private void performSmartPotionAttack(LivingEntity target) {
        double distance = this.witch.distanceTo(target);
        double targetX = 0, targetY = 0, targetZ = 0, horizontalDist = 0;

        if (distance > 4.0D) {
            double flightTime = distance / 20.0D;
            Vec3 movement = target.getDeltaMovement();
            double predictedX = target.getX() + movement.x * flightTime * 1.5D;
            double predictedZ = target.getZ() + movement.z * flightTime * 1.5D;

            targetX = predictedX - this.witch.getX();
            targetY = (target.getEyeY() - 1.1F) - this.witch.getY();
            targetZ = predictedZ - this.witch.getZ();
            horizontalDist = Math.sqrt(targetX * targetX + targetZ * targetZ);
        }

        if (target instanceof Enderman) {
            ItemStack lingeringStack = new ItemStack(Items.LINGERING_POTION);
            lingeringStack.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.HARMING));

            ThrownLingeringPotion lingeringPotion = new ThrownLingeringPotion(this.witch.level(), this.witch, lingeringStack);

            if (distance <= 4.0D) {
                lingeringPotion.setXRot(lingeringPotion.getXRot() + 80.0F);
                lingeringPotion.shoot(0.0D, -1.0D, 0.0D, 0.3F, 8.0F);
            } else {
                lingeringPotion.setXRot(lingeringPotion.getXRot() + 20.0F);
                lingeringPotion.shoot(targetX, targetY + horizontalDist * 0.2D, targetZ, 0.75F, 8.0F);
            }

            target.setCustomName(net.minecraft.network.chat.Component.literal("§c[DEBUG] Nube (Enderman)"));
            target.setCustomNameVisible(true);
            playThrowSound();
            this.witch.level().addFreshEntity(lingeringPotion);
            return;
        }

        ItemStack potionStack = new ItemStack(Items.SPLASH_POTION);
        String effectName = "Desconocido";

        if (target.isInvertedHealAndHarm()) {
            if (target.getHealth() > 4.0F) {
                potionStack.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.HEALING));
                effectName = "Curación (Daño)";
            } else {
                potionStack.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.STRONG_HEALING));
                effectName = "Curación Letal";
            }
        } else if (target instanceof IronGolem || target.getArmorValue() > 15) {
            if (!target.hasEffect(net.minecraft.world.effect.MobEffects.WEAKNESS)) {
                potionStack.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.WEAKNESS));
                effectName = "Debilidad";
            } else {
                potionStack.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.HARMING));
                effectName = "Daño Instantáneo";
            }
        } else if (target instanceof Spider || target.fireImmune()) {
            if (distance > 7.0D && !target.hasEffect(net.minecraft.world.effect.MobEffects.SLOWNESS)) {
                potionStack.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.SLOWNESS));
                effectName = "Lentitud";
            } else {
                potionStack.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.HARMING));
                effectName = "Daño Instantáneo";
            }
        } else {
            if (distance > 8.0D && !target.hasEffect(net.minecraft.world.effect.MobEffects.SLOWNESS)) {
                potionStack.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.SLOWNESS));
                effectName = "Lentitud";
            } else if (target.getHealth() >= 8.0F && !target.hasEffect(net.minecraft.world.effect.MobEffects.POISON)) {
                potionStack.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.POISON));
                effectName = "Veneno";
            } else {
                potionStack.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.HARMING));
                effectName = "Daño Instantáneo";
            }
        }

        target.setCustomName(net.minecraft.network.chat.Component.literal("§e[Bruja -> Ataca]: §6" + effectName));
        target.setCustomNameVisible(true);

        this.witch.setCustomName(net.minecraft.network.chat.Component.literal("§a[Bruja State]: §f" + effectName));
        this.witch.setCustomNameVisible(true);

        ThrownSplashPotion thrownSplashPotion = new ThrownSplashPotion(this.witch.level(), this.witch, potionStack);

        if (distance <= 4.0D) {
            thrownSplashPotion.setXRot(thrownSplashPotion.getXRot() + 80.0F);
            thrownSplashPotion.shoot(0.0D, -1.0D, 0.0D, 0.3F, 8.0F);
        } else {
            thrownSplashPotion.setXRot(thrownSplashPotion.getXRot() + 20.0F);
            thrownSplashPotion.shoot(targetX, targetY + horizontalDist * 0.2D, targetZ, 0.75F, 8.0F);
        }

        playThrowSound();
        this.witch.level().addFreshEntity(thrownSplashPotion);
    }

    private void playThrowSound() {
        this.witch.level().playSound(
                null,
                this.witch.getX(), this.witch.getY(), this.witch.getZ(),
                SoundEvents.WITCH_THROW,
                this.witch.getSoundSource(),
                1.0F,
                0.8F + this.witch.getRandom().nextFloat() * 0.4F
        );
    }
}