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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

@Mixin(Villager.class)
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

        Villager patient = hc$getCurrentTarget(world);

        if (patient == null || !hc$isValidPatient(patient, cleric)) {
            patient = hc$findNearestDamagedVillager(world, cleric);

            if (patient == null) {
                hc$clearOnlyHelpfulClericState(cleric);
                return;
            }

            hc$targetVillagerUuid = patient.getUUID();
        }

        double distanceSq = cleric.distanceToSqr(patient);
        double healDistanceSq = HC_HEAL_DISTANCE * HC_HEAL_DISTANCE;

        hc$holdHealingPotion(cleric);

        if (distanceSq > healDistanceSq) {
            cleric.getNavigation().moveTo(patient, 0.65D);
            cleric.getLookControl().setLookAt(patient, 30.0F, 30.0F);
            return;
        }

        cleric.getNavigation().stop();
        cleric.getLookControl().setLookAt(patient, 30.0F, 30.0F);

        if (hc$healCooldown <= 0) {
            patient.addEffect(new MobEffectInstance(MobEffects.INSTANT_HEALTH, 1, 0));
            cleric.swing(InteractionHand.MAIN_HAND);

            world.playSound(
                    null,
                    patient.blockPosition(),
                    SoundEvents.GENERIC_DRINK,
                    SoundSource.NEUTRAL,
                    1.0F,
                    1.0F
            );

            hc$healCooldown = 60;

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
    private boolean hc$isRestTime(ServerLevel world) {
        return world.isDarkOutside();
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
    private void hc$fleeFromDanger(ServerLevel world, Villager cleric) {
        LivingEntity threat = hc$findNearestThreat(world, cleric);
        Vec3 away;

        if (threat != null) {
            away = new Vec3(
                    cleric.getX() - threat.getX(),
                    0.0D,
                    cleric.getZ() - threat.getZ()
            );
        } else {
            double angle = cleric.getRandom().nextDouble() * Math.PI * 2.0D;
            away = new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
        }

        if (away.lengthSqr() < 0.0001D) {
            double angle = cleric.getRandom().nextDouble() * Math.PI * 2.0D;
            away = new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
        }

        away = away.normalize();

        double targetX = cleric.getX() + away.x * HC_FLEE_DISTANCE;
        double targetY = cleric.getY();
        double targetZ = cleric.getZ() + away.z * HC_FLEE_DISTANCE;

        cleric.getNavigation().moveTo(targetX, targetY, targetZ, 0.8D);
    }

    @Unique
    @Nullable
    private LivingEntity hc$findNearestThreat(ServerLevel world, Villager cleric) {
        LivingEntity attacker = cleric.getLastHurtByMob();

        if (attacker != null && attacker.isAlive() && !attacker.isRemoved()) {
            return attacker;
        }

        List<Monster> hostiles = world.getEntitiesOfClass(
                Monster.class,
                cleric.getBoundingBox().inflate(HC_DANGER_RADIUS),
                hostile -> hostile.isAlive() && !hostile.isRemoved()
        );

        return hostiles.stream()
                .min(Comparator.comparingDouble(cleric::distanceToSqr))
                .orElse(null);
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
    private Villager hc$findNearestDamagedVillager(ServerLevel world, Villager cleric) {
        if (hc$scanCooldown > 0) {
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
                .min(Comparator.comparingDouble(cleric::distanceToSqr))
                .orElse(null);
    }

    @Unique
    private boolean hc$isValidPatient(Villager villager, Villager cleric) {
        return villager.isAlive()
                && !villager.isRemoved()
                && !villager.getUUID().equals(cleric.getUUID())
                && villager.getHealth() < villager.getMaxHealth();
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
    private void hc$cancelHealing(Villager cleric) {
        boolean wasHealing = hc$targetVillagerUuid != null || hc$holdingHealingPotion;

        hc$targetVillagerUuid = null;

        if (wasHealing) {
            cleric.getNavigation().stop();
        }

        if (hc$holdingHealingPotion || cleric.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.POTION)) {
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
