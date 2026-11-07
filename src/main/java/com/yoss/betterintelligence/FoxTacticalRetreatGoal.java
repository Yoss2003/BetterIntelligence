package com.yoss.betterintelligence;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.entity.player.Player;
import java.util.EnumSet;

public class FoxTacticalRetreatGoal extends Goal {
    private final Fox fox;
    private final double speedModifier;
    private Player closestTrustedPlayer;

    public FoxTacticalRetreatGoal(Fox fox, double speedModifier) {
        this.fox = fox;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (this.fox.getHealth() > this.fox.getMaxHealth() * 0.30F) {
            return false;
        }

        this.closestTrustedPlayer = null;
        double closestDist = Double.MAX_VALUE;

        java.util.List<Player> players = this.fox.level().getEntitiesOfClass(Player.class, this.fox.getBoundingBox().inflate(30.0D));

        for (Player p : players) {
            // Usamos el Helper para verificar sin errores
            if (FoxTrustHelper.trusts(this.fox, p)) {
                double dist = this.fox.distanceToSqr(p);
                if (dist < closestDist) {
                    closestDist = dist;
                    this.closestTrustedPlayer = p;
                }
            }
        }

        return this.closestTrustedPlayer != null && this.fox.distanceToSqr(this.closestTrustedPlayer) > 4.0D;
    }

    @Override
    public void start() {
        this.fox.setTarget(null);
    }

    @Override
    public void tick() {
        this.fox.setTarget(null);
        if (this.closestTrustedPlayer != null) {
            this.fox.getNavigation().moveTo(this.closestTrustedPlayer, this.speedModifier);
        }
    }
}