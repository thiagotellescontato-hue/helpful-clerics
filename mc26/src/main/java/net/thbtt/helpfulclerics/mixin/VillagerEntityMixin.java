package net.thbtt.helpfulclerics.mixin;

import net.thbtt.helpfulclerics.compat.ProfessionCompat;
import net.thbtt.helpfulclerics.compat.WorldCompat;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

@Mixin(Villager.class)
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

    @Inject(method = "customServerAiStep", at = @At("TAIL"))
    private void helpfulClerics$tickVillagerHealing(CallbackInfo ci) {
        Villager cleric = (Villager) (Object) this;

        ServerLevel world = WorldCompat.getServerWorld(cleric);
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
            }
            return;
        }

        hc$selfHealDelay = 0;

        List<Villager> nearbyPatients = hc$findNearbyDamagedVillagers(world, cleric);
        if (nearbyPatients.size() >= HC_GROUP_HEAL_MIN_PATIENTS && hc$healCooldown <= 0) {
            hc$holdSplashHealingPotion(cleric);
            cleric.getNavigation().stop();
            hc$splashHealVillager(world, cleric, hc$mostInjuredVillager(nearbyPatients, cleric));
            hc$cancelHealing(cleric);
            return;
        }

        Villager patient = hc$getCurrentTarget(world);
        Villager priorityPatient = hc$findMostInjuredVillager(world, cleric, raidActive || hc$shouldForceScan());

        if (priorityPatient != null) {
            patient = priorityPatient;
            hc$targetVillagerUuid = patient.getUUID();
        } else if (patient == null || !hc$isValidPatient(patient, cleric)) {
            patient = hc$findMostInjuredVillager(world, cleric, false);

            if (patient == null) {
                hc$clearOnlyHelpfulClericState(cleric);
                return;
            }

            hc$targetVillagerUuid = patient.getUUID();
        }

        double distanceSq = cleric.distanceToSqr(patient);
        double healDistanceSq = HC_HEAL_DISTANCE * HC_HEAL_DISTANCE;

        hc$holdHealingPotion(cleric);

        if (distanceSq > healDistanceSq || !cleric.hasLineOfSight(patient)) {
            cleric.getLookControl().setLookAt(patient, 30.0F, 30.0F);

            if (raidActive && hc$dangerNearby) {
                cleric.getNavigation().stop();

                if (cleric.hasLineOfSight(patient) && hc$healCooldown <= 0) {
                    hc$holdSplashHealingPotion(cleric);
                    hc$splashHealVillager(world, cleric, patient);
                    hc$cancelHealing(cleric);
                }

                return;
            }

            boolean canReachPatient = cleric.getNavigation().moveTo(patient, 0.65D);

            if (!canReachPatient && hc$healCooldown <= 0) {
                hc$holdSplashHealingPotion(cleric);
                hc$splashHealVillager(world, cleric, patient);
                hc$cancelHealing(cleric);
            }

            return;
        }

        cleric.getNavigation().stop();
        cleric.getLookControl().setLookAt(patient, 30.0F, 30.0F);

        if (hc$healCooldown <= 0) {
            hc$healVillager(world, cleric, patient);

            if (patient.getHealth() >= patient.getMaxHealth()) {
                hc$cancelHealing(cleric);
            }
        }
    }

    @Unique
    private boolean hc$isCleric(Villager villager) {
        return ProfessionCompat.isCleric(villager);
    }

    @Unique
    private void hc$tickPatientSeeking(ServerLevel world, Villager patient) {
        if (patient.isBaby()) {
            hc$seekingCleric = false;
            return;
        }

        Villager cleric = hc$findNearestCleric(world, patient);
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
            patient.getNavigation().moveTo(cleric, 0.9D);
            patient.getLookControl().setLookAt(cleric, 30.0F, 30.0F);
            return;
        }

        double seekDistanceSq = HC_SEEK_CLERIC_DISTANCE * HC_SEEK_CLERIC_DISTANCE;
        if (patient.distanceToSqr(cleric) > seekDistanceSq) {
            patient.getNavigation().moveTo(cleric, 0.7D);
        } else {
            patient.getNavigation().stop();
        }

        patient.getLookControl().setLookAt(cleric, 30.0F, 30.0F);
    }

    @Unique
    private boolean hc$isRestTime(ServerLevel world) {
        return world.isDarkOutside();
    }

    @Unique
    private boolean hc$isRaidActive(ServerLevel world, Villager villager) {
        Raid raid = world.getRaidAt(villager.blockPosition());
        return raid != null && raid.isActive();
    }

    @Unique
    private void hc$updateDangerState(ServerLevel world, Villager cleric) {
        if (hc$dangerCooldown > 0) {
            hc$dangerCooldown--;
            return;
        }

        hc$dangerCooldown = 10;

        List<Monster> hostiles = world.getEntitiesOfClass(
                Monster.class,
                cleric.getBoundingBox().inflate(HC_DANGER_RADIUS),
                hostile -> hostile.isAlive() && !hostile.isRemoved()
        );

        hc$dangerNearby = !hostiles.isEmpty();
    }

    @Unique
    private boolean hc$isThreatNearby(ServerLevel world, Villager villager) {
        return !world.getEntitiesOfClass(
                Monster.class,
                villager.getBoundingBox().inflate(HC_DANGER_RADIUS),
                hostile -> hostile.isAlive() && !hostile.isRemoved()
        ).isEmpty();
    }

    @Unique
    @Nullable
    private Villager hc$getCurrentTarget(ServerLevel world) {
        if (hc$targetVillagerUuid == null) {
            return null;
        }

        if (world.getEntity(hc$targetVillagerUuid) instanceof Villager villager) {
            return villager;
        }

        return null;
    }

    @Unique
    @Nullable
    private Villager hc$findMostInjuredVillager(ServerLevel world, Villager cleric, boolean force) {
        if (!force && hc$scanCooldown > 0) {
            hc$scanCooldown--;
            return null;
        }

        hc$scanCooldown = 20 + cleric.getRandom().nextInt(20);

        List<Villager> villagers = world.getEntitiesOfClass(
                Villager.class,
                cleric.getBoundingBox().inflate(HC_SEARCH_RADIUS),
                villager -> hc$isValidPatient(villager, cleric)
        );

        return villagers.stream()
                .min(Comparator
                        .comparingDouble(this::hc$healthPercent)
                        .thenComparingDouble(cleric::distanceToSqr))
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
    private List<Villager> hc$findNearbyDamagedVillagers(ServerLevel world, Villager cleric) {
        return world.getEntitiesOfClass(
                Villager.class,
                cleric.getBoundingBox().inflate(HC_GROUP_HEAL_RADIUS),
                villager -> hc$isValidPatient(villager, cleric)
        );
    }

    @Unique
    private Villager hc$mostInjuredVillager(List<Villager> villagers, Villager cleric) {
        return villagers.stream()
                .min(Comparator
                        .comparingDouble(this::hc$healthPercent)
                        .thenComparingDouble(cleric::distanceToSqr))
                .orElse(villagers.get(0));
    }

    @Unique
    private double hc$healthPercent(Villager villager) {
        return villager.getHealth() / villager.getMaxHealth();
    }

    @Unique
    @Nullable
    private Villager hc$findNearestCleric(ServerLevel world, Villager patient) {
        List<Villager> clerics = world.getEntitiesOfClass(
                Villager.class,
                patient.getBoundingBox().inflate(HC_SEARCH_RADIUS),
                villager -> villager.isAlive()
                        && !villager.isRemoved()
                        && !villager.isBaby()
                        && !villager.getUUID().equals(patient.getUUID())
                        && hc$isCleric(villager)
        );

        return clerics.stream()
                .min(Comparator.comparingDouble(patient::distanceToSqr))
                .orElse(null);
    }

    @Unique
    private boolean hc$isValidPatient(Villager villager, Villager cleric) {
        return villager.isAlive()
                && !villager.isRemoved()
                && !villager.getUUID().equals(cleric.getUUID())
                && villager.getHealth() < villager.getMaxHealth()
                && cleric.hasLineOfSight(villager);
    }

    @Unique
    private void hc$healVillager(ServerLevel world, Villager cleric, Villager patient) {
        if (hc$healCooldown > 0) {
            return;
        }

        patient.addEffect(new MobEffectInstance(MobEffects.INSTANT_HEALTH, 1, 0));
        hc$spawnHealingParticles(world, patient);
        cleric.swing(InteractionHand.MAIN_HAND);

        world.playSound(
                null,
                patient.blockPosition(),
                SoundEvents.ENCHANTMENT_TABLE_USE,
                SoundSource.NEUTRAL,
                0.65F,
                1.35F
        );

        hc$healCooldown = 60;
    }

    @Unique
    private void hc$splashHealVillager(ServerLevel world, Villager cleric, Villager target) {
        if (hc$healCooldown > 0) {
            return;
        }

        ItemStack potionStack = PotionContents.createItemStack(Items.SPLASH_POTION, Potions.HEALING);

        cleric.getLookControl().setLookAt(target, 30.0F, 30.0F);
        cleric.swing(InteractionHand.MAIN_HAND);
        ThrownSplashPotion potion = new ThrownSplashPotion(world, cleric, potionStack);

        double deltaX = target.getX() - cleric.getX();
        double deltaY = target.getY(0.5D) - potion.getY();
        double deltaZ = target.getZ() - cleric.getZ();
        double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

        potion.shoot(deltaX, deltaY + horizontalDistance * 0.2D, deltaZ, 0.75F, 8.0F);
        world.addFreshEntity(potion);

        hc$healCooldown = 60;
    }

    @Unique
    private void hc$spawnHealingParticles(ServerLevel world, Villager patient) {
        world.sendParticles(
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
    private void hc$holdHealingPotion(Villager cleric) {
        ItemStack mainHand = cleric.getItemBySlot(EquipmentSlot.MAINHAND);

        if (!mainHand.is(Items.POTION)) {
            cleric.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.POTION));
            cleric.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        }

        hc$holdingHealingPotion = true;
    }

    @Unique
    private void hc$holdSplashHealingPotion(Villager cleric) {
        ItemStack mainHand = cleric.getItemBySlot(EquipmentSlot.MAINHAND);

        if (!mainHand.is(Items.SPLASH_POTION)) {
            cleric.setItemSlot(EquipmentSlot.MAINHAND, PotionContents.createItemStack(Items.SPLASH_POTION, Potions.HEALING));
            cleric.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        }

        hc$holdingHealingPotion = true;
    }

    @Unique
    private boolean hc$isHealingPotionItem(ItemStack stack) {
        return stack.is(Items.POTION) || stack.is(Items.SPLASH_POTION);
    }

    @Unique
    private void hc$cancelHealing(Villager cleric) {
        boolean wasHealing = hc$targetVillagerUuid != null || hc$holdingHealingPotion;

        hc$targetVillagerUuid = null;

        if (wasHealing) {
            cleric.getNavigation().stop();
        }

        if (hc$holdingHealingPotion || hc$isHealingPotionItem(cleric.getItemBySlot(EquipmentSlot.MAINHAND))) {
            cleric.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            hc$holdingHealingPotion = false;
        }
    }

    @Unique
    private void hc$clearOnlyHelpfulClericState(Villager cleric) {
        hc$targetVillagerUuid = null;

        if (hc$holdingHealingPotion) {
            cleric.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            hc$holdingHealingPotion = false;
        }
    }
}
