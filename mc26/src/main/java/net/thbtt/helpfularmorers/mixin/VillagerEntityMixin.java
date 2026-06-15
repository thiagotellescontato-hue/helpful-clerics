package net.thbtt.helpfularmorers.mixin;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

@Mixin(Villager.class)
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

    @Inject(method = "customServerAiStep", at = @At("TAIL"))
    private void helpfulArmorers$tickGolemRepair(CallbackInfo ci) {
        Villager armorer = (Villager) (Object) this;

        ServerLevel world = WorldCompat.getServerWorld(armorer);
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

        IronGolem golem = ha$getCurrentTarget(world);

        if (golem == null || !ha$isValidGolemTarget(golem)) {
            golem = ha$findNearestDamagedGolem(world, armorer);

            if (golem == null) {
                ha$clearOnlyHelpfulArmorerState(armorer);
                return;
            }

            ha$targetGolemUuid = golem.getUUID();
        }

        if (golem.getTarget() != null) {
            ha$cancelRepair(armorer);
            return;
        }

        double distanceSq = armorer.distanceToSqr(golem);
        double repairDistanceSq = HA_REPAIR_DISTANCE * HA_REPAIR_DISTANCE;

        ha$holdIronIngot(armorer);

        if (distanceSq > repairDistanceSq) {
            armorer.getNavigation().moveTo(golem, 0.65D);
            armorer.getLookControl().setLookAt(golem, 30.0F, 30.0F);
            return;
        }

        armorer.getNavigation().stop();
        armorer.getLookControl().setLookAt(golem, 30.0F, 30.0F);

        if (ha$repairCooldown <= 0) {
            golem.heal(HA_REPAIR_AMOUNT);
            armorer.swing(InteractionHand.MAIN_HAND);

            world.playSound(
                    null,
                    golem.blockPosition(),
                    SoundEvents.IRON_GOLEM_REPAIR,
                    SoundSource.NEUTRAL,
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
    private boolean ha$isArmorer(Villager villager) {
        return ProfessionCompat.isArmorer(villager);
    }

    @Unique
    private boolean ha$isRestTime(ServerLevel world) {
        return world.isDarkOutside();
    }

    @Unique
    private void ha$updateDangerState(ServerLevel world, Villager armorer) {
        if (ha$dangerCooldown > 0) {
            ha$dangerCooldown--;
            return;
        }

        ha$dangerCooldown = 10;

        List<Monster> hostiles = world.getEntitiesOfClass(
                Monster.class,
                armorer.getBoundingBox().inflate(HA_DANGER_RADIUS),
                hostile -> hostile.isAlive() && !hostile.isRemoved()
        );

        ha$dangerNearby = !hostiles.isEmpty();
    }

    @Unique
    private void ha$fleeFromDanger(ServerLevel world, Villager armorer) {
        LivingEntity threat = ha$findNearestThreat(world, armorer);
        Vec3 away;

        if (threat != null) {
            away = new Vec3(
                    armorer.getX() - threat.getX(),
                    0.0D,
                    armorer.getZ() - threat.getZ()
            );
        } else {
            double angle = armorer.getRandom().nextDouble() * Math.PI * 2.0D;
            away = new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
        }

        if (away.lengthSqr() < 0.0001D) {
            double angle = armorer.getRandom().nextDouble() * Math.PI * 2.0D;
            away = new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
        }

        away = away.normalize();

        double targetX = armorer.getX() + away.x * HA_FLEE_DISTANCE;
        double targetY = armorer.getY();
        double targetZ = armorer.getZ() + away.z * HA_FLEE_DISTANCE;

        armorer.getNavigation().moveTo(targetX, targetY, targetZ, 0.8D);
    }

    @Unique
    @Nullable
    private LivingEntity ha$findNearestThreat(ServerLevel world, Villager armorer) {
        LivingEntity attacker = armorer.getLastHurtByMob();

        if (attacker != null && attacker.isAlive() && !attacker.isRemoved()) {
            return attacker;
        }

        List<Monster> hostiles = world.getEntitiesOfClass(
                Monster.class,
                armorer.getBoundingBox().inflate(HA_DANGER_RADIUS),
                hostile -> hostile.isAlive() && !hostile.isRemoved()
        );

        return hostiles.stream()
                .min(Comparator.comparingDouble(armorer::distanceToSqr))
                .orElse(null);
    }

    @Unique
    @Nullable
    private IronGolem ha$getCurrentTarget(ServerLevel world) {
        if (ha$targetGolemUuid == null) {
            return null;
        }

        if (world.getEntity(ha$targetGolemUuid) instanceof IronGolem golem) {
            return golem;
        }

        return null;
    }

    @Unique
    @Nullable
    private IronGolem ha$findNearestDamagedGolem(ServerLevel world, Villager armorer) {
        if (ha$scanCooldown > 0) {
            ha$scanCooldown--;
            return null;
        }

        ha$scanCooldown = 20 + armorer.getRandom().nextInt(20);

        List<IronGolem> golems = world.getEntitiesOfClass(
                IronGolem.class,
                armorer.getBoundingBox().inflate(HA_SEARCH_RADIUS),
                this::ha$isValidGolemTarget
        );

        return golems.stream()
                .min(Comparator.comparingDouble(armorer::distanceToSqr))
                .orElse(null);
    }

    @Unique
    private boolean ha$isValidGolemTarget(IronGolem golem) {
        return golem.isAlive()
                && !golem.isRemoved()
                && golem.getHealth() < golem.getMaxHealth()
                && golem.getTarget() == null;
    }

    @Unique
    private void ha$holdIronIngot(Villager armorer) {
        ItemStack mainHand = armorer.getItemBySlot(EquipmentSlot.MAINHAND);

        if (!mainHand.is(Items.IRON_INGOT)) {
            armorer.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_INGOT));
            armorer.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        }

        ha$holdingIronIngot = true;
    }

    @Unique
    private void ha$cancelRepair(Villager armorer) {
        boolean wasRepairing = ha$targetGolemUuid != null || ha$holdingIronIngot;

        ha$targetGolemUuid = null;

        if (wasRepairing) {
            armorer.getNavigation().stop();
        }

        if (ha$holdingIronIngot || armorer.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.IRON_INGOT)) {
            armorer.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            ha$holdingIronIngot = false;
        }
    }

    @Unique
    private void ha$clearOnlyHelpfulArmorerState(Villager armorer) {
        ha$targetGolemUuid = null;

        if (ha$holdingIronIngot) {
            armorer.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            ha$holdingIronIngot = false;
        }
    }
}
