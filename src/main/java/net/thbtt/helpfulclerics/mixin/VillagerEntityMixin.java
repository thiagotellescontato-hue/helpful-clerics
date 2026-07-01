package net.thbtt.helpfulclerics.mixin;

import net.thbtt.helpfulclerics.compat.ProfessionCompat;
import net.thbtt.helpfulclerics.compat.SoundCompat;
import net.thbtt.helpfulclerics.compat.WorldCompat;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.potion.Potions;
import net.minecraft.server.world.ServerWorld;
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
    @Unique private static final double HC_SEARCH_RADIUS = 16.0D;
    @Unique private static final double HC_DANGER_RADIUS = 10.0D;
    @Unique private static final double HC_HEAL_DISTANCE = 2.4D;
    @Unique private static final double HC_FLEE_DISTANCE = 10.0D;

    @Unique private int hc$scanCooldown = 0;
    @Unique private int hc$healCooldown = 0;
    @Unique private int hc$dangerCooldown = 0;
    @Unique private int hc$fleeTicks = 0;

    @Unique private float hc$lastHealth = -1.0F;

    @Unique private boolean hc$dangerNearby = false;
    @Unique private boolean hc$holdingHealingPotion = false;
    @Unique private UUID hc$targetVillagerUuid = null;

    @Inject(method = "mobTick", at = @At("TAIL"))
    private void helpfulClerics$tickVillagerHealing(CallbackInfo ci) {
        VillagerEntity cleric = (VillagerEntity) (Object) this;

        ServerWorld world = WorldCompat.getServerWorld(cleric);
        if (world == null) {
            return;
        }

        if (hc$lastHealth < 0.0F) {
            hc$lastHealth = cleric.getHealth();
        }

        boolean tookDamage = cleric.getHealth() < hc$lastHealth;
        hc$lastHealth = cleric.getHealth();

        if (cleric.isBaby() || !hc$isCleric(cleric)) {
            hc$clearOnlyHelpfulClericState(cleric);
            return;
        }

        if (tookDamage) {
            hc$fleeTicks = 160;
            hc$cancelHealing(cleric);
            hc$fleeFromDanger(world, cleric);
            return;
        }

        if (hc$fleeTicks > 0) {
            hc$fleeTicks--;
            hc$cancelHealing(cleric);

            if (hc$fleeTicks % 10 == 0) {
                hc$fleeFromDanger(world, cleric);
            }

            return;
        }

        if (hc$isRestTime(world)) {
            hc$cancelHealing(cleric);
            return;
        }

        if (hc$healCooldown > 0) {
            hc$healCooldown--;
        }

        hc$updateDangerState(world, cleric);

        if (hc$dangerNearby) {
            hc$cancelHealing(cleric);
            return;
        }

        VillagerEntity patient = hc$getCurrentTarget(world);

        if (patient == null || !hc$isValidPatient(patient, cleric)) {
            patient = hc$findNearestDamagedVillager(world, cleric);

            if (patient == null) {
                hc$clearOnlyHelpfulClericState(cleric);
                return;
            }

            hc$targetVillagerUuid = patient.getUuid();
        }

        double distanceSq = cleric.squaredDistanceTo(patient);
        double healDistanceSq = HC_HEAL_DISTANCE * HC_HEAL_DISTANCE;

        hc$holdHealingPotion(cleric);

        if (distanceSq > healDistanceSq) {
            cleric.getNavigation().startMovingTo(patient, 0.65D);
            cleric.getLookControl().lookAt(patient, 30.0F, 30.0F);
            return;
        }

        cleric.getNavigation().stop();
        cleric.getLookControl().lookAt(patient, 30.0F, 30.0F);

        if (hc$healCooldown <= 0) {
            patient.addStatusEffect(new StatusEffectInstance(StatusEffects.INSTANT_HEALTH, 1, 0));
            cleric.swingHand(Hand.MAIN_HAND);
            SoundCompat.playHealingSound(world, patient.getBlockPos());

            hc$healCooldown = 60;

            if (patient.getHealth() >= patient.getMaxHealth()) {
                hc$cancelHealing(cleric);
            }
        }
    }

    @Unique
    private boolean hc$isCleric(VillagerEntity villager) {
        return ProfessionCompat.isCleric(villager);
    }

    @Unique
    private boolean hc$isRestTime(ServerWorld world) {
        return world.isNight();
    }

    @Unique
    private void hc$updateDangerState(ServerWorld world, VillagerEntity cleric) {
        if (hc$dangerCooldown > 0) {
            hc$dangerCooldown--;
            return;
        }

        hc$dangerCooldown = 10;

        List<HostileEntity> hostiles = world.getEntitiesByClass(
                HostileEntity.class,
                cleric.getBoundingBox().expand(HC_DANGER_RADIUS),
                hostile -> hostile.isAlive() && !hostile.isRemoved()
        );

        hc$dangerNearby = !hostiles.isEmpty();
    }

    @Unique
    private void hc$fleeFromDanger(ServerWorld world, VillagerEntity cleric) {
        LivingEntity threat = hc$findNearestThreat(world, cleric);
        Vec3d away;

        if (threat != null) {
            away = new Vec3d(
                    cleric.getX() - threat.getX(),
                    0.0D,
                    cleric.getZ() - threat.getZ()
            );
        } else {
            double angle = cleric.getRandom().nextDouble() * Math.PI * 2.0D;
            away = new Vec3d(Math.cos(angle), 0.0D, Math.sin(angle));
        }

        if (away.lengthSquared() < 0.0001D) {
            double angle = cleric.getRandom().nextDouble() * Math.PI * 2.0D;
            away = new Vec3d(Math.cos(angle), 0.0D, Math.sin(angle));
        }

        away = away.normalize();

        double targetX = cleric.getX() + away.x * HC_FLEE_DISTANCE;
        double targetY = cleric.getY();
        double targetZ = cleric.getZ() + away.z * HC_FLEE_DISTANCE;

        cleric.getNavigation().startMovingTo(targetX, targetY, targetZ, 0.8D);
    }

    @Unique
    @Nullable
    private LivingEntity hc$findNearestThreat(ServerWorld world, VillagerEntity cleric) {
        LivingEntity attacker = cleric.getAttacker();

        if (attacker != null && attacker.isAlive() && !attacker.isRemoved()) {
            return attacker;
        }

        List<HostileEntity> hostiles = world.getEntitiesByClass(
                HostileEntity.class,
                cleric.getBoundingBox().expand(HC_DANGER_RADIUS),
                hostile -> hostile.isAlive() && !hostile.isRemoved()
        );

        return hostiles.stream()
                .min(Comparator.comparingDouble(cleric::squaredDistanceTo))
                .orElse(null);
    }

    @Unique
    @Nullable
    private VillagerEntity hc$getCurrentTarget(ServerWorld world) {
        if (hc$targetVillagerUuid == null) {
            return null;
        }

        if (world.getEntity(hc$targetVillagerUuid) instanceof VillagerEntity villager) {
            return villager;
        }

        return null;
    }

    @Unique
    @Nullable
    private VillagerEntity hc$findNearestDamagedVillager(ServerWorld world, VillagerEntity cleric) {
        if (hc$scanCooldown > 0) {
            hc$scanCooldown--;
            return null;
        }

        hc$scanCooldown = 20 + cleric.getRandom().nextInt(20);

        List<VillagerEntity> villagers = world.getEntitiesByClass(
                VillagerEntity.class,
                cleric.getBoundingBox().expand(HC_SEARCH_RADIUS),
                villager -> hc$isValidPatient(villager, cleric)
        );

        return villagers.stream()
                .min(Comparator.comparingDouble(cleric::squaredDistanceTo))
                .orElse(null);
    }

    @Unique
    private boolean hc$isValidPatient(VillagerEntity villager, VillagerEntity cleric) {
        return villager.isAlive()
                && !villager.isRemoved()
                && !villager.getUuid().equals(cleric.getUuid())
                && villager.getHealth() < villager.getMaxHealth();
    }

    @Unique
    private void hc$holdHealingPotion(VillagerEntity cleric) {
        ItemStack mainHand = cleric.getEquippedStack(EquipmentSlot.MAINHAND);

        if (!mainHand.isOf(Items.POTION)) {
            cleric.equipStack(EquipmentSlot.MAINHAND, PotionContentsComponent.createStack(Items.POTION, Potions.HEALING));
            cleric.setEquipmentDropChance(EquipmentSlot.MAINHAND, 0.0F);
        }

        hc$holdingHealingPotion = true;
    }

    @Unique
    private void hc$cancelHealing(VillagerEntity cleric) {
        boolean wasHealing = hc$targetVillagerUuid != null || hc$holdingHealingPotion;

        hc$targetVillagerUuid = null;

        if (wasHealing) {
            cleric.getNavigation().stop();
        }

        if (hc$holdingHealingPotion || cleric.getEquippedStack(EquipmentSlot.MAINHAND).isOf(Items.POTION)) {
            cleric.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            hc$holdingHealingPotion = false;
        }
    }

    @Unique
    private void hc$clearOnlyHelpfulClericState(VillagerEntity cleric) {
        hc$targetVillagerUuid = null;

        if (hc$holdingHealingPotion) {
            cleric.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            hc$holdingHealingPotion = false;
        }
    }
}
