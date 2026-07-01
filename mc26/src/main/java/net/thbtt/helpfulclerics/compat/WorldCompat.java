package net.thbtt.helpfulclerics.compat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import org.jetbrains.annotations.Nullable;

public final class WorldCompat {
    private WorldCompat() {
    }

    @Nullable
    public static ServerLevel getServerWorld(Villager villager) {
        return villager.level() instanceof ServerLevel world ? world : null;
    }
}
