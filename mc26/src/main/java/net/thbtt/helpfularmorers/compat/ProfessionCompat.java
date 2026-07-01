package net.thbtt.helpfularmorers.compat;

import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

public final class ProfessionCompat {
    private ProfessionCompat() {
    }

    public static boolean isArmorer(Villager villager) {
        return villager.getVillagerData().profession().is(VillagerProfession.ARMORER);
    }
}
