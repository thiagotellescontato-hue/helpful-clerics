package net.thbtt.helpfulclerics.mixin;

import net.thbtt.helpfulclerics.compat.ProfessionCompat;
import net.thbtt.helpfulclerics.compat.SoundCompat;
import net.thbtt.helpfulclerics.compat.SplashPotionCompat;
import net.thbtt.helpfulclerics.compat.WorldCompat;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.potion.Potions;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.raid.Raid;
import net.minecraft.world.RaycastContext;
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
    @Unique private static final double HC_GROUP_HEAL_RADIUS = 4.0D;
    @Unique private static final double HC_SEEK_CLERIC_DISTANCE = 3.0D;
    @Unique private static final int HC_GROUP_HEAL_MIN_PATIENTS = 2;
    @Unique private static final int HC_SELF_HEAL_DELAY_TICKS = 20;
    @Unique private static final int HC_FORCED_SCAN_INTERVAL_TICKS = 100;

    @Unique private int hc$scanCooldown = 0;
    @Unique private int hc$forcedScanCooldown = 0;
    @Unique private int hc$healCooldown = 0;
    @Unique private int hc$dangerCooldown = 0;
    @Unique private int hc$selfHealDelay = 0;

    @Unique private float hc$lastHealth = -1.0F;

    @Unique private boolean hc$dangerNearby = false;
    @Unique private boolean hc$holdingHealingPotion = false;
    @Unique private boolean hc$seekingCleric = false;
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
        boolean raidActive = hc$isRaidActive(world, cleric);

        if (cleric.isBaby() || !hc$isCleric(cleric)) {
            hc$tickPatientSeeking(world, cleric);
            hc$clearOnlyHelpfulClericState(cleric);
            return;
        }

        if (tookDamage) {
            hc$cancelHealing(cleric);
            hc$selfHealDelay = HC_SELF_HEAL_DELAY_TICKS;
        }

        if (!raidActive && hc$isRestTime(world)) {
            hc$cancelHealing(cleric);
            return;
        }

        if (hc$healCooldown > 0) {
            hc$healCooldown--;
        }

        hc$updateDangerState(world, cleric);

        if (!raidActive && hc$dangerNearby) {
            hc$cancelHealing(cleric);
            return;
        }

        if (cleric.getHealth() < cleric.getMaxHealth()) {
            hc$holdHealingPotion(cleric);
            cleric.getNavigation().stop();

            if (hc$selfHealDelay > 0) {
                hc$selfHealDelay--;
                return;
            }

            hc$healVillager(world, cleric, cleric);
            if (cleric.getHealth() >= cleric.getMaxHealth()) {
                hc$cancelHealing(cleric);
                hc$selfHealDelay = 0;
                hc$finishHealingMovement(world, cleric);
            }
            return;
        }

        hc$selfHealDelay = 0;

        List<VillagerEntity> nearbyPatients = hc$findNearbyDamagedVillagers(world, cleric);
        if (nearbyPatients.size() >= HC_GROUP_HEAL_MIN_PATIENTS && hc$healCooldown <= 0) {
            hc$holdSplashHealingPotion(cleric);
            cleric.getNavigation().stop();
            hc$splashHealVillager(world, cleric, hc$mostInjuredVillager(nearbyPatients, cleric));
            hc$cancelHealing(cleric);
            return;
        }

        VillagerEntity patient = hc$getCurrentTarget(world);
        VillagerEntity priorityPatient = hc$findMostInjuredVillager(world, cleric, raidActive || hc$shouldForceScan());

        if (priorityPatient != null) {
            patient = priorityPatient;
            hc$targetVillagerUuid = patient.getUuid();
        } else if (patient == null || !hc$isValidPatient(patient, cleric)) {
            patient = hc$findMostInjuredVillager(world, cleric, false);

            if (patient == null) {
                hc$clearOnlyHelpfulClericState(cleric);
                return;
            }

            hc$targetVillagerUuid = patient.getUuid();
        }

        double distanceSq = cleric.squaredDistanceTo(patient);
        double healDistanceSq = HC_HEAL_DISTANCE * HC_HEAL_DISTANCE;

        hc$holdHealingPotion(cleric);

        if (distanceSq > healDistanceSq || !hc$canSeePatient(world, cleric, patient)) {
            cleric.getLookControl().lookAt(patient, 30.0F, 30.0F);

            if (raidActive && hc$dangerNearby) {
                cleric.getNavigation().stop();

                if (hc$canSeePatient(world, cleric, patient) && hc$healCooldown <= 0) {
                    hc$holdSplashHealingPotion(cleric);
                    hc$splashHealVillager(world, cleric, patient);
                    hc$cancelHealing(cleric);
                }

                return;
            }

            boolean canReachPatient = cleric.getNavigation().startMovingTo(patient, 0.65D);

            if (!canReachPatient && hc$canSeePatient(world, cleric, patient) && hc$healCooldown <= 0) {
                hc$holdSplashHealingPotion(cleric);
                hc$splashHealVillager(world, cleric, patient);
                hc$cancelHealing(cleric);
            }

            return;
        }

        cleric.getNavigation().stop();
        cleric.getLookControl().lookAt(patient, 30.0F, 30.0F);

        if (hc$healCooldown <= 0) {
            hc$healVillager(world, cleric, patient);

            if (patient.getHealth() >= patient.getMaxHealth()) {
                hc$finishHealingMovement(world, patient);
                hc$cancelHealing(cleric);
            }
        }
    }

    @Unique
    private boolean hc$isCleric(VillagerEntity villager) {
        return ProfessionCompat.isCleric(villager);
    }

    @Unique
    private void hc$tickPatientSeeking(ServerWorld world, VillagerEntity patient) {
        if (patient.isBaby()) {
            hc$seekingCleric = false;
            return;
        }

        if (hc$seekingCleric && patient.getHealth() >= patient.getMaxHealth()) {
            hc$finishHealingMovement(world, patient);
            return;
        }

        VillagerEntity cleric = hc$findNearestCleric(world, patient);
        if (cleric == null) {
            hc$seekingCleric = false;
            return;
        }

        if (patient.getHealth() >= patient.getMaxHealth() || (!hc$isRaidActive(world, patient) && hc$isRestTime(world))) {
            if (hc$seekingCleric) {
                patient.getNavigation().stop();
            }
            hc$seekingCleric = false;
            return;
        }

        hc$seekingCleric = true;

        if (hc$isThreatNearby(world, patient)) {
            patient.getNavigation().startMovingTo(cleric, 0.9D);
            patient.getLookControl().lookAt(cleric, 30.0F, 30.0F);
            return;
        }

        double seekDistanceSq = HC_SEEK_CLERIC_DISTANCE * HC_SEEK_CLERIC_DISTANCE;
        if (patient.squaredDistanceTo(cleric) > seekDistanceSq) {
            patient.getNavigation().startMovingTo(cleric, 0.7D);
        } else {
            patient.getNavigation().stop();
        }

        patient.getLookControl().lookAt(cleric, 30.0F, 30.0F);
    }

    @Unique
    private boolean hc$isRestTime(ServerWorld world) {
        return world.isNight();
    }

    @Unique
    private boolean hc$isRaidActive(ServerWorld world, VillagerEntity villager) {
        Raid raid = world.getRaidAt(villager.getBlockPos());
        return raid != null && raid.isActive();
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
    private boolean hc$isThreatNearby(ServerWorld world, VillagerEntity villager) {
        return !world.getEntitiesByClass(
                HostileEntity.class,
                villager.getBoundingBox().expand(HC_DANGER_RADIUS),
                hostile -> hostile.isAlive() && !hostile.isRemoved()
        ).isEmpty();
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
    private VillagerEntity hc$findMostInjuredVillager(ServerWorld world, VillagerEntity cleric, boolean force) {
        if (!force && hc$scanCooldown > 0) {
            hc$scanCooldown--;
            return null;
        }

        hc$scanCooldown = 20 + cleric.getRandom().nextInt(20);

        List<VillagerEntity> villagers = world.getEntitiesByClass(
                VillagerEntity.class,
                cleric.getBoundingBox().expand(HC_SEARCH_RADIUS),
                villager -> hc$isValidPatient(villager, cleric) && hc$canSeePatient(world, cleric, villager)
        );

        return villagers.stream()
                .min(Comparator
                        .comparingDouble(this::hc$healthPercent)
                        .thenComparingDouble(cleric::squaredDistanceTo))
                .orElse(null);
    }

    @Unique
    private boolean hc$shouldForceScan() {
        if (hc$forcedScanCooldown > 0) {
            hc$forcedScanCooldown--;
            return false;
        }

        hc$forcedScanCooldown = HC_FORCED_SCAN_INTERVAL_TICKS;
        return true;
    }

    @Unique
    private List<VillagerEntity> hc$findNearbyDamagedVillagers(ServerWorld world, VillagerEntity cleric) {
        return world.getEntitiesByClass(
                VillagerEntity.class,
                cleric.getBoundingBox().expand(HC_GROUP_HEAL_RADIUS),
                villager -> hc$isValidPatient(villager, cleric) && hc$canSeePatient(world, cleric, villager)
        );
    }

    @Unique
    private VillagerEntity hc$mostInjuredVillager(List<VillagerEntity> villagers, VillagerEntity cleric) {
        return villagers.stream()
                .min(Comparator
                        .comparingDouble(this::hc$healthPercent)
                        .thenComparingDouble(cleric::squaredDistanceTo))
                .orElse(villagers.get(0));
    }

    @Unique
    private double hc$healthPercent(VillagerEntity villager) {
        return villager.getHealth() / villager.getMaxHealth();
    }

    @Unique
    @Nullable
    private VillagerEntity hc$findNearestCleric(ServerWorld world, VillagerEntity patient) {
        List<VillagerEntity> clerics = world.getEntitiesByClass(
                VillagerEntity.class,
                patient.getBoundingBox().expand(HC_SEARCH_RADIUS),
                villager -> villager.isAlive()
                        && !villager.isRemoved()
                        && !villager.isBaby()
                        && !villager.getUuid().equals(patient.getUuid())
                        && hc$isCleric(villager)
        );

        return clerics.stream()
                .min(Comparator.comparingDouble(patient::squaredDistanceTo))
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
    private boolean hc$canSeePatient(ServerWorld world, VillagerEntity cleric, VillagerEntity patient) {
        Vec3d from = cleric.getEyePos();

        return hc$hasClearRay(world, from, patient.getEyePos(), cleric)
                || hc$hasClearRay(world, from, new Vec3d(patient.getX(), patient.getBodyY(0.5D), patient.getZ()), cleric)
                || hc$hasClearRay(world, from, new Vec3d(patient.getX(), patient.getY() + 0.2D, patient.getZ()), cleric);
    }

    @Unique
    private boolean hc$hasClearRay(ServerWorld world, Vec3d from, Vec3d to, VillagerEntity cleric) {
        HitResult hit = world.raycast(new RaycastContext(
                from,
                to,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                cleric
        ));

        return hit.getType() == HitResult.Type.MISS;
    }

    @Unique
    private void hc$healVillager(ServerWorld world, VillagerEntity cleric, VillagerEntity patient) {
        if (hc$healCooldown > 0) {
            return;
        }

        patient.addStatusEffect(new StatusEffectInstance(StatusEffects.INSTANT_HEALTH, 1, 0));
        hc$spawnHealingParticles(world, patient);
        cleric.swingHand(Hand.MAIN_HAND);
        SoundCompat.playHealingSound(world, patient.getBlockPos());
        hc$healCooldown = 60;
    }

    @Unique
    private void hc$finishHealingMovement(ServerWorld world, VillagerEntity patient) {
        if (!hc$isThreatNearby(world, patient)) {
            hc$clearPanicMemories(patient);
            patient.getNavigation().stop();
        }
        hc$seekingCleric = false;
    }

    @Unique
    private void hc$clearPanicMemories(VillagerEntity patient) {
        patient.getBrain().forget(MemoryModuleType.WALK_TARGET);
        patient.getBrain().forget(MemoryModuleType.LOOK_TARGET);
        patient.getBrain().forget(MemoryModuleType.HURT_BY);
        patient.getBrain().forget(MemoryModuleType.HURT_BY_ENTITY);
        patient.getBrain().forget(MemoryModuleType.AVOID_TARGET);
        patient.getBrain().forget(MemoryModuleType.NEAREST_HOSTILE);
        patient.getBrain().forget(MemoryModuleType.DANGER_DETECTED_RECENTLY);
        patient.getBrain().forget(MemoryModuleType.IS_PANICKING);
    }

    @Unique
    private void hc$splashHealVillager(ServerWorld world, VillagerEntity cleric, VillagerEntity target) {
        if (hc$healCooldown > 0) {
            return;
        }

        cleric.getLookControl().lookAt(target, 30.0F, 30.0F);
        cleric.swingHand(Hand.MAIN_HAND);

        if (SplashPotionCompat.throwHealingSplash(world, cleric, target)) {
            hc$healCooldown = 60;
        }
    }

    @Unique
    private void hc$spawnHealingParticles(ServerWorld world, VillagerEntity patient) {
        world.spawnParticles(
                ParticleTypes.HAPPY_VILLAGER,
                patient.getX(),
                patient.getY() + 1.0D,
                patient.getZ(),
                8,
                0.35D,
                0.45D,
                0.35D,
                0.02D
        );
    }

    @Unique
    private void hc$holdHealingPotion(VillagerEntity cleric) {
        cleric.equipStack(EquipmentSlot.MAINHAND, PotionContentsComponent.createStack(Items.POTION, Potions.HEALING));
        cleric.setEquipmentDropChance(EquipmentSlot.MAINHAND, 0.0F);

        hc$holdingHealingPotion = true;
    }

    @Unique
    private void hc$holdSplashHealingPotion(VillagerEntity cleric) {
        cleric.equipStack(EquipmentSlot.MAINHAND, PotionContentsComponent.createStack(Items.SPLASH_POTION, Potions.HEALING));
        cleric.setEquipmentDropChance(EquipmentSlot.MAINHAND, 0.0F);

        hc$holdingHealingPotion = true;
    }

    @Unique
    private boolean hc$isHealingPotionItem(ItemStack stack) {
        return stack.isOf(Items.POTION) || stack.isOf(Items.SPLASH_POTION);
    }

    @Unique
    private void hc$cancelHealing(VillagerEntity cleric) {
        boolean wasHealing = hc$targetVillagerUuid != null || hc$holdingHealingPotion;

        hc$targetVillagerUuid = null;

        if (wasHealing) {
            cleric.getNavigation().stop();
        }

        if (hc$holdingHealingPotion || hc$isHealingPotionItem(cleric.getEquippedStack(EquipmentSlot.MAINHAND))) {
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
