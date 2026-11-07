package com.yoss.betterintelligence;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.feline.Ocelot;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.ArrowLooseEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.EnumSet;
import java.util.List;

@EventBusSubscriber(modid = BetterIntelligence.MODID)
public class MobAiModifier {

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        // Validación principal: ¿Es un Mob (cualquier criatura que se mueva)?
        if (event.getEntity() instanceof Mob mob) {

            // 1. META UNIVERSAL (Para TODOS los mobs: Hostiles, animales, mascotas)
            mob.goalSelector.addGoal(0, new UniversalExtinguishFireGoal(mob, 1.5D));

            // 2. REGLAS SOLO PARA HOSTILES (Enemigos)
            if (mob instanceof Enemy) {
                mob.setPathfindingMalus(PathType.LAVA, -1.0F);
                mob.setPathfindingMalus(PathType.FIRE, -1.0F);
                mob.setPathfindingMalus(PathType.FIRE_IN_NEIGHBOR, -1.0F);

                // Investigar el origen de los sonidos
                mob.goalSelector.addGoal(1, new InvestigateSoundGoal(mob));
            }

            // 3. REGLAS ESPECÍFICAS PARA EL CREEPER
            if (mob instanceof Creeper creeper) {
                creeper.goalSelector.getAvailableGoals().removeIf(prioritizedGoal ->
                        prioritizedGoal.getGoal() instanceof AvoidEntityGoal
                );

                // Añadimos nuevas metas con velocidad extrema al ver gatos/ocelotes
                creeper.goalSelector.addGoal(3, new AvoidEntityGoal<>(
                        creeper, Cat.class, 14.0F, 1.5D, 2.0D));
                creeper.goalSelector.addGoal(3, new AvoidEntityGoal<>(
                        creeper, Ocelot.class, 14.0F, 1.5D, 2.0D));
            }
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof Level level) || event.isCanceled()) return;
        triggerSoundEvent(level, event.getPos(), 24.0, 0.80, 1);
    }

    @SubscribeEvent
    public static void onBlockChange(BlockEvent.NeighborNotifyEvent event) {
        if (!(event.getLevel() instanceof Level level)) return;
        triggerSoundEvent(level, event.getPos(), 24.0, 0.60, 1);
    }

    // ==========================================================
    // CORRECCIÓN 1: DAÑO AMBIENTAL IGNORADO
    // ==========================================================
    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Post event) {
        // Solo genera alerta máxima (+3) si el daño FUE INFLIGIDO POR UN JUGADOR
        // Esto ignora automáticamente lava, cactus, bayas, fuego, ahogamiento, etc.
        if (event.getSource().getEntity() instanceof Player) {
            triggerSoundEvent(event.getEntity().level(), event.getEntity().blockPosition(), 16.0, 12.0, 1.0, 3);
        }
    }

    @SubscribeEvent
    public static void onItemUseFinish(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity() instanceof Player player) {
            ItemUseAnimation anim = event.getItem().getUseAnimation();
            if (anim == ItemUseAnimation.EAT || anim == ItemUseAnimation.DRINK) {
                triggerSoundEvent(player.level(), player.blockPosition(), 16.0, 0.50, 1);
            }
        }
    }

    @SubscribeEvent
    public static void onArrowLoose(ArrowLooseEvent event) {
        triggerSoundEvent(event.getEntity().level(), event.getEntity().blockPosition(), 20.0, 0.75, 1);
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        triggerSoundEvent(event.getProjectile().level(), event.getProjectile().blockPosition(), 24.0, 1.0, 1);
    }

    @SubscribeEvent
    public static void onEquipArmor(LivingEquipmentChangeEvent event) {
        if (event.getEntity() instanceof Player player) {
            triggerSoundEvent(player.level(), player.blockPosition(), 20.0, 0.85, 1);
        }
    }

    @SubscribeEvent
    public static void onPlayerFall(LivingFallEvent event) {
        if (event.getEntity() instanceof Player player && event.getDistance() > 2.0F) {
            double radius = Math.min(32.0, 10.0 + (event.getDistance() * 2));
            triggerSoundEvent(player.level(), player.blockPosition(), radius, 0.90, 2);
        }
    }

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.RightClickBlock event) {
        triggerSoundEvent(event.getLevel(), event.getPos(), 16.0, 0.65, 1);
    }

    // ==========================================================
    // CORRECCIÓN 2: FILTRO PARA EXPLOSIONES DE CREEPER
    // ==========================================================
    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Start event) {
        BlockPos center = BlockPos.containing(event.getExplosion().center());
        net.minecraft.world.entity.Entity exploder = event.getExplosion().getDirectSourceEntity();

        // Identificamos si el origen de la explosión es un Creeper
        boolean isCreeper = exploder instanceof Creeper;

        triggerExplosionSoundEvent(event.getLevel(), center, 80.0, 40.0, 50.0, 1.0, 3, isCreeper);
    }

    private static void triggerSoundEvent(Level level, BlockPos sourcePos, double baseRadius, double probability, int alertBoost) {
        triggerExplosionSoundEvent(level, sourcePos, baseRadius, 16.0, 12.0, probability, alertBoost, false);
    }

    private static void triggerSoundEvent(Level level, BlockPos sourcePos, double verticalRadius, double muffledRadius, double probability, int alertBoost) {
        triggerExplosionSoundEvent(level, sourcePos, 32.0, verticalRadius, muffledRadius, probability, alertBoost, false);
    }

    // MOTOR ACÚSTICO PRINCIPAL (CON FILTRO DE CREEPER)
    private static void triggerExplosionSoundEvent(Level level, BlockPos sourcePos, double baseRadius, double verticalRadius, double muffledRadius, double probability, int alertBoost, boolean isCreeperExplosion) {
        AABB area = new AABB(
                sourcePos.getX() - baseRadius, sourcePos.getY() - verticalRadius, sourcePos.getZ() - baseRadius,
                sourcePos.getX() + baseRadius, sourcePos.getY() + verticalRadius, sourcePos.getZ() + baseRadius
        );
        List<Mob> nearbyMobs = level.getEntitiesOfClass(Mob.class, area, mob -> mob instanceof Enemy);

        for (Mob mob : nearbyMobs) {
            if (level.getRandom().nextDouble() > probability) continue;

            // EL FILTRO VANILLA: Si es explosión de Creeper, SOLO reaccionan estos mobs
            if (isCreeperExplosion) {
                boolean isInterested = mob instanceof Spider ||
                        mob instanceof AbstractSkeleton ||
                        mob instanceof Zombie ||
                        mob instanceof Creeper;
                if (!isInterested) continue;
            }

            double distanceSqr = mob.distanceToSqr(Vec3.atCenterOf(sourcePos));
            double currentMaxRadius = baseRadius;

            HitResult hitResult = level.clip(new ClipContext(
                    mob.getEyePosition(), Vec3.atCenterOf(sourcePos),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob
            ));

            if (hitResult.getType() == HitResult.Type.BLOCK) {
                currentMaxRadius = muffledRadius;
            }

            if (distanceSqr > (currentMaxRadius * currentMaxRadius)) continue;

            mob.getLookControl().setLookAt(sourcePos.getX(), sourcePos.getY(), sourcePos.getZ(), 30.0F, 30.0F);
            mob.goalSelector.getAvailableGoals().forEach(wrappedGoal -> {
                if (wrappedGoal.getGoal() instanceof InvestigateSoundGoal investigateGoal) {
                    investigateGoal.addSuspicion(sourcePos, alertBoost);
                }
            });
        }
    }

    @SubscribeEvent
    public static void onMobTargetChange(LivingChangeTargetEvent event) {
        if (event.getEntity() instanceof Mob mob && mob instanceof Enemy) {
            var newTarget = event.getNewAboutToBeSetTarget();
            var oldTarget = mob.getTarget();

            if (newTarget == null && oldTarget instanceof Player) {
                mob.goalSelector.getAvailableGoals().forEach(wrappedGoal -> {
                    if (wrappedGoal.getGoal() instanceof InvestigateSoundGoal investigateGoal) {
                        investigateGoal.addSuspicion(oldTarget.blockPosition(), 2);
                    }
                });
                return;
            }

            if (newTarget instanceof Player player) {
                double distanceSqr = mob.distanceToSqr(player);
                if (distanceSqr < 9.0) return;

                if (player.isShiftKeyDown()) {
                    Vec3 mobLookDirection = mob.getViewVector(1.0F);
                    Vec3 vectorToPlayer = player.position().subtract(mob.position()).normalize();

                    if (mobLookDirection.dot(vectorToPlayer) < 0.5) {
                        event.setNewAboutToBeSetTarget(null);
                    }
                }
            }
        }
    }

    // ==========================================================
    // FORMACIÓN TÁCTICA: ANTI-APILAMIENTO (CROWD CONTROL)
    // ==========================================================
    @SubscribeEvent
    public static void onEnemyCrowdControl(EntityTickEvent.Pre event) {
        if (event.getEntity() instanceof Mob mob && mob instanceof net.minecraft.world.entity.monster.Enemy && !mob.level().isClientSide()) {

            // Reducimos la caja de detección para actuar solo si ya se están atravesando
            java.util.List<Mob> crowd = mob.level().getEntitiesOfClass(
                    Mob.class,
                    mob.getBoundingBox().inflate(0.4D),
                    e -> e instanceof net.minecraft.world.entity.monster.Enemy && e != mob
            );

            if (!crowd.isEmpty()) {
                for (Mob ally : crowd) {
                    double dX = mob.getX() - ally.getX();
                    double dZ = mob.getZ() - ally.getZ();
                    double dist = Math.sqrt(dX * dX + dZ * dZ);

                    if (dist < 0.8D) { // 0.8 significa que están fusionados

                        // Si por algún motivo están en el mismo pixel exacto, creamos una diferencia falsa
                        if (dist == 0.0) {
                            dX = (mob.getRandom().nextDouble() - 0.5) * 0.1;
                            dZ = (mob.getRandom().nextDouble() - 0.5) * 0.1;
                            dist = Math.sqrt(dX * dX + dZ * dZ);
                        }

                        // EMPUJE FÍSICO DURO
                        double force = (1.0D - dist) * 0.6D;
                        mob.push((dX / dist) * force, 0.0D, (dZ / dist) * force);

                        // TRUCO TÉCNICO: Para evitar que el motor Vanilla anule nuestro empuje,
                        // detenemos las piernas de uno de los dos usando su ID como desempate.
                        if (mob.getId() > ally.getId()) {
                            mob.getNavigation().stop();
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onEquipChange(net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent event) {
        if (event.getEntity() instanceof Player player && event.getSlot() == net.minecraft.world.entity.EquipmentSlot.MAINHAND) {
            if (player.level().getRandom().nextFloat() < 0.01F) {
                triggerSoundEvent(player.level(), player.blockPosition(), 12.0, 1.0, 1);
            }
        }
    }

    public static class UniversalExtinguishFireGoal extends Goal {
        private final Mob mob;
        private final double speed;
        private BlockPos waterPos;

        public UniversalExtinguishFireGoal(Mob mob, double speed) {
            this.mob = mob;
            this.speed = speed;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            // Si no se está quemando y no está pisando fuego, no la usa
            if (!this.mob.isOnFire() && !this.mob.level().getBlockState(this.mob.blockPosition()).is(net.minecraft.world.level.block.Blocks.FIRE)) return false;

            this.waterPos = findWater();
            return true; // Huye incluso si no hay agua, corriendo al azar
        }

        @Override
        public void tick() {
            if (this.waterPos != null) {
                // Va directo al agua
                this.mob.getNavigation().moveTo(this.waterPos.getX(), this.waterPos.getY(), this.waterPos.getZ(), this.speed);
            } else {
                // Si no encuentra agua, corre erráticamente huyendo del bloque donde se quemó
                if (this.mob.getNavigation().isDone()) {
                    double rX = this.mob.getX() + (this.mob.getRandom().nextDouble() - 0.5) * 16.0;
                    double rZ = this.mob.getZ() + (this.mob.getRandom().nextDouble() - 0.5) * 16.0;
                    this.mob.getNavigation().moveTo(rX, this.mob.getY(), rZ, this.speed);
                }
            }
        }

        private BlockPos findWater() {
            BlockPos mobPos = this.mob.blockPosition();
            for (int x = -10; x <= 10; x++) {
                for (int y = -4; y <= 4; y++) {
                    for (int z = -10; z <= 10; z++) {
                        BlockPos checkPos = mobPos.offset(x, y, z);
                        if (this.mob.level().getFluidState(checkPos).is(net.minecraft.tags.FluidTags.WATER)) {
                            return checkPos;
                        }
                    }
                }
            }
            return null;
        }
    }
}