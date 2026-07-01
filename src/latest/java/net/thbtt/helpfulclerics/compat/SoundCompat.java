package net.thbtt.helpfulclerics.compat;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;

public final class SoundCompat {
    private SoundCompat() {
    }

    public static void playHealingSound(ServerWorld world, BlockPos pos) {
        world.playSound(null, pos, SoundEvents.ENTITY_GENERIC_DRINK.value(), SoundCategory.NEUTRAL, 1.0F, 1.0F);
    }
}