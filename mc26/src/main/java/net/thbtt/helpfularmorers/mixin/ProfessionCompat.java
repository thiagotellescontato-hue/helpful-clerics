package net.thbtt.helpfularmorers.mixin;

import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

final class ProfessionCompat {
    private ProfessionCompat() {
    }

    static boolean isArmorer(Villager villager) {
        return villager.getVillagerData().profession().is(VillagerProfession.ARMORER);
    }
}
