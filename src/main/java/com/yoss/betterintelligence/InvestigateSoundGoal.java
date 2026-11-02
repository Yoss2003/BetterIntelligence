package com.yoss.betterintelligence;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import java.util.EnumSet;

public class InvestigateSoundGoal extends Goal {
    private final Mob mob;
    private BlockPos targetPos;
    private BlockPos patrolPos; // Nueva variable para el patrullaje
    private int searchTicks = 0;
    private int cooldownTicks = 0;

    public InvestigateSoundGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    public void setInvestigatePosition(BlockPos pos) {
        if (this.mob.getTarget() != null) return;
        if (this.cooldownTicks > 0) return;

        this.targetPos = pos;
        this.patrolPos = null; // Reiniciamos el patrullaje al escuchar un nuevo ruido
        this.searchTicks = 0;

        // Caminan rápido hacia el nuevo ruido
        this.mob.getNavigation().moveTo(pos.getX(), pos.getY(), pos.getZ(), 1.15D);

        double distanceSqr = this.mob.distanceToSqr(Vec3.atCenterOf(pos));
        double distance = Math.sqrt(distanceSqr);

        if (distance >= 16.0D) {
            this.cooldownTicks = 160;
        } else if (distance >= 8.0D) {
            this.cooldownTicks = 60;
        } else {
            this.cooldownTicks = 0;
        }
    }

    @Override
    public boolean canUse() {
        if (this.targetPos == null && this.cooldownTicks > 0) {
            this.cooldownTicks--;
        }
        return this.targetPos != null && this.mob.getTarget() == null;
    }

    @Override
    public boolean canContinueToUse() {
        // Aumenté un poco el tiempo para que patrullen por más tiempo (200 ticks = 10 segundos)
        return this.targetPos != null && this.mob.getTarget() == null && this.searchTicks < 200;
    }

    @Override
    public void start() {
        if (this.targetPos != null) {
            this.mob.getNavigation().moveTo(this.targetPos.getX(), this.targetPos.getY(), this.targetPos.getZ(), 1.15D);
        }
    }

    @Override
    public void tick() {
        if (this.targetPos == null) return;
        if (this.cooldownTicks > 0) this.cooldownTicks--;

        // 1. ESCÁNER ANTI-CEGUERA: Revisa activamente si tiene al jugador enfrente
        Player closestPlayer = this.mob.level().getNearestPlayer(this.mob, 16.0);
        if (closestPlayer != null && this.mob.getSensing().hasLineOfSight(closestPlayer)) {
            // Si el jugador NO está agachado, o si está demasiado cerca, ataca de inmediato
            if (!closestPlayer.isShiftKeyDown() || this.mob.distanceToSqr(closestPlayer) < 25.0) {
                this.mob.setTarget(closestPlayer);
                this.targetPos = null; // Aborta la investigación
                return;
            }
        }

        double distanceToOrigin = this.mob.distanceToSqr(Vec3.atCenterOf(this.targetPos));

        // 2. FASE DE ACERCAMIENTO
        if (distanceToOrigin > 25.0D && this.patrolPos == null) {
            if (this.mob.getNavigation().isDone()) {
                this.mob.getNavigation().moveTo(this.targetPos.getX(), this.targetPos.getY(), this.targetPos.getZ(), 1.15D);
            }
        }
        // 3. FASE DE PATRULLAJE (Cuando llega a la zona del ruido)
        else {
            this.searchTicks++;

            // Si no tiene punto de patrulla, o ya llegó al anterior, elige uno nuevo
            if (this.patrolPos == null || this.mob.getNavigation().isDone()) {

                // Elige un punto al azar a 6 bloques de distancia del ruido original
                double randomX = this.targetPos.getX() + (this.mob.getRandom().nextDouble() - 0.5) * 12.0;
                double randomZ = this.targetPos.getZ() + (this.mob.getRandom().nextDouble() - 0.5) * 12.0;

                this.patrolPos = BlockPos.containing(randomX, this.targetPos.getY(), randomZ);

                // Camina más lento (0.85D) simulando que está buscando cuidadosamente
                this.mob.getNavigation().moveTo(this.patrolPos.getX(), this.patrolPos.getY(), this.patrolPos.getZ(), 0.85D);
            }
        }
    }

    @Override
    public void stop() {
        this.targetPos = null;
        this.patrolPos = null;
        this.searchTicks = 0;
        this.mob.getNavigation().stop();
    }
}