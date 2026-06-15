package net.thbtt.helpfularmorers.mixin;

import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.village.VillagerProfession;

final class ProfessionCompat {
    private ProfessionCompat() {
    }

    static boolean isArmorer(VillagerEntity villager) {
        return villager.getVillagerData().profession().matchesKey(VillagerProfession.ARMORER);
    }
}
