package net.thbtt.helpfularmorers.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import org.jetbrains.annotations.Nullable;

final class WorldCompat {
    private WorldCompat() {
    }

    @Nullable
    static ServerLevel getServerWorld(Villager villager) {
        return villager.level() instanceof ServerLevel world ? world : null;
    }
}
