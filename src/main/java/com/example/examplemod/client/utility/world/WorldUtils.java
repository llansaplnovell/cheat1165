package com.example.examplemod.client.utility.world;

import net.minecraft.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;

/** Common world queries shared across modules and HUD elements. */
public final class WorldUtils {

    private WorldUtils() {
    }

    public static boolean isAir(BlockPos pos) {
        ClientWorld world = Minecraft.getInstance().level;
        return world == null || world.getBlockState(pos).getBlock() == Blocks.AIR;
    }

    public static float getBlockHardness(BlockPos pos) {
        ClientWorld world = Minecraft.getInstance().level;
        if (world == null) {
            return 0f;
        }
        return world.getBlockState(pos).getDestroySpeed(world, pos);
    }
}
