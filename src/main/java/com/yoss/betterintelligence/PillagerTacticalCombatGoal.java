package com.yoss.betterintelligence;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

public class PillagerTacticalCombatGoal extends Goal {
    private final Pillager pillager;
    private LivingEntity target;

    public PillagerTacticalCombatGoal(Pillager pillager) {
        this.pillager = pillager;
        // Solo bloqueamos el movimiento para no interferir con su puntería
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        // 1. Detección temprana: ¿Hay un creeper a punto de explotar cerca?
        boolean dangerCreeper = !this.pillager.level().getEntitiesOfClass(
                Creeper.class,
                this.pillager.getBoundingBox().inflate(6.0D),
                c -> c.getSwellDir() > 0 // > 0 significa que su mecha está encendida
        ).isEmpty();

        // 2. Detección de invasión de espacio personal
        this.target = this.pillager.getTarget();
        boolean enemyTooClose = this.target != null && this.target.isAlive() &&
                this.pillager.distanceToSqr(this.target) < 25.0D; // Menos de 5 bloques

        return dangerCreeper || enemyTooClose;
    }

    @Override
    public void tick() {
        // PRIORIDAD MÁXIMA: Huir del Creeper explosivo
        List<Creeper> nearbyCreepers = this.pillager.level().getEntitiesOfClass(
                Creeper.class,
                this.pillager.getBoundingBox().inflate(6.0D),
                c -> c.getSwellDir() > 0
        );

        if (!nearbyCreepers.isEmpty()) {
            Creeper creeper = nearbyCreepers.getFirst();
            Vec3 fleePos = DefaultRandomPos.getPosAway(this.pillager, 10, 7, creeper.position());
            if (fleePos != null) {
                // Huye rápido (velocidad 1.25)
                this.pillager.getNavigation().moveTo(fleePos.x, fleePos.y, fleePos.z, 1.25D);
            }
            this.pillager.getLookControl().setLookAt(creeper, 30.0F, 30.0F);
            return; // Cortamos el tick aquí para priorizar su vida
        }

        // KITING (Combate de Retirada)
        if (this.target != null && this.target.isAlive()) {
            this.pillager.getLookControl().setLookAt(this.target, 30.0F, 30.0F);
            double distanceSq = this.pillager.distanceToSqr(this.target);

            if (distanceSq < 25.0D) {
                // Retrocede tácticamente para evitar los golpes cuerpo a cuerpo
                Vec3 retreatPos = DefaultRandomPos.getPosAway(this.pillager, 8, 4, this.target.position());
                if (retreatPos != null) {
                    this.pillager.getNavigation().moveTo(retreatPos.x, retreatPos.y, retreatPos.z, 1.15D);
                }
            } else {
                // Ya está a salvo, se detiene para disparar su ballesta con precisión
                this.pillager.getNavigation().stop();
            }
        }
    }
}