package dev.hunchclient.util;

import dev.hunchclient.module.impl.dungeons.SecretHitboxesModule;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Helper class for chest hitbox extension.
 * This is separate from the mixin to avoid ProGuard bytecode verification issues.
 */
public class ChestHitboxHelper {

    private static final VoxelShape EXTENDED_SHAPE = Shapes.block();

    /**
     * Check if chest hitbox should be extended.
     * Returns extended shape if enabled, null otherwise.
     */
    public static VoxelShape getExtendedShapeIfEnabled() {
        SecretHitboxesModule module = SecretHitboxesModule.getInstance();
        if (module == null) {
            return null;
        }
        if (!module.isChestsEnabled()) {
            return null;
        }
        return EXTENDED_SHAPE;
    }
}
