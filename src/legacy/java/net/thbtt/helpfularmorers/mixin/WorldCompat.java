package net.thbtt.helpfularmorers.mixin;

import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;

final class WorldCompat {
    private WorldCompat() {
    }

    @Nullable
    static ServerWorld getServerWorld(VillagerEntity villager) {
        return villager.getWorld() instanceof ServerWorld world ? world : null;
    }
}
