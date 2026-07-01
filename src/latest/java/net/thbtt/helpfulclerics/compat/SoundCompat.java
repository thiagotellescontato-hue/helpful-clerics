package net.thbtt.helpfulclerics.compat;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;

public final class SoundCompat {
    private SoundCompat() {
    }

    public static void playHealingSound(ServerWorld world, BlockPos pos) {
        world.playSound(null, pos, SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE.value(), SoundCategory.NEUTRAL, 0.65F, 1.35F);
    }
}
