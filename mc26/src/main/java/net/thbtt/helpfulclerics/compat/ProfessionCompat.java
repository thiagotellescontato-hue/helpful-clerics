package net.thbtt.helpfulclerics.compat;

import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

public final class ProfessionCompat {
    private ProfessionCompat() {
    }

    public static boolean isCleric(Villager villager) {
        return villager.getVillagerData().profession().is(VillagerProfession.CLERIC);
    }
}
