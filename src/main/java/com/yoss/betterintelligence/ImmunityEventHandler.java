package com.yoss.betterintelligence;

import com.yoss.betterintelligence.registry.ModPotions;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownLingeringPotion;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

@EventBusSubscriber(modid = "betterintelligence")
public class ImmunityEventHandler {

    @SubscribeEvent
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        if (event.getEntity().hasEffect(ModPotions.TACTICAL_IMMUNITY)) {
            var immunityInstance = event.getEntity().getEffect(ModPotions.TACTICAL_IMMUNITY);
            var incomingEffect = event.getEffectInstance().getEffect();

            // Evitamos que la inmunidad se anule a sí misma al intentar aplicarse
            if (incomingEffect == ModPotions.TACTICAL_IMMUNITY) return;

            if (immunityInstance != null) {
                // Si es INMUNIDAD II (Amplifier 1 o mayor): Bloquea LITERALMENTE TODO (curación, fuerza, veneno, etc.)
                if (immunityInstance.getAmplifier() >= 1) {
                    event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
                    return;
                }

                // Si es INMUNIDAD I (Amplifier 0): Solo bloquea efectos negativos específicos
                if (incomingEffect == MobEffects.POISON ||
                        incomingEffect == MobEffects.SLOWNESS ||
                        incomingEffect == MobEffects.INSTANT_DAMAGE ||
                        incomingEffect == MobEffects.WEAKNESS) {
                    event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Pre event) {
        if (event.getEntity().hasEffect(ModPotions.TACTICAL_IMMUNITY)) {
            var source = event.getSource();
            var directEntity = source.getDirectEntity();

            // Evaluamos daño mágico puro y usamos las clases de proyectiles exactas de la 26.3
            if (source.is(DamageTypes.MAGIC) ||
                    source.is(DamageTypes.INDIRECT_MAGIC) ||
                    source.is(DamageTypes.GENERIC_KILL) ||
                    directEntity instanceof ThrownSplashPotion ||
                    directEntity instanceof ThrownLingeringPotion ||
                    directEntity instanceof AreaEffectCloud) {

                event.setNewDamage(0.0F);
                event.getEntity().hurtTime = 0;
                event.getEntity().hurtDuration = 0;
            }
        }
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof Witch witch) {
            witch.goalSelector.addGoal(1, new DrinkImmunityPotionGoal(witch));
            witch.goalSelector.addGoal(2, new SmartWitchAttackGoal(witch, 60));
        }

        else if (event.getEntity() instanceof Raider raider) {
            raider.goalSelector.addGoal(1, new DrinkImmunityPotionGoal(raider));
        }
    }

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        var entity = event.getEntity();

        // Verificamos si el mob que acaba de morir es una Bruja o un miembro de Incursión (saqueadores, Vindicadores, etc.)
        if (entity instanceof Raider || entity instanceof Witch) {

            // Configuración de rareza: 0.05F equivale a un 5% de probabilidad de drop (1 de cada 20 mobs)
            if (entity.getRandom().nextFloat() < 0.05F) {

                // 1. Fabricamos el ítem de la poción con tu efecto
                ItemStack potionStack = new ItemStack(Items.POTION);
                potionStack.set(DataComponents.POTION_CONTENTS,
                        new PotionContents(ModPotions.IMMUNITY_POTION));

                // 2. Creamos el objeto físico para que caiga en las coordenadas del cadáver
                ItemEntity dropItem = new ItemEntity(
                        entity.level(),
                        entity.getX(),
                        entity.getY(),
                        entity.getZ(),
                        potionStack
                );

                // 3. Añadimos la poción a la lista de botín (loot) de la entidad
                event.getDrops().add(dropItem);
            }
        }
    }

    @SubscribeEvent
    public static void onEffectRemove(MobEffectEvent.Remove event) {
        if (event.getEffect() == ModPotions.TACTICAL_IMMUNITY) {

            var instance = event.getEntity().getEffect(ModPotions.TACTICAL_IMMUNITY);

            if (instance != null && instance.getDuration() > 5) {
                event.setCanceled(true);
            }
        }
    }
}