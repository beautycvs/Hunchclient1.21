package dev.hunchclient.mixin.accessor;

import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor to modify final Slot x/y fields for custom GUI slot repositioning
 */
@Mixin(Slot.class)
public interface SlotAccessor {

    @Accessor("x")
    int getX();

    @Mutable
    @Accessor("x")
    void setX(int x);

    @Accessor("y")
    int getY();

    @Mutable
    @Accessor("y")
    void setY(int y);
}
