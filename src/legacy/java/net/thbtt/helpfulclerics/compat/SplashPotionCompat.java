package net.thbtt.helpfulclerics.compat;

import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potions;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

public final class SplashPotionCompat {
    private SplashPotionCompat() {
    }

    public static boolean throwHealingSplash(ServerWorld world, VillagerEntity cleric, VillagerEntity target) {
        PotionEntity potion = createSplashPotion(world, cleric);
        if (potion == null) {
            return false;
        }

        double deltaX = target.getX() - cleric.getX();
        double deltaY = target.getBodyY(0.5D) - potion.getY();
        double deltaZ = target.getZ() - cleric.getZ();
        double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

        potion.setVelocity(deltaX, deltaY + horizontalDistance * 0.2D, deltaZ, 0.75F, 8.0F);
        world.spawnEntity(potion);
        return true;
    }

    private static PotionEntity createSplashPotion(ServerWorld world, VillagerEntity cleric) {
        ItemStack stack = PotionContentsComponent.createStack(Items.SPLASH_POTION, Potions.HEALING);

        try {
            return PotionEntity.class
                    .getConstructor(World.class, LivingEntity.class, ItemStack.class)
                    .newInstance(world, cleric, stack);
        } catch (ReflectiveOperationException ignored) {
            // Minecraft 1.21.1 has no ItemStack constructor.
        }

        try {
            PotionEntity potion = PotionEntity.class
                    .getConstructor(World.class, LivingEntity.class)
                    .newInstance(world, cleric);
            potion.setItem(stack);
            return potion;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
