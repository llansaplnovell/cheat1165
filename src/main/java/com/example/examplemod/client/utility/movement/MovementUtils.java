package com.example.examplemod.client.utility.movement;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.vector.Vector3d;

/** Vector math shared by movement-related modules. */
public final class MovementUtils {

    private MovementUtils() {
    }

    /** Horizontal speed, ignoring the vertical (fall/jump) component. */
    public static double horizontalSpeed(LivingEntity entity) {
        Vector3d motion = entity.getDeltaMovement();
        return Math.sqrt(motion.x * motion.x + motion.z * motion.z);
    }

    /** A unit vector pointing the way {@code entity} is facing, on the horizontal plane. */
    public static Vector3d facingDirection(LivingEntity entity) {
        double yawRad = Math.toRadians(entity.yRot);
        return new Vector3d(-Math.sin(yawRad), 0, Math.cos(yawRad));
    }
}
