package net.thbtt.helpfularmorers.mixin;

import net.thbtt.helpfularmorers.compat.ProfessionCompat;
import net.thbtt.helpfularmorers.compat.WorldCompat;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Mixin(VillagerEntity.class)
public abstract class VillagerEntityMixin {
    @Unique private static final double HA_SEARCH_RADIUS = 16.0D;
    @Unique private static final double HA_DANGER_RADIUS = 10.0D;
    @Unique private static final double HA_REPAIR_DISTANCE = 2.4D;
    @Unique private static final double HA_FLEE_DISTANCE = 10.0D;
    @Unique private static final float HA_REPAIR_AMOUNT = 25.0F;

    @Unique private int ha$scanCooldown = 0;
    @Unique private int ha$repairCooldown = 0;
    @Unique private int ha$dangerCooldown = 0;
    @Unique private int ha$fleeTicks = 0;

    @Unique private float ha$lastHealth = -1.0F;

    @Unique private boolean ha$dangerNearby = false;
    @Unique private boolean ha$holdingIronIngot = false;
    @Unique private UUID ha$targetGolemUuid = null;

    @Inject(method = "mobTick", at = @At("TAIL"))
    private void helpfulArmorers$tickGolemRepair(CallbackInfo ci) {
        VillagerEntity armorer = (VillagerEntity) (Object) this;

        ServerWorld world = WorldCompat.getServerWorld(armorer);
        if (world == null) {
            return;
        }

        if (ha$lastHealth < 0.0F) {
            ha$lastHealth = armorer.getHealth();
        }

        boolean tookDamage = armorer.getHealth() < ha$lastHealth;
        ha$lastHealth = armorer.getHealth();

        if (armorer.isBaby() || !ha$isArmorer(armorer)) {
            ha$clearOnlyHelpfulArmorerState(armorer);
            return;
        }

        // If the Armorer takes damage, cancel repair and flee.
        if (tookDamage) {
            ha$fleeTicks = 160;
            ha$cancelRepair(armorer);
            ha$fleeFromDanger(world, armorer);
            return;
        }

        // While fleeing, do not try to repair golems.
        if (ha$fleeTicks > 0) {
            ha$fleeTicks--;
            ha$cancelRepair(armorer);

            if (ha$fleeTicks % 10 == 0) {
                ha$fleeFromDanger(world, armorer);
            }

            return;
        }

        // At night, leave vanilla behavior to handle beds, shelter, and sleep.
        if (ha$isRestTime(world)) {
            ha$cancelRepair(armorer);
            return;
        }

        if (ha$repairCooldown > 0) {
            ha$repairCooldown--;
        }

        ha$updateDangerState(world, armorer);

        if (ha$dangerNearby) {
            ha$cancelRepair(armorer);
            return;
        }

        IronGolemEntity golem = ha$getCurrentTarget(world);

        if (golem == null || !ha$isValidGolemTarget(golem)) {
            golem = ha$findNearestDamagedGolem(world, armorer);

            if (golem == null) {
                ha$clearOnlyHelpfulArmorerState(armorer);
                return;
            }

            ha$targetGolemUuid = golem.getUuid();
        }

        if (golem.getTarget() != null) {
            ha$cancelRepair(armorer);
            return;
        }

        double distanceSq = armorer.squaredDistanceTo(golem);
        double repairDistanceSq = HA_REPAIR_DISTANCE * HA_REPAIR_DISTANCE;

        ha$holdIronIngot(armorer);

        if (distanceSq > repairDistanceSq) {
            armorer.getNavigation().startMovingTo(golem, 0.65D);
            armorer.getLookControl().lookAt(golem, 30.0F, 30.0F);
            return;
        }

        armorer.getNavigation().stop();
        armorer.getLookControl().lookAt(golem, 30.0F, 30.0F);

        if (ha$repairCooldown <= 0) {
            golem.heal(HA_REPAIR_AMOUNT);
            armorer.swingHand(Hand.MAIN_HAND);

            world.playSound(
                    null,
                    golem.getBlockPos(),
                    SoundEvents.ENTITY_IRON_GOLEM_REPAIR,
                    SoundCategory.NEUTRAL,
                    1.0F,
                    1.0F
            );

            ha$repairCooldown = 60;

            if (golem.getHealth() >= golem.getMaxHealth()) {
                ha$cancelRepair(armorer);
            }
        }
    }

    @Unique
    private boolean ha$isArmorer(VillagerEntity villager) {
        return ProfessionCompat.isArmorer(villager);
    }

    @Unique
    private boolean ha$isRestTime(ServerWorld world) {
        return world.isNight();
    }

