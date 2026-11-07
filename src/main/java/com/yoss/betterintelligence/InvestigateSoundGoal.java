package com.yoss.betterintelligence;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.feline.Ocelot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class InvestigateSoundGoal extends Goal {
    private final Mob mob;
    private BlockPos targetPos;
    private BlockPos patrolPos;
    private int alertLevel = 0;
    private int cooldownTicks = 0;
    private int stareTimer = 0;

    // Variables para coordinar la mirada aleatoria de búsqueda
    private double lookX, lookY, lookZ;

    public InvestigateSoundGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    public void addSuspicion(BlockPos pos, int suspicionLevel) {
        if (this.mob.getTarget() != null) return;
        this.targetPos = pos;
        this.patrolPos = null;
        this.cooldownTicks = 200;
        this.stareTimer = 60 + this.mob.getRandom().nextInt(40);

        this.alertLevel += suspicionLevel;
        if (this.alertLevel > 3) this.alertLevel = 3;

        if (this.alertLevel >= 2) {
            this.mob.getNavigation().moveTo(pos.getX(), pos.getY(), pos.getZ(), 1.25D);
        }
    }

    @Override
    public boolean canUse() {
        if (this.targetPos == null && this.cooldownTicks > 0) this.cooldownTicks--;
        if (this.cooldownTicks <= 0) {
            this.alertLevel = 0;
            this.targetPos = null;
        }
        return this.targetPos != null && this.mob.getTarget() == null;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.targetPos == null || this.mob.getTarget() != null || this.cooldownTicks <= 0) return false;

        // ==========================================================
        // SUPERVIVENCIA ABSOLUTA: El Creeper aborta si hay un gato
        // ==========================================================
        if (this.mob instanceof Creeper) {
            boolean hasCat = !this.mob.level().getEntitiesOfClass(Cat.class, this.mob.getBoundingBox().inflate(12.0D)).isEmpty() ||
                    !this.mob.level().getEntitiesOfClass(Ocelot.class, this.mob.getBoundingBox().inflate(12.0D)).isEmpty();
            if (hasCat) return false; // Rompe la meta de sonido para activar la huida nativa
        }

        return true;
    }

    @Override
    public void tick() {
        if (this.targetPos == null) return;
        if (this.cooldownTicks > 0) this.cooldownTicks--;

        Player closestPlayer = this.mob.level().getNearestPlayer(this.mob, 16.0);
        if (closestPlayer != null && !closestPlayer.isCreative() && !closestPlayer.isSpectator()) {
            if (this.mob.getSensing().hasLineOfSight(closestPlayer)) {
                if (!closestPlayer.isShiftKeyDown() || this.mob.distanceToSqr(closestPlayer) < 25.0) {
                    this.mob.setTarget(closestPlayer);
                    this.stop();
                    return;
                }
            }
        }

        double distanceToOrigin = this.mob.distanceToSqr(Vec3.atCenterOf(this.targetPos));

        // Escáner de Peligro (Lava/Fuego)
        double safeDistanceSqr = 4.0;
        boolean hasHazard = false;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockState state = this.mob.level().getBlockState(this.targetPos.offset(dx, dy, dz));
                    if (state.is(Blocks.LAVA) || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CAMPFIRE)) {
                        hasHazard = true; break;
                    }
                }
                if (hasHazard) break;
            }
            if (hasHazard) break;
        }
        if (hasHazard) safeDistanceSqr = 25.0;

        // ==========================================================
        // MODO BÚSQUEDA Y PATRULLAJE
        // ==========================================================
        if (this.alertLevel == 1 || (this.alertLevel == 3 && distanceToOrigin <= safeDistanceSqr)) {
            // Frena en seco
            this.mob.getNavigation().stop();

            if (this.stareTimer > 0) {
                // Cada 15 ticks (menos de un segundo), elige un nuevo punto aleatorio para mirar
                if (this.stareTimer % 15 == 0) {
                    this.lookX = this.mob.getX() + (this.mob.getRandom().nextDouble() - 0.5) * 12.0;
                    this.lookY = this.mob.getEyeY() + (this.mob.getRandom().nextDouble() - 0.5) * 4.0;
                    this.lookZ = this.mob.getZ() + (this.mob.getRandom().nextDouble() - 0.5) * 12.0;
                }
                // Aplica el giro de cabeza buscando
                this.mob.getLookControl().setLookAt(this.lookX, this.lookY, this.lookZ, 30.0F, 30.0F);
                this.stareTimer--;
            } else {
                this.stop(); // Terminó de buscar, vuelve a su rutina
            }
        }
        else {
            // Mientras camina hacia el objetivo, mira fijamente el punto de origen
            this.mob.getLookControl().setLookAt(this.targetPos.getX(), this.targetPos.getY(), this.targetPos.getZ(), 30.0F, 30.0F);

            double dX = this.targetPos.getX() - this.mob.getX();
            double dZ = this.targetPos.getZ() - this.mob.getZ();
            float yaw = (float)(Math.atan2(dZ, dX) * (180D / Math.PI)) - 90.0F;
            this.mob.yBodyRot = this.mob.yBodyRot + (yaw - this.mob.yBodyRot) * 0.2F;

            if (this.alertLevel == 2) {
                if (this.patrolPos == null || this.mob.getNavigation().isDone()) {
                    for (int i = 0; i < 5; i++) {
                        double rX = this.targetPos.getX() + (this.mob.getRandom().nextDouble() - 0.5) * 12.0;
                        double rZ = this.targetPos.getZ() + (this.mob.getRandom().nextDouble() - 0.5) * 12.0;
                        BlockPos checkPos = BlockPos.containing(rX, this.targetPos.getY(), rZ);
                        BlockState floor = this.mob.level().getBlockState(checkPos.below());
                        BlockState feet = this.mob.level().getBlockState(checkPos);
                        if (!floor.is(Blocks.LAVA) && !floor.is(Blocks.MAGMA_BLOCK) && !feet.is(Blocks.LAVA) && !feet.is(Blocks.FIRE)) {
                            this.patrolPos = checkPos; break;
                        }
                    }
                    if (this.patrolPos != null) this.mob.getNavigation().moveTo(this.patrolPos.getX(), this.patrolPos.getY(), this.patrolPos.getZ(), 0.9D);
                }
            }
            else if (this.alertLevel == 3) {
                this.mob.getNavigation().moveTo(this.targetPos.getX(), this.targetPos.getY(), this.targetPos.getZ(), 1.25D);
            }
        }
    }

    @Override
    public void stop() {
        this.targetPos = null;
        this.patrolPos = null;
        this.alertLevel = 0;
        this.cooldownTicks = 0;
        this.stareTimer = 0;
        this.mob.getNavigation().stop();
    }
}