package com.yoss.betterintelligence;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.item.ItemStack;

public class FoxWeaponLockGoal extends Goal {
    private final Fox fox;

    public FoxWeaponLockGoal(Fox fox) {
        this.fox = fox;
    }

    @Override
    public boolean canUse() {
        return true;
    }

    @Override
    public void tick() {
        ItemStack itemInMouth = this.fox.getMainHandItem();
        this.fox.setCanPickUpLoot(!itemInMouth.is(net.minecraft.tags.ItemTags.SWORDS) &&
                !itemInMouth.is(net.minecraft.tags.ItemTags.AXES)); // Ponemos el candado mental
    }
}