package com.yoss.betterintelligence;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.particles.ParticleTypes;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = "betterintelligence")
public class ParrotDroneSystem {

    private static final Map<UUID, Long> PARROT_COOLDOWNS = new HashMap<>();
    private static final Map<UUID, Long> PLAYER_HEAL_BUFFER = new HashMap<>();
    private static final Map<UUID, Float> SECRET_FALL_TRACKER = new HashMap<>();
    private static final Set<UUID> HAD_PARROT_LAST_TICK = new HashSet<>();
    private static final Map<UUID, Long> JUST_HEALED_FROM_SHOULDER = new HashMap<>();

    private static boolean hasParrotOnShoulder(Player player) {
        return player.getShoulderParrotLeft().isPresent() ||
                player.getShoulderParrotRight().isPresent();
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Pre event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;

        UUID playerId = player.getUUID();
        long currentTime = player.level().getGameTime();
        boolean currentlyHasParrot = hasParrotOnShoulder(player);
        boolean hadParrot = HAD_PARROT_LAST_TICK.contains(playerId);

        // --- 1. DETECTOR DE DESPLIEGUE ---
        if (hadParrot && !currentlyHasParrot) {
            if (player.level() instanceof ServerLevel serverLevel) {
                List<Parrot> droppedParrots = serverLevel.getEntitiesOfClass(Parrot.class, player.getBoundingBox().inflate(4.0D));
                for (Parrot parrot : droppedParrots) {
                    if (parrot.getOwner() == player) {
                        parrot.setDeltaMovement(parrot.getDeltaMovement().add(0.0D, 0.7D, 0.0D));
                        parrot.setOrderedToSit(false);

                        if (currentTime - JUST_HEALED_FROM_SHOULDER.getOrDefault(playerId, 0L) < 5L) {
                            PARROT_COOLDOWNS.put(parrot.getUUID(), currentTime + 800L);
                        }
                    }
                }
            }
        }

        if (currentlyHasParrot) { HAD_PARROT_LAST_TICK.add(playerId); }
        else { HAD_PARROT_LAST_TICK.remove(playerId); }

        // --- 2. TRACCIÓN MAGNÉTICA Y ROTACIÓN CORRECTA ---
        if (player.tickCount % 5 == 0) {
            if (player.level() instanceof ServerLevel serverLevel) {
                List<Parrot> flyingParrots = serverLevel.getEntitiesOfClass(Parrot.class, player.getBoundingBox().inflate(20.0D));
                for (Parrot parrot : flyingParrots) {
                    if (parrot.getOwner() == player && !parrot.isOrderedToSit()) {
                        double distance = player.distanceTo(parrot);
                        if (distance > 3.0D) {
                            // Calculamos el vector de dirección
                            Vec3 pullDirection = player.position().add(0, 1.5, 0).subtract(parrot.position()).normalize();

                            double boost = player.isSprinting() ? 0.35D : 0.15D;
                            if (distance > 10.0D) boost *= 1.5D;

                            // Aplicamos el impulso físico
                            parrot.setDeltaMovement(parrot.getDeltaMovement().add(pullDirection.scale(boost)));

                            // NUEVO: Corrección de Rotación (Trigonometría para mirar hacia donde vuelan)
                            double dX = pullDirection.x;
                            double dY = pullDirection.y;
                            double dZ = pullDirection.z;
                            double horizontalDistance = Math.sqrt(dX * dX + dZ * dZ);

                            // Calculamos el ángulo Yaw (horizontal) y Pitch (vertical)
                            float yaw = (float)(Math.atan2(dZ, dX) * (180D / Math.PI)) - 90.0F;
                            float pitch = (float)(-(Math.atan2(dY, horizontalDistance) * (180D / Math.PI)));

                            // Forzamos al cuerpo y cabeza del loro a adoptar este ángulo
                            parrot.setYRot(yaw);
                            parrot.setXRot(pitch);
                            parrot.yBodyRot = yaw;
                            parrot.yHeadRot = yaw;
                        }
                    }
                }
            }
        }

        if (currentlyHasParrot) {
            float currentVanillaFall = (float) player.fallDistance;
            if (currentVanillaFall > 0.0F) {
                float totalFallen = SECRET_FALL_TRACKER.getOrDefault(playerId, 0.0F) + currentVanillaFall;
                SECRET_FALL_TRACKER.put(playerId, totalFallen);
                player.fallDistance = 0.0F;
            }
            if (player.onGround()) {
                float totalFallen = SECRET_FALL_TRACKER.getOrDefault(playerId, 0.0F);
                if (totalFallen > 0.0F) {
                    SECRET_FALL_TRACKER.remove(playerId);
                    if (totalFallen > 3.0F) {
                        player.causeFallDamage(totalFallen, 1.0F, player.damageSources().fall());
                    }
                }
            }
        } else {
            SECRET_FALL_TRACKER.remove(playerId);
        }

        // --- 4. SISTEMA MULTI-DRON DE CURACIÓN ---
        if (player.getHealth() <= player.getMaxHealth() * 0.20F && currentTime > PLAYER_HEAL_BUFFER.getOrDefault(playerId, 0L)) {

            boolean droneActivated = false;

            if (currentlyHasParrot) {
                if (player.level() instanceof ServerLevel serverLevel) {
                    player.hurtServer(serverLevel, player.damageSources().generic(), 0.0F);
                }
                JUST_HEALED_FROM_SHOULDER.put(playerId, currentTime);
                droneActivated = true;
            }
            else if (player.level() instanceof ServerLevel serverLevel) {
                List<Parrot> nearbyParrots = serverLevel.getEntitiesOfClass(Parrot.class, player.getBoundingBox().inflate(2.5D));
                for (Parrot p : nearbyParrots) {
                    if (p.getOwner() == player && !p.isOrderedToSit()) {
                        if (currentTime > PARROT_COOLDOWNS.getOrDefault(p.getUUID(), 0L)) {
                            PARROT_COOLDOWNS.put(p.getUUID(), currentTime + 800L);
                            p.setDeltaMovement(p.getDeltaMovement().add(0.0D, 0.4D, 0.0D));
                            droneActivated = true;
                            break;
                        }
                    }
                }
            }

            if (droneActivated) {
                float targetHp = player.getMaxHealth() * 0.45F;
                float hpNeeded = targetHp - player.getHealth();

                if (hpNeeded > 0) {
                    int durationTicks = (int) Math.ceil(hpNeeded * 25.0F);
                    player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, durationTicks, 1));
                }

                PLAYER_HEAL_BUFFER.put(playerId, currentTime + 40L);

                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.PARROT_FLY, SoundSource.PLAYERS, 1.0F, 1.5F);

