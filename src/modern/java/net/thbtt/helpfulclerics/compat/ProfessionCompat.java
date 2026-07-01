package net.thbtt.helpfulclerics.compat;

import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.village.VillagerProfession;

public final class ProfessionCompat {
    private ProfessionCompat() {
    }

    public static boolean isCleric(VillagerEntity villager) {
        return villager.getVillagerData().profession().matchesKey(VillagerProfession.CLERIC);
    }
}