    @Unique
    private void ha$updateDangerState(ServerWorld world, VillagerEntity armorer) {
        if (ha$dangerCooldown > 0) {
            ha$dangerCooldown--;
            return;
        }

        ha$dangerCooldown = 10;

        List<HostileEntity> hostiles = world.getEntitiesByClass(
                HostileEntity.class,
                armorer.getBoundingBox().expand(HA_DANGER_RADIUS),
                hostile -> hostile.isAlive() && !hostile.isRemoved()
        );

        ha$dangerNearby = !hostiles.isEmpty();
    }

    @Unique
    private void ha$fleeFromDanger(ServerWorld world, VillagerEntity armorer) {
        LivingEntity threat = ha$findNearestThreat(world, armorer);
        Vec3d away;

        if (threat != null) {
            away = new Vec3d(
                    armorer.getX() - threat.getX(),
                    0.0D,
                    armorer.getZ() - threat.getZ()
            );
        } else {
            double angle = armorer.getRandom().nextDouble() * Math.PI * 2.0D;
            away = new Vec3d(Math.cos(angle), 0.0D, Math.sin(angle));
        }

        if (away.lengthSquared() < 0.0001D) {
            double angle = armorer.getRandom().nextDouble() * Math.PI * 2.0D;
            away = new Vec3d(Math.cos(angle), 0.0D, Math.sin(angle));
        }

        away = away.normalize();

        double targetX = armorer.getX() + away.x * HA_FLEE_DISTANCE;
        double targetY = armorer.getY();
        double targetZ = armorer.getZ() + away.z * HA_FLEE_DISTANCE;

        armorer.getNavigation().startMovingTo(targetX, targetY, targetZ, 0.8D);
    }

    @Unique
    @Nullable
    private LivingEntity ha$findNearestThreat(ServerWorld world, VillagerEntity armorer) {
        LivingEntity attacker = armorer.getAttacker();

        if (attacker != null && attacker.isAlive() && !attacker.isRemoved()) {
            return attacker;
        }

        List<HostileEntity> hostiles = world.getEntitiesByClass(
                HostileEntity.class,
                armorer.getBoundingBox().expand(HA_DANGER_RADIUS),
                hostile -> hostile.isAlive() && !hostile.isRemoved()
        );

        return hostiles.stream()
                .min(Comparator.comparingDouble(armorer::squaredDistanceTo))
                .orElse(null);
    }

    @Unique
    @Nullable
    private IronGolemEntity ha$getCurrentTarget(ServerWorld world) {
        if (ha$targetGolemUuid == null) {
            return null;
        }

        if (world.getEntity(ha$targetGolemUuid) instanceof IronGolemEntity golem) {
            return golem;
        }

        return null;
    }

    @Unique
    @Nullable
    private IronGolemEntity ha$findNearestDamagedGolem(ServerWorld world, VillagerEntity armorer) {
        if (ha$scanCooldown > 0) {
            ha$scanCooldown--;
            return null;
        }

        ha$scanCooldown = 20 + armorer.getRandom().nextInt(20);

        List<IronGolemEntity> golems = world.getEntitiesByClass(
                IronGolemEntity.class,
                armorer.getBoundingBox().expand(HA_SEARCH_RADIUS),
                this::ha$isValidGolemTarget
        );

        return golems.stream()
                .min(Comparator.comparingDouble(armorer::squaredDistanceTo))
                .orElse(null);
    }

    @Unique
    private boolean ha$isValidGolemTarget(IronGolemEntity golem) {
        return golem.isAlive()
                && !golem.isRemoved()
                && golem.getHealth() < golem.getMaxHealth()
                && golem.getTarget() == null;
    }

    @Unique
    private void ha$holdIronIngot(VillagerEntity armorer) {
        ItemStack mainHand = armorer.getEquippedStack(EquipmentSlot.MAINHAND);

        if (!mainHand.isOf(Items.IRON_INGOT)) {
            armorer.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_INGOT));
            armorer.setEquipmentDropChance(EquipmentSlot.MAINHAND, 0.0F);
        }

        ha$holdingIronIngot = true;
    }

    @Unique
    private void ha$cancelRepair(VillagerEntity armorer) {
        boolean wasRepairing = ha$targetGolemUuid != null || ha$holdingIronIngot;

        ha$targetGolemUuid = null;

        if (wasRepairing) {
            armorer.getNavigation().stop();
        }

        if (ha$holdingIronIngot || armorer.getEquippedStack(EquipmentSlot.MAINHAND).isOf(Items.IRON_INGOT)) {
            armorer.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            ha$holdingIronIngot = false;
        }
    }

    @Unique
    private void ha$clearOnlyHelpfulArmorerState(VillagerEntity armorer) {
        ha$targetGolemUuid = null;

        if (ha$holdingIronIngot) {
            armorer.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            ha$holdingIronIngot = false;
        }
    }
}