                if (player.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                            player.getX(), player.getY() + 2.2D, player.getZ(), 25, 0.5D, 0.2D, 0.5D, 0.0D);
                }
            }
        }

        // --- 5. PULSO DE RADAR ---
        if (player.tickCount % 200 == 0) {
            boolean hasActiveDrone = currentlyHasParrot;
            if (!hasActiveDrone && player.level() instanceof ServerLevel serverLevel) {
                List<Parrot> radarRangeParrots = serverLevel.getEntitiesOfClass(Parrot.class, player.getBoundingBox().inflate(16.0D));
                for(Parrot p : radarRangeParrots) { if (p.getOwner() == player && !p.isOrderedToSit()) { hasActiveDrone = true; break; } }
            }

            if (hasActiveDrone) {
                List<LivingEntity> mobs = player.level().getEntitiesOfClass(LivingEntity.class,
                        player.getBoundingBox().inflate(16.0D), entity -> entity instanceof Enemy);

                boolean sung = false;

                for (LivingEntity mob : mobs) {
                    HitResult result = player.level().clip(new ClipContext(
                            player.getEyePosition(), mob.getEyePosition(),
                            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));

                    if (result.getType() == HitResult.Type.MISS) {
                        mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, false, false));
                        sung = true;
                    }
                }

                if (sung) {
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.PARROT_IMITATE_PHANTOM, SoundSource.PLAYERS, 0.8F, 1.0F);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        UUID playerId = event.getEntity().getUUID();
        PLAYER_HEAL_BUFFER.remove(playerId);
        SECRET_FALL_TRACKER.remove(playerId);
        HAD_PARROT_LAST_TICK.remove(playerId);
        JUST_HEALED_FROM_SHOULDER.remove(playerId);
    }

    @SubscribeEvent
    public static void onParrotDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        if (event.getEntity() instanceof Parrot parrot) {
            PARROT_COOLDOWNS.remove(parrot.getUUID());
        }
    }
}