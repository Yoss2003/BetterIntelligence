package com.yoss.betterintelligence;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.monster.Phantom;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.minecraft.world.InteractionResult;
import org.jspecify.annotations.NonNull;

import java.util.EnumSet;
import java.util.List;

@EventBusSubscriber(modid = BetterIntelligence.MODID)
public class PetAiModifier {

    // INMUNIDAD AL FUEGO AMIGO
    @SubscribeEvent
    public static void onFriendlyFire(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof TamableAnimal pet && pet.isTame()) {
            if (event.getSource().getEntity() == pet.getOwner()) {
                event.setCanceled(true);
                return;
            }
        }

        if (event.getEntity() instanceof Fox fox) {
            // 1. Inmunidad a tus propios golpes
            if (event.getSource().getEntity() instanceof Player player) {
                if (PetAiModifier.isPlayerTrusted(fox, player)) {
                    event.setCanceled(true);
                    return;
                }
            }

            // 2. NUEVO: Habilidad Felina (Inmunidad absoluta a caídas)
            // Un zorro táctico que hace parkour y caza en el aire jamás debe morir por gravedad.
            if (event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_FALL)) {
                event.setCanceled(true);
            }
        }
    }

    // INTERACT JUGADOR A ZORRO
    @SubscribeEvent
    public static void onInteractFox(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;

        if (event.getTarget() instanceof Fox fox && !event.getLevel().isClientSide()) {

            // Si es un bebé, actuará como un zorro normal
            if (fox.isBaby()) return;

            Player player = event.getEntity();

            if (PetAiModifier.isPlayerTrusted(fox, player)) {
                ItemStack playerHand = player.getItemInHand(event.getHand());
                ItemStack foxMouth = fox.getItemBySlot(EquipmentSlot.MAINHAND);

                // REGLA DE seguimiento
                if (playerHand.is(Items.SWEET_BERRIES)) {
                    fox.getPersistentData().putInt("ContratoMercenario", fox.tickCount + 6000);
                    fox.playSound(net.minecraft.sounds.SoundEvents.FOX_EAT, 1.0F, 1.0F);
                    ((net.minecraft.server.level.ServerLevel)fox.level()).sendParticles(
                            net.minecraft.core.particles.ParticleTypes.HEART,
                            fox.getX(), fox.getY() + 0.8, fox.getZ(), 3, 0.3, 0.3, 0.3, 0.0
                    );

                    if (!player.isCreative()) playerHand.shrink(1);
                    event.setCanceled(true);
                    event.setCancellationResult(InteractionResult.SUCCESS);
                    return;
                }

                // QUITAR OBJETO
                if (playerHand.isEmpty() && !foxMouth.isEmpty()) {
                    player.setItemInHand(event.getHand(), foxMouth.copy());
                    fox.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                    event.setCanceled(true);
                    event.setCancellationResult(InteractionResult.SUCCESS);
                }

                // DAR OBJETO
                else if (!playerHand.isEmpty() && foxMouth.isEmpty()) {
                    ItemStack toGive = playerHand.copy();
                    toGive.setCount(1);
                    fox.setItemSlot(EquipmentSlot.MAINHAND, toGive);
                    if (!player.isCreative()) playerHand.shrink(1);
                    event.setCanceled(true);
                    event.setCancellationResult(InteractionResult.SUCCESS);
                }
            }
        }
    }


    @SubscribeEvent
    public static void onPetJoin(EntityJoinLevelEvent event) {

        // CONDICIÓN
        if (event.getEntity() instanceof TamableAnimal pet) {
            if (pet instanceof Wolf || pet instanceof Cat) {
                pet.setPathfindingMalus(PathType.LAVA, -1.0F);
                pet.goalSelector.getAvailableGoals().removeIf(prioritizedGoal -> prioritizedGoal.getGoal().getClass().getSimpleName().contains("FollowOwner"));

                pet.goalSelector.addGoal(0, new PetFleeLavaGoal(pet, 1.5D));
                pet.goalSelector.addGoal(1, new PetWaitForFoodGoal(pet));
                pet.goalSelector.addGoal(2, new PetTacticalRetreatGoal(pet, 1.5D));

                pet.goalSelector.addGoal(4, new PetFollowLeaderGoal(pet, 1.1D));
                pet.goalSelector.addGoal(5, new PetFollowBehindOwnerGoal(pet, 1.0D, 5.0F, 2.0F));
            }
        }

        if (event.getEntity() instanceof Wolf wolf) {
            // --- SISTEMA DE GENÉTICA Y Emociones ---
            if (!wolf.getPersistentData().contains("EsMiedoso")) {
                boolean cobarde = wolf.getRandom().nextFloat() < 0.15F;
                wolf.getPersistentData().putBoolean("EsMiedoso", cobarde);
            }

            // --- TAMAÑO FÍSICO SEGÚN EDAD Y Personalidad ---
            var scaleAttr = wolf.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SCALE);
            if (scaleAttr != null) {
                if (wolf.isBaby()) {
                    scaleAttr.setBaseValue(1.0D);
                } else {
                    boolean esMiedoso = wolf.getPersistentData().getBoolean("EsMiedoso").orElse(false);
                    if (esMiedoso) {
                        scaleAttr.setBaseValue(0.85D);
                    } else {
                        scaleAttr.setBaseValue(1.0D);
                    }
                }
            }

            // --- REMOVER EL "ROGAR" DE MINECRAFT PARA NO INTERRUMPIR APAREAMIENTO ---
            wolf.goalSelector.getAvailableGoals().removeIf(prioritizedGoal -> {
                net.minecraft.world.entity.ai.goal.Goal goal = prioritizedGoal.getGoal();
                return goal instanceof net.minecraft.world.entity.ai.goal.BegGoal ||
                        goal instanceof net.minecraft.world.entity.ai.goal.TemptGoal;
            });

            // --- AÑADIR TUS METAS ---
            wolf.targetSelector.addGoal(1, new WolfPackTargetGoal<>(wolf, net.minecraft.world.entity.monster.skeleton.Skeleton.class, true));
            wolf.targetSelector.addGoal(2, new WolfRandomHostileGoal(wolf));
            wolf.targetSelector.addGoal(3, new WolfPackTargetGoal<>(wolf, net.minecraft.world.entity.monster.zombie.Zombie.class, true));

            // Añadimos la nueva meta de curación con altísima prioridad (2)
            wolf.goalSelector.addGoal(3, new WolfFleeCreeperGoal(wolf, 8.0F, 1.5D));
            wolf.goalSelector.addGoal(4, new WolfFearDarknessGoal(wolf, 1.2D));
        }

        if (event.getEntity() instanceof Cat cat) {
            cat.setPathfindingMalus(PathType.WATER, -1.0F);
            cat.goalSelector.addGoal(3, new CatChaseCreeperGoal(cat, 1.2D, 10.0D));
            cat.goalSelector.addGoal(4, new CatWarnDangerGoal(cat));
        }

        if (event.getEntity() instanceof Fox fox) {
            fox.goalSelector.getAvailableGoals().removeIf(prioritizedGoal ->
                    prioritizedGoal.getGoal().getClass().getSimpleName().contains("Sleep")
            );

            fox.setPathfindingMalus(PathType.LAVA, -1.0F);

            // Limpiamos los instintos de rayos X
            fox.targetSelector.getAvailableGoals().removeIf(prioritizedGoal ->
                    prioritizedGoal.getGoal().getClass().getSimpleName().equals("FoxMeleeAttackGoal") ||
                            prioritizedGoal.getGoal().getClass().getSimpleName().contains("NearestAttackableTargetGoal")
            );

            // Objetivos TÁCTICOS (La visión - Quien es el enemigo)

            // 0. supervivencia propia: Si alguien le pega, ese es el objetivo a destruir.
            fox.targetSelector.addGoal(0, new net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal(fox));

            // 1. El Guardaespaldas: Proteger a su jefe de amenazas inminentes es lo más importante.
            fox.targetSelector.addGoal(1, new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(fox, net.minecraft.world.entity.monster.Monster.class, 10, true, false, (entity, _) -> {
                if (entity instanceof net.minecraft.world.entity.Mob enemyMob) {
                    net.minecraft.world.entity.LivingEntity enemyTarget = enemyMob.getTarget();
                    if (enemyTarget instanceof net.minecraft.world.entity.player.Player targetPlayer) {
                        if (com.yoss.betterintelligence.FoxTrustHelper.trusts(fox, targetPlayer)) return true;
                    }
                }
                java.util.List<net.minecraft.world.entity.player.Player> nearbyPlayers = fox.level().getEntitiesOfClass(net.minecraft.world.entity.player.Player.class, entity.getBoundingBox().inflate(4.5D));
                for (net.minecraft.world.entity.player.Player player : nearbyPlayers) {
                    if (com.yoss.betterintelligence.FoxTrustHelper.trusts(fox, player)) return true;
                }
                return false;
            }));

            // 2. Venganza del jefe (Mercenario): Si tú atacas a alguien o alguien te pegó primero.
            fox.targetSelector.addGoal(2, new FoxMercenaryTargetGoal(fox));

            // 3. Caza Mayor (Fantasmas): Peligros del cielo.
            fox.targetSelector.addGoal(3, new FoxSquadTargetGoal<>(fox, Phantom.class, 3, false));

            // 4. Diversión y Comida (Gallinas): Mínima prioridad táctica. No ignorará un monstruo por una gallina.
            fox.targetSelector.addGoal(4, new FoxSquadTargetGoal<>(fox, Chicken.class, 2, true));

            // 4. NUEVA Personalidad: El Guardaespaldas Calculador
            fox.targetSelector.addGoal(4, new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(fox, net.minecraft.world.entity.monster.Monster.class, 10, true, false, (entity, _) -> {
                // 1. Intercepción: ¿El enemigo quiere atacar a mi jefe?
                if (entity instanceof net.minecraft.world.entity.Mob enemyMob) {
                    net.minecraft.world.entity.LivingEntity enemyTarget = enemyMob.getTarget();

                    // Si el monstruo tiene en la mira a un jugador...
                    if (enemyTarget instanceof net.minecraft.world.entity.player.Player targetPlayer) {
                        // Usamos Nuestra GANZÚA PARA PREGUNTAR
                        if (com.yoss.betterintelligence.FoxTrustHelper.trusts(fox, targetPlayer)) {
                            return true; // ¡Cruzó la línea, lo intercepto!
                        }
                    }
                }

                // 2. Defensa Territorial: ¿El enemigo está invadiendo el espacio personal de mi jefe?
                java.util.List<net.minecraft.world.entity.player.Player> nearbyPlayers = fox.level().getEntitiesOfClass(net.minecraft.world.entity.player.Player.class, entity.getBoundingBox().inflate(4.5D));

                for (net.minecraft.world.entity.player.Player player : nearbyPlayers) {
                    // Usamos Nuestra GANZÚA OTRA VEZ
                    if (com.yoss.betterintelligence.FoxTrustHelper.trusts(fox, player)) {
                        return true; // ¡Se acercó demasiado, lo elimino!
                    }
                }

                return false;
            }));

            // Objetivos DE COMPORTAMIENTO (El cerebro - Qué hacer y en qué orden)

            // --- FASE DE Reflejos Absolutos ---
            // 0. Candado: siempre activo, no afecta sus patas.
            fox.goalSelector.addGoal(0, new FoxWeaponLockGoal(fox));
            // 1. Evitar la lava: ¡La muerte por quemaduras es peor que todo lo demás!
            fox.goalSelector.addGoal(1, new PetAiModifier.FoxPassiveLavaEvasionGoal(fox));
            // 2. Emergencia Médica: Si tiene poca vida, huye con su jefe de inmediato.
            fox.goalSelector.addGoal(2, new FoxTacticalRetreatGoal(fox, 1.5D));

            // --- FASE DE REACCIÓN (NUEVO) ---
            // 3. Miedo a los lobos: Huye del depredador si no está ya peleando con uno.
            fox.goalSelector.addGoal(3, new net.minecraft.world.entity.ai.goal.AvoidEntityGoal<>(fox, Wolf.class, 16.0F, 1.4D, 1.6D) {
                @Override
                public boolean canUse() {
                    if (fox.getTarget() instanceof Wolf) return false; // Si se está defendiendo, no huye.
                    return super.canUse();
                }
            });

            // --- FASE TÁCTICA Y COMBATE ---
            // 4. Parkour: Debe poder saltar paredes tanto para atacar como para huir.
            fox.goalSelector.addGoal(4, new FoxTerrainParkourGoal(fox));
            // 5. Combate Aéreo
            fox.goalSelector.addGoal(5, new FoxAttackPhantomGoal(fox, 1.4D));
            // 6. Combate Terrestre
            fox.goalSelector.addGoal(6, new FoxTacticalMeleeGoal(fox, 1.7D));

            // --- FASE DE UTILIDAD Y FIDELIDAD ---
            // 7. seguimiento de Fidelidad: Si te alejas mucho, te sigue ANTES de ponerse a buscar minerales. (Evita que lo pierdas).
            fox.goalSelector.addGoal(7, new FoxFollowBerryGoal(fox, 1.2D));
            // 8. Utilidad: Rastrear minerales (Si el área es segura y no te has ido lejos).
            fox.goalSelector.addGoal(8, new FoxTrackOreGoal(fox));

            // --- FASE DE DESCANSO ---
            // 9. Descanso: Si no hay peligro, no hay dueños corriendo y no hay diamantes, busca un árbol y duerme.
            fox.goalSelector.addGoal(9, new FoxSeekShelterSleepGoal(fox, 1.0D));
            // 10. Merodeo: Si no tiene sueño, camina al azar.
            fox.goalSelector.addGoal(10, new net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal(fox, 1.0D) {
                @Override
                public boolean canUse() {
                    if (fox.isSleeping()) return false;
                    return super.canUse();
                }
            });
        }

        if (event.getEntity() instanceof Chicken chicken) {
            chicken.goalSelector.addGoal(1, new net.minecraft.world.entity.ai.goal.AvoidEntityGoal<>(chicken, Fox.class, 12.0F, 1.2D, 1.5D));
        }

        if (event.getEntity() instanceof Phantom phantom) {
            phantom.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(phantom, Fox.class, true));
        }
    }

    public static boolean isTargetNearCreeper(LivingEntity target, double safeDistance) {
        if (target == null) return false;
        List<Creeper> creepers = target.level().getEntitiesOfClass(Creeper.class, target.getBoundingBox().inflate(safeDistance));
        return !creepers.isEmpty();
    }

    public static boolean isLavaBetween(LivingEntity pet, LivingEntity target) {
        if (pet == null || target == null) return false;

        Vec3 start = pet.position();
        Vec3 end = target.position();
        double distance = start.distanceTo(end);

        // No verificamos distancias absurdas para no dar lag
        if (distance > 30.0) return false;

        Vec3 direction = end.subtract(start).normalize();

        // Caminamos bloque por bloque en línea recta hacia el objetivo
        for (double i = 0; i < distance; i += 1.0) {
            Vec3 checkPos = start.add(direction.scale(i));
            BlockPos pos = BlockPos.containing(checkPos.x, checkPos.y, checkPos.z);

            // Verificamos el bloque a la altura del cuerpo y el bloque de los pies
            if (pet.level().getBlockState(pos).is(Blocks.LAVA) || pet.level().getBlockState(pos.below()).is(Blocks.LAVA)) {
                return true; // ¡Lava detectada en la ruta de ataque!
            }
        }
        return false;
    }

    public static boolean isPlayerTrusted(Fox fox, Player player) {
        try {
            // Le pedimos a Java que busque el método privado "trusts" que recibe una LivingEntity
            java.lang.reflect.Method trustsMethod = Fox.class.getDeclaredMethod("trusts", net.minecraft.world.entity.LivingEntity.class);

            // ¡Rompemos el candado privado de Mojang!
            trustsMethod.setAccessible(true);

            // Invocamos el método y devolvemos el resultado (true/false)
            return (boolean) trustsMethod.invoke(fox, player);
        } catch (Exception e) {
            // Si algo falla (por seguridad extrema), devolvemos false para que el juego no se crashed
            return false;
        }
    }

    public static void setFoxSleeping(Fox fox, boolean sleeping) {
        try {
            // Buscamos el método privado de dormir
            java.lang.reflect.Method sleepMethod = Fox.class.getDeclaredMethod("setSleeping", boolean.class);
            sleepMethod.setAccessible(true); // Rompemos el candado
            sleepMethod.invoke(fox, sleeping); // Lo ejecutamos
        } catch (Exception e) {
            // silencioso por seguridad
        }
    }

    public static void applyTacticalSpacing(Fox fox) {
        java.util.List<Fox> allies = fox.level().getEntitiesOfClass(Fox.class, fox.getBoundingBox().inflate(1.5D));
        for (Fox ally : allies) {
            if (ally != fox) {
                double dX = fox.getX() - ally.getX();
                double dZ = fox.getZ() - ally.getZ();
                double dist = Math.sqrt(dX * dX + dZ * dZ);
                if (dist < 1.5 && dist > 0.01) {
                    double force = (1.5 - dist) * 0.15; // Fuerza un poco mayor
                    // 'push' aplica fuerza física directa, ignorando el pathfinding
                    fox.push((dX / dist) * force, 0, (dZ / dist) * force);
                }
            }
        }
    }

    public static void drawJumpTrajectory(Fox fox, double velX, double velY, double velZ) {
        if (!(fox.level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) return;

        double simX = fox.getX();
        double simY = fox.getY();
        double simZ = fox.getZ();

        // simulamos 40 ticks (2 segundos de vuelo)
        for (int i = 0; i < 40; i++) {
            simX += velX;
            simY += velY;
            simZ += velZ;

            // Revisamos qué hay en el bloque que estamos simulando
            net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(simX, simY, simZ);
            boolean hasLava = serverLevel.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.LAVA) ||
                    serverLevel.getBlockState(pos.below()).is(net.minecraft.world.level.block.Blocks.LAVA);

            // Si hay lava debajo de este punto, pintamos fuego. Si es seguro, pintamos chispas verdes.
            net.minecraft.core.particles.SimpleParticleType particle = hasLava ?
                    net.minecraft.core.particles.ParticleTypes.FLAME :
                    net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER;

            // Dibujamos la partícula
            serverLevel.sendParticles(particle, simX, simY + 0.2, simZ, 1, 0, 0, 0, 0);

            // Aplicamos la gravedad y fricción exacta de Minecraft para calcular el siguiente punto
            velY = (velY - 0.08) * 0.98; // Gravedad
            velX *= 0.91;                // Fricción del aire horizontal
            velZ *= 0.91;

            // Si en la simulación choca contra el suelo, detenemos la línea para no dibujar bajo tierra
            if (i > 5 && !serverLevel.getBlockState(pos).getCollisionShape(serverLevel, pos).isEmpty()) {
                break;
            }
        }
    }

    public static boolean isJumpTrajectoryDangerous(Fox fox, double velX, double velY, double velZ) {
        if (!(fox.level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) return false;

        double simX = fox.getX();
        double simY = fox.getY();
        double simZ = fox.getZ();

        for (int i = 0; i < 40; i++) {
            simX += velX;
            simY += velY;
            simZ += velZ;

            net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(simX, simY, simZ);

            // Si la simulación física cae en lava, es 100% mortal.
            if (serverLevel.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.LAVA) ||
                    serverLevel.getBlockState(pos.below()).is(net.minecraft.world.level.block.Blocks.LAVA)) {
                return true;
            }

            velY = (velY - 0.08) * 0.98; // Gravedad
            velX *= 0.91;                // Fricción
            velZ *= 0.91;

            // Si choca con suelo sólido y no era lava, el salto es seguro. Cortamos la simulación.
            if (i > 5 && !serverLevel.getBlockState(pos).getCollisionShape(serverLevel, pos).isEmpty()) {
                return false;
            }
        }
        return false;
    }

    public static void triggerLavaRetreat(Fox fox, LivingEntity target) {
        fox.setIsCrouching(false);
        Vec3 retreatDir = fox.position().subtract(target.position()).normalize();
        net.minecraft.world.level.Level level = fox.level();

        // Escanea desde 8 bloques hacia atrás hasta 2, buscando un bloque de pasto/tierra seguro
        for (int dist = 8; dist >= 2; dist--) {

            net.minecraft.core.BlockPos ground = BlockPos.containing(
                    fox.getX() + retreatDir.x * dist, fox.getY(), fox.getZ() + retreatDir.z * dist
            );
            while (ground.getY() > -64 && level.getBlockState(ground).getCollisionShape(level, ground).isEmpty()) {
                ground = ground.below();
            }

            // Si encontró suelo y NO es lava, huye hacia allí
            if (!level.getBlockState(ground).is(net.minecraft.world.level.block.Blocks.LAVA) &&
                    !level.getBlockState(ground.below()).is(net.minecraft.world.level.block.Blocks.LAVA)) {
                fox.getNavigation().moveTo(ground.getX(), ground.getY() + 1, ground.getZ(), 1.2D);
                return;
            }
        }

        fox.getNavigation().moveTo(fox.getX() + (Math.random() - 0.5) * 8.0, fox.getY(), fox.getZ() + (Math.random() - 0.5) * 8.0, 1.2D);
    }

    public static boolean isPathAheadDangerous(Fox fox, LivingEntity target) {
        Vec3 dir = target.position().subtract(fox.position()).normalize();
        net.minecraft.world.level.Level level = fox.level();

        // Escaneamos 3 metros por delante del zorro en la dirección hacia la que corre
        for (int i = 1; i <= 3; i++) {
            net.minecraft.core.BlockPos frontPos = net.minecraft.core.BlockPos.containing(
                    fox.getX() + dir.x * i,
                    fox.getY(),
                    fox.getZ() + dir.z * i
            );

            // Si el bloque de enfrente (o el suelo que va a pisar) es lava, es peligroso
            if (level.getBlockState(frontPos).is(net.minecraft.world.level.block.Blocks.LAVA) ||
                    level.getBlockState(frontPos.below()).is(net.minecraft.world.level.block.Blocks.LAVA)) {
                return true;
            }
        }
        return false;
    }

    // =====================================================================
    //          supervivencia UNIVERSAL Y TÁCTICA
    // =====================================================================

    public static class PetWaitForFoodGoal extends Goal {
        private final TamableAnimal pet;
        private Player owner;
        private int timeToRecalcPath;

        public PetWaitForFoodGoal(TamableAnimal pet) {
            this.pet = pet;
            this.setFlags(java.util.EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!this.pet.isTame() || this.pet.getTarget() != null) return false;
            Player p = (Player) this.pet.getOwner();
            if (p == null) return false;

            // Filtro 0: Si no tienes comida, te ignoran
            if (!this.pet.isFood(p.getMainHandItem()) && !this.pet.isFood(p.getOffhandItem())) {
                return false;
            }

            // Filtro 1: Tienen baja vida.
            // CORRECCIÓN: Restamos 0.1f para ignorar el bug de decimales de Minecraft
            if (this.pet.getHealth() < (this.pet.getMaxHealth() - 0.1f)) {
                this.owner = p;
                return true;
            }

            // Filtro 2: Están listos para aparearse (Adultos sanos sin cooldown)
            if (this.pet.canFallInLove()) {
                this.owner = p;
                return true;
            }

            // Filtro 3: Son cachorros (Queremos darles de comer para que crezcan rápido)
            if (this.pet.isBaby()) {
                this.owner = p;
                return true;
            }

            // EL RECHAZO: Son adultos, tienen vida completa y están en cooldown. ¡Pónganse a vagar!
            return false;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            this.timeToRecalcPath = 0;
        }

        @Override
        public void tick() {
            this.pet.getLookControl().setLookAt(this.owner, 10.0F, (float)this.pet.getMaxHeadXRot());

            if (this.pet.distanceToSqr(this.owner) > 9.0) {
                if (--this.timeToRecalcPath <= 0) {
                    this.timeToRecalcPath = 10;
                    this.pet.getNavigation().moveTo(this.owner, 1.1D);
                }
            } else {
                this.pet.getNavigation().stop();
                this.timeToRecalcPath = 0;
            }
        }
    }

    public static class PetTacticalRetreatGoal extends Goal {
        private final TamableAnimal pet;
        private Player owner;
        private final double speed;

        public PetTacticalRetreatGoal(TamableAnimal pet, double speed) {
            this.pet = pet;
            this.speed = speed;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!this.pet.isTame() || this.pet.getTarget() == null) return false;
            if (this.pet.getHealth() <= this.pet.getMaxHealth() * 0.25f) {
                Player p = (Player) this.pet.getOwner();
                if (p != null) {
                    this.owner = p;
                    return true;
                }
            }
            return false;
        }

        @Override
        public void tick() {
            this.pet.setTarget(null);
            Vec3 playerLook = this.owner.getViewVector(1.0F).normalize().scale(-2.0);
            Vec3 hidePos = this.owner.position().add(playerLook.x, 0, playerLook.z);
            this.pet.getNavigation().moveTo(hidePos.x, this.owner.getY(), hidePos.z, this.speed);
        }
    }

    public static class PetFleeLavaGoal extends Goal {
        private final TamableAnimal pet;
        private final double speed;
        private BlockPos dangerLava;

        public PetFleeLavaGoal(TamableAnimal pet, double speed) {
            this.pet = pet;
            this.speed = speed;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!this.pet.isAlive()) return false;
            BlockPos pos = this.pet.blockPosition();
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        BlockPos check = pos.offset(x, y, z);
                        if (this.pet.level().getBlockState(check).is(Blocks.LAVA)) {
                            this.dangerLava = check;
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        @Override
        public void tick() {
            if (this.dangerLava != null) {
                Vec3 fleeDir = this.pet.position().subtract(Vec3.atCenterOf(this.dangerLava)).normalize().scale(2.0);
                Vec3 safeSpot = this.pet.position().add(fleeDir);
                this.pet.getNavigation().moveTo(safeSpot.x, safeSpot.y, safeSpot.z, this.speed);
            }
        }
    }

    public static class PetFollowBehindOwnerGoal extends Goal {
        private final TamableAnimal pet;
        private Player owner;
        private final double speed;
        private final float stopDistance;
        private final float startDistance;
        private int timeToRecalcPath; // NUEVO: Temporizador

        public PetFollowBehindOwnerGoal(TamableAnimal pet, double speed, float startDist, float stopDist) {
            this.pet = pet;
            this.speed = speed;
            this.startDistance = startDist;
            this.stopDistance = stopDist;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!this.pet.isTame() || this.pet.isOrderedToSit() || this.pet.getTarget() != null) return false;

            if (this.pet.getOwner() instanceof Player p) {
                if (this.pet instanceof Wolf) {
                    boolean hasArmor = !this.pet.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.BODY).isEmpty();
                    if (!hasArmor) {
                        if (PetAiModifier.isTargetNearCreeper(this.pet, 12.0) || PetAiModifier.isTargetNearCreeper(p, 12.0)) {
                            return false;
                        }
                    }
                }

                if (this.pet.distanceToSqr(p) < (double)(this.startDistance * this.startDistance)) return false;
                this.owner = p;
                return true;
            }
            return false;
        }

        @Override
        public boolean canContinueToUse() {
            if (this.pet.getTarget() != null || this.pet.isOrderedToSit()) return false;

            if (this.pet instanceof Wolf) {
                boolean hasArmor = !this.pet.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.BODY).isEmpty();
                if (!hasArmor) {
                    if (PetAiModifier.isTargetNearCreeper(this.pet, 12.0) || PetAiModifier.isTargetNearCreeper(this.owner, 12.0)) {
                        return false;
                    }
                }
            }

            return this.pet.distanceToSqr(this.owner) > (this.stopDistance * this.stopDistance);
        }

        @Override
        public void start() {
            this.timeToRecalcPath = 0; // Reiniciamos al iniciar la meta
        }

        @Override
        public void tick() {
            if (this.owner.isInWater() || this.owner.isSwimming()) {
                this.pet.setPathfindingMalus(PathType.WATER, 0.0F);
            } else {
                this.pet.setPathfindingMalus(PathType.WATER, -1.0F);
            }

            Vec3 playerLook = this.owner.getViewVector(1.0F).normalize().scale(-3.0);
            Vec3 targetPos = this.owner.position().add(playerLook.x, 0, playerLook.z);

            this.pet.getLookControl().setLookAt(this.owner, 10.0F, (float)this.pet.getMaxHeadXRot());

            double distanceToOwner = this.pet.distanceToSqr(this.owner);

            if (distanceToOwner >= 144.0) {
                BlockPos safeGround = findSafeGround(this.pet.level(), targetPos.x, this.owner.getY(), targetPos.z);

                if (safeGround != null) {
                    this.pet.teleportTo(safeGround.getX() + 0.5, safeGround.getY(), safeGround.getZ() + 0.5);
                } else {
                    BlockPos ownerGround = findSafeGround(this.pet.level(), this.owner.getX(), this.owner.getY(), this.owner.getZ());
                    if (ownerGround != null) {
                        this.pet.teleportTo(ownerGround.getX() + 0.5, ownerGround.getY(), ownerGround.getZ() + 0.5);
                    }
                }
                this.pet.getNavigation().stop();
                return;
            }

            float dynamicStopDistance = this.stopDistance;
            if (this.pet.level().getBlockState(this.pet.blockPosition().below()).is(Blocks.LAVA)) {
                dynamicStopDistance += 3.0F;
            }

            // CORRECCIÓN: Solo exige nueva ruta cada 10 ticks (medio segundo)
            if (this.pet.distanceToSqr(targetPos) > (dynamicStopDistance * dynamicStopDistance)) {
                if (--this.timeToRecalcPath <= 0) {
                    this.timeToRecalcPath = 10;
                    this.pet.getNavigation().moveTo(targetPos.x, this.owner.getY(), targetPos.z, this.speed);
                }
            } else {
                this.pet.getNavigation().stop();
                this.timeToRecalcPath = 0;
            }
        }

        private BlockPos findSafeGround(Level level, double x, double ownerY, double z) {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, ownerY + 2, z);
            while (pos.getY() > ownerY - 30 && pos.getY() > level.getMinY()) {
                pos.move(Direction.DOWN);
                BlockState state = level.getBlockState(pos);
                BlockState above1 = level.getBlockState(pos.above());
                BlockState above2 = level.getBlockState(pos.above(2));

                if (!state.getCollisionShape(level, pos).isEmpty() && !state.is(Blocks.LAVA)
                        && above1.getCollisionShape(level, pos.above()).isEmpty() && !above1.is(Blocks.LAVA)
                        && above2.getCollisionShape(level, pos.above(2)).isEmpty()) {
                    return pos.above();
                }
            }
            return null;
        }
    }

    public static class PetFollowLeaderGoal extends Goal {
        private final TamableAnimal pet;
        private TamableAnimal alpha;
        private final double speed;
        private int timeToRecalcPath; // NUEVO

        public PetFollowLeaderGoal(TamableAnimal pet, double speed) {
            this.pet = pet;
            this.speed = speed;
        }

        @Override
        public boolean canUse() {
            if (!this.pet.isTame() || this.pet.isOrderedToSit() || this.pet.getTarget() != null) return false;
            LivingEntity owner = this.pet.getOwner();
            if (owner == null) return false;

            List<? extends TamableAnimal> pack = this.pet.level().getEntitiesOfClass(this.pet.getClass(), this.pet.getBoundingBox().inflate(12.0));
            TamableAnimal bestLeader = null;

            double closestDistToOwner = this.pet.distanceToSqr(owner);

            for (TamableAnimal mate : pack) {
                if (mate != this.pet && mate.isTame() && mate.getOwner() == owner && !mate.isOrderedToSit()) {
                    double distToOwner = mate.distanceToSqr(owner);

                    if (distToOwner < closestDistToOwner - 4.0) {
                        closestDistToOwner = distToOwner;
                        bestLeader = mate;
                    }
                }
            }

            if (bestLeader != null) {
                this.alpha = bestLeader;
                return true;
            }
            return false;
        }

        @Override
        public boolean canContinueToUse() {
            if (this.alpha == null || !this.alpha.isAlive() || this.pet.getTarget() != null) return false;
            LivingEntity owner = this.pet.getOwner();
            if (owner == null) return false;
            return this.alpha.distanceToSqr(owner) < this.pet.distanceToSqr(owner);
        }

        @Override
        public void start() {
            this.timeToRecalcPath = 0;
        }

        @Override
        public void tick() {
            if (this.alpha != null) {
                if (this.pet.distanceToSqr(this.alpha) > 4.0) {
                    if (--this.timeToRecalcPath <= 0) {
                        this.timeToRecalcPath = 10;
                        this.pet.getNavigation().moveTo(this.alpha, this.speed);
                    }
                } else {
                    this.pet.getNavigation().stop();
                    this.timeToRecalcPath = 0;
                }
            }
        }

        @Override
        public void stop() {
            this.alpha = null;
        }
    }

    // =====================================================================
    //                       IA DE LOS Lobos
    // =====================================================================

    public static class WolfPackTargetGoal<T extends Mob> extends NearestAttackableTargetGoal<T> {
        private final Wolf wolf;

        public WolfPackTargetGoal(Wolf wolf, Class<T> targetType, boolean mustSee) {
            super(wolf, targetType, mustSee);
            this.wolf = wolf;
        }

        @Override
        public boolean canUse() {
            boolean canTarget = super.canUse();
            if (canTarget && this.target != null) {

                // CORRECCIÓN LAVA: Si hay lava en medio de la ruta, abandona al objetivo
                if (PetAiModifier.isLavaBetween(this.wolf, this.target)) return false;

                boolean hasArmor = !this.wolf.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.BODY).isEmpty();
                if (!hasArmor && PetAiModifier.isTargetNearCreeper(this.target, 7.0)) return false;

                List<Wolf> pack = this.wolf.level().getEntitiesOfClass(Wolf.class, this.target.getBoundingBox().inflate(15.0));
                int attackers = 0;
                for (Wolf w : pack) {
                    if (this.target == w.getTarget()) attackers++;
                }
                if (attackers >= 3) return false;
            }
            return canTarget;
        }

        @Override
        public boolean canContinueToUse() {
            if (this.target != null) {
                // CORRECCIÓN LAVA: Aborta si de repente aparece lava entre ellos (o tú la pones)
                if (PetAiModifier.isLavaBetween(this.wolf, this.target)) return false;

                boolean hasArmor = !this.wolf.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.BODY).isEmpty();
                if (!hasArmor && PetAiModifier.isTargetNearCreeper(this.target, 7.0)) {
                    return false;
                }
            }
            return super.canContinueToUse();
        }
    }

    // Método para evitar que el lobo se lance por precipicios mortales al atacar
    public static boolean isSafeDropToTarget(Wolf wolf, net.minecraft.world.entity.LivingEntity target) {
        if (target == null) return true;

        double wolfY = wolf.getY();
        double targetY = target.getY();

        if (wolfY - targetY > 3.5D) {
            double distanceSq = wolf.distanceToSqr(target);
            return distanceSq < 25.0D; // ¡Es un risco peligroso, no bajes!
        }
        return false;
    }

    public static class WolfRandomHostileGoal extends NearestAttackableTargetGoal<Mob> {
        private final Wolf wolf;

        public WolfRandomHostileGoal(Wolf wolf) {
            super(wolf, Mob.class, 10, true, false, null);
            this.wolf = wolf;
        }

        @Override
        public boolean canUse() {
            if (this.wolf.isBaby()) return false;

            LivingEntity owner = this.wolf.getOwner();
            if (owner != null && this.wolf.distanceToSqr(owner) > 144.0D) {
                return false;
            }

            if (!super.canUse()) return false;
            if (!(this.target instanceof Enemy) || this.target instanceof Witch) return false;
            if (PetAiModifier.isSafeDropToTarget(this.wolf, this.target)) {
                this.wolf.setTarget(null);
                return false;
            }
            if (PetAiModifier.isLavaBetween(this.wolf, this.target)) return false;

            boolean esMiedoso = this.wolf.getPersistentData().getBoolean("EsMiedoso").orElse(false);

            if (esMiedoso) {
                long packSize = this.wolf.level().getEntitiesOfClass(Wolf.class, this.wolf.getBoundingBox().inflate(12.0D),
                        w -> w.isTame() && w.getOwner() == this.wolf.getOwner()).size();

                if (packSize < 3) {
                    if (this.wolf.getRandom().nextInt(15) == 0) {
                        this.wolf.playSound(net.minecraft.sounds.SoundEvents.WOLF_WHINE_BABY.value(), 0.7F, 1.2F);
                        if (this.wolf.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SPLASH,
                                    this.wolf.getX(), this.wolf.getY() + 0.6, this.wolf.getZ(),
                                    4, 0.3, 0.2, 0.3, 0.0);
                        }
                    }
                    return false;
                }
            }

            // CORRECCIÓN LAVA
            if (PetAiModifier.isLavaBetween(this.wolf, this.target)) return false;

            boolean hasArmor = !this.wolf.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.BODY).isEmpty();

            if (this.target instanceof Creeper) {
                return hasArmor;
            }

            return hasArmor || !PetAiModifier.isTargetNearCreeper(this.target, 7.0);
        }

        @SubscribeEvent
        public static void onWolfTarget(net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent event) {
            if (event.getEntity() instanceof net.minecraft.world.entity.animal.wolf.Wolf attacker &&
                    event.getTargetType() instanceof net.minecraft.world.entity.animal.wolf.Wolf target) {

                if (attacker.isTame() && target.isTame() && attacker.getOwner() == target.getOwner()) {
                    event.setCanceled(true);
                }
            }
        }

        @Override
        public boolean canContinueToUse() {
            // REGLA DE LA CORREA 2: Abortar cacerías que se alejen demasiado
            LivingEntity owner = this.wolf.getOwner();
            if (owner != null && this.wolf.distanceToSqr(owner) > 225.0D) { // Límite de 15 bloques
                this.wolf.setTarget(null); // Olvida al objetivo
                return false;
            }
            if (PetAiModifier.isSafeDropToTarget(this.wolf, this.wolf.getTarget())) {
                this.wolf.setTarget(null);
                return false;
            }
            return super.canContinueToUse();
        }
    }

    public static class WolfFleeCreeperGoal extends Goal {
        private final Wolf wolf;
        private Creeper dangerCreeper;
        private final float distance;
        private final double speed;

        public WolfFleeCreeperGoal(Wolf wolf, float distance, double speed) {
            this.wolf = wolf;
            this.distance = distance;
            this.speed = speed;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!this.wolf.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.BODY).isEmpty()) {
                return false;
            }

            List<Creeper> creepers = this.wolf.level().getEntitiesOfClass(Creeper.class, this.wolf.getBoundingBox().inflate(this.distance));
            if (creepers.isEmpty()) return false;

            Creeper closest = null;
            double minDist = Double.MAX_VALUE;
            for (Creeper c : creepers) {
                double d = this.wolf.distanceToSqr(c);
                if (d < minDist) {
                    minDist = d;
                    closest = c;
                }
            }
            this.dangerCreeper = closest;

            if (this.wolf.getTarget() != null) {
                if (this.wolf.getTarget().distanceToSqr(this.dangerCreeper) > 49.0) {
                    return false;
                } else {
                    this.wolf.setTarget(null);
                }
            }
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return this.dangerCreeper != null && this.dangerCreeper.isAlive() && this.wolf.distanceToSqr(this.dangerCreeper) < (this.distance * this.distance);
        }

        @Override
        public void tick() {
            if (this.dangerCreeper != null) {
                Vec3 fleeDir = this.wolf.position().subtract(this.dangerCreeper.position()).normalize().scale(10.0);
                Vec3 safeSpot = this.wolf.position().add(fleeDir);
                this.wolf.getNavigation().moveTo(safeSpot.x, safeSpot.y, safeSpot.z, this.speed);
            }
        }

        @Override
        public void stop() {
            this.dangerCreeper = null;
        }
    }

    public static class WolfFearDarknessGoal extends Goal {
        private final Wolf wolf;
        private final double speed;
        private int timeToRecalcPath; // NUEVO

        public WolfFearDarknessGoal(Wolf wolf, double speed) {
            this.wolf = wolf;
            this.speed = speed;
            this.setFlags(java.util.EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (this.wolf.isBaby() || !this.wolf.isTame() || this.wolf.getTarget() != null || this.wolf.isOrderedToSit()) return false;
            if (!this.wolf.getPersistentData().getBoolean("EsMiedoso").orElse(false)) return false;

            long packSize = this.wolf.level().getEntitiesOfClass(Wolf.class, this.wolf.getBoundingBox().inflate(12.0D),
                    w -> w.isTame() && w.getOwner() == this.wolf.getOwner()).size();

            if (packSize >= 3) return false;

            LivingEntity owner = this.wolf.getOwner();
            if (owner == null) return false;

            int lightLevel = this.wolf.level().getMaxLocalRawBrightness(this.wolf.blockPosition());
            return lightLevel < 6;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            this.wolf.playSound(net.minecraft.sounds.SoundEvents.WOLF_WHINE_BABY.value(), 0.7F, 1.2F);
            this.timeToRecalcPath = 0;
        }

        @Override
        public void tick() {
            LivingEntity owner = this.wolf.getOwner();
            if (owner != null) {
                this.wolf.getLookControl().setLookAt(owner, 10.0F, (float)this.wolf.getMaxHeadXRot());

                if (this.wolf.distanceToSqr(owner) > 16.0D) {
                    if (--this.timeToRecalcPath <= 0) {
                        this.timeToRecalcPath = 10;
                        this.wolf.getNavigation().moveTo(owner, this.speed);
                    }
                } else {
                    this.wolf.getNavigation().stop();
                    this.timeToRecalcPath = 0;
                }
            }
        }
    }

    // =====================================================================
    //                       IA DE LOS Gatos
    // =====================================================================

    // Asustar CREEPERS
    public static class CatChaseCreeperGoal extends Goal {
        private final Cat cat;
        private Creeper targetCreeper;
        private final double speedModifier;
        private final double maxDistance;

        public CatChaseCreeperGoal(Cat cat, double speed, double maxDistance) {
            this.cat = cat;
            this.speedModifier = speed;
            this.maxDistance = maxDistance;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            LivingEntity owner = this.cat.getOwner();
            if (owner != null && this.cat.distanceToSqr(owner) > 400.0) return false;

            List<Creeper> creepers = this.cat.level().getEntitiesOfClass(Creeper.class, this.cat.getBoundingBox().inflate(this.maxDistance));
            for (Creeper creeper : creepers) {

                // --- NUEVO: VISIÓN TÉRMICA FELINA ---
                // Si hay lava entre el gato y el Creeper, ignora a ese Creeper y busca otro
                if (PetAiModifier.isLavaBetween(this.cat, creeper)) continue;

                boolean alreadyTargeted = false;
                List<Cat> nearbyCats = this.cat.level().getEntitiesOfClass(Cat.class, creeper.getBoundingBox().inflate(15.0));
                for (Cat otherCat : nearbyCats) {
                    if (otherCat != this.cat && creeper == otherCat.getTarget()) {
                        alreadyTargeted = true;
                        break;
                    }
                }
                if (!alreadyTargeted) {
                    this.targetCreeper = creeper;
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity owner = this.cat.getOwner();
            if (owner != null && this.cat.distanceToSqr(owner) > 400.0) return false;

            // --- NUEVO: ABORTAR MISIÓN SI APARECE LAVA ---
            if (this.targetCreeper != null && PetAiModifier.isLavaBetween(this.cat, this.targetCreeper)) {
                return false;
            }

            return this.targetCreeper != null && this.targetCreeper.isAlive() && this.cat.distanceToSqr(this.targetCreeper) < (this.maxDistance * this.maxDistance * 2);
        }

        @Override
        public void start() {
            this.cat.setTarget(this.targetCreeper);
        }

        @Override
        public void tick() {
            this.cat.getLookControl().setLookAt(this.targetCreeper, 30.0F, 30.0F);
            this.cat.getNavigation().moveTo(this.targetCreeper, this.speedModifier);
        }

        @Override
        public void stop() {
            this.targetCreeper = null;
            this.cat.setTarget(null);
            this.cat.getNavigation().stop();
        }
    }

    public static class CatWarnDangerGoal extends Goal {
        private final Cat cat;
        private int cooldown = 0; // Para que no sisee como una ametralladora

        public CatWarnDangerGoal(Cat cat) {
            this.cat = cat;
        }

        @Override
        public boolean canUse() {
            if (!this.cat.isTame() || this.cat.isSleeping()) return false;

            if (this.cooldown > 0) {
                this.cooldown--;
                return false;
            }

            // Escanea 12 bloques a la redonda en busca de hostiles
            java.util.List<Mob> dangers = this.cat.level().getEntitiesOfClass(
                    Mob.class,
                    this.cat.getBoundingBox().inflate(12.0),
                    entity -> entity instanceof Enemy
            );

            if (!dangers.isEmpty()) {
                // Elige un monstruo al azar de los que detectó para mirarlo
                LivingEntity threat = dangers.getFirst();
                this.cat.getLookControl().setLookAt(threat, 30.0F, 30.0F);

                // Reproduce el siseo de advertencia (Hiss)
                this.cat.playSound(net.minecraft.sounds.SoundEvents.CAT_HISS_BABY.value(), 0.8F, 0.5F);
                // Entra en enfriamiento por 10 segundos (200 ticks) para no saturar tus oídos
                this.cooldown = 200;
                return true;
            }
            return false;
        }
    }

    // =====================================================================
    //                       IA DE LOS Zorros
    // =====================================================================

    // SEGUIDOR FIEL
    public static class FoxFollowBerryGoal extends Goal {
        private final Fox fox;
        private Player owner;
        private final double speed;

        public FoxFollowBerryGoal(Fox fox, double speed) {
            this.fox = fox;
            this.speed = speed;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (this.fox.isBaby()) return false;

            int tiempoContrato = this.fox.getPersistentData().getInt("ContratoMercenario").orElse(0);
            if (this.fox.tickCount >= tiempoContrato) return false;

            // Aumentamos el rango de escaneo para que no se pierda tan fácil
            Player p = this.fox.level().getNearestPlayer(this.fox, 30.0);
            if (p == null || !PetAiModifier.isPlayerTrusted(this.fox, p)) return false;

            if (this.fox.distanceToSqr(p) < 100.0) return false;

            this.owner = p;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            if (this.owner == null || !this.owner.isAlive()) return false;

            int tiempoContrato = this.fox.getPersistentData().getInt("ContratoMercenario").orElse(0);

            if (this.fox.tickCount >= tiempoContrato) {
                if (this.fox.tickCount == tiempoContrato + 1 && this.fox.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                    sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE, this.fox.getX(), this.fox.getY() + 0.8, this.fox.getZ(), 5, 0.2, 0.2, 0.2, 0.0);
                    this.fox.playSound(net.minecraft.sounds.SoundEvents.FOX_SCREECH, 0.7F, 1.0F);
                }
                return false;
            }

            // Una vez que empecé a correr hacia ti, no me detengo hasta estar a 3 bloques (9.0)
            return this.fox.distanceToSqr(this.owner) > 9.0;
        }

        @Override
        public void tick() {
            // Hacemos que solo te mire si está activamente corriendo hacia ti
            this.fox.getLookControl().setLookAt(this.owner, 10.0F, (float)this.fox.getMaxHeadXRot());
            double dist = this.fox.distanceToSqr(this.owner);

            // Si te alejas a más de 16 bloques (256), hace teleport
            if (dist >= 256.0) {
                BlockPos safeGround = findSafeGroundFox(this.fox.level(), this.owner.getX(), this.owner.getY(), this.owner.getZ());
                if (safeGround != null) {
                    this.fox.teleportTo(safeGround.getX() + 0.5, safeGround.getY(), safeGround.getZ() + 0.5);
                    this.fox.getNavigation().stop();
                }
                return;
            }

            this.fox.getNavigation().moveTo(this.owner, this.speed);
            this.fox.setSitting(false);
        }

        private BlockPos findSafeGroundFox(Level level, double x, double ownerY, double z) {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, ownerY + 2, z);
            while (pos.getY() > ownerY - 30 && pos.getY() > level.getMinY()) {
                pos.move(Direction.DOWN);
                BlockState state = level.getBlockState(pos);
                if (!state.getCollisionShape(level, pos).isEmpty() && !state.is(Blocks.LAVA)) {
                    return pos.above();
                }
            }
            return null;
        }
    }

    // CAZA-FANTASMAS
    public static class FoxAttackPhantomGoal extends Goal {
        private final Fox fox;
        private final double speed;
        private int stalkTimer = 0;
        private int jumpCooldown = 0;
        private boolean hitLanded = false;
        private int lavaRetreatTimer = 0;

        private double calcX, calcY, calcZ;
        private boolean isPreparingJump = false;

        public FoxAttackPhantomGoal(Fox fox, double speed) {
            this.fox = fox;
            this.speed = speed;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            if (this.fox.isBaby()) return false;
            return this.fox.getTarget() instanceof Phantom && this.fox.getTarget().isAlive();
        }

        @Override
        public void tick() {
            if (this.lavaRetreatTimer > 0) {
                this.lavaRetreatTimer--;
                return;
            }

            PetAiModifier.applyTacticalSpacing(this.fox);

            LivingEntity target = this.fox.getTarget();
            if (target == null || !target.isAlive() || this.fox.distanceToSqr(target) > 324.0) {
                this.fox.setTarget(null);
                this.isPreparingJump = false;
                this.stalkTimer = 0;
                return;
            }

            this.fox.getLookControl().setLookAt(target.getX(), target.getY(), target.getZ());

            // NUEVO: Escáner frontal constante. Si en algún momento la persecución lo lleva a la lava, huye.
            if (this.fox.onGround() && PetAiModifier.isPathAheadDangerous(this.fox, target)) {
                this.lavaRetreatTimer = 40;
                this.stalkTimer = 0;
                this.isPreparingJump = false;
                PetAiModifier.triggerLavaRetreat(this.fox, target);
                return;
            }

            if (this.jumpCooldown > 0) {
                this.jumpCooldown--;
                if (!this.fox.onGround() && !this.hitLanded && this.fox.distanceToSqr(target) < 16.0) {
                    if (this.fox.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                        this.fox.doHurtTarget(sl, target);
                    }
                    this.hitLanded = true;
                }
                if (this.fox.onGround()) this.fox.getNavigation().moveTo(target, this.speed);
                return;
            }

            double dist = this.fox.distanceToSqr(target);

            if (dist <= 256.0 && this.fox.onGround()) {
                this.fox.getNavigation().stop();

                if (!this.isPreparingJump) {
                    this.isPreparingJump = true;
                    this.fox.setIsCrouching(true);
                    this.stalkTimer = 0;

                    Vec3 jumpDir = target.position().subtract(this.fox.position()).normalize();
                    this.calcY = Math.min(jumpDir.y * 0.7 + 0.5, 1.2);
                    this.calcX = jumpDir.x * 0.8;
                    this.calcZ = jumpDir.z * 0.8;

                    if (PetAiModifier.isJumpTrajectoryDangerous(this.fox, this.calcX, this.calcY, this.calcZ)) {
                        this.lavaRetreatTimer = 40;
                        this.isPreparingJump = false;
                        PetAiModifier.triggerLavaRetreat(this.fox, target);
                        return;
                    }
                }

                this.stalkTimer++;

                if (this.stalkTimer % 2 == 0) {
                    PetAiModifier.drawJumpTrajectory(this.fox, this.calcX, this.calcY, this.calcZ);
                }

                if (this.stalkTimer >= 10) {
                    this.fox.setIsCrouching(false);
                    this.isPreparingJump = false;
                    this.hitLanded = false;
                    this.fox.setDeltaMovement(this.calcX, this.calcY, this.calcZ);
                    this.stalkTimer = 0;
                    this.jumpCooldown = 50;
                }
            } else {
                this.fox.setIsCrouching(false);
                this.isPreparingJump = false;
                this.stalkTimer = 0;
                if (this.fox.onGround()) this.fox.getNavigation().moveTo(target, this.speed);
            }
        }

        @Override
        public void stop() {
            this.fox.setIsCrouching(false);
            this.isPreparingJump = false;
            this.stalkTimer = 0;
            this.jumpCooldown = 0;
            this.hitLanded = false;
            this.lavaRetreatTimer = 0;
        }
    }

    // ATAQUE DEPREDADOR Terrestre
    public static class FoxTacticalMeleeGoal extends MeleeAttackGoal {
        private final Fox fox;
        private boolean isPreparingJump = false;
        private int prepTimer = 0;
        private int lavaRetreatTimer = 0;
        private int jumpCooldown = 0;

        // Variables para no recalcular la física cada tick
        private double calcX, calcY, calcZ;

        public FoxTacticalMeleeGoal(Fox fox, double speed) {
            super(fox, speed, true);
            this.fox = fox;
        }

        @Override
        public void tick() {
            if (this.lavaRetreatTimer > 0) {
                this.lavaRetreatTimer--;
                return;
            }

            PetAiModifier.applyTacticalSpacing(this.fox);

            LivingEntity enemy = this.fox.getTarget();
            if (enemy == null || !enemy.isAlive() || this.fox.distanceToSqr(enemy) > 225.0) {
                this.fox.setTarget(null); // Borra su objetivo
                this.isPreparingJump = false;
                return; // Aborta la persecución
            }

            double dX = enemy.getX() - this.fox.getX();
            double dZ = enemy.getZ() - this.fox.getZ();
            double horizDist = Math.sqrt(dX * dX + dZ * dZ);

            if (this.fox.onGround()) {
                this.fox.getLookControl().setLookAt(enemy, 30.0F, 30.0F);
                boolean enemyIsHigh = enemy.getY() > this.fox.getY() + 1.0;

                if (enemyIsHigh && horizDist < 3.0) {
                    this.isPreparingJump = true;
                    this.fox.setIsCrouching(true);
                    Vec3 retreatDir = this.fox.position().subtract(enemy.position()).normalize();
                    this.fox.getNavigation().moveTo(this.fox.getX() + retreatDir.x * 2.0, this.fox.getY(), this.fox.getZ() + retreatDir.z * 2.0, 1.0D);
                    return;
                }
                else if (horizDist >= 3.0 && horizDist <= 10.0) {
                    if (!this.isPreparingJump && this.jumpCooldown <= 0) {

                        // PROBABILIDAD: 60% de saltar, 40% de correr a pie
                        if (this.fox.getRandom().nextFloat() > 0.60F) {
                            this.jumpCooldown = 60; // Decidió correr. Se obliga a correr por 3 segundos sin intentar saltos.
                        } else {
                            // Decidió saltar
                            this.isPreparingJump = true;
                            this.fox.setIsCrouching(true);
                            this.prepTimer = 0;

                            Vec3 jumpDir = new Vec3(dX, 0, dZ).normalize();
                            double forceHorizontal = Math.min(horizDist * 0.15 + 0.3, 1.2);
                            this.calcY = enemyIsHigh ? 0.75 : 0.5;
                            this.calcX = jumpDir.x * forceHorizontal;
                            this.calcZ = jumpDir.z * forceHorizontal;

                            if (PetAiModifier.isJumpTrajectoryDangerous(this.fox, this.calcX, this.calcY, this.calcZ)) {
                                this.lavaRetreatTimer = 40;
                                this.isPreparingJump = false;
                                this.fox.setIsCrouching(false);
                                PetAiModifier.triggerLavaRetreat(this.fox, enemy);
                                return;
                            }
                        }
                    }

                    // Si está en medio de la preparación, dibuja partículas y salta
                    if (this.isPreparingJump) {
                        this.fox.getNavigation().stop(); // Detiene las patas para concentrarse
                        this.prepTimer++;

                        if (this.prepTimer % 2 == 0) {
                            PetAiModifier.drawJumpTrajectory(this.fox, this.calcX, this.calcY, this.calcZ);
                        }

                        if (this.prepTimer >= 10) {
                            this.fox.setIsCrouching(false);
                            this.isPreparingJump = false;
                            this.prepTimer = 0;
                            this.jumpCooldown = 50; // Descansa 2.5 segundos antes de intentar otro salto
                            this.fox.setDeltaMovement(this.calcX, this.calcY, this.calcZ);
                        }
                        return; // Evita que corra normalmente mientras vuela
                    }
                }
            }

            if (this.isPreparingJump) {
                this.fox.setIsCrouching(false);
                this.isPreparingJump = false;
                this.prepTimer = 0;
            }

            // NUEVO: Antes de correr ciegamente hacia el enemigo, revisa sus bigotes tácticos
            if (PetAiModifier.isPathAheadDangerous(this.fox, enemy)) {
                this.lavaRetreatTimer = 40;
                PetAiModifier.triggerLavaRetreat(this.fox, enemy);
                return; // Bloquea el motor de caminar por defecto
            }

            // Si el camino a pie está limpio, persigue normalmente
            super.tick();
        }

        @Override
        protected void checkAndPerformAttack(@NonNull LivingEntity enemy) {
            if (this.lavaRetreatTimer > 0) return;
            double dist = this.fox.distanceToSqr(enemy);
            double attackReachSqr = this.fox.getBbWidth() * 2.0F * this.fox.getBbWidth() * 2.0F + enemy.getBbWidth();
            if (dist <= attackReachSqr && this.getTicksUntilNextAttack() <= 0) {
                this.resetAttackCooldown();
                if (this.fox.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                    this.fox.doHurtTarget(sl, enemy);
                }
            }
        }

        @Override
        public void stop() {
            this.fox.setIsCrouching(false);
            this.isPreparingJump = false;
            this.prepTimer = 0;
            this.lavaRetreatTimer = 0;
            this.jumpCooldown = 0;
            super.stop();
        }
    }

    // MERCENARIO
    public static class FoxMercenaryTargetGoal extends Goal {
        private final Fox fox;
        private LivingEntity target;

        public FoxMercenaryTargetGoal(Fox fox) {
            this.fox = fox;
            this.setFlags(EnumSet.of(Goal.Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (this.fox.isBaby()) return false;

            if (!this.fox.getItemBySlot(EquipmentSlot.MAINHAND).is(ItemTags.SWORDS)) {
                return false;
            }

            Player p = this.fox.level().getNearestPlayer(this.fox, 15.0);
            if (p == null) return false;

            if ((p.getLastHurtByMob() != null && p.getLastHurtByMob().isAlive()) ||
                    (p.getLastHurtMob() != null && p.getLastHurtMob().isAlive())) {

                if (PetAiModifier.isPlayerTrusted(this.fox, p)) {
                    this.target = p.getLastHurtByMob() != null ? p.getLastHurtByMob() : p.getLastHurtMob();
                    return true;
                }
            }
            return false;
        }

        @Override
        public void start() {
            this.fox.setTarget(this.target);
        }
    }

    // Rastreador DE Minerales
    public static class FoxTrackOreGoal extends Goal {
        private final Fox fox;
        private BlockPos targetOre;
        private int scanCooldown = 0;

        public FoxTrackOreGoal(Fox fox) {
            this.fox = fox;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (this.fox.isBaby()) return false;
            ItemStack mouthItem = this.fox.getItemBySlot(EquipmentSlot.MAINHAND);
            if (mouthItem.isEmpty()) return false;

            if (this.targetOre == null) {
                if (this.scanCooldown > 0) {
                    this.scanCooldown--;
                    return false;
                }

                this.targetOre = findClosestOre(mouthItem);
                this.scanCooldown = 40;
            }

            return this.targetOre != null;
        }

        @Override
        public boolean canContinueToUse() {
            ItemStack mouthItem = this.fox.getItemBySlot(EquipmentSlot.MAINHAND);
            if (mouthItem.isEmpty() || this.targetOre == null) return false;

            BlockState state = this.fox.level().getBlockState(this.targetOre);
            return isMatchingOre(state, mouthItem);
        }

        @Override
        public void tick() {
            if (this.targetOre != null) {
                double distanceToOreSqr = this.fox.distanceToSqr(Vec3.atCenterOf(this.targetOre));

                if (distanceToOreSqr > 9.0) {
                    this.fox.getNavigation().moveTo(this.targetOre.getX(), this.targetOre.getY(), this.targetOre.getZ(), 1.2D);
                    this.fox.setSitting(false);
                }
                else {
                    this.fox.getNavigation().stop();
                    this.fox.getLookControl().setLookAt(this.targetOre.getX() + 0.5, this.targetOre.getY() + 0.5, this.targetOre.getZ() + 0.5);
                    this.fox.setSitting(true);

                    if (this.fox.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                        if (this.fox.tickCount % 8 == 0) {
                            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
                                    this.targetOre.getX() + 0.5, this.targetOre.getY() + 0.5, this.targetOre.getZ() + 0.5,
                                    4, 0.3, 0.3, 0.3, 0.0);
                        }
                    }
                }
            }
        }

        @Override
        public void stop() {
            this.targetOre = null;
            this.fox.setSitting(false);
            this.fox.getNavigation().stop();
            this.scanCooldown = 40;
        }

        private BlockPos findClosestOre(ItemStack stack) {
            BlockPos foxPos = this.fox.blockPosition();
            BlockPos closest = null;
            double minDistance = Double.MAX_VALUE;

            for (int x = -15; x <= 15; x++) {
                for (int y = -8; y <= 8; y++) {
                    for (int z = -15; z <= 15; z++) {
                        BlockPos checkPos = foxPos.offset(x, y, z);
                        BlockState state = this.fox.level().getBlockState(checkPos);

                        if (isMatchingOre(state, stack)) {
                            double dX = checkPos.getX() - foxPos.getX();
                            double dY = checkPos.getY() - foxPos.getY();
                            double dZ = checkPos.getZ() - foxPos.getZ();
                            double penalisedDist = (dX * dX) + (dY * dY * 4.0) + (dZ * dZ);

                            if (penalisedDist < minDistance) {
                                minDistance = penalisedDist;
                                closest = checkPos;
                            }
                        }
                    }
                }
            }
            return closest;
        }

        private boolean isMatchingOre(BlockState state, ItemStack stack) {
            if (stack.is(Items.RAW_IRON) || stack.is(Items.IRON_INGOT)) return state.is(Blocks.IRON_ORE) || state.is(Blocks.DEEPSLATE_IRON_ORE);
            if (stack.is(Items.DIAMOND)) return state.is(Blocks.DIAMOND_ORE) || state.is(Blocks.DEEPSLATE_DIAMOND_ORE);
            if (stack.is(Items.RAW_GOLD) || stack.is(Items.GOLD_INGOT)) return state.is(Blocks.GOLD_ORE) || state.is(Blocks.DEEPSLATE_GOLD_ORE);
            if (stack.is(Items.RAW_COPPER) || stack.is(Items.COPPER_INGOT)) return state.is(Blocks.COPPER_ORE) || state.is(Blocks.DEEPSLATE_COPPER_ORE);
            if (stack.is(Items.EMERALD)) return state.is(Blocks.EMERALD_ORE) || state.is(Blocks.DEEPSLATE_EMERALD_ORE);
            if (stack.is(Items.REDSTONE)) return state.is(Blocks.REDSTONE_ORE) || state.is(Blocks.DEEPSLATE_REDSTONE_ORE);
            if (stack.is(Items.LAPIS_LAZULI)) return state.is(Blocks.LAPIS_ORE) || state.is(Blocks.DEEPSLATE_LAPIS_ORE);
            if (stack.is(Items.COAL)) return state.is(Blocks.COAL_ORE) || state.is(Blocks.DEEPSLATE_COAL_ORE);
            return false;
        }
    }

    // EL PARKOUR DEL ZORRO
    public static class FoxTerrainParkourGoal extends Goal {
        private final Fox fox;
        private int cooldown = 0;
        private int prepareTimer = 0;
        private boolean isPreparing = false;

        public FoxTerrainParkourGoal(Fox fox) {
            this.fox = fox;
            this.setFlags(EnumSet.of(Goal.Flag.JUMP, Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (this.cooldown > 0) {
                this.cooldown--;
                return false;
            }
            if (this.fox.isBaby() || !this.fox.onGround()) return false;
            if (!this.fox.horizontalCollision) return false;

            BlockPos foxPos = this.fox.blockPosition();
            BlockPos jumpSpace = foxPos.above(2);
            if (!this.fox.level().getBlockState(jumpSpace).getCollisionShape(this.fox.level(), jumpSpace).isEmpty()) {
                return false; // Hay un techo, no puede saltar
            }

            // NUEVO: SENSOR DE ATERRIZAJE seguro
            // Calculamos exactamente dónde caerá el zorro multiplicando su vector de mirada
            Vec3 look = this.fox.getLookAngle();
            BlockPos landingSpot = BlockPos.containing(
                    this.fox.getX() + (look.x * 2.0),
                    this.fox.getY() - 1.0,
                    this.fox.getZ() + (look.z * 2.0)
            );

            // Si hay lava donde va a caer (o en el bloque de sus pies al caer), aborta
            return !this.fox.level().getBlockState(landingSpot).is(Blocks.LAVA) &&
                    !this.fox.level().getBlockState(landingSpot.above()).is(Blocks.LAVA);// <- El "return true" siempre debe ser lo ÚLTIMO del método
        }

        @Override
        public void start() {
            this.isPreparing = true;
            this.prepareTimer = 0;
            this.fox.getNavigation().stop();
            this.fox.setIsCrouching(true); // Se prepara visualmente
        }

        @Override
        public boolean canContinueToUse() {
            // Mantiene el estado activo mientras se prepara o mientras está volando
            return this.isPreparing || !this.fox.onGround();
        }

        @Override
        public void tick() {
            if (this.isPreparing) {
                this.prepareTimer++;

                // En el primer instante, retrocede un poco de la pared
                if (this.prepareTimer == 1) {
                    Vec3 look = this.fox.getLookAngle();
                    this.fox.setDeltaMovement(look.x * -0.2, 0, look.z * -0.2);
                }

                // A los 8 ticks (menos de medio segundo), da el salto
                if (this.prepareTimer >= 8) {
                    this.isPreparing = false;
                    this.fox.setIsCrouching(false);

                    Vec3 look = this.fox.getLookAngle();
                    // Fuerte impulso en Y (0.75) y avance frontal mediano (0.4) para esquivar la arista
                    this.fox.setDeltaMovement(look.x * 0.4, 0.75, look.z * 0.4);
                }
            }
        }

        @Override
        public void stop() {
            this.isPreparing = false;
            this.fox.setIsCrouching(false);
            this.prepareTimer = 0;
            this.cooldown = 20;
        }
    }

    // LA MADRIGUERA DINÁMICA
    public static class FoxSeekShelterSleepGoal extends Goal {
        private final Fox fox;
        private BlockPos shelterPos;
        private final double speed;
        private boolean isSleeping = false;

        public FoxSeekShelterSleepGoal(Fox fox, double speed) {
            this.fox = fox;
            this.speed = speed;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        // Método universal para calcular si es de día (0 a 13000 ticks)
        private boolean isDaytime() {
            return this.fox.level().getSkyDarken() < 4;
        }

        @Override
        public boolean canUse() {
            // Verificamos matemáticamente si es de día
            if (!isDaytime() || this.fox.isBaby() || this.fox.getTarget() != null) return false;

            if (!this.fox.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()) return false;

            int tiempoContrato = this.fox.getPersistentData().getInt("ContratoMercenario").orElse(0);
            if (this.fox.tickCount < tiempoContrato) return false;

            if (this.fox.isSleeping()) return false;

            this.shelterPos = findTreeShelter();
            return this.shelterPos != null;
        }

        @Override
        public boolean canContinueToUse() {
            if (this.fox.getLastHurtByMob() != null || this.fox.getTarget() != null) {
                return false;
            }
            return isDaytime() && this.shelterPos != null;
        }

        @Override
        public void start(){
            this.isSleeping = false;
        }

        @Override
        public void tick() {
            if (this.shelterPos != null) {
                double dist = this.fox.distanceToSqr(Vec3.atCenterOf(this.shelterPos));

                if (this.isSleeping) {
                    if (dist > 16.0) {
                        this.isSleeping = false;
                        PetAiModifier.setFoxSleeping(this.fox, false);
                    } else {
                        this.fox.getNavigation().stop();
                        PetAiModifier.setFoxSleeping(this.fox, true);
                        return;
                    }
                }

                if (dist > 2.5) {
                    this.fox.getNavigation().moveTo(this.shelterPos.getX(), this.shelterPos.getY(), this.shelterPos.getZ(), this.speed);
                    PetAiModifier.setFoxSleeping(this.fox, false);
                } else {
                    this.fox.getNavigation().stop();
                    PetAiModifier.setFoxSleeping(this.fox, true);
                    this.isSleeping = true;
                }
            }
        }

        @Override
        public void stop() {
            this.shelterPos = null;
            this.isSleeping = false;
            PetAiModifier.setFoxSleeping(this.fox, false);
        }

        private BlockPos findTreeShelter() {
            BlockPos foxPos = this.fox.blockPosition();
            for (int i = 0; i < 20; i++) {
                BlockPos randomPos = foxPos.offset(this.fox.getRandom().nextInt(16) - 8, this.fox.getRandom().nextInt(4) - 2, this.fox.getRandom().nextInt(16) - 8);

                if (this.fox.level().getBlockState(randomPos).isAir()) {
                    BlockPos below = randomPos.below();
                    BlockState floorState = this.fox.level().getBlockState(below);

                    if (!floorState.getCollisionShape(this.fox.level(), below).isEmpty()) {
                        if (!this.fox.level().canSeeSky(randomPos)) {
                            return randomPos;
                        }
                    }
                }
            }
            return null;
        }
    }

    // Rastreador DE Escuadrones
    public static class FoxSquadTargetGoal<T extends LivingEntity> extends NearestAttackableTargetGoal<T> {
        private final Fox fox;
        private final int maxAttackers;

        public FoxSquadTargetGoal(Fox fox, Class<T> targetType, int maxAttackers, boolean mustSee) {
            super(fox, targetType, mustSee);
            this.fox = fox;
            this.maxAttackers = maxAttackers;
        }

        @Override
        public boolean canUse() {
            if (this.fox.isBaby()) return false;

            // Primero, dejamos que Minecraft busque el objetivo más cercano
            if (super.canUse() && this.target != null) {

                // Escaneamos el área (32 bloques alrededor del objetivo) buscando otros zorros
                java.util.List<Fox> alliedFoxes = this.fox.level().getEntitiesOfClass(
                        Fox.class,
                        this.target.getBoundingBox().inflate(32.0D)
                );

                int currentAttackers = 0;

                // Contamos cuántos de esos zorros ya tienen este objetivo en la mira
                for (Fox ally : alliedFoxes) {
                    if (ally != this.fox && ally.getTarget() == this.target) {
                        currentAttackers++;
                    }
                }

                // Si ya hay demasiados atacando a este objetivo, cancelamos
                if (currentAttackers >= this.maxAttackers) {
                    this.target = null; // Borramos el objetivo
                    return false;       // Ignoramos a esta presa
                }

                // Si hay espacio en el escuadrón, ¡vamos por él!
                return true;
            }
            return false;
        }
    }

    // EVASIÓN Pasiva DE LAVA
    public static class FoxPassiveLavaEvasionGoal extends Goal {
        private final Fox fox;
        private Vec3 escapeRoute = null;
        private int cooldown = 0; // Temporizador para que no entre en pánico cada milisegundo

        public FoxPassiveLavaEvasionGoal(Fox fox) {
            this.fox = fox;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        // Método auxiliar: ¿Hay lava a menos de 2 bloques?
        private boolean isInDangerZone() {
            net.minecraft.core.BlockPos pos = this.fox.blockPosition();
            net.minecraft.world.level.Level level = this.fox.level();
            for(int x = -2; x <= 2; x++) {
                for(int z = -2; z <= 2; z++) {
                    net.minecraft.core.BlockPos checkPos = pos.offset(x, 0, z);
                    if (level.getBlockState(checkPos).is(net.minecraft.world.level.block.Blocks.LAVA) ||
                            level.getBlockState(checkPos.below()).is(net.minecraft.world.level.block.Blocks.LAVA)) {
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public boolean canUse() {
            // Si está descansando del susto o atacando, no se activa
            if (this.cooldown > 0) {
                this.cooldown--;
                return false;
            }
            if (this.fox.getTarget() != null) return false;

            if (isInDangerZone()) {
                net.minecraft.core.BlockPos pos = this.fox.blockPosition();
                net.minecraft.world.level.Level level = this.fox.level();
                net.minecraft.core.BlockPos nearestLava = null;
                double closestDist = 999.0;

                for(int x = -2; x <= 2; x++) {
                    for(int z = -2; z <= 2; z++) {
                        net.minecraft.core.BlockPos checkPos = pos.offset(x, 0, z);
                        if (level.getBlockState(checkPos).is(net.minecraft.world.level.block.Blocks.LAVA) ||
                                level.getBlockState(checkPos.below()).is(net.minecraft.world.level.block.Blocks.LAVA)) {
                            double dist = pos.distSqr(checkPos);
                            if (dist < closestDist) {
                                closestDist = dist;
                                nearestLava = checkPos;
                            }
                        }
                    }
                }

                if (nearestLava != null) {
                    Vec3 foxVec = this.fox.position();
                    Vec3 lavaVec = new Vec3(nearestLava.getX(), nearestLava.getY(), nearestLava.getZ());
                    Vec3 awayDir = foxVec.subtract(lavaVec).normalize();

                    // Calculamos una huida corta de 4 bloques
                    this.escapeRoute = foxVec.add(awayDir.x * 4.0, 0, awayDir.z * 4.0);
                    return true;
                }
            }
            return false;
        }

        @Override
        public void start() {
            if (this.escapeRoute != null) {
                this.fox.getNavigation().moveTo(this.escapeRoute.x, this.fox.getY(), this.escapeRoute.z, 1.4D);
            }
        }

        @Override
        public boolean canContinueToUse() {
            // El secreto ESTÁ AQUÍ: Si ya salió de la zona de peligro, detiene la huida de inmediato.
            if (!isInDangerZone()) {
                return false;
            }
            return !this.fox.getNavigation().isDone();
        }

        @Override
        public void stop() {
            this.fox.getNavigation().stop(); // Se clava en el piso
            this.cooldown = 30;
        }
    }
}