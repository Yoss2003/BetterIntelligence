package com.yoss.betterintelligence;

import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

@EventBusSubscriber(modid = BetterIntelligence.MODID)
public class VillageAiModifier {

    // =====================================================================
    // 1. CONTROL DE Cohetes Y METAS DEL GOLEM
    // =====================================================================
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {

        if (event.getEntity() instanceof FireworkRocketEntity firework) {
            if (!firework.level().canSeeSky(firework.blockPosition())) {
                long villagersNear = firework.level().getEntitiesOfClass(Villager.class,
                        firework.getBoundingBox().inflate(5.0D)).size();
                if (villagersNear > 0) {
                    event.setCanceled(true);
                }
            }
        }

        if (event.getEntity() instanceof IronGolem golem) {
            golem.targetSelector.addGoal(1, new GolemRescueVillagerGoal(golem));
        }
    }

    // =====================================================================
    // 2. IA DE Aldeanos: HUIDA TÁCTICA Y Asegurar Puertas
    // =====================================================================
    @SubscribeEvent
    public static void onVillagerTick(net.neoforged.neoforge.event.tick.EntityTickEvent.Pre event) {
        if (event.getEntity() instanceof Villager villager) {

            if (villager.tickCount % 20 == 0 && !villager.level().isClientSide()) {

                // --- 1. MODALES UNIVERSALES (Cerrar puertas siempre, con o sin pánico) ---
                net.minecraft.core.BlockPos.betweenClosedStream(
                        villager.blockPosition().offset(-2, -1, -2),
                        villager.blockPosition().offset(2, 1, 2)
                ).forEach(pos -> {
                    net.minecraft.world.level.block.state.BlockState state = villager.level().getBlockState(pos);

                    if (state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock door && state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN)) {
                        // Distancia de seguridad para que crucen completamente antes de cerrar y no se atrapen a sí mismos
                        if (villager.distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(pos)) > 2.25D) {
                            door.setOpen(villager, villager.level(), state, pos, false);
                        }
                    }
                });


                // --- 2. SISTEMA DE PÁNICO Y SUPERVIVENCIA ---
                if (villager.getBrain().hasMemoryValue(MemoryModuleType.NEAREST_HOSTILE)) {

                    // A) LA CORREA DE LA ALDEA (Evitar que huyan al bosque)
                    var meetingPointOpt = villager.getBrain().getMemory(MemoryModuleType.MEETING_POINT);
                    if (meetingPointOpt.isPresent() && meetingPointOpt.get().dimension() == villager.level().dimension()) {
                        net.minecraft.core.BlockPos bellPos = meetingPointOpt.get().pos();

                        // Si se alejan a más de 35 bloques (1225 al cuadrado) de la campana, dan la vuelta
                        if (villager.distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(bellPos)) > 1225.0D) {
                            villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(bellPos, 0.7F, 3));
                        }
                    }

                    // B) INVOCACIÓN Y REFUGIO
                    boolean isExposed = villager.level().canSeeSky(villager.blockPosition());

                    if (isExposed) {
                        // 1. INVOCACIÓN DE EMERGENCIA
                        var golems = villager.level().getEntitiesOfClass(IronGolem.class, villager.getBoundingBox().inflate(24.0D));

                        if (golems.isEmpty()) {
                            IronGolem emergencyGolem = new net.minecraft.world.entity.animal.golem.IronGolem(net.minecraft.world.entity.EntityTypes.IRON_GOLEM, villager.level());
                            emergencyGolem.setPos(villager.getX(), villager.getY(), villager.getZ());
                            villager.level().addFreshEntity(emergencyGolem);
                        }

                    } else {
                        // 2. Asegurar EL PERÍMETRO INTERNO (Solo trampillas, las puertas ya se manejan arriba)
                        net.minecraft.core.BlockPos.betweenClosedStream(
                                villager.blockPosition().offset(-2, -1, -2),
                                villager.blockPosition().offset(2, 2, 2)
                        ).forEach(pos -> {
                            net.minecraft.world.level.block.state.BlockState state = villager.level().getBlockState(pos);

                            if (state.getBlock() instanceof net.minecraft.world.level.block.TrapDoorBlock && state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN)) {
                                villager.level().setBlock(pos, state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN, false), 2);
                                villager.level().playSound(null, pos, net.minecraft.sounds.SoundEvents.WOODEN_TRAPDOOR_CLOSE, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
                            }
                        });
                    }
                }
            }
        }
    }

    // =====================================================================
    // 3. Clases Auxiliares (GOALS)
    // =====================================================================
    public static class GolemRescueVillagerGoal extends NearestAttackableTargetGoal<Mob> {
        public GolemRescueVillagerGoal(IronGolem golem) {
            super(golem, Mob.class, 5, true, false,
                    (entity, ignored) -> entity instanceof Mob attacker && attacker.getTarget() instanceof Villager);
        }
    }
}