package com.yoss.betterintelligence;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
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

import java.util.List;

@EventBusSubscriber(modid = BetterIntelligence.MODID)
public class MobAiModifier {

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof Mob mob && mob instanceof Enemy) {
            mob.goalSelector.addGoal(1, new InvestigateSoundGoal(mob));
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof Level level) || event.isCanceled()) return;
        triggerSoundEvent(level, event.getPos(), 24.0, 0.80);
    }

    @SubscribeEvent
    public static void onBlockChange(BlockEvent.NeighborNotifyEvent event) {
        if (!(event.getLevel() instanceof Level level)) return;
        triggerSoundEvent(level, event.getPos(), 24.0, 0.60);
    }

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Post event) {
        triggerSoundEvent(event.getEntity().level(), event.getEntity().blockPosition(), 32.0, 1.0);
    }

    @SubscribeEvent
    public static void onItemUseFinish(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity() instanceof Player player) {
            net.minecraft.world.item.ItemUseAnimation anim = event.getItem().getUseAnimation();
            if (anim == net.minecraft.world.item.ItemUseAnimation.EAT || anim == net.minecraft.world.item.ItemUseAnimation.DRINK) {
                triggerSoundEvent(player.level(), player.blockPosition(), 16.0, 0.50);
            }
        }
    }

    @SubscribeEvent
    public static void onArrowLoose(ArrowLooseEvent event) {
        triggerSoundEvent(event.getEntity().level(), event.getEntity().blockPosition(), 20.0, 0.75);
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        triggerSoundEvent(event.getProjectile().level(), event.getProjectile().blockPosition(), 24.0, 1.0);
    }

    @SubscribeEvent
    public static void onEquipArmor(LivingEquipmentChangeEvent event) {
        if (event.getEntity() instanceof Player player) {
            triggerSoundEvent(player.level(), player.blockPosition(), 20.0, 0.85);
        }
    }

    @SubscribeEvent
    public static void onPlayerFall(LivingFallEvent event) {
        if (event.getEntity() instanceof Player player && event.getDistance() > 2.0F) {
            double radius = Math.min(32.0, 10.0 + (event.getDistance() * 2));
            triggerSoundEvent(player.level(), player.blockPosition(), radius, 0.90);
        }
    }

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.RightClickBlock event) {
        triggerSoundEvent(event.getLevel(), event.getPos(), 16.0, 0.65);
    }

    // --- EL EVENTO MASIVO DE EXPLOSIÓN ---
    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Start event) {
        BlockPos center = BlockPos.containing(event.getExplosion().center());
        // Radio: 80 | Rango Vertical: 40 | Atraviesa paredes hasta: 50 | Probabilidad: 100%
        triggerSoundEvent(event.getLevel(), center, 80.0, 40.0, 50.0, 1.0);
    }

    // Método estándar para sonidos normales (Mantiene la oclusión de 12 bloques y vertical de 16)
    private static void triggerSoundEvent(Level level, BlockPos sourcePos, double baseRadius, double probability) {
        triggerSoundEvent(level, sourcePos, baseRadius, 16.0, 12.0, probability);
    }

    // --- NUEVO: MOTOR DE AUDICIÓN CON ONDAS DE CHOQUE ---
    private static void triggerSoundEvent(Level level, BlockPos sourcePos, double baseRadius, double verticalRadius, double muffledRadius, double probability) {
        AABB area = new AABB(
                sourcePos.getX() - baseRadius, sourcePos.getY() - verticalRadius, sourcePos.getZ() - baseRadius,
                sourcePos.getX() + baseRadius, sourcePos.getY() + verticalRadius, sourcePos.getZ() + baseRadius
        );

        List<Mob> nearbyMobs = level.getEntitiesOfClass(Mob.class, area, mob -> mob instanceof Enemy);

        for (Mob mob : nearbyMobs) {
            if (level.getRandom().nextDouble() > probability) {
                continue;
            }

            double distanceSqr = mob.distanceToSqr(Vec3.atCenterOf(sourcePos));
            double currentMaxRadius = baseRadius;

            HitResult hitResult = level.clip(new ClipContext(
                    mob.getEyePosition(),
                    Vec3.atCenterOf(sourcePos),
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    mob
            ));

            if (hitResult.getType() == HitResult.Type.BLOCK) {
                currentMaxRadius = muffledRadius;
            }

            if (distanceSqr > (currentMaxRadius * currentMaxRadius)) continue;

            mob.getLookControl().setLookAt(sourcePos.getX(), sourcePos.getY(), sourcePos.getZ(), 30.0F, 30.0F);

            mob.goalSelector.getAvailableGoals().forEach(wrappedGoal -> {
                if (wrappedGoal.getGoal() instanceof InvestigateSoundGoal investigateGoal) {
                    investigateGoal.setInvestigatePosition(sourcePos);
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
                        investigateGoal.setInvestigatePosition(oldTarget.blockPosition());
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
}