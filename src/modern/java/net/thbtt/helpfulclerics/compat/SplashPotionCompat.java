package net.thbtt.helpfulclerics.compat;

import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.projectile.thrown.SplashPotionEntity;
import net.minecraft.item.Items;
import net.minecraft.potion.Potions;
import net.minecraft.server.world.ServerWorld;

public final class SplashPotionCompat {
    private SplashPotionCompat() {
    }

    public static boolean throwHealingSplash(ServerWorld world, VillagerEntity cleric, VillagerEntity target) {
        SplashPotionEntity potion = new SplashPotionEntity(
                world,
                cleric,
                PotionContentsComponent.createStack(Items.SPLASH_POTION, Potions.HEALING)
        );

        double deltaX = target.getX() - cleric.getX();
        double deltaY = target.getBodyY(0.5D) - potion.getY();
        double deltaZ = target.getZ() - cleric.getZ();
        double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

        potion.setVelocity(deltaX, deltaY + horizontalDistance * 0.2D, deltaZ, 0.75F, 8.0F);
        world.spawnEntity(potion);
        return true;
    }
}
