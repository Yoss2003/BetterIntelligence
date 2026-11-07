package com.yoss.betterintelligence;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

@EventBusSubscriber(modid = BetterIntelligence.MODID)
public class FactionWarsModifier {

    // =====================================================================
    // 1. INMUNIDAD AL FUEGO AMIGO ENTRE ILLAGERS
    // =====================================================================
    @SubscribeEvent
    public static void onIllagerFriendlyFire(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof AbstractIllager && event.getSource().getEntity() instanceof AbstractIllager) {
            event.setCanceled(true);
        }
    }

    // =====================================================================
    // 2. Disciplina TÁCTICA Y GUERRA DE Facciones
    // =====================================================================
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {

        // --- CONTROL DE SPAWN DE Brujas ---
        if (event.getEntity() instanceof Witch witch && !witch.level().isClientSide()) {
            witch.goalSelector.getAvailableGoals().removeIf(wrappedGoal ->
                    wrappedGoal.getGoal() instanceof net.minecraft.world.entity.ai.goal.RangedAttackGoal
            );

            witch.goalSelector.addGoal(2, new SmartWitchAttackGoal(witch, 40));

            if (witch.tickCount == 0) {
                ServerLevel serverLevel = (ServerLevel) witch.level();
                BlockPos pos = witch.blockPosition();

                boolean inSwamp = serverLevel.getBiome(pos).is(net.minecraft.tags.BiomeTags.HAS_SWAMP_HUT);
                boolean nearIllagers = !serverLevel.getEntitiesOfClass(AbstractIllager.class, witch.getBoundingBox().inflate(48.0D)).isEmpty();
                boolean playerSpawning = !serverLevel.getEntitiesOfClass(Player.class,
                        witch.getBoundingBox().inflate(15.0D),
                        p -> p.getMainHandItem().is(net.minecraft.world.item.Items.WITCH_SPAWN_EGG)).isEmpty();

                if (!inSwamp && !nearIllagers && !playerSpawning) {
                    event.setCanceled(true);
                    return;
                }
            }
        }

        // --- IA DE COMBATE ENTRE Facciones ---
        if (event.getEntity() instanceof AbstractIllager || event.getEntity() instanceof Witch) {
            Mob factionMember = (Mob) event.getEntity();

            factionMember.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(factionMember, Zombie.class, true));
            factionMember.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(factionMember, Skeleton.class, true));
            factionMember.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(factionMember, Spider.class, true));
            factionMember.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(factionMember, Creeper.class, true));
        }

        if (event.getEntity() instanceof Zombie || event.getEntity() instanceof Skeleton ||
                event.getEntity() instanceof Spider || event.getEntity() instanceof Creeper) {

            Mob monster = (Mob) event.getEntity();

            monster.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(monster, AbstractIllager.class, true));
            monster.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(monster, Witch.class, true));
        }

        if (event.getEntity() instanceof Pillager pillager) {
            pillager.goalSelector.addGoal(1, new PillagerTacticalCombatGoal(pillager));
        }
    }

    // =====================================================================
    // 3. CONTROL DE TRAYECTORIA DE Disparo (Disciplina de saqueadores)
    // =====================================================================
    @SubscribeEvent
    public static void onIllagerTick(EntityTickEvent.Pre event) {
        if (event.getEntity() instanceof AbstractIllager illager && !illager.level().isClientSide()) {

            // Evaluamos cada segundo (20 ticks) si tiene un objetivo de combate
            if (illager.tickCount % 20 == 0 && illager.getTarget() != null) {
                LivingEntity target = illager.getTarget();

                // Verificamos si hay un aliado Illager directo en la línea de fuego hacia el objetivo
                Vec3 illagerPos = illager.position();
                Vec3 targetPos = target.position();
                Vec3 direction = targetPos.subtract(illagerPos).normalize();

                // Escaneamos si hay compañeros en un radio de 3 bloques enfrente de su trayectoria
                boolean allyInLineOfFire = !illager.level().getEntitiesOfClass(
                        AbstractIllager.class,
                        illager.getBoundingBox().inflate(3.0D).expandTowards(direction.scale(4.0D)),
                        ally -> ally != illager
                ).isEmpty();

                if (allyInLineOfFire) {
                    illager.setTarget(null);
                    illager.getNavigation().moveTo(
                            illager.getX() + (illager.getRandom().nextDouble() - 0.5) * 4.0,
                            illager.getY(),
                            illager.getZ() + (illager.getRandom().nextDouble() - 0.5) * 4.0,
                            1.2D
                    );
                }
            }
        }
    }
}