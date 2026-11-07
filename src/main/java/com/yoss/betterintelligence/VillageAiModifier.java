package com.yoss.betterintelligence;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.EnumSet;

@EventBusSubscriber(modid = BetterIntelligence.MODID)
public class VillageAiModifier {

    // ==========================================================
    // LÓGICA DEL METAL: Daño masivo por lava y parálisis por frío/agua
    // ==========================================================
    @SubscribeEvent
    public static void onGolemDamage(LivingDamageEvent.Pre event) {
        if (event.getEntity() instanceof IronGolem golem) {
            // LAVA = Derretimiento absoluto
            if (event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_FIRE) && golem.isInLava()) {
                event.setNewDamage(event.getNewDamage() * 5.0F);
            }
        }
    }

    @SubscribeEvent
    public static void onGolemTick(EntityTickEvent.Pre event) {
        if (event.getEntity() instanceof IronGolem golem) {
            // INMOVILIZACIÓN POR FRÍO EXTREMO (Nieve en polvo)
            if (golem.isInPowderSnow || golem.level().getBlockState(golem.blockPosition()).is(Blocks.POWDER_SNOW)) {
                golem.setDeltaMovement(0, golem.getDeltaMovement().y, 0);
            }
            // PESO EN EL AGUA: Se mueven arrastrándose
            else if (golem.isInWater()) {
                golem.setDeltaMovement(golem.getDeltaMovement().multiply(0.3D, 1.0D, 0.3D));
            }
        }
    }

    // ==========================================================
    // RECLUTAMIENTO: Darle amapola a un Golem Vanilla
    // ==========================================================
    @SubscribeEvent
    public static void onGivePoppy(PlayerInteractEvent.EntityInteract event) {
        if (event.getTarget() instanceof IronGolem golem && !event.getLevel().isClientSide()) {
            if (event.getEntity().getItemInHand(event.getHand()).is(Items.POPPY)) {
                golem.getPersistentData().putBoolean("EsDefensorAldea", true);
                if (!event.getEntity().isCreative()) {
                    event.getEntity().getItemInHand(event.getHand()).shrink(1);
                }
                event.getEntity().playSound(SoundEvents.IRON_GOLEM_REPAIR, 1.0F, 1.5F);
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
        }
    }

    // ==========================================================
    // IA DE ALDEANOS: Pánico natural y control de generación
    // ==========================================================
    @SubscribeEvent
    public static void onVillagerTick(EntityTickEvent.Pre event) {
        if (event.getEntity() instanceof Villager villager && villager.tickCount % 40 == 0 && !villager.level().isClientSide()) {

            // Fobia a fuego o lava cercana (Huyen a velocidad normal, sin supervelocidad)
            BlockPos villagerPos = villager.blockPosition();
            boolean nearHazard = false;
            for (int x = -2; x <= 2; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -2; z <= 2; z++) {
                        BlockState state = villager.level().getBlockState(villagerPos.offset(x, y, z));
                        if (state.is(Blocks.LAVA) || state.is(Blocks.FIRE) || state.is(Blocks.MAGMA_BLOCK)) {
                            nearHazard = true;
                            break;
                        }
                    }
                    if (nearHazard) break;
                }
                if (nearHazard) break;
            }

            if (nearHazard) {
                double rx = villager.getX() + (villager.getRandom().nextDouble() - 0.5) * 8.0;
                double rz = villager.getZ() + (villager.getRandom().nextDouble() - 0.5) * 8.0;
                villager.getNavigation().moveTo(rx, villager.getY(), rz, 1.0D);
                return;
            }

            boolean inPanic = villager.getBrain().hasMemoryValue(net.minecraft.world.entity.ai.memory.MemoryModuleType.NEAREST_HOSTILE);

            if (inPanic) {
                java.util.List<IronGolem> visibleGolems = villager.level().getEntitiesOfClass(IronGolem.class, villager.getBoundingBox().inflate(20.0D));
                boolean shouldSpawn = false;

                // Generación equilibrada: Si no hay gólems o están sobrepasados
                if (visibleGolems.isEmpty()) {
                    shouldSpawn = true;
                } else {
                    for (IronGolem g : visibleGolems) {
                        long attackers = villager.level().getEntitiesOfClass(Mob.class, g.getBoundingBox().inflate(6.0D),
                                m -> m.getTarget() == g).size();
                        if (attackers >= 3) {
                            shouldSpawn = true;
                            break;
                        }
                    }
                }

                if (shouldSpawn) {
                    IronGolem newGolem = new IronGolem(EntityTypes.IRON_GOLEM, villager.level());
                    newGolem.setPos(villager.getX(), villager.getY(), villager.getZ());
                    newGolem.getPersistentData().putBoolean("EsDefensorAldea", true);
                    villager.level().addFreshEntity(newGolem);
                }

                // Si es de día y hay gólem cerca, se agrupa junto a él para protegerse
                long worldTime = villager.level().getOverworldClockTime() % 24000L;
                boolean isDayTime = worldTime >= 0L && worldTime < 12000L;

                if (!visibleGolems.isEmpty() && isDayTime) {
                    IronGolem savior = visibleGolems.getFirst();
                    if (villager.distanceToSqr(savior) > 16.0) {
                        villager.getNavigation().moveTo(savior, 0.9D);
                    }
                }
            }
        }
    }

    // ==========================================================
    // REUBICACIÓN DEFENSIVA DE LA ALDEA (Campana)
    // ==========================================================
    @SubscribeEvent
    public static void onBellRing(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().getBlockState(event.getPos()).is(Blocks.BELL) && !event.getLevel().isClientSide()) {
            BlockPos bellPos = event.getPos();
            java.util.List<IronGolem> defenders = event.getLevel().getEntitiesOfClass(IronGolem.class,
                    new net.minecraft.world.phys.AABB(bellPos).inflate(64.0D));

            int totalGolems = 0;
            for (IronGolem g : defenders) {
                if (g.isPlayerCreated() && !g.getPersistentData().getBoolean("EsDefensorAldea").orElse(false)) continue;
                totalGolems++;
            }

            if (totalGolems > 0) {
                double radiusPerGolem = 56.0 / totalGolems;
                int count = 0;
                for (IronGolem g : defenders) {
                    if (g.isPlayerCreated() && !g.getPersistentData().getBoolean("EsDefensorAldea").orElse(false)) continue;

                    double angle = (360.0 / totalGolems) * count;
                    double targetX = bellPos.getX() + (Math.cos(Math.toRadians(angle)) * radiusPerGolem);
                    double targetZ = bellPos.getZ() + (Math.sin(Math.toRadians(angle)) * radiusPerGolem);

                    g.getNavigation().moveTo(targetX, bellPos.getY(), targetZ, 1.2D);
                    count++;
                }
            }
        }
    }

    // ==========================================================
    // INYECCIÓN DE IA EN EL GOLEM
    // ==========================================================
    @SubscribeEvent
    public static void onGolemJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof IronGolem golem) {
            golem.targetSelector.addGoal(1, new GolemRetaliationGoal(golem));
            golem.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(golem, Zombie.class, 10, true, false, (entity, ignored) -> !(entity instanceof Slime)));
            golem.targetSelector.addGoal(2, new GolemTargetPlayerGoal(golem));

            // Disciplina de caminos y turbo boost
            golem.goalSelector.addGoal(1, new GolemPathdisciplineGoal(golem));
            golem.goalSelector.addGoal(2, new GolemTurboBoostGoal(golem));
        }
    }

    // ==========================================================
    // METAS DE GOLEM
    // ==========================================================

    public static class GolemTurboBoostGoal extends Goal {
        private final IronGolem golem;
        private int cooldown = 0;

        public GolemTurboBoostGoal(IronGolem golem) {
            this.golem = golem;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (this.cooldown > 0) this.cooldown--;

            LivingEntity currentTarget = this.golem.getTarget();
            if (currentTarget != null) {
                java.util.List<Mob> closerEnemies = this.golem.level().getEntitiesOfClass(Mob.class, this.golem.getBoundingBox().inflate(6.0D),
                        m -> m != currentTarget && m instanceof Zombie);
                if (!closerEnemies.isEmpty()) {
                    this.golem.setTarget(closerEnemies.get(0));
                }
            }

            return this.golem.getTarget() != null && this.cooldown <= 0 && this.golem.distanceToSqr(this.golem.getTarget()) > 25.0;
        }

        @Override
        public void start() {
            this.golem.addEffect(new MobEffectInstance(MobEffects.SPEED, 60, 1));
            this.cooldown = 200;
        }
    }

    // Disciplina de caminos optimizada: Regresa al DIRT_PATH al terminar combate o patrullando
    public static class GolemPathdisciplineGoal extends Goal {
        private final IronGolem golem;
        private int checkCooldown = 0;

        public GolemPathdisciplineGoal(IronGolem golem) {
            this.golem = golem;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            // Si está combatiendo activamente, prioriza la lucha
            if (this.golem.getTarget() != null && this.golem.getTarget().isAlive()) return false;

            if (this.checkCooldown > 0) {
                this.checkCooldown--;
                return false;
            }
            this.checkCooldown = 30;

            BlockPos belowPos = this.golem.blockPosition().below();
            BlockState floorState = this.golem.level().getBlockState(belowPos);

            // Se activa si está pisando pasto o tierra fuera del sendero
            return floorState.is(Blocks.GRASS_BLOCK) || floorState.is(Blocks.DIRT);
        }

        @Override
        public void start() {
            BlockPos golemPos = this.golem.blockPosition();
            BlockPos closestPath = null;
            double minDistance = Double.MAX_VALUE;

            for (int x = -10; x <= 10; x++) {
                for (int y = -2; y <= 2; y++) {
                    for (int z = -10; z <= 10; z++) {
                        BlockPos checkPos = golemPos.offset(x, y, z);
                        if (this.golem.level().getBlockState(checkPos).is(Blocks.DIRT_PATH)) {
                            double dist = checkPos.distSqr(golemPos);
                            if (dist < minDistance) {
                                minDistance = dist;
                                closestPath = checkPos;
                            }
                        }
                    }
                }
            }

            if (closestPath != null) {
                this.golem.getNavigation().moveTo(closestPath.getX() + 0.5, closestPath.getY(), closestPath.getZ() + 0.5, 1.0D);
            }
        }
    }

    public static class GolemTargetPlayerGoal extends NearestAttackableTargetGoal<Player> {
        public GolemTargetPlayerGoal(IronGolem golem) {
            super(golem, Player.class, 10, true, false, (entity, ignored) -> {
                if (entity instanceof Player p) {
                    LivingEntity lastHurt = golem.getLastHurtByMob();
                    return lastHurt != null && p.equals(lastHurt) && golem.getLastDamageSource() != null;
                }
                return false;
            });
        }
    }

    public static class GolemRetaliationGoal extends NearestAttackableTargetGoal<Mob> {
        public GolemRetaliationGoal(IronGolem golem) {
            super(golem, Mob.class, 10, true, false, (entity, ignored) -> {
                LivingEntity lastHurt = golem.getLastHurtByMob();
                return lastHurt != null && entity.equals(lastHurt);
            });
        }
    }
}