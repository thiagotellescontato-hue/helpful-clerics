package net.thbtt.helpfulclerics.compat;

import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;

public final class WorldCompat {
    private WorldCompat() {
    }

    @Nullable
    public static ServerWorld getServerWorld(VillagerEntity villager) {
        return villager.getEntityWorld() instanceof ServerWorld world ? world : null;
    }
}
