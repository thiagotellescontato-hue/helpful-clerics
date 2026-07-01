package net.thbtt.helpfularmorers.compat;

import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.village.VillagerProfession;

public final class ProfessionCompat {
    private ProfessionCompat() {
    }

    public static boolean isArmorer(VillagerEntity villager) {
        return villager.getVillagerData().getProfession() == VillagerProfession.ARMORER;
    }
}
